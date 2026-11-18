package com.codingkeyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {

    private val bg = 0xFF151219.toInt()
    private val fg = 0xFFE9E1EC.toInt()
    private val dim = 0xFFB3AAB9.toInt()
    private lateinit var col: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(bg)
        col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(28), dp(20), dp(28))
        scroll.addView(col)
        setContentView(scroll)
        val section = intent.getStringExtra("section")
        if (section == null) main() else sub(section)
    }

    private fun sp() = Prefs.sp(this)

    private fun open(section: String) {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra("section", section))
    }

    private fun title(t: String) {
        val tv = TextView(this)
        tv.text = t
        tv.setTextColor(fg)
        tv.textSize = 26f
        tv.setPadding(0, 0, 0, dp(12))
        col.addView(tv)
    }

    private fun note(t: String) {
        val tv = TextView(this)
        tv.text = t
        tv.setTextColor(dim)
        tv.textSize = 14f
        tv.setPadding(0, 0, 0, dp(16))
        col.addView(tv)
    }

    private fun chevron(): View {
        val tv = TextView(this)
        tv.text = "\u203A"
        tv.setTextColor(dim)
        tv.textSize = 28f
        return tv
    }

    private fun item(
        t: String,
        s: String?,
        icon: Int? = null,
        trailing: View? = null,
        click: (() -> Unit)? = null
    ) {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(14), 0, dp(14))
        if (icon != null) {
            val iv = ImageView(this)
            iv.setImageResource(icon)
            iv.setColorFilter(fg)
            val lp = LinearLayout.LayoutParams(dp(24), dp(24))
            lp.rightMargin = dp(20)
            r.addView(iv, lp)
        }
        val tc = LinearLayout(this)
        tc.orientation = LinearLayout.VERTICAL
        val a = TextView(this)
        a.text = t
        a.setTextColor(fg)
        a.textSize = 18f
        tc.addView(a)
        if (s != null) {
            val b = TextView(this)
            b.text = s
            b.setTextColor(dim)
            b.textSize = 14f
            tc.addView(b)
        }
        r.addView(tc, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (trailing != null) r.addView(trailing)
        if (click != null) {
            r.isClickable = true
            r.setOnClickListener { click() }
        }
        col.addView(r)
    }

    private fun toggle(t: String, s: String?, key: String, def: Boolean) {
        val sw = Switch(this)
        sw.isChecked = sp().getBoolean(key, def)
        sw.setOnCheckedChangeListener { _, on -> sp().edit().putBoolean(key, on).apply() }
        item(t, s, null, sw) { sw.toggle() }
    }

    private fun choice(t: String, key: String, labels: List<String>, values: List<String>, def: String) {
        val cur = sp().getString(key, def) ?: def
        val idx = values.indexOf(cur).coerceAtLeast(0)
        item(t, labels[idx]) {
            AlertDialog.Builder(this)
                .setTitle(t)
                .setSingleChoiceItems(labels.toTypedArray(), idx) { d, w ->
                    sp().edit().putString(key, values[w]).apply()
                    d.dismiss()
                    recreate()
                }
                .show()
        }
    }

    private fun choiceInt(t: String, key: String, labels: List<String>, values: List<Int>, def: Int) {
        val cur = sp().getInt(key, def)
        val idx = values.indexOf(cur).coerceAtLeast(0)
        item(t, labels[idx]) {
            AlertDialog.Builder(this)
                .setTitle(t)
                .setSingleChoiceItems(labels.toTypedArray(), idx) { d, w ->
                    sp().edit().putInt(key, values[w]).apply()
                    d.dismiss()
                    recreate()
                }
                .show()
        }
    }

    private fun main() {
        title("Coding Keyboard")
        note("Long-press the comma key on the keyboard to open this screen.")

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(16), dp(16), dp(16), dp(16))
        val shape = GradientDrawable()
        shape.setColor(0xFF211D27.toInt())
        shape.cornerRadius = dp(16).toFloat()
        card.background = shape

        val head = TextView(this)
        head.text = "Set up"
        head.setTextColor(fg)
        head.textSize = 18f
        card.addView(head)

        val b1 = Button(this)
        b1.text = "1. Enable keyboard"
        b1.setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        card.addView(b1)

        val b2 = Button(this)
        b2.text = "2. Switch to Coding Keyboard"
        b2.setOnClickListener {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        card.addView(b2)

        val et = EditText(this)
        et.hint = "Type here to try the keyboard"
        et.setTextColor(fg)
        et.setHintTextColor(dim)
        et.minLines = 3
        et.gravity = Gravity.TOP
        card.addView(et)

        val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        clp.bottomMargin = dp(12)
        col.addView(card, clp)

        item("Languages & Layouts", "English (US)", android.R.drawable.ic_menu_mapmode, chevron()) { open("languages") }
        item("Preferences", null, android.R.drawable.ic_menu_preferences, chevron()) { open("preferences") }
        item("Appearance", null, android.R.drawable.ic_menu_gallery, chevron()) { open("appearance") }
        item("Toolbar", null, android.R.drawable.ic_menu_more, chevron()) { open("toolbar") }
        item("Text correction", null, android.R.drawable.ic_menu_edit, chevron()) { open("text_correction") }
        item("Secondary layouts", null, android.R.drawable.ic_menu_sort_by_size, chevron()) { open("secondary") }
        item("Dictionaries", null, android.R.drawable.ic_menu_agenda, chevron()) { open("dictionaries") }
        item("Advanced", null, android.R.drawable.ic_menu_manage, chevron()) { open("advanced") }
        item("About", null, android.R.drawable.ic_menu_info_details, chevron()) { open("about") }
    }

    private fun sub(section: String) {
        when (section) {
            "languages" -> {
                title("Languages & Layouts")
                item("English (US)", "QWERTY layout built for coding. More languages and layouts are planned.")
            }
            "preferences" -> {
                title("Preferences")
                toggle("Vibrate on keypress", null, "vibrate", true)
                toggle(
                    "Auto-pair brackets and quotes",
                    "Typing ( adds ) and puts the cursor between them.",
                    "autopair", true
                )
                choiceInt("Tab width", "tab_width", listOf("2 spaces", "4 spaces"), listOf(2, 4), 2)
            }
            "appearance" -> {
                title("Appearance")
                choice(
                    "Theme", "theme",
                    listOf("Dark", "Light", "Follow system"), listOf("dark", "light", "system"), "dark"
                )
                choice(
                    "Key height", "key_height",
                    listOf("Small", "Medium", "Large"), listOf("small", "medium", "large"), "medium"
                )
            }
            "toolbar" -> {
                title("Toolbar")
                toggle(
                    "Show arrow and undo row",
                    "Left, right, undo, and redo keys above the letters.",
                    "show_util", true
                )
            }
            "text_correction" -> {
                title("Text correction")
                item(
                    "Autocorrect is off",
                    "Autocorrect, auto-capitalization, and suggestions stay off because they break code."
                )
            }
            "secondary" -> {
                title("Secondary layouts")
                item("Coming later", "Extra symbol pages, like one for Markdown or JSON, are planned for a later version.")
            }
            "dictionaries" -> {
                title("Dictionaries")
                item("Coming later", "Word suggestions and snippet lists are planned for a later version.")
            }
            "advanced" -> {
                title("Advanced")
                choiceInt(
                    "Long-press delay", "long_press_ms",
                    listOf("300 ms", "400 ms", "500 ms", "700 ms"), listOf(300, 400, 500, 700), 400
                )
            }
            "about" -> {
                title("About")
                item(
                    "Coding Keyboard 1.0",
                    "A keyboard for writing HTML, CSS, and JavaScript on your phone. Long-press the comma key to open settings."
                )
            }
            else -> main()
        }
    }
}
