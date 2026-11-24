package com.example.narutolauncher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

data class Cfg(
    val res: Int = 2,                  // 1..6 = internal resolution multiplier
    val stretch: Boolean = true,
    val msaa: Int = 0,                 // 0 off, 2, 4
    val texScale: Int = 1,             // 1 off, 2, 3 (xBRZ)
    val skipBufferEffects: Boolean = true,
    val fpsOverlay: Boolean = true,
    val autoFrameskip: Boolean = false,
)

object Emulator {
    /**
     * Per-game PPSSPP config. NOTE: key names are written from memory, check them against
     * PPSSPP Core/Config.cpp (or a ppsspp.ini you saved from the app) and fix here if needed.
     */
    fun iniText(c: Cfg): String = buildString {
        appendLine("[Graphics]")
        appendLine("GraphicsBackend = 3 (VULKAN)")
        appendLine("InternalResolution = ${c.res}")
        appendLine("StretchToDisplay = ${c.stretch}")
        appendLine("MultiSampleLevel = ${when (c.msaa) { 2 -> 1; 4 -> 2; else -> 0 }}")
        appendLine("TexScalingLevel = ${c.texScale}")
        appendLine("TexScalingType = 0")
        appendLine("SkipBufferEffects = ${c.skipBufferEffects}")
        appendLine("AutoFrameSkip = ${c.autoFrameskip}")
        appendLine("ShowFPSCounter = ${if (c.fpsOverlay) 2 else 0}")
    }

    /** Real path of the game folder for a picked tree (internal storage or SD card), or null. */
    fun gameRootPath(tree: Uri): String? {
        if (tree.authority != "com.android.externalstorage.documents") return null
        val id = DocumentsContract.getTreeDocumentId(tree)      // e.g. primary:Download/Naruto
        val vol = id.substringBefore(':'); val rel = id.substringAfter(':', "")
        val base = if (vol == "primary") Environment.getExternalStorageDirectory().path else "/storage/$vol"
        val path = if (rel.isEmpty()) base else "$base/$rel"
        return if (File(path).name.equals("PSP_GAME", true)) File(path).parent else path
    }

    /** Writes /sdcard/PSP/SYSTEM/<ID>_ppsspp.ini then opens the game in installed PPSSPP. Returns an error message or null. */
    fun launch(ctx: Context, game: Uri, discId: String, cfg: Cfg): String? {
        try {
            val dir = File(Environment.getExternalStorageDirectory(), "PSP/SYSTEM").apply { mkdirs() }
            File(dir, "${discId}_ppsspp.ini").writeText(iniText(cfg))
        } catch (e: Exception) { return "Could not write config: ${e.message}" }

        for (pkg in listOf("org.ppsspp.ppsspp", "org.ppsspp.ppssppgold")) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW)
                    .setClassName(pkg, "org.ppsspp.ppsspp.PpssppActivity")
                    .setDataAndType(game, if (game.scheme == null) null else "application/octet-stream")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                return null
            } catch (e: ActivityNotFoundException) { }
        }
        return "PPSSPP is not installed."
    }
}
