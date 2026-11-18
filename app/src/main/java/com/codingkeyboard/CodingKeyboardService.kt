package com.codingkeyboard

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

enum class K { CHAR, PAIR, CLOSE, SHIFT, BACK, ENTER, SPACE, TAB, LEFT, RIGHT, UNDO, REDO, TOGGLE, COMMA }

class Key(
    val label: String,
    val kind: K,
    val text: String = label,
    val weight: Float = 1f,
    val style: Int = 0,
    val back: Int = 0,
    val spacer: Boolean = false
)

class CodingKeyboardService : InputMethodService() {

    private val handler = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var symbols = false
    private var shift = false
    private var caps = false
    private var lastShiftTap = 0L
    private var longFired = false
    private var cancelled = false
    private var longTask: Runnable? = null
    private var repeatTask: Runnable? = null
    private var gear: PopupWindow? = null
    private val repeatable = setOf(K.BACK, K.LEFT, K.RIGHT)
    private val pairs = mapOf('(' to ')', '[' to ']', '{' to '}', '"' to '"', '\'' to '\'', '`' to '`')

    private val sp get() = Prefs.sp(this)

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.VERTICAL
        root = r
        fill()
        return r
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) {
            symbols = false
            shift = false
            caps = false
        }
        fill()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        stopTimers()
        dismissGear()
    }

    private fun c(s: String) = Key(s, K.CHAR)
    private fun spacer() = Key("", K.CHAR, weight = 0.5f, spacer = true)

    private fun rows(): List<List<Key>> {
        val list = mutableListOf<List<Key>>()
        if (sp.getBoolean("show_util", true)) {
            list.add(
                listOf(
                    Key("\u2190", K.LEFT, style = 1),
                    Key("\u2192", K.RIGHT, style = 1),
                    Key("UNDO", K.UNDO, style = 1),
                    Key("REDO", K.REDO, style = 1)
                )
            )
        }
        if (symbols) {
            list.add("1234567890".map { c(it.toString()) })
            list.add(
                listOf(
                    Key("(", K.PAIR), Key(")", K.CLOSE), Key("{", K.PAIR), Key("}", K.CLOSE),
                    Key("[", K.PAIR), Key("]", K.CLOSE), c("<"), c(">"), c("="), c(";")
                )
            )
            list.add("+-*/%&|!?_".map { c(it.toString()) })
            list.add(
                listOf(
                    c("#"), c("@"), c("\$"), c("\\"), Key("'", K.PAIR), Key("\"", K.PAIR),
                    Key("`", K.PAIR), c("~"), c("^"), c(":")
                )
            )
            list.add(
                listOf(
                    Key("</>", K.CHAR, "<>", 1.3f, back = 1),
                    Key("=>", K.CHAR, weight = 1.1f),
                    Key("===", K.CHAR, weight = 1.3f),
                    c("."),
                    Key(",", K.COMMA),
                    Key("\u232B", K.BACK, style = 1, weight = 1.2f)
                )
            )
            list.add(
                listOf(
                    Key("ABC", K.TOGGLE, style = 2, weight = 1.4f),
                    Key("TAB", K.TAB, style = 1, weight = 1.4f),
                    Key("SPACE", K.SPACE, style = 1, weight = 3.5f),
                    Key("ENTER", K.ENTER, style = 1, weight = 1.6f)
                )
            )
        } else {
            list.add("qwertyuiop".map { c(it.toString()) })
            list.add(listOf(spacer()) + "asdfghjkl".map { c(it.toString()) } + spacer())
            list.add(
                listOf(Key("\u21E7", K.SHIFT, style = if (shift || caps) 2 else 1, weight = 1.4f)) +
                    "zxcvbnm".map { c(it.toString()) } +
                    Key("\u232B", K.BACK, style = 1, weight = 1.4f)
            )
            list.add(
                listOf(
                    Key("SYM", K.TOGGLE, style = 2, weight = 1.4f),
                    Key("TAB", K.TAB, style = 1, weight = 1.3f),
                    Key(",", K.COMMA),
                    Key("SPACE", K.SPACE, style = 1, weight = 3f),
                    c("."),
                    Key("ENTER", K.ENTER, style = 1, weight = 1.6f)
                )
            )
        }
        return list
    }

    private fun fill() {
        val r = root ?: return
        val p = Palettes.pick(this)
        r.setBackgroundColor(p.bg)
        r.setPadding(dp(4), dp(6), dp(4), dp(8))
        r.removeAllViews()
        val h = dp(
            when (sp.getString("key_height", "medium")) {
                "small" -> 40
                "large" -> 54
                else -> 47
            }
        )
        for (row in rows()) {
            val rl = LinearLayout(this)
            rl.orientation = LinearLayout.HORIZONTAL
            for (k in row) {
                val lp = LinearLayout.LayoutParams(0, h, k.weight)
                lp.setMargins(dp(2), dp(3), dp(2), dp(3))
                if (k.spacer) rl.addView(View(this), lp) else rl.addView(makeKey(k, p), lp)
            }
            r.addView(
                rl,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun rounded(color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(8).toFloat()
        return g
    }

    private fun isLetterKey(k: Key) = k.kind == K.CHAR && k.text.length == 1 && k.text[0].isLetter()

    private fun makeKey(k: Key, p: Palette): View {
        val tv = TextView(this)
        tv.text = if (isLetterKey(k) && (shift || caps)) k.label.uppercase() else k.label
        tv.gravity = Gravity.CENTER
        tv.setTextColor(if (k.style == 2) p.accentText else p.text)
        val size = when {
            k.style != 0 -> 13f
            k.label.length > 1 -> 16f
            else -> 19f
        }
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        tv.typeface = if (k.style == 0) Typeface.MONOSPACE else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val base = when (k.style) {
            2 -> p.accent
            1 -> p.special
            else -> p.key
        }
        val bg = StateListDrawable()
        bg.addState(intArrayOf(android.R.attr.state_pressed), rounded(p.pressed))
        bg.addState(intArrayOf(), rounded(base))
        tv.background = bg
        tv.setOnTouchListener { v, e ->
            touch(v, k, e)
            true
        }
        return tv
    }

    private fun touch(v: View, k: Key, e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.isPressed = true
                cancelled = false
                longFired = false
                if (sp.getBoolean("vibrate", true)) {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                if (k.kind in repeatable) {
                    perform(k)
                    startRepeat(k)
                } else if (k.kind == K.COMMA) {
                    val t = Runnable {
                        longFired = true
                        showGear(v)
                    }
                    longTask = t
                    handler.postDelayed(t, sp.getInt("long_press_ms", 400).toLong())
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val inside = e.x >= 0 && e.x <= v.width && e.y >= 0 && e.y <= v.height
                if (!inside && !longFired && !cancelled) {
                    cancelled = true
                    stopTimers()
                    v.isPressed = false
                }
            }
            MotionEvent.ACTION_UP -> {
                v.isPressed = false
                stopTimers()
                dismissGear()
                if (cancelled) return
                when {
                    k.kind in repeatable -> {}
                    k.kind == K.COMMA && longFired -> {
                        val near = e.x > -v.width && e.x < v.width * 2 &&
                            e.y > -v.height * 3 && e.y < v.height * 2
                        if (near) openSettings()
                    }
                    else -> perform(k)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                v.isPressed = false
                stopTimers()
                dismissGear()
            }
        }
    }

    private fun startRepeat(k: Key) {
        val t = object : Runnable {
            override fun run() {
                perform(k)
                handler.postDelayed(this, 45)
            }
        }
        repeatTask = t
        handler.postDelayed(t, 400)
    }

    private fun stopTimers() {
        longTask?.let { handler.removeCallbacks(it) }
        repeatTask?.let { handler.removeCallbacks(it) }
        longTask = null
        repeatTask = null
    }

    private fun sendKey(ic: InputConnection, code: Int, meta: Int = 0) {
        val t = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
    }

    private fun perform(k: Key) {
        if (k.kind == K.TOGGLE) {
            symbols = !symbols
            fill()
            return
        }
        if (k.kind == K.SHIFT) {
            val now = SystemClock.uptimeMillis()
            if (caps) {
                caps = false
                shift = false
            } else if (shift && now - lastShiftTap < 400) {
                caps = true
            } else {
                shift = !shift
            }
            lastShiftTap = now
            fill()
            return
        }
        val ic = currentInputConnection ?: return
        val auto = sp.getBoolean("autopair", true)
        when (k.kind) {
            K.CHAR, K.COMMA -> {
                var t = k.text
                if (t.length == 1 && t[0].isLetter() && (shift || caps)) {
                    t = t.uppercase()
                    if (!caps) {
                        shift = false
                        fill()
                    }
                }
                ic.commitText(t, 1)
                repeat(k.back) { sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT) }
            }
            K.PAIR -> {
                val ch = k.text[0]
                if (!auto) {
                    ic.commitText(k.text, 1)
                } else {
                    val isQuote = ch == '"' || ch == '\'' || ch == '`'
                    val after = ic.getTextAfterCursor(1, 0)?.toString().orEmpty()
                    val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
                    if (isQuote && after == k.text) {
                        sendKey(ic, KeyEvent.KEYCODE_DPAD_RIGHT)
                    } else if (isQuote && before.isNotEmpty() && before[0].isLetterOrDigit()) {
                        ic.commitText(k.text, 1)
                    } else {
                        ic.commitText(k.text + pairs.getValue(ch), 1)
                        sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT)
                    }
                }
            }
            K.CLOSE -> {
                val after = ic.getTextAfterCursor(1, 0)?.toString().orEmpty()
                if (auto && after == k.text) sendKey(ic, KeyEvent.KEYCODE_DPAD_RIGHT)
                else ic.commitText(k.text, 1)
            }
            K.BACK -> {
                val sel = ic.getSelectedText(0)
                if (!sel.isNullOrEmpty()) {
                    ic.commitText("", 1)
                } else {
                    val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
                    val after = ic.getTextAfterCursor(1, 0)?.toString().orEmpty()
                    if (before.isEmpty()) {
                        sendKey(ic, KeyEvent.KEYCODE_DEL)
                    } else if (auto && after.isNotEmpty() && pairs[before[0]] == after[0]) {
                        ic.deleteSurroundingText(1, 1)
                    } else {
                        ic.deleteSurroundingText(1, 0)
                    }
                }
            }
            K.ENTER -> {
                val info = currentInputEditorInfo
                val multi = info != null && (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
                val noAction = info != null && (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                val action = if (info != null) info.imeOptions and EditorInfo.IME_MASK_ACTION else EditorInfo.IME_ACTION_NONE
                if (!multi && !noAction && action != EditorInfo.IME_ACTION_NONE &&
                    action != EditorInfo.IME_ACTION_UNSPECIFIED
                ) {
                    ic.performEditorAction(action)
                } else {
                    sendKey(ic, KeyEvent.KEYCODE_ENTER)
                }
            }
            K.SPACE -> ic.commitText(" ", 1)
            K.TAB -> ic.commitText(" ".repeat(sp.getInt("tab_width", 2)), 1)
            K.LEFT -> sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT)
            K.RIGHT -> sendKey(ic, KeyEvent.KEYCODE_DPAD_RIGHT)
            K.UNDO -> sendKey(ic, KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
            K.REDO -> sendKey(
                ic, KeyEvent.KEYCODE_Z,
                KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
            )
            K.SHIFT, K.TOGGLE -> {}
        }
    }

    private fun openSettings() {
        val i = Intent(this, SettingsActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        requestHideSelf(0)
    }

    private fun showGear(anchor: View) {
        val p = Palettes.pick(this)
        val iv = ImageView(this)
        iv.setImageResource(R.drawable.ic_gear)
        iv.setColorFilter(p.accentText)
        iv.setPadding(dp(10), dp(10), dp(10), dp(10))
        iv.background = rounded(p.accent)
        val pw = PopupWindow(iv, dp(48), dp(48), false)
        pw.isTouchable = false
        pw.isClippingEnabled = false
        try {
            pw.showAsDropDown(anchor, (anchor.width - dp(48)) / 2, -(anchor.height + dp(48) + dp(6)))
            gear = pw
        } catch (e: Exception) {
        }
        if (sp.getBoolean("vibrate", true)) {
            anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private fun dismissGear() {
        try {
            gear?.dismiss()
        } catch (e: Exception) {
        }
        gear = null
    }
}
