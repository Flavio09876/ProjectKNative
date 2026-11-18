package br.com.projectk.nativeapp

import android.graphics.Color
import org.jsoup.nodes.Document
import java.util.concurrent.ConcurrentHashMap

/**
 * Leitura das molduras do site.
 *
 * Na “leva 1” este arquivo renderizava o CSS num WebView fora da tela e
 * fotografava o resultado — por isso as molduras viravam PNG estático.
 * Agora ele apenas extrai as cores/definições do CSS; o desenho animado
 * acontece em [AnimatedFrameView], 100% Canvas, sem WebView de tela.
 */
object Frames {
    class F(val url: String, val inset: Float)

    private var map: Map<String, F>? = null
    private var colors: Map<String, IntArray> = HashMap()

    private val user = ConcurrentHashMap<String, F>()
    private val ucolors = ConcurrentHashMap<String, IntArray>()
    private val custom = java.util.Collections.synchronizedSet(LinkedHashSet<String>())

    private val hexRx = Regex("""#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})\b""")
    private val urlRx = Regex("""url\(["']?([^"')]+)["']?\)""")
    private val insetRx = Regex("""inset:\s*(-?[0-9.]+)%""")

    private fun hexColors(css: String): IntArray = hexRx.findAll(css).mapNotNull {
        try { Color.parseColor("#" + it.groupValues[1]) } catch (e: Exception) { null }
    }.toList().distinct().toIntArray()

    fun colorsOf(key: String?): IntArray? {
        if (key == null) return null
        ucolors[key]?.let { if (it.isNotEmpty()) return it }
        if (key.startsWith("cx-")) {
            val p = key.removePrefix("cx-").split("-")
            try {
                val cs = p.map { Color.parseColor("#" + it) }.toIntArray()
                if (cs.isNotEmpty()) return cs
            } catch (e: Exception) {}
        }
        return colors[key]?.takeIf { it.isNotEmpty() }
    }

    /** Lê as regras .k-frame--* do CSS do site para alimentar as cores nativas. */
    fun ensure() {
        if (map != null) return
        try {
            val css = Net.get("/static/css/k-ui.css", "text/css").body
            val urls = HashMap<String, String>()
            val insets = HashMap<String, Float>()
            val palette = HashMap<String, IntArray>()

            for (m in Regex("""\.k-frame--([a-z0-9_]+)::after\s*\{([^}]*)\}""").findAll(css)) {
                val key = m.groupValues[1]
                urlRx.find(m.groupValues[2])?.let { urls[key] = it.groupValues[1] }
                insetRx.find(m.groupValues[2])?.let { insets[key] = kotlin.math.abs(it.groupValues[1].toFloat()) }
                val cs = hexColors(m.groupValues[2])
                if (cs.isNotEmpty()) palette[key] = cs
            }
            for (m in Regex("""\.k-frame--([a-z0-9_]+)::before\s*\{([^}]*)\}""").findAll(css)) {
                val cs = hexColors(m.groupValues[2])
                if (cs.isNotEmpty()) palette[m.groupValues[1]] = cs
            }

            colors = palette
            val out = HashMap<String, F>()
            for ((k, u) in urls) {
                if (u.contains("/static/img/frames/")) out[k] = F(Net.abs(u) ?: u, insets[k] ?: 25f)
            }
            map = out
        } catch (e: SessionExpired) {
            throw e
        } catch (e: Exception) {
            if (map == null) map = emptyMap()
        }
    }

    /** Molduras de usuário (.uf-ID) e estilos próprios vêm do <style data-sp> de cada página. */
    fun feed(d: Document) {
        try {
            val css = d.select("style[data-sp]").joinToString("") { it.data() }
            if (!css.contains("uf-")) return
            feedCss(css)
        } catch (e: Throwable) {}
    }

    fun feedRaw(html: String) {
        try {
            if (!html.contains("uf-")) return
            feedCss(html)
        } catch (e: Throwable) {}
    }

    private fun feedCss(css: String) {
        for (m in Regex("""\.(uf-\d+)::after\s*\{([^}]*)\}""").findAll(css)) {
            val key = m.groupValues[1]
            val u = urlRx.find(m.groupValues[2])?.groupValues?.get(1)
            if (u != null) {
                val ins = insetRx.find(m.groupValues[2])?.groupValues?.get(1)?.toFloatOrNull()
                user[key] = F(Net.abs(u) ?: u, if (ins != null) kotlin.math.abs(ins) else 20f)
            }
            val cs = hexColors(m.groupValues[2])
            if (cs.isNotEmpty()) ucolors[key] = cs
        }
        for (m in Regex("""\.(uf-\d+)::before\s*\{([^}]*)\}""").findAll(css)) {
            val cs = hexColors(m.groupValues[2])
            if (cs.isNotEmpty()) {
                val old = ucolors[m.groupValues[1]] ?: IntArray(0)
                ucolors[m.groupValues[1]] = (old.toList() + cs.toList()).distinct().toIntArray()
            }
        }
        for (m in Regex("""\.(cx-[0-9a-fA-F-]+)[^{]*\{([^}]*)\}""").findAll(css)) {
            val cs = hexColors(m.groupValues[2])
            if (cs.isNotEmpty()) ucolors[m.groupValues[1]] = cs
        }
    }

    /** Mantém os estilos próprios das páginas para extrair cores — nunca para screenshot. */
    fun addCss(d: Document) {
        try {
            val css = d.select("style[data-sp]").joinToString("") { it.data() }
            if (css.isNotBlank()) synchronized(custom) {
                if (custom.size < 60) custom.add(css)
            }
            feedCss(css)
        } catch (e: Throwable) {}
    }

    fun get(key: String?): F? = if (key == null) null else (user[key] ?: map?.get(key))
}
