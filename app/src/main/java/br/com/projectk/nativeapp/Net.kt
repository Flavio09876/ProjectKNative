package br.com.projectk.nativeapp

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SessionExpired : Exception("Sessão expirada. Entre novamente.")

data class Resp(val code: Int, val url: String, val body: String) {
    val path: String
        get() = try { URL(url).path.trimEnd('/') } catch (e: Exception) { "" }
}

class Upload(val field: String, val name: String, val mime: String, val bytes: ByteArray)

object Net {
    const val BASE = "https://projectspacek.squareweb.app"
    private const val UA = "ProjectK-Android/0.3"

    fun abs(u: String?): String? {
        if (u.isNullOrBlank() || u == "null") return null
        return when {
            u.startsWith("http") -> u
            u.startsWith("//") -> "https:$u"
            u.startsWith("/") -> BASE + u
            else -> "$BASE/$u"
        }
    }

    fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

    private fun open(path: String, method: String, accept: String): HttpURLConnection {
        val full = if (path.startsWith("http")) path else BASE + path
        val conn = URL(full).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 25000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", accept)
        conn.setRequestProperty("User-Agent", UA)
        return conn
    }

    private fun finish(conn: HttpURLConnection): Resp {
        try {
            val code = conn.responseCode
            val stream = if (code < 400) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val r = Resp(code, conn.url.toString(), body)
            if (r.path.endsWith("/login")) throw SessionExpired()
            return r
        } finally {
            conn.disconnect()
        }
    }

    fun get(path: String, accept: String = "text/html"): Resp {
        val r = finish(open(path, "GET", accept))
        if (r.code >= 400) throw Exception("HTTP ${r.code}")
        return r
    }

    fun postForm(path: String, fields: List<Pair<String, String>>, accept: String = "application/json"): Resp {
        val conn = open(path, "POST", accept)
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        val data = fields.joinToString("&") { enc(it.first) + "=" + enc(it.second) }
        conn.outputStream.use { it.write(data.toByteArray(Charsets.UTF_8)) }
        return finish(conn)
    }

    fun postMultipart(path: String, fields: List<Pair<String, String>>, file: Upload?, accept: String = "text/html"): Resp {
        val boundary = "----KNative" + System.currentTimeMillis()
        val conn = open(path, "POST", accept)
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        val bos = ByteArrayOutputStream()
        fun w(s: String) { bos.write(s.toByteArray(Charsets.UTF_8)) }
        for (f in fields) {
            w("--$boundary\r\nContent-Disposition: form-data; name=\"${f.first}\"\r\n\r\n${f.second}\r\n")
        }
        if (file != null) {
            w("--$boundary\r\nContent-Disposition: form-data; name=\"${file.field}\"; filename=\"${file.name}\"\r\nContent-Type: ${file.mime}\r\n\r\n")
            bos.write(file.bytes)
            w("\r\n")
        }
        w("--$boundary--\r\n")
        val data = bos.toByteArray()
        conn.outputStream.use { it.write(data) }
        return finish(conn)
    }

    fun bytes(url: String): ByteArray? {
        val conn = open(url, "GET", "image/*,*/*")
        try {
            if (conn.responseCode >= 400) return null
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            conn.inputStream.use { input ->
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > 15_000_000) return null
                }
            }
            return out.toByteArray()
        } finally {
            conn.disconnect()
        }
    }
}
