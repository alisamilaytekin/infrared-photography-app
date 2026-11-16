@file:Suppress("SpellCheckingInspection")

package com.example.kameras

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.TonemapCurve
import android.media.ExifInterface
import android.media.ImageReader
import android.media.MediaActionSound
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

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
    val TextSecondary = Color(0xFF94A3B8)
    val BorderSubtle = Color(0x26FFFFFF)
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

private enum class ControlTab { WB, RENK, POZLAMA }

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
    @Volatile var currentExposureBoxSizeNorm: Float = 0.28f
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
    exifOrientation: Int
): Boolean {
    var tempOut: File? = null
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
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
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
        true
    } catch (e: Exception) {
        Log.e("CameraScreen", "Failed to save image", e)
        false
    } finally {
        try { tempOut?.delete() } catch (_: Exception) {}
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

    var activeTab by remember { mutableStateOf(ControlTab.WB) }
    var showGrid by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showResolutionDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    var highlightProtectionEnabled by remember { mutableStateOf(true) }
    var meteringMode by remember { mutableStateOf(ExposureMeteringMode.FULL_SCREEN) }
    var isExposureBoxPicking by remember { mutableStateOf(false) }
    var exposureBoxNorm by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var exposureBoxSizeNorm by remember { mutableFloatStateOf(0.28f) }
    var targetBrightnessLevel by remember { mutableFloatStateOf(1.00f) }
    var measuredRegionBrightness by remember { mutableIntStateOf(128) }

    val availableResolutions = remember { mutableStateListOf<Size>() }
    var selectedResolution by remember { mutableStateOf<Size?>(null) }

    var isPickerActive by remember { mutableStateOf(false) }
    var pickedMarkerPos by remember { mutableStateOf<Offset?>(null) }

    val sharedPrefs = remember { context.getSharedPreferences("CameraPresetsPrefs", Context.MODE_PRIVATE) }
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

    var statusBanner by remember { mutableStateOf<String?>(null) }
    val shutterFlashAlpha = remember { Animatable(0f) }
    var isShutterPressed by remember { mutableStateOf(false) }

    val savedPresets = remember { mutableStateListOf<CameraPreset>() }

    fun showStatus(message: String) {
        statusBanner = message
    }

    LaunchedEffect(statusBanner) {
        if (statusBanner != null) {
            delay(2000)
            statusBanner = null
        }
    }

    LaunchedEffect(pickedMarkerPos, isPickerActive) {
        if (pickedMarkerPos != null && !isPickerActive) {
            delay(1600)
            pickedMarkerPos = null
        }
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
        submitRepeatingRequest(session, builder)
    }

    fun measureAndApplyExposureMetering(applyEvAdjust: Boolean) {
        val view = textureViewInstance ?: return
        if (!view.isAvailable || view.width <= 0 || view.height <= 0) return
        val bmp = try { view.getBitmap(160, 160) } catch (_: Exception) { null } ?: return
        try {
            val stats = measureRegionLuminance(bmp, meteringMode, exposureBoxNorm, exposureBoxSizeNorm)
            measuredRegionBrightness = stats.avgLuma.roundToInt()

            if (applyEvAdjust) {
                val targetLuma = (128f * targetBrightnessLevel).coerceIn(55f, 205f)
                var neededRatio = (targetLuma / stats.avgLuma.coerceAtLeast(14f)).coerceIn(0.35f, 2.75f)

                // Highlight protection: if bright white areas exist in the scene,
                // prevent exposure from pushing highlights into pure burnout clipping (>226)
                if (highlightProtectionEnabled && stats.maxLuma > 200f) {
                    val safeRatio = (226f / stats.maxLuma).coerceAtMost(1.0f)
                    if (stats.highlightRatio > 0.03f || stats.maxLuma > 228f) {
                        neededRatio = minOf(neededRatio, safeRatio)
                    } else if (neededRatio > 1.0f) {
                        neededRatio = minOf(neededRatio, 235f / stats.maxLuma)
                    }
                }

                val deltaStops = (ln(neededRatio.toDouble()) / ln(2.0)).toFloat()
                val hasAeRange = aeRange.upper > aeRange.lower
                val step = aeStep.coerceAtLeast(0.1f)
                val newEv = if (hasAeRange) {
                    val deltaSteps = (deltaStops / step).roundToInt()
                    (exposureEvIndex + deltaSteps).coerceIn(aeRange.lower, aeRange.upper)
                } else {
                    exposureEvIndex
                }
                exposureEvIndex = newEv
                updatePreviewSettings()

                val pct = (stats.avgLuma * 100f / 255f).roundToInt()
                val evDisplay = exposureEvIndex * step
                val modeLabel = if (meteringMode == ExposureMeteringMode.BOX_SPOT) "Kutu" else "Tam Ekran"
                val protBadge = if (highlightProtectionEnabled && stats.maxLuma > 220f) " 🛡 Beyaz Korundu" else ""
                showStatus("$modeLabel Pozlama: $pct% · ${if (evDisplay > 0.01f) "+" else ""}${"%.1f".format(evDisplay)} EV$protBadge")
            }
        } finally {
            bmp.recycle()
        }
    }

    fun runAutoCalibrate() {
        val view = textureViewInstance ?: return
        if (!view.isAvailable || view.width <= 0 || view.height <= 0) return
        val bmp = try { view.getBitmap(240, 240) } catch (_: Exception) { null } ?: return
        try {
            val cal = autoCalibrateSceneFromPreview(bmp, holder.lastHardwareGains)
            isPickerActive = false
            isExposureBoxPicking = false
            isAutoMode = false
            kelvinValue = cal.kelvin
            tintValue = cal.tint
            contrastValue = DEFAULT_CONTRAST
            saturationValue = DEFAULT_SATURATION
            measureAndApplyExposureMetering(applyEvAdjust = true)
            updatePreviewSettings()
            showStatus("Oto-Kalibre: ${cal.kelvin.toInt()}K · ${formatSigned(cal.tint)} (${cal.foundNeutralCount} referans)")
        } finally {
            bmp.recycle()
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
            mainHandler.post {
                aeRange = range
                aeStep = step
            }

            synchronized(holder) {
                holder.imageReader?.close()
                holder.imageReader = ImageReader.newInstance(chosenJpeg.width, chosenJpeg.height, ImageFormat.JPEG, 4)
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
                        @Suppress("DEPRECATION")
                        camera.createCaptureSession(
                            listOf(surface, readerSurface),
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

            coroutineScope.launch(Dispatchers.IO) {
                val ok = saveOutputToGallery(context, bytes, null, ExifInterface.ORIENTATION_NORMAL)
                holder.isTakingPhoto = false
                mainHandler.post {
                    showStatus(if (ok) "Fotoğraf galeriye kaydedildi" else "Fotoğraf kaydedilemedi")
                }
            }
        }, cameraHandler)

        val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(reader.surface)
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
    val measuredBrightnessPct = (measuredRegionBrightness * 100f / 255f).roundToInt().coerceIn(0, 100)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(IrPalette.VoidBlack),
        contentAlignment = Alignment.Center
    ) {
        val containerRatio = if (maxHeight > 0.dp) maxWidth / maxHeight else 0.5f
        val viewfinderModifier = if (containerRatio > previewAspectRatio) {
            Modifier.fillMaxHeight().aspectRatio(previewAspectRatio)
        } else {
            Modifier.fillMaxWidth().aspectRatio(previewAspectRatio)
        }

        // Exact aspect-ratio viewfinder box matching the chosen JPEG resolution
        BoxWithConstraints(
            modifier = viewfinderModifier.clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            val vfWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val vfHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            var isDraggingExposureBox by remember { mutableStateOf(false) }

            key(surfaceGeneration) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isPickerActive, isExposureBoxPicking, meteringMode) {
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
                                            showStatus("WB: ${newK.toInt()}K · ${formatSigned(newT)}")
                                        } finally {
                                            bitmap.recycle()
                                        }
                                    }
                                } else if (isExposureBoxPicking) {
                                    val nx = (offset.x / view.width.coerceAtLeast(1)).coerceIn(0.08f, 0.92f)
                                    val ny = (offset.y / view.height.coerceAtLeast(1)).coerceIn(0.08f, 0.92f)
                                    exposureBoxNorm = Offset(nx, ny)
                                    meteringMode = ExposureMeteringMode.BOX_SPOT
                                    isExposureBoxPicking = false
                                    measureAndApplyExposureMetering(applyEvAdjust = true)
                                }
                            })
                        }
                        .pointerInput(meteringMode, exposureBoxSizeNorm, isPickerActive) {
                            if (meteringMode == ExposureMeteringMode.BOX_SPOT && !isPickerActive) {
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
                                            measureAndApplyExposureMetering(applyEvAdjust = true)
                                        }
                                    },
                                    onDragCancel = {
                                        isDraggingExposureBox = false
                                    }
                                )
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
                                    // Restore exact Camera2 buffer size so TextureView.onSizeChanged cannot stretch it
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

            // Optional 3x3 Rule-of-Thirds Grid, Exposure Box [ ☀️ ], & WB Picker Reticle
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (showGrid) {
                    val gridColor = Color.White.copy(alpha = 0.22f)
                    val stroke = 1.dp.toPx()
                    val thirdW = size.width / 3f
                    val thirdH = size.height / 3f
                    drawLine(gridColor, Offset(thirdW, 0f), Offset(thirdW, size.height), strokeWidth = stroke)
                    drawLine(gridColor, Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, size.height), strokeWidth = stroke)
                    drawLine(gridColor, Offset(0f, thirdH), Offset(size.width, thirdH), strokeWidth = stroke)
                    drawLine(gridColor, Offset(0f, thirdH * 2f), Offset(size.width, thirdH * 2f), strokeWidth = stroke)
                }

                if (isPickerActive || isExposureBoxPicking) {
                    val borderStroke = 2.dp.toPx()
                    drawRect(
                        color = if (isPickerActive) IrPalette.IrCoral.copy(alpha = 0.65f) else IrPalette.IrAmber.copy(alpha = 0.65f),
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

                    // Subtle translucent fill + corner brackets
                    drawRect(
                        color = boxColor.copy(alpha = 0.08f),
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(boxSide, boxSide)
                    )
                    // Top-left corner
                    drawLine(boxColor, Offset(left, top), Offset(left + corner, top), strokeW)
                    drawLine(boxColor, Offset(left, top), Offset(left, top + corner), strokeW)
                    // Top-right corner
                    drawLine(boxColor, Offset(right, top), Offset(right - corner, top), strokeW)
                    drawLine(boxColor, Offset(right, top), Offset(right, top + corner), strokeW)
                    // Bottom-left corner
                    drawLine(boxColor, Offset(left, bottom), Offset(left + corner, bottom), strokeW)
                    drawLine(boxColor, Offset(left, bottom), Offset(left, bottom - corner), strokeW)
                    // Bottom-right corner
                    drawLine(boxColor, Offset(right, bottom), Offset(right - corner, bottom), strokeW)
                    drawLine(boxColor, Offset(right, bottom), Offset(right, bottom - corner), strokeW)
                    // Center sun dot
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

            // Live brightness badge anchored near the Exposure Box
            if (meteringMode == ExposureMeteringMode.BOX_SPOT) {
                val boxSidePx = minOf(vfWidthPx, vfHeightPx) * exposureBoxSizeNorm
                val badgeOffsetX = ((exposureBoxNorm.x - 0.5f) * vfWidthPx)
                    .roundToInt()
                    .coerceIn(-(vfWidthPx * 0.40f).roundToInt(), (vfWidthPx * 0.40f).roundToInt())
                val rawBadgeY = if (exposureBoxNorm.y < 0.22f) {
                    (exposureBoxNorm.y - 0.5f) * vfHeightPx + boxSidePx * 0.5f + 26f
                } else {
                    (exposureBoxNorm.y - 0.5f) * vfHeightPx - boxSidePx * 0.5f - 26f
                }
                val badgeOffsetY = rawBadgeY
                    .roundToInt()
                    .coerceIn(-(vfHeightPx * 0.45f).roundToInt(), (vfHeightPx * 0.45f).roundToInt())
                Box(
                    modifier = Modifier
                        .offset { androidx.compose.ui.unit.IntOffset(badgeOffsetX, badgeOffsetY) }
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .border(1.dp, IrPalette.IrAmber.copy(alpha = 0.75f), RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "☀ $measuredBrightnessPct%",
                        color = IrPalette.IrAmber,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Shutter Capture Flash Overlay
            if (shutterFlashAlpha.value > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = shutterFlashAlpha.value))
                )
            }
        }

        // Main Pro HUD Overlay
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP HUD
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Brand + Telemetry + Resolution Switcher + Grid Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (cameraReady) IrPalette.IrCoral else Color.Gray)
                        )
                        Text(
                            text = "KAMERAS",
                            color = IrPalette.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(IrPalette.HudCard)
                                .border(1.dp, IrPalette.BorderSubtle, RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            val telemetry = if (isAutoMode) {
                                "~${displayKelvin.toInt()}K · ${formatSigned(displayTint)} · ☀$measuredBrightnessPct%"
                            } else {
                                "${displayKelvin.toInt()}K · ${formatSigned(displayTint)} · ☀$measuredBrightnessPct%"
                            }
                            Text(
                                text = telemetry,
                                color = IrPalette.TextPrimary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        val activeRes = selectedResolution
                        val resLabel = if (activeRes != null) {
                            "${formatMegapixels(activeRes)} · ${formatAspectRatioTag(activeRes)} ▾"
                        } else {
                            "Çözünürlük ▾"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(IrPalette.HudCard)
                                .border(
                                    1.dp,
                                    if (availableResolutions.isNotEmpty()) IrPalette.IrCyan.copy(alpha = 0.45f)
                                    else IrPalette.BorderSubtle,
                                    RoundedCornerShape(50)
                                )
                                .clickable(enabled = availableResolutions.isNotEmpty()) {
                                    showResolutionDialog = true
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = resLabel,
                                color = IrPalette.IrCyan,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (showGrid) IrPalette.IrAmber.copy(alpha = 0.24f) else IrPalette.HudCard)
                                .border(
                                    1.dp,
                                    if (showGrid) IrPalette.IrAmber else IrPalette.BorderSubtle,
                                    CircleShape
                                )
                                .clickable { showGrid = !showGrid },
                            contentAlignment = Alignment.Center
                        ) {
                            GridIcon(
                                tint = if (showGrid) IrPalette.IrAmber else IrPalette.TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Clean 2-Tab Mode Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(IrPalette.HudGlass)
                        .border(1.dp, IrPalette.BorderSubtle, RoundedCornerShape(50))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ModePillButton(
                        label = "OTOMATİK",
                        selected = isAutoMode,
                        accentColor = IrPalette.IrEmerald,
                        enabled = cameraReady,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            isPickerActive = false
                            isExposureBoxPicking = false
                            isAutoMode = true
                            updatePreviewSettings()
                        }
                    )
                    ModePillButton(
                        label = "MANUEL",
                        selected = !isAutoMode,
                        accentColor = IrPalette.IrAmber,
                        enabled = cameraReady,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            isPickerActive = false
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

                // Smart IR Actions + Preset Carousel
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (highlightProtectionEnabled) IrPalette.IrTeal.copy(alpha = 0.22f)
                                    else IrPalette.HudChip
                                )
                                .border(
                                    1.dp,
                                    if (highlightProtectionEnabled) IrPalette.IrTeal else IrPalette.BorderSubtle,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable(enabled = cameraReady) {
                                    highlightProtectionEnabled = !highlightProtectionEnabled
                                    measureAndApplyExposureMetering(applyEvAdjust = true)
                                    showStatus(
                                        if (highlightProtectionEnabled) "Vurgu Koruması Açık (Beyazlar Korunur)"
                                        else "Vurgu Koruması Kapalı"
                                    )
                                }
                                .padding(horizontal = 11.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (highlightProtectionEnabled) "🛡 Vurgu Koruma ●" else "🛡 Vurgu Koruma",
                                color = if (highlightProtectionEnabled) IrPalette.IrTeal else IrPalette.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    item {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(IrPalette.IrCyan.copy(alpha = 0.16f))
                                .border(1.dp, IrPalette.IrCyan.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                                .clickable(enabled = cameraReady) { runAutoCalibrate() }
                                .padding(horizontal = 11.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Oto-Kalibre",
                                color = IrPalette.IrCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (!isAutoMode) {
                        item {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(IrPalette.IrAmber.copy(alpha = 0.18f))
                                    .border(1.dp, IrPalette.IrAmber.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                                    .clickable(enabled = cameraReady) { showSaveDialog = true }
                                    .padding(horizontal = 11.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "+ Kaydet",
                                    color = IrPalette.IrAmber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    items(savedPresets, key = { it.name }) { preset ->
                        val isMatching = !isAutoMode &&
                            abs(kelvinValue - preset.kelvin) < 5f &&
                            abs(tintValue - preset.tint) < 1f
                        PresetChip(
                            preset = preset,
                            isActive = isMatching,
                            onClick = {
                                isPickerActive = false
                                isExposureBoxPicking = false
                                isAutoMode = false
                                kelvinValue = preset.kelvin
                                tintValue = preset.tint
                                contrastValue = preset.contrast
                                saturationValue = preset.saturation
                                updatePreviewSettings()
                                showStatus("${preset.name} yüklendi")
                            },
                            onLongClick = {
                                savedPresets.remove(preset)
                                savePresetsToStorage()
                                showStatus("${preset.name} silindi")
                            }
                        )
                    }
                }

                // Animated Status / Picker Instruction Pill
                AnimatedVisibility(
                    visible = statusBanner != null || isPickerActive || isExposureBoxPicking,
                    enter = fadeIn() + slideInVertically { -it / 2 },
                    exit = fadeOut() + slideOutVertically { -it / 2 }
                ) {
                    val bannerText = when {
                        isPickerActive -> "WB Seçici Aktif — Nötr bir noktaya dokunun"
                        isExposureBoxPicking -> "Pozlama Kutusu — Hedef bölgeye dokunun veya sürükleyin"
                        else -> statusBanner ?: ""
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    when {
                                        isPickerActive -> IrPalette.IrCoral.copy(alpha = 0.9f)
                                        isExposureBoxPicking -> IrPalette.IrAmber.copy(alpha = 0.9f)
                                        else -> Color(0xE61E293B)
                                    }
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = bannerText,
                                color = if (isExposureBoxPicking) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // BOTTOM PRO CONTROL DECK
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isAutoMode) {
                    // Pro Manual Deck (3 Tabs: WB / RENK & IR / POZLAMA)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(IrPalette.HudGlass)
                            .border(1.dp, IrPalette.BorderSubtle, RoundedCornerShape(22.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TabChip(
                                title = "WB",
                                selected = activeTab == ControlTab.WB,
                                modifier = Modifier.weight(1f),
                                onClick = { activeTab = ControlTab.WB }
                            )
                            TabChip(
                                title = "RENK & IR",
                                selected = activeTab == ControlTab.RENK,
                                modifier = Modifier.weight(1f),
                                onClick = { activeTab = ControlTab.RENK }
                            )
                            TabChip(
                                title = "POZLAMA",
                                selected = activeTab == ControlTab.POZLAMA,
                                modifier = Modifier.weight(1f),
                                onClick = { activeTab = ControlTab.POZLAMA }
                            )
                        }

                        when (activeTab) {
                            ControlTab.WB -> {
                                ProGradientSliderRow(
                                    label = "KELVİN",
                                    valueBadge = "${kelvinValue.toInt()} K",
                                    value = kelvinValue,
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
                                    onValueChange = {
                                        kelvinValue = it
                                        updatePreviewSettings()
                                    }
                                )

                                ProGradientSliderRow(
                                    label = "TİNT",
                                    valueBadge = formatSigned(tintValue),
                                    value = tintValue,
                                    valueRange = -100f..100f,
                                    enabled = cameraReady,
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(IrPalette.IrMagenta, Color(0xFFE2E8F0), IrPalette.IrEmerald)
                                    ),
                                    onStepMinus = {
                                        tintValue = (tintValue - 5f).coerceIn(-100f, 100f)
                                        updatePreviewSettings()
                                    },
                                    onStepPlus = {
                                        tintValue = (tintValue + 5f).coerceIn(-100f, 100f)
                                        updatePreviewSettings()
                                    },
                                    onBadgeClick = {
                                        tintValue = 0f
                                        updatePreviewSettings()
                                    },
                                    onValueChange = {
                                        tintValue = it
                                        updatePreviewSettings()
                                    }
                                )
                            }

                            ControlTab.RENK -> {
                                ProGradientSliderRow(
                                    label = "DOYGUNLUK",
                                    valueBadge = "${"%.2f".format(saturationValue)}x",
                                    value = saturationValue,
                                    valueRange = 0.50f..2.00f,
                                    enabled = cameraReady,
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF64748B), IrPalette.IrCyan, IrPalette.IrMagenta)
                                    ),
                                    onStepMinus = {
                                        saturationValue = (saturationValue - 0.05f).coerceIn(0.50f, 2.00f)
                                        updatePreviewSettings()
                                    },
                                    onStepPlus = {
                                        saturationValue = (saturationValue + 0.05f).coerceIn(0.50f, 2.00f)
                                        updatePreviewSettings()
                                    },
                                    onBadgeClick = {
                                        saturationValue = DEFAULT_SATURATION
                                        updatePreviewSettings()
                                    },
                                    onValueChange = {
                                        saturationValue = it
                                        updatePreviewSettings()
                                    }
                                )

                                ProGradientSliderRow(
                                    label = "KONTRAST",
                                    valueBadge = "${"%.2f".format(contrastValue)}x",
                                    value = contrastValue,
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
                                    onBadgeClick = {
                                        contrastValue = DEFAULT_CONTRAST
                                        updatePreviewSettings()
                                    },
                                    onValueChange = {
                                        contrastValue = it
                                        updatePreviewSettings()
                                    }
                                )
                            }

                            ControlTab.POZLAMA -> {
                                // Metering Mode Switcher: Full-Screen Average vs Box Spot
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ModePillButton(
                                        label = "TÜM EKRAN ORT.",
                                        selected = meteringMode == ExposureMeteringMode.FULL_SCREEN,
                                        accentColor = IrPalette.IrCyan,
                                        enabled = cameraReady,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            isExposureBoxPicking = false
                                            meteringMode = ExposureMeteringMode.FULL_SCREEN
                                            measureAndApplyExposureMetering(applyEvAdjust = true)
                                        }
                                    )
                                    ModePillButton(
                                        label = "KUTU SEÇİMİ ☀",
                                        selected = meteringMode == ExposureMeteringMode.BOX_SPOT,
                                        accentColor = IrPalette.IrAmber,
                                        enabled = cameraReady,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            meteringMode = ExposureMeteringMode.BOX_SPOT
                                            isExposureBoxPicking = true
                                            measureAndApplyExposureMetering(applyEvAdjust = true)
                                        }
                                    )
                                }

                                // Highlight Protection Switch Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ModePillButton(
                                        label = if (highlightProtectionEnabled) "🛡 VURGU KORUMA (BEYAZI KORU): AÇIK" else "🛡 VURGU KORUMA: KAPALI",
                                        selected = highlightProtectionEnabled,
                                        accentColor = IrPalette.IrTeal,
                                        enabled = cameraReady,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            highlightProtectionEnabled = !highlightProtectionEnabled
                                            measureAndApplyExposureMetering(applyEvAdjust = true)
                                        }
                                    )
                                    QuickPillAction(
                                        text = "Eşitle ($measuredBrightnessPct%)",
                                        highlighted = false,
                                        compact = true,
                                        enabled = cameraReady,
                                        onClick = { measureAndApplyExposureMetering(applyEvAdjust = true) }
                                    )
                                }

                                // Box Sizing & Positioning Controls
                                if (meteringMode == ExposureMeteringMode.BOX_SPOT) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        QuickPillAction(
                                            text = if (isExposureBoxPicking) "Hedefe Dokun" else "Kutuyu Taşı",
                                            highlighted = isExposureBoxPicking,
                                            compact = true,
                                            enabled = cameraReady,
                                            onClick = {
                                                isPickerActive = false
                                                isExposureBoxPicking = !isExposureBoxPicking
                                            }
                                        )
                                        listOf(
                                            "%15" to 0.15f,
                                            "%30" to 0.30f,
                                            "%50" to 0.50f,
                                            "%75" to 0.75f
                                        ).forEach { (lbl, sz) ->
                                            val isSel = abs(exposureBoxSizeNorm - sz) < 0.08f
                                            ModePillButton(
                                                label = lbl,
                                                selected = isSel,
                                                accentColor = IrPalette.IrAmber,
                                                enabled = cameraReady,
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    exposureBoxSizeNorm = sz
                                                    measureAndApplyExposureMetering(applyEvAdjust = true)
                                                }
                                            )
                                        }
                                    }

                                    ProGradientSliderRow(
                                        label = "KUTU BOYUTU",
                                        valueBadge = "${(exposureBoxSizeNorm * 100f).roundToInt()}%",
                                        value = exposureBoxSizeNorm,
                                        valueRange = 0.10f..0.80f,
                                        enabled = cameraReady,
                                        gradient = Brush.horizontalGradient(
                                            colors = listOf(Color(0xFF0284C7), IrPalette.IrAmber, Color(0xFFF97316))
                                        ),
                                        onStepMinus = {
                                            exposureBoxSizeNorm = (exposureBoxSizeNorm - 0.05f).coerceIn(0.10f, 0.80f)
                                            measureAndApplyExposureMetering(applyEvAdjust = true)
                                        },
                                        onStepPlus = {
                                            exposureBoxSizeNorm = (exposureBoxSizeNorm + 0.05f).coerceIn(0.10f, 0.80f)
                                            measureAndApplyExposureMetering(applyEvAdjust = true)
                                        },
                                        onBadgeClick = {
                                            exposureBoxSizeNorm = 0.30f
                                            measureAndApplyExposureMetering(applyEvAdjust = true)
                                        },
                                        onValueChange = {
                                            exposureBoxSizeNorm = it
                                            measureAndApplyExposureMetering(applyEvAdjust = true)
                                        }
                                    )
                                }

                                // Target Brightness Slider
                                ProGradientSliderRow(
                                    label = "HEDEF PARLAKLIK",
                                    valueBadge = "${(targetBrightnessLevel * 100f).roundToInt()}%",
                                    value = targetBrightnessLevel,
                                    valueRange = 0.55f..1.45f,
                                    enabled = cameraReady,
                                    gradient = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF334155), IrPalette.IrAmber, Color(0xFFFEF9C3))
                                    ),
                                    onStepMinus = {
                                        targetBrightnessLevel = (targetBrightnessLevel - 0.05f).coerceIn(0.55f, 1.45f)
                                        measureAndApplyExposureMetering(applyEvAdjust = true)
                                    },
                                    onStepPlus = {
                                        targetBrightnessLevel = (targetBrightnessLevel + 0.05f).coerceIn(0.55f, 1.45f)
                                        measureAndApplyExposureMetering(applyEvAdjust = true)
                                    },
                                    onBadgeClick = {
                                        targetBrightnessLevel = 1.00f
                                        measureAndApplyExposureMetering(applyEvAdjust = true)
                                    },
                                    onValueChange = {
                                        targetBrightnessLevel = it
                                        measureAndApplyExposureMetering(applyEvAdjust = true)
                                    }
                                )

                                // Manual Hardware EV Compensation
                                if (aeRange.upper > aeRange.lower) {
                                    val minEv = aeRange.lower.toFloat()
                                    val maxEv = aeRange.upper.toFloat()
                                    val evDisplay = exposureEvIndex * aeStep
                                    val evBadge = "${if (evDisplay > 0.01f) "+" else ""}${"%.1f".format(evDisplay)} EV"
                                    ProGradientSliderRow(
                                        label = "MANUEL EV",
                                        valueBadge = evBadge,
                                        value = exposureEvIndex.toFloat(),
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
                                        onBadgeClick = {
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
                    }
                }

                // Bottom Shutter Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left action: Sıfırla (Manual) or Quick Exposure Mode Toggle (Auto)
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (!isAutoMode) {
                            QuickPillAction(
                                text = "Sıfırla",
                                enabled = cameraReady,
                                onClick = {
                                    when (activeTab) {
                                        ControlTab.RENK -> {
                                            contrastValue = DEFAULT_CONTRAST
                                            saturationValue = DEFAULT_SATURATION
                                        }
                                        ControlTab.POZLAMA -> {
                                            exposureEvIndex = 0
                                            targetBrightnessLevel = 1.00f
                                            exposureBoxNorm = Offset(0.5f, 0.5f)
                                        }
                                        ControlTab.WB -> {
                                            kelvinValue = liveAutoKelvin.coerceIn(KELVIN_MIN, KELVIN_MAX)
                                            tintValue = liveAutoTint.coerceIn(-100f, 100f)
                                        }
                                    }
                                    updatePreviewSettings()
                                    showStatus("Ayarlar sıfırlandı")
                                }
                            )
                        } else {
                            QuickPillAction(
                                text = if (meteringMode == ExposureMeteringMode.BOX_SPOT) "☀ Kutu" else "☀ Tam",
                                highlighted = meteringMode == ExposureMeteringMode.BOX_SPOT,
                                enabled = cameraReady,
                                onClick = {
                                    if (meteringMode == ExposureMeteringMode.FULL_SCREEN) {
                                        meteringMode = ExposureMeteringMode.BOX_SPOT
                                        isExposureBoxPicking = true
                                        measureAndApplyExposureMetering(applyEvAdjust = true)
                                    } else {
                                        isExposureBoxPicking = false
                                        meteringMode = ExposureMeteringMode.FULL_SCREEN
                                        measureAndApplyExposureMetering(applyEvAdjust = true)
                                    }
                                }
                            )
                        }
                    }

                    // Concentric Pro Shutter Button
                    val innerSize by animateDpAsState(
                        targetValue = if (isShutterPressed) 50.dp else 58.dp,
                        animationSpec = tween(100),
                        label = "shutterSize"
                    )
                    Box(
                        modifier = Modifier
                            .size(74.dp)
                            .clip(CircleShape)
                            .border(
                                3.dp,
                                Color.White.copy(alpha = if (cameraReady) 0.95f else 0.35f),
                                CircleShape
                            )
                            .clickable(
                                enabled = cameraReady,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { takePhoto() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(innerSize)
                                .clip(CircleShape)
                                .background(
                                    if (!cameraReady) Color.Gray
                                    else if (isAutoMode) Color.White
                                    else IrPalette.IrCoral
                                )
                        )
                    }

                    // Right action: Quick Exposure Box + WB Seç
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isAutoMode) {
                            QuickPillAction(
                                text = if (meteringMode == ExposureMeteringMode.BOX_SPOT) "☀" else "Tam",
                                highlighted = meteringMode == ExposureMeteringMode.BOX_SPOT || isExposureBoxPicking,
                                compact = true,
                                enabled = cameraReady,
                                onClick = {
                                    isPickerActive = false
                                    if (meteringMode == ExposureMeteringMode.FULL_SCREEN) {
                                        meteringMode = ExposureMeteringMode.BOX_SPOT
                                        isExposureBoxPicking = true
                                        measureAndApplyExposureMetering(applyEvAdjust = true)
                                    } else {
                                        isExposureBoxPicking = false
                                        meteringMode = ExposureMeteringMode.FULL_SCREEN
                                        measureAndApplyExposureMetering(applyEvAdjust = true)
                                    }
                                }
                            )
                        }
                        QuickPillAction(
                            text = if (isPickerActive) "İptal" else "WB Seç",
                            highlighted = isPickerActive,
                            compact = !isAutoMode,
                            enabled = cameraReady,
                            onClick = {
                                isExposureBoxPicking = false
                                isPickerActive = !isPickerActive
                            }
                        )
                    }
                }
            }
        }
    }

    // Save Preset Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = Color(0xFF141822),
            titleContentColor = IrPalette.TextPrimary,
            textContentColor = IrPalette.TextSecondary,
            title = {
                Text(
                    text = "Preset Kaydet",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${kelvinValue.toInt()}K · Tint ${formatSigned(tintValue)} · C${"%.2f".format(contrastValue)} · S${"%.2f".format(saturationValue)}",
                        color = IrPalette.IrAmber,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    OutlinedTextField(
                        value = presetNameInput,
                        onValueChange = { presetNameInput = it },
                        label = { Text("Preset Adı") },
                        placeholder = { Text("Örn: Sunny-IRChrome") },
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
                            showStatus("Lütfen bir isim girin")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IrPalette.IrCoral)
                ) {
                    Text("Kaydet", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Vazgeç", color = IrPalette.TextSecondary)
                }
            }
        )
    }

    // Resolution Selector Dialog
    if (showResolutionDialog) {
        AlertDialog(
            onDismissRequest = { showResolutionDialog = false },
            containerColor = Color(0xFF141822),
            titleContentColor = IrPalette.TextPrimary,
            textContentColor = IrPalette.TextSecondary,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Fotoğraf Çözünürlüğü",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${availableResolutions.size} desteklenen boyut · Önizleme otomatik hizalanır",
                        color = IrPalette.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(availableResolutions, key = { "${it.width}x${it.height}" }) { res ->
                        val isSelected = selectedResolution?.let {
                            it.width == res.width && it.height == res.height
                        } == true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) IrPalette.IrCyan.copy(alpha = 0.16f)
                                    else Color.White.copy(alpha = 0.04f)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) IrPalette.IrCyan else IrPalette.BorderSubtle,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    sharedPrefs.edit {
                                        putInt("selected_res_w", res.width)
                                        putInt("selected_res_h", res.height)
                                    }
                                    selectedResolution = res
                                    holder.selectedResolution = res
                                    holder.previewFallbackLevel = 0
                                    holder.activePreviewSize = null
                                    showResolutionDialog = false
                                    showStatus("Çözünürlük: ${formatMegapixels(res)} (${res.width}×${res.height})")
                                    restartForResolution(res)
                                }
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
                                    color = if (isSelected) IrPalette.IrCyan else IrPalette.TextPrimary,
                                    fontSize = 13.sp,
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
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = formatAspectRatioTag(res),
                                        color = IrPalette.TextPrimary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(IrPalette.IrCyan)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showResolutionDialog = false }) {
                    Text("Kapat", color = IrPalette.IrCyan)
                }
            }
        )
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
    val bgAlpha by animateFloatAsState(if (selected) 0.24f else 0f, label = "modeBg")
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(accentColor.copy(alpha = bgAlpha))
            .then(
                if (selected) Modifier.border(1.dp, accentColor.copy(alpha = 0.7f), RoundedCornerShape(50))
                else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) accentColor else IrPalette.TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChip(
    preset: CameraPreset,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val indicatorColor = if (preset.kelvin < 4500f) IrPalette.IrCoral else IrPalette.IrCyan
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isActive) IrPalette.IrAmber.copy(alpha = 0.22f) else IrPalette.HudChip)
            .border(
                1.dp,
                if (isActive) IrPalette.IrAmber else IrPalette.BorderSubtle,
                RoundedCornerShape(14.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(indicatorColor)
        )
        Column {
            Text(
                text = preset.name,
                color = IrPalette.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = "${preset.kelvin.toInt()}K · T${formatSigned(preset.tint)}",
                color = IrPalette.TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (selected) IrPalette.TextPrimary else IrPalette.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp
        )
    }
}

@Composable
private fun ProGradientSliderRow(
    label: String,
    valueBadge: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    gradient: Brush,
    onStepMinus: () -> Unit,
    onStepPlus: () -> Unit,
    onBadgeClick: (() -> Unit)? = null,
    onValueChange: (Float) -> Unit
) {
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
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .then(if (onBadgeClick != null) Modifier.clickable(onClick = onBadgeClick) else Modifier)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = valueBadge,
                        color = IrPalette.TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StepButton(symbol = "−", enabled = enabled, onClick = onStepMinus)
                StepButton(symbol = "+", enabled = enabled, onClick = onStepPlus)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(gradient)
            )
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = onValueChange,
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
    Box(
        modifier = Modifier
            .size(width = 28.dp, height = 22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(enabled = enabled, onClick = onClick),
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

@Composable
private fun QuickPillAction(
    text: String,
    highlighted: Boolean = false,
    compact: Boolean = false,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (highlighted) IrPalette.IrCoral.copy(alpha = 0.28f) else IrPalette.HudCard)
            .border(
                1.dp,
                if (highlighted) IrPalette.IrCoral else IrPalette.BorderSubtle,
                RoundedCornerShape(50)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            color = if (highlighted) IrPalette.IrCoral else IrPalette.TextPrimary,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
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
    return if (intVal > 0) "+$intVal" else "$intVal"
}
