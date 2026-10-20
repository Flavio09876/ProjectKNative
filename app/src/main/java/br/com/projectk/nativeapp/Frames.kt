package br.com.projectk.nativeapp

/** Molduras de avatar que são imagens no CSS do site (.k-frame--chave::after). As demais viram um anel simples. */
object Frames {
    class F(val url: String, val inset: Float)

    private var map: Map<String, F>? = null

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
            for ((k, u) in urls) {
                if (u.contains("/static/img/frames/")) out[k] = F(Net.abs(u) ?: u, insets[k] ?: 25f)
            }
        } catch (e: SessionExpired) {
            throw e
        } catch (e: Exception) {
        }
        map = out
    }

    fun get(key: String?): F? = if (key == null) null else map?.get(key)
}
