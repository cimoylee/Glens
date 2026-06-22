package com.fc.lens.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

object OcrBridge {
    private const val TAG = "OcrBridge"
    @Volatile
    private var ocr: PaddleOCR? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Resolusi 960 untuk akurasi teks kecil
    private const val MAX_OCR_DIMENSION = 960

    init {
        try {
            System.loadLibrary("opencv_java4")
            Log.d(TAG, "OpenCV native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load OpenCV native library", e)
        }
    }

    fun interface SuccessCallback {
        fun onSuccess(results: List<OcrItem>)
    }

    fun interface FailureCallback {
        fun onFailure(e: Exception)
    }

    @Synchronized
    fun init(context: Context) {
        executor.execute {
            try {
                Log.d(TAG, "Starting PaddleOCR initialization...")
                val engineConfig = EngineConfig()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    try {
                        val field = engineConfig.javaClass.getDeclaredField("useNNAPI")
                        field.isAccessible = true
                        field.setBoolean(engineConfig, true)
                        Log.d(TAG, "✅ NNAPI enabled")
                    } catch (e: Exception) {
                        Log.w(TAG, "NNAPI not available, using CPU")
                    }
                }

                runBlocking {
                    ocr = PaddleOCR.create(
                        context = context,
                        config = PaddleOCRConfig(),
                        engineConfig = engineConfig,
                        detModelAssetPath = "models/det.onnx",
                        recModelAssetPath = "models/rec.onnx",
                        recConfigAssetPath = "models/rec.yml"
                    )
                }
                Log.d(TAG, "PaddleOCR initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "PaddleOCR init failed", e)
            }
        }
    }

    private fun resizeForOcr(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val maxDim = maxOf(w, h)
        if (maxDim <= MAX_OCR_DIMENSION) return bitmap

        val scale = MAX_OCR_DIMENSION.toFloat() / maxDim
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()

        val resized = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        Log.d(TAG, "Resized: ${w}x${h} → ${newW}x${newH}")
        return resized
    }

    fun recognize(
        bitmap: Bitmap,
        onSuccess: SuccessCallback,
        onFailure: FailureCallback
    ) {
        executor.execute {
            try {
                if (ocr == null) {
                    mainHandler.post { onFailure.onFailure(Exception("PaddleOCR not initialized")) }
                    return@execute
                }

                val t0 = System.currentTimeMillis()
                val resizedBitmap = resizeForOcr(bitmap)
                val scaleX = bitmap.width.toFloat() / resizedBitmap.width
                val scaleY = bitmap.height.toFloat() / resizedBitmap.height

                Log.d(TAG, "Starting OCR on ${resizedBitmap.width}x${resizedBitmap.height}...")
                val result = runBlocking { ocr?.recognize(resizedBitmap) }
                    ?: throw IllegalStateException("PaddleOCR returned null")
                val t2 = System.currentTimeMillis()
                Log.d(TAG, "⏱ OCR inference: ${t2 - t0}ms")

                if (resizedBitmap !== bitmap) resizedBitmap.recycle()

                val items = mutableListOf<OcrItem>()
                result.results.forEach { ocrResult ->
                    val points = ocrResult.box.points
                    val xs = points.map { (it.x * scaleX).toInt() }
                    val ys = points.map { (it.y * scaleY).toInt() }
                    val lineRect = Rect(
                        xs.minOrNull() ?: 0, ys.minOrNull() ?: 0,
                        xs.maxOrNull() ?: 0, ys.maxOrNull() ?: 0
                    )

                    val text = ocrResult.text.trim()
                    if (text.isNotEmpty()) {
                        items.add(OcrItem(text, lineRect))
                    }
                }

                Log.d(TAG, "✅ Total: ${System.currentTimeMillis() - t0}ms | ${items.size} lines found")
                mainHandler.post { onSuccess.onSuccess(items) }
            } catch (e: Exception) {
                Log.e(TAG, "OCR failed", e)
                mainHandler.post { onFailure.onFailure(e) }
            }
        }
    }

    fun release() {
        executor.execute {
            try {
                runBlocking { ocr?.release() }
            } finally {
                ocr = null
            }
        }
    }

    // Kembali ke OcrItem (per baris/kata)
    data class OcrItem(val text: String, val bounds: Rect)
}
