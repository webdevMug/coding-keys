package com.codingkeyboard

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF151219.toInt()
        window.navigationBarColor = 0xFF151219.toInt()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFB8C7FF),
                    onPrimary = Color(0xFF0F1B3D),
                    background = Color(0xFF151219),
                    onBackground = Color(0xFFE9E1EC),
                    surface = Color(0xFF151219),
                    onSurface = Color(0xFFE9E1EC),
                    surfaceVariant = Color(0xFF211D27),
                    onSurfaceVariant = Color(0xFFB3AAB9)
                )
            ) {
                SettingsApp()
            }
        }
    }
}

fun saveKeyboardImage(ctx: Context, uri: Uri): Boolean {
    return try {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1080) sample *= 2
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return false
        File(ctx.filesDir, "keyboard_bg.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        Prefs.sp(ctx).edit().putString("theme", "image").apply()
        true
    } catch (e: Exception) {
        false
    }
}

fun titleFor(s: String?): String = when (s) {
    "languages" -> "Languages & Layouts"
    "preferences" -> "Preferences"
    "appearance" -> "Appearance"
    "toolbar" -> "Toolbar"
    "text_correction" -> "Text correction"
    "secondary" -> "Secondary layouts"
    "dictionaries" -> "Dictionaries"
    "advanced" -> "Advanced"
    "about" -> "About"
    else -> "Coding Keyboard"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsApp() {
    var screen by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = screen != null) { screen = null }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleFor(screen)) },
                navigationIcon = {
                    if (screen != null) {
                        IconButton(onClick = { screen = null }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
        ) {
            val s = screen
            if (s == null) MainScreen { screen = it } else SectionScreen(s)
        }
    }
}

@Composable
fun SettingItem(
    title: String,
    sub: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val click = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Row(
        Modifier
            .fillMaxWidth()
            .then(click)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (sub != null) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun NavItem(title: String, sub: String?, icon: ImageVector, onClick: () -> Unit) {
    SettingItem(title, sub, icon, onClick) {
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MainScreen(go: (String) -> Unit) {
    val ctx = LocalContext.current
    var test by remember { mutableStateOf("") }
    Text(
        "Long-press the comma key on the keyboard to open this screen.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Set up", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = { ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("1. Enable keyboard") }
            FilledTonalButton(
                onClick = {
                    (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("2. Switch to Coding Keyboard") }
            OutlinedTextField(
                value = test,
                onValueChange = { test = it },
                placeholder = { Text("Type here to try the keyboard") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    NavItem("Languages & Layouts", "English (US)", Icons.Outlined.Language) { go("languages") }
    NavItem("Preferences", null, Icons.Outlined.Tune) { go("preferences") }
    NavItem("Appearance", null, Icons.Outlined.Palette) { go("appearance") }
    NavItem("Toolbar", null, Icons.Outlined.ChevronRight) { go("toolbar") }
    NavItem("Text correction", null, Icons.Outlined.Spellcheck) { go("text_correction") }
    NavItem("Secondary layouts", null, Icons.Outlined.Keyboard) { go("secondary") }
    NavItem("Dictionaries", null, Icons.Outlined.MenuBook) { go("dictionaries") }
    NavItem("Advanced", null, Icons.Outlined.Build) { go("advanced") }
    NavItem("About", null, Icons.Outlined.Info) { go("about") }
}

@Composable
fun SwitchRow(title: String, sub: String?, key: String, def: Boolean) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(Prefs.sp(ctx).getBoolean(key, def)) }
    val flip = { v: Boolean ->
        on = v
        Prefs.sp(ctx).edit().putBoolean(key, v).apply()
    }
    SettingItem(title, sub, onClick = { flip(!on) }) {
        Switch(checked = on, onCheckedChange = { flip(it) })
    }
}

@Composable
fun ChoiceRow(title: String, labels: List<String>, initial: Int, onPick: (Int) -> Unit) {
    var cur by remember { mutableStateOf(initial) }
    var open by remember { mutableStateOf(false) }
    SettingItem(title, labels[cur], onClick = { open = true })
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    labels.forEachIndexed { i, l ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    cur = i
                                    onPick(i)
                                    open = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = i == cur, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(l)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun StringChoice(title: String, key: String, labels: List<String>, values: List<String>, def: String) {
    val ctx = LocalContext.current
    val cur = Prefs.sp(ctx).getString(key, def) ?: def
    ChoiceRow(title, labels, values.indexOf(cur).coerceAtLeast(0)) {
        Prefs.sp(ctx).edit().putString(key, values[it]).apply()
    }
}

@Composable
fun IntChoice(title: String, key: String, labels: List<String>, values: List<Int>, def: Int) {
    val ctx = LocalContext.current
    val cur = Prefs.sp(ctx).getInt(key, def)
    ChoiceRow(title, labels, values.indexOf(cur).coerceAtLeast(0)) {
        Prefs.sp(ctx).edit().putInt(key, values[it]).apply()
    }
}

@Composable
fun ThemeSection() {
    val ctx = LocalContext.current
    val labels = listOf("Dark", "Light", "Golden accent", "Transparent", "Image", "Follow system")
    val values = listOf("dark", "light", "gold", "transparent", "image", "system")
    var cur by remember { mutableStateOf(values.indexOf(Prefs.sp(ctx).getString("theme", "dark")).coerceAtLeast(0)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (saveKeyboardImage(ctx, uri)) {
                cur = values.indexOf("image")
            } else {
                Toast.makeText(ctx, "Couldn't load that image. Try another one.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    var open by remember { mutableStateOf(false) }
    SettingItem("Theme", labels[cur], onClick = { open = true })
    SettingItem(
        "Keyboard image",
        "Pick a photo to use as the keyboard background.",
        onClick = { picker.launch("image/*") }
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Theme") },
            text = {
                Column {
                    labels.forEachIndexed { i, l ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    open = false
                                    if (values[i] == "image" && !File(ctx.filesDir, "keyboard_bg.jpg").exists()) {
                                        picker.launch("image/*")
                                    } else {
                                        cur = i
                                        Prefs.sp(ctx).edit().putString("theme", values[i]).apply()
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = i == cur, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(l)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun KeyHeightSection() {
    val ctx = LocalContext.current
    var h by remember { mutableStateOf(Prefs.sp(ctx).getInt("key_height_dp", 47).coerceIn(20, 100).toFloat()) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text("Key height: ${h.toInt()}", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = h,
            onValueChange = { h = it },
            onValueChangeFinished = { Prefs.sp(ctx).edit().putInt("key_height_dp", h.toInt()).apply() },
            valueRange = 20f..100f
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(h.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("Aa </> {}", style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = {
            h = 47f
            Prefs.sp(ctx).edit().putInt("key_height_dp", 47).apply()
        }) { Text("Reset to default") }
    }
}

@Composable
fun SectionScreen(section: String) {
    when (section) {
        "languages" -> SettingItem(
            "English (US)",
            "QWERTY layout built for coding. More languages and layouts are planned."
        )
        "preferences" -> {
            SwitchRow("Vibrate on keypress", null, "vibrate", true)
            SwitchRow(
                "Auto-pair brackets and quotes",
                "Typing ( adds ) and puts the cursor between them.",
                "autopair", true
            )
            IntChoice("Tab width", "tab_width", listOf("2 spaces", "4 spaces"), listOf(2, 4), 2)
        }
        "appearance" -> {
            ThemeSection()
            KeyHeightSection()
        }
        "toolbar" -> SwitchRow(
            "Show arrow and undo row",
            "Left, right, undo, and redo keys above the letters.",
            "show_util", true
        )
        "text_correction" -> SettingItem(
            "Autocorrect is off",
            "Autocorrect, auto-capitalization, and suggestions stay off because they break code."
        )
        "secondary" -> SettingItem(
            "Coming later",
            "Extra symbol pages, like one for Markdown or JSON, are planned for a later version."
        )
        "dictionaries" -> SettingItem(
            "Coming later",
            "Word suggestions and snippet lists are planned for a later version."
        )
        "advanced" -> IntChoice(
            "Long-press delay", "long_press_ms",
            listOf("300 ms", "400 ms", "500 ms", "700 ms"), listOf(300, 400, 500, 700), 400
        )
        "about" -> {
            SettingItem(
                "Coding Keyboard 2.0",
                "A keyboard for writing HTML, CSS, and JavaScript on your phone. Long-press the comma key to open settings."
            )
            SettingItem("Developer", "Muhammad Umar Gumel")
        }
        else -> {}
    }
}
