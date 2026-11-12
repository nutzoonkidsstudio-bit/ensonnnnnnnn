package com.example.altyazi

import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.media.PlaybackParams
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Azure Speech (resmî API) ile doğal Türkçe nöral ses.
 * Ücretsiz F0 katmanı ayda 500.000 karaktere kadar ücretsizdir.
 * İstek: https://{bölge}.tts.speech.microsoft.com/cognitiveservices/v1
 */
class AzureVoice {

    companion object {
        val VOICES = listOf(
            "tr-TR-EmelNeural" to "Emel (kadın)",
            "tr-TR-AhmetNeural" to "Ahmet (erkek)"
        )
        const val DEFAULT_VOICE = "tr-TR-EmelNeural"
        const val DEFAULT_REGION = "westeurope"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger(0)
    @Volatile private var player: MediaPlayer? = null

    fun speak(
        apiKey: String,
        region: String,
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
                val mp3 = fetch(apiKey, region, voice, text)
                if (gen != generation.get()) return@execute
                play(mp3, rate, gen)
            } catch (e: Exception) {
                if (gen == generation.get()) onError(e.message ?: "bilinmeyen hata")
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

    private fun escape(s: String): String =
        s.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), " ")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun fetch(apiKey: String, region: String, voice: String, text: String): ByteArray {
        val reg = region.trim().lowercase().ifEmpty { DEFAULT_REGION }
        val url = URL("https://$reg.tts.speech.microsoft.com/cognitiveservices/v1")
        val ssml = "<speak version='1.0' xml:lang='tr-TR'>" +
            "<voice xml:lang='tr-TR' name='$voice'>${escape(text)}</voice></speak>"

        val c = url.openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 20000
            c.doOutput = true
            c.setRequestProperty("Ocp-Apim-Subscription-Key", apiKey)
            c.setRequestProperty("Content-Type", "application/ssml+xml")
            c.setRequestProperty("X-Microsoft-OutputFormat", "audio-24khz-48kbitrate-mono-mp3")
            c.setRequestProperty("User-Agent", "AltyaziCeviri")
            c.outputStream.use { it.write(ssml.toByteArray(Charsets.UTF_8)) }

            val code = c.responseCode
            if (code !in 200..299) {
                val err = try {
                    c.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (e: Exception) {
                    ""
                }
                throw Exception(describeError(code, err))
            }
            val bytes = c.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) throw Exception("Ses servisi boş yanıt verdi")
            return bytes
        } finally {
            c.disconnect()
        }
    }

    private fun describeError(code: Int, body: String): String {
        val hint = when (code) {
            400 -> "İstek geçersiz"
            401, 403 -> "Anahtar ya da bölge yanlış"
            404 -> "Bölge adı yanlış"
            429 -> "Kota doldu, biraz bekle"
            else -> "Sunucu hatası"
        }
        val detail = try {
            JSONObject(body).getJSONObject("error").getString("message")
        } catch (e: Exception) {
            body.take(80)
        }
        return "$hint (HTTP $code)" + if (detail.isNotBlank()) ": $detail" else ""
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

    private fun play(mp3: ByteArray, rate: Float, gen: Int) {
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
                mp.playbackParams = PlaybackParams().setSpeed(rate)
            } catch (e: Exception) {
                // hız ayarlanamazsa normal hızda çal
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
            player = null
        }
    }
}
