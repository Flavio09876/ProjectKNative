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
import android.widget.HorizontalScrollView
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
    private var red = Color.rgb(220, 35, 55)
    private var bg = Color.rgb(18, 18, 22)
    private var panel = Color.rgb(29, 29, 35)

    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var loginInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private var busy = false

    private var ink = Color.rgb(7, 5, 10)
    private var glass = Color.rgb(18, 10, 13)
    private var muted = Color.rgb(169, 154, 157)
    private var violet = Color.rgb(225, 29, 46)
    private var errorColor = Color.rgb(255, 120, 130)
    private lateinit var palette: KPalette

    private fun applyTheme() {
        palette = KTheme.current(this)
        red = palette.accent
        bg = palette.bg
        panel = palette.surfaceRaised
        ink = palette.bg
        glass = palette.surface
        muted = palette.muted
        violet = palette.accent
        errorColor = palette.danger
    }

    /** O site guarda o tema em data-theme no <html>; o app passa a seguir a sessão. */
    private fun applySiteTheme(d: org.jsoup.nodes.Document): Boolean {
        try {
            val key = d.selectFirst("html[data-theme]")?.attr("data-theme")?.trim().orEmpty()
            if (key.isBlank() || key == palette.key) return false
            if (KTheme.all.none { it.key == key }) return false
            KTheme.save(this, key)
            applyTheme()
            return true
        } catch (e: Throwable) {
            return false
        }
    }

    private fun syncSiteThemeAsync(sid: Int) {
        thread {
            try {
                val key = Parse.doc(Net.get("/home").body)
                    .selectFirst("html[data-theme]")?.attr("data-theme")?.trim().orEmpty()
                if (key.isBlank() || key == palette.key) return@thread
                if (KTheme.all.none { it.key == key }) return@thread
                runOnUiThread {
                    if (sid != token) return@runOnUiThread
                    KTheme.save(this, key)
                    applyTheme()
                    showFeed()
                }
            } catch (e: Throwable) {
            }
        }
    }

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
    private var discordButton: Button? = null
    private var backdropAnim: android.animation.ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        var etapa = "tema"
        var superCalled = false
        try {
            applyTheme()
            Img.init(applicationContext)
            Crash.install(applicationContext)
            Crash.mark(this, "boot")
            etapa = "notificacoes"
            try {
                androidx.work.WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                    "k-notif", androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    androidx.work.PeriodicWorkRequestBuilder<Notifier>(15, java.util.concurrent.TimeUnit.MINUTES).build())
            } catch (e: Throwable) {}
            // Molduras da comunidade são componentes decorativos; nada de tela WebView.
            if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                try { requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 91) } catch (e: Throwable) {}
            }
            etapa = "super"
            super.onCreate(savedInstanceState)
            superCalled = true
            Crash.mark(this, "step_super")

            // Configura a jarra de cookies ANTES do deep link: o callback do Discord
            // precisa gravar a sessão mesmo quando o app abre do zero pelo navegador.
            etapa = "cookies"
            CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ALL))
            Crash.mark(this, "step_cookies")

            etapa = "deeplink"
            if (handleDiscordOnCreate(intent)) return
            Crash.mark(this, "step_deeplink")

            etapa = "statusbar"
            Crash.mark(this, "step_before_statusbar")
            window.statusBarColor = bg
            window.navigationBarColor = bg
            Crash.mark(this, "step_statusbar")

            etapa = "sessao"
            Crash.mark(this, "step_before_restore")
            val restored = restoreCookies()
            Crash.mark(this, if (restored) "step_restored" else "step_no_cookie")
            if (restored) {
                val splash = TextView(this).apply {
                    text = "Entrando…"
                    textSize = 16f
                    setTextColor(Color.LTGRAY)
                    gravity = Gravity.CENTER
                    setBackgroundColor(bg)
                }
                setContentView(splash)
                Crash.mark(this, "step_session")
                // Usa o async() para não sobrescrever a tela de espera se o deep link
                // do Discord chegar enquanto a sessão está sendo restaurada.
                async({ Parse.doc(Net.get("/home").body) }, { d ->
                    Crash.mark(this, "step_home")
                    applySiteTheme(d)
                    showHome("")
                }, {
                    Crash.mark(this, "step_fail")
                    showLogin()
                })
            } else {
                etapa = "login"
                showLogin()
            }
        } catch (e: Throwable) {
            Crash.mark(this, "step_catch_$etapa")
            if (!superCalled) {
                try { super.onCreate(savedInstanceState) } catch (e2: Throwable) {}
            }
            Crash.screen(this, e, etapa)
        }
    }

    override fun onDestroy() {
        stopPoll()
        backdropAnim?.cancel()
        backdropAnim = null
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
                arr.put(JSONObject()
                    .put("n", c.name).put("v", c.value)
                    .put("p", c.path ?: "/").put("s", c.secure)
                    .put("d", c.domain)
                    .put("h", c.isHttpOnly)
                    .put("x", c.maxAge))
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
                c.isHttpOnly = o.optBoolean("h", false)
                val domain = o.optString("d", "")
                if (domain.isNotBlank()) {
                    try { c.domain = domain } catch (e: Throwable) {}
                }
                val age = o.optLong("x", -1L)
                if (age >= 0L) c.maxAge = age
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

    private fun tint(color: Int, alpha: Float): Int = Color.argb(
        (255 * alpha).toInt(), Color.red(color), Color.green(color), Color.blue(color)
    )

    private fun params() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun label(text: String, size: Float = 14f): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(palette.text)
        }

    private fun makeRoot(): LinearLayout {
        backdropAnim?.cancel()
        val bd = KUi.backdrop(palette)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(36), dp(24), dp(28))
            background = bd
        }
        backdropAnim = bd.startAnimating()

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
            setTextColor(palette.text)
            setHintTextColor(palette.muted)
            setSingleLine(true)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = KUi.field(palette, dp(palette.radiusMd).toFloat())
            typeface = KUi.bodyTypeface(palette)
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

    private fun makeButton(text: String, action: () -> Unit, primary: Boolean = true): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            setTextColor(if (primary) Color.WHITE else palette.text)
            typeface = KUi.displayTypeface(palette)
            background = KUi.button(palette, dp(palette.radiusMd).toFloat(), primary)
            stateListAnimator = null
            elevation = 0f
            setPadding(dp(16), dp(11), dp(16), dp(11))
            setOnClickListener { KUi.press(this); action() }
        }
    }

    private fun showLogin() {
        token++
        stopPoll()
        backAction = null
        activeTab = "Login"
        discordButton = null
        makeRoot()

        addSpace(34)

        val logo = TextView(this).apply {
            text = "K"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(palette.accent)
            }
        }
        root.addView(logo, LinearLayout.LayoutParams(dp(64), dp(64)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(16)
        })

        root.addView(label("Project K", 26f).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.text)
        }, params())

        root.addView(label("Entre na sua conta", 14f).apply {
            gravity = Gravity.CENTER
            setTextColor(palette.textSoft)
        }, params().apply { bottomMargin = dp(30) })

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = KUi.card(palette, dp(14).toFloat())
        }
        root.addView(panel, params())

        fun input(hint: String, pass: Boolean): EditText {
            val e = EditText(this).apply {
                this.hint = hint
                textSize = 15f
                setTextColor(palette.text)
                setHintTextColor(palette.muted)
                setSingleLine(true)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = KUi.field(palette, dp(10).toFloat())
                typeface = Typeface.DEFAULT
                inputType = if (pass) {
                    android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                } else android.text.InputType.TYPE_CLASS_TEXT
            }
            panel.addView(e, params().apply { bottomMargin = dp(10) })
            return e
        }

        loginInput = input("Usuário ou e-mail", false)
        passwordInput = input("Senha", true)

        loginButton = makeButton("Entrar", { login() })
        panel.addView(loginButton, params().apply { topMargin = dp(4) })

        val divider = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        divider.addView(View(this).apply {
            background = GradientDrawable().apply { setColor(KUi.alpha(palette.border, 0.8f)) }
        }, LinearLayout.LayoutParams(0, 1, 1f))
        divider.addView(label("   ou   ", 12f).apply { setTextColor(palette.muted) })
        divider.addView(View(this).apply {
            background = GradientDrawable().apply { setColor(KUi.alpha(palette.border, 0.8f)) }
        }, LinearLayout.LayoutParams(0, 1, 1f))
        panel.addView(divider, params().apply { topMargin = dp(16); bottomMargin = dp(16) })

        val dc = makeButton("Continuar com Discord", { discordLogin() }, primary = false).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.rgb(88, 101, 242))
            }
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        discordButton = dc
        panel.addView(dc, params())

        status = label("", 12f).apply {
            gravity = Gravity.CENTER
            setTextColor(palette.textSoft)
            typeface = Typeface.DEFAULT
        }
        panel.addView(status, params().apply { topMargin = dp(12) })
        Crash.mark(this, "login")
    }

    private var dotsRun: Runnable? = null
    private var dotsAnim: android.animation.ObjectAnimator? = null

    private fun startDots() {
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        var n = 0
        val r = object : Runnable {
            override fun run() {
                loginButton.text = "Entrando" + ".".repeat(n % 4)
                n++
                h.postDelayed(this, 350)
            }
        }
        dotsRun = r
        h.post(r)
        dotsAnim = android.animation.ObjectAnimator.ofFloat(loginButton, "alpha", 1f, 0.55f).apply {
            duration = 700; repeatMode = android.animation.ValueAnimator.REVERSE; repeatCount = android.animation.ValueAnimator.INFINITE; start()
        }
    }

    private fun stopDots() {
        dotsRun?.let { android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(it) }
        dotsRun = null
        dotsAnim?.cancel(); dotsAnim = null
        try { loginButton.alpha = 1f; loginButton.text = "Entrar" } catch (e: Throwable) {}
    }

    /**
     * Login Discord 100% nativo: o app abre o OAuth no navegador e o site
     * devolve o controle por deep link (projz://discord ou projectk://discord)
     * com um ticket assinado. Nenhum WebView fica dentro do app.
     */
    private fun discordLogin() {
        val raw = ByteArray(24)
        java.security.SecureRandom().nextBytes(raw)
        val state = android.util.Base64.encodeToString(raw, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
            .trimEnd('=')
        prefs().edit().putString("discord_state", state).apply()
        discordButton?.let {
            it.isEnabled = false
            it.text = "AGUARDANDO AUTORIZAÇÃO…"
        }
        if (::status.isInitialized) {
            status.text = "Aguardando autorização no navegador…"
            status.setTextColor(palette.textSoft)
        }
        val url = Net.BASE + "/app/discord/start?s=" + enc(state) + "&r=projz"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            if (::status.isInitialized) {
                status.text = "Aguardando autorização… Toque aqui para reenviar."
                status.setOnClickListener { discordLogin() }
            }
        } catch (e: Throwable) {
            prefs().edit().remove("discord_state").apply()
            discordButton?.let { it.isEnabled = true; it.text = "ENTRAR COM DISCORD" }
            if (::status.isInitialized) {
                status.text = "Não foi possível abrir o navegador. Toque para tentar de novo."
                status.setOnClickListener { discordLogin() }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleDiscordCallback(intent)) clearDeepLink()
    }

    private fun clearDeepLink() {
        try { setIntent(Intent(this, MainActivity::class.java)) } catch (e: Throwable) {}
    }

    private fun isDiscordUri(u: Uri?): Boolean =
        u != null &&
            (u.scheme?.equals("projz", true) == true || u.scheme?.equals("projectk", true) == true) &&
            u.host?.equals("discord", true) == true

    private fun handleDiscordOnCreate(i: Intent?): Boolean {
        val u = i?.data ?: return false
        if (!isDiscordUri(u)) return false
        val handled = handleDiscordCallback(i)
        clearDeepLink()
        return handled
    }

    private fun discordError(error: String): String = when (error) {
        "off" -> "O login com Discord não está disponível no site."
        "discord" -> "O Discord não respondeu. Tente de novo."
        "banido" -> "Esta conta está suspensa."
        "email" -> "Já existe conta com esse e-mail. Entre com senha e ligue o Discord em Configurações."
        "oauth" -> "Autorização cancelada ou expirada."
        else -> "Não foi possível entrar com o Discord."
    }

    private fun handleDiscordCallback(i: Intent?): Boolean {
        val u = i?.data ?: return false
        if (!isDiscordUri(u)) return false
        val state = u.getQueryParameter("s").orEmpty()
        val ticket = u.getQueryParameter("t").orEmpty()
        val error = u.getQueryParameter("err")

        // Consome o estado em TODOS os ramos: links antigos ou reprocessados
        // nunca mais podem casar com um pedido pendente.
        val expected = prefs().getString("discord_state", null)
        prefs().edit().remove("discord_state").apply()
        discordButton?.let { it.isEnabled = true; it.text = "ENTRAR COM DISCORD" }
        if (::status.isInitialized) status.setOnClickListener(null)

        if (!error.isNullOrBlank()) {
            toast(discordError(error))
            showLogin()
            return true
        }
        if (state.isBlank() || expected == null || expected != state) {
            toast("Pedido de login inválido. Tente de novo.")
            showLogin()
            return true
        }
        if (u.getQueryParameter("need") == "1") {
            if (ticket.isBlank()) {
                toast("O Discord não devolveu a autorização. Tente de novo.")
                showLogin()
            } else showDiscordSignup(state, ticket)
            return true
        }
        if (ticket.isBlank()) {
            toast("O Discord não devolveu a autorização. Tente de novo.")
            showLogin()
            return true
        }
        finishDiscordLogin(state, ticket)
        return true
    }

    /** Aceita tanto o JSON {ok:true} quanto um redirect para /home com cookie de sessão. */
    private fun discordSessionOk(r: Resp): Boolean {
        val jsonOk = try { JSONObject(r.body).optBoolean("ok") } catch (e: Throwable) { false }
        return jsonOk || r.path.endsWith("/home")
    }

    private fun discordSessionError(r: Resp): String {
        val jsonError = try { JSONObject(r.body).optString("error") } catch (e: Throwable) { "" }
        return jsonError.ifBlank { "Não foi possível concluir o login. (HTTP ${r.code})" }
    }

    private fun finishDiscordLogin(state: String, ticket: String) {
        val wait = TextView(this).apply {
            text = "Entrando com Discord…"
            textSize = 16f
            setTextColor(palette.text)
            gravity = Gravity.CENTER
            setBackgroundColor(bg)
        }
        setContentView(wait)
        thread {
            var msg: String? = null
            var ok = false
            try {
                val r = Net.postJson(
                    "/app/discord/session",
                    JSONObject().put("s", state).put("t", ticket),
                )
                // Grava o cookie mesmo quando a resposta veio como redirect HTML.
                saveCookies()
                ok = discordSessionOk(r)
                if (!ok) msg = discordSessionError(r) else meUser = null
            } catch (e: SessionExpired) {
                msg = "Sessão expirada. Tente de novo."
            } catch (e: Throwable) {
                msg = "Falha ao entrar: ${e.message}"
            }
            val failure = msg
            runOnUiThread {
                if (ok) showHome("") else {
                    toast(failure ?: "Não foi possível concluir o login.")
                    showLogin()
                }
            }
        }
    }

    private fun showDiscordSignup(state: String, ticket: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(8))
            background = KUi.card(palette, dp(palette.radiusLg).toFloat())
        }
        box.addView(text("Escolha seu @ para entrar", 17f).apply {
            typeface = KUi.displayTypeface(palette)
            setPadding(0, 0, 0, dp(6))
        })
        box.addView(text("Sua conta Discord foi autorizada. Falta só o @ e a data de nascimento.", 13f, palette.textSoft))
        val user = fieldBox("usuário (somente letras, números e _)")
        val birth = fieldBox("data de nascimento (AAAA-MM-DD)")
        box.addView(user, params().apply { topMargin = dp(14) })
        box.addView(birth)
        val error = text("", 12f, errorColor)
        box.addView(error, params().apply { topMargin = dp(4) })

        val dlg = android.app.AlertDialog.Builder(this)
            .setTitle("Novo no K")
            .setView(box)
            .setCancelable(false)
            .setPositiveButton("Criar conta", null)
            .setNegativeButton("Cancelar") { _, _ -> showLogin() }
            .create()
        dlg.setOnShowListener {
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val uname = user.text.toString().trim()
                val data = birth.text.toString().trim()
                if (uname.length < 3 || !uname.all { it.isLetterOrDigit() || it == '_' }) {
                    error.text = "O @ precisa de 3+ caracteres: letras, números e _."
                    return@setOnClickListener
                }
                if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(data)) {
                    error.text = "Informe a data no formato AAAA-MM-DD."
                    return@setOnClickListener
                }
                dlg.dismiss()
                thread {
                    var newTicket: String? = null
                    var msg: String? = null
                    try {
                        val r = Net.postJson(
                            "/app/discord/complete",
                            JSONObject()
                                .put("s", state).put("t", ticket)
                                .put("username", uname).put("birth_date", data),
                        )
                        saveCookies()
                        val o = try { JSONObject(r.body) } catch (e: Throwable) { null }
                        when {
                            o?.optBoolean("ok") == true -> {
                                newTicket = o.optString("t")
                                if (newTicket.isNullOrBlank()) {
                                    msg = "A conta foi criada. Entre com Discord novamente."
                                    newTicket = null
                                }
                            }
                            r.path.endsWith("/home") -> {
                                saveCookies()
                                meUser = null
                                newTicket = ticket
                            }
                            else -> msg = o?.optString("error")?.ifBlank { null }
                                ?: "Não foi possível criar a conta. (HTTP ${r.code})"
                        }
                    } catch (e: Throwable) {
                        msg = "Falha: ${e.message}"
                    }
                    val fail = msg
                    val nt = newTicket
                    runOnUiThread {
                        if (nt != null) finishDiscordLogin(state, nt)
                        else {
                            toast(fail ?: "Falha ao criar a conta.")
                            showLogin()
                        }
                    }
                }
            }
        }
        dlg.show()
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
        status.text = ""
        startDots()
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
                stopDots()
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
            setStroke(dp(1), if (palette.light) palette.borderStrong else tint(palette.borderStrong, 0.45f))
        }
    }

    private fun text(value: String, size: Float = 14f, color: Int = palette.text): TextView {
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
            background = KUi.card(palette, dp(palette.radiusLg).toFloat())
            layoutParams = params().apply { bottomMargin = dp(11) }
        }
    }

    private fun addLine(parent: LinearLayout, value: String, size: Float = 14f, color: Int = palette.text) {
        parent.addView(text(value, size, color), params().apply { bottomMargin = dp(5) })
    }

    private fun eyebrow(parent: LinearLayout, value: String) {
        parent.addView(text(value.uppercase(), 11f, palette.accentHover).apply {
            typeface = KUi.displayTypeface(palette)
        }, params().apply { bottomMargin = dp(6) })
    }

    private fun pill(value: String, action: () -> Unit): TextView {
        return text(value, 13f, palette.text).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            typeface = KUi.displayTypeface(palette)
            background = KUi.button(palette, dp(palette.radiusMd).toFloat(), false)
            setOnClickListener { KUi.press(this); action() }
        }
    }

    private fun toast(m: String) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    }

    private fun fieldBox(hintText: String, multi: Boolean = false): EditText {
        return EditText(this).apply {
            hint = hintText
            setHintTextColor(palette.muted)
            setTextColor(palette.text)
            textSize = 15f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = KUi.field(palette, dp(palette.radiusMd).toFloat())
            typeface = KUi.bodyTypeface(palette)
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
        private var message: TextView? = null
        var text: CharSequence = ""
            set(v) {
                field = v
                removeAllViews()
                alpha = 1f
                val t = TextView(context)
                t.text = v
                t.textSize = 14f
                t.setTextColor(Color.rgb(255, 120, 120))
                message = t
                addView(t)
            }
        fun setTextColor(col: Int) { message?.setTextColor(col) }
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
            av.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(palette.surfaceHover) }
            r.addView(av, LinearLayout.LayoutParams(dp(40), dp(40)))
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            for (w in intArrayOf(140, 260, 200)) {
                val bar = View(this)
                bar.background = GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setColor(palette.surfaceHover) }
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
        // Apenas molduras criadas pela comunidade (.uf-N), com o CSS original
        // e animação real. Nada de anéis genéricos inventados pelo app.
        val key = Regex("""uf-\d+""").find(frame.orEmpty())?.value
        if (key != null && Frames.cssOf(key) != null) {
            return CommunityFrameView(this, key, url, dp(sizeDp), name)
        }
        val round = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        val base: View
        if (url != null) {
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setBackgroundColor(KUi.mix(palette.surfaceRaised, Color.BLACK, 0.4f))
            Img.load(url, iv, 256)
            base = iv
        } else {
            val tv = TextView(this)
            tv.text = (name.trim().firstOrNull() ?: '?').uppercaseChar().toString()
            tv.gravity = Gravity.CENTER
            tv.textSize = (sizeDp / 2.4f)
            tv.typeface = KUi.displayTypeface(palette)
            tv.setTextColor(Color.WHITE)
            tv.setBackgroundColor(KUi.mix(palette.accentLo, Color.BLACK, 0.35f))
            base = tv
        }
        base.outlineProvider = round
        base.clipToOutline = true
        base.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        return base
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
        n.typeface = KUi.displayTypeface(palette)
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

        backdropAnim?.cancel()
        val bd = KUi.backdrop(palette)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.background = bd
        backdropAnim = bd.startAnimating()
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(16), dp(12), dp(16), dp(10))
        top.background = GradientDrawable().apply {
            setColor(if (palette.light) Color.WHITE else palette.bg)
            setStroke(1, KUi.alpha(palette.border, 0.5f))
        }
        if (back != null) {
            val b = text("‹", 32f, palette.text)
            b.setPadding(dp(2), 0, dp(12), dp(4))
            b.setOnClickListener { back() }
            top.addView(b)
        }
        val t = text(title, 24f)
        t.typeface = Typeface.DEFAULT_BOLD
        t.maxLines = 1
        t.ellipsize = TextUtils.TruncateAt.END
        t.setTextColor(palette.text)
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
        Crash.mark(this, "tela")
        return c
    }

    private fun navBar(tab: String): LinearLayout {
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = Gravity.CENTER
        nav.setPadding(dp(4), dp(8), dp(4), dp(8))
        nav.background = GradientDrawable().apply {
            setColor(if (palette.light) Color.WHITE else palette.surface)
            setStroke(1, KUi.alpha(palette.border, 0.55f))
            cornerRadius = dp(18).toFloat()
        }
        fun slot(name: String, res: Int) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER
            val on = name == tab
            val color = if (on) palette.accent else palette.muted
            val ico = icon(res, color, if (on) 25 else 23)
            ico.alpha = if (on) 1f else 0.8f
            col.addView(ico)
            val l = text(name, 10f, color).apply {
                typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            }
            col.addView(l)
            col.setOnClickListener { KUi.press(col); openTab(name) }
            nav.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        slot("Início", R.drawable.ic_home)
        slot("Salas", R.drawable.ic_people)
        slot("Servidores", R.drawable.ic_dns)
        val plus = FrameLayout(this)
        plus.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.accent)
        }
        plus.addView(icon(R.drawable.ic_plus, Color.WHITE, 24), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        plus.setOnClickListener { KUi.press(plus); newPost() }
        nav.addView(plus, LinearLayout.LayoutParams(dp(46), dp(46)).apply { leftMargin = dp(6); rightMargin = dp(6) })
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
        syncSiteThemeAsync(token)
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
        checkUpdate(false)
        val c = screen("Início", "Início")
        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        for (pair in listOf(Pair("todos", "Todos"), Pair("seguindo", "Seguindo"))) {
            val on = pair.first == mode
            val b = text(pair.second, 14f, if (on) Color.WHITE else palette.textSoft)
            b.gravity = Gravity.CENTER
            b.setPadding(dp(18), dp(9), dp(18), dp(9))
            b.typeface = KUi.displayTypeface(palette)
            b.background = KUi.button(palette, dp(palette.radiusXl).toFloat(), on)
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
        if (requestCode == 78) {
            chatAttach = if (resultCode == RESULT_OK) data?.data else null
            if (chatAttach != null) toast("Arquivo anexado. Toque em Enviar.")
        }
        if (requestCode == 79) {
            fileCb?.onReceiveValue(android.webkit.WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCb = null
        }
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
        if (media != null) {
            val iv = imageBlock(media)
            iv.minimumHeight = dp(120)
            iv.setOnClickListener { try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(media))) } catch (e: Throwable) {} }
            b.addView(iv, LinearLayout.LayoutParams(dp(230), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
        if (time.isNotBlank()) b.addView(text(time, 10f, muted).apply { gravity = Gravity.END })
        wrap.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (mine) leftMargin = dp(50) else rightMargin = dp(50)
        })
        return wrap
    }

    private var chatAttach: android.net.Uri? = null
    private var fileCb: android.webkit.ValueCallback<Array<android.net.Uri>>? = null

    private fun readUpload(uri: android.net.Uri, field: String): Upload {
        val mime = contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw Exception("Não consegui ler o arquivo.")
        val ext = when { mime.contains("png") -> "png"; mime.contains("webp") -> "webp"; mime.contains("gif") -> "gif"; mime.contains("mp4") -> "mp4"; mime.contains("video") -> "mp4"; else -> "jpg" }
        return Upload(field, "arquivo.$ext", mime, bytes)
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
        chatAttach = null
        val clip = pill("+") {}
        clip.setOnClickListener {
            val i = Intent(Intent.ACTION_GET_CONTENT)
            i.type = "*/*"
            i.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            startActivityForResult(Intent.createChooser(i, "Anexar"), 78)
        }
        bar.addView(clip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(6) })
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
            val att = chatAttach
            if (t.isEmpty() && att == null) return@footerBar
            send.isEnabled = false
            thread {
                try {
                    val resp = if (att != null) Net.postMultipart("/salas/${r.id}/enviar", listOf(Pair("body", t)), readUpload(att, "media"), "application/json")
                        else Net.postForm("/salas/${r.id}/enviar", listOf(Pair("body", t)))
                    chatAttach = null
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
        if (raw.startsWith("[[PZ_MEDIA]]")) {
            val rest = raw.removePrefix("[[PZ_MEDIA]]")
            val i = rest.indexOf("[[PZ_TEXT]]")
            val url = (if (i >= 0) rest.substring(0, i) else rest).trim()
            val txt = if (i >= 0) rest.substring(i + 11).trim() else ""
            return Pair(txt, Net.abs(url))
        }
        if (raw.startsWith("[[")) {
            val end = raw.indexOf("]]")
            val tag = if (end > 0) raw.substring(2, end) else ""
            val rest = if (end > 0) raw.substring(end + 2).trim() else raw
            val url = Net.abs(rest.takeIf { it.startsWith("/") || it.startsWith("http") })
            if (tag.contains("MEDIA") && url != null) return Pair("", url)
            return Pair(Regex("\\[\\[PZ_[A-Z]+\\]\\]").replace(raw, " ").trim().ifEmpty { "[anexo]" }, null)
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
            val att = chatAttach
            if ((t.isEmpty() && att == null) || convId == 0) return@footerBar
            send.isEnabled = false
            thread {
                try {
                    val resp = if (att != null) Net.postMultipart("/api/chat/$convId/send", listOf(Pair("content", t)), readUpload(att, "attachment"), "application/json")
                        else Net.postForm("/api/chat/$convId/send", listOf(Pair("content", t)))
                    chatAttach = null
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

    // ---------- Telas nativas do K ----------

    private fun remoteImage(url: String?, heightDp: Int): ImageView {
        val iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.setBackgroundColor(palette.surfaceRaised)
        if (url != null) Img.load(url, iv, 512)
        iv.layoutParams = LinearLayout.LayoutParams(-1, dp(heightDp))
        return iv
    }

    private fun sectionTitle(parent: LinearLayout, eyebrow: String, title: String) {
        eyebrow(parent, eyebrow)
        addLine(parent, title, 22f, palette.text)
    }

    private fun groupCard(parent: LinearLayout): LinearLayout {
        val g = LinearLayout(this)
        g.orientation = LinearLayout.VERTICAL
        g.background = rounded(palette.surface, palette.radiusMd.toFloat())
        g.setPadding(dp(14), dp(6), dp(14), dp(6))
        parent.addView(g, params().apply { bottomMargin = dp(14) })
        return g
    }

    private fun row(
        parent: LinearLayout, iconRes: Int, iconColor: Int, label: String,
        hint: String? = null, action: (() -> Unit)? = null,
    ): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(2), dp(13), dp(2), dp(13))
        val tile = FrameLayout(this)
        tile.background = rounded(tint(iconColor, 0.16f), palette.radiusSm.toFloat())
        tile.addView(icon(iconRes, iconColor, 18), FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
        row.addView(tile, LinearLayout.LayoutParams(dp(34), dp(34)))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(text(label, 15f), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(13) })
        if (hint != null) col.addView(text(hint, 12f, palette.muted), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(13) })
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        if (action != null) {
            row.addView(text("›", 22f, palette.muted))
            row.setOnClickListener { action() }
        }
        parent.addView(row)
        parent.addView(View(this).apply { setBackgroundColor(tint(palette.border, 0.7f)) }, LinearLayout.LayoutParams(-1, 1).apply { leftMargin = dp(50) })
        return row
    }

    private fun submitForm(
        path: String,
        fields: List<Pair<String, String>>,
        accept: String = "text/html",
        onDone: () -> Unit,
    ) {
        thread {
            var failure: String? = null
            try {
                val r = Net.postForm(path, fields, accept)
                if (r.code >= 400) failure = "HTTP ${r.code}"
            } catch (e: SessionExpired) {
                failure = "Sessão expirada. Entre novamente."
            } catch (e: Throwable) {
                failure = e.message ?: "Falha na operação."
            }
            val f = failure
            runOnUiThread {
                if (f == null) onDone() else {
                    toast(f)
                    if (f.contains("Sessão")) sessionLost()
                }
            }
        }
    }

    private fun showWallet() {
        val c = screen("Mais", "Carteira", back = { showMore() })
        val st = skeleton()
        c.addView(st)
        async({ NativeData.wallet(NativeData.doc(Net.get("/carteira").body)) }, { w ->
            c.removeView(st)
            sectionTitle(c, "CARTEIRA", "Seus saldos")

            val grid = LinearLayout(this)
            grid.orientation = LinearLayout.HORIZONTAL
            grid.weightSum = 2f
            listOf("kcoin" to w.kcoin, "cristais" to w.crystals).forEachIndexed { i, pair ->
                val b = LinearLayout(this)
                b.orientation = LinearLayout.VERTICAL
                b.gravity = Gravity.CENTER
                b.setPadding(dp(10), dp(18), dp(10), dp(18))
                b.background = rounded(palette.surface, palette.radiusMd.toFloat())
                b.addView(text(pair.first.uppercase(), 11f, palette.accent).apply { typeface = Typeface.DEFAULT_BOLD })
                b.addView(text(pair.second, 24f).apply { typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }, params().apply { topMargin = dp(6) })
                grid.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i == 0) rightMargin = dp(6) else leftMargin = dp(6) })
            }
            c.addView(grid, params().apply { topMargin = dp(6); bottomMargin = dp(14) })

            val exchange = groupCard(c)
            addLine(exchange, "Trocar", 16f, palette.text)
            addLine(exchange, "Compra: 1 cristal = ${w.buyRate} kcoin.\nVenda: 1 cristal = ${w.sellRate} kcoin.\nA venda tem taxa de 10%.", 12f, palette.textSoft)
            val qty = fieldBox("quantidade")
            qty.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            qty.setText("1")
            exchange.addView(qty, params().apply { topMargin = dp(8) })
            val actions = LinearLayout(this)
            actions.orientation = LinearLayout.HORIZONTAL
            val buy = pill("Comprar cristais") {
                submitForm("/carteira", listOf("qty" to qty.text.toString(), "action" to "buy")) { showWallet() }
            }
            val sell = pill("Vender cristais") {
                submitForm("/carteira", listOf("qty" to qty.text.toString(), "action" to "sell")) { showWallet() }
            }
            actions.addView(buy, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(5) })
            actions.addView(sell, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(5) })
            exchange.addView(actions, params().apply { topMargin = dp(6); bottomMargin = dp(10) })

            val hist = groupCard(c)
            addLine(hist, "Histórico", 16f, palette.text)
            if (w.history.isEmpty()) addLine(hist, "Nenhuma movimentação ainda.", 13f, palette.muted)
            for (r in w.history) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                val left = LinearLayout(this)
                left.orientation = LinearLayout.VERTICAL
                left.addView(text(r.label, 14f))
                left.addView(text(r.whenText, 11f, palette.muted))
                row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
                val right = LinearLayout(this)
                right.orientation = LinearLayout.VERTICAL
                right.gravity = Gravity.END
                if (r.kcoin.isNotBlank()) right.addView(text(r.kcoin, 12f, palette.success))
                if (r.crystals.isNotBlank()) right.addView(text(r.crystals, 12f, palette.info))
                row.addView(right)
                row.setPadding(dp(2), dp(11), dp(2), dp(11))
                hist.addView(row)
                hist.addView(View(this).apply { setBackgroundColor(tint(palette.border, 0.7f)) }, LinearLayout.LayoutParams(-1, 1))
            }
            hist.setPadding(dp(14), dp(6), dp(14), dp(10))
        }, { m -> st.text = "Falha ao carregar a carteira: $m" })
    }

    private fun showTasks() {
        val c = screen("Mais", "Tarefas", back = { showMore() })
        val st = skeleton()
        c.addView(st)
        async({ NativeData.tasks(NativeData.doc(Net.get("/tarefas").body)) }, { groups ->
            c.removeView(st)
            sectionTitle(c, "GANHE KCOIN", "Tarefas")
            for ((section, items) in groups) {
                eyebrow(c, section)
                val g = groupCard(c)
                for (t in items) {
                    val row = LinearLayout(this)
                    row.orientation = LinearLayout.VERTICAL
                    row.setPadding(dp(2), dp(12), dp(2), dp(12))
                    row.addView(text(t.title, 15f).apply { typeface = Typeface.DEFAULT_BOLD })
                    row.addView(text(t.desc, 12f, palette.textSoft).apply { setPadding(0, dp(3), 0, 0) })
                    if (t.progress != null) {
                        val barBg = View(this)
                        barBg.background = rounded(palette.surfaceHover, 6f)
                        row.addView(barBg, LinearLayout.LayoutParams(-1, dp(5)).apply { topMargin = dp(8) })
                        val bar = View(this)
                        bar.background = rounded(palette.accent, 6f)
                        row.addView(bar, LinearLayout.LayoutParams(dp((t.progress.toIntOrNull() ?: 0) * 3), dp(5)).apply { topMargin = dp(-5) })
                    }
                    val actionRow = LinearLayout(this)
                    actionRow.orientation = LinearLayout.HORIZONTAL
                    actionRow.gravity = Gravity.CENTER_VERTICAL
                    actionRow.addView(text("+${t.reward} kcoin", 14f, palette.success).apply { typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(8) })
                    when (t.state) {
                        "done" -> actionRow.addView(text("✓ feito", 12f, palette.muted))
                        "ready" -> actionRow.addView(pill("Pegar") {
                            submitForm("/tarefas", listOf("task" to t.key)) { showTasks() }
                        })
                        else -> actionRow.addView(text("Pendente", 12f, palette.muted))
                    }
                    row.addView(actionRow, params().apply { topMargin = dp(6) })
                    g.addView(row)
                    g.addView(View(this).apply { setBackgroundColor(tint(palette.border, 0.7f)) }, LinearLayout.LayoutParams(-1, 1))
                }
            }
        }, { m -> st.text = "Falha ao carregar as tarefas: $m" })
    }

    private fun showBadges() {
        val c = screen("Mais", "Conquistas", back = { showMore() })
        val st = skeleton()
        c.addView(st)
        async({ NativeData.badges(NativeData.doc(Net.get("/conquistas").body)) }, { pair ->
            c.removeView(st)
            sectionTitle(c, "CONQUISTAS", "Suas insígnias")
            addLine(c, pair.first, 13f, palette.muted)
            val grid = LinearLayout(this)
            grid.orientation = LinearLayout.VERTICAL
            pair.second.chunked(2).forEach { chunk ->
                val line = LinearLayout(this)
                line.orientation = LinearLayout.HORIZONTAL
                line.weightSum = 2f
                chunk.forEachIndexed { i, b ->
                    val tile = LinearLayout(this)
                    tile.orientation = LinearLayout.VERTICAL
                    tile.gravity = Gravity.CENTER
                    tile.setPadding(dp(8), dp(14), dp(8), dp(14))
                    tile.background = rounded(
                        if (b.locked) palette.surface else tint(palette.accent, 0.08f),
                        palette.radiusLg.toFloat(),
                    )
                    if (b.art != null) {
                        val iv = ImageView(this)
                        iv.scaleType = ImageView.ScaleType.FIT_CENTER
                        iv.layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
                        Img.load(b.art, iv, 128)
                        tile.addView(iv)
                    } else {
                        tile.addView(text(if (b.locked) "🔒" else "🏅", 28f))
                    }
                    tile.addView(text(b.name, 13f, if (b.locked) palette.muted else palette.text).apply { gravity = Gravity.CENTER }, params().apply { topMargin = dp(8) })
                    tile.addView(text(b.rarity.ifBlank { b.desc }, 10f, palette.accent).apply { gravity = Gravity.CENTER })
                    tile.setOnClickListener {
                        android.app.AlertDialog.Builder(this)
                            .setTitle(b.name)
                            .setMessage("${b.rarity}\n\n${b.desc}${if (b.locked) "\n\nAinda bloqueada." else ""}")
                            .setPositiveButton("Fechar", null)
                            .show()
                    }
                    line.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i == 0) rightMargin = dp(6) else leftMargin = dp(6) })
                }
                if (chunk.size == 1) line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = dp(6) })
                grid.addView(line, params().apply { bottomMargin = dp(8) })
            }
            c.addView(grid)
        }, { m -> st.text = "Falha ao carregar as conquistas: $m" })
    }

    private fun showShop(cat: String? = null) {
        val c = screen("Mais", "Loja K", back = { showMore() })
        val st = skeleton()
        c.addView(st)
        val path = if (cat.isNullOrBlank()) "/loja" else "/loja?cat=" + enc(cat)
        async({ NativeData.shop(NativeData.doc(Net.get(path).body)) }, { result ->
            c.removeView(st)
            sectionTitle(c, "LOJA", "Loja K")
            val tabsScroll = HorizontalScrollView(this)
            val tabs = LinearLayout(this)
            tabs.orientation = LinearLayout.HORIZONTAL
            for (tab in result.first) {
                val chip = text(tab.label, 12f, if (tab.active) palette.text else palette.textSoft)
                chip.setPadding(dp(12), dp(8), dp(12), dp(8))
                chip.background = rounded(if (tab.active) tint(palette.accent, 0.2f) else palette.surface, palette.radiusSm.toFloat())
                chip.setOnClickListener {
                    val slug = tab.href.substringAfterLast('/').substringBefore('?')
                    showShop(slug)
                }
                tabs.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
            }
            tabsScroll.addView(tabs)
            c.addView(tabsScroll, params().apply { bottomMargin = dp(12) })

            for (item in result.second) {
                val card = card()
                if (item.previewImage != null) card.addView(remoteImage(item.previewImage, 110))
                else if (item.previewText != null) card.addView(text(item.previewText, 20f, palette.accent).apply { gravity = Gravity.CENTER; setPadding(dp(10), dp(18), dp(10), dp(18)) })
                addLine(card, item.name, 16f, palette.text)
                if (item.desc.isNotBlank()) addLine(card, item.desc, 12f, palette.textSoft)
                if (item.price.isNotBlank()) addLine(card, item.price + " kcoin", 14f, palette.accent)
                val btn = pill(item.button.ifBlank { "Comprar" }) {
                    val act = item.action
                    if (act.isBlank()) { toast("Item indisponível."); return@pill }
                    val fields = ArrayList<Pair<String, String>>()
                    if (item.kind.isNotBlank()) fields.add("kind" to item.kind)
                    if (item.item.isNotBlank()) fields.add("item" to item.item)
                    val pathAction = try { java.net.URL(act).path } catch (e: Throwable) { act }
                    submitForm(pathAction, fields) { showShop(cat) }
                }
                card.addView(btn, params().apply { topMargin = dp(8) })
                c.addView(card)
            }
            if (result.second.isEmpty()) addLine(c, "Nenhum item nesta categoria.", 13f, palette.muted)
        }, { m -> st.text = "Falha ao carregar a loja: $m" })
    }

    private fun showGames() {
        val c = screen("Mais", "Jogos", back = { showMore() })
        val st = skeleton()
        c.addView(st)
        async({ NativeData.games(NativeData.doc(Net.get("/jogos").body)) }, { games ->
            c.removeView(st)
            sectionTitle(c, "ARCADE", "Jogos")
            addLine(c, "Entre, jogue e ganhe kcoin. Os placares são sempre validados pelo servidor.", 12f, palette.textSoft)
            val all = games.ifEmpty {
                listOf(
                    GameInfo("Jogo do gráfico", "Acompanhe o gráfico e decidiu quando sacar.", "/casino"),
                    GameInfo("Mines", "Campo minado com multiplicadores.", "/mines"),
                    GameInfo("Cobrinha", "Clássico com placar no servidor.", "/cobrinha"),
                    GameInfo("Arcade", "Desafios rápidos por kcoin.", "/arcade"),
                )
            }
            val icons = intArrayOf(R.drawable.ic_apps, R.drawable.ic_seal, R.drawable.ic_people, R.drawable.ic_crown)
            for ((i, g) in all.withIndex()) {
                val card = card()
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                val tile = FrameLayout(this)
                tile.background = rounded(tint(palette.accent, 0.16f), palette.radiusMd.toFloat())
                tile.addView(icon(icons[i % icons.size], palette.accent, 22), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
                row.addView(tile, LinearLayout.LayoutParams(dp(44), dp(44)))
                val col = LinearLayout(this)
                col.orientation = LinearLayout.VERTICAL
                col.addView(text(g.name, 16f).apply { typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(13) })
                col.addView(text(g.desc, 12f, palette.textSoft), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(13) })
                row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
                card.addView(row)
                card.addView(pill("Jogar") {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Net.abs(g.href))))
                    } catch (e: Throwable) { toast("Não foi possível abrir.") }
                }, params().apply { topMargin = dp(10) })
                c.addView(card)
            }
        }, { m -> st.text = "Falha ao carregar os jogos: $m" })
    }

    private fun showAdmin(tab: String = "overview") {
        val c = screen("Mais", "Painel admin", back = { showMore() })
        val st = skeleton()
        c.addView(st)
        async({ NativeData.admin(NativeData.doc(Net.get("/admin?tab=" + enc(tab)).body)) }, { data ->
            c.removeView(st)
            sectionTitle(c, "ADMINISTRAÇÃO", "Painel")
            val tabsScroll = HorizontalScrollView(this)
            val tabs = LinearLayout(this)
            tabs.orientation = LinearLayout.HORIZONTAL
            for ((label, key) in listOf(
                "Visão geral" to "overview", "Denúncias" to "reports", "Apelações" to "appeals",
                "Usuários" to "users", "Publicações" to "posts", "Registro" to "log",
            )) {
                val chip = text(label, 12f, if (tab == key) palette.text else palette.textSoft)
                chip.setPadding(dp(12), dp(8), dp(12), dp(8))
                chip.background = rounded(if (tab == key) tint(palette.accent, 0.2f) else palette.surface, palette.radiusSm.toFloat())
                chip.setOnClickListener { showAdmin(key) }
                tabs.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
            }
            tabsScroll.addView(tabs)
            c.addView(tabsScroll, params().apply { bottomMargin = dp(10) })
            val atalhos = LinearLayout(this)
            atalhos.orientation = LinearLayout.HORIZONTAL
            listOf("Avisos" to "/admin/avisos", "Insígnias" to "/admin/insignias", "Títulos" to "/admin/titulo-honorario").forEach { (l, p) ->
                atalhos.addView(pill(l) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Net.abs(p)))) } catch (e: Throwable) { toast("Falha ao abrir.") }
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(4) })
            }
            c.addView(atalhos, params().apply { bottomMargin = dp(12) })

            if (data.second.isNotEmpty()) {
                val g = groupCard(c)
                data.second.chunked(2).forEach { chunk ->
                    val line = LinearLayout(this)
                    line.orientation = LinearLayout.HORIZONTAL
                    line.weightSum = 2f
                    chunk.forEachIndexed { i, s ->
                        val b = LinearLayout(this)
                        b.orientation = LinearLayout.VERTICAL
                        b.setPadding(dp(8), dp(10), dp(8), dp(10))
                        b.addView(text(s.value, 19f, if (s.alert) palette.danger else palette.accent).apply { typeface = Typeface.DEFAULT_BOLD })
                        b.addView(text(s.label, 11f, palette.muted))
                        line.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i == 0) rightMargin = dp(6) else leftMargin = dp(6) })
                    }
                    if (chunk.size == 1) line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                    g.addView(line)
                }
                g.setPadding(dp(8), dp(10), dp(8), dp(10))
            }
            for (r in data.third) {
                val card = card()
                val head = LinearLayout(this)
                head.orientation = LinearLayout.HORIZONTAL
                head.gravity = Gravity.CENTER_VERTICAL
                if (r.avatar != null) head.addView(avatarView(r.avatar, r.title, 36))
                val col = LinearLayout(this)
                col.orientation = LinearLayout.VERTICAL
                col.addView(text(r.title, 14f).apply { typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(if (r.avatar != null) 10 else 0) })
                if (r.subtitle.isNotBlank()) col.addView(text(r.subtitle, 11f, palette.muted), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(if (r.avatar != null) 10 else 0) })
                head.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
                card.addView(head)
                if (r.actions.isNotEmpty()) {
                    val acts = LinearLayout(this)
                    acts.orientation = LinearLayout.HORIZONTAL
                    r.actions.forEach { (path, label) ->
                        acts.addView(pill(label) { submitForm(path, listOf("next" to "/admin?tab=$tab")) { showAdmin(tab) } },
                            LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
                    }
                    card.addView(acts, params().apply { topMargin = dp(8) })
                }
                c.addView(card)
            }
            if (data.third.isEmpty() && data.second.isEmpty()) addLine(c, "Nada por aqui.", 13f, palette.muted)
        }, { m -> st.text = "Falha ao abrir o painel: $m" })
    }

    private fun showThemePicker() {
        val c = screen("Mais", "Aparência", back = { showSettings() })
        addLine(c, "Escolha o tema do app. Os mesmos 12 temas do site.", 13f, palette.textSoft)
        for (p in KTheme.all) {
            val card = card()
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val swatch = View(this)
            swatch.background = rounded(p.accent, palette.radiusSm.toFloat())
            row.addView(swatch, LinearLayout.LayoutParams(dp(28), dp(28)))
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.addView(text(p.label, 15f).apply { typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
            col.addView(text("fundo " + String.format("#%06X", 0xFFFFFF and p.bg), 11f, palette.muted), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            if (p.key == palette.key) row.addView(text("✓", 18f, palette.accent))
            card.addView(row)
            card.setOnClickListener {
                KTheme.save(this, p.key)
                applyTheme()
                showThemePicker()
            }
            c.addView(card)
        }
    }

    private fun formCard(
        parent: LinearLayout,
        title: String,
        hint: String,
        action: String,
        fieldHints: List<String>,
        fieldNames: List<String>,
        buttonLabel: String,
        passwordFields: Set<Int> = emptySet(),
    ) {
        val card = card()
        addLine(card, title, 15f, palette.text)
        addLine(card, hint, 12f, palette.textSoft)
        val edits = ArrayList<EditText>()
        for ((i, h) in fieldHints.withIndex()) {
            val e = fieldBox(h)
            if (i in passwordFields) e.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            edits.add(e)
            card.addView(e, params().apply { topMargin = dp(6) })
        }
        card.addView(pill(buttonLabel) {
            val fields = edits.mapIndexed { i, e -> fieldNames[i] to e.text.toString() }
            submitForm(action, fields) { toast("Alteração enviada."); showSettings() }
        }, params().apply { topMargin = dp(10) })
        parent.addView(card)
    }

    private fun showSettings() {
        val c = screen("Mais", "Configurações", back = { showMore() })
        sectionTitle(c, "SUA CONTA", "Configurações")

        val g1 = groupCard(c)
        row(g1, R.drawable.ic_user, palette.accent, "Perfil", "Nome, foto, capa, bio e status.") { editProfile() }
        row(g1, R.drawable.ic_apps, palette.info, "Aparência", "Tema: ${palette.label}") { showThemePicker() }
        row(g1, R.drawable.ic_mail, Color.rgb(66, 133, 244), "Conta", "E-mail e senha.") { showAccountSettings() }

        val g2 = groupCard(c)
        row(g2, R.drawable.ic_chat, Color.rgb(88, 101, 242), "Discord", "Ligar conta e avisos por DM.") { showDiscordSettings() }
        row(g2, R.drawable.ic_block, palette.muted, "Usuários bloqueados", "Gerir quem você bloqueou.") { showBlocked() }
        row(g2, R.drawable.ic_info, palette.info, "Ajuda e informações", "Termos, privacidade e sair.") { showHelpSettings() }
        row(g2, R.drawable.ic_trash, palette.danger, "Excluir conta", "Zona de perigo.") { showDeleteAccount() }
    }

    private fun showAccountSettings() {
        val c = screen("Mais", "Conta", back = { showSettings() })
        sectionTitle(c, "CONTA", "E-mail e senha")
        formCard(c, "Alterar e-mail", "Novo e-mail e sua senha atual.", "/settings/email",
            listOf("Novo e-mail", "Sua senha"), listOf("email", "password"), "Alterar e-mail", setOf(1))
        formCard(c, "Alterar senha", "Mínimo de 8 caracteres.", "/settings/password",
            listOf("Senha atual", "Nova senha", "Confirmar nova senha"),
            listOf("current_password", "new_password", "confirm_password"), "Alterar senha", setOf(0, 1, 2))
    }

    private fun showDiscordSettings() {
        val c = screen("Mais", "Discord", back = { showSettings() })
        sectionTitle(c, "DISCORD", "Sua conta Discord")
        addLine(c, "Avinque o Discord para entrar com um clique e receber avisos no seu privado.", 12f, palette.textSoft)
        c.addView(pill("Ligar / entrar com Discord") { discordLogin() }, params().apply { topMargin = dp(6); bottomMargin = dp(14) })
        val g = groupCard(c)
        row(g, R.drawable.ic_bell, Color.rgb(88, 101, 242), "Ativar avisos por DM") {
            submitForm("/discord/dm", listOf("on" to "1")) { toast("Avisos atualizados.") }
        }
        row(g, R.drawable.ic_bell, palette.muted, "Desativar avisos por DM") {
            submitForm("/discord/dm", listOf("on" to "0")) { toast("Avisos atualizados.") }
        }
        row(g, R.drawable.ic_block, palette.danger, "Desligar Discord") {
            submitForm("/auth/discord/desligar", emptyList()) { toast("Discord desligado."); showSettings() }
        }
    }

    private fun showBlocked() {
        val c = screen("Mais", "Bloqueados", back = { showSettings() })
        val st = skeleton()
        c.addView(st)
        async({ NativeData.doc(Net.get("/settings").body) }, { d ->
            c.removeView(st)
            sectionTitle(c, "PRIVACIDADE", "Usuários bloqueados")
            addLine(c, "Quem você bloqueia não vê seu perfil, não te manda mensagem e some do feed.", 12f, palette.textSoft)
            val card = card()
            val sec = d.select("section#privacidade").firstOrNull()
            val people = sec?.select("div.k-row") ?: org.jsoup.Jsoup.parse("<div></div>").select("div.k-row")
            if (people.isEmpty()) addLine(card, "Você não bloqueou ninguém.", 13f, palette.muted)
            for (p in people) {
                val name = p.select("strong").firstOrNull()?.text()?.trim().orEmpty()
                val user = p.select("span.k-muted").firstOrNull()?.text()?.trim().orEmpty()
                val r = LinearLayout(this)
                r.orientation = LinearLayout.HORIZONTAL
                r.gravity = Gravity.CENTER_VERTICAL
                val col = LinearLayout(this)
                col.orientation = LinearLayout.VERTICAL
                col.addView(text(name.ifBlank { user }, 14f), LinearLayout.LayoutParams(0, -2, 1f))
                r.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
                val path = p.select("form").firstOrNull()?.attr("action").orEmpty()
                if (path.isNotBlank()) r.addView(pill("Desbloquear") {
                    submitForm(path, listOf("action" to "unblock", "next" to "/settings#privacidade")) { showBlocked() }
                })
                card.addView(r, params().apply { topMargin = dp(8) })
            }
            c.addView(card)
        }, { m -> st.text = "Falha: $m" })
    }

    private fun showHelpSettings() {
        val c = screen("Mais", "Ajuda", back = { showSettings() })
        sectionTitle(c, "AJUDA", "Informações")
        val g = groupCard(c)
        row(g, R.drawable.ic_info, palette.info, "Termos de uso") { openExternal("/termos") }
        row(g, R.drawable.ic_lock, palette.info, "Privacidade") { openExternal("/privacidade") }
        row(g, R.drawable.ic_feedback, palette.accent, "Enviar feedback") { openExternal("/settings#ajuda") }
        row(g, R.drawable.ic_user, palette.danger, "Sair da conta") { logout() }
    }

    private fun showDeleteAccount() {
        val c = screen("Mais", "Excluir conta", back = { showSettings() })
        sectionTitle(c, "ZONA DE PERIGO", "Excluir conta")
        addLine(c, "Sua conta passa a se chamar “Unknown”. Por 30 dias você pode recuperá-la entrando de novo. Depois disso perfil, publicações, mensagens, carteira e itens são apagados de vez.", 12f, palette.textSoft)
        formCard(c, "Confirmar exclusão", "Senha e seu @ para confirmar.", "/settings/delete",
            listOf("Sua senha", "Digite seu @"), listOf("password", "confirm_username"),
            "Excluir minha conta", setOf(0))
    }

    private fun openExternal(path: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Net.abs(path))))
        } catch (e: Throwable) { toast("Não foi possível abrir.") }
    }

    // ---------- Cabeçalho nativo do perfil ----------
    private fun addProfileHeader(c: LinearLayout, p: br.com.projectk.nativeapp.Profile) {
        val extras = try {
            NativeData.profileExtras(NativeData.doc(Net.get("/u/" + enc(p.username) + "?app=1").body))
        } catch (e: Throwable) {
            NativeData.ProfileExtras(p.cover, null, p.frame, null, null, "", null, emptyList())
        }

        val cover = remoteImage(extras.cover ?: p.cover, 116)
        cover.adjustViewBounds = true
        c.addView(cover, params().apply { bottomMargin = 0 })

        val head = LinearLayout(this)
        head.orientation = LinearLayout.VERTICAL
        head.setPadding(dp(16), dp(12), dp(16), dp(14))
        head.background = rounded(palette.surface, palette.radiusLg.toFloat())

        val avatar = avatarView(extras.avatar ?: p.avatar, p.name, 84, extras.frame ?: p.frame)
        (avatar.layoutParams as? LinearLayout.LayoutParams)?.gravity = Gravity.CENTER_HORIZONTAL
        head.addView(avatar)

        val name = text(p.name, 19f).apply {
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        head.addView(name, params())
        head.addView(text("@" + p.username, 13f, palette.muted).apply { gravity = Gravity.CENTER })

        val status = extras.status ?: p.status
        if (!status.isNullOrBlank()) {
            val bubble = text("💬  $status", 12f, palette.textSoft)
            bubble.setPadding(dp(12), dp(8), dp(12), dp(8))
            bubble.background = rounded(tint(palette.accent, 0.12f), palette.radiusSm.toFloat())
            head.addView(bubble, params().apply { topMargin = dp(8) })
        }

        val honorName = extras.honorName
        if (!honorName.isNullOrBlank()) {
            val honor = LinearLayout(this)
            honor.orientation = LinearLayout.HORIZONTAL
            honor.gravity = Gravity.CENTER_VERTICAL
            honor.setPadding(dp(10), dp(7), dp(10), dp(7))
            honor.background = rounded(tint(Color.rgb(127, 224, 255), 0.14f), 99f)
            if (extras.honorArt != null) {
                val art = ImageView(this)
                art.scaleType = ImageView.ScaleType.FIT_CENTER
                art.layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
                Img.load(extras.honorArt, art, 64)
                honor.addView(art)
            }
            honor.addView(text(honorName, 12f, Color.rgb(223, 246, 255)).apply { typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
            honor.addView(text("ⓘ", 12f, Color.rgb(127, 224, 255)), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
            honor.setOnClickListener {
                val detail = try {
                    val o = JSONObject(extras.honorJson)
                    "Título honorário\n\n${o.optString("name")}\n\n${o.optString("description").ifBlank { o.optString("desc") }}"
                } catch (e: Throwable) { "Título honorário\n\n$honorName" }
                android.app.AlertDialog.Builder(this).setTitle("Título honorário").setMessage(detail).setPositiveButton("Fechar", null).show()
            }
            head.addView(honor, params().apply { topMargin = dp(8) })
        }

        val badges = extras.badges
        if (badges.isNotEmpty()) {
            val strip = HorizontalScrollView(this)
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            for (b in badges) {
                val chip = text("✦ " + b.name, 11f, palette.textSoft)
                chip.setPadding(dp(10), dp(6), dp(10), dp(6))
                chip.background = rounded(tint(palette.accent, 0.12f), 99f)
                chip.setOnClickListener {
                    android.app.AlertDialog.Builder(this)
                        .setTitle(b.name)
                        .setMessage("${b.rarity}\n\n${b.desc}")
                        .setPositiveButton("Fechar", null).show()
                }
                line.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
            }
            strip.addView(line)
            head.addView(strip, params().apply { topMargin = dp(10) })
        }

        if (p.stats.isNotEmpty()) {
            val statsRow = LinearLayout(this)
            statsRow.orientation = LinearLayout.HORIZONTAL
            statsRow.weightSum = p.stats.size.toFloat()
            for (s in p.stats) {
                val box = LinearLayout(this)
                box.orientation = LinearLayout.VERTICAL
                box.gravity = Gravity.CENTER
                box.addView(text(s.second, 16f, palette.accent).apply { typeface = Typeface.DEFAULT_BOLD })
                box.addView(text(s.first, 10f, palette.muted))
                statsRow.addView(box, LinearLayout.LayoutParams(0, -2, 1f))
            }
            head.addView(statsRow, params().apply { topMargin = dp(12) })
        }
        c.addView(head, params().apply { topMargin = dp(-18); bottomMargin = dp(14) })
    }

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
            try { Frames.ensure() } catch (e: SessionExpired) { throw e } catch (e: Exception) { }
            val d = Parse.doc(Net.get("/u/" + enc(u)).body)
            Parse.profile(d, u, u == meUser)
        }, { p ->
            c.removeView(st)
            addProfileHeader(c, p)
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
    private fun showUpdateDialog(latest: Int, cur: Int) {
        val dlg = android.app.Dialog(this)
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(22), dp(24), dp(22), dp(18))
        box.background = GradientDrawable().apply {
            setColor(Color.rgb(24, 9, 14)); cornerRadius = dp(26).toFloat(); setStroke(dp(1), Color.argb(140, 225, 29, 46))
        }
        val tile = TextView(this)
        tile.text = "K"; tile.textSize = 26f; tile.gravity = Gravity.CENTER
        tile.setTextColor(Color.WHITE); tile.typeface = Typeface.DEFAULT_BOLD
        tile.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(225, 29, 46), Color.rgb(120, 10, 25))).apply { cornerRadius = dp(16).toFloat() }
        box.addView(tile, LinearLayout.LayoutParams(dp(56), dp(56)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        box.addView(text("Nova versão disponível", 20f).apply { typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(4)) })
        box.addView(text("Versão $latest pronta para instalar.\nVocê está na $cur.", 14f, muted).apply { gravity = Gravity.CENTER })
        val yes = pill("Atualizar agora") {
            dlg.dismiss()
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Flavio09876/ProjectKNative/releases/download/apk-latest/ProjectK-debug.apk")))
        }
        box.addView(yes, params().apply { topMargin = dp(18) })
        val no = text("Agora não", 14f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(2)); setOnClickListener { dlg.dismiss() } }
        box.addView(no)
        dlg.setContentView(box)
        dlg.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setLayout((resources.displayMetrics.widthPixels * 0.88f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dlg.show()
    }

    private var updateChecked = false

    private fun checkUpdate(manual: Boolean) {
        if (updateChecked && !manual) return
        updateChecked = true
        thread {
            try {
                val c = java.net.URL("https://api.github.com/repos/Flavio09876/ProjectKNative/releases/tags/apk-latest").openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 10000; c.readTimeout = 10000
                val body = c.inputStream.bufferedReader().readText()
                val notes = org.json.JSONObject(body).optString("body")
                val latest = Regex("build (\\d+)").find(notes)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val cur = packageManager.getPackageInfo(packageName, 0).versionCode
                runOnUiThread {
                    if (latest > cur) {
                        showUpdateDialog(latest, cur)
                    } else if (manual) Toast.makeText(this, "Você já está na versão mais recente ($cur).", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Throwable) {
                if (manual) runOnUiThread { Toast.makeText(this, "Não foi possível verificar agora.", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    /** Telas ainda sem implementação nativa abrem no navegador — nunca dentro de um WebView. */
    private fun openSite(path: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Net.BASE + path)))
        } catch (e: Throwable) {
            toast("Não foi possível abrir esta página.")
        }
    }

    private fun showMore() {
        val c = screen("Mais", "Mais")
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(6), 0, dp(16))
            background = KUi.card(palette, dp(palette.radiusXl).toFloat())
        }
        head.addView(avatarView(meAvatar, meUser ?: "K", 84))
        head.addView(text("@${meUser ?: "você"}", 20f).apply {
            typeface = KUi.displayTypeface(palette)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        })
        head.addView(text("Toque para ver seu perfil", 12f, palette.textSoft).apply { gravity = Gravity.CENTER })
        head.setOnClickListener { showProfile(null) }
        c.addView(head, params())

        fun section(caption: String, vararg items: Triple<String, Pair<Int, Int>, () -> Unit>) {
            val wrap = LinearLayout(this)
            wrap.orientation = LinearLayout.VERTICAL
            wrap.addView(text(caption, 11f, palette.accentHover).apply {
                typeface = KUi.displayTypeface(palette)
                setPadding(dp(6), dp(6), 0, dp(6))
            })
            val g = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = KUi.card(palette, dp(palette.radiusLg).toFloat())
            }
            items.forEachIndexed { i, item ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                }
                val tile = FrameLayout(this).apply {
                    background = KUi.button(palette, dp(palette.radiusSm).toFloat(), false)
                }
                tile.addView(icon(item.second.first, item.second.second, 20),
                    FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
                row.addView(tile, LinearLayout.LayoutParams(dp(34), dp(34)))
                row.addView(text(item.first, 16f).apply {
                    typeface = KUi.bodyTypeface(palette)
                    setPadding(dp(14), 0, 0, 0)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(text("›", 22f, palette.muted))
                row.setOnClickListener { KUi.press(row); item.third() }
                g.addView(row)
                if (i < items.size - 1) {
                    g.addView(View(this).apply {
                        setBackgroundColor(KUi.alpha(palette.border, 0.7f))
                    }, LinearLayout.LayoutParams(-1, 1).apply { leftMargin = dp(62) })
                }
            }
            wrap.addView(g)
            c.addView(wrap, params().apply { bottomMargin = dp(12) })
        }

        val red = palette.accent
        val green = palette.success
        val blue = palette.info
        val gold = Color.rgb(212, 160, 0)
        val pink = Color.rgb(214, 51, 132)

        section("EXPLORAR",
            Triple("Descobrir", Pair(R.drawable.ic_info, blue)) { openSite("/discover") },
            Triple("Comunidades", Pair(R.drawable.ic_people, green)) { openSite("/comunidades") },
            Triple("Servidores", Pair(R.drawable.ic_dns, pink)) { showServers() },
            Triple("Convites 🔥", Pair(R.drawable.ic_crown, red)) { openSite("/convites") },
        )
        section("MISSÕES E PROGRESSO",
            Triple("Missões", Pair(R.drawable.ic_seal, green)) { showTasks() },
            Triple("Conquistas", Pair(R.drawable.ic_crown, gold)) { showBadges() },
            Triple("Criações", Pair(R.drawable.ic_apps, pink)) { openSite("/criacoes") },
            Triple("Meu perfil", Pair(R.drawable.ic_people, red)) { showProfile(null) },
        )
        section("BOLSA E JOGOS",
            Triple("Bolsa K", Pair(R.drawable.ic_seal, gold)) { openSite("/bolsa") },
            Triple("Carteira", Pair(R.drawable.ic_seal, blue)) { showWallet() },
            Triple("Jogo do gráfico", Pair(R.drawable.ic_apps, red)) { openSite("/casino") },
            Triple("Mines", Pair(R.drawable.ic_dns, red)) { openSite("/mines") },
            Triple("Cobrinha", Pair(R.drawable.ic_apps, green)) { openSite("/cobrinha") },
            Triple("Arcade", Pair(R.drawable.ic_apps, pink)) { openSite("/arcade") },
        )
        section("CONTA",
            Triple("Loja", Pair(R.drawable.ic_crown, pink)) { showShop() },
            Triple("Notificações", Pair(R.drawable.ic_bell, gold)) { showNotifs() },
            Triple("Configurações", Pair(R.drawable.ic_shield, palette.muted)) { showSettings() },
            Triple("Atualização do app", Pair(R.drawable.ic_dns, blue)) { checkUpdate(true) },
            Triple("Sair da conta", Pair(R.drawable.ic_plus, red)) { logout() },
        )
        if (isAdminUser) {
            section("ADMIN",
                Triple("Painel admin", Pair(R.drawable.ic_shield, red)) { showAdmin() },
            )
        }
        addLine(c, "Versão " + try {
            packageManager.getPackageInfo(packageName, 0).versionCode
        } catch (e: Throwable) { 0 }, 12f, palette.muted)
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
