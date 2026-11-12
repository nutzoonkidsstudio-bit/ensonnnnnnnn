package com.example.altyazi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

data class HistoryItem(val time: Long, val original: String, val translated: String)

class CaptionEngine(context: Context) {

    companion object {
        val LANGUAGES = listOf(
            TranslateLanguage.ENGLISH to "İngilizce",
            TranslateLanguage.GERMAN to "Almanca",
            TranslateLanguage.FRENCH to "Fransızca",
            TranslateLanguage.SPANISH to "İspanyolca",
            TranslateLanguage.ITALIAN to "İtalyanca",
            TranslateLanguage.PORTUGUESE to "Portekizce"
        )
        const val DEF_LEFT = 0.15f
        const val DEF_TOP = 0.72f
        const val DEF_WIDTH = 0.70f
        const val DEF_HEIGHT = 0.24f
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val netExecutor = Executors.newSingleThreadExecutor()

    // ---------- Ayarlar ----------
    var speak by mutableStateOf(prefs.getBoolean("speak", true))
    var speechRate by mutableFloatStateOf(prefs.getFloat("rate", 1.0f))
    var pitch by mutableFloatStateOf(prefs.getFloat("pitch", 1.0f))
    var flushSpeech by mutableStateOf(prefs.getBoolean("flush", true))
    var textSize by mutableFloatStateOf(prefs.getFloat("size", 24f))
    var overlay by mutableStateOf(prefs.getBoolean("overlay", true))
    var scanSeconds by mutableFloatStateOf(prefs.getFloat("scan", 0.5f))
    var themeMode by mutableIntStateOf(prefs.getInt("theme", 0))
    var keepAwake by mutableStateOf(prefs.getBoolean("awake", true))
    var keepHistory by mutableStateOf(prefs.getBoolean("keephist", true))
    var tipSeen by mutableStateOf(prefs.getBoolean("tip", false))

    // ---------- Ses motoru (0 telefon sesi, 1 Gemini, 2 Azure, 3 Supertonic/Ücretsiz Doğal) ----------
    var voiceEngine by mutableIntStateOf(prefs.getInt("veng3", 3))
    var azureKey by mutableStateOf(prefs.getString("akey", "") ?: "")
    var azureRegion by mutableStateOf(
        prefs.getString("aregion", AzureVoice.DEFAULT_REGION) ?: AzureVoice.DEFAULT_REGION
    )
    var azureVoiceName by mutableStateOf(
        prefs.getString("avoice", AzureVoice.DEFAULT_VOICE) ?: AzureVoice.DEFAULT_VOICE
    )
    var azureError by mutableStateOf<String?>(null)
    private val azure = AzureVoice()

    private val supertonic = SupertonicVoice(appContext)
    var stVoice by mutableStateOf(
        prefs.getString("stvoice", SupertonicVoice.DEFAULT_VOICE) ?: SupertonicVoice.DEFAULT_VOICE
    )
    var stReady by mutableStateOf(true)
    var stBusy by mutableStateOf(false)
    var stStatus by mutableStateOf<String?>(null)
    var stError by mutableStateOf<String?>(null)
    var geminiKey by mutableStateOf(prefs.getString("gkey", "") ?: "")
    var geminiVoice by mutableStateOf(prefs.getString("gvoice", "Kore") ?: "Kore")
    var geminiModel by mutableStateOf(
        prefs.getString("gmodel", GeminiVoice.DEFAULT_MODEL) ?: GeminiVoice.DEFAULT_MODEL
    )
    var geminiError by mutableStateOf<String?>(null)
    private val gemini = GeminiVoice()
    var lang by mutableStateOf(
        prefs.getString("lang", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH
    )

    // ---------- Altyazı kutusu ----------
    var boxLeft by mutableFloatStateOf(prefs.getFloat("bl2", DEF_LEFT))
    var boxTop by mutableFloatStateOf(prefs.getFloat("bt2", DEF_TOP))
    var boxWidth by mutableFloatStateOf(prefs.getFloat("bw2", DEF_WIDTH))
    var boxHeight by mutableFloatStateOf(prefs.getFloat("bh2", DEF_HEIGHT))

    // ---------- Çalışma durumu ----------
    var running by mutableStateOf(false)
    var original by mutableStateOf("")
    var translated by mutableStateOf("")
    var modelReady by mutableStateOf(true)
    var modelError by mutableStateOf(false)
    var ttsMissing by mutableStateOf(false)
    private var status by mutableStateOf("Yeşil kutuyu altyazının üstüne getir")

    val history = mutableStateListOf<HistoryItem>()

    val statusText: String
        get() = when {
            !running -> "Duraklatıldı"
            else -> status
        }

    val languageName: String
        get() = LANGUAGES.firstOrNull { it.first == lang }?.second ?: lang

    private val recognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private fun buildTranslator(code: String) = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(code)
            .setTargetLanguage(TranslateLanguage.TURKISH)
            .build()
    )

    private var translator = buildTranslator(lang)
    private var tts: TextToSpeech? = null

    @Volatile private var busy = false
    private var lastRun = 0L
    private var lastWords: Set<String> = emptySet()
    private var lastCleanText: String = ""
    private var pendingText: String = ""

    init {
        tts = TextToSpeech(appContext) { code ->
            if (code == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("tr", "TR"))
                ttsMissing = r == TextToSpeech.LANG_MISSING_DATA ||
                    r == TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        loadHistory()
        prepareModel()
    }

    private fun prepareModel() {
        modelError = false
        val t = translator
        t.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { if (t === translator) modelReady = true }
            .addOnFailureListener {
                // Çevrimdışı model inmezse bile çevrimiçi GTX çevirisi çalışır
                if (t === translator) modelReady = true
            }
    }

    fun retryModel() = prepareModel()

    fun changeLanguage(code: String) {
        if (code == lang) return
        translator.close()
        lang = code
        lastWords = emptySet()
        lastCleanText = ""
        pendingText = ""
        original = ""
        translated = ""
        translator = buildTranslator(code)
        prefs.edit().putString("lang", code).apply()
        prepareModel()
    }

    fun persist() {
        prefs.edit()
            .putBoolean("speak", speak)
            .putFloat("rate", speechRate)
            .putFloat("pitch", pitch)
            .putBoolean("flush", flushSpeech)
            .putFloat("size", textSize)
            .putBoolean("overlay", overlay)
            .putFloat("scan", scanSeconds)
            .putInt("theme", themeMode)
            .putBoolean("awake", keepAwake)
            .putBoolean("keephist", keepHistory)
            .putBoolean("tip", tipSeen)
            .putInt("veng3", voiceEngine)
            .putString("gkey", geminiKey)
            .putString("gvoice", geminiVoice)
            .putString("gmodel", geminiModel)
            .putString("akey", azureKey)
            .putString("aregion", azureRegion)
            .putString("avoice", azureVoiceName)
            .putString("stvoice", stVoice)
            .putFloat("bl2", boxLeft)
            .putFloat("bt2", boxTop)
            .putFloat("bw2", boxWidth)
            .putFloat("bh2", boxHeight)
            .apply()
        if (!speak) {
            tts?.stop()
            gemini.stop()
            azure.stop()
            supertonic.stop()
        }
    }

    fun moveBox(dx: Float, dy: Float) {
        boxLeft = (boxLeft + dx).coerceIn(0f, 1f - boxWidth)
        boxTop = (boxTop + dy).coerceIn(0f, 1f - boxHeight)
    }

    fun resizeBox(dx: Float, dy: Float) {
        boxWidth = (boxWidth + dx).coerceIn(0.1f, 1f - boxLeft)
        boxHeight = (boxHeight + dy).coerceIn(0.05f, 1f - boxTop)
    }

    fun resetBox() {
        boxLeft = DEF_LEFT
        boxTop = DEF_TOP
        boxWidth = DEF_WIDTH
        boxHeight = DEF_HEIGHT
        persist()
    }

    private fun loadHistory() {
        try {
            val arr = JSONArray(prefs.getString("history", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                history.add(HistoryItem(o.getLong("t"), o.getString("o"), o.getString("r")))
            }
        } catch (e: Exception) {
        }
    }

    private fun saveHistory() {
        val arr = JSONArray()
        history.forEach {
            arr.put(JSONObject().put("t", it.time).put("o", it.original).put("r", it.translated))
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun addHistory(o: String, r: String) {
        history.add(0, HistoryItem(System.currentTimeMillis(), o, r))
        while (history.size > 200) history.removeAt(history.size - 1)
        saveHistory()
    }

    fun clearHistory() {
        history.clear()
        saveHistory()
    }

    // ---------- Seslendirme ----------
    fun speakText(text: String) {
        if (text.isEmpty()) return
        when (voiceEngine) {
            1 -> {
                if (geminiKey.isBlank()) {
                    geminiError = "API anahtarı girilmedi, doğal yedek ses kullanılıyor."
                    speakSupertonic(text)
                    return
                }
                geminiError = null
                gemini.speak(
                    geminiKey.trim(),
                    geminiModel.trim().ifEmpty { GeminiVoice.DEFAULT_MODEL },
                    geminiVoice,
                    text,
                    speechRate,
                    flushSpeech
                ) { msg ->
                    geminiError = "Gemini sesi çalışmadı: $msg. Doğal yedek ses kullanıldı."
                    speakSupertonic(text)
                }
            }
            2 -> speakAzure(text)
            3 -> speakSupertonic(text)
            else -> speakLocal(text)
        }
    }

    private fun speakSupertonic(text: String) {
        stError = null
        supertonic.speak(stVoice, text, speechRate, flushSpeech) { msg ->
            stError = "Doğal ses bağlantı hatası ($msg), telefon sesi kullanıldı."
            speakLocal(text)
        }
    }

    fun previewSupertonic(id: String) {
        stError = null
        supertonic.speak(id, "Merhaba, bu doğal Türkçe ses denemesidir.", speechRate, true) { msg ->
            stError = "Ses çalışmadı: $msg"
        }
    }

    fun downloadSupertonic() {
        stReady = true
        stBusy = false
        stStatus = "Hazır"
    }

    fun deleteSupertonic() {
        supertonic.delete()
    }

    private fun speakAzure(text: String) {
        if (azureKey.isBlank()) {
            azureError = "Azure anahtarı girilmedi, doğal yedek ses kullanılıyor."
            speakSupertonic(text)
            return
        }
        azureError = null
        azure.speak(
            azureKey.trim(),
            azureRegion,
            azureVoiceName,
            text,
            speechRate,
            flushSpeech
        ) { msg ->
            azureError = "Azure sesi çalışmadı: $msg."
            speakSupertonic(text)
        }
    }

    private fun speakLocal(text: String) {
        tts?.language = Locale("tr", "TR")
        tts?.setSpeechRate(speechRate)
        tts?.setPitch(pitch)
        tts?.speak(
            text,
            if (flushSpeech) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
            null,
            "caption"
        )
    }

    // ---------- Kare işleme ----------
    fun analyze(proxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (!running || busy || now - lastRun < (scanSeconds * 1000).toLong()) {
            proxy.close()
            return
        }
        busy = true
        lastRun = now

        val crop: Bitmap? = try {
            val src = proxy.toBitmap()
            val m = Matrix().apply { postRotate(proxy.imageInfo.rotationDegrees.toFloat()) }
            val full = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            val x = (boxLeft.coerceIn(0f, 0.95f) * full.width).toInt().coerceAtMost(full.width - 1)
            val y = (boxTop.coerceIn(0f, 0.95f) * full.height).toInt().coerceAtMost(full.height - 1)
            val w = (boxWidth * full.width).toInt().coerceIn(1, full.width - x)
            val h = (boxHeight * full.height).toInt().coerceIn(1, full.height - y)
            Bitmap.createBitmap(full, x, y, w, h)
        } catch (e: Exception) {
            null
        } finally {
            proxy.close()
        }

        if (crop == null) {
            busy = false
            return
        }

        val ocrInput = if (crop.height < 260) {
            try {
                Bitmap.createScaledBitmap(crop, crop.width * 2, crop.height * 2, true)
            } catch (e: Exception) {
                crop
            }
        } else crop

        recognizer.process(InputImage.fromBitmap(ocrInput, 0))
            .addOnSuccessListener { handle(extractText(it)) }
            .addOnFailureListener { busy = false }
    }

    // ---------- OCR temizliği ----------
    private val vowels = "aeıioöuüyAEIİOÖUÜYàáâèéêìíîòóôùúû"

    private fun extractText(t: Text): String {
        val parts = ArrayList<String>()
        for (block in t.textBlocks) {
            for (line in block.lines) {
                val l = cleanLine(line.text)
                if (l.isNotEmpty()) parts.add(l)
            }
        }
        return fixCaps(parts.joinToString(" ").trim())
    }

    private fun cleanLine(src: String): String {
        var t = src.replace('|', 'I')
            .replace(Regex("[_~^*#@<>\\[\\]{}\\\\]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (t.length < 2) return ""

        val letters = t.count { it.isLetter() }
        if (letters < 2 || letters.toFloat() / t.length < 0.45f) return ""

        // En az bir sesli harf bulunsun (menü ikonlarını ve rastgele çizgileri eler)
        if (!t.any { it in vowels }) return ""
        return t
    }

    private fun fixCaps(s: String): String {
        val letters = s.filter { it.isLetter() }
        if (letters.length < 4) return s
        if (letters.count { it.isUpperCase() }.toFloat() / letters.length < 0.80f) return s
        val sb = StringBuilder()
        var start = true
        for (ch in s.lowercase()) {
            if (start && ch.isLetter()) {
                sb.append(ch.uppercaseChar())
                start = false
            } else {
                sb.append(ch)
            }
            if (ch == '.' || ch == '!' || ch == '?') start = true
        }
        var out = sb.toString()
        if (lang == TranslateLanguage.ENGLISH) out = out.replace(Regex("\\bi\\b"), "I")
        return out
    }

    private fun handle(raw: String) {
        val clean = raw.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        val words = clean.lowercase()
            .split(Regex("[^\\p{L}0-9']+"))
            .filter { it.isNotEmpty() }
            .toSet()
        val letters = clean.count { it.isLetter() }

        if (clean.length < 3 || words.isEmpty() || letters.toFloat() / clean.length < 0.45f) {
            status = "Kutuda yazı bulunamadı"
            pendingText = ""
            busy = false
            return
        }

        // 1) Bir önceki çevrilen cümleyle neredeyse aynıysa tekrar çevirme ve sesi kesme
        if (similar(words, lastWords) || isSubsequenceChange(clean, lastCleanText)) {
            busy = false
            return
        }

        // 2) Oyunlarda daktilo (harf harf) yazım bitene kadar bekle:
        // Eğer yeni okunan metin bir önceki okumanın uzamış haliyse, yazının tamamlanmasını bekle
        if (pendingText.isNotEmpty() && clean.startsWith(pendingText.take(pendingText.length - 2)) && clean.length > pendingText.length + 2) {
            pendingText = clean
            status = "Altyazı tamamlanıyor…"
            busy = false
            return
        }
        pendingText = clean

        lastWords = words
        lastCleanText = clean
        original = clean
        status = "Çevriliyor…"

        // Önce ücretsiz ve yüksek doğruluklu Google GTX Nöral çeviriyi dene, internet yoksa ML Kit'e düş
        netExecutor.execute {
            val onlineTr = fetchOnlineTranslation(clean, lang)
            if (!onlineTr.isNullOrBlank()) {
                mainHandler.post {
                    applyTranslation(clean, onlineTr)
                    busy = false
                }
            } else {
                translator.translate(clean)
                    .addOnSuccessListener { raw2 ->
                        applyTranslation(clean, raw2)
                        busy = false
                    }
                    .addOnFailureListener { busy = false }
            }
        }
    }

    private fun applyTranslation(cleanOrig: String, rawTr: String) {
        val tr = rawTr.replace(Regex("\\s+"), " ").trim()
        if (tr.isEmpty()) return
        translated = tr
        status = "Çalışıyor"
        if (keepHistory) addHistory(cleanOrig, tr)
        if (speak) speakText(tr)
    }

    /** Ücretsiz, API anahtarı gerektirmeyen bağlamsal nöral çeviri */
    private fun fetchOnlineTranslation(text: String, sourceLang: String): String? {
        return try {
            val q = URLEncoder.encode(text, "UTF-8")
            val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=tr&dt=t&q=$q")
            val c = url.openConnection() as HttpURLConnection
            c.requestMethod = "GET"
            c.connectTimeout = 3500
            c.readTimeout = 4500
            c.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (c.responseCode !in 200..299) return null
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val outer = JSONArray(body).getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until outer.length()) {
                val seg = outer.getJSONArray(i)
                if (!seg.isNull(0)) sb.append(seg.getString(0))
            }
            sb.toString().trim()
        } catch (e: Exception) {
            null
        }
    }

    private fun isSubsequenceChange(a: String, b: String): Boolean {
        if (b.isEmpty()) return false
        val na = a.lowercase().filter { it.isLetterOrDigit() }
        val nb = b.lowercase().filter { it.isLetterOrDigit() }
        if (na == nb) return true
        val diff = kotlin.math.abs(na.length - nb.length)
        return diff <= 3 && (na.startsWith(nb.take(nb.length - 2)) || nb.startsWith(na.take(na.length - 2)))
    }

    private fun similar(a: Set<String>, b: Set<String>): Boolean {
        if (b.isEmpty()) return false
        val inter = a.intersect(b).size.toFloat()
        val minSize = minOf(a.size, b.size).toFloat().coerceAtLeast(1f)
        return (inter / a.union(b).size >= 0.60f) || (inter / minSize >= 0.80f)
    }

    fun close() {
        recognizer.close()
        translator.close()
        tts?.stop()
        tts?.shutdown()
        gemini.release()
        azure.release()
        supertonic.release()
        netExecutor.shutdownNow()
    }
}
