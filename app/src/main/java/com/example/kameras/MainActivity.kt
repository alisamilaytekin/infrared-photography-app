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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
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
    val VoidBlack = Color(0xFF07080C)
    val HudGlass = Color(0xCC0D1017)
    val HudCard = Color(0xD9141822)
    val HudChip = Color(0xCC1C2230)
    val IrCoral = Color(0xFFFF5722)
    val IrAmber = Color(0xFFF59E0B)
    val IrCyan = Color(0xFF38BDF8)
    val IrEmerald = Color(0xFF10B981)
    val IrTeal = Color(0xFF14B8A6)
    val IrMagenta = Color(0xFFE84393)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFFB8C4D6)
    val TextTertiary = Color(0xFF94A3B8)
    val BorderSubtle = Color(0x2EFFFFFF)
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

// Renk çipi alt tekil kaydırıcı seçimi
private enum class ColorSubSlider { DOYGUNLUK, KONTRAST }

// Noktasal renk pipeti alt tekil kaydırıcı seçimi
private enum class PointColorSubSlider { TON, DOYGUNLUK, PARLAKLIK }

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
 * Generates a hardware TonemapCurve with filmic highlight roll-off (soft shoulder),
 * preventing bright whites / specular highlights from harsh clipping into pure 1.0.
 */
private fun buildContrastTonemapCurve(
    contrast: Float,
    maxCurvePoints: Int
): TonemapCurve {
    val count = minOf(33, maxCurvePoints).coerceIn(2, 64)
    val curve = FloatArray(count * 2)
    val c = contrast.coerceIn(0.5f, 2.0f)

    for (i in 0 until count) {
        val x = i.toFloat() / (count - 1).toFloat()
        val y = when (i) {
            0 -> 0f
            count - 1 -> 1f
            else -> {
                val shaped = if (x < 0.45f) {
                    0.45f * (x / 0.45).pow(c.toDouble()).toFloat()
                } else {
                    1.0f - 0.55f * ((1.0 - x) / 0.55).pow(c.toDouble()).toFloat()
                }
                val shouldered = if (shaped > 0.72f) {
                    val over = shaped - 0.72f
                    0.72f + 0.28f * (over / (over + 0.18f))
                } else {
                    shaped
                }
                val clamped = shouldered.coerceIn(0f, 1f)
                if (i > 0) maxOf(clamped, curve[(i - 1) * 2 + 1]) else clamped
            }
        }
        curve[i * 2] = x
        curve[i * 2 + 1] = y
    }

    return TonemapCurve(curve, curve, curve)
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
 * Lightroom-style Point Color Targeted HSL processing with Hermite-cubic smoothstep feathering.
 * Applies Hue Shift, Saturation, and Luminance changes exclusively to the target color envelope.
 */
private fun applyTargetedHslToBitmap(
    bitmap: Bitmap,
    config: TargetedColorConfig
): Bitmap {
    if (!config.isActive || (config.deltaHue == 0f && config.deltaSat == 0f && config.deltaLum == 0f)) {
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

    val hsv = FloatArray(3)
    var y = 0
    while (y < h) {
        val rows = minOf(stripHeight, h - y)
        work.getPixels(rowBuf, 0, w, 0, y, w, rows)
        val count = w * rows
        for (i in 0 until count) {
            val px = rowBuf[i]
            val a = px and -0x1000000
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF

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
                    var newH = (hVal + dH * weight) % 360f
                    if (newH < 0f) newH += 360f
                    val newS = (sVal * (1.0f + dS * weight)).coerceIn(0f, 1f)
                    val newV = (vVal + dL * weight * 0.45f).coerceIn(0f, 1f)

                    hsv[0] = newH
                    hsv[1] = newS
                    hsv[2] = newV
                    val outRgb = AndroidColor.HSVToColor(a, hsv)
                    rowBuf[i] = outRgb
                }
            }
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
    val idealContrast = when {
        dynamicRange < 110 -> 1.32f
        dynamicRange < 150 -> 1.20f
        dynamicRange < 190 -> 1.10f
        dynamicRange > 232 -> 0.94f
        else -> 1.05f
    }

    val midLuma = (pLow + pHigh) * 0.5f
    val targetMid = 128f
    val neededRatio = targetMid / midLuma.coerceAtLeast(15f)
    val deltaStops = (ln(neededRatio.toDouble()) / ln(2.0)).toFloat()
    val deltaSteps = if (aeStep > 0.05f) (deltaStops / aeStep).roundToInt() else 0

    return AutoContrastResult(
        recommendedContrast = idealContrast,
        recommendedEvIndexDelta = deltaSteps,
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

    vec4 main(vec2 fragCoord) {
        vec4 color = uContent.eval(fragCoord);
        if (uActive < 0.5) return color;
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
            hsl.x = mod(hsl.x + uDeltaHue * weight + 360.0, 360.0);
            hsl.y = clamp(hsl.y * (1.0 + uDeltaSat * weight), 0.0, 1.0);
            hsl.z = clamp(hsl.z + uDeltaLum * weight * 0.45, 0.0, 1.0);
            color.rgb = hsl2rgb(hsl);
        }
        return color;
    }
"""

private fun updateTextureViewShader(
    view: TextureView?,
    config: TargetedColorConfig
) {
    if (view == null) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
            if (!config.isActive || (config.deltaHue == 0f && config.deltaSat == 0f && config.deltaLum == 0f)) {
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
            shader.setFloatUniform("uActive", 1.0f)
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

// Önizleme görüntüsünden normalize edilmiş 32 sütunlu parlaklık histogramı üretir
private fun computeLuminanceHistogram(bitmap: Bitmap, bins: Int = 32): FloatArray {
    val result = FloatArray(bins)
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return result

    val stepX = maxOf(1, w / 40)
    val stepY = maxOf(1, h / 40)
    val counts = IntArray(bins)
    var maxCount = 1

    var py = 0
    while (py < h) {
        var px = 0
        while (px < w) {
            val c = bitmap[px, py]
            val luma = (0.299f * AndroidColor.red(c) + 0.587f * AndroidColor.green(c) + 0.114f * AndroidColor.blue(c))
            val bin = ((luma / 256f) * bins).toInt().coerceIn(0, bins - 1)
            counts[bin]++
            if (counts[bin] > maxCount) maxCount = counts[bin]
            px += stepX
        }
        py += stepY
    }

    for (i in 0 until bins) {
        result[i] = (counts[i].toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
    }
    return result
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
        DngCreator(characteristics, result).use { dngCreator ->
            resolver.openOutputStream(uri)?.use { out ->
                dngCreator.writeImage(out, rawImage)
                out.flush()
            }
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

@Composable
private fun rememberGreenFade(
    decayDurationMs: Int = 650
): Pair<Float, (onClick: () -> Unit) -> Unit> {
    val anim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val trigger: (() -> Unit) -> Unit = { action ->
        scope.launch {
            anim.snapTo(1f)
            anim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = decayDurationMs, easing = LinearOutSlowInEasing)
            )
        }
        action()
    }
    return Pair(anim.value, trigger)
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
    var activeColorSubSlider by remember { mutableStateOf(ColorSubSlider.DOYGUNLUK) }
    var activePointColorSubSlider by remember { mutableStateOf(PointColorSubSlider.TON) }
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
    var targetBrightnessLevel by remember { mutableFloatStateOf(1.00f) }
    var measuredRegionBrightness by remember { mutableIntStateOf(128) }
    var liveHistogram by remember { mutableStateOf(FloatArray(32)) }

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

    LaunchedEffect(targetedColorConfig, textureViewInstance) {
        holder.currentTargetedColor = targetedColorConfig
        updateTextureViewShader(textureViewInstance, targetedColorConfig)
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
                        buildContrastTonemapCurve(contrastVal, holder.maxTonemapCurvePoints)
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
                val hist = computeLuminanceHistogram(bmp, 32)
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
                    contrastValue = res.recommendedContrast
                    if (res.recommendedEvIndexDelta != 0 && aeRange.upper > aeRange.lower) {
                        exposureEvIndex = (exposureEvIndex + res.recommendedEvIndexDelta).coerceIn(aeRange.lower, aeRange.upper)
                    }
                    updatePreviewSettings()
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
            coroutineScope.launch(Dispatchers.Default) {
                val hasSelectiveHsl = snapTargetedColor.isActive &&
                    (snapTargetedColor.deltaHue != 0f || snapTargetedColor.deltaSat != 0f || snapTargetedColor.deltaLum != 0f)

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
                                graded = applyTargetedHslToBitmap(decoded, snapTargetedColor)
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
    val turkishLocale = remember { Locale("tr", "TR") }

    // Durum çubuğu metin kaynakları
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

    // Ana dikey yerleşim: Üst durum çubuğu → Önizleme (panel asla üstüne binmez) → Alt kontroller
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IrPalette.VoidBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1) ÜST DURUM ÇUBUĞU: Okunur, etiketli ve dokunulabilir değerler
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Sol: Oto / Pro mod anahtarı (Oto yeşil, Pro amber)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(IrPalette.HudGlass)
                    .border(1.dp, IrPalette.BorderSubtle, RoundedCornerShape(50))
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (otoFlash, triggerOto) = rememberGreenFade()
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = cameraReady) {
                            triggerOto {
                                isPickerActive = false
                                isColorPipetteActive = false
                                isExposureBoxPicking = false
                                isAutoMode = true
                                updatePreviewSettings()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                lerp(
                                    if (isAutoMode) IrPalette.IrEmerald else Color.Transparent,
                                    Color(0xFF00E676).copy(alpha = 0.85f),
                                    otoFlash
                                )
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strModeAuto,
                            color = lerp(if (isAutoMode) Color.Black else IrPalette.TextSecondary, Color.Black, otoFlash),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val (proFlash, triggerPro) = rememberGreenFade()
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = cameraReady) {
                            triggerPro {
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
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                lerp(
                                    if (!isAutoMode) IrPalette.IrAmber else Color.Transparent,
                                    Color(0xFF00E676).copy(alpha = 0.85f),
                                    proFlash
                                )
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strModePro,
                            color = lerp(if (!isAutoMode) Color.Black else IrPalette.TextSecondary, Color.Black, proFlash),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Orta: Etiketli, dokunulabilir durum değerleri (BD · Ton · EV)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusTelemetryChip(
                    text = "$strWbShort ${displayKelvin.toInt()}K",
                    accentColor = if (!isAutoMode && activeTab == ControlTab.BD && !isDeckCollapsed) IrPalette.IrAmber else IrPalette.TextPrimary,
                    enabled = cameraReady,
                    onClick = { openProControl(ControlTab.BD) }
                )
                if (!isAutoMode) {
                    Text(
                        text = "·",
                        color = IrPalette.TextTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 1.dp)
                    )
                    StatusTelemetryChip(
                        text = "$strTintShort ${formatSigned(displayTint)}",
                        accentColor = if (activeTab == ControlTab.BD && !isDeckCollapsed) IrPalette.IrAmber else IrPalette.TextPrimary,
                        enabled = cameraReady,
                        onClick = { openProControl(ControlTab.BD) }
                    )
                }
                if (aeRange.upper > aeRange.lower) {
                    Text(
                        text = "·",
                        color = IrPalette.TextTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 1.dp)
                    )
                    val evFormatted = formatSignedDecimal(evDisplayVal, turkishLocale)
                    StatusTelemetryChip(
                        text = "$strEvShort $evFormatted",
                        accentColor = if (!isAutoMode && activeTab == ControlTab.POZ && !isDeckCollapsed) IrPalette.IrAmber else IrPalette.TextPrimary,
                        enabled = cameraReady,
                        onClick = { openProControl(ControlTab.POZ) }
                    )
                }
            }

            // Sağ: Çözünürlük + Izgara düğmeleri (en az 48x48dp dokunma alanı)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (resFlash, triggerRes) = rememberGreenFade()
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = availableResolutions.isNotEmpty()) {
                            triggerRes { showResolutionDialog = true }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(lerp(IrPalette.HudCard, Color(0xFF00E676).copy(alpha = 0.85f), resFlash))
                            .border(1.dp, lerp(IrPalette.BorderSubtle, Color(0xFF00E676), resFlash), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        val activeRes = selectedResolution
                        val resLabel = if (activeRes != null) formatMegapixels(activeRes) else strResFallback
                        Text(
                            text = resLabel,
                            color = lerp(IrPalette.IrCyan, Color.Black, resFlash),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // RAW/DNG anahtarı (yalnızca RAW sensör destekleyen cihazlarda görünür)
                if (supportsRaw) {
                    val (rawFlash, triggerRaw) = rememberGreenFade()
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(enabled = cameraReady) {
                                triggerRaw {
                                    rawEnabled = !rawEnabled
                                    holder.rawCaptureEnabled = rawEnabled
                                    showStatus(if (rawEnabled) strRawOn else strRawOff)
                                    cameraHandler.post { closeCameraInternal(releaseReader = true) }
                                    surfaceGeneration += 1
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    lerp(
                                        if (rawEnabled) IrPalette.IrAmber.copy(alpha = 0.28f) else IrPalette.HudCard,
                                        Color(0xFF00E676).copy(alpha = 0.85f),
                                        rawFlash
                                    )
                                )
                                .border(
                                    1.dp,
                                    lerp(if (rawEnabled) IrPalette.IrAmber else IrPalette.BorderSubtle, Color(0xFF00E676), rawFlash),
                                    RoundedCornerShape(50)
                                )
                                .padding(horizontal = 7.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = strRaw,
                                color = lerp(if (rawEnabled) IrPalette.IrAmber else IrPalette.TextSecondary, Color.Black, rawFlash),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                val (gridFlash, triggerGrid) = rememberGreenFade()
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = strGridCd }
                        .clip(CircleShape)
                        .clickable { triggerGrid { showGrid = !showGrid } },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                lerp(
                                    if (showGrid) IrPalette.IrAmber.copy(alpha = 0.24f) else IrPalette.HudCard,
                                    Color(0xFF00E676).copy(alpha = 0.85f),
                                    gridFlash
                                )
                            )
                            .border(
                                1.dp,
                                lerp(if (showGrid) IrPalette.IrAmber else IrPalette.BorderSubtle, Color(0xFF00E676), gridFlash),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        GridIcon(
                            tint = lerp(if (showGrid) IrPalette.IrAmber else IrPalette.TextSecondary, Color.Black, gridFlash),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Zamanlayıcı (Kapalı -> 3 sn -> 10 sn)
                val (timerFlash, triggerTimer) = rememberGreenFade()
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable {
                            triggerTimer {
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
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                lerp(
                                    if (timerSeconds > 0) IrPalette.IrAmber.copy(alpha = 0.24f) else IrPalette.HudCard,
                                    Color(0xFF00E676).copy(alpha = 0.85f),
                                    timerFlash
                                )
                            )
                            .border(
                                1.dp,
                                lerp(if (timerSeconds > 0) IrPalette.IrAmber else IrPalette.BorderSubtle, Color(0xFF00E676), timerFlash),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (timerSeconds > 0) "${timerSeconds}s" else "⏱",
                            color = lerp(if (timerSeconds > 0) IrPalette.IrAmber else IrPalette.TextSecondary, Color.Black, timerFlash),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
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
                    .clip(RoundedCornerShape(10.dp))
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
                                                pickedMarkerPos = offset
                                                syncHolderSettings()
                                                updateTextureViewShader(view, targetedColorConfig)
                                                showStatus("${getHueName(hsv[0])} seçildi")
                                            } finally {
                                                bitmap.recycle()
                                            }
                                        }
                                    } else {
                                        // Dokunulan noktaya ölçüm kutusunu taşı ve donanımsal AE bölgesini güncelle
                                        // (Yazılımsal EV sıçraması yapılmaz, pozlama stabil kalır)
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
                            // İki parmakla büyütme/küçültme: Ölçüm kutusu boyutunu ayarlama
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

                // Izgara, ölçüm kutusu ve seçici işareti
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

                    // Su terazisi (ufuk çizgisi): ±1.5° içinde yeşil yanar
                    if (showLevel) {
                        val isFlat = abs(levelAngleDeg) <= 1.5f
                        val levelColor = if (isFlat) IrPalette.IrEmerald else Color.White.copy(alpha = 0.80f)
                        val cx = size.width * 0.5f
                        val cy = size.height * 0.5f
                        val halfLen = minOf(size.width, size.height) * 0.22f
                        val rad = Math.toRadians((-levelAngleDeg).toDouble())
                        val dx = (cos(rad) * halfLen).toFloat()
                        val dy = (sin(rad) * halfLen).toFloat()
                        val strokeW = if (isFlat) 2.2.dp.toPx() else 1.5.dp.toPx()
                        // Sabit referans yan çentikleri
                        drawLine(
                            Color.White.copy(alpha = 0.35f),
                            Offset(cx - halfLen - 16.dp.toPx(), cy),
                            Offset(cx - halfLen - 6.dp.toPx(), cy),
                            strokeWidth = 1.5.dp.toPx()
                        )
                        drawLine(
                            Color.White.copy(alpha = 0.35f),
                            Offset(cx + halfLen + 6.dp.toPx(), cy),
                            Offset(cx + halfLen + 16.dp.toPx(), cy),
                            strokeWidth = 1.5.dp.toPx()
                        )
                        // Eğime göre dönen ufuk çizgisi
                        drawLine(
                            color = levelColor,
                            start = Offset(cx - dx, cy - dy),
                            end = Offset(cx + dx, cy + dy),
                            strokeWidth = strokeW
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
                            color = boxColor.copy(alpha = 0.08f),
                            topLeft = Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(boxSide, boxSide)
                        )
                        drawLine(boxColor, Offset(left, top), Offset(left + corner, top), strokeW)
                        drawLine(boxColor, Offset(left, top), Offset(left, top + corner), strokeW)
                        drawLine(boxColor, Offset(right, top), Offset(right - corner, top), strokeW)
                        drawLine(boxColor, Offset(right, top), Offset(right, top + corner), strokeW)
                        drawLine(boxColor, Offset(left, bottom), Offset(left + corner, bottom), strokeW)
                        drawLine(boxColor, Offset(left, bottom), Offset(left, bottom - corner), strokeW)
                        drawLine(boxColor, Offset(right, bottom), Offset(right - corner, bottom), strokeW)
                        drawLine(boxColor, Offset(right, bottom), Offset(right, bottom - corner), strokeW)
                        drawCircle(
                            color = boxColor.copy(alpha = 0.85f),
                            radius = 4.dp.toPx(),
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

                // Geçici bildirim veya OTO karşılaştırma rozeti
                val topBannerMsg = if (isComparingAuto) strCompareBanner else statusBannerText
                AnimatedVisibility(
                    visible = topBannerMsg != null,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(250)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                ) {
                    topBannerMsg?.let { msg ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isComparingAuto) IrPalette.IrEmerald.copy(alpha = 0.88f)
                                    else Color.Black.copy(alpha = 0.72f)
                                )
                                .border(1.dp, IrPalette.BorderSubtle, RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = msg,
                                color = if (isComparingAuto) Color.Black else IrPalette.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Zamanlayıcı geri sayım göstergesi
                if (timerCountdown > 0) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.62f))
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

                // PRO modda sol alt köşede basılı tutunca OTO önizlemesi gösteren "Karşılaştır" düğmesi
                if (cameraReady && !isAutoMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
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
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isComparingAuto) IrPalette.IrEmerald.copy(alpha = 0.88f)
                                    else Color.Black.copy(alpha = 0.56f)
                                )
                                .border(
                                    1.dp,
                                    if (isComparingAuto) IrPalette.IrEmerald else Color.White.copy(alpha = 0.22f),
                                    RoundedCornerShape(50)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = strCompare,
                                color = if (isComparingAuto) Color.Black else IrPalette.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Önizleme alt ortasında mercek / yakınlaştırma seçici (0.5x · 1x · 2x)
                if (cameraReady && availableZoomOptions.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.56f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        availableZoomOptions.forEach { z ->
                            val isSelected = abs(zoomRatio - z) < 0.08f
                            val zLabel = if (z == 0.5f) "0,5x" else "${z.toInt()}x"
                            Box(
                                modifier = Modifier
                                    .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) IrPalette.IrAmber else Color.Transparent)
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
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Önizleme sağ alt köşesinde küçük, yarı saydam canlı histogram
                if (cameraReady) {
                    LiveHistogramOverlay(
                        bins = liveHistogram,
                        accentColor = if (isAutoMode || isComparingAuto) IrPalette.IrEmerald else IrPalette.IrAmber,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    )
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

        // 3) ALT KONTROL ALANI: Önizlemenin altında ayrı bölüm (görüntüyü asla kapatmaz)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(IrPalette.VoidBlack)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // OTO Modda kompakt Pozlama (EV) kaydırıcısı
            AnimatedVisibility(visible = isAutoMode && aeRange.upper > aeRange.lower) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(IrPalette.HudGlass)
                        .border(1.dp, IrPalette.BorderSubtle, RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    val evBadge = "${formatSignedDecimal(evDisplayVal, turkishLocale)} $strEvShort"
                    ProGradientSliderRow(
                        label = stringResource(R.string.slider_exposure),
                        valueBadge = evBadge,
                        value = exposureEvIndex.toFloat(),
                        defaultValue = 0f,
                        snapTolerance = 0.45f,
                        valueRange = aeRange.lower.toFloat()..aeRange.upper.toFloat(),
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
            }

            // PRO Mod Çip Tabanlı Tek Kaydırıcı Paneli
            AnimatedVisibility(visible = !isAutoMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(IrPalette.HudGlass)
                        .border(1.dp, IrPalette.BorderSubtle, RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Seçili çipin tek/kompakt kaydırıcı alanı
                    AnimatedVisibility(
                        visible = !isDeckCollapsed,
                        enter = fadeIn() + slideInVertically { it / 4 },
                        exit = fadeOut() + slideOutVertically { it / 4 }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            when (activeTab) {
                                ControlTab.BD -> {
                                    // Beyaz Dengesi: Kelvin ve Ton alt alta, kompakt
                                    ProGradientSliderRow(
                                        label = stringResource(R.string.slider_kelvin),
                                        valueBadge = "${kelvinValue.toInt()} K",
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

                                    ProGradientSliderRow(
                                        label = stringResource(R.string.slider_tint),
                                        valueBadge = formatSigned(tintValue),
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
                                            tintValue = 0f
                                            updatePreviewSettings()
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onValueChange = {
                                            tintValue = it
                                            updatePreviewSettings()
                                        }
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ModePillButton(
                                            label = stringResource(R.string.action_auto_calibrate),
                                            selected = false,
                                            accentColor = IrPalette.IrAmber,
                                            enabled = cameraReady,
                                            modifier = Modifier.weight(1f),
                                            onClick = { runAutoCalibrate() }
                                        )
                                        ModePillButton(
                                            label = if (isPickerActive) stringResource(R.string.action_spot_wb_active) else stringResource(R.string.action_spot_wb),
                                            selected = isPickerActive,
                                            accentColor = IrPalette.IrAmber,
                                            enabled = cameraReady,
                                            modifier = Modifier.weight(1f),
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
                                    // Pozlama (EV) tek kaydırıcı + kalkan ikonlu kompakt Vurgu Koruma anahtarı
                                    if (aeRange.upper > aeRange.lower) {
                                        val minEv = aeRange.lower.toFloat()
                                        val maxEv = aeRange.upper.toFloat()
                                        val evBadge = "${formatSignedDecimal(evDisplayVal, turkishLocale)} $strEvShort"
                                        ProGradientSliderRow(
                                            label = stringResource(R.string.slider_exposure),
                                            valueBadge = evBadge,
                                            value = exposureEvIndex.toFloat(),
                                            defaultValue = 0f,
                                            snapTolerance = 0.45f,
                                            valueRange = minEv..maxEv,
                                            enabled = cameraReady,
                                            gradient = Brush.horizontalGradient(
                                                colors = listOf(Color(0xFF1E293B), Color(0xFF94A3B8), Color(0xFFFEF08A))
                                            ),
                                            trailingHeaderContent = {
                                                HighlightGuardToggle(
                                                    enabled = highlightProtectionEnabled,
                                                    interactive = cameraReady,
                                                    onToggle = {
                                                        highlightProtectionEnabled = !highlightProtectionEnabled
                                                        measureAndApplyExposureMetering(applyEvAdjust = highlightProtectionEnabled)
                                                        showStatus(if (highlightProtectionEnabled) strHighlightOn else strHighlightOff)
                                                    }
                                                )
                                            },
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ModePillButton(
                                            label = stringResource(R.string.action_zebra),
                                            selected = showZebra,
                                            accentColor = IrPalette.IrCoral,
                                            enabled = cameraReady,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                showZebra = !showZebra
                                                showStatus(if (showZebra) strZebraOn else strZebraOff)
                                            }
                                        )
                                        ModePillButton(
                                            label = stringResource(R.string.action_level),
                                            selected = showLevel,
                                            accentColor = IrPalette.IrEmerald,
                                            enabled = cameraReady,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                showLevel = !showLevel
                                                showStatus(if (showLevel) strLevelOn else strLevelOff)
                                            }
                                        )
                                    }
                                }

                                ControlTab.RENK -> {
                                    // Renk: Ekranda yalnızca tek kaydırıcı
                                    if (targetedColorConfig.isActive) {
                                        // Noktasal renk seçili: renk başlığı + genişlik + tekil alt kaydırıcı seçici
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(targetedColorConfig.previewColorInt))
                                                        .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                                                )
                                                Text(
                                                    text = "${getHueName(targetedColorConfig.targetHue)} (${targetedColorConfig.targetHue.toInt()}°)",
                                                    color = IrPalette.IrTeal,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
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
                                                            .background(if (isSel) IrPalette.IrAmber else Color.White.copy(alpha = 0.08f))
                                                            .clickable {
                                                                targetedColorConfig = targetedColorConfig.copy(
                                                                    hueWidth = w,
                                                                    hueFeather = if (w == 14f) 12f else if (w == 22f) 18f else 26f
                                                                )
                                                            }
                                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                                    ) {
                                                        Text(
                                                            text = lbl,
                                                            color = if (isSel) Color.Black else IrPalette.TextSecondary,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = stringResource(R.string.action_remove),
                                                    color = IrPalette.IrCoral,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            targetedColorConfig = targetedColorConfig.copy(isActive = false)
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                                )
                                            }
                                        }

                                        // Tekil noktasal renk kaydırıcı seçici
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            ModePillButton(
                                                label = stringResource(R.string.slider_point_hue),
                                                selected = activePointColorSubSlider == PointColorSubSlider.TON,
                                                accentColor = IrPalette.IrAmber,
                                                enabled = cameraReady,
                                                modifier = Modifier.weight(1f),
                                                onClick = { activePointColorSubSlider = PointColorSubSlider.TON }
                                            )
                                            ModePillButton(
                                                label = stringResource(R.string.slider_saturation),
                                                selected = activePointColorSubSlider == PointColorSubSlider.DOYGUNLUK,
                                                accentColor = IrPalette.IrAmber,
                                                enabled = cameraReady,
                                                modifier = Modifier.weight(1f),
                                                onClick = { activePointColorSubSlider = PointColorSubSlider.DOYGUNLUK }
                                            )
                                            ModePillButton(
                                                label = stringResource(R.string.slider_point_lum),
                                                selected = activePointColorSubSlider == PointColorSubSlider.PARLAKLIK,
                                                accentColor = IrPalette.IrAmber,
                                                enabled = cameraReady,
                                                modifier = Modifier.weight(1f),
                                                onClick = { activePointColorSubSlider = PointColorSubSlider.PARLAKLIK }
                                            )
                                        }

                                        when (activePointColorSubSlider) {
                                            PointColorSubSlider.TON -> ProGradientSliderRow(
                                                label = stringResource(R.string.slider_point_hue),
                                                valueBadge = "${formatSigned(targetedColorConfig.deltaHue)}°",
                                                value = targetedColorConfig.deltaHue,
                                                defaultValue = 0f,
                                                snapTolerance = 3f,
                                                valueRange = -60f..60f,
                                                enabled = cameraReady,
                                                gradient = Brush.horizontalGradient(
                                                    listOf(IrPalette.IrCyan, IrPalette.IrAmber, IrPalette.IrMagenta)
                                                ),
                                                onStepMinus = {
                                                    targetedColorConfig = targetedColorConfig.copy(
                                                        deltaHue = (targetedColorConfig.deltaHue - 5f).coerceIn(-60f, 60f)
                                                    )
                                                },
                                                onStepPlus = {
                                                    targetedColorConfig = targetedColorConfig.copy(
                                                        deltaHue = (targetedColorConfig.deltaHue + 5f).coerceIn(-60f, 60f)
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

                                            PointColorSubSlider.DOYGUNLUK -> ProGradientSliderRow(
                                                label = stringResource(R.string.slider_point_sat),
                                                valueBadge = "${formatSigned(targetedColorConfig.deltaSat * 100f)}%",
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
                                        // Genel renk ayarları: Doygunluk / Kontrast seçicisi + tek kaydırıcı
                                        if (activeColorSubSlider == ColorSubSlider.DOYGUNLUK) {
                                            ProGradientSliderRow(
                                                label = stringResource(R.string.slider_saturation),
                                                valueBadge = "${String.format(turkishLocale, "%.2f", saturationValue)}x",
                                                value = saturationValue,
                                                defaultValue = DEFAULT_SATURATION,
                                                snapTolerance = 0.04f,
                                                valueRange = 0.50f..2.00f,
                                                enabled = cameraReady,
                                                // Gri → canlı renk geçişi
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
                                                valueBadge = "${String.format(turkishLocale, "%.2f", contrastValue)}x",
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
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ModePillButton(
                                                label = stringResource(R.string.slider_saturation),
                                                selected = activeColorSubSlider == ColorSubSlider.DOYGUNLUK,
                                                accentColor = IrPalette.IrAmber,
                                                enabled = cameraReady,
                                                modifier = Modifier.weight(1f),
                                                onClick = { activeColorSubSlider = ColorSubSlider.DOYGUNLUK }
                                            )
                                            ModePillButton(
                                                label = stringResource(R.string.slider_contrast),
                                                selected = activeColorSubSlider == ColorSubSlider.KONTRAST,
                                                accentColor = IrPalette.IrAmber,
                                                enabled = cameraReady,
                                                modifier = Modifier.weight(1f),
                                                onClick = { activeColorSubSlider = ColorSubSlider.KONTRAST }
                                            )
                                            ModePillButton(
                                                label = if (isColorPipetteActive) stringResource(R.string.action_color_pipette_active) else stringResource(R.string.action_color_pipette),
                                                selected = isColorPipetteActive,
                                                accentColor = IrPalette.IrTeal,
                                                enabled = cameraReady,
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    isColorPipetteActive = !isColorPipetteActive
                                                    if (isColorPipetteActive) {
                                                        isPickerActive = false
                                                        isExposureBoxPicking = false
                                                        showStatus(strTapColor)
                                                    }
                                                }
                                            )
                                            ModePillButton(
                                                label = stringResource(R.string.action_auto_contrast),
                                                selected = false,
                                                accentColor = IrPalette.IrAmber,
                                                enabled = cameraReady,
                                                modifier = Modifier.weight(1f),
                                                onClick = { runAutoContrast() }
                                            )
                                        }
                                    }
                                }

                                ControlTab.OLCUM -> {
                                    // Ölçüm modu: Tüm ekran / Kutu ölçüm
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ModePillButton(
                                            label = stringResource(R.string.metering_full),
                                            selected = meteringMode == ExposureMeteringMode.FULL_SCREEN,
                                            accentColor = IrPalette.IrAmber,
                                            enabled = cameraReady,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                isExposureBoxPicking = false
                                                meteringMode = ExposureMeteringMode.FULL_SCREEN
                                                updatePreviewSettings()
                                                measureAndApplyExposureMetering(applyEvAdjust = false)
                                            }
                                        )
                                        ModePillButton(
                                            label = stringResource(R.string.metering_box),
                                            selected = meteringMode == ExposureMeteringMode.BOX_SPOT,
                                            accentColor = IrPalette.IrAmber,
                                            enabled = cameraReady,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                meteringMode = ExposureMeteringMode.BOX_SPOT
                                                updatePreviewSettings()
                                                measureAndApplyExposureMetering(applyEvAdjust = false)
                                            }
                                        )
                                    }

                                    // Kutu modundaysa tek seçimli (radio) boyut seçimi + merkeze al + iki parmak ipucu
                                    if (meteringMode == ExposureMeteringMode.BOX_SPOT) {
                                        val selectedBoxSize = MeteringBoxSize.entries.minByOrNull {
                                            abs(exposureBoxSizeNorm - it.norm)
                                        } ?: MeteringBoxSize.ORTA

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            listOf(
                                                MeteringBoxSize.KUCUK to stringResource(R.string.metering_size_small),
                                                MeteringBoxSize.ORTA to stringResource(R.string.metering_size_medium),
                                                MeteringBoxSize.BUYUK to stringResource(R.string.metering_size_large)
                                            ).forEach { (sizeEnum, label) ->
                                                ModePillButton(
                                                    label = label,
                                                    selected = selectedBoxSize == sizeEnum,
                                                    accentColor = IrPalette.IrAmber,
                                                    enabled = cameraReady,
                                                    modifier = Modifier.weight(1f),
                                                    onClick = {
                                                        exposureBoxSizeNorm = sizeEnum.norm
                                                        updatePreviewSettings()
                                                        measureAndApplyExposureMetering(applyEvAdjust = false)
                                                    }
                                                )
                                            }
                                            ModePillButton(
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
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Yatay Çip Satırı: [BD] [Poz] [Renk] [Ölçüm] + tek panel aç/kapa ikonu (⌄ / ⌃)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                ControlTab.BD to stringResource(R.string.chip_wb),
                                ControlTab.POZ to stringResource(R.string.chip_exposure),
                                ControlTab.RENK to stringResource(R.string.chip_color),
                                ControlTab.OLCUM to stringResource(R.string.chip_metering)
                            ).forEach { (tab, title) ->
                                TabChip(
                                    title = title,
                                    selected = activeTab == tab && !isDeckCollapsed,
                                    modifier = Modifier.weight(1f),
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

                        // Paneli açma/kapatma için tek ⌄ / ⌃ ikonu (48x48dp dokunma alanı)
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
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isDeckCollapsed) IrPalette.IrAmber.copy(alpha = 0.22f)
                                        else Color.White.copy(alpha = 0.08f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isDeckCollapsed) IrPalette.IrAmber else IrPalette.BorderSubtle,
                                        RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isDeckCollapsed) "⌃" else "⌄",
                                    color = if (isDeckCollapsed) IrPalette.IrAmber else IrPalette.TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // ALT DEKLANŞÖR ÇUBUĞU: Simetrik, temiz, her modda beyaz deklanşör
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Sol: Galeri küçük resmi + Ön ayarlar düğmesi
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = strGalleryCd }
                            .clip(CircleShape)
                            .background(IrPalette.HudCard)
                            .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
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
                            Text(
                                text = "▣",
                                color = IrPalette.TextSecondary,
                                fontSize = 18.sp
                            )
                        }
                    }

                    QuickPillAction(
                        text = stringResource(R.string.action_presets),
                        highlighted = showPresetsListDialog,
                        compact = true,
                        enabled = cameraReady,
                        onClick = { showPresetsListDialog = true }
                    )
                }

                // Orta: Her modda beyaz halkalı beyaz daire deklanşör
                val (shutterFlash, triggerShutter) = rememberGreenFade(decayDurationMs = 750)
                val innerSize by animateDpAsState(
                    targetValue = if (isShutterPressed) 48.dp else 56.dp,
                    animationSpec = tween(100),
                    label = "shutterSize"
                )
                val baseShutterColor = if (!cameraReady) Color.Gray else Color.White
                val shutterInnerColor = lerp(baseShutterColor, Color(0xFF00E676), shutterFlash)
                val shutterBorderColor = lerp(
                    Color.White.copy(alpha = if (cameraReady) 0.95f else 0.35f),
                    Color(0xFF00E676),
                    shutterFlash
                )
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .semantics { contentDescription = strShutterCd }
                        .clip(CircleShape)
                        .border(3.dp, shutterBorderColor, CircleShape)
                        .clickable(
                            enabled = cameraReady,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            triggerShutter { triggerCaptureWithTimer() }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(innerSize)
                            .clip(CircleShape)
                            .background(shutterInnerColor)
                    )
                }

                // Sağ: PRO modda Tümünü sıfırla (onaylı), OTO modda Ölçüm modu geçişi
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (!isAutoMode) {
                        QuickPillAction(
                            text = stringResource(R.string.action_reset_all),
                            highlighted = false,
                            compact = true,
                            enabled = cameraReady,
                            onClick = { showResetAllDialog = true }
                        )
                    } else {
                        QuickPillAction(
                            text = if (meteringMode == ExposureMeteringMode.BOX_SPOT) {
                                stringResource(R.string.metering_box)
                            } else {
                                stringResource(R.string.metering_full)
                            },
                            highlighted = meteringMode == ExposureMeteringMode.BOX_SPOT,
                            compact = true,
                            enabled = cameraReady,
                            onClick = {
                                meteringMode = if (meteringMode == ExposureMeteringMode.FULL_SCREEN) {
                                    ExposureMeteringMode.BOX_SPOT
                                } else {
                                    ExposureMeteringMode.FULL_SCREEN
                                }
                                updatePreviewSettings()
                                measureAndApplyExposureMetering(applyEvAdjust = false)
                            }
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
            containerColor = Color(0xFF141822),
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
                        tintValue = 0f
                        exposureEvIndex = 0
                        saturationValue = DEFAULT_SATURATION
                        contrastValue = DEFAULT_CONTRAST
                        targetedColorConfig = TargetedColorConfig()
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
            containerColor = Color(0xFF141822),
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
                            .clickable {
                                showPresetsListDialog = false
                                showSaveDialog = true
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
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
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isMatching) IrPalette.IrAmber.copy(alpha = 0.20f)
                                        else Color.White.copy(alpha = 0.05f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isMatching) IrPalette.IrAmber else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(10.dp)
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
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            containerColor = Color(0xFF141822),
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
            availableResolutions = availableResolutions,
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

    // 3 Adımlı İlk Açılış Rehberi (Noktasal BD, Oto-kalibre & Ölçüm Kutusu)
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
            containerColor = Color(0xFF141822),
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
private fun StatusTelemetryChip(
    text: String,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .sizeIn(minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = accentColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun LiveHistogramOverlay(
    bins: FloatArray,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(96.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.52f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (bins.isEmpty()) return@Canvas
            val w = size.width
            val h = size.height
            val step = w / (bins.size - 1).coerceAtLeast(1)

            val fillPath = Path().apply {
                moveTo(0f, h)
                bins.forEachIndexed { idx, v ->
                    val x = idx * step
                    val y = h - (v.coerceIn(0f, 1f) * h)
                    lineTo(x, y)
                }
                lineTo(w, h)
                close()
            }
            drawPath(
                path = fillPath,
                color = accentColor.copy(alpha = 0.36f),
                style = Fill
            )

            val strokePath = Path().apply {
                bins.forEachIndexed { idx, v ->
                    val x = idx * step
                    val y = h - (v.coerceIn(0f, 1f) * h)
                    if (idx == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            drawPath(
                path = strokePath,
                color = Color.White.copy(alpha = 0.85f),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }
    }
}

@Composable
private fun HighlightGuardToggle(
    enabled: Boolean,
    interactive: Boolean,
    onToggle: () -> Unit
) {
    val label = stringResource(R.string.action_highlight_guard)
    Row(
        modifier = Modifier
            .sizeIn(minHeight = 48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = interactive, onClick = onToggle)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (enabled) IrPalette.IrTeal.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f))
                .border(1.dp, if (enabled) IrPalette.IrTeal else IrPalette.BorderSubtle, RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Text(
                text = "🛡 $label",
                color = if (enabled) IrPalette.IrTeal else IrPalette.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
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
    // Birbirine %8'den yakın çözünürlükleri (örn. 4080×3060 / 4000×3000) "Gelişmiş" bölümüne ayır
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
        containerColor = Color(0xFF141822),
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
                // Adım 1: Oran çipleri (4:3 · 16:9 · 1:1 · 20:9)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(availableRatios) { ratioTag ->
                        val isSel = ratioTag == selectedRatioTag
                        Box(
                            modifier = Modifier
                                .sizeIn(minWidth = 54.dp, minHeight = 48.dp)
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

                // Adım 2: Seçilen orana ait MP seçenekleri
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
                                Text(
                                    text = if (showAdvanced) "⌃" else "⌄",
                                    color = IrPalette.TextSecondary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
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

@Composable
private fun ModePillButton(
    label: String,
    selected: Boolean,
    accentColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val (greenPulse, triggerClick) = rememberGreenFade()
    val bgAlpha by animateFloatAsState(if (selected) 0.24f else 0.06f, label = "modeBg")
    val baseBg = if (selected) accentColor.copy(alpha = bgAlpha) else Color.White.copy(alpha = 0.06f)
    val bg = lerp(baseBg, Color(0xFF00E676).copy(alpha = 0.85f), greenPulse)
    val baseBorder = if (selected) accentColor.copy(alpha = 0.85f) else IrPalette.BorderSubtle
    val border = lerp(baseBorder, Color(0xFF00E676), greenPulse)

    Box(
        modifier = modifier
            .sizeIn(minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { triggerClick(onClick) },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(10.dp))
                .padding(horizontal = 6.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val baseText = if (selected) accentColor else IrPalette.TextPrimary
            val textColor = lerp(baseText, Color.Black, greenPulse)
            Text(
                text = label,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val (greenPulse, triggerClick) = rememberGreenFade()
    val baseBg = if (selected) IrPalette.IrAmber.copy(alpha = 0.24f) else Color.Transparent
    val bg = lerp(baseBg, Color(0xFF00E676).copy(alpha = 0.85f), greenPulse)
    val baseBorder = if (selected) IrPalette.IrAmber else Color.Transparent
    val border = lerp(baseBorder, Color(0xFF00E676), greenPulse)
    val baseText = if (selected) IrPalette.IrAmber else IrPalette.TextSecondary
    val textColor = lerp(baseText, Color.Black, greenPulse)

    Box(
        modifier = modifier
            .sizeIn(minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { triggerClick(onClick) },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(10.dp))
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ProGradientSliderRow(
    label: String,
    valueBadge: String,
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
    var wasSnapped by remember { mutableStateOf(abs(value - defaultValue) <= snapTolerance * 0.5f) }

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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable(enabled = enabled, onClick = onReset)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = valueBadge,
                        color = IrPalette.TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
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

        // Kaydırıcı: varsayılan değerde görünür çentik + hafif yapışma (haptic) + çift dokunmayla sıfırlama
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(enabled, onReset) {
                    if (enabled) {
                        detectTapGestures(onDoubleTap = { onReset() })
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Arka plan gradyan çubuğu + varsayılan değer çentiği
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(gradient)
                )
                val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
                val defaultFraction = ((defaultValue - valueRange.start) / span).coerceIn(0f, 1f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val tickX = size.width * defaultFraction
                    drawLine(
                        color = Color.White.copy(alpha = 0.90f),
                        start = Offset(tickX, 0f),
                        end = Offset(tickX, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = { raw ->
                    val snapped = if (abs(raw - defaultValue) <= snapTolerance) {
                        if (!wasSnapped) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            wasSnapped = true
                        }
                        defaultValue
                    } else {
                        wasSnapped = false
                        raw
                    }
                    onValueChange(snapped)
                },
                valueRange = valueRange,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    disabledThumbColor = Color.White.copy(alpha = 0.35f),
                    disabledActiveTrackColor = Color.Transparent,
                    disabledInactiveTrackColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val (greenPulse, triggerClick) = rememberGreenFade(decayDurationMs = 450)
    val bg = lerp(Color.White.copy(alpha = 0.12f), Color(0xFF00E676).copy(alpha = 0.9f), greenPulse)
    val textColor = lerp(IrPalette.TextPrimary, Color.Black, greenPulse)
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { triggerClick(onClick) },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 26.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(bg)
                .border(1.dp, lerp(Color.Transparent, Color(0xFF00E676), greenPulse), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symbol,
                color = textColor,
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
    onClick: () -> Unit
) {
    val (greenPulse, triggerClick) = rememberGreenFade()
    val baseBg = if (highlighted) IrPalette.IrAmber.copy(alpha = 0.24f) else IrPalette.HudCard
    val bg = lerp(baseBg, Color(0xFF00E676).copy(alpha = 0.85f), greenPulse)
    val baseBorder = if (highlighted) IrPalette.IrAmber else IrPalette.BorderSubtle
    val border = lerp(baseBorder, Color(0xFF00E676), greenPulse)
    val baseText = if (highlighted) IrPalette.IrAmber else IrPalette.TextPrimary
    val textColor = lerp(baseText, Color.Black, greenPulse)

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled) { triggerClick(onClick) },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(50))
                .padding(horizontal = if (compact) 11.dp else 14.dp, vertical = 9.dp)
        ) {
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
private fun GridIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.4.dp.toPx()
        val x1 = size.width / 3f
        val x2 = size.width * 2f / 3f
        val y1 = size.height / 3f
        val y2 = size.height * 2f / 3f
        drawLine(tint, Offset(x1, 0f), Offset(x1, size.height), strokeWidth = stroke)
        drawLine(tint, Offset(x2, 0f), Offset(x2, size.height), strokeWidth = stroke)
        drawLine(tint, Offset(0f, y1), Offset(size.width, y1), strokeWidth = stroke)
        drawLine(tint, Offset(0f, y2), Offset(size.width, y2), strokeWidth = stroke)
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
