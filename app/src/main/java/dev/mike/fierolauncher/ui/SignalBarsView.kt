package dev.mike.fierolauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import dev.mike.fierolauncher.R

/** Four rising bars; [level] 0..4 of them lit, -1 = no service (all dim). */
class SignalBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val on = Paint().apply { color = context.getColor(R.color.text) }
    private val off = Paint().apply { color = context.getColor(R.color.divider) }

    var level: Int = -1
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    override fun onDraw(canvas: Canvas) {
        val gap = width / 12f
        val barW = (width - 3 * gap) / 4f
        for (i in 0 until 4) {
            val left = i * (barW + gap)
            val top = height * (1f - (i + 1) / 4f)
            canvas.drawRect(left, top, left + barW, height.toFloat(), if (i < level) on else off)
        }
    }
}
