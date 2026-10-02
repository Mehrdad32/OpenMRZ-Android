package ir.mehrdad32.openmrz.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import ir.mehrdad32.openmrz.ocr.MrzOcrResult
import ir.mehrdad32.openmrz.ocr.MrzScanStatus
import ir.mehrdad32.openmrz.ocr.OpenMrzImageRecognizer
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

interface OpenMrzScannerListener {
    fun onReady() {}
    fun onResult(result: MrzOcrResult)
    fun onError(error: Throwable) {}
}

fun interface OpenMrzResultCallback {
    fun onResult(result: MrzOcrResult)
}

class OpenMrzScanner(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val listener: OpenMrzScannerListener,
    private val config: OpenMrzScannerConfig = OpenMrzScannerConfig(),
) : Closeable {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val processing = AtomicBoolean(false)

    @Volatile
    private var started = false

    @Volatile
    private var closed = false

    private var recognizer: OpenMrzImageRecognizer? = null
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var lastAnalysisAt = 0L

    fun start() {
        check(!closed) { "Scanner is already closed." }
        if (started) return
        started = true

        worker.execute {
            try {
                ensureRecognizer()
                mainExecutor.execute {
                    if (started && !closed) bindCamera()
                }
            } catch (t: Throwable) {
                reportError(t)
            }
        }
    }

    fun stop() {
        started = false
        mainExecutor.execute {
            val localProvider = provider ?: return@execute
            previewUseCase?.let(localProvider::unbind)
            analysisUseCase?.let(localProvider::unbind)
            previewUseCase = null
            analysisUseCase = null
            camera = null
        }
    }

    fun setTorch(enabled: Boolean) {
        mainExecutor.execute {
            camera?.cameraControl?.enableTorch(enabled)
        }
    }

    fun recognize(
        bitmap: Bitmap,
        callback: OpenMrzResultCallback,
    ) {
        check(!closed) { "Scanner is already closed." }

        val safeCopy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        worker.execute {
            try {
                val result = ensureRecognizer().recognize(safeCopy)
                mainExecutor.execute { callback.onResult(result) }
            } catch (t: Throwable) {
                reportError(t)
            } finally {
                safeCopy.recycle()
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        stop()

        worker.execute {
            recognizer?.close()
            recognizer = null
        }
        worker.shutdown()
    }

    private fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener(
            {
                try {
                    val localProvider = future.get()
                    provider = localProvider

                    previewUseCase?.let(localProvider::unbind)
                    analysisUseCase?.let(localProvider::unbind)

                    val preview = Preview.Builder()
                        .build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(worker, ::analyze)

                    camera = localProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )

                    previewUseCase = preview
                    analysisUseCase = analysis
                    listener.onReady()
                } catch (t: Throwable) {
                    reportError(t)
                }
            },
            mainExecutor,
        )
    }

    private fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()

        if (
            !started ||
            closed ||
            now - lastAnalysisAt < config.analysisIntervalMs ||
            !processing.compareAndSet(false, true)
        ) {
            image.close()
            return
        }

        lastAnalysisAt = now

        try {
            val raw = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            image.close()

            val rotated = rotate(raw, rotation)
            if (rotated !== raw) raw.recycle()

            val roi = config.regionOfInterest.crop(rotated)
            rotated.recycle()

            // The CameraX ROI is already an MRZ-oriented crop. Do not run the
            // still-image region detector a second time.
            val result = ensureRecognizer().recognizeMrzCrop(roi)
            roi.recycle()

            if (
                config.emitUnrecognizedResults ||
                result.status != MrzScanStatus.NOT_RECOGNIZED
            ) {
                mainExecutor.execute { listener.onResult(result) }
            }
        } catch (t: Throwable) {
            runCatching { image.close() }
            reportError(t)
        } finally {
            processing.set(false)
        }
    }

    private fun ensureRecognizer(): OpenMrzImageRecognizer {
        val current = recognizer
        if (current != null) return current

        return OpenMrzImageRecognizer(
            context = appContext,
            config = config.recognition,
        ).also {
            recognizer = it
        }
    }

    private fun reportError(error: Throwable) {
        mainExecutor.execute {
            listener.onError(error)
        }
    }

    private fun rotate(
        bitmap: Bitmap,
        degrees: Int,
    ): Bitmap {
        if (degrees == 0) return bitmap

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(degrees.toFloat()) },
            true,
        )
    }
}
