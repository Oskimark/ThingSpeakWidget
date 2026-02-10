package com.example.thingspeakwidget

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class GraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: List<Float> = emptyList()
    private val paintLine = Paint().apply {
        color = Color.BLUE
        strokeWidth = 5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val paintPoint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val paintGrid = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 2f
    }
    private val paintText = Paint().apply {
        color = Color.BLACK
        textSize = 30f
        isAntiAlias = true
    }

    fun setData(newData: List<Float>) {
        data = newData
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.size < 2) {
            canvas.drawText("No enough data to graph", 50f, height / 2f, paintText)
            return
        }

        val padding = 60f
        val graphHeight = height - 2 * padding
        val graphWidth = width - 2 * padding

        val minVal = data.minOrNull() ?: 0f
        val maxVal = data.maxOrNull() ?: 100f
        val range = if (maxVal == minVal) 1f else maxVal - minVal

        val stepX = graphWidth / (data.size - 1)

        // Draw Grid
        canvas.drawLine(padding, padding, padding, height - padding, paintGrid) // Y axis
        canvas.drawLine(padding, height - padding, width - padding, height - padding, paintGrid) // X axis

        val path = Path()
        for (i in data.indices) {
            val x = padding + i * stepX
            val y = height - padding - ((data[i] - minVal) / range * graphHeight)

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
            canvas.drawCircle(x, y, 8f, paintPoint)
        }
        canvas.drawPath(path, paintLine)

        // Draw Min/Max text
        canvas.drawText("%.2f".format(maxVal), 5f, padding + 30f, paintText)
        canvas.drawText("%.2f".format(minVal), 5f, height - padding, paintText)
    }
}
