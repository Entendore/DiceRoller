package com.example.diceroller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class DiceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var sides: Int = 6
        set(newValue) {
            field = newValue.coerceIn(1, 100)
            value = value.coerceIn(1, field)
            updateAccessibility(); invalidate()
        }

    var value: Int = 1
        set(newValue) {
            field = newValue.coerceIn(1, sides)
            updateAccessibility(); invalidate()
        }

    /** Draws a small red dot so players can see the die is loaded. */
    var loaded: Boolean = false
        set(v) { field = v; updateAccessibility(); invalidate() }

    private var faceColor = Color.WHITE
    private var pipColor = Color.BLACK

    init {
        val ta = context.theme.obtainStyledAttributes(
            intArrayOf(R.attr.diceFaceColor, R.attr.dicePipColor)
        )
        try {
            faceColor = ta.getColor(0, Color.WHITE)
            pipColor = ta.getColor(1, Color.BLACK)
        } finally {
            ta.recycle()
        }
        updateAccessibility()
    }

    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val loadedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E53935") }
    private val path = Path()

    private fun updateAccessibility() {
        contentDescription = "d$sides die showing $value" + if (loaded) ", loaded" else ""
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = min(width, height).toFloat()
        val left = (width - side) / 2f
        val top = (height - side) / 2f
        val cx = left + side / 2f
        val cy = top + side / 2f
        val radius = side / 2f - side * 0.05f

        facePaint.color = faceColor
        borderPaint.color = pipColor
        borderPaint.strokeWidth = side * 0.03f
        pipPaint.color = pipColor
        textPaint.color = pipColor
        textPaint.textSize = side * if (value >= 10) 0.34f else 0.42f

        when (sides) {
            6 -> { drawRoundedSquare(canvas, left, top, side); drawPips(canvas, left, top, side) }
            else -> {
                val vertices = verticesFor(sides)
                if (vertices == 0) drawCircleFace(canvas, cx, cy, radius)
                else drawPolygon(canvas, cx, cy, radius, vertices)
                drawNumber(canvas, left, top, side)
            }
        }

        if (loaded) {
            // top-center for the d4 triangle (its bottom-right is outside the shape)
            val dx = if (sides == 4) 0.5f else 0.90f
            val dy = if (sides == 4) 0.20f else 0.90f
            canvas.drawCircle(left + side * dx, top + side * dy, side * 0.055f, loadedPaint)
        }
    }

    // 0 = draw a circle (d1, d2, d100). Otherwise a regular polygon with that
    // many vertices — the classic "top-down view" of each die type.
    private fun verticesFor(sides: Int): Int = when {
        sides <= 2 || sides >= 100 -> 0
        else -> min(sides, 12)
    }

    private fun drawRoundedSquare(canvas: Canvas, left: Float, top: Float, side: Float) {
        val rect = RectF(left, top, left + side, top + side)
        val corner = side * 0.18f
        canvas.drawRoundRect(rect, corner, corner, facePaint)
        canvas.drawRoundRect(rect, corner, corner, borderPaint)
    }

    private fun drawCircleFace(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        canvas.drawCircle(cx, cy, radius, facePaint)
        canvas.drawCircle(cx, cy, radius, borderPaint)
    }

    private fun drawPolygon(canvas: Canvas, cx: Float, cy: Float, radius: Float, vertices: Int) {
        path.reset()
        val offsetDeg = if (vertices % 2 == 0) 180f / vertices else 0f  // flat top for even shapes
        for (i in 0 until vertices) {
            val angle = Math.toRadians((offsetDeg + 360f * i / vertices - 90f).toDouble())
            val x = cx + radius * cos(angle).toFloat()
            val y = cy + radius * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, facePaint)
        canvas.drawPath(path, borderPaint)
    }

    private fun drawNumber(canvas: Canvas, left: Float, top: Float, side: Float) {
        val cx = left + side / 2f
        val centerY = if (sides == 4) top + side * 0.66f else top + side * 0.5f
        val baseline = centerY - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(value.toString(), cx, baseline, textPaint)
    }

    private fun drawPips(canvas: Canvas, left: Float, top: Float, side: Float) {
        val a = left + side * 0.27f; val b = left + side * 0.5f; val c = left + side * 0.73f
        val r1 = top + side * 0.27f; val r2 = top + side * 0.5f; val r3 = top + side * 0.73f
        val positions = when (value) {
            1 -> listOf(b to r2)
            2 -> listOf(a to r1, c to r3)
            3 -> listOf(a to r1, b to r2, c to r3)
            4 -> listOf(a to r1, c to r1, a to r3, c to r3)
            5 -> listOf(a to r1, c to r1, b to r2, a to r3, c to r3)
            else -> listOf(a to r1, c to r1, a to r2, c to r2, a to r3, c to r3)
        }
        val radius = side * 0.085f
        for ((x, y) in positions) canvas.drawCircle(x, y, radius, pipPaint)
    }
}
