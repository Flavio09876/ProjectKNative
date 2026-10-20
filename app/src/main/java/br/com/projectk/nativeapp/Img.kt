package br.com.projectk.nativeapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.LruCache
import android.widget.ImageView
import com.caverock.androidsvg.SVG
import java.util.concurrent.Executors

object Img {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val pool = Executors.newFixedThreadPool(8)

    fun svgToBitmap(svgText: String, px: Int): Bitmap? {
        return try {
            val svg = SVG.getFromString(svgText)
            var dw = svg.documentWidth
            var dh = svg.documentHeight
            if (dw <= 0f || dh <= 0f) {
                val vb = svg.documentViewBox
                dw = vb?.width() ?: 100f
                dh = vb?.height() ?: 100f
            }
            val w = px
            val h = Math.max(1, (px * (dh / dw)).toInt())
            svg.setDocumentWidth(w.toFloat())
            svg.setDocumentHeight(h.toFloat())
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            bmp
        } catch (e: Throwable) {
            null
        }
    }

    private fun isRaster(d: ByteArray): Boolean {
        if (d.size < 4) return false
        val b0 = d[0].toInt() and 0xFF
        val b1 = d[1].toInt() and 0xFF
        return (b0 == 0x89 && b1 == 0x50) || (b0 == 0xFF && b1 == 0xD8) || (b0 == 0x47 && b1 == 0x49) || (b0 == 0x52 && b1 == 0x49)
    }

    private fun decode(data: ByteArray, maxW: Int): Bitmap? {
        if (!isRaster(data)) {
            val txt = String(data, Charsets.UTF_8)
            if (txt.contains("<svg")) return svgToBitmap(txt, maxW)
        }
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(data, 0, data.size, o)
        var sample = 1
        while (o.outWidth > 0 && o.outWidth / (sample * 2) >= maxW) sample *= 2
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = sample
        return BitmapFactory.decodeByteArray(data, 0, data.size, o2)
    }

    private val pending = HashMap<String, ArrayList<(Bitmap?) -> Unit>>()
    private var dir: java.io.File? = null

    fun init(ctx: android.content.Context) {
        dir = java.io.File(ctx.cacheDir, "img").apply { mkdirs() }
    }

    private fun fileFor(url: String): java.io.File? =
        dir?.let { java.io.File(it, Integer.toHexString(url.hashCode()) + "_" + url.length) }

    private fun fetch(url: String): ByteArray? {
        val f = fileFor(url)
        if (f != null && f.exists() && f.length() > 0) {
            try { return f.readBytes() } catch (e: Throwable) {}
        }
        var delay = 400L
        for (i in 0 until 3) {
            try {
                val b = Net.bytes(url)
                if (b != null && b.isNotEmpty()) {
                    try { f?.writeBytes(b) } catch (e: Throwable) {}
                    return b
                }
            } catch (e: Throwable) {}
            try { Thread.sleep(delay) } catch (e: Throwable) {}
            delay *= 2
        }
        return null
    }

    fun load(url: String?, view: ImageView, maxW: Int = 1080) {
        view.tag = url
        view.setImageDrawable(null)
        if (url == null) return
        val key = url + "@" + maxW
        val hit = cache.get(key)
        if (hit != null) {
            view.setImageBitmap(hit)
            return
        }
        val cb: (Bitmap?) -> Unit = { bmp ->
            view.post {
                if (view.tag == url && bmp != null) {
                    view.alpha = 0f
                    view.setImageBitmap(bmp)
                    view.animate().alpha(1f).setDuration(180).start()
                }
            }
        }
        synchronized(pending) {
            val l = pending[key]
            if (l != null) { l.add(cb); return }
            pending[key] = arrayListOf(cb)
        }
        pool.execute {
            val bmp = try {
                val b = fetch(url)
                if (b == null) null else decode(b, maxW)
            } catch (e: Throwable) {
                null
            }
            if (bmp != null) cache.put(key, bmp)
            val ls = synchronized(pending) { pending.remove(key) }
            ls?.forEach { it(bmp) }
        }
    }

    fun loadSvg(svgText: String, view: ImageView, px: Int) {
        val key = "svg:" + svgText.hashCode() + "@" + px
        view.tag = key
        val hit = cache.get(key)
        if (hit != null) {
            view.setImageBitmap(hit)
            return
        }
        pool.execute {
            val bmp = svgToBitmap(svgText, px)
            if (bmp != null) {
                cache.put(key, bmp)
                view.post { if (view.tag == key) view.setImageBitmap(bmp) }
            }
        }
    }
}
