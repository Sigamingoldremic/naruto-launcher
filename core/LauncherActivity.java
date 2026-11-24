package org.ppsspp.ppsspp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

/** Launcher screen inside the PPSSPP app: pick game, set graphics, PLAY. */
public class LauncherActivity extends Activity {
    /** Put your game's DISC_ID here. Empty = accept any PSP game (the ID is shown under the title). */
    static final String[] ALLOWED = {};
    static final int BG = 0xFF130D1D, ORANGE = 0xFFFF8A1F, INK = 0xFFF6EDE3, MUTED = 0xFFA99A8A, DARK = 0xFF1A0D03;

    interface Pick { void on(int i); }

    LinearLayout root; TextView status; SharedPreferences sp;
    String gameUri, gamePath, discId, gameName;
    int res = 2, msaa = 0, tex = 1;
    boolean stretch = true, skipBuf = true, fps = true, autoSkip = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("launcher", 0);
        gameUri = sp.getString("uri", null); gamePath = sp.getString("path", null);
        discId = sp.getString("id", null); gameName = sp.getString("name", null);
        res = sp.getInt("res", 2); msaa = sp.getInt("msaa", 0); tex = sp.getInt("tex", 1);
        stretch = sp.getBoolean("st", true); skipBuf = sp.getBoolean("sb", true);
        fps = sp.getBoolean("fps", true); autoSkip = sp.getBoolean("as", false);

        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(BG);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(28), dp(16), dp(28));
        sv.addView(root); setContentView(sv);

        add(text("Naruto Ultimate Ninja Impact", 26, INK, true), 0);
        status = text("", 14, MUTED, false); add(status, 4);
        add(button("Select game folder", new View.OnClickListener() { public void onClick(View v) { pickFolder(); } }), 14);
        add(button("Select game file (.iso)", new View.OnClickListener() { public void onClick(View v) { pickFile(); } }), 8);

        seg("Resolution", new String[]{"1x", "2x", "3x", "4x", "5x", "6x"}, res - 1, new Pick() { public void on(int i) { res = i + 1; save(); } });
        seg("Screen", new String[]{"Original", "Stretch"}, stretch ? 1 : 0, new Pick() { public void on(int i) { stretch = i == 1; save(); } });
        seg("Anti-aliasing", new String[]{"Off", "MSAA 2x", "MSAA 4x"}, msaa == 2 ? 1 : msaa == 4 ? 2 : 0, new Pick() { public void on(int i) { msaa = i * 2; save(); } });
        seg("Texture scaling", new String[]{"Off", "xBRZ 2x", "xBRZ 3x"}, tex - 1, new Pick() { public void on(int i) { tex = i + 1; save(); } });
        tog("Performance (skip buffer effects)", skipBuf, new Pick() { public void on(int i) { skipBuf = i == 1; save(); } });
        tog("FPS overlay", fps, new Pick() { public void on(int i) { fps = i == 1; save(); } });
        tog("Auto frameskip", autoSkip, new Pick() { public void on(int i) { autoSkip = i == 1; save(); } });

        Button play = button("PLAY", new View.OnClickListener() { public void onClick(View v) { play(); } });
        play.setTextSize(24); add(play, 24);
        add(button("Emulator settings (GPU driver, controls)", new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(LauncherActivity.this, PpssppActivity.class)); } }), 10);
        refresh();
    }

    // ---------- game picking ----------
    void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, 1);
    }
    void pickFolder() {
        if (!hasAll()) {
            toast("Allow access to all files, then press Select game folder again.");
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 2);
    }
    boolean hasAll() { return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager(); }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        if (req == 1) {
            try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception e) { }
            String id = null, name = "game.iso";
            try {
                ParcelFileDescriptor p = getContentResolver().openFileDescriptor(u, "r");
                FileChannel ch = new FileInputStream(p.getFileDescriptor()).getChannel();
                id = discIdFromIso(ch); ch.close(); p.close();
                Cursor c = getContentResolver().query(u, null, null, null, null);
                if (c != null) { int k = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (k >= 0 && c.moveToFirst()) name = c.getString(k); c.close(); }
            } catch (Exception e) { }
            accept(id, u.toString(), null, name);
        } else if (req == 2) {
            String p = treePath(u);
            if (p == null) { toast("Choose a folder in internal storage or on the SD card."); return; }
            String id = null;
            try {
                FileInputStream in = new FileInputStream(new File(p, "PSP_GAME/PARAM.SFO"));
                byte[] buf = new byte[65536]; int n = in.read(buf); in.close();
                id = parseSfo(Arrays.copyOf(buf, Math.max(n, 0)));
            } catch (Exception e) { }
            accept(id, null, p, new File(p).getName());
        }
    }

    void accept(String id, String uri, String path, String name) {
        if (id == null) { toast("Not a valid PSP game. Pick the folder that contains PSP_GAME, or an .iso file."); return; }
        if (ALLOWED.length > 0 && !Arrays.asList(ALLOWED).contains(id)) { toast("Wrong game (" + id + ")."); return; }
        gameUri = uri; gamePath = path; discId = id; gameName = name; save(); refresh();
    }

    String treePath(Uri t) {
        if (!"com.android.externalstorage.documents".equals(t.getAuthority())) return null;
        String id = DocumentsContract.getTreeDocumentId(t); int c = id.indexOf(':');
        String vol = c < 0 ? id : id.substring(0, c), rel = c < 0 ? "" : id.substring(c + 1);
        String base = "primary".equals(vol) ? Environment.getExternalStorageDirectory().getPath() : "/storage/" + vol;
        String p = rel.isEmpty() ? base : base + "/" + rel;
        File f = new File(p);
        return "PSP_GAME".equalsIgnoreCase(f.getName()) ? f.getParent() : p;
    }

    // ---------- play ----------
    void play() {
        if (discId == null) { toast("Select a game first."); return; }
        try {
            File base = getExternalFilesDir(null); if (base == null) base = getFilesDir();
            File dir = new File(base, "PSP/SYSTEM"); dir.mkdirs();
            String s = "[Graphics]\nGraphicsBackend = 3 (VULKAN)\nInternalResolution = " + res
                + "\nStretchToDisplay = " + stretch + "\nMultiSampleLevel = " + (msaa == 2 ? 1 : msaa == 4 ? 2 : 0)
                + "\nTexScalingLevel = " + tex + "\nTexScalingType = 0\nSkipBufferEffects = " + skipBuf
                + "\nAutoFrameSkip = " + autoSkip + "\nShowFPSCounter = " + (fps ? 2 : 0) + "\n";
            FileOutputStream o = new FileOutputStream(new File(dir, discId + "_ppsspp.ini")); o.write(s.getBytes()); o.close();
        } catch (Exception e) { toast("Could not write config: " + e.getMessage()); return; }
        Intent i = new Intent(this, PpssppActivity.class);
        i.setAction(Intent.ACTION_VIEW); i.addCategory(Intent.CATEGORY_DEFAULT);
        if (gamePath != null) i.setData(Uri.parse(gamePath));
        else { i.setDataAndType(Uri.parse(gameUri), "application/octet-stream"); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        startActivity(i);
    }

    // ---------- DISC_ID from ISO / PARAM.SFO ----------
    static String discIdFromIso(FileChannel ch) throws IOException {
        byte[] pvd = read(ch, 16L * 2048, 2048);
        if (pvd[0] != 1 || !new String(pvd, 1, 5, "US-ASCII").equals("CD001")) return null;
        long[] game = find(ch, le32(pvd, 158), le32(pvd, 166), "PSP_GAME"); if (game == null) return null;
        long[] sfo = find(ch, game[0], game[1], "PARAM.SFO"); if (sfo == null) return null;
        return parseSfo(read(ch, sfo[0] * 2048, (int) Math.min(sfo[1], 65536)));
    }
    static long[] find(FileChannel ch, long lba, long len, String name) throws IOException {
        byte[] d = read(ch, lba * 2048, (int) len); int o = 0;
        while (o + 33 < d.length) {
            int l = d[o] & 255;
            if (l == 0) { o = (o / 2048 + 1) * 2048; continue; }
            int nl = d[o + 32] & 255;
            if (o + 33 + nl <= d.length) {
                String n = new String(d, o + 33, nl, "US-ASCII"); int s = n.indexOf(';'); if (s >= 0) n = n.substring(0, s);
                if (n.equalsIgnoreCase(name)) return new long[]{le32(d, o + 2), le32(d, o + 10)};
            }
            o += l;
        }
        return null;
    }
    static String parseSfo(byte[] b) {
        if (b.length < 20 || b[1] != 'P' || b[2] != 'S' || b[3] != 'F') return null;
        int keys = (int) le32(b, 8), data = (int) le32(b, 12), n = (int) le32(b, 16);
        for (int i = 0; i < n; i++) {
            int e = 20 + i * 16; if (e + 16 > b.length) break;
            if ("DISC_ID".equals(cstr(b, keys + le16(b, e)))) return cstr(b, data + (int) le32(b, e + 12));
        }
        return null;
    }
    static byte[] read(FileChannel ch, long pos, int n) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(n); ch.position(pos);
        while (b.hasRemaining() && ch.read(b) > 0) { }
        return b.array();
    }
    static String cstr(byte[] b, int o) { if (o < 0 || o >= b.length) return ""; int e = o; while (e < b.length && b[e] != 0) e++; return new String(b, o, e - o); }
    static int le16(byte[] b, int o) { return (b[o] & 255) | ((b[o + 1] & 255) << 8); }
    static long le32(byte[] b, int o) { return (le16(b, o) & 0xFFFFL) | ((long) le16(b, o + 2) << 16); }

    // ---------- small UI helpers ----------
    void save() {
        sp.edit().putInt("res", res).putInt("msaa", msaa).putInt("tex", tex).putBoolean("st", stretch).putBoolean("sb", skipBuf)
            .putBoolean("fps", fps).putBoolean("as", autoSkip).putString("uri", gameUri).putString("path", gamePath)
            .putString("id", discId).putString("name", gameName).apply();
    }
    void refresh() { status.setText(discId == null ? "No game selected" : gameName + " (" + discId + ")"); }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }
    Button button(String t, View.OnClickListener l) {
        Button b = new Button(this); b.setText(t); b.setTextColor(DARK); b.setBackgroundColor(ORANGE); b.setOnClickListener(l); return b;
    }
    void add(View v, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); root.addView(v, p);
    }
    void seg(String title, String[] o, int sel, final Pick p) {
        add(text("\u25C6 " + title.toUpperCase(), 13, INK, true), 16);
        LinearLayout r = new LinearLayout(this); r.setBackgroundColor(0xFF120E1A);
        final TextView[] tv = new TextView[o.length];
        for (int i = 0; i < o.length; i++) {
            final int k = i; tv[i] = text(o[i], 14, MUTED, true); tv[i].setGravity(Gravity.CENTER); tv[i].setPadding(0, dp(11), 0, dp(11));
            tv[i].setOnClickListener(new View.OnClickListener() { public void onClick(View v) { paint(tv, k); p.on(k); } });
            r.addView(tv[i], new LinearLayout.LayoutParams(0, -2, 1));
        }
        paint(tv, sel); add(r, 4);
    }
    void paint(TextView[] tv, int sel) {
        for (int i = 0; i < tv.length; i++) { tv[i].setBackgroundColor(i == sel ? ORANGE : 0); tv[i].setTextColor(i == sel ? DARK : MUTED); }
    }
    void tog(String title, boolean v, final Pick p) {
        Switch s = new Switch(this); s.setText(title); s.setTextColor(INK); s.setChecked(v);
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton c, boolean on) { p.on(on ? 1 : 0); } });
        add(s, 14);
    }
}
