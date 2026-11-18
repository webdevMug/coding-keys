package com.codingkeyboard

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration

object Prefs {
    fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("coding_keyboard", Context.MODE_PRIVATE)
}

fun Context.dp(n: Int): Int = (n * resources.displayMetrics.density + 0.5f).toInt()

class Palette(
    val bg: Int,
    val key: Int,
    val pressed: Int,
    val special: Int,
    val accent: Int,
    val accentText: Int,
    val text: Int,
    val image: Boolean = false
)

object Palettes {
    private val dark = Palette(
        0xFF121016.toInt(), 0xFF2A2730.toInt(), 0xFF454050.toInt(), 0xFF1D1A23.toInt(),
        0xFF35476B.toInt(), 0xFFD9E4FF.toInt(), 0xFFECE6F0.toInt()
    )
    private val light = Palette(
        0xFFE9E6EE.toInt(), 0xFFFFFFFF.toInt(), 0xFFCFCAD8.toInt(), 0xFFD8D4E0.toInt(),
        0xFFC6D6FA.toInt(), 0xFF1B3A75.toInt(), 0xFF1C1B20.toInt()
    )
    private val gold = Palette(
        0xFF121016.toInt(), 0xFF2A2730.toInt(), 0xFF5A4A1E.toInt(), 0xFF1D1A23.toInt(),
        0xFFD4AF37.toInt(), 0xFF1A1405.toInt(), 0xFFF3E9C8.toInt()
    )
    private val glass = Palette(
        0x33000000.toInt(), 0x40FFFFFF.toInt(), 0x73FFFFFF.toInt(), 0x26FFFFFF.toInt(),
        0x806FA8FF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()
    )
    private val photo = Palette(
        0xFF121016.toInt(), 0x8C000000.toInt(), 0xCC000000.toInt(), 0x59000000.toInt(),
        0xCC3D5A99.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), true
    )

    fun pick(c: Context): Palette {
        val t = Prefs.sp(c).getString("theme", "dark")
        val night = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return when (t) {
            "light" -> light
            "gold" -> gold
            "transparent" -> glass
            "image" -> photo
            "system" -> if (night) dark else light
            else -> dark
        }
    }
}
