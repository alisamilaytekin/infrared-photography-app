@file:Suppress("SpellCheckingInspection")

package com.example.kameras

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.get
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.*
import android.media.ImageReader
import android.media.MediaActionSound
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

class MainActivity : ComponentActivity() {
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionLauncher.launch(Manifest.permission.CAMERA)
        setContent { CameraScreen() }
    }
}

private class CameraHolder {
    var device: CameraDevice? = null
    var session: CameraCaptureSession? = null
    var previewBuilder: CaptureRequest.Builder? = null
    var imageReader: ImageReader? = null
    var lastHardwareGains: RggbChannelVector = RggbChannelVector(1.5f, 1.5f, 1.5f, 1.5f)
}

data class CameraPreset(val name: String, val kelvin: Float, val tint: Float)
private const val KELVIN_MIN = 1f
private const val KELVIN_MAX = 10000f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraManager = remember { context.getSystemService<CameraManager>()!! }
    val holder = remember { CameraHolder() }
    val cameraThread = remember { HandlerThread("CameraThread").also { it.start() } }
    val cameraHandler = remember { Handler(cameraThread.looper) }

    // Shutter sound generator
    val shutterSound = remember { MediaActionSound() }

    var cameraReady by remember { mutableStateOf(false) }
    var isAutoMode by remember { mutableStateOf(true) }
    var kelvinValue by remember { mutableFloatStateOf(5500f) }
    var tintValue by remember { mutableFloatStateOf(0f) }
    var textureViewInstance by remember { mutableStateOf<TextureView?>(null) }
    var previewSize by remember { mutableStateOf<Size?>(null) }

    // Trigger to update aspect ratio and refresh the transform matrix
    var matrixRefreshTrigger by remember { mutableIntStateOf(0) }

    var isPickerActive by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    val savedPresets = remember { mutableStateListOf<CameraPreset>() }
    val sharedPrefs = remember { context.getSharedPreferences("CameraPresetsPrefs", Context.MODE_PRIVATE) }

    fun loadPresets() {
        savedPresets.clear()
        val jsonString = sharedPrefs.getString("presets_list", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                savedPresets.add(CameraPreset(obj.getString("name"), obj.getDouble("kelvin").toFloat(), obj.getDouble("tint").toFloat()))
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) { loadPresets() }

    fun kelvinToGains(kVal: Float, tVal: Float): Triple<Float, Float, Float> {
        // As Kelvin increases, blue increases (cool); as it decreases, red increases (warm)
        val kRatio = (kVal - 1000f) / (10000f - 1000f)  // 0.0 (1000K) → 1.0 (10000K)
        val r = (4.0f - (kRatio * 3.0f)).coerceIn(1.0f, 4.0f)  // 1000K → 4.0 (very warm), 10000K → 1.0
        val b = (1.0f + (kRatio * 3.0f)).coerceIn(1.0f, 4.0f)  // 1000K → 1.0, 10000K → 4.0 (very cool)

        // Tint: only affects the green channel
        // Positive tint = more green (reduces magenta)
        // Negative tint = less green (increases magenta)
        val tRatio = tVal / 100f
        val g = (1.5f + tRatio * 1.5f).coerceIn(1.0f, 4.0f)

        return Triple(r, g, b)
    }

    // Yardımcı fonksiyon: Seçilen noktadan Kelvin ve Tint türetir (Gelişmiş Kapalı Devre Modeli)
    fun calculateSpotWhiteBalance(
        bitmap: android.graphics.Bitmap,
        x: Int,
        y: Int,
        currentGains: RggbChannelVector
    ): Pair<Float, Float> {
        val sampleSize = 25
        val startX = (x - sampleSize / 2).coerceIn(0, bitmap.width - sampleSize)
        val startY = (y - sampleSize / 2).coerceIn(0, bitmap.height - sampleSize)

        var rSum = 0.0; var gSum = 0.0; var bSum = 0.0

        for (i in 0 until sampleSize) {
            for (j in 0 until sampleSize) {
                val pixel = bitmap[startX + i, startY + j]
                rSum += AndroidColor.red(pixel)
                gSum += AndroidColor.green(pixel)
                bSum += AndroidColor.blue(pixel)
            }
        }

        val count = (sampleSize * sampleSize).toDouble()

        // NOT: Tip hatalarını önlemek için sonlarına .toFloat() ekledik
        val rAvg = (rSum / count).coerceAtLeast(1.0).toFloat()
        val gAvg = (gSum / count).coerceAtLeast(1.0).toFloat()
        val bAvg = (bSum / count).coerceAtLeast(1.0).toFloat()

        // 1. Mevcut donanım kazançlarını piksellere oranlayarak ham sensör değerlerinin tersini buluyoruz
        val rRawInverse = currentGains.red / rAvg
        val bRawInverse = currentGains.blue / bAvg
        val gRawInverse = currentGains.greenEven / gAvg

        // 2. Kelvin modelimize (R + B = 5.0) uyması için ölçekleme katsayısını hesaplıyoruz
        val scaleFactor = 5.0f / (rRawInverse + bRawInverse)

        // 3. Renkleri nötrleyecek hedef donanım kazançlarını buluyoruz
        val rTarget = rRawInverse * scaleFactor
        val bTarget = bRawInverse * scaleFactor
        val gTarget = gRawInverse * scaleFactor

        // 4. Hedef R ve B kazançlarından kelvinToGains'in tersini alarak Kelvin değerini hesaplıyoruz
        val kRatioR = (4.0f - rTarget) / 3.0f
        val kRatioB = (bTarget - 1.0f) / 3.0f
        val kRatio = ((kRatioR + kRatioB) / 2.0f).coerceIn(0.0f, 1.0f)
        val kelvin = 1000f + kRatio * 9000f

        // 5. Hedef Yeşil kazancından Tint sürgü değerini (-100 ile +100 arası) hesaplıyoruz
        val tint = (100f * (gTarget - 1.5f) / 1.5f).coerceIn(-100f, 100f)

        return Pair(kelvin, tint)
    }

    val captureCallback = remember {
        object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
                holder.lastHardwareGains = result[CaptureResult.COLOR_CORRECTION_GAINS] ?: holder.lastHardwareGains
            }
        }
    }

    fun applyCamera2Settings(builder: CaptureRequest.Builder, auto: Boolean, kVal: Float, tVal: Float) {
        if (auto) {
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        } else {
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
            // TRANSFORM_MATRIX is used instead of HIGH_QUALITY — more reliable for gain overrides
            val (r, g, b) = kelvinToGains(kVal, tVal)
            builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, RggbChannelVector(r, g, g, b))
        }
    }

    fun savePresetsToStorage() {
        val jsonArray = JSONArray()
        savedPresets.forEach {
            jsonArray.put(JSONObject().apply {
                put("name", it.name)
                put("kelvin", it.kelvin.toDouble())
                put("tint", it.tint.toDouble())
            })
        }
        sharedPrefs.edit { putString("presets_list", jsonArray.toString()) }
    }

    fun updatePreviewSettings() {
        val builder = holder.previewBuilder ?: return
        val session = holder.session ?: return
        applyCamera2Settings(builder, isAutoMode, kelvinValue, tintValue)
        try {
            session.setRepeatingRequest(builder.build(), captureCallback, cameraHandler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startCamera(surface: Surface, auto: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        val cameraIdList = cameraManager.cameraIdList
        if (cameraIdList.isEmpty()) return
        val cameraId = cameraIdList[0]
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return
        val outputSizes = map.getOutputSizes(ImageFormat.JPEG) ?: return
        val largest = outputSizes.maxByOrNull { it.width * it.height } ?: return
        holder.imageReader = ImageReader.newInstance(largest.width, largest.height, ImageFormat.JPEG, 2)

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    holder.device = camera
                    val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(surface)
                        applyCamera2Settings(this, auto, kelvinValue, tintValue)
                    }
                    holder.previewBuilder = builder
                    @Suppress("DEPRECATION")
                    camera.createCaptureSession(listOf(surface, holder.imageReader!!.surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            holder.session = session
                            try {
                                session.setRepeatingRequest(builder.build(), captureCallback, cameraHandler)
                                cameraReady = true
                            } catch (e: CameraAccessException) {
                                Log.e("CameraScreen", "Failed to start repeating request", e)
                                cameraReady = false
                            }
                        }
                        override fun onConfigureFailed(s: CameraCaptureSession) {
                            cameraReady = false
                        }
                    }, cameraHandler)
                }
                override fun onDisconnected(c: CameraDevice) { c.close(); holder.device = null; cameraReady = false }
                override fun onError(c: CameraDevice, e: Int) { c.close(); holder.device = null; cameraReady = false }
            }, cameraHandler)
        } catch (e: CameraAccessException) {
            Log.e("CameraScreen", "Failed to open camera", e)
        }
    }

    fun takePhoto(auto: Boolean) {
        val camera = holder.device ?: return
        val session = holder.session ?: return
        val reader = holder.imageReader ?: return

        // 1. Calculate device and sensor orientations
        val cameraIdList = cameraManager.cameraIdList
        if (cameraIdList.isEmpty()) return
        val cameraId = cameraIdList[0]
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val displayRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display.rotation
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

        // Calculate JPEG orientation mapping
        val jpegOrientation = (sensorOrientation - rotationCompensation + 360) % 360

        // 2. Define the image capture available listener
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
        }, cameraHandler)

        // 3. Prepare the still capture request
        val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(reader.surface)
            applyCamera2Settings(this, auto, kelvinValue, tintValue)
            // Inject calculated orientation parameter here
            set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)
        }

        // 4. Execute the capture, play sound, and restore preview loop
        try {
            session.stopRepeating()

            // Play the native system camera shutter sound asynchronously
            shutterSound.play(MediaActionSound.SHUTTER_CLICK)

            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, res: TotalCaptureResult) {
                    super.onCaptureCompleted(s, r, res)
                    holder.previewBuilder?.let {
                        applyCamera2Settings(it, auto, kelvinValue, tintValue)
                        try {
                            session.setRepeatingRequest(it.build(), captureCallback, cameraHandler)
                        } catch (e: CameraAccessException) {
                            Log.e("CameraScreen", "Failed to resume repeating request", e)
                        }
                    }
                    matrixRefreshTrigger++
                }
            }, cameraHandler)
        } catch (e: CameraAccessException) {
            Log.e("CameraScreen", "Failed to capture photo", e)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Force matrix calculation on resume
                matrixRefreshTrigger++

                if (holder.device == null) textureViewInstance?.let { tv ->
                    if (tv.isAvailable) tv.surfaceTexture?.let { startCamera(Surface(it), isAutoMode) }
                }
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                holder.session?.close()
                holder.device?.close()
                holder.session = null
                holder.device = null
                cameraReady = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.session?.close()
            holder.device?.close()
            holder.imageReader?.close()
            cameraThread.quitSafely()
            shutterSound.release()
            holder.session = null
            holder.device = null
            holder.imageReader = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        // AndroidView Configuration
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
                                    // Scale raw coordinates onto Bitmap boundaries
                                    val x = (offset.x * bitmap.width / view.width).toInt()
                                    val y = (offset.y * bitmap.height / view.height).toInt()

                                    // DEĞİŞEN SATIR BURASI: holder.lastHardwareGains parametresini ekledik
                                    val (newK, newT) = calculateSpotWhiteBalance(bitmap, x, y, holder.lastHardwareGains)

                                    // Apply parsed adjustments
                                    kelvinValue = newK
                                    tintValue = newT
                                    isAutoMode = false
                                    isPickerActive = false
                                    updatePreviewSettings()
                                    Toast.makeText(context, "WB Set: ${newK.toInt()}K / Tint: ${newT.toInt()}", Toast.LENGTH_SHORT).show()
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
                            val hRes = 3000; val wRes = 4000
                            previewSize = Size(hRes, wRes)
                            st.setDefaultBufferSize(hRes, wRes)
                            startCamera(Surface(st), isAutoMode)
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                            previewSize = Size(w, h)
                            st.setDefaultBufferSize(w, h)
                            matrixRefreshTrigger++ // Recalculate aspect ratio matrix
                        }
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            },
            update = { view ->
                // Observe matrixRefreshTrigger state changes to force execution blocks
                Log.d("CameraScreen", "Updating matrix, trigger: $matrixRefreshTrigger")

                val preview = previewSize ?: return@AndroidView

                val viewWidth = view.width.toFloat()
                val viewHeight = view.height.toFloat()
                val sensorRatio = preview.width.toFloat() / preview.height.toFloat()
                val viewRatio = viewWidth / viewHeight

                val matrix = android.graphics.Matrix()
                val scaleX: Float
                val scaleY: Float

                if (viewRatio > sensorRatio) {
                    scaleY = 1f; scaleX = sensorRatio / viewRatio
                } else {
                    scaleX = 1f; scaleY = viewRatio / sensorRatio
                }

                matrix.setScale(scaleX, scaleY, viewWidth / 2, viewHeight / 2)
                view.setTransform(matrix)
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { isAutoMode = true; updatePreviewSettings() },
                        enabled = cameraReady,
                        colors = ButtonDefaults.buttonColors(containerColor = if (isAutoMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f))
                    ) { Text("Auto") }

                    Button(
                        onClick = {
                            isPickerActive = !isPickerActive
                            if (isPickerActive) {
                                Toast.makeText(context, "Tap a neutral/white spot on the preview", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = cameraReady,
                        colors = ButtonDefaults.buttonColors(containerColor = if (isPickerActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f))
                    ) {
                        Text(if (isPickerActive) "Select Spot..." else "WB Picker 🎯")
                    }

                    Button(
                        onClick = { isAutoMode = false; updatePreviewSettings() },
                        enabled = cameraReady,
                        colors = ButtonDefaults.buttonColors(containerColor = if (!isAutoMode && !isPickerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f))
                    ) { Text("Manual") }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (savedPresets.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(savedPresets) { preset ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        isAutoMode = false
                                        kelvinValue = preset.kelvin
                                        tintValue = preset.tint
                                        updatePreviewSettings()
                                        Toast.makeText(context, "${preset.name} loaded", Toast.LENGTH_SHORT).show()
                                    },
                                    onLongClick = {
                                        savedPresets.remove(preset)
                                        savePresetsToStorage()
                                        Toast.makeText(context, "${preset.name} deleted", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(text = preset.name, style = MaterialTheme.typography.titleSmall)
                                    Text(text = "${preset.kelvin.toInt()}K / Tint:${preset.tint.toInt()}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(16.dp)
            ) {
                Text(
                    text = if (isAutoMode) "Live Auto Kelvin: ${kelvinValue.toInt()} K" else "Kelvin: ${kelvinValue.toInt()} K",
                    color = Color.White
                )
                Slider(
                    value = kelvinValue,
                    onValueChange = { kelvinValue = it; updatePreviewSettings() },
                    valueRange = KELVIN_MIN..KELVIN_MAX,
                    enabled = cameraReady && !isAutoMode
                )

                Text(
                    text = if (isAutoMode) "Live Auto Tint: ${if(tintValue > 0) "+" else ""}${tintValue.toInt()}" else "Tint: ${if(tintValue > 0) "+" else ""}${tintValue.toInt()}",
                    color = Color.White
                )
                Slider(
                    value = tintValue,
                    onValueChange = { tintValue = it; updatePreviewSettings() },
                    valueRange = -100f..100f,
                    enabled = cameraReady && !isAutoMode
                )

                if (!isAutoMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = presetNameInput,
                            onValueChange = { presetNameInput = it },
                            label = { Text("Preset Name", color = Color.White) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.6f)
                            )
                        )
                        Button(
                            onClick = {
                                if (presetNameInput.isNotBlank()) {
                                    savedPresets.removeAll { it.name.equals(presetNameInput, ignoreCase = true) }
                                    savedPresets.add(CameraPreset(presetNameInput, kelvinValue, tintValue))
                                    savePresetsToStorage()
                                    Toast.makeText(context, "$presetNameInput saved", Toast.LENGTH_SHORT).show()
                                    presetNameInput = ""
                                } else {
                                    Toast.makeText(context, "Please enter a name", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = cameraReady
                        ) {
                            Text("Save")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Button(
                        onClick = { takePhoto(isAutoMode) },
                        enabled = cameraReady,
                        modifier = Modifier.fillMaxWidth(0.5f)
                    ) {
                        Text("Capture")
                    }
                }
            }
        }
    }
}
