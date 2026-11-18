package br.com.projectk.nativeapp

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Camada visual "level 2" — limpa e sólida, no estilo WhatsApp iOS com a
 * paleta do site K. Sem vidro exagerado, grão, meteoros ou brilho.
 */
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

    fun bodyTypeface(p: KPalette): Typeface = Typeface.DEFAULT
    fun displayTypeface(p: KPalette): Typeface = Typeface.DEFAULT_BOLD

    fun button(p: KPalette, radiusPx: Float, primary: Boolean): Drawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            if (primary) intArrayOf(p.accentHover, p.accent)
            else intArrayOf(p.surfaceRaised, p.surface)
        ).apply {
            cornerRadius = radiusPx
            setStroke(1, if (primary) Color.TRANSPARENT else KUi.alpha(p.borderStrong, 0.45f))
        }

    fun field(p: KPalette, radiusPx: Float): Drawable = GradientDrawable().apply {
        cornerRadius = radiusPx
        setColor(if (p.light) Color.WHITE else p.surface)
        setStroke(1, KUi.alpha(p.border, 0.7f))
    }

    fun glass(p: KPalette, radiusPx: Float, strong: Boolean = false): Drawable = GradientDrawable().apply {
        cornerRadius = radiusPx
        setColor(if (p.light) Color.WHITE else p.surfaceRaised)
        setStroke(1, KUi.alpha(p.border, 0.5f))
    }

    fun card(p: KPalette, radiusPx: Float): Drawable = KCardDrawable(p, radiusPx)

    fun backdrop(p: KPalette): KBackdropDrawable = KBackdropDrawable(p)

    fun press(view: View) {
        ValueAnimator.ofFloat(1f, 0.975f, 1f).apply {
            duration = 140
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                view.scaleX = f
                view.scaleY = f
            }
            start()
        }
    }
}

/** Cartão simples: fundo sólido, borda fina, cantos arredondados. */
class KCardDrawable(private val p: KPalette, private val radius: Float) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun draw(canvas: Canvas) {
        if (bounds.width() <= 2 || bounds.height() <= 2) return
        val box = android.graphics.RectF(bounds).apply { inset(0.5f, 0.5f) }
        fill.color = if (p.light) Color.WHITE else p.surfaceRaised
        canvas.drawRoundRect(box, radius, radius, fill)
        edge.strokeWidth = 1f
        edge.color = KUi.alpha(p.border, 0.7f)
        canvas.drawRoundRect(box, radius, radius, edge)
    }

    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

/** Fundo com gradiente sutil — sem elementos decorativos que distraiam. */
class KBackdropDrawable(private val p: KPalette) : Drawable() {
    private val base = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0 || h <= 0) return
        base.shader = LinearGradient(
            0f, 0f, 0f, h,
            if (p.light) Color.WHITE else p.bg,
            if (p.light) p.surface else KUi.mix(p.bg, p.surface, 0.45f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawRect(bounds, base)
    }

    fun startAnimating(): android.animation.ValueAnimator =
        android.animation.ValueAnimator.ofFloat(0f, 0f).apply { start() }

    fun stopAnimating() {}

    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
}
