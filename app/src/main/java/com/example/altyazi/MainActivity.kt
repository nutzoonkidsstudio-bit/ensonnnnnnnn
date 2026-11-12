package com.example.altyazi

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface as ViewSurface
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlin.math.abs

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3DDC97),
    onPrimary = Color(0xFF00382A),
    background = Color(0xFF0E1117),
    onBackground = Color(0xFFE6E9EF),
    surface = Color(0xFF171C26),
    onSurface = Color(0xFFE6E9EF),
    surfaceVariant = Color(0xFF1F2633),
    onSurfaceVariant = Color(0xFFA9B2C3),
    secondaryContainer = Color(0xFF243044),
    onSecondaryContainer = Color(0xFFE6E9EF)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00855A),
    onPrimary = Color.White,
    background = Color(0xFFF3F5F9),
    onBackground = Color(0xFF10141C),
    surface = Color.White,
    onSurface = Color(0xFF10141C),
    surfaceVariant = Color(0xFFE6EAF1),
    onSurfaceVariant = Color(0xFF4A5568),
    secondaryContainer = Color(0xFFD3F0E3),
    onSecondaryContainer = Color(0xFF10141C)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val engine = remember { CaptionEngine(applicationContext) }
            DisposableEffect(Unit) { onDispose { engine.close() } }

            val dark = when (engine.themeMode) {
                1 -> true
                2 -> false
                else -> isSystemInDarkTheme()
            }
            MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Root(engine)
                }
            }
        }
    }
}

enum class Screen { SPLASH, HOME, TRANSLATE, SETTINGS, HISTORY, HOWTO }

@Composable
fun Root(engine: CaptionEngine) {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf(Screen.SPLASH) }
    var permissionDenied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1600)
        if (screen == Screen.SPLASH) screen = Screen.HOME
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        if (ok) {
            permissionDenied = false
            screen = Screen.TRANSLATE
        } else {
            permissionDenied = true
        }
    }

    BackHandler(enabled = screen != Screen.HOME && screen != Screen.SPLASH) {
        screen = Screen.HOME
    }

    when (screen) {
        Screen.SPLASH -> SplashScreen()
        Screen.HOME -> HomeScreen(
            engine = engine,
            permissionDenied = permissionDenied,
            onStart = {
                val granted = ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) screen = Screen.TRANSLATE
                else launcher.launch(Manifest.permission.CAMERA)
            },
            onSettings = { screen = Screen.SETTINGS },
            onHistory = { screen = Screen.HISTORY },
            onHowTo = { screen = Screen.HOWTO }
        )
        Screen.TRANSLATE -> TranslateScreen(engine) { screen = Screen.HOME }
        Screen.SETTINGS -> SettingsScreen(engine) { screen = Screen.HOME }
        Screen.HISTORY -> HistoryScreen(engine) { screen = Screen.HOME }
        Screen.HOWTO -> HowToScreen { screen = Screen.HOME }
    }
}

// ------------------------------------------------------------ ORTAK PARÇALAR

@Composable
fun TopBar(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = cs.primary)
            content()
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label)
    }
}

@Composable
fun StepRow(number: String, text: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(cs.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = cs.onPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = cs.onSurface)
    }
}

// ------------------------------------------------------------- AÇILIŞ EKRANI

@Composable
fun SplashScreen() {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (dark) listOf(Color(0xFF000000), Color(0xFF0A0F1C))
                    else listOf(Color(0xFFFFFFFF), Color(0xFFE6ECF5))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(34.dp))
                    .background(cs.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("A文", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = cs.onPrimary)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Altyazı Çeviri",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Canlı oyun altyazı çevirisi",
                fontSize = 17.sp,
                color = cs.onSurfaceVariant
            )
        }
    }
}

// ----------------------------------------------------------------- ORTAK MENÜ

@Composable
fun SectionHeader(text: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(cs.onSurface.copy(alpha = 0.12f))
        )
    }
}

@Composable
fun CameraGlyph(color: Color) {
    Canvas(Modifier.size(30.dp)) {
        val sw = 3.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(sw / 2, size.height * 0.22f),
            size = GeoSize(size.width - sw, size.height * 0.62f),
            cornerRadius = CornerRadius(8.dp.toPx()),
            style = Stroke(sw)
        )
        drawCircle(
            color = color,
            radius = size.width * 0.18f,
            center = Offset(size.width / 2, size.height * 0.53f),
            style = Stroke(sw)
        )
    }
}

@Composable
fun MenuCard(
    title: String,
    subtitle: String?,
    accent: Color,
    big: Boolean,
    onClick: () -> Unit,
    trailing: String? = null,
    icon: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (big) 24.dp else 18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surface)
            .border(1.dp, accent.copy(alpha = if (big) 0.55f else 0f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (big) 20.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(if (big) 64.dp else 44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = if (big) 20.sp else 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = cs.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------- ANA EKRAN

@Composable
fun HomeScreen(
    engine: CaptionEngine,
    permissionDenied: Boolean,
    onStart: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onHowTo: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(cs.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("A文", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = cs.onPrimary)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    "Altyazı Çeviri",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onBackground
                )
                Text(
                    "Oyun ve dizi altyazılarını Türkçe anla.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
        }

        SectionHeader("Telefonda çevir")
        MenuCard(
            title = "Kamera ile kullan",
            subtitle = "Telefonu ekrana doğrult, altyazı kutusunu seç. Konsol, bilgisayar, film ve dizilerde çalışır. Çeviri telefonunda görünür.",
            accent = cs.primary,
            big = true,
            onClick = onStart
        ) { CameraGlyph(cs.primary) }

        if (permissionDenied) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Kamera izni verilmedi. Telefon ayarlarından uygulamaya kamera izni ver.",
                color = cs.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        SectionHeader("Yardım ve ayarlar")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuCard(
                title = "Nasıl kullanılır?",
                subtitle = null,
                accent = cs.onSurfaceVariant,
                big = false,
                onClick = onHowTo
            ) { Icon(Icons.Default.Info, contentDescription = null, tint = cs.onSurface) }
            MenuCard(
                title = "Geçmiş",
                subtitle = null,
                accent = cs.onSurfaceVariant,
                big = false,
                onClick = onHistory,
                trailing = engine.history.size.toString()
            ) { Icon(Icons.Default.DateRange, contentDescription = null, tint = cs.onSurface) }
            MenuCard(
                title = "Ayarlar",
                subtitle = null,
                accent = cs.onSurfaceVariant,
                big = false,
                onClick = onSettings,
                trailing = engine.languageName + " → Türkçe"
            ) { Icon(Icons.Default.Settings, contentDescription = null, tint = cs.onSurface) }
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            engine.modelError -> cs.error
                            engine.modelReady -> cs.primary
                            else -> Color(0xFFFFB74D)
                        }
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    engine.modelError -> "Çeviri modeli indirilemedi, internet bağlantını kontrol et"
                    engine.modelReady -> "Çeviri modeli hazır, internet olmadan çalışır"
                    else -> "Çeviri modeli indiriliyor… (ilk seferde internet gerekir)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (engine.modelError) {
                TextButton(onClick = { engine.retryModel() }) { Text("Tekrar dene") }
            }
        }
    }
}

// ---------------------------------------------------------- NASIL KULLANILIR?

@Composable
fun HowToScreen(onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopBar("Nasıl kullanılır?", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(
                "Çeviriyi başlat" to "Ana ekranda Kamera ile kullan butonuna dokun. Ekran yatay konuma döner.",
                "Telefonu ekrana doğrult" to "TV ya da monitördeki altyazıyı kameraya al. Altyazı net ve büyük görünmeli.",
                "Altyazı kutusunu ayarla" to "Yeşil kutuyu parmağınla sürükle, sağ alt köşesinden büyüt. Yalnızca kutunun içi okunur ve çevrilir.",
                "Oku ya da dinle" to "Çeviri sağda ve kameranın üstünde görünür. Sesi Ses düğmesiyle açıp kapatabilirsin."
            ).forEachIndexed { i, (title, text) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = cs.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(cs.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${i + 1}", color = cs.onPrimary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(title, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                            Spacer(Modifier.height(4.dp))
                            Text(text, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }

            SectionCard("İpuçları") {
                Text("• Ortam çok karanlıksa Fener düğmesini dene.", color = cs.onSurfaceVariant)
                Text("• Uzaktan okuyamıyorsa Yakınlaştırma kaydırıcısını kullan.", color = cs.onSurfaceVariant)
                Text("• Oyundaki altyazı boyutunu büyük ve kontrastlı yap.", color = cs.onSurfaceVariant)
                Text("• Altyazı dilini Ayarlar bölümünden seç.", color = cs.onSurfaceVariant)
            }
        }
    }
}

// ------------------------------------------------------------ ÇEVİRİ EKRANI

private fun findActivity(ctx: Context): Activity? {
    var c = ctx
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Kamera görüntüsünün üstünde yüzen koyu paneller için ortak kap. */
@Composable
fun DarkPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit
) {
    MaterialTheme(colorScheme = DarkColors) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = Color(0xEB10141A),
            contentColor = Color.White,
            content = content
        )
    }
}

@Composable
fun RoundBtn(onClick: () -> Unit, size: Dp = 48.dp, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0x99000000))
            .border(1.dp, Color(0x33FFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun TogglePill(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) Color(0xFF3DDC97) else Color(0xFF2A3342))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (on) Color(0xFF00382A) else Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun PauseGlyph(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width * 0.28f
        drawRoundRect(color, Offset(size.width * 0.12f, 0f), GeoSize(w, size.height), CornerRadius(4f, 4f))
        drawRoundRect(color, Offset(size.width * 0.60f, 0f), GeoSize(w, size.height), CornerRadius(4f, 4f))
    }
}

@Composable
fun SlidersGlyph(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val sw = 2.5.dp.toPx()
        for ((i, f) in listOf(0.2f, 0.5f, 0.8f).withIndex()) {
            val y = size.height * f
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = sw)
            val cx = size.width * (if (i == 1) 0.7f else 0.3f)
            drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(cx, y))
        }
    }
}

/** Bu ekran yatay çalışır: tam ekran kamera, üstünde yüzen kontroller. */
@Composable
fun TranslateScreen(engine: CaptionEngine, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hostView = LocalView.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val executor = remember { Executors.newSingleThreadExecutor() }
    val providerHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    val cameraHolder = remember { arrayOfNulls<Camera>(1) }
    val analysisHolder = remember { arrayOfNulls<ImageAnalysis>(1) }
    var torchOn by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(0f) }
    var showSettings by remember { mutableStateOf(false) }
    var showQuick by remember { mutableStateOf(false) }
    var showZoom by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val act = findActivity(ctx)
        act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        engine.running = true

        // Telefon ters yatay çevrilirse okunan görüntü de doğru döndürülsün
        val orient = object : OrientationEventListener(ctx) {
            override fun onOrientationChanged(o: Int) {
                if (o == ORIENTATION_UNKNOWN) return
                val rot = when (o) {
                    in 45..134 -> ViewSurface.ROTATION_270
                    in 135..224 -> ViewSurface.ROTATION_180
                    in 225..314 -> ViewSurface.ROTATION_90
                    else -> ViewSurface.ROTATION_0
                }
                analysisHolder[0]?.targetRotation = rot
            }
        }
        orient.enable()

        onDispose {
            orient.disable()
            engine.running = false
            providerHolder[0]?.unbindAll()
            executor.shutdown()
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    DisposableEffect(engine.keepAwake) {
        hostView.keepScreenOn = engine.keepAwake
        onDispose { hostView.keepScreenOn = false }
    }

    BackHandler(enabled = showSettings || showQuick || showZoom) {
        showSettings = false
        showQuick = false
        showZoom = false
    }

    if (!landscape) {
        // Ekran yatay konuma geçene kadar kamerayı açma
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Yatay moda geçiliyor…", color = cs.onSurfaceVariant)
        }
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // ---------- Kamera katmanı (16:9) ----------
            Box(
                Modifier
                    .align(Alignment.Center)
                    .aspectRatio(16f / 9f)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        val view = PreviewView(c).apply {
                            scaleType = PreviewView.ScaleType.FIT_CENTER
                        }
                        val future = ProcessCameraProvider.getInstance(c)
                        future.addListener({
                            val provider = future.get()
                            providerHolder[0] = provider

                            val preview = Preview.Builder()
                                .setResolutionSelector(
                                    ResolutionSelector.Builder()
                                        .setAspectRatioStrategy(
                                            AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                                        )
                                        .build()
                                )
                                .build()
                                .also { it.setSurfaceProvider(view.surfaceProvider) }

                            // Uzaktaki küçük yazıyı okuyabilmek için yüksek çözünürlük
                            val analysis = ImageAnalysis.Builder()
                                .setResolutionSelector(
                                    ResolutionSelector.Builder()
                                        .setAspectRatioStrategy(
                                            AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                                        )
                                        .setResolutionStrategy(
                                            ResolutionStrategy(
                                                Size(1920, 1080),
                                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                            )
                                        )
                                        .build()
                                )
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { it.setAnalyzer(executor, engine::analyze) }
                            analysisHolder[0] = analysis

                            provider.unbindAll()
                            val cam = provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis
                            )
                            cameraHolder[0] = cam
                            hasTorch = cam.cameraInfo.hasFlashUnit()
                        }, ContextCompat.getMainExecutor(c))
                        view
                    }
                )

                // Taşınabilir ve köşesinden boyutlandırılabilir altyazı kutusu
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            var mode = 1 // 1 taşı, 2 boyutlandır
                            detectDragGestures(
                                onDragStart = { o ->
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()
                                    val hx = (engine.boxLeft + engine.boxWidth) * w
                                    val hy = (engine.boxTop + engine.boxHeight) * h
                                    mode = if (abs(o.x - hx) < 90f && abs(o.y - hy) < 90f) 2 else 1
                                },
                                onDragEnd = { engine.persist() },
                                onDragCancel = { engine.persist() },
                                onDrag = { change, drag ->
                                    change.consume()
                                    val dx = drag.x / size.width
                                    val dy = drag.y / size.height
                                    if (mode == 2) engine.resizeBox(dx, dy) else engine.moveBox(dx, dy)
                                }
                            )
                        }
                ) {
                    val x = engine.boxLeft * size.width
                    val y = engine.boxTop * size.height
                    val w = engine.boxWidth * size.width
                    val h = engine.boxHeight * size.height
                    // Kutunun dışını karart (yalnızca ekranda görünür, yazı okuma etkilenmez)
                    val dim = Color(0xCC000000)
                    val bottomY = (y + h).coerceAtMost(size.height)
                    val rightX = (x + w).coerceAtMost(size.width)
                    drawRect(dim, Offset(0f, 0f), GeoSize(size.width, y))
                    drawRect(dim, Offset(0f, bottomY), GeoSize(size.width, size.height - bottomY))
                    drawRect(dim, Offset(0f, y), GeoSize(x, bottomY - y))
                    drawRect(dim, Offset(rightX, y), GeoSize(size.width - rightX, bottomY - y))
                    drawRect(
                        color = Color(0x1F00E676),
                        topLeft = Offset(x, y),
                        size = GeoSize(w, h)
                    )
                    drawRoundRect(
                        color = Color(0xFF3DDC97),
                        topLeft = Offset(x, y),
                        size = GeoSize(w, h),
                        cornerRadius = CornerRadius(12f, 12f),
                        style = Stroke(
                            width = 4f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 12f))
                        )
                    )
                    drawCircle(
                        color = Color(0xFF3DDC97),
                        radius = 22f,
                        center = Offset(x + w, y + h)
                    )
                }
            }

            // ---------- Kontrol katmanı ----------
            Box(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(12.dp)
            ) {
                // Üst sıra: geri + durum | çeviri alanı | duraklat
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.width(150.dp)) {
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(Color(0x99000000))
                                .border(1.5.dp, cs.primary, CircleShape)
                                .clickable(onClick = onBack)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Geri", color = cs.primary, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (engine.running) cs.primary else Color(0xFF9AA3B2))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (engine.running) "Çeviri açık" else "Duraklatıldı",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            engine.statusText,
                            color = Color(0xFFD0D6E0),
                            fontSize = 12.sp
                        )
                    }

                    if (engine.overlay) {
                        DarkPanel(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 72.dp, max = 150.dp),
                            RoundedCornerShape(26.dp)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (engine.translated.isEmpty()) "ÇEVİRİ ALANI" else engine.translated,
                                    fontSize = engine.textSize.sp,
                                    lineHeight = (engine.textSize * 1.2f).sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (engine.translated.isEmpty()) Color(0x66FFFFFF) else Color.White
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    Column(Modifier.width(150.dp), horizontalAlignment = Alignment.End) {
                        if (engine.running) {
                            RoundBtn(onClick = { engine.running = false }, size = 52.dp) {
                                PauseGlyph(Color.White)
                            }
                        } else {
                            Row(
                                Modifier
                                    .clip(CircleShape)
                                    .background(Color(0x99000000))
                                    .clickable { engine.running = true }
                                    .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(cs.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = cs.onPrimary)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("Başlat", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // İpucu
                if (!engine.tipSeen && !showSettings) {
                    DarkPanel(
                        Modifier
                            .align(Alignment.Center)
                            .offset(y = 36.dp)
                            .widthIn(max = 420.dp),
                        RoundedCornerShape(24.dp)
                    ) {
                        Row(
                            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = cs.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Altyazıyı yeşil kutuya sabitle. Kutuyu sürükle ya da telefonu oynat.",
                                modifier = Modifier.weight(1f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(onClick = {
                                engine.tipSeen = true
                                engine.persist()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                            }
                        }
                    }
                }

                // Alt sol: hızlı ayarlar + ayarlar
                Row(
                    Modifier.align(Alignment.BottomStart),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RoundBtn(onClick = {
                        showQuick = !showQuick
                        showZoom = false
                    }) { SlidersGlyph(Color.White) }
                    RoundBtn(onClick = {
                        showSettings = true
                        showQuick = false
                        showZoom = false
                    }) { Icon(Icons.Default.Settings, contentDescription = "Ayarlar", tint = Color.White) }
                }

                // Alt sağ: sorun mu var? + yakınlaştır
                Row(
                    Modifier.align(Alignment.BottomEnd),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(Color(0xFFD9534F))
                            .clickable { showHelp = true }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Sorun mu var?", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    RoundBtn(onClick = {
                        showZoom = !showZoom
                        showQuick = false
                    }) { Icon(Icons.Default.Search, contentDescription = "Yakınlaştır", tint = Color.White) }
                }

                // Hızlı ayarlar paneli
                if (showQuick) {
                    DarkPanel(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 60.dp)
                            .width(300.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Hızlı ayarlar", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TogglePill("Sesli çeviri", engine.speak, Modifier.weight(1f)) {
                                    engine.speak = !engine.speak
                                    engine.persist()
                                }
                                TogglePill(
                                    if (torchOn) "Fener: Açık" else "Fener",
                                    torchOn,
                                    Modifier.weight(1f)
                                ) {
                                    if (hasTorch) {
                                        torchOn = !torchOn
                                        cameraHolder[0]?.cameraControl?.enableTorch(torchOn)
                                    }
                                }
                            }
                            Text("Konuşma hızı: ${"%.1f".format(engine.speechRate)}x", fontSize = 14.sp)
                            Slider(
                                value = engine.speechRate,
                                onValueChange = { engine.speechRate = it },
                                onValueChangeFinished = { engine.persist() },
                                valueRange = 0.6f..1.6f
                            )
                            TextButton(onClick = { engine.resetBox() }) { Text("Kutuyu sıfırla") }
                        }
                    }
                }

                // Yakınlaştırma paneli
                if (showZoom) {
                    DarkPanel(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 60.dp)
                            .width(260.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Yakınlaştırma", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Slider(
                                value = zoom,
                                onValueChange = {
                                    zoom = it
                                    cameraHolder[0]?.cameraControl?.setLinearZoom(it)
                                }
                            )
                        }
                    }
                }

                // Ayarlar paneli (sağdan)
                if (showSettings) {
                    DarkPanel(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .fillMaxWidth(0.5f)
                            .widthIn(max = 420.dp)
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Ayarlar",
                                    modifier = Modifier.weight(1f),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { showSettings = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                                }
                            }

                            SectionCard("Çeviri") {
                                Text(
                                    "Çeviri telefonunda yapılır, internet gerekmez.",
                                    fontSize = 13.sp,
                                    color = Color(0xFFA9B2C3)
                                )
                                Text("Çevrilecek dil", fontWeight = FontWeight.SemiBold)
                                CaptionEngine.LANGUAGES.forEach { (code, name) ->
                                    RadioRow(name, engine.lang == code) { engine.changeLanguage(code) }
                                }
                                Text(
                                    when {
                                        engine.modelError -> "Dil paketi indirilemedi."
                                        engine.modelReady -> "Dil paketi: Hazır."
                                        else -> "Dil paketi indiriliyor…"
                                    },
                                    fontSize = 13.sp,
                                    color = Color(0xFFA9B2C3)
                                )
                                if (engine.modelError) {
                                    TextButton(onClick = { engine.retryModel() }) { Text("Tekrar dene") }
                                }
                            }

                            SectionCard("Ses") {
                                SwitchRow("Sesli çeviri", engine.speak) {
                                    engine.speak = it
                                    engine.persist()
                                }
                                SwitchRow("Yeni satırda önceki konuşmayı kes", engine.flushSpeech) {
                                    engine.flushSpeech = it
                                    engine.persist()
                                }
                                Text("Konuşma hızı: ${"%.1f".format(engine.speechRate)}x")
                                Slider(
                                    value = engine.speechRate,
                                    onValueChange = { engine.speechRate = it },
                                    onValueChangeFinished = { engine.persist() },
                                    valueRange = 0.6f..1.6f
                                )
                                Text("Ses tonu: ${"%.1f".format(engine.pitch)}")
                                Slider(
                                    value = engine.pitch,
                                    onValueChange = { engine.pitch = it },
                                    onValueChangeFinished = { engine.persist() },
                                    valueRange = 0.6f..1.6f
                                )
                                if (engine.ttsMissing) {
                                    Text(
                                        "Türkçe ses bulunamadı. Telefon ayarlarında Metin okuma (Google) bölümünden Türkçe sesi indir.",
                                        color = Color(0xFFFF8A80),
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            VoiceEngineSection(engine)
            SectionCard("Görünüm") {
                                SwitchRow("Çeviri alanını göster", engine.overlay) {
                                    engine.overlay = it
                                    engine.persist()
                                }
                                Text("Yazı boyutu")
                                Slider(
                                    value = engine.textSize,
                                    onValueChange = { engine.textSize = it },
                                    onValueChangeFinished = { engine.persist() },
                                    valueRange = 16f..40f
                                )
                            }

                            SectionCard("Okuma") {
                                Text("Tarama sıklığı: ${"%.1f".format(engine.scanSeconds)} sn")
                                Slider(
                                    value = engine.scanSeconds,
                                    onValueChange = { engine.scanSeconds = it },
                                    onValueChangeFinished = { engine.persist() },
                                    valueRange = 0.3f..2.0f
                                )
                                SwitchRow("Çevirileri geçmişe kaydet", engine.keepHistory) {
                                    engine.keepHistory = it
                                    engine.persist()
                                }
                                SwitchRow("Ekranı açık tut", engine.keepAwake) {
                                    engine.keepAwake = it
                                    engine.persist()
                                }
                                Button(onClick = { engine.resetBox() }) { Text("Kutuyu sıfırla") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Sorun mu var?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• Yeşil kutuyu altyazının tam üstüne getir.")
                    Text("• Oyundaki altyazı boyutunu büyük yap.")
                    Text("• Telefonu sabit tut, gerekirse yakınlaştır.")
                    Text("• Ekran parlamasından ve yansımalardan kaçın.")
                    Text("• Ayarlar bölümünden dil paketinin hazır olduğunu kontrol et.")
                }
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Tamam") } },
            dismissButton = {
                TextButton(onClick = {
                    engine.resetBox()
                    showHelp = false
                }) { Text("Kutuyu sıfırla") }
            }
        )
    }
}

// ------------------------------------------------------------------- GEÇMİŞ

@Composable
fun HistoryScreen(engine: CaptionEngine, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopBar("Geçmiş", onBack) {
            TextButton(
                onClick = {
                    val text = engine.history.joinToString("\n\n") { it.original + "\n→ " + it.translated }
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    ctx.startActivity(Intent.createChooser(i, "Paylaş"))
                },
                enabled = engine.history.isNotEmpty()
            ) { Text("Paylaş") }
            TextButton(
                onClick = { engine.clearHistory() },
                enabled = engine.history.isNotEmpty()
            ) { Text("Temizle") }
        }

        if (engine.history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Henüz çeviri yok", color = cs.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(engine.history) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cs.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboard.setText(AnnotatedString(item.translated))
                                Toast.makeText(ctx, "Kopyalandı", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                fmt.format(Date(item.time)),
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                item.translated,
                                style = MaterialTheme.typography.titleMedium,
                                color = cs.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                item.original,
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- SES MOTORU

@Composable
fun VoiceEngineSection(engine: CaptionEngine) {
    val cs = MaterialTheme.colorScheme
    SectionCard("Ses motoru") {
        RadioRow("Supertonic yapay zekâ sesi (çevrimdışı, ücretsiz, kotasız)", engine.voiceEngine == 3) {
            engine.voiceEngine = 3
            engine.persist()
        }
        RadioRow("Azure doğal ses (internet ve anahtar gerekir)", engine.voiceEngine == 2) {
            engine.voiceEngine = 2
            engine.persist()
        }
        RadioRow("Telefon sesi (ücretsiz, internetsiz)", engine.voiceEngine == 0) {
            engine.voiceEngine = 0
            engine.persist()
        }
        RadioRow("Gemini sesi (daha doğal, internet gerekir)", engine.voiceEngine == 1) {
            engine.voiceEngine = 1
            engine.persist()
        }
        if (engine.voiceEngine == 3) {
            Text(
                "Ses cihazda çalışır; internet, anahtar ve kota gerekmez. Modelin bir kez indirilmesi gerekir (yaklaşık ${SupertonicVoice.APPROX_SIZE_MB} MB, Wi-Fi önerilir).",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
            if (!engine.stReady) {
                Button(onClick = { engine.downloadSupertonic() }, enabled = !engine.stBusy) {
                    Text(if (engine.stBusy) "İndiriliyor…" else "Ses modelini indir")
                }
            }
            engine.stStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Text("Ses", fontWeight = FontWeight.SemiBold)
            SupertonicVoice.VOICES.forEach { (id, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        RadioRow(label, engine.stVoice == id) {
                            engine.stVoice = id
                            engine.persist()
                        }
                    }
                    Button(onClick = { engine.previewSupertonic(id) }, enabled = engine.stReady) {
                        Text("Dene")
                    }
                }
            }
            engine.stError?.let {
                Text(it, color = cs.error, style = MaterialTheme.typography.bodySmall)
            }
            if (engine.stReady) {
                TextButton(onClick = { engine.deleteSupertonic() }) { Text("Ses modelini sil") }
            }
        }
        if (engine.voiceEngine == 2) {
            Text(
                "Azure Speech ücretsiz katmanı ayda 500.000 karaktere kadar ücretsizdir. portal.azure.com'da \"Speech\" kaynağı oluşturup (fiyat: Free F0) Anahtarlar ve Konum sayfasındaki KEY 1 ve Konum/Bölge değerini aşağıya gir. Anahtar yalnızca bu cihazda saklanır.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
            OutlinedTextField(
                value = engine.azureKey,
                onValueChange = {
                    engine.azureKey = it
                    engine.persist()
                },
                label = { Text("Azure anahtarı (KEY 1)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = engine.azureRegion,
                onValueChange = {
                    engine.azureRegion = it
                    engine.persist()
                },
                label = { Text("Bölge (ör. westeurope)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Ses", fontWeight = FontWeight.SemiBold)
            AzureVoice.VOICES.forEach { (id, label) ->
                RadioRow(label, engine.azureVoiceName == id) {
                    engine.azureVoiceName = id
                    engine.persist()
                }
            }
            Button(onClick = {
                engine.azureError = null
                engine.speakText("Merhaba, bu Azure sesiyle bir deneme.")
            }) {
                Text("Sesi dene")
            }
            engine.azureError?.let {
                Text(it, color = cs.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (engine.voiceEngine == 1) {
            Text(
                "Google AI Studio'dan (aistudio.google.com) bir API anahtarı alıp aşağıya yapıştır. Kullanıma göre ücret çıkabilir. Anahtar yalnızca bu telefonda saklanır.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
            OutlinedTextField(
                value = engine.geminiKey,
                onValueChange = {
                    engine.geminiKey = it
                    engine.persist()
                },
                label = { Text("API anahtarı") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Text("Ses", fontWeight = FontWeight.SemiBold)
            GeminiVoice.VOICES.forEach { v ->
                RadioRow(v, engine.geminiVoice == v) {
                    engine.geminiVoice = v
                    engine.persist()
                }
            }
            OutlinedTextField(
                value = engine.geminiModel,
                onValueChange = {
                    engine.geminiModel = it
                    engine.persist()
                },
                label = { Text("Model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { engine.speakText("Merhaba, bu Gemini sesiyle bir deneme.") }) {
                Text("Sesi dene")
            }
            engine.geminiError?.let {
                Text(it, color = cs.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ------------------------------------------------------------------ AYARLAR

@Composable
fun SettingsScreen(engine: CaptionEngine, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopBar("Ayarlar", onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard("Seslendirme") {
                SwitchRow("Çeviriyi sesli oku", engine.speak) {
                    engine.speak = it
                    engine.persist()
                }
                SwitchRow("Yeni satırda önceki konuşmayı kes", engine.flushSpeech) {
                    engine.flushSpeech = it
                    engine.persist()
                }
                Text(
                    "Konuşma hızı: ${"%.1f".format(engine.speechRate)}x",
                    color = cs.onSurfaceVariant
                )
                Slider(
                    value = engine.speechRate,
                    onValueChange = { engine.speechRate = it },
                    onValueChangeFinished = { engine.persist() },
                    valueRange = 0.6f..1.6f
                )
                Text(
                    "Ses tonu: ${"%.1f".format(engine.pitch)}",
                    color = cs.onSurfaceVariant
                )
                Slider(
                    value = engine.pitch,
                    onValueChange = { engine.pitch = it },
                    onValueChangeFinished = { engine.persist() },
                    valueRange = 0.6f..1.6f
                )
                if (engine.ttsMissing) {
                    Text(
                        "Türkçe ses bulunamadı. Telefon ayarlarında Metin okuma (Google) bölümünden Türkçe sesi indir.",
                        color = cs.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            VoiceEngineSection(engine)
            SectionCard("Görünüm") {
                Text(
                    "Örnek çeviri yazısı",
                    fontSize = engine.textSize.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = engine.textSize,
                    onValueChange = { engine.textSize = it },
                    onValueChangeFinished = { engine.persist() },
                    valueRange = 16f..40f
                )
                SwitchRow("Çeviriyi kameranın üstünde göster", engine.overlay) {
                    engine.overlay = it
                    engine.persist()
                }
                Text("Tema", color = cs.onSurfaceVariant)
                RadioRow("Telefona uy", engine.themeMode == 0) {
                    engine.themeMode = 0
                    engine.persist()
                }
                RadioRow("Koyu", engine.themeMode == 1) {
                    engine.themeMode = 1
                    engine.persist()
                }
                RadioRow("Açık", engine.themeMode == 2) {
                    engine.themeMode = 2
                    engine.persist()
                }
            }

            SectionCard("Okuma") {
                Text(
                    "Tarama sıklığı: ${"%.1f".format(engine.scanSeconds)} sn",
                    color = cs.onSurfaceVariant
                )
                Slider(
                    value = engine.scanSeconds,
                    onValueChange = { engine.scanSeconds = it },
                    onValueChangeFinished = { engine.persist() },
                    valueRange = 0.3f..2.0f
                )
                Text(
                    "Düşük değer daha hızlı okur ama pili daha çok harcar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                SwitchRow("Çeviri sırasında ekranı açık tut", engine.keepAwake) {
                    engine.keepAwake = it
                    engine.persist()
                }
                SwitchRow("Çevirileri geçmişe kaydet", engine.keepHistory) {
                    engine.keepHistory = it
                    engine.persist()
                }
            }

            SectionCard("Altyazının dili") {
                CaptionEngine.LANGUAGES.forEach { (code, name) ->
                    RadioRow(name, engine.lang == code) { engine.changeLanguage(code) }
                }
                Text(
                    "Dil değişince çeviri modeli bir kez indirilir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }

            Text(
                "Altyazı Çeviri · Sürüm 2.0",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}
