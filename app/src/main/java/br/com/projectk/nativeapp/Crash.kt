package br.com.projectk.nativeapp

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.provider.MediaStore
import android.widget.TextView
import java.io.PrintWriter
import java.io.StringWriter

/** Diagnóstico de última linha: grava o erro em vários lugares e mostra na tela. */
object Crash {
    fun dump(e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    fun save(ctx: Context, text: String) {
        val msg = "Project K crash\n$text"
        try { android.util.Log.e("ProjectKCrash", msg) } catch (e: Throwable) {}

        val places = ArrayList<java.io.File>()
        try { places.add(java.io.File(ctx.cacheDir, "projectk_crash.txt")) } catch (e: Throwable) {}
        try {
            places.add(java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS), "projectk_crash.txt"))
        } catch (e: Throwable) {}
        for (f in places) {
            try { f.parentFile?.mkdirs(); f.writeText(msg) } catch (e: Throwable) {}
        }
        try {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "projectk_crash.txt")
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
            ctx.contentResolver.openOutputStream(uri ?: return)?.use {
                it.write(msg.toByteArray(Charsets.UTF_8))
            }
        } catch (e: Throwable) {}
    }

    /** Sinal de vida: grava quando uma tela chegou ao fim sem exceção. */
    fun mark(ctx: Context, tag: String) {
        val text = "ok ${System.currentTimeMillis()} $tag\n"
        try { android.util.Log.i("ProjectK", text.trim()) } catch (e: Throwable) {}
        try {
            java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS), "projectk_$tag.txt"
            ).writeText(text)
        } catch (e: Throwable) {}
        try {
            java.io.File(ctx.cacheDir, "projectk_$tag.txt").writeText(text)
        } catch (e: Throwable) {}
    }

    fun install(ctx: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            save(ctx, "thread=${t.name}\n" + dump(e))
            try { previous?.uncaughtException(t, e) } catch (e2: Throwable) {}
        }
    }

    fun screen(activity: Activity, e: Throwable, stage: String) {
        val msg = "etapa=$stage\n" + dump(e)
        save(activity, msg)
        try {
            val tv = TextView(activity).apply {
                text = "ERRO AO ABRIR ($stage)\n\n$msg"
                textSize = 11f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(34, 8, 14))
                setPadding(28, 36, 28, 36)
            }
            activity.setContentView(tv)
        } catch (e2: Throwable) {
            save(activity, "falha ao exibir erro:\n" + dump(e2))
        }
    }
}
