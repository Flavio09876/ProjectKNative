package br.com.projectk.nativeapp

import android.content.Context
import android.graphics.Color

/**
 * Tokens nativos do design system do K.
 *
 * Cada entrada espelha um bloco [data-theme="..."] de
 * site-k/static/css/style.css e site-k/static/css/themes.css.
 */
data class KPalette(
    val key: String,
    val label: String,
    val bg: Int,
    val surface: Int,
    val surfaceRaised: Int,
    val surfaceHover: Int,
    val border: Int,
    val borderStrong: Int,
    val text: Int,
    val textSoft: Int,
    val muted: Int,
    val accent: Int,
    val accentHover: Int,
    val accentHi: Int,
    val accentLo: Int,
    val success: Int,
    val danger: Int,
    val info: Int,
    val radiusSm: Int,
    val radiusMd: Int,
    val radiusLg: Int,
    val radiusXl: Int,
    val mono: Boolean = false,
    val light: Boolean = false,
    val glow: Boolean = false,
)

object KTheme {
    const val PREF = "projectk"
    const val KEY_THEME = "theme"

    // Clássico = :root de style.css
    private val classic = KPalette(
        "classico", "Clássico",
        hex("#050506"), hex("#0e0e10"), hex("#151518"), hex("#1e1e22"),
        hex("#232327"), hex("#3a3a41"),
        hex("#f5f5f6"), hex("#c4c4cc"), hex("#858590"),
        hex("#e11d2e"), hex("#ff3347"), hex("#ff2d40"), hex("#8f0a16"),
        hex("#55c99a"), hex("#ff6474"), hex("#8eafff"),
        9, 15, 21, 28,
    )

    val all = listOf(
        classic,
        KPalette(
            "cyber", "Cyber",
            hex("#04050d"), hex("#0a0d1c"), hex("#0f1328"), hex("#161b38"),
            hex("#1a2350"), hex("#2b3a86"),
            hex("#eaf6ff"), hex("#b4cde6"), hex("#6f86b3"),
            hex("#00e5ff"), hex("#6af3ff"), hex("#6af3ff"), hex("#7b2ff7"),
            hex("#55c99a"), hex("#ff6474"), hex("#8eafff"),
            2, 3, 4, 6, mono = true, glow = true,
        ),
        KPalette(
            "sakura", "Sakura",
            hex("#160b13"), hex("#21121d"), hex("#2b1826"), hex("#37202f"),
            hex("#3d2535"), hex("#6b3f58"),
            hex("#fff1f6"), hex("#f0cddc"), hex("#b78fa4"),
            hex("#ff7aa8"), hex("#ffa3c2"), hex("#ffb0cd"), hex("#d8457a"),
            hex("#55c99a"), hex("#ff6474"), hex("#8eafff"),
            14, 20, 28, 36,
        ),
        KPalette(
            "terminal", "Terminal",
            hex("#000000"), hex("#030a05"), hex("#06130a"), hex("#0a1f10"),
            hex("#0e3a1a"), hex("#17a63c"),
            hex("#38ff7a"), hex("#2bd161"), hex("#1f8f45"),
            hex("#38ff7a"), hex("#8dffb3"), hex("#8dffb3"), hex("#0e7a30"),
            hex("#38ff7a"), hex("#ff6474"), hex("#8eafff"),
            0, 0, 0, 0, mono = true, glow = true,
        ),
        KPalette(
            "vidro", "Vidro",
            hex("#0b0b1a"), hex("#1d1d31"), hex("#282840"), hex("#33334f"),
            hex("#3a3a55"), hex("#60607f"),
            hex("#f6f7ff"), hex("#d3d6f0"), hex("#9aa0cc"),
            hex("#8b7bff"), hex("#b0a5ff"), hex("#b0a5ff"), hex("#4a3fd1"),
            hex("#55c99a"), hex("#ff6474"), hex("#8eafff"),
            12, 18, 26, 34,
        ),
        KPalette(
            "sangue", "Sangue",
            hex("#070000"), hex("#0f0304"), hex("#170507"), hex("#220a0d"),
            hex("#34090f"), hex("#6b1019"),
            hex("#f3dede"), hex("#cfaaaa"), hex("#8f5f63"),
            hex("#b3001e"), hex("#e0203b"), hex("#e0203b"), hex("#4a0009"),
            hex("#55c99a"), hex("#ff6474"), hex("#8eafff"),
            4, 6, 8, 10, glow = true,
        ),
        KPalette(
            "gameover", "Game Over",
            hex("#040406"), hex("#14141c"), hex("#1e1e26"), hex("#282830"),
            hex("#3a3a44"), hex("#60606e"),
            hex("#f3f3f6"), hex("#c9c9d3"), hex("#85858f"),
            hex("#e0202f"), hex("#ff4757"), hex("#ff4757"), hex("#7d0a14"),
            hex("#55c99a"), hex("#ff6474"), hex("#8eafff"),
            10, 16, 22, 30, glow = true,
        ),
        KPalette(
            "aurora", "Aurora",
            hex("#060b14"), hex("#111e2d"), hex("#162838"), hex("#1d3547"),
            hex("#254755"), hex("#3d7780"),
            hex("#eefcff"), hex("#c2dde6"), hex("#7f9aa8"),
            hex("#2ee6c5"), hex("#6ff5dc"), hex("#6ff5dc"), hex("#7a5cff"),
            hex("#55c99a"), hex("#ff6474"), hex("#8eafff"),
            12, 18, 26, 34, glow = true,
        ),
        KPalette(
            "oceano", "Oceano",
            hex("#04121f"), hex("#072238"), hex("#0a2d48"), hex("#0f3b5c"),
            hex("#14486e"), hex("#1f6a9c"),
            hex("#eaf7ff"), hex("#b9d9ee"), hex("#78a3c2"),
            hex("#22d3ee"), hex("#67e8f9"), hex("#67e8f9"), hex("#0ea5e9"),
            hex("#55c99a"), hex("#ff6474"), hex("#8eafff"),
            12, 18, 26, 32, glow = true,
        ),
        KPalette(
            "brutal", "Brutal",
            hex("#111111"), hex("#1a1a1a"), hex("#222222"), hex("#2c2c2c"),
            hex("#f5f5f5"), hex("#ffffff"),
            hex("#ffffff"), hex("#e0e0e0"), hex("#a8a8a8"),
            hex("#ffdd00"), hex("#ffe84d"), hex("#ffe84d"), hex("#ff9d00"),
            hex("#38d9ff"), hex("#ff4d6d"), hex("#8eafff"),
            0, 0, 0, 0,
        ),
        KPalette(
            "grimorio", "Grimório",
            hex("#0d0a08"), hex("#17110d"), hex("#1f1712"), hex("#2a1e15"),
            hex("#4a3418"), hex("#7a5a26"),
            hex("#f3e6c8"), hex("#d8c79f"), hex("#9c8760"),
            hex("#d4a437"), hex("#efc45a"), hex("#efc45a"), hex("#8a5d12"),
            hex("#55c99a"), hex("#ff6474"), hex("#8eafff"),
            3, 4, 6, 8,
        ),
        KPalette(
            "synth", "Synthwave",
            hex("#12002b"), hex("#220642"), hex("#340a60"), hex("#481078"),
            hex("#6b21a8"), hex("#ff4fd8"),
            hex("#fff4ff"), hex("#e8c8f5"), hex("#b58ad0"),
            hex("#ff4fd8"), hex("#ff86e6"), hex("#ff86e6"), hex("#00e5ff"),
            hex("#55c99a"), hex("#ff6474"), hex("#00e5ff"),
            8, 12, 16, 20, glow = true,
        ),
        KPalette(
            "branco", "Branco",
            hex("#ffffff"), hex("#ffffff"), hex("#f4f5f7"), hex("#eceef2"),
            hex("#e3e5ea"), hex("#c7cbd3"),
            hex("#1e1f22"), hex("#4a4d55"), hex("#6a6d77"),
            hex("#4f5bd5"), hex("#3d48b8"), hex("#6c78f0"), hex("#3b46b5"),
            hex("#1f9d6b"), hex("#d92d3f"), hex("#3b6fe0"),
            10, 14, 18, 24, light = true,
        ),
    )

    fun save(context: Context, key: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, key).apply()
    }

    fun current(context: Context): KPalette {
        val key = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_THEME, "classico") ?: "classico"
        return all.firstOrNull { it.key == key } ?: classic
    }

    private fun hex(v: String): Int = Color.parseColor(v)
}
