package br.com.projectk.nativeapp

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val baseUrl = "https://projectspacek.squareweb.app"
    private lateinit var resultText: TextView
    private lateinit var testButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 28)
            setBackgroundColor(Color.rgb(18, 18, 22))
        }

        val title = TextView(this).apply {
            text = "PROJECT K — NATIVO"
            textSize = 23f
            setTextColor(Color.rgb(220, 35, 55))
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Teste de conexão HTTP direto do Android"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 24)
        }

        testButton = Button(this).apply {
            text = "TESTAR API /api/status"
            setOnClickListener { testApi() }
        }

        resultText = TextView(this).apply {
            text = "Pronto para testar.\n\nServidor:\n$baseUrl"
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, 24, 0, 0)
        }

        root.addView(title, matchWidth())
        root.addView(subtitle, matchWidth())
        root.addView(testButton, matchWidth())
        root.addView(resultText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun testApi() {
        testButton.isEnabled = false
        resultText.text = "Conectando ao servidor..."
        thread {
            val output = try {
                val url = URL("$baseUrl/api/status")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 12000
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val code = conn.responseCode
                    val stream = if (code in 200..399) conn.inputStream else conn.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() } ?: "(resposta vazia)"
                    val prettyBody = try {
                        JSONObject(body).toString(2)
                    } catch (_: Exception) {
                        body
                    }
                    "HTTP $code\n\nURL: $baseUrl/api/status\n\nResposta:\n$prettyBody"
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                "FALHA NA CONEXÃO\n\n${e.javaClass.simpleName}: ${e.message}\n\nVerifique a internet e tente novamente."
            }

            runOnUiThread {
                resultText.text = output
                testButton.isEnabled = true
            }
        }
    }
}
