package ir.mehrdad32.openmrz.sample

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class MrzOverlayView(context: Context) : View(context) {
    private val shade = Paint().apply { color = 0x88000000.toInt() }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(105, 240, 174)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = width * 0.05f
        val right = width * 0.95f
        val top = height * 0.53f
        val bottom = height * 0.88f
        val box = RectF(left, top, right, bottom)

        canvas.drawRect(0f, 0f, width.toFloat(), top, shade)
        canvas.drawRect(0f, bottom, width.toFloat(), height.toFloat(), shade)
        canvas.drawRect(0f, top, left, bottom, shade)
        canvas.drawRect(right, top, width.toFloat(), bottom, shade)
        canvas.drawRoundRect(box, 18f, 18f, border)
    }
}
