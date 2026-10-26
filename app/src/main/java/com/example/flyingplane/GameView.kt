package com.example.flyingplane

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class GameView(
    context: Context,
    private val difficulty: Int,
    private val skin: Int,
    private val prefs: android.content.SharedPreferences,
    saved: Bundle?
) : View(context) {
    var onGameOver: ((Int, Int) -> Unit)? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }
    private var px = 0f
    private var py = 0f
    private var tx = 0f
    private var ty = 0f
    private var angle = 0f
    private var touching = false
    private var started = false
    private var ended = false
    private var frame = 0
    private var coins = 0
    private var score = 0
    private var altitude = 0
    private val trail = ArrayDeque<PointF>()
    private val coinPoints = mutableListOf<PointF>()
    private val obstacles = mutableListOf<Obstacle>()
    private data class Obstacle(var x: Float, var y: Float, val bird: Boolean, var phase: Float = Random.nextFloat() * 6.28f)

    private val scrollSpeed: Float get() = when (difficulty) { 0 -> 3f; 1 -> 4.5f; else -> 6f }
    private val followSpeed: Float get() = when (difficulty) { 0 -> .25f; 1 -> .29f; else -> .34f }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        if (saved != null) importState(saved)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (px == 0f) {
            px = w * .27f; py = h * .5f; tx = px; ty = py
        }
        if (coinPoints.isEmpty() && obstacles.isEmpty()) {
            var x = w * .7f
            repeat(9) { coinPoints.add(PointF(x, randomY())); x += w * .34f }
            x = w * .95f
            repeat(8) { obstacles.add(Obstacle(x, randomY(), Random.nextBoolean())); x += w * .42f }
        }
    }

    private fun randomY(): Float {
        val top = max(145f, height * .12f)
        val bottom = max(top + 100f, height * .88f)
        return Random.nextFloat() * (bottom - top) + top
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(Color.rgb(100, 191, 244))
        drawCloudBackground(c, w, h)

        p.style = Paint.Style.FILL
        p.color = Color.argb(220, 255, 255, 255)
        c.drawRoundRect(RectF(14f, 14f, w - 14f, 110f), 22f, 22f, p)
        p.color = Color.rgb(25, 68, 103); p.textSize = 24f; p.isFakeBoldText = true
        c.drawText("Монеты: $coins     Счёт: $score", 28f, 51f, p)
        p.textSize = 19f; c.drawText("Высота: ${max(0, altitude)} м", 28f, 83f, p)
        p.isFakeBoldText = false

        if (trail.size > 1) {
            val path = Path()
            val pts = trail.toList()
            path.moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
            c.drawPath(path, trailPaint)
        }
        coinPoints.forEach { if (it.x > -30f && it.x < w + 30f) drawCoin(c, it.x, it.y) }
        obstacles.forEach { if (it.x > -80f && it.x < w + 80f) drawObstacle(c, it) }
        drawPlane(c, px, py, angle)

        if (!started && !ended) {
            p.color = Color.WHITE; p.textSize = 21f; p.textAlign = Paint.Align.CENTER
            c.drawText("Коснись экрана и веди пальцем", w / 2f, h - 45f, p)
            p.textAlign = Paint.Align.LEFT
        }
        if (ended) {
            p.color = Color.argb(185, 13, 38, 62); c.drawRect(0f, 0f, w, h, p)
            p.color = Color.WHITE; p.textAlign = Paint.Align.CENTER; p.textSize = 34f; p.isFakeBoldText = true
            c.drawText("СТОЛКНОВЕНИЕ!", w / 2f, h / 2f, p)
            p.textSize = 22f; p.isFakeBoldText = false
            c.drawText("Счёт: $score", w / 2f, h / 2f + 40f, p)
            c.drawText("Вернись в меню для нового полёта", w / 2f, h / 2f + 75f, p)
            p.textAlign = Paint.Align.LEFT
        } else if (started) {
            updateGame()
            postInvalidateOnAnimation()
        }
    }

    private fun drawCloudBackground(c: Canvas, w: Float, h: Float) {
        p.style = Paint.Style.FILL; p.color = Color.argb(85, 255, 255, 255)
        for (i in 0..6) {
            val x = ((i * 181f - frame * .6f) % (w + 180f) + w + 180f) % (w + 180f)
            val y = 145f + (i % 4) * h * .18f
            c.drawCircle(x, y, 27f, p); c.drawCircle(x + 25f, y - 12f, 34f, p); c.drawCircle(x + 52f, y, 25f, p)
        }
    }

    private fun drawCoin(c: Canvas, x: Float, y: Float) {
        p.style = Paint.Style.FILL; p.color = Color.rgb(255, 183, 0); c.drawCircle(x, y, 18f, p)
        p.color = Color.rgb(255, 232, 105); p.style = Paint.Style.STROKE; p.strokeWidth = 4f; c.drawCircle(x, y, 12f, p)
        p.style = Paint.Style.FILL; p.color = Color.rgb(160, 91, 0); p.textSize = 18f; p.textAlign = Paint.Align.CENTER
        c.drawText("$", x, y + 6f, p); p.textAlign = Paint.Align.LEFT
    }

    private fun drawObstacle(c: Canvas, o: Obstacle) {
        p.style = Paint.Style.FILL
        if (o.bird) {
            val flap = sin(o.phase).toFloat() * 12f
            p.color = Color.rgb(53, 62, 78); c.drawOval(o.x - 23f, o.y - 10f, o.x + 23f, o.y + 10f, p)
            val wing = Path().apply { moveTo(o.x - 2f, o.y); lineTo(o.x - 22f, o.y - 18f - flap); lineTo(o.x + 8f, o.y - 5f); close() }
            c.drawPath(wing, p)
            p.color = Color.rgb(255, 190, 35)
            val beak = Path().apply { moveTo(o.x + 20f, o.y - 2f); lineTo(o.x + 33f, o.y + 2f); lineTo(o.x + 20f, o.y + 5f); close() }
            c.drawPath(beak, p)
            p.color = Color.WHITE; c.drawCircle(o.x + 11f, o.y - 4f, 3f, p)
            p.color = Color.BLACK; c.drawCircle(o.x + 12f, o.y - 4f, 1.5f, p)
        } else {
            p.color = Color.WHITE
            c.drawCircle(o.x - 20f, o.y + 3f, 23f, p); c.drawCircle(o.x + 4f, o.y - 9f, 31f, p); c.drawCircle(o.x + 31f, o.y + 4f, 22f, p)
        }
    }

    private fun drawPlane(c: Canvas, x: Float, y: Float, rotation: Float) {
        val body = when (skin) { 1 -> Color.rgb(35, 117, 255); 2 -> Color.rgb(255, 190, 20); 3 -> Color.rgb(255, 90, 160); else -> Color.WHITE }
        c.save()
        // Nose points right. Small rotation follows the direction of movement.
        c.scale(1.5f, 1.5f, x, y)
        c.rotate(Math.toDegrees(rotation.toDouble()).toFloat(), x, y)
        p.style = Paint.Style.FILL; p.color = Color.argb(50, 20, 50, 70); c.drawOval(x - 25f, y + 22f, x + 30f, y + 31f, p)
        val plane = Path().apply { moveTo(x + 40f, y); moveTo(x + 39f, y - 1f); lineTo(x - 31f, y - 23f); lineTo(x - 14f, y + 1f); lineTo(x - 31f, y + 20f); close() }
        p.color = body; c.drawPath(plane, p)
        val fold = Path().apply { moveTo(x - 14f, y + 1f); lineTo(x + 39f, y - 1f); lineTo(x - 22f, y + 13f); close() }
        p.color = when (skin) { 1 -> Color.rgb(15, 72, 180); 2 -> Color.rgb(220, 143, 0); 3 -> Color.rgb(205, 40, 112); else -> Color.rgb(180, 205, 220) }
        c.drawPath(fold, p)
        c.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (ended) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                started = true; touching = true
                tx = e.x.coerceIn(45f, max(46f, width - 45f))
                ty = e.y.coerceIn(125f, max(126f, height - 22f))
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { touching = false; return true }
        }
        return true
    }

    private fun updateGame() {
        if (ended || width <= 0 || height <= 0) return
        frame++
        val oldX = px; val oldY = py
        val f = if (touching) followSpeed else .10f
        px += (tx - px) * f
        py += (ty - py) * f
        val dx = px - oldX; val dy = py - oldY
        if (abs(dx) + abs(dy) > .1f) {
            val desired = atan2(dy, max(1f, abs(dx))).coerceIn(-.48f, .48f)
            angle = angle * .76f + desired * .24f
        } else angle *= .9f

        // Endless scrolling: random objects enter from the right forever.
        coinPoints.forEach { it.x -= scrollSpeed }
        obstacles.forEach { it.x -= scrollSpeed; it.phase += .22f; if (it.bird) it.y += sin(it.phase).toFloat() * .45f }
        coinPoints.removeAll { it.x < -40f }
        obstacles.removeAll { it.x < -100f }
        if (frame % 34 == 0) coinPoints.add(PointF(width + 30f, randomY()))
        val obstaclePeriod = when (difficulty) { 0 -> 105; 1 -> 78; else -> 58 }
        if (frame % obstaclePeriod == 0) obstacles.add(Obstacle(width + 70f, randomY(), Random.nextBoolean()))

        val iterator = coinPoints.iterator()
        while (iterator.hasNext()) {
            val coin = iterator.next()
            if (distance(px, py, coin.x, coin.y) < 34f) { coins++; score += 10; iterator.remove() }
        }
        for (o in obstacles) {
            if (distance(px, py, o.x, o.y) < if (o.bird) 29f else 39f) { finish(); return }
        }
        altitude = max(0, ((height - py) / 4f).toInt())
        score++
        if (py < 118f || py > height - 18f || px < -15f || px > width + 15f) { finish(); return }
        trail.addLast(PointF(px, py))
        while (trail.size > 30) trail.removeFirst()
    }

    private fun finish() {
        if (ended) return
        ended = true; started = false; touching = false
        onGameOver?.invoke(coins, score)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float = hypot(x1 - x2, y1 - y2)

    fun exportState(): Bundle {
        val b = Bundle()
        b.putFloat("planeX", px); b.putFloat("planeY", py); b.putFloat("targetX", tx); b.putFloat("targetY", ty)
        b.putFloat("planeAngle", angle); b.putBoolean("started", started); b.putBoolean("ended", ended)
        b.putInt("frame", frame); b.putInt("coins", coins); b.putInt("score", score); b.putInt("altitude", altitude)
        b.putFloatArray("coinX", FloatArray(coinPoints.size) { coinPoints[it].x })
        b.putFloatArray("coinY", FloatArray(coinPoints.size) { coinPoints[it].y })
        b.putFloatArray("obsX", FloatArray(obstacles.size) { obstacles[it].x })
        b.putFloatArray("obsY", FloatArray(obstacles.size) { obstacles[it].y })
        b.putBooleanArray("obsBird", BooleanArray(obstacles.size) { obstacles[it].bird })
        return b
    }

    private fun importState(b: Bundle) {
        px = b.getFloat("planeX", 0f); py = b.getFloat("planeY", 0f)
        tx = b.getFloat("targetX", px); ty = b.getFloat("targetY", py); angle = b.getFloat("planeAngle", 0f)
        started = b.getBoolean("started", false); ended = b.getBoolean("ended", false)
        frame = b.getInt("frame", 0); coins = b.getInt("coins", 0); score = b.getInt("score", 0); altitude = b.getInt("altitude", 0)
        val cx = b.getFloatArray("coinX") ?: floatArrayOf(); val cy = b.getFloatArray("coinY") ?: floatArrayOf()
        coinPoints.clear()
        for (i in 0 until min(cx.size, cy.size)) coinPoints.add(PointF(cx[i], cy[i]))
        val ox = b.getFloatArray("obsX") ?: floatArrayOf(); val oy = b.getFloatArray("obsY") ?: floatArrayOf()
        val ob = b.getBooleanArray("obsBird") ?: booleanArrayOf()
        obstacles.clear()
        for (i in 0 until min(min(ox.size, oy.size), ob.size)) obstacles.add(Obstacle(ox[i], oy[i], ob[i]))
    }
}
