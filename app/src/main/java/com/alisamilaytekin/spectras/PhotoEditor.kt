@file:Suppress("SpellCheckingInspection")

package com.alisamilaytekin.spectras

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

private enum class EditorTab { WB, EXPOSURE, COLOR, LOOKS }

internal data class PhotoEditConfig(
    val kelvin: Float = 5500f,
    val tint: Float = 0f,
    val exposureEv: Float = 0f,
    val saturation: Float = DEFAULT_SATURATION,
    val contrast: Float = DEFAULT_CONTRAST,
    val channelSwap: Boolean = false,
    val colorBands: ColorBandsConfig = ColorBandsConfig(),
    val targetedColor: TargetedColorConfig = TargetedColorConfig()
) {
    val isWbModified: Boolean
        get() = abs(kelvin - 5500f) > 40f || abs(tint) > 1.5f

    val isExposureModified: Boolean
        get() = abs(exposureEv) > 0.03f

    val isColorModified: Boolean
        get() = abs(saturation - DEFAULT_SATURATION) > 0.02f ||
            abs(contrast - DEFAULT_CONTRAST) > 0.02f ||
            channelSwap ||
            colorBands.hasAnyModification ||
            (targetedColor.isActive && (targetedColor.deltaHue != 0f || targetedColor.deltaSat != 0f || targetedColor.deltaLum != 0f))

    val hasAnyEdit: Boolean
        get() = isWbModified || isExposureModified || isColorModified
}

private fun computeEditorWbGains(kelvin: Float, tint: Float): Triple<Float, Float, Float> {
    val temp = ((kelvin - 5500f) / 4500f).coerceIn(-0.95f, 1.0f)
    val tintN = (tint / 100f).coerceIn(-1.0f, 1.0f)
    val rRaw = (1.0f + temp * 0.42f + tintN * 0.12f).coerceAtLeast(0.25f)
    val gRaw = (1.0f - tintN * 0.30f).coerceAtLeast(0.25f)
    val bRaw = (1.0f - temp * 0.42f + tintN * 0.12f).coerceAtLeast(0.25f)
    val lumNorm = (0.2126f * rRaw + 0.7152f * gRaw + 0.0722f * bRaw).coerceAtLeast(0.01f)
    return Triple(rRaw / lumNorm, gRaw / lumNorm, bRaw / lumNorm)
}

private const val EDITOR_FULL_SKSL = """
    uniform shader uContent;
    uniform vec3 uWbGains;
    uniform float uExposureScale;
    uniform float uContrast;
    uniform float uSaturation;
    uniform float uChannelSwap;
    uniform float uTargetHue;
    uniform float uHueWidth;
    uniform float uHueFeather;
    uniform float uDeltaHue;
    uniform float uDeltaSat;
    uniform float uDeltaLum;
    uniform float uPointActive;
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

    float applySCurve(float v, float k) {
        float x = clamp(v, 0.0, 1.0);
        if (x <= 0.5) {
            return 0.5 * pow(max(2.0 * x, 0.00001), k);
        } else {
            return 1.0 - 0.5 * pow(max(2.0 * (1.0 - x), 0.00001), k);
        }
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
        vec3 rgb = color.rgb;

        // 1) White balance + exposure
        rgb = clamp(rgb * uWbGains * uExposureScale, 0.0, 1.0);

        // 2) Midpoint-anchored perceptual contrast S-curve
        if (abs(uContrast - 1.0) > 0.005) {
            rgb = vec3(
                applySCurve(rgb.r, uContrast),
                applySCurve(rgb.g, uContrast),
                applySCurve(rgb.b, uContrast)
            );
        }

        // 3) Global saturation
        if (abs(uSaturation - 1.0) > 0.005) {
            float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
            rgb = clamp(mix(vec3(luma), rgb, uSaturation), 0.0, 1.0);
        }

        // 4) Infrared R<->B Channel Swap
        if (uChannelSwap > 0.5) {
            rgb = rgb.bgr;
        }

        // 5) 8-Band Color Histogram Mixer
        if (uBandsActive > 0.5) {
            vec3 hsl = rgb2hsl(rgb);
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
                    vec3 acc = rgb * (1.0 - wTot);
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
                    rgb = acc;
                }
            }
        }

        // 6) 360-degree Point Color Pipette
        if (uPointActive > 0.5) {
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
                vec3 shifted = hsl;
                shifted.x = mod(hsl.x + uDeltaHue + 360.0, 360.0);
                shifted.y = clamp(hsl.y * (1.0 + uDeltaSat), 0.0, 1.0);
                shifted.z = clamp(hsl.z + uDeltaLum * 0.45, 0.0, 1.0);
                rgb = mix(rgb, hsl2rgb(shifted), weight);
            }
        }

        return vec4(rgb, color.a);
    }
"""

private fun updateEditorImageViewShader(
    view: ImageView?,
    config: PhotoEditConfig,
    bypass: Boolean
) {
    if (view == null) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
            if (bypass || !config.hasAnyEdit) {
                view.setRenderEffect(null)
                return
            }
            val shader = RuntimeShader(EDITOR_FULL_SKSL)
            val (rG, gG, bG) = computeEditorWbGains(config.kelvin, config.tint)
            val evScale = 2.0f.pow(config.exposureEv)
            val pt = config.targetedColor
            val hasPoint = pt.isActive && (pt.deltaHue != 0f || pt.deltaSat != 0f || pt.deltaLum != 0f)
            val hasBands = config.colorBands.hasAnyModification

            shader.setFloatUniform("uWbGains", rG, gG, bG)
            shader.setFloatUniform("uExposureScale", evScale)
            shader.setFloatUniform("uContrast", config.contrast)
            shader.setFloatUniform("uSaturation", config.saturation)
            shader.setFloatUniform("uChannelSwap", if (config.channelSwap) 1.0f else 0.0f)
            shader.setFloatUniform("uTargetHue", pt.targetHue)
            shader.setFloatUniform("uHueWidth", pt.hueWidth)
            shader.setFloatUniform("uHueFeather", pt.hueFeather)
            shader.setFloatUniform("uDeltaHue", pt.deltaHue)
            shader.setFloatUniform("uDeltaSat", pt.deltaSat)
            shader.setFloatUniform("uDeltaLum", pt.deltaLum)
            shader.setFloatUniform("uPointActive", if (hasPoint) 1.0f else 0.0f)
            shader.setFloatUniform("uBandsActive", if (hasBands) 1.0f else 0.0f)

            val b = config.colorBands.bands
            shader.setFloatUniform("uBandHueA", b[0].deltaHue, b[1].deltaHue, b[2].deltaHue, b[3].deltaHue)
            shader.setFloatUniform("uBandHueB", b[4].deltaHue, b[5].deltaHue, b[6].deltaHue, b[7].deltaHue)
            shader.setFloatUniform("uBandSatA", b[0].deltaSat, b[1].deltaSat, b[2].deltaSat, b[3].deltaSat)
            shader.setFloatUniform("uBandSatB", b[4].deltaSat, b[5].deltaSat, b[6].deltaSat, b[7].deltaSat)
            shader.setFloatUniform("uBandLumA", b[0].deltaLum, b[1].deltaLum, b[2].deltaLum, b[3].deltaLum)
            shader.setFloatUniform("uBandLumB", b[4].deltaLum, b[5].deltaLum, b[6].deltaLum, b[7].deltaLum)

            view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "uContent"))
        } catch (e: Throwable) {
            Log.e("PhotoEditor", "AGSL RenderEffect failed", e)
        }
    }
}

private fun applyFullPhotoEditToBitmap(source: Bitmap, config: PhotoEditConfig): Bitmap {
    val work = source.copy(Bitmap.Config.ARGB_8888, true)
    if (!config.hasAnyEdit) return work

    val (rG, gG, bG) = computeEditorWbGains(config.kelvin, config.tint)
    val evScale = 2.0f.pow(config.exposureEv)
    val rMul = rG * evScale
    val gMul = gG * evScale
    val bMul = bG * evScale
    val doWbExp = abs(rMul - 1f) > 0.004f || abs(gMul - 1f) > 0.004f || abs(bMul - 1f) > 0.004f
    val doContrast = abs(config.contrast - 1f) > 0.005f
    val doSat = abs(config.saturation - 1f) > 0.005f

    // Precompute fast 256-entry perceptual S-curve LUT for crisp export speed
    val contrastLut = if (doContrast) {
        IntArray(256) { idx ->
            val x = idx / 255f
            val y = if (x <= 0.5f) {
                0.5f * (2f * x).coerceAtLeast(0.00001f).pow(config.contrast)
            } else {
                1.0f - 0.5f * (2f * (1f - x)).coerceAtLeast(0.00001f).pow(config.contrast)
            }
            (y * 255f).roundToInt().coerceIn(0, 255)
        }
    } else null

    val w = work.width
    val h = work.height
    val stripHeight = 256.coerceAtMost(h)
    val rowBuf = IntArray(w * stripHeight)

    if (doWbExp || doContrast || doSat) {
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

                if (doWbExp) {
                    r = (r * rMul).roundToInt().coerceIn(0, 255)
                    g = (g * gMul).roundToInt().coerceIn(0, 255)
                    b = (b * bMul).roundToInt().coerceIn(0, 255)
                }
                if (contrastLut != null) {
                    r = contrastLut[r]
                    g = contrastLut[g]
                    b = contrastLut[b]
                }
                if (doSat) {
                    val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                    r = (luma + (r - luma) * config.saturation).roundToInt().coerceIn(0, 255)
                    g = (luma + (g - luma) * config.saturation).roundToInt().coerceIn(0, 255)
                    b = (luma + (b - luma) * config.saturation).roundToInt().coerceIn(0, 255)
                }
                rowBuf[i] = a or (r shl 16) or (g shl 8) or b
            }
            work.setPixels(rowBuf, 0, w, 0, y, w, rows)
            y += rows
        }
    }

    return applyTargetedHslToBitmap(
        bitmap = work,
        config = config.targetedColor,
        colorBands = config.colorBands,
        channelSwap = config.channelSwap
    )
}

private fun decodeUprightBitmapFromUri(context: Context, uri: Uri, maxEdge: Int): Bitmap? {
    return try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (longEdge / (sample * 2) >= maxEdge) {
            sample *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        val orientation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                resolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
        } else {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return decoded
        }
        val transformed = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (transformed !== decoded) decoded.recycle()
        transformed
    } catch (e: Throwable) {
        Log.e("PhotoEditor", "Failed to decode URI: $uri", e)
        null
    }
}

private fun computePhotoSpotWb(bitmap: Bitmap, cx: Int, cy: Int): Pair<Float, Float> {
    var sumR = 0.0
    var sumG = 0.0
    var sumB = 0.0
    var count = 0
    val radius = 10
    val x0 = (cx - radius).coerceIn(0, bitmap.width - 1)
    val x1 = (cx + radius).coerceIn(0, bitmap.width - 1)
    val y0 = (cy - radius).coerceIn(0, bitmap.height - 1)
    val y1 = (cy + radius).coerceIn(0, bitmap.height - 1)
    for (y in y0..y1) {
        for (x in x0..x1) {
            val c = bitmap[x, y]
            sumR += AndroidColor.red(c)
            sumG += AndroidColor.green(c)
            sumB += AndroidColor.blue(c)
            count++
        }
    }
    if (count == 0) return 5500f to 0f
    val mr = (sumR / count).toFloat().coerceAtLeast(8f)
    val mg = (sumG / count).toFloat().coerceAtLeast(8f)
    val mb = (sumB / count).toFloat().coerceAtLeast(8f)

    // Solve temp such that mr*(1 + 0.42*temp) ~= mb*(1 - 0.42*temp)
    val temp = (((mb - mr) / (mr + mb).coerceAtLeast(1f)) / 0.42f).coerceIn(-0.85f, 0.95f)
    val kelvin = (5500f + temp * 4500f).coerceIn(1500f, 10000f)

    val rbAvg = (mr * (1f + temp * 0.42f) + mb * (1f - temp * 0.42f)) * 0.5f
    val tintRatio = ((mg - rbAvg) / (mg + rbAvg).coerceAtLeast(1f)) / 0.30f
    val tint = (tintRatio * 100f).coerceIn(-100f, 100f)
    return kelvin to tint
}

private fun computePhotoAutoWb(bitmap: Bitmap): Pair<Float, Float> {
    var sumR = 0.0
    var sumG = 0.0
    var sumB = 0.0
    var count = 0
    val stepX = maxOf(1, bitmap.width / 32)
    val stepY = maxOf(1, bitmap.height / 32)
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val c = bitmap[x, y]
            val r = AndroidColor.red(c)
            val g = AndroidColor.green(c)
            val b = AndroidColor.blue(c)
            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            if (luma in 35f..235f) {
                sumR += r
                sumG += g
                sumB += b
                count++
            }
            x += stepX
        }
        y += stepY
    }
    if (count == 0) return 5500f to 0f
    val mr = (sumR / count).toFloat().coerceAtLeast(8f)
    val mg = (sumG / count).toFloat().coerceAtLeast(8f)
    val mb = (sumB / count).toFloat().coerceAtLeast(8f)
    val temp = ((((mb - mr) / (mr + mb).coerceAtLeast(1f)) / 0.42f) * 0.72f).coerceIn(-0.65f, 0.65f)
    val kelvin = (5500f + temp * 4500f).coerceIn(2200f, 9200f)
    val rbAvg = (mr * (1f + temp * 0.42f) + mb * (1f - temp * 0.42f)) * 0.5f
    val tint = ((((mg - rbAvg) / (mg + rbAvg).coerceAtLeast(1f)) / 0.30f) * 70f).coerceIn(-60f, 60f)
    return kelvin to tint
}

private fun computePhotoAutoContrast(bitmap: Bitmap): Float {
    val hist = IntArray(256)
    var total = 0
    val stepX = maxOf(1, bitmap.width / 36)
    val stepY = maxOf(1, bitmap.height / 36)
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val c = bitmap[x, y]
            val luma = (0.299f * AndroidColor.red(c) + 0.587f * AndroidColor.green(c) + 0.114f * AndroidColor.blue(c)).roundToInt()
            hist[luma.coerceIn(0, 255)]++
            total++
            x += stepX
        }
        y += stepY
    }
    if (total == 0) return 1.0f
    val lowCut = (total * 0.008f).roundToInt()
    val highCut = (total * 0.992f).roundToInt()
    var acc = 0
    var pLow = 0
    var pHigh = 255
    for (i in 0 until 256) {
        acc += hist[i]
        if (acc >= lowCut && pLow == 0) pLow = i
        if (acc >= highCut) {
            pHigh = i
            break
        }
    }
    val span = (pHigh - pLow).coerceAtLeast(20)
    return when {
        span < 110 -> 1.24f
        span < 150 -> 1.15f
        span < 185 -> 1.08f
        span > 230 -> 0.95f
        else -> 1.02f
    }
}

@Composable
internal fun PhotoEditorScreen(
    initialUri: Uri?,
    photoNonce: Int,
    lastCapturedUri: Uri?,
    savedPresets: List<CameraPreset>,
    onSaveCustomPreset: (CameraPreset) -> Unit,
    onDeleteCustomPreset: (CameraPreset) -> Unit,
    onSwitchToCamera: (wantPro: Boolean) -> Unit,
    onPhotoSaved: (Uri) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val appLocale = remember { Locale.getDefault() }

    var currentUri by remember { mutableStateOf(initialUri) }
    var sourcePreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var smallSourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var fallbackRenderedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageViewRef by remember { mutableStateOf<ImageView?>(null) }

    var isLoadingPhoto by remember { mutableStateOf(false) }
    var isSavingPhoto by remember { mutableStateOf(false) }
    var editConfig by remember { mutableStateOf(PhotoEditConfig()) }
    var activeTab by remember { mutableStateOf(EditorTab.LOOKS) }
    var activeWbSubSlider by remember { mutableStateOf(WbSubSlider.KELVIN) }
    var colorPanelMode by remember { mutableStateOf(ColorPanelMode.GENEL) }
    var activeColorSubSlider by remember { mutableStateOf(ColorSubSlider.DOYGUNLUK) }
    var selectedColorBand by remember { mutableStateOf(ColorBand.RED) }
    var activeBandSubSlider by remember { mutableStateOf(PointColorSubSlider.TON) }
    var activePointSubSlider by remember { mutableStateOf(PointColorSubSlider.TON) }
    var isDeckCollapsed by remember { mutableStateOf(false) }
    var isAnySliderDragging by remember { mutableStateOf(false) }
    var isComparingOriginal by remember { mutableStateOf(false) }
    var isSpotWbActive by remember { mutableStateOf(false) }
    var isPipetteActive by remember { mutableStateOf(false) }
    var pickedMarkerPos by remember { mutableStateOf<Offset?>(null) }
    var liveHistogram by remember { mutableStateOf(ProHistogramData()) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var statusNonce by remember { mutableIntStateOf(0) }
    var lastExportedUri by remember { mutableStateOf<Uri?>(null) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    fun showEditorStatus(msg: String) {
        statusText = msg
        statusNonce++
    }

    LaunchedEffect(statusNonce) {
        if (statusText != null) {
            delay(1800)
            statusText = null
        }
    }

    LaunchedEffect(pickedMarkerPos, isSpotWbActive, isPipetteActive) {
        if (pickedMarkerPos != null && !isSpotWbActive && !isPipetteActive) {
            delay(1500)
            pickedMarkerPos = null
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { pickedUri: Uri? ->
        if (pickedUri != null) {
            currentUri = pickedUri
            lastExportedUri = null
        }
    }

    LaunchedEffect(initialUri, photoNonce) {
        if (initialUri != null && initialUri != currentUri) {
            currentUri = initialUri
            lastExportedUri = null
        }
    }

    // Load upright preview bitmap whenever URI changes
    LaunchedEffect(currentUri) {
        val uri = currentUri
        if (uri == null) {
            sourcePreviewBitmap = null
            smallSourceBitmap = null
            fallbackRenderedBitmap = null
            return@LaunchedEffect
        }
        isLoadingPhoto = true
        val loaded = withContext(Dispatchers.IO) {
            decodeUprightBitmapFromUri(context, uri, maxEdge = 1500)
        }
        if (loaded != null) {
            sourcePreviewBitmap = loaded
            val smallW = 180
            val smallH = (loaded.height * smallW / loaded.width.coerceAtLeast(1)).coerceIn(60, 260)
            smallSourceBitmap = Bitmap.createScaledBitmap(loaded, smallW, smallH, true)
            fallbackRenderedBitmap = null
            editConfig = PhotoEditConfig()
            isSpotWbActive = false
            isPipetteActive = false
        } else {
            showEditorStatus(context.getString(R.string.editor_load_failed))
        }
        isLoadingPhoto = false
    }

    // Real-time GPU shader on Android 13+ (API 33+)
    LaunchedEffect(editConfig, isComparingOriginal, imageViewRef, sourcePreviewBitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            updateEditorImageViewShader(
                view = imageViewRef,
                config = editConfig,
                bypass = isComparingOriginal
            )
        }
    }

    // Live CPU preview fallback for Android 5–12 (API 21–32) so EVERY phone sees live edits!
    LaunchedEffect(editConfig, isComparingOriginal, sourcePreviewBitmap) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val src = sourcePreviewBitmap ?: return@LaunchedEffect
            if (isComparingOriginal || !editConfig.hasAnyEdit) {
                fallbackRenderedBitmap = null
                return@LaunchedEffect
            }
            delay(45)
            val rendered = withContext(Dispatchers.Default) {
                val maxSide = 860
                val working = if (maxOf(src.width, src.height) > maxSide) {
                    val scale = maxSide.toFloat() / maxOf(src.width, src.height).toFloat()
                    Bitmap.createScaledBitmap(
                        src,
                        (src.width * scale).roundToInt().coerceAtLeast(1),
                        (src.height * scale).roundToInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    src
                }
                val out = applyFullPhotoEditToBitmap(working, editConfig)
                if (working !== src && working !== out) working.recycle()
                out
            }
            fallbackRenderedBitmap = rendered
        }
    }

    // Live edited RGB + Hue Histogram computation (debounced for 60fps UI responsiveness)
    LaunchedEffect(editConfig, isComparingOriginal, smallSourceBitmap) {
        val tiny = smallSourceBitmap ?: return@LaunchedEffect
        delay(80)
        val hist = withContext(Dispatchers.Default) {
            val gradedTiny = if (isComparingOriginal || !editConfig.hasAnyEdit) {
                tiny
            } else {
                applyFullPhotoEditToBitmap(tiny, editConfig)
            }
            val data = computeProHistogramData(gradedTiny, 48)
            if (gradedTiny !== tiny && !gradedTiny.isRecycled) gradedTiny.recycle()
            data
        }
        liveHistogram = hist
    }

    fun exportPhoto(andShare: Boolean) {
        val uri = currentUri ?: return
        if (isSavingPhoto) return
        isSavingPhoto = true
        coroutineScope.launch {
            var exportedUri: Uri? = null
            val ok = withContext(Dispatchers.IO) {
                var fullBmp: Bitmap? = null
                var gradedBmp: Bitmap? = null
                try {
                    fullBmp = decodeUprightBitmapFromUri(context, uri, maxEdge = 4096)
                    if (fullBmp != null) {
                        gradedBmp = applyFullPhotoEditToBitmap(fullBmp, editConfig)
                        saveOutputToGallery(
                            context = context,
                            rawBytes = null,
                            processedBitmap = gradedBmp,
                            exifOrientation = ExifInterface.ORIENTATION_NORMAL
                        ) { saved ->
                            exportedUri = saved
                        }
                    } else {
                        false
                    }
                } catch (e: Throwable) {
                    Log.e("PhotoEditor", "Export failed", e)
                    false
                } finally {
                    if (gradedBmp != null && gradedBmp !== fullBmp) gradedBmp.recycle()
                    fullBmp?.recycle()
                }
            }
            isSavingPhoto = false
            if (ok) {
                val saved = exportedUri
                if (saved != null) {
                    lastExportedUri = saved
                    onPhotoSaved(saved)
                }
                showEditorStatus(context.getString(R.string.editor_saved_status))
                if (andShare && saved != null) {
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, saved)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(shareIntent, context.getString(R.string.editor_share_chooser))
                        )
                    } catch (_: Exception) {}
                }
            } else {
                showEditorStatus(context.getString(R.string.editor_save_failed))
            }
        }
    }

    fun applyPresetToEditor(preset: CameraPreset) {
        isSpotWbActive = false
        isPipetteActive = false
        editConfig = PhotoEditConfig(
            kelvin = preset.kelvin.coerceIn(1500f, 10000f),
            tint = preset.tint.coerceIn(-100f, 100f),
            exposureEv = 0f,
            saturation = preset.saturation.coerceIn(0f, 2.0f),
            contrast = preset.contrast.coerceIn(0.50f, 1.80f),
            channelSwap = preset.channelSwap,
            colorBands = preset.colorBands,
            targetedColor = TargetedColorConfig()
        )
        showEditorStatus(context.getString(R.string.status_preset_applied, preset.name))
    }

    val deckBgAlpha by animateFloatAsState(
        targetValue = if (isAnySliderDragging) 0.14f else 0.78f,
        animationSpec = tween(150),
        label = "edDeckBgAlpha"
    )
    val ghostChromeAlpha by animateFloatAsState(
        targetValue = if (isAnySliderDragging) 0.08f else 1f,
        animationSpec = tween(150),
        label = "edGhostAlpha"
    )

    val hasPhoto = sourcePreviewBitmap != null
    val strModeAuto = stringResource(R.string.mode_auto)
    val strModePro = stringResource(R.string.mode_pro)
    val strModeStudio = stringResource(R.string.mode_studio)
    val strWbShort = stringResource(R.string.label_wb_short)
    val strTintShort = stringResource(R.string.label_tint)
    val strEvShort = stringResource(R.string.label_ev_short)
    val strBadgeSwap = stringResource(R.string.badge_channel_swap)
    val strTapWb = stringResource(R.string.status_tap_wb)
    val strTapColor = stringResource(R.string.status_tap_color)
    val strCancelPick = stringResource(R.string.action_cancel_pick)
    val strCompare = stringResource(R.string.action_compare)
    val strCompareBanner = stringResource(R.string.editor_compare_banner)
    val strPanelCd = stringResource(R.string.cd_panel_toggle)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IrPalette.VoidBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1) ÜST BAR: [Oto | Pro | Stüdyo] + [Aç] · [Kaydet] · [Paylaş]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(IrPalette.HudCard)
                    .border(1.dp, IrPalette.BorderSubtle, RoundedCornerShape(50))
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeSegmentItem(
                    text = strModeAuto,
                    selected = false,
                    activeColor = IrPalette.IrEmerald,
                    enabled = true,
                    onClick = { onSwitchToCamera(false) }
                )
                ModeSegmentItem(
                    text = strModePro,
                    selected = false,
                    activeColor = IrPalette.IrAmber,
                    enabled = true,
                    onClick = { onSwitchToCamera(true) }
                )
                ModeSegmentItem(
                    text = strModeStudio,
                    selected = true,
                    activeColor = IrPalette.IrCyan,
                    enabled = true,
                    onClick = {}
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TopBadgeButton(
                    text = stringResource(R.string.editor_pick_photo),
                    active = false,
                    accentColor = IrPalette.IrCyan,
                    enabled = !isSavingPhoto,
                    onClick = { photoPickerLauncher.launch("image/*") }
                )
                if (hasPhoto) {
                    TopIconButton(
                        active = false,
                        accentColor = IrPalette.IrCyan,
                        contentDesc = stringResource(R.string.editor_share),
                        onClick = { exportPhoto(andShare = true) }
                    ) { tint ->
                        ShareIcon(tint = tint, modifier = Modifier.size(14.dp))
                    }
                    TopBadgeButton(
                        text = if (isSavingPhoto) stringResource(R.string.editor_saving) else stringResource(R.string.editor_save),
                        active = true,
                        accentColor = IrPalette.IrAmber,
                        enabled = !isSavingPhoto,
                        onClick = { exportPhoto(andShare = false) }
                    )
                }
            }
        }

        // 2) ANA TUVAL: Tam ekran fotoğraf + canlı histogram + yüzen yarı saydam Pro Stüdyo paneli
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(IrPalette.VoidBlack),
            contentAlignment = Alignment.Center
        ) {
            val previewBmp = sourcePreviewBitmap
            if (previewBmp == null && !isLoadingPhoto) {
                // Boş Stüdyo başlangıç kartı
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(IrPalette.HudCard)
                        .border(1.dp, IrPalette.BorderSubtle, RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(IrPalette.IrCyan.copy(alpha = 0.14f))
                            .border(1.dp, IrPalette.IrCyan.copy(alpha = 0.65f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        GalleryIcon(tint = IrPalette.IrCyan, modifier = Modifier.size(26.dp))
                    }
                    Text(
                        text = stringResource(R.string.editor_empty_title),
                        color = IrPalette.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.editor_empty_subtitle),
                        color = IrPalette.TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = IrPalette.IrAmber),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.editor_choose_gallery),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    if (lastCapturedUri != null) {
                        OutlinedButton(
                            onClick = { currentUri = lastCapturedUri },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, IrPalette.IrCyan.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.editor_use_last_shot),
                                color = IrPalette.IrCyan,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else if (previewBmp != null) {
                val displayBmp = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && !isComparingOriginal) {
                    fallbackRenderedBitmap ?: previewBmp
                } else {
                    previewBmp
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clipToBounds()
                        .pointerInput(isSpotWbActive, isPipetteActive, previewBmp, editConfig) {
                            detectTapGestures { offset ->
                                if (!isSpotWbActive && !isPipetteActive) return@detectTapGestures
                                val viewW = size.width.toFloat().coerceAtLeast(1f)
                                val viewH = size.height.toFloat().coerceAtLeast(1f)
                                val bmpW = previewBmp.width.toFloat().coerceAtLeast(1f)
                                val bmpH = previewBmp.height.toFloat().coerceAtLeast(1f)
                                val scale = minOf(viewW / bmpW, viewH / bmpH)
                                val drawnW = bmpW * scale
                                val drawnH = bmpH * scale
                                val left = (viewW - drawnW) * 0.5f
                                val top = (viewH - drawnH) * 0.5f
                                val bx = ((offset.x - left) / scale).roundToInt().coerceIn(0, previewBmp.width - 1)
                                val by = ((offset.y - top) / scale).roundToInt().coerceIn(0, previewBmp.height - 1)

                                if (isSpotWbActive) {
                                    val (newK, newT) = computePhotoSpotWb(previewBmp, bx, by)
                                    editConfig = editConfig.copy(kelvin = newK, tint = newT)
                                    pickedMarkerPos = offset
                                    isSpotWbActive = false
                                    showEditorStatus(context.getString(R.string.editor_spot_wb_done))
                                } else if (isPipetteActive) {
                                    val px = previewBmp[bx, by]
                                    val r = AndroidColor.red(px)
                                    val g = AndroidColor.green(px)
                                    val b = AndroidColor.blue(px)
                                    val sr = if (editConfig.channelSwap) b else r
                                    val sb = if (editConfig.channelSwap) r else b
                                    val hsv = FloatArray(3)
                                    AndroidColor.RGBToHSV(sr, g, sb, hsv)
                                    val previewInt = AndroidColor.rgb(sr, g, sb)
                                    editConfig = editConfig.copy(
                                        targetedColor = editConfig.targetedColor.copy(
                                            targetHue = hsv[0],
                                            targetSat = hsv[1],
                                            targetLum = hsv[2],
                                            isActive = true,
                                            previewColorInt = previewInt
                                        )
                                    )
                                    pickedMarkerPos = offset
                                    isPipetteActive = false
                                    activeTab = EditorTab.COLOR
                                    colorPanelMode = ColorPanelMode.PIPET
                                    showEditorStatus(context.getString(R.string.status_color_picked, getHueName(context, hsv[0])))
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                adjustViewBounds = false
                                if (!displayBmp.isRecycled) setImageBitmap(displayBmp)
                                imageViewRef = this
                            }
                        },
                        update = { iv ->
                            if (imageViewRef !== iv) imageViewRef = iv
                            if (!displayBmp.isRecycled) iv.setImageBitmap(displayBmp)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                updateEditorImageViewShader(iv, editConfig, isComparingOriginal)
                            }
                        },
                        onRelease = { iv ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                iv.setRenderEffect(null)
                            }
                            iv.setImageBitmap(null)
                            if (imageViewRef === iv) imageViewRef = null
                        }
                    )

                    // Aktif seçici kenar çerçevesi & hedef işareti
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (isSpotWbActive || isPipetteActive) {
                            drawRect(
                                color = if (isPipetteActive) IrPalette.IrTeal.copy(alpha = 0.85f) else IrPalette.IrAmber.copy(alpha = 0.85f),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                        pickedMarkerPos?.let { pos ->
                            drawCircle(
                                color = if (isPipetteActive) IrPalette.IrTeal else IrPalette.IrAmber,
                                radius = 20.dp.toPx(),
                                center = pos,
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(color = Color.White, radius = 3.5.dp.toPx(), center = pos)
                        }
                    }

                    // Üst HUD: Sol canlı telemetri + Sağ canlı RGB histogram
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(IrPalette.HudScrim)
                                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = "${editConfig.kelvin.toInt()}K · $strTintShort ${formatSigned(editConfig.tint)} · $strEvShort ${formatSignedDecimal(editConfig.exposureEv, appLocale)}",
                                    color = IrPalette.TextPrimary,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }

                            LiveHistogramOverlay(
                                data = liveHistogram,
                                modifier = Modifier.clickable {
                                    activeTab = EditorTab.COLOR
                                    colorPanelMode = ColorPanelMode.BANTLAR
                                    isDeckCollapsed = false
                                }
                            )
                        }

                        // Seçici aktif şeridi (Pipet / Noktasal BD)
                        val toolPrompt = when {
                            isSpotWbActive -> strTapWb
                            isPipetteActive -> strTapColor
                            else -> null
                        }
                        AnimatedVisibility(visible = toolPrompt != null, enter = fadeIn(tween(140)), exit = fadeOut(tween(160))) {
                            toolPrompt?.let { prompt ->
                                val accent = if (isPipetteActive) IrPalette.IrTeal else IrPalette.IrAmber
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(IrPalette.HudScrim)
                                        .border(1.dp, accent.copy(alpha = 0.85f), RoundedCornerShape(50))
                                        .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(accent))
                                    Text(text = prompt, color = IrPalette.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = strCancelPick,
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(accent)
                                            .clickable {
                                                isSpotWbActive = false
                                                isPipetteActive = false
                                            }
                                            .padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        val banner = if (isComparingOriginal) strCompareBanner else if (toolPrompt == null) statusText else null
                        AnimatedVisibility(visible = banner != null, enter = fadeIn(tween(140)), exit = fadeOut(tween(180))) {
                            banner?.let { msg ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (isComparingOriginal) IrPalette.IrEmerald.copy(alpha = 0.94f) else IrPalette.HudScrim)
                                        .border(
                                            1.dp,
                                            if (isComparingOriginal) IrPalette.IrEmerald else IrPalette.BorderBright,
                                            RoundedCornerShape(50)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = msg,
                                        color = if (isComparingOriginal) Color.Black else IrPalette.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // YÜZEN ALT STÜDYO PANELİ: Karşılaştır + Yarı saydam Pro kontrol paneli (Hayalet Mod & Oto Gizleme!)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(ghostChromeAlpha)
                                .padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .sizeIn(minWidth = 40.dp, minHeight = 34.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                isComparingOriginal = true
                                                tryAwaitRelease()
                                                isComparingOriginal = false
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (isComparingOriginal) IrPalette.IrEmerald else IrPalette.HudScrim)
                                        .border(
                                            0.8.dp,
                                            if (isComparingOriginal) IrPalette.IrEmerald else Color.White.copy(alpha = 0.20f),
                                            RoundedCornerShape(50)
                                        )
                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    CompareEyeIcon(
                                        tint = if (isComparingOriginal) Color.Black else IrPalette.TextPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = strCompare,
                                        color = if (isComparingOriginal) Color.Black else IrPalette.TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (editConfig.channelSwap) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(IrPalette.HudScrim)
                                        .border(0.8.dp, IrPalette.IrCyan.copy(alpha = 0.7f), RoundedCornerShape(50))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = strBadgeSwap,
                                        color = IrPalette.IrCyan,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = !isSpotWbActive && !isPipetteActive,
                            enter = fadeIn(tween(160)) + slideInVertically(tween(180)) { it / 3 },
                            exit = fadeOut(tween(140)) + slideOutVertically(tween(150)) { it / 3 }
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
                                AnimatedVisibility(
                                    visible = !isDeckCollapsed,
                                    enter = fadeIn(tween(140)) + slideInVertically(tween(160)) { it / 4 },
                                    exit = fadeOut(tween(120)) + slideOutVertically(tween(140)) { it / 4 }
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        when (activeTab) {
                                            EditorTab.WB -> {
                                                SegmentedChoiceRow(
                                                    options = listOf(
                                                        WbSubSlider.KELVIN to "${stringResource(R.string.sub_kelvin)} · ${editConfig.kelvin.toInt()}K",
                                                        WbSubSlider.TINT to "${stringResource(R.string.sub_tint_wb)} · ${formatSigned(editConfig.tint)}"
                                                    ),
                                                    selected = activeWbSubSlider,
                                                    enabled = true,
                                                    modifier = Modifier.alpha(ghostChromeAlpha),
                                                    onSelect = { activeWbSubSlider = it }
                                                )

                                                if (activeWbSubSlider == WbSubSlider.KELVIN) {
                                                    ProGradientSliderRow(
                                                        label = stringResource(R.string.slider_kelvin),
                                                        valueBadge = "${editConfig.kelvin.toInt()} K",
                                                        isModified = abs(editConfig.kelvin - 5500f) > 40f,
                                                        value = editConfig.kelvin,
                                                        defaultValue = 5500f,
                                                        snapTolerance = 140f,
                                                        valueRange = 1500f..10000f,
                                                        enabled = true,
                                                        gradient = Brush.horizontalGradient(
                                                            listOf(IrPalette.IrCyan, Color(0xFFFFF4E6), Color(0xFFFF6B2B))
                                                        ),
                                                        onDraggingChange = { isAnySliderDragging = it },
                                                        onStepMinus = { editConfig = editConfig.copy(kelvin = (editConfig.kelvin - 100f).coerceIn(1500f, 10000f)) },
                                                        onStepPlus = { editConfig = editConfig.copy(kelvin = (editConfig.kelvin + 100f).coerceIn(1500f, 10000f)) },
                                                        onReset = {
                                                            editConfig = editConfig.copy(kelvin = 5500f)
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        },
                                                        onValueChange = { editConfig = editConfig.copy(kelvin = it) }
                                                    )
                                                } else {
                                                    ProGradientSliderRow(
                                                        label = stringResource(R.string.slider_tint),
                                                        valueBadge = formatSigned(editConfig.tint),
                                                        isModified = abs(editConfig.tint) > 1.5f,
                                                        value = editConfig.tint,
                                                        defaultValue = 0f,
                                                        snapTolerance = 4f,
                                                        valueRange = -100f..100f,
                                                        enabled = true,
                                                        gradient = Brush.horizontalGradient(
                                                            listOf(IrPalette.IrEmerald, Color(0xFFE2E8F0), IrPalette.IrMagenta)
                                                        ),
                                                        onDraggingChange = { isAnySliderDragging = it },
                                                        onStepMinus = { editConfig = editConfig.copy(tint = (editConfig.tint - 5f).coerceIn(-100f, 100f)) },
                                                        onStepPlus = { editConfig = editConfig.copy(tint = (editConfig.tint + 5f).coerceIn(-100f, 100f)) },
                                                        onReset = {
                                                            editConfig = editConfig.copy(tint = 0f)
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        },
                                                        onValueChange = { editConfig = editConfig.copy(tint = it) }
                                                    )
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth().alpha(ghostChromeAlpha),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    ActionPillButton(
                                                        label = stringResource(R.string.action_auto_calibrate),
                                                        selected = false,
                                                        accentColor = IrPalette.IrAmber,
                                                        enabled = true,
                                                        modifier = Modifier.weight(1f),
                                                        leadingIcon = { tint -> SparkleAutoIcon(tint = tint, modifier = Modifier.size(13.dp)) },
                                                        onClick = {
                                                            val (k, t) = computePhotoAutoWb(previewBmp)
                                                            editConfig = editConfig.copy(kelvin = k, tint = t)
                                                            showEditorStatus(context.getString(R.string.editor_auto_wb_done))
                                                        }
                                                    )
                                                    ActionPillButton(
                                                        label = stringResource(R.string.action_spot_wb),
                                                        selected = isSpotWbActive,
                                                        accentColor = IrPalette.IrAmber,
                                                        enabled = true,
                                                        modifier = Modifier.weight(1f),
                                                        leadingIcon = { tint -> TargetCrosshairIcon(tint = tint, modifier = Modifier.size(13.dp)) },
                                                        onClick = {
                                                            isSpotWbActive = true
                                                            isPipetteActive = false
                                                        }
                                                    )
                                                }
                                            }

                                            EditorTab.EXPOSURE -> {
                                                ProGradientSliderRow(
                                                    label = stringResource(R.string.slider_exposure),
                                                    valueBadge = "${formatSignedDecimal(editConfig.exposureEv, appLocale)} $strEvShort",
                                                    isModified = abs(editConfig.exposureEv) > 0.03f,
                                                    value = editConfig.exposureEv,
                                                    defaultValue = 0f,
                                                    snapTolerance = 0.08f,
                                                    valueRange = -2.0f..2.0f,
                                                    enabled = true,
                                                    gradient = Brush.horizontalGradient(
                                                        listOf(Color(0xFF1E293B), Color(0xFF94A3B8), Color(0xFFFEF08A))
                                                    ),
                                                    onDraggingChange = { isAnySliderDragging = it },
                                                    onStepMinus = { editConfig = editConfig.copy(exposureEv = (editConfig.exposureEv - 0.1f).coerceIn(-2f, 2f)) },
                                                    onStepPlus = { editConfig = editConfig.copy(exposureEv = (editConfig.exposureEv + 0.1f).coerceIn(-2f, 2f)) },
                                                    onReset = {
                                                        editConfig = editConfig.copy(exposureEv = 0f)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    },
                                                    onValueChange = { editConfig = editConfig.copy(exposureEv = it) }
                                                )

                                                Row(
                                                    modifier = Modifier.fillMaxWidth().alpha(ghostChromeAlpha),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    ActionPillButton(
                                                        label = stringResource(R.string.action_auto_contrast),
                                                        selected = false,
                                                        accentColor = IrPalette.IrAmber,
                                                        enabled = true,
                                                        modifier = Modifier.weight(1f),
                                                        leadingIcon = { tint -> SparkleAutoIcon(tint = tint, modifier = Modifier.size(13.dp)) },
                                                        onClick = {
                                                            val rec = computePhotoAutoContrast(previewBmp)
                                                            editConfig = editConfig.copy(contrast = rec)
                                                            val fmt = String.format(appLocale, "%.2f", rec)
                                                            showEditorStatus(context.getString(R.string.status_auto_contrast_fmt, fmt))
                                                        }
                                                    )
                                                    ActionPillButton(
                                                        label = stringResource(R.string.action_channel_swap),
                                                        selected = editConfig.channelSwap,
                                                        accentColor = IrPalette.IrCyan,
                                                        enabled = true,
                                                        modifier = Modifier.weight(1f),
                                                        leadingIcon = { tint -> SwapChannelIcon(tint = tint, modifier = Modifier.size(13.dp)) },
                                                        onClick = {
                                                            val next = !editConfig.channelSwap
                                                            editConfig = editConfig.copy(channelSwap = next)
                                                            showEditorStatus(
                                                                context.getString(
                                                                    if (next) R.string.status_channel_swap_on
                                                                    else R.string.status_channel_swap_off
                                                                )
                                                            )
                                                        }
                                                    )
                                                }
                                            }

                                            EditorTab.COLOR -> {
                                                val bandsLabel = if (editConfig.colorBands.hasAnyModification) {
                                                    "${stringResource(R.string.color_mode_bands)} •"
                                                } else {
                                                    stringResource(R.string.color_mode_bands)
                                                }
                                                val pipetteLabel = if (editConfig.targetedColor.isActive) {
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
                                                    enabled = true,
                                                    modifier = Modifier.alpha(ghostChromeAlpha),
                                                    onSelect = { colorPanelMode = it }
                                                )

                                                when (colorPanelMode) {
                                                    ColorPanelMode.GENEL -> {
                                                        val satStr = String.format(appLocale, "%.2f", editConfig.saturation)
                                                        val conStr = String.format(appLocale, "%.2f", editConfig.contrast)
                                                        SegmentedChoiceRow(
                                                            options = listOf(
                                                                ColorSubSlider.DOYGUNLUK to "${stringResource(R.string.slider_saturation)} · ${satStr}x",
                                                                ColorSubSlider.KONTRAST to "${stringResource(R.string.slider_contrast)} · ${conStr}x"
                                                            ),
                                                            selected = activeColorSubSlider,
                                                            enabled = true,
                                                            modifier = Modifier.alpha(ghostChromeAlpha),
                                                            onSelect = { activeColorSubSlider = it }
                                                        )

                                                        if (activeColorSubSlider == ColorSubSlider.DOYGUNLUK) {
                                                            ProGradientSliderRow(
                                                                label = stringResource(R.string.slider_saturation),
                                                                valueBadge = "${satStr}x",
                                                                isModified = abs(editConfig.saturation - DEFAULT_SATURATION) > 0.02f,
                                                                value = editConfig.saturation,
                                                                defaultValue = DEFAULT_SATURATION,
                                                                snapTolerance = 0.04f,
                                                                valueRange = 0.0f..2.0f,
                                                                enabled = true,
                                                                gradient = Brush.horizontalGradient(
                                                                    listOf(Color(0xFF64748B), Color(0xFF94A3B8), Color(0xFF22C55E), Color(0xFFF59E0B), Color(0xFFEF4444))
                                                                ),
                                                                onDraggingChange = { isAnySliderDragging = it },
                                                                onStepMinus = { editConfig = editConfig.copy(saturation = (editConfig.saturation - 0.05f).coerceIn(0f, 2f)) },
                                                                onStepPlus = { editConfig = editConfig.copy(saturation = (editConfig.saturation + 0.05f).coerceIn(0f, 2f)) },
                                                                onReset = {
                                                                    editConfig = editConfig.copy(saturation = DEFAULT_SATURATION)
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                },
                                                                onValueChange = { editConfig = editConfig.copy(saturation = it) }
                                                            )
                                                        } else {
                                                            ProGradientSliderRow(
                                                                label = stringResource(R.string.slider_contrast),
                                                                valueBadge = "${conStr}x",
                                                                isModified = abs(editConfig.contrast - DEFAULT_CONTRAST) > 0.02f,
                                                                value = editConfig.contrast,
                                                                defaultValue = DEFAULT_CONTRAST,
                                                                snapTolerance = 0.04f,
                                                                valueRange = 0.50f..1.80f,
                                                                enabled = true,
                                                                gradient = Brush.horizontalGradient(
                                                                    listOf(Color(0xFF475569), Color(0xFFCBD5E1), Color.White)
                                                                ),
                                                                onDraggingChange = { isAnySliderDragging = it },
                                                                onStepMinus = { editConfig = editConfig.copy(contrast = (editConfig.contrast - 0.05f).coerceIn(0.50f, 1.80f)) },
                                                                onStepPlus = { editConfig = editConfig.copy(contrast = (editConfig.contrast + 0.05f).coerceIn(0.50f, 1.80f)) },
                                                                onReset = {
                                                                    editConfig = editConfig.copy(contrast = DEFAULT_CONTRAST)
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                },
                                                                onValueChange = { editConfig = editConfig.copy(contrast = it) }
                                                            )
                                                        }

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().alpha(ghostChromeAlpha),
                                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                        ) {
                                                            ActionPillButton(
                                                                label = stringResource(R.string.action_auto_contrast),
                                                                selected = false,
                                                                accentColor = IrPalette.IrAmber,
                                                                enabled = true,
                                                                modifier = Modifier.weight(1f),
                                                                leadingIcon = { tint -> SparkleAutoIcon(tint = tint, modifier = Modifier.size(12.dp)) },
                                                                onClick = {
                                                                    val rec = computePhotoAutoContrast(previewBmp)
                                                                    editConfig = editConfig.copy(contrast = rec)
                                                                    activeColorSubSlider = ColorSubSlider.KONTRAST
                                                                    showEditorStatus(context.getString(R.string.status_auto_contrast_fmt, String.format(appLocale, "%.2f", rec)))
                                                                }
                                                            )
                                                            ActionPillButton(
                                                                label = stringResource(R.string.action_channel_swap),
                                                                selected = editConfig.channelSwap,
                                                                accentColor = IrPalette.IrCyan,
                                                                enabled = true,
                                                                modifier = Modifier.weight(1f),
                                                                leadingIcon = { tint -> SwapChannelIcon(tint = tint, modifier = Modifier.size(12.dp)) },
                                                                onClick = {
                                                                    val next = !editConfig.channelSwap
                                                                    editConfig = editConfig.copy(channelSwap = next)
                                                                    showEditorStatus(
                                                                        context.getString(
                                                                            if (next) R.string.status_channel_swap_on
                                                                            else R.string.status_channel_swap_off
                                                                        )
                                                                    )
                                                                }
                                                            )
                                                            ActionPillButton(
                                                                label = stringResource(R.string.action_color_pipette),
                                                                selected = isPipetteActive,
                                                                accentColor = IrPalette.IrTeal,
                                                                enabled = true,
                                                                modifier = Modifier.weight(0.85f),
                                                                leadingIcon = { tint -> PipetteIcon(tint = tint, modifier = Modifier.size(12.dp)) },
                                                                onClick = {
                                                                    colorPanelMode = ColorPanelMode.PIPET
                                                                    isPipetteActive = true
                                                                    isSpotWbActive = false
                                                                }
                                                            )
                                                        }
                                                    }

                                                    ColorPanelMode.BANTLAR -> {
                                                        val bandAdj = editConfig.colorBands.get(selectedColorBand)
                                                        val bandLabel = stringResource(selectedColorBand.labelRes)
                                                        val bandShiftedHue = (((selectedColorBand.centerHue + bandAdj.deltaHue) % 360f + 360f) % 360f).coerceIn(0f, 359.99f)
                                                        val bandHasHueShift = abs(bandAdj.deltaHue) > 1f

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().alpha(ghostChromeAlpha),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                                modifier = Modifier.weight(1f, fill = false)
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(13.dp)
                                                                        .clip(CircleShape)
                                                                        .background(selectedColorBand.swatchColor)
                                                                        .border(1.dp, Color.White.copy(alpha = 0.75f), CircleShape)
                                                                )
                                                                if (bandHasHueShift) {
                                                                    val shiftedC = Color.hsv(
                                                                        hue = bandShiftedHue,
                                                                        saturation = (0.85f * (1f + bandAdj.deltaSat * 0.5f)).coerceIn(0.25f, 1f),
                                                                        value = (0.94f + bandAdj.deltaLum * 0.35f).coerceIn(0.30f, 1f)
                                                                    )
                                                                    Text("→", color = IrPalette.TextSecondary, fontSize = 10.sp)
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(13.dp)
                                                                            .clip(CircleShape)
                                                                            .background(shiftedC)
                                                                            .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                                                                    )
                                                                }
                                                                Text(
                                                                    text = if (bandHasHueShift) "$bandLabel → ${getHueName(context, bandShiftedHue)}" else bandLabel,
                                                                    color = IrPalette.TextPrimary,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                            if (bandAdj.isModified) {
                                                                Text(
                                                                    text = stringResource(R.string.action_reset_band),
                                                                    color = IrPalette.IrAmber,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    modifier = Modifier
                                                                        .clip(RoundedCornerShape(6.dp))
                                                                        .background(IrPalette.IrAmber.copy(alpha = 0.14f))
                                                                        .clickable {
                                                                            editConfig = editConfig.copy(
                                                                                colorBands = editConfig.colorBands.update(selectedColorBand) { BandHslAdjustment() }
                                                                            )
                                                                        }
                                                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                                                )
                                                            } else if (editConfig.colorBands.hasAnyModification) {
                                                                Text(
                                                                    text = stringResource(R.string.action_reset_all_bands),
                                                                    color = IrPalette.IrCoral,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    modifier = Modifier
                                                                        .clip(RoundedCornerShape(6.dp))
                                                                        .background(IrPalette.IrCoral.copy(alpha = 0.14f))
                                                                        .clickable { editConfig = editConfig.copy(colorBands = ColorBandsConfig()) }
                                                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                                                )
                                                            }
                                                        }

                                                        ColorHistogramBandPicker(
                                                            hueBins = liveHistogram.hueBins,
                                                            selectedBand = selectedColorBand,
                                                            config = editConfig.colorBands,
                                                            enabled = true,
                                                            modifier = Modifier.alpha(ghostChromeAlpha),
                                                            onSelectBand = { band ->
                                                                if (selectedColorBand != band) {
                                                                    selectedColorBand = band
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                }
                                                            }
                                                        )

                                                        SegmentedChoiceRow(
                                                            options = listOf(
                                                                PointColorSubSlider.TON to "${stringResource(R.string.sub_tint)} · ${formatSigned(bandAdj.deltaHue)}°",
                                                                PointColorSubSlider.DOYGUNLUK to "${stringResource(R.string.sub_sat_short)} · ${formatSigned(bandAdj.deltaSat * 100f)}%",
                                                                PointColorSubSlider.PARLAKLIK to "${stringResource(R.string.sub_lum_short)} · ${formatSigned(bandAdj.deltaLum * 100f)}%"
                                                            ),
                                                            selected = activeBandSubSlider,
                                                            enabled = true,
                                                            modifier = Modifier.alpha(ghostChromeAlpha),
                                                            onSelect = { activeBandSubSlider = it }
                                                        )

                                                        when (activeBandSubSlider) {
                                                            PointColorSubSlider.TON -> {
                                                                val spectrum = remember(selectedColorBand) {
                                                                    val base = selectedColorBand.centerHue
                                                                    List(9) { idx ->
                                                                        val h = (((base - 180f + idx * 45f) % 360f + 360f) % 360f).coerceIn(0f, 359.99f)
                                                                        Color.hsv(h, 0.88f, 0.96f)
                                                                    }
                                                                }
                                                                ProGradientSliderRow(
                                                                    label = "$bandLabel · ${stringResource(R.string.sub_tint)}",
                                                                    valueBadge = "${formatSigned(bandAdj.deltaHue)}°",
                                                                    isModified = abs(bandAdj.deltaHue) > 0.5f,
                                                                    value = bandAdj.deltaHue,
                                                                    defaultValue = 0f,
                                                                    snapTolerance = 5f,
                                                                    valueRange = -180f..180f,
                                                                    enabled = true,
                                                                    gradient = Brush.horizontalGradient(spectrum),
                                                                    onDraggingChange = { isAnySliderDragging = it },
                                                                    onStepMinus = {
                                                                        editConfig = editConfig.copy(
                                                                            colorBands = editConfig.colorBands.update(selectedColorBand) {
                                                                                it.copy(deltaHue = (it.deltaHue - 10f).coerceIn(-180f, 180f))
                                                                            }
                                                                        )
                                                                    },
                                                                    onStepPlus = {
                                                                        editConfig = editConfig.copy(
                                                                            colorBands = editConfig.colorBands.update(selectedColorBand) {
                                                                                it.copy(deltaHue = (it.deltaHue + 10f).coerceIn(-180f, 180f))
                                                                            }
                                                                        )
                                                                    },
                                                                    onReset = {
                                                                        editConfig = editConfig.copy(
                                                                            colorBands = editConfig.colorBands.update(selectedColorBand) { it.copy(deltaHue = 0f) }
                                                                        )
                                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    },
                                                                    onValueChange = { v ->
                                                                        editConfig = editConfig.copy(
                                                                            colorBands = editConfig.colorBands.update(selectedColorBand) { it.copy(deltaHue = v) }
                                                                        )
                                                                    }
                                                                )
                                                            }
                                                            PointColorSubSlider.DOYGUNLUK -> ProGradientSliderRow(
                                                                label = "$bandLabel · ${stringResource(R.string.slider_saturation)}",
                                                                valueBadge = "${formatSigned(bandAdj.deltaSat * 100f)}%",
                                                                isModified = abs(bandAdj.deltaSat) > 0.02f,
                                                                value = bandAdj.deltaSat,
                                                                defaultValue = 0f,
                                                                snapTolerance = 0.05f,
                                                                valueRange = -1f..1f,
                                                                enabled = true,
                                                                gradient = Brush.horizontalGradient(listOf(Color(0xFF475569), selectedColorBand.swatchColor)),
                                                                onDraggingChange = { isAnySliderDragging = it },
                                                                onStepMinus = {
                                                                    editConfig = editConfig.copy(
                                                                        colorBands = editConfig.colorBands.update(selectedColorBand) {
                                                                            it.copy(deltaSat = (it.deltaSat - 0.05f).coerceIn(-1f, 1f))
                                                                        }
                                                                    )
                                                                },
                                                                onStepPlus = {
                                                                    editConfig = editConfig.copy(
                                                                        colorBands = editConfig.colorBands.update(selectedColorBand) {
                                                                            it.copy(deltaSat = (it.deltaSat + 0.05f).coerceIn(-1f, 1f))
                                                                        }
                                                                    )
                                                                },
                                                                onReset = {
                                                                    editConfig = editConfig.copy(
                                                                        colorBands = editConfig.colorBands.update(selectedColorBand) { it.copy(deltaSat = 0f) }
                                                                    )
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                },
                                                                onValueChange = { v ->
                                                                    editConfig = editConfig.copy(
                                                                        colorBands = editConfig.colorBands.update(selectedColorBand) { it.copy(deltaSat = v) }
                                                                    )
                                                                }
                                                            )
                                                            PointColorSubSlider.PARLAKLIK -> ProGradientSliderRow(
                                                                label = "$bandLabel · ${stringResource(R.string.sub_lum_short)}",
                                                                valueBadge = "${formatSigned(bandAdj.deltaLum * 100f)}%",
                                                                isModified = abs(bandAdj.deltaLum) > 0.02f,
                                                                value = bandAdj.deltaLum,
                                                                defaultValue = 0f,
                                                                snapTolerance = 0.05f,
                                                                valueRange = -1f..1f,
                                                                enabled = true,
                                                                gradient = Brush.horizontalGradient(listOf(Color(0xFF1E293B), selectedColorBand.swatchColor, Color.White)),
                                                                onDraggingChange = { isAnySliderDragging = it },
                                                                onStepMinus = {
                                                                    editConfig = editConfig.copy(
                                                                        colorBands = editConfig.colorBands.update(selectedColorBand) {
                                                                            it.copy(deltaLum = (it.deltaLum - 0.05f).coerceIn(-1f, 1f))
                                                                        }
                                                                    )
                                                                },
                                                                onStepPlus = {
                                                                    editConfig = editConfig.copy(
                                                                        colorBands = editConfig.colorBands.update(selectedColorBand) {
                                                                            it.copy(deltaLum = (it.deltaLum + 0.05f).coerceIn(-1f, 1f))
                                                                        }
                                                                    )
                                                                },
                                                                onReset = {
                                                                    editConfig = editConfig.copy(
                                                                        colorBands = editConfig.colorBands.update(selectedColorBand) { it.copy(deltaLum = 0f) }
                                                                    )
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                },
                                                                onValueChange = { v ->
                                                                    editConfig = editConfig.copy(
                                                                        colorBands = editConfig.colorBands.update(selectedColorBand) { it.copy(deltaLum = v) }
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    }

                                                    ColorPanelMode.PIPET -> {
                                                        val tc = editConfig.targetedColor
                                                        if (tc.isActive) {
                                                            val shiftedHue = (((tc.targetHue + tc.deltaHue) % 360f + 360f) % 360f).coerceIn(0f, 359.99f)
                                                            val hasShift = abs(tc.deltaHue) > 1f
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth().alpha(ghostChromeAlpha),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                                    modifier = Modifier.weight(1f, fill = false)
                                                                ) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(13.dp)
                                                                            .clip(CircleShape)
                                                                            .background(Color(tc.previewColorInt))
                                                                            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                                                                    )
                                                                    if (hasShift) {
                                                                        val sc = Color.hsv(
                                                                            hue = shiftedHue,
                                                                            saturation = (tc.targetSat * (1f + tc.deltaSat)).coerceIn(0.35f, 1f),
                                                                            value = (tc.targetLum + tc.deltaLum * 0.45f).coerceIn(0.35f, 1f)
                                                                        )
                                                                        Text("→", color = IrPalette.TextSecondary, fontSize = 10.sp)
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(13.dp)
                                                                                .clip(CircleShape)
                                                                                .background(sc)
                                                                                .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                                                                        )
                                                                    }
                                                                    Text(
                                                                        text = if (hasShift) getHueName(context, shiftedHue) else "${getHueName(context, tc.targetHue)} (${tc.targetHue.toInt()}°)",
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
                                                                        val sel = tc.hueWidth == w
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .clip(RoundedCornerShape(6.dp))
                                                                                .background(if (sel) IrPalette.IrAmber else IrPalette.SurfaceSubtle)
                                                                                .clickable {
                                                                                    editConfig = editConfig.copy(
                                                                                        targetedColor = tc.copy(
                                                                                            hueWidth = w,
                                                                                            hueFeather = if (w == 14f) 12f else if (w == 22f) 18f else 26f
                                                                                        )
                                                                                    )
                                                                                }
                                                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                                                        ) {
                                                                            Text(lbl, color = if (sel) Color.Black else IrPalette.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                                                        }
                                                                    }
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .clip(RoundedCornerShape(6.dp))
                                                                            .background(IrPalette.IrTeal.copy(alpha = 0.16f))
                                                                            .clickable {
                                                                                isPipetteActive = true
                                                                                isSpotWbActive = false
                                                                            }
                                                                            .padding(horizontal = 6.dp, vertical = 4.dp)
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
                                                                            .clickable { editConfig = editConfig.copy(targetedColor = TargetedColorConfig()) }
                                                                            .padding(horizontal = 5.dp, vertical = 3.dp)
                                                                    )
                                                                }
                                                            }

                                                            SegmentedChoiceRow(
                                                                options = listOf(
                                                                    PointColorSubSlider.TON to "${stringResource(R.string.sub_tint)} · ${formatSigned(tc.deltaHue)}°",
                                                                    PointColorSubSlider.DOYGUNLUK to "${stringResource(R.string.sub_sat_short)} · ${formatSigned(tc.deltaSat * 100f)}%",
                                                                    PointColorSubSlider.PARLAKLIK to "${stringResource(R.string.sub_lum_short)} · ${formatSigned(tc.deltaLum * 100f)}%"
                                                                ),
                                                                selected = activePointSubSlider,
                                                                enabled = true,
                                                                modifier = Modifier.alpha(ghostChromeAlpha),
                                                                onSelect = { activePointSubSlider = it }
                                                            )

                                                            when (activePointSubSlider) {
                                                                PointColorSubSlider.TON -> {
                                                                    val spectrum = remember(tc.targetHue) {
                                                                        List(9) { idx ->
                                                                            val h = (((tc.targetHue - 180f + idx * 45f) % 360f + 360f) % 360f).coerceIn(0f, 359.99f)
                                                                            Color.hsv(h, 0.88f, 0.96f)
                                                                        }
                                                                    }
                                                                    ProGradientSliderRow(
                                                                        label = stringResource(R.string.slider_point_hue),
                                                                        valueBadge = "${formatSigned(tc.deltaHue)}°",
                                                                        isModified = abs(tc.deltaHue) > 0.5f,
                                                                        value = tc.deltaHue,
                                                                        defaultValue = 0f,
                                                                        snapTolerance = 5f,
                                                                        valueRange = -180f..180f,
                                                                        enabled = true,
                                                                        gradient = Brush.horizontalGradient(spectrum),
                                                                        onDraggingChange = { isAnySliderDragging = it },
                                                                        onStepMinus = { editConfig = editConfig.copy(targetedColor = tc.copy(deltaHue = (tc.deltaHue - 10f).coerceIn(-180f, 180f))) },
                                                                        onStepPlus = { editConfig = editConfig.copy(targetedColor = tc.copy(deltaHue = (tc.deltaHue + 10f).coerceIn(-180f, 180f))) },
                                                                        onReset = { editConfig = editConfig.copy(targetedColor = tc.copy(deltaHue = 0f)) },
                                                                        onValueChange = { editConfig = editConfig.copy(targetedColor = tc.copy(deltaHue = it)) }
                                                                    )
                                                                }
                                                                PointColorSubSlider.DOYGUNLUK -> ProGradientSliderRow(
                                                                    label = stringResource(R.string.slider_point_sat),
                                                                    valueBadge = "${formatSigned(tc.deltaSat * 100f)}%",
                                                                    isModified = abs(tc.deltaSat) > 0.02f,
                                                                    value = tc.deltaSat,
                                                                    defaultValue = 0f,
                                                                    snapTolerance = 0.05f,
                                                                    valueRange = -1f..1f,
                                                                    enabled = true,
                                                                    gradient = Brush.horizontalGradient(listOf(Color(0xFF64748B), Color(0xFF22C55E), Color(0xFFF59E0B), Color(0xFFEF4444))),
                                                                    onDraggingChange = { isAnySliderDragging = it },
                                                                    onStepMinus = { editConfig = editConfig.copy(targetedColor = tc.copy(deltaSat = (tc.deltaSat - 0.05f).coerceIn(-1f, 1f))) },
                                                                    onStepPlus = { editConfig = editConfig.copy(targetedColor = tc.copy(deltaSat = (tc.deltaSat + 0.05f).coerceIn(-1f, 1f))) },
                                                                    onReset = { editConfig = editConfig.copy(targetedColor = tc.copy(deltaSat = 0f)) },
                                                                    onValueChange = { editConfig = editConfig.copy(targetedColor = tc.copy(deltaSat = it)) }
                                                                )
                                                                PointColorSubSlider.PARLAKLIK -> ProGradientSliderRow(
                                                                    label = stringResource(R.string.slider_point_lum),
                                                                    valueBadge = "${formatSigned(tc.deltaLum * 100f)}%",
                                                                    isModified = abs(tc.deltaLum) > 0.02f,
                                                                    value = tc.deltaLum,
                                                                    defaultValue = 0f,
                                                                    snapTolerance = 0.05f,
                                                                    valueRange = -1f..1f,
                                                                    enabled = true,
                                                                    gradient = Brush.horizontalGradient(listOf(Color(0xFF334155), Color.White)),
                                                                    onDraggingChange = { isAnySliderDragging = it },
                                                                    onStepMinus = { editConfig = editConfig.copy(targetedColor = tc.copy(deltaLum = (tc.deltaLum - 0.05f).coerceIn(-1f, 1f))) },
                                                                    onStepPlus = { editConfig = editConfig.copy(targetedColor = tc.copy(deltaLum = (tc.deltaLum + 0.05f).coerceIn(-1f, 1f))) },
                                                                    onReset = { editConfig = editConfig.copy(targetedColor = tc.copy(deltaLum = 0f)) },
                                                                    onValueChange = { editConfig = editConfig.copy(targetedColor = tc.copy(deltaLum = it)) }
                                                                )
                                                            }
                                                        } else {
                                                            Text(
                                                                text = stringResource(R.string.pipette_empty_hint),
                                                                color = IrPalette.TextSecondary,
                                                                fontSize = 11.sp
                                                            )
                                                            ActionPillButton(
                                                                label = stringResource(R.string.action_color_pipette),
                                                                selected = isPipetteActive,
                                                                accentColor = IrPalette.IrTeal,
                                                                enabled = true,
                                                                modifier = Modifier.fillMaxWidth(),
                                                                leadingIcon = { tint -> PipetteIcon(tint = tint, modifier = Modifier.size(13.dp)) },
                                                                onClick = {
                                                                    isPipetteActive = true
                                                                    isSpotWbActive = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            EditorTab.LOOKS -> {
                                                // Tek dokunuşluk yatay Kızılötesi & Sinema görünümleri şeridi + Özel Kaydet
                                                val allLooks = remember(savedPresets) { BUILT_IN_SIGNATURE_PRESETS + savedPresets }
                                                LazyRow(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    item(key = "save_new_look") {
                                                        Box(
                                                            modifier = Modifier
                                                                .height(48.dp)
                                                                .clip(RoundedCornerShape(10.dp))
                                                                .background(IrPalette.IrAmber.copy(alpha = 0.14f))
                                                                .border(0.9.dp, IrPalette.IrAmber.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                                                                .clickable { showSavePresetDialog = true }
                                                                .padding(horizontal = 10.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = stringResource(R.string.dialog_presets_new),
                                                                color = IrPalette.IrAmber,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                    items(allLooks, key = { "${it.isBuiltIn}_${it.name}" }) { preset ->
                                                        val isSel = abs(editConfig.kelvin - preset.kelvin) < 5f &&
                                                            abs(editConfig.tint - preset.tint) < 1f &&
                                                            editConfig.channelSwap == preset.channelSwap &&
                                                            editConfig.colorBands == preset.colorBands
                                                        val dotC = Color(preset.accentHex)
                                                        Column(
                                                            modifier = Modifier
                                                                .height(48.dp)
                                                                .clip(RoundedCornerShape(10.dp))
                                                                .background(if (isSel) dotC.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
                                                                .border(
                                                                    0.9.dp,
                                                                    if (isSel) dotC else IrPalette.BorderSubtle,
                                                                    RoundedCornerShape(10.dp)
                                                                )
                                                                .clickable { applyPresetToEditor(preset) }
                                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                                            verticalArrangement = Arrangement.Center,
                                                            horizontalAlignment = Alignment.Start
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(8.dp)
                                                                        .clip(CircleShape)
                                                                        .background(dotC)
                                                                )
                                                                Text(
                                                                    text = preset.name,
                                                                    color = if (isSel) dotC else IrPalette.TextPrimary,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    maxLines = 1
                                                                )
                                                                if (!preset.isBuiltIn) {
                                                                    Text(
                                                                        text = "✕",
                                                                        color = IrPalette.TextTertiary,
                                                                        fontSize = 10.sp,
                                                                        modifier = Modifier
                                                                            .clip(CircleShape)
                                                                            .clickable {
                                                                                onDeleteCustomPreset(preset)
                                                                                showEditorStatus(context.getString(R.string.status_preset_deleted, preset.name))
                                                                            }
                                                                            .padding(horizontal = 3.dp)
                                                                    )
                                                                }
                                                            }
                                                            if (preset.channelSwap) {
                                                                Text(
                                                                    text = strBadgeSwap,
                                                                    color = IrPalette.IrCyan,
                                                                    fontSize = 9.sp,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Alt Stüdyo Sekme Çubuğu (kaydırarak gizle/aç)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .alpha(ghostChromeAlpha)
                                        .pointerInput(Unit) {
                                            detectVerticalDragGestures { _, dragAmount ->
                                                if (dragAmount > 14f) isDeckCollapsed = true
                                                else if (dragAmount < -14f) isDeckCollapsed = false
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
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        val tabs = listOf(
                                            Triple(EditorTab.LOOKS, stringResource(R.string.editor_tab_looks), false),
                                            Triple(EditorTab.WB, stringResource(R.string.chip_wb), editConfig.isWbModified),
                                            Triple(EditorTab.EXPOSURE, stringResource(R.string.chip_exposure), editConfig.isExposureModified),
                                            Triple(EditorTab.COLOR, stringResource(R.string.chip_color), editConfig.isColorModified)
                                        )
                                        tabs.forEach { (tab, title, mod) ->
                                            TabChip(
                                                title = title,
                                                selected = activeTab == tab && !isDeckCollapsed,
                                                hasActiveIndicator = mod,
                                                modifier = Modifier.weight(1f),
                                                icon = { tint ->
                                                    when (tab) {
                                                        EditorTab.LOOKS -> PresetStarIcon(tint = tint, modifier = Modifier.size(13.dp))
                                                        EditorTab.WB -> WhiteBalanceIcon(tint = tint, modifier = Modifier.size(13.dp))
                                                        EditorTab.EXPOSURE -> ExposureIcon(tint = tint, modifier = Modifier.size(13.dp))
                                                        EditorTab.COLOR -> ColorWheelIcon(tint = tint, modifier = Modifier.size(13.dp))
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

                                    val chevronRot by animateFloatAsState(
                                        targetValue = if (isDeckCollapsed) 180f else 0f,
                                        animationSpec = tween(200),
                                        label = "edChevronRot"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .semantics { contentDescription = strPanelCd }
                                            .clip(CircleShape)
                                            .clickable { isDeckCollapsed = !isDeckCollapsed },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(if (isDeckCollapsed) IrPalette.IrAmber.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f))
                                                .border(0.8.dp, if (isDeckCollapsed) IrPalette.IrAmber else IrPalette.BorderSubtle, RoundedCornerShape(9.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            ChevronIcon(
                                                tint = if (isDeckCollapsed) IrPalette.IrAmber else IrPalette.TextPrimary,
                                                modifier = Modifier.size(13.dp).rotate(chevronRot)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                CircularProgressIndicator(color = IrPalette.IrAmber, strokeWidth = 2.5.dp)
            }
        }

        // 3) KOMPAKT ALT STÜDYO İŞLEM ÇUBUĞU: Sistem Galerisi · R↔B · Sıfırla · Kaydet
        if (hasPhoto) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IrPalette.VoidBlack)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickPillAction(
                    text = stringResource(R.string.editor_open_sys_gallery),
                    compact = true,
                    enabled = true,
                    leadingIcon = { tint -> GalleryIcon(tint = tint, modifier = Modifier.size(13.dp)) },
                    onClick = {
                        if (!openGallerySafely(context, lastExportedUri ?: currentUri)) {
                            showEditorStatus(context.getString(R.string.status_gallery_open_failed))
                        }
                    }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuickPillAction(
                        text = strBadgeSwap,
                        highlighted = editConfig.channelSwap,
                        compact = true,
                        enabled = true,
                        leadingIcon = { tint -> SwapChannelIcon(tint = tint, modifier = Modifier.size(13.dp)) },
                        onClick = {
                            val next = !editConfig.channelSwap
                            editConfig = editConfig.copy(channelSwap = next)
                            showEditorStatus(
                                context.getString(
                                    if (next) R.string.status_channel_swap_on
                                    else R.string.status_channel_swap_off
                                )
                            )
                        }
                    )

                    QuickPillAction(
                        text = stringResource(R.string.dialog_confirm),
                        highlighted = editConfig.hasAnyEdit,
                        compact = true,
                        enabled = editConfig.hasAnyEdit,
                        leadingIcon = { tint -> ResetIcon(tint = tint, modifier = Modifier.size(13.dp)) },
                        onClick = {
                            editConfig = PhotoEditConfig()
                            isSpotWbActive = false
                            isPipetteActive = false
                            showEditorStatus(context.getString(R.string.editor_reset_done))
                        }
                    )
                }

                QuickPillAction(
                    text = if (isSavingPhoto) stringResource(R.string.editor_saving) else stringResource(R.string.editor_save),
                    highlighted = true,
                    compact = true,
                    enabled = !isSavingPhoto,
                    leadingIcon = { tint -> SaveIcon(tint = tint, modifier = Modifier.size(13.dp)) },
                    onClick = { exportPhoto(andShare = false) }
                )
            }
        }
    }

    // Stüdyo ayarlarını ortak Özel Ön Ayar olarak kaydetme diyalogu (Canlı Kamerada da çalışır!)
    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
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
                        text = "$strWbShort ${editConfig.kelvin.toInt()}K · $strTintShort ${formatSigned(editConfig.tint)} · C ${String.format(appLocale, "%.2f", editConfig.contrast)}x · S ${String.format(appLocale, "%.2f", editConfig.saturation)}x${if (editConfig.channelSwap) " · $strBadgeSwap" else ""}",
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
                            onSaveCustomPreset(
                                CameraPreset(
                                    name = trimmed,
                                    kelvin = editConfig.kelvin,
                                    tint = editConfig.tint,
                                    contrast = editConfig.contrast,
                                    saturation = editConfig.saturation,
                                    channelSwap = editConfig.channelSwap,
                                    colorBands = editConfig.colorBands
                                )
                            )
                            presetNameInput = ""
                            showSavePresetDialog = false
                            showEditorStatus(context.getString(R.string.status_preset_saved, trimmed))
                        } else {
                            showEditorStatus(context.getString(R.string.status_enter_name))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IrPalette.IrAmber)
                ) {
                    Text(stringResource(R.string.dialog_save), color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel), color = IrPalette.TextSecondary)
                }
            }
        )
    }
}
