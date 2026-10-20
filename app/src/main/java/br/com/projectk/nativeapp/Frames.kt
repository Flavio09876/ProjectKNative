package br.com.projectk.nativeapp

/** Molduras de avatar que são imagens no CSS do site (.k-frame--chave::after). As demais viram um anel simples. */
object Frames {
    class F(val url: String, val inset: Float)

    private var map: Map<String, F>? = null

    private var colors: Map<String, IntArray> = HashMap()
    fun colorsOf(key: String?): IntArray? {
        if (key == null) return null
        ucolors[key]?.let { return it }
        if (key.startsWith("cx-")) {
            val p = key.removePrefix("cx-").split("-")
            try { return p.map { android.graphics.Color.parseColor("#" + it) }.toIntArray() } catch (e: Exception) {}
        }
        return colors[key]
    }

    fun ensure() {
        if (map != null) return
        val out = HashMap<String, F>()
        try {
            val css = Net.get("/static/css/k-ui.css", "text/css").body
            val rule = Regex("""\.k-frame--([a-z0-9_]+)::after\s*\{([^}]*)\}""")
            val urlRx = Regex("""url\(["']?([^"')]+)["']?\)""")
            val insetRx = Regex("""inset:\s*(-?[0-9.]+)%""")
            val urls = HashMap<String, String>()
            val insets = HashMap<String, Float>()
            for (m in rule.findAll(css)) {
                val key = m.groupValues[1]
                val body = m.groupValues[2]
                urlRx.find(body)?.let { urls[key] = it.groupValues[1] }
                insetRx.find(body)?.let { insets[key] = Math.abs(it.groupValues[1].toFloat()) }
            }
            val cm = HashMap<String, IntArray>()
            val bef = Regex("""\.k-frame--([a-z0-9_]+)::before\s*\{([^}]*)\}""")
            val hex = Regex("""#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})\b""")
            for (m in bef.findAll(css)) {
                val cs = hex.findAll(m.groupValues[2]).mapNotNull {
                    try { android.graphics.Color.parseColor("#" + it.groupValues[1]) } catch (e: Exception) { null }
                }.toList()
                if (cs.isNotEmpty()) cm[m.groupValues[1]] = cs.toIntArray()
            }
            colors = cm
            for ((k, u) in urls) {
                if (u.contains("/static/img/frames/")) out[k] = F(Net.abs(u) ?: u, insets[k] ?: 25f)
            }
        } catch (e: SessionExpired) {
            throw e
        } catch (e: Exception) {
        }
        if (out.isNotEmpty() || colors.isNotEmpty()) map = out
    }

    private val user = java.util.concurrent.ConcurrentHashMap<String, F>()
    private val ucolors = java.util.concurrent.ConcurrentHashMap<String, IntArray>()

    /** Molduras criadas por usuários (.uf-ID) vêm no <style data-sp> de cada página. */
    fun feed(d: org.jsoup.nodes.Document) {
        try {
            val css = d.select("style[data-sp]").joinToString("") { it.data() }
            if (!css.contains("uf-")) return
            val aft = Regex("""\.(uf-\d+)::after\s*\{([^}]*)\}""")
            val bef = Regex("""\.(uf-\d+)::before\s*\{([^}]*)\}""")
            val urlRx = Regex("""url\(["']?([^"')]+)["']?\)""")
            val insetRx = Regex("""inset:\s*(-?[0-9.]+)%""")
            val hex = Regex("""#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})\b""")
            for (m in aft.findAll(css)) {
                val u = urlRx.find(m.groupValues[2])?.groupValues?.get(1) ?: continue
                val ins = insetRx.find(m.groupValues[2])?.groupValues?.get(1)?.toFloatOrNull()
                user[m.groupValues[1]] = F(Net.abs(u) ?: u, if (ins != null) Math.abs(ins) else 20f)
            }
            for (m in bef.findAll(css)) {
                val cs = hex.findAll(m.groupValues[2]).mapNotNull {
                    try { android.graphics.Color.parseColor("#" + it.groupValues[1]) } catch (e: Exception) { null }
                }.toList()
                if (cs.isNotEmpty()) ucolors[m.groupValues[1]] = cs.toIntArray()
            }
        } catch (e: Throwable) {}
    }

    fun get(key: String?): F? = if (key == null) null else (user[key] ?: map?.get(key))
}
