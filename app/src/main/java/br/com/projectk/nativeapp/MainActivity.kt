package br.com.projectk.nativeapp

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val baseUrl = Net.BASE
    private val red = Color.rgb(220, 35, 55)
    private val bg = Color.rgb(18, 18, 22)
    private val panel = Color.rgb(29, 29, 35)

    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var loginInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private var busy = false

    private val ink = Color.rgb(7, 5, 10)
    private val glass = Color.rgb(18, 10, 13)
    private val muted = Color.rgb(169, 154, 157)
    private val violet = Color.rgb(225, 29, 46)
    private val errorColor = Color.rgb(255, 120, 130)

    private val handler = Handler(Looper.getMainLooper())
    private var token = 0
    private var activeTab = "Início"
    private lateinit var content: LinearLayout
    private lateinit var scrollRef: ScrollView
    private var meUser: String? = null
    private var pollTask: Runnable? = null
    private var meAvatar: String? = null
    private var unreadDot = false
    private var isAdminUser = false

    private var pickedUri: Uri? = null
    private var pickedLabel: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Img.init(applicationContext)
        Frames.init(this)
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        CookieHandler.setDefault(
            CookieManager(null, CookiePolicy.ACCEPT_ALL)
        )

        if (restoreCookies()) {
            val splash = TextView(this).apply {
                text = "Entrando…"
                textSize = 16f
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                setBackgroundColor(bg)
            }
            setContentView(splash)
            thread {
                val ok = try {
                    Net.get("/home")
                    true
                } catch (e: Exception) {
                    false
                }
                runOnUiThread {
                    if (ok) showHome("") else showLogin()
                }
            }
        } else {
            showLogin()
        }
    }

    override fun onDestroy() {
        stopPoll()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (backAction != null) {
            val b = backAction
            b?.invoke()
        } else if (activeTab != "Início" && ::content.isInitialized) {
            showFeed()
        } else {
            super.onBackPressed()
        }
    }

    // ---------- sessão (cookies) ----------
    private fun prefs() = getSharedPreferences("projectk", MODE_PRIVATE)

    private fun saveCookies() {
        try {
            val store = (CookieHandler.getDefault() as CookieManager).cookieStore
            val arr = JSONArray()
            for (c in store.cookies) {
                arr.put(JSONObject().put("n", c.name).put("v", c.value).put("p", c.path ?: "/").put("s", c.secure))
            }
            prefs().edit().putString("cookies", arr.toString()).apply()
        } catch (e: Exception) {
        }
    }

    private fun restoreCookies(): Boolean {
        return try {
            val s = prefs().getString("cookies", null) ?: return false
            val arr = JSONArray(s)
            val store = (CookieHandler.getDefault() as CookieManager).cookieStore
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val c = HttpCookie(o.getString("n"), o.getString("v"))
                c.path = o.optString("p", "/")
                c.secure = o.optBoolean("s")
                store.add(URI(Net.BASE), c)
            }
            arr.length() > 0
        } catch (e: Exception) {
            false
        }
    }

    private fun logout() {
        try {
            (CookieHandler.getDefault() as? CookieManager)?.cookieStore?.removeAll()
        } catch (e: Exception) {
        }
        prefs().edit().remove("cookies").apply()
        meUser = null
        stopPoll()
        showLogin()
    }

    private fun sessionLost() {
        Toast.makeText(this, "Sessão expirada. Entre novamente.", Toast.LENGTH_LONG).show()
        logout()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun params() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun label(text: String, size: Float = 14f): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.WHITE)
        }

    private fun makeRoot(): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(36), dp(24), dp(28))
            setBackgroundColor(bg)
        }

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.addView(root)
        setContentView(scroll)
        return root
    }

    private fun addSpace(height: Int) {
        root.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun makeInput(hintText: String, password: Boolean = false): EditText {
        return EditText(this).apply {
            hint = hintText
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(145, 145, 155))
            setSingleLine(true)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(panel)
            if (password) {
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                inputType = android.text.InputType.TYPE_CLASS_TEXT
            }
            root.addView(this, params().apply {
                bottomMargin = dp(14)
            })
        }
    }

    private fun makeButton(text: String, action: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(red)
            )
            setOnClickListener { action() }
        }
    }

    private fun showLogin() {
        makeRoot()

        addSpace(30)
        val tile = TextView(this)
        tile.text = "K"
        tile.textSize = 40f
        tile.gravity = Gravity.CENTER
        tile.setTextColor(Color.WHITE)
        tile.typeface = android.graphics.Typeface.DEFAULT_BOLD
        tile.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(225, 29, 46), Color.rgb(120, 10, 25))).apply { cornerRadius = dp(22).toFloat() }
        root.addView(tile, LinearLayout.LayoutParams(dp(84), dp(84)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        addSpace(16)
        root.addView(label("Project K", 26f).apply {
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, params())
        root.addView(label("Entre para continuar", 14f).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(169, 154, 157))
        }, params())

        addSpace(28)

        root.addView(label("Usuário ou e-mail", 14f), params())
        addSpace(8)
        loginInput = makeInput("Digite seu usuário ou e-mail")

        root.addView(label("Senha", 14f), params())
        addSpace(8)
        passwordInput = makeInput("Digite sua senha", true)

        addSpace(8)
        loginButton = makeButton("Entrar") { login() }
        root.addView(loginButton, params())

        addSpace(16)
        status = label("", 13f).apply {
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        root.addView(status, params())

    }

    private fun login() {
        if (busy) return

        val username = loginInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (username.isBlank() || password.isBlank()) {
            Toast.makeText(this, "Preencha usuário e senha.", Toast.LENGTH_SHORT).show()
            return
        }

        busy = true
        loginButton.isEnabled = false
        status.text = "Autenticando..."
        status.setTextColor(Color.LTGRAY)

        thread {
            var message = "Não foi possível confirmar o login."
            var success = false

            try {
                val form = "login=${enc(username)}&password=${enc(password)}"
                val conn = (URL("$baseUrl/login").openConnection()
                    as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 15000
                    instanceFollowRedirects = true
                    doOutput = true
                    setRequestProperty(
                        "Content-Type",
                        "application/x-www-form-urlencoded; charset=UTF-8"
                    )
                    setRequestProperty("Accept", "text/html")
                    setRequestProperty("User-Agent", "ProjectK-Android/0.1")
                }

                try {
                    conn.outputStream.use {
                        it.write(form.toByteArray(Charsets.UTF_8))
                    }

                    val code = conn.responseCode
                    val finalUrl = conn.url.toString()
                    val path = URL(finalUrl).path
                    val stream = if (code < 400) conn.inputStream
                        else conn.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

                    when {
                        code in 200..399 && path.trimEnd('/').endsWith("/home") -> {
                            success = true
                            message = "Login confirmado pelo redirecionamento do servidor."
                        }
                        path.trimEnd('/').endsWith("/login") -> {
                            message = "O servidor permaneceu na tela de login. Confira os dados ou verifique se a conta exige alguma etapa adicional."
                        }
                        path.contains("suspended") -> {
                            message = "A conta foi direcionada para a página de suspensão."
                        }
                        code >= 400 -> {
                            message = "O servidor retornou HTTP $code."
                        }
                        else -> {
                            message = "Resposta recebida, mas o login não pôde ser confirmado.\nHTTP $code\nDestino: $path"
                        }
                    }

                    if (!success && body.contains("bloquead", ignoreCase = true)) {
                        message = "O servidor indicou que a conta pode estar bloqueada."
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                message = "Falha de conexão: ${e.javaClass.simpleName}: ${e.message ?: "erro desconhecido"}"
            }

            runOnUiThread {
                busy = false
                if (success) {
                    showHome(message)
                } else {
                    status.text = message
                    status.setTextColor(red)
                    loginButton.isEnabled = true
                }
            }
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    // ---------- utilidades de interface ----------
    private var backAction: (() -> Unit)? = null

    private fun rounded(color: Int, radius: Float = 22f): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius * resources.displayMetrics.density
            setStroke(dp(1), Color.argb(80, 225, 29, 46))
        }
    }

    private fun text(value: String, size: Float = 14f, color: Int = Color.WHITE): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            setPadding(dp(2), dp(3), dp(2), dp(3))
        }
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(13), dp(15), dp(13))
            background = rounded(glass)
            layoutParams = params().apply { bottomMargin = dp(11) }
        }
    }

    private fun addLine(parent: LinearLayout, value: String, size: Float = 14f, color: Int = Color.WHITE) {
        parent.addView(text(value, size, color), params().apply { bottomMargin = dp(5) })
    }

    private fun pill(value: String, action: () -> Unit): TextView {
        return text(value, 13f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(Color.rgb(42, 12, 17), 18f)
            setOnClickListener { action() }
        }
    }

    private fun toast(m: String) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    }

    private fun fieldBox(hintText: String, multi: Boolean = false): EditText {
        return EditText(this).apply {
            hint = hintText
            setHintTextColor(muted)
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(glass, 16f)
            if (multi) {
                minLines = 4
                gravity = Gravity.TOP
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            } else {
                setSingleLine(true)
                inputType = android.text.InputType.TYPE_CLASS_TEXT
            }
        }
    }

    private class Skel(c: android.content.Context) : LinearLayout(c) {
        var text: CharSequence = ""
            set(v) {
                field = v
                removeAllViews()
                alpha = 1f
                val t = TextView(context)
                t.text = v
                t.textSize = 14f
                t.setTextColor(Color.rgb(255, 120, 120))
                addView(t)
            }
        fun setTextColor(col: Int) {}
    }

    private fun skeleton(): Skel {
        val box = Skel(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(4), dp(8), dp(4), dp(8))
        for (i in 0 until 4) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.setPadding(0, dp(10), 0, dp(10))
            val av = View(this)
            av.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(34, 16, 20)) }
            r.addView(av, LinearLayout.LayoutParams(dp(40), dp(40)))
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            for (w in intArrayOf(140, 260, 200)) {
                val bar = View(this)
                bar.background = GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setColor(Color.rgb(34, 16, 20)) }
                col.addView(bar, LinearLayout.LayoutParams(dp(w), dp(10)).apply { topMargin = dp(7) })
            }
            r.addView(col, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
            box.addView(r)
        }
        val a = android.animation.ObjectAnimator.ofFloat(box, "alpha", 0.35f, 1f)
        a.duration = 750
        a.repeatMode = android.animation.ValueAnimator.REVERSE
        a.repeatCount = android.animation.ValueAnimator.INFINITE
        a.start()
        box.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { a.cancel() }
        })
        return box
    }

    private fun avatarView(url: String?, name: String, sizeDp: Int, frame: String? = null): View {
        val round = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        val base: View
        if (url != null) {
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setBackgroundColor(Color.rgb(42, 12, 17))
            Img.load(url, iv, 256)
            base = iv
        } else {
            val tv = TextView(this)
            tv.text = (name.trim().firstOrNull() ?: '?').uppercaseChar().toString()
            tv.gravity = Gravity.CENTER
            tv.textSize = (sizeDp / 2.4f)
            tv.setTextColor(Color.WHITE)
            tv.setBackgroundColor(Color.rgb(90, 14, 26))
            base = tv
        }
        base.outlineProvider = round
        base.clipToOutline = true
        if (frame == null) {
            base.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
            return base
        }
        val fw = Math.max(3, (sizeDp * 0.0625f).toInt())
        val ukey = Regex("uf-\\d+").find(frame)?.value
        val uf = Frames.get(ukey)
        if (uf != null) {
            val ovd = (sizeDp * (1f + 2f * uf.inset / 100f)).toInt()
            val bx = FrameLayout(this)
            bx.clipChildren = false
            bx.addView(base, FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), Gravity.CENTER))
            val oi = ImageView(this)
            oi.scaleType = ImageView.ScaleType.FIT_CENTER
            Img.load(uf.url, oi, 512)
            bx.addView(oi, FrameLayout.LayoutParams(dp(ovd), dp(ovd), Gravity.CENTER))
            bx.layoutParams = LinearLayout.LayoutParams(dp(ovd), dp(ovd))
            return bx
        }
        val tot = sizeDp + fw * 2
        val box = FrameLayout(this)
        box.clipChildren = false
        box.clipToPadding = false
        box.addView(base, FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), Gravity.CENTER))
        val ov = (sizeDp * Frames.SCALE).toInt()
        val fi = ImageView(this)
        fi.scaleType = ImageView.ScaleType.FIT_XY
        box.addView(fi, FrameLayout.LayoutParams(dp(ov), dp(ov), Gravity.CENTER))
        Frames.snapshot(frame, sizeDp) { bmp ->
            if (bmp != null) { fi.alpha = 0f; fi.setImageBitmap(bmp); fi.animate().alpha(1f).setDuration(200).start() }
            else {
                val cs = Frames.colorsOf(Regex("uf-\\d+|cx-[0-9a-fA-F]+-[0-9a-fA-F]+|k-frame--([a-z0-9]+)").findAll(frame).map { it.groupValues[1].ifEmpty { it.value } }.lastOrNull())
                    ?: intArrayOf(violet, Color.rgb(255, 154, 31), violet)
                val g = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke(dp(fw), cs[0]) }
                val rv = View(this); rv.background = g
                box.addView(rv, FrameLayout.LayoutParams(dp(tot), dp(tot), Gravity.CENTER))
            }
        }
        box.layoutParams = LinearLayout.LayoutParams(dp(tot), dp(tot))
        return box
    }

    private fun imageBlock(url: String): ImageView {
        val iv = ImageView(this)
        iv.adjustViewBounds = true
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.maxHeight = dp(420)
        iv.layoutParams = params().apply { topMargin = dp(8); bottomMargin = dp(4) }
        Img.load(url, iv, 1080)
        return iv
    }

    private fun stopPoll() {
        pollTask?.let { handler.removeCallbacks(it) }
        pollTask = null
    }

    private fun startPoll(everyMs: Long, fn: () -> Unit) {
        stopPoll()
        val sid = token
        val t = object : Runnable {
            override fun run() {
                if (sid != token) return
                fn()
                handler.postDelayed(this, everyMs)
            }
        }
        pollTask = t
        handler.postDelayed(t, everyMs)
    }

    /** Executa [work] fora da tela principal e entrega o resultado só se a tela ainda for a mesma. */
    private fun <T> async(work: () -> T, done: (T) -> Unit, fail: ((String) -> Unit)? = null) {
        val sid = token
        thread {
            try {
                val r = work()
                runOnUiThread { if (sid == token) done(r) }
            } catch (e: SessionExpired) {
                runOnUiThread { sessionLost() }
            } catch (e: Exception) {
                val m = e.message ?: e.javaClass.simpleName
                runOnUiThread {
                    if (sid == token) {
                        if (fail != null) fail(m) else addLine(content, "Falha: $m", 13f, errorColor)
                    }
                }
            }
        }
    }

    private fun icon(res: Int, color: Int, sizeDp: Int): ImageView {
        val iv = ImageView(this)
        iv.setImageResource(res)
        iv.setColorFilter(color)
        iv.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        return iv
    }

    private fun seal(): ImageView {
        val iv = ImageView(this)
        iv.setImageResource(R.drawable.ic_seal)
        iv.layoutParams = LinearLayout.LayoutParams(dp(17), dp(17)).apply { leftMargin = dp(6) }
        return iv
    }

    private fun nameRow(name: String, verified: Boolean, role: String, size: Float = 15f): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val n = text(name, size)
        n.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        n.maxLines = 1
        n.ellipsize = TextUtils.TruncateAt.END
        row.addView(n, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (verified) row.addView(seal())
        if (role == "admin" || role == "mod") {
            val ri = icon(if (role == "admin") R.drawable.ic_crown else R.drawable.ic_shield, violet, 16)
            (ri.layoutParams as LinearLayout.LayoutParams).leftMargin = dp(5)
            row.addView(ri)
        }
        return row
    }

    private fun screen(
        tab: String, title: String, back: (() -> Unit)? = null,
        action: View? = null, footer: View? = null
    ): LinearLayout {
        token++
        stopPoll()
        backAction = back
        activeTab = tab
        window.statusBarColor = ink
        window.navigationBarColor = ink
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(ink)
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(16), dp(12), dp(16), dp(8))
        if (back != null) {
            val b = text("‹", 30f, violet)
            b.setPadding(dp(4), 0, dp(14), dp(4))
            b.setOnClickListener { back() }
            top.addView(b)
        } else {
            val k = TextView(this)
            k.text = "K"
            k.textSize = 20f
            k.typeface = Typeface.DEFAULT_BOLD
            k.setTextColor(Color.WHITE)
            k.gravity = Gravity.CENTER
            k.background = rounded(Color.rgb(60, 12, 20), 10f)
            top.addView(k, LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(12) })
        }
        val t = text(title, 21f)
        t.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        t.maxLines = 1
        t.ellipsize = TextUtils.TruncateAt.END
        top.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (action != null) top.addView(action)
        if (back == null) {
            val bell = FrameLayout(this)
            bell.addView(icon(R.drawable.ic_bell, Color.WHITE, 24), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
            if (unreadDot) {
                val dot = View(this)
                val dg = GradientDrawable()
                dg.shape = GradientDrawable.OVAL
                dg.setColor(violet)
                dot.background = dg
                bell.addView(dot, FrameLayout.LayoutParams(dp(9), dp(9), Gravity.TOP or Gravity.END).apply { topMargin = dp(5); rightMargin = dp(5) })
            }
            bell.setOnClickListener { showNotifs() }
            top.addView(bell, LinearLayout.LayoutParams(dp(42), dp(42)))
            val av = avatarView(meAvatar, meUser ?: "K", 34)
            av.setOnClickListener { showProfile(null) }
            top.addView(av)
        }
        page.addView(top)

        val scroll = ScrollView(this)
        scrollRef = scroll
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(16), dp(4), dp(16), dp(12))
        scroll.addView(c)
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (footer != null) page.addView(footer, params())
        page.addView(navBar(tab), params().apply { setMargins(dp(12), dp(4), dp(12), dp(8)) })
        setContentView(page)
        content = c
        return c
    }

    private fun navBar(tab: String): LinearLayout {
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = Gravity.CENTER
        nav.setPadding(dp(4), dp(8), dp(4), dp(8))
        nav.background = rounded(Color.rgb(14, 8, 11), 22f)
        fun slot(name: String, res: Int) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER
            val color = if (name == tab) violet else muted
            col.addView(icon(res, color, 24))
            val l = text(name, 10f, color)
            l.gravity = Gravity.CENTER
            col.addView(l)
            col.setOnClickListener { openTab(name) }
            nav.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        slot("Início", R.drawable.ic_home)
        slot("Salas", R.drawable.ic_people)
        slot("Servidores", R.drawable.ic_dns)
        val plus = FrameLayout(this)
        plus.background = rounded(Color.rgb(200, 16, 36), 18f)
        plus.addView(icon(R.drawable.ic_plus, Color.WHITE, 28), FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
        plus.setOnClickListener { newPost() }
        nav.addView(plus, LinearLayout.LayoutParams(dp(54), dp(46)).apply { leftMargin = dp(4); rightMargin = dp(4) })
        slot("Chat", R.drawable.ic_chat)
        slot("Mais", R.drawable.ic_apps)
        return nav
    }

    private fun openTab(name: String) {
        when (name) {
            "Início" -> showFeed()
            "Salas" -> showRooms()
            "Servidores" -> showServers()
            "Chat" -> showChats()
            else -> showMore()
        }
    }

    private fun showHome(message: String) {
        saveCookies()
        showFeed()
        if (message.isNotBlank()) toast("Login confirmado.")
    }

    // ---------- Início ----------
    private fun postCard(p: Post, open: Boolean = true): LinearLayout {
        val c = card()
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(avatarView(p.avatar, p.name, 42, p.frame))
        val who = LinearLayout(this)
        who.orientation = LinearLayout.VERTICAL
        who.setPadding(dp(10), 0, 0, 0)
        who.addView(nameRow(p.name, p.verified, p.role))
        who.addView(text("@${p.username}", 12f, muted))
        head.addView(who, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(text(p.time, 12f, muted))
        head.setOnClickListener { showProfile(p.username) }
        c.addView(head)
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        if (p.text.isNotBlank()) {
            body.addView(text(p.text, 15f).apply { setPadding(dp(2), dp(10), dp(2), dp(2)) })
        }
        if (p.media != null) body.addView(imageBlock(p.media))
        if (open) body.setOnClickListener { postDetail(p.id) }
        c.addView(body)
        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.setPadding(0, dp(8), 0, 0)
        fun heart() = (if (p.liked) "♥ " else "♡ ") + p.likes
        val like = text(heart(), 14f, if (p.liked) violet else muted)
        like.setPadding(dp(2), dp(6), dp(18), dp(6))
        like.setOnClickListener {
            like.isEnabled = false
            thread {
                try {
                    val r = Net.postForm("/api/posts/${p.id}/like", emptyList())
                    val j = JSONObject(r.body)
                    runOnUiThread {
                        p.liked = j.optBoolean("liked", p.liked)
                        p.likes = j.optInt("count", p.likes)
                        like.text = heart()
                        like.setTextColor(if (p.liked) violet else muted)
                        like.isEnabled = true
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        like.isEnabled = true
                        toast("Não foi possível curtir.")
                    }
                }
            }
        }
        actions.addView(like)
        val cm = text("◌ ${p.comments}", 14f, muted)
        cm.setPadding(dp(2), dp(6), dp(2), dp(6))
        if (open) cm.setOnClickListener { postDetail(p.id) }
        actions.addView(cm)
        c.addView(actions)
        return c
    }

    private fun postDetail(id: Int) {
        val c = screen("Início", "Publicação", back = { showFeed() })
        val st = skeleton()
        c.addView(st)
        async({ try { Frames.ensure() } catch (e: SessionExpired) { throw e } catch (e: Exception) { }; Parse.doc(Net.get("/post/$id").body) }, { d ->
            c.removeView(st)
            val post = Parse.posts(d).firstOrNull()
            if (post != null) c.addView(postCard(post, false))
            val coms = Parse.comments(d)
            addLine(c, "Comentários (${coms.size})", 17f)
            val box = fieldBox("Escreva um comentário…", true)
            box.minLines = 2
            c.addView(box, params().apply { bottomMargin = dp(8) })
            val send = pill("Comentar") {}
            send.setOnClickListener {
                val t = box.text.toString().trim()
                if (t.isEmpty()) {
                    toast("Escreva algo.")
                    return@setOnClickListener
                }
                send.isEnabled = false
                send.text = "Enviando…"
                async({ Net.postForm("/post/$id", listOf(Pair("content", t)), "text/html").code }, { _ -> postDetail(id) }, { m ->
                    send.isEnabled = true
                    send.text = "Comentar"
                    toast(m)
                })
            }
            c.addView(send, params().apply { bottomMargin = dp(14) })
            if (coms.isEmpty()) addLine(c, "Ainda não há comentários.", 13f, muted)
            for (cm in coms) {
                val k = card()
                if (cm.depth) (k.layoutParams as LinearLayout.LayoutParams).leftMargin = dp(26)
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.addView(avatarView(cm.avatar, cm.name, 34, cm.frame))
                val w = LinearLayout(this)
                w.orientation = LinearLayout.VERTICAL
                w.setPadding(dp(10), 0, 0, 0)
                w.addView(nameRow(cm.name, cm.verified, "", 14f))
                w.addView(text(cm.meta, 11f, muted))
                row.addView(w)
                row.setOnClickListener { showProfile(cm.username) }
                k.addView(row)
                k.addView(text(cm.text, 14f).apply { setPadding(dp(2), dp(8), dp(2), dp(2)) })
                c.addView(k)
            }
        }, { m -> st.text = "Falha: $m"; st.setTextColor(errorColor) })
    }

    private fun renderPosts(c: LinearLayout, posts: List<Post>, more: String?) {
        for (p in posts) c.addView(postCard(p))
        if (more != null) {
            val b = pill("Carregar mais publicações") {}
            b.setOnClickListener {
                b.text = "Carregando…"
                async({
                    val d = Parse.doc(Net.get(more).body)
                    Pair(Parse.posts(d), Parse.moreHref(d))
                }, { r ->
                    c.removeView(b)
                    renderPosts(c, r.first, r.second)
                }, { m -> b.text = "Falhou: $m. Toque para tentar de novo" })
            }
            c.addView(b)
        }
    }

    private fun showFeed(mode: String = "todos") {
        val c = screen("Início", "Início")
        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        for (pair in listOf(Pair("todos", "Todos"), Pair("seguindo", "Seguindo"))) {
            val on = pair.first == mode
            val b = text(pair.second, 14f, Color.WHITE)
            b.gravity = Gravity.CENTER
            b.setPadding(dp(18), dp(9), dp(18), dp(9))
            b.background = rounded(if (on) Color.rgb(60, 12, 20) else Color.rgb(14, 8, 11), 20f)
            b.setOnClickListener { if (!on) showFeed(pair.first) }
            tabs.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(8) })
        }
        c.addView(tabs, params().apply { bottomMargin = dp(12) })
        val disc = card()
        disc.addView(text("Descobrir pessoas e comunidades…", 14f, muted))
        disc.setOnClickListener { showSearch("") }
        c.addView(disc)
        val st = skeleton()
        c.addView(st)
        async({
            val d = Parse.doc(Net.get(if (mode == "seguindo") "/home?feed=seguindo" else "/home").body)
            try { Frames.ensure() } catch (e: SessionExpired) { throw e } catch (e: Exception) { }
            Triple(Parse.posts(d), Parse.moreHref(d), Parse.homeMeta(d))
        }, { r ->
            c.removeView(st)
            val meta = r.third
            if (meta.me != null) meUser = meta.me
            if (meta.avatar != null) meAvatar = meta.avatar
            unreadDot = meta.unread
            isAdminUser = meta.isAdmin
            if (r.first.isEmpty()) addLine(c, "Nenhuma publicação para mostrar ainda.", 14f, muted)
            else renderPosts(c, r.first, r.second)
        }, { m ->
            st.text = "Falha ao carregar: $m"
            st.setTextColor(errorColor)
        })
    }

    // ---------- Publicar ----------
    private fun newPost() {
        pickedUri = null
        val c = screen("Início", "Nova publicação", back = { showFeed() })
        val box = fieldBox("O que está acontecendo?", true)
        c.addView(box, params().apply { topMargin = dp(8); bottomMargin = dp(12) })
        val label = text("Nenhuma imagem anexada", 12f, muted)
        pickedLabel = label
        c.addView(pill("Anexar imagem") {
            val i = Intent(Intent.ACTION_GET_CONTENT)
            i.type = "image/*"
            startActivityForResult(Intent.createChooser(i, "Escolher imagem"), 77)
        }, params())
        c.addView(label, params().apply { topMargin = dp(6); bottomMargin = dp(16) })
        val send = pill("Publicar") {}
        send.setOnClickListener {
            val txt = box.text.toString().trim()
            val uri = pickedUri
            if (txt.isEmpty() && uri == null) {
                toast("Escreva algo ou anexe uma imagem.")
                return@setOnClickListener
            }
            send.isEnabled = false
            send.text = "Publicando…"
            async({
                var up: Upload? = null
                if (uri != null) {
                    val mime = contentResolver.getType(uri) ?: "image/jpeg"
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw Exception("Não consegui ler a imagem.")
                    val ext = if (mime.contains("png")) "png" else if (mime.contains("webp")) "webp" else if (mime.contains("gif")) "gif" else "jpg"
                    up = Upload("media", "imagem.$ext", mime, bytes)
                }
                val r = Net.postMultipart("/create", listOf(Pair("content", txt)), up)
                if (r.path.endsWith("/create")) {
                    throw Exception(Parse.flash(Parse.doc(r.body)) ?: "O servidor não aceitou a publicação.")
                }
                true
            }, { _ ->
                pickedUri = null
                toast("Publicado.")
                showFeed()
            }, { m ->
                send.isEnabled = true
                send.text = "Publicar"
                toast(m)
            })
        }
        c.addView(send, params())
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 77 && resultCode == RESULT_OK && data?.data != null) {
            pickedUri = data.data
            pickedLabel?.text = "Imagem anexada"
        }
    }

    // ---------- Buscar ----------
    private fun personRow(p: Person): LinearLayout {
        val c = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(avatarView(p.avatar, p.name, 44))
        val who = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(6), 0)
        }
        who.addView(text(p.name, 15f).apply { typeface = Typeface.DEFAULT_BOLD })
        who.addView(text("@${p.username}", 12f, muted))
        row.addView(who, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.setOnClickListener { showProfile(p.username) }
        c.addView(row)
        if (p.bio.isNotBlank()) c.addView(text(p.bio, 13f, muted).apply { setPadding(dp(2), dp(8), dp(2), dp(2)) })
        val btns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        if (p.showFollow && p.username != meUser) {
            val f = pill(if (p.following) "Seguindo" else "Seguir") {}
            f.setOnClickListener { toggleFollow(p, f) }
            btns.addView(f, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
        }
        if (p.username != meUser) {
            btns.addView(pill("Mensagem") { openChat(p.username, p.name) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        if (btns.childCount > 0) c.addView(btns)
        return c
    }

    private fun toggleFollow(p: Person, btn: TextView) {
        btn.isEnabled = false
        thread {
            try {
                val r = Net.postForm("/api/users/${p.username}/follow", emptyList())
                val j = JSONObject(r.body)
                runOnUiThread {
                    p.following = j.optBoolean("following", p.following)
                    btn.text = if (p.following) "Seguindo" else "Seguir"
                    btn.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btn.isEnabled = true
                    toast("Não foi possível atualizar.")
                }
            }
        }
    }

    private fun loadCommunities(): List<Community> {
        val out = LinkedHashMap<Int, Community>()
        for (path in listOf("/home", "/discover")) {
            try {
                for (cm in Parse.communities(Parse.doc(Net.get(path).body))) out[cm.id] = cm
            } catch (e: SessionExpired) {
                throw e
            } catch (e: Exception) {
            }
        }
        return out.values.toList()
    }

    private fun showSearch(q: String) {
        val c = screen("Buscar", "Buscar")
        val input = fieldBox("Pessoas, publicações, salas e #tags")
        input.setText(q)
        input.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        val go = pill("Buscar") { }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
        row.addView(go)
        c.addView(row, params().apply { bottomMargin = dp(14) })
        val run = {
            val t = input.text.toString().trim()
            if (t.isNotEmpty()) showSearch(t)
        }
        go.setOnClickListener { run() }
        input.setOnEditorActionListener { _, _, _ -> run(); true }

        val st = skeleton()
        c.addView(st)
        if (q.isBlank()) {
            async({
                val b = Parse.doc(Net.get("/buscar").body)
                val people = try { Parse.discoverPeople(Parse.doc(Net.get("/discover").body)) } catch (e: SessionExpired) { throw e } catch (e: Exception) { emptyList() }
                Pair(Parse.tags(b), people)
            }, { r ->
                c.removeView(st)
                if (r.first.isNotEmpty()) {
                    addLine(c, "Em alta", 18f)
                    val flow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    for (tag in r.first) {
                        flow.addView(pill(tag) { showSearch(tag) }, params().apply { bottomMargin = dp(8) })
                    }
                    c.addView(flow)
                }
                if (r.second.isNotEmpty()) {
                    addLine(c, "Pessoas para conhecer", 18f)
                    for (p in r.second) c.addView(personRow(p))
                }
                if (r.first.isEmpty() && r.second.isEmpty()) addLine(c, "Digite algo para buscar.", 14f, muted)
            }, { m -> st.text = "Falha: $m"; st.setTextColor(errorColor) })
        } else {
            async({
                val d = Parse.doc(Net.get("/buscar?q=" + enc(q)).body)
                val needle = q.lowercase().removePrefix("#")
                val rooms = try { Parse.rooms(Parse.doc(Net.get("/salas").body)).filter { it.name.lowercase().contains(needle) || it.desc.lowercase().contains(needle) } } catch (e: SessionExpired) { throw e } catch (e: Exception) { emptyList() }
                val comms = try { loadCommunities().filter { it.name.lowercase().contains(needle) } } catch (e: SessionExpired) { throw e } catch (e: Exception) { emptyList() }
                arrayOf<Any>(Parse.searchPeople(d), Parse.posts(d), rooms, comms)
            }, { r ->
                c.removeView(st)
                @Suppress("UNCHECKED_CAST") val people = r[0] as List<Person>
                @Suppress("UNCHECKED_CAST") val posts = r[1] as List<Post>
                @Suppress("UNCHECKED_CAST") val rooms = r[2] as List<Room>
                @Suppress("UNCHECKED_CAST") val comms = r[3] as List<Community>
                if (people.isEmpty() && posts.isEmpty() && rooms.isEmpty() && comms.isEmpty()) {
                    addLine(c, "Nada encontrado para \"$q\".", 14f, muted)
                }
                if (people.isNotEmpty()) {
                    addLine(c, "Pessoas", 18f)
                    for (p in people) c.addView(personRow(p))
                }
                if (rooms.isNotEmpty()) {
                    addLine(c, "Salas", 18f)
                    for (rm in rooms) c.addView(roomCard(rm))
                }
                if (comms.isNotEmpty()) {
                    addLine(c, "Comunidades", 18f)
                    for (cm in comms) c.addView(communityCard(cm))
                }
                if (posts.isNotEmpty()) {
                    addLine(c, "Publicações", 18f)
                    for (p in posts) c.addView(postCard(p))
                }
            }, { m -> st.text = "Falha: $m"; st.setTextColor(errorColor) })
        }
    }

    // ---------- Salas e comunidades ----------
    private fun roomCard(r: Room): LinearLayout {
        val c = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(avatarView(r.icon, r.name, 46))
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        info.addView(text(r.name, 16f).apply { typeface = Typeface.DEFAULT_BOLD })
        info.addView(text(r.members, 12f, muted))
        row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(row)
        if (r.desc.isNotBlank()) c.addView(text(r.desc, 13f, muted).apply { setPadding(dp(2), dp(8), dp(2), dp(4)) })
        c.addView(pill("Entrar na sala") { openRoom(r) }, params().apply { topMargin = dp(6) })
        return c
    }

    private fun communityCard(cm: Community): LinearLayout {
        val c = card()
        c.addView(text("◈  ${cm.name}", 16f).apply { typeface = Typeface.DEFAULT_BOLD })
        c.addView(pill("Ver comunidade") { openCommunity(cm) }, params().apply { topMargin = dp(8) })
        return c
    }

    private fun showRooms() {
        val c = screen("Salas", "Salas e comunidades")
        val st = skeleton()
        c.addView(st)
        async({
            val rooms = Parse.rooms(Parse.doc(Net.get("/salas").body))
            val comms = loadCommunities()
            Pair(rooms, comms)
        }, { r ->
            c.removeView(st)
            addLine(c, "Salas públicas", 18f)
            if (r.first.isEmpty()) addLine(c, "Nenhuma sala ainda.", 14f, muted)
            for (rm in r.first) c.addView(roomCard(rm))
            if (r.second.isNotEmpty()) {
                addLine(c, "Comunidades", 18f)
                for (cm in r.second) c.addView(communityCard(cm))
            }
        }, { m -> st.text = "Falha ao carregar: $m"; st.setTextColor(errorColor) })
    }

    private fun openCommunity(cm: Community) {
        val c = screen("Salas", cm.name, back = { showRooms() })
        val st = skeleton()
        c.addView(st)
        async({ Parse.doc(Net.get("/community/${cm.id}").body) }, { d ->
            c.removeView(st)
            val box = card()
            addLine(box, d.selectFirst(".community-info h1")?.text() ?: cm.name, 22f)
            val desc = d.selectFirst(".community-info p")?.text().orEmpty()
            if (desc.isNotBlank()) addLine(box, desc, 14f, muted)
            val members = d.selectFirst(".community-members")?.text().orEmpty()
            if (members.isNotBlank()) addLine(box, members, 12f, muted)
            val member = d.selectFirst(".community-status") != null
            val btn = pill(if (member) "Você participa (toque para sair)" else "Participar") {}
            btn.setOnClickListener {
                btn.isEnabled = false
                thread {
                    try {
                        val r = Net.postForm("/api/community/${cm.id}/join", emptyList())
                        val j = JSONObject(r.body)
                        runOnUiThread {
                            btn.text = if (j.optBoolean("joined")) "Você participa (toque para sair)" else "Participar"
                            btn.isEnabled = true
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            btn.isEnabled = true
                            toast("Não foi possível concluir.")
                        }
                    }
                }
            }
            box.addView(btn, params().apply { topMargin = dp(10) })
            c.addView(box)
        }, { m -> st.text = "Falha: $m"; st.setTextColor(errorColor) })
    }

    // ---------- bolhas de mensagem (salas e chat) ----------
    private fun systemLine(t: String): View {
        val v = text(t, 12f, muted)
        v.gravity = Gravity.CENTER
        v.setPadding(dp(4), dp(6), dp(4), dp(6))
        return v
    }

    private fun bubble(who: String, body: String, mine: Boolean, media: String?, time: String = ""): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (mine) Gravity.END else Gravity.START
            layoutParams = params().apply { bottomMargin = dp(7) }
        }
        val b = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(9), dp(13), dp(9))
            background = rounded(if (mine) Color.rgb(120, 18, 32) else glass, 18f)
        }
        if (!mine && who.isNotBlank()) b.addView(text(who, 11f, violet).apply { typeface = Typeface.DEFAULT_BOLD })
        if (body.isNotBlank()) b.addView(text(body, 15f))
        if (media != null) b.addView(imageBlock(media))
        if (time.isNotBlank()) b.addView(text(time, 10f, muted).apply { gravity = Gravity.END })
        wrap.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (mine) leftMargin = dp(50) else rightMargin = dp(50)
        })
        return wrap
    }

    private fun footerBar(hintText: String, onSend: (EditText, TextView) -> Unit): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(4))
            setBackgroundColor(ink)
        }
        val input = fieldBox(hintText)
        input.maxLines = 3
        val send = pill("Enviar") {}
        send.setOnClickListener { onSend(input, send) }
        bar.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
        bar.addView(send)
        return bar
    }

    private fun scrollDown() {
        scrollRef.post { scrollRef.fullScroll(View.FOCUS_DOWN) }
    }

    // ---------- Sala ----------
    private fun roomMsgView(m: JSONObject): View {
        val kind = m.optString("kind", "text")
        val name = m.optString("name")
        if (m.optBoolean("deleted")) return systemLine("Mensagem excluída")
        val body = m.optString("body", "")
        return when (kind) {
            "text" -> bubble(name, body, m.optBoolean("mine"), if (m.isNull("media")) null else Net.abs(m.optString("media")), "")
            "points" -> systemLine("$name doou $body ponto(s)")
            "kcoin" -> { val p = body.split("|"); systemLine("$name doou ${p[0]} kcoin" + (if (p.size > 1) " para @${p[1]}" else "")) }
            "crystal" -> { val p = body.split("|"); systemLine("$name doou ${p[0]} cristal(is)" + (if (p.size > 1) " para @${p[1]}" else "")) }
            "gift" -> systemLine("$name enviou um presente: $body")
            else -> systemLine("$name: $body")
        }
    }

    private fun openRoom(r: Room) {
        var lastId = 0L
        var firstId = Long.MAX_VALUE
        val footer = footerBar("Mensagem para a sala") { input, send ->
            val t = input.text.toString().trim()
            if (t.isEmpty()) return@footerBar
            send.isEnabled = false
            thread {
                try {
                    val resp = Net.postForm("/salas/${r.id}/enviar", listOf(Pair("body", t)))
                    val j = JSONObject(resp.body)
                    runOnUiThread {
                        send.isEnabled = true
                        if (j.has("error")) toast(j.optString("error")) else input.setText("")
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        send.isEnabled = true
                        toast("Não foi possível enviar.")
                    }
                }
            }
        }
        val c = screen("Salas", r.name, back = { showRooms() }, footer = footer)
        val st = text("Entrando na sala…", 13f, muted)
        c.addView(st)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(list)

        fun appendMsgs(arr: JSONArray, atTop: Boolean) {
            var pos = 0
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val id = m.optLong("id")
                if (id > lastId) lastId = id
                if (id < firstId) firstId = id
                val v = roomMsgView(m)
                if (atTop) {
                    list.addView(v, pos)
                    pos++
                } else {
                    list.addView(v)
                }
            }
        }

        async({
            Net.get("/salas/${r.id}")   // abrir a sala já registra você como membro
            JSONObject(Net.get("/salas/${r.id}/msgs", "application/json").body)
        }, { j ->
            c.removeView(st)
            val arr = j.optJSONArray("messages") ?: JSONArray()
            if (j.optBoolean("more")) {
                val older = pill("Carregar mensagens anteriores") {}
                older.setOnClickListener {
                    val before = firstId
                    async({ JSONObject(Net.get("/salas/${r.id}/msgs?before=$before", "application/json").body) }, { oj ->
                        val oa = oj.optJSONArray("messages") ?: JSONArray()
                        appendMsgs(oa, true)
                        if (!oj.optBoolean("more")) c.removeView(older)
                    })
                }
                c.addView(older, 0, params().apply { bottomMargin = dp(8) })
            }
            if (arr.length() == 0) list.addView(systemLine("Nenhuma mensagem ainda. Seja o primeiro."))
            appendMsgs(arr, false)
            scrollDown()
            startPoll(4000) {
                val sid = token
                thread {
                    try {
                        val pj = JSONObject(Net.get("/salas/${r.id}/msgs?after=$lastId", "application/json").body)
                        val pa = pj.optJSONArray("messages")
                        if (pa != null && pa.length() > 0) {
                            runOnUiThread {
                                if (sid == token) {
                                    appendMsgs(pa, false)
                                    scrollDown()
                                }
                            }
                        }
                    } catch (e: SessionExpired) {
                        runOnUiThread { sessionLost() }
                    } catch (e: Exception) {
                    }
                }
            }
        }, { m -> st.text = "Falha ao abrir a sala: $m"; st.setTextColor(errorColor) })
    }

    // ---------- Chat ----------
    private fun showChats() {
        val c = screen("Chat", "Mensagens")
        val st = skeleton()
        c.addView(st)
        async({ Parse.convos(Parse.doc(Net.get("/chat").body)) }, { list ->
            c.removeView(st)
            if (list.isEmpty()) {
                addLine(c, "Nenhuma conversa ainda. Busque uma pessoa e toque em Mensagem.", 14f, muted)
            }
            for (cv in list) {
                val cardView = card()
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(avatarView(cv.avatar, cv.name, 46))
                val d = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, dp(6), 0)
                }
                d.addView(text(cv.name, 16f).apply { typeface = Typeface.DEFAULT_BOLD; maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                d.addView(text(cv.preview.ifBlank { "Nenhuma mensagem ainda." }, 12f, muted).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                row.addView(d, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val side = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
                side.addView(text(cv.time, 11f, muted))
                if (cv.unread > 0) side.addView(text("${cv.unread}", 11f, violet).apply { typeface = Typeface.DEFAULT_BOLD })
                row.addView(side)
                cardView.addView(row)
                cardView.setOnClickListener { openChat(cv.username, cv.name) }
                c.addView(cardView)
            }
        }, { m -> st.text = "Falha ao carregar: $m"; st.setTextColor(errorColor) })
    }

    private fun chatText(m: JSONObject): Pair<String, String?> {
        val raw = m.optString("content", "")
        if (raw.startsWith("[[")) {
            val end = raw.indexOf("]]")
            val tag = if (end > 0) raw.substring(2, end) else ""
            val rest = if (end > 0) raw.substring(end + 2).trim() else raw
            val url = Net.abs(rest.takeIf { it.startsWith("/") || it.startsWith("http") })
            return if (tag.contains("MEDIA") && url != null) Pair("", url) else Pair("[anexo]", null)
        }
        return Pair(raw, null)
    }

    private fun openChat(username: String, display: String) {
        var convId = 0
        var meId = 0L
        var lastId = 0L
        var list: LinearLayout? = null
        val footer = footerBar("Mensagem") { input, send ->
            val t = input.text.toString().trim()
            if (t.isEmpty() || convId == 0) return@footerBar
            send.isEnabled = false
            thread {
                try {
                    val resp = Net.postForm("/api/chat/$convId/send", listOf(Pair("content", t)))
                    val j = JSONObject(resp.body)
                    runOnUiThread {
                        send.isEnabled = true
                        if (j.optBoolean("ok")) input.setText("") else toast(j.optString("error", "Não foi possível enviar."))
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        send.isEnabled = true
                        toast("Não foi possível enviar.")
                    }
                }
            }
        }
        val c = screen("Chat", display, back = { showChats() }, footer = footer)
        val st = text("Abrindo conversa…", 13f, muted)
        c.addView(st)

        fun addMsgs(arr: JSONArray) {
            val l = list ?: return
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val id = m.optLong("id")
                if (id <= lastId) continue
                lastId = id
                val (txt, media) = chatText(m)
                l.addView(bubble("", txt, m.optLong("sender_id") == meId, media, ""))
            }
        }

        async({
            val d = Parse.doc(Net.get("/chat/" + enc(username)).body)
            val box = d.selectFirst("[data-conversation-id]") ?: throw Exception("Conversa não encontrada.")
            val id = box.attr("data-conversation-id").toIntOrNull() ?: throw Exception("Conversa inválida.")
            val me = box.attr("data-current-user-id").toLongOrNull() ?: 0L
            val j = JSONObject(Net.get("/api/chat/$id/messages?after=0", "application/json").body)
            Triple(id, me, j)
        }, { r ->
            c.removeView(st)
            convId = r.first
            meId = r.second
            val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            list = l
            c.addView(l)
            val arr = r.third.optJSONArray("messages") ?: JSONArray()
            if (arr.length() == 0) l.addView(systemLine("Nenhuma mensagem ainda. Diga oi."))
            else addMsgs(arr)
            scrollDown()
            startPoll(4000) {
                val sid = token
                thread {
                    try {
                        val pj = JSONObject(Net.get("/api/chat/$convId/messages?after=$lastId", "application/json").body)
                        val pa = pj.optJSONArray("messages")
                        if (pa != null && pa.length() > 0) {
                            runOnUiThread {
                                if (sid == token) {
                                    addMsgs(pa)
                                    scrollDown()
                                }
                            }
                        }
                    } catch (e: SessionExpired) {
                        runOnUiThread { sessionLost() }
                    } catch (e: Exception) {
                    }
                }
            }
        }, { m -> st.text = "Falha ao abrir: $m"; st.setTextColor(errorColor) })
    }

    // ---------- Perfil ----------
    private fun showProfile(username: String?) {
        val mine = username == null || username == meUser
        val backFn: (() -> Unit)? = if (mine) null else fun() { showFeed() }
        val c = screen("Mais", if (mine) "Meu perfil" else "@$username", back = backFn)
        val st = skeleton()
        c.addView(st)
        async({
            var u = username ?: meUser
            if (u == null) {
                u = Parse.meUsername(Parse.doc(Net.get("/settings/profile").body)) ?: throw Exception("Não consegui identificar sua conta.")
                meUser = u
            }
            u
        }, { u ->
            c.removeView(st)
            openWebProfile(c, u)
        }, { m -> st.text = "Falha ao abrir: $m"; st.setTextColor(errorColor) })
    }

    private fun openWebProfile(c: LinearLayout, u: String) {
        try {
            val cm = android.webkit.CookieManager.getInstance()
            cm.setAcceptCookie(true)
            (java.net.CookieHandler.getDefault() as? java.net.CookieManager)?.cookieStore?.cookies?.forEach {
                cm.setCookie(Net.BASE, it.name + "=" + it.value + "; Path=/")
            }
            cm.flush()
        } catch (e: Throwable) {}
        val wv = android.webkit.WebView(this)
        wv.setBackgroundColor(Color.rgb(7, 5, 10))
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.setOnTouchListener { v, _ -> v.parent.requestDisallowInterceptTouchEvent(true); false }
        val css = "header.topbar,.k-side,nav.top-navigation,footer.mobile-navigation,.k-sheet,#k-more-btn{display:none!important}" +
            "html,body{background:#07050a!important}.main-content{margin:0!important;padding:8px!important;max-width:100%!important;width:100%!important}" +
            "body{padding:0!important}"
        val js = "(function(){var s=document.createElement('style');s.textContent=" + org.json.JSONObject.quote(css) + ";document.head.appendChild(s);})()"
        wv.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageStarted(v: android.webkit.WebView, url: String?, f: android.graphics.Bitmap?) { v.evaluateJavascript(js, null) }
            override fun onPageFinished(v: android.webkit.WebView, url: String?) { v.evaluateJavascript(js, null) }
            override fun shouldOverrideUrlLoading(v: android.webkit.WebView, r: android.webkit.WebResourceRequest): Boolean {
                val p = r.url.path ?: ""
                if (p.startsWith("/u/")) return false
                if (p.startsWith("/settings/profile")) { editProfile(); return true }
                return true
            }
        }
        val h = resources.displayMetrics.heightPixels - dp(230)
        c.addView(wv, LinearLayout.LayoutParams(-1, h))
        wv.loadUrl(Net.BASE + "/u/" + enc(u))
    }

    private fun showProfileNative(username: String?) {
        val mine = username == null || username == meUser
        val backFn: (() -> Unit)? = if (mine) null else fun() { showFeed() }
        val c = screen("Mais", if (mine) "Meu perfil" else "@$username", back = backFn)
        val st = skeleton()
        c.addView(st)
        async({
            var u = username ?: meUser
            if (u == null) {
                u = Parse.meUsername(Parse.doc(Net.get("/settings/profile").body)) ?: throw Exception("Não consegui identificar sua conta.")
                meUser = u
            }
            try { Frames.ensure() } catch (e: SessionExpired) { throw e } catch (e: Exception) { }
            val d = Parse.doc(Net.get("/u/" + enc(u)).body)
            Parse.profile(d, u, u == meUser)
        }, { p ->
            c.removeView(st)
            if (p.cover != null) {
                val cv = ImageView(this)
                cv.scaleType = ImageView.ScaleType.CENTER_CROP
                cv.setBackgroundColor(Color.rgb(42, 12, 17))
                cv.layoutParams = params().apply { bottomMargin = 0 }
                (cv.layoutParams as LinearLayout.LayoutParams).height = dp(150)
                cv.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) { outline.setRoundRect(0, 0, view.width, view.height + dp(30), dp(26).toFloat()) }
                }
                cv.clipToOutline = true
                Img.load(p.cover, cv, 1080)
                c.addView(cv)
            }
            val head = card().apply {
                background = rounded(Color.rgb(50, 12, 20), 26f)
                if (p.cover != null) {
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(50, 12, 20))
                        val r = dp(26).toFloat()
                        cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
                        setStroke(dp(1), Color.argb(80, 225, 29, 46))
                    }
                }
            }
            val r1 = LinearLayout(this)
            r1.orientation = LinearLayout.HORIZONTAL
            r1.gravity = Gravity.CENTER_VERTICAL
            val live = if (Frames.get(Regex("uf-\\d+").find(p.frame ?: "")?.value) == null && (p.frame != null || Frames.profileHtml?.contains("k-frame") == true)) Frames.liveView(this, 150) else null
            if (live != null) r1.addView(live, LinearLayout.LayoutParams(dp(150), dp(150))) else r1.addView(avatarView(p.avatar, p.name, 76, p.frame))
            val nm = LinearLayout(this)
            nm.orientation = LinearLayout.VERTICAL
            nm.setPadding(dp(14), 0, 0, 0)
            nm.addView(nameRow(p.name, p.verified, p.role, 21f))
            nm.addView(text("@${p.username}", 13f, muted))
            r1.addView(nm)
            head.addView(r1)
            if (p.status.isNotBlank()) head.addView(text("● ${p.status}", 13f, violet).apply { setPadding(dp(2), dp(10), dp(2), dp(2)) })
            if (p.badges.isNotEmpty()) {
                val hs = android.widget.HorizontalScrollView(this)
                hs.isHorizontalScrollBarEnabled = false
                val br = LinearLayout(this)
                br.orientation = LinearLayout.HORIZONTAL
                for (svg in p.badges) {
                    val bi = ImageView(this)
                    bi.layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(6) }
                    Img.loadSvg(svg, bi, dp(30) * 2)
                    br.addView(bi)
                }
                hs.addView(br)
                head.addView(hs, params().apply { topMargin = dp(10) })
            }
            if (p.bio.isNotBlank()) head.addView(text(p.bio, 14f).apply { setPadding(dp(2), dp(12), dp(2), dp(4)) })
            if (p.stats.isNotEmpty()) {
                val stats = LinearLayout(this)
                stats.orientation = LinearLayout.HORIZONTAL
                stats.setPadding(0, dp(10), 0, 0)
                for (s in p.stats.take(4)) {
                    val cell = LinearLayout(this)
                    cell.orientation = LinearLayout.VERTICAL
                    cell.gravity = Gravity.CENTER_HORIZONTAL
                    cell.addView(text(s.second, 16f).apply { typeface = Typeface.DEFAULT_BOLD })
                    cell.addView(text(s.first, 10f, muted))
                    stats.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
                head.addView(stats)
            }
            c.addView(head)
            if (p.isMe) {
                c.addView(pill("Editar perfil") { editProfile() }, params().apply { bottomMargin = dp(8) })
                c.addView(pill("Sair da conta") { logout() }, params().apply { bottomMargin = dp(14) })
            } else {
                val person = Person(p.name, p.username, p.avatar, "", p.following, true)
                val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val f = pill(if (p.following) "Seguindo" else "Seguir") {}
                f.setOnClickListener { toggleFollow(person, f) }
                btns.addView(f, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
                btns.addView(pill("Mensagem") { openChat(p.username, p.name) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                c.addView(btns, params().apply { bottomMargin = dp(14) })
            }
            addLine(c, "Publicações", 18f)
            if (p.posts.isEmpty()) addLine(c, "Nenhuma publicação ainda.", 14f, muted)
            for (po in p.posts) c.addView(postCard(po))
        }, { m -> st.text = "Falha ao carregar: $m"; st.setTextColor(errorColor) })
    }

    private fun editProfile() {
        val c = screen("Mais", "Editar perfil", back = { showProfile(null) })
        val st = skeleton()
        c.addView(st)
        async({
            val d = Parse.doc(Net.get("/settings/profile").body)
            Parse.formFields(d, "settings/profile")
        }, { fields ->
            c.removeView(st)
            if (fields.isEmpty()) {
                addLine(c, "Não encontrei o formulário de edição.", 14f, errorColor)
                return@async
            }
            val map = LinkedHashMap<String, String>()
            val order = ArrayList<Pair<String, String>>()
            for (f in fields) {
                map[f.first] = f.second
                order.add(f)
            }
            addLine(c, "Nome", 13f, muted)
            val name = fieldBox("Nome")
            name.setText(map["name"] ?: "")
            c.addView(name, params().apply { bottomMargin = dp(10) })
            addLine(c, "Status (dura 24 h)", 13f, muted)
            val status = fieldBox("Status")
            status.setText(map["status"] ?: "")
            c.addView(status, params().apply { bottomMargin = dp(10) })
            addLine(c, "Bio", 13f, muted)
            val bio = fieldBox("Bio", true)
            bio.setText(map["bio"] ?: "")
            c.addView(bio, params().apply { bottomMargin = dp(14) })
            val save = pill("Salvar alterações") {}
            save.setOnClickListener {
                val n = name.text.toString().trim()
                if (n.isEmpty()) {
                    toast("O nome não pode ficar vazio.")
                    return@setOnClickListener
                }
                save.isEnabled = false
                save.text = "Salvando…"
                async({
                    val out = ArrayList<Pair<String, String>>()
                    var hadName = false
                    var hadStatus = false
                    var hadBio = false
                    for (f in order) {
                        when (f.first) {
                            "name" -> { out.add(Pair("name", n)); hadName = true }
                            "status" -> { out.add(Pair("status", status.text.toString().trim())); hadStatus = true }
                            "bio" -> { out.add(Pair("bio", bio.text.toString().trim())); hadBio = true }
                            else -> out.add(f)
                        }
                    }
                    if (!hadName) out.add(Pair("name", n))
                    if (!hadStatus) out.add(Pair("status", status.text.toString().trim()))
                    if (!hadBio) out.add(Pair("bio", bio.text.toString().trim()))
                    val r = Net.postForm("/settings/profile", out, "text/html")
                    if (r.code >= 400) throw Exception("HTTP ${r.code}")
                    Parse.flash(Parse.doc(r.body)) ?: "Perfil atualizado."
                }, { msg ->
                    toast(msg)
                    showProfile(null)
                }, { m ->
                    save.isEnabled = true
                    save.text = "Salvar alterações"
                    toast(m)
                })
            }
            c.addView(save, params())
        }, { m -> st.text = "Falha ao carregar: $m"; st.setTextColor(errorColor) })
    }

    // ---------- Mais, notificações e servidores ----------
    private fun showMore() {
        val c = screen("Mais", "Mais")
        fun row(title: String, sub: String, go: () -> Unit) {
            val k = card()
            k.addView(text(title, 16f).apply { typeface = Typeface.DEFAULT_BOLD })
            k.addView(text(sub, 12f, muted))
            k.setOnClickListener { go() }
            c.addView(k)
        }
        row("Meu perfil", "Publicações, insígnias e edição") { showProfile(null) }
        row("Notificações", "Atividade da sua conta") { showNotifs() }
        row("Sair da conta", "Encerrar a sessão neste aparelho") { logout() }
        addLine(c, "Loja, jogos, carteira e painel chegam nas próximas etapas.", 12f, muted)
    }

    private fun showNotifs() {
        val c = screen("Mais", "Notificações", back = { showFeed() })
        val st = skeleton()
        c.addView(st)
        async({ Parse.notifs(Parse.doc(Net.get("/notifications").body)) }, { list ->
            c.removeView(st)
            unreadDot = false
            if (list.isEmpty()) addLine(c, "Nenhuma notificação.", 14f, muted)
            for (n in list) {
                val k = card()
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                if (n.avatar != null) row.addView(avatarView(n.avatar, "?", 38))
                val t = text(n.text, 14f)
                t.setPadding(dp(if (n.avatar != null) 10 else 2), dp(2), dp(2), dp(2))
                row.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                k.addView(row)
                k.setOnClickListener {
                    val h = n.href
                    val pid = Regex("^/post/(\\d+)").find(h)?.groupValues?.get(1)?.toIntOrNull()
                    when {
                        pid != null -> postDetail(pid)
                        h.startsWith("/u/") -> showProfile(h.removePrefix("/u/").substringBefore("/").substringBefore("?"))
                        h.startsWith("/chat/") -> openChat(h.removePrefix("/chat/").substringBefore("/").substringBefore("?"), "Conversa")
                    }
                }
                c.addView(k)
            }
        }, { m -> st.text = "Falha: $m"; st.setTextColor(errorColor) })
    }

    private fun showServers() {
        val c = screen("Servidores", "Servidores")
        val st = skeleton()
        c.addView(st)
        async({ Parse.servers(Parse.doc(Net.get("/servidores").body)) }, { list ->
            c.removeView(st)
            addLine(c, "Servidores de Discord parceiros do K.", 13f, muted)
            if (list.isEmpty()) addLine(c, "Nenhum servidor ainda.", 14f, muted)
            for (s in list) {
                val k = card()
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.addView(avatarView(s.icon, s.name, 46))
                val w = LinearLayout(this)
                w.orientation = LinearLayout.VERTICAL
                w.setPadding(dp(12), 0, 0, 0)
                w.addView(text(s.name, 16f).apply { typeface = Typeface.DEFAULT_BOLD })
                w.addView(text(s.members, 12f, muted))
                row.addView(w)
                k.addView(row)
                if (s.desc.isNotBlank()) k.addView(text(s.desc, 13f, muted).apply { setPadding(dp(2), dp(8), dp(2), dp(2)) })
                k.addView(pill("Ver servidor") {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Net.abs(s.href))))
                    } catch (e: Exception) {
                        toast("Não foi possível abrir.")
                    }
                }, params().apply { topMargin = dp(8) })
                c.addView(k)
            }
        }, { m -> st.text = "Falha: $m"; st.setTextColor(errorColor) })
    }
}
