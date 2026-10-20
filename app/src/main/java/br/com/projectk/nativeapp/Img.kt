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
    private val pool = Executors.newFixedThreadPool(4)

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

    private fun decode(data: ByteArray, maxW: Int): Bitmap? {
        val head = String(data, 0, Math.min(200, data.size), Charsets.UTF_8)
        if (head.contains("<svg") || head.contains("<?xml")) {
            return svgToBitmap(String(data, Charsets.UTF_8), maxW)
        }
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(data, 0, data.size, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= maxW) sample *= 2
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = sample
        return BitmapFactory.decodeByteArray(data, 0, data.size, o2)
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
        pool.execute {
            val bmp = try {
                val b = Net.bytes(url)
                if (b == null) null else decode(b, maxW)
            } catch (e: Throwable) {
                null
            }
            if (bmp != null) {
                cache.put(key, bmp)
                view.post { if (view.tag == url) view.setImageBitmap(bmp) }
            }
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
