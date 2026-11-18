package br.com.projectk.nativeapp

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Camada visual “level 2”: vidro, brilho e movimento desenhados no próprio app. */
object KUi {
    fun alpha(color: Int, a: Float): Int = Color.argb(
        (255 * a.coerceIn(0f, 1f)).toInt(),
        Color.red(color), Color.green(color), Color.blue(color),
    )

    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt(),
        )
    }

    fun bodyTypeface(p: KPalette): Typeface = if (p.mono) Typeface.MONOSPACE else Typeface.DEFAULT
    fun displayTypeface(p: KPalette): Typeface = if (p.mono) Typeface.MONOSPACE else Typeface.DEFAULT_BOLD

    fun button(p: KPalette, radiusPx: Float, primary: Boolean): Drawable {
        val top = if (primary) p.accentHover else alpha(p.surfaceHover, 0.95f)
        val bottom = if (primary) p.accentLo else alpha(p.surfaceRaised, 0.85f)
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(top, bottom)).apply {
            cornerRadius = radiusPx
            setStroke(
                1,
                if (primary) alpha(Color.WHITE, if (p.light) 0.15f else 0.32f)
                else alpha(p.borderStrong, 0.55f),
            )
        }
    }

    fun field(p: KPalette, radiusPx: Float): Drawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(alpha(Color.BLACK, if (p.light) 0.02f else 0.32f), alpha(p.surfaceRaised, 0.92f)),
    ).apply {
        cornerRadius = radiusPx
        setStroke(1, alpha(p.borderStrong, 0.62f))
    }

    fun glass(p: KPalette, radiusPx: Float, strong: Boolean = false): Drawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            alpha(if (p.light) Color.WHITE else p.surfaceRaised, if (strong) 0.96f else 0.86f),
            alpha(if (p.light) Color.WHITE else p.surface, if (strong) 0.92f else 0.68f),
        ),
    ).apply {
        cornerRadius = radiusPx
        setStroke(1, alpha(p.borderStrong, if (p.glow) 0.48f else 0.34f))
    }

    fun card(p: KPalette, radiusPx: Float): Drawable = KCardDrawable(p, radiusPx)

    /** Fundo cinematográfico: lua, meteoros, vinheta e grão. A animação é controlada pela Activity. */
    fun backdrop(p: KPalette): KBackdropDrawable = KBackdropDrawable(p)

    fun press(view: View) {
        val a = ValueAnimator.ofFloat(1f, 0.965f, 1f).apply {
            duration = 190
            interpolator = DecelerateInterpolator()
            addUpdateListener { v ->
                val f = v.animatedValue as Float
                view.scaleX = f
                view.scaleY = f
            }
        }
        a.start()
    }
}

/** Cartão de vidro com borda luminosa — inspirado nos painéis do tema gameover. */
class KCardDrawable(private val p: KPalette, private val radius: Float) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val box = RectF()

    override fun draw(canvas: Canvas) {
        if (bounds.width() <= 2 || bounds.height() <= 2) return
        box.set(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())
        val inset = 1.5f
        box.inset(inset, inset)
        fill.shader = LinearGradient(
            box.left, box.top, box.right, box.bottom,
            KUi.alpha(KUi.mix(p.surfaceRaised, Color.BLACK, 0.28f), if (p.light) 0.96f else 0.94f),
            KUi.alpha(p.surface, if (p.light) 0.94f else 0.88f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(box, radius, radius, fill)

        if (p.glow) {
            glow.strokeWidth = 5f
            glow.color = KUi.alpha(p.accent, 0.10f)
            canvas.drawRoundRect(box, radius, radius, glow)
        }
        edge.strokeWidth = 1f
        edge.shader = LinearGradient(
            box.left, box.top, box.right, box.top,
            KUi.alpha(p.accent, if (p.glow) 0.48f else 0.22f),
            KUi.alpha(p.borderStrong, 0.62f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(box, radius, radius, edge)
    }

    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

/** Lua desfocada, meteoros e grão — tudo em Runtime, sem imagem estática. */
class KBackdropDrawable(private val p: KPalette) : Drawable() {
    private val base = Paint(Paint.ANTI_ALIAS_FLAG)
    private val moon = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vignette = Paint(Paint.ANTI_ALIAS_FLAG)
    private val meteor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val grain = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var anim: ValueAnimator? = null

    fun startAnimating(): ValueAnimator {
        stopAnimating()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 18000
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
        return anim!!
    }

    fun stopAnimating() {
        anim?.cancel()
        anim = null
    }

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0 || h <= 0) return

        base.color = p.bg
        canvas.drawRect(bounds, base)

        if (p.light) {
            val g = LinearGradient(0f, 0f, 0f, h * 0.35f, KUi.alpha(p.accent, 0.06f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = g }
            canvas.drawRect(0f, 0f, w, h * 0.35f, gp)
            return
        }

        val drift = sin(phase * Math.PI.toFloat() * 2f) * 18f
        val moonR = maxOf(w, h) * 0.52f
        val mx = w * 0.94f + drift
        val my = -h * 0.12f + drift * 0.5f
        moon.shader = RadialGradient(
            mx, my, 1f, mx, my, moonR,
            longArrayOf(
                KUi.alpha(Color.WHITE, 0.30f).toLong(),
                KUi.alpha(Color.WHITE, 0.12f).toLong(),
                KUi.alpha(p.accent, 0.07f).toLong(),
                Color.TRANSPARENT.toLong(),
            ),
            floatArrayOf(0f, 0.28f, 0.62f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(mx, my, moonR, moon)

        for (i in 0 until 3) {
            val t = ((phase + i * 0.34f) % 1f + 1f) % 1f
            if (t > 0.32f) continue
            val x = w * (0.92f - t * 1.5f)
            val y = h * (0.06f + t * 1.05f)
            val len = w * (0.12f + i * 0.03f)
            val a = (1f - t / 0.32f) * 0.6f
            meteor.strokeWidth = 1.5f + i * 0.4f
            meteor.shader = LinearGradient(x, y, x - len, y + len * 0.62f,
                KUi.alpha(Color.WHITE, a), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawLine(x, y, x - len, y + len * 0.62f, meteor)
        }

        vignette.shader = RadialGradient(
            w * 0.5f, h * 0.42f, 1f,
            w * 0.5f, h * 0.42f, maxOf(w, h) * 0.78f,
            longArrayOf(Color.TRANSPARENT.toLong(), Color.TRANSPARENT.toLong(), KUi.alpha(Color.BLACK, 0.52f).toLong()),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(bounds, vignette)

        grain.color = KUi.alpha(Color.WHITE, 0.028f)
        var i = 0
        while (i < 110) {
            val x = ((abs(sin(i * 12.9898f) * 43758.5453f) % 1f) * w)
            val y = ((abs(sin(i * 78.233f) * 34567.123f) % 1f) * h)
            canvas.drawCircle(x, y, 0.6f + (i % 3) * 0.25f, grain)
            i++
        }
    }

    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
}
