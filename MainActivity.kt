@file:Suppress("SpellCheckingInspection")

package com.example.kameras

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

private object IrPalette {
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

class MainActivity : ComponentActivity() {
    private var hasCameraPermission by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val camGranted = results[Manifest.permission.CAMERA]
                ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            hasCameraPermission = camGranted
            if (!camGranted) {
                Toast.makeText(this, "Kamera izni gerekli", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        hasCameraPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        val missingPermissions = mutableListOf<String>()
        if (!hasCameraPermission) {
            missingPermissions.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.M..Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CameraScreen(hasCameraPermission = hasCameraPermission)
            }
        }
    }
}

// Ana kontrol çipleri: Beyaz Dengesi, Pozlama, Renk, Ölçüm
private enum class ControlTab { BD, POZ, RENK, OLCUM }

// Beyaz dengesi alt tekil kaydırıcı seçimi (önizleme yüksekliğini korur)
private enum class WbSubSlider { KELVIN, TINT }

// Renk çipi üst modları: Genel (Doygunluk/Kontrast), Renk Bantları (Histogram Mikseri), Pipet
private enum class ColorPanelMode { GENEL, BANTLAR, PIPET }

// Renk çipi alt tekil kaydırıcı seçimi
private enum class ColorSubSlider { DOYGUNLUK, KONTRAST }

// Noktasal renk pipeti ve renk bantları alt tekil kaydırıcı seçimi
private enum class PointColorSubSlider { TON, DOYGUNLUK, PARLAKLIK }

// Lightroom / Darkroom tarzı 8 profesyonel renk bandı
private enum class ColorBand(
    val index: Int,
    val label: String,
    val centerHue: Float,
    val swatchColor: Color
) {
    RED(0, "Kırmızı", 0f, Color(0xFFEF4444)),
    ORANGE(1, "Turuncu", 30f, Color(0xFFF97316)),
    YELLOW(2, "Sarı", 60f, Color(0xFFEAB308)),
    GREEN(3, "Yeşil", 120f, Color(0xFF22C55E)),
    CYAN(4, "Camgöbeği", 180f, Color(0xFF06B6D4)),
    BLUE(5, "Mavi", 225f, Color(0xFF3B82F6)),
    PURPLE(6, "Mor", 275f, Color(0xFF8B5CF6)),
    MAGENTA(7, "Macenta", 320f, Color(0xFFEC4899))
}

private data class BandHslAdjustment(
    val deltaHue: Float = 0f,
    val deltaSat: Float = 0f,
    val deltaLum: Float = 0f
) {
    val isModified: Boolean
        get() = abs(deltaHue) > 0.5f || abs(deltaSat) > 0.01f || abs(deltaLum) > 0.01f
}

private data class ColorBandsConfig(
    val bands: List<BandHslAdjustment> = List(8) { BandHslAdjustment() }
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
}

private class ProHistogramData(
    val r: FloatArray = FloatArray(48),
    val g: FloatArray = FloatArray(48),
    val b: FloatArray = FloatArray(48),
    val luma: FloatArray = FloatArray(48),
    val hueBins: FloatArray = FloatArray(48),
    val shadowClip: Boolean = false,
    val highlightClip: Boolean = false
)

// Ölçüm kutusu boyutu için tek seçimli (radio) değerler
private enum class MeteringBoxSize(val norm: Float) {
    KUCUK(0.18f),
    ORTA(0.32f),
    BUYUK(0.55f)
}

private enum class ExposureMeteringMode { FULL_SCREEN, BOX_SPOT }

private class CameraHolder {
    var activeCameraId: String? = null
    var device: CameraDevice? = null
    var session: CameraCaptureSession? = null
    var previewBuilder: CaptureRequest.Builder? = null
    var imageReader: ImageReader? = null
    var activeSurface: Surface? = null
    var isOpening: Boolean = false
    var isDisposed: Boolean = false

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

data class CameraPreset(
    val name: String,
    val kelvin: Float,
    val tint: Float,
    val contrast: Float = DEFAULT_CONTRAST,
    val saturation: Float = DEFAULT_SATURATION
)

private const val KELVIN_MIN = 1f
private const val KELVIN_MAX = 10000f
private const val DEFAULT_CONTRAST = 1.0f
private const val DEFAULT_SATURATION = 1.0f

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

    val rRawInverse = rGain / rAvg.coerceAtLeast(1f)
    val bRawInverse = bGain / bAvg.coerceAtLeast(1f)
    val gRawInverse = gGain / gAvg.coerceAtLeast(1f)

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

private fun getHueName(hue: Float): String {
    val h = (hue % 360f + 360f) % 360f
    return when (h) {
        in 0f..15f -> "Kırmızı"
        in 16f..45f -> "Turuncu / Amber"
        in 46f..70f -> "Sarı / Yaprak"
        in 71f..150f -> "Yeşil"
        in 151f..195f -> "Camgöbeği (Cyan)"
        in 196f..260f -> "Mavi / Gökyüzü"
        in 261f..315f -> "Mor / Menekşe"
        in 316f..345f -> "Macenta / Pembe"
        else -> "Kırmızı"
    }
}

/**
 * Lightroom / Darkroom tarzı 8-Bantlı Renk Mikseri + Noktasal Renk Pipeti işlemcisi.
 * Tam 360° ton dönüşümlerinde kenar halesi (rainbow halo) oluşturmamak için RGB uzayında yumuşak karıştırma uygular.
 */
private fun applyTargetedHslToBitmap(
    bitmap: Bitmap,
    config: TargetedColorConfig,
    colorBands: ColorBandsConfig = ColorBandsConfig()
): Bitmap {
    val hasPoint = config.isActive && (config.deltaHue != 0f || config.deltaSat != 0f || config.deltaLum != 0f)
    val hasBands = colorBands.hasAnyModification
    if (!hasPoint && !hasBands) {
        return bitmap
    }
    val work = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val w = work.width
    val h = work.height
    val stripHeight = 256.coerceAtMost(h)
    val rowBuf = IntArray(w * stripHeight)

    val targetHue = config.targetHue
    val hueWidth = config.hueWidth.coerceAtLeast(4f)
    val hueFeather = config.hueFeather.coerceAtLeast(4f)
    val dH = config.deltaHue
    val dS = config.deltaSat
    val dL = config.deltaLum

    val bandCenters = floatArrayOf(0f, 30f, 60f, 120f, 180f, 225f, 275f, 320f)
    val bandDH = FloatArray(8) { colorBands.bands.getOrNull(it)?.deltaHue ?: 0f }
    val bandDS = FloatArray(8) { colorBands.bands.getOrNull(it)?.deltaSat ?: 0f }
    val bandDL = FloatArray(8) { colorBands.bands.getOrNull(it)?.deltaLum ?: 0f }
    val bandActive = BooleanArray(8) { colorBands.bands.getOrNull(it)?.isModified == true }

    val hsv = FloatArray(3)
    var y = 0
    while (y < h) {
        val rows = minOf(stripHeight, h - y)
        work.getPixels(rowBuf, 0, w, 0, y, w, rows)
        val count = w * rows
        for (i in 0 until count) {
            val px = rowBuf[i]
            val a = px and -0x1000000
            var r = (px ushr 16) and 0xFF
            var g = (px ushr 8) and 0xFF
            var b = px and 0xFF

            // 1) 8-Bantlı Renk Histogramı Mikseri (pürüzsüz komşu bant geçişi)
            if (hasBands) {
                AndroidColor.RGBToHSV(r, g, b, hsv)
                val hVal = hsv[0]
                val sVal = hsv[1]
                val vVal = hsv[2]

                val satW = ((sVal - 0.05f) / 0.14f).coerceIn(0f, 1f)
                val wS = satW * satW * (3f - 2f * satW)
                val lumLow = ((vVal - 0.04f) / 0.10f).coerceIn(0f, 1f)
                val lumHigh = 1.0f - ((vVal - 0.93f) / 0.06f).coerceIn(0f, 1f)
                val mask = wS * lumLow * lumHigh

                if (mask > 0.001f) {
                    var idxA = 7
                    var idxB = 0
                    var t = 0f
                    if (hVal >= 320f) {
                        idxA = 7
                        idxB = 0
                        t = ((hVal - 320f) / 40f).coerceIn(0f, 1f)
                    } else {
                        for (k in 0 until 7) {
                            if (hVal >= bandCenters[k] && hVal < bandCenters[k + 1]) {
                                idxA = k
                                idxB = k + 1
                                t = ((hVal - bandCenters[k]) / (bandCenters[k + 1] - bandCenters[k])).coerceIn(0f, 1f)
                                break
                            }
                        }
                    }
                    val s = t * t * (3f - 2f * t)
                    val wA = if (bandActive[idxA]) (1f - s) * mask else 0f
                    val wB = if (bandActive[idxB]) s * mask else 0f
                    val wTotal = (wA + wB).coerceIn(0f, 1f)
                    if (wTotal > 0.001f) {
                        var accR = r * (1f - wTotal)
                        var accG = g * (1f - wTotal)
                        var accB = b * (1f - wTotal)
                        if (wA > 0.001f) {
                            var nh = (hVal + bandDH[idxA]) % 360f
                            if (nh < 0f) nh += 360f
                            hsv[0] = nh
                            hsv[1] = (sVal * (1f + bandDS[idxA])).coerceIn(0f, 1f)
                            hsv[2] = (vVal + bandDL[idxA] * 0.45f).coerceIn(0f, 1f)
                            val cA = AndroidColor.HSVToColor(a, hsv)
                            accR += ((cA ushr 16) and 0xFF) * wA
                            accG += ((cA ushr 8) and 0xFF) * wA
                            accB += (cA and 0xFF) * wA
                        }
                        if (wB > 0.001f) {
                            var nh = (hVal + bandDH[idxB]) % 360f
                            if (nh < 0f) nh += 360f
                            hsv[0] = nh
                            hsv[1] = (sVal * (1f + bandDS[idxB])).coerceIn(0f, 1f)
                            hsv[2] = (vVal + bandDL[idxB] * 0.45f).coerceIn(0f, 1f)
                            val cB = AndroidColor.HSVToColor(a, hsv)
                            accR += ((cB ushr 16) and 0xFF) * wB
                            accG += ((cB ushr 8) and 0xFF) * wB
                            accB += (cB and 0xFF) * wB
                        }
                        r = accR.roundToInt().coerceIn(0, 255)
                        g = accG.roundToInt().coerceIn(0, 255)
                        b = accB.roundToInt().coerceIn(0, 255)
                    }
                }
            }

            // 2) Noktasal Renk Pipeti (isteğe bağlı ek katman)
            if (hasPoint) {
                AndroidColor.RGBToHSV(r, g, b, hsv)
                val hVal = hsv[0]
                val sVal = hsv[1]
                val vVal = hsv[2]

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
                    val lumLow = ((vVal - 0.04f) / 0.10f).coerceIn(0f, 1f)
                    val lumHigh = 1.0f - ((vVal - 0.92f) / 0.07f).coerceIn(0f, 1f)
                    val wL = lumLow * lumHigh

                    val weight = wH * wS * wL
                    if (weight > 0.001f) {
                        var newH = (hVal + dH) % 360f
                        if (newH < 0f) newH += 360f
                        val newS = (sVal * (1.0f + dS)).coerceIn(0f, 1f)
                        val newV = (vVal + dL * 0.45f).coerceIn(0f, 1f)

                        hsv[0] = newH
                        hsv[1] = newS
                        hsv[2] = newV
                        val shiftedRgb = AndroidColor.HSVToColor(a, hsv)
                        val sr = (shiftedRgb ushr 16) and 0xFF
                        val sg = (shiftedRgb ushr 8) and 0xFF
                        val sb = shiftedRgb and 0xFF
                        r = (r + (sr - r) * weight).roundToInt().coerceIn(0, 255)
                        g = (g + (sg - g) * weight).roundToInt().coerceIn(0, 255)
                        b = (b + (sb - b) * weight).roundToInt().coerceIn(0, 255)
                    }
                }
            }

            rowBuf[i] = a or (r shl 16) or (g shl 8) or b
        }
        work.setPixels(rowBuf, 0, w, 0, y, w, rows)
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
    uniform vec4 uBandSatA;
    uniform vec4 uBandSatB;
    uniform vec4 uBandLumA;
    uniform vec4 uBandLumB;

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
        return vec3(uBandHueB.w, uBandSatB.w, uBandLumB.w);
    }

    vec4 main(vec2 fragCoord) {
        vec4 color = uContent.eval(fragCoord);

        // 1) 8-Bantlı Renk Histogramı Mikseri (Lightroom / Darkroom)
        if (uBandsActive > 0.5) {
            vec3 hsl = rgb2hsl(color.rgb);
            float satClamp = clamp((hsl.y - 0.05) / 0.14, 0.0, 1.0);
            float wS = satClamp * satClamp * (3.0 - 2.0 * satClamp);
            float lumLow = clamp((hsl.z - 0.04) / 0.10, 0.0, 1.0);
            float lumHigh = 1.0 - clamp((hsl.z - 0.93) / 0.06, 0.0, 1.0);
            float mask = wS * lumLow * lumHigh;

            if (mask > 0.001) {
                float h = hsl.x;
                int idxA = 7;
                int idxB = 0;
                float t = 0.0;
                if (h < 30.0) {
                    idxA = 0; idxB = 1; t = h / 30.0;
                } else if (h < 60.0) {
                    idxA = 1; idxB = 2; t = (h - 30.0) / 30.0;
                } else if (h < 120.0) {
                    idxA = 2; idxB = 3; t = (h - 60.0) / 60.0;
                } else if (h < 180.0) {
                    idxA = 3; idxB = 4; t = (h - 120.0) / 60.0;
                } else if (h < 225.0) {
                    idxA = 4; idxB = 5; t = (h - 180.0) / 45.0;
                } else if (h < 275.0) {
                    idxA = 5; idxB = 6; t = (h - 225.0) / 50.0;
                } else if (h < 320.0) {
                    idxA = 6; idxB = 7; t = (h - 275.0) / 45.0;
                } else {
                    idxA = 7; idxB = 0; t = (h - 320.0) / 40.0;
                }
                float s = t * t * (3.0 - 2.0 * t);
                vec3 dA = getBandDelta(idxA);
                vec3 dB = getBandDelta(idxB);
                bool actA = (abs(dA.x) > 0.5 || abs(dA.y) > 0.01 || abs(dA.z) > 0.01);
                bool actB = (abs(dB.x) > 0.5 || abs(dB.y) > 0.01 || abs(dB.z) > 0.01);
                float wA = actA ? (1.0 - s) * mask : 0.0;
                float wB = actB ? s * mask : 0.0;
                float wTot = clamp(wA + wB, 0.0, 1.0);
                if (wTot > 0.001) {
                    vec3 acc = color.rgb * (1.0 - wTot);
                    if (wA > 0.001) {
                        vec3 shA = vec3(
                            mod(hsl.x + dA.x + 360.0, 360.0),
                            clamp(hsl.y * (1.0 + dA.y), 0.0, 1.0),
                            clamp(hsl.z + dA.z * 0.45, 0.0, 1.0)
                        );
                        acc += hsl2rgb(shA) * wA;
                    }
                    if (wB > 0.001) {
                        vec3 shB = vec3(
                            mod(hsl.x + dB.x + 360.0, 360.0),
                            clamp(hsl.y * (1.0 + dB.y), 0.0, 1.0),
                            clamp(hsl.z + dB.z * 0.45, 0.0, 1.0)
                        );
                        acc += hsl2rgb(shB) * wB;
                    }
                    color.rgb = acc;
                }
            }
        }

        // 2) Noktasal Renk Pipeti katmanı
        if (uActive > 0.5) {
            vec3 hsl = rgb2hsl(color.rgb);
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
                vec3 shifted = hsl;
                shifted.x = mod(hsl.x + uDeltaHue + 360.0, 360.0);
                shifted.y = clamp(hsl.y * (1.0 + uDeltaSat), 0.0, 1.0);
                shifted.z = clamp(hsl.z + uDeltaLum * 0.45, 0.0, 1.0);
                color.rgb = mix(color.rgb, hsl2rgb(shifted), weight);
            }
        }
        return color;
    }
"""

private fun updateTextureViewShader(
    view: TextureView?,
    config: TargetedColorConfig,
    colorBands: ColorBandsConfig,
    bypass: Boolean = false
) {
    if (view == null) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
            val hasPoint = config.isActive && (config.deltaHue != 0f || config.deltaSat != 0f || config.deltaLum != 0f)
            val hasBands = colorBands.hasAnyModification
            if (bypass || (!hasPoint && !hasBands)) {
                view.setRenderEffect(null)
                return
            }
            val shader = RuntimeShader(HSL_SKSL)
            shader.setFloatUniform("uTargetHue", config.targetHue)
            shader.setFloatUniform("uHueWidth", config.hueWidth)
            shader.setFloatUniform("uHueFeather", config.hueFeather)
            shader.setFloatUniform("uDeltaHue", config.deltaHue)
            shader.setFloatUniform("uDeltaSat", config.deltaSat)
            shader.setFloatUniform("uDeltaLum", config.deltaLum)
            shader.setFloatUniform("uActive", if (hasPoint) 1.0f else 0.0f)
            shader.setFloatUniform("uBandsActive", if (hasBands) 1.0f else 0.0f)
            val b = colorBands.bands
            shader.setFloatUniform("uBandHueA", b[0].deltaHue, b[1].deltaHue, b[2].deltaHue, b[3].deltaHue)
            shader.setFloatUniform("uBandHueB", b[4].deltaHue, b[5].deltaHue, b[6].deltaHue, b[7].deltaHue)
            shader.setFloatUniform("uBandSatA", b[0].deltaSat, b[1].deltaSat, b[2].deltaSat, b[3].deltaSat)
            shader.setFloatUniform("uBandSatB", b[4].deltaSat, b[5].deltaSat, b[6].deltaSat, b[7].deltaSat)
            shader.setFloatUniform("uBandLumA", b[0].deltaLum, b[1].deltaLum, b[2].deltaLum, b[3].deltaLum)
            shader.setFloatUniform("uBandLumB", b[4].deltaLum, b[5].deltaLum, b[6].deltaLum, b[7].deltaLum)
            val effect = RenderEffect.createRuntimeShaderEffect(shader, "uContent")
            view.setRenderEffect(effect)
        } catch (e: Throwable) {
            Log.e("CameraScreen", "RenderEffect not supported on this GPU/HAL", e)
        }
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

// 8 renk bandı sınırları (Kırmızı 340°..15°, Turuncu 15°..45°, Sarı 45°..90°, Yeşil 90°..150°,
// Camgöbeği 150°..202.5°, Mavi 202.5°..250°, Mor 250°..297.5°, Macenta 297.5°..340°)
private val HUE_BAND_EDGES = floatArrayOf(0f, 35f, 65f, 110f, 170f, 222.5f, 270f, 317.5f, 360f)

private fun hueToPerceptualBandPos(hueDeg: Float): Float {
    // 340° başlangıç olacak şekilde döndür (böylece Kırmızı 0° tam 0. bandın ortasına düşer)
    val shifted = ((hueDeg - 340f) % 360f + 360f) % 360f
    for (k in 0 until 8) {
        if (shifted < HUE_BAND_EDGES[k + 1] || k == 7) {
            val span = (HUE_BAND_EDGES[k + 1] - HUE_BAND_EDGES[k]).coerceAtLeast(1f)
            val frac = ((shifted - HUE_BAND_EDGES[k]) / span).coerceIn(0f, 1f)
            return (k + frac).coerceIn(0f, 7.9999f)
        }
    }
    return 0f
}

private fun perceptualBandPosToHue(pos: Float): Float {
    val clamped = pos.coerceIn(0f, 7.9999f)
    val k = clamped.toInt().coerceIn(0, 7)
    val frac = clamped - k
    val shifted = HUE_BAND_EDGES[k] + frac * (HUE_BAND_EDGES[k + 1] - HUE_BAND_EDGES[k])
    return ((shifted + 340f) % 360f + 360f) % 360f
}

// Önizleme görüntüsünden 48 sütunlu profesyonel RGB + Parlaklık + Algısal Bant Hizalı Renk Tonu (Hue) histogramı üretir
private fun computeProHistogramData(bitmap: Bitmap, bins: Int = 48): ProHistogramData {
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

            // Doygunluk ağırlıklı renk tonu (Hue) dağılımı — 8 bant sütunuyla 1:1 hizalı
            AndroidColor.RGBToHSV(r, g, b, hsv)
            val sat = hsv[1]
            val lum = hsv[2]
            if (sat > 0.10f && lum > 0.06f && lum < 0.96f) {
                val bandPos = hueToPerceptualBandPos(hsv[0])
                val hueBin = ((bandPos / 8f) * bins).toInt().coerceIn(0, bins - 1)
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
private fun computeZebraGrid(bitmap: Bitmap, cols: Int = 32, rows: Int = 32, threshold: Float = 240f): BooleanArray {
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
        return AutoCalibrateResult(3348f, 13f, avgLuma, neutCount)
    }

    val (estK, estT) = inverseRgbToKelvinTint(
        rAvg = (rSum / neutCount).toFloat(),
        gAvg = (gSum / neutCount).toFloat(),
        bAvg = (bSum / neutCount).toFloat(),
        currentGains = currentGains
    )
    return AutoCalibrateResult(
        kelvin = estK.coerceIn(1600f, 7500f),
        tint = estT.coerceIn(-60f, 60f),
        avgLuma = avgLuma,
        foundNeutralCount = neutCount
    )
}

private fun readExifOrientation(bytes: ByteArray, cacheDir: File): Int {
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

private fun saveOutputToGallery(
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
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_$timestamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Kameras")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return false

        if (processedBitmap != null) {
            tempOut = File.createTempFile("kameras_out_", ".jpg", context.cacheDir)
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
                } catch (_: Exception) {
                }
            }
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(tempOut).use { inp ->
                    inp.copyTo(out)
                }
                out.flush()
            }
        } else if (rawBytes != null) {
            resolver.openOutputStream(uri)?.use { out ->
                out.write(rawBytes)
                out.flush()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }
        onSavedUri?.invoke(uri)
        true
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

// RAW sensör görüntüsünü DngCreator ile galeriye (.dng) kaydeder
private fun saveRawDngToGallery(
    context: Context,
    characteristics: CameraCharacteristics,
    result: TotalCaptureResult,
    rawImage: android.media.Image
): Boolean {
    var uri: Uri? = null
    return try {
        val timestamp = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "RAW_$timestamp.dng")
            put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Kameras")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        val dngCreator = DngCreator(characteristics, result)
        try {
            resolver.openOutputStream(uri)?.use { out ->
                dngCreator.writeImage(out, rawImage)
                out.flush()
            }
        } finally {
            dngCreator.close()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }
        true
    } catch (e: Exception) {
        Log.e("CameraScreen", "RAW/DNG kaydedilemedi", e)
        if (uri != null) {
            try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
        }
        false
    }
}

// JPEG baytlarından düşük bellekli küçük önizleme resmi üretir
private fun decodeThumbnailFromBytes(bytes: ByteArray): Bitmap? {
    return try {
        val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CameraScreen(hasCameraPermission: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val cameraManager = remember { context.getSystemService<CameraManager>()!! }
    val holder = remember { CameraHolder() }
    val cameraThread = remember { HandlerThread("CameraThread").also { it.start() } }
    val cameraHandler = remember { Handler(cameraThread.looper) }
    val shutterSound = remember { MediaActionSound() }
    val coroutineScope = rememberCoroutineScope()
    val turkishLocale = remember { Locale("tr", "TR") }

    var cameraReady by remember { mutableStateOf(false) }
    var isAutoMode by remember { mutableStateOf(true) }
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
    var selectedColorBand by remember { mutableStateOf(ColorBand.RED) }
    var activeBandSubSlider by remember { mutableStateOf(PointColorSubSlider.TON) }
    var colorBandsConfig by remember { mutableStateOf(ColorBandsConfig()) }
    var isDeckCollapsed by remember { mutableStateOf(false) }
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

    val sharedPrefs = remember { context.getSharedPreferences("CameraPresetsPrefs", Context.MODE_PRIVATE) }
    var showOnboardingGuide by remember { mutableStateOf(!sharedPrefs.getBoolean("onboarding_v1_done", false)) }
    var onboardingStep by remember { mutableIntStateOf(0) }

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

    LaunchedEffect(targetedColorConfig, colorBandsConfig, isAutoMode, isComparingAuto, textureViewInstance) {
        holder.currentTargetedColor = targetedColorConfig
        holder.currentColorBands = colorBandsConfig
        updateTextureViewShader(
            view = textureViewInstance,
            config = targetedColorConfig,
            colorBands = colorBandsConfig,
            bypass = isAutoMode || isComparingAuto
        )
    }

    fun savePresetsToStorage() {
        val jsonArray = JSONArray()
        savedPresets.forEach {
            jsonArray.put(JSONObject().apply {
                put("name", it.name)
                put("kelvin", it.kelvin.toDouble())
                put("tint", it.tint.toDouble())
                put("contrast", it.contrast.toDouble())
                put("saturation", it.saturation.toDouble())
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
                savedPresets.add(
                    CameraPreset(
                        name = name,
                        kelvin = obj.getDouble("kelvin").toFloat(),
                        tint = obj.getDouble("tint").toFloat(),
                        contrast = obj.optDouble("contrast", DEFAULT_CONTRAST.toDouble()).toFloat(),
                        saturation = obj.optDouble("saturation", DEFAULT_SATURATION.toDouble()).toFloat()
                    )
                )
            }
        } catch (_: Exception) {
        }

        if (savedPresets.isEmpty() && !sharedPrefs.getBoolean("presets_seeded_v8", false)) {
            savedPresets.addAll(
                listOf(
                    CameraPreset("IRChrome Doğal", 3348f, 13f, 1.00f, 1.15f),
                    CameraPreset("590nm Altın", 2400f, 8f, 1.05f, 1.25f),
                    CameraPreset("720nm Klasik IR", 1800f, 0f, 1.10f, 0.90f)
                )
            )
            savePresetsToStorage()
            sharedPrefs.edit { putBoolean("presets_seeded_v8", true) }
        } else if (savedPresets.isNotEmpty() && !sharedPrefs.getBoolean("presets_seeded_v8", false)) {
            sharedPrefs.edit { putBoolean("presets_seeded_v8", true) }
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
        holder.currentZoomRatio = zoomRatio
        holder.rawCaptureEnabled = rawEnabled
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
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        if (holder.availableAfModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }
        if (holder.aeCompensationRange.upper > holder.aeCompensationRange.lower) {
            val clampedEv = exposureEv.coerceIn(holder.aeCompensationRange.lower, holder.aeCompensationRange.upper)
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, clampedEv)
        }
        if (holder.maxAeRegions > 0 && !compatibilityFallback) {
            buildAeMeteringRegion(
                activeArray = holder.activeSensorArray,
                sensorOrientation = holder.activeSensorOrientation,
                mode = meteringModeVal,
                boxNorm = boxNorm,
                boxSizeNorm = boxSizeNorm
            )?.let { rects ->
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, rects)
            }
        }
        // Mercek / yakınlaştırma oranı (Android 11+ CONTROL_ZOOM_RATIO, altı SCALER_CROP_REGION)
        val clampedZoom = holder.currentZoomRatio.coerceIn(holder.zoomRange.lower, holder.zoomRange.upper)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, clampedZoom)
        } else if (clampedZoom > 1.01f) {
            holder.activeSensorArray?.let { active ->
                val cropW = (active.width() / clampedZoom).roundToInt().coerceAtLeast(1)
                val cropH = (active.height() / clampedZoom).roundToInt().coerceAtLeast(1)
                val dx = (active.width() - cropW) / 2
                val dy = (active.height() - cropH) / 2
                builder.set(CaptureRequest.SCALER_CROP_REGION, Rect(dx, dy, dx + cropW, dy + cropH))
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
                result[CaptureResult.COLOR_CORRECTION_GAINS]?.let { gains ->
                    holder.lastHardwareGains = gains
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

    fun runAutoCalibrate() {
        val view = textureViewInstance ?: return
        if (!view.isAvailable || view.width <= 0 || view.height <= 0) return
        val bmp = try { view.getBitmap(240, 240) } catch (_: Exception) { null } ?: return
        coroutineScope.launch(Dispatchers.Default) {
            try {
                val cal = autoCalibrateSceneFromPreview(bmp, holder.lastHardwareGains)
                withContext(Dispatchers.Main) {
                    isPickerActive = false
                    isExposureBoxPicking = false
                    isAutoMode = false
                    kelvinValue = cal.kelvin
                    tintValue = cal.tint
                    contrastValue = DEFAULT_CONTRAST
                    saturationValue = DEFAULT_SATURATION
                    measureAndApplyExposureMetering(applyEvAdjust = true)
                    updatePreviewSettings()
                    showStatus("Oto denge: ${cal.kelvin.toInt()}K · Ton ${formatSigned(cal.tint)}")
                }
            } finally {
                bmp.recycle()
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
                    val formatted = String.format(turkishLocale, "%.2f", res.recommendedContrast)
                    showStatus("Oto kontrast: ${formatted}x")
                }
            } finally {
                bmp.recycle()
            }
        }
    }

    /** Returns true when no open CameraDevice existed (so caller can reopen immediately without waiting for onClosed). */
    fun closeCameraInternal(releaseReader: Boolean = true, pendingReopen: SurfaceTexture? = null): Boolean {
        var closedWithoutActiveDevice = false
        synchronized(holder) {
            holder.cameraGeneration += 1
            holder.isOpening = false
            holder.isTakingPhoto = false
            holder.pendingReopenSurfaceTexture = pendingReopen
            val hadDevice = holder.device != null
            if (hadDevice) {
                holder.isClosingDevice = true
            }
            try { holder.session?.close() } catch (_: Exception) {}
            holder.session = null
            try { holder.device?.close() } catch (_: Exception) {}
            holder.device = null
            holder.previewBuilder = null
            try { holder.activeSurface?.release() } catch (_: Exception) {}
            holder.activeSurface = null
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
        mainHandler.post { cameraReady = false }
        return closedWithoutActiveDevice
    }

    @SuppressLint("MissingPermission")
    fun startCameraInternal(surfaceTexture: SurfaceTexture) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val openGen = synchronized(holder) {
            if (holder.isDisposed || holder.device != null || holder.isOpening) return
            if (holder.isClosingDevice) {
                holder.pendingReopenSurfaceTexture = surfaceTexture
                return
            }
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
            val cameraId = cameraIdList.firstOrNull { id ->
                try {
                    cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                } catch (_: Exception) {
                    false
                }
            } ?: cameraIdList[0]
            holder.activeCameraId = cameraId

            val chars = cameraManager.getCameraCharacteristics(cameraId)
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

            val surface = Surface(surfaceTexture)

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

            // Desteklenen yakınlaştırma / mercek aralığı (0.5x / 1x / 2x)
            val zRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) ?: Range(1f, 1f)
            } else {
                val maxDigital = (chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f).coerceAtLeast(1f)
                Range(1f, maxDigital)
            }
            holder.zoomRange = zRange
            val zoomOpts = buildList {
                if (zRange.lower <= 0.65f) add(0.5f)
                add(1f)
                if (zRange.upper >= 2.0f) add(2f)
            }

            // RAW/DNG sensör desteği kontrolü
            val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val rawSizes = if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
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
                    ImageReader.newInstance(chosenRaw.width, chosenRaw.height, ImageFormat.RAW_SENSOR, 2)
                } else {
                    null
                }
                holder.activeSurface = surface
            }

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    synchronized(holder) {
                        holder.isOpening = false
                        if (holder.isDisposed || holder.cameraGeneration != openGen) {
                            camera.close()
                            return
                        }
                        holder.device = camera
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
                                    Log.w("CameraScreen", "Session config failed at tier=${holder.previewFallbackLevel}")
                                    if (!holder.isDisposed && holder.previewFallbackLevel < 2) {
                                        holder.previewFallbackLevel += 1
                                        if (closeCameraInternal(releaseReader = true, pendingReopen = surfaceTexture)) {
                                            cameraHandler.post { startCameraInternal(surfaceTexture) }
                                        }
                                    } else {
                                        closeCameraInternal(releaseReader = true)
                                        mainHandler.post {
                                            cameraReady = false
                                            showStatus("Bu çözünürlük desteklenmedi")
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
                    if (pending != null && !holder.isDisposed) {
                        cameraHandler.post { startCameraInternal(pending) }
                    }
                }
            }, cameraHandler)
        } catch (e: Exception) {
            synchronized(holder) { holder.isOpening = false }
            Log.e("CameraScreen", "Failed to open camera", e)
        }
    }

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
        val jpegOrientation = (sensorOrientation - rotationCompensation + 360) % 360

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
        coroutineScope.launch {
            isShutterPressed = true
            shutterFlashAlpha.snapTo(0.45f)
            shutterFlashAlpha.animateTo(0f, animationSpec = tween(220))
            isShutterPressed = false
            delay(5000)
            if (holder.isTakingPhoto) {
                holder.isTakingPhoto = false
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
            val snapApplyColorGrading = !snapAuto
            coroutineScope.launch(Dispatchers.Default) {
                val hasSelectiveHsl = snapApplyColorGrading && (
                    (snapTargetedColor.isActive &&
                        (snapTargetedColor.deltaHue != 0f || snapTargetedColor.deltaSat != 0f || snapTargetedColor.deltaLum != 0f)) ||
                        snapColorBands.hasAnyModification
                    )

                val onSaved: (Uri) -> Unit = { savedUri ->
                    mainHandler.post { lastCapturedUri = savedUri }
                }
                val thumbBmp = decodeThumbnailFromBytes(bytes)
                if (thumbBmp != null) {
                    val imgBmp = thumbBmp.asImageBitmap()
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
                                graded = applyTargetedHslToBitmap(decoded, snapTargetedColor, snapColorBands)
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
        holder.lastStillCaptureResult = null
        activeRawReader?.setOnImageAvailableListener({ rawImgReader ->
            val rawImg = try { rawImgReader.acquireLatestImage() } catch (_: Exception) { null } ?: return@setOnImageAvailableListener
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // CaptureResult gelene kadar kısa süre bekle
                    var waitMs = 0
                    while (holder.lastStillCaptureResult == null && waitMs < 1500) {
                        delay(50)
                        waitMs += 50
                    }
                    val capRes = holder.lastStillCaptureResult
                    if (capRes != null) {
                        val savedRaw = saveRawDngToGallery(context, characteristics, capRes, rawImg)
                        if (savedRaw) {
                            withContext(Dispatchers.Main) { showStatus("RAW (DNG) kaydedildi") }
                        }
                    }
                } finally {
                    rawImg.close()
                }
            }
        }, cameraHandler)

        val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(reader.surface)
            activeRawReader?.surface?.let { addTarget(it) }
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
                isStillCapture = true
            )
            set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)
        }

        try {
            shutterSound.play(MediaActionSound.SHUTTER_CLICK)
            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    s: CameraCaptureSession,
                    r: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    holder.lastStillCaptureResult = result
                }

                override fun onCaptureFailed(
                    s: CameraCaptureSession,
                    r: CaptureRequest,
                    failure: CaptureFailure
                ) {
                    holder.isTakingPhoto = false
                }
            }, cameraHandler)
        } catch (e: Exception) {
            holder.isTakingPhoto = false
            Log.e("CameraScreen", "Failed to capture photo", e)
        }
    }

    LaunchedEffect(hasCameraPermission, textureViewInstance, surfaceGeneration) {
        if (hasCameraPermission && holder.device == null && !holder.isOpening && !holder.isClosingDevice) {
            textureViewInstance?.let { tv ->
                if (tv.isAvailable) {
                    tv.surfaceTexture?.let { startCamera(it) }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                holder.previewFallbackLevel = 0
                if (holder.device == null && !holder.isOpening) {
                    textureViewInstance?.let { tv ->
                        if (tv.isAvailable) {
                            tv.surfaceTexture?.let { startCamera(it) }
                        }
                    }
                }
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                closeCameraInternal(releaseReader = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            holder.isDisposed = true
            lifecycleOwner.lifecycle.removeObserver(observer)
            closeCameraInternal(releaseReader = true)
            cameraThread.quitSafely()
            shutterSound.release()
        }
    }

    LaunchedEffect(cameraReady, meteringMode, exposureBoxNorm, exposureBoxSizeNorm, surfaceGeneration) {
        while (cameraReady) {
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
    val isEvModified = exposureEvIndex != 0
    val isColorModified = abs(saturationValue - DEFAULT_SATURATION) > 0.02f ||
        abs(contrastValue - DEFAULT_CONTRAST) > 0.02f ||
        targetedColorConfig.isActive ||
        colorBandsConfig.hasAnyModification
    val isMeteringModified = meteringMode == ExposureMeteringMode.BOX_SPOT
    val hasAnyManualChange = isWbModified || isEvModified || isColorModified || isMeteringModified

    // Arayüz metin kaynakları
    val strModeAuto = stringResource(R.string.mode_auto)
    val strModePro = stringResource(R.string.mode_pro)
    val strWbShort = stringResource(R.string.label_wb_short)
    val strTintShort = stringResource(R.string.label_tint)
    val strEvShort = stringResource(R.string.label_ev_short)
    val strResFallback = stringResource(R.string.label_res_fallback)
    val strGridCd = stringResource(R.string.cd_grid_toggle)
    val strPanelCd = stringResource(R.string.cd_panel_toggle)
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

    // Ana dikey yerleşim: Üst araç çubuğu → Önizleme (panel asla üstüne binmez) → Alt kontrol alanı
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IrPalette.VoidBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1) ÜST ARAÇ ÇUBUĞU: Ferah, sıkışmayan mod anahtarı + keskin vektör hızlı araçlar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Sol: Zarif Oto / Pro kapsül anahtarı (Oto yeşil, Pro amber)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(IrPalette.HudCard)
                    .border(1.dp, IrPalette.BorderSubtle, RoundedCornerShape(50))
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeSegmentItem(
                    text = strModeAuto,
                    selected = isAutoMode,
                    activeColor = IrPalette.IrEmerald,
                    enabled = cameraReady,
                    onClick = {
                        isPickerActive = false
                        isColorPipetteActive = false
                        isExposureBoxPicking = false
                        isAutoMode = true
                        updatePreviewSettings()
                    }
                )
                ModeSegmentItem(
                    text = strModePro,
                    selected = !isAutoMode,
                    activeColor = IrPalette.IrAmber,
                    enabled = cameraReady,
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
            }

            // Sağ: Çözünürlük · RAW · Izgara · Terazi · Zamanlayıcı
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
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

                TopIconButton(
                    active = showGrid,
                    accentColor = IrPalette.IrAmber,
                    contentDesc = strGridCd,
                    onClick = { showGrid = !showGrid }
                ) { tint ->
                    GridIcon(tint = tint, modifier = Modifier.size(16.dp))
                }

                TopIconButton(
                    active = showLevel,
                    accentColor = IrPalette.IrEmerald,
                    contentDesc = strLevelCd,
                    onClick = {
                        showLevel = !showLevel
                        showStatus(if (showLevel) strLevelOn else strLevelOff)
                    }
                ) { tint ->
                    LevelIcon(tint = tint, modifier = Modifier.size(17.dp))
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
                        showStatus("Zamanlayıcı: $lbl")
                    }
                ) { tint ->
                    if (timerSeconds > 0) {
                        Text(
                            text = "${timerSeconds}s",
                            color = tint,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        TimerIcon(tint = tint, modifier = Modifier.size(16.dp))
                    }
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
                                                    currentGains = holder.lastHardwareGains
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
                                                    bypass = isAutoMode || isComparingAuto
                                                )
                                                showStatus("${getHueName(hsv[0])} seçildi")
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

                                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                }
                            }
                        },
                        update = { view ->
                            holder.activePreviewSize?.let { preview ->
                                view.surfaceTexture?.setDefaultBufferSize(preview.width, preview.height)
                            }
                            applyPreviewTransform(view, holder.activePreviewSize, holder.activeSensorOrientation)
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

                    if (meteringMode == ExposureMeteringMode.BOX_SPOT) {
                        val boxSide = minOf(size.width, size.height) * exposureBoxSizeNorm
                        val half = boxSide * 0.5f
                        val cx = (exposureBoxNorm.x * size.width).coerceIn(half, (size.width - half).coerceAtLeast(half))
                        val cy = (exposureBoxNorm.y * size.height).coerceIn(half, (size.height - half).coerceAtLeast(half))
                        val left = cx - half
                        val top = cy - half
                        val right = cx + half
                        val bottom = cy + half
                        val corner = boxSide * 0.24f
                        val boxColor = if (isExposureBoxPicking) IrPalette.IrCoral else IrPalette.IrAmber
                        val strokeW = 2.2.dp.toPx()

                        drawRect(
                            color = boxColor.copy(alpha = 0.07f),
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
                            color = boxColor.copy(alpha = 0.90f),
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

                // Önizleme üst HUD satırı: Sol canlı telemetri + Sağ kompakt histogram (çakışma sıfır)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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
                                val evFormatted = formatSignedDecimal(evDisplayVal, turkishLocale)
                                StatusTelemetryChip(
                                    text = "$strEvShort $evFormatted",
                                    accentColor = if ((!isAutoMode && activeTab == ControlTab.POZ && !isDeckCollapsed) || isEvModified) IrPalette.IrAmber else IrPalette.TextPrimary,
                                    enabled = cameraReady,
                                    onClick = { openProControl(ControlTab.POZ) }
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

                // Önizleme alt HUD satırı: Tek satırda hizalı [Karşılaştır] · [0,5x | 1x | 2x] · [Aktif araç rozeti]
                // Yatay Row düzeni sayesinde hiçbir en-boy oranında öğeler birbirinin üstüne binmez
                if (cameraReady) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sol slot: PRO modda basılı tutunca OTO önizlemesi gösteren Karşılaştır düğmesi
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (!isAutoMode) {
                                Box(
                                    modifier = Modifier
                                        .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                                        .pointerInput(cameraReady) {
                                            detectTapGestures(
                                                onPress = {
                                                    isComparingAuto = true
                                                    updatePreviewSettings()
                                                    tryAwaitRelease()
                                                    isComparingAuto = false
                                                    updatePreviewSettings()
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
                                                1.dp,
                                                if (isComparingAuto) IrPalette.IrEmerald else Color.White.copy(alpha = 0.20f),
                                                RoundedCornerShape(50)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        CompareEyeIcon(
                                            tint = if (isComparingAuto) Color.Black else IrPalette.TextPrimary,
                                            modifier = Modifier.size(14.dp)
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

                        // Orta slot: Mercek / yakınlaştırma kapsülü (0,5x · 1x · 2x)
                        if (availableZoomOptions.size > 1) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(IrPalette.HudScrim)
                                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                availableZoomOptions.forEach { z ->
                                    val isSelected = abs(zoomRatio - z) < 0.08f
                                    val zLabel = if (z == 0.5f) "0,5x" else "${z.toInt()}x"
                                    val chipBg by animateColorAsState(
                                        targetValue = if (isSelected) IrPalette.IrAmber else Color.Transparent,
                                        animationSpec = tween(160),
                                        label = "zoomChipBg"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .sizeIn(minWidth = 36.dp, minHeight = 32.dp)
                                            .clip(CircleShape)
                                            .background(chipBg)
                                            .clickable {
                                                zoomRatio = z
                                                updatePreviewSettings()
                                            }
                                            .padding(horizontal = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = zLabel,
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Sağ slot: Simetriyi koruyan kompakt aktif araç rozeti (ZEBRA / KUTU)
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            val badgeInfo: Pair<String, Color>? = when {
                                showZebra -> "ZEBRA" to IrPalette.IrCoral
                                meteringMode == ExposureMeteringMode.BOX_SPOT -> "KUTU" to IrPalette.IrAmber
                                else -> null
                            }
                            if (badgeInfo != null) {
                                val (tag, color) = badgeInfo
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(IrPalette.HudScrim)
                                        .border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(50))
                                        .padding(horizontal = 9.dp, vertical = 5.dp)
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

                // Deklanşör çekim flaş efekti
                if (shutterFlashAlpha.value > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = shutterFlashAlpha.value))
                    )
                }
            }
        }

        // 3) ALT KONTROL ALANI: Önizlemenin altında ayrı bölüm (OTO modda tam ferah vizör, PRO modda tek kaydırıcı paneli)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(IrPalette.VoidBlack)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // PRO Mod Çip Tabanlı Tek Kaydırıcı Paneli
            AnimatedVisibility(visible = !isAutoMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(IrPalette.HudGlass)
                        .border(1.dp, IrPalette.BorderSubtle, RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Seçili çipin tek/kompakt kaydırıcı alanı
                    AnimatedVisibility(
                        visible = !isDeckCollapsed,
                        enter = fadeIn() + slideInVertically { it / 4 },
                        exit = fadeOut() + slideOutVertically { it / 4 }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (activeTab) {
                                ControlTab.BD -> {
                                    // Tek kaydırıcı + canlı değer gösteren 2'li alt seçici (vizör asla daralmaz)
                                    SegmentedChoiceRow(
                                        options = listOf(
                                            WbSubSlider.KELVIN to "${stringResource(R.string.sub_kelvin)} · ${kelvinValue.toInt()}K",
                                            WbSubSlider.TINT to "${stringResource(R.string.sub_tint)} · ${formatSigned(tintValue)}"
                                        ),
                                        selected = activeWbSubSlider,
                                        enabled = cameraReady,
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
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ActionPillButton(
                                            label = stringResource(R.string.action_auto_calibrate),
                                            selected = false,
                                            accentColor = IrPalette.IrAmber,
                                            enabled = cameraReady,
                                            modifier = Modifier.weight(1f),
                                            leadingIcon = { tint -> SparkleAutoIcon(tint = tint, modifier = Modifier.size(15.dp)) },
                                            onClick = { runAutoCalibrate() }
                                        )
                                        ActionPillButton(
                                            label = if (isPickerActive) stringResource(R.string.action_spot_wb_active) else stringResource(R.string.action_spot_wb),
                                            selected = isPickerActive,
                                            accentColor = IrPalette.IrAmber,
                                            enabled = cameraReady,
                                            modifier = Modifier.weight(1f),
                                            leadingIcon = { tint -> TargetCrosshairIcon(tint = tint, modifier = Modifier.size(15.dp)) },
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
                                        val evBadge = "${formatSignedDecimal(evDisplayVal, turkishLocale)} $strEvShort"
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

                                    // Dengeli 2'li araç satırı: Vurgu koruma + Zebra uyarısı
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ActionPillButton(
                                            label = stringResource(R.string.action_highlight_guard),
                                            selected = highlightProtectionEnabled,
                                            accentColor = IrPalette.IrTeal,
                                            enabled = cameraReady,
                                            modifier = Modifier.weight(1f),
                                            leadingIcon = { tint -> ShieldIcon(tint = tint, modifier = Modifier.size(15.dp)) },
                                            onClick = {
                                                highlightProtectionEnabled = !highlightProtectionEnabled
                                                measureAndApplyExposureMetering(applyEvAdjust = highlightProtectionEnabled)
                                                showStatus(if (highlightProtectionEnabled) strHighlightOn else strHighlightOff)
                                            }
                                        )
                                        ActionPillButton(
                                            label = stringResource(R.string.action_zebra),
                                            selected = showZebra,
                                            accentColor = IrPalette.IrCoral,
                                            enabled = cameraReady,
                                            modifier = Modifier.weight(1f),
                                            leadingIcon = { tint -> ZebraIcon(tint = tint, modifier = Modifier.size(15.dp)) },
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
                                        onSelect = { mode ->
                                            colorPanelMode = mode
                                            if (mode != ColorPanelMode.PIPET) {
                                                isColorPipetteActive = false
                                            }
                                        }
                                    )

                                    when (colorPanelMode) {
                                        ColorPanelMode.GENEL -> {
                                            val satStr = String.format(turkishLocale, "%.2f", saturationValue)
                                            val conStr = String.format(turkishLocale, "%.2f", contrastValue)
                                            SegmentedChoiceRow(
                                                options = listOf(
                                                    ColorSubSlider.DOYGUNLUK to "${stringResource(R.string.slider_saturation)} · ${satStr}x",
                                                    ColorSubSlider.KONTRAST to "${stringResource(R.string.slider_contrast)} · ${conStr}x"
                                                ),
                                                selected = activeColorSubSlider,
                                                enabled = cameraReady,
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
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                ActionPillButton(
                                                    label = stringResource(R.string.action_auto_contrast),
                                                    selected = false,
                                                    accentColor = IrPalette.IrAmber,
                                                    enabled = cameraReady,
                                                    modifier = Modifier.weight(1f),
                                                    leadingIcon = { tint -> SparkleAutoIcon(tint = tint, modifier = Modifier.size(15.dp)) },
                                                    onClick = { runAutoContrast() }
                                                )
                                                ActionPillButton(
                                                    label = stringResource(R.string.action_color_pipette),
                                                    selected = isColorPipetteActive,
                                                    accentColor = IrPalette.IrTeal,
                                                    enabled = cameraReady,
                                                    modifier = Modifier.weight(1f),
                                                    leadingIcon = { tint -> PipetteIcon(tint = tint, modifier = Modifier.size(15.dp)) },
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
                                            // Lightroom / Darkroom tarzı etkileşimli Renk Histogramı Bant Mikseri
                                            val bandAdj = colorBandsConfig.get(selectedColorBand)
                                            val bandShiftedHue = (((selectedColorBand.centerHue + bandAdj.deltaHue) % 360f + 360f) % 360f).coerceIn(0f, 359.99f)
                                            val bandHasHueShift = abs(bandAdj.deltaHue) > 1f

                                            // Seçili bant başlığı + kaynak -> hedef renk önizlemesi + sıfırla
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.weight(1f, fill = false)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(14.dp)
                                                            .clip(CircleShape)
                                                            .background(selectedColorBand.swatchColor)
                                                            .border(1.dp, Color.White.copy(alpha = 0.75f), CircleShape)
                                                    )
                                                    if (bandHasHueShift) {
                                                        val shiftedBandColor = Color.hsv(
                                                            hue = bandShiftedHue,
                                                            saturation = (0.85f * (1f + bandAdj.deltaSat * 0.5f)).coerceIn(0.25f, 1f),
                                                            value = (0.94f + bandAdj.deltaLum * 0.35f).coerceIn(0.30f, 1f)
                                                        )
                                                        Text(
                                                            text = "→",
                                                            color = IrPalette.TextSecondary,
                                                            fontSize = 11.sp
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .size(14.dp)
                                                                .clip(CircleShape)
                                                                .background(shiftedBandColor)
                                                                .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                                                        )
                                                    }
                                                    Text(
                                                        text = if (bandHasHueShift) {
                                                            "${selectedColorBand.label} → ${getHueName(bandShiftedHue)}"
                                                        } else {
                                                            selectedColorBand.label
                                                        },
                                                        color = IrPalette.TextPrimary,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    if (bandAdj.isModified) {
                                                        Text(
                                                            text = stringResource(R.string.action_reset_band),
                                                            color = IrPalette.IrAmber,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(7.dp))
                                                                .background(IrPalette.IrAmber.copy(alpha = 0.14f))
                                                                .clickable {
                                                                    colorBandsConfig = colorBandsConfig.update(selectedColorBand) { BandHslAdjustment() }
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                }
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    } else if (colorBandsConfig.hasAnyModification) {
                                                        Text(
                                                            text = stringResource(R.string.action_reset_all_bands),
                                                            color = IrPalette.IrCoral,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(7.dp))
                                                                .background(IrPalette.IrCoral.copy(alpha = 0.14f))
                                                                .clickable {
                                                                    colorBandsConfig = ColorBandsConfig()
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                }
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            // Canlı Renk Tonu Histogramı üzerinden dokunarak/sürükleyerek 8 bant seçimi
                                            ColorHistogramBandPicker(
                                                hueBins = liveHistogram.hueBins,
                                                selectedBand = selectedColorBand,
                                                config = colorBandsConfig,
                                                enabled = cameraReady,
                                                onSelectBand = { band ->
                                                    if (selectedColorBand != band) {
                                                        selectedColorBand = band
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                }
                                            )

                                            // Seçili bant için Ton / Doygunluk / Parlaklık seçicisi
                                            SegmentedChoiceRow(
                                                options = listOf(
                                                    PointColorSubSlider.TON to "${stringResource(R.string.sub_tint)} · ${formatSigned(bandAdj.deltaHue)}°",
                                                    PointColorSubSlider.DOYGUNLUK to "${stringResource(R.string.slider_saturation)} · ${formatSigned(bandAdj.deltaSat * 100f)}%",
                                                    PointColorSubSlider.PARLAKLIK to "${stringResource(R.string.slider_point_lum)} · ${formatSigned(bandAdj.deltaLum * 100f)}%"
                                                ),
                                                selected = activeBandSubSlider,
                                                enabled = cameraReady,
                                                onSelect = { activeBandSubSlider = it }
                                            )

                                            when (activeBandSubSlider) {
                                                PointColorSubSlider.TON -> {
                                                    val bandSpectrum = remember(selectedColorBand) {
                                                        val base = selectedColorBand.centerHue
                                                        List(9) { idx ->
                                                            val offset = -180f + idx * 45f
                                                            val h = (((base + offset) % 360f + 360f) % 360f).coerceIn(0f, 359.99f)
                                                            Color.hsv(h, 0.88f, 0.96f)
                                                        }
                                                    }
                                                    ProGradientSliderRow(
                                                        label = "${selectedColorBand.label} · ${stringResource(R.string.slider_point_hue)}",
                                                        valueBadge = "${formatSigned(bandAdj.deltaHue)}°",
                                                        isModified = abs(bandAdj.deltaHue) > 0.5f,
                                                        value = bandAdj.deltaHue,
                                                        defaultValue = 0f,
                                                        snapTolerance = 5f,
                                                        valueRange = -180f..180f,
                                                        enabled = cameraReady,
                                                        gradient = Brush.horizontalGradient(bandSpectrum),
                                                        onStepMinus = {
                                                            colorBandsConfig = colorBandsConfig.update(selectedColorBand) {
                                                                it.copy(deltaHue = (it.deltaHue - 10f).coerceIn(-180f, 180f))
                                                            }
                                                        },
                                                        onStepPlus = {
                                                            colorBandsConfig = colorBandsConfig.update(selectedColorBand) {
                                                                it.copy(deltaHue = (it.deltaHue + 10f).coerceIn(-180f, 180f))
                                                            }
                                                        },
                                                        onReset = {
                                                            colorBandsConfig = colorBandsConfig.update(selectedColorBand) { it.copy(deltaHue = 0f) }
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        },
                                                        onValueChange = { newVal ->
                                                            colorBandsConfig = colorBandsConfig.update(selectedColorBand) { it.copy(deltaHue = newVal) }
                                                        }
                                                    )
                                                }

                                                PointColorSubSlider.DOYGUNLUK -> ProGradientSliderRow(
                                                    label = "${selectedColorBand.label} · ${stringResource(R.string.slider_saturation)}",
                                                    valueBadge = "${formatSigned(bandAdj.deltaSat * 100f)}%",
                                                    isModified = abs(bandAdj.deltaSat) > 0.02f,
                                                    value = bandAdj.deltaSat,
                                                    defaultValue = 0f,
                                                    snapTolerance = 0.05f,
                                                    valueRange = -1.0f..1.0f,
                                                    enabled = cameraReady,
                                                    gradient = Brush.horizontalGradient(
                                                        listOf(Color(0xFF475569), selectedColorBand.swatchColor)
                                                    ),
                                                    onStepMinus = {
                                                        colorBandsConfig = colorBandsConfig.update(selectedColorBand) {
                                                            it.copy(deltaSat = (it.deltaSat - 0.05f).coerceIn(-1.0f, 1.0f))
                                                        }
                                                    },
                                                    onStepPlus = {
                                                        colorBandsConfig = colorBandsConfig.update(selectedColorBand) {
                                                            it.copy(deltaSat = (it.deltaSat + 0.05f).coerceIn(-1.0f, 1.0f))
                                                        }
                                                    },
                                                    onReset = {
                                                        colorBandsConfig = colorBandsConfig.update(selectedColorBand) { it.copy(deltaSat = 0f) }
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    },
                                                    onValueChange = { newVal ->
                                                        colorBandsConfig = colorBandsConfig.update(selectedColorBand) { it.copy(deltaSat = newVal) }
                                                    }
                                                )

                                                PointColorSubSlider.PARLAKLIK -> ProGradientSliderRow(
                                                    label = "${selectedColorBand.label} · ${stringResource(R.string.slider_point_lum)}",
                                                    valueBadge = "${formatSigned(bandAdj.deltaLum * 100f)}%",
                                                    isModified = abs(bandAdj.deltaLum) > 0.02f,
                                                    value = bandAdj.deltaLum,
                                                    defaultValue = 0f,
                                                    snapTolerance = 0.05f,
                                                    valueRange = -1.0f..1.0f,
                                                    enabled = cameraReady,
                                                    gradient = Brush.horizontalGradient(
                                                        listOf(Color(0xFF1E293B), selectedColorBand.swatchColor, Color.White)
                                                    ),
                                                    onStepMinus = {
                                                        colorBandsConfig = colorBandsConfig.update(selectedColorBand) {
                                                            it.copy(deltaLum = (it.deltaLum - 0.05f).coerceIn(-1.0f, 1.0f))
                                                        }
                                                    },
                                                    onStepPlus = {
                                                        colorBandsConfig = colorBandsConfig.update(selectedColorBand) {
                                                            it.copy(deltaLum = (it.deltaLum + 0.05f).coerceIn(-1.0f, 1.0f))
                                                        }
                                                    },
                                                    onReset = {
                                                        colorBandsConfig = colorBandsConfig.update(selectedColorBand) { it.copy(deltaLum = 0f) }
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    },
                                                    onValueChange = { newVal ->
                                                        colorBandsConfig = colorBandsConfig.update(selectedColorBand) { it.copy(deltaLum = newVal) }
                                                    }
                                                )
                                            }
                                        }

                                        ColorPanelMode.PIPET -> {
                                            if (targetedColorConfig.isActive) {
                                                // Noktasal renk başlığı + genişlik + kaldır
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    val shiftedHue = (((targetedColorConfig.targetHue + targetedColorConfig.deltaHue) % 360f + 360f) % 360f).coerceIn(0f, 359.99f)
                                                    val hasHueShift = abs(targetedColorConfig.deltaHue) > 1f
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(15.dp)
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
                                                                fontSize = 11.sp
                                                            )
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(15.dp)
                                                                    .clip(CircleShape)
                                                                    .background(shiftedColor)
                                                                    .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                                                            )
                                                        }
                                                        Text(
                                                            text = if (hasHueShift) {
                                                                getHueName(shiftedHue)
                                                            } else {
                                                                "${getHueName(targetedColorConfig.targetHue)} (${targetedColorConfig.targetHue.toInt()}°)"
                                                            },
                                                            color = IrPalette.IrTeal,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        listOf(
                                                            stringResource(R.string.range_narrow) to 14f,
                                                            stringResource(R.string.range_balanced) to 22f,
                                                            stringResource(R.string.range_wide) to 36f
                                                        ).forEach { (lbl, w) ->
                                                            val isSel = targetedColorConfig.hueWidth == w
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .background(if (isSel) IrPalette.IrAmber else IrPalette.SurfaceSubtle)
                                                                    .clickable {
                                                                        targetedColorConfig = targetedColorConfig.copy(
                                                                            hueWidth = w,
                                                                            hueFeather = if (w == 14f) 12f else if (w == 22f) 18f else 26f
                                                                        )
                                                                    }
                                                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                                                            ) {
                                                                Text(
                                                                    text = lbl,
                                                                    color = if (isSel) Color.Black else IrPalette.TextSecondary,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.SemiBold
                                                                )
                                                            }
                                                        }
                                                        Text(
                                                            text = stringResource(R.string.action_remove),
                                                            color = IrPalette.IrCoral,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .clickable {
                                                                    targetedColorConfig = TargetedColorConfig()
                                                                }
                                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }

                                                SegmentedChoiceRow(
                                                    options = listOf(
                                                        PointColorSubSlider.TON to "${stringResource(R.string.sub_tint)} · ${formatSigned(targetedColorConfig.deltaHue)}°",
                                                        PointColorSubSlider.DOYGUNLUK to "${stringResource(R.string.slider_saturation)} · ${formatSigned(targetedColorConfig.deltaSat * 100f)}%",
                                                        PointColorSubSlider.PARLAKLIK to "${stringResource(R.string.slider_point_lum)} · ${formatSigned(targetedColorConfig.deltaLum * 100f)}%"
                                                    ),
                                                    selected = activePointColorSubSlider,
                                                    enabled = cameraReady,
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
                                                    fontSize = 12.sp
                                                )
                                                ActionPillButton(
                                                    label = if (isColorPipetteActive) stringResource(R.string.action_color_pipette_active) else stringResource(R.string.action_color_pipette),
                                                    selected = isColorPipetteActive,
                                                    accentColor = IrPalette.IrTeal,
                                                    enabled = cameraReady,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    leadingIcon = { tint -> PipetteIcon(tint = tint, modifier = Modifier.size(15.dp)) },
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

                    // Yatay Profesyonel Sekme Çubuğu: İkon + anlaşılır Türkçe etiket + aktiflik noktası + panel aç/kapa
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
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
                                            ControlTab.BD -> WhiteBalanceIcon(tint = tint, modifier = Modifier.size(14.dp))
                                            ControlTab.POZ -> ExposureIcon(tint = tint, modifier = Modifier.size(14.dp))
                                            ControlTab.RENK -> ColorWheelIcon(tint = tint, modifier = Modifier.size(14.dp))
                                            ControlTab.OLCUM -> MeteringFrameIcon(tint = tint, modifier = Modifier.size(14.dp))
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

                        // Panel aç/kapa vektör düğmesi (48x48dp dokunma alanı, 180° dönüş)
                        val chevronRotation by animateFloatAsState(
                            targetValue = if (isDeckCollapsed) 180f else 0f,
                            animationSpec = tween(220),
                            label = "chevronRot"
                        )
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = strPanelCd }
                                .clip(CircleShape)
                                .clickable { isDeckCollapsed = !isDeckCollapsed },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(
                                        if (isDeckCollapsed) IrPalette.IrAmber.copy(alpha = 0.20f)
                                        else Color.White.copy(alpha = 0.06f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isDeckCollapsed) IrPalette.IrAmber else IrPalette.BorderSubtle,
                                        RoundedCornerShape(11.dp)
                                    ),
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

            // ALT DEKLANŞÖR ÇUBUĞU: Kusursuz 3 bölgeli simetrik düzen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Sol bölge: 52dp dairesel Galeri küçük resmi
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .semantics { contentDescription = strGalleryCd }
                            .clip(CircleShape)
                            .background(IrPalette.HudCard)
                            .border(1.5.dp, Color.White.copy(alpha = 0.34f), CircleShape)
                            .clickable {
                                val targetUri = lastCapturedUri
                                val intent = if (targetUri != null) {
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(targetUri, "image/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                } else {
                                    Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    showStatus("Galeri açılamadı")
                                }
                            },
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
                            GalleryIcon(tint = IrPalette.TextSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Orta: Profesyonel çift halkalı deklanşör (geri sayımda kırmızı iptal düğmesine dönüşür)
                val innerSize by animateDpAsState(
                    targetValue = if (isShutterPressed) 52.dp else 60.dp,
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
                        .size(76.dp)
                        .semantics { contentDescription = strShutterCd }
                        .clip(CircleShape)
                        .border(2.8.dp, shutterBorderColor, CircleShape)
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
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Sağ bölge: Ön ayarlar hapı + Sıfırla ikonu (PRO) / Ölçüm kısayolu (OTO)
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!isAutoMode) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = strResetAllCd }
                                .clip(CircleShape)
                                .clickable(enabled = cameraReady) { showResetAllDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (hasAnyManualChange) IrPalette.IrAmber.copy(alpha = 0.16f)
                                        else IrPalette.HudCard
                                    )
                                    .border(
                                        1.dp,
                                        if (hasAnyManualChange) IrPalette.IrAmber.copy(alpha = 0.7f) else IrPalette.BorderSubtle,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                ResetIcon(
                                    tint = if (hasAnyManualChange) IrPalette.IrAmber else IrPalette.TextSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    QuickPillAction(
                        text = stringResource(R.string.action_presets),
                        highlighted = showPresetsListDialog,
                        compact = true,
                        enabled = cameraReady,
                        leadingIcon = { tint -> PresetStarIcon(tint = tint, modifier = Modifier.size(14.dp)) },
                        onClick = { showPresetsListDialog = true }
                    )
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

    // Ön Ayarlar Listesi Diyalogu
    if (showPresetsListDialog) {
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.dialog_presets_new),
                        color = IrPalette.IrAmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(IrPalette.IrAmber.copy(alpha = 0.14f))
                            .clickable {
                                showPresetsListDialog = false
                                showSaveDialog = true
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            },
            text = {
                if (savedPresets.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dialog_presets_empty),
                        color = IrPalette.TextSecondary,
                        fontSize = 13.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(savedPresets, key = { it.name }) { preset ->
                            val isMatching = !isAutoMode &&
                                abs(kelvinValue - preset.kelvin) < 5f &&
                                abs(tintValue - preset.tint) < 1f
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isMatching) IrPalette.IrAmber.copy(alpha = 0.18f)
                                        else Color.White.copy(alpha = 0.05f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isMatching) IrPalette.IrAmber else Color.White.copy(alpha = 0.10f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        isPickerActive = false
                                        isColorPipetteActive = false
                                        isExposureBoxPicking = false
                                        isAutoMode = false
                                        kelvinValue = preset.kelvin
                                        tintValue = preset.tint
                                        contrastValue = preset.contrast
                                        saturationValue = preset.saturation
                                        updatePreviewSettings()
                                        showPresetsListDialog = false
                                        showStatus("${preset.name} yüklendi")
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = preset.name,
                                        color = if (isMatching) IrPalette.IrAmber else IrPalette.TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$strWbShort ${preset.kelvin.toInt()}K · $strTintShort ${formatSigned(preset.tint)} · K ${String.format(turkishLocale, "%.2f", preset.contrast)}x · D ${String.format(turkishLocale, "%.2f", preset.saturation)}x",
                                        color = IrPalette.TextSecondary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            savedPresets.remove(preset)
                                            savePresetsToStorage()
                                            showStatus("${preset.name} silindi")
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "✕",
                                        color = IrPalette.TextSecondary,
                                        fontSize = 14.sp
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
                    Text(
                        text = "$strWbShort ${kelvinValue.toInt()}K · $strTintShort ${formatSigned(tintValue)} · K ${String.format(turkishLocale, "%.2f", contrastValue)}x · D ${String.format(turkishLocale, "%.2f", saturationValue)}x",
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
                            savedPresets.removeAll { it.name.equals(trimmed, ignoreCase = true) }
                            savedPresets.add(
                                CameraPreset(
                                    name = trimmed,
                                    kelvin = kelvinValue,
                                    tint = tintValue,
                                    contrast = contrastValue,
                                    saturation = saturationValue
                                )
                            )
                            savePresetsToStorage()
                            presetNameInput = ""
                            showSaveDialog = false
                            showStatus("$trimmed kaydedildi")
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
                showStatus("Çözünürlük: ${formatMegapixels(res)} (${formatAspectRatioTag(res)})")
                restartForResolution(res)
            },
            onDismiss = { showResolutionDialog = false }
        )
    }

    // 3 Adımlı İlk Açılış Rehberi
    if (showOnboardingGuide) {
        val stepTitles = listOf(
            stringResource(R.string.guide_step_1_title),
            stringResource(R.string.guide_step_2_title),
            stringResource(R.string.guide_step_3_title)
        )
        val stepBodies = listOf(
            stringResource(R.string.guide_step_1_body),
            stringResource(R.string.guide_step_2_body),
            stringResource(R.string.guide_step_3_body)
        )
        val safeStep = onboardingStep.coerceIn(0, 2)
        AlertDialog(
            onDismissRequest = {
                sharedPrefs.edit { putBoolean("onboarding_v1_done", true) }
                showOnboardingGuide = false
            },
            containerColor = IrPalette.SurfaceElevated,
            titleContentColor = IrPalette.TextPrimary,
            textContentColor = IrPalette.TextSecondary,
            title = {
                Text(
                    text = stepTitles[safeStep],
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = IrPalette.IrAmber
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stepBodies[safeStep],
                        fontSize = 14.sp,
                        color = IrPalette.TextPrimary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0..2) {
                            Box(
                                modifier = Modifier
                                    .height(6.dp)
                                    .width(if (i == safeStep) 22.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(if (i == safeStep) IrPalette.IrAmber else Color.White.copy(alpha = 0.22f))
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (safeStep < 2) {
                            onboardingStep = safeStep + 1
                        } else {
                            sharedPrefs.edit { putBoolean("onboarding_v1_done", true) }
                            showOnboardingGuide = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IrPalette.IrAmber)
                ) {
                    Text(
                        text = if (safeStep < 2) stringResource(R.string.guide_next) else stringResource(R.string.guide_done),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        sharedPrefs.edit { putBoolean("onboarding_v1_done", true) }
                        showOnboardingGuide = false
                    }
                ) {
                    Text(stringResource(R.string.guide_skip), color = IrPalette.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ModeSegmentItem(
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
            .sizeIn(minWidth = 48.dp, minHeight = 40.dp)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TopBadgeButton(
    text: String,
    active: Boolean,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (active) accentColor.copy(alpha = 0.22f) else IrPalette.HudCard)
                .border(1.dp, if (active) accentColor else IrPalette.BorderSubtle, RoundedCornerShape(50))
                .padding(horizontal = 9.dp, vertical = 6.dp)
        ) {
            Text(
                text = text,
                color = if (active) accentColor else IrPalette.TextPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TopIconButton(
    active: Boolean,
    accentColor: Color,
    contentDesc: String,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .semantics { contentDescription = contentDesc }
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (active) accentColor.copy(alpha = 0.20f) else IrPalette.HudCard)
                .border(1.dp, if (active) accentColor else IrPalette.BorderSubtle, CircleShape),
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
private fun LiveHistogramOverlay(
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
 * Lightroom / Darkroom tarzı etkileşimli Canlı Renk Tonu (Hue) Histogramı ve 8-Bant Seçici.
 * Sahnede hangi renklerin yoğun olduğunu canlı renkli histogram olarak gösterir;
 * histograma dokunarak veya sürükleyerek (ya da alt renk noktalarından) 8 banttan birini anında seçersiniz.
 */
@Composable
private fun ColorHistogramBandPicker(
    hueBins: FloatArray,
    selectedBand: ColorBand,
    config: ColorBandsConfig,
    enabled: Boolean,
    onSelectBand: (ColorBand) -> Unit
) {
    val currentOnSelect by rememberUpdatedState(onSelectBand)
    val allBands = ColorBand.entries

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF090C11))
            .border(1.dp, IrPalette.BorderSubtle, RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Üst: Canlı 48 sütunlu gökkuşağı Hue histogramı + seçili bant çerçevesi (dokun & sürükle)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
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
                        currentOnSelect(xToBand(down.position.x))
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            currentOnSelect(xToBand(change.position.x))
                            change.consume()
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val bandW = w / allBands.size.toFloat()

                // 1) Seçili bandın arka plan vurgusu
                val selLeft = selectedBand.index * bandW
                drawRect(
                    color = selectedBand.swatchColor.copy(alpha = 0.16f),
                    topLeft = Offset(selLeft, 0f),
                    size = androidx.compose.ui.geometry.Size(bandW, h)
                )

                // 2) 8 bant dikey ayırıcı çizgileri
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
                        val bandPos = ((i + 0.5f) / hueBins.size.toFloat()) * 8f
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

                // 4) Seçili bandın keskin optik çerçevesi
                drawRoundRect(
                    color = selectedBand.swatchColor,
                    topLeft = Offset(selLeft + 0.8.dp.toPx(), 0.8.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(bandW - 1.6.dp.toPx(), h - 1.6.dp.toPx()),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = Stroke(width = 1.4.dp.toPx())
                )
            }
        }

        // Alt: Histogram bantlarıyla 1:1 hizalı 8 renk noktası (düzenlenen bantlarda üstte nokta göstergesi)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            allBands.forEach { band ->
                val isSelected = band == selectedBand
                val isBandModified = config.get(band).isModified
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = enabled) { onSelectBand(band) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 18.dp else 13.dp)
                            .clip(CircleShape)
                            .background(band.swatchColor)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.28f),
                                shape = CircleShape
                            )
                    )
                    if (isBandModified) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 1.dp, end = 4.dp)
                                .size(5.dp)
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
private fun <T> SegmentedChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, IrPalette.BorderSubtle, RoundedCornerShape(11.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { (item, label) ->
            val isSel = item == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minHeight = 36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSel) IrPalette.IrAmber.copy(alpha = 0.20f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (isSel) IrPalette.IrAmber.copy(alpha = 0.85f) else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(enabled = enabled) { onSelect(item) }
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSel) IrPalette.IrAmber else IrPalette.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ActionPillButton(
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
            .sizeIn(minHeight = 44.dp)
            .clip(RoundedCornerShape(11.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(11.dp))
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                leadingIcon(contentColor)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TabChip(
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
            .sizeIn(minHeight = 46.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            icon?.invoke(contentColor)
            Text(
                text = title,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (hasActiveIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 5.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(IrPalette.IrAmber)
            )
        }
    }
}

/**
 * Profesyonel optik skala kaydırıcı:
 * - Çift çizgi karışıklığını tamamen ortadan kaldıran dairesel mekanik düğme
 * - Üstte ince ters üçgen varsayılan referans işareti
 * - Altta hassas cetvel çentikleri
 * - Sıfır gecikmeli anında dokunma & sürükleme + değer hapına dokununca sıfırlama
 */
@Composable
private fun ProGradientSliderRow(
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
    onStepMinus: () -> Unit,
    onStepPlus: () -> Unit,
    onReset: () -> Unit,
    onValueChange: (Float) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    var wasSnapped by remember { mutableStateOf(abs(value - defaultValue) <= snapTolerance * 0.5f) }
    var isDragging by remember { mutableStateOf(false) }

    val thumbRadiusDp by animateDpAsState(
        targetValue = if (isDragging) 11.5.dp else 9.5.dp,
        animationSpec = tween(120),
        label = "thumbRadius"
    )

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = label,
                    color = IrPalette.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isModified) IrPalette.IrAmber.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f))
                        .border(
                            1.dp,
                            if (isModified) IrPalette.IrAmber.copy(alpha = 0.65f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(enabled = enabled, onClick = onReset)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = valueBadge,
                        color = if (isModified) IrPalette.IrAmber else IrPalette.TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    if (isModified) {
                        ResetIcon(
                            tint = IrPalette.IrAmber.copy(alpha = 0.85f),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
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
                .height(42.dp)
                .semantics { contentDescription = "$label $valueBadge" }
                .pointerInput(enabled, valueRange, defaultValue, snapTolerance) {
                    if (!enabled) return@pointerInput
                    val padPx = 14.dp.toPx()
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
                        currentOnValueChange(xToSnapped(down.position.x))
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            currentOnValueChange(xToSnapped(change.position.x))
                            change.consume()
                        }
                        isDragging = false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padX = 14.dp.toPx()
                val trackW = (size.width - padX * 2f).coerceAtLeast(1f)
                val trackY = size.height * 0.46f
                val trackH = 5.dp.toPx()
                val cornerR = trackH * 0.5f

                // 1) Spektrum arka plan rayı
                drawRoundRect(
                    brush = gradient,
                    topLeft = Offset(padX, trackY - cornerR),
                    size = androidx.compose.ui.geometry.Size(trackW, trackH),
                    cornerRadius = CornerRadius(cornerR, cornerR),
                    alpha = if (enabled) 0.92f else 0.35f
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.16f),
                    topLeft = Offset(padX, trackY - cornerR),
                    size = androidx.compose.ui.geometry.Size(trackW, trackH),
                    cornerRadius = CornerRadius(cornerR, cornerR),
                    style = Stroke(width = 0.8.dp.toPx())
                )

                // 2) Altta hassas cetvel çentikleri
                val tickCount = 16
                val tickTop = trackY + cornerR + 4.dp.toPx()
                for (i in 0..tickCount) {
                    val tx = padX + trackW * (i.toFloat() / tickCount)
                    val isMajor = i % 4 == 0
                    val tickLen = if (isMajor) 6.dp.toPx() else 3.5.dp.toPx()
                    drawLine(
                        color = Color.White.copy(alpha = if (isMajor) 0.38f else 0.18f),
                        start = Offset(tx, tickTop),
                        end = Offset(tx, tickTop + tickLen),
                        strokeWidth = if (isMajor) 1.2.dp.toPx() else 0.9.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 3) Üstte ince ters üçgen varsayılan değer işareti (dairesel düğmeden tamamen farklı biçim)
                val defX = padX + trackW * defaultFraction
                val triTop = trackY - cornerR - 8.dp.toPx()
                val triBottom = trackY - cornerR - 2.5.dp.toPx()
                val triHalfW = 3.5.dp.toPx()
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
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 5) Mekanik dairesel optik düğme (sürüklerken hafif büyür)
                val r = thumbRadiusDp.toPx()
                if (isDragging) {
                    drawCircle(
                        color = IrPalette.IrAmber.copy(alpha = 0.24f),
                        radius = r + 5.dp.toPx(),
                        center = Offset(thumbX, trackY)
                    )
                }
                // Koyu iç gövde
                drawCircle(
                    color = IrPalette.HudGlass,
                    radius = r,
                    center = Offset(thumbX, trackY)
                )
                // Parlak beyaz halka
                drawCircle(
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
                    radius = r,
                    center = Offset(thumbX, trackY),
                    style = Stroke(width = 2.4.dp.toPx())
                )
                // Değiştirilmişse ortada sıcak amber nokta
                if (isModified && enabled) {
                    drawCircle(
                        color = IrPalette.IrAmber,
                        radius = 3.2.dp.toPx(),
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
            .size(width = 40.dp, height = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 30.dp, height = 25.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, IrPalette.BorderSubtle, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symbol,
                color = IrPalette.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun QuickPillAction(
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
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(50))
                .padding(horizontal = if (compact) 11.dp else 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            leadingIcon?.invoke(textColor)
            Text(
                text = text,
                color = textColor,
                fontSize = 12.sp,
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
private fun GridIcon(tint: Color, modifier: Modifier = Modifier) {
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
private fun ChevronIcon(tint: Color, modifier: Modifier = Modifier) {
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
private fun TimerIcon(tint: Color, modifier: Modifier = Modifier) {
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
private fun LevelIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.6.dp.toPx()
        val cy = size.height * 0.5f
        drawLine(tint, Offset(size.width * 0.08f, cy), Offset(size.width * 0.34f, cy), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.66f, cy), Offset(size.width * 0.92f, cy), stroke, cap = StrokeCap.Round)
        drawCircle(tint, radius = size.minDimension * 0.14f, center = Offset(size.width * 0.5f, cy), style = Stroke(width = stroke))
    }
}

@Composable
private fun ShieldIcon(tint: Color, modifier: Modifier = Modifier) {
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
private fun ZebraIcon(tint: Color, modifier: Modifier = Modifier) {
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
private fun TargetCrosshairIcon(tint: Color, modifier: Modifier = Modifier) {
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
private fun PipetteIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.5.dp.toPx()
        drawLine(tint, Offset(size.width * 0.22f, size.height * 0.78f), Offset(size.width * 0.70f, size.height * 0.30f), stroke, cap = StrokeCap.Round)
        drawCircle(tint, radius = size.minDimension * 0.16f, center = Offset(size.width * 0.76f, size.height * 0.24f), style = Stroke(width = stroke))
        drawLine(tint, Offset(size.width * 0.52f, size.height * 0.24f), Offset(size.width * 0.76f, size.height * 0.48f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun SparkleAutoIcon(tint: Color, modifier: Modifier = Modifier) {
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
private fun ResetIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.6.dp.toPx()
        drawArc(
            color = tint,
            startAngle = -60f,
            sweepAngle = 290f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(tint, Offset(size.width * 0.64f, size.height * 0.10f), Offset(size.width * 0.78f, size.height * 0.26f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.56f, size.height * 0.28f), Offset(size.width * 0.78f, size.height * 0.26f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun GalleryIcon(tint: Color, modifier: Modifier = Modifier) {
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
private fun PresetStarIcon(tint: Color, modifier: Modifier = Modifier) {
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
private fun WhiteBalanceIcon(tint: Color, modifier: Modifier = Modifier) {
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
private fun ExposureIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.4.dp.toPx()
        drawRoundRect(
            color = tint,
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = stroke)
        )
        // Çapraz bölme çizgisi + sol üstte artı, sağ altta eksi
        drawLine(tint, Offset(size.width * 0.22f, size.height * 0.78f), Offset(size.width * 0.78f, size.height * 0.22f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.22f, size.height * 0.32f), Offset(size.width * 0.44f, size.height * 0.32f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.33f, size.height * 0.21f), Offset(size.width * 0.33f, size.height * 0.43f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.56f, size.height * 0.70f), Offset(size.width * 0.78f, size.height * 0.70f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun ColorWheelIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.4.dp.toPx()
        val r = size.minDimension * 0.26f
        drawCircle(tint, radius = r, center = Offset(size.width * 0.50f, size.height * 0.34f), style = Stroke(width = stroke))
        drawCircle(tint, radius = r, center = Offset(size.width * 0.34f, size.height * 0.64f), style = Stroke(width = stroke))
        drawCircle(tint, radius = r, center = Offset(size.width * 0.66f, size.height * 0.64f), style = Stroke(width = stroke))
    }
}

@Composable
private fun MeteringFrameIcon(tint: Color, modifier: Modifier = Modifier) {
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
private fun CompareEyeIcon(tint: Color, modifier: Modifier = Modifier) {
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

private fun formatSigned(value: Float): String {
    val intVal = value.roundToInt()
    return when {
        intVal > 0 -> "+$intVal"
        intVal < 0 -> "−${abs(intVal)}"
        else -> "0"
    }
}

private fun formatSignedDecimal(value: Float, locale: Locale): String {
    val formatted = String.format(locale, "%.1f", abs(value))
    return when {
        value > 0.05f -> "+$formatted"
        value < -0.05f -> "−$formatted"
        else -> "0,0"
    }
}
