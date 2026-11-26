@file:Suppress("SpellCheckingInspection")

package com.alisamilaytekin.spectras

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.SurfaceTexture
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.*
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.TonemapCurve
import android.media.ExifInterface
import android.media.ImageReader
import android.media.MediaActionSound
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.util.Range
import android.util.Rational
import android.util.Size
import android.view.KeyEvent
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.graphics.get
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

internal object IrPalette {
    // Optical Precision Instrument palette (OLED obsidian + calibrated graphite + Leica amber)
    val VoidBlack = Color(0xFF000000)
    val HudGlass = Color(0xFF0C0F14)
    val HudCard = Color(0xFF151922)
    val HudChip = Color(0xFF1C2230)
    val HudScrim = Color(0xC207090D)
    val SurfaceElevated = Color(0xFF161B24)
    val SurfaceSubtle = Color(0x14FFFFFF)
    val IrCoral = Color(0xFFEF4444)
    val IrAmber = Color(0xFFF5A623)
    val IrCyan = Color(0xFF38BDF8)
    val IrEmerald = Color(0xFF22C55E)
    val IrTeal = Color(0xFF2DD4BF)
    val IrMagenta = Color(0xFFE84393)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFFB4C0D0)
    val TextTertiary = Color(0xFF8391A6)
    val BorderSubtle = Color(0x22FFFFFF)
    val BorderBright = Color(0x47FFFFFF)
}

internal enum class AppLanguage(val code: String, val nativeName: String, val shortCode: String) {
    EN("en", "English", "EN"),
    TR("tr", "Türkçe", "TR"),
    ES("es", "Español", "ES"),
    PT("pt", "Português", "PT"),
    HI("hi", "हिन्दी", "HI"),
    ID("in", "Bahasa Indonesia", "ID"),
    AR("ar", "العربية", "AR"),
    FR("fr", "Français", "FR"),
    DE("de", "Deutsch", "DE"),
    RU("ru", "Русский", "RU"),
    JA("ja", "日本語", "JA"),
    KO("ko", "한국어", "KO"),
    ZH("zh-CN", "简体中文", "中文");

    fun toLocale(): Locale = when (code) {
        "zh-CN" -> Locale("zh", "CN")
        "in" -> Locale("in")
        else -> Locale(code)
    }

    companion object {
        const val PREF_KEY = "app_language_code"
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull {
                it.code.equals(code, ignoreCase = true) || (code.equals("id", ignoreCase = true) && it == ID)
            } ?: EN
    }
}

internal fun getSavedAppLanguage(context: Context): AppLanguage {
    val prefs = context.getSharedPreferences("CameraPresetsPrefs", Context.MODE_PRIVATE)
    return AppLanguage.fromCode(prefs.getString(AppLanguage.PREF_KEY, AppLanguage.EN.code))
}

internal fun saveAppLanguageAndRecreate(context: Context, language: AppLanguage) {
    val prefs = context.getSharedPreferences("CameraPresetsPrefs", Context.MODE_PRIVATE)
    prefs.edit().putString(AppLanguage.PREF_KEY, language.code).commit()
    (context as? Activity)?.recreate()
}

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val lang = getSavedAppLanguage(newBase)
        val locale = lang.toLocale()
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    private var hasCameraPermission by mutableStateOf(false)
    private var incomingEditUri by mutableStateOf<Uri?>(null)
    private var incomingEditNonce by mutableIntStateOf(0)
    @Volatile private var volumeShutterAction: (() -> Unit)? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val camGranted = results[Manifest.permission.CAMERA]
                ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            hasCameraPermission = camGranted
            if (!camGranted) {
                Toast.makeText(this, getString(R.string.status_camera_permission), Toast.LENGTH_LONG).show()
            }
        }

    private fun extractImageUriFromIntent(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
            }
            Intent.ACTION_EDIT, Intent.ACTION_VIEW -> intent.data
            else -> null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractImageUriFromIntent(intent)?.let { uri ->
            incomingEditUri = uri
            incomingEditNonce++
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val action = volumeShutterAction
            if (action != null && (event == null || event.repeatCount == 0)) {
                action.invoke()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun requestCameraPermissions() {
        val missingPermissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.M..Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            hasCameraPermission = true
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        hasCameraPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        extractImageUriFromIntent(intent)?.let { uri ->
            incomingEditUri = uri
            incomingEditNonce++
        }
        hasCameraPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission || (Build.VERSION.SDK_INT in Build.VERSION_CODES.M..Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        ) {
            requestCameraPermissions()
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CameraScreen(
                    hasCameraPermission = hasCameraPermission,
                    incomingEditUri = incomingEditUri,
                    incomingEditNonce = incomingEditNonce,
                    onRegisterVolumeShutter = { cb -> volumeShutterAction = cb },
                    onRequestCameraPermission = { requestCameraPermissions() },
                    onOpenAppSettings = { openAppSettings() }
                )
            }
        }
    }
}

internal enum class AppMode { AUTO, PRO, STUDIO }

internal enum class ExperienceMode(val prefKey: String) {
    AESTHETIC("aesthetic"),
    IR_LAB("ir_lab"),
    PRO("pro");

    companion object {
        fun fromKey(key: String?): ExperienceMode =
            entries.firstOrNull { it.prefKey == key } ?: PRO
    }
}

internal enum class AestheticSubSlider { STRENGTH, BRIGHTNESS }

internal enum class FlashMode { OFF, AUTO, ON, TORCH }

// Ana kontrol çipleri: Beyaz Dengesi, Pozlama, Renk, Ölçüm
internal enum class ControlTab { BD, POZ, RENK, OLCUM }

// Beyaz dengesi alt tekil kaydırıcı seçimi (önizleme yüksekliğini korur)
internal enum class WbSubSlider { KELVIN, TINT }

// Renk çipi üst modları: Genel (Doygunluk/Kontrast), Renk Bantları (Histogram Mikseri), Pipet
internal enum class ColorPanelMode { GENEL, BANTLAR, PIPET }

// Renk çipi alt tekil kaydırıcı seçimi
internal enum class ColorSubSlider { DOYGUNLUK, KONTRAST }

// Noktasal renk pipeti ve renk bantları alt tekil kaydırıcı seçimi
internal enum class PointColorSubSlider { TON, DOYGUNLUK, PARLAKLIK }

// Lightroom / Darkroom tarzı 12 sık profesyonel renk bandı (30° uniform adım + çoklu seçim uyumlu)
internal enum class ColorBand(
    val index: Int,
    val labelRes: Int,
    val centerHue: Float,
    val swatchColor: Color
) {
    RED(0, R.string.band_red, 0f, Color(0xFFEF4444)),
    ORANGE(1, R.string.band_orange, 30f, Color(0xFFF97316)),
    YELLOW(2, R.string.band_yellow, 60f, Color(0xFFEAB308)),
    LIME(3, R.string.band_lime, 90f, Color(0xFF84CC16)),
    GREEN(4, R.string.band_green, 120f, Color(0xFF22C55E)),
    EMERALD(5, R.string.band_emerald, 150f, Color(0xFF10B981)),
    CYAN(6, R.string.band_cyan, 180f, Color(0xFF06B6D4)),
    AZURE(7, R.string.band_azure, 210f, Color(0xFF0EA5E9)),
    BLUE(8, R.string.band_blue, 240f, Color(0xFF3B82F6)),
    PURPLE(9, R.string.band_purple, 270f, Color(0xFF8B5CF6)),
    MAGENTA(10, R.string.band_magenta, 300f, Color(0xFFD946EF)),
    ROSE(11, R.string.band_rose, 330f, Color(0xFFF43F5E))
}

internal data class BandHslAdjustment(
    val deltaHue: Float = 0f,
    val deltaSat: Float = 0f,
    val deltaLum: Float = 0f
) {
    val isModified: Boolean
        get() = abs(deltaHue) > 0.5f || abs(deltaSat) > 0.01f || abs(deltaLum) > 0.01f
}

internal data class ColorBandsConfig(
    val bands: List<BandHslAdjustment> = List(ColorBand.entries.size) { BandHslAdjustment() }
) {
    val hasAnyModification: Boolean
        get() = bands.any { it.isModified }

    fun get(band: ColorBand): BandHslAdjustment = bands.getOrElse(band.index) { BandHslAdjustment() }

    fun update(band: ColorBand, transform: (BandHslAdjustment) -> BandHslAdjustment): ColorBandsConfig {
        val next = bands.toMutableList()
        if (band.index in next.indices) {
            next[band.index] = transform(next[band.index])
        }
        return ColorBandsConfig(next)
    }

    fun updateAll(targetBands: Set<ColorBand>, transform: (BandHslAdjustment) -> BandHslAdjustment): ColorBandsConfig {
        val next = bands.toMutableList()
        for (band in targetBands) {
            if (band.index in next.indices) {
                next[band.index] = transform(next[band.index])
            }
        }
        return ColorBandsConfig(next)
    }
}

internal class ProHistogramData(
    val r: FloatArray = FloatArray(48),
    val g: FloatArray = FloatArray(48),
    val b: FloatArray = FloatArray(48),
    val luma: FloatArray = FloatArray(48),
    val hueBins: FloatArray = FloatArray(48),
    val shadowClip: Boolean = false,
    val highlightClip: Boolean = false
)

// Ölçüm kutusu boyutu için tek seçimli (radio) değerler
internal enum class MeteringBoxSize(val norm: Float) {
    KUCUK(0.18f),
    ORTA(0.32f),
    BUYUK(0.55f)
}

internal enum class ExposureMeteringMode { FULL_SCREEN, BOX_SPOT }

private class CameraHolder {
    var activeCameraId: String? = null
    var device: CameraDevice? = null
    var session: CameraCaptureSession? = null
    var previewBuilder: CaptureRequest.Builder? = null
    var imageReader: ImageReader? = null
    var activeSurface: Surface? = null
    @Volatile var activeSurfaceTexture: SurfaceTexture? = null
    var isOpening: Boolean = false
    var isDisposed: Boolean = false

    @Volatile var useFrontCamera: Boolean = false
    @Volatile var hasFlash: Boolean = false
    @Volatile var flashMode: FlashMode = FlashMode.OFF
    @Volatile var availableAeModes: IntArray = intArrayOf()
    @Volatile var lastAeState: Int = CaptureResult.CONTROL_AE_STATE_INACTIVE
    @Volatile var flashPrecaptureActive: Boolean = false
    @Volatile var flashPrecaptureSawActiveState: Boolean = false
    @Volatile var flashPrecaptureStartMs: Long = 0L
    @Volatile var pendingFlashCaptureRunner: (() -> Unit)? = null
    @Volatile var pendingTorchFallbackActive: Boolean = false

    @Volatile var selectedResolution: Size? = null
    @Volatile var activePreviewSize: Size? = null
    @Volatile var activeSensorOrientation: Int = 90
    @Volatile var activeSensorArray: Rect? = null
    @Volatile var maxAeRegions: Int = 0
    @Volatile var previewFallbackLevel: Int = 0
    @Volatile var isTakingPhoto: Boolean = false
    @Volatile var isClosingDevice: Boolean = false
    var pendingReopenSurfaceTexture: SurfaceTexture? = null
    var cameraGeneration: Int = 0

    @Volatile var lastHardwareGains: RggbChannelVector = RggbChannelVector(1.5f, 1.0f, 1.0f, 1.5f)
    @Volatile var hasRealHardwareGains: Boolean = false
    @Volatile var lastAwbState: Int = CaptureResult.CONTROL_AWB_STATE_INACTIVE
    @Volatile var lastAutoColorTransform: ColorSpaceTransform? = null
    @Volatile var lastNativeTonemapCurve: TonemapCurve? = null
    @Volatile var lastTelemetryUpdateMs: Long = 0L
    @Volatile var supportedTonemapModes: IntArray = intArrayOf()
    @Volatile var availableAfModes: IntArray = intArrayOf()
    @Volatile var maxTonemapCurvePoints: Int = 33
    @Volatile var aeCompensationRange: Range<Int> = Range(0, 0)
    @Volatile var aeCompensationStep: Float = 0.5f

    // Synchronized snapshot of current user settings for async Camera2 callbacks
    @Volatile var currentAutoMode: Boolean = true
    @Volatile var currentKelvin: Float = 5500f
    @Volatile var currentTint: Float = 0f
    @Volatile var currentContrast: Float = DEFAULT_CONTRAST
    @Volatile var currentSaturation: Float = DEFAULT_SATURATION
    @Volatile var currentExposureEv: Int = 0
    @Volatile var currentMeteringMode: ExposureMeteringMode = ExposureMeteringMode.FULL_SCREEN
    @Volatile var currentExposureBoxNorm: Offset = Offset(0.5f, 0.5f)
    @Volatile var currentExposureBoxSizeNorm: Float = 0.32f
    @Volatile var currentTargetedColor: TargetedColorConfig = TargetedColorConfig()
    @Volatile var currentColorBands: ColorBandsConfig = ColorBandsConfig()
    @Volatile var currentLookGrade: LookGradeParams = LookGradeParams()
    @Volatile var currentExperienceMode: ExperienceMode = ExperienceMode.PRO
    @Volatile var currentChannelSwap: Boolean = false
    @Volatile var currentAeLock: Boolean = false

    // Mercek / yakınlaştırma ve RAW/DNG durumu
    @Volatile var zoomRange: Range<Float> = Range(1f, 1f)
    @Volatile var currentZoomRatio: Float = 1f
    @Volatile var supportsRaw: Boolean = false
    @Volatile var rawSize: Size? = null
    @Volatile var rawCaptureEnabled: Boolean = false
    var rawImageReader: ImageReader? = null
    @Volatile var lastStillCaptureResult: TotalCaptureResult? = null
}

private fun formatMegapixels(size: Size): String {
    val mp = (size.width.toLong() * size.height.toLong()) / 1_000_000.0
    return when {
        mp >= 9.95 -> "${mp.roundToInt()}MP"
        mp >= 1.0 -> "${"%.1f".format(Locale.US, mp)}MP"
        else -> "${"%.2f".format(Locale.US, mp)}MP"
    }
}

private tailrec fun gcdInt(a: Int, b: Int): Int = if (b == 0) a else gcdInt(b, a % b)

private fun formatAspectRatioTag(size: Size): String {
    if (size.height <= 0) return ""
    val r = size.width.toFloat() / size.height.toFloat()
    return when {
        abs(r - 4f / 3f) < 0.035f -> "4:3"
        abs(r - 16f / 9f) < 0.04f -> "16:9"
        abs(r - 1f) < 0.035f -> "1:1"
        abs(r - 3f / 2f) < 0.035f -> "3:2"
        abs(r - 5f / 4f) < 0.035f -> "5:4"
        abs(r - 18f / 9f) < 0.06f -> "18:9"
        abs(r - 20f / 9f) < 0.06f -> "20:9"
        abs(r - 21f / 9f) < 0.06f -> "21:9"
        else -> {
            val g = gcdInt(size.width, size.height).coerceAtLeast(1)
            val rw = size.width / g
            val rh = size.height / g
            if (rw <= 25 && rh <= 25) "$rw:$rh" else "${"%.2f".format(Locale.US, r)}:1"
        }
    }
}

private fun choosePreviewSize(
    previewSizes: Array<Size>,
    targetJpeg: Size,
    fallbackTier: Int
): Size {
    val targetRatio = targetJpeg.width.toFloat() / targetJpeg.height.coerceAtLeast(1).toFloat()
    val matching = previewSizes.filter {
        it.height > 0 && abs((it.width.toFloat() / it.height.toFloat()) - targetRatio) <= 0.03f
    }
    val candidates = matching.ifEmpty { previewSizes.toList() }

    // Allow 1920x1080 (16:9), 1440x1080 (4:3), and 1088x1088 (1:1) while staying within LIMITED bounds
    val (maxLong, maxPixels) = when (fallbackTier) {
        0 -> 1920 to (1920L * 1088L)
        1 -> 1280 to (1280L * 960L)
        else -> 800 to (800L * 600L)
    }

    return candidates
        .filter { maxOf(it.width, it.height) <= maxLong && (it.width.toLong() * it.height.toLong()) <= maxPixels }
        .maxByOrNull { it.width.toLong() * it.height.toLong() }
        ?: candidates.minByOrNull { it.width.toLong() * it.height.toLong() }
        ?: previewSizes.minByOrNull { it.width.toLong() * it.height.toLong() }
        ?: targetJpeg
}

private fun applyPreviewTransform(
    view: TextureView,
    previewSize: Size?,
    sensorOrientation: Int
) {
    if (previewSize == null || view.width <= 0 || view.height <= 0 || previewSize.height <= 0) {
        view.setTransform(null)
        return
    }
    val swapped = sensorOrientation == 90 || sensorOrientation == 270
    val bufW = (if (swapped) previewSize.height else previewSize.width).toFloat()
    val bufH = (if (swapped) previewSize.width else previewSize.height).toFloat()
    if (bufW <= 0f || bufH <= 0f) {
        view.setTransform(null)
        return
    }
    val viewRatio = view.width.toFloat() / view.height.toFloat()
    val bufRatio = bufW / bufH
    if (abs(viewRatio - bufRatio) <= 0.015f) {
        view.setTransform(null)
        return
    }
    val (scaleX, scaleY) = if (viewRatio < bufRatio) {
        (bufRatio / viewRatio) to 1f
    } else {
        1f to (viewRatio / bufRatio)
    }
    val matrix = Matrix()
    matrix.setScale(scaleX, scaleY, view.width / 2f, view.height / 2f)
    view.setTransform(matrix)
}

/**
 * Pro-grade Lightroom/Filmic Look Parameters executed in 60fps AGSL GPU Shader & full-res CPU capture.
 * Runs ON TOP of Camera2 Hardware Auto-HDR + AWB so portraits stay razor-sharp with zero ISP downgrade.
 */
internal data class LookGradeParams(
    val tempShift: Float = 0f,       // Relative warmth (-1..+1)
    val tintShift: Float = 0f,       // Relative rosy/magenta (+) vs green (-) (-1..+1)
    val exposureEv: Float = 0f,      // Soft-shoulder radiance lift (-1..+1 EV)
    val contrast: Float = 0f,        // Midpoint-anchored crisp S-curve (-0.5..+0.5)
    val highlights: Float = 0f,      // Highlight roll-off recovery (-1..+1)
    val shadows: Float = 0f,         // Shadow detail lift anchored above pure black (-1..+1)
    val whites: Float = 0f,          // Crisp white-point punch (-1..+1)
    val blacks: Float = 0f,          // Deep black anchor (-1..+1, negative deepens blacks)
    val vibrance: Float = 0f,        // Skin-protecting smart vibrance (-1..+1)
    val saturation: Float = 0f,      // Relative global saturation delta (-1..+1)
    val sharpness: Float = 0f,       // Edge-guarded luminance unsharp mask (0..0.6)
    val shadowHue: Float = 0f,       // 3-Way Color Grading: Shadows hue (0..360°)
    val shadowSat: Float = 0f,       // Shadows tint strength (0..0.35)
    val midtoneHue: Float = 0f,      // Midtones hue (0..360°)
    val midtoneSat: Float = 0f,      // Midtones tint strength (0..0.35)
    val highlightHue: Float = 0f,    // Highlights hue (0..360°)
    val highlightSat: Float = 0f,    // Highlights tint strength (0..0.35)
    val monochromeMix: Float = 0f,   // 0..1 smooth blend into true channel-mixed B&W
    val bwWeightR: Float = 0.34f,
    val bwWeightG: Float = 0.56f,
    val bwWeightB: Float = 0.10f
) {
    val hasSplitToning: Boolean
        get() = shadowSat > 0.003f || midtoneSat > 0.003f || highlightSat > 0.003f

    val isActive: Boolean
        get() = monochromeMix > 0.004f ||
            sharpness > 0.004f ||
            hasSplitToning ||
            abs(tempShift) > 0.004f || abs(tintShift) > 0.004f ||
            abs(exposureEv) > 0.004f || abs(contrast) > 0.004f ||
            abs(highlights) > 0.004f || abs(shadows) > 0.004f ||
            abs(whites) > 0.004f || abs(blacks) > 0.004f ||
            abs(vibrance) > 0.004f || abs(saturation) > 0.004f
}

internal fun scaleLookGrade(grade: LookGradeParams, strength: Float): LookGradeParams {
    val s = strength.coerceIn(0f, 1f)
    if (s <= 0.005f || !grade.isActive) return LookGradeParams()
    if (s >= 0.995f) return grade
    return grade.copy(
        tempShift = grade.tempShift * s,
        tintShift = grade.tintShift * s,
        exposureEv = grade.exposureEv * s,
        contrast = grade.contrast * s,
        highlights = grade.highlights * s,
        shadows = grade.shadows * s,
        whites = grade.whites * s,
        blacks = grade.blacks * s,
        vibrance = grade.vibrance * s,
        saturation = grade.saturation * s,
        sharpness = grade.sharpness * s,
        shadowSat = grade.shadowSat * s,
        midtoneSat = grade.midtoneSat * s,
        highlightSat = grade.highlightSat * s,
        monochromeMix = grade.monochromeMix * s
    )
}

internal fun computeLookWbGains(tempShift: Float, tintShift: Float): Triple<Float, Float, Float> {
    val t = tempShift.coerceIn(-1f, 1f)
    val ti = tintShift.coerceIn(-1f, 1f)
    val rRaw = (1.0f + t * 0.34f + ti * 0.14f).coerceAtLeast(0.45f)
    val gRaw = (1.0f - ti * 0.24f).coerceAtLeast(0.45f)
    val bRaw = (1.0f - t * 0.34f + ti * 0.14f).coerceAtLeast(0.45f)
    val lum = (0.2126f * rRaw + 0.7152f * gRaw + 0.0722f * bRaw).coerceAtLeast(0.01f)
    return Triple(rRaw / lum, gRaw / lum, bRaw / lum)
}

internal fun computeSplitWheelTint(hueDeg: Float, sat: Float): Triple<Float, Float, Float> {
    if (sat <= 0.001f) return Triple(0f, 0f, 0f)
    val h = ((hueDeg % 360f) + 360f) % 360f
    val c = AndroidColor.HSVToColor(floatArrayOf(h, 1f, 1f))
    val r = ((c ushr 16) and 0xFF) / 255f
    val g = ((c ushr 8) and 0xFF) / 255f
    val b = (c and 0xFF) / 255f
    val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
    return Triple((r - l) * sat, (g - l) * sat, (b - l) * sat)
}

internal data class CameraPreset(
    val name: String,
    val kelvin: Float,
    val tint: Float,
    val contrast: Float = DEFAULT_CONTRAST,
    val saturation: Float = DEFAULT_SATURATION,
    val channelSwap: Boolean = false,
    val colorBands: ColorBandsConfig = ColorBandsConfig(),
    val lookGrade: LookGradeParams = LookGradeParams(),
    val accentHex: Long = 0xFFF5A623L,
    val isBuiltIn: Boolean = false
)

internal const val KELVIN_MIN = 1f
internal const val KELVIN_MAX = 10000f
internal const val DEFAULT_CONTRAST = 1.0f
internal const val DEFAULT_SATURATION = 1.0f

internal fun buildBandPreset(vararg entries: Pair<ColorBand, BandHslAdjustment>): ColorBandsConfig {
    val list = MutableList(ColorBand.entries.size) { BandHslAdjustment() }
    val explicitlySet = HashSet<ColorBand>()
    entries.forEach { (band, adj) ->
        list[band.index] = adj
        explicitlySet.add(band)
    }
    val bridges = listOf(
        Triple(ColorBand.LIME, ColorBand.YELLOW, ColorBand.GREEN),
        Triple(ColorBand.EMERALD, ColorBand.GREEN, ColorBand.CYAN),
        Triple(ColorBand.AZURE, ColorBand.CYAN, ColorBand.BLUE),
        Triple(ColorBand.ROSE, ColorBand.MAGENTA, ColorBand.RED)
    )
    for ((mid, prev, next) in bridges) {
        if (mid !in explicitlySet && prev in explicitlySet && next in explicitlySet) {
            val a = list[prev.index]
            val b = list[next.index]
            list[mid.index] = BandHslAdjustment(
                deltaHue = (a.deltaHue + b.deltaHue) * 0.5f,
                deltaSat = (a.deltaSat + b.deltaSat) * 0.5f,
                deltaLum = (a.deltaLum + b.deltaLum) * 0.5f
            )
        }
    }
    return ColorBandsConfig(list)
}

internal fun scaleColorBands(bands: ColorBandsConfig, strength: Float): ColorBandsConfig {
    val s = strength.coerceIn(0f, 1f)
    if (s >= 0.995f) return bands
    if (s <= 0.005f) return ColorBandsConfig()
    return ColorBandsConfig(
        bands.bands.map { adj ->
            BandHslAdjustment(
                deltaHue = adj.deltaHue * s,
                deltaSat = adj.deltaSat * s,
                deltaLum = adj.deltaLum * s
            )
        }
    )
}

// Calibrated from MIT-licensed Lightroom XMP film & portrait recipes (peva3/EmulsionPresets)
// with crisp anchored blacks, edge-guarded optical clarity, 3-way split toning & skin-safe HSL.
internal val AESTHETIC_PRESETS: List<CameraPreset> = listOf(
    CameraPreset(
        name = "Natural",
        kelvin = 5500f,
        tint = 0f,
        contrast = 1.00f,
        saturation = 1.00f,
        channelSwap = false,
        colorBands = ColorBandsConfig(),
        lookGrade = LookGradeParams(),
        accentHex = 0xFF94A3B8L,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "Natural Skin",
        kelvin = 5500f,
        tint = 0f,
        contrast = 1.00f,
        saturation = 1.00f,
        channelSwap = false,
        colorBands = buildBandPreset(
            ColorBand.RED to BandHslAdjustment(deltaHue = 2f, deltaSat = -0.08f, deltaLum = 0.06f),
            ColorBand.ORANGE to BandHslAdjustment(deltaHue = 0f, deltaSat = -0.10f, deltaLum = 0.14f),
            ColorBand.GREEN to BandHslAdjustment(deltaHue = 4f, deltaSat = -0.12f, deltaLum = 0.0f)
        ),
        lookGrade = LookGradeParams(
            tempShift = 0.03f,
            tintShift = 0.03f,
            exposureEv = 0.10f,
            contrast = 0.08f,
            highlights = -0.25f,
            shadows = 0.24f,
            whites = 0.10f,
            blacks = -0.03f,
            vibrance = 0.08f,
            saturation = 0.0f,
            sharpness = 0.28f,
            highlightHue = 40f,
            highlightSat = 0.04f
        ),
        accentHex = 0xFFFDA4AFL,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "Portra 400",
        kelvin = 5500f,
        tint = 0f,
        contrast = 1.00f,
        saturation = 1.00f,
        channelSwap = false,
        colorBands = buildBandPreset(
            ColorBand.ORANGE to BandHslAdjustment(deltaHue = -3f, deltaSat = -0.05f, deltaLum = 0.15f),
            ColorBand.YELLOW to BandHslAdjustment(deltaHue = -8f, deltaSat = -0.04f, deltaLum = 0.04f),
            ColorBand.GREEN to BandHslAdjustment(deltaHue = -15f, deltaSat = -0.22f, deltaLum = 0.0f),
            ColorBand.CYAN to BandHslAdjustment(deltaHue = 0f, deltaSat = -0.10f, deltaLum = 0.0f),
            ColorBand.BLUE to BandHslAdjustment(deltaHue = -5f, deltaSat = -0.15f, deltaLum = -0.10f)
        ),
        lookGrade = LookGradeParams(
            tempShift = 0.07f,
            tintShift = 0.03f,
            exposureEv = 0.16f,
            contrast = 0.05f,
            highlights = -0.45f,
            shadows = 0.28f,
            whites = 0.05f,
            blacks = -0.02f,
            vibrance = 0.07f,
            saturation = 0.0f,
            sharpness = 0.26f,
            shadowHue = 210f,
            shadowSat = 0.09f,
            highlightHue = 45f,
            highlightSat = 0.13f
        ),
        accentHex = 0xFFFB923CL,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "Kodak Gold",
        kelvin = 5500f,
        tint = 0f,
        contrast = 1.00f,
        saturation = 1.00f,
        channelSwap = false,
        colorBands = buildBandPreset(
            ColorBand.RED to BandHslAdjustment(deltaHue = 7f, deltaSat = 0.10f, deltaLum = 0.02f),
            ColorBand.ORANGE to BandHslAdjustment(deltaHue = -2f, deltaSat = 0.12f, deltaLum = 0.14f),
            ColorBand.YELLOW to BandHslAdjustment(deltaHue = -12f, deltaSat = 0.18f, deltaLum = 0.09f),
            ColorBand.GREEN to BandHslAdjustment(deltaHue = -22f, deltaSat = -0.16f, deltaLum = -0.07f),
            ColorBand.CYAN to BandHslAdjustment(deltaHue = -12f, deltaSat = -0.05f, deltaLum = 0.0f),
            ColorBand.BLUE to BandHslAdjustment(deltaHue = -10f, deltaSat = -0.15f, deltaLum = -0.10f),
            ColorBand.PURPLE to BandHslAdjustment(deltaHue = -15f, deltaSat = -0.20f, deltaLum = 0.0f),
            ColorBand.MAGENTA to BandHslAdjustment(deltaHue = -15f, deltaSat = -0.20f, deltaLum = 0.0f)
        ),
        lookGrade = LookGradeParams(
            tempShift = 0.14f,
            tintShift = 0.03f,
            exposureEv = 0.09f,
            contrast = 0.09f,
            highlights = -0.42f,
            shadows = 0.22f,
            whites = -0.03f,
            blacks = -0.04f,
            vibrance = 0.10f,
            saturation = 0.02f,
            sharpness = 0.28f,
            shadowHue = 208f,
            shadowSat = 0.15f,
            highlightHue = 50f,
            highlightSat = 0.16f
        ),
        accentHex = 0xFFFBBF24L,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "Beige Cream",
        kelvin = 5500f,
        tint = 0f,
        contrast = 1.00f,
        saturation = 1.00f,
        channelSwap = false,
        colorBands = buildBandPreset(
            ColorBand.ORANGE to BandHslAdjustment(deltaHue = -2f, deltaSat = -0.06f, deltaLum = 0.10f),
            ColorBand.YELLOW to BandHslAdjustment(deltaHue = -10f, deltaSat = -0.18f, deltaLum = 0.04f),
            ColorBand.GREEN to BandHslAdjustment(deltaHue = -15f, deltaSat = -0.28f, deltaLum = -0.04f),
            ColorBand.BLUE to BandHslAdjustment(deltaHue = -6f, deltaSat = -0.18f, deltaLum = -0.04f)
        ),
        lookGrade = LookGradeParams(
            tempShift = 0.08f,
            tintShift = 0.01f,
            exposureEv = 0.11f,
            contrast = 0.15f,
            highlights = -0.22f,
            shadows = 0.19f,
            whites = 0.11f,
            blacks = -0.04f,
            vibrance = 0.06f,
            saturation = -0.05f,
            sharpness = 0.34f,
            shadowHue = 220f,
            shadowSat = 0.08f,
            highlightHue = 42f,
            highlightSat = 0.10f
        ),
        accentHex = 0xFFD6C5B3L,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "Rose Gold",
        kelvin = 5500f,
        tint = 0f,
        contrast = 1.00f,
        saturation = 1.00f,
        channelSwap = false,
        colorBands = buildBandPreset(
            ColorBand.RED to BandHslAdjustment(deltaHue = -4f, deltaSat = 0.06f, deltaLum = 0.06f),
            ColorBand.ORANGE to BandHslAdjustment(deltaHue = -3f, deltaSat = -0.08f, deltaLum = 0.12f),
            ColorBand.GREEN to BandHslAdjustment(deltaHue = 10f, deltaSat = -0.25f, deltaLum = 0.0f),
            ColorBand.PURPLE to BandHslAdjustment(deltaHue = 0f, deltaSat = 0.10f, deltaLum = 0.04f),
            ColorBand.MAGENTA to BandHslAdjustment(deltaHue = 0f, deltaSat = 0.16f, deltaLum = 0.08f)
        ),
        lookGrade = LookGradeParams(
            tempShift = 0.04f,
            tintShift = 0.15f,
            exposureEv = 0.14f,
            contrast = 0.08f,
            highlights = -0.35f,
            shadows = 0.24f,
            whites = 0.12f,
            blacks = -0.03f,
            vibrance = 0.08f,
            saturation = -0.02f,
            sharpness = 0.28f,
            shadowHue = 215f,
            shadowSat = 0.06f,
            highlightHue = 338f,
            highlightSat = 0.11f
        ),
        accentHex = 0xFFF472B6L,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "Riviera",
        kelvin = 5500f,
        tint = 0f,
        contrast = 1.00f,
        saturation = 1.00f,
        channelSwap = false,
        colorBands = buildBandPreset(
            ColorBand.RED to BandHslAdjustment(deltaHue = 2f, deltaSat = 0.10f, deltaLum = 0.05f),
            ColorBand.ORANGE to BandHslAdjustment(deltaHue = -2f, deltaSat = 0.10f, deltaLum = 0.10f),
            ColorBand.YELLOW to BandHslAdjustment(deltaHue = -4f, deltaSat = 0.10f, deltaLum = 0.04f),
            ColorBand.GREEN to BandHslAdjustment(deltaHue = 15f, deltaSat = -0.15f, deltaLum = 0.0f),
            ColorBand.CYAN to BandHslAdjustment(deltaHue = 0f, deltaSat = 0.15f, deltaLum = 0.08f),
            ColorBand.BLUE to BandHslAdjustment(deltaHue = -4f, deltaSat = 0.16f, deltaLum = 0.04f)
        ),
        lookGrade = LookGradeParams(
            tempShift = 0.11f,
            tintShift = 0.02f,
            exposureEv = 0.12f,
            contrast = 0.15f,
            highlights = -0.40f,
            shadows = 0.24f,
            whites = 0.14f,
            blacks = -0.05f,
            vibrance = 0.12f,
            saturation = 0.05f,
            sharpness = 0.32f,
            shadowHue = 35f,
            shadowSat = 0.08f,
            highlightHue = 40f,
            highlightSat = 0.12f
        ),
        accentHex = 0xFF38BDF8L,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "Classic Cuban",
        kelvin = 5500f,
        tint = 0f,
        contrast = 1.00f,
        saturation = 1.00f,
        channelSwap = false,
        colorBands = buildBandPreset(
            ColorBand.RED to BandHslAdjustment(deltaHue = 5f, deltaSat = 0.10f, deltaLum = -0.12f),
            ColorBand.ORANGE to BandHslAdjustment(deltaHue = -5f, deltaSat = 0.05f, deltaLum = 0.06f),
            ColorBand.YELLOW to BandHslAdjustment(deltaHue = -10f, deltaSat = 0.15f, deltaLum = 0.10f),
            ColorBand.GREEN to BandHslAdjustment(deltaHue = 25f, deltaSat = -0.40f, deltaLum = -0.14f),
            ColorBand.CYAN to BandHslAdjustment(deltaHue = -15f, deltaSat = 0.10f, deltaLum = -0.10f),
            ColorBand.BLUE to BandHslAdjustment(deltaHue = -20f, deltaSat = 0.05f, deltaLum = -0.22f),
            ColorBand.PURPLE to BandHslAdjustment(deltaHue = 0f, deltaSat = -0.15f, deltaLum = 0.0f),
            ColorBand.MAGENTA to BandHslAdjustment(deltaHue = 0f, deltaSat = -0.15f, deltaLum = 0.0f)
        ),
        lookGrade = LookGradeParams(
            tempShift = 0.10f,
            tintShift = -0.02f,
            exposureEv = 0.10f,
            contrast = 0.16f,
            highlights = -0.45f,
            shadows = -0.06f,
            whites = -0.05f,
            blacks = -0.08f,
            vibrance = 0.28f,
            saturation = 0.04f,
            sharpness = 0.32f,
            shadowHue = 210f,
            shadowSat = 0.11f,
            midtoneHue = 45f,
            midtoneSat = 0.10f,
            highlightHue = 45f,
            highlightSat = 0.15f
        ),
        accentHex = 0xFFEA580CL,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "K-Drama",
        kelvin = 5500f,
        tint = 0f,
        contrast = 1.00f,
        saturation = 1.00f,
        channelSwap = false,
        colorBands = buildBandPreset(
            ColorBand.RED to BandHslAdjustment(deltaHue = 0f, deltaSat = -0.04f, deltaLum = 0.05f),
            ColorBand.ORANGE to BandHslAdjustment(deltaHue = 0f, deltaSat = -0.06f, deltaLum = 0.12f),
            ColorBand.GREEN to BandHslAdjustment(deltaHue = -42f, deltaSat = -0.35f, deltaLum = 0.0f),
            ColorBand.BLUE to BandHslAdjustment(deltaHue = -5f, deltaSat = -0.15f, deltaLum = -0.04f)
        ),
        lookGrade = LookGradeParams(
            tempShift = -0.02f,
            tintShift = 0.02f,
            exposureEv = 0.06f,
            contrast = 0.18f,
            highlights = -0.38f,
            shadows = 0.22f,
            whites = 0.10f,
            blacks = -0.08f,
            vibrance = 0.06f,
            saturation = 0.0f,
            sharpness = 0.34f,
            shadowHue = 210f,
            shadowSat = 0.12f,
            midtoneHue = 35f,
            midtoneSat = 0.05f,
            highlightHue = 40f,
            highlightSat = 0.10f
        ),
        accentHex = 0xFF2DD4BFL,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "Fuji Chrome",
        kelvin = 5500f,
        tint = 0f,
        contrast = 1.00f,
        saturation = 1.00f,
        channelSwap = false,
        colorBands = buildBandPreset(
            ColorBand.RED to BandHslAdjustment(deltaHue = 0f, deltaSat = -0.20f, deltaLum = -0.12f),
            ColorBand.ORANGE to BandHslAdjustment(deltaHue = 3f, deltaSat = -0.14f, deltaLum = -0.06f),
            ColorBand.YELLOW to BandHslAdjustment(deltaHue = 0f, deltaSat = -0.08f, deltaLum = -0.06f),
            ColorBand.GREEN to BandHslAdjustment(deltaHue = -20f, deltaSat = -0.30f, deltaLum = -0.18f),
            ColorBand.CYAN to BandHslAdjustment(deltaHue = 10f, deltaSat = -0.20f, deltaLum = -0.10f),
            ColorBand.BLUE to BandHslAdjustment(deltaHue = -8f, deltaSat = -0.24f, deltaLum = -0.16f),
            ColorBand.PURPLE to BandHslAdjustment(deltaHue = -15f, deltaSat = -0.22f, deltaLum = -0.08f),
            ColorBand.MAGENTA to BandHslAdjustment(deltaHue = 0f, deltaSat = -0.20f, deltaLum = -0.12f)
        ),
        lookGrade = LookGradeParams(
            tempShift = 0.02f,
            tintShift = 0.0f,
            exposureEv = 0.04f,
            contrast = 0.20f,
            highlights = -0.45f,
            shadows = -0.12f,
            whites = 0.06f,
            blacks = -0.12f,
            vibrance = -0.04f,
            saturation = -0.08f,
            sharpness = 0.34f,
            shadowHue = 212f,
            shadowSat = 0.09f,
            highlightHue = 50f,
            highlightSat = 0.06f
        ),
        accentHex = 0xFF60A5FAL,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "Espresso",
        kelvin = 5500f,
        tint = 0f,
        contrast = 1.00f,
        saturation = 1.00f,
        channelSwap = false,
        colorBands = buildBandPreset(
            ColorBand.ORANGE to BandHslAdjustment(deltaHue = -2f, deltaSat = 0.06f, deltaLum = 0.10f),
            ColorBand.YELLOW to BandHslAdjustment(deltaHue = -16f, deltaSat = -0.16f, deltaLum = 0.02f),
            ColorBand.GREEN to BandHslAdjustment(deltaHue = -25f, deltaSat = -0.35f, deltaLum = -0.10f),
            ColorBand.BLUE to BandHslAdjustment(deltaHue = -8f, deltaSat = -0.30f, deltaLum = -0.06f)
        ),
        lookGrade = LookGradeParams(
            tempShift = 0.12f,
            tintShift = 0.04f,
            exposureEv = 0.06f,
            contrast = 0.15f,
            highlights = -0.24f,
            shadows = 0.16f,
            whites = 0.08f,
            blacks = -0.05f,
            vibrance = -0.05f,
            saturation = -0.08f,
            sharpness = 0.30f,
            shadowHue = 35f,
            shadowSat = 0.13f,
            midtoneHue = 38f,
            midtoneSat = 0.07f,
            highlightHue = 42f,
            highlightSat = 0.11f
        ),
        accentHex = 0xFFA16207L,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "Acros B&W",
        kelvin = 5500f,
        tint = 0f,
        contrast = 1.00f,
        saturation = 1.00f,
        channelSwap = false,
        colorBands = buildBandPreset(
            ColorBand.RED to BandHslAdjustment(0f, 0f, 0.20f),
            ColorBand.ORANGE to BandHslAdjustment(0f, 0f, 0.14f),
            ColorBand.YELLOW to BandHslAdjustment(0f, 0f, 0.06f),
            ColorBand.GREEN to BandHslAdjustment(0f, 0f, 0.10f),
            ColorBand.CYAN to BandHslAdjustment(0f, 0f, -0.06f),
            ColorBand.BLUE to BandHslAdjustment(0f, 0f, -0.18f),
            ColorBand.MAGENTA to BandHslAdjustment(0f, 0f, -0.05f)
        ),
        lookGrade = LookGradeParams(
            exposureEv = 0.06f,
            contrast = 0.20f,
            highlights = -0.28f,
            shadows = 0.18f,
            whites = 0.14f,
            blacks = -0.16f,
            sharpness = 0.36f,
            monochromeMix = 1.0f,
            bwWeightR = 0.34f,
            bwWeightG = 0.54f,
            bwWeightB = 0.12f
        ),
        accentHex = 0xFFE2E8F0L,
        isBuiltIn = true
    )
)

internal val IR_LAB_PRESETS: List<CameraPreset> = listOf(
    CameraPreset(
        name = "Aerochrome IR",
        kelvin = 5200f,
        tint = 8f,
        contrast = 1.14f,
        saturation = 1.20f,
        channelSwap = false,
        colorBands = buildBandPreset(
            ColorBand.YELLOW to BandHslAdjustment(deltaHue = -55f, deltaSat = 0.35f, deltaLum = 0.05f),
            ColorBand.GREEN to BandHslAdjustment(deltaHue = -130f, deltaSat = 0.55f, deltaLum = 0.10f),
            ColorBand.CYAN to BandHslAdjustment(deltaHue = 20f, deltaSat = 0.25f, deltaLum = -0.12f),
            ColorBand.BLUE to BandHslAdjustment(deltaHue = -6f, deltaSat = 0.35f, deltaLum = -0.20f)
        ),
        accentHex = 0xFFEF4444L,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "590nm Gold (R↔B)",
        kelvin = 2800f,
        tint = 5f,
        contrast = 1.12f,
        saturation = 1.25f,
        channelSwap = true,
        colorBands = buildBandPreset(
            ColorBand.ORANGE to BandHslAdjustment(deltaHue = 12f, deltaSat = 0.25f, deltaLum = 0.08f),
            ColorBand.BLUE to BandHslAdjustment(deltaHue = -8f, deltaSat = 0.30f, deltaLum = -0.10f)
        ),
        accentHex = 0xFFF5A623L,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "665nm False Color",
        kelvin = 2400f,
        tint = 0f,
        contrast = 1.16f,
        saturation = 1.10f,
        channelSwap = true,
        colorBands = buildBandPreset(
            ColorBand.YELLOW to BandHslAdjustment(deltaHue = 8f, deltaSat = -0.35f, deltaLum = 0.32f),
            ColorBand.ORANGE to BandHslAdjustment(deltaHue = 14f, deltaSat = -0.25f, deltaLum = 0.25f),
            ColorBand.BLUE to BandHslAdjustment(deltaHue = -6f, deltaSat = 0.40f, deltaLum = -0.18f),
            ColorBand.CYAN to BandHslAdjustment(deltaHue = 10f, deltaSat = 0.30f, deltaLum = -0.10f)
        ),
        accentHex = 0xFF38BDF8L,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "720nm B&W IR",
        kelvin = 5500f,
        tint = 0f,
        contrast = 1.26f,
        saturation = 0.50f,
        channelSwap = false,
        colorBands = buildBandPreset(
            ColorBand.RED to BandHslAdjustment(0f, -1.0f, 0.05f),
            ColorBand.ORANGE to BandHslAdjustment(0f, -1.0f, 0.15f),
            ColorBand.YELLOW to BandHslAdjustment(0f, -1.0f, 0.45f),
            ColorBand.GREEN to BandHslAdjustment(0f, -1.0f, 0.55f),
            ColorBand.CYAN to BandHslAdjustment(0f, -1.0f, -0.40f),
            ColorBand.BLUE to BandHslAdjustment(0f, -1.0f, -0.55f),
            ColorBand.PURPLE to BandHslAdjustment(0f, -1.0f, -0.20f),
            ColorBand.MAGENTA to BandHslAdjustment(0f, -1.0f, 0.0f)
        ),
        accentHex = 0xFFE2E8F0L,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "850nm Deep B&W",
        kelvin = 1800f,
        tint = 0f,
        contrast = 1.38f,
        saturation = 0.50f,
        channelSwap = false,
        colorBands = buildBandPreset(
            ColorBand.RED to BandHslAdjustment(0f, -1.0f, 0.10f),
            ColorBand.ORANGE to BandHslAdjustment(0f, -1.0f, 0.20f),
            ColorBand.YELLOW to BandHslAdjustment(0f, -1.0f, 0.60f),
            ColorBand.GREEN to BandHslAdjustment(0f, -1.0f, 0.70f),
            ColorBand.CYAN to BandHslAdjustment(0f, -1.0f, -0.55f),
            ColorBand.BLUE to BandHslAdjustment(0f, -1.0f, -0.75f),
            ColorBand.PURPLE to BandHslAdjustment(0f, -1.0f, -0.35f),
            ColorBand.MAGENTA to BandHslAdjustment(0f, -1.0f, -0.10f)
        ),
        accentHex = 0xFF94A3B8L,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "IRChrome Natural",
        kelvin = 3350f,
        tint = 12f,
        contrast = 1.06f,
        saturation = 1.15f,
        channelSwap = false,
        colorBands = ColorBandsConfig(),
        accentHex = 0xFFF97316L,
        isBuiltIn = true
    )
)

internal fun getAllBuiltInPresetsForMode(mode: ExperienceMode): List<CameraPreset> {
    val ordered = when (mode) {
        ExperienceMode.AESTHETIC -> AESTHETIC_PRESETS + BUILT_IN_SIGNATURE_PRESETS + IR_LAB_PRESETS
        ExperienceMode.IR_LAB -> IR_LAB_PRESETS + BUILT_IN_SIGNATURE_PRESETS + AESTHETIC_PRESETS
        ExperienceMode.PRO -> BUILT_IN_SIGNATURE_PRESETS + IR_LAB_PRESETS + AESTHETIC_PRESETS
    }
    return ordered.distinctBy { it.name }
}

internal val BUILT_IN_SIGNATURE_PRESETS: List<CameraPreset> = listOf(
    CameraPreset(
        name = "Aerochrome IR",
        kelvin = 5200f,
        tint = 8f,
        contrast = 1.14f,
        saturation = 1.20f,
        channelSwap = false,
        colorBands = buildBandPreset(
            ColorBand.YELLOW to BandHslAdjustment(deltaHue = -55f, deltaSat = 0.35f, deltaLum = 0.05f),
            ColorBand.GREEN to BandHslAdjustment(deltaHue = -130f, deltaSat = 0.55f, deltaLum = 0.10f),
            ColorBand.CYAN to BandHslAdjustment(deltaHue = 20f, deltaSat = 0.25f, deltaLum = -0.12f),
            ColorBand.BLUE to BandHslAdjustment(deltaHue = -6f, deltaSat = 0.35f, deltaLum = -0.20f)
        ),
        accentHex = 0xFFEF4444L,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "IR 590nm Gold (R↔B)",
        kelvin = 2800f,
        tint = 5f,
        contrast = 1.12f,
        saturation = 1.25f,
        channelSwap = true,
        colorBands = buildBandPreset(
            ColorBand.ORANGE to BandHslAdjustment(deltaHue = 12f, deltaSat = 0.25f, deltaLum = 0.08f),
            ColorBand.BLUE to BandHslAdjustment(deltaHue = -8f, deltaSat = 0.30f, deltaLum = -0.10f)
        ),
        accentHex = 0xFFF5A623L,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "720nm B&W IR",
        kelvin = 5500f,
        tint = 0f,
        contrast = 1.26f,
        saturation = 0.50f,
        channelSwap = false,
        colorBands = buildBandPreset(
            ColorBand.RED to BandHslAdjustment(0f, -1.0f, 0.05f),
            ColorBand.ORANGE to BandHslAdjustment(0f, -1.0f, 0.15f),
            ColorBand.YELLOW to BandHslAdjustment(0f, -1.0f, 0.45f),
            ColorBand.GREEN to BandHslAdjustment(0f, -1.0f, 0.55f),
            ColorBand.CYAN to BandHslAdjustment(0f, -1.0f, -0.40f),
            ColorBand.BLUE to BandHslAdjustment(0f, -1.0f, -0.55f),
            ColorBand.PURPLE to BandHslAdjustment(0f, -1.0f, -0.20f),
            ColorBand.MAGENTA to BandHslAdjustment(0f, -1.0f, 0.0f)
        ),
        accentHex = 0xFFE2E8F0L,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "Teal & Orange",
        kelvin = 5600f,
        tint = -4f,
        contrast = 1.12f,
        saturation = 1.10f,
        channelSwap = false,
        colorBands = buildBandPreset(
            ColorBand.RED to BandHslAdjustment(10f, 0.15f, 0f),
            ColorBand.ORANGE to BandHslAdjustment(-4f, 0.25f, 0.08f),
            ColorBand.YELLOW to BandHslAdjustment(-22f, 0.10f, 0f),
            ColorBand.GREEN to BandHslAdjustment(45f, -0.25f, -0.12f),
            ColorBand.CYAN to BandHslAdjustment(5f, 0.25f, -0.05f),
            ColorBand.BLUE to BandHslAdjustment(-22f, 0.25f, -0.15f)
        ),
        accentHex = 0xFF2DD4BFL,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "Cyberpunk Neon",
        kelvin = 4400f,
        tint = 28f,
        contrast = 1.18f,
        saturation = 1.25f,
        channelSwap = false,
        colorBands = buildBandPreset(
            ColorBand.RED to BandHslAdjustment(-25f, 0.35f, 0.05f),
            ColorBand.GREEN to BandHslAdjustment(55f, 0.20f, -0.10f),
            ColorBand.BLUE to BandHslAdjustment(-30f, 0.45f, 0.05f),
            ColorBand.PURPLE to BandHslAdjustment(22f, 0.50f, 0.10f),
            ColorBand.MAGENTA to BandHslAdjustment(10f, 0.45f, 0.12f)
        ),
        accentHex = 0xFFE84393L,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "Sin City Crimson",
        kelvin = 5500f,
        tint = 0f,
        contrast = 1.24f,
        saturation = 1.05f,
        channelSwap = false,
        colorBands = buildBandPreset(
            ColorBand.RED to BandHslAdjustment(0f, 0.55f, 0.05f),
            ColorBand.ORANGE to BandHslAdjustment(0f, -1.0f, 0f),
            ColorBand.YELLOW to BandHslAdjustment(0f, -1.0f, 0f),
            ColorBand.GREEN to BandHslAdjustment(0f, -1.0f, 0f),
            ColorBand.CYAN to BandHslAdjustment(0f, -1.0f, 0f),
            ColorBand.BLUE to BandHslAdjustment(0f, -1.0f, -0.10f),
            ColorBand.PURPLE to BandHslAdjustment(0f, -1.0f, 0f),
            ColorBand.MAGENTA to BandHslAdjustment(0f, -1.0f, 0f)
        ),
        accentHex = 0xFFDC2626L,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "Golden Hour Film",
        kelvin = 6600f,
        tint = 6f,
        contrast = 1.06f,
        saturation = 1.12f,
        channelSwap = false,
        colorBands = buildBandPreset(
            ColorBand.ORANGE to BandHslAdjustment(4f, 0.25f, 0.10f),
            ColorBand.YELLOW to BandHslAdjustment(-12f, 0.20f, 0.08f),
            ColorBand.GREEN to BandHslAdjustment(-25f, -0.15f, -0.05f),
            ColorBand.BLUE to BandHslAdjustment(-10f, -0.15f, -0.05f)
        ),
        accentHex = 0xFFF97316L,
        isBuiltIn = true
    ),
    CameraPreset(
        name = "Natural / Neutral",
        kelvin = 5500f,
        tint = 0f,
        contrast = 1.00f,
        saturation = 1.00f,
        channelSwap = false,
        colorBands = ColorBandsConfig(),
        accentHex = 0xFF22C55EL,
        isBuiltIn = true
    )
)

private val DEFAULT_SRGB_CCM = floatArrayOf(
    1.65f, -0.50f, -0.15f,
    -0.20f, 1.45f, -0.25f,
    -0.05f, -0.55f, 1.60f
)

private fun kelvinToRawTriple(kVal: Float, tVal: Float): Triple<Float, Float, Float> {
    val kRatio = ((kVal - 1000f) / (10000f - 1000f)).coerceIn(0f, 1f)
    val r = (4.0f - (kRatio * 3.0f)).coerceIn(1.0f, 4.0f)
    val b = (1.0f + (kRatio * 3.0f)).coerceIn(1.0f, 4.0f)
    val tRatio = (tVal / 100f).coerceIn(-1f, 1f)
    // Allow g down to 0.5 before normalization so negative Tint (-100..-34) takes effect cleanly
    val g = (1.5f + tRatio * 1.5f).coerceIn(0.5f, 4.0f)
    return Triple(r, g, b)
}

/**
 * Normalizes RGGB gains so the weakest channel is 1.0f.
 * This preserves exact R:G:B color balance while eliminating unnecessary digital overexposure
 * and highlight clipping that previously washed out contrast and saturation in Manual mode.
 */
private fun kelvinToGains(kVal: Float, tVal: Float): RggbChannelVector {
    val (rRaw, gRaw, bRaw) = kelvinToRawTriple(kVal, tVal)
    val minChannel = minOf(rRaw, gRaw, bRaw).coerceAtLeast(0.01f)
    val r = (rRaw / minChannel).coerceAtLeast(1.0f)
    val g = (gRaw / minChannel).coerceAtLeast(1.0f)
    val b = (bRaw / minChannel).coerceAtLeast(1.0f)
    return RggbChannelVector(r, g, g, b)
}

private fun gainsToKelvinTint(gains: RggbChannelVector): Pair<Float, Float> {
    val r = gains.red.coerceAtLeast(0.01f)
    val g = ((gains.greenEven + gains.greenOdd) * 0.5f).coerceAtLeast(0.01f)
    val b = gains.blue.coerceAtLeast(0.01f)
    val scale = 5.0f / (r + b).coerceAtLeast(0.01f)
    val rScaled = r * scale
    val bScaled = b * scale
    val gScaled = g * scale

    val kRatioR = (4.0f - rScaled) / 3.0f
    val kRatioB = (bScaled - 1.0f) / 3.0f
    val kRatio = ((kRatioR + kRatioB) * 0.5f).coerceIn(0.0f, 1.0f)
    val kelvin = 1000f + kRatio * 9000f
    val tint = (100f * (gScaled - 1.5f) / 1.5f).coerceIn(-100f, 100f)
    return Pair(kelvin, tint)
}

/**
 * Derives Kelvin and Tint from a 25x25 spot sample on the live preview (closed-loop inverse model).
 */
private fun calculateSpotWhiteBalance(
    bitmap: Bitmap,
    x: Int,
    y: Int,
    currentGains: RggbChannelVector
): Pair<Float, Float> {
    val sampleSize = 25.coerceAtMost(minOf(bitmap.width, bitmap.height))
    if (sampleSize <= 0) return Pair(5500f, 0f)
    val startX = (x - sampleSize / 2).coerceIn(0, (bitmap.width - sampleSize).coerceAtLeast(0))
    val startY = (y - sampleSize / 2).coerceIn(0, (bitmap.height - sampleSize).coerceAtLeast(0))

    var rSum = 0.0
    var gSum = 0.0
    var bSum = 0.0

    for (i in 0 until sampleSize) {
        for (j in 0 until sampleSize) {
            val pixel = bitmap[startX + i, startY + j]
            rSum += AndroidColor.red(pixel)
            gSum += AndroidColor.green(pixel)
            bSum += AndroidColor.blue(pixel)
        }
    }

    val count = (sampleSize * sampleSize).toDouble()
    val rAvg = (rSum / count).coerceAtLeast(1.0).toFloat()
    val gAvg = (gSum / count).coerceAtLeast(1.0).toFloat()
    val bAvg = (bSum / count).coerceAtLeast(1.0).toFloat()

    return inverseRgbToKelvinTint(rAvg, gAvg, bAvg, currentGains)
}

private fun inverseRgbToKelvinTint(
    rAvg: Float,
    gAvg: Float,
    bAvg: Float,
    currentGains: RggbChannelVector
): Pair<Float, Float> {
    val rGain = currentGains.red.coerceAtLeast(0.01f)
    val gGain = ((currentGains.greenEven + currentGains.greenOdd) * 0.5f).coerceAtLeast(0.01f)
    val bGain = currentGains.blue.coerceAtLeast(0.01f)

    val rS = rAvg.coerceAtLeast(1f)
    val gS = gAvg.coerceAtLeast(1f)
    val bS = bAvg.coerceAtLeast(1f)

    // 2.5% neutral deadband in sRGB: keeps repeated taps on an already-balanced white wall rock-steady
    val maxC = maxOf(rS, maxOf(gS, bS))
    val minC = minOf(rS, minOf(gS, bS)).coerceAtLeast(1f)
    if (maxC / minC < 1.025f) {
        return gainsToKelvinTint(currentGains)
    }

    val rRawInverse = rGain / rS
    val bRawInverse = bGain / bS
    val gRawInverse = gGain / gS

    val scaleFactor = 5.0f / (rRawInverse + bRawInverse).coerceAtLeast(1e-6f)
    val rTarget = rRawInverse * scaleFactor
    val bTarget = bRawInverse * scaleFactor
    val gTarget = gRawInverse * scaleFactor

    val kRatioR = (4.0f - rTarget) / 3.0f
    val kRatioB = (bTarget - 1.0f) / 3.0f
    val kRatio = ((kRatioR + kRatioB) / 2.0f).coerceIn(0.0f, 1.0f)
    val kelvin = 1000f + kRatio * 9000f
    val tint = (100f * (gTarget - 1.5f) / 1.5f).coerceIn(-100f, 100f)

    return Pair(kelvin, tint)
}

/**
 * Builds a 3x3 hardware ColorSpaceTransform with luminance-preserving saturation.
 * Uses the camera sensor's native matrix (or default sRGB matrix) without synthetic distortion.
 */
private fun buildColorTransform(
    baseTransform: ColorSpaceTransform?,
    saturation: Float
): ColorSpaceTransform {
    val base = FloatArray(9)
    var validBase = false
    if (baseTransform != null) {
        try {
            val elements = Array(9) { Rational(0, 1) }
            baseTransform.copyElements(elements, 0)
            for (i in 0 until 9) {
                val rat = elements[i]
                if (rat.denominator == 0) {
                    validBase = false
                    break
                }
                val v = rat.toFloat()
                if (!v.isFinite()) {
                    validBase = false
                    break
                }
                base[i] = v
                validBase = true
            }
            if (validBase && (abs(base[0]) < 0.1f || abs(base[4]) < 0.1f || abs(base[8]) < 0.1f)) {
                validBase = false
            }
        } catch (_: Exception) {
            validBase = false
        }
    }
    if (!validBase) {
        DEFAULT_SRGB_CCM.copyInto(base)
    }

    val s = saturation.coerceIn(0.0f, 2.5f)
    val lr = 0.299f
    val lg = 0.587f
    val lb = 0.114f
    val satMat = floatArrayOf(
        (1f - s) * lr + s, (1f - s) * lg,     (1f - s) * lb,
        (1f - s) * lr,     (1f - s) * lg + s, (1f - s) * lb,
        (1f - s) * lr,     (1f - s) * lg,     (1f - s) * lb + s
    )

    val outRationals = Array(9) { idx ->
        val row = idx / 3
        val col = idx % 3
        val value =
            satMat[row * 3] * base[col] +
                satMat[row * 3 + 1] * base[3 + col] +
                satMat[row * 3 + 2] * base[6 + col]
        Rational((value * 1000f).roundToInt().coerceIn(-10000, 10000), 1000)
    }
    return ColorSpaceTransform(outRationals)
}

/**
 * Standard sRGB OETF (linear sensor light [0..1] -> gamma-encoded sRGB [0..1]).
 * Camera2 TONEMAP_CURVE maps linear RGB to gamma-encoded sRGB; applying an S-curve
 * directly to linear x without sRGB gamma collapses midtones (~0.46 -> ~0.18).
 */
private fun linearToSrgb(x: Float): Float {
    val clamped = x.coerceIn(0f, 1f)
    return if (clamped <= 0.0031308f) {
        (12.92f * clamped).coerceIn(0f, 1f)
    } else {
        (1.055 * clamped.toDouble().pow(1.0 / 2.4) - 0.055).toFloat().coerceIn(0f, 1f)
    }
}

private fun sampleTonemapChannel(curve: TonemapCurve, channel: Int, x: Float): Float {
    val count = curve.getPointCount(channel)
    if (count < 2) return linearToSrgb(x)
    val clampedX = x.coerceIn(0f, 1f)
    val first = curve.getPoint(channel, 0)
    if (clampedX <= first.x) return first.y.coerceIn(0f, 1f)
    val last = curve.getPoint(channel, count - 1)
    if (clampedX >= last.x) return last.y.coerceIn(0f, 1f)

    var low = 0
    var high = count - 1
    while (high - low > 1) {
        val mid = (low + high) ushr 1
        if (curve.getPoint(channel, mid).x <= clampedX) {
            low = mid
        } else {
            high = mid
        }
    }
    val p0 = curve.getPoint(channel, low)
    val p1 = curve.getPoint(channel, high)
    val dx = (p1.x - p0.x).coerceAtLeast(1e-6f)
    val t = ((clampedX - p0.x) / dx).coerceIn(0f, 1f)
    return (p0.y + (p1.y - p0.y) * t).coerceIn(0f, 1f)
}

private fun isValidNativeTonemapCurve(curve: TonemapCurve?): Boolean {
    if (curve == null) return false
    return try {
        val countG = curve.getPointCount(TonemapCurve.CHANNEL_GREEN)
        if (countG < 8) return false
        val y0 = sampleTonemapChannel(curve, TonemapCurve.CHANNEL_GREEN, 0.0f)
        val yMid = sampleTonemapChannel(curve, TonemapCurve.CHANNEL_GREEN, 0.18f)
        val yHalf = sampleTonemapChannel(curve, TonemapCurve.CHANNEL_GREEN, 0.50f)
        val y1 = sampleTonemapChannel(curve, TonemapCurve.CHANNEL_GREEN, 1.0f)
        // Confirm real gamma-encoded ISP curve (not a linear dummy)
        y0 <= 0.12f && yMid in 0.30f..0.74f && yHalf in 0.55f..0.96f && y1 >= 0.90f && yHalf >= yMid
    } catch (_: Exception) {
        false
    }
}

/**
 * Generates a perceptually calibrated Camera2 TonemapCurve by layering a midpoint-anchored
 * sRGB S-curve on top of the camera ISP's native gamma curve (or exact sRGB OETF fallback).
 * Guarantees zero sudden brightness drop when moving away from 1.00x contrast.
 */
private fun buildContrastTonemapCurve(
    contrast: Float,
    maxCurvePoints: Int,
    nativeCurve: TonemapCurve? = null
): TonemapCurve {
    val count = minOf(64, maxCurvePoints).coerceIn(2, 64)
    val c = contrast.coerceIn(0.60f, 1.80f)
    // Smooth, photographic S-curve strength around perceptual 0.50 midpoint:
    // c = 1.00 -> gammaS = 1.00 (exact identity), c > 1.0 -> crisp contrast without darkening midtones
    val gammaS = (1.0 + (c - 1.0) * 0.78).coerceIn(0.55, 1.85)
    val useNative = isValidNativeTonemapCurve(nativeCurve)

    fun buildChannel(channel: Int): FloatArray {
        val pts = FloatArray(count * 2)
        var prevX = 0f
        var prevY = 0f
        val minStep = 1e-5f
        for (i in 0 until count) {
            val x = when (i) {
                0 -> 0f
                count - 1 -> 1f
                else -> {
                    // Perceptually spaced linear x (dense in shadows where sRGB slope is steepest)
                    val t = i.toDouble() / (count - 1).toDouble()
                    val upperBound = (1f - minStep * (count - 1 - i)).coerceAtLeast(prevX + minStep)
                    t.pow(2.2).toFloat().coerceIn(prevX + minStep, upperBound)
                }
            }
            val y = when (i) {
                0 -> 0f
                count - 1 -> 1f
                else -> {
                    val baseY = if (useNative && nativeCurve != null) {
                        sampleTonemapChannel(nativeCurve, channel, x)
                    } else {
                        linearToSrgb(x)
                    }.coerceIn(0f, 1f)

                    val shaped = if (abs(gammaS - 1.0) < 0.002) {
                        baseY
                    } else if (baseY < 0.5f) {
                        0.5f * (baseY / 0.5f).coerceIn(0f, 1f).toDouble().pow(gammaS).toFloat()
                    } else {
                        1.0f - 0.5f * ((1.0f - baseY) / 0.5f).coerceIn(0f, 1f).toDouble().pow(gammaS).toFloat()
                    }
                    maxOf(shaped.coerceIn(0f, 1f), prevY)
                }
            }
            pts[i * 2] = x
            pts[i * 2 + 1] = y
            prevX = x
            prevY = y
        }
        return pts
    }

    return if (useNative) {
        TonemapCurve(
            buildChannel(TonemapCurve.CHANNEL_RED),
            buildChannel(TonemapCurve.CHANNEL_GREEN),
            buildChannel(TonemapCurve.CHANNEL_BLUE)
        )
    } else {
        val mono = buildChannel(TonemapCurve.CHANNEL_GREEN)
        TonemapCurve(mono, mono, mono)
    }
}

data class TargetedColorConfig(
    val targetHue: Float = 0f,
    val targetSat: Float = 0f,
    val targetLum: Float = 0f,
    val deltaHue: Float = 0f,
    val deltaSat: Float = 0f,
    val deltaLum: Float = 0f,
    val hueWidth: Float = 22f,
    val hueFeather: Float = 24f,
    val isActive: Boolean = false,
    val previewColorInt: Int = 0
)

internal fun getHueName(context: Context, hue: Float): String {
    val h = (hue % 360f + 360f) % 360f
    val resId = when (h) {
        in 0f..15f -> R.string.hue_red
        in 16f..45f -> R.string.hue_orange
        in 46f..70f -> R.string.hue_yellow
        in 71f..150f -> R.string.hue_green
        in 151f..195f -> R.string.hue_cyan
        in 196f..260f -> R.string.hue_blue
        in 261f..315f -> R.string.hue_purple
        in 316f..345f -> R.string.hue_magenta
        else -> R.string.hue_red
    }
    return context.getString(resId)
}

private fun smoothstep01(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun rgbToHsl01(r: Float, g: Float, b: Float, out: FloatArray) {
    val cMin = minOf(r, g, b)
    val cMax = maxOf(r, g, b)
    val delta = cMax - cMin
    val l = (cMax + cMin) * 0.5f
    var s = 0f
    var h = 0f
    if (delta > 0.001f) {
        s = if (l < 0.5f) delta / (cMax + cMin) else delta / (2.0f - cMax - cMin)
        h = when {
            r >= cMax -> (g - b) / delta + (if (g < b) 6.0f else 0.0f)
            g >= cMax -> (b - r) / delta + 2.0f
            else -> (r - g) / delta + 4.0f
        } * 60.0f
    }
    out[0] = h
    out[1] = s
    out[2] = l
}

private fun hue2Rgb01(p: Float, q: Float, tIn: Float): Float {
    var t = tIn
    if (t < 0f) t += 1f
    if (t > 1f) t -= 1f
    return when {
        t < 0.1666667f -> p + (q - p) * 6.0f * t
        t < 0.5f -> q
        t < 0.6666667f -> p + (q - p) * (0.6666667f - t) * 6.0f
        else -> p
    }
}

private fun hslToRgb01(hDeg: Float, s: Float, l: Float, out: FloatArray) {
    if (s <= 0.001f) {
        out[0] = l
        out[1] = l
        out[2] = l
        return
    }
    val h = hDeg / 360.0f
    val q = if (l < 0.5f) l * (1.0f + s) else l + s - l * s
    val p = 2.0f * l - q
    out[0] = hue2Rgb01(p, q, h + 0.3333333f)
    out[1] = hue2Rgb01(p, q, h)
    out[2] = hue2Rgb01(p, q, h - 0.3333333f)
}

/**
 * Unified Pro Color-Grading Engine (CPU bit-exact mirror of AGSL GPU Shader):
 * Edge-Guarded Unsharp Mask + WB/Soft-Shoulder Exposure + Photographic Tone Zones +
 * Skin-Safe Smart Vibrance + Lightroom 3-Way Split Toning + R↔B Channel Swap +
 * Bell-Curve 12-Band HSL + Point Pipette + True B&W Channel Mixer.
 */
internal fun applyTargetedHslToBitmap(
    bitmap: Bitmap,
    config: TargetedColorConfig,
    colorBands: ColorBandsConfig = ColorBandsConfig(),
    channelSwap: Boolean = false,
    lookGrade: LookGradeParams = LookGradeParams()
): Bitmap {
    val hasPoint = config.isActive && (config.deltaHue != 0f || config.deltaSat != 0f || config.deltaLum != 0f)
    val hasBands = colorBands.hasAnyModification
    val hasLook = lookGrade.isActive
    if (!hasLook && !channelSwap && !hasPoint && !hasBands) {
        return bitmap
    }
    val work = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val w = work.width
    val h = work.height
    if (w <= 0 || h <= 0) return work

    val doSharp = hasLook && lookGrade.sharpness > 0.004f && w >= 3 && h >= 3
    val sharpAmt = lookGrade.sharpness * 1.35f
    val (wbR, wbG, wbB) = if (hasLook) computeLookWbGains(lookGrade.tempShift, lookGrade.tintShift) else Triple(1f, 1f, 1f)
    val expScale = if (hasLook) 2.0f.pow(lookGrade.exposureEv) else 1f
    val mulR = wbR * expScale
    val mulG = wbG * expScale
    val mulB = wbB * expScale
    val doWbExp = hasLook && (abs(mulR - 1f) > 0.003f || abs(mulG - 1f) > 0.003f || abs(mulB - 1f) > 0.003f)
    val doZones = hasLook && (
        abs(lookGrade.shadows) > 0.003f || abs(lookGrade.highlights) > 0.003f ||
            abs(lookGrade.blacks) > 0.003f || abs(lookGrade.whites) > 0.003f ||
            abs(lookGrade.contrast) > 0.004f
        )
    val contrastK = (1.0f + lookGrade.contrast * 0.85f).coerceIn(0.70f, 1.55f)
    val doContrastK = hasLook && abs(lookGrade.contrast) > 0.004f
    val doVibSat = hasLook && (abs(lookGrade.vibrance) > 0.004f || abs(lookGrade.saturation) > 0.004f)
    val hasSplit = hasLook && lookGrade.hasSplitToning
    val (shR, shG, shB) = if (hasSplit) computeSplitWheelTint(lookGrade.shadowHue, lookGrade.shadowSat) else Triple(0f, 0f, 0f)
    val (midR, midG, midB) = if (hasSplit) computeSplitWheelTint(lookGrade.midtoneHue, lookGrade.midtoneSat) else Triple(0f, 0f, 0f)
    val (hiR, hiG, hiB) = if (hasSplit) computeSplitWheelTint(lookGrade.highlightHue, lookGrade.highlightSat) else Triple(0f, 0f, 0f)
    val doMono = hasLook && lookGrade.monochromeMix > 0.004f
    val monoMix = lookGrade.monochromeMix.coerceIn(0f, 1f)
    val bwR = lookGrade.bwWeightR
    val bwG = lookGrade.bwWeightG
    val bwB = lookGrade.bwWeightB

    val targetHue = config.targetHue
    val hueWidth = config.hueWidth.coerceAtLeast(4f)
    val hueFeather = config.hueFeather.coerceAtLeast(4f)
    val dH = config.deltaHue
    val dS = config.deltaSat
    val dL = config.deltaLum

    val bandDH = FloatArray(12) { colorBands.bands.getOrNull(it)?.deltaHue ?: 0f }
    val bandDS = FloatArray(12) { colorBands.bands.getOrNull(it)?.deltaSat ?: 0f }
    val bandDL = FloatArray(12) { colorBands.bands.getOrNull(it)?.deltaLum ?: 0f }
    val bandActive = BooleanArray(12) { colorBands.bands.getOrNull(it)?.isModified == true }

    val stripHeight = 128.coerceAtMost(h)
    val padBuf = IntArray(w * (stripHeight + 2))
    val outBuf = IntArray(w * stripHeight)
    val lumaPad = if (doSharp) FloatArray(w * (stripHeight + 2)) else FloatArray(0)
    val hsl = FloatArray(3)
    val rgbTmp = FloatArray(3)
    val inv255 = 1.0f / 255.0f

    var y = 0
    while (y < h) {
        val rows = minOf(stripHeight, h - y)
        val topPad = if (doSharp && y > 0) 1 else 0
        val botPad = if (doSharp && y + rows < h) 1 else 0
        val totalPadRows = rows + topPad + botPad
        work.getPixels(padBuf, 0, w, 0, y - topPad, w, totalPadRows)

        if (doSharp) {
            val totalPx = w * totalPadRows
            for (k in 0 until totalPx) {
                val px = padBuf[k]
                val pr = ((px ushr 16) and 0xFF) * inv255
                val pg = ((px ushr 8) and 0xFF) * inv255
                val pb = (px and 0xFF) * inv255
                lumaPad[k] = 0.2126f * pr + 0.7152f * pg + 0.0722f * pb
            }
        }

        for (ry in 0 until rows) {
            val padRow = ry + topPad
            val padRowOff = padRow * w
            val outRowOff = ry * w
            val rowN = (padRow - 1).coerceAtLeast(0) * w
            val rowS = (padRow + 1).coerceAtMost(totalPadRows - 1) * w

            for (rx in 0 until w) {
                val pIdx = padRowOff + rx
                val px = padBuf[pIdx]
                val a = px and -0x1000000
                var rf = ((px ushr 16) and 0xFF) * inv255
                var gf = ((px ushr 8) and 0xFF) * inv255
                var bf = (px and 0xFF) * inv255

                // Stage A: Edge-guarded luminance-only unsharp mask (keeps skin smooth & eyes/details crisp)
                if (doSharp) {
                    val xW = if (rx > 0) rx - 1 else 0
                    val xE = if (rx + 1 < w) rx + 1 else w - 1
                    val lumC = lumaPad[pIdx]
                    val lumAvg = (lumaPad[rowN + rx] + lumaPad[rowS + rx] + lumaPad[padRowOff + xW] + lumaPad[padRowOff + xE]) * 0.25f
                    val detail = lumC - lumAvg
                    val absD = abs(detail)
                    if (absD > 0.006f) {
                        val noiseGate = smoothstep01(0.006f, 0.025f, absD)
                        val haloGuard = 1.0f - smoothstep01(0.18f, 0.45f, absD)
                        val boost = detail * sharpAmt * noiseGate * haloGuard
                        rf = (rf + boost).coerceIn(0f, 1f)
                        gf = (gf + boost).coerceIn(0f, 1f)
                        bf = (bf + boost).coerceIn(0f, 1f)
                    }
                }

                // Stage B: Relative White Balance + Filmic Soft-Shoulder Exposure
                if (doWbExp) {
                    rf *= mulR
                    gf *= mulG
                    bf *= mulB
                    if (rf > 0.88f) rf /= (1.0f + (rf - 0.88f) * 0.55f)
                    if (gf > 0.88f) gf /= (1.0f + (gf - 0.88f) * 0.55f)
                    if (bf > 0.88f) bf /= (1.0f + (bf - 0.88f) * 0.55f)
                    rf = rf.coerceIn(0f, 1f)
                    gf = gf.coerceIn(0f, 1f)
                    bf = bf.coerceIn(0f, 1f)
                }

                // Stage C: Photographic Tone Zones (anchored above true black!) + Midpoint S-Curve
                if (doZones) {
                    val l = 0.2126f * rf + 0.7152f * gf + 0.0722f * bf
                    val oml = 1.0f - l
                    val shMask = oml * oml * smoothstep01(0.0f, 0.08f, l)
                    val hiMask = l * l * (1.0f - smoothstep01(0.96f, 1.0f, l) * 0.25f)
                    val blkMask = oml * oml * oml
                    val whtMask = l * l * l
                    var targetL = (
                        l +
                            lookGrade.shadows * 0.24f * shMask +
                            lookGrade.highlights * 0.22f * hiMask +
                            lookGrade.blacks * 0.10f * blkMask +
                            lookGrade.whites * 0.12f * whtMask
                        ).coerceIn(0f, 1f)
                    if (doContrastK) {
                        targetL = if (targetL <= 0.5f) {
                            0.5f * (2.0f * targetL).coerceAtLeast(0.00001f).pow(contrastK)
                        } else {
                            1.0f - 0.5f * (2.0f * (1.0f - targetL)).coerceAtLeast(0.00001f).pow(contrastK)
                        }
                    }
                    val lRatio = targetL / l.coerceAtLeast(0.001f)
                    val scale = 1.0f + (lRatio - 1.0f) * 0.92f
                    val lift = (targetL - l) * 0.08f
                    rf = (rf * scale + lift).coerceIn(0f, 1f)
                    gf = (gf * scale + lift).coerceIn(0f, 1f)
                    bf = (bf * scale + lift).coerceIn(0f, 1f)
                }

                // Stage D: Skin-Protecting Smart Vibrance + Global Saturation
                if (doVibSat) {
                    val luma = 0.2126f * rf + 0.7152f * gf + 0.0722f * bf
                    val maxC = maxOf(rf, gf, bf)
                    val minC = minOf(rf, gf, bf)
                    val curSat = maxC - minC
                    val skinMask = ((rf - gf) * 3.0f).coerceIn(0f, 1f) * ((gf - bf) * 3.0f).coerceIn(0f, 1f)
                    val vibBoost = lookGrade.vibrance * (1.0f - curSat) * (1.0f - skinMask * 0.65f)
                    val satFactor = (1.0f + lookGrade.saturation + vibBoost).coerceIn(0f, 2.0f)
                    rf = (luma + (rf - luma) * satFactor).coerceIn(0f, 1f)
                    gf = (luma + (gf - luma) * satFactor).coerceIn(0f, 1f)
                    bf = (luma + (bf - luma) * satFactor).coerceIn(0f, 1f)
                }

                // Stage E: Lightroom 3-Way Color Grading (Shadows / Midtones / Highlights Split Toning)
                if (hasSplit) {
                    val l = 0.2126f * rf + 0.7152f * gf + 0.0722f * bf
                    val oml = 1.0f - l
                    val wSh = oml * oml * smoothstep01(0.0f, 0.10f, l)
                    val wHi = l * l * (1.0f - smoothstep01(0.92f, 1.0f, l) * 0.70f)
                    val wMid = 4.0f * l * oml
                    rf = (rf + (shR * wSh + midR * wMid + hiR * wHi) * 0.42f).coerceIn(0f, 1f)
                    gf = (gf + (shG * wSh + midG * wMid + hiG * wHi) * 0.42f).coerceIn(0f, 1f)
                    bf = (bf + (shB * wSh + midB * wMid + hiB * wHi) * 0.42f).coerceIn(0f, 1f)
                }

                // 0) Gerçek Kızılötesi R↔B Kanal Takası (Channel Swap)
                if (channelSwap) {
                    val tmp = rf
                    rf = bf
                    bf = tmp
                }

                // 1) 8-Bantlı Renk Histogramı Mikseri (çan-eğrisi parlaklık korumasıyla pürüzsüz geçiş)
                if (hasBands) {
                    rgbToHsl01(rf, gf, bf, hsl)
                    val hVal = hsl[0]
                    val sVal = hsl[1]
                    val lVal = hsl[2]

                    val satW = ((sVal - 0.05f) / 0.14f).coerceIn(0f, 1f)
                    val wS = satW * satW * (3f - 2f * satW)
                    val lumLow = ((lVal - 0.04f) / 0.10f).coerceIn(0f, 1f)
                    val lumHigh = 1.0f - ((lVal - 0.93f) / 0.06f).coerceIn(0f, 1f)
                    val mask = wS * lumLow * lumHigh

                    if (mask > 0.001f) {
                        val scaled = (((hVal % 360f) + 360f) % 360f) / 30f
                        val idxA = scaled.toInt().coerceIn(0, 11)
                        val idxB = (idxA + 1) % 12
                        val t = (scaled - idxA).coerceIn(0f, 1f)
                        val s = t * t * (3f - 2f * t)
                        val wA = if (bandActive[idxA]) (1f - s) * mask else 0f
                        val wB = if (bandActive[idxB]) s * mask else 0f
                        val wTotal = (wA + wB).coerceIn(0f, 1f)
                        if (wTotal > 0.001f) {
                            val lumBell = 4.0f * lVal * (1.0f - lVal)
                            var accR = rf * (1f - wTotal)
                            var accG = gf * (1f - wTotal)
                            var accB = bf * (1f - wTotal)
                            if (wA > 0.001f) {
                                var nh = (hVal + bandDH[idxA]) % 360f
                                if (nh < 0f) nh += 360f
                                val ns = (sVal * (1f + bandDS[idxA])).coerceIn(0f, 1f)
                                val nl = (lVal + bandDL[idxA] * 0.42f * lumBell).coerceIn(0f, 1f)
                                hslToRgb01(nh, ns, nl, rgbTmp)
                                accR += rgbTmp[0] * wA
                                accG += rgbTmp[1] * wA
                                accB += rgbTmp[2] * wA
                            }
                            if (wB > 0.001f) {
                                var nh = (hVal + bandDH[idxB]) % 360f
                                if (nh < 0f) nh += 360f
                                val ns = (sVal * (1f + bandDS[idxB])).coerceIn(0f, 1f)
                                val nl = (lVal + bandDL[idxB] * 0.42f * lumBell).coerceIn(0f, 1f)
                                hslToRgb01(nh, ns, nl, rgbTmp)
                                accR += rgbTmp[0] * wB
                                accG += rgbTmp[1] * wB
                                accB += rgbTmp[2] * wB
                            }
                            rf = accR.coerceIn(0f, 1f)
                            gf = accG.coerceIn(0f, 1f)
                            bf = accB.coerceIn(0f, 1f)
                        }
                    }
                }

                // 2) Noktasal Renk Pipeti (isteğe bağlı ek katman)
                if (hasPoint) {
                    rgbToHsl01(rf, gf, bf, hsl)
                    val hVal = hsl[0]
                    val sVal = hsl[1]
                    val lVal = hsl[2]

                    var diffH = abs(hVal - targetHue)
                    if (diffH > 180f) diffH = 360f - diffH

                    val wH = when {
                        diffH <= hueWidth -> 1.0f
                        diffH <= hueWidth + hueFeather -> {
                            val t = (diffH - hueWidth) / hueFeather
                            1.0f - t * t * (3.0f - 2.0f * t)
                        }
                        else -> 0.0f
                    }

                    if (wH > 0.001f) {
                        val satWeight = ((sVal - 0.06f) / 0.14f).coerceIn(0f, 1f)
                        val wS = satWeight * satWeight * (3f - 2f * satWeight)
                        val lumLow = ((lVal - 0.04f) / 0.10f).coerceIn(0f, 1f)
                        val lumHigh = 1.0f - ((lVal - 0.92f) / 0.07f).coerceIn(0f, 1f)
                        val wL = lumLow * lumHigh

                        val weight = wH * wS * wL
                        if (weight > 0.001f) {
                            var newH = (hVal + dH) % 360f
                            if (newH < 0f) newH += 360f
                            val newS = (sVal * (1.0f + dS)).coerceIn(0f, 1f)
                            val lumBell = 4.0f * lVal * (1.0f - lVal)
                            val newL = (lVal + dL * 0.42f * lumBell).coerceIn(0f, 1f)
                            hslToRgb01(newH, newS, newL, rgbTmp)
                            rf = (rf + (rgbTmp[0] - rf) * weight).coerceIn(0f, 1f)
                            gf = (gf + (rgbTmp[1] - gf) * weight).coerceIn(0f, 1f)
                            bf = (bf + (rgbTmp[2] - bf) * weight).coerceIn(0f, 1f)
                        }
                    }
                }

                // Stage F: True Channel-Mixed Monochrome (e.g. Fujifilm Acros B&W)
                if (doMono) {
                    val mono = (rf * bwR + gf * bwG + bf * bwB).coerceIn(0f, 1f)
                    rf += (mono - rf) * monoMix
                    gf += (mono - gf) * monoMix
                    bf += (mono - bf) * monoMix
                }

                val ri = (rf * 255.0f).roundToInt().coerceIn(0, 255)
                val gi = (gf * 255.0f).roundToInt().coerceIn(0, 255)
                val bi = (bf * 255.0f).roundToInt().coerceIn(0, 255)
                outBuf[outRowOff + rx] = a or (ri shl 16) or (gi shl 8) or bi
            }
        }
        work.setPixels(outBuf, 0, w, 0, y, w, rows)
        y += rows
    }
    return work
}

private data class AutoContrastResult(
    val recommendedContrast: Float,
    val recommendedEvIndexDelta: Int,
    val blackPointPercentile: Float,
    val whitePointPercentile: Float
)

/**
 * Robust non-AI Auto Contrast & Color Levels using 0.8% - 99.2% cumulative histogram percentile clipping.
 */
private fun calculateAutoContrastFromPreview(
    bitmap: Bitmap,
    aeStep: Float,
    currentEv: Int,
    aeRange: Range<Int>
): AutoContrastResult {
    val w = bitmap.width
    val h = bitmap.height
    val hist = IntArray(256)
    var total = 0

    val stepX = maxOf(1, w / 20)
    val stepY = maxOf(1, h / 20)
    var y = h / 10
    while (y < (h * 9) / 10) {
        var x = w / 10
        while (x < (w * 9) / 10) {
            val c = bitmap[x, y]
            val luma = (0.299f * AndroidColor.red(c) + 0.587f * AndroidColor.green(c) + 0.114f * AndroidColor.blue(c)).roundToInt()
            hist[luma.coerceIn(0, 255)]++
            total++
            x += stepX
        }
        y += stepY
    }

    if (total == 0) return AutoContrastResult(1.0f, 0, 0f, 255f)

    val shadowCut = (total * 0.008f).roundToInt()
    val highlightCut = (total * 0.992f).roundToInt()

    var accum = 0
    var pLow = 0
    var pHigh = 255
    for (i in 0 until 256) {
        accum += hist[i]
        if (accum >= shadowCut && pLow == 0) {
            pLow = i
        }
        if (accum >= highlightCut) {
            pHigh = i
            break
        }
    }

    val dynamicRange = (pHigh - pLow).coerceAtLeast(20)
    // Algısal sRGB S-eğrisi ile uyumlu, orta ton parlaklığını koruyan dengeli kontrast önerisi
    val idealContrast = when {
        dynamicRange < 110 -> 1.22f
        dynamicRange < 145 -> 1.14f
        dynamicRange < 180 -> 1.08f
        dynamicRange > 225 -> 0.94f
        else -> 1.00f
    }

    return AutoContrastResult(
        recommendedContrast = idealContrast,
        recommendedEvIndexDelta = 0,
        blackPointPercentile = pLow.toFloat(),
        whitePointPercentile = pHigh.toFloat()
    )
}

private const val HSL_SKSL = """
    uniform shader uContent;
    uniform float uLookActive;
    uniform float uLookSharpness;
    uniform vec3 uLookWbGains;
    uniform float uLookExpScale;
    uniform float uLookContrast;
    uniform float uLookHighlights;
    uniform float uLookShadows;
    uniform float uLookWhites;
    uniform float uLookBlacks;
    uniform float uLookVibrance;
    uniform float uLookSat;
    uniform float uSplitActive;
    uniform vec3 uSplitShadow;
    uniform vec3 uSplitMid;
    uniform vec3 uSplitHigh;
    uniform float uMonoMix;
    uniform vec3 uBwWeights;
    uniform float uChannelSwap;
    uniform float uTargetHue;
    uniform float uHueWidth;
    uniform float uHueFeather;
    uniform float uDeltaHue;
    uniform float uDeltaSat;
    uniform float uDeltaLum;
    uniform float uActive;
    uniform float uBandsActive;
    uniform vec4 uBandHueA;
    uniform vec4 uBandHueB;
    uniform vec4 uBandHueC;
    uniform vec4 uBandSatA;
    uniform vec4 uBandSatB;
    uniform vec4 uBandSatC;
    uniform vec4 uBandLumA;
    uniform vec4 uBandLumB;
    uniform vec4 uBandLumC;

    vec3 rgb2hsl(vec3 c) {
        float cMin = min(min(c.r, c.g), c.b);
        float cMax = max(max(c.r, c.g), c.b);
        float delta = cMax - cMin;
        float l = (cMax + cMin) * 0.5;
        float s = 0.0;
        float h = 0.0;
        if (delta > 0.001) {
            s = l < 0.5 ? delta / (cMax + cMin) : delta / (2.0 - cMax - cMin);
            if (c.r >= cMax) {
                h = (c.g - c.b) / delta + (c.g < c.b ? 6.0 : 0.0);
            } else if (c.g >= cMax) {
                h = (c.b - c.r) / delta + 2.0;
            } else {
                h = (c.r - c.g) / delta + 4.0;
            }
            h *= 60.0;
        }
        return vec3(h, s, l);
    }

    float hue2rgb(float p, float q, float t) {
        if (t < 0.0) t += 1.0;
        if (t > 1.0) t -= 1.0;
        if (t < 0.1666667) return p + (q - p) * 6.0 * t;
        if (t < 0.5) return q;
        if (t < 0.6666667) return p + (q - p) * (0.6666667 - t) * 6.0;
        return p;
    }

    vec3 hsl2rgb(vec3 hsl) {
        float h = hsl.x / 360.0;
        float s = hsl.y;
        float l = hsl.z;
        if (s <= 0.001) return vec3(l);
        float q = l < 0.5 ? l * (1.0 + s) : l + s - l * s;
        float p = 2.0 * l - q;
        return vec3(
            hue2rgb(p, q, h + 0.3333333),
            hue2rgb(p, q, h),
            hue2rgb(p, q, h - 0.3333333)
        );
    }

    vec3 getBandDelta(int idx) {
        if (idx == 0) return vec3(uBandHueA.x, uBandSatA.x, uBandLumA.x);
        if (idx == 1) return vec3(uBandHueA.y, uBandSatA.y, uBandLumA.y);
        if (idx == 2) return vec3(uBandHueA.z, uBandSatA.z, uBandLumA.z);
        if (idx == 3) return vec3(uBandHueA.w, uBandSatA.w, uBandLumA.w);
        if (idx == 4) return vec3(uBandHueB.x, uBandSatB.x, uBandLumB.x);
        if (idx == 5) return vec3(uBandHueB.y, uBandSatB.y, uBandLumB.y);
        if (idx == 6) return vec3(uBandHueB.z, uBandSatB.z, uBandLumB.z);
        if (idx == 7) return vec3(uBandHueB.w, uBandSatB.w, uBandLumB.w);
        if (idx == 8) return vec3(uBandHueC.x, uBandSatC.x, uBandLumC.x);
        if (idx == 9) return vec3(uBandHueC.y, uBandSatC.y, uBandLumC.y);
        if (idx == 10) return vec3(uBandHueC.z, uBandSatC.z, uBandLumC.z);
        return vec3(uBandHueC.w, uBandSatC.w, uBandLumC.w);
    }

    vec4 main(vec2 fragCoord) {
        vec4 color = uContent.eval(fragCoord);
        vec3 rgb = color.rgb;

        // A) Edge-guarded luminance-only optical unsharp mask (keeps skin smooth & eyes razor-sharp)
        if (uLookSharpness > 0.004) {
            vec3 cN = uContent.eval(fragCoord + vec2(0.0, -1.0)).rgb;
            vec3 cS = uContent.eval(fragCoord + vec2(0.0,  1.0)).rgb;
            vec3 cW = uContent.eval(fragCoord + vec2(-1.0, 0.0)).rgb;
            vec3 cE = uContent.eval(fragCoord + vec2( 1.0, 0.0)).rgb;
            vec3 avg = (cN + cS + cW + cE) * 0.25;
            float lumC = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
            float lumAvg = dot(avg, vec3(0.2126, 0.7152, 0.0722));
            float detail = lumC - lumAvg;
            float absD = abs(detail);
            float noiseGate = smoothstep(0.006, 0.025, absD);
            float haloGuard = 1.0 - smoothstep(0.18, 0.45, absD);
            float boost = detail * uLookSharpness * 1.35 * noiseGate * haloGuard;
            rgb = clamp(rgb + vec3(boost), 0.0, 1.0);
        }

        // B-E) Pro Lightroom Look Grade (WB + Soft-Shoulder Exposure + Tone Zones + Vibrance + 3-Way Split Toning)
        if (uLookActive > 0.5) {
            rgb = rgb * uLookWbGains * uLookExpScale;
            rgb = rgb / (1.0 + max(rgb - vec3(0.88), vec3(0.0)) * 0.55);
            rgb = clamp(rgb, 0.0, 1.0);

            float l = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
            float oml = 1.0 - l;
            float shMask = oml * oml * smoothstep(0.0, 0.08, l);
            float hiMask = l * l * (1.0 - smoothstep(0.96, 1.0, l) * 0.25);
            float blkMask = oml * oml * oml;
            float whtMask = l * l * l;
            float targetL = clamp(
                l + uLookShadows * 0.24 * shMask
                  + uLookHighlights * 0.22 * hiMask
                  + uLookBlacks * 0.10 * blkMask
                  + uLookWhites * 0.12 * whtMask,
                0.0, 1.0
            );
            if (abs(uLookContrast) > 0.004) {
                float k = clamp(1.0 + uLookContrast * 0.85, 0.70, 1.55);
                targetL = targetL <= 0.5
                    ? 0.5 * pow(max(2.0 * targetL, 0.00001), k)
                    : 1.0 - 0.5 * pow(max(2.0 * (1.0 - targetL), 0.00001), k);
            }
            float lRatio = targetL / max(l, 0.001);
            rgb = clamp(rgb * mix(1.0, lRatio, 0.92) + vec3((targetL - l) * 0.08), 0.0, 1.0);

            if (abs(uLookVibrance) > 0.004 || abs(uLookSat) > 0.004) {
                float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
                float maxC = max(max(rgb.r, rgb.g), rgb.b);
                float minC = min(min(rgb.r, rgb.g), rgb.b);
                float curSat = maxC - minC;
                float skinMask = clamp((rgb.r - rgb.g) * 3.0, 0.0, 1.0) * clamp((rgb.g - rgb.b) * 3.0, 0.0, 1.0);
                float vibBoost = uLookVibrance * (1.0 - curSat) * (1.0 - skinMask * 0.65);
                float satFactor = clamp(1.0 + uLookSat + vibBoost, 0.0, 2.0);
                rgb = clamp(mix(vec3(luma), rgb, satFactor), 0.0, 1.0);
            }

            if (uSplitActive > 0.5) {
                float ls = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
                float omls = 1.0 - ls;
                float wSh = omls * omls * smoothstep(0.0, 0.10, ls);
                float wHi = ls * ls * (1.0 - smoothstep(0.92, 1.0, ls) * 0.70);
                float wMid = 4.0 * ls * omls;
                rgb = clamp(rgb + (uSplitShadow * wSh + uSplitMid * wMid + uSplitHigh * wHi) * 0.42, 0.0, 1.0);
            }
        }

        // 0) Kızılötesi R↔B Kanal Takası
        if (uChannelSwap > 0.5) {
            rgb = rgb.bgr;
        }

        // 1) 12-Bantlı Sık Renk Histogramı Mikseri (30° uniform aralık + çan-eğrisi parlaklık koruması)
        if (uBandsActive > 0.5) {
            vec3 hsl = rgb2hsl(rgb);
            float satClamp = clamp((hsl.y - 0.05) / 0.14, 0.0, 1.0);
            float wS = satClamp * satClamp * (3.0 - 2.0 * satClamp);
            float lumLow = clamp((hsl.z - 0.04) / 0.10, 0.0, 1.0);
            float lumHigh = 1.0 - clamp((hsl.z - 0.93) / 0.06, 0.0, 1.0);
            float mask = wS * lumLow * lumHigh;

            if (mask > 0.001) {
                float scaled = mod(max(hsl.x, 0.0), 360.0) / 30.0;
                int idxA = int(floor(scaled));
                if (idxA < 0) idxA = 0;
                if (idxA >= 12) idxA = 0;
                int idxB = idxA + 1;
                if (idxB >= 12) idxB = 0;
                float t = fract(scaled);
                float s = t * t * (3.0 - 2.0 * t);
                vec3 dA = getBandDelta(idxA);
                vec3 dB = getBandDelta(idxB);
                bool actA = (abs(dA.x) > 0.5 || abs(dA.y) > 0.01 || abs(dA.z) > 0.01);
                bool actB = (abs(dB.x) > 0.5 || abs(dB.y) > 0.01 || abs(dB.z) > 0.01);
                float wA = actA ? (1.0 - s) * mask : 0.0;
                float wB = actB ? s * mask : 0.0;
                float wTot = clamp(wA + wB, 0.0, 1.0);
                if (wTot > 0.001) {
                    float lumBell = 4.0 * hsl.z * (1.0 - hsl.z);
                    vec3 acc = rgb * (1.0 - wTot);
                    if (wA > 0.001) {
                        vec3 shA = vec3(
                            mod(hsl.x + dA.x + 360.0, 360.0),
                            clamp(hsl.y * (1.0 + dA.y), 0.0, 1.0),
                            clamp(hsl.z + dA.z * 0.42 * lumBell, 0.0, 1.0)
                        );
                        acc += hsl2rgb(shA) * wA;
                    }
                    if (wB > 0.001) {
                        vec3 shB = vec3(
                            mod(hsl.x + dB.x + 360.0, 360.0),
                            clamp(hsl.y * (1.0 + dB.y), 0.0, 1.0),
                            clamp(hsl.z + dB.z * 0.42 * lumBell, 0.0, 1.0)
                        );
                        acc += hsl2rgb(shB) * wB;
                    }
                    rgb = acc;
                }
            }
        }

        // 2) Noktasal Renk Pipeti katmanı
        if (uActive > 0.5) {
            vec3 hsl = rgb2hsl(rgb);
            float dH = abs(hsl.x - uTargetHue);
            dH = min(dH, 360.0 - dH);

            float wH = 0.0;
            if (dH <= uHueWidth) {
                wH = 1.0;
            } else if (dH <= uHueWidth + uHueFeather) {
                float t = (dH - uHueWidth) / uHueFeather;
                wH = 1.0 - t * t * (3.0 - 2.0 * t);
            }

            float satClamp = clamp((hsl.y - 0.06) / 0.14, 0.0, 1.0);
            float wS = satClamp * satClamp * (3.0 - 2.0 * satClamp);
            float lumLow = clamp((hsl.z - 0.04) / 0.10, 0.0, 1.0);
            float lumHigh = 1.0 - clamp((hsl.z - 0.92) / 0.07, 0.0, 1.0);
            float wL = lumLow * lumHigh;

            float weight = wH * wS * wL;
            if (weight > 0.001) {
                float lumBell = 4.0 * hsl.z * (1.0 - hsl.z);
                vec3 shifted = hsl;
                shifted.x = mod(hsl.x + uDeltaHue + 360.0, 360.0);
                shifted.y = clamp(hsl.y * (1.0 + uDeltaSat), 0.0, 1.0);
                shifted.z = clamp(hsl.z + uDeltaLum * 0.42 * lumBell, 0.0, 1.0);
                rgb = mix(rgb, hsl2rgb(shifted), weight);
            }
        }

        // F) True Channel-Mixed Monochrome (e.g. Fujifilm Acros B&W)
        if (uMonoMix > 0.004) {
            float mono = clamp(dot(rgb, uBwWeights), 0.0, 1.0);
            rgb = mix(rgb, vec3(mono), clamp(uMonoMix, 0.0, 1.0));
        }

        return vec4(clamp(rgb, 0.0, 1.0), color.a);
    }
"""

private fun updateTextureViewShader(
    view: TextureView?,
    config: TargetedColorConfig,
    colorBands: ColorBandsConfig,
    channelSwap: Boolean = false,
    lookGrade: LookGradeParams = LookGradeParams(),
    bypass: Boolean = false,
    preTiramisuGlRenderer: PreTiramisuGlPreviewRenderer? = null
) {
    if (view == null) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
            val hasPoint = config.isActive && (config.deltaHue != 0f || config.deltaSat != 0f || config.deltaLum != 0f)
            val hasBands = colorBands.hasAnyModification
            val hasLook = lookGrade.isActive
            if (bypass || (!hasLook && !channelSwap && !hasPoint && !hasBands)) {
                view.setRenderEffect(null)
                return
            }
            val shader = RuntimeShader(HSL_SKSL)
            val (wbR, wbG, wbB) = if (hasLook) computeLookWbGains(lookGrade.tempShift, lookGrade.tintShift) else Triple(1f, 1f, 1f)
            val hasSplit = hasLook && lookGrade.hasSplitToning
            val (shR, shG, shB) = if (hasSplit) computeSplitWheelTint(lookGrade.shadowHue, lookGrade.shadowSat) else Triple(0f, 0f, 0f)
            val (midR, midG, midB) = if (hasSplit) computeSplitWheelTint(lookGrade.midtoneHue, lookGrade.midtoneSat) else Triple(0f, 0f, 0f)
            val (hiR, hiG, hiB) = if (hasSplit) computeSplitWheelTint(lookGrade.highlightHue, lookGrade.highlightSat) else Triple(0f, 0f, 0f)

            shader.setFloatUniform("uLookActive", if (hasLook) 1.0f else 0.0f)
            shader.setFloatUniform("uLookSharpness", if (hasLook) lookGrade.sharpness else 0.0f)
            shader.setFloatUniform("uLookWbGains", wbR, wbG, wbB)
            shader.setFloatUniform("uLookExpScale", if (hasLook) 2.0f.pow(lookGrade.exposureEv) else 1.0f)
            shader.setFloatUniform("uLookContrast", if (hasLook) lookGrade.contrast else 0.0f)
            shader.setFloatUniform("uLookHighlights", if (hasLook) lookGrade.highlights else 0.0f)
            shader.setFloatUniform("uLookShadows", if (hasLook) lookGrade.shadows else 0.0f)
            shader.setFloatUniform("uLookWhites", if (hasLook) lookGrade.whites else 0.0f)
            shader.setFloatUniform("uLookBlacks", if (hasLook) lookGrade.blacks else 0.0f)
            shader.setFloatUniform("uLookVibrance", if (hasLook) lookGrade.vibrance else 0.0f)
            shader.setFloatUniform("uLookSat", if (hasLook) lookGrade.saturation else 0.0f)
            shader.setFloatUniform("uSplitActive", if (hasSplit) 1.0f else 0.0f)
            shader.setFloatUniform("uSplitShadow", shR, shG, shB)
            shader.setFloatUniform("uSplitMid", midR, midG, midB)
            shader.setFloatUniform("uSplitHigh", hiR, hiG, hiB)
            shader.setFloatUniform("uMonoMix", if (hasLook) lookGrade.monochromeMix else 0.0f)
            shader.setFloatUniform("uBwWeights", lookGrade.bwWeightR, lookGrade.bwWeightG, lookGrade.bwWeightB)

            shader.setFloatUniform("uChannelSwap", if (channelSwap) 1.0f else 0.0f)
            shader.setFloatUniform("uTargetHue", config.targetHue)
            shader.setFloatUniform("uHueWidth", config.hueWidth)
            shader.setFloatUniform("uHueFeather", config.hueFeather)
            shader.setFloatUniform("uDeltaHue", config.deltaHue)
            shader.setFloatUniform("uDeltaSat", config.deltaSat)
            shader.setFloatUniform("uDeltaLum", config.deltaLum)
            shader.setFloatUniform("uActive", if (hasPoint) 1.0f else 0.0f)
            shader.setFloatUniform("uBandsActive", if (hasBands) 1.0f else 0.0f)
            val b = colorBands.bands
            fun bd(idx: Int) = b.getOrElse(idx) { BandHslAdjustment() }
            shader.setFloatUniform("uBandHueA", bd(0).deltaHue, bd(1).deltaHue, bd(2).deltaHue, bd(3).deltaHue)
            shader.setFloatUniform("uBandHueB", bd(4).deltaHue, bd(5).deltaHue, bd(6).deltaHue, bd(7).deltaHue)
            shader.setFloatUniform("uBandHueC", bd(8).deltaHue, bd(9).deltaHue, bd(10).deltaHue, bd(11).deltaHue)
            shader.setFloatUniform("uBandSatA", bd(0).deltaSat, bd(1).deltaSat, bd(2).deltaSat, bd(3).deltaSat)
            shader.setFloatUniform("uBandSatB", bd(4).deltaSat, bd(5).deltaSat, bd(6).deltaSat, bd(7).deltaSat)
            shader.setFloatUniform("uBandSatC", bd(8).deltaSat, bd(9).deltaSat, bd(10).deltaSat, bd(11).deltaSat)
            shader.setFloatUniform("uBandLumA", bd(0).deltaLum, bd(1).deltaLum, bd(2).deltaLum, bd(3).deltaLum)
            shader.setFloatUniform("uBandLumB", bd(4).deltaLum, bd(5).deltaLum, bd(6).deltaLum, bd(7).deltaLum)
            shader.setFloatUniform("uBandLumC", bd(8).deltaLum, bd(9).deltaLum, bd(10).deltaLum, bd(11).deltaLum)
            val effect = RenderEffect.createRuntimeShaderEffect(shader, "uContent")
            view.setRenderEffect(effect)
        } catch (e: Throwable) {
            Log.e("CameraScreen", "RenderEffect not supported on this GPU/HAL", e)
        }
    } else {
        preTiramisuGlRenderer?.updateUniforms(
            config = config,
            colorBands = colorBands,
            channelSwap = channelSwap,
            lookGrade = lookGrade,
            bypass = bypass
        )
    }
}







private data class MeteringStats(
    val avgLuma: Float,
    val maxLuma: Float,
    val highlightRatio: Float
)

/**
 * Measures average luminance and highlight saturation (0..255) inside the selected
 * Exposure Box or across the full frame. Used for intelligent highlight preservation.
 */
private fun measureRegionLuminance(
    bitmap: Bitmap,
    mode: ExposureMeteringMode,
    boxNorm: Offset,
    boxSizeNorm: Float
): MeteringStats {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return MeteringStats(128f, 128f, 0f)

    val (x0, x1, y0, y1) = if (mode == ExposureMeteringMode.BOX_SPOT) {
        val boxW = (minOf(w, h) * boxSizeNorm).roundToInt().coerceAtLeast(12)
        val cx = (boxNorm.x * w).roundToInt()
        val cy = (boxNorm.y * h).roundToInt()
        val left = (cx - boxW / 2).coerceIn(0, (w - boxW).coerceAtLeast(0))
        val top = (cy - boxW / 2).coerceIn(0, (h - boxW).coerceAtLeast(0))
        listOf(left, (left + boxW).coerceAtMost(w), top, (top + boxW).coerceAtMost(h))
    } else {
        listOf((w * 0.05f).roundToInt(), (w * 0.95f).roundToInt(), (h * 0.05f).roundToInt(), (h * 0.95f).roundToInt())
    }

    val stepX = maxOf(1, (x1 - x0) / 28)
    val stepY = maxOf(1, (y1 - y0) / 28)
    var lumaSum = 0.0
    var sampleCount = 0
    var maxLuma = 0.0
    var highlightCount = 0

    var py = y0
    while (py < y1) {
        var px = x0
        while (px < x1) {
            val c = bitmap[px, py]
            val luma = 0.299 * AndroidColor.red(c) + 0.587 * AndroidColor.green(c) + 0.114 * AndroidColor.blue(c)
            lumaSum += luma
            sampleCount++
            if (luma > maxLuma) maxLuma = luma
            if (luma > 220.0) highlightCount++
            px += stepX
        }
        py += stepY
    }
    val avg = if (sampleCount > 0) (lumaSum / sampleCount).toFloat().coerceIn(0f, 255f) else 128f
    val ratio = if (sampleCount > 0) highlightCount.toFloat() / sampleCount.toFloat() else 0f
    return MeteringStats(avg, maxLuma.toFloat(), ratio)
}

// 12 uniform 30° renk bandı sınırları (Kırmızı 0° tam 0. sütunun ortasında, 345°..15°, her bant 30°)
private fun hueToPerceptualBandPos(hueDeg: Float): Float {
    val shifted = ((hueDeg + 15f) % 360f + 360f) % 360f
    return (shifted / 30f).coerceIn(0f, 11.9999f)
}

private fun perceptualBandPosToHue(pos: Float): Float {
    val clamped = pos.coerceIn(0f, 11.9999f)
    return ((clamped * 30f - 15f) % 360f + 360f) % 360f
}

// Önizleme görüntüsünden 48 sütunlu profesyonel RGB + Parlaklık + Algısal Bant Hizalı Renk Tonu (Hue) histogramı üretir
internal fun computeProHistogramData(bitmap: Bitmap, bins: Int = 48): ProHistogramData {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return ProHistogramData()

    val stepX = maxOf(1, w / 48)
    val stepY = maxOf(1, h / 48)
    val rCounts = FloatArray(bins)
    val gCounts = FloatArray(bins)
    val bCounts = FloatArray(bins)
    val lCounts = FloatArray(bins)
    val hueWeights = FloatArray(bins)
    val hsv = FloatArray(3)
    var totalPixels = 0
    var shadowClipped = 0
    var highlightClipped = 0

    var py = 0
    while (py < h) {
        var px = 0
        while (px < w) {
            val c = bitmap[px, py]
            val r = AndroidColor.red(c)
            val g = AndroidColor.green(c)
            val b = AndroidColor.blue(c)
            val luma = 0.299f * r + 0.587f * g + 0.114f * b

            rCounts[((r / 256f) * bins).toInt().coerceIn(0, bins - 1)] += 1f
            gCounts[((g / 256f) * bins).toInt().coerceIn(0, bins - 1)] += 1f
            bCounts[((b / 256f) * bins).toInt().coerceIn(0, bins - 1)] += 1f
            lCounts[((luma / 256f) * bins).toInt().coerceIn(0, bins - 1)] += 1f

            if (luma <= 5f) shadowClipped++
            if (luma >= 250f) highlightClipped++
            totalPixels++

            // Doygunluk ağırlıklı renk tonu (Hue) dağılımı — 12 bant sütunuyla 1:1 hizalı
            AndroidColor.RGBToHSV(r, g, b, hsv)
            val sat = hsv[1]
            val lum = hsv[2]
            if (sat > 0.10f && lum > 0.06f && lum < 0.96f) {
                val bandPos = hueToPerceptualBandPos(hsv[0])
                val hueBin = ((bandPos / 12f) * bins).toInt().coerceIn(0, bins - 1)
                hueWeights[hueBin] += sat * sat
            }
            px += stepX
        }
        py += stepY
    }

    fun smoothAndNormalize(arr: FloatArray, globalMax: Float, circular: Boolean = false): FloatArray {
        val out = FloatArray(bins)
        val denom = globalMax.coerceAtLeast(1f)
        for (i in 0 until bins) {
            val prev = if (i > 0) arr[i - 1] else if (circular) arr[bins - 1] else arr[0]
            val next = if (i < bins - 1) arr[i + 1] else if (circular) arr[0] else arr[bins - 1]
            val smoothed = (prev * 0.25f + arr[i] * 0.50f + next * 0.25f)
            // Hafif logaritmik sıkıştırma: tek bir tepe diğer tonları ezmez (Lightroom tarzı okunabilirlik)
            val ratio = (smoothed / denom).coerceIn(0f, 1f)
            out[i] = ratio.toDouble().pow(0.75).toFloat().coerceIn(0f, 1f)
        }
        return out
    }

    var rgbPeak = 1f
    for (i in 0 until bins) {
        if (rCounts[i] > rgbPeak) rgbPeak = rCounts[i]
        if (gCounts[i] > rgbPeak) rgbPeak = gCounts[i]
        if (bCounts[i] > rgbPeak) rgbPeak = bCounts[i]
        if (lCounts[i] > rgbPeak) rgbPeak = lCounts[i]
    }
    var huePeak = 0.01f
    for (i in 0 until bins) {
        if (hueWeights[i] > huePeak) huePeak = hueWeights[i]
    }

    val denomTotal = totalPixels.coerceAtLeast(1).toFloat()
    return ProHistogramData(
        r = smoothAndNormalize(rCounts, rgbPeak),
        g = smoothAndNormalize(gCounts, rgbPeak),
        b = smoothAndNormalize(bCounts, rgbPeak),
        luma = smoothAndNormalize(lCounts, rgbPeak),
        hueBins = smoothAndNormalize(hueWeights, huePeak, circular = true),
        shadowClip = (shadowClipped / denomTotal) > 0.015f,
        highlightClip = (highlightClipped / denomTotal) > 0.015f
    )
}

// Önizleme görüntüsünde aşırı parlak (yanık) bölgeleri 32x32 ızgara olarak işaretler (zebra uyarısı)
internal fun computeZebraGrid(bitmap: Bitmap, cols: Int = 32, rows: Int = 32, threshold: Float = 240f): BooleanArray {
    val grid = BooleanArray(cols * rows)
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return grid

    for (r in 0 until rows) {
        val py = ((r + 0.5f) * h / rows).toInt().coerceIn(0, h - 1)
        for (c in 0 until cols) {
            val px = ((c + 0.5f) * w / cols).toInt().coerceIn(0, w - 1)
            val color = bitmap[px, py]
            val luma = 0.299f * AndroidColor.red(color) + 0.587f * AndroidColor.green(color) + 0.114f * AndroidColor.blue(color)
            grid[r * cols + c] = luma >= threshold
        }
    }
    return grid
}

/**
 * Maps normalized portrait viewfinder exposure box coordinates onto the camera sensor active array.
 */
private fun buildAeMeteringRegion(
    activeArray: Rect?,
    sensorOrientation: Int,
    mode: ExposureMeteringMode,
    boxNorm: Offset,
    boxSizeNorm: Float
): Array<MeteringRectangle>? {
    if (activeArray == null || activeArray.width() <= 0 || activeArray.height() <= 0) return null
    if (mode == ExposureMeteringMode.FULL_SCREEN) {
        return arrayOf(
            MeteringRectangle(
                activeArray.left,
                activeArray.top,
                activeArray.width(),
                activeArray.height(),
                MeteringRectangle.METERING_WEIGHT_MAX
            )
        )
    }

    val nx = boxNorm.x.coerceIn(0.05f, 0.95f)
    val ny = boxNorm.y.coerceIn(0.05f, 0.95f)
    val (sx, sy) = when (sensorOrientation) {
        90 -> ny to (1f - nx)
        270 -> (1f - ny) to nx
        180 -> (1f - nx) to (1f - ny)
        else -> nx to ny
    }

    val halfW = (activeArray.width() * boxSizeNorm * 0.5f).roundToInt().coerceAtLeast(16)
    val halfH = (activeArray.height() * boxSizeNorm * 0.5f).roundToInt().coerceAtLeast(16)
    val cx = activeArray.left + (sx * activeArray.width()).roundToInt()
    val cy = activeArray.top + (sy * activeArray.height()).roundToInt()
    val left = (cx - halfW).coerceIn(activeArray.left, (activeArray.right - 20).coerceAtLeast(activeArray.left))
    val top = (cy - halfH).coerceIn(activeArray.top, (activeArray.bottom - 20).coerceAtLeast(activeArray.top))
    val width = minOf(halfW * 2, activeArray.right - left).coerceAtLeast(16)
    val height = minOf(halfH * 2, activeArray.bottom - top).coerceAtLeast(16)

    return arrayOf(MeteringRectangle(left, top, width, height, MeteringRectangle.METERING_WEIGHT_MAX))
}

private data class AutoCalibrateResult(
    val kelvin: Float,
    val tint: Float,
    val avgLuma: Float,
    val foundNeutralCount: Int
)

/**
 * Analyzes preview pixels across a 24x24 grid, filters out IR foliage (R >> G ~ B),
 * and derives balanced Kelvin/Tint + brightness telemetry from neutral scene surfaces.
 */
private fun autoCalibrateSceneFromPreview(
    bitmap: Bitmap,
    currentGains: RggbChannelVector
): AutoCalibrateResult {
    val w = bitmap.width
    val h = bitmap.height
    var rSum = 0.0
    var gSum = 0.0
    var bSum = 0.0
    var neutCount = 0
    var lumaSum = 0.0
    var totalCount = 0

    val stepX = maxOf(1, w / 24)
    val stepY = maxOf(1, h / 24)

    var py = h / 12
    while (py < (h * 11) / 12) {
        var px = w / 12
        while (px < (w * 11) / 12) {
            val c = bitmap[px, py]
            val r = AndroidColor.red(c)
            val g = AndroidColor.green(c)
            val b = AndroidColor.blue(c)
            lumaSum += 0.299 * r + 0.587 * g + 0.114 * b
            totalCount++

            val maxC = maxOf(r, maxOf(g, b))
            val minC = minOf(r, minOf(g, b))
            val isLikelyFoliage = r > g * 1.18f && abs(g - b) < (r - g) * 0.45f
            if (maxC in 48..226 && !isLikelyFoliage && (maxC - minC) < maxC * 0.42f) {
                rSum += r
                gSum += g
                bSum += b
                neutCount++
            }
            px += stepX
        }
        py += stepY
    }

    val avgLuma = (lumaSum / totalCount.coerceAtLeast(1)).toFloat()
    if (neutCount < 6) {
        // Fallback: Gray-World average across midtones (35..240) instead of hard-coded yellow 3348K
        var allR = 0.0
        var allG = 0.0
        var allB = 0.0
        var allCnt = 0
        var y2 = h / 8
        while (y2 < (h * 7) / 8) {
            var x2 = w / 8
            while (x2 < (w * 7) / 8) {
                val c = bitmap[x2, y2]
                val rr = AndroidColor.red(c)
                val gg = AndroidColor.green(c)
                val bb = AndroidColor.blue(c)
                val mx = maxOf(rr, maxOf(gg, bb))
                if (mx in 35..240) {
                    allR += rr
                    allG += gg
                    allB += bb
                    allCnt++
                }
                x2 += stepX
            }
            y2 += stepY
        }
        if (allCnt < 4) {
            val (curK, curT) = gainsToKelvinTint(currentGains)
            return AutoCalibrateResult(
                kelvin = curK.coerceIn(KELVIN_MIN, KELVIN_MAX),
                tint = curT.coerceIn(-60f, 60f),
                avgLuma = avgLuma,
                foundNeutralCount = 0
            )
        }
        val (gwK, gwT) = inverseRgbToKelvinTint(
            rAvg = (allR / allCnt).toFloat(),
            gAvg = (allG / allCnt).toFloat(),
            bAvg = (allB / allCnt).toFloat(),
            currentGains = currentGains
        )
        return AutoCalibrateResult(
            kelvin = gwK.coerceIn(KELVIN_MIN, KELVIN_MAX),
            tint = gwT.coerceIn(-60f, 60f),
            avgLuma = avgLuma,
            foundNeutralCount = allCnt
        )
    }

    val (estK, estT) = inverseRgbToKelvinTint(
        rAvg = (rSum / neutCount).toFloat(),
        gAvg = (gSum / neutCount).toFloat(),
        bAvg = (bSum / neutCount).toFloat(),
        currentGains = currentGains
    )
    return AutoCalibrateResult(
        kelvin = estK.coerceIn(KELVIN_MIN, KELVIN_MAX),
        tint = estT.coerceIn(-60f, 60f),
        avgLuma = avgLuma,
        foundNeutralCount = neutCount
    )
}

internal fun readExifOrientation(bytes: ByteArray, cacheDir: File): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        try {
            return ExifInterface(bytes.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) {
        }
    }
    var tempFile: File? = null
    return try {
        tempFile = File.createTempFile("kameras_in_", ".jpg", cacheDir)
        FileOutputStream(tempFile).use { it.write(bytes) }
        ExifInterface(tempFile.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    } finally {
        try { tempFile?.delete() } catch (_: Exception) {}
    }
}

private fun jpegDegreesToExifOrientation(degrees: Int): Int {
    return when (((degrees % 360) + 360) % 360) {
        90 -> ExifInterface.ORIENTATION_ROTATE_90
        180 -> ExifInterface.ORIENTATION_ROTATE_180
        270 -> ExifInterface.ORIENTATION_ROTATE_270
        else -> ExifInterface.ORIENTATION_NORMAL
    }
}

internal fun saveOutputToGallery(
    context: Context,
    rawBytes: ByteArray?,
    processedBitmap: Bitmap?,
    exifOrientation: Int,
    onSavedUri: ((Uri) -> Unit)? = null
): Boolean {
    var tempOut: File? = null
    var uri: Uri? = null
    return try {
        val timestamp = System.currentTimeMillis()
        val displayName = "SpectraS_$timestamp.jpg"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/SpectraS")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return false

            if (processedBitmap != null) {
                tempOut = File.createTempFile("spectras_out_", ".jpg", context.cacheDir)
                FileOutputStream(tempOut).use { fos ->
                    processedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                    fos.flush()
                }
                if (exifOrientation != ExifInterface.ORIENTATION_NORMAL) {
                    try {
                        ExifInterface(tempOut.absolutePath).apply {
                            setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation.toString())
                            saveAttributes()
                        }
                    } catch (_: Exception) {}
                }
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(tempOut).use { inp -> inp.copyTo(out) }
                    out.flush()
                }
            } else if (rawBytes != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(rawBytes)
                    out.flush()
                }
            }

            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            onSavedUri?.invoke(uri)
            true
        } else {
            // Android 5–9 (API 21–28): DCIM/SpectraS klasörüne doğrudan yazıp MediaScanner ile galeriye anında kaydet
            @Suppress("DEPRECATION")
            val dcimDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "SpectraS")
            if (!dcimDir.exists()) dcimDir.mkdirs()
            val outFile = File(dcimDir, displayName)

            if (processedBitmap != null) {
                FileOutputStream(outFile).use { fos ->
                    processedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                    fos.flush()
                }
                if (exifOrientation != ExifInterface.ORIENTATION_NORMAL) {
                    try {
                        ExifInterface(outFile.absolutePath).apply {
                            setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation.toString())
                            saveAttributes()
                        }
                    } catch (_: Exception) {}
                }
            } else if (rawBytes != null) {
                FileOutputStream(outFile).use { fos ->
                    fos.write(rawBytes)
                    fos.flush()
                }
            } else {
                return false
            }

            MediaScannerConnection.scanFile(
                context,
                arrayOf(outFile.absolutePath),
                arrayOf("image/jpeg")
            ) { _, scannedUri ->
                if (scannedUri != null) {
                    onSavedUri?.invoke(scannedUri)
                }
            }
            true
        }
    } catch (e: Exception) {
        Log.e("CameraScreen", "Failed to save image", e)
        if (uri != null) {
            try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
        }
        false
    } finally {
        try { tempOut?.delete() } catch (_: Exception) {}
    }
}

/**
 * Evrensel RAW/DNG kayıt motoru (Android 5–15, Samsung / Xiaomi / Pixel / OnePlus / OPPO / Huawei uyumlu):
 * - DngCreator'a doğru EXIF yönünü işler (dikey fotoğraflar yan dönmez)
 * - API 29+ cihazlarda önce Images.Media, OEM reddederse Files.getContentUri("external") yedeğini kullanır
 * - API 21–28 cihazlarda DCIM/Kameras altına yazıp MediaScanner ile galeriye bildirir
 */
private fun saveRawDngToGallery(
    context: Context,
    characteristics: CameraCharacteristics,
    result: TotalCaptureResult,
    rawImage: android.media.Image,
    jpegOrientationDegrees: Int
): Boolean {
    var uri: Uri? = null
    val timestamp = System.currentTimeMillis()
    val displayName = "RAW_$timestamp.dng"
    val exifOrientation = jpegDegreesToExifOrientation(jpegOrientationDegrees)

    fun writeDngToStream(out: java.io.OutputStream) {
        val dngCreator = DngCreator(characteristics, result)
        try {
            try {
                dngCreator.setOrientation(exifOrientation)
            } catch (_: Exception) {}
            try {
                dngCreator.setDescription("SpectraS Infrared RAW")
            } catch (_: Exception) {}
            dngCreator.writeImage(out, rawImage)
            out.flush()
        } finally {
            try { dngCreator.close() } catch (_: Exception) {}
        }
    }

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val imgValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
                put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/SpectraS")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            // Bazı OEM'ler DNG'yi Images.Media yerine Files koleksiyonunda kabul eder
            uri = try {
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imgValues)
            } catch (_: Exception) {
                null
            }
            if (uri == null) {
                val fileValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/x-adobe-dng")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/SpectraS")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                uri = resolver.insert(MediaStore.Files.getContentUri("external"), fileValues)
            }
            val targetUri = uri ?: return false
            resolver.openOutputStream(targetUri)?.use { out ->
                writeDngToStream(out)
            } ?: return false
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(targetUri, done, null, null)
            true
        } else {
            @Suppress("DEPRECATION")
            val dcimDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "SpectraS")
            if (!dcimDir.exists()) dcimDir.mkdirs()
            val outFile = File(dcimDir, displayName)
            FileOutputStream(outFile).use { fos ->
                writeDngToStream(fos)
            }
            MediaScannerConnection.scanFile(
                context,
                arrayOf(outFile.absolutePath),
                arrayOf("image/x-adobe-dng"),
                null
            )
            true
        }
    } catch (e: Exception) {
        Log.e("CameraScreen", "RAW/DNG kaydedilemedi", e)
        if (uri != null) {
            try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
        }
        false
    }
}

// JPEG baytlarından EXIF yönü düzeltilmiş düşük bellekli küçük önizleme resmi üretir
private fun decodeThumbnailFromBytes(bytes: ByteArray, cacheDir: File): Bitmap? {
    return try {
        val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val orientation = readExifOrientation(bytes, cacheDir)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) {
            raw
        } else {
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            if (rotated !== raw) raw.recycle()
            rotated
        }
    } catch (_: Exception) {
        null
    }
}

// Tüm üreticilerin (Samsung, Xiaomi, Pixel, OnePlus/OPPO, Huawei vb.) Galeri uygulamalarını 4 kademeli yedekle açar
internal fun openGallerySafely(context: Context, lastUri: Uri?): Boolean {
    val candidates = buildList<Intent> {
        if (lastUri != null) {
            add(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(lastUri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        }
        add(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
            }
        )
        add(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY))
        add(
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
        )
    }
    for (intent in candidates) {
        try {
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (_: Exception) {
        }
    }
    return false
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CameraScreen(
    hasCameraPermission: Boolean,
    incomingEditUri: Uri? = null,
    incomingEditNonce: Int = 0,
    onRegisterVolumeShutter: ((() -> Unit)?) -> Unit = {},
    onRequestCameraPermission: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val cameraManager = remember { context.getSystemService<CameraManager>()!! }
    val holder = remember { CameraHolder() }
    val cameraThread = remember { HandlerThread("CameraThread").also { it.start() } }
    val cameraHandler = remember { Handler(cameraThread.looper) }
    val shutterSound = remember { try { MediaActionSound() } catch (_: Throwable) { null } }
    val coroutineScope = rememberCoroutineScope()
    val appLocale = remember { Locale.getDefault() }
    var shownPreTiramisuHslNotice by remember { mutableStateOf(false) }

    val hasFrontCamera = remember {
        try {
            val ids = cameraManager.cameraIdList
            val hasBack = ids.any {
                try {
                    cameraManager.getCameraCharacteristics(it)
                        .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                } catch (_: Exception) { false }
            }
            val hasFront = ids.any {
                try {
                    cameraManager.getCameraCharacteristics(it)
                        .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
                } catch (_: Exception) { false }
            }
            hasBack && hasFront
        } catch (_: Exception) {
            false
        }
    }

    val sharedPrefs = remember { context.getSharedPreferences("CameraPresetsPrefs", Context.MODE_PRIVATE) }
    var experienceMode by remember {
        mutableStateOf(ExperienceMode.fromKey(sharedPrefs.getString("experience_mode", null)))
    }
    var cameraReady by remember { mutableStateOf(false) }
    var isStudioMode by remember { mutableStateOf(incomingEditUri != null) }
    val currentIsStudioMode by rememberUpdatedState(isStudioMode)
    var studioPhotoUri by remember { mutableStateOf<Uri?>(incomingEditUri) }
    var studioPhotoNonce by remember { mutableIntStateOf(incomingEditNonce) }
    var isAutoMode by remember { mutableStateOf(experienceMode != ExperienceMode.IR_LAB) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var flashMode by remember { mutableStateOf(FlashMode.OFF) }
    var channelSwapEnabled by remember { mutableStateOf(false) }
    var aeLockEnabled by remember { mutableStateOf(false) }
    var kelvinValue by remember { mutableFloatStateOf(5500f) }
    var tintValue by remember { mutableFloatStateOf(0f) }
    var contrastValue by remember { mutableFloatStateOf(DEFAULT_CONTRAST) }
    var saturationValue by remember { mutableFloatStateOf(DEFAULT_SATURATION) }
    var exposureEvIndex by remember { mutableIntStateOf(0) }
    var aeRange by remember { mutableStateOf(Range(0, 0)) }
    var aeStep by remember { mutableFloatStateOf(0.5f) }

    var liveAutoKelvin by remember { mutableFloatStateOf(5500f) }
    var liveAutoTint by remember { mutableFloatStateOf(0f) }

    var activeTab by remember { mutableStateOf(ControlTab.BD) }
    var activeWbSubSlider by remember { mutableStateOf(WbSubSlider.KELVIN) }
    var colorPanelMode by remember { mutableStateOf(ColorPanelMode.GENEL) }
    var activeColorSubSlider by remember { mutableStateOf(ColorSubSlider.DOYGUNLUK) }
    var activePointColorSubSlider by remember { mutableStateOf(PointColorSubSlider.TON) }
    var selectedColorBands by remember { mutableStateOf(setOf(ColorBand.RED)) }
    var isMultiBandMode by remember { mutableStateOf(false) }
    var activeBandSubSlider by remember { mutableStateOf(PointColorSubSlider.TON) }
    var colorBandsConfig by remember { mutableStateOf(ColorBandsConfig()) }
    var isDeckCollapsed by remember { mutableStateOf(experienceMode == ExperienceMode.IR_LAB) }
    var isAnySliderDragging by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showPresetsListDialog by remember { mutableStateOf(false) }
    var showResolutionDialog by remember { mutableStateOf(false) }
    var showResetAllDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    var highlightProtectionEnabled by remember { mutableStateOf(true) }
    var meteringMode by remember { mutableStateOf(ExposureMeteringMode.FULL_SCREEN) }
    var isExposureBoxPicking by remember { mutableStateOf(false) }
    var exposureBoxNorm by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var exposureBoxSizeNorm by remember { mutableFloatStateOf(MeteringBoxSize.ORTA.norm) }
    var measuredRegionBrightness by remember { mutableIntStateOf(128) }
    var liveHistogram by remember { mutableStateOf(ProHistogramData()) }

    // Ek özellik durumları: OTO karşılaştırma, zebra, su terazisi, zamanlayıcı ve ilk açılış rehberi
    var isComparingAuto by remember { mutableStateOf(false) }
    var showZebra by remember { mutableStateOf(false) }
    var zebraGrid by remember { mutableStateOf(BooleanArray(32 * 32)) }
    var showLevel by remember { mutableStateOf(false) }
    var levelAngleDeg by remember { mutableFloatStateOf(0f) }
    var timerSeconds by remember { mutableIntStateOf(0) }
    var timerCountdown by remember { mutableIntStateOf(0) }
    var timerJob by remember { mutableStateOf<Job?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var availableZoomOptions by remember { mutableStateOf(listOf(1f)) }
    var supportsRaw by remember { mutableStateOf(false) }
    var rawEnabled by remember { mutableStateOf(false) }
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }
    var lastCapturedThumb by remember { mutableStateOf<ImageBitmap?>(null) }

    // Yalnızca kullanıcı bir ayarı değiştirdiğinde kısa süre gösterilen bildirim metni
    var statusBannerText by remember { mutableStateOf<String?>(null) }
    var statusBannerNonce by remember { mutableIntStateOf(0) }

    val availableResolutions = remember { mutableStateListOf<Size>() }
    var selectedResolution by remember { mutableStateOf<Size?>(null) }

    var isPickerActive by remember { mutableStateOf(false) }
    var isColorPipetteActive by remember { mutableStateOf(false) }
    var targetedColorConfig by remember { mutableStateOf(TargetedColorConfig()) }
    var pickedMarkerPos by remember { mutableStateOf<Offset?>(null) }

    var showExperienceWelcome by remember {
        mutableStateOf(!sharedPrefs.getBoolean("experience_mode_chosen_v1", false))
    }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val activeLanguage = remember { getSavedAppLanguage(context) }
    var selectedAestheticPreset by remember { mutableStateOf(AESTHETIC_PRESETS.first()) }
    var activeLookGrade by remember { mutableStateOf(LookGradeParams()) }
    var aestheticFilterStrength by remember { mutableFloatStateOf(1.0f) }
    var aestheticSubSlider by remember { mutableStateOf(AestheticSubSlider.STRENGTH) }
    var focusBoxVisible by remember { mutableStateOf(false) }
    var focusBoxTriggerNonce by remember { mutableIntStateOf(0) }

    // Su terazisi açıkken cihazın eğimini ivmeölçerden düşük güçle okur
    DisposableEffect(showLevel) {
        if (!showLevel) {
            onDispose { }
        } else {
            val sensorManager = context.getSystemService<SensorManager>()
            val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            var smoothedAngle = 0f
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val ax = event.values[0]
                    val ay = event.values[1]
                    val rawDeg = Math.toDegrees(atan2(ax.toDouble(), ay.toDouble())).toFloat()
                    smoothedAngle = smoothedAngle * 0.82f + rawDeg * 0.18f
                    levelAngleDeg = smoothedAngle.coerceIn(-45f, 45f)
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            if (accel != null) {
                sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
            }
            onDispose {
                sensorManager?.unregisterListener(listener)
            }
        }
    }
    val initialRatio = remember {
        val w = sharedPrefs.getInt("selected_res_w", -1)
        val h = sharedPrefs.getInt("selected_res_h", -1)
        if (w > 0 && h > 0) {
            (minOf(w, h).toFloat() / maxOf(w, h).toFloat()).coerceIn(0.4f, 2.0f)
        } else {
            3f / 4f
        }
    }
    var textureViewInstance by remember { mutableStateOf<TextureView?>(null) }
    val preTiramisuGlRenderer = remember {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) PreTiramisuGlPreviewRenderer() else null
    }
    var previewAspectRatio by remember { mutableFloatStateOf(initialRatio) }
    var surfaceGeneration by remember { mutableIntStateOf(0) }

    val shutterFlashAlpha = remember { Animatable(0f) }
    var isShutterPressed by remember { mutableStateOf(false) }

    val savedPresets = remember { mutableStateListOf<CameraPreset>() }

    fun showStatus(message: String) {
        Log.d("CameraScreen", message)
        statusBannerText = message
        statusBannerNonce++
    }

    LaunchedEffect(statusBannerNonce) {
        if (statusBannerText != null) {
            delay(1700)
            statusBannerText = null
        }
    }

    LaunchedEffect(pickedMarkerPos, isPickerActive, isColorPipetteActive) {
        if (pickedMarkerPos != null && !isPickerActive && !isColorPipetteActive) {
            delay(1600)
            pickedMarkerPos = null
        }
    }

    LaunchedEffect(incomingEditNonce) {
        if (incomingEditUri != null) {
            studioPhotoUri = incomingEditUri
            studioPhotoNonce++
            isStudioMode = true
        }
    }

    LaunchedEffect(
        targetedColorConfig,
        colorBandsConfig,
        channelSwapEnabled,
        activeLookGrade,
        experienceMode,
        isAutoMode,
        isComparingAuto,
        textureViewInstance
    ) {
        holder.currentTargetedColor = targetedColorConfig
        holder.currentColorBands = colorBandsConfig
        holder.currentChannelSwap = channelSwapEnabled
        holder.currentLookGrade = activeLookGrade
        holder.currentExperienceMode = experienceMode
        val applyShaderInAuto = (experienceMode == ExperienceMode.AESTHETIC) || activeLookGrade.isActive
        val bypassShader = isComparingAuto || (isAutoMode && !applyShaderInAuto)
        updateTextureViewShader(
            view = textureViewInstance,
            config = targetedColorConfig,
            colorBands = colorBandsConfig,
            channelSwap = channelSwapEnabled,
            lookGrade = activeLookGrade,
            bypass = bypassShader,
            preTiramisuGlRenderer = preTiramisuGlRenderer
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && (preTiramisuGlRenderer == null || preTiramisuGlRenderer.isFailed) && !shownPreTiramisuHslNotice && (!isAutoMode || applyShaderInAuto)) {
            val hasGrading = activeLookGrade.isActive || channelSwapEnabled || colorBandsConfig.hasAnyModification ||
                (targetedColorConfig.isActive && (targetedColorConfig.deltaHue != 0f || targetedColorConfig.deltaSat != 0f || targetedColorConfig.deltaLum != 0f))
            if (hasGrading) {
                shownPreTiramisuHslNotice = true
                showStatus(context.getString(R.string.status_pre_tiramisu_hsl))
            }
        }
    }

    fun savePresetsToStorage() {
        val jsonArray = JSONArray()
        savedPresets.forEach { preset ->
            jsonArray.put(JSONObject().apply {
                put("name", preset.name)
                put("kelvin", preset.kelvin.toDouble())
                put("tint", preset.tint.toDouble())
                put("contrast", preset.contrast.toDouble())
                put("saturation", preset.saturation.toDouble())
                put("channelSwap", preset.channelSwap)
                if (preset.colorBands.hasAnyModification) {
                    val bandsArr = JSONArray()
                    preset.colorBands.bands.forEach { adj ->
                        bandsArr.put(JSONObject().apply {
                            put("h", adj.deltaHue.toDouble())
                            put("s", adj.deltaSat.toDouble())
                            put("l", adj.deltaLum.toDouble())
                        })
                    }
                    put("bands", bandsArr)
                }
            })
        }
        sharedPrefs.edit { putString("presets_list", jsonArray.toString()) }
    }

    fun loadPresets() {
        savedPresets.clear()
        val jsonString = sharedPrefs.getString("presets_list", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.getString("name")
                val bandsArr = obj.optJSONArray("bands")
                val bandsConfig = if (bandsArr != null && bandsArr.length() == 12) {
                    ColorBandsConfig(
                        List(12) { idx ->
                            val bObj = bandsArr.optJSONObject(idx)
                            BandHslAdjustment(
                                deltaHue = bObj?.optDouble("h", 0.0)?.toFloat() ?: 0f,
                                deltaSat = bObj?.optDouble("s", 0.0)?.toFloat() ?: 0f,
                                deltaLum = bObj?.optDouble("l", 0.0)?.toFloat() ?: 0f
                            )
                        }
                    )
                } else if (bandsArr != null && bandsArr.length() == 8) {
                    val legacyTargets = listOf(
                        ColorBand.RED, ColorBand.ORANGE, ColorBand.YELLOW, ColorBand.GREEN,
                        ColorBand.CYAN, ColorBand.BLUE, ColorBand.PURPLE, ColorBand.MAGENTA
                    )
                    val mapped = MutableList(12) { BandHslAdjustment() }
                    legacyTargets.forEachIndexed { legacyIdx, band ->
                        val bObj = bandsArr.optJSONObject(legacyIdx)
                        mapped[band.index] = BandHslAdjustment(
                            deltaHue = bObj?.optDouble("h", 0.0)?.toFloat() ?: 0f,
                            deltaSat = bObj?.optDouble("s", 0.0)?.toFloat() ?: 0f,
                            deltaLum = bObj?.optDouble("l", 0.0)?.toFloat() ?: 0f
                        )
                    }
                    val bridges = listOf(
                        Triple(ColorBand.LIME, ColorBand.YELLOW, ColorBand.GREEN),
                        Triple(ColorBand.EMERALD, ColorBand.GREEN, ColorBand.CYAN),
                        Triple(ColorBand.AZURE, ColorBand.CYAN, ColorBand.BLUE),
                        Triple(ColorBand.ROSE, ColorBand.MAGENTA, ColorBand.RED)
                    )
                    for ((mid, prev, next) in bridges) {
                        val a = mapped[prev.index]
                        val b = mapped[next.index]
                        if (a.isModified || b.isModified) {
                            mapped[mid.index] = BandHslAdjustment(
                                deltaHue = (a.deltaHue + b.deltaHue) * 0.5f,
                                deltaSat = (a.deltaSat + b.deltaSat) * 0.5f,
                                deltaLum = (a.deltaLum + b.deltaLum) * 0.5f
                            )
                        }
                    }
                    ColorBandsConfig(mapped)
                } else {
                    ColorBandsConfig()
                }
                savedPresets.add(
                    CameraPreset(
                        name = name,
                        kelvin = obj.getDouble("kelvin").toFloat(),
                        tint = obj.getDouble("tint").toFloat(),
                        contrast = obj.optDouble("contrast", DEFAULT_CONTRAST.toDouble()).toFloat(),
                        saturation = obj.optDouble("saturation", DEFAULT_SATURATION.toDouble()).toFloat(),
                        channelSwap = obj.optBoolean("channelSwap", false),
                        colorBands = bandsConfig
                    )
                )
            }
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(Unit) { loadPresets() }

    fun syncHolderSettings() {
        holder.currentAutoMode = isAutoMode
        holder.currentKelvin = kelvinValue
        holder.currentTint = tintValue
        holder.currentContrast = contrastValue
        holder.currentSaturation = saturationValue
        holder.currentExposureEv = exposureEvIndex
        holder.currentMeteringMode = meteringMode
        holder.currentExposureBoxNorm = exposureBoxNorm
        holder.currentExposureBoxSizeNorm = exposureBoxSizeNorm
        holder.currentTargetedColor = targetedColorConfig
        holder.currentColorBands = colorBandsConfig
        holder.currentLookGrade = activeLookGrade
        holder.currentExperienceMode = experienceMode
        holder.currentChannelSwap = channelSwapEnabled
        holder.currentAeLock = aeLockEnabled
        holder.currentZoomRatio = zoomRatio
        holder.rawCaptureEnabled = rawEnabled
        holder.flashMode = flashMode
        holder.useFrontCamera = useFrontCamera
    }

    fun applyCamera2Settings(
        builder: CaptureRequest.Builder,
        auto: Boolean,
        kVal: Float,
        tVal: Float,
        contrastVal: Float,
        saturationVal: Float,
        exposureEv: Int,
        meteringModeVal: ExposureMeteringMode,
        boxNorm: Offset,
        boxSizeNorm: Float,
        isStillCapture: Boolean = false,
        compatibilityFallback: Boolean = false
    ) {
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        val desiredFlash = if (holder.hasFlash) holder.flashMode else FlashMode.OFF
        when (desiredFlash) {
            FlashMode.TORCH -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            }
            FlashMode.AUTO -> {
                if (holder.availableAeModes.contains(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)) {
                    // Arm AE auto-flash on BOTH preview & still capture so AE continuously evaluates ambient light
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                } else {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
            }
            FlashMode.ON -> {
                if (holder.availableAeModes.contains(CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)) {
                    // Arm AE always-flash on BOTH preview & still capture with FLASH_MODE_OFF for true AE flash sync
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                } else {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    builder.set(
                        CaptureRequest.FLASH_MODE,
                        if (holder.pendingTorchFallbackActive) CaptureRequest.FLASH_MODE_TORCH
                        else CaptureRequest.FLASH_MODE_OFF
                    )
                }
            }
            FlashMode.OFF -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
        }
        if (isStillCapture && (desiredFlash == FlashMode.ON || desiredFlash == FlashMode.AUTO)) {
            try {
                builder.set(
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE
                )
            } catch (_: Exception) {
            }
        }
        when {
            holder.availableAfModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) -> {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
            holder.availableAfModes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) -> {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            }
        }
        if (holder.aeCompensationRange.upper > holder.aeCompensationRange.lower) {
            val clampedEv = exposureEv.coerceIn(holder.aeCompensationRange.lower, holder.aeCompensationRange.upper)
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, clampedEv)
        }
        try {
            val allowAeLock = !(isStillCapture && (desiredFlash == FlashMode.ON || desiredFlash == FlashMode.AUTO))
            builder.set(CaptureRequest.CONTROL_AE_LOCK, if (allowAeLock) holder.currentAeLock else false)
        } catch (_: Exception) {
        }
        // Keep optical ISP edge clarity & noise reduction active on both preview & still capture
        try {
            builder.set(
                CaptureRequest.EDGE_MODE,
                if (isStillCapture) CaptureRequest.EDGE_MODE_HIGH_QUALITY
                else CaptureRequest.EDGE_MODE_FAST
            )
            builder.set(
                CaptureRequest.NOISE_REDUCTION_MODE,
                if (isStillCapture) CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
                else CaptureRequest.NOISE_REDUCTION_MODE_FAST
            )
        } catch (_: Exception) {
        }
        if (holder.maxAeRegions > 0 && !compatibilityFallback) {
            try {
                buildAeMeteringRegion(
                    activeArray = holder.activeSensorArray,
                    sensorOrientation = holder.activeSensorOrientation,
                    mode = meteringModeVal,
                    boxNorm = boxNorm,
                    boxSizeNorm = boxSizeNorm
                )?.let { rects ->
                    builder.set(CaptureRequest.CONTROL_AE_REGIONS, rects)
                }
            } catch (_: Exception) {
            }
        }
        // Mercek / yakınlaştırma oranı (Android 11+ CONTROL_ZOOM_RATIO + güvenli SCALER_CROP_REGION yedeği)
        val clampedZoom = holder.currentZoomRatio.coerceIn(holder.zoomRange.lower, holder.zoomRange.upper)
        var appliedZoomRatio = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, clampedZoom)
                appliedZoomRatio = true
            } catch (_: Exception) {
                appliedZoomRatio = false
            }
        }
        if (!appliedZoomRatio && clampedZoom > 1.01f) {
            holder.activeSensorArray?.let { active ->
                val cropW = (active.width() / clampedZoom).roundToInt().coerceAtLeast(1)
                val cropH = (active.height() / clampedZoom).roundToInt().coerceAtLeast(1)
                val dx = (active.width() - cropW) / 2
                val dy = (active.height() - cropH) / 2
                try {
                    builder.set(CaptureRequest.SCALER_CROP_REGION, Rect(dx, dy, dx + cropW, dy + cropH))
                } catch (_: Exception) {
                }
            }
        }

        if (auto) {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            builder.set(
                CaptureRequest.COLOR_CORRECTION_MODE,
                if (isStillCapture) CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY
                else CaptureRequest.COLOR_CORRECTION_MODE_FAST
            )
            builder.set(
                CaptureRequest.TONEMAP_MODE,
                if (isStillCapture) CaptureRequest.TONEMAP_MODE_HIGH_QUALITY
                else CaptureRequest.TONEMAP_MODE_FAST
            )
        } else if (compatibilityFallback) {
            // Safe legacy/LIMITED fallback if a HAL rejects manual transform matrix or tonemap curve
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_FAST)
            builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, kelvinToGains(kVal, tVal))
            builder.set(
                CaptureRequest.TONEMAP_MODE,
                if (isStillCapture) CaptureRequest.TONEMAP_MODE_HIGH_QUALITY
                else CaptureRequest.TONEMAP_MODE_FAST
            )
        } else {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
            builder.set(
                CaptureRequest.COLOR_CORRECTION_MODE,
                CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX
            )
            builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, kelvinToGains(kVal, tVal))
            builder.set(
                CaptureRequest.COLOR_CORRECTION_TRANSFORM,
                buildColorTransform(holder.lastAutoColorTransform, saturationVal)
            )

            // When contrast is standard (~1.0), use HIGH_QUALITY hardware tone mapping so
            // the camera ISP's native dynamic range engine prevents whites from blowing out!
            if (contrastVal in 0.98f..1.02f) {
                builder.set(
                    CaptureRequest.TONEMAP_MODE,
                    if (isStillCapture) CaptureRequest.TONEMAP_MODE_HIGH_QUALITY
                    else CaptureRequest.TONEMAP_MODE_FAST
                )
            } else {
                val supportsContrastCurve =
                    holder.supportedTonemapModes.contains(CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE) &&
                        holder.maxTonemapCurvePoints >= 2

                if (supportsContrastCurve) {
                    builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
                    builder.set(
                        CaptureRequest.TONEMAP_CURVE,
                        buildContrastTonemapCurve(
                            contrast = contrastVal,
                            maxCurvePoints = holder.maxTonemapCurvePoints,
                            nativeCurve = holder.lastNativeTonemapCurve
                        )
                    )
                } else {
                    builder.set(
                        CaptureRequest.TONEMAP_MODE,
                        if (isStillCapture) CaptureRequest.TONEMAP_MODE_HIGH_QUALITY
                        else CaptureRequest.TONEMAP_MODE_FAST
                    )
                }
            }
        }
    }

    val captureCallback = remember {
        object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                s: CameraCaptureSession,
                r: CaptureRequest,
                result: TotalCaptureResult
            ) {
                val aeState = result[CaptureResult.CONTROL_AE_STATE]
                if (aeState != null) {
                    holder.lastAeState = aeState
                }
                if (holder.flashPrecaptureActive) {
                    val elapsed = System.currentTimeMillis() - holder.flashPrecaptureStartMs
                    if (aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                        aeState == CaptureResult.CONTROL_AE_STATE_SEARCHING
                    ) {
                        holder.flashPrecaptureSawActiveState = true
                    }
                    val precaptureSettled =
                        (holder.flashPrecaptureSawActiveState &&
                            aeState != null &&
                            aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE &&
                            aeState != CaptureResult.CONTROL_AE_STATE_SEARCHING) ||
                            (!holder.flashPrecaptureSawActiveState &&
                                elapsed >= 280L &&
                                (aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                                    aeState == CaptureResult.CONTROL_AE_STATE_LOCKED)) ||
                            elapsed >= 1350L
                    if (precaptureSettled) {
                        val runner = synchronized(holder) {
                            if (holder.flashPrecaptureActive) {
                                holder.flashPrecaptureActive = false
                                holder.flashPrecaptureSawActiveState = false
                                val cb = holder.pendingFlashCaptureRunner
                                holder.pendingFlashCaptureRunner = null
                                cb
                            } else null
                        }
                        runner?.invoke()
                    }
                }
                result[CaptureResult.CONTROL_AWB_STATE]?.let { awbState ->
                    holder.lastAwbState = awbState
                }
                result[CaptureResult.COLOR_CORRECTION_GAINS]?.let { gains ->
                    if (gains.red.isFinite() && gains.blue.isFinite() && gains.red > 0.05f && gains.blue > 0.05f) {
                        holder.lastHardwareGains = gains
                        holder.hasRealHardwareGains = true
                    }
                    if (holder.currentAutoMode) {
                        val now = System.currentTimeMillis()
                        if (now - holder.lastTelemetryUpdateMs >= 250L) {
                            holder.lastTelemetryUpdateMs = now
                            val (estK, estT) = gainsToKelvinTint(gains)
                            mainHandler.post {
                                if (abs(liveAutoKelvin - estK) >= 15f) liveAutoKelvin = estK
                                if (abs(liveAutoTint - estT) >= 1f) liveAutoTint = estT
                            }
                        }
                    }
                }
                if (holder.currentAutoMode || holder.lastAutoColorTransform == null) {
                    result[CaptureResult.COLOR_CORRECTION_TRANSFORM]?.let { transform ->
                        holder.lastAutoColorTransform = transform
                    }
                }
                if (holder.currentAutoMode || holder.currentContrast in 0.98f..1.02f || holder.lastNativeTonemapCurve == null) {
                    result[CaptureResult.TONEMAP_CURVE]?.let { nativeCurve ->
                        if (isValidNativeTonemapCurve(nativeCurve)) {
                            holder.lastNativeTonemapCurve = nativeCurve
                        }
                    }
                }
            }
        }
    }

    fun submitRepeatingRequest(session: CameraCaptureSession, builder: CaptureRequest.Builder): Boolean {
        return try {
            session.setRepeatingRequest(builder.build(), captureCallback, cameraHandler)
            true
        } catch (e: Exception) {
            Log.w("CameraScreen", "Primary repeating request failed, trying compatibility fallback", e)
            if (!holder.currentAutoMode) {
                try {
                    applyCamera2Settings(
                        builder = builder,
                        auto = false,
                        kVal = holder.currentKelvin,
                        tVal = holder.currentTint,
                        contrastVal = holder.currentContrast,
                        saturationVal = holder.currentSaturation,
                        exposureEv = holder.currentExposureEv,
                        meteringModeVal = holder.currentMeteringMode,
                        boxNorm = holder.currentExposureBoxNorm,
                        boxSizeNorm = holder.currentExposureBoxSizeNorm,
                        isStillCapture = false,
                        compatibilityFallback = true
                    )
                    session.setRepeatingRequest(builder.build(), captureCallback, cameraHandler)
                    return true
                } catch (e2: Exception) {
                    Log.e("CameraScreen", "Compatibility repeating request failed", e2)
                }
            }
            false
        }
    }

    fun updatePreviewSettings() {
        syncHolderSettings()
        val builder = holder.previewBuilder ?: return
        val session = holder.session ?: return
        applyCamera2Settings(
            builder = builder,
            auto = holder.currentAutoMode || isComparingAuto,
            kVal = holder.currentKelvin,
            tVal = holder.currentTint,
            contrastVal = holder.currentContrast,
            saturationVal = holder.currentSaturation,
            exposureEv = holder.currentExposureEv,
            meteringModeVal = holder.currentMeteringMode,
            boxNorm = holder.currentExposureBoxNorm,
            boxSizeNorm = holder.currentExposureBoxSizeNorm,
            isStillCapture = false
        )
        submitRepeatingRequest(session, builder)
    }

    fun measureAndApplyExposureMetering(applyEvAdjust: Boolean) {
        val view = textureViewInstance ?: return
        if (!view.isAvailable || view.width <= 0 || view.height <= 0) return
        val bmp = try { view.getBitmap(160, 160) } catch (_: Exception) { null } ?: return
        val captureZebra = showZebra
        coroutineScope.launch(Dispatchers.Default) {
            try {
                val stats = measureRegionLuminance(bmp, meteringMode, exposureBoxNorm, exposureBoxSizeNorm)
                val hist = computeProHistogramData(bmp, 48)
                val zGrid = if (captureZebra) computeZebraGrid(bmp, 32, 32) else null
                withContext(Dispatchers.Main) {
                    measuredRegionBrightness = stats.avgLuma.roundToInt()
                    liveHistogram = hist
                    if (zGrid != null) zebraGrid = zGrid

                    if (applyEvAdjust) {
                        // Donanımsal AE ölçüm bölgesi (CONTROL_AE_REGIONS) doğal pozlamayı yönetir.
                        // Karanlık/siyah nesneye dokunulduğunda yazılımsal EV sıçraması yapılmaz;
                        // yalnızca vurgu koruması açıkken gerçek yanma varsa yumuşak (-1 adım) koruma uygulanır.
                        if (highlightProtectionEnabled && aeRange.upper > aeRange.lower) {
                            if (stats.highlightRatio > 0.08f && stats.maxLuma > 242f && exposureEvIndex > aeRange.lower) {
                                exposureEvIndex = (exposureEvIndex - 1).coerceIn(aeRange.lower, aeRange.upper)
                            }
                        }
                        updatePreviewSettings()
                    }
                }
            } finally {
                bmp.recycle()
            }
        }
    }

    var isAutoCalibrating by remember { mutableStateOf(false) }

    fun runAutoCalibrate() {
        if (isAutoCalibrating || !cameraReady) return
        isAutoCalibrating = true
        coroutineScope.launch(Dispatchers.Main) {
            try {
                if (!holder.currentAutoMode) {
                    holder.lastAwbState = CaptureResult.CONTROL_AWB_STATE_INACTIVE
                    holder.hasRealHardwareGains = false
                    cameraHandler.post {
                        val builder = holder.previewBuilder ?: return@post
                        val session = holder.session ?: return@post
                        applyCamera2Settings(
                            builder = builder,
                            auto = true,
                            kVal = holder.currentKelvin,
                            tVal = holder.currentTint,
                            contrastVal = DEFAULT_CONTRAST,
                            saturationVal = DEFAULT_SATURATION,
                            exposureEv = holder.currentExposureEv,
                            meteringModeVal = holder.currentMeteringMode,
                            boxNorm = holder.currentExposureBoxNorm,
                            boxSizeNorm = holder.currentExposureBoxSizeNorm,
                            isStillCapture = false
                        )
                        submitRepeatingRequest(session, builder)
                    }
                }
                delay(180)
                for (attempt in 0 until 10) {
                    if (holder.lastAwbState == CaptureResult.CONTROL_AWB_STATE_CONVERGED ||
                        holder.lastAwbState == CaptureResult.CONTROL_AWB_STATE_LOCKED
                    ) {
                        break
                    }
                    delay(45)
                }

                val (finalK, finalT) = if (holder.hasRealHardwareGains) {
                    val (hwK, hwT) = gainsToKelvinTint(holder.lastHardwareGains)
                    hwK.coerceIn(KELVIN_MIN, KELVIN_MAX) to hwT.coerceIn(-100f, 100f)
                } else {
                    val view = textureViewInstance
                    val bmp = if (view != null && view.isAvailable && view.width > 0 && view.height > 0) {
                        try { view.getBitmap(240, 240) } catch (_: Exception) { null }
                    } else null
                    if (bmp != null) {
                        try {
                            val cal = withContext(Dispatchers.Default) {
                                autoCalibrateSceneFromPreview(bmp, kelvinToGains(kelvinValue, tintValue))
                            }
                            cal.kelvin to cal.tint
                        } finally {
                            bmp.recycle()
                        }
                    } else {
                        kelvinValue to tintValue
                    }
                }

                isPickerActive = false
                isExposureBoxPicking = false
                isAutoMode = false
                kelvinValue = finalK
                tintValue = finalT
                contrastValue = DEFAULT_CONTRAST
                saturationValue = DEFAULT_SATURATION
                updatePreviewSettings()
                measureAndApplyExposureMetering(applyEvAdjust = true)
                showStatus(context.getString(R.string.status_auto_cal_fmt, finalK.toInt(), formatSigned(finalT)))
            } finally {
                isAutoCalibrating = false
            }
        }
    }

    fun runAutoContrast() {
        val view = textureViewInstance ?: return
        if (!view.isAvailable || view.width <= 0 || view.height <= 0) return
        val bmp = try { view.getBitmap(160, 160) } catch (_: Exception) { null } ?: return
        coroutineScope.launch(Dispatchers.Default) {
            try {
                val res = calculateAutoContrastFromPreview(bmp, aeStep, exposureEvIndex, aeRange)
                withContext(Dispatchers.Main) {
                    isAutoMode = false
                    activeColorSubSlider = ColorSubSlider.KONTRAST
                    contrastValue = res.recommendedContrast
                    if (res.recommendedEvIndexDelta != 0 && aeRange.upper > aeRange.lower) {
                        exposureEvIndex = (exposureEvIndex + res.recommendedEvIndexDelta).coerceIn(aeRange.lower, aeRange.upper)
                    }
                    updatePreviewSettings()
                    val formatted = String.format(appLocale, "%.2f", res.recommendedContrast)
                    showStatus(context.getString(R.string.status_auto_contrast_fmt, formatted))
                }
            } finally {
                bmp.recycle()
            }
        }
    }

    var startCameraRef: ((SurfaceTexture) -> Unit)? = null

    /** Returns true when no open CameraDevice existed (so caller can reopen immediately without waiting for onClosed). */
    fun closeCameraInternal(releaseReader: Boolean = true, pendingReopen: SurfaceTexture? = null): Boolean {
        var closedWithoutActiveDevice = false
        var closeGenSnapshot = 0
        var hadDevice = false
        synchronized(holder) {
            holder.cameraGeneration += 1
            closeGenSnapshot = holder.cameraGeneration
            holder.isOpening = false
            holder.isTakingPhoto = false
            holder.flashPrecaptureActive = false
            holder.flashPrecaptureSawActiveState = false
            holder.pendingFlashCaptureRunner = null
            holder.pendingTorchFallbackActive = false
            if (pendingReopen != null) {
                holder.pendingReopenSurfaceTexture = pendingReopen
            }
            hadDevice = holder.device != null
            if (hadDevice) {
                holder.isClosingDevice = true
            } else {
                holder.isClosingDevice = false
            }
            try { holder.session?.close() } catch (_: Exception) {}
            holder.session = null
            try { holder.device?.close() } catch (_: Exception) {}
            holder.device = null
            holder.previewBuilder = null
            try { holder.activeSurface?.release() } catch (_: Exception) {}
            holder.activeSurface = null
            holder.activeSurfaceTexture = null
            if (releaseReader) {
                try { holder.imageReader?.close() } catch (_: Exception) {}
                holder.imageReader = null
                try { holder.rawImageReader?.close() } catch (_: Exception) {}
                holder.rawImageReader = null
            }
            if (!hadDevice && pendingReopen != null && !holder.isDisposed) {
                holder.pendingReopenSurfaceTexture = null
                closedWithoutActiveDevice = true
            }
        }
        if (hadDevice) {
            // Güvenlik zaman aşımı: bazı cihaz HAL'ları onClosed çağrısını geciktirir veya atlar;
            // 550 ms içinde onClosed gelmezse isClosingDevice kilidini açıp bekleyen yüzeyi başlat.
            cameraHandler.postDelayed({
                val pending = synchronized(holder) {
                    if (holder.isClosingDevice && holder.cameraGeneration == closeGenSnapshot) {
                        holder.isClosingDevice = false
                        val st = holder.pendingReopenSurfaceTexture
                        holder.pendingReopenSurfaceTexture = null
                        st
                    } else {
                        null
                    }
                }
                if (pending != null && !holder.isDisposed && !currentIsStudioMode) {
                    startCameraRef?.invoke(pending)
                }
            }, 550L)
        }
        mainHandler.post { cameraReady = false }
        return closedWithoutActiveDevice
    }

    @SuppressLint("MissingPermission")
    fun startCameraInternal(surfaceTexture: SurfaceTexture) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && surfaceTexture.isReleased) {
            return
        }
        val openGen = synchronized(holder) {
            if (holder.isDisposed || currentIsStudioMode) return
            if (holder.isClosingDevice || holder.isOpening) {
                // Açılış veya kapanış sürerken gelen yeni yüzeyi ASLA düşürme, kuyruğa al!
                holder.pendingReopenSurfaceTexture = surfaceTexture
                return
            }
            if (holder.device != null) return
            holder.isOpening = true
            holder.cameraGeneration += 1
            holder.cameraGeneration
        }

        try {
            val cameraIdList = cameraManager.cameraIdList
            if (cameraIdList.isEmpty()) {
                synchronized(holder) { holder.isOpening = false }
                return
            }
            val desiredFacing = if (holder.useFrontCamera) {
                CameraCharacteristics.LENS_FACING_FRONT
            } else {
                CameraCharacteristics.LENS_FACING_BACK
            }
            val cameraId = cameraIdList.firstOrNull { id ->
                try {
                    cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING) == desiredFacing
                } catch (_: Exception) {
                    false
                }
            } ?: cameraIdList[0]
            holder.activeCameraId = cameraId

            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val aeModes = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()
            holder.hasFlash = flashAvailable
            holder.availableAeModes = aeModes
            if (!flashAvailable) {
                holder.flashMode = FlashMode.OFF
            }
            mainHandler.post {
                hasFlash = flashAvailable
                if (!flashAvailable) flashMode = FlashMode.OFF
            }
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            if (map == null) {
                synchronized(holder) { holder.isOpening = false }
                return
            }

            val rawOutputSizes = map.getOutputSizes(ImageFormat.JPEG)
            if (rawOutputSizes.isNullOrEmpty()) {
                synchronized(holder) { holder.isOpening = false }
                return
            }

            val filteredRes = rawOutputSizes.filter { it.width >= 640 && it.height >= 480 }
            val sortedSizes = (filteredRes.ifEmpty { rawOutputSizes.toList() })
                .distinctBy { it.width to it.height }
                .sortedByDescending { it.width.toLong() * it.height.toLong() }

            val savedW = sharedPrefs.getInt("selected_res_w", -1)
            val savedH = sharedPrefs.getInt("selected_res_h", -1)
            val chosenJpeg = holder.selectedResolution?.let { sel ->
                sortedSizes.firstOrNull { it.width == sel.width && it.height == sel.height }
            } ?: sortedSizes.firstOrNull { it.width == savedW && it.height == savedH }
                ?: sortedSizes.first()

            holder.selectedResolution = chosenJpeg
            mainHandler.post {
                availableResolutions.clear()
                availableResolutions.addAll(sortedSizes)
                selectedResolution = chosenJpeg
            }

            val previewSizes = map.getOutputSizes(SurfaceTexture::class.java) ?: arrayOf(chosenJpeg)
            val chosenPreview = choosePreviewSize(previewSizes, chosenJpeg, holder.previewFallbackLevel)
            val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            holder.activePreviewSize = chosenPreview
            holder.activeSensorOrientation = sensorOrientation
            holder.activeSensorArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            holder.maxAeRegions = chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0

            surfaceTexture.setDefaultBufferSize(chosenPreview.width, chosenPreview.height)
            val swapped = sensorOrientation == 90 || sensorOrientation == 270
            val uprightJpegW = if (swapped) chosenJpeg.height else chosenJpeg.width
            val uprightJpegH = if (swapped) chosenJpeg.width else chosenJpeg.height
            val ratio = (uprightJpegW.toFloat() / uprightJpegH.coerceAtLeast(1).toFloat()).coerceIn(0.4f, 2.0f)
            mainHandler.post {
                previewAspectRatio = ratio
                textureViewInstance?.let { tv ->
                    tv.surfaceTexture?.setDefaultBufferSize(chosenPreview.width, chosenPreview.height)
                    applyPreviewTransform(tv, chosenPreview, sensorOrientation)
                }
            }

            val surfaceTargetSt = preTiramisuGlRenderer?.getOrAttachInputSurface(surfaceTexture) ?: surfaceTexture
            surfaceTargetSt.setDefaultBufferSize(chosenPreview.width, chosenPreview.height)
            val surface = Surface(surfaceTargetSt)

            holder.supportedTonemapModes =
                chars.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES) ?: intArrayOf()
            holder.availableAfModes =
                chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
            holder.maxTonemapCurvePoints =
                chars.get(CameraCharacteristics.TONEMAP_MAX_CURVE_POINTS) ?: 33
            val range = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: Range(0, 0)
            val step = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 0.5f
            holder.aeCompensationRange = range
            holder.aeCompensationStep = step

            // Desteklenen yakınlaştırma / mercek aralığı (0.5x veya 0.6x geniş açı / 1x / 2x)
            val zRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) ?: Range(1f, 1f)
                } catch (_: Exception) {
                    Range(1f, (chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f).coerceAtLeast(1f))
                }
            } else {
                val maxDigital = (chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f).coerceAtLeast(1f)
                Range(1f, maxDigital)
            }
            holder.zoomRange = zRange
            val zoomOpts = buildList {
                if (zRange.lower <= 0.70f) {
                    add(if (zRange.lower > 0.55f) 0.6f else 0.5f)
                }
                add(1f)
                if (zRange.upper >= 2.0f) add(2f)
            }

            // RAW/DNG sensör desteği kontrolü (CFA + ActiveArray doğrulamasıyla tüm cihazlarda güvenli)
            val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val hasCfa = chars.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT) != null
            val hasActiveArr = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) != null
            val rawSizes = if (
                caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) &&
                hasCfa &&
                hasActiveArr
            ) {
                map.getOutputSizes(ImageFormat.RAW_SENSOR)
            } else null
            val chosenRaw = rawSizes?.maxByOrNull { it.width.toLong() * it.height.toLong() }
            holder.supportsRaw = chosenRaw != null
            holder.rawSize = chosenRaw

            mainHandler.post {
                aeRange = range
                aeStep = step
                availableZoomOptions = zoomOpts
                supportsRaw = chosenRaw != null
                if (chosenRaw == null && rawEnabled) {
                    rawEnabled = false
                    holder.rawCaptureEnabled = false
                }
            }

            synchronized(holder) {
                holder.imageReader?.close()
                holder.imageReader = ImageReader.newInstance(chosenJpeg.width, chosenJpeg.height, ImageFormat.JPEG, 4)
                holder.rawImageReader?.close()
                holder.rawImageReader = if (holder.rawCaptureEnabled && chosenRaw != null) {
                    ImageReader.newInstance(chosenRaw.width, chosenRaw.height, ImageFormat.RAW_SENSOR, 4)
                } else {
                    null
                }
                holder.activeSurface = surface
                holder.activeSurfaceTexture = surfaceTexture
            }

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    val surfaceDead = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && surfaceTexture.isReleased
                    var stalePending: SurfaceTexture? = null
                    val shouldAbort = synchronized(holder) {
                        holder.isOpening = false
                        if (holder.isDisposed || currentIsStudioMode || holder.cameraGeneration != openGen || surfaceDead) {
                            stalePending = holder.pendingReopenSurfaceTexture
                            holder.pendingReopenSurfaceTexture = null
                            true
                        } else {
                            holder.device = camera
                            false
                        }
                    }
                    if (shouldAbort) {
                        try { camera.close() } catch (_: Exception) {}
                        val nextSt = stalePending
                        if (nextSt != null && !holder.isDisposed && !currentIsStudioMode) {
                            cameraHandler.post { startCameraInternal(nextSt) }
                        }
                        return
                    }
                    val readerSurface = holder.imageReader?.surface
                    if (readerSurface == null) {
                        closeCameraInternal(releaseReader = true)
                        return
                    }
                    try {
                        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(surface)
                            applyCamera2Settings(
                                builder = this,
                                auto = holder.currentAutoMode,
                                kVal = holder.currentKelvin,
                                tVal = holder.currentTint,
                                contrastVal = holder.currentContrast,
                                saturationVal = holder.currentSaturation,
                                exposureEv = holder.currentExposureEv,
                                meteringModeVal = holder.currentMeteringMode,
                                boxNorm = holder.currentExposureBoxNorm,
                                boxSizeNorm = holder.currentExposureBoxSizeNorm,
                                isStillCapture = false
                            )
                        }
                        holder.previewBuilder = builder
                        val sessionSurfaces = buildList {
                            add(surface)
                            add(readerSurface)
                            holder.rawImageReader?.surface?.let { add(it) }
                        }
                        @Suppress("DEPRECATION")
                        camera.createCaptureSession(
                            sessionSurfaces,
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    if (holder.isDisposed || holder.device !== camera) {
                                        try { session.close() } catch (_: Exception) {}
                                        return
                                    }
                                    holder.session = session
                                    val started = submitRepeatingRequest(session, builder)
                                    mainHandler.post {
                                        cameraReady = started
                                        textureViewInstance?.let { tv ->
                                            tv.surfaceTexture?.setDefaultBufferSize(chosenPreview.width, chosenPreview.height)
                                            applyPreviewTransform(tv, chosenPreview, sensorOrientation)
                                        }
                                    }
                                }

                                override fun onConfigureFailed(s: CameraCaptureSession) {
                                    Log.w("CameraScreen", "Session config failed at tier=${holder.previewFallbackLevel}, raw=${holder.rawCaptureEnabled}")
                                    if (!holder.isDisposed && holder.previewFallbackLevel < 2) {
                                        holder.previewFallbackLevel += 1
                                        if (closeCameraInternal(releaseReader = true, pendingReopen = surfaceTexture)) {
                                            cameraHandler.post { startCameraInternal(surfaceTexture) }
                                        }
                                    } else if (!holder.isDisposed && holder.rawCaptureEnabled) {
                                        // Bazı cihazlar yüksek çözünürlüklü JPEG + RAW + Preview 3'lü oturumunu desteklemez:
                                        // güvenli şekilde JPEG moduna dönüp kamerayı kesintisiz yeniden açar.
                                        holder.rawCaptureEnabled = false
                                        holder.previewFallbackLevel = 0
                                        mainHandler.post {
                                            rawEnabled = false
                                            showStatus(context.getString(R.string.status_raw_unsupported_res))
                                        }
                                        if (closeCameraInternal(releaseReader = true, pendingReopen = surfaceTexture)) {
                                            cameraHandler.post { startCameraInternal(surfaceTexture) }
                                        }
                                    } else {
                                        closeCameraInternal(releaseReader = true)
                                        mainHandler.post {
                                            cameraReady = false
                                            showStatus(context.getString(R.string.status_res_unsupported))
                                        }
                                    }
                                }
                            },
                            cameraHandler
                        )
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Failed to create capture session", e)
                        closeCameraInternal(releaseReader = true)
                    }
                }

                override fun onDisconnected(c: CameraDevice) {
                    closeCameraInternal(releaseReader = true)
                    try { c.close() } catch (_: Exception) {}
                }

                override fun onError(c: CameraDevice, e: Int) {
                    closeCameraInternal(releaseReader = true)
                    try { c.close() } catch (_: Exception) {}
                }

                override fun onClosed(c: CameraDevice) {
                    val pending = synchronized(holder) {
                        holder.isClosingDevice = false
                        val st = holder.pendingReopenSurfaceTexture
                        holder.pendingReopenSurfaceTexture = null
                        st
                    }
                    if (pending != null && !holder.isDisposed && !currentIsStudioMode) {
                        cameraHandler.post { startCameraInternal(pending) }
                    }
                }
            }, cameraHandler)
        } catch (e: Exception) {
            synchronized(holder) {
                holder.isOpening = false
                holder.isClosingDevice = false
            }
            Log.e("CameraScreen", "Failed to open camera", e)
        }
    }

    startCameraRef = { st -> startCameraInternal(st) }

    fun startCamera(surfaceTexture: SurfaceTexture) {
        cameraHandler.post { startCameraInternal(surfaceTexture) }
    }

    fun restartForResolution(res: Size) {
        val swapped = holder.activeSensorOrientation == 90 || holder.activeSensorOrientation == 270
        val uprightW = if (swapped) res.height else res.width
        val uprightH = if (swapped) res.width else res.height
        previewAspectRatio = (uprightW.toFloat() / uprightH.coerceAtLeast(1).toFloat()).coerceIn(0.4f, 2.0f)
        cameraHandler.post { closeCameraInternal(releaseReader = true) }
        surfaceGeneration += 1
    }

    fun takePhoto() {
        if (holder.isTakingPhoto) return
        syncHolderSettings()
        val camera = holder.device ?: return
        val session = holder.session ?: return
        val reader = holder.imageReader ?: return

        val cameraId = holder.activeCameraId ?: cameraManager.cameraIdList.firstOrNull() ?: return
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val displayRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            (context as? Activity)?.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        }

        val rotationCompensation = when (displayRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK
        val jpegOrientation = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            (sensorOrientation + rotationCompensation) % 360
        } else {
            (sensorOrientation - rotationCompensation + 360) % 360
        }

        val snapAuto = holder.currentAutoMode
        val snapKelvin = holder.currentKelvin
        val snapTint = holder.currentTint
        val snapContrast = holder.currentContrast
        val snapSat = holder.currentSaturation
        val snapEv = holder.currentExposureEv
        val snapMetering = holder.currentMeteringMode
        val snapBoxNorm = holder.currentExposureBoxNorm
        val snapBoxSize = holder.currentExposureBoxSizeNorm

        holder.isTakingPhoto = true
        fun playShutterFeedback() {
            coroutineScope.launch {
                isShutterPressed = true
                shutterFlashAlpha.snapTo(0.45f)
                shutterFlashAlpha.animateTo(0f, animationSpec = tween(220))
                isShutterPressed = false
            }
        }
        coroutineScope.launch {
            delay(7500)
            if (holder.isTakingPhoto) {
                holder.isTakingPhoto = false
                holder.flashPrecaptureActive = false
                holder.pendingFlashCaptureRunner = null
                holder.pendingTorchFallbackActive = false
            }
        }

        reader.setOnImageAvailableListener({ imageReader ->
            val image = try { imageReader.acquireLatestImage() } catch (_: Exception) { null }
            if (image == null) {
                holder.isTakingPhoto = false
                return@setOnImageAvailableListener
            }
            val bytes = try {
                val buffer = image.planes[0].buffer
                ByteArray(buffer.remaining()).also { buffer.get(it) }
            } finally {
                image.close()
            }

            val snapTargetedColor = holder.currentTargetedColor
            val snapColorBands = holder.currentColorBands
            val snapChannelSwap = holder.currentChannelSwap
            val snapLookGrade = holder.currentLookGrade
            val snapExpMode = holder.currentExperienceMode
            val snapApplyColorGrading = !snapAuto || snapLookGrade.isActive || snapExpMode == ExperienceMode.AESTHETIC
            coroutineScope.launch(Dispatchers.Default) {
                val hasSelectiveHsl = snapApplyColorGrading && (
                    snapLookGrade.isActive ||
                        snapChannelSwap ||
                        (snapTargetedColor.isActive &&
                            (snapTargetedColor.deltaHue != 0f || snapTargetedColor.deltaSat != 0f || snapTargetedColor.deltaLum != 0f)) ||
                        snapColorBands.hasAnyModification
                    )

                val onSaved: (Uri) -> Unit = { savedUri ->
                    mainHandler.post { lastCapturedUri = savedUri }
                }
                val thumbBmp = decodeThumbnailFromBytes(bytes, context.cacheDir)
                if (thumbBmp != null) {
                    val displayThumb = if (hasSelectiveHsl) {
                        applyTargetedHslToBitmap(thumbBmp, snapTargetedColor, snapColorBands, snapChannelSwap, snapLookGrade)
                    } else {
                        thumbBmp
                    }
                    val imgBmp = displayThumb.asImageBitmap()
                    withContext(Dispatchers.Main) { lastCapturedThumb = imgBmp }
                }

                val ok = withContext(Dispatchers.IO) {
                    if (hasSelectiveHsl) {
                        var decoded: Bitmap? = null
                        var graded: Bitmap? = null
                        try {
                            val orientation = readExifOrientation(bytes, context.cacheDir)
                            val opts = BitmapFactory.Options().apply {
                                inMutable = true
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                            }
                            decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                            if (decoded != null) {
                                graded = applyTargetedHslToBitmap(decoded, snapTargetedColor, snapColorBands, snapChannelSwap, snapLookGrade)
                                saveOutputToGallery(context, null, graded, orientation, onSaved)
                            } else {
                                saveOutputToGallery(context, bytes, null, ExifInterface.ORIENTATION_NORMAL, onSaved)
                            }
                        } catch (e: Exception) {
                            Log.e("CameraScreen", "Targeted HSL post-processing failed", e)
                            saveOutputToGallery(context, bytes, null, ExifInterface.ORIENTATION_NORMAL, onSaved)
                        } finally {
                            if (graded != null && graded !== decoded) graded.recycle()
                            decoded?.recycle()
                        }
                    } else {
                        saveOutputToGallery(context, bytes, null, ExifInterface.ORIENTATION_NORMAL, onSaved)
                    }
                }

                holder.isTakingPhoto = false
                Log.d("CameraScreen", if (ok) "Photo saved to gallery" else "Failed to save photo")
            }
        }, cameraHandler)

        val activeRawReader = if (holder.rawCaptureEnabled) holder.rawImageReader else null
        val shotResultDeferred = CompletableDeferred<TotalCaptureResult?>()
        holder.lastStillCaptureResult = null
        activeRawReader?.setOnImageAvailableListener({ rawImgReader ->
            val rawImg = try { rawImgReader.acquireLatestImage() } catch (_: Exception) { null } ?: return@setOnImageAvailableListener
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val capRes = withTimeoutOrNull(5000L) { shotResultDeferred.await() }
                        ?: holder.lastStillCaptureResult
                    if (capRes != null) {
                        val savedRaw = saveRawDngToGallery(
                            context = context,
                            characteristics = characteristics,
                            result = capRes,
                            rawImage = rawImg,
                            jpegOrientationDegrees = jpegOrientation
                        )
                        withContext(Dispatchers.Main) {
                            if (savedRaw) {
                                showStatus(context.getString(R.string.status_raw_saved))
                            } else {
                                showStatus(context.getString(R.string.status_raw_failed_jpeg_ok))
                            }
                        }
                    }
                } finally {
                    try { rawImg.close() } catch (_: Exception) {}
                }
            }
        }, cameraHandler)

        fun buildStillCaptureRequest(includeRaw: Boolean, fallbackMode: Boolean): CaptureRequest {
            return camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                if (includeRaw) {
                    activeRawReader?.surface?.let { addTarget(it) }
                }
                applyCamera2Settings(
                    builder = this,
                    auto = snapAuto,
                    kVal = snapKelvin,
                    tVal = snapTint,
                    contrastVal = snapContrast,
                    saturationVal = snapSat,
                    exposureEv = snapEv,
                    meteringModeVal = snapMetering,
                    boxNorm = snapBoxNorm,
                    boxSizeNorm = snapBoxSize,
                    isStillCapture = true,
                    compatibilityFallback = fallbackMode
                )
                set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)
            }.build()
        }

        val desiredFlash = if (holder.hasFlash) holder.flashMode else FlashMode.OFF
        val supportsAlwaysFlash = holder.availableAeModes.contains(CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
        val supportsAutoFlash = holder.availableAeModes.contains(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
        val needsAePrecapture =
            (desiredFlash == FlashMode.ON && supportsAlwaysFlash) ||
                (desiredFlash == FlashMode.AUTO && supportsAutoFlash &&
                    holder.lastAeState != CaptureResult.CONTROL_AE_STATE_CONVERGED)
        val needsTorchFallback = (desiredFlash == FlashMode.ON && !supportsAlwaysFlash)
        val usedFlashSync = needsAePrecapture || needsTorchFallback

        fun unlockAfterFlashCapture() {
            if (!usedFlashSync) return
            cameraHandler.post {
                val activeDev = holder.device
                val activeSess = holder.session
                val previewSurf = holder.activeSurface
                synchronized(holder) {
                    holder.flashPrecaptureActive = false
                    holder.flashPrecaptureSawActiveState = false
                    holder.pendingFlashCaptureRunner = null
                    holder.pendingTorchFallbackActive = false
                }
                if (activeDev != null && activeSess != null && previewSurf != null && previewSurf.isValid) {
                    try {
                        val unlockReq = activeDev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(previewSurf)
                            applyCamera2Settings(
                                builder = this,
                                auto = snapAuto,
                                kVal = snapKelvin,
                                tVal = snapTint,
                                contrastVal = snapContrast,
                                saturationVal = snapSat,
                                exposureEv = snapEv,
                                meteringModeVal = snapMetering,
                                boxNorm = snapBoxNorm,
                                boxSizeNorm = snapBoxSize,
                                isStillCapture = false
                            )
                            if (holder.availableAfModes.isNotEmpty()) {
                                set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                set(
                                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL
                                )
                            }
                        }.build()
                        activeSess.capture(unlockReq, null, cameraHandler)
                    } catch (_: Exception) {
                    }
                    holder.previewBuilder?.let { pb ->
                        applyCamera2Settings(
                            builder = pb,
                            auto = holder.currentAutoMode,
                            kVal = holder.currentKelvin,
                            tVal = holder.currentTint,
                            contrastVal = holder.currentContrast,
                            saturationVal = holder.currentSaturation,
                            exposureEv = holder.currentExposureEv,
                            meteringModeVal = holder.currentMeteringMode,
                            boxNorm = holder.currentExposureBoxNorm,
                            boxSizeNorm = holder.currentExposureBoxSizeNorm,
                            isStillCapture = false
                        )
                        submitRepeatingRequest(activeSess, pb)
                    }
                }
            }
        }

        val stillCallback = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                s: CameraCaptureSession,
                r: CaptureRequest,
                result: TotalCaptureResult
            ) {
                holder.lastStillCaptureResult = result
                shotResultDeferred.complete(result)
                unlockAfterFlashCapture()
            }

            override fun onCaptureFailed(
                s: CameraCaptureSession,
                r: CaptureRequest,
                failure: CaptureFailure
            ) {
                shotResultDeferred.complete(null)
                holder.isTakingPhoto = false
                unlockAfterFlashCapture()
            }
        }

        fun executeStillCaptureNow() {
            val activeSess = holder.session
            if (activeSess == null || holder.device == null) {
                holder.isTakingPhoto = false
                unlockAfterFlashCapture()
                return
            }
            try {
                playShutterFeedback()
                try { shutterSound?.play(MediaActionSound.SHUTTER_CLICK) } catch (_: Throwable) {}
                activeSess.capture(
                    buildStillCaptureRequest(includeRaw = activeRawReader != null, fallbackMode = false),
                    stillCallback,
                    cameraHandler
                )
            } catch (e: Exception) {
                Log.w("CameraScreen", "Primary still capture failed, trying compatibility fallback", e)
                try {
                    activeSess.capture(
                        buildStillCaptureRequest(includeRaw = false, fallbackMode = true),
                        stillCallback,
                        cameraHandler
                    )
                } catch (e2: Exception) {
                    shotResultDeferred.complete(null)
                    holder.isTakingPhoto = false
                    unlockAfterFlashCapture()
                    Log.e("CameraScreen", "Failed to capture photo even with fallback", e2)
                    mainHandler.post { showStatus(context.getString(R.string.status_capture_failed)) }
                }
            }
        }

        if (needsAePrecapture) {
            cameraHandler.post {
                val previewSurf = holder.activeSurface
                val activeDev = holder.device
                val activeSess = holder.session
                if (previewSurf == null || !previewSurf.isValid || activeDev == null || activeSess == null) {
                    executeStillCaptureNow()
                    return@post
                }
                val startMs = System.currentTimeMillis()
                synchronized(holder) {
                    holder.flashPrecaptureActive = true
                    holder.flashPrecaptureSawActiveState = false
                    holder.flashPrecaptureStartMs = startMs
                    holder.pendingFlashCaptureRunner = { executeStillCaptureNow() }
                }
                try {
                    val precaptureReq = activeDev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(previewSurf)
                        applyCamera2Settings(
                            builder = this,
                            auto = snapAuto,
                            kVal = snapKelvin,
                            tVal = snapTint,
                            contrastVal = snapContrast,
                            saturationVal = snapSat,
                            exposureEv = snapEv,
                            meteringModeVal = snapMetering,
                            boxNorm = snapBoxNorm,
                            boxSizeNorm = snapBoxSize,
                            isStillCapture = false
                        )
                        // Ensure AE lock doesn't block pre-flash metering
                        set(CaptureRequest.CONTROL_AE_LOCK, false)
                        set(
                            CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                            CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START
                        )
                        if (holder.availableAfModes.isNotEmpty()) {
                            set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                        }
                    }.build()
                    activeSess.capture(precaptureReq, captureCallback, cameraHandler)
                    // Safety watchdog: if a HAL stops delivering repeating frames during precapture, fire cleanly after 1450ms
                    cameraHandler.postDelayed({
                        val timedOutRunner = synchronized(holder) {
                            if (holder.flashPrecaptureActive && holder.flashPrecaptureStartMs == startMs) {
                                holder.flashPrecaptureActive = false
                                holder.flashPrecaptureSawActiveState = false
                                val cb = holder.pendingFlashCaptureRunner
                                holder.pendingFlashCaptureRunner = null
                                cb
                            } else null
                        }
                        timedOutRunner?.invoke()
                    }, 1450L)
                } catch (e: Exception) {
                    Log.w("CameraScreen", "AE precapture trigger failed, shooting directly", e)
                    synchronized(holder) {
                        holder.flashPrecaptureActive = false
                        holder.pendingFlashCaptureRunner = null
                    }
                    executeStillCaptureNow()
                }
            }
        } else if (needsTorchFallback) {
            // Fallback for rare devices without ON_ALWAYS_FLASH: synchronized 360ms pre-flash torch metering
            cameraHandler.post {
                holder.pendingTorchFallbackActive = true
                val activeSess = holder.session
                val pb = holder.previewBuilder
                if (activeSess != null && pb != null) {
                    applyCamera2Settings(
                        builder = pb,
                        auto = snapAuto,
                        kVal = snapKelvin,
                        tVal = snapTint,
                        contrastVal = snapContrast,
                        saturationVal = snapSat,
                        exposureEv = snapEv,
                        meteringModeVal = snapMetering,
                        boxNorm = snapBoxNorm,
                        boxSizeNorm = snapBoxSize,
                        isStillCapture = false
                    )
                    submitRepeatingRequest(activeSess, pb)
                }
                cameraHandler.postDelayed({ executeStillCaptureNow() }, 360L)
            }
        } else {
            executeStillCaptureNow()
        }
    }

    // Note: currentIsStudioMode declared near top of CameraScreen so closeCameraInternal/startCameraInternal can read it safely
    var prevStudioMode by remember { mutableStateOf(isStudioMode) }

    BackHandler(enabled = isStudioMode) {
        isStudioMode = false
    }

    LaunchedEffect(isStudioMode) {
        if (isStudioMode) {
            prevStudioMode = true
            isPickerActive = false
            isColorPipetteActive = false
            isExposureBoxPicking = false
            textureViewInstance = null
            cameraHandler.post {
                synchronized(holder) { holder.pendingReopenSurfaceTexture = null }
                closeCameraInternal(releaseReader = true)
            }
        } else if (prevStudioMode) {
            // Stüdyo'dan kameraya dönüşte yeni TextureView zaten sıfırdan oluşturuluyor;
            // surfaceGeneration'ı artırma (yoksa yeni TextureView açılırken iptal olur!).
            prevStudioMode = false
            holder.previewFallbackLevel = 0
        }
    }

    LaunchedEffect(hasCameraPermission, textureViewInstance, surfaceGeneration, isStudioMode) {
        if (!isStudioMode && hasCameraPermission) {
            textureViewInstance?.let { tv ->
                if (tv.isAvailable) {
                    tv.surfaceTexture?.let { startCamera(it) }
                }
            }
        }
    }

    // Kendi kendini onaran kamera bekçisi: herhangi bir HAL gecikmesinde siyah ekran kalmasını önler
    LaunchedEffect(isStudioMode, hasCameraPermission, cameraReady, textureViewInstance, surfaceGeneration) {
        if (!isStudioMode && hasCameraPermission && !cameraReady) {
            repeat(3) {
                delay(900)
                if (currentIsStudioMode || cameraReady) return@LaunchedEffect
                val tv = textureViewInstance
                val st = tv?.surfaceTexture
                val valid = tv != null && tv.isAvailable && st != null &&
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !st.isReleased)
                if (valid && st != null) {
                    cameraHandler.post {
                        if (!holder.isDisposed && !currentIsStudioMode && holder.device == null) {
                            synchronized(holder) {
                                holder.isOpening = false
                                holder.isClosingDevice = false
                            }
                            startCameraInternal(st)
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                holder.previewFallbackLevel = 0
                cameraHandler.post {
                    if (!currentIsStudioMode && !holder.isDisposed && holder.device == null) {
                        val tv = textureViewInstance
                        val st = tv?.surfaceTexture
                        val valid = tv != null && tv.isAvailable && st != null &&
                            (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !st.isReleased)
                        if (valid && st != null) {
                            startCameraInternal(st)
                        }
                    }
                }
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                cameraHandler.post {
                    synchronized(holder) { holder.pendingReopenSurfaceTexture = null }
                    closeCameraInternal(releaseReader = true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            holder.isDisposed = true
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraHandler.post {
                closeCameraInternal(releaseReader = true)
                preTiramisuGlRenderer?.release()
                cameraThread.quitSafely()
            }
            try { shutterSound?.release() } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(cameraReady, isStudioMode, meteringMode, exposureBoxNorm, exposureBoxSizeNorm, surfaceGeneration) {
        while (cameraReady && !isStudioMode) {
            if (!holder.isTakingPhoto) {
                measureAndApplyExposureMetering(applyEvAdjust = false)
            }
            delay(450)
        }
    }

    val displayKelvin = if (isAutoMode) liveAutoKelvin else kelvinValue
    val displayTint = if (isAutoMode) liveAutoTint else tintValue
    val evDisplayVal = exposureEvIndex * aeStep
    val haptic = LocalHapticFeedback.current

    // Her ayar kategorisinde varsayılan dışına çıkılıp çıkılmadığını gösteren durumlar
    val isWbModified = !isAutoMode && (abs(kelvinValue - liveAutoKelvin) > 120f || abs(tintValue - liveAutoTint) > 3f)
    val isEvModified = exposureEvIndex != 0 || aeLockEnabled
    val isColorModified = abs(saturationValue - DEFAULT_SATURATION) > 0.02f ||
        abs(contrastValue - DEFAULT_CONTRAST) > 0.02f ||
        channelSwapEnabled ||
        targetedColorConfig.isActive ||
        colorBandsConfig.hasAnyModification
    val isMeteringModified = meteringMode == ExposureMeteringMode.BOX_SPOT
    val hasAnyManualChange = isWbModified || isEvModified || isColorModified || isMeteringModified

    // Arayüz metin kaynakları
    val strModeAuto = stringResource(R.string.mode_auto)
    val strModePro = stringResource(R.string.mode_pro)
    val strModeStudio = stringResource(R.string.mode_studio)
    val strModeCameraShort = stringResource(R.string.mode_camera_short)
    val strModeIrShort = stringResource(R.string.mode_ir_short)
    val strSettingsCd = stringResource(R.string.cd_settings)
    val strBadgeAeLock = stringResource(R.string.badge_ae_lock)
    val strWbShort = stringResource(R.string.label_wb_short)
    val strTintShort = stringResource(R.string.label_tint)
    val strEvShort = stringResource(R.string.label_ev_short)
    val strResFallback = stringResource(R.string.label_res_fallback)
    val strGridCd = stringResource(R.string.cd_grid_toggle)
    val strPanelCd = stringResource(R.string.cd_panel_toggle)
    val strFlashCd = stringResource(R.string.cd_flash_toggle)
    val strFlipCameraCd = stringResource(R.string.cd_flip_camera)
    val strShutterCd = stringResource(R.string.cd_shutter)
    val strAllReset = stringResource(R.string.status_all_reset)
    val strHighlightOn = stringResource(R.string.status_highlight_on)
    val strHighlightOff = stringResource(R.string.status_highlight_off)
    val strTapColor = stringResource(R.string.status_tap_color)
    val strTapWb = stringResource(R.string.status_tap_wb)
    val strEnterName = stringResource(R.string.status_enter_name)
    val strBoxCentered = stringResource(R.string.status_box_centered)
    val strZebraOn = stringResource(R.string.status_zebra_on)
    val strZebraOff = stringResource(R.string.status_zebra_off)
    val strLevelOn = stringResource(R.string.status_level_on)
    val strLevelOff = stringResource(R.string.status_level_off)
    val strTimerOff = stringResource(R.string.timer_off)
    val strTimer3s = stringResource(R.string.timer_3s)
    val strTimer10s = stringResource(R.string.timer_10s)
    val strCompare = stringResource(R.string.action_compare)
    val strCompareBanner = stringResource(R.string.banner_compare_auto)
    val strRaw = stringResource(R.string.action_raw)
    val strRawOn = stringResource(R.string.status_raw_on)
    val strRawOff = stringResource(R.string.status_raw_off)
    val strGalleryCd = stringResource(R.string.cd_gallery)
    val strLevelCd = stringResource(R.string.cd_level_toggle)
    val strTimerCd = stringResource(R.string.cd_timer_toggle)
    val strResetAllCd = stringResource(R.string.cd_reset_all)
    val strCancelPick = stringResource(R.string.action_cancel_pick)
    val strBadgeZebra = stringResource(R.string.badge_zebra)
    val strBadgeBox = stringResource(R.string.badge_box)
    val strBadgeSwap = stringResource(R.string.badge_channel_swap)

    fun triggerCaptureWithTimer() {
        if (timerCountdown > 0) {
            timerJob?.cancel()
            timerJob = null
            timerCountdown = 0
            return
        }
        if (timerSeconds <= 0) {
            takePhoto()
        } else {
            timerJob?.cancel()
            timerJob = coroutineScope.launch {
                for (sec in timerSeconds downTo 1) {
                    timerCountdown = sec
                    delay(1000)
                }
                timerCountdown = 0
                takePhoto()
            }
        }
    }

    DisposableEffect(cameraReady, isStudioMode) {
        onRegisterVolumeShutter(if (cameraReady && !isStudioMode) { { triggerCaptureWithTimer() } } else null)
        onDispose { onRegisterVolumeShutter(null) }
    }

    fun openProControl(tab: ControlTab) {
        isPickerActive = false
        isColorPipetteActive = false
        isExposureBoxPicking = false
        if (isAutoMode) {
            kelvinValue = liveAutoKelvin.coerceIn(KELVIN_MIN, KELVIN_MAX)
            tintValue = liveAutoTint.coerceIn(-100f, 100f)
            isAutoMode = false
            updatePreviewSettings()
        }
        activeTab = tab
        isDeckCollapsed = false
    }

    fun applyAestheticPreset(preset: CameraPreset, strength: Float) {
        selectedAestheticPreset = preset
        val s = strength.coerceIn(0f, 1f)
        aestheticFilterStrength = s
        isPickerActive = false
        isColorPipetteActive = false
        isExposureBoxPicking = false
        // ALWAYS keep Camera2 in hardware Auto-HDR + AWB_AUTO so preview & photos stay razor-sharp!
        // All aesthetic color grading runs at 60fps in our AGSL GPU shader + identical full-res JPEG processor.
        isAutoMode = true
        kelvinValue = 5500f
        tintValue = 0f
        contrastValue = DEFAULT_CONTRAST
        saturationValue = DEFAULT_SATURATION
        if (preset.name == "Natural" || s <= 0.01f) {
            activeLookGrade = LookGradeParams()
            channelSwapEnabled = false
            colorBandsConfig = ColorBandsConfig()
            targetedColorConfig = TargetedColorConfig()
        } else {
            activeLookGrade = scaleLookGrade(preset.lookGrade, s)
            channelSwapEnabled = preset.channelSwap && s >= 0.5f
            colorBandsConfig = scaleColorBands(preset.colorBands, s)
            targetedColorConfig = TargetedColorConfig()
        }
        updatePreviewSettings()
    }

    fun applyPresetDirect(preset: CameraPreset, announce: Boolean = true) {
        isPickerActive = false
        isColorPipetteActive = false
        isExposureBoxPicking = false
        val isPureShaderLook = preset.lookGrade.isActive &&
            abs(preset.kelvin - 5500f) < 1f &&
            abs(preset.tint) < 0.5f &&
            abs(preset.contrast - DEFAULT_CONTRAST) < 0.01f &&
            abs(preset.saturation - DEFAULT_SATURATION) < 0.01f
        isAutoMode = isPureShaderLook
        kelvinValue = preset.kelvin
        tintValue = preset.tint
        contrastValue = preset.contrast
        saturationValue = preset.saturation
        channelSwapEnabled = preset.channelSwap
        colorBandsConfig = preset.colorBands
        activeLookGrade = preset.lookGrade
        targetedColorConfig = TargetedColorConfig()
        updatePreviewSettings()
        if (announce) {
            showStatus(context.getString(R.string.status_preset_applied, preset.name))
        }
    }

    fun switchExperienceMode(newMode: ExperienceMode, announce: Boolean = true) {
        experienceMode = newMode
        sharedPrefs.edit {
            putString("experience_mode", newMode.prefKey)
            putBoolean("experience_mode_chosen_v1", true)
        }
        isPickerActive = false
        isColorPipetteActive = false
        isExposureBoxPicking = false
        isDeckCollapsed = false
        when (newMode) {
            ExperienceMode.AESTHETIC -> {
                showZebra = false
                aeLockEnabled = false
                applyAestheticPreset(selectedAestheticPreset, aestheticFilterStrength)
            }
            ExperienceMode.IR_LAB -> {
                // Kızılötesi modu kırmızı Aerochrome filtresiyle değil normal renklerle ve
                // kompakt White Balance · Exposure · Color · Metering sekme şeridi küçültülmüş şekilde başlar.
                isAutoMode = false
                kelvinValue = liveAutoKelvin.coerceIn(KELVIN_MIN, KELVIN_MAX)
                tintValue = liveAutoTint.coerceIn(-100f, 100f)
                contrastValue = DEFAULT_CONTRAST
                saturationValue = DEFAULT_SATURATION
                channelSwapEnabled = false
                colorBandsConfig = ColorBandsConfig()
                targetedColorConfig = TargetedColorConfig()
                activeLookGrade = LookGradeParams()
                activeTab = ControlTab.BD
                isDeckCollapsed = true
                updatePreviewSettings()
            }
            ExperienceMode.PRO -> {
                activeLookGrade = LookGradeParams()
                updatePreviewSettings()
            }
        }
        if (announce) {
            val modeTitle = when (newMode) {
                ExperienceMode.AESTHETIC -> context.getString(R.string.exp_aesthetic_title)
                ExperienceMode.IR_LAB -> context.getString(R.string.exp_ir_title)
                ExperienceMode.PRO -> context.getString(R.string.exp_pro_title)
            }
            showStatus(context.getString(R.string.status_exp_mode_changed, modeTitle))
        }
    }

    val deckBgAlpha by animateFloatAsState(
        targetValue = if (isAnySliderDragging) 0.14f else 0.78f,
        animationSpec = tween(150),
        label = "deckBgAlpha"
    )
    val ghostChromeAlpha by animateFloatAsState(
        targetValue = if (isAnySliderDragging) 0.08f else 1f,
        animationSpec = tween(150),
        label = "ghostChromeAlpha"
    )

    if (isStudioMode) {
        PhotoEditorScreen(
            initialUri = studioPhotoUri ?: lastCapturedUri,
            photoNonce = studioPhotoNonce,
            lastCapturedUri = lastCapturedUri,
            experienceMode = experienceMode,
            savedPresets = savedPresets.toList(),
            onSaveCustomPreset = { preset ->
                savedPresets.removeAll { it.name.equals(preset.name, ignoreCase = true) }
                savedPresets.add(preset)
                savePresetsToStorage()
            },
            onDeleteCustomPreset = { preset ->
                savedPresets.remove(preset)
                savePresetsToStorage()
            },
            onSwitchToCamera = { wantPro ->
                if (experienceMode != ExperienceMode.AESTHETIC) {
                    isAutoMode = !wantPro
                }
                isStudioMode = false
            },
            onPhotoSaved = { savedUri ->
                lastCapturedUri = savedUri
            }
        )
        return
    }

    // Ana dikey yerleşim + ilk açılış deneyim seçici katmanı
    Box(modifier = Modifier.fillMaxSize().background(IrPalette.VoidBlack)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (showExperienceWelcome) 0f else 1f)
            .background(IrPalette.VoidBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1) ÜST ARAÇ ÇUBUĞU: Deneyim moduna göre uyarlanmış sade üst şerit + Ayarlar (⚙)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Sol kapsül: Estetik modda [Kamera | Stüdyo], IR Lab'da [Oto | IR Lab | Stüdyo], Pro'da [Oto | Pro | Stüdyo]
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(IrPalette.HudCard)
                    .border(1.dp, IrPalette.BorderSubtle, RoundedCornerShape(50))
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (experienceMode == ExperienceMode.AESTHETIC) {
                    ModeSegmentItem(
                        text = strModeCameraShort,
                        selected = true,
                        activeColor = IrPalette.IrMagenta,
                        enabled = true,
                        onClick = {}
                    )
                    ModeSegmentItem(
                        text = strModeStudio,
                        selected = false,
                        activeColor = IrPalette.IrCyan,
                        enabled = true,
                        onClick = { isStudioMode = true }
                    )
                } else {
                    ModeSegmentItem(
                        text = strModeAuto,
                        selected = isAutoMode,
                        activeColor = IrPalette.IrEmerald,
                        enabled = true,
                        onClick = {
                            isPickerActive = false
                            isColorPipetteActive = false
                            isExposureBoxPicking = false
                            activeLookGrade = LookGradeParams()
                            isAutoMode = true
                            updatePreviewSettings()
                        }
                    )
                    ModeSegmentItem(
                        text = if (experienceMode == ExperienceMode.IR_LAB) strModeIrShort else strModePro,
                        selected = !isAutoMode,
                        activeColor = if (experienceMode == ExperienceMode.IR_LAB) IrPalette.IrCoral else IrPalette.IrAmber,
                        enabled = true,
                        onClick = {
                            isPickerActive = false
                            isColorPipetteActive = false
                            isExposureBoxPicking = false
                            if (isAutoMode) {
                                kelvinValue = liveAutoKelvin.coerceIn(KELVIN_MIN, KELVIN_MAX)
                                tintValue = liveAutoTint.coerceIn(-100f, 100f)
                                isAutoMode = false
                            }
                            updatePreviewSettings()
                        }
                    )
                    ModeSegmentItem(
                        text = strModeStudio,
                        selected = false,
                        activeColor = IrPalette.IrCyan,
                        enabled = true,
                        onClick = { isStudioMode = true }
                    )
                }
            }

            // Sağ: Moda özel hızlı araçlar + her modda Ayarlar (⚙)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (experienceMode != ExperienceMode.AESTHETIC) {
                    val activeRes = selectedResolution
                    val resLabel = if (activeRes != null) formatMegapixels(activeRes) else strResFallback
                    TopBadgeButton(
                        text = resLabel,
                        active = false,
                        accentColor = IrPalette.IrCyan,
                        enabled = availableResolutions.isNotEmpty(),
                        onClick = { showResolutionDialog = true }
                    )

                    if (supportsRaw) {
                        TopBadgeButton(
                            text = strRaw,
                            active = rawEnabled,
                            accentColor = IrPalette.IrAmber,
                            enabled = cameraReady,
                            onClick = {
                                rawEnabled = !rawEnabled
                                holder.rawCaptureEnabled = rawEnabled
                                showStatus(if (rawEnabled) strRawOn else strRawOff)
                                cameraHandler.post { closeCameraInternal(releaseReader = true) }
                                surfaceGeneration += 1
                            }
                        )
                    }
                }

                if (hasFlash) {
                    TopIconButton(
                        active = flashMode != FlashMode.OFF,
                        accentColor = if (flashMode == FlashMode.TORCH) IrPalette.IrEmerald else IrPalette.IrAmber,
                        contentDesc = strFlashCd,
                        onClick = {
                            flashMode = when (flashMode) {
                                FlashMode.OFF -> FlashMode.AUTO
                                FlashMode.AUTO -> FlashMode.ON
                                FlashMode.ON -> FlashMode.TORCH
                                FlashMode.TORCH -> FlashMode.OFF
                            }
                            updatePreviewSettings()
                            val flashLabel = when (flashMode) {
                                FlashMode.OFF -> context.getString(R.string.flash_off)
                                FlashMode.AUTO -> context.getString(R.string.flash_auto)
                                FlashMode.ON -> context.getString(R.string.flash_on)
                                FlashMode.TORCH -> context.getString(R.string.flash_torch)
                            }
                            showStatus(context.getString(R.string.status_flash_set, flashLabel))
                        }
                    ) { tint ->
                        FlashIcon(mode = flashMode, tint = tint, modifier = Modifier.size(14.dp))
                    }
                }

                if (experienceMode == ExperienceMode.AESTHETIC) {
                    TopIconButton(
                        active = showGrid,
                        accentColor = IrPalette.IrMagenta,
                        contentDesc = strGridCd,
                        onClick = { showGrid = !showGrid }
                    ) { tint ->
                        GridIcon(tint = tint, modifier = Modifier.size(13.dp))
                    }
                }

                TopIconButton(
                    active = timerSeconds > 0,
                    accentColor = IrPalette.IrAmber,
                    contentDesc = strTimerCd,
                    onClick = {
                        timerSeconds = when (timerSeconds) {
                            0 -> 3
                            3 -> 10
                            else -> 0
                        }
                        val lbl = when (timerSeconds) {
                            3 -> strTimer3s
                            10 -> strTimer10s
                            else -> strTimerOff
                        }
                        showStatus(context.getString(R.string.status_timer_set, lbl))
                    }
                ) { tint ->
                    if (timerSeconds > 0) {
                        Text(
                            text = "${timerSeconds}s",
                            color = tint,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        TimerIcon(tint = tint, modifier = Modifier.size(13.dp))
                    }
                }

                TopIconButton(
                    active = showSettingsDialog,
                    accentColor = when (experienceMode) {
                        ExperienceMode.AESTHETIC -> IrPalette.IrMagenta
                        ExperienceMode.IR_LAB -> IrPalette.IrCoral
                        ExperienceMode.PRO -> IrPalette.IrAmber
                    },
                    contentDesc = strSettingsCd,
                    onClick = { showSettingsDialog = true }
                ) { tint ->
                    SettingsGearIcon(tint = tint, modifier = Modifier.size(14.dp))
                }
            }
        }

        // 2) ÖNİZLEME ALANI: Üste hizalı, hiçbir ayar paneli üstüne binmez
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(IrPalette.VoidBlack),
            contentAlignment = Alignment.TopCenter
        ) {
            val containerRatio = if (maxHeight > 0.dp) maxWidth / maxHeight else 0.75f
            val viewfinderModifier = if (containerRatio > previewAspectRatio) {
                Modifier.fillMaxHeight().aspectRatio(previewAspectRatio)
            } else {
                Modifier.fillMaxWidth().aspectRatio(previewAspectRatio)
            }

            BoxWithConstraints(
                modifier = viewfinderModifier
                    .clip(RoundedCornerShape(14.dp))
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                var isDraggingExposureBox by remember { mutableStateOf(false) }
                val keepFocusBoxPinned = experienceMode == ExperienceMode.PRO &&
                    !isAutoMode &&
                    activeTab == ControlTab.OLCUM &&
                    !isDeckCollapsed
                LaunchedEffect(focusBoxTriggerNonce, isDraggingExposureBox, keepFocusBoxPinned) {
                    if (isDraggingExposureBox || keepFocusBoxPinned) {
                        focusBoxVisible = true
                    } else if (focusBoxVisible) {
                        delay(500)
                        focusBoxVisible = false
                    }
                }
                val focusBoxAlpha by animateFloatAsState(
                    targetValue = if (focusBoxVisible) 1f else 0f,
                    animationSpec = tween(if (focusBoxVisible) 75 else 240),
                    label = "focusBoxAlpha"
                )

                key(surfaceGeneration) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            // Tek dokunma: Noktasal BD, Renk Pipeti veya Ölçüm Kutusu taşıma
                            .pointerInput(isPickerActive, isColorPipetteActive, isExposureBoxPicking, meteringMode) {
                                detectTapGestures(onTap = { offset ->
                                    val view = textureViewInstance ?: return@detectTapGestures
                                    if (isPickerActive) {
                                        val sw = (view.width / 2).coerceIn(180, 480)
                                        val sh = (view.height / 2).coerceIn(180, 480)
                                        val bitmap = try { view.getBitmap(sw, sh) } catch (_: Exception) { null }
                                        if (bitmap != null) {
                                            try {
                                                val x = (offset.x * bitmap.width / view.width.coerceAtLeast(1)).toInt()
                                                val y = (offset.y * bitmap.height / view.height.coerceAtLeast(1)).toInt()
                                                val (newK, newT) = calculateSpotWhiteBalance(
                                                    bitmap = bitmap,
                                                    x = x,
                                                    y = y,
                                                    currentGains = if (holder.hasRealHardwareGains) holder.lastHardwareGains else kelvinToGains(kelvinValue, tintValue)
                                                )
                                                pickedMarkerPos = offset
                                                kelvinValue = newK
                                                tintValue = newT
                                                isAutoMode = false
                                                isPickerActive = false
                                                updatePreviewSettings()
                                                showStatus("$strWbShort ${newK.toInt()}K · $strTintShort ${formatSigned(newT)}")
                                            } finally {
                                                bitmap.recycle()
                                            }
                                        }
                                    } else if (isColorPipetteActive) {
                                        val sw = (view.width / 2).coerceIn(180, 480)
                                        val sh = (view.height / 2).coerceIn(180, 480)
                                        val bitmap = try { view.getBitmap(sw, sh) } catch (_: Exception) { null }
                                        if (bitmap != null) {
                                            try {
                                                val x = (offset.x * bitmap.width / view.width.coerceAtLeast(1)).toInt().coerceIn(0, bitmap.width - 1)
                                                val y = (offset.y * bitmap.height / view.height.coerceAtLeast(1)).toInt().coerceIn(0, bitmap.height - 1)
                                                val pixel = bitmap[x, y]
                                                val hsv = FloatArray(3)
                                                AndroidColor.RGBToHSV(
                                                    AndroidColor.red(pixel),
                                                    AndroidColor.green(pixel),
                                                    AndroidColor.blue(pixel),
                                                    hsv
                                                )
                                                targetedColorConfig = targetedColorConfig.copy(
                                                    targetHue = hsv[0],
                                                    targetSat = hsv[1],
                                                    targetLum = hsv[2],
                                                    isActive = true,
                                                    previewColorInt = pixel
                                                )
                                                isColorPipetteActive = false
                                                colorPanelMode = ColorPanelMode.PIPET
                                                pickedMarkerPos = offset
                                                syncHolderSettings()
                                                updateTextureViewShader(
                                                    view = view,
                                                    config = targetedColorConfig,
                                                    colorBands = colorBandsConfig,
                                                    channelSwap = channelSwapEnabled,
                                                    lookGrade = activeLookGrade,
                                                    bypass = isComparingAuto || (isAutoMode && experienceMode != ExperienceMode.AESTHETIC && !activeLookGrade.isActive),
                                                    preTiramisuGlRenderer = preTiramisuGlRenderer
                                                )
                                                showStatus(context.getString(R.string.status_color_picked, getHueName(context, hsv[0])))
                                            } finally {
                                                bitmap.recycle()
                                            }
                                        }
                                    } else {
                                        val nx = (offset.x / view.width.coerceAtLeast(1)).coerceIn(0.08f, 0.92f)
                                        val ny = (offset.y / view.height.coerceAtLeast(1)).coerceIn(0.08f, 0.92f)
                                        exposureBoxNorm = Offset(nx, ny)
                                        meteringMode = ExposureMeteringMode.BOX_SPOT
                                        isExposureBoxPicking = false
                                        focusBoxVisible = true
                                        focusBoxTriggerNonce++
                                        updatePreviewSettings()
                                        measureAndApplyExposureMetering(applyEvAdjust = false)
                                    }
                                })
                            }
                            // Sürükleme: Ölçüm kutusunu akıcı taşıma
                            .pointerInput(meteringMode, exposureBoxSizeNorm, isPickerActive, isColorPipetteActive) {
                                if (meteringMode == ExposureMeteringMode.BOX_SPOT && !isPickerActive && !isColorPipetteActive) {
                                    detectDragGestures(
                                        onDragStart = { startOffset ->
                                            val boxCenterX = exposureBoxNorm.x * size.width
                                            val boxCenterY = exposureBoxNorm.y * size.height
                                            val halfSide = (minOf(size.width, size.height) * exposureBoxSizeNorm * 0.5f) + 56f
                                            isDraggingExposureBox = isExposureBoxPicking ||
                                                (abs(startOffset.x - boxCenterX) <= halfSide &&
                                                    abs(startOffset.y - boxCenterY) <= halfSide)
                                        },
                                        onDrag = { change, dragAmount ->
                                            if (isDraggingExposureBox) {
                                                change.consume()
                                                val nx = (exposureBoxNorm.x + dragAmount.x / size.width.coerceAtLeast(1))
                                                    .coerceIn(0.08f, 0.92f)
                                                val ny = (exposureBoxNorm.y + dragAmount.y / size.height.coerceAtLeast(1))
                                                    .coerceIn(0.08f, 0.92f)
                                                exposureBoxNorm = Offset(nx, ny)
                                            }
                                        },
                                        onDragEnd = {
                                            if (isDraggingExposureBox) {
                                                isDraggingExposureBox = false
                                                isExposureBoxPicking = false
                                                focusBoxVisible = true
                                                focusBoxTriggerNonce++
                                                updatePreviewSettings()
                                                measureAndApplyExposureMetering(applyEvAdjust = false)
                                            }
                                        },
                                        onDragCancel = {
                                            isDraggingExposureBox = false
                                        }
                                    )
                                }
                            }
                            // İki parmakla boyutlandırma: Ölçüm kutusu boyutunu ayarlama
                            .pointerInput(isPickerActive, isColorPipetteActive) {
                                if (!isPickerActive && !isColorPipetteActive) {
                                    detectTransformGestures { _, _, zoom, _ ->
                                        if (abs(zoom - 1f) > 0.015f) {
                                            meteringMode = ExposureMeteringMode.BOX_SPOT
                                            exposureBoxSizeNorm = (exposureBoxSizeNorm * zoom).coerceIn(0.14f, 0.62f)
                                            focusBoxVisible = true
                                            focusBoxTriggerNonce++
                                            updatePreviewSettings()
                                        }
                                    }
                                }
                            },
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                textureViewInstance = this
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                        holder.activePreviewSize?.let { preview ->
                                            st.setDefaultBufferSize(preview.width, preview.height)
                                        }
                                        applyPreviewTransform(this@apply, holder.activePreviewSize, holder.activeSensorOrientation)
                                        startCamera(st)
                                    }

                                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                                        holder.activePreviewSize?.let { preview ->
                                            st.setDefaultBufferSize(preview.width, preview.height)
                                        }
                                        applyPreviewTransform(this@apply, holder.activePreviewSize, holder.activeSensorOrientation)
                                    }

                                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                        preTiramisuGlRenderer?.detachOutputSurface(st)
                                        if (textureViewInstance?.surfaceTexture === st) {
                                            textureViewInstance = null
                                        }
                                        cameraHandler.post {
                                            val shouldClose = synchronized(holder) {
                                                if (holder.pendingReopenSurfaceTexture === st) {
                                                    holder.pendingReopenSurfaceTexture = null
                                                }
                                                holder.activeSurfaceTexture === st
                                            }
                                            if (shouldClose) {
                                                closeCameraInternal(releaseReader = true)
                                            }
                                        }
                                        return true
                                    }
                                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                }
                            }
                        },
                        update = { view ->
                            if (textureViewInstance !== view) {
                                textureViewInstance = view
                            }
                            holder.activePreviewSize?.let { preview ->
                                view.surfaceTexture?.setDefaultBufferSize(preview.width, preview.height)
                            }
                            applyPreviewTransform(view, holder.activePreviewSize, holder.activeSensorOrientation)
                        },
                        onRelease = { view ->
                            if (textureViewInstance === view) {
                                textureViewInstance = null
                            }
                        }
                    )
                }

                // Izgara, zebra, su terazisi, ölçüm kutusu ve seçici işareti
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (showGrid) {
                        val gridColor = Color.White.copy(alpha = 0.24f)
                        val stroke = 1.dp.toPx()
                        val thirdW = size.width / 3f
                        val thirdH = size.height / 3f
                        drawLine(gridColor, Offset(thirdW, 0f), Offset(thirdW, size.height), strokeWidth = stroke)
                        drawLine(gridColor, Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, size.height), strokeWidth = stroke)
                        drawLine(gridColor, Offset(0f, thirdH), Offset(size.width, thirdH), strokeWidth = stroke)
                        drawLine(gridColor, Offset(0f, thirdH * 2f), Offset(size.width, thirdH * 2f), strokeWidth = stroke)
                    }

                    // Parlak alan uyarısı (zebra çizgileri)
                    if (showZebra && zebraGrid.size == 32 * 32) {
                        val cellW = size.width / 32f
                        val cellH = size.height / 32f
                        val zebraColor = Color(0xCCFF3B30)
                        val zStroke = 1.3.dp.toPx()
                        for (r in 0 until 32) {
                            for (c in 0 until 32) {
                                if (zebraGrid[r * 32 + c]) {
                                    val x0 = c * cellW
                                    val y0 = r * cellH
                                    drawLine(zebraColor, Offset(x0, y0 + cellH), Offset(x0 + cellW, y0), strokeWidth = zStroke)
                                }
                            }
                        }
                    }

                    // Su terazisi (ufuk çizgisi): ±1,5° içinde yeşil yanar
                    if (showLevel) {
                        val isFlat = abs(levelAngleDeg) <= 1.5f
                        val levelColor = if (isFlat) IrPalette.IrEmerald else Color.White.copy(alpha = 0.85f)
                        val cx = size.width * 0.5f
                        val cy = size.height * 0.5f
                        val halfLen = minOf(size.width, size.height) * 0.20f
                        val rad = Math.toRadians(levelAngleDeg.toDouble())
                        val dx = (cos(rad) * halfLen).toFloat()
                        val dy = (sin(rad) * halfLen).toFloat()
                        val strokeW = if (isFlat) 2.2.dp.toPx() else 1.5.dp.toPx()
                        drawLine(
                            Color.White.copy(alpha = 0.35f),
                            Offset(cx - halfLen - 16.dp.toPx(), cy),
                            Offset(cx - halfLen - 6.dp.toPx(), cy),
                            strokeWidth = 1.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            Color.White.copy(alpha = 0.35f),
                            Offset(cx + halfLen + 6.dp.toPx(), cy),
                            Offset(cx + halfLen + 16.dp.toPx(), cy),
                            strokeWidth = 1.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = levelColor,
                            start = Offset(cx - dx, cy - dy),
                            end = Offset(cx + dx, cy + dy),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                    }

                    if (isPickerActive || isExposureBoxPicking || isColorPipetteActive) {
                        val borderStroke = 2.dp.toPx()
                        drawRect(
                            color = when {
                                isColorPipetteActive -> IrPalette.IrTeal.copy(alpha = 0.80f)
                                isPickerActive -> IrPalette.IrAmber.copy(alpha = 0.80f)
                                else -> IrPalette.IrAmber.copy(alpha = 0.70f)
                            },
                            style = Stroke(width = borderStroke)
                        )
                    }

                    // Ekrana dokununca çıkan turuncu odak/ölçüm kutusu: 0,5 saniye sonra yumuşakça silinir
                    if (meteringMode == ExposureMeteringMode.BOX_SPOT && focusBoxAlpha > 0.01f) {
                        val boxSide = minOf(size.width, size.height) * exposureBoxSizeNorm
                        val half = boxSide * 0.5f
                        val cx = (exposureBoxNorm.x * size.width).coerceIn(half, (size.width - half).coerceAtLeast(half))
                        val cy = (exposureBoxNorm.y * size.height).coerceIn(half, (size.height - half).coerceAtLeast(half))
                        val left = cx - half
                        val top = cy - half
                        val right = cx + half
                        val bottom = cy + half
                        val corner = boxSide * 0.24f
                        val baseColor = if (isExposureBoxPicking) IrPalette.IrCoral else IrPalette.IrAmber
                        val boxColor = baseColor.copy(alpha = focusBoxAlpha)
                        val strokeW = 2.2.dp.toPx()

                        drawRect(
                            color = baseColor.copy(alpha = 0.07f * focusBoxAlpha),
                            topLeft = Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(boxSide, boxSide)
                        )
                        drawLine(boxColor, Offset(left, top), Offset(left + corner, top), strokeW, cap = StrokeCap.Round)
                        drawLine(boxColor, Offset(left, top), Offset(left, top + corner), strokeW, cap = StrokeCap.Round)
                        drawLine(boxColor, Offset(right, top), Offset(right - corner, top), strokeW, cap = StrokeCap.Round)
                        drawLine(boxColor, Offset(right, top), Offset(right, top + corner), strokeW, cap = StrokeCap.Round)
                        drawLine(boxColor, Offset(left, bottom), Offset(left + corner, bottom), strokeW, cap = StrokeCap.Round)
                        drawLine(boxColor, Offset(left, bottom), Offset(left, bottom - corner), strokeW, cap = StrokeCap.Round)
                        drawLine(boxColor, Offset(right, bottom), Offset(right - corner, bottom), strokeW, cap = StrokeCap.Round)
                        drawLine(boxColor, Offset(right, bottom), Offset(right, bottom - corner), strokeW, cap = StrokeCap.Round)
                        drawCircle(
                            color = baseColor.copy(alpha = 0.90f * focusBoxAlpha),
                            radius = 3.5.dp.toPx(),
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.6.dp.toPx())
                        )
                    }

                    pickedMarkerPos?.let { pos ->
                        val outerR = 22.dp.toPx()
                        val innerR = 4.dp.toPx()
                        drawCircle(
                            color = IrPalette.IrAmber,
                            radius = outerR,
                            center = pos,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White,
                            radius = innerR,
                            center = pos
                        )
                    }
                }

                // Önizleme üst HUD satırı: Estetik modda tertemiz vizör; IR Lab ve Pro modda canlı telemetri + RGB histogram
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (experienceMode != ExperienceMode.AESTHETIC) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Sol: Telemetri okuma kapsülü (dokununca ilgili sekmeye gider)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(IrPalette.HudScrim)
                                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusTelemetryChip(
                                text = "${displayKelvin.toInt()}K",
                                accentColor = if (!isAutoMode && activeTab == ControlTab.BD && !isDeckCollapsed) IrPalette.IrAmber else IrPalette.TextPrimary,
                                enabled = cameraReady,
                                onClick = {
                                    activeWbSubSlider = WbSubSlider.KELVIN
                                    openProControl(ControlTab.BD)
                                }
                            )
                            if (!isAutoMode) {
                                Text(
                                    text = "·",
                                    color = IrPalette.TextTertiary,
                                    fontSize = 11.sp
                                )
                                StatusTelemetryChip(
                                    text = "$strTintShort ${formatSigned(displayTint)}",
                                    accentColor = if (activeTab == ControlTab.BD && activeWbSubSlider == WbSubSlider.TINT && !isDeckCollapsed) IrPalette.IrAmber else IrPalette.TextPrimary,
                                    enabled = cameraReady,
                                    onClick = {
                                        activeWbSubSlider = WbSubSlider.TINT
                                        openProControl(ControlTab.BD)
                                    }
                                )
                            }
                            if (aeRange.upper > aeRange.lower) {
                                Text(
                                    text = "·",
                                    color = IrPalette.TextTertiary,
                                    fontSize = 11.sp
                                )
                                val evFormatted = formatSignedDecimal(evDisplayVal, appLocale)
                                StatusTelemetryChip(
                                    text = "$strEvShort $evFormatted",
                                    accentColor = if ((!isAutoMode && activeTab == ControlTab.POZ && !isDeckCollapsed) || isEvModified) IrPalette.IrAmber else IrPalette.TextPrimary,
                                    enabled = cameraReady,
                                    onClick = {
                                        openProControl(ControlTab.POZ)
                                    }
                                )
                            }
                        }

                        // Sağ üst: Profesyonel 5-bölge RGB + Parlaklık canlı histogramı
                        if (cameraReady) {
                            LiveHistogramOverlay(
                                data = liveHistogram,
                                modifier = Modifier.clickable(enabled = cameraReady) {
                                    openProControl(ControlTab.RENK)
                                    colorPanelMode = ColorPanelMode.BANTLAR
                                }
                            )
                        }
                    }
                    }

                    // Aktif seçici (Noktasal BD / Renk Pipeti) yönlendirme şeridi + tek dokunuşla İptal
                    val activeToolPrompt = when {
                        isPickerActive -> strTapWb
                        isColorPipetteActive -> strTapColor
                        else -> null
                    }
                    AnimatedVisibility(
                        visible = activeToolPrompt != null,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(180))
                    ) {
                        activeToolPrompt?.let { prompt ->
                            val toolAccent = if (isColorPipetteActive) IrPalette.IrTeal else IrPalette.IrAmber
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(IrPalette.HudScrim)
                                    .border(1.dp, toolAccent.copy(alpha = 0.85f), RoundedCornerShape(50))
                                    .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(toolAccent)
                                )
                                Text(
                                    text = prompt,
                                    color = IrPalette.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = strCancelPick,
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(toolAccent)
                                        .clickable {
                                            isPickerActive = false
                                            isColorPipetteActive = false
                                        }
                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Geçici işlem bildirimi veya Karşılaştırma rozeti
                    val topBannerMsg = if (isComparingAuto) strCompareBanner else if (activeToolPrompt == null) statusBannerText else null
                    AnimatedVisibility(
                        visible = topBannerMsg != null,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(220))
                    ) {
                        topBannerMsg?.let { msg ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (isComparingAuto) IrPalette.IrEmerald.copy(alpha = 0.94f)
                                        else IrPalette.HudScrim
                                    )
                                    .border(
                                        1.dp,
                                        if (isComparingAuto) IrPalette.IrEmerald else IrPalette.BorderBright,
                                        RoundedCornerShape(50)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = msg,
                                    color = if (isComparingAuto) Color.Black else IrPalette.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Zamanlayıcı geri sayım göstergesi
                if (timerCountdown > 0) {
                    Box(
                        modifier = Modifier
                            .size(94.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.72f))
                            .border(2.dp, IrPalette.IrAmber, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$timerCountdown",
                            color = Color.White,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Deklanşör çekim flaş efekti
                if (shutterFlashAlpha.value > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = shutterFlashAlpha.value))
                    )
                }

                // Kamera izni reddedildiğinde siyah ekran yerine açıklayıcı kart göster
                if (!hasCameraPermission && !showExperienceWelcome) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(IrPalette.VoidBlack.copy(alpha = 0.88f))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 360.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(IrPalette.HudCard)
                                .border(1.dp, IrPalette.BorderBright, RoundedCornerShape(18.dp))
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.permission_camera_title),
                                color = IrPalette.TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.permission_camera_body),
                                color = IrPalette.TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = onRequestCameraPermission,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = IrPalette.IrAmber,
                                    contentColor = Color.Black
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.permission_camera_grant),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            OutlinedButton(
                                onClick = onOpenAppSettings,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, IrPalette.BorderBright),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = IrPalette.TextPrimary)
                            ) {
                                Text(
                                    text = stringResource(R.string.permission_open_settings),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                            TextButton(onClick = { isStudioMode = true }) {
                                Text(
                                    text = stringResource(R.string.permission_use_studio),
                                    color = IrPalette.IrCyan,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // YÜZEN ALT KATMAN: Tam genişlikte HUD satırı + Yarı saydam Pro kontrol paneli
            // Vizör asla küçülmez; pipet/BD seçicisinde panel otomatik gizlenir; slider sürüklerken Hayalet Mod açılır
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Alt HUD satırı: [Karşılaştır] · [0,5x | 1x | 2x] · [ZEBRA/KUTU] (tam ekran genişliğinde, asla kırpılmaz)
                if (cameraReady) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(ghostChromeAlpha)
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sol slot: PRO modda basılı tutunca OTO önizlemesi gösteren Karşılaştır düğmesi
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            val canCompare = !isAutoMode || activeLookGrade.isActive || colorBandsConfig.hasAnyModification || channelSwapEnabled
                            if (canCompare) {
                                Box(
                                    modifier = Modifier
                                        .sizeIn(minWidth = 40.dp, minHeight = 36.dp)
                                        .pointerInput(cameraReady, isAutoMode) {
                                            detectTapGestures(
                                                onPress = {
                                                    isComparingAuto = true
                                                    if (!isAutoMode) updatePreviewSettings()
                                                    tryAwaitRelease()
                                                    isComparingAuto = false
                                                    if (!isAutoMode) updatePreviewSettings()
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                if (isComparingAuto) IrPalette.IrEmerald
                                                else IrPalette.HudScrim
                                            )
                                            .border(
                                                0.8.dp,
                                                if (isComparingAuto) IrPalette.IrEmerald else Color.White.copy(alpha = 0.20f),
                                                RoundedCornerShape(50)
                                            )
                                            .padding(horizontal = 9.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        CompareEyeIcon(
                                            tint = if (isComparingAuto) Color.Black else IrPalette.TextPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = strCompare,
                                            color = if (isComparingAuto) Color.Black else IrPalette.TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        // Orta slot: Kompakt mercek / yakınlaştırma kapsülü (0,5x · 1x · 2x)
                        if (availableZoomOptions.size > 1) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(IrPalette.HudScrim)
                                    .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                availableZoomOptions.forEach { z ->
                                    val isSelected = abs(zoomRatio - z) < 0.08f
                                    val zLabel = if (z < 0.99f) String.format(appLocale, "%.1fx", z) else "${z.toInt()}x"
                                    val chipBg by animateColorAsState(
                                        targetValue = if (isSelected) IrPalette.IrAmber else Color.Transparent,
                                        animationSpec = tween(160),
                                        label = "zoomChipBg"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .sizeIn(minWidth = 32.dp, minHeight = 28.dp)
                                            .clip(CircleShape)
                                            .background(chipBg)
                                            .clickable {
                                                zoomRatio = z
                                                updatePreviewSettings()
                                            }
                                            .padding(horizontal = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = zLabel,
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Sağ slot: Yalnızca aktif özel teknik rozetler (R↔B / AE-L / ZEBRA — KUTU rozeti kaldırıldı!)
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            val badgeInfo: Pair<String, Color>? = when {
                                experienceMode == ExperienceMode.AESTHETIC -> null
                                !isAutoMode && channelSwapEnabled -> strBadgeSwap to IrPalette.IrCyan
                                aeLockEnabled -> strBadgeAeLock to IrPalette.IrAmber
                                showZebra -> strBadgeZebra to IrPalette.IrCoral
                                else -> null
                            }
                            if (badgeInfo != null) {
                                val (tag, color) = badgeInfo
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(IrPalette.HudScrim)
                                        .border(0.8.dp, color.copy(alpha = 0.65f), RoundedCornerShape(50))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        color = color,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // A) ESTETİK & FİLTRE MODU PANELİ: Sade canlı filtre şeridi + Filtre Yoğunluğu / Parlaklık
                AnimatedVisibility(
                    visible = experienceMode == ExperienceMode.AESTHETIC && !isPickerActive && !isColorPipetteActive,
                    enter = fadeIn(tween(170)) + slideInVertically(tween(190)) { it / 3 },
                    exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { it / 3 }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(IrPalette.HudGlass.copy(alpha = deckBgAlpha))
                            .border(
                                0.8.dp,
                                IrPalette.BorderSubtle.copy(alpha = if (isAnySliderDragging) 0.08f else 0.24f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val hasActiveFilter = selectedAestheticPreset.name != "Natural"
                        AnimatedVisibility(visible = !isDeckCollapsed && (hasActiveFilter || aeRange.upper > aeRange.lower)) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                if (hasActiveFilter && aeRange.upper > aeRange.lower) {
                                    SegmentedChoiceRow(
                                        options = listOf(
                                            AestheticSubSlider.STRENGTH to "${stringResource(R.string.slider_filter_strength)} · ${(aestheticFilterStrength * 100f).roundToInt()}%",
                                            AestheticSubSlider.BRIGHTNESS to "${stringResource(R.string.slider_soft_light)} · ${formatSignedDecimal(evDisplayVal, appLocale)}"
                                        ),
                                        selected = aestheticSubSlider,
                                        enabled = cameraReady,
                                        modifier = Modifier.alpha(ghostChromeAlpha),
                                        onSelect = { aestheticSubSlider = it }
                                    )
                                }
                                if (hasActiveFilter && (aestheticSubSlider == AestheticSubSlider.STRENGTH || aeRange.upper <= aeRange.lower)) {
                                    ProGradientSliderRow(
                                        label = "${selectedAestheticPreset.name} · ${stringResource(R.string.slider_filter_strength)}",
                                        valueBadge = "${(aestheticFilterStrength * 100f).roundToInt()}%",
                                        isModified = aestheticFilterStrength < 0.98f,
                                        value = aestheticFilterStrength,
                                        defaultValue = 1.0f,
                                        snapTolerance = 0.04f,
                                        valueRange = 0f..1f,
                                        enabled = cameraReady,
                                        gradient = Brush.horizontalGradient(
                                            listOf(Color(0xFF475569), Color(selectedAestheticPreset.accentHex))
                                        ),
                                        onDraggingChange = { isAnySliderDragging = it },
                                        onStepMinus = { applyAestheticPreset(selectedAestheticPreset, (aestheticFilterStrength - 0.1f).coerceIn(0f, 1f)) },
                                        onStepPlus = { applyAestheticPreset(selectedAestheticPreset, (aestheticFilterStrength + 0.1f).coerceIn(0f, 1f)) },
                                        onReset = { applyAestheticPreset(selectedAestheticPreset, 1.0f) },
                                        onValueChange = { applyAestheticPreset(selectedAestheticPreset, it) }
                                    )
                                } else if (aeRange.upper > aeRange.lower) {
                                    ProGradientSliderRow(
                                        label = stringResource(R.string.slider_soft_light),
                                        valueBadge = formatSignedDecimal(evDisplayVal, appLocale),
                                        isModified = exposureEvIndex != 0,
                                        value = exposureEvIndex.toFloat(),
                                        defaultValue = 0f,
                                        snapTolerance = 0.45f,
                                        valueRange = aeRange.lower.toFloat()..aeRange.upper.toFloat(),
                                        enabled = cameraReady,
                                        gradient = Brush.horizontalGradient(
                                            listOf(Color(0xFF1E293B), Color(0xFFCBD5E1), Color(0xFFFEF08A))
                                        ),
                                        onDraggingChange = { isAnySliderDragging = it },
                                        onStepMinus = {
                                            exposureEvIndex = (exposureEvIndex - 1).coerceIn(aeRange.lower, aeRange.upper)
                                            updatePreviewSettings()
                                        },
                                        onStepPlus = {
                                            exposureEvIndex = (exposureEvIndex + 1).coerceIn(aeRange.lower, aeRange.upper)
                                            updatePreviewSettings()
                                        },
                                        onReset = {
                                            exposureEvIndex = 0
                                            updatePreviewSettings()
                                        },
                                        onValueChange = {
                                            exposureEvIndex = it.roundToInt().coerceIn(aeRange.lower, aeRange.upper)
                                            updatePreviewSettings()
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(ghostChromeAlpha),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(AESTHETIC_PRESETS, key = { "aes_${it.name}" }) { preset ->
                                    val isSel = selectedAestheticPreset.name == preset.name
                                    val accent = Color(preset.accentHex)
                                    Row(
                                        modifier = Modifier
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(if (isSel) accent.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.06f))
                                            .border(
                                                0.9.dp,
                                                if (isSel) accent else IrPalette.BorderSubtle,
                                                RoundedCornerShape(50)
                                            )
                                            .clickable(enabled = cameraReady) {
                                                val nextStr = if (isSel) aestheticFilterStrength else 1.0f
                                                aestheticSubSlider = AestheticSubSlider.STRENGTH
                                                applyAestheticPreset(preset, nextStr)
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(accent)
                                                .border(0.8.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                                        )
                                        Text(
                                            text = preset.name,
                                            color = if (isSel) accent else IrPalette.TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            val aesChevRot by animateFloatAsState(
                                targetValue = if (isDeckCollapsed) 180f else 0f,
                                animationSpec = tween(200),
                                label = "aesChevRot"
                            )
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (isDeckCollapsed) IrPalette.IrMagenta.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f))
                                    .border(0.8.dp, if (isDeckCollapsed) IrPalette.IrMagenta else IrPalette.BorderSubtle, RoundedCornerShape(9.dp))
                                    .clickable { isDeckCollapsed = !isDeckCollapsed },
                                contentAlignment = Alignment.Center
                            ) {
                                ChevronIcon(
                                    tint = if (isDeckCollapsed) IrPalette.IrMagenta else IrPalette.TextPrimary,
                                    modifier = Modifier.size(13.dp).rotate(aesChevRot)
                                )
                            }
                        }
                    }
                }

                // B) IR LAB & PRO MOD Yarı Saydam Yüzen Kontrol Paneli (White Balance · Exposure · Color · Metering)
                AnimatedVisibility(
                    visible = (experienceMode == ExperienceMode.PRO || experienceMode == ExperienceMode.IR_LAB) &&
                        !isAutoMode && !isPickerActive && !isColorPipetteActive,
                    enter = fadeIn(tween(170)) + slideInVertically(tween(190)) { it / 3 },
                    exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { it / 3 }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(IrPalette.HudGlass.copy(alpha = deckBgAlpha))
                            .border(
                                0.8.dp,
                                IrPalette.BorderSubtle.copy(alpha = if (isAnySliderDragging) 0.08f else 0.24f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Seçili çipin tek/kompakt kaydırıcı alanı
                        AnimatedVisibility(
                            visible = !isDeckCollapsed,
                            enter = fadeIn(tween(150)) + slideInVertically(tween(170)) { it / 4 },
                            exit = fadeOut(tween(130)) + slideOutVertically(tween(150)) { it / 4 }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                            when (activeTab) {
                                ControlTab.BD -> {
                                    // Tek kaydırıcı + canlı değer gösteren 2'li alt seçici
                                    SegmentedChoiceRow(
                                        options = listOf(
                                            WbSubSlider.KELVIN to "${stringResource(R.string.sub_kelvin)} · ${kelvinValue.toInt()}K",
                                            WbSubSlider.TINT to "${stringResource(R.string.sub_tint_wb)} · ${formatSigned(tintValue)}"
                                        ),
                                        selected = activeWbSubSlider,
                                        enabled = cameraReady,
                                        modifier = Modifier.alpha(ghostChromeAlpha),
                                        onSelect = { activeWbSubSlider = it }
                                    )

                                    if (activeWbSubSlider == WbSubSlider.KELVIN) {
                                        ProGradientSliderRow(
                                            label = stringResource(R.string.slider_kelvin),
                                            valueBadge = "${kelvinValue.toInt()} K",
                                            isModified = abs(kelvinValue - liveAutoKelvin) > 120f,
                                            value = kelvinValue,
                                            defaultValue = 5500f,
                                            snapTolerance = 140f,
                                            valueRange = KELVIN_MIN..KELVIN_MAX,
                                            enabled = cameraReady,
                                            gradient = Brush.horizontalGradient(
                                                colors = listOf(Color(0xFFFF6B2B), Color(0xFFFFF4E6), IrPalette.IrCyan)
                                            ),
                                            onDraggingChange = { isAnySliderDragging = it },
                                            onStepMinus = {
                                                kelvinValue = (kelvinValue - 100f).coerceIn(KELVIN_MIN, KELVIN_MAX)
                                                updatePreviewSettings()
                                            },
                                            onStepPlus = {
                                                kelvinValue = (kelvinValue + 100f).coerceIn(KELVIN_MIN, KELVIN_MAX)
                                                updatePreviewSettings()
                                            },
                                            onReset = {
                                                kelvinValue = liveAutoKelvin.coerceIn(KELVIN_MIN, KELVIN_MAX)
                                                updatePreviewSettings()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onValueChange = {
                                                kelvinValue = it
                                                updatePreviewSettings()
                                            }
                                        )
                                    } else {
                                        ProGradientSliderRow(
                                            label = stringResource(R.string.slider_tint),
                                            valueBadge = formatSigned(tintValue),
                                            isModified = abs(tintValue - liveAutoTint) > 3f,
                                            value = tintValue,
                                            defaultValue = 0f,
                                            snapTolerance = 4f,
                                            valueRange = -100f..100f,
                                            enabled = cameraReady,
                                            gradient = Brush.horizontalGradient(
                                                colors = listOf(IrPalette.IrEmerald, Color(0xFFE2E8F0), IrPalette.IrMagenta)
                                            ),
                                            onDraggingChange = { isAnySliderDragging = it },
                                            onStepMinus = {
                                                tintValue = (tintValue - 5f).coerceIn(-100f, 100f)
                                                updatePreviewSettings()
                                            },
                                            onStepPlus = {
                                                tintValue = (tintValue + 5f).coerceIn(-100f, 100f)
                                                updatePreviewSettings()
                                            },
                                            onReset = {
                                                tintValue = liveAutoTint.coerceIn(-100f, 100f)
                                                updatePreviewSettings()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onValueChange = {
                                                tintValue = it
                                                updatePreviewSettings()
                                            }
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .alpha(ghostChromeAlpha),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ActionPillButton(
                                            label = stringResource(R.string.action_auto_calibrate),
                                            selected = false,
                                            accentColor = IrPalette.IrAmber,
                                            enabled = cameraReady,
                                            modifier = Modifier.weight(1f),
                                            leadingIcon = { tint -> SparkleAutoIcon(tint = tint, modifier = Modifier.size(13.dp)) },
                                            onClick = { runAutoCalibrate() }
                                        )
                                        ActionPillButton(
                                            label = if (isPickerActive) stringResource(R.string.action_spot_wb_active) else stringResource(R.string.action_spot_wb),
                                            selected = isPickerActive,
                                            accentColor = IrPalette.IrAmber,
                                            enabled = cameraReady,
                                            modifier = Modifier.weight(1f),
                                            leadingIcon = { tint -> TargetCrosshairIcon(tint = tint, modifier = Modifier.size(13.dp)) },
                                            onClick = {
                                                isPickerActive = !isPickerActive
                                                if (isPickerActive) {
                                                    isColorPipetteActive = false
                                                    showStatus(strTapWb)
                                                }
                                            }
                                        )
                                    }
                                }

                                ControlTab.POZ -> {
                                    if (aeRange.upper > aeRange.lower) {
                                        val minEv = aeRange.lower.toFloat()
                                        val maxEv = aeRange.upper.toFloat()
                                        val evBadge = "${formatSignedDecimal(evDisplayVal, appLocale)} $strEvShort"
                                        ProGradientSliderRow(
                                            label = stringResource(R.string.slider_exposure),
                                            valueBadge = evBadge,
                                            isModified = isEvModified,
                                            value = exposureEvIndex.toFloat(),
                                            defaultValue = 0f,
                                            snapTolerance = 0.45f,
                                            valueRange = minEv..maxEv,
                                            enabled = cameraReady,
                                            gradient = Brush.horizontalGradient(
                                                colors = listOf(Color(0xFF1E293B), Color(0xFF94A3B8), Color(0xFFFEF08A))
                                            ),
                                            onDraggingChange = { isAnySliderDragging = it },
                                            onStepMinus = {
                                                exposureEvIndex = (exposureEvIndex - 1).coerceIn(aeRange.lower, aeRange.upper)
                                                updatePreviewSettings()
                                            },
                                            onStepPlus = {
                                                exposureEvIndex = (exposureEvIndex + 1).coerceIn(aeRange.lower, aeRange.upper)
                                                updatePreviewSettings()
                                            },
                                            onReset = {
                                                exposureEvIndex = 0
                                                updatePreviewSettings()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onValueChange = {
                                                exposureEvIndex = it.roundToInt().coerceIn(aeRange.lower, aeRange.upper)
                                                updatePreviewSettings()
                                            }
                                        )
                                    }

                                    // Dengeli 3'lü pozlama araç satırı: Vurgu koruma + AE Kilit + Zebra uyarısı
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .alpha(ghostChromeAlpha),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ActionPillButton(
                                            label = stringResource(R.string.action_highlight_guard),
                                            selected = highlightProtectionEnabled,
                                            accentColor = IrPalette.IrTeal,
                                            enabled = cameraReady,
                                            modifier = Modifier.weight(1.1f),
                                            leadingIcon = { tint -> ShieldIcon(tint = tint, modifier = Modifier.size(12.dp)) },
                                            onClick = {
                                                highlightProtectionEnabled = !highlightProtectionEnabled
                                                measureAndApplyExposureMetering(applyEvAdjust = highlightProtectionEnabled)
                                                showStatus(if (highlightProtectionEnabled) strHighlightOn else strHighlightOff)
                                            }
                                        )
                                        ActionPillButton(
                                            label = stringResource(R.string.action_ae_lock),
                                            selected = aeLockEnabled,
                                            accentColor = IrPalette.IrAmber,
                                            enabled = cameraReady,
                                            modifier = Modifier.weight(0.95f),
                                            leadingIcon = { tint -> LockIcon(locked = aeLockEnabled, tint = tint, modifier = Modifier.size(12.dp)) },
                                            onClick = {
                                                aeLockEnabled = !aeLockEnabled
                                                updatePreviewSettings()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                showStatus(
                                                    context.getString(
                                                        if (aeLockEnabled) R.string.status_ae_lock_on
                                                        else R.string.status_ae_lock_off
                                                    )
                                                )
                                            }
                                        )
                                        ActionPillButton(
                                            label = stringResource(R.string.action_zebra),
                                            selected = showZebra,
                                            accentColor = IrPalette.IrCoral,
                                            enabled = cameraReady,
                                            modifier = Modifier.weight(0.85f),
                                            leadingIcon = { tint -> ZebraIcon(tint = tint, modifier = Modifier.size(12.dp)) },
                                            onClick = {
                                                showZebra = !showZebra
                                                showStatus(if (showZebra) strZebraOn else strZebraOff)
                                            }
                                        )
                                    }
                                }

                                ControlTab.RENK -> {
                                    // 3'lü profesyonel renk stüdyosu seçicisi: [Genel | Bantlar (Histogram) | Pipet]
                                    val bandsLabel = if (colorBandsConfig.hasAnyModification) {
                                        "${stringResource(R.string.color_mode_bands)} •"
                                    } else {
                                        stringResource(R.string.color_mode_bands)
                                    }
                                    val pipetteLabel = if (targetedColorConfig.isActive) {
                                        "${stringResource(R.string.color_mode_pipette)} •"
                                    } else {
                                        stringResource(R.string.color_mode_pipette)
                                    }
                                    SegmentedChoiceRow(
                                        options = listOf(
                                            ColorPanelMode.GENEL to stringResource(R.string.color_mode_general),
                                            ColorPanelMode.BANTLAR to bandsLabel,
                                            ColorPanelMode.PIPET to pipetteLabel
                                        ),
                                        selected = colorPanelMode,
                                        enabled = cameraReady,
                                        modifier = Modifier.alpha(ghostChromeAlpha),
                                        onSelect = { mode ->
                                            colorPanelMode = mode
                                            if (mode != ColorPanelMode.PIPET) {
                                                isColorPipetteActive = false
                                            }
                                        }
                                    )

                                    when (colorPanelMode) {
                                        ColorPanelMode.GENEL -> {
                                            val satStr = String.format(appLocale, "%.2f", saturationValue)
                                            val conStr = String.format(appLocale, "%.2f", contrastValue)
                                            SegmentedChoiceRow(
                                                options = listOf(
                                                    ColorSubSlider.DOYGUNLUK to "${stringResource(R.string.slider_saturation)} · ${satStr}x",
                                                    ColorSubSlider.KONTRAST to "${stringResource(R.string.slider_contrast)} · ${conStr}x"
                                                ),
                                                selected = activeColorSubSlider,
                                                enabled = cameraReady,
                                                modifier = Modifier.alpha(ghostChromeAlpha),
                                                onSelect = { activeColorSubSlider = it }
                                            )

                                            if (activeColorSubSlider == ColorSubSlider.DOYGUNLUK) {
                                                ProGradientSliderRow(
                                                    label = stringResource(R.string.slider_saturation),
                                                    valueBadge = "${satStr}x",
                                                    isModified = abs(saturationValue - DEFAULT_SATURATION) > 0.02f,
                                                    value = saturationValue,
                                                    defaultValue = DEFAULT_SATURATION,
                                                    snapTolerance = 0.04f,
                                                    valueRange = 0.50f..2.00f,
                                                    enabled = cameraReady,
                                                    gradient = Brush.horizontalGradient(
                                                        colors = listOf(
                                                            Color(0xFF64748B),
                                                            Color(0xFF94A3B8),
                                                            Color(0xFF22C55E),
                                                            Color(0xFFF59E0B),
                                                            Color(0xFFEF4444)
                                                        )
                                                    ),
                                                    onDraggingChange = { isAnySliderDragging = it },
                                                    onStepMinus = {
                                                        saturationValue = (saturationValue - 0.05f).coerceIn(0.50f, 2.00f)
                                                        updatePreviewSettings()
                                                    },
                                                    onStepPlus = {
                                                        saturationValue = (saturationValue + 0.05f).coerceIn(0.50f, 2.00f)
                                                        updatePreviewSettings()
                                                    },
                                                    onReset = {
                                                        saturationValue = DEFAULT_SATURATION
                                                        updatePreviewSettings()
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    },
                                                    onValueChange = {
                                                        saturationValue = it
                                                        updatePreviewSettings()
                                                    }
                                                )
                                            } else {
                                                ProGradientSliderRow(
                                                    label = stringResource(R.string.slider_contrast),
                                                    valueBadge = "${conStr}x",
                                                    isModified = abs(contrastValue - DEFAULT_CONTRAST) > 0.02f,
                                                    value = contrastValue,
                                                    defaultValue = DEFAULT_CONTRAST,
                                                    snapTolerance = 0.04f,
                                                    valueRange = 0.60f..1.80f,
                                                    enabled = cameraReady,
                                                    gradient = Brush.horizontalGradient(
                                                        colors = listOf(Color(0xFF475569), Color(0xFFCBD5E1), Color.White)
                                                    ),
                                                    onDraggingChange = { isAnySliderDragging = it },
                                                    onStepMinus = {
                                                        contrastValue = (contrastValue - 0.05f).coerceIn(0.60f, 1.80f)
                                                        updatePreviewSettings()
                                                    },
                                                    onStepPlus = {
                                                        contrastValue = (contrastValue + 0.05f).coerceIn(0.60f, 1.80f)
                                                        updatePreviewSettings()
                                                    },
                                                    onReset = {
                                                        contrastValue = DEFAULT_CONTRAST
                                                        updatePreviewSettings()
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    },
                                                    onValueChange = {
                                                        contrastValue = it
                                                        updatePreviewSettings()
                                                    }
                                                )
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .alpha(ghostChromeAlpha),
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                ActionPillButton(
                                                    label = stringResource(R.string.action_auto_contrast),
                                                    selected = false,
                                                    accentColor = IrPalette.IrAmber,
                                                    enabled = cameraReady,
                                                    modifier = Modifier.weight(1f),
                                                    leadingIcon = { tint -> SparkleAutoIcon(tint = tint, modifier = Modifier.size(12.dp)) },
                                                    onClick = { runAutoContrast() }
                                                )
                                                ActionPillButton(
                                                    label = stringResource(R.string.action_channel_swap),
                                                    selected = channelSwapEnabled,
                                                    accentColor = IrPalette.IrCyan,
                                                    enabled = cameraReady,
                                                    modifier = Modifier.weight(1f),
                                                    leadingIcon = { tint -> SwapChannelIcon(tint = tint, modifier = Modifier.size(12.dp)) },
                                                    onClick = {
                                                        channelSwapEnabled = !channelSwapEnabled
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        showStatus(
                                                            context.getString(
                                                                if (channelSwapEnabled) R.string.status_channel_swap_on
                                                                else R.string.status_channel_swap_off
                                                            )
                                                        )
                                                    }
                                                )
                                                ActionPillButton(
                                                    label = stringResource(R.string.action_color_pipette),
                                                    selected = isColorPipetteActive,
                                                    accentColor = IrPalette.IrTeal,
                                                    enabled = cameraReady,
                                                    modifier = Modifier.weight(0.85f),
                                                    leadingIcon = { tint -> PipetteIcon(tint = tint, modifier = Modifier.size(12.dp)) },
                                                    onClick = {
                                                        colorPanelMode = ColorPanelMode.PIPET
                                                        isColorPipetteActive = true
                                                        isPickerActive = false
                                                        isExposureBoxPicking = false
                                                        showStatus(strTapColor)
                                                    }
                                                )
                                            }
                                        }

                                        ColorPanelMode.BANTLAR -> {
                                            val activeBands = selectedColorBands.ifEmpty { setOf(ColorBand.RED) }
                                            val orderedSelected = ColorBand.entries.filter { it in activeBands }
                                            val primaryBand = orderedSelected.first()
                                            val isMultiSelection = orderedSelected.size > 1
                                            val avgHue = orderedSelected.sumOf { colorBandsConfig.get(it).deltaHue.toDouble() }.toFloat() / orderedSelected.size
                                            val avgSat = orderedSelected.sumOf { colorBandsConfig.get(it).deltaSat.toDouble() }.toFloat() / orderedSelected.size
                                            val avgLum = orderedSelected.sumOf { colorBandsConfig.get(it).deltaLum.toDouble() }.toFloat() / orderedSelected.size
                                            val bandLabel = if (isMultiSelection) {
                                                stringResource(R.string.bands_selected_count, orderedSelected.size)
                                            } else {
                                                stringResource(primaryBand.labelRes)
                                            }
                                            val bandShiftedHue = (((primaryBand.centerHue + avgHue) % 360f + 360f) % 360f).coerceIn(0f, 359.99f)
                                            val bandHasHueShift = !isMultiSelection && abs(avgHue) > 1f
                                            val selectedModified = orderedSelected.any { colorBandsConfig.get(it).isModified }
                                            val satGradColors = if (isMultiSelection) {
                                                listOf(Color(0xFF475569)) + orderedSelected.map { it.swatchColor }
                                            } else {
                                                listOf(Color(0xFF475569), primaryBand.swatchColor)
                                            }
                                            val lumGradColors = if (isMultiSelection) {
                                                listOf(Color(0xFF1E293B)) + orderedSelected.map { it.swatchColor } + listOf(Color.White)
                                            } else {
                                                listOf(Color(0xFF1E293B), primaryBand.swatchColor, Color.White)
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .alpha(ghostChromeAlpha),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.weight(1f, fill = false)
                                                ) {
                                                    if (isMultiSelection) {
                                                        orderedSelected.take(4).forEach { selBand ->
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(11.dp)
                                                                    .clip(CircleShape)
                                                                    .background(selBand.swatchColor)
                                                                    .border(0.9.dp, Color.White.copy(alpha = 0.78f), CircleShape)
                                                            )
                                                        }
                                                        if (orderedSelected.size > 4) {
                                                            Text(
                                                                text = "+${orderedSelected.size - 4}",
                                                                color = IrPalette.TextSecondary,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(13.dp)
                                                                .clip(CircleShape)
                                                                .background(primaryBand.swatchColor)
                                                                .border(1.dp, Color.White.copy(alpha = 0.75f), CircleShape)
                                                        )
                                                        if (bandHasHueShift) {
                                                            val shiftedBandColor = Color.hsv(
                                                                hue = bandShiftedHue,
                                                                saturation = (0.85f * (1f + avgSat * 0.5f)).coerceIn(0.25f, 1f),
                                                                value = (0.94f + avgLum * 0.35f).coerceIn(0.30f, 1f)
                                                            )
                                                            Text(
                                                                text = "→",
                                                                color = IrPalette.TextSecondary,
                                                                fontSize = 10.sp
                                                            )
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(13.dp)
                                                                    .clip(CircleShape)
                                                                    .background(shiftedBandColor)
                                                                    .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = if (bandHasHueShift) {
                                                            "$bandLabel → ${getHueName(context, bandShiftedHue)}"
                                                        } else {
                                                            bandLabel
                                                        },
                                                        color = IrPalette.TextPrimary,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.action_multi_select),
                                                        color = if (isMultiBandMode) IrPalette.IrAmber else IrPalette.TextSecondary,
                                                        fontSize = 10.sp,
                                                        fontWeight = if (isMultiBandMode) FontWeight.Bold else FontWeight.SemiBold,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(
                                                                if (isMultiBandMode) IrPalette.IrAmber.copy(alpha = 0.20f)
                                                                else Color.White.copy(alpha = 0.07f)
                                                            )
                                                            .border(
                                                                0.8.dp,
                                                                if (isMultiBandMode) IrPalette.IrAmber.copy(alpha = 0.78f)
                                                                else IrPalette.BorderSubtle,
                                                                RoundedCornerShape(6.dp)
                                                            )
                                                            .clickable(enabled = cameraReady) {
                                                                isMultiBandMode = !isMultiBandMode
                                                                if (!isMultiBandMode) {
                                                                    selectedColorBands = setOf(primaryBand)
                                                                }
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            }
                                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                                    )
                                                    if (isMultiBandMode) {
                                                        val allSelected = activeBands.size == ColorBand.entries.size
                                                        Text(
                                                            text = stringResource(R.string.action_select_all_bands),
                                                            color = if (allSelected) IrPalette.IrCyan else IrPalette.TextSecondary,
                                                            fontSize = 10.sp,
                                                            fontWeight = if (allSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(
                                                                    if (allSelected) IrPalette.IrCyan.copy(alpha = 0.18f)
                                                                    else Color.White.copy(alpha = 0.07f)
                                                                )
                                                                .border(
                                                                    0.8.dp,
                                                                    if (allSelected) IrPalette.IrCyan.copy(alpha = 0.75f)
                                                                    else IrPalette.BorderSubtle,
                                                                    RoundedCornerShape(6.dp)
                                                                )
                                                                .clickable(enabled = cameraReady) {
                                                                    selectedColorBands = if (allSelected) {
                                                                        setOf(primaryBand)
                                                                    } else {
                                                                        ColorBand.entries.toSet()
                                                                    }
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                }
                                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                    if (selectedModified) {
                                                        Text(
                                                            text = stringResource(R.string.action_reset_band),
                                                            color = IrPalette.IrAmber,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(IrPalette.IrAmber.copy(alpha = 0.14f))
                                                                .clickable(enabled = cameraReady) {
                                                                    colorBandsConfig = colorBandsConfig.updateAll(activeBands) { BandHslAdjustment() }
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                }
                                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                                        )
                                                    } else if (colorBandsConfig.hasAnyModification) {
                                                        Text(
                                                            text = stringResource(R.string.action_reset_all_bands),
                                                            color = IrPalette.IrCoral,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(IrPalette.IrCoral.copy(alpha = 0.14f))
                                                                .clickable(enabled = cameraReady) {
                                                                    colorBandsConfig = ColorBandsConfig()
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                }
                                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            ColorHistogramBandPicker(
                                                hueBins = liveHistogram.hueBins,
                                                selectedBands = activeBands,
                                                multiSelectMode = isMultiBandMode,
                                                config = colorBandsConfig,
                                                enabled = cameraReady,
                                                modifier = Modifier.alpha(ghostChromeAlpha),
                                                onSelectBand = { band ->
                                                    if (isMultiBandMode) {
                                                        selectedColorBands = if (band in activeBands && activeBands.size > 1) {
                                                            activeBands - band
                                                        } else {
                                                            activeBands + band
                                                        }
                                                    } else {
                                                        selectedColorBands = setOf(band)
                                                    }
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                },
                                                onSelectBandRange = { range ->
                                                    selectedColorBands = range.ifEmpty { setOf(ColorBand.RED) }
                                                },
                                                onLongPressBand = { band ->
                                                    isMultiBandMode = true
                                                    selectedColorBands = activeBands + band
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            )

                                            SegmentedChoiceRow(
                                                options = listOf(
                                                    PointColorSubSlider.TON to "${stringResource(R.string.sub_tint)} · ${formatSigned(avgHue)}°",
                                                    PointColorSubSlider.DOYGUNLUK to "${stringResource(R.string.sub_sat_short)} · ${formatSigned(avgSat * 100f)}%",
                                                    PointColorSubSlider.PARLAKLIK to "${stringResource(R.string.sub_lum_short)} · ${formatSigned(avgLum * 100f)}%"
                                                ),
                                                selected = activeBandSubSlider,
                                                enabled = cameraReady,
                                                modifier = Modifier.alpha(ghostChromeAlpha),
                                                onSelect = { activeBandSubSlider = it }
                                            )

                                            when (activeBandSubSlider) {
                                                PointColorSubSlider.TON -> {
                                                    val bandSpectrum = remember(primaryBand) {
                                                        val base = primaryBand.centerHue
                                                        List(9) { idx ->
                                                            val offset = -180f + idx * 45f
                                                            val h = (((base + offset) % 360f + 360f) % 360f).coerceIn(0f, 359.99f)
                                                            Color.hsv(h, 0.88f, 0.96f)
                                                        }
                                                    }
                                                    ProGradientSliderRow(
                                                        label = "$bandLabel · ${stringResource(R.string.sub_tint)}",
                                                        valueBadge = "${formatSigned(avgHue)}°",
                                                        isModified = orderedSelected.any { abs(colorBandsConfig.get(it).deltaHue) > 0.5f },
                                                        value = avgHue,
                                                        defaultValue = 0f,
                                                        snapTolerance = 5f,
                                                        valueRange = -180f..180f,
                                                        enabled = cameraReady,
                                                        gradient = Brush.horizontalGradient(bandSpectrum),
                                                        onDraggingChange = { isAnySliderDragging = it },
                                                        onStepMinus = {
                                                            colorBandsConfig = colorBandsConfig.updateAll(activeBands) {
                                                                it.copy(deltaHue = (it.deltaHue - 10f).coerceIn(-180f, 180f))
                                                            }
                                                        },
                                                        onStepPlus = {
                                                            colorBandsConfig = colorBandsConfig.updateAll(activeBands) {
                                                                it.copy(deltaHue = (it.deltaHue + 10f).coerceIn(-180f, 180f))
                                                            }
                                                        },
                                                        onReset = {
                                                            colorBandsConfig = colorBandsConfig.updateAll(activeBands) { it.copy(deltaHue = 0f) }
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        },
                                                        onValueChange = { newVal ->
                                                            colorBandsConfig = if (activeBands.size == 1) {
                                                                colorBandsConfig.update(primaryBand) { it.copy(deltaHue = newVal) }
                                                            } else {
                                                                val liveAvg = activeBands.sumOf { colorBandsConfig.get(it).deltaHue.toDouble() }.toFloat() / activeBands.size
                                                                val delta = newVal - liveAvg
                                                                colorBandsConfig.updateAll(activeBands) {
                                                                    it.copy(deltaHue = (it.deltaHue + delta).coerceIn(-180f, 180f))
                                                                }
                                                            }
                                                        }
                                                    )
                                                }

                                                PointColorSubSlider.DOYGUNLUK -> ProGradientSliderRow(
                                                    label = "$bandLabel · ${stringResource(R.string.slider_saturation)}",
                                                    valueBadge = "${formatSigned(avgSat * 100f)}%",
                                                    isModified = orderedSelected.any { abs(colorBandsConfig.get(it).deltaSat) > 0.02f },
                                                    value = avgSat,
                                                    defaultValue = 0f,
                                                    snapTolerance = 0.05f,
                                                    valueRange = -1.0f..1.0f,
                                                    enabled = cameraReady,
                                                    gradient = Brush.horizontalGradient(satGradColors),
                                                    onDraggingChange = { isAnySliderDragging = it },
                                                    onStepMinus = {
                                                        colorBandsConfig = colorBandsConfig.updateAll(activeBands) {
                                                            it.copy(deltaSat = (it.deltaSat - 0.05f).coerceIn(-1.0f, 1.0f))
                                                        }
                                                    },
                                                    onStepPlus = {
                                                        colorBandsConfig = colorBandsConfig.updateAll(activeBands) {
                                                            it.copy(deltaSat = (it.deltaSat + 0.05f).coerceIn(-1.0f, 1.0f))
                                                        }
                                                    },
                                                    onReset = {
                                                        colorBandsConfig = colorBandsConfig.updateAll(activeBands) { it.copy(deltaSat = 0f) }
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    },
                                                    onValueChange = { newVal ->
                                                        colorBandsConfig = if (activeBands.size == 1) {
                                                            colorBandsConfig.update(primaryBand) { it.copy(deltaSat = newVal) }
                                                        } else {
                                                            val liveAvg = activeBands.sumOf { colorBandsConfig.get(it).deltaSat.toDouble() }.toFloat() / activeBands.size
                                                            val delta = newVal - liveAvg
                                                            colorBandsConfig.updateAll(activeBands) {
                                                                it.copy(deltaSat = (it.deltaSat + delta).coerceIn(-1.0f, 1.0f))
                                                            }
                                                        }
                                                    }
                                                )

                                                PointColorSubSlider.PARLAKLIK -> ProGradientSliderRow(
                                                    label = "$bandLabel · ${stringResource(R.string.sub_lum_short)}",
                                                    valueBadge = "${formatSigned(avgLum * 100f)}%",
                                                    isModified = orderedSelected.any { abs(colorBandsConfig.get(it).deltaLum) > 0.02f },
                                                    value = avgLum,
                                                    defaultValue = 0f,
                                                    snapTolerance = 0.05f,
                                                    valueRange = -1.0f..1.0f,
                                                    enabled = cameraReady,
                                                    gradient = Brush.horizontalGradient(lumGradColors),
                                                    onDraggingChange = { isAnySliderDragging = it },
                                                    onStepMinus = {
                                                        colorBandsConfig = colorBandsConfig.updateAll(activeBands) {
                                                            it.copy(deltaLum = (it.deltaLum - 0.05f).coerceIn(-1.0f, 1.0f))
                                                        }
                                                    },
                                                    onStepPlus = {
                                                        colorBandsConfig = colorBandsConfig.updateAll(activeBands) {
                                                            it.copy(deltaLum = (it.deltaLum + 0.05f).coerceIn(-1.0f, 1.0f))
                                                        }
                                                    },
                                                    onReset = {
                                                        colorBandsConfig = colorBandsConfig.updateAll(activeBands) { it.copy(deltaLum = 0f) }
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    },
                                                    onValueChange = { newVal ->
                                                        colorBandsConfig = if (activeBands.size == 1) {
                                                            colorBandsConfig.update(primaryBand) { it.copy(deltaLum = newVal) }
                                                        } else {
                                                            val liveAvg = activeBands.sumOf { colorBandsConfig.get(it).deltaLum.toDouble() }.toFloat() / activeBands.size
                                                            val delta = newVal - liveAvg
                                                            colorBandsConfig.updateAll(activeBands) {
                                                                it.copy(deltaLum = (it.deltaLum + delta).coerceIn(-1.0f, 1.0f))
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        }

                                        ColorPanelMode.PIPET -> {
                                            if (targetedColorConfig.isActive) {
                                                // Noktasal renk başlığı + genişlik + yeniden seç + kaldır
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .alpha(ghostChromeAlpha),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    val shiftedHue = (((targetedColorConfig.targetHue + targetedColorConfig.deltaHue) % 360f + 360f) % 360f).coerceIn(0f, 359.99f)
                                                    val hasHueShift = abs(targetedColorConfig.deltaHue) > 1f
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(13.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(targetedColorConfig.previewColorInt))
                                                                .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                                                        )
                                                        if (hasHueShift) {
                                                            val shiftedColor = Color.hsv(
                                                                hue = shiftedHue,
                                                                saturation = (targetedColorConfig.targetSat * (1f + targetedColorConfig.deltaSat)).coerceIn(0.35f, 1f),
                                                                value = (targetedColorConfig.targetLum + targetedColorConfig.deltaLum * 0.45f).coerceIn(0.35f, 1f)
                                                            )
                                                            Text(
                                                                text = "→",
                                                                color = IrPalette.TextSecondary,
                                                                fontSize = 10.sp
                                                            )
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(13.dp)
                                                                    .clip(CircleShape)
                                                                    .background(shiftedColor)
                                                                    .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                                                            )
                                                        }
                                                        Text(
                                                            text = if (hasHueShift) {
                                                                getHueName(context, shiftedHue)
                                                            } else {
                                                                "${getHueName(context, targetedColorConfig.targetHue)} (${targetedColorConfig.targetHue.toInt()}°)"
                                                            },
                                                            color = IrPalette.IrTeal,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        listOf(
                                                            stringResource(R.string.range_narrow) to 14f,
                                                            stringResource(R.string.range_balanced) to 22f,
                                                            stringResource(R.string.range_wide) to 36f
                                                        ).forEach { (lbl, w) ->
                                                            val isSel = targetedColorConfig.hueWidth == w
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(6.dp))
                                                                    .background(if (isSel) IrPalette.IrAmber else IrPalette.SurfaceSubtle)
                                                                    .clickable {
                                                                        targetedColorConfig = targetedColorConfig.copy(
                                                                            hueWidth = w,
                                                                            hueFeather = if (w == 14f) 12f else if (w == 22f) 18f else 26f
                                                                        )
                                                                    }
                                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                                            ) {
                                                                Text(
                                                                    text = lbl,
                                                                    color = if (isSel) Color.Black else IrPalette.TextSecondary,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.SemiBold
                                                                )
                                                            }
                                                        }
                                                        // Tek dokunuşla yeniden pipetle seç (panel otomatik gizlenip tam ekran açılır!)
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(IrPalette.IrTeal.copy(alpha = 0.16f))
                                                                .clickable {
                                                                    isColorPipetteActive = true
                                                                    isPickerActive = false
                                                                    isExposureBoxPicking = false
                                                                    showStatus(strTapColor)
                                                                }
                                                                .padding(horizontal = 6.dp, vertical = 4.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            PipetteIcon(tint = IrPalette.IrTeal, modifier = Modifier.size(12.dp))
                                                        }
                                                        Text(
                                                            text = stringResource(R.string.action_remove),
                                                            color = IrPalette.IrCoral,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .clickable {
                                                                    targetedColorConfig = TargetedColorConfig()
                                                                }
                                                                .padding(horizontal = 5.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }

                                                SegmentedChoiceRow(
                                                    options = listOf(
                                                        PointColorSubSlider.TON to "${stringResource(R.string.sub_tint)} · ${formatSigned(targetedColorConfig.deltaHue)}°",
                                                        PointColorSubSlider.DOYGUNLUK to "${stringResource(R.string.sub_sat_short)} · ${formatSigned(targetedColorConfig.deltaSat * 100f)}%",
                                                        PointColorSubSlider.PARLAKLIK to "${stringResource(R.string.sub_lum_short)} · ${formatSigned(targetedColorConfig.deltaLum * 100f)}%"
                                                    ),
                                                    selected = activePointColorSubSlider,
                                                    enabled = cameraReady,
                                                    modifier = Modifier.alpha(ghostChromeAlpha),
                                                    onSelect = { activePointColorSubSlider = it }
                                                )

                                                when (activePointColorSubSlider) {
                                                    PointColorSubSlider.TON -> {
                                                        val hueSpectrumColors = remember(targetedColorConfig.targetHue) {
                                                            val base = targetedColorConfig.targetHue
                                                            List(9) { idx ->
                                                                val offset = -180f + idx * 45f
                                                                val h = (((base + offset) % 360f + 360f) % 360f).coerceIn(0f, 359.99f)
                                                                Color.hsv(h, 0.88f, 0.96f)
                                                            }
                                                        }
                                                        ProGradientSliderRow(
                                                            label = stringResource(R.string.slider_point_hue),
                                                            valueBadge = "${formatSigned(targetedColorConfig.deltaHue)}°",
                                                            isModified = abs(targetedColorConfig.deltaHue) > 0.5f,
                                                            value = targetedColorConfig.deltaHue,
                                                            defaultValue = 0f,
                                                            snapTolerance = 5f,
                                                            valueRange = -180f..180f,
                                                            enabled = cameraReady,
                                                            gradient = Brush.horizontalGradient(hueSpectrumColors),
                                                            onDraggingChange = { isAnySliderDragging = it },
                                                            onStepMinus = {
                                                                targetedColorConfig = targetedColorConfig.copy(
                                                                    deltaHue = (targetedColorConfig.deltaHue - 10f).coerceIn(-180f, 180f)
                                                                )
                                                            },
                                                            onStepPlus = {
                                                                targetedColorConfig = targetedColorConfig.copy(
                                                                    deltaHue = (targetedColorConfig.deltaHue + 10f).coerceIn(-180f, 180f)
                                                                )
                                                            },
                                                            onReset = {
                                                                targetedColorConfig = targetedColorConfig.copy(deltaHue = 0f)
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            },
                                                            onValueChange = {
                                                                targetedColorConfig = targetedColorConfig.copy(deltaHue = it)
                                                            }
                                                        )
                                                    }

                                                    PointColorSubSlider.DOYGUNLUK -> ProGradientSliderRow(
                                                        label = stringResource(R.string.slider_point_sat),
                                                        valueBadge = "${formatSigned(targetedColorConfig.deltaSat * 100f)}%",
                                                        isModified = abs(targetedColorConfig.deltaSat) > 0.02f,
                                                        value = targetedColorConfig.deltaSat,
                                                        defaultValue = 0f,
                                                        snapTolerance = 0.05f,
                                                        valueRange = -1.0f..1.0f,
                                                        enabled = cameraReady,
                                                        gradient = Brush.horizontalGradient(
                                                            listOf(Color(0xFF64748B), Color(0xFF22C55E), Color(0xFFF59E0B), Color(0xFFEF4444))
                                                        ),
                                                        onDraggingChange = { isAnySliderDragging = it },
                                                        onStepMinus = {
                                                            targetedColorConfig = targetedColorConfig.copy(
                                                                deltaSat = (targetedColorConfig.deltaSat - 0.05f).coerceIn(-1.0f, 1.0f)
                                                            )
                                                        },
                                                        onStepPlus = {
                                                            targetedColorConfig = targetedColorConfig.copy(
                                                                deltaSat = (targetedColorConfig.deltaSat + 0.05f).coerceIn(-1.0f, 1.0f)
                                                            )
                                                        },
                                                        onReset = {
                                                            targetedColorConfig = targetedColorConfig.copy(deltaSat = 0f)
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        },
                                                        onValueChange = {
                                                            targetedColorConfig = targetedColorConfig.copy(deltaSat = it)
                                                        }
                                                    )

                                                    PointColorSubSlider.PARLAKLIK -> ProGradientSliderRow(
                                                        label = stringResource(R.string.slider_point_lum),
                                                        valueBadge = "${formatSigned(targetedColorConfig.deltaLum * 100f)}%",
                                                        isModified = abs(targetedColorConfig.deltaLum) > 0.02f,
                                                        value = targetedColorConfig.deltaLum,
                                                        defaultValue = 0f,
                                                        snapTolerance = 0.05f,
                                                        valueRange = -1.0f..1.0f,
                                                        enabled = cameraReady,
                                                        gradient = Brush.horizontalGradient(
                                                            listOf(Color(0xFF334155), Color.White)
                                                        ),
                                                        onDraggingChange = { isAnySliderDragging = it },
                                                        onStepMinus = {
                                                            targetedColorConfig = targetedColorConfig.copy(
                                                                deltaLum = (targetedColorConfig.deltaLum - 0.05f).coerceIn(-1.0f, 1.0f)
                                                            )
                                                        },
                                                        onStepPlus = {
                                                            targetedColorConfig = targetedColorConfig.copy(
                                                                deltaLum = (targetedColorConfig.deltaLum + 0.05f).coerceIn(-1.0f, 1.0f)
                                                            )
                                                        },
                                                        onReset = {
                                                            targetedColorConfig = targetedColorConfig.copy(deltaLum = 0f)
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        },
                                                        onValueChange = {
                                                            targetedColorConfig = targetedColorConfig.copy(deltaLum = it)
                                                        }
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    text = stringResource(R.string.pipette_empty_hint),
                                                    color = IrPalette.TextSecondary,
                                                    fontSize = 11.sp
                                                )
                                                ActionPillButton(
                                                    label = if (isColorPipetteActive) stringResource(R.string.action_color_pipette_active) else stringResource(R.string.action_color_pipette),
                                                    selected = isColorPipetteActive,
                                                    accentColor = IrPalette.IrTeal,
                                                    enabled = cameraReady,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    leadingIcon = { tint -> PipetteIcon(tint = tint, modifier = Modifier.size(13.dp)) },
                                                    onClick = {
                                                        isColorPipetteActive = !isColorPipetteActive
                                                        if (isColorPipetteActive) {
                                                            isPickerActive = false
                                                            isExposureBoxPicking = false
                                                            showStatus(strTapColor)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                ControlTab.OLCUM -> {
                                    SegmentedChoiceRow(
                                        options = listOf(
                                            ExposureMeteringMode.FULL_SCREEN to stringResource(R.string.metering_full),
                                            ExposureMeteringMode.BOX_SPOT to stringResource(R.string.metering_box)
                                        ),
                                        selected = meteringMode,
                                        enabled = cameraReady,
                                        onSelect = { mode ->
                                            isExposureBoxPicking = false
                                            meteringMode = mode
                                            updatePreviewSettings()
                                            measureAndApplyExposureMetering(applyEvAdjust = false)
                                        }
                                    )

                                    if (meteringMode == ExposureMeteringMode.BOX_SPOT) {
                                        val selectedBoxSize = MeteringBoxSize.entries.minByOrNull {
                                            abs(exposureBoxSizeNorm - it.norm)
                                        } ?: MeteringBoxSize.ORTA

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            SegmentedChoiceRow(
                                                options = listOf(
                                                    MeteringBoxSize.KUCUK to stringResource(R.string.metering_size_small),
                                                    MeteringBoxSize.ORTA to stringResource(R.string.metering_size_medium),
                                                    MeteringBoxSize.BUYUK to stringResource(R.string.metering_size_large)
                                                ),
                                                selected = selectedBoxSize,
                                                enabled = cameraReady,
                                                modifier = Modifier.weight(1.65f),
                                                onSelect = { sizeEnum ->
                                                    exposureBoxSizeNorm = sizeEnum.norm
                                                    updatePreviewSettings()
                                                    measureAndApplyExposureMetering(applyEvAdjust = false)
                                                }
                                            )
                                            ActionPillButton(
                                                label = stringResource(R.string.action_center_box),
                                                selected = false,
                                                accentColor = IrPalette.IrCyan,
                                                enabled = cameraReady,
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    exposureBoxNorm = Offset(0.5f, 0.5f)
                                                    updatePreviewSettings()
                                                    measureAndApplyExposureMetering(applyEvAdjust = false)
                                                    showStatus(strBoxCentered)
                                                }
                                            )
                                        }
                                        Text(
                                            text = stringResource(R.string.metering_pinch_hint),
                                            color = IrPalette.TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    } else {
                                        Text(
                                            text = stringResource(R.string.metering_full_hint),
                                            color = IrPalette.TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Yatay Profesyonel Sekme Çubuğu (aşağı kaydırarak gizle, yukarı kaydırarak aç + Hayalet Modda silikleşir)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(ghostChromeAlpha)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    if (dragAmount > 14f) {
                                        isDeckCollapsed = true
                                    } else if (dragAmount < -14f) {
                                        isDeckCollapsed = false
                                    }
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                .padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val chips = listOf(
                                Triple(ControlTab.BD, stringResource(R.string.chip_wb), isWbModified),
                                Triple(ControlTab.POZ, stringResource(R.string.chip_exposure), isEvModified),
                                Triple(ControlTab.RENK, stringResource(R.string.chip_color), isColorModified),
                                Triple(ControlTab.OLCUM, stringResource(R.string.chip_metering), isMeteringModified)
                            )
                            chips.forEach { (tab, title, modified) ->
                                TabChip(
                                    title = title,
                                    selected = activeTab == tab && !isDeckCollapsed,
                                    hasActiveIndicator = modified,
                                    modifier = Modifier.weight(1f),
                                    icon = { tint ->
                                        when (tab) {
                                            ControlTab.BD -> WhiteBalanceIcon(tint = tint, modifier = Modifier.size(13.dp))
                                            ControlTab.POZ -> ExposureIcon(tint = tint, modifier = Modifier.size(13.dp))
                                            ControlTab.RENK -> ColorWheelIcon(tint = tint, modifier = Modifier.size(13.dp))
                                            ControlTab.OLCUM -> MeteringFrameIcon(tint = tint, modifier = Modifier.size(13.dp))
                                        }
                                    },
                                    onClick = {
                                        if (activeTab == tab && !isDeckCollapsed) {
                                            isDeckCollapsed = true
                                        } else {
                                            activeTab = tab
                                            isDeckCollapsed = false
                                        }
                                    }
                                )
                            }
                        }

                        // Kompakt panel aç/kapa vektör düğmesi
                        val chevronRotation by animateFloatAsState(
                            targetValue = if (isDeckCollapsed) 180f else 0f,
                            animationSpec = tween(200),
                            label = "chevronRot"
                        )
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .semantics { contentDescription = strPanelCd }
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isDeckCollapsed) IrPalette.IrAmber.copy(alpha = 0.20f)
                                    else Color.White.copy(alpha = 0.06f)
                                )
                                .border(
                                    0.8.dp,
                                    if (isDeckCollapsed) IrPalette.IrAmber else IrPalette.BorderSubtle,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { isDeckCollapsed = !isDeckCollapsed },
                            contentAlignment = Alignment.Center
                        ) {
                            ChevronIcon(
                                tint = if (isDeckCollapsed) IrPalette.IrAmber else IrPalette.TextPrimary,
                                modifier = Modifier
                                    .size(15.dp)
                                    .rotate(chevronRotation)
                            )
                        }
                    }
                }
            }
        }
        }

        // 3) KOMPAKT ALT DEKLANŞÖR ÇUBUĞU: Tam genişlikli vizörün altında sade, dengeli çekim şeridi
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(IrPalette.VoidBlack)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
                // Sol bölge: 46dp Galeri (dokun: Stüdyoda düzenle, uzun bas: Galeri) + 36dp Ön/Arka Kamera
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .semantics { contentDescription = strGalleryCd }
                            .clip(CircleShape)
                            .background(IrPalette.HudCard)
                            .border(1.3.dp, Color.White.copy(alpha = 0.34f), CircleShape)
                            .combinedClickable(
                                onClick = {
                                    studioPhotoUri = lastCapturedUri
                                    studioPhotoNonce++
                                    isStudioMode = true
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (!openGallerySafely(context, lastCapturedUri)) {
                                        showStatus(context.getString(R.string.status_gallery_open_failed))
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val thumb = lastCapturedThumb
                        if (thumb != null) {
                            Image(
                                bitmap = thumb,
                                contentDescription = strGalleryCd,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            GalleryIcon(tint = IrPalette.TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }

                    if (hasFrontCamera) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .semantics { contentDescription = strFlipCameraCd }
                                .clip(CircleShape)
                                .clickable(enabled = cameraReady) {
                                    useFrontCamera = !useFrontCamera
                                    holder.useFrontCamera = useFrontCamera
                                    holder.selectedResolution = null
                                    holder.previewFallbackLevel = 0
                                    zoomRatio = 1f
                                    holder.currentZoomRatio = 1f
                                    if (useFrontCamera && flashMode != FlashMode.OFF) {
                                        flashMode = FlashMode.OFF
                                        holder.flashMode = FlashMode.OFF
                                    }
                                    showStatus(
                                        context.getString(
                                            if (useFrontCamera) R.string.status_camera_front
                                            else R.string.status_camera_back
                                        )
                                    )
                                    cameraHandler.post { closeCameraInternal(releaseReader = true) }
                                    surfaceGeneration += 1
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (useFrontCamera) IrPalette.IrCyan.copy(alpha = 0.16f) else IrPalette.HudCard)
                                    .border(
                                        0.9.dp,
                                        if (useFrontCamera) IrPalette.IrCyan.copy(alpha = 0.7f) else IrPalette.BorderSubtle,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                FlipCameraIcon(
                                    tint = if (useFrontCamera) IrPalette.IrCyan else IrPalette.TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Orta: Kompakt profesyonel çift halkalı deklanşör (68dp)
                val innerSize by animateDpAsState(
                    targetValue = if (isShutterPressed) 46.dp else 54.dp,
                    animationSpec = tween(90),
                    label = "shutterSize"
                )
                val shutterInnerColor by animateColorAsState(
                    targetValue = when {
                        !cameraReady -> Color(0xFF525866)
                        timerCountdown > 0 -> IrPalette.IrCoral
                        else -> Color.White
                    },
                    animationSpec = tween(140),
                    label = "shutterInnerColor"
                )
                val shutterBorderColor = when {
                    !cameraReady -> Color.White.copy(alpha = 0.28f)
                    timerCountdown > 0 -> IrPalette.IrCoral.copy(alpha = 0.90f)
                    else -> Color.White.copy(alpha = 0.95f)
                }
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .semantics { contentDescription = strShutterCd }
                        .clip(CircleShape)
                        .border(2.5.dp, shutterBorderColor, CircleShape)
                        .clickable(
                            enabled = cameraReady,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            triggerCaptureWithTimer()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(innerSize)
                            .clip(CircleShape)
                            .background(shutterInnerColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (timerCountdown > 0) {
                            Text(
                                text = "✕",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Sağ bölge: Kompakt Sıfırla ikonu + Ön ayarlar hapı
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!isAutoMode) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .semantics { contentDescription = strResetAllCd }
                                .clip(CircleShape)
                                .clickable(enabled = cameraReady) { showResetAllDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (hasAnyManualChange) IrPalette.IrAmber.copy(alpha = 0.16f)
                                        else IrPalette.HudCard
                                    )
                                    .border(
                                        0.9.dp,
                                        if (hasAnyManualChange) IrPalette.IrAmber.copy(alpha = 0.7f) else IrPalette.BorderSubtle,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                ResetIcon(
                                    tint = if (hasAnyManualChange) IrPalette.IrAmber else IrPalette.TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    QuickPillAction(
                        text = stringResource(R.string.action_presets),
                        highlighted = showPresetsListDialog,
                        compact = true,
                        enabled = cameraReady,
                        leadingIcon = { tint -> PresetStarIcon(tint = tint, modifier = Modifier.size(13.dp)) },
                        onClick = { showPresetsListDialog = true }
                    )
                }
        }
    }

    // İLK AÇILIŞ GÖRSEL DENEYİM MODU SEÇİCİSİ (A1 Estetik · B1 IR Lab · C1 Pro Kamera + Dil Seçici)
    AnimatedVisibility(
        visible = showExperienceWelcome,
        enter = fadeIn(tween(240)),
        exit = fadeOut(tween(220))
    ) {
        val defaultWelcomeMode = remember {
            if (sharedPrefs.getBoolean("experience_mode_chosen_v1", false)) experienceMode else ExperienceMode.AESTHETIC
        }
        var pickedWelcomeMode by remember { mutableStateOf(defaultWelcomeMode) }
        val ctaAccent by animateColorAsState(
            targetValue = when (pickedWelcomeMode) {
                ExperienceMode.AESTHETIC -> IrPalette.IrMagenta
                ExperienceMode.IR_LAB -> IrPalette.IrCoral
                ExperienceMode.PRO -> IrPalette.IrAmber
            },
            animationSpec = tween(220),
            label = "welcomeAccent"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(IrPalette.VoidBlack)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            ctaAccent.copy(alpha = 0.16f),
                            Color.Transparent,
                            Color.Transparent
                        )
                    )
                )
                .pointerInput(Unit) {}
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Üst başlık + Sağ üst Dil değiştirme kapsülü (🌐 EN ▾)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "SpectraS",
                            color = IrPalette.TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.3.sp
                        )
                        Text(
                            text = stringResource(R.string.welcome_title),
                            color = ctaAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(IrPalette.HudCard)
                            .border(1.dp, IrPalette.BorderBright, RoundedCornerShape(50))
                            .clickable { showLanguageDialog = true }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        GlobeIcon(tint = IrPalette.IrCyan, modifier = Modifier.size(14.dp))
                        Text(
                            text = activeLanguage.shortCode,
                            color = IrPalette.TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.welcome_subtitle),
                    color = IrPalette.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                val modeCards = listOf(
                    listOf(
                        ExperienceMode.AESTHETIC,
                        IrPalette.IrMagenta,
                        R.drawable.mode_aesthetic,
                        R.string.exp_aesthetic_title,
                        R.string.exp_aesthetic_desc,
                        R.string.exp_aesthetic_tags
                    ),
                    listOf(
                        ExperienceMode.IR_LAB,
                        IrPalette.IrCoral,
                        R.drawable.mode_ir_lab,
                        R.string.exp_ir_title,
                        R.string.exp_ir_desc,
                        R.string.exp_ir_tags
                    ),
                    listOf(
                        ExperienceMode.PRO,
                        IrPalette.IrAmber,
                        R.drawable.mode_pro,
                        R.string.exp_pro_title,
                        R.string.exp_pro_desc,
                        R.string.exp_pro_tags
                    )
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(modeCards, key = { (it[0] as ExperienceMode).prefKey }) { card ->
                        val mode = card[0] as ExperienceMode
                        val accent = card[1] as Color
                        val imageRes = card[2] as Int
                        val titleRes = card[3] as Int
                        val descRes = card[4] as Int
                        val tagsRes = card[5] as Int
                        val selected = pickedWelcomeMode == mode
                        val cardHeight by animateDpAsState(
                            targetValue = if (selected) 174.dp else 144.dp,
                            animationSpec = tween(220),
                            label = "welcomeCardH_${mode.prefKey}"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cardHeight)
                                .clip(RoundedCornerShape(18.dp))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) accent else IrPalette.BorderSubtle,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable {
                                    pickedWelcomeMode = mode
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                        ) {
                            Image(
                                painter = painterResource(id = imageRes),
                                contentDescription = stringResource(titleRes),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Seçili kart parlak ve canlı, diğerleri odak için hafif koyu
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = if (selected) 0.06f else 0.30f))
                            )
                            // Alt okunabilirlik degrade katmanı (WCAG kontrast)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.0f to Color.Black.copy(alpha = 0.22f),
                                                0.38f to Color.Transparent,
                                                0.66f to Color(0xAA06080D),
                                                1.0f to Color(0xF205070B)
                                            )
                                        )
                                    )
                            )

                            // Üst satır: Sol etiket kapsülü + Sağ seçili rozeti
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .padding(horizontal = 11.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xB8080B10))
                                        .border(0.8.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(50))
                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = stringResource(tagsRes),
                                        color = IrPalette.TextPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (selected) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(accent)
                                            .padding(horizontal = 9.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "✓ ${stringResource(R.string.welcome_selected_badge)}",
                                            color = if (mode == ExperienceMode.PRO) Color.Black else Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x66000000))
                                            .border(1.5.dp, Color.White.copy(alpha = 0.65f), CircleShape)
                                    )
                                }
                            }

                            // Alt satır: Mod başlığı + net 1-2 satır açıklama
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 13.dp, vertical = 11.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(titleRes),
                                    color = if (selected) accent else Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(descRes),
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Alt aksiyon düğmesi
                val pickedTitle = when (pickedWelcomeMode) {
                    ExperienceMode.AESTHETIC -> stringResource(R.string.exp_aesthetic_title)
                    ExperienceMode.IR_LAB -> stringResource(R.string.exp_ir_title)
                    ExperienceMode.PRO -> stringResource(R.string.exp_pro_title)
                }
                Button(
                    onClick = {
                        showExperienceWelcome = false
                        switchExperienceMode(pickedWelcomeMode, announce = true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ctaAccent),
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = "${stringResource(R.string.welcome_start)} · $pickedTitle",
                        color = if (pickedWelcomeMode == ExperienceMode.PRO) Color.Black else Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    }

    // Tümünü Sıfırla Onay Diyalogu
    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            containerColor = IrPalette.SurfaceElevated,
            titleContentColor = IrPalette.TextPrimary,
            textContentColor = IrPalette.TextSecondary,
            title = {
                Text(
                    text = stringResource(R.string.dialog_reset_all_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.dialog_reset_all_message),
                    fontSize = 14.sp,
                    color = IrPalette.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        kelvinValue = liveAutoKelvin.coerceIn(KELVIN_MIN, KELVIN_MAX)
                        tintValue = liveAutoTint.coerceIn(-100f, 100f)
                        exposureEvIndex = 0
                        saturationValue = DEFAULT_SATURATION
                        contrastValue = DEFAULT_CONTRAST
                        channelSwapEnabled = false
                        aeLockEnabled = false
                        selectedAestheticPreset = AESTHETIC_PRESETS.first()
                        activeLookGrade = LookGradeParams()
                        aestheticFilterStrength = 1.0f
                        targetedColorConfig = TargetedColorConfig()
                        colorBandsConfig = ColorBandsConfig()
                        isPickerActive = false
                        isColorPipetteActive = false
                        exposureBoxNorm = Offset(0.5f, 0.5f)
                        exposureBoxSizeNorm = MeteringBoxSize.ORTA.norm
                        meteringMode = ExposureMeteringMode.FULL_SCREEN
                        updatePreviewSettings()
                        showResetAllDialog = false
                        showStatus(strAllReset)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IrPalette.IrAmber)
                ) {
                    Text(stringResource(R.string.dialog_confirm), color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel), color = IrPalette.TextSecondary)
                }
            }
        )
    }

    // Görünümler & Ön Ayarlar Listesi Diyalogu (Hazır Kızılötesi/Sinema Görünümleri + Özel Ön Ayarlar)
    if (showPresetsListDialog) {
        val applyPresetToCamera: (CameraPreset) -> Unit = { preset ->
            applyPresetDirect(preset, announce = true)
            showPresetsListDialog = false
        }
        AlertDialog(
            onDismissRequest = { showPresetsListDialog = false },
            containerColor = IrPalette.SurfaceElevated,
            titleContentColor = IrPalette.TextPrimary,
            textContentColor = IrPalette.TextSecondary,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.dialog_presets_title),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.dialog_presets_new),
                        color = IrPalette.IrAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(IrPalette.IrAmber.copy(alpha = 0.14f))
                            .clickable {
                                showPresetsListDialog = false
                                showSaveDialog = true
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 390.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    item(key = "hdr_builtin") {
                        Text(
                            text = stringResource(R.string.presets_section_builtin),
                            color = IrPalette.IrAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                        )
                    }
                    val modeOrderedBuiltIns = getAllBuiltInPresetsForMode(experienceMode)
                    items(modeOrderedBuiltIns, key = { "bi_${it.name}" }) { preset ->
                        val isMatching = !isAutoMode &&
                            abs(kelvinValue - preset.kelvin) < 5f &&
                            abs(tintValue - preset.tint) < 1f &&
                            channelSwapEnabled == preset.channelSwap &&
                            colorBandsConfig == preset.colorBands
                        val dotColor = Color(preset.accentHex)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    if (isMatching) dotColor.copy(alpha = 0.18f)
                                    else Color.White.copy(alpha = 0.05f)
                                )
                                .border(
                                    1.dp,
                                    if (isMatching) dotColor else Color.White.copy(alpha = 0.10f),
                                    RoundedCornerShape(11.dp)
                                )
                                .clickable { applyPresetToCamera(preset) }
                                .padding(horizontal = 11.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                        .border(1.dp, Color.White.copy(alpha = 0.65f), CircleShape)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = preset.name,
                                        color = if (isMatching) dotColor else IrPalette.TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$strWbShort ${preset.kelvin.toInt()}K · C ${String.format(appLocale, "%.2f", preset.contrast)}x · S ${String.format(appLocale, "%.2f", preset.saturation)}x",
                                        color = IrPalette.TextSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            if (preset.channelSwap) {
                                Text(
                                    text = strBadgeSwap,
                                    color = IrPalette.IrCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(IrPalette.IrCyan.copy(alpha = 0.16f))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    item(key = "hdr_custom") {
                        Text(
                            text = stringResource(R.string.presets_section_custom),
                            color = IrPalette.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    if (savedPresets.isEmpty()) {
                        item(key = "empty_custom") {
                            Text(
                                text = stringResource(R.string.dialog_presets_empty),
                                color = IrPalette.TextTertiary,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        items(savedPresets, key = { "u_${it.name}" }) { preset ->
                            val isMatching = !isAutoMode &&
                                abs(kelvinValue - preset.kelvin) < 5f &&
                                abs(tintValue - preset.tint) < 1f &&
                                channelSwapEnabled == preset.channelSwap
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(
                                        if (isMatching) IrPalette.IrAmber.copy(alpha = 0.18f)
                                        else Color.White.copy(alpha = 0.05f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isMatching) IrPalette.IrAmber else Color.White.copy(alpha = 0.10f),
                                        RoundedCornerShape(11.dp)
                                    )
                                    .clickable { applyPresetToCamera(preset) }
                                    .padding(horizontal = 11.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = preset.name,
                                        color = if (isMatching) IrPalette.IrAmber else IrPalette.TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$strWbShort ${preset.kelvin.toInt()}K · $strTintShort ${formatSigned(preset.tint)} · C ${String.format(appLocale, "%.2f", preset.contrast)}x${if (preset.channelSwap) " · $strBadgeSwap" else ""}",
                                        color = IrPalette.TextSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            savedPresets.remove(preset)
                                            savePresetsToStorage()
                                            showStatus(context.getString(R.string.status_preset_deleted, preset.name))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "✕",
                                        color = IrPalette.TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPresetsListDialog = false }) {
                    Text(stringResource(R.string.dialog_close), color = IrPalette.TextSecondary)
                }
            }
        )
    }

    // Ön Ayar Kaydet Diyalogu
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = IrPalette.SurfaceElevated,
            titleContentColor = IrPalette.TextPrimary,
            textContentColor = IrPalette.TextSecondary,
            title = {
                Text(
                    text = stringResource(R.string.dialog_save_preset_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val saveK = if (isAutoMode) liveAutoKelvin else kelvinValue
                    val saveT = if (isAutoMode) liveAutoTint else tintValue
                    Text(
                        text = "$strWbShort ${saveK.toInt()}K · $strTintShort ${formatSigned(saveT)} · C ${String.format(appLocale, "%.2f", contrastValue)}x · S ${String.format(appLocale, "%.2f", saturationValue)}x${if (channelSwapEnabled) " · $strBadgeSwap" else ""}",
                        color = IrPalette.IrAmber,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    OutlinedTextField(
                        value = presetNameInput,
                        onValueChange = { presetNameInput = it },
                        label = { Text(stringResource(R.string.dialog_preset_name_label)) },
                        placeholder = { Text(stringResource(R.string.dialog_preset_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = IrPalette.TextPrimary,
                            unfocusedTextColor = IrPalette.TextPrimary,
                            focusedBorderColor = IrPalette.IrAmber,
                            unfocusedBorderColor = IrPalette.BorderSubtle,
                            focusedLabelColor = IrPalette.IrAmber,
                            unfocusedLabelColor = IrPalette.TextSecondary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = presetNameInput.trim()
                        if (trimmed.isNotEmpty()) {
                            val saveK = if (isAutoMode) liveAutoKelvin else kelvinValue
                            val saveT = if (isAutoMode) liveAutoTint else tintValue
                            savedPresets.removeAll { it.name.equals(trimmed, ignoreCase = true) }
                            savedPresets.add(
                                CameraPreset(
                                    name = trimmed,
                                    kelvin = saveK,
                                    tint = saveT,
                                    contrast = contrastValue,
                                    saturation = saturationValue,
                                    channelSwap = channelSwapEnabled,
                                    colorBands = colorBandsConfig
                                )
                            )
                            savePresetsToStorage()
                            presetNameInput = ""
                            showSaveDialog = false
                            showStatus(context.getString(R.string.status_preset_saved, trimmed))
                        } else {
                            showStatus(strEnterName)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IrPalette.IrAmber)
                ) {
                    Text(stringResource(R.string.dialog_save), color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel), color = IrPalette.TextSecondary)
                }
            }
        )
    }

    // Oran Öncelikli Çözünürlük Seçici Diyalogu (4:3 · 16:9 · 1:1 · 20:9 + Gelişmiş)
    if (showResolutionDialog) {
        ResolutionSelectorDialog(
            availableResolutions = availableResolutions.toList(),
            selectedResolution = selectedResolution,
            onSelect = { res ->
                sharedPrefs.edit {
                    putInt("selected_res_w", res.width)
                    putInt("selected_res_h", res.height)
                }
                selectedResolution = res
                holder.selectedResolution = res
                holder.previewFallbackLevel = 0
                holder.activePreviewSize = null
                showResolutionDialog = false
                showStatus(context.getString(R.string.status_res_fmt, formatMegapixels(res), formatAspectRatioTag(res)))
                restartForResolution(res)
            },
            onDismiss = { showResolutionDialog = false }
        )
    }

    // Ayarlar & Deneyim Modu Değiştirme Diyalogu (üst çubuktaki ⚙ simgesinden her zaman erişilir)
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = IrPalette.SurfaceElevated,
            titleContentColor = IrPalette.TextPrimary,
            textContentColor = IrPalette.TextSecondary,
            title = {
                Text(
                    text = stringResource(R.string.settings_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "sec_exp") {
                        Text(
                            text = stringResource(R.string.settings_exp_section),
                            color = IrPalette.IrAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val expItems = listOf(
                        Triple(ExperienceMode.AESTHETIC, IrPalette.IrMagenta, R.string.exp_aesthetic_title to R.string.exp_aesthetic_tags),
                        Triple(ExperienceMode.IR_LAB, IrPalette.IrCoral, R.string.exp_ir_title to R.string.exp_ir_tags),
                        Triple(ExperienceMode.PRO, IrPalette.IrAmber, R.string.exp_pro_title to R.string.exp_pro_tags)
                    )
                    items(expItems, key = { it.first.prefKey }) { (mode, accent, resPair) ->
                        val isSel = experienceMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
                                .border(
                                    1.dp,
                                    if (isSel) accent else Color.White.copy(alpha = 0.10f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    showSettingsDialog = false
                                    switchExperienceMode(mode, announce = true)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(resPair.first),
                                    color = if (isSel) accent else IrPalette.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(resPair.second),
                                    color = IrPalette.TextSecondary,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (isSel) {
                                Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(accent))
                            }
                        }
                    }

                    item(key = "sec_lang") {
                        Text(
                            text = stringResource(R.string.settings_language_section),
                            color = IrPalette.IrCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    item(key = "tool_lang") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(0.8.dp, IrPalette.IrCyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .clickable {
                                    showSettingsDialog = false
                                    showLanguageDialog = true
                                }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GlobeIcon(tint = IrPalette.IrCyan, modifier = Modifier.size(16.dp))
                                Text(
                                    text = stringResource(R.string.settings_language),
                                    color = IrPalette.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = activeLanguage.nativeName,
                                color = IrPalette.IrCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    item(key = "sec_tools") {
                        Text(
                            text = stringResource(R.string.settings_tools_section),
                            color = IrPalette.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    item(key = "tool_grid") {
                        SettingsToggleRow(
                            label = stringResource(R.string.settings_grid),
                            checked = showGrid,
                            accentColor = IrPalette.IrAmber,
                            onToggle = { showGrid = !showGrid }
                        )
                    }
                    item(key = "tool_level") {
                        SettingsToggleRow(
                            label = stringResource(R.string.settings_level),
                            checked = showLevel,
                            accentColor = IrPalette.IrEmerald,
                            onToggle = {
                                showLevel = !showLevel
                                showStatus(if (showLevel) strLevelOn else strLevelOff)
                            }
                        )
                    }
                    if (supportsRaw) {
                        item(key = "tool_raw") {
                            SettingsToggleRow(
                                label = stringResource(R.string.settings_raw),
                                checked = rawEnabled,
                                accentColor = IrPalette.IrAmber,
                                onToggle = {
                                    rawEnabled = !rawEnabled
                                    holder.rawCaptureEnabled = rawEnabled
                                    showStatus(if (rawEnabled) strRawOn else strRawOff)
                                    cameraHandler.post { closeCameraInternal(releaseReader = true) }
                                    surfaceGeneration += 1
                                }
                            )
                        }
                    }
                    item(key = "tool_res") {
                        val activeRes = selectedResolution
                        val resTxt = if (activeRes != null) "${formatMegapixels(activeRes)} (${formatAspectRatioTag(activeRes)})" else strResFallback
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .clickable(enabled = availableResolutions.isNotEmpty()) {
                                    showSettingsDialog = false
                                    showResolutionDialog = true
                                }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.settings_resolution), color = IrPalette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(resTxt, color = IrPalette.IrCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }

                    item(key = "sec_about") {
                        Text(
                            text = stringResource(R.string.settings_about_section),
                            color = IrPalette.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    item(key = "tool_privacy") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .clickable {
                                    try {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://alisamilaytekin.github.io/infrared-photography-app/privacy-policy.html")
                                        )
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.settings_privacy_policy),
                                color = IrPalette.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "↗",
                                color = IrPalette.IrCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    item(key = "tool_version") {
                        val appVersionName = remember {
                            try {
                                val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    context.packageManager.getPackageInfo(
                                        context.packageName,
                                        PackageManager.PackageInfoFlags.of(0)
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    context.packageManager.getPackageInfo(context.packageName, 0)
                                }
                                pkgInfo.versionName ?: "5.0"
                            } catch (_: Exception) {
                                "5.0"
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.settings_version),
                                color = IrPalette.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "v$appVersionName",
                                color = IrPalette.TextSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text(stringResource(R.string.dialog_close), color = IrPalette.IrAmber, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // 13 Küresel Dil Seçim Diyalogu (Varsayılan: English)
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = IrPalette.SurfaceElevated,
            titleContentColor = IrPalette.TextPrimary,
            textContentColor = IrPalette.TextSecondary,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlobeIcon(tint = IrPalette.IrCyan, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.dialog_language_title),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(AppLanguage.entries, key = { it.code }) { lang ->
                        val isSel = activeLanguage == lang
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) IrPalette.IrCyan.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
                                .border(
                                    1.dp,
                                    if (isSel) IrPalette.IrCyan else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    showLanguageDialog = false
                                    if (lang != activeLanguage) {
                                        saveAppLanguageAndRecreate(context, lang)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = lang.nativeName,
                                color = if (isSel) IrPalette.IrCyan else IrPalette.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = lang.shortCode,
                                    color = IrPalette.TextTertiary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isSel) {
                                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(IrPalette.IrCyan))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.dialog_close), color = IrPalette.IrCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
internal fun GlobeIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.11f
        val r = size.minDimension * 0.42f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = tint, radius = r, center = c, style = Stroke(width = stroke))
        drawOval(
            color = tint,
            topLeft = Offset(c.x - r * 0.48f, c.y - r),
            size = androidx.compose.ui.geometry.Size(r * 0.96f, r * 2f),
            style = Stroke(width = stroke * 0.85f)
        )
        drawLine(
            color = tint,
            start = Offset(c.x - r, c.y),
            end = Offset(c.x + r, c.y),
            strokeWidth = stroke * 0.85f
        )
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    accentColor: Color,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = IrPalette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = IrPalette.TextSecondary,
                uncheckedTrackColor = Color.White.copy(alpha = 0.14f)
            )
        )
    }
}

@Composable
internal fun ModeSegmentItem(
    text: String,
    selected: Boolean,
    activeColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) activeColor else Color.Transparent,
        animationSpec = tween(180),
        label = "modeSegBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.Black else IrPalette.TextSecondary,
        animationSpec = tween(180),
        label = "modeSegTxt"
    )
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 36.dp, minHeight = 30.dp)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
internal fun TopBadgeButton(
    text: String,
    active: Boolean,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 34.dp, minHeight = 36.dp)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (active) accentColor.copy(alpha = 0.22f) else IrPalette.HudCard)
                .border(0.9.dp, if (active) accentColor else IrPalette.BorderSubtle, RoundedCornerShape(50))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(
                text = text,
                color = if (active) accentColor else IrPalette.TextPrimary,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
internal fun TopIconButton(
    active: Boolean,
    accentColor: Color,
    contentDesc: String,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    Box(
        modifier = Modifier
            .size(33.dp)
            .semantics { contentDescription = contentDesc }
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (active) accentColor.copy(alpha = 0.20f) else IrPalette.HudCard)
                .border(0.9.dp, if (active) accentColor else IrPalette.BorderSubtle, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            icon(if (active) accentColor else IrPalette.TextSecondary)
        }
    }
}

@Composable
private fun StatusTelemetryChip(
    text: String,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .sizeIn(minHeight = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = accentColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Profesyonel 5-bölge RGB + Parlaklık (Luma) canlı kamera histogramı.
 * Lightroom / Sony Alpha / Halide tarzı yumuşatılmış RGB kanalları + parlaklık zarfı + gölge/vurgu taşma uyarıları.
 */
@Composable
internal fun LiveHistogramOverlay(
    data: ProHistogramData,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        data.highlightClip -> IrPalette.IrCoral.copy(alpha = 0.72f)
        data.shadowClip -> IrPalette.IrCyan.copy(alpha = 0.65f)
        else -> Color.White.copy(alpha = 0.16f)
    }
    Box(
        modifier = modifier
            .width(114.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xD907090D))
            .border(0.9.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 5.dp, vertical = 4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val luma = data.luma
            if (luma.isEmpty()) return@Canvas
            val w = size.width
            val h = size.height
            val topPad = 3.dp.toPx()
            val graphH = (h - topPad).coerceAtLeast(1f)

            // 5 pozlama bölgesi dikey referans ızgarası (Siyahlar · Gölgeler · Orta · Vurgular · Beyazlar)
            val zoneGuide = Color.White.copy(alpha = 0.10f)
            val midGuide = Color.White.copy(alpha = 0.17f)
            for (z in 1..4) {
                val x = w * (z / 5f)
                drawLine(zoneGuide, Offset(x, 0f), Offset(x, h), strokeWidth = 0.7.dp.toPx())
            }
            drawLine(midGuide, Offset(0f, topPad + graphH * 0.5f), Offset(w, topPad + graphH * 0.5f), strokeWidth = 0.6.dp.toPx())

            fun buildSmoothCurvePaths(bins: FloatArray): Pair<Path, Path> {
                val step = w / (bins.size - 1).coerceAtLeast(1)
                val fillPath = Path()
                val strokePath = Path()
                fillPath.moveTo(0f, h)
                var prevX = 0f
                var prevY = h - (bins[0].coerceIn(0f, 1f) * graphH)
                fillPath.lineTo(prevX, prevY)
                strokePath.moveTo(prevX, prevY)
                for (i in 1 until bins.size) {
                    val cx = i * step
                    val cy = h - (bins[i].coerceIn(0f, 1f) * graphH)
                    val midX = (prevX + cx) * 0.5f
                    val midY = (prevY + cy) * 0.5f
                    fillPath.quadraticBezierTo(prevX, prevY, midX, midY)
                    strokePath.quadraticBezierTo(prevX, prevY, midX, midY)
                    prevX = cx
                    prevY = cy
                }
                fillPath.lineTo(prevX, prevY)
                fillPath.lineTo(w, h)
                fillPath.close()
                strokePath.lineTo(prevX, prevY)
                return fillPath to strokePath
            }

            // Üst üste katmanlı RGB kanalları (renk dengesi tek bakışta okunur)
            val channels = listOf(
                Triple(data.r, Color(0xFFFF453A), 0.34f),
                Triple(data.g, Color(0xFF30D158), 0.32f),
                Triple(data.b, Color(0xFF0A84FF), 0.36f)
            )
            channels.forEach { (bins, color, fillAlpha) ->
                if (bins.isNotEmpty()) {
                    val (fillP, strokeP) = buildSmoothCurvePaths(bins)
                    drawPath(
                        path = fillP,
                        brush = Brush.verticalGradient(
                            colors = listOf(color.copy(alpha = fillAlpha), color.copy(alpha = 0.04f))
                        ),
                        style = Fill
                    )
                    drawPath(
                        path = strokeP,
                        color = color.copy(alpha = 0.72f),
                        style = Stroke(width = 0.9.dp.toPx(), join = StrokeJoin.Round)
                    )
                }
            }

            // Üstte parlak beyaz Luma (genel parlaklık) zarf çizgisi
            val (lumaFill, lumaStroke) = buildSmoothCurvePaths(luma)
            drawPath(
                path = lumaFill,
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.02f))
                ),
                style = Fill
            )
            drawPath(
                path = lumaStroke,
                color = Color.White.copy(alpha = 0.94f),
                style = Stroke(width = 1.25.dp.toPx(), join = StrokeJoin.Round)
            )

            // Alt taban çizgisi
            drawLine(
                color = Color.White.copy(alpha = 0.22f),
                start = Offset(0f, h),
                end = Offset(w, h),
                strokeWidth = 0.8.dp.toPx()
            )

            // Sol üst gölge taşma (crushed blacks) + sağ üst vurgu yanma (blown highlights) üçgen göstergeleri
            val tri = 4.2.dp.toPx()
            val shadowTri = Path().apply {
                moveTo(0f, 0f)
                lineTo(tri, 0f)
                lineTo(0f, tri)
                close()
            }
            drawPath(
                path = shadowTri,
                color = if (data.shadowClip) IrPalette.IrCyan else Color.White.copy(alpha = 0.18f),
                style = Fill
            )

            val highlightTri = Path().apply {
                moveTo(w, 0f)
                lineTo(w - tri, 0f)
                lineTo(w, tri)
                close()
            }
            drawPath(
                path = highlightTri,
                color = if (data.highlightClip) IrPalette.IrCoral else Color.White.copy(alpha = 0.18f),
                style = Fill
            )
        }
    }
}

/**
 * Lightroom / Darkroom tarzı etkileşimli Canlı Renk Tonu (Hue) Histogramı ve 12-Bantlı Çoklu Seçici.
 * Histogramda veya alttaki 12 renk noktasından tekli ya da çoklu renk seçip eşzamanlı düzenleyebilirsiniz.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ColorHistogramBandPicker(
    hueBins: FloatArray,
    selectedBands: Set<ColorBand>,
    multiSelectMode: Boolean,
    config: ColorBandsConfig,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelectBand: (ColorBand) -> Unit,
    onSelectBandRange: (Set<ColorBand>) -> Unit,
    onLongPressBand: (ColorBand) -> Unit
) {
    val currentOnSelect by rememberUpdatedState(onSelectBand)
    val currentOnSelectRange by rememberUpdatedState(onSelectBandRange)
    val currentMultiMode by rememberUpdatedState(multiSelectMode)
    val allBands = ColorBand.entries

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF090C11))
            .border(0.8.dp, IrPalette.BorderSubtle, RoundedCornerShape(10.dp))
            .padding(horizontal = 5.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Üst: Canlı 48 sütunlu gökkuşağı Hue histogramı + seçili bant çerçeveleri (dokun & aralık sürükle)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFF05070A))
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    fun xToBand(x: Float): ColorBand {
                        val frac = (x / size.width.coerceAtLeast(1).toFloat()).coerceIn(0f, 0.9999f)
                        val idx = (frac * allBands.size).toInt().coerceIn(0, allBands.lastIndex)
                        return allBands[idx]
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val anchorBand = xToBand(down.position.x)
                        down.consume()
                        if (!currentMultiMode) {
                            var lastBand = anchorBand
                            currentOnSelect(anchorBand)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val curBand = xToBand(change.position.x)
                                if (curBand != lastBand) {
                                    lastBand = curBand
                                    currentOnSelect(curBand)
                                }
                                change.consume()
                            }
                        } else {
                            var didRangeDrag = false
                            var lastRange: Pair<Int, Int>? = null
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val curBand = xToBand(change.position.x)
                                if (curBand != anchorBand || didRangeDrag) {
                                    didRangeDrag = true
                                    val lo = minOf(anchorBand.index, curBand.index)
                                    val hi = maxOf(anchorBand.index, curBand.index)
                                    val pair = lo to hi
                                    if (pair != lastRange) {
                                        lastRange = pair
                                        currentOnSelectRange(allBands.subList(lo, hi + 1).toSet())
                                    }
                                }
                                change.consume()
                            }
                            if (!didRangeDrag) {
                                currentOnSelect(anchorBand)
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val bandW = w / allBands.size.toFloat()

                // 1) Seçili tüm bantların arka plan vurgusu
                for (band in selectedBands) {
                    val selLeft = band.index * bandW
                    drawRect(
                        color = band.swatchColor.copy(alpha = 0.20f),
                        topLeft = Offset(selLeft, 0f),
                        size = androidx.compose.ui.geometry.Size(bandW, h)
                    )
                }

                // 2) 12 bant dikey ayırıcı çizgileri
                for (i in 1 until allBands.size) {
                    val x = i * bandW
                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 0.7.dp.toPx()
                    )
                }

                // 3) 48 sütunlu canlı gökkuşağı Hue dağılım histogramı
                if (hueBins.isNotEmpty()) {
                    val barW = w / hueBins.size.toFloat()
                    val maxBarH = h - 6.dp.toPx()
                    for (i in hueBins.indices) {
                        val v = hueBins[i].coerceIn(0f, 1f)
                        val bandPos = ((i + 0.5f) / hueBins.size.toFloat()) * allBands.size.toFloat()
                        val hueDeg = perceptualBandPosToHue(bandPos).coerceIn(0f, 359.99f)
                        val barColor = Color.hsv(hueDeg, 0.86f, 0.96f)
                        val barH = (v * maxBarH).coerceAtLeast(1.5.dp.toPx())
                        val bx = i * barW
                        drawRect(
                            color = barColor.copy(alpha = 0.58f),
                            topLeft = Offset(bx + 0.4f, h - barH),
                            size = androidx.compose.ui.geometry.Size((barW - 0.8f).coerceAtLeast(1f), barH)
                        )
                        // Üst kenar parlak taç çizgisi
                        drawLine(
                            color = barColor.copy(alpha = 0.95f),
                            start = Offset(bx, h - barH),
                            end = Offset(bx + barW, h - barH),
                            strokeWidth = 1.4.dp.toPx()
                        )
                    }
                }

                // 4) Seçili bantların keskin optik çerçeveleri
                for (band in selectedBands) {
                    val selLeft = band.index * bandW
                    drawRoundRect(
                        color = band.swatchColor,
                        topLeft = Offset(selLeft + 0.7.dp.toPx(), 0.7.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size((bandW - 1.4.dp.toPx()).coerceAtLeast(2f), h - 1.4.dp.toPx()),
                        cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx()),
                        style = Stroke(width = 1.3.dp.toPx())
                    )
                }
            }
        }

        // Alt: Histogram bantlarıyla 1:1 hizalı 12 renk noktası (bas-bırak seçim + uzun bas: çoklu seçim)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            allBands.forEach { band ->
                val isSelected = band in selectedBands
                val isBandModified = config.get(band).isModified
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .combinedClickable(
                            enabled = enabled,
                            onClick = { onSelectBand(band) },
                            onLongClick = { onLongPressBand(band) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 14.dp else 10.dp)
                            .clip(CircleShape)
                            .background(band.swatchColor)
                            .border(
                                width = if (isSelected) 1.8.dp else 0.8.dp,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.28f),
                                shape = CircleShape
                            )
                    )
                    if (isBandModified) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 1.dp, end = 2.dp)
                                .size(4.5.dp)
                                .clip(CircleShape)
                                .background(IrPalette.IrAmber)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun <T> SegmentedChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(0.8.dp, IrPalette.BorderSubtle, RoundedCornerShape(9.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { (item, label) ->
            val isSel = item == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minHeight = 30.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isSel) IrPalette.IrAmber.copy(alpha = 0.20f) else Color.Transparent)
                    .border(
                        0.8.dp,
                        if (isSel) IrPalette.IrAmber.copy(alpha = 0.85f) else Color.Transparent,
                        RoundedCornerShape(7.dp)
                    )
                    .clickable(enabled = enabled) { onSelect(item) }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSel) IrPalette.IrAmber else IrPalette.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun ActionPillButton(
    label: String,
    selected: Boolean,
    accentColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable (Color) -> Unit)? = null,
    onClick: () -> Unit
) {
    val bgAlpha by animateFloatAsState(if (selected) 0.20f else 0.05f, label = "actionBg")
    val bg = if (selected) accentColor.copy(alpha = bgAlpha) else Color.White.copy(alpha = 0.05f)
    val border = if (selected) accentColor.copy(alpha = 0.85f) else IrPalette.BorderSubtle
    val contentColor = if (selected) accentColor else IrPalette.TextPrimary

    Box(
        modifier = modifier
            .sizeIn(minHeight = 34.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(bg)
                .border(0.8.dp, border, RoundedCornerShape(9.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                leadingIcon(contentColor)
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun TabChip(
    title: String,
    selected: Boolean,
    hasActiveIndicator: Boolean,
    modifier: Modifier = Modifier,
    icon: (@Composable (Color) -> Unit)? = null,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (selected) IrPalette.IrAmber.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = tween(160),
        label = "tabBg"
    )
    val borderColor = if (selected) IrPalette.IrAmber.copy(alpha = 0.85f) else Color.Transparent
    val contentColor = if (selected) IrPalette.IrAmber else IrPalette.TextSecondary

    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(0.8.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
        ) {
            icon?.let {
                Box(
                    modifier = Modifier.size(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    it(contentColor)
                }
            }
            Text(
                text = title,
                color = contentColor,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
        if (hasActiveIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 4.dp)
                    .size(4.5.dp)
                    .clip(CircleShape)
                    .background(IrPalette.IrAmber)
            )
        }
    }
}

/**
 * Profesyonel optik skala kaydırıcı:
 * - Çift çizgi karışıklığını tamamen ortadan kaldıran dairesel mekanik düğme
 * - Üstte ince ters üçgen varsayılan referans işareti + altta hassas cetvel çentikleri
 * - Sürükleme sırasında onDraggingChange ile Hayalet Mod desteği
 */
@Composable
internal fun ProGradientSliderRow(
    label: String,
    valueBadge: String,
    isModified: Boolean,
    value: Float,
    defaultValue: Float,
    snapTolerance: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    gradient: Brush,
    trailingHeaderContent: (@Composable () -> Unit)? = null,
    onDraggingChange: (Boolean) -> Unit = {},
    onStepMinus: () -> Unit,
    onStepPlus: () -> Unit,
    onReset: () -> Unit,
    onValueChange: (Float) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnDraggingChange by rememberUpdatedState(onDraggingChange)
    var wasSnapped by remember { mutableStateOf(abs(value - defaultValue) <= snapTolerance * 0.5f) }
    var isDragging by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            currentOnDraggingChange(false)
        }
    }

    val thumbRadiusDp by animateDpAsState(
        targetValue = if (isDragging) 10.5.dp else 8.5.dp,
        animationSpec = tween(120),
        label = "thumbRadius"
    )

    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = label,
                    color = IrPalette.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isModified) IrPalette.IrAmber.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f))
                        .border(
                            0.8.dp,
                            if (isModified) IrPalette.IrAmber.copy(alpha = 0.72f) else Color.Transparent,
                            RoundedCornerShape(7.dp)
                        )
                        .clickable(enabled = enabled, onClick = onReset)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = valueBadge,
                        color = if (isModified) IrPalette.IrAmber else IrPalette.TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    if (isModified) {
                        ResetIcon(
                            tint = IrPalette.IrAmber.copy(alpha = 0.85f),
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                trailingHeaderContent?.invoke()
                StepButton(symbol = "−", enabled = enabled, onClick = onStepMinus)
                StepButton(symbol = "+", enabled = enabled, onClick = onStepPlus)
            }
        }

        val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
        val clampedVal = value.coerceIn(valueRange.start, valueRange.endInclusive)
        val valueFraction = ((clampedVal - valueRange.start) / span).coerceIn(0f, 1f)
        val defaultFraction = ((defaultValue - valueRange.start) / span).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .semantics { contentDescription = "$label $valueBadge" }
                .pointerInput(enabled, valueRange, defaultValue, snapTolerance) {
                    if (!enabled) return@pointerInput
                    val padPx = 12.dp.toPx()
                    fun xToSnapped(x: Float): Float {
                        val usable = (size.width - padPx * 2f).coerceAtLeast(1f)
                        val frac = ((x - padPx) / usable).coerceIn(0f, 1f)
                        val raw = valueRange.start + frac * (valueRange.endInclusive - valueRange.start)
                        return if (abs(raw - defaultValue) <= snapTolerance) {
                            if (!wasSnapped) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                wasSnapped = true
                            }
                            defaultValue
                        } else {
                            wasSnapped = false
                            raw
                        }
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isDragging = true
                        currentOnDraggingChange(true)
                        try {
                            currentOnValueChange(xToSnapped(down.position.x))
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                currentOnValueChange(xToSnapped(change.position.x))
                                change.consume()
                            }
                        } finally {
                            isDragging = false
                            currentOnDraggingChange(false)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padX = 12.dp.toPx()
                val trackW = (size.width - padX * 2f).coerceAtLeast(1f)
                val trackY = size.height * 0.46f
                val trackH = 4.5.dp.toPx()
                val cornerR = trackH * 0.5f

                // 1) Spektrum arka plan rayı
                drawRoundRect(
                    brush = gradient,
                    topLeft = Offset(padX, trackY - cornerR),
                    size = androidx.compose.ui.geometry.Size(trackW, trackH),
                    cornerRadius = CornerRadius(cornerR, cornerR),
                    alpha = if (enabled) 0.94f else 0.35f
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.16f),
                    topLeft = Offset(padX, trackY - cornerR),
                    size = androidx.compose.ui.geometry.Size(trackW, trackH),
                    cornerRadius = CornerRadius(cornerR, cornerR),
                    style = Stroke(width = 0.7.dp.toPx())
                )

                // 2) Altta hassas cetvel çentikleri
                val tickCount = 16
                val tickTop = trackY + cornerR + 3.dp.toPx()
                for (i in 0..tickCount) {
                    val tx = padX + trackW * (i.toFloat() / tickCount)
                    val isMajor = i % 4 == 0
                    val tickLen = if (isMajor) 5.dp.toPx() else 2.8.dp.toPx()
                    drawLine(
                        color = Color.White.copy(alpha = if (isMajor) 0.38f else 0.18f),
                        start = Offset(tx, tickTop),
                        end = Offset(tx, tickTop + tickLen),
                        strokeWidth = if (isMajor) 1.1.dp.toPx() else 0.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 3) Üstte ince ters üçgen varsayılan değer işareti
                val defX = padX + trackW * defaultFraction
                val triTop = trackY - cornerR - 7.dp.toPx()
                val triBottom = trackY - cornerR - 2.dp.toPx()
                val triHalfW = 3.dp.toPx()
                val notchPath = Path().apply {
                    moveTo(defX - triHalfW, triTop)
                    lineTo(defX + triHalfW, triTop)
                    lineTo(defX, triBottom)
                    close()
                }
                drawPath(
                    path = notchPath,
                    color = IrPalette.TextSecondary.copy(alpha = 0.88f),
                    style = Fill
                )

                // 4) Varsayılan ile mevcut değer arası ince parlak vurgu çizgisi
                val thumbX = padX + trackW * valueFraction
                if (abs(thumbX - defX) > 2.dp.toPx()) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.90f),
                        start = Offset(defX, trackY),
                        end = Offset(thumbX, trackY),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 5) Mekanik dairesel optik düğme
                val r = thumbRadiusDp.toPx()
                if (isDragging) {
                    drawCircle(
                        color = IrPalette.IrAmber.copy(alpha = 0.24f),
                        radius = r + 4.dp.toPx(),
                        center = Offset(thumbX, trackY)
                    )
                }
                drawCircle(
                    color = IrPalette.HudGlass,
                    radius = r,
                    center = Offset(thumbX, trackY)
                )
                drawCircle(
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
                    radius = r,
                    center = Offset(thumbX, trackY),
                    style = Stroke(width = 2.2.dp.toPx())
                )
                if (isModified && enabled) {
                    drawCircle(
                        color = IrPalette.IrAmber,
                        radius = 2.8.dp.toPx(),
                        center = Offset(thumbX, trackY)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 32.dp, height = 28.dp)
            .clip(RoundedCornerShape(7.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 26.dp, height = 22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(0.8.dp, IrPalette.BorderSubtle, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symbol,
                color = IrPalette.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun QuickPillAction(
    text: String,
    highlighted: Boolean = false,
    compact: Boolean = false,
    enabled: Boolean,
    leadingIcon: (@Composable (Color) -> Unit)? = null,
    onClick: () -> Unit
) {
    val bg = if (highlighted) IrPalette.IrAmber.copy(alpha = 0.20f) else IrPalette.HudCard
    val border = if (highlighted) IrPalette.IrAmber else IrPalette.BorderSubtle
    val textColor = if (highlighted) IrPalette.IrAmber else IrPalette.TextPrimary

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(bg)
                .border(0.9.dp, border, RoundedCornerShape(50))
                .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            leadingIcon?.invoke(textColor)
            Text(
                text = text,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun ResolutionSelectorDialog(
    availableResolutions: List<Size>,
    selectedResolution: Size?,
    onSelect: (Size) -> Unit,
    onDismiss: () -> Unit
) {
    val grouped = remember(availableResolutions) {
        availableResolutions
            .sortedByDescending { it.width.toLong() * it.height.toLong() }
            .groupBy { formatAspectRatioTag(it) }
    }
    val preferredOrder = listOf("4:3", "16:9", "1:1", "20:9")
    val availableRatios = remember(grouped) {
        val ordered = preferredOrder.filter { grouped.containsKey(it) }
        val others = grouped.keys.filter { it !in preferredOrder }.sorted()
        ordered + others
    }

    val initialRatioTag = remember(selectedResolution, availableRatios) {
        selectedResolution?.let { formatAspectRatioTag(it) }
            ?.takeIf { it in availableRatios }
            ?: availableRatios.firstOrNull()
            ?: "4:3"
    }
    var selectedRatioTag by remember { mutableStateOf(initialRatioTag) }
    var showAdvanced by remember { mutableStateOf(false) }

    val ratioResolutions = grouped[selectedRatioTag].orEmpty()
    val (primaryList, advancedList) = remember(ratioResolutions) {
        val primary = mutableListOf<Size>()
        val advanced = mutableListOf<Size>()
        for (res in ratioResolutions) {
            val px = res.width.toLong() * res.height.toLong()
            val isNearDuplicate = primary.any { existing ->
                val exPx = existing.width.toLong() * existing.height.toLong()
                abs(exPx - px).toDouble() / maxOf(exPx, 1L).toDouble() < 0.08
            }
            if (isNearDuplicate) {
                advanced.add(res)
            } else {
                primary.add(res)
            }
        }
        primary to advanced
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = IrPalette.SurfaceElevated,
        titleContentColor = IrPalette.TextPrimary,
        textContentColor = IrPalette.TextSecondary,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.dialog_resolution_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.dialog_resolution_subtitle),
                    color = IrPalette.TextSecondary,
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(availableRatios) { ratioTag ->
                        val isSel = ratioTag == selectedRatioTag
                        Box(
                            modifier = Modifier
                                .sizeIn(minWidth = 56.dp, minHeight = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedRatioTag = ratioTag
                                    showAdvanced = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) IrPalette.IrAmber else Color.White.copy(alpha = 0.08f))
                                    .border(
                                        1.dp,
                                        if (isSel) IrPalette.IrAmber else IrPalette.BorderSubtle,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = ratioTag,
                                    color = if (isSel) Color.Black else IrPalette.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(primaryList, key = { "${it.width}x${it.height}" }) { res ->
                        val isSelected = selectedResolution?.let { it.width == res.width && it.height == res.height } == true
                        val isTopRecommended = res == primaryList.firstOrNull()
                        ResolutionItemRow(
                            res = res,
                            isSelected = isSelected,
                            isRecommended = isTopRecommended,
                            onClick = { onSelect(res) }
                        )
                    }

                    if (advancedList.isNotEmpty()) {
                        item(key = "advanced_toggle") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .sizeIn(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showAdvanced = !showAdvanced }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${stringResource(R.string.dialog_resolution_advanced)} (${advancedList.size})",
                                    color = IrPalette.TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                ChevronIcon(
                                    tint = IrPalette.TextSecondary,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .rotate(if (showAdvanced) 180f else 0f)
                                )
                            }
                        }
                        if (showAdvanced) {
                            items(advancedList, key = { "adv_${it.width}x${it.height}" }) { res ->
                                val isSelected = selectedResolution?.let { it.width == res.width && it.height == res.height } == true
                                ResolutionItemRow(
                                    res = res,
                                    isSelected = isSelected,
                                    isRecommended = false,
                                    onClick = { onSelect(res) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_close), color = IrPalette.IrAmber)
            }
        }
    )
}

@Composable
private fun ResolutionItemRow(
    res: Size,
    isSelected: Boolean,
    isRecommended: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) IrPalette.IrAmber.copy(alpha = 0.18f)
                else Color.White.copy(alpha = 0.05f)
            )
            .border(
                1.dp,
                if (isSelected) IrPalette.IrAmber else IrPalette.BorderSubtle,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = formatMegapixels(res),
                color = if (isSelected) IrPalette.IrAmber else IrPalette.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${res.width} × ${res.height}",
                color = IrPalette.TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isRecommended) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(IrPalette.IrEmerald.copy(alpha = 0.22f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dialog_resolution_recommended),
                        color = IrPalette.IrEmerald,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(IrPalette.IrAmber)
                )
            }
        }
    }
}

// --- Özel Keskin Vektör İkonları (Canvas) ---

@Composable
internal fun GridIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.4.dp.toPx()
        val x1 = size.width / 3f
        val x2 = size.width * 2f / 3f
        val y1 = size.height / 3f
        val y2 = size.height * 2f / 3f
        drawLine(tint, Offset(x1, 0f), Offset(x1, size.height), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(x2, 0f), Offset(x2, size.height), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(0f, y1), Offset(size.width, y1), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(0f, y2), Offset(size.width, y2), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
internal fun ChevronIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.35f)
            lineTo(size.width * 0.5f, size.height * 0.68f)
            lineTo(size.width * 0.8f, size.height * 0.35f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
internal fun TimerIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.5.dp.toPx()
        val cx = size.width * 0.5f
        val cy = size.height * 0.56f
        val r = size.minDimension * 0.38f
        drawCircle(tint, radius = r, center = Offset(cx, cy), style = Stroke(width = stroke))
        drawLine(tint, Offset(cx - r * 0.35f, size.height * 0.08f), Offset(cx + r * 0.35f, size.height * 0.08f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(cx, cy), Offset(cx + r * 0.45f, cy - r * 0.45f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
internal fun LevelIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.6.dp.toPx()
        val cy = size.height * 0.5f
        drawLine(tint, Offset(size.width * 0.08f, cy), Offset(size.width * 0.34f, cy), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.66f, cy), Offset(size.width * 0.92f, cy), stroke, cap = StrokeCap.Round)
        drawCircle(tint, radius = size.minDimension * 0.14f, center = Offset(size.width * 0.5f, cy), style = Stroke(width = stroke))
    }
}

@Composable
internal fun ShieldIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.5.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.08f)
            lineTo(size.width * 0.86f, size.height * 0.24f)
            lineTo(size.width * 0.86f, size.height * 0.52f)
            quadraticBezierTo(size.width * 0.86f, size.height * 0.80f, size.width * 0.5f, size.height * 0.94f)
            quadraticBezierTo(size.width * 0.14f, size.height * 0.80f, size.width * 0.14f, size.height * 0.52f)
            lineTo(size.width * 0.14f, size.height * 0.24f)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = stroke, join = StrokeJoin.Round))
    }
}

@Composable
internal fun ZebraIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.4.dp.toPx()
        drawRoundRect(
            color = tint,
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawLine(tint, Offset(size.width * 0.18f, size.height * 0.55f), Offset(size.width * 0.55f, size.height * 0.18f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.22f, size.height * 0.84f), Offset(size.width * 0.84f, size.height * 0.22f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.52f, size.height * 0.84f), Offset(size.width * 0.84f, size.height * 0.52f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
internal fun TargetCrosshairIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.5.dp.toPx()
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val r = size.minDimension * 0.34f
        drawCircle(tint, radius = r, center = Offset(cx, cy), style = Stroke(width = stroke))
        drawLine(tint, Offset(cx, size.height * 0.04f), Offset(cx, cy - r * 0.6f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(cx, cy + r * 0.6f), Offset(cx, size.height * 0.96f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.04f, cy), Offset(cx - r * 0.6f, cy), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(cx + r * 0.6f, cy), Offset(size.width * 0.96f, cy), stroke, cap = StrokeCap.Round)
    }
}

@Composable
internal fun PipetteIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.5.dp.toPx()
        drawLine(tint, Offset(size.width * 0.22f, size.height * 0.78f), Offset(size.width * 0.70f, size.height * 0.30f), stroke, cap = StrokeCap.Round)
        drawCircle(tint, radius = size.minDimension * 0.16f, center = Offset(size.width * 0.76f, size.height * 0.24f), style = Stroke(width = stroke))
        drawLine(tint, Offset(size.width * 0.52f, size.height * 0.24f), Offset(size.width * 0.76f, size.height * 0.48f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
internal fun SparkleAutoIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val path = Path().apply {
            moveTo(cx, size.height * 0.08f)
            quadraticBezierTo(cx, cy, size.width * 0.92f, cy)
            quadraticBezierTo(cx, cy, cx, size.height * 0.92f)
            quadraticBezierTo(cx, cy, size.width * 0.08f, cy)
            quadraticBezierTo(cx, cy, cx, size.height * 0.08f)
            close()
        }
        drawPath(path, color = tint, style = Fill)
    }
}

@Composable
internal fun ResetIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.6.dp.toPx()
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val r = size.minDimension * 0.34f
        drawArc(
            color = tint,
            startAngle = -140f,
            sweepAngle = 285f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        val tipRad = Math.toRadians(-140.0)
        val tipX = cx + (cos(tipRad) * r).toFloat()
        val tipY = cy + (sin(tipRad) * r).toFloat()
        val barb = r * 0.48f
        val a1 = Math.toRadians(-8.0)
        val a2 = Math.toRadians(-98.0)
        val arrow = Path().apply {
            moveTo(tipX + (cos(a1) * barb).toFloat(), tipY + (sin(a1) * barb).toFloat())
            lineTo(tipX, tipY)
            lineTo(tipX + (cos(a2) * barb).toFloat(), tipY + (sin(a2) * barb).toFloat())
        }
        drawPath(
            path = arrow,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
internal fun GalleryIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.5.dp.toPx()
        drawRoundRect(
            color = tint,
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = stroke)
        )
        val mountain = Path().apply {
            moveTo(size.width * 0.14f, size.height * 0.78f)
            lineTo(size.width * 0.42f, size.height * 0.46f)
            lineTo(size.width * 0.60f, size.height * 0.66f)
            lineTo(size.width * 0.74f, size.height * 0.52f)
            lineTo(size.width * 0.88f, size.height * 0.78f)
        }
        drawPath(mountain, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(tint, radius = size.minDimension * 0.09f, center = Offset(size.width * 0.30f, size.height * 0.30f))
    }
}

@Composable
internal fun PresetStarIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.5.dp.toPx()
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val outer = size.minDimension * 0.44f
        val inner = outer * 0.44f
        val path = Path()
        for (i in 0 until 10) {
            val angle = Math.toRadians((i * 36.0) - 90.0)
            val r = if (i % 2 == 0) outer else inner
            val x = cx + (cos(angle) * r).toFloat()
            val y = cy + (sin(angle) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color = tint, style = Stroke(width = stroke, join = StrokeJoin.Round))
    }
}

@Composable
internal fun WhiteBalanceIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.4.dp.toPx()
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val r = size.minDimension * 0.24f
        drawCircle(tint, radius = r, center = Offset(cx, cy), style = Stroke(width = stroke))
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            val x0 = cx + (cos(angle) * (r + size.minDimension * 0.11f)).toFloat()
            val y0 = cy + (sin(angle) * (r + size.minDimension * 0.11f)).toFloat()
            val x1 = cx + (cos(angle) * (size.minDimension * 0.46f)).toFloat()
            val y1 = cy + (sin(angle) * (size.minDimension * 0.46f)).toFloat()
            drawLine(tint, Offset(x0, y0), Offset(x1, y1), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

@Composable
internal fun ExposureIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.4.dp.toPx()
        drawRoundRect(
            color = tint,
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawLine(tint, Offset(size.width * 0.22f, size.height * 0.78f), Offset(size.width * 0.78f, size.height * 0.22f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.22f, size.height * 0.32f), Offset(size.width * 0.44f, size.height * 0.32f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.33f, size.height * 0.21f), Offset(size.width * 0.33f, size.height * 0.43f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.56f, size.height * 0.70f), Offset(size.width * 0.78f, size.height * 0.70f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
internal fun ColorWheelIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.4.dp.toPx()
        val r = size.minDimension * 0.26f
        drawCircle(tint, radius = r, center = Offset(size.width * 0.50f, size.height * 0.34f), style = Stroke(width = stroke))
        drawCircle(tint, radius = r, center = Offset(size.width * 0.34f, size.height * 0.64f), style = Stroke(width = stroke))
        drawCircle(tint, radius = r, center = Offset(size.width * 0.66f, size.height * 0.64f), style = Stroke(width = stroke))
    }
}

@Composable
internal fun MeteringFrameIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.5.dp.toPx()
        val m = size.minDimension * 0.12f
        val c = size.minDimension * 0.26f
        val r = size.width - m
        val b = size.height - m
        drawLine(tint, Offset(m, m), Offset(m + c, m), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(m, m), Offset(m, m + c), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(r, m), Offset(r - c, m), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(r, m), Offset(r, m + c), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(m, b), Offset(m + c, b), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(m, b), Offset(m, b - c), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(r, b), Offset(r - c, b), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(r, b), Offset(r, b - c), stroke, cap = StrokeCap.Round)
        drawCircle(tint, radius = size.minDimension * 0.11f, center = Offset(size.width * 0.5f, size.height * 0.5f))
    }
}

@Composable
internal fun CompareEyeIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.4.dp.toPx()
        val cy = size.height * 0.5f
        val eye = Path().apply {
            moveTo(size.width * 0.08f, cy)
            quadraticBezierTo(size.width * 0.5f, size.height * 0.10f, size.width * 0.92f, cy)
            quadraticBezierTo(size.width * 0.5f, size.height * 0.90f, size.width * 0.08f, cy)
            close()
        }
        drawPath(eye, color = tint, style = Stroke(width = stroke, join = StrokeJoin.Round))
        drawCircle(tint, radius = size.minDimension * 0.15f, center = Offset(size.width * 0.5f, cy))
    }
}

@Composable
internal fun FlashIcon(mode: FlashMode, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.4.dp.toPx()
        val bolt = Path().apply {
            moveTo(size.width * 0.58f, size.height * 0.06f)
            lineTo(size.width * 0.22f, size.height * 0.54f)
            lineTo(size.width * 0.50f, size.height * 0.54f)
            lineTo(size.width * 0.38f, size.height * 0.94f)
            lineTo(size.width * 0.78f, size.height * 0.44f)
            lineTo(size.width * 0.50f, size.height * 0.44f)
            close()
        }
        if (mode == FlashMode.ON || mode == FlashMode.TORCH) {
            drawPath(bolt, color = tint, style = Fill)
        } else {
            drawPath(bolt, color = tint, style = Stroke(width = stroke, join = StrokeJoin.Round))
        }
        if (mode == FlashMode.OFF) {
            drawLine(
                color = tint,
                start = Offset(size.width * 0.14f, size.height * 0.14f),
                end = Offset(size.width * 0.86f, size.height * 0.86f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        } else if (mode == FlashMode.AUTO) {
            drawCircle(tint, radius = size.minDimension * 0.10f, center = Offset(size.width * 0.82f, size.height * 0.82f))
        }
    }
}

@Composable
internal fun FlipCameraIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.65.dp.toPx()
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val r = size.minDimension * 0.34f
        val arcTopLeft = Offset(cx - r, cy - r)
        val arcSize = androidx.compose.ui.geometry.Size(r * 2f, r * 2f)

        drawArc(
            color = tint,
            startAngle = 205f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = tint,
            startAngle = 25f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        fun drawArrowHead(endAngleDeg: Double) {
            val endRad = Math.toRadians(endAngleDeg)
            val tx = cx + r * cos(endRad).toFloat()
            val ty = cy + r * sin(endRad).toFloat()
            val wingLen = r * 0.48f
            val backDeg = endAngleDeg - 90.0
            val w1Rad = Math.toRadians(backDeg - 38.0)
            val w2Rad = Math.toRadians(backDeg + 38.0)
            val arrowPath = Path().apply {
                moveTo(tx + wingLen * cos(w1Rad).toFloat(), ty + wingLen * sin(w1Rad).toFloat())
                lineTo(tx, ty)
                lineTo(tx + wingLen * cos(w2Rad).toFloat(), ty + wingLen * sin(w2Rad).toFloat())
            }
            drawPath(
                path = arrowPath,
                color = tint,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        drawArrowHead(335.0)
        drawArrowHead(155.0)
    }
}

@Composable
internal fun SwapChannelIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.4.dp.toPx()
        drawLine(tint, Offset(size.width * 0.14f, size.height * 0.32f), Offset(size.width * 0.84f, size.height * 0.32f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.66f, size.height * 0.16f), Offset(size.width * 0.84f, size.height * 0.32f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.66f, size.height * 0.48f), Offset(size.width * 0.84f, size.height * 0.32f), stroke, cap = StrokeCap.Round)

        drawLine(tint, Offset(size.width * 0.86f, size.height * 0.68f), Offset(size.width * 0.16f, size.height * 0.68f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.34f, size.height * 0.52f), Offset(size.width * 0.16f, size.height * 0.68f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.34f, size.height * 0.84f), Offset(size.width * 0.16f, size.height * 0.68f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
internal fun ShareIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.4.dp.toPx()
        val r = size.minDimension * 0.13f
        val pLeft = Offset(size.width * 0.22f, size.height * 0.50f)
        val pTop = Offset(size.width * 0.76f, size.height * 0.20f)
        val pBot = Offset(size.width * 0.76f, size.height * 0.80f)
        drawLine(tint, pLeft, pTop, stroke, cap = StrokeCap.Round)
        drawLine(tint, pLeft, pBot, stroke, cap = StrokeCap.Round)
        drawCircle(tint, radius = r, center = pLeft)
        drawCircle(tint, radius = r, center = pTop)
        drawCircle(tint, radius = r, center = pBot)
    }
}

@Composable
internal fun SaveIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.5.dp.toPx()
        val cx = size.width * 0.50f
        drawLine(tint, Offset(cx, size.height * 0.12f), Offset(cx, size.height * 0.66f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.28f, size.height * 0.46f), Offset(cx, size.height * 0.68f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.72f, size.height * 0.46f), Offset(cx, size.height * 0.68f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.16f, size.height * 0.86f), Offset(size.width * 0.84f, size.height * 0.86f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
internal fun SettingsGearIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.45.dp.toPx()
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val innerR = size.minDimension * 0.19f
        val outerR = size.minDimension * 0.36f
        val toothOuter = size.minDimension * 0.47f
        drawCircle(tint, radius = innerR, center = Offset(cx, cy), style = Stroke(width = stroke))
        drawCircle(tint, radius = outerR, center = Offset(cx, cy), style = Stroke(width = stroke))
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            val x0 = cx + (cos(angle) * outerR).toFloat()
            val y0 = cy + (sin(angle) * outerR).toFloat()
            val x1 = cx + (cos(angle) * toothOuter).toFloat()
            val y1 = cy + (sin(angle) * toothOuter).toFloat()
            drawLine(tint, Offset(x0, y0), Offset(x1, y1), strokeWidth = stroke * 1.15f, cap = StrokeCap.Round)
        }
    }
}

@Composable
internal fun LockIcon(locked: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.4.dp.toPx()
        val bodyTop = size.height * 0.46f
        val bodyH = size.height * 0.46f
        val bodyW = size.width * 0.68f
        val bodyLeft = (size.width - bodyW) * 0.5f
        drawRoundRect(
            color = tint,
            topLeft = Offset(bodyLeft, bodyTop),
            size = androidx.compose.ui.geometry.Size(bodyW, bodyH),
            cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
            style = Stroke(width = stroke)
        )
        val archW = size.width * 0.40f
        val archLeft = (size.width - archW) * 0.5f
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = if (locked) 180f else 135f,
            useCenter = false,
            topLeft = Offset(archLeft, size.height * 0.14f),
            size = androidx.compose.ui.geometry.Size(archW, size.height * 0.56f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        if (locked) {
            drawCircle(tint, radius = size.minDimension * 0.08f, center = Offset(size.width * 0.5f, bodyTop + bodyH * 0.5f))
        }
    }
}

internal fun formatSigned(value: Float): String {
    val intVal = value.roundToInt()
    return when {
        intVal > 0 -> "+$intVal"
        intVal < 0 -> "−${abs(intVal)}"
        else -> "0"
    }
}

internal fun formatSignedDecimal(value: Float, locale: Locale): String {
    val formatted = String.format(locale, "%.1f", abs(value))
    return when {
        value > 0.05f -> "+$formatted"
        value < -0.05f -> "−$formatted"
        else -> String.format(locale, "%.1f", 0f)
    }
}
