package com.codingkeyboard

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import java.io.File

enum class K { CHAR, PAIR, CLOSE, SHIFT, BACK, ENTER, SPACE, TAB, LEFT, RIGHT, UNDO, REDO, TOGGLE, COMMA }

class Key(
    val label: String,
    val kind: K,
    val text: String = label,
    val weight: Float = 1f,
    val style: Int = 0,
    val back: Int = 0,
    val spacer: Boolean = false,
    val icon: Int = 0
)

class CropDrawable(private val bmp: Bitmap, private val dim: Int) : Drawable() {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() == 0 || b.height() == 0) return
        val scale = maxOf(b.width().toFloat() / bmp.width, b.height().toFloat() / bmp.height)
        val sw = (b.width() / scale).toInt()
        val sh = (b.height() / scale).toInt()
        val sx = (bmp.width - sw) / 2
        val sy = (bmp.height - sh) / 2
        canvas.drawBitmap(bmp, Rect(sx, sy, sx + sw, sy + sh), b, paint)
        canvas.drawColor(dim)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}

class CodingKeyboardService : InputMethodService() {

    private companion object {
        const val INS = 0
        const val DEL = 1
        const val PAIR = 2
        const val UNPAIR = 3
        const val SELDEL = 4
    }

    private class Op(val kind: Int, var text: String)

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
    private var bgBitmap: Bitmap? = null
    private var bgStamp = 0L
    private var ownEditAt = 0L
    private val undoStack = ArrayList<Op>()
    private val redoStack = ArrayList<Op>()
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
            undoStack.clear()
            redoStack.clear()
        }
        fill()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        stopTimers()
        dismissGear()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (SystemClock.uptimeMillis() - ownEditAt > 600) {
            undoStack.clear()
            redoStack.clear()
        }
    }

    private fun c(s: String) = Key(s, K.CHAR)
    private fun spacer() = Key("", K.CHAR, weight = 0.5f, spacer = true)

    private fun rows(): List<List<Key>> {
        val list = mutableListOf<List<Key>>()
        if (sp.getBoolean("show_util", true)) {
            list.add(
                listOf(
                    Key("", K.LEFT, style = 1, icon = R.drawable.ic_arrow_left),
                    Key("", K.RIGHT, style = 1, icon = R.drawable.ic_arrow_right),
                    Key("", K.UNDO, style = 1, icon = R.drawable.ic_undo),
                    Key("", K.REDO, style = 1, icon = R.drawable.ic_redo)
                )
            )
        }
        val back = { w: Float -> Key("", K.BACK, style = 1, weight = w, icon = R.drawable.ic_backspace) }
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
                    back(1.2f)
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
                listOf(Key("", K.SHIFT, style = if (shift || caps) 2 else 1, weight = 1.4f, icon = R.drawable.ic_shift)) +
                    "zxcvbnm".map { c(it.toString()) } + back(1.4f)
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

    private fun loadBg(): Bitmap? {
        val f = File(filesDir, "keyboard_bg.jpg")
        if (!f.exists()) return null
        if (bgBitmap == null || bgStamp != f.lastModified()) {
            bgBitmap = BitmapFactory.decodeFile(f.path)
            bgStamp = f.lastModified()
        }
        return bgBitmap
    }

    private fun fill() {
        val r = root ?: return
        val p = Palettes.pick(this)
        val bmp = if (p.image) loadBg() else null
        if (bmp != null) r.background = CropDrawable(bmp, 0x44000000) else r.setBackgroundColor(p.bg)
        r.setPadding(dp(4), dp(6), dp(4), dp(8))
        r.removeAllViews()
        val h = dp(sp.getInt("key_height_dp", 47).coerceIn(20, 100))
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
        val box = FrameLayout(this)
        val fg = if (k.style == 2) p.accentText else p.text
        if (k.icon != 0) {
            val iv = ImageView(this)
            iv.setImageResource(k.icon)
            iv.setColorFilter(fg)
            val s = dp(22)
            box.addView(iv, FrameLayout.LayoutParams(s, s, Gravity.CENTER))
        } else {
            val tv = TextView(this)
            tv.text = if (isLetterKey(k) && (shift || caps)) k.label.uppercase() else k.label
            tv.gravity = Gravity.CENTER
            tv.setTextColor(fg)
            val size = when {
                k.style != 0 -> 13f
                k.label.length > 1 -> 16f
                else -> 19f
            }
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            tv.typeface = if (k.style == 0) Typeface.MONOSPACE else Typeface.create("sans-serif-medium", Typeface.NORMAL)
            box.addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val base = when (k.style) {
            2 -> p.accent
            1 -> p.special
            else -> p.key
        }
        val bg = StateListDrawable()
        bg.addState(intArrayOf(android.R.attr.state_pressed), rounded(p.pressed))
        bg.addState(intArrayOf(), rounded(base))
        box.background = bg
        box.contentDescription = if (k.label.isEmpty()) k.kind.name else k.label
        box.setOnTouchListener { v, e ->
            touch(v, k, e)
            true
        }
        return box
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

    private fun record(kind: Int, text: String, merge: Boolean) {
        redoStack.clear()
        val last = undoStack.lastOrNull()
        if (merge && last != null && kind == INS && last.kind == INS &&
            last.text.length < 40 && !last.text.last().isWhitespace()
        ) {
            last.text += text
        } else if (merge && last != null && kind == DEL && last.kind == DEL && last.text.length < 40) {
            last.text = text + last.text
        } else {
            undoStack.add(Op(kind, text))
            if (undoStack.size > 100) undoStack.removeAt(0)
        }
    }

    private fun before(ic: InputConnection, n: Int) = ic.getTextBeforeCursor(n, 0)?.toString()
    private fun after(ic: InputConnection, n: Int) = ic.getTextAfterCursor(n, 0)?.toString()

    private fun undo(ic: InputConnection) {
        val op = undoStack.lastOrNull() ?: return
        val ok = when (op.kind) {
            INS -> if (before(ic, op.text.length) == op.text) {
                ic.deleteSurroundingText(op.text.length, 0)
                true
            } else false
            DEL, SELDEL -> {
                ic.commitText(op.text, 1)
                true
            }
            PAIR -> if (before(ic, 1) == op.text.substring(0, 1) && after(ic, 1) == op.text.substring(1)) {
                ic.deleteSurroundingText(1, 1)
                true
            } else false
            UNPAIR -> {
                ic.commitText(op.text, 1)
                sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT)
                true
            }
            else -> false
        }
        if (ok) {
            undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(op)
        } else {
            undoStack.clear()
            redoStack.clear()
        }
    }

    private fun redo(ic: InputConnection) {
        val op = redoStack.lastOrNull() ?: return
        val ok = when (op.kind) {
            INS -> {
                ic.commitText(op.text, 1)
                true
            }
            DEL, SELDEL -> if (before(ic, op.text.length) == op.text) {
                ic.deleteSurroundingText(op.text.length, 0)
                true
            } else false
            PAIR -> {
                ic.commitText(op.text, 1)
                sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT)
                true
            }
            UNPAIR -> if (before(ic, 1) == op.text.substring(0, 1) && after(ic, 1) == op.text.substring(1)) {
                ic.deleteSurroundingText(1, 1)
                true
            } else false
            else -> false
        }
        if (ok) {
            redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(op)
        } else {
            undoStack.clear()
            redoStack.clear()
        }
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
        ownEditAt = SystemClock.uptimeMillis()
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
                if (k.back > 0) {
                    record(PAIR, t, false)
                    repeat(k.back) { sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT) }
                } else {
                    record(INS, t, true)
                }
            }
            K.PAIR -> {
                val ch = k.text[0]
                if (!auto) {
                    ic.commitText(k.text, 1)
                    record(INS, k.text, true)
                } else {
                    val isQuote = ch == '"' || ch == '\'' || ch == '`'
                    val nextChar = after(ic, 1).orEmpty()
                    val prevChar = before(ic, 1).orEmpty()
                    if (isQuote && nextChar == k.text) {
                        sendKey(ic, KeyEvent.KEYCODE_DPAD_RIGHT)
                    } else if (isQuote && prevChar.isNotEmpty() && prevChar[0].isLetterOrDigit()) {
                        ic.commitText(k.text, 1)
                        record(INS, k.text, true)
                    } else {
                        val pair = k.text + pairs.getValue(ch)
                        ic.commitText(pair, 1)
                        sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT)
                        record(PAIR, pair, false)
                    }
                }
            }
            K.CLOSE -> {
                val nextChar = after(ic, 1).orEmpty()
                if (auto && nextChar == k.text) {
                    sendKey(ic, KeyEvent.KEYCODE_DPAD_RIGHT)
                } else {
                    ic.commitText(k.text, 1)
                    record(INS, k.text, true)
                }
            }
            K.BACK -> {
                val sel = ic.getSelectedText(0)
                if (!sel.isNullOrEmpty()) {
                    ic.commitText("", 1)
                    record(SELDEL, sel.toString(), false)
                } else {
                    val prevChar = before(ic, 1).orEmpty()
                    val nextChar = after(ic, 1).orEmpty()
                    if (prevChar.isEmpty()) {
                        sendKey(ic, KeyEvent.KEYCODE_DEL)
                    } else if (auto && nextChar.isNotEmpty() && pairs[prevChar[0]] == nextChar[0]) {
                        ic.deleteSurroundingText(1, 1)
                        record(UNPAIR, prevChar + nextChar, false)
                    } else {
                        ic.deleteSurroundingText(1, 0)
                        record(DEL, prevChar, true)
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
                    record(INS, "\n", false)
                }
            }
            K.SPACE -> {
                ic.commitText(" ", 1)
                record(INS, " ", true)
            }
            K.TAB -> {
                val spaces = " ".repeat(sp.getInt("tab_width", 2))
                ic.commitText(spaces, 1)
                record(INS, spaces, true)
            }
            K.LEFT -> sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT)
            K.RIGHT -> sendKey(ic, KeyEvent.KEYCODE_DPAD_RIGHT)
            K.UNDO -> undo(ic)
            K.REDO -> redo(ic)
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
        iv.background = rounded(if (p.accent ushr 24 < 0xC0) 0xFF35476B.toInt() else p.accent)
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
