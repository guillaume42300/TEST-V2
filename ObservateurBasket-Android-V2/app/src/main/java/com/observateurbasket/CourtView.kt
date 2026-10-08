package com.observateurbasket

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

data class CourtActor(
    var label: String,
    var x: Float,
    var y: Float,
    var kind: Int // 0 player A, 1 player B, 2 referee
)

class CourtView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    val actors = mutableListOf<CourtActor>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var selected: CourtActor? = null
    private var downX = 0f
    private var downY = 0f

    init { setBackgroundColor(Color.WHITE) }

    fun setActors(labels: List<String>) {
        actors.clear()
        labels.distinct().forEachIndexed { i, label ->
            val kind = if (label == "R1" || label == "R2") 2 else if (label.startsWith("A")) 0 else 1
            val x = 0.20f + (i % 4) * 0.20f
            val y = 0.25f + (i / 4) * 0.18f
            actors.add(CourtActor(label, x, y, kind))
        }
        invalidate()
    }

    fun getActors(): List<CourtActor> = actors.toList()

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(238, 188, 105)
        c.drawRect(0f, 0f, w, h, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.WHITE
        c.drawRect(8f, 8f, w-8f, h-8f, paint)
        c.drawLine(w/2, 8f, w/2, h-8f, paint)
        c.drawCircle(w/2, h/2, minOf(w,h)*0.10f, paint)

        // baskets / keys / 3-point arcs on both ends
        drawBasketSide(c, 0f, h/2, true)
        drawBasketSide(c, w, h/2, false)

        actors.forEach { a ->
            paint.style = Paint.Style.FILL
            paint.color = when (a.kind) {
                0 -> Color.rgb(190, 40, 40)
                1 -> Color.rgb(35, 80, 180)
                else -> Color.rgb(35, 35, 35)
            }
            val px = a.x * w
            val py = a.y * h
            c.drawCircle(px, py, 22f, paint)
            paint.color = Color.WHITE
            paint.textSize = 22f
            paint.textAlign = Paint.Align.CENTER
            c.drawText(a.label, px, py + 8f, paint)
        }
    }

    private fun drawBasketSide(c: Canvas, x: Float, cy: Float, left: Boolean) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.WHITE
        val dir = if (left) 1 else -1
        val keyW = width * 0.16f
        val keyL = if (left) x else x-keyW
        c.drawRect(keyL, cy-height*0.12f, keyL+keyW, cy+height*0.12f, paint)
        c.drawCircle(x + dir*18f, cy, 10f, paint)
        val oval = RectF(
            if (left) x-10f else x-width*0.29f+10f,
            cy-height*0.29f,
            if (left) width*0.29f-10f else x+10f,
            cy+height*0.29f
        )
        c.drawArc(oval, if (left) -65f else 115f, 130f, false, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when(e.action) {
            MotionEvent.ACTION_DOWN -> {
                downX=e.x; downY=e.y
                selected = actors.minByOrNull { abs(it.x*width-e.x)+abs(it.y*height-e.y) }
                if (selected != null) {
                    val d = abs(selected!!.x*width-e.x)+abs(selected!!.y*height-e.y)
                    if (d > 70f) selected=null
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                selected?.let {
                    it.x=(e.x/width).coerceIn(0.03f,0.97f)
                    it.y=(e.y/height).coerceIn(0.03f,0.97f)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> { selected=null; return true }
        }
        return true
    }
}
