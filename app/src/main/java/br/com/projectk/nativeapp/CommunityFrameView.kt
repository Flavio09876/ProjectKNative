package br.com.projectk.nativeapp

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/**
 * Moldura criada pela comunidade, montada como no Project Z:
 * avatar oval nativo + overlay de imagem sobre ele.
 *
 * Quando a criação tem mídia enviada (`media_url`, PNG/GIF/WebP), o overlay é
 * carregado pelo mesmo caminho do Z: URL → decodificador → ImageView. Sem
 * WebView. Para criações que são só CSS, usa-se um componente decorativo
 * minúsculo com o CSS original, sem virar tela nem navegação.
 */
class CommunityFrameView(
    context: Context,
    private val frameKey: String,
    avatarUrl: String?,
    avatarSizePx: Int,
    label: String?,
) : FrameLayout(context) {

    init {
        // Proporção do Project Z: moldura 84dp sobre avatar 60dp (1.4x).
        val pad = (avatarSizePx * 0.20f).toInt().coerceAtLeast(6)
        val total = avatarSizePx + pad * 2
        val oval = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }

        val avatar: View = if (!avatarUrl.isNullOrBlank()) {
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setPadding(dp(2), dp(2), dp(2), dp(2))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.rgb(42, 43, 50))
                    setStroke(dp(1), Color.WHITE)
                }
                clipToOutline = true
                outlineProvider = oval
                Img.load(avatarUrl, this, avatarSizePx)
            }
        } else {
            TextView(context).apply {
                text = (label?.trim()?.firstOrNull() ?: 'K').uppercaseChar().toString()
                gravity = Gravity.CENTER
                textSize = avatarSizePx / 2.6f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(42, 43, 50))
            }
        }
        addView(avatar, LayoutParams(avatarSizePx, avatarSizePx, Gravity.CENTER))

        val frameUrl = Frames.get(frameKey)?.url
        if (!frameUrl.isNullOrBlank()) {
            Crash.mark(context, "frame")
            addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                Img.loadAnimated(frameUrl, this, total)
            }, LayoutParams(total, total, Gravity.CENTER))
        } else {
            val css = Frames.cssOf(frameKey).orEmpty()
            if (css.isNotBlank()) {
                addView(WebView(context).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    settings.javaScriptEnabled = false
                    settings.setSupportZoom(false)
                    isFocusable = false
                    isClickable = false
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    loadDataWithBaseURL(Net.BASE + "/", page(css, avatarUrl, label), "text/html", "UTF-8", null)
                }, LayoutParams(total, total, Gravity.CENTER))
            }
        }
        layoutParams = android.widget.LinearLayout.LayoutParams(total, total)
    }

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

    private fun esc(v: String): String = v
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun page(css: String, avatarUrl: String?, label: String?): String {
        val initial = (label?.trim()?.firstOrNull() ?: 'K').uppercaseChar().toString()
        val inner = if (!avatarUrl.isNullOrBlank()) {
            "<img src=\"${esc(Net.abs(avatarUrl) ?: avatarUrl)}\" alt=\"\">"
        } else {
            "<div class=\"k-frame__ph\">${esc(initial)}</div>"
        }
        return """
            <!doctype html><html><head><meta charset="utf-8"><style>
            *{margin:0;padding:0;box-sizing:border-box}
            html,body{width:100%;height:100%;background:transparent;overflow:hidden}
            .k-frame,.uf{--fw:6px;position:relative;display:inline-grid;place-items:center;width:100%;height:100%;border-radius:50%}
            .k-frame>img,.k-frame>.k-frame__ph,.uf>img,.uf>.k-frame__ph{position:relative;z-index:2;width:calc(100% - var(--fw)*2);height:calc(100% - var(--fw)*2);border-radius:50%;object-fit:cover;background:#2a2b32;display:grid;place-items:center;font-weight:800;color:#fff}
            .k-frame::before,.k-frame::after,.uf::before,.uf::after{content:"";position:absolute;border-radius:50%;pointer-events:none}
            @keyframes kf-spin{to{transform:rotate(360deg)}}
            @keyframes kf-pulse{0%,100%{box-shadow:0 0 6px 1px #ff2d40,inset 0 0 6px 1px #ff2d40}50%{box-shadow:0 0 22px 6px #ff2d40,inset 0 0 12px 3px #ff2d40}}
            @keyframes kf-pulse2{0%,100%{transform:scale(1);opacity:1}50%{transform:scale(1.06);opacity:.75}}
            @keyframes kf-scan{to{background-position:300% 0}}
            @keyframes kf-glitch{0%,86%,100%{transform:translate(0,0)}88%{transform:translate(-3px,1px)}91%{transform:translate(3px,-1px)}94%{transform:translate(-2px,-2px)}97%{transform:translate(2px,2px)}}
            @keyframes kf-glow{0%,100%{opacity:.45}50%{opacity:1}}
            @keyframes kf-flicker{0%,100%{opacity:1}8%{opacity:.35}12%{opacity:1}55%{opacity:.6}60%{opacity:1}85%{opacity:.5}88%{opacity:1}}
            @keyframes kf-twinkle{0%,100%{opacity:.25;transform:rotate(0)}50%{opacity:1;transform:rotate(40deg)}}
            @keyframes kf-beat{0%,40%,100%{transform:scale(1)}10%{transform:scale(1.07)}20%{transform:scale(1.01)}30%{transform:scale(1.09)}}
            @keyframes kf-ripple{0%{transform:scale(1);opacity:.8}100%{transform:scale(1.35);opacity:0}}
            @keyframes bxp{50%{box-shadow:0 0 24px rgba(255,45,64,.95),inset 0 0 14px rgba(255,45,64,.4)}}
            @keyframes bxb{to{background-position:300% 0}}
            @keyframes bxh{to{background-position:0 300%}}
            $css
            </style></head><body><div class="uf $frameKey">$inner</div></body></html>
        """.trimIndent()
    }
}
