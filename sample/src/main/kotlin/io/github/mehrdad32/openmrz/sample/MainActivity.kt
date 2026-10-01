package io.github.mehrdad32.openmrz.sample

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import io.github.mehrdad32.openmrz.core.MrzDocument
import io.github.mehrdad32.openmrz.core.MrzParseResult
import io.github.mehrdad32.openmrz.ocr.MrzOcrResult
import io.github.mehrdad32.openmrz.ocr.TesseractMrzRecognizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var statusView: TextView
    private lateinit var resultView: TextView
    private lateinit var pauseButton: Button

    private val worker = Executors.newSingleThreadExecutor()
    private val processing = AtomicBoolean(false)

    @Volatile private var recognizer: TesseractMrzRecognizer? = null
    @Volatile private var paused = false

    private var camera: Camera? = null
    private var lastAnalysisAt = 0L

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else showStatus("Camera permission is required. You can still test from Gallery.")
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null || !processing.compareAndSet(false, true)) return@registerForActivityResult

            showStatus("Reading MRZ from selected image…")
            worker.execute {
                try {
                    val bitmap = contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                    if (bitmap == null) {
                        runOnUiThread { showStatus("Could not decode selected image.") }
                        return@execute
                    }
                    val crop = cropMrzRegion(bitmap)
                    if (crop !== bitmap) bitmap.recycle()
                    val result = recognizer?.recognize(crop)
                    crop.recycle()
                    if (result != null) publishResult(result)
                } catch (t: Throwable) {
                    runOnUiThread { showStatus("Gallery scan failed: " + (t.message ?: t.javaClass.simpleName)) }
                } finally {
                    processing.set(false)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        showStatus("Loading offline OCR engine…")

        worker.execute {
            try {
                recognizer = TesseractMrzRecognizer(this)
                runOnUiThread {
                    showStatus("Ready. Put the MRZ inside the green frame.")
                    ensureCameraPermission()
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    showStatus("OCR initialization failed: " + (t.message ?: t.javaClass.simpleName))
                }
            }
        }
    }

    override fun onDestroy() {
        recognizer?.close()
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(11, 15, 20)) }

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        root.addView(MrzOverlayView(this), FrameLayout.LayoutParams(-1, -1))

        val title = TextView(this).apply {
            text = "OpenMRZ"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(dp(18), dp(18), dp(18), dp(8))
        }
        root.addView(title, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            setBackgroundColor(0xE6111820.toInt())
        }

        statusView = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(105, 240, 174))
        }
        bottom.addView(statusView)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val galleryButton = Button(this).apply {
            text = "Gallery"
            setOnClickListener { pickImage.launch("image/*") }
        }
        buttons.addView(galleryButton, LinearLayout.LayoutParams(0, dp(48), 1f))

        pauseButton = Button(this).apply {
            text = "Pause"
            setOnClickListener {
                paused = !paused
                text = if (paused) "Resume" else "Pause"
                showStatus(if (paused) "Scanner paused." else "Scanner resumed.")
            }
        }
        buttons.addView(pauseButton, LinearLayout.LayoutParams(0, dp(48), 1f))

        val torchButton = Button(this).apply {
            text = "Torch"
            setOnClickListener {
                val current = camera?.cameraInfo?.torchState?.value == 1
                camera?.cameraControl?.enableTorch(!current)
            }
        }
        buttons.addView(torchButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        bottom.addView(buttons)

        resultView = TextView(this).apply {
            text = "No document scanned yet."
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, 0)
            setTextIsSelectable(true)
        }

        val scroll = ScrollView(this).apply { addView(resultView) }
        bottom.addView(scroll, LinearLayout.LayoutParams(-1, dp(170)))

        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
    }

    private fun ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(worker, ::analyze)

            provider.unbindAll()
            camera = provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (
            paused ||
            recognizer == null ||
            now - lastAnalysisAt < ANALYSIS_INTERVAL_MS ||
            !processing.compareAndSet(false, true)
        ) {
            image.close()
            return
        }

        lastAnalysisAt = now

        try {
            val bitmap = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            image.close()

            val rotated = rotate(bitmap, rotation)
            if (rotated !== bitmap) bitmap.recycle()

            val crop = cropMrzRegion(rotated)
            if (crop !== rotated) rotated.recycle()

            val result = recognizer?.recognize(crop)
            crop.recycle()

            if (result != null) publishResult(result)
        } catch (t: Throwable) {
            runCatching { image.close() }
            runOnUiThread { showStatus("Scan error: " + (t.message ?: t.javaClass.simpleName)) }
        } finally {
            processing.set(false)
        }
    }

    private fun publishResult(result: MrzOcrResult) {
        runOnUiThread {
            when (val parsed = result.parseResult) {
                is MrzParseResult.Success -> {
                    val document = parsed.document
                    showStatus(
                        if (document.validation.isValid) {
                            "Valid MRZ found • OCR ${result.confidence}%"
                        } else {
                            "MRZ candidate found • check digits incomplete • OCR ${result.confidence}%"
                        }
                    )
                    resultView.text = formatDocument(document, result)
                }

                is MrzParseResult.Failure -> {
                    showStatus("Searching… OCR ${result.confidence}%")
                    resultView.text =
                        if (result.normalizedText.isBlank()) "No MRZ text recognized yet."
                        else "Candidate text:\n${result.normalizedText}"
                }
            }
        }
    }

    private fun formatDocument(document: MrzDocument, result: MrzOcrResult): String = buildString {
        appendLine("Format: ${document.format}")
        appendLine("Document: ${document.documentCode}  •  Issuer: ${document.issuingState}")
        appendLine("Number: ${document.documentNumber}")
        appendLine("Name: ${document.surname}, ${document.givenNames.joinToString(" ")}")
        appendLine("Nationality: ${document.nationality}")
        appendLine("Birth: ${document.birthDate}  •  Sex: ${document.sex}")
        appendLine("Expiry: ${document.expiryDate}")
        appendLine("Check digits: ${if (document.validation.isValid) "VALID" else "PARTIAL / INVALID"}")
        appendLine("OCR confidence: ${result.confidence}%")
        appendLine()
        append(result.normalizedText)
    }

    private fun cropMrzRegion(bitmap: Bitmap): Bitmap {
        val left = (bitmap.width * 0.04f).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (bitmap.height * 0.50f).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (bitmap.width * 0.96f).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bitmap.height * 0.91f).roundToInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
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

    private fun showStatus(message: String) {
        statusView.text = message
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val ANALYSIS_INTERVAL_MS = 1100L
    }
}
