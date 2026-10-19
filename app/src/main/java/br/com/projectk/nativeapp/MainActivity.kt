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

    private fun showHome(message: String) {
        makeRoot()

        root.addView(label("K", 48f).apply {
            setTextColor(red)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, params())

        root.addView(label("Você entrou no Project K", 23f).apply {
            gravity = Gravity.CENTER
        }, params())

        addSpace(12)
        root.addView(label(message, 14f).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
        }, params())

        addSpace(28)
        root.addView(makeButton("Testar API") {
            testApi()
        }, params())

        addSpace(12)
        root.addView(makeButton("Sair desta tela") {
            (CookieHandler.getDefault() as? CookieManager)
                ?.cookieStore?.removeAll()
            showLogin()
        }, params())

        addSpace(20)
        root.addView(label(
            "A sessão está guardada em cookies durante a execução. " +
                "O feed, as mensagens e as comunidades nativas ainda precisam ser conectados.",
            13f
        ).apply {
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }, params())
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
