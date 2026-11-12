package com.example.altyazi

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.media.PlaybackParams
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hızlı, ücretsiz, API anahtarı ve model indirmesi gerektirmeyen doğal Türkçe ses motoru.
 * MainActivity ve CaptionEngine ile %100 uyumlu olması için sınıf yapısı korunmuştur.
 */
class SupertonicVoice(context: Context) {

    companion object {
        val VOICES = listOf(
            "F1" to "Emel (Kadın, Doğal ve Akıcı)",
            "F2" to "Seda (Kadın, Canlı Ton)",
            "M1" to "Ahmet (Erkek, Doğal Spiker)",
            "M2" to "Murat (Erkek, Derin ve Net)"
        )
        const val DEFAULT_VOICE = "F1"
        const val APPROX_SIZE_MB = 0
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger(0)
    @Volatile private var player: MediaPlayer? = null

    // Model indirme zorunluluğunu kaldırıyoruz, her zaman hazır döner
    fun isInstalled(): Boolean = true

    fun delete() {
        stop()
    }

    fun download(onProgress: (String) -> Unit, onDone: (Boolean, String?) -> Unit) {
        onProgress("Hazır (%100)")
        onDone(true, null)
    }

    fun warmUp() {
        // Bulut akışı anlık çalıştığı için ön ısıtma gerekmez
    }

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
                val pieces = splitSentences(text)
                val combinedMp3 = ByteArrayOutputStream()
                for (piece in pieces) {
                    if (gen != generation.get()) return@execute
                    val bytes = fetchFreeNaturalTts(piece)
                    combinedMp3.write(bytes)
                }
                val finalAudio = combinedMp3.toByteArray()
                if (finalAudio.isEmpty()) throw Exception("Ses verisi alınamadı")
                if (gen != generation.get()) return@execute

                // Erkek veya farklı ton seçildiyse hızı/tonu hafifçe uyarla
                val pitchMod = when (voice) {
                    "M1" -> 0.86f
                    "M2" -> 0.78f
                    "F2" -> 1.06f
                    else -> 1.0f
                }
                play(finalAudio, rate, pitchMod, gen)
            } catch (e: Throwable) {
                if (gen == generation.get()) {
                    onError(e.message ?: "Bağlantı hatası")
                }
            }
        }
    }

    fun stop() {
        generation.incrementAndGet()
        try {
            player?.stop()
        } catch (e: Exception) {
            // yok say
        }
    }

    fun release() {
        stop()
        executor.shutdownNow()
    }

    private fun splitSentences(text: String): List<String> {
        val raw = text.trim().replace(Regex("\\s+"), " ")
        if (raw.length <= 160) return listOf(raw)
        val out = ArrayList<String>()
        var cur = StringBuilder()
        for (word in raw.split(" ")) {
            if (cur.length + word.length + 1 > 160) {
                out.add(cur.toString())
                cur = StringBuilder(word)
            } else {
                if (cur.isNotEmpty()) cur.append(" ")
                cur.append(word)
            }
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    private fun fetchFreeNaturalTts(text: String): ByteArray {
        val encoded = URLEncoder.encode(text, "UTF-8")
        val urlStr = "https://translate.googleapis.com/translate_tts?ie=UTF-8&client=tw-ob&tl=tr&q=$encoded"
        val c = URL(urlStr).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "GET"
            c.connectTimeout = 5000
            c.readTimeout = 8000
            c.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
            )
            val code = c.responseCode
            if (code !in 200..299) throw Exception("HTTP $code")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    private class BytesSource(private val data: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= data.size) return -1
            val n = minOf(size, (data.size - position).toInt())
            System.arraycopy(data, position.toInt(), buffer, offset, n)
            return n
        }

        override fun getSize(): Long = data.size.toLong()
        override fun close() {}
    }

    private fun play(mp3: ByteArray, rate: Float, pitch: Float, gen: Int) {
        val done = CountDownLatch(1)
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(BytesSource(mp3))
            mp.setOnCompletionListener { done.countDown() }
            mp.setOnErrorListener { _, _, _ -> done.countDown(); true }
            mp.prepare()
            player = mp
            mp.start()
            try {
                mp.playbackParams = PlaybackParams()
                    .setSpeed(rate.coerceIn(0.5f, 2.0f))
                    .setPitch(pitch.coerceIn(0.5f, 2.0f))
            } catch (e: Exception) {
                // Cihaz desteklemiyorsa varsayılan hızda çal
            }
            while (!done.await(40, TimeUnit.MILLISECONDS)) {
                if (gen != generation.get()) break
            }
        } finally {
            try {
                mp.stop()
            } catch (e: Exception) {
                // yok say
            }
            mp.release()
            if (player === mp) player = null
        }
    }
}
