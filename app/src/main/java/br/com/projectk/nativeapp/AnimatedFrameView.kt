package br.com.projectk.nativeapp

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * Moldura de avatar 100% nativa e animada.
 *
 * Não há WebView, Bitmap de screenshot, GIF ou WebP: o anel é desenhado em Canvas
 * e gira/pulsa/ripple/twinkle continuamente com ValueAnimator, inspirado nos
 * keyframes kf-spin / kf-pulse / kf-glitch / kf-beat / kf-ripple / kf-twinkle do site.
 */
class AnimatedFrameView(
    context: Context,
    private val palette: KPalette,
    private val frame: String?,
    avatarUrl: String?,
    sizePx: Int,
    private val label: String? = null,
) : View(context) {

    private val modes: List<String>
    private var colors: IntArray
    private var avatar: Bitmap? = null
    private var t = 0f
    private var anim: ValueAnimator? = null

    private val back = Paint(Paint.ANTI_ALIAS_FLAG)
    private val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val ripple = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val star = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val box = RectF()

    init {
        val found = Regex("uf-\\d+|cx-[0-9a-fA-F]+(?:-[0-9a-fA-F]+)+|k-frame--[a-z0-9_]+")
            .findAll(frame.orEmpty()).map { it.value }.toList()
        modes = found
        colors = found.firstNotNullOfOrNull { Frames.colorsOf(it) }
            ?: intArrayOf(
                palette.accent,
                palette.accentHover,
                if (palette.glow) Color.WHITE else palette.accentLo,
                palette.accentHover,
                palette.accent,
            )
        if (colors.isEmpty()) colors = intArrayOf(palette.accent, palette.accentHover, palette.accent)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        Img.loadBitmap(avatarUrl, sizePx) { bmp ->
            post {
                avatar = bmp
                invalidate()
            }
        }
    }

    private fun styleOf(): String {
        val f = modes.joinToString(" ").lowercase()
        return when {
            f.contains("galaxy") || f.contains("cosmic") || f.contains("star") -> "cosmic"
            f.contains("flame") || f.contains("fire") || f.contains("ember") || f.contains("red") -> "ember"
            f.contains("ice") || f.contains("frost") || f.contains("blue") -> "ice"
            f.startsWith("cx-") -> "neon"
            f.contains("uf-") -> "royal"
            else -> "pulse"
        }
    }

    private fun start() {
        if (anim != null) return
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = when (styleOf()) {
                "neon" -> 3200L
                "cosmic" -> 6800L
                else -> 4600L
            }
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                t = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stop() {
        anim?.cancel()
        anim = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(w: Int, h: Int) {
        val side = maxOf(2, minOf(measuredWidth.takeIf { it > 0 } ?: 200, 240))
        setMeasuredDimension(
            resolveSize(measuredWidth.takeIf { it > 0 } ?: side, w),
            resolveSize(measuredHeight.takeIf { it > 0 } ?: side, h),
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        avatarPaint.shader = null
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w < 4 || h < 4) return
        val cx = w / 2f
        val cy = h / 2f
        val fw = (w * 0.0625f).coerceAtLeast(3f)
        val radius = (w / 2f) - fw
        if (radius <= 2f) return

        // Fundo e avatar recortados.
        back.color = KUi.mix(palette.surfaceRaised, Color.BLACK, 0.55f)
        canvas.drawCircle(cx, cy, radius, back)
        val bmp = avatar
        if (bmp != null && bmp.width > 0 && bmp.height > 0) {
            val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = maxOf(radius * 2f / bmp.width, radius * 2f / bmp.height)
            val m = Matrix().apply {
                setScale(scale, scale)
                postTranslate(cx - bmp.width * scale / 2f, cy - bmp.height * scale / 2f)
            }
            shader.setLocalMatrix(m)
            avatarPaint.shader = shader
            canvas.drawCircle(cx, cy, radius - 1f, avatarPaint)
        } else if (!label.isNullOrBlank()) {
            letter.textSize = radius * 0.9f
            letter.color = Color.WHITE
            canvas.drawText(label.take(1).uppercase(), cx, cy + radius * 0.32f, letter)
        }

        // Halo pulsante (sem blur: vários anéis translúcidos).
        val beat = 0.5f + 0.5f * sin(t * Math.PI.toFloat() * 2f)
        for (i in 3 downTo 1) {
            glow.strokeWidth = fw * (0.7f + i * 0.55f)
            glow.color = KUi.alpha(colors[i % colors.size], (0.06f + beat * 0.06f) / i)
            canvas.drawCircle(cx, cy, radius + fw * 0.55f + i * fw * 0.3f, glow)
        }

        // Anel cônico giratório — o “kf-spin” nativo.
        val rot = t * 360f
        val m = Matrix().apply {
            setRotate(rot, cx, cy)
        }
        val sweepColors = if (colors.size == 1) intArrayOf(colors[0], colors[0], colors[0]) else colors
        val sg = SweepGradient(cx, cy, sweepColors, null)
        sg.setLocalMatrix(m)
        ring.shader = sg
        ring.strokeWidth = fw
        canvas.drawCircle(cx, cy, radius + fw * 0.5f, ring)

        // Arcos de destaque: dão a sensação de engrenagem/energia.
        arc.strokeWidth = fw * 0.42f
        for (i in 0 until 3) {
            val a0 = rot * (if (i % 2 == 0) 1f else -1.35f) + i * 120f
            val span = when (styleOf()) {
                "ember" -> 42f
                "cosmic" -> 74f
                else -> 58f
            }
            val jitter = if (styleOf() == "neon" && (t * 13f).toInt() % 7 == 0) 4f else 0f
            arc.color = KUi.alpha(colors[(i + 1) % colors.size], 0.95f)
            arc.shader = null
            box.set(
                cx - radius - fw + jitter, cy - radius - fw,
                cx + radius + fw + jitter, cy + radius + fw,
            )
            canvas.drawArc(box, a0, span, false, arc)
        }

        // Ripple expansivo.
        val rp = (t * 1.7f) % 1f
        ripple.strokeWidth = fw * 0.55f
        ripple.color = KUi.alpha(colors[0], (1f - rp) * 0.5f)
        canvas.drawCircle(cx, cy, radius + fw + rp * fw * 4f, ripple)

        // Estrelinhas em órbita.
        if (styleOf() == "cosmic" || styleOf() == "royal") {
            star.color = KUi.alpha(Color.WHITE, 0.55f + beat * 0.4f)
            for (i in 0 until 7) {
                val a = rot * (0.7f + i * 0.07f) + i * (360f / 7f)
                val rr = radius + fw * 2.6f + sin(t * 8f + i) * fw
                val sx = cx + cos(Math.toRadians(a.toDouble())).toFloat() * rr
                val sy = cy + sin(Math.toRadians(a.toDouble())).toFloat() * rr
                val sr = fw * (0.22f + 0.14f * sin(t * 11f + i * 2f))
                canvas.drawCircle(sx, sy, sr.coerceAtLeast(0.6f), star)
            }
        }
    }
}
