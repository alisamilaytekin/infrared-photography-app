package com.alisamilaytekin.spectras

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.pow

/**
 * OpenGL ES 2.0 OES-to-EGL live preview shader renderer for Android 12 and below
 * (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU), where AGSL RuntimeShader
 * and RenderEffect.createRuntimeShaderEffect are not available.
 *
 * On Android 13+ this class is never instantiated, leaving the existing
 * RenderEffect/RuntimeShader pipeline 100% untouched.
 */
internal class PreTiramisuGlPreviewRenderer {

    private data class UniformSnapshot(
        val bypass: Boolean = false,
        val hasLook: Boolean = false,
        val sharpness: Float = 0f,
        val wbR: Float = 1f,
        val wbG: Float = 1f,
        val wbB: Float = 1f,
        val expScale: Float = 1f,
        val contrast: Float = 0f,
        val highlights: Float = 0f,
        val shadows: Float = 0f,
        val whites: Float = 0f,
        val blacks: Float = 0f,
        val vibrance: Float = 0f,
        val saturation: Float = 0f,
        val hasSplit: Boolean = false,
        val shR: Float = 0f,
        val shG: Float = 0f,
        val shB: Float = 0f,
        val midR: Float = 0f,
        val midG: Float = 0f,
        val midB: Float = 0f,
        val hiR: Float = 0f,
        val hiG: Float = 0f,
        val hiB: Float = 0f,
        val monoMix: Float = 0f,
        val bwR: Float = 0.2126f,
        val bwG: Float = 0.7152f,
        val bwB: Float = 0.0722f,
        val channelSwap: Boolean = false,
        val hasPoint: Boolean = false,
        val targetHue: Float = 0f,
        val hueWidth: Float = 0f,
        val hueFeather: Float = 1f,
        val deltaHue: Float = 0f,
        val deltaSat: Float = 0f,
        val deltaLum: Float = 0f,
        val hasBands: Boolean = false,
        val bandHue: FloatArray = FloatArray(12),
        val bandSat: FloatArray = FloatArray(12),
        val bandLum: FloatArray = FloatArray(12),
        val focusPeaking: Boolean = false
    )

    private val glThread = HandlerThread("SpectrasGlPreview").apply { start() }
    private val glHandler = Handler(glThread.looper)

    @Volatile
    private var snapshot = UniformSnapshot()

    @Volatile
    var isFailed: Boolean = false
        private set

    @Volatile
    private var isReleased: Boolean = false

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var attachedOutputSt: SurfaceTexture? = null

    private var program: Int = 0
    private var passthroughProgram: Int = 0
    private var pPositionLoc = -1
    private var pTexCoordLoc = -1
    private var pTexMatrixLoc = -1
    private var pContentLoc = -1
    private var lastUploadedSnapshot: UniformSnapshot? = null
    private var lastUsedProgram: Int = 0
    private var oesTextureId: Int = 0
    private var inputSurfaceTexture: SurfaceTexture? = null
    private var hasFirstFrame: Boolean = false
    private val redrawRunnable = Runnable {
        if (!isReleased && !isFailed && hasFirstFrame && eglSurface != EGL14.EGL_NO_SURFACE) {
            drawCurrentFrame(updateTexture = false)
        }
    }

    private val texMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val surfaceDim = IntArray(2)

    private val quadVertices: FloatBuffer = ByteBuffer
        .allocateDirect(16 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            // x, y, u, v (triangle strip)
            put(
                floatArrayOf(
                    -1f, -1f, 0f, 0f,
                     1f, -1f, 1f, 0f,
                    -1f,  1f, 0f, 1f,
                     1f,  1f, 1f, 1f
                )
            )
            position(0)
        }

    // Cached GL locations
    private var aPositionLoc = -1
    private var aTexCoordLoc = -1
    private var uTexMatrixLoc = -1
    private var uContentLoc = -1
    private var uTexelSizeLoc = -1
    private var uLookActiveLoc = -1
    private var uLookSharpnessLoc = -1
    private var uLookWbGainsLoc = -1
    private var uLookExpScaleLoc = -1
    private var uLookContrastLoc = -1
    private var uLookHighlightsLoc = -1
    private var uLookShadowsLoc = -1
    private var uLookWhitesLoc = -1
    private var uLookBlacksLoc = -1
    private var uLookVibranceLoc = -1
    private var uLookSatLoc = -1
    private var uSplitActiveLoc = -1
    private var uSplitShadowLoc = -1
    private var uSplitMidLoc = -1
    private var uSplitHighLoc = -1
    private var uMonoMixLoc = -1
    private var uBwWeightsLoc = -1
    private var uChannelSwapLoc = -1
    private var uTargetHueLoc = -1
    private var uHueWidthLoc = -1
    private var uHueFeatherLoc = -1
    private var uDeltaHueLoc = -1
    private var uDeltaSatLoc = -1
    private var uDeltaLumLoc = -1
    private var uActiveLoc = -1
    private var uBandsActiveLoc = -1
    private var uBandHueALoc = -1
    private var uBandHueBLoc = -1
    private var uBandHueCLoc = -1
    private var uBandSatALoc = -1
    private var uBandSatBLoc = -1
    private var uBandSatCLoc = -1
    private var uBandLumALoc = -1
    private var uBandLumBLoc = -1
    private var uBandLumCLoc = -1
    private var uFocusPeakingLoc = -1

    fun updateUniforms(
        config: TargetedColorConfig,
        colorBands: ColorBandsConfig,
        channelSwap: Boolean,
        lookGrade: LookGradeParams,
        bypass: Boolean,
        focusPeaking: Boolean = false
    ) {
        if (isReleased || isFailed) return
        val hasPoint = config.isActive && (config.deltaHue != 0f || config.deltaSat != 0f || config.deltaLum != 0f)
        val hasBands = colorBands.hasAnyModification
        val hasLook = lookGrade.isActive
        val (wbR, wbG, wbB) = if (hasLook) computeLookWbGains(lookGrade.tempShift, lookGrade.tintShift) else Triple(1f, 1f, 1f)
        val hasSplit = hasLook && lookGrade.hasSplitToning
        val (shR, shG, shB) = if (hasSplit) computeSplitWheelTint(lookGrade.shadowHue, lookGrade.shadowSat) else Triple(0f, 0f, 0f)
        val (midR, midG, midB) = if (hasSplit) computeSplitWheelTint(lookGrade.midtoneHue, lookGrade.midtoneSat) else Triple(0f, 0f, 0f)
        val (hiR, hiG, hiB) = if (hasSplit) computeSplitWheelTint(lookGrade.highlightHue, lookGrade.highlightSat) else Triple(0f, 0f, 0f)

        val b = colorBands.bands
        val bh = FloatArray(12) { idx -> b.getOrNull(idx)?.deltaHue ?: 0f }
        val bs = FloatArray(12) { idx -> b.getOrNull(idx)?.deltaSat ?: 0f }
        val bl = FloatArray(12) { idx -> b.getOrNull(idx)?.deltaLum ?: 0f }

        snapshot = UniformSnapshot(
            bypass = bypass,
            hasLook = hasLook,
            sharpness = if (hasLook) lookGrade.sharpness else 0f,
            wbR = wbR,
            wbG = wbG,
            wbB = wbB,
            expScale = if (hasLook) 2.0f.pow(lookGrade.exposureEv) else 1f,
            contrast = if (hasLook) lookGrade.contrast else 0f,
            highlights = if (hasLook) lookGrade.highlights else 0f,
            shadows = if (hasLook) lookGrade.shadows else 0f,
            whites = if (hasLook) lookGrade.whites else 0f,
            blacks = if (hasLook) lookGrade.blacks else 0f,
            vibrance = if (hasLook) lookGrade.vibrance else 0f,
            saturation = if (hasLook) lookGrade.saturation else 0f,
            hasSplit = hasSplit,
            shR = shR,
            shG = shG,
            shB = shB,
            midR = midR,
            midG = midG,
            midB = midB,
            hiR = hiR,
            hiG = hiG,
            hiB = hiB,
            monoMix = if (hasLook) lookGrade.monochromeMix else 0f,
            bwR = lookGrade.bwWeightR,
            bwG = lookGrade.bwWeightG,
            bwB = lookGrade.bwWeightB,
            channelSwap = channelSwap,
            hasPoint = hasPoint,
            targetHue = config.targetHue,
            hueWidth = config.hueWidth,
            hueFeather = config.hueFeather.coerceAtLeast(0.001f),
            deltaHue = config.deltaHue,
            deltaSat = config.deltaSat,
            deltaLum = config.deltaLum,
            hasBands = hasBands,
            bandHue = bh,
            bandSat = bs,
            bandLum = bl,
            focusPeaking = focusPeaking
        )

        glHandler.removeCallbacks(redrawRunnable)
        glHandler.post(redrawRunnable)
    }

    /**
     * Attaches [outputSt] (the TextureView's SurfaceTexture) as the EGL output target
     * and returns the OES input SurfaceTexture that Camera2 should render into.
     * Returns null if EGL/GL initialization fails (caller falls back to direct outputSt).
     */
    fun getOrAttachInputSurface(outputSt: SurfaceTexture): SurfaceTexture? {
        if (isReleased || isFailed) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && outputSt.isReleased) return null

        val latch = CountDownLatch(1)
        var result: SurfaceTexture? = null
        val posted = glHandler.post {
            try {
                result = attachOnGlThread(outputSt)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize pre-Tiramisu GL preview", t)
                isFailed = true
                cleanupSurfaceOnGlThread()
                result = null
            } finally {
                latch.countDown()
            }
        }
        if (!posted) return null
        try {
            latch.await(1500, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            return null
        }
        return result
    }

    fun detachOutputSurface(outputSt: SurfaceTexture) {
        if (isReleased) return
        val latch = CountDownLatch(1)
        val posted = glHandler.post {
            try {
                if (attachedOutputSt === outputSt) {
                    cleanupSurfaceOnGlThread()
                }
            } finally {
                latch.countDown()
            }
        }
        if (posted) {
            try {
                latch.await(300, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {}
        }
    }

    fun release() {
        if (isReleased) return
        isReleased = true
        val latch = CountDownLatch(1)
        val posted = glHandler.post {
            try {
                cleanupSurfaceOnGlThread()
                cleanupContextOnGlThread()
            } catch (t: Throwable) {
                Log.w(TAG, "Error during GL release", t)
            } finally {
                latch.countDown()
            }
        }
        if (posted) {
            try {
                latch.await(800, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {}
        }
        glThread.quitSafely()
    }

    private fun attachOnGlThread(outputSt: SurfaceTexture): SurfaceTexture? {
        if (isReleased) return null
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            initEglContextOnGlThread()
        }
        val existingInput = inputSurfaceTexture
        val inputAlive = existingInput != null &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !existingInput.isReleased)
        if (attachedOutputSt === outputSt && eglSurface != EGL14.EGL_NO_SURFACE && inputAlive) {
            return existingInput
        }

        cleanupSurfaceOnGlThread()

        val cfg = eglConfig ?: return null
        val attribs = intArrayOf(EGL14.EGL_NONE)
        val surface = EGL14.eglCreateWindowSurface(eglDisplay, cfg, outputSt, attribs, 0)
        if (surface == null || surface == EGL14.EGL_NO_SURFACE) {
            Log.e(TAG, "eglCreateWindowSurface failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
            return null
        }
        if (!EGL14.eglMakeCurrent(eglDisplay, surface, surface, eglContext)) {
            Log.e(TAG, "eglMakeCurrent failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
            EGL14.eglDestroySurface(eglDisplay, surface)
            return null
        }
        eglSurface = surface
        attachedOutputSt = outputSt
        hasFirstFrame = false

        if (program == 0) {
            initProgramOnGlThread()
        }

        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        oesTextureId = texIds[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val st = SurfaceTexture(oesTextureId)
        st.setOnFrameAvailableListener({
            if (!isReleased && !isFailed && inputSurfaceTexture === st) {
                drawCurrentFrame(updateTexture = true)
            }
        }, glHandler)
        inputSurfaceTexture = st
        return st
    }

    private fun initEglContextOnGlThread() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            throw IllegalStateException("eglGetDisplay failed")
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw IllegalStateException("eglInitialize failed")
        }
        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] <= 0) {
            throw IllegalStateException("eglChooseConfig failed")
        }
        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (context == null || context == EGL14.EGL_NO_CONTEXT) {
            throw IllegalStateException("eglCreateContext failed")
        }
        eglDisplay = display
        eglConfig = configs[0]
        eglContext = context
    }

    private fun initProgramOnGlThread() {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetProgramInfoLog(prog)
            GLES20.glDeleteProgram(prog)
            throw IllegalStateException("Program link failed: $log")
        }
        program = prog

        aPositionLoc = GLES20.glGetAttribLocation(prog, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(prog, "aTexCoord")
        uTexMatrixLoc = GLES20.glGetUniformLocation(prog, "uTexMatrix")
        uContentLoc = GLES20.glGetUniformLocation(prog, "uContent")
        uTexelSizeLoc = GLES20.glGetUniformLocation(prog, "uTexelSize")

        uLookActiveLoc = GLES20.glGetUniformLocation(prog, "uLookActive")
        uLookSharpnessLoc = GLES20.glGetUniformLocation(prog, "uLookSharpness")
        uLookWbGainsLoc = GLES20.glGetUniformLocation(prog, "uLookWbGains")
        uLookExpScaleLoc = GLES20.glGetUniformLocation(prog, "uLookExpScale")
        uLookContrastLoc = GLES20.glGetUniformLocation(prog, "uLookContrast")
        uLookHighlightsLoc = GLES20.glGetUniformLocation(prog, "uLookHighlights")
        uLookShadowsLoc = GLES20.glGetUniformLocation(prog, "uLookShadows")
        uLookWhitesLoc = GLES20.glGetUniformLocation(prog, "uLookWhites")
        uLookBlacksLoc = GLES20.glGetUniformLocation(prog, "uLookBlacks")
        uLookVibranceLoc = GLES20.glGetUniformLocation(prog, "uLookVibrance")
        uLookSatLoc = GLES20.glGetUniformLocation(prog, "uLookSat")
        uSplitActiveLoc = GLES20.glGetUniformLocation(prog, "uSplitActive")
        uSplitShadowLoc = GLES20.glGetUniformLocation(prog, "uSplitShadow")
        uSplitMidLoc = GLES20.glGetUniformLocation(prog, "uSplitMid")
        uSplitHighLoc = GLES20.glGetUniformLocation(prog, "uSplitHigh")
        uMonoMixLoc = GLES20.glGetUniformLocation(prog, "uMonoMix")
        uBwWeightsLoc = GLES20.glGetUniformLocation(prog, "uBwWeights")
        uChannelSwapLoc = GLES20.glGetUniformLocation(prog, "uChannelSwap")
        uTargetHueLoc = GLES20.glGetUniformLocation(prog, "uTargetHue")
        uHueWidthLoc = GLES20.glGetUniformLocation(prog, "uHueWidth")
        uHueFeatherLoc = GLES20.glGetUniformLocation(prog, "uHueFeather")
        uDeltaHueLoc = GLES20.glGetUniformLocation(prog, "uDeltaHue")
        uDeltaSatLoc = GLES20.glGetUniformLocation(prog, "uDeltaSat")
        uDeltaLumLoc = GLES20.glGetUniformLocation(prog, "uDeltaLum")
        uActiveLoc = GLES20.glGetUniformLocation(prog, "uActive")
        uBandsActiveLoc = GLES20.glGetUniformLocation(prog, "uBandsActive")
        uBandHueALoc = GLES20.glGetUniformLocation(prog, "uBandHueA")
        uBandHueBLoc = GLES20.glGetUniformLocation(prog, "uBandHueB")
        uBandHueCLoc = GLES20.glGetUniformLocation(prog, "uBandHueC")
        uBandSatALoc = GLES20.glGetUniformLocation(prog, "uBandSatA")
        uBandSatBLoc = GLES20.glGetUniformLocation(prog, "uBandSatB")
        uBandSatCLoc = GLES20.glGetUniformLocation(prog, "uBandSatC")
        uBandLumALoc = GLES20.glGetUniformLocation(prog, "uBandLumA")
        uBandLumBLoc = GLES20.glGetUniformLocation(prog, "uBandLumB")
        uBandLumCLoc = GLES20.glGetUniformLocation(prog, "uBandLumC")
        uFocusPeakingLoc = GLES20.glGetUniformLocation(prog, "uFocusPeaking")

        // Ultra-fast passthrough shader for zero-overhead preview when no grading/peaking is active
        try {
            val pVs = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
            val pFs = compileShader(GLES20.GL_FRAGMENT_SHADER, PASSTHROUGH_FRAGMENT_SHADER)
            val pProg = GLES20.glCreateProgram()
            GLES20.glAttachShader(pProg, pVs)
            GLES20.glAttachShader(pProg, pFs)
            GLES20.glLinkProgram(pProg)
            val pStatus = IntArray(1)
            GLES20.glGetProgramiv(pProg, GLES20.GL_LINK_STATUS, pStatus, 0)
            GLES20.glDeleteShader(pVs)
            GLES20.glDeleteShader(pFs)
            if (pStatus[0] == GLES20.GL_TRUE) {
                passthroughProgram = pProg
                pPositionLoc = GLES20.glGetAttribLocation(pProg, "aPosition")
                pTexCoordLoc = GLES20.glGetAttribLocation(pProg, "aTexCoord")
                pTexMatrixLoc = GLES20.glGetUniformLocation(pProg, "uTexMatrix")
                pContentLoc = GLES20.glGetUniformLocation(pProg, "uContent")
            } else {
                GLES20.glDeleteProgram(pProg)
            }
        } catch (_: Throwable) {
            passthroughProgram = 0
        }
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("Shader compile failed: $log")
        }
        return shader
    }

    private fun drawCurrentFrame(updateTexture: Boolean) {
        val st = inputSurfaceTexture ?: return
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglSurface == EGL14.EGL_NO_SURFACE || program == 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && st.isReleased) return

        try {
            if (EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW) != eglSurface) {
                if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) return
            }
            if (updateTexture) {
                st.updateTexImage()
                st.getTransformMatrix(texMatrix)
                hasFirstFrame = true
            }
            if (!hasFirstFrame) return

            EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_WIDTH, surfaceDim, 0)
            EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_HEIGHT, surfaceDim, 1)
            val w = surfaceDim[0].coerceAtLeast(1)
            val h = surfaceDim[1].coerceAtLeast(1)
            GLES20.glViewport(0, 0, w, h)

            val s = snapshot
            val activeLook = !s.bypass && s.hasLook
            val activeSwap = !s.bypass && s.channelSwap
            val activePoint = !s.bypass && s.hasPoint
            val activeBands = !s.bypass && s.hasBands
            val needsGrading = activeLook || activeSwap || activePoint || activeBands || s.focusPeaking

            if (!needsGrading && passthroughProgram != 0) {
                if (lastUsedProgram != passthroughProgram) {
                    GLES20.glUseProgram(passthroughProgram)
                    lastUsedProgram = passthroughProgram
                }
                quadVertices.position(0)
                GLES20.glEnableVertexAttribArray(pPositionLoc)
                GLES20.glVertexAttribPointer(pPositionLoc, 2, GLES20.GL_FLOAT, false, 16, quadVertices)

                quadVertices.position(2)
                GLES20.glEnableVertexAttribArray(pTexCoordLoc)
                GLES20.glVertexAttribPointer(pTexCoordLoc, 2, GLES20.GL_FLOAT, false, 16, quadVertices)

                GLES20.glUniformMatrix4fv(pTexMatrixLoc, 1, false, texMatrix, 0)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
                GLES20.glUniform1i(pContentLoc, 0)

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                GLES20.glDisableVertexAttribArray(pPositionLoc)
                GLES20.glDisableVertexAttribArray(pTexCoordLoc)
            } else {
                val switchedProgram = lastUsedProgram != program
                if (switchedProgram) {
                    GLES20.glUseProgram(program)
                    lastUsedProgram = program
                }

                quadVertices.position(0)
                GLES20.glEnableVertexAttribArray(aPositionLoc)
                GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 16, quadVertices)

                quadVertices.position(2)
                GLES20.glEnableVertexAttribArray(aTexCoordLoc)
                GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 16, quadVertices)

                GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)
                GLES20.glUniform2f(uTexelSizeLoc, 1.0f / w.toFloat(), 1.0f / h.toFloat())

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
                GLES20.glUniform1i(uContentLoc, 0)

                if (switchedProgram || lastUploadedSnapshot !== s) {
                    lastUploadedSnapshot = s
                    GLES20.glUniform1f(uLookActiveLoc, if (activeLook) 1f else 0f)
                    GLES20.glUniform1f(uLookSharpnessLoc, if (activeLook) s.sharpness else 0f)
                    GLES20.glUniform3f(uLookWbGainsLoc, s.wbR, s.wbG, s.wbB)
                    GLES20.glUniform1f(uLookExpScaleLoc, if (activeLook) s.expScale else 1f)
                    GLES20.glUniform1f(uLookContrastLoc, if (activeLook) s.contrast else 0f)
                    GLES20.glUniform1f(uLookHighlightsLoc, if (activeLook) s.highlights else 0f)
                    GLES20.glUniform1f(uLookShadowsLoc, if (activeLook) s.shadows else 0f)
                    GLES20.glUniform1f(uLookWhitesLoc, if (activeLook) s.whites else 0f)
                    GLES20.glUniform1f(uLookBlacksLoc, if (activeLook) s.blacks else 0f)
                    GLES20.glUniform1f(uLookVibranceLoc, if (activeLook) s.vibrance else 0f)
                    GLES20.glUniform1f(uLookSatLoc, if (activeLook) s.saturation else 0f)
                    GLES20.glUniform1f(uSplitActiveLoc, if (activeLook && s.hasSplit) 1f else 0f)
                    GLES20.glUniform3f(uSplitShadowLoc, s.shR, s.shG, s.shB)
                    GLES20.glUniform3f(uSplitMidLoc, s.midR, s.midG, s.midB)
                    GLES20.glUniform3f(uSplitHighLoc, s.hiR, s.hiG, s.hiB)
                    GLES20.glUniform1f(uMonoMixLoc, if (activeLook) s.monoMix else 0f)
                    GLES20.glUniform3f(uBwWeightsLoc, s.bwR, s.bwG, s.bwB)
                    GLES20.glUniform1f(uChannelSwapLoc, if (activeSwap) 1f else 0f)
                    GLES20.glUniform1f(uTargetHueLoc, s.targetHue)
                    GLES20.glUniform1f(uHueWidthLoc, s.hueWidth)
                    GLES20.glUniform1f(uHueFeatherLoc, s.hueFeather)
                    GLES20.glUniform1f(uDeltaHueLoc, s.deltaHue)
                    GLES20.glUniform1f(uDeltaSatLoc, s.deltaSat)
                    GLES20.glUniform1f(uDeltaLumLoc, s.deltaLum)
                    GLES20.glUniform1f(uActiveLoc, if (activePoint) 1f else 0f)
                    GLES20.glUniform1f(uBandsActiveLoc, if (activeBands) 1f else 0f)

                    GLES20.glUniform4f(uBandHueALoc, s.bandHue[0], s.bandHue[1], s.bandHue[2], s.bandHue[3])
                    GLES20.glUniform4f(uBandHueBLoc, s.bandHue[4], s.bandHue[5], s.bandHue[6], s.bandHue[7])
                    GLES20.glUniform4f(uBandHueCLoc, s.bandHue[8], s.bandHue[9], s.bandHue[10], s.bandHue[11])
                    GLES20.glUniform4f(uBandSatALoc, s.bandSat[0], s.bandSat[1], s.bandSat[2], s.bandSat[3])
                    GLES20.glUniform4f(uBandSatBLoc, s.bandSat[4], s.bandSat[5], s.bandSat[6], s.bandSat[7])
                    GLES20.glUniform4f(uBandSatCLoc, s.bandSat[8], s.bandSat[9], s.bandSat[10], s.bandSat[11])
                    GLES20.glUniform4f(uBandLumALoc, s.bandLum[0], s.bandLum[1], s.bandLum[2], s.bandLum[3])
                    GLES20.glUniform4f(uBandLumBLoc, s.bandLum[4], s.bandLum[5], s.bandLum[6], s.bandLum[7])
                    GLES20.glUniform4f(uBandLumCLoc, s.bandLum[8], s.bandLum[9], s.bandLum[10], s.bandLum[11])
                    GLES20.glUniform1f(uFocusPeakingLoc, if (s.focusPeaking) 1f else 0f)
                }

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                GLES20.glDisableVertexAttribArray(aPositionLoc)
                GLES20.glDisableVertexAttribArray(aTexCoordLoc)
            }

            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        } catch (t: Throwable) {
            Log.w(TAG, "drawCurrentFrame error", t)
        }
    }

    private fun cleanupSurfaceOnGlThread() {
        hasFirstFrame = false
        attachedOutputSt = null
        lastUsedProgram = 0
        lastUploadedSnapshot = null
        inputSurfaceTexture?.let { st ->
            try {
                st.setOnFrameAvailableListener(null)
                st.release()
            } catch (_: Throwable) {}
        }
        inputSurfaceTexture = null

        if (oesTextureId != 0) {
            val tex = intArrayOf(oesTextureId)
            GLES20.glDeleteTextures(1, tex, 0)
            oesTextureId = 0
        }

        if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            eglSurface = EGL14.EGL_NO_SURFACE
        }
    }

    private fun cleanupContextOnGlThread() {
        lastUsedProgram = 0
        lastUploadedSnapshot = null
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
        if (passthroughProgram != 0) {
            GLES20.glDeleteProgram(passthroughProgram)
            passthroughProgram = 0
        }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }

    companion object {
        private const val TAG = "PreTiramisuGlPreview"

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
            }
        """

        private const val PASSTHROUGH_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying highp vec2 vTexCoord;
            uniform samplerExternalOES uContent;
            void main() {
                gl_FragColor = texture2D(uContent, vTexCoord);
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;

            varying highp vec2 vTexCoord;
            uniform samplerExternalOES uContent;
            uniform vec2 uTexelSize;

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
            uniform float uFocusPeaking;

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

            void main() {
                vec4 color = texture2D(uContent, vTexCoord);
                vec3 rgb = color.rgb;

                if (uLookSharpness > 0.004) {
                    vec3 cN = texture2D(uContent, vTexCoord + vec2(0.0, -uTexelSize.y)).rgb;
                    vec3 cS = texture2D(uContent, vTexCoord + vec2(0.0,  uTexelSize.y)).rgb;
                    vec3 cW = texture2D(uContent, vTexCoord + vec2(-uTexelSize.x, 0.0)).rgb;
                    vec3 cE = texture2D(uContent, vTexCoord + vec2( uTexelSize.x, 0.0)).rgb;
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
                            ? 0.5 * pow(max(2.0 * targetL, 0.0005), k)
                            : 1.0 - 0.5 * pow(max(2.0 * (1.0 - targetL), 0.0005), k);
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

                if (uChannelSwap > 0.5) {
                    rgb = rgb.bgr;
                }

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

                if (uMonoMix > 0.004) {
                    float mono = clamp(dot(rgb, uBwWeights), 0.0, 1.0);
                    rgb = mix(rgb, vec3(mono), clamp(uMonoMix, 0.0, 1.0));
                }

                if (uFocusPeaking > 0.5) {
                    vec2 step2 = uTexelSize * 2.0;
                    float lN = dot(texture2D(uContent, vTexCoord + vec2(0.0, -step2.y)).rgb, vec3(0.2126, 0.7152, 0.0722));
                    float lS = dot(texture2D(uContent, vTexCoord + vec2(0.0,  step2.y)).rgb, vec3(0.2126, 0.7152, 0.0722));
                    float lW = dot(texture2D(uContent, vTexCoord + vec2(-step2.x, 0.0)).rgb, vec3(0.2126, 0.7152, 0.0722));
                    float lE = dot(texture2D(uContent, vTexCoord + vec2( step2.x, 0.0)).rgb, vec3(0.2126, 0.7152, 0.0722));
                    float edgeMag = abs(lN - lS) + abs(lW - lE);
                    float peakMask = smoothstep(0.16, 0.34, edgeMag);
                    rgb = mix(rgb, vec3(0.22, 0.88, 1.0), peakMask * 0.88);
                }

                gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), color.a);
            }
        """
    }
}
