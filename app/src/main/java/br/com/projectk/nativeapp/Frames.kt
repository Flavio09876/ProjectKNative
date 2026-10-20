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

    // ---- Renderização fiel: o CSS real do site num WebView fora da tela, capturado como imagem ----
    private var act: android.app.Activity? = null
    private val custom = LinkedHashSet<String>()
    private val snaps = android.util.LruCache<String, android.graphics.Bitmap>(12 * 1024 * 1024)
    private val waiting = HashMap<String, ArrayList<(android.graphics.Bitmap?) -> Unit>>()
    private val queue = java.util.ArrayDeque<Triple<String, Int, String>>()
    private var running = false
    private val h = android.os.Handler(android.os.Looper.getMainLooper())

    fun init(a: android.app.Activity) { act = a }

    fun addCss(d: org.jsoup.nodes.Document) {
        try {
            val css = d.select("style[data-sp]").joinToString("") { it.data() }
            if (css.isNotBlank()) synchronized(custom) { if (custom.size < 40) custom.add(css) }
        } catch (e: Throwable) {}
    }

    /** px do bitmap de captura em relação ao avatar (cobre brilhos e enfeites que passam da borda). */
    const val SCALE = 1.9f

    fun snapshot(classes: String, sizeDp: Int, cb: (android.graphics.Bitmap?) -> Unit) {
        val key = classes + "@" + sizeDp
        snaps.get(key)?.let { cb(it); return }
        h.post {
            val l = waiting[key]
            if (l != null) { l.add(cb); return@post }
            waiting[key] = arrayListOf(cb)
            queue.add(Triple(key, sizeDp, classes))
            pump()
        }
    }

    private fun finish(key: String, bmp: android.graphics.Bitmap?) {
        if (bmp != null) snaps.put(key, bmp)
        val ls = waiting.remove(key)
        ls?.forEach { it(bmp) }
        running = false
        pump()
    }

    private fun pump() {
        if (running) return
        val t = queue.poll() ?: return
        val a = act
        if (a == null) { running = true; finish(t.first, null); return }
        running = true
        val (key, sizeDp, classes) = t
        val dens = a.resources.displayMetrics.density
        val side = (sizeDp * SCALE * dens).toInt()
        try {
            val wv = android.webkit.WebView(a)
            wv.setBackgroundColor(0)
            wv.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            wv.settings.javaScriptEnabled = false
            val host = a.window.decorView as android.view.ViewGroup
            wv.layoutParams = android.widget.FrameLayout.LayoutParams(side, side)
            host.addView(wv, 0)
            var done = false
            val end = { bmp: android.graphics.Bitmap? ->
                if (!done) {
                    done = true
                    try { host.removeView(wv); wv.destroy() } catch (e: Throwable) {}
                    finish(key, bmp)
                }
            }
            h.postDelayed({ end(null) }, 9000)
            wv.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(v: android.webkit.WebView, url: String?) {
                    h.postDelayed({
                        if (done) return@postDelayed
                        try {
                            val bmp = android.graphics.Bitmap.createBitmap(side, side, android.graphics.Bitmap.Config.ARGB_8888)
                            wv.draw(android.graphics.Canvas(bmp))
                            val c = android.graphics.Canvas(bmp)
                            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                            p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                            c.drawCircle(side / 2f, side / 2f, sizeDp * dens / 2f - 1f, p)
                            var any = false
                            val st = Math.max(1, side / 24)
                            var y = 0
                            while (y < side && !any) { var x = 0; while (x < side) { if ((bmp.getPixel(x, y) ushr 24) > 8) { any = true; break }; x += st }; y += st }
                            end(if (any) bmp else null)
                        } catch (e: Throwable) { end(null) }
                    }, 450)
                }
            }
            val fw = Math.max(3f, sizeDp * 0.0625f)
            val css = synchronized(custom) { custom.joinToString("") }
            val html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<link rel='stylesheet' href='/static/css/k-ui.css'><style>" + css +
                "html,body{margin:0;height:100%;background:transparent!important;overflow:hidden}" +
                "body{display:grid;place-items:center}</style></head><body>" +
                "<span class='" + classes.replace("'", "") + "' style='--sz:" + sizeDp + "px;--fw:" + fw + "px'><span class='k-frame__ph'></span></span></body></html>"
            wv.loadDataWithBaseURL(Net.BASE + "/", html, "text/html", "utf-8", null)
        } catch (e: Throwable) {
            finish(key, null)
        }
    }
}
