package br.com.projectk.nativeapp

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/**
 * Moldura oficial da comunidade (.uf-N), renderizada com o CSS original do site.
 *
 * O avatar é nativo (ImageView); somente a moldura — um quadrado de poucos
 * centímetros — usa o motor de CSS/GIF para reproduzir fielmente o que o autor
 * criou, incluindo kf-spin, pulsos e GIFs enviados por upload. Não é uma tela
 * WebView: é um componente decorativo, equivalente a um drawable animado.
 */
class CommunityFrameView(
    context: Context,
    private val frameKey: String,
    avatarUrl: String?,
    avatarSizePx: Int,
    label: String?,
) : FrameLayout(context) {

    private val avatar: View

    init {
        val pad = (avatarSizePx * 0.11f).toInt().coerceAtLeast(6)
        val total = avatarSizePx + pad * 2

        avatar = if (!avatarUrl.isNullOrBlank()) {
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                val ring = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(KUi.mix(paletteSafe(), Color.BLACK, 0.35f))
                }
                background = ring
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: View, o: android.graphics.Outline) {
                        o.setOval(0, 0, v.width, v.height)
                    }
                }
                Img.load(avatarUrl, this, 320)
            }
        } else {
            TextView(context).apply {
                text = (label?.trim()?.firstOrNull() ?: 'K').uppercaseChar().toString()
                gravity = Gravity.CENTER
                textSize = avatarSizePx / 2.6f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(KUi.mix(paletteSafe(), Color.BLACK, 0.3f))
                }
            }
        }
        addView(avatar, LayoutParams(avatarSizePx, avatarSizePx, Gravity.CENTER))

        val css = Frames.cssOf(frameKey).orEmpty()
        if (css.isNotBlank()) {
            val web = WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = false
                settings.setSupportZoom(false)
                isFocusable = false
                isClickable = false
                loadDataWithBaseURL(Net.BASE + "/", page(css), "text/html", "UTF-8", null)
            }
            addView(web, LayoutParams(total, total, Gravity.CENTER))
        }
        layoutParams = android.widget.LinearLayout.LayoutParams(total, total)
    }

    private fun paletteSafe(): Int = try {
        KTheme.current(context).accentLo
    } catch (e: Throwable) {
        Color.rgb(120, 20, 32)
    }

    private fun page(css: String): String = """
        <!doctype html><html><head><meta charset="utf-8"><style>
        *{margin:0;padding:0;box-sizing:border-box}
        html,body{width:100%;height:100%;background:transparent;overflow:hidden}
        :root{--fw:4px}
        @keyframes kf-spin{to{transform:rotate(360deg)}}
        @keyframes kf-pulse{0%,100%{box-shadow:0 0 6px 1px #ff2d40,inset 0 0 6px 1px #ff2d40}50%{box-shadow:0 0 22px 6px #ff2d40,inset 0 0 12px 3px #ff2d40}}
        @keyframes kf-pulse2{0%,100%{transform:scale(1);opacity:1}50%{transform:scale(1.06);opacity:.75}}
        @keyframes kf-scan{to{background-position:300% 0}}
        @keyframes kf-glitch{0%,86%,100%{transform:translate(0,0)}88%{transform:translate(-3px,1px)}91%{transform:translate(3px,-1px)}94%{transform:translate(-2px,-2px)}97%{transform:translate(2px,2px)}}
        @keyframes kf-glow{0%,100%{opacity:.45}50%{opacity:1}}
        @keyframes kf-flicker{0%,100%{opacity:1}8%{opacity:.35}12%{opacity:1}55%{opacity:.6}60%{opacity:1}85%{opacity:.5}88%{opacity:1}}
        @keyframes kf-twinkle{0%,100%{opacity:.25;transform:rotate(0)}50%{opacity:1;transform:rotate(40deg)}}
        @keyframes kf-beat{0%,100%{transform:scale(1)}50%{transform:scale(1.08)}}
        @keyframes kf-ripple{0%{transform:scale(1);opacity:.8}100%{transform:scale(1.35);opacity:0}}
        @keyframes bxb{to{background-position:300% 0}}
        @keyframes bxh{to{background-position:0 300%}}
        @keyframes bxp{50%{box-shadow:0 0 24px rgba(255,45,64,.95),inset 0 0 14px rgba(255,45,64,.4)}}
        .uf{position:absolute;inset:0;border-radius:50%;pointer-events:none}
        $css
        </style></head><body><div class="uf $frameKey"></div></body></html>
    """.trimIndent()
}
