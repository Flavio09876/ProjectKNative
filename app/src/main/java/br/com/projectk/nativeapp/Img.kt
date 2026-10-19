package br.com.projectk.nativeapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.util.concurrent.Executors

object Img {
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val pool = Executors.newFixedThreadPool(4)

    private fun decode(data: ByteArray, maxW: Int): Bitmap? {
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
        val hit = cache.get(url)
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
                cache.put(url, bmp)
                view.post { if (view.tag == url) view.setImageBitmap(bmp) }
            }
        }
    }
}
