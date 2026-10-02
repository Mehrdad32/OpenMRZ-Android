package ir.mehrdad32.openmrz.sample

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class MrzOverlayView(context: Context) : View(context) {
    private val shade = Paint().apply { color = 0x77000000.toInt() }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(105, 240, 174)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = width * ROI_LEFT
        val right = width * ROI_RIGHT
        val top = height * ROI_TOP
        val bottom = height * ROI_BOTTOM
        val box = RectF(left, top, right, bottom)

        canvas.drawRect(0f, 0f, width.toFloat(), top, shade)
        canvas.drawRect(0f, bottom, width.toFloat(), height.toFloat(), shade)
        canvas.drawRect(0f, top, left, bottom, shade)
        canvas.drawRect(right, top, width.toFloat(), bottom, shade)
        canvas.drawRoundRect(box, 18f, 18f, border)
    }

    companion object {
        const val ROI_LEFT = 0.04f
        const val ROI_TOP = 0.34f
        const val ROI_RIGHT = 0.96f
        const val ROI_BOTTOM = 0.66f
    }
}
