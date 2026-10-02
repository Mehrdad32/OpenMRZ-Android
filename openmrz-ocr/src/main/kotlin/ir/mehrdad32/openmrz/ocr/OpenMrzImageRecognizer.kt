package ir.mehrdad32.openmrz.ocr

import android.content.Context
import android.graphics.Bitmap
import java.io.Closeable

/**
 * Camera-independent OpenMRZ entry point for host applications that already
 * own image capture, cropping, storage, or document acquisition.
 *
 * This API lives in the openmrz-ocr artifact and does not depend on CameraX.
 */
class OpenMrzImageRecognizer @JvmOverloads constructor(
    context: Context,
    config: MrzRecognizerConfig = MrzRecognizerConfig(),
) : Closeable {
    private val delegate = TesseractMrzRecognizer(
        context = context.applicationContext,
        config = config,
    )

    /**
     * Recognizes an MRZ from a document image. The image may be a full frame,
     * a cropped passport/ID page, or another image containing an MRZ.
     *
     * OpenMRZ will locate the MRZ region automatically when enabled in config.
     * The input Bitmap remains owned by the caller and is never recycled here.
     */
    fun recognize(bitmap: Bitmap): MrzOcrResult =
        delegate.recognize(bitmap)

    /**
     * Recognizes a Bitmap that is already cropped tightly to the MRZ lines.
     * This skips region detection and is useful when the host app performs its
     * own document detection/cropping pipeline.
     *
     * The input Bitmap remains owned by the caller and is never recycled here.
     */
    fun recognizeMrzCrop(bitmap: Bitmap): MrzOcrResult =
        delegate.recognizeCropped(bitmap)

    override fun close() {
        delegate.close()
    }
}
