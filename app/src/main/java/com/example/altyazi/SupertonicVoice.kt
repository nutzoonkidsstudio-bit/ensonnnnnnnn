package com.example.altyazi

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.text.Normalizer
import java.util.Random
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Supertonic 3: cihazda çalışan (ONNX Runtime) çok dilli yapay zekâ sesi.
 * İnternet, API anahtarı ve kota gerekmez. Model dosyaları ilk kullanımda bir kez indirilir.
 *
 * Hat: duration_predictor -> text_encoder -> vector_estimator (n adım) -> vocoder -> 44,1 kHz ses
 */
class SupertonicVoice(context: Context) {

    companion object {
        // Dosya kimliği -> ekranda görünen ad
        val VOICES = listOf(
            "F1" to "Sarah (kadın, sakin ve dengeli)",
            "F2" to "Lily (kadın, canlı ve neşeli)",
            "F3" to "Jessica (kadın, spiker tonu)",
            "F4" to "Olivia (kadın, net ve kendinden emin)",
            "F5" to "Emily (kadın, yumuşak)",
            "M1" to "Alex (erkek, canlı)",
            "M2" to "James (erkek, derin ve ağırbaşlı)",
            "M3" to "Robert (erkek, otoriter spiker)",
            "M4" to "Sam (erkek, yumuşak ve genç)",
            "M5" to "Daniel (erkek, sıcak ve rahatlatıcı)"
        )
        const val DEFAULT_VOICE = "F1"
        const val APPROX_SIZE_MB = 380

        private const val STEPS = 5          // 5 hızlı, 8 daha kaliteli (Python örneğinde varsayılan 8)
        private const val SPEED = 1.05f
        private const val LANG = "tr"
        private const val MAX_CHUNK = 90      // parça başına en fazla karakter (kısa parça = daha hızlı ilk ses)
        private const val FIRST_CHUNK = 60    // ilk parça daha kısa: konuşma hemen başlasın
        private const val TAG = "Supertonic"

        private val BASES = listOf(
            "https://huggingface.co/Supertone/supertonic-3/resolve/main/",
            "https://huggingface.co/supertone-oss-archive/supertonic-3/resolve/main/"
        )
        private val FILES: List<String> = listOf(
            "onnx/tts.json",
            "onnx/unicode_indexer.json",
            "onnx/duration_predictor.onnx",
            "onnx/text_encoder.onnx",
            "onnx/vocoder.onnx",
            "onnx/vector_estimator.onnx"
        ) + VOICES.map { "voice_styles/${it.first}.json" }
    }

    private val dir = File(context.applicationContext.filesDir, "supertonic")
    private val executor = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger(0)
    @Volatile private var track: AudioTrack? = null

    // Yüklenen modeller (ilk kullanımda belleğe alınır)
    private var env: OrtEnvironment? = null
    private var dp: OrtSession? = null
    private var te: OrtSession? = null
    private var ve: OrtSession? = null
    private var voc: OrtSession? = null
    private var indexer: IntArray = IntArray(0)
    private var sampleRate = 44100
    private var baseChunk = 512
    private var chunkFactor = 6
    private var latentDim = 24
    private val styles = HashMap<String, Pair<FloatArray, FloatArray>>()

    // ------------------------------------------------------------ İndirme

    fun isInstalled(): Boolean = FILES.all { File(dir, it).let { f -> f.exists() && f.length() > 0 } }

    fun delete() {
        stop()
        close()
        dir.deleteRecursively()
    }

    fun download(onProgress: (String) -> Unit, onDone: (Boolean, String?) -> Unit) {
        Thread {
            try {
                FILES.forEachIndexed { i, rel ->
                    val target = File(dir, rel)
                    if (target.exists() && target.length() > 0) return@forEachIndexed
                    target.parentFile?.mkdirs()
                    val part = File(dir, "$rel.part")
                    var lastErr: Exception? = null
                    var ok = false
                    for (base in BASES) {
                        try {
                            fetchFile(base + rel, part) { pct ->
                                onProgress("İndiriliyor: ${i + 1}/${FILES.size} (%$pct)")
                            }
                            ok = true
                            break
                        } catch (e: Exception) {
                            lastErr = e
                        }
                    }
                    if (!ok) throw lastErr ?: Exception("İndirilemedi: $rel")
                    if (!part.renameTo(target)) throw Exception("Dosya kaydedilemedi: $rel")
                }
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, e.message ?: "bilinmeyen hata")
            }
        }.start()
    }

    private fun fetchFile(url: String, out: File, onPct: (Int) -> Unit) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "AltyaziCeviri")
            val code = c.responseCode
            if (code !in 200..299) throw Exception("Sunucu yanıtı HTTP $code")
            val total = c.contentLengthLong
            var done = 0L
            var lastPct = -1
            c.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                onPct(pct)
                            }
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }

    // ------------------------------------------------------------- Konuşma

    fun speak(
        voice: String,
        text: String,
        rate: Float,
        flush: Boolean,
        onError: (String) -> Unit
    ) {
        if (flush) stop()
        val gen = generation.get()
        executor.execute {
            if (gen != generation.get()) return@execute
            try {
                ensureLoaded()
                speakStreaming(voice, text, rate, gen)
            } catch (e: Throwable) {
                if (gen == generation.get()) onError(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Modeli önceden belleğe alır, ilk cümle gecikmesin. */
    fun warmUp() {
        executor.execute {
            try {
                ensureLoaded()
                // Boş bir deneme: ONNX çekirdekleri ısınsın, ilk cümle 6-7 sn sürmesin
                val t0 = System.currentTimeMillis()
                synthChunk(DEFAULT_VOICE, preprocess("Merhaba."), generation.get())
                Log.d(TAG, "ısınma ${System.currentTimeMillis() - t0} ms")
            } catch (e: Throwable) {
                // ilk gerçek istekte hata gösterilir
            }
        }
    }

    fun stop() {
        generation.incrementAndGet()
        try {
            track?.stop()
        } catch (e: Exception) {
            // yok say
        }
    }

    fun release() {
        stop()
        executor.shutdownNow()
        close()
    }

    private fun close() {
        listOf(dp, te, ve, voc).forEach { try { it?.close() } catch (e: Exception) { } }
        dp = null; te = null; ve = null; voc = null
        styles.clear()
    }

    // ------------------------------------------------------------ Model

    private fun ensureLoaded() {
        if (voc != null) return
        val e = OrtEnvironment.getEnvironment()
        env = e

        val cfg = JSONObject(File(dir, "onnx/tts.json").readText())
        cfg.optJSONObject("ae")?.let {
            sampleRate = it.optInt("sample_rate", sampleRate)
            baseChunk = it.optInt("base_chunk_size", baseChunk)
        }
        cfg.optJSONObject("ttl")?.let {
            chunkFactor = it.optInt("chunk_compress_factor", chunkFactor)
            latentDim = it.optInt("latent_dim", latentDim)
        }

        val arr = JSONArray(File(dir, "onnx/unicode_indexer.json").readText())
        indexer = IntArray(arr.length()) { arr.getInt(it) }

        val opts = { OrtSession.SessionOptions().apply { setIntraOpNumThreads(min(4, max(2, Runtime.getRuntime().availableProcessors() / 2))) } }
        dp = e.createSession(File(dir, "onnx/duration_predictor.onnx").path, opts())
        te = e.createSession(File(dir, "onnx/text_encoder.onnx").path, opts())
        ve = e.createSession(File(dir, "onnx/vector_estimator.onnx").path, opts())
        voc = e.createSession(File(dir, "onnx/vocoder.onnx").path, opts())
    }

    private fun flatten(a: Any?, out: ArrayList<Float>) {
        when (a) {
            is JSONArray -> for (i in 0 until a.length()) flatten(a.get(i), out)
            is Number -> out.add(a.toFloat())
        }
    }

    private fun loadStyle(id: String): Pair<FloatArray, FloatArray> = styles.getOrPut(id) {
        val o = JSONObject(File(dir, "voice_styles/$id.json").readText())
        fun read(key: String): FloatArray {
            val l = ArrayList<Float>()
            flatten(o.getJSONObject(key).get("data"), l)
            return l.toFloatArray()
        }
        read("style_ttl") to read("style_dp")
    }

    // --------------------------------------------------------- Metin işleme

    private fun preprocess(text: String): String {
        var t = Normalizer.normalize(text, Normalizer.Form.NFKD)
        t = t.replace(Regex("[\\p{So}\\p{Cs}\\u200B-\\u200F\\uFE0F]"), "")
        t = t.replace("–", "-").replace("‑", "-").replace("—", "-")
        t = t.replace("“", "\"").replace("”", "\"")
        t = t.replace("‘", "'").replace("’", "'").replace("´", "'").replace("`", "'")
        t = t.replace(Regex("[\\[\\]|#*_~^<>]"), " ")
        t = t.replace(Regex("\\.{2,}\\s*$"), ".").replace(Regex("\\.{2,}"), ",")
        t = t.replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return ""
        if (!Regex("[.!?;:,'\"\\u201C\\u201D\\u2018\\u2019)\\]}…]$").containsMatchIn(t)) t += "."
        return "<$LANG>$t</$LANG>"
    }

    private fun toIds(t: String): LongArray {
        val ids = ArrayList<Long>()
        for (ch in t) {
            val code = ch.code
            if (code < indexer.size) {
                val v = indexer[code]
                if (v >= 0) ids.add(v.toLong())
            }
        }
        return ids.toLongArray()
    }

    private fun chunks(text: String): List<String> {
        // Cümle sonlarında böl; uzun cümleleri virgül/noktalı virgülde de böl
        val pieces = ArrayList<String>()
        for (sent in text.split(Regex("(?<=[.!?…])\\s+")).filter { it.isNotBlank() }) {
            if (sent.length <= MAX_CHUNK) {
                pieces.add(sent)
            } else {
                pieces.addAll(sent.split(Regex("(?<=[,;:])\\s+")).filter { it.isNotBlank() })
            }
        }
        val out = ArrayList<String>()
        var cur = StringBuilder()
        for (p in pieces) {
            val limit = if (out.isEmpty()) FIRST_CHUNK else MAX_CHUNK
            if (cur.isNotEmpty() && cur.length + p.length + 1 > limit) {
                out.add(cur.toString())
                cur = StringBuilder()
            }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(p)
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    // ------------------------------------------------------------ Sentez

    /**
     * Parça parça üretir; ilk parça hazır olur olmaz çalmaya başlar, sonrakiler
     * çalarken üretilir (eskiden tüm metin bitmeden hiç ses çıkmıyordu).
     */
    private fun speakStreaming(voice: String, text: String, rate: Float, gen: Int) {
        val t0 = System.currentTimeMillis()
        var t: AudioTrack? = null
        var frames = 0L
        var lastWrite = 0L
        try {
            for (c in chunks(text)) {
                if (gen != generation.get()) return
                // Okunacak harf yoksa ("...", "- -") model rastgele harf mırıldanır: atla
                if (c.count { it.isLetter() } < 2) continue
                val pre = preprocess(c)
                if (pre.isEmpty()) continue

                val ts = System.currentTimeMillis()
                val pcm = synthChunk(voice, pre, gen)
                if (gen != generation.get()) return
                if (pcm.isEmpty()) continue
                Log.d(
                    TAG,
                    "parça ${c.length} kr: üretim ${System.currentTimeMillis() - ts} ms, " +
                        "ses ${pcm.size * 1000L / sampleRate} ms, ilk sese kadar ${System.currentTimeMillis() - t0} ms"
                )

                if (t == null) {
                    t = newTrack()
                    try {
                        t.playbackParams = PlaybackParams().setSpeed(rate)
                    } catch (e: Exception) {
                        // hız ayarlanamazsa normal hızda çal
                    }
                    track = t
                    t.write(pcm, 0, pcm.size)
                    t.play()
                } else {
                    t.write(pcm, 0, pcm.size)
                }
                frames += pcm.size
                lastWrite = System.currentTimeMillis()
            }

            val tr = t ?: return
            // Çalmanın bitmesini bekle (iptal edilirse çık)
            val deadline = lastWrite + (frames * 1000L / sampleRate / rate).toLong() + 1500
            while (System.currentTimeMillis() < deadline) {
                if (gen != generation.get()) break
                if (tr.playState != AudioTrack.PLAYSTATE_PLAYING) break
                if (tr.playbackHeadPosition >= frames) break
                Thread.sleep(40)
            }
        } finally {
            val tr = t
            if (tr != null) {
                try {
                    tr.stop()
                } catch (e: Exception) {
                    // yok say
                }
                tr.release()
                if (track === tr) track = null
            }
        }
    }

    private fun newTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        // ~40 sn'lik tampon: yazma hiç bloklanmaz, parçalar arası boşluk olursa ses kendiliğinden devam eder
        val buf = max(minBuf, sampleRate * 2 * 40)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun f(shape: LongArray, data: FloatArray): OnnxTensor =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)

    private fun floats(t: Any?): FloatArray {
        val fb = (t as OnnxTensor).floatBuffer
        val a = FloatArray(fb.remaining())
        fb.get(a)
        return a
    }

    private fun synthChunk(voice: String, pre: String, gen: Int): ShortArray {
        val ids = toIds(pre)
        if (ids.isEmpty()) return ShortArray(0)
        val (styleTtl, styleDp) = loadStyle(voice)
        val len = ids.size.toLong()

        val tIds = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, len))
        val tMask = f(longArrayOf(1, 1, len), FloatArray(ids.size) { 1f })
        val tStyleDp = f(longArrayOf(1, 8, 16), styleDp)
        val tStyleTtl = f(longArrayOf(1, 50, 256), styleTtl)

        val toClose = ArrayList<AutoCloseable>()
        try {
            toClose.addAll(listOf(tIds, tMask, tStyleDp, tStyleTtl))

            // 1) süre tahmini
            val dRes = dp!!.run(mapOf("text_ids" to tIds, "style_dp" to tStyleDp, "text_mask" to tMask))
            toClose.add(dRes)
            val duration = floats(dRes.get(0))[0] / SPEED
            val wavLen = max(1, (duration * sampleRate).toInt())

            // 2) metin gömmesi
            val eRes = te!!.run(mapOf("text_ids" to tIds, "style_ttl" to tStyleTtl, "text_mask" to tMask))
            toClose.add(eRes)
            val textEmb = eRes.get(0) as OnnxTensor

            // 3) gürültüden gizli ses temsiline (flow matching)
            val chunkSize = baseChunk * chunkFactor
            val latentLen = ceil(wavLen.toDouble() / chunkSize).toInt()
            val dim = latentDim * chunkFactor
            val rnd = Random()
            var xt = FloatArray(dim * latentLen) { rnd.nextGaussian().toFloat() }
            val tLatMask = f(longArrayOf(1, 1, latentLen.toLong()), FloatArray(latentLen) { 1f })
            toClose.add(tLatMask)
            val total = f(longArrayOf(1), floatArrayOf(STEPS.toFloat()))
            toClose.add(total)

            for (step in 0 until STEPS) {
                // Yeni cümle geldiyse eskisini yarıda bırak (kuyrukta bekletme)
                if (gen != generation.get()) return ShortArray(0)
                val tXt = f(longArrayOf(1, dim.toLong(), latentLen.toLong()), xt)
                val tStep = f(longArrayOf(1), floatArrayOf(step.toFloat()))
                val r = ve!!.run(
                    mapOf(
                        "noisy_latent" to tXt,
                        "text_emb" to textEmb,
                        "style_ttl" to tStyleTtl,
                        "latent_mask" to tLatMask,
                        "text_mask" to tMask,
                        "current_step" to tStep,
                        "total_step" to total
                    )
                )
                xt = floats(r.get(0))
                r.close(); tXt.close(); tStep.close()
            }

            // 4) ses dalgası
            val tLat = f(longArrayOf(1, dim.toLong(), latentLen.toLong()), xt)
            toClose.add(tLat)
            val vRes = voc!!.run(mapOf("latent" to tLat))
            toClose.add(vRes)
            val wav = floats(vRes.get(0))

            val n = min(wavLen, wav.size)
            return ShortArray(n) {
                (wav[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }
        } finally {
            toClose.forEach { try { it.close() } catch (e: Exception) { } }
        }
    }
}
