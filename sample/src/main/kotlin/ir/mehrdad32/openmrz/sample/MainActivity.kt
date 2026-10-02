package ir.mehrdad32.openmrz.sample

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import ir.mehrdad32.openmrz.android.NormalizedRect
import ir.mehrdad32.openmrz.android.OpenMrzResultCallback
import ir.mehrdad32.openmrz.android.OpenMrzScanner
import ir.mehrdad32.openmrz.android.OpenMrzScannerConfig
import ir.mehrdad32.openmrz.android.OpenMrzScannerListener
import ir.mehrdad32.openmrz.core.MrzDocument
import ir.mehrdad32.openmrz.core.MrzParseResult
import ir.mehrdad32.openmrz.ocr.MrzOcrResult
import ir.mehrdad32.openmrz.ocr.MrzScanStatus
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var statusView: TextView
    private lateinit var retryButton: Button

    private var scanner: OpenMrzScanner? = null
    private var torchEnabled = false
    private var resultLocked = false
    private var lastResultText: String? = null

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) ensureScanner().start()
            else showStatus("Camera permission is required. Gallery recognition still works.")
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult

            val bitmap = contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            if (bitmap == null) {
                showStatus("Could not decode selected image.")
                return@registerForActivityResult
            }

            scanner?.stop()
            resultLocked = false
            retryButton.isEnabled = false
            showStatus("Reading MRZ from selected image…")
            ensureScanner().recognize(
                bitmap,
                OpenMrzResultCallback(::publishResult),
            )
            bitmap.recycle()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        ensureCameraPermission()
    }

    override fun onDestroy() {
        scanner?.close()
        scanner = null
        super.onDestroy()
    }

    private fun ensureScanner(): OpenMrzScanner {
        val current = scanner
        if (current != null) return current

        return OpenMrzScanner(
            context = this,
            lifecycleOwner = this,
            previewView = previewView,
            listener = object : OpenMrzScannerListener {
                override fun onReady() {
                    if (!resultLocked) {
                        showStatus("Ready. Put only the MRZ lines inside the green frame.")
                        if (torchEnabled) {
                            scanner?.setTorch(true)
                        }
                    }
                }

                override fun onResult(result: MrzOcrResult) {
                    publishResult(result)
                }

                override fun onError(error: Throwable) {
                    showStatus("Scanner error: " + (error.message ?: error.javaClass.simpleName))
                }
            },
            config = OpenMrzScannerConfig(
                regionOfInterest = NormalizedRect(
                    left = MrzOverlayView.ROI_LEFT,
                    top = MrzOverlayView.ROI_TOP,
                    right = MrzOverlayView.ROI_RIGHT,
                    bottom = MrzOverlayView.ROI_BOTTOM,
                ),
            ),
        ).also {
            scanner = it
        }
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(11, 15, 20))
        }

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        root.addView(MrzOverlayView(this), FrameLayout.LayoutParams(-1, -1))

        val title = TextView(this).apply {
            text = "OpenMRZ SDK"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(dp(18), dp(18), dp(18), dp(8))
        }
        root.addView(title, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            setBackgroundColor(0xE6111820.toInt())
        }

        statusView = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(105, 240, 174))
            setPadding(0, 0, 0, dp(6))
            maxLines = 2
            setOnClickListener { showResultDialog() }
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

        retryButton = Button(this).apply {
            text = "Retry"
            isEnabled = false
            setOnClickListener { retryScan() }
        }
        buttons.addView(retryButton, LinearLayout.LayoutParams(0, dp(48), 1f))

        val torchButton = Button(this).apply {
            text = "Torch"
            setOnClickListener {
                torchEnabled = !torchEnabled
                scanner?.setTorch(torchEnabled)
            }
        }
        buttons.addView(torchButton, LinearLayout.LayoutParams(0, dp(48), 1f))

        bottom.addView(buttons)
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
    }

    private fun ensureCameraPermission() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            ensureScanner().start()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun publishResult(result: MrzOcrResult) {
        if (resultLocked) return

        lastResultText = when (val parsed = result.parseResult) {
            is MrzParseResult.Success -> formatDocument(parsed.document, result)
            is MrzParseResult.Failure -> {
                if (result.normalizedText.isBlank()) {
                    null
                } else {
                    "Candidate text:\n${result.normalizedText}"
                }
            }
        }

        when (result.status) {
            MrzScanStatus.VERIFIED ->
                lockResult("VERIFIED • OCR ${result.confidence}%")

            MrzScanStatus.CHECKSUM_VALID_LOW_CONFIDENCE ->
                lockResult("CHECKSUM VALID • LOW CONFIDENCE ${result.confidence}%")

            MrzScanStatus.NEEDS_REVIEW ->
                lockResult("NEEDS REVIEW • OCR ${result.confidence}%")

            MrzScanStatus.NOT_RECOGNIZED -> {
                if (lastResultText == null) {
                    showStatus("Searching… OCR ${result.confidence}%")
                } else {
                    showStatus("Candidate found • OCR ${result.confidence}%")
                }
            }
        }
    }

    private fun lockResult(status: String) {
        resultLocked = true
        scanner?.stop()
        retryButton.isEnabled = true
        showStatus("$status • PAUSED • TAP FOR DETAILS")
        showResultDialog()
    }

    private fun retryScan() {
        lastResultText = null
        resultLocked = false
        retryButton.isEnabled = false
        showStatus("Retrying…")
        ensureScanner().start()
    }

    private fun showResultDialog() {
        val details = lastResultText ?: return

        val detailsView = TextView(this).apply {
            text = details
            textSize = 14f
            setTextColor(Color.WHITE)
            setTextIsSelectable(true)
            setPadding(dp(20), dp(12), dp(20), dp(20))
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(17, 24, 32))
            addView(detailsView)
        }

        AlertDialog.Builder(this)
            .setTitle("MRZ result")
            .setView(scroll)
            .setNeutralButton("Copy") { _, _ ->
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("MRZ result", details))
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun formatDocument(
        document: MrzDocument,
        result: MrzOcrResult,
    ): String = buildString {
        appendLine("Status: ${result.status}")
        appendLine("Format: ${document.format}")
        appendLine("Document: ${document.documentCode}  •  Issuer: ${document.issuingState}")
        appendLine("Number: ${document.documentNumber}")
        appendLine("Name: ${document.surname}, ${document.givenNames.joinToString(" ")}")
        appendLine("Nationality: ${document.nationality}")
        appendLine("Birth: ${document.birthDate}  •  Sex: ${document.sex}")
        appendLine("Expiry: ${document.expiryDate}")
        appendLine("Check digits: ${if (document.validation.checkDigitsValid) "VALID" else "PARTIAL / INVALID"}")
        appendLine("Field structure: ${if (document.validation.fields.isValid) "VALID" else "SUSPICIOUS"}")
        if (!document.validation.fields.issuingState) {
            appendLine("Warning: issuing-state code is not recognized.")
        }
        if (!document.validation.fields.nationality) {
            appendLine("Warning: nationality code is not recognized.")
        }
        appendLine("OCR confidence: ${result.confidence}%")
        appendLine("Processing: ${result.processingTimeMs} ms • Attempts: ${result.attemptCount} • Engine: ${result.engine}")
        appendLine("Auto corrections: ${result.correctionCount}")
        appendLine("Trusted scan: ${result.isTrusted}")
        appendLine()
        append(result.normalizedText)
    }

    private fun showStatus(message: String) {
        statusView.text = message
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()
}
