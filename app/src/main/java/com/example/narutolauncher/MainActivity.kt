package com.example.narutolauncher

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

private val Orange = Color(0xFFFF8A1F)
private val OrangeLt = Color(0xFFFFB44D)
private val Bg = Color(0xFF130D1D)
private val PanelC = Color(0xFF1B1626)
private val Edge = Color(0xFF5A3A1F)
private val Ink = Color(0xFFF6EDE3)
private val Muted = Color(0xFFA99A8A)
private val OnOrange = Color(0xFF1A0D03)
private val OrangeBrush = Brush.verticalGradient(listOf(OrangeLt, Orange))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Orange)) { App() }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var cfg by remember { mutableStateOf(Cfg()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u == null) return@rememberLauncherForActivityResult
        try { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        val disc = PspIso.readDiscId(ctx, u)
        when {
            disc == null -> err = "Not a valid PSP game. Choose an .iso file."
            PspIso.ALLOWED.isNotEmpty() && disc !in PspIso.ALLOWED ->
                err = "Wrong game ($disc). Only Naruto Ultimate Ninja Impact works here."
            else -> { err = ""; uri = u; id = disc; name = PspIso.displayName(ctx, u) }
        }
    }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { t ->
        if (t == null) return@rememberLauncherForActivityResult
        val disc = PspIso.readDiscIdFromTree(ctx, t)
        val path = Emulator.gameRootPath(t)
        when {
            disc == null -> err = "PSP_GAME/PARAM.SFO not found. Choose the folder that contains PSP_GAME."
            path == null -> err = "Choose a folder in internal storage or on the SD card."
            PspIso.ALLOWED.isNotEmpty() && disc !in PspIso.ALLOWED ->
                err = "Wrong game ($disc). Only Naruto Ultimate Ninja Impact works here."
            else -> { err = ""; uri = Uri.parse(path); id = disc; name = File(path).name }
        }
    }
    val pickFile = { picker.launch(arrayOf("*/*")) }
    val pickFolder = { treePicker.launch(null) }

    val play = {
        val hasAll = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
        if (!hasAll) {
            Toast.makeText(ctx, "Allow access to all files, then press PLAY again.", Toast.LENGTH_LONG).show()
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")))
        } else {
            Emulator.launch(ctx, uri!!, id, cfg)?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        if (uri == null) PickScreen(err, pickFolder, pickFile)
        else LauncherScreen(name, id, cfg, { cfg = it }, { uri = null }, play)
    }
}

@Composable
fun PickScreen(err: String, onFolder: () -> Unit, onFile: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Naruto Ultimate Ninja Impact", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Pick your game folder or file to get started.", color = Muted)
        Spacer(Modifier.height(24.dp))
        OrangeButton("Select game folder", onFolder, Modifier.height(56.dp))
        Spacer(Modifier.height(12.dp))
        OrangeButton("Select game file (.iso)", onFile, Modifier.height(56.dp))
        if (err.isNotEmpty()) { Spacer(Modifier.height(14.dp)); Text(err, color = Color(0xFFFF7A6B), textAlign = TextAlign.Center) }
    }
}

@Composable
fun LauncherScreen(name: String, id: String, cfg: Cfg, onCfg: (Cfg) -> Unit, change: () -> Unit, play: () -> Unit) {
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val card: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().border(3.dp, Orange, RoundedCornerShape(20.dp)).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Naruto Ultimate Ninja Impact", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            OrangeButton("PLAY", play, Modifier.height(60.dp), 26.sp)
            Spacer(Modifier.height(12.dp))
            Text("$name ($id)", color = Ink, fontSize = 13.sp, textAlign = TextAlign.Center)
            Text("tap to change", color = OrangeLt, fontSize = 13.sp, modifier = Modifier.clickable { change() }.padding(6.dp))
        }
    }

    val panels: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Panel("Resolution") { Seg(listOf("1x", "2x", "3x", "4x", "5x", "6x"), cfg.res - 1) { onCfg(cfg.copy(res = it + 1)) } }
            Panel("Screen") { Seg(listOf("Original", "Stretch"), if (cfg.stretch) 1 else 0) { onCfg(cfg.copy(stretch = it == 1)) } }
            Panel("Anti-aliasing") { Seg(listOf("Off", "MSAA 2x", "MSAA 4x"), listOf(0, 2, 4).indexOf(cfg.msaa)) { onCfg(cfg.copy(msaa = listOf(0, 2, 4)[it])) } }
            Panel("Texture scaling") { Seg(listOf("Off", "xBRZ 2x", "xBRZ 3x"), cfg.texScale - 1) { onCfg(cfg.copy(texScale = it + 1)) } }
            Panel("Performance") { Tog("Skip buffer effects", cfg.skipBufferEffects) { onCfg(cfg.copy(skipBufferEffects = it)) } }
            Panel("FPS overlay") { Tog("Show FPS counter", cfg.fpsOverlay) { onCfg(cfg.copy(fpsOverlay = it)) } }
            Panel("Frameskip") { Tog("Auto frameskip", cfg.autoFrameskip) { onCfg(cfg.copy(autoFrameskip = it)) } }
            Panel("GPU driver") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("System default (custom driver needs the embedded core)", color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OrangeButton("Change", {}, enabled = false)
                }
            }
        }
    }

    if (land) {
        Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.weight(0.36f).verticalScroll(rememberScrollState())) { card() }
            Box(Modifier.weight(0.64f).verticalScroll(rememberScrollState())) { panels() }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            card(); panels()
        }
    }
}

@Composable
fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(PanelC).border(1.dp, Edge).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("◆ ${title.uppercase()}", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 2.sp)
        content()
    }
}

@Composable
fun Seg(options: List<String>, sel: Int, onSel: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF120E1A))) {
        options.forEachIndexed { i, o ->
            val on = i == sel
            Box(
                Modifier.weight(1f).padding(2.dp)
                    .then(if (on) Modifier.background(OrangeBrush) else Modifier)
                    .clickable { onSel(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text(o, color = if (on) OnOrange else Muted, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
    }
}

@Composable
fun Tog(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted)
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Orange, checkedThumbColor = OnOrange))
    }
}

@Composable
fun OrangeButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.TextUnit = 18.sp, enabled: Boolean = true) {
    Box(
        modifier.then(if (enabled) Modifier.background(OrangeBrush) else Modifier.background(Color(0xFF33293F)))
            .clickable(enabled = enabled) { onClick() }.padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (enabled) OnOrange else Muted, fontWeight = FontWeight.Bold, fontSize = size) }
}
