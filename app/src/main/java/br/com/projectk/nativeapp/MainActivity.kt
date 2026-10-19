package br.com.projectk.nativeapp

import android.app.Activity
import android.os.Bundle
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

        addSpace(36)

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
            cornerRadius = dp(radius)
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

                val posts = listOf(
                    Triple("noct", "Hoje o céu parece uma tela de carregamento infinita.", "♡ 24     ◯ 8 comentários"),
                    Triple("luna.exe", "Alguém mais gosta daquela sensação de explorar lugares vazios de madrugada?", "♡ 18     ◯ 5 comentários"),
                    Triple("void", "Pequenas coisas também merecem ser compartilhadas.", "♡ 31     ◯ 12 comentários")
                )
                posts.forEachIndexed { i, post ->
                    val c = card()
                    addLine(c, "◉   ${post.first}     ·     ${i + 2} h", 13f, violet)
                    addLine(c, post.second, 16f)
                    View(this).apply {
                        setBackgroundColor(Color.rgb(48, 49, 64))
                        c.addView(this, LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
                        ).apply { topMargin = dp(8); bottomMargin = dp(8) })
                    }
                    addLine(c, post.third, 12f, muted)
                    content.addView(c)
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
                    singleLine = true
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
