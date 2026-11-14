@file:Suppress("SpellCheckingInspection")

package com.example.kameras

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.TonemapCurve
import android.media.ImageReader
import android.media.MediaActionSound
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
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
    val IrMagenta = Color(0xFFE84393)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val BorderSubtle = Color(0x26FFFFFF)
}

class MainActivity : ComponentActivity() {
    private var hasCameraPermission by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
            if (!granted) {
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
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CameraScreen(hasCameraPermission = hasCameraPermission)
            }
        }
    }
}

private class CameraHolder {
    var device: CameraDevice? = null
    var session: CameraCaptureSession? = null
    var previewBuilder: CaptureRequest.Builder? = null
    var imageReader: ImageReader? = null
    var activeSurface: Surface? = null
    var isOpening: Boolean = false
    var isDisposed: Boolean = false

    @Volatile var lastHardwareGains: RggbChannelVector = RggbChannelVector(1.5f, 1.0f, 1.0f, 1.5f)
    @Volatile var lastAutoColorTransform: ColorSpaceTransform? = null
    @Volatile var lastTelemetryUpdateMs: Long = 0L
    @Volatile var supportedTonemapModes: IntArray = intArrayOf()
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
}

data class CameraPreset(
    val name: String,
    val kelvin: Float,
    val tint: Float,
    val contrast: Float = DEFAULT_CONTRAST,
    val saturation: Float = DEFAULT_SATURATION
)

private enum class ControlTab { WB, TONE }

private const val KELVIN_MIN = 1f
private const val KELVIN_MAX = 10000f
private const val DEFAULT_CONTRAST = 1.18f
private const val DEFAULT_SATURATION = 1.22f

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

    val rGain = currentGains.red.coerceAtLeast(0.01f)
    val gGain = currentGains.greenEven.coerceAtLeast(0.01f)
    val bGain = currentGains.blue.coerceAtLeast(0.01f)

    val rRawInverse = rGain / rAvg
    val bRawInverse = bGain / bAvg
    val gRawInverse = gGain / gAvg

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
 * Builds a 3x3 hardware ColorSpaceTransform by combining the sensor's calibrated Auto CCM
 * (or a calibrated sRGB fallback) with a luminance-preserving saturation matrix.
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

    val s = saturation.coerceIn(0.3f, 2.5f)
    val lr = 0.299f
    val lg = 0.587f
    val lb = 0.114f
    val satMat = floatArrayOf(
        (1f - s) * lr + s, (1f - s) * lg, (1f - s) * lb,
        (1f - s) * lr, (1f - s) * lg + s, (1f - s) * lb,
        (1f - s) * lr, (1f - s) * lg, (1f - s) * lb + s
    )

    // Multiply satMat * base (3x3 row-major) so sRGB luminance & neutral white point stay preserved
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
 * Generates a hardware TonemapCurve applying sRGB gamma plus a perceptual S-curve for rich contrast.
 */
private fun buildContrastTonemapCurve(contrast: Float, maxCurvePoints: Int): TonemapCurve {
    val count = minOf(33, maxCurvePoints).coerceIn(2, 64)
    val curve = FloatArray(count * 2)
    val c = contrast.coerceIn(0.5f, 2.0f)

    for (i in 0 until count) {
        val x = i.toFloat() / (count - 1).toFloat()
        val y = when (i) {
            0 -> 0f
            count - 1 -> 1f
            else -> {
                val perceptual = if (x <= 0.0031308f) {
                    12.92f * x
                } else {
                    (1.055f * x.toDouble().pow(1.0 / 2.4).toFloat() - 0.055f).coerceIn(0f, 1f)
                }
                val shaped = if (perceptual < 0.5f) {
                    0.5f * (2.0 * perceptual).pow(c.toDouble()).toFloat()
                } else {
                    1.0f - 0.5f * (2.0 * (1.0 - perceptual)).pow(c.toDouble()).toFloat()
                }
                val clamped = shaped.coerceIn(0f, 1f)
                if (i > 0) maxOf(clamped, curve[(i - 1) * 2 + 1]) else clamped
            }
        }
        curve[i * 2] = x
        curve[i * 2 + 1] = y
    }

    return TonemapCurve(curve, curve, curve)
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
    var presetNameInput by remember { mutableStateOf("") }

    var isPickerActive by remember { mutableStateOf(false) }
    var pickedMarkerPos by remember { mutableStateOf<Offset?>(null) }

    var textureViewInstance by remember { mutableStateOf<TextureView?>(null) }
    var previewSize by remember { mutableStateOf<Size?>(null) }
    var matrixRefreshTrigger by remember { mutableIntStateOf(0) }

    var statusBanner by remember { mutableStateOf<String?>(null) }
    val shutterFlashAlpha = remember { Animatable(0f) }
    var isShutterPressed by remember { mutableStateOf(false) }

    val savedPresets = remember { mutableStateListOf<CameraPreset>() }
    val sharedPrefs = remember { context.getSharedPreferences("CameraPresetsPrefs", Context.MODE_PRIVATE) }

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
                savedPresets.add(
                    CameraPreset(
                        name = obj.getString("name"),
                        kelvin = obj.getDouble("kelvin").toFloat(),
                        tint = obj.getDouble("tint").toFloat(),
                        contrast = obj.optDouble("contrast", DEFAULT_CONTRAST.toDouble()).toFloat(),
                        saturation = obj.optDouble("saturation", DEFAULT_SATURATION.toDouble()).toFloat()
                    )
                )
            }
        } catch (_: Exception) {
        }

        if (savedPresets.isEmpty() && !sharedPrefs.getBoolean("presets_seeded_v4", false)) {
            savedPresets.addAll(
                listOf(
                    CameraPreset("IRChrome", 3200f, -10f, 1.20f, 1.28f),
                    CameraPreset("590nm Gold", 2400f, 8f, 1.22f, 1.25f),
                    CameraPreset("720nm Crisp", 1800f, 0f, 1.35f, 0.90f)
                )
            )
            savePresetsToStorage()
            sharedPrefs.edit { putBoolean("presets_seeded_v4", true) }
        } else if (savedPresets.isNotEmpty() && !sharedPrefs.getBoolean("presets_seeded_v4", false)) {
            sharedPrefs.edit { putBoolean("presets_seeded_v4", true) }
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
    }

    fun applyCamera2Settings(
        builder: CaptureRequest.Builder,
        auto: Boolean,
        kVal: Float,
        tVal: Float,
        contrastVal: Float,
        saturationVal: Float,
        exposureEv: Int,
        isStillCapture: Boolean = false
    ) {
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        val clampedEv = exposureEv.coerceIn(holder.aeCompensationRange.lower, holder.aeCompensationRange.upper)
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, clampedEv)

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
            isStillCapture = false
        )
        try {
            session.setRepeatingRequest(builder.build(), captureCallback, cameraHandler)
        } catch (e: Exception) {
            Log.e("CameraScreen", "Failed to update preview settings", e)
        }
    }

    fun startCamera(surface: Surface) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        if (holder.isDisposed || holder.device != null || holder.isOpening) return

        val cameraIdList = cameraManager.cameraIdList
        if (cameraIdList.isEmpty()) return
        val cameraId = cameraIdList[0]
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return
        val outputSizes = map.getOutputSizes(ImageFormat.JPEG) ?: return
        val largest = outputSizes.maxByOrNull { it.width * it.height } ?: return

        holder.supportedTonemapModes =
            chars.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES) ?: intArrayOf()
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

        holder.imageReader?.close()
        holder.imageReader = ImageReader.newInstance(largest.width, largest.height, ImageFormat.JPEG, 2)
        holder.activeSurface = surface
        holder.isOpening = true

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    holder.isOpening = false
                    if (holder.isDisposed) {
                        camera.close()
                        return
                    }
                    holder.device = camera
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
                            isStillCapture = false
                        )
                    }
                    holder.previewBuilder = builder
                    @Suppress("DEPRECATION")
                    camera.createCaptureSession(
                        listOf(surface, holder.imageReader!!.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                if (holder.isDisposed) {
                                    session.close()
                                    return
                                }
                                holder.session = session
                                try {
                                    session.setRepeatingRequest(builder.build(), captureCallback, cameraHandler)
                                    mainHandler.post { cameraReady = true }
                                } catch (e: CameraAccessException) {
                                    Log.e("CameraScreen", "Failed to start repeating request", e)
                                    mainHandler.post { cameraReady = false }
                                }
                            }

                            override fun onConfigureFailed(s: CameraCaptureSession) {
                                mainHandler.post { cameraReady = false }
                            }
                        },
                        cameraHandler
                    )
                }

                override fun onDisconnected(c: CameraDevice) {
                    holder.isOpening = false
                    c.close()
                    holder.device = null
                    cameraReady = false
                }

                override fun onError(c: CameraDevice, e: Int) {
                    holder.isOpening = false
                    c.close()
                    holder.device = null
                    cameraReady = false
                }
            }, cameraHandler)
        } catch (e: CameraAccessException) {
            holder.isOpening = false
            Log.e("CameraScreen", "Failed to open camera", e)
        }
    }

    fun takePhoto() {
        syncHolderSettings()
        val camera = holder.device ?: return
        val session = holder.session ?: return
        val reader = holder.imageReader ?: return

        val cameraIdList = cameraManager.cameraIdList
        if (cameraIdList.isEmpty()) return
        val cameraId = cameraIdList[0]
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

        coroutineScope.launch {
            isShutterPressed = true
            shutterFlashAlpha.snapTo(0.45f)
            shutterFlashAlpha.animateTo(0f, animationSpec = tween(220))
            isShutterPressed = false
        }

        reader.setOnImageAvailableListener({ imageReader ->
            val image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
            image.close()

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }

            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) }
            }
            mainHandler.post { showStatus("Fotoğraf galeriye kaydedildi") }
        }, cameraHandler)

        val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(reader.surface)
            applyCamera2Settings(
                builder = this,
                auto = holder.currentAutoMode,
                kVal = holder.currentKelvin,
                tVal = holder.currentTint,
                contrastVal = holder.currentContrast,
                saturationVal = holder.currentSaturation,
                exposureEv = holder.currentExposureEv,
                isStillCapture = true
            )
            set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)
        }

        try {
            session.stopRepeating()
            shutterSound.play(MediaActionSound.SHUTTER_CLICK)

            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    s: CameraCaptureSession,
                    r: CaptureRequest,
                    res: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(s, r, res)
                    holder.previewBuilder?.let {
                        applyCamera2Settings(
                            builder = it,
                            auto = holder.currentAutoMode,
                            kVal = holder.currentKelvin,
                            tVal = holder.currentTint,
                            contrastVal = holder.currentContrast,
                            saturationVal = holder.currentSaturation,
                            exposureEv = holder.currentExposureEv,
                            isStillCapture = false
                        )
                        try {
                            session.setRepeatingRequest(it.build(), captureCallback, cameraHandler)
                        } catch (e: CameraAccessException) {
                            Log.e("CameraScreen", "Failed to resume repeating request", e)
                        }
                    }
                    mainHandler.post { matrixRefreshTrigger++ }
                }
            }, cameraHandler)
        } catch (e: CameraAccessException) {
            Log.e("CameraScreen", "Failed to capture photo", e)
        }
    }

    LaunchedEffect(hasCameraPermission, textureViewInstance) {
        if (hasCameraPermission && holder.device == null && !holder.isOpening) {
            textureViewInstance?.let { tv ->
                if (tv.isAvailable) {
                    tv.surfaceTexture?.let { startCamera(Surface(it)) }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                matrixRefreshTrigger++
                if (holder.device == null && !holder.isOpening) {
                    textureViewInstance?.let { tv ->
                        if (tv.isAvailable) {
                            tv.surfaceTexture?.let { startCamera(Surface(it)) }
                        }
                    }
                }
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                holder.isOpening = false
                holder.session?.close()
                holder.device?.close()
                holder.activeSurface?.release()
                holder.activeSurface = null
                holder.session = null
                holder.device = null
                cameraReady = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            holder.isDisposed = true
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.session?.close()
            holder.device?.close()
            holder.imageReader?.close()
            holder.activeSurface?.release()
            cameraThread.quitSafely()
            shutterSound.release()
            holder.session = null
            holder.device = null
            holder.imageReader = null
        }
    }

    val displayKelvin = if (isAutoMode) liveAutoKelvin else kelvinValue
    val displayTint = if (isAutoMode) liveAutoTint else tintValue

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IrPalette.VoidBlack),
        contentAlignment = Alignment.Center
    ) {
        // Live camera TextureView preserving exact aspect ratio matrix logic
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isPickerActive) {
                    detectTapGestures(onTap = { offset ->
                        if (isPickerActive) {
                            val view = textureViewInstance
                            if (view != null) {
                                val bitmap = view.bitmap
                                if (bitmap != null) {
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
                                    showStatus("WB Ayarlandı: ${newK.toInt()}K · Tint ${formatSigned(newT)}")
                                }
                            }
                        }
                    })
                },
            factory = { ctx ->
                TextureView(ctx).apply {
                    textureViewInstance = this
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            val hRes = 3000
                            val wRes = 4000
                            previewSize = Size(hRes, wRes)
                            st.setDefaultBufferSize(hRes, wRes)
                            startCamera(Surface(st))
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                            previewSize = Size(w, h)
                            st.setDefaultBufferSize(w, h)
                            matrixRefreshTrigger++
                        }

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            },
            update = { view ->
                Log.d("CameraScreen", "Updating matrix, trigger: $matrixRefreshTrigger")
                val preview = previewSize ?: return@AndroidView
                val viewWidth = view.width.toFloat()
                val viewHeight = view.height.toFloat()
                if (viewWidth <= 0f || viewHeight <= 0f) return@AndroidView

                val sensorRatio = preview.width.toFloat() / preview.height.toFloat()
                val viewRatio = viewWidth / viewHeight

                val matrix = android.graphics.Matrix()
                val scaleX: Float
                val scaleY: Float
                if (viewRatio > sensorRatio) {
                    scaleY = 1f
                    scaleX = sensorRatio / viewRatio
                } else {
                    scaleX = 1f
                    scaleY = viewRatio / sensorRatio
                }
                matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
                view.setTransform(matrix)
            }
        )

        // Optional 3x3 Rule-of-Thirds Grid & WB Picker Reticle Overlay
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

            if (isPickerActive) {
                val borderStroke = 2.dp.toPx()
                drawRect(
                    color = IrPalette.IrCoral.copy(alpha = 0.65f),
                    style = Stroke(width = borderStroke)
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

        // Shutter Capture Flash Overlay
        if (shutterFlashAlpha.value > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = shutterFlashAlpha.value))
            )
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
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Brand + Telemetry + Grid Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (cameraReady) IrPalette.IrCoral else Color.Gray)
                        )
                        Text(
                            text = "KAMERAS",
                            color = IrPalette.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.6.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(IrPalette.IrCoral.copy(alpha = 0.22f))
                                .border(1.dp, IrPalette.IrCoral.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "IR PRO",
                                color = IrPalette.IrCoral,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Telemetry Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(IrPalette.HudCard)
                                .border(1.dp, IrPalette.BorderSubtle, RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            val telemetry = if (isAutoMode) {
                                "AUTO · ~${displayKelvin.toInt()}K · T${formatSigned(displayTint)}"
                            } else {
                                "${displayKelvin.toInt()}K · T${formatSigned(displayTint)} · C${"%.2f".format(contrastValue)} · S${"%.2f".format(saturationValue)}"
                            }
                            Text(
                                text = telemetry,
                                color = IrPalette.TextPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Grid Toggle Button
                        Box(
                            modifier = Modifier
                                .size(32.dp)
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
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Segmented Mode Selector Pill
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
                        label = "AUTO",
                        selected = isAutoMode && !isPickerActive,
                        accentColor = IrPalette.IrEmerald,
                        enabled = cameraReady,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            isPickerActive = false
                            isAutoMode = true
                            updatePreviewSettings()
                        }
                    )
                    ModePillButton(
                        label = if (isPickerActive) "Nokta Seç..." else "WB Seçici",
                        selected = isPickerActive,
                        accentColor = IrPalette.IrCoral,
                        enabled = cameraReady,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            isPickerActive = !isPickerActive
                            if (isPickerActive) {
                                showStatus("Önizlemede nötr veya beyaz bir noktaya dokunun")
                            }
                        }
                    )
                    ModePillButton(
                        label = "MANUEL",
                        selected = !isAutoMode && !isPickerActive,
                        accentColor = IrPalette.IrAmber,
                        enabled = cameraReady,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            isPickerActive = false
                            isAutoMode = false
                            updatePreviewSettings()
                        }
                    )
                }

                // Preset Carousel
                if (savedPresets.isNotEmpty() || !isAutoMode) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isAutoMode) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(IrPalette.IrAmber.copy(alpha = 0.18f))
                                        .border(1.dp, IrPalette.IrAmber.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                                        .clickable(enabled = cameraReady) { showSaveDialog = true }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "+ Kaydet",
                                        color = IrPalette.IrAmber,
                                        fontSize = 12.sp,
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
                }

                // Animated Status / WB Picker Instruction Pill
                AnimatedVisibility(
                    visible = statusBanner != null || isPickerActive,
                    enter = fadeIn() + slideInVertically { -it / 2 },
                    exit = fadeOut() + slideOutVertically { -it / 2 }
                ) {
                    val bannerText = when {
                        isPickerActive -> "WB Seçici Aktif — Nötr bir noktaya dokunun"
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
                                    if (isPickerActive) IrPalette.IrCoral.copy(alpha = 0.9f)
                                    else Color(0xE61E293B)
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = bannerText,
                                color = Color.White,
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
                if (isAutoMode) {
                    // Clean Auto Mode Summary Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(IrPalette.HudGlass)
                            .border(1.dp, IrPalette.BorderSubtle, RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Otomatik Beyaz Dengesi",
                                color = IrPalette.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Ölçülen: ~${liveAutoKelvin.toInt()}K · Tint ${formatSigned(liveAutoTint)}",
                                color = IrPalette.IrEmerald,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(IrPalette.IrAmber.copy(alpha = 0.18f))
                                .border(1.dp, IrPalette.IrAmber.copy(alpha = 0.55f), RoundedCornerShape(50))
                                .clickable(enabled = cameraReady) {
                                    kelvinValue = liveAutoKelvin.coerceIn(KELVIN_MIN, KELVIN_MAX)
                                    tintValue = liveAutoTint.coerceIn(-100f, 100f)
                                    isPickerActive = false
                                    isAutoMode = false
                                    updatePreviewSettings()
                                    showStatus("Oto ayar Manuele kilitlendi")
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "Manuele Kilitle",
                                color = IrPalette.IrAmber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    // Pro Manual Deck
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(IrPalette.HudGlass)
                            .border(1.dp, IrPalette.BorderSubtle, RoundedCornerShape(22.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Tab Switcher: WB vs Tone/Color
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TabChip(
                                title = "KELVİN & TİNT",
                                selected = activeTab == ControlTab.WB,
                                modifier = Modifier.weight(1f),
                                onClick = { activeTab = ControlTab.WB }
                            )
                            TabChip(
                                title = "KONTRAST & RENK",
                                selected = activeTab == ControlTab.TONE,
                                modifier = Modifier.weight(1f),
                                onClick = { activeTab = ControlTab.TONE }
                            )
                        }

                        if (activeTab == ControlTab.WB) {
                            // Kelvin Control
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

                            // Tint Control
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
                        } else {
                            // Contrast Control (Hardware S-Curve Tonemap)
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

                            // Saturation Control (Hardware 3x3 CCM)
                            ProGradientSliderRow(
                                label = "DOYGUNLUK",
                                valueBadge = "${"%.2f".format(saturationValue)}x",
                                value = saturationValue,
                                valueRange = 0.50f..2.00f,
                                enabled = cameraReady,
                                gradient = Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF64748B), IrPalette.IrAmber, IrPalette.IrCoral)
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

                            // AE Exposure Compensation (if hardware supports EV range)
                            if (aeRange.upper > aeRange.lower) {
                                val minEv = aeRange.lower.toFloat()
                                val maxEv = aeRange.upper.toFloat()
                                val evDisplay = exposureEvIndex * aeStep
                                val evBadge = "${if (evDisplay > 0.01f) "+" else ""}${"%.1f".format(evDisplay)} EV"
                                ProGradientSliderRow(
                                    label = "POZLAMA",
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

                // Bottom Shutter Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left action
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (isAutoMode) {
                            QuickPillAction(
                                text = "Manuele Al",
                                enabled = cameraReady,
                                onClick = {
                                    kelvinValue = liveAutoKelvin.coerceIn(KELVIN_MIN, KELVIN_MAX)
                                    tintValue = liveAutoTint.coerceIn(-100f, 100f)
                                    isPickerActive = false
                                    isAutoMode = false
                                    updatePreviewSettings()
                                }
                            )
                        } else {
                            QuickPillAction(
                                text = if (activeTab == ControlTab.TONE) "Sıfırla" else "Oto Eşitle",
                                enabled = cameraReady,
                                onClick = {
                                    if (activeTab == ControlTab.TONE) {
                                        contrastValue = DEFAULT_CONTRAST
                                        saturationValue = DEFAULT_SATURATION
                                        exposureEvIndex = 0
                                        updatePreviewSettings()
                                        showStatus("Kontrast ve renk sıfırlandı")
                                    } else {
                                        kelvinValue = liveAutoKelvin.coerceIn(KELVIN_MIN, KELVIN_MAX)
                                        tintValue = liveAutoTint.coerceIn(-100f, 100f)
                                        updatePreviewSettings()
                                        showStatus("Oto değerlere eşitlendi")
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
                            .border(3.dp, Color.White.copy(alpha = if (cameraReady) 0.95f else 0.35f), CircleShape)
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

                    // Right action
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        QuickPillAction(
                            text = if (isPickerActive) "İptal" else "WB Seç",
                            highlighted = isPickerActive,
                            enabled = cameraReady,
                            onClick = {
                                isPickerActive = !isPickerActive
                                if (isPickerActive) {
                                    showStatus("Nötr veya beyaz alana dokunun")
                                }
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
                        placeholder = { Text("Örn: Sunny-590nm") },
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
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            color = if (highlighted) IrPalette.IrCoral else IrPalette.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
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
