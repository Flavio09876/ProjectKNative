package br.com.projectk.nativeapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.os.Bundle
import android.text.Html
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.CookieHandler
import java.net.HttpCookie
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val baseUrl = "https://projectspacek.squareweb.app"
    private val red = Color.rgb(220, 35, 55)
    private val bg = Color.rgb(18, 18, 22)
    private val panel = Color.rgb(29, 29, 35)

    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var loginInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        CookieHandler.setDefault(
            CookieManager(null, CookiePolicy.ACCEPT_ALL)
        )

        showLogin()
        restoreCookies()
        if (!handleAuthIntent(intent)) tryRestoreSession()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private val prefs by lazy { getSharedPreferences("projectk_session", MODE_PRIVATE) }

    private fun cookieStore() =
        (CookieHandler.getDefault() as? CookieManager)?.cookieStore

    private fun saveCookies() {
        val list = cookieStore()?.get(URI(baseUrl)).orEmpty()
        val data = list.joinToString("\n") {
            listOf(it.name, it.value, it.path ?: "/").joinToString("\t")
        }
        prefs.edit().putString("cookies", data).apply()
    }

    private fun restoreCookies() {
        val data = prefs.getString("cookies", null) ?: return
        data.lines().filter { it.isNotBlank() }.forEach { line ->
            val p = line.split("\t")
            if (p.size >= 3) {
                val c = HttpCookie(p[0], p[1]).apply {
                    path = p[2]
                    domain = URI(baseUrl).host
                    maxAge = -1
                    secure = true
                    isHttpOnly = true
                }
                cookieStore()?.add(URI(baseUrl), c)
            }
        }
    }

    private fun tryRestoreSession() {
        if (prefs.getString("cookies", null).isNullOrBlank()) return
        thread {
            val ok = try { fetchPage("/home"); true } catch (e: Exception) { false }
            runOnUiThread { if (ok) showHome("Sessão restaurada.") }
        }
    }

    private fun b64url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun startDiscordLogin() {
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val verifier = b64url(raw)
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        prefs.edit().putString("verifier", verifier).apply()
        status.text = "Abrindo o navegador para entrar com o Discord…"
        status.setTextColor(Color.LTGRAY)
        val uri = Uri.parse("$baseUrl/auth/discord/app").buildUpon()
            .appendQueryParameter("challenge", challenge).build()
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun handleAuthIntent(i: Intent?): Boolean {
        if (i == null) return false
        val data = i.data ?: return false
        if (data.scheme != "projectk" || data.host != "auth") return false
        i.data = null
        val code = data.getQueryParameter("code")
        val verifier = prefs.getString("verifier", null)
        if (code.isNullOrBlank() || verifier.isNullOrBlank()) {
            status.text = "Login com Discord cancelado ou inválido. Tente novamente."
            status.setTextColor(red)
            return true
        }
        prefs.edit().remove("verifier").apply()
        busy = true
        status.text = "Finalizando login…"
        thread {
            var ok = false
            var message = "Não foi possível concluir o login com Discord."
            try {
                val form = "code=${enc(code)}&verifier=${enc(verifier)}"
                val conn = (URL("$baseUrl/auth/app/exchange").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 15000
                    instanceFollowRedirects = false
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    setRequestProperty("User-Agent", "ProjectK-Android/0.3")
                }
                try {
                    conn.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
                    val rc = conn.responseCode
                    if (rc in 200..299) {
                        fetchPage("/home")
                        ok = true
                    } else {
                        message = "O servidor recusou o login (HTTP $rc). Tente de novo."
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                message = "Falha: ${e.message ?: e.javaClass.simpleName}"
            }
            runOnUiThread {
                busy = false
                if (ok) {
                    saveCookies()
                    showHome("Login com Discord confirmado.")
                } else {
                    status.text = message
                    status.setTextColor(red)
                }
            }
        }
        return true
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

        root.addView(label("K", 54f).apply {
            setTextColor(red)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, params())

        root.addView(label("PROJECT K", 23f).apply {
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, params())

        root.addView(label("Entre na sua conta", 14f).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
        }, params())

        addSpace(30)

        root.addView(Button(this).apply {
            text = "Entrar com Discord"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.rgb(88, 101, 242))
            )
            setOnClickListener { startDiscordLogin() }
        }, params())
        addSpace(8)
        root.addView(label("Você será levado ao navegador para entrar no Discord. O app nunca vê sua senha do Discord.", 12f).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
        }, params())

        addSpace(26)
        root.addView(label("ou use usuário e senha", 12f).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
        }, params())
        addSpace(12)

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
        status = label("Conexão com o servidor: $baseUrl", 12f).apply {
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        root.addView(status, params())

        addSpace(18)
        root.addView(label("Aplicativo nativo em desenvolvimento", 12f).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
        }, params())
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
                    saveCookies()
                    showHome(message)
                } else {
                    status.text = message
                    status.setTextColor(red)
                    loginButton.isEnabled = true
                }
            }
        }
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, "UTF-8")

    private var activeTab = "Início"

    private val ink = Color.rgb(8, 9, 14)
    private val glass = Color.rgb(23, 25, 35)
    private val muted = Color.rgb(160, 164, 181)
    private val violet = Color.rgb(157, 111, 255)

    private fun rounded(color: Int, radius: Float = 22f): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius * resources.displayMetrics.density
            setStroke(dp(1), Color.argb(45, 190, 180, 255))
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
            setPadding(dp(17), dp(15), dp(17), dp(15))
            background = rounded(glass)
            layoutParams = params().apply { bottomMargin = dp(13) }
        }
    }

    private fun addLine(parent: LinearLayout, value: String, size: Float = 14f, color: Int = Color.WHITE) {
        parent.addView(text(value, size, color), params().apply {
            bottomMargin = dp(7)
        })
    }

    private fun pill(value: String, action: () -> Unit): TextView {
        return text(value, 13f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setPadding(dp(15), dp(11), dp(15), dp(11))
            background = rounded(Color.rgb(43, 36, 65), 18f)
            setOnClickListener { action() }
        }
    }


    private fun fetchPage(path: String): String {
        val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "text/html")
            setRequestProperty("User-Agent", "ProjectK-Android/0.2")
        }
        return try {
            val code = conn.responseCode
            val stream = if (code < 400) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code >= 400) throw Exception("HTTP $code")
            if (conn.url.path.trimEnd('/').endsWith("/login")) {
                throw Exception("Sessão expirada. Entre novamente.")
            }
            body
        } finally {
            conn.disconnect()
        }
    }


    private fun loadRealFeed(onResult: (List<String>, String?) -> Unit) {
        thread {
            try {
                val html = fetchPage("/home")
                val extracted = Regex(
                    """(?is)<article\b[^>]*>(.*?)</article>"""
                ).findAll(html).map { match ->
                    Html.fromHtml(
                        match.groupValues[1]
                            .replace(Regex("(?is)<(script|style)\b.*?</\\1>"), "")
                            .replace(Regex("(?is)<img\b[^>]*>"), " [imagem] "),
                        Html.FROM_HTML_MODE_COMPACT
                    ).toString().replace(Regex("\\s+"), " ").trim()
                }.filter { it.isNotBlank() && it.length > 8 }.toList()

                runOnUiThread { onResult(extracted, null) }
            } catch (e: Exception) {
                runOnUiThread {
                    onResult(emptyList(), e.message ?: "erro desconhecido")
                }
            }
        }
    }

    private fun showHome(message: String) {
        renderTab("Início", message)
    }

    private fun renderTab(tab: String, message: String = "") {
        activeTab = tab
        window.statusBarColor = ink
        window.navigationBarColor = ink

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ink)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(19), dp(13), dp(19), dp(13))
        }

        val brand = text("K", 29f, violet).apply {
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        top.addView(brand, LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.WRAP_CONTENT))
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(text("PROJECT K", 13f, Color.WHITE).apply {
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        heading.addView(text("seu espaço, do seu jeito", 11f, muted))
        top.addView(heading, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(pill("＋", { newPost() }))
        page.addView(top)

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            setPadding(dp(16), dp(8), dp(16), dp(12))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)
        page.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        when (tab) {
            "Início" -> {
                val hero = card().apply {
                    background = rounded(Color.rgb(36, 27, 58), 26f)
                }
                addLine(hero, "SEU CANTO NA INTERNET", 11f, violet)
                addLine(hero, "Um lugar para ser você.", 25f)
                addLine(hero, "Explore ideias, pessoas e comunidades.", 13f, muted)
                hero.addView(pill("＋ Criar publicação", { newPost() }))
                content.addView(hero)

                addLine(content, "Seu feed", 21f)
                addLine(content, "O que está acontecendo por aí", 12f, muted)

                val feedStatus = text("Carregando publicações reais…", 14f, muted)
                content.addView(feedStatus, params())

                loadRealFeed { posts, error ->
                    content.removeView(feedStatus)

                    if (posts.isEmpty()) {
                        val detail = if (error != null) {
                            "Falha ao carregar: $error"
                        } else {
                            "O HTML chegou, mas não encontrei blocos article. Precisamos ajustar o extrator."
                        }
                        addLine(content, detail, 13f, muted)
                    } else {
                        addLine(content, "Publicações reais", 18f, violet)
                        posts.forEach { postText ->
                            val postCard = card()
                            addLine(postCard, postText, 14f)
                            content.addView(postCard)
                        }
                    }
                }
                if (message.isNotBlank()) addLine(content, message, 11f, muted)
            }
            "Buscar" -> {
                addLine(content, "Descobrir", 27f)
                addLine(content, "Encontre assuntos e pessoas.", 13f, muted)
                val search = EditText(this).apply {
                    hint = "Buscar no Project K"
                    setHintTextColor(muted)
                    setTextColor(Color.WHITE)
                    textSize = 15f
                    setSingleLine(true)
                    setPadding(dp(15), dp(13), dp(15), dp(13))
                    background = rounded(glass)
                }
                content.addView(search, params().apply { topMargin = dp(14); bottomMargin = dp(18) })
                addLine(content, "Assuntos em destaque", 18f)
                listOf("# dreamcore", "# tecnologia", "# jogos", "# música", "# fotografia").forEachIndexed { i, v ->
                    val c = card()
                    addLine(c, "0${i + 1}    $v", 16f)
                    addLine(c, "Explore publicações sobre este assunto", 12f, muted)
                    content.addView(c)
                }
            }
            "Salas" -> {
                addLine(content, "Salas & comunidades", 25f)
                addLine(content, "Espaços para encontrar sua galera.", 13f, muted)
                listOf(
                    Triple("Dreamspace", "Arte, sonhos e lugares liminares", "128 membros"),
                    Triple("Pixel Club", "Jogos, mods e projetos independentes", "86 membros"),
                    Triple("After Hours", "Conversas tranquilas sem pressa", "42 membros")
                ).forEach { item ->
                    val c = card()
                    addLine(c, "◈  ${item.first}", 18f)
                    addLine(c, item.second, 13f, muted)
                    c.addView(pill(item.third + "  ·  Ver sala", {
                        Toast.makeText(this, "Tela demonstrativa: sala ainda não conectada.", Toast.LENGTH_SHORT).show()
                    }))
                    content.addView(c)
                }
            }
            "Chat" -> {
                addLine(content, "Mensagens", 27f)
                addLine(content, "Suas conversas em um só lugar.", 13f, muted)
                listOf(
                    Triple("Mika", "me manda aquele link depois!", "09:42"),
                    Triple("Dreamspace", "nova conversa na comunidade", "Ontem"),
                    Triple("Rin", "essa estética ficou muito boa", "Seg.")
                ).forEach { item ->
                    val c = card()
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    row.addView(text("◉", 26f, violet), LinearLayout.LayoutParams(dp(45), ViewGroup.LayoutParams.WRAP_CONTENT))
                    val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    addLine(details, item.first, 16f)
                    addLine(details, item.second, 12f, muted)
                    row.addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(text(item.third, 11f, muted))
                    c.addView(row)
                    c.setOnClickListener {
                        Toast.makeText(this, "Conversas reais ainda não conectadas.", Toast.LENGTH_SHORT).show()
                    }
                    content.addView(c)
                }
            }
            "Perfil" -> {
                val cover = card().apply {
                    minimumHeight = dp(110)
                    gravity = Gravity.BOTTOM
                    background = rounded(Color.rgb(48, 35, 77), 25f)
                }
                addLine(cover, "PROJECT K  /  PROFILE", 11f, violet)
                content.addView(cover)
                val profile = card()
                addLine(profile, "◉", 39f, violet)
                addLine(profile, "Usuário do Project K", 22f)
                addLine(profile, "@seu_usuario", 13f, muted)
                addLine(profile, "Sua bio aparece aqui. Personalize seu espaço e compartilhe o que importa.", 14f)
                val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                listOf("0 publicações", "0 seguidores", "0 seguindo").forEach {
                    stats.addView(text(it, 11f, muted), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
                profile.addView(stats)
                content.addView(profile)
                val note = card()
                addLine(note, "Ainda em construção", 16f)
                addLine(note, "Perfil, publicações e seguidores serão ligados aos dados reais numa próxima etapa.", 13f, muted)
                content.addView(note)
                content.addView(makeButton("Sair da conta") {
                    (CookieHandler.getDefault() as? CookieManager)?.cookieStore?.removeAll()
                    prefs.edit().remove("cookies").apply()
                    showLogin()
                }, params())
            }
        }

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(8), dp(5), dp(8))
            background = rounded(Color.rgb(18, 19, 28), 20f)
        }
        listOf("Início", "Buscar", "Salas", "Chat", "Perfil").forEach { name ->
            val selected = name == tab
            val item = TextView(this).apply {
                text = when (name) {
                    "Início" -> "⌂\nInício"
                    "Buscar" -> "⌕\nBuscar"
                    "Salas" -> "◇\nSalas"
                    "Chat" -> "▤\nChat"
                    else -> "◉\nPerfil"
                }
                gravity = Gravity.CENTER
                textSize = 11f
                setTextColor(if (selected) violet else muted)
                setPadding(dp(2), dp(5), dp(2), dp(5))
                setOnClickListener { renderTab(name) }
            }
            nav.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        page.addView(nav, params().apply {
            setMargins(dp(12), dp(4), dp(12), dp(8))
        })
        setContentView(page)
    }

    private fun newPost() {
        val box = EditText(this).apply {
            hint = "O que está acontecendo?"
            setHintTextColor(muted)
            setTextColor(Color.WHITE)
            textSize = 16f
            minLines = 4
            gravity = Gravity.TOP
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(glass)
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
            setBackgroundColor(ink)
            addView(text("Nova publicação", 23f), params())
            addView(text("Rascunho local de demonstração", 12f, muted), params())
            addView(box, params().apply { topMargin = dp(16); bottomMargin = dp(14) })
            addView(makeButton("Voltar ao feed") { renderTab("Início") }, params())
        }
        setContentView(wrap)
    }

    private fun testApi() {
        thread {
            val result = try {
                val conn = (URL("$baseUrl/api/status").openConnection()
                    as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 12000
                    setRequestProperty("Accept", "application/json")
                }

                try {
                    val code = conn.responseCode
                    val stream = if (code < 400) conn.inputStream
                        else conn.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() }
                        ?: "(resposta vazia)"
                    "HTTP $code\n\n$body"
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                "Erro: ${e.javaClass.simpleName}: ${e.message}"
            }

            runOnUiThread {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Status da API")
                    .setMessage(result)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}
