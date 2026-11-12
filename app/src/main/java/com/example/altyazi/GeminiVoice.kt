package com.example.altyazi

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Gemini API metinden sese (TTS) ile çeviriyi seslendirir.
 * İstek: generativelanguage.googleapis.com ... :generateContent
 * Yanıt: base64 PCM, 24 kHz, 16 bit, mono.
 */
class GeminiVoice {

    companion object {
        val VOICES = listOf("Kore", "Puck", "Zephyr", "Charon", "Fenrir", "Aoede", "Leda", "Orus")
        const val DEFAULT_MODEL = "gemini-3.1-flash-tts-preview"
        private const val FALLBACK_MODEL = "gemini-2.5-flash-preview-tts"
        private const val SAMPLE_RATE = 24000
    }

    private class HttpError(val code: Int, message: String) : Exception(message)

    private val executor = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger(0)
    @Volatile private var track: AudioTrack? = null

    fun speak(
        apiKey: String,
        model: String,
        voice: String,
        text: String,
        rate: Float,
        flush: Boolean,
        onError: (String) -> Unit
    ) {
        val gen = if (flush) {
            stop()
            generation.incrementAndGet()
        } else {
            generation.get()
        }
        executor.execute {
            if (flush && gen != generation.get()) return@execute
            try {
                val pcm = fetchWithFallback(apiKey, model, voice, text)
                if (flush && gen != generation.get()) return@execute
                play(pcm, rate, gen, flush)
            } catch (e: Exception) {
                if (!(flush && gen != generation.get())) {
                    onError(e.message ?: "bilinmeyen hata")
                }
            }
        }
    }

    fun release() {
        stop()
        executor.shutdownNow()
    }

    fun stop() {
        try {
            track?.stop()
        } catch (e: Exception) {
            // yok say
        }
    }

    private fun fetchWithFallback(apiKey: String, model: String, voice: String, text: String): ByteArray {
        val models = listOf(model, FALLBACK_MODEL).distinct()
        var last: Exception? = null
        for (m in models) {
            try {
                return fetch(apiKey, m, voice, text)
            } catch (e: HttpError) {
                // Model bulunamadıysa yedek modeli dene, diğer hatalarda dur
                if (e.code == 404) last = e else throw e
            }
        }
        throw last ?: Exception("Model bulunamadı")
    }

    private fun fetch(apiKey: String, model: String, voice: String, text: String): ByteArray {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val prompt = "Türkçe, doğal ve akıcı bir şekilde söyle: $text"
        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                )
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put(
                        "speechConfig",
                        JSONObject().put(
                            "voiceConfig",
                            JSONObject().put(
                                "prebuiltVoiceConfig",
                                JSONObject().put("voiceName", voice)
                            )
                        )
                    )
            )

        val c = url.openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 25000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-goog-api-key", apiKey)
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val resp = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw HttpError(code, describeError(code, resp))

            val data = JSONObject(resp)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0)
                .getJSONObject("inlineData")
                .getString("data")
            return Base64.decode(data, Base64.DEFAULT)
        } finally {
            c.disconnect()
        }
    }

    private fun describeError(code: Int, resp: String): String {
        val detail = try {
            JSONObject(resp).getJSONObject("error").getString("message")
        } catch (e: Exception) {
            ""
        }
        val hint = when (code) {
            400 -> "API anahtarı ya da istek geçersiz"
            401, 403 -> "API anahtarı reddedildi"
            404 -> "Model bulunamadı"
            429 -> "Kota doldu, biraz bekle"
            else -> "Sunucu hatası"
        }
        return "$hint (HTTP $code)" + if (detail.isNotEmpty()) ": " + detail.take(100) else ""
    }

    private fun play(pcm: ByteArray, rate: Float, gen: Int, flush: Boolean) {
        if (pcm.isEmpty()) return
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(pcm, 0, pcm.size)
        try {
            t.setPlaybackParams(PlaybackParams().setSpeed(rate))
        } catch (e: Exception) {
            // hız ayarlanamazsa normal hızda çal
        }
        track = t
        t.play()

        val durationMs = (pcm.size / 2 * 1000L / SAMPLE_RATE / rate).toLong() + 400
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < durationMs) {
            if (t.playState != AudioTrack.PLAYSTATE_PLAYING) break
            if (flush && gen != generation.get()) break
            Thread.sleep(40)
        }
        try {
            t.stop()
        } catch (e: Exception) {
            // yok say
        }
        t.release()
        track = null
    }
}
