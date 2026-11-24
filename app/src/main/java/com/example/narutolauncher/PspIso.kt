package com.example.narutolauncher

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Reads DISC_ID from PSP_GAME/PARAM.SFO inside a plain .iso (ISO9660). */
object PspIso {
    /** Put the DISC_ID of your Naruto Ultimate Ninja Impact dump here. Empty = accept any PSP ISO and just show its ID. */
    val ALLOWED: Set<String> = emptySet()
    private const val SECTOR = 2048

    fun displayName(ctx: Context, uri: Uri): String =
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        } ?: "game.iso"

    fun readDiscId(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { discId(it) }
        }
    } catch (e: Exception) { null }

    /** Folder version. The picked folder is either the game root (has PSP_GAME inside) or PSP_GAME itself. */
    fun readDiscIdFromTree(ctx: Context, tree: Uri): String? = try {
        val root = DocumentFile.fromTreeUri(ctx, tree)
        val psp = if (root?.name.equals("PSP_GAME", true)) root else root?.findFile("PSP_GAME")
        val sfo = psp?.findFile("PARAM.SFO")
        sfo?.let { f -> ctx.contentResolver.openInputStream(f.uri)?.use { parseSfo(it.readBytes()) } }
    } catch (e: Exception) { null }

    private fun discId(ch: FileChannel): String? {
        val pvd = read(ch, 16L * SECTOR, SECTOR)
        if (pvd[0].toInt() != 1 || String(pvd, 1, 5, Charsets.US_ASCII) != "CD001") return null
        val root = Pair(le32(pvd, 158), le32(pvd, 166))
        val game = find(ch, root, "PSP_GAME") ?: return null
        val sfo = find(ch, game, "PARAM.SFO") ?: return null
        return parseSfo(read(ch, sfo.first * SECTOR, minOf(sfo.second, 65536L).toInt()))
    }

    private fun find(ch: FileChannel, dir: Pair<Long, Long>, name: String): Pair<Long, Long>? {
        val d = read(ch, dir.first * SECTOR, dir.second.toInt())
        var o = 0
        while (o + 33 < d.size) {
            val len = d[o].toInt() and 0xFF
            if (len == 0) { o = (o / SECTOR + 1) * SECTOR; continue }
            val nl = d[o + 32].toInt() and 0xFF
            if (o + 33 + nl <= d.size) {
                val n = String(d, o + 33, nl, Charsets.US_ASCII).substringBefore(';')
                if (n.equals(name, true)) return Pair(le32(d, o + 2), le32(d, o + 10))
            }
            o += len
        }
        return null
    }

    private fun parseSfo(b: ByteArray): String? {
        if (b.size < 20 || b[1] != 'P'.code.toByte() || b[2] != 'S'.code.toByte() || b[3] != 'F'.code.toByte()) return null
        val keys = le32(b, 8).toInt(); val data = le32(b, 12).toInt(); val n = le32(b, 16).toInt()
        for (i in 0 until n) {
            val e = 20 + i * 16
            if (e + 16 > b.size) break
            if (cstr(b, keys + le16(b, e)) == "DISC_ID") return cstr(b, data + le32(b, e + 12).toInt())
        }
        return null
    }

    private fun read(ch: FileChannel, pos: Long, n: Int): ByteArray {
        val buf = ByteBuffer.allocate(n); ch.position(pos)
        while (buf.hasRemaining() && ch.read(buf) > 0) { }
        return buf.array()
    }
    private fun cstr(b: ByteArray, o: Int): String {
        if (o < 0 || o >= b.size) return ""
        var e = o; while (e < b.size && b[e] != 0.toByte()) e++
        return String(b, o, e - o, Charsets.US_ASCII)
    }
    private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, o: Int): Long = le16(b, o).toLong() or (le16(b, o + 2).toLong() shl 16)
}
