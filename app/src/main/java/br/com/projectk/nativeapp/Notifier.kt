package br.com.projectk.nativeapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONArray
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI

/** Verifica /notifications periodicamente (sem FCM) e avisa quando há novidade. */
class Notifier(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        try {
            val prefs = applicationContext.getSharedPreferences("projectk", Context.MODE_PRIVATE)
            val saved = prefs.getString("cookies", null) ?: return Result.success()
            if (CookieHandler.getDefault() == null) CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ALL))
            val store = (CookieHandler.getDefault() as CookieManager).cookieStore
            val arr = JSONArray(saved)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val c = HttpCookie(o.getString("n"), o.getString("v"))
                c.path = o.optString("p", "/"); c.secure = o.optBoolean("s")
                store.add(URI(Net.BASE), c)
            }
            val list = Parse.notifs(Parse.doc(Net.get("/notifications").body))
            if (list.isEmpty()) return Result.success()
            val top = list.first().text
            val last = prefs.getString("last_notif", null)
            prefs.edit().putString("last_notif", top).apply()
            if (last == null || last == top) return Result.success()
            val fresh = list.takeWhile { it.text != last }.take(3)
            show(fresh.joinToString("\n") { it.text }, fresh.size)
        } catch (e: Throwable) {
        }
        return Result.success()
    }

    private fun show(text: String, n: Int) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("k", "Project K", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        val b = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(applicationContext, "k") else android.app.Notification.Builder(applicationContext)
        b.setSmallIcon(R.drawable.ic_bell).setContentTitle(if (n > 1) "Novas notificações" else "Project K")
            .setContentText(text.lines().first()).setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi).setAutoCancel(true)
        nm.notify(1, b.build())
    }
}
