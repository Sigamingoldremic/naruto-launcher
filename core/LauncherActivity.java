package org.ppsspp.ppsspp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.WebView;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Naruto launcher screen inside the PPSSPP app. */
public class LauncherActivity extends Activity {
    /** Put your game's DISC_ID here. Empty = accept any PSP game. */
    static final String[] ALLOWED = {};
    static final String SAVE_ID = "ULUS10582", SAVE_DIR = "ULUS105820001", TRAILER_ID = "u8RFoYpdjCU";
    static final int ORANGE = 0xFFFF8A1F, ORANGE2 = 0xFFFFB44D, INK = 0xFFF6EDE3, MUTED = 0xFFA99A8A, DARK = 0xFF1A0D03,
            PANEL = 0xFF1B1626, EDGE = 0xFF5A3A1F, CELL = 0xFF120E1A;
    static final float UI = 0.78f;   // global UI scale: lower = smaller menu
    static final int RQ_FILE = 1, RQ_TREE = 2, RQ_SAVE = 3, RQ_MOD = 4, RQ_DRV = 5;

    interface Pick { void on(int i); }

    SharedPreferences sp; float dens; boolean land;
    String gameUri, gamePath, discId, gameName, drv;
    int res = 2, msaa = 0, tex = 1, opacity = 65, hide = 20;
    boolean stretch = true, skipBuf = true, fps = true, autoSkip = false, full = false, busy = false;
    TextView status, saveInfo, drvInfo, modInfo; Switch lastSw, fullSw; AlertDialog setupDialog;

    // ---------------------------------------------------------------- lifecycle
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("launcher", 0);
        gameUri = sp.getString("uri", null); gamePath = sp.getString("path", null);
        discId = sp.getString("id", null); gameName = sp.getString("name", null); drv = sp.getString("drv", null);
        res = sp.getInt("res", 2); msaa = sp.getInt("msaa", 0); tex = sp.getInt("tex", 1); opacity = sp.getInt("op", 65); hide = sp.getInt("hide", 20);
        stretch = sp.getBoolean("st", true); skipBuf = sp.getBoolean("sb", true);
        fps = sp.getBoolean("fps", true); autoSkip = sp.getBoolean("as", false); full = sp.getBoolean("full", false);
        dens = getResources().getDisplayMetrics().density;
        land = true;   // the manifest locks this screen to landscape
        if (Build.VERSION.SDK_INT >= 28) {
            android.view.WindowManager.LayoutParams a = getWindow().getAttributes();
            a.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(a);
        }
        if (discId == null) showPick(); else showMain();
    }

    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) hideBars(); }
    @Override protected void onResume() { super.onResume(); maybeSetup(); refreshInfo(); }
    void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    void save() {
        sp.edit().putInt("res", res).putInt("msaa", msaa).putInt("tex", tex).putBoolean("st", stretch).putBoolean("sb", skipBuf)
            .putInt("op", opacity).putInt("hide", hide).putBoolean("fps", fps).putBoolean("as", autoSkip).putBoolean("full", full).putString("uri", gameUri).putString("path", gamePath)
            .putString("id", discId).putString("name", gameName).putString("drv", drv).apply();
    }

    // ---------------------------------------------------------------- screens
    void showPick() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setGravity(Gravity.CENTER);
        l.setPadding(dp(40), dp(24), dp(40), dp(24)); l.setBackground(bgGradient());
        TextView t = text("Naruto Ultimate Ninja Impact", 30, INK, true); t.setGravity(Gravity.CENTER); t.setShadowLayer(24f, 0, 0, ORANGE);
        l.addView(t);
        TextView s = text("Pick your game folder or file to get started.", 14, MUTED, false); s.setGravity(Gravity.CENTER);
        l.addView(s, lp(-1, -2, 0, 8));
        Shape a = new Shape(this, "Select game folder", 1, 18);
        a.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickFolder(); } });
        l.addView(a, lp(dp(280), dp(54), 0, 26));
        Shape b = new Shape(this, "Select game file (.iso)", 1, 18);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickFile(); } });
        l.addView(b, lp(dp(280), dp(54), 0, 12));
        if (discId != null) {
            TextView c = text("Cancel", 15, ORANGE2, false); c.setPadding(dp(16), dp(14), dp(16), dp(14));
            c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showMain(); } });
            l.addView(c);
        }
        setContentView(l);
    }

    void showMain() {
        LinearLayout card = buildCard(), col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL);

        gap(col, segPanel("Resolution", new String[]{"1x|272p", "2x|544p", "3x|816p", "4x|1088p", "5x|1360p", "6x|1632p"}, res - 1,
            new Pick() { public void on(int i) { res = i + 1; save(); } }), 0);
        row(col, segPanel("Screen", new String[]{"Original", "Stretch"}, stretch ? 1 : 0, new Pick() { public void on(int i) { stretch = i == 1; save(); } }),
                 segPanel("Anti-aliasing", new String[]{"Off", "MSAA 2x", "MSAA 4x"}, msaa == 2 ? 1 : msaa == 4 ? 2 : 0,
                     new Pick() { public void on(int i) { msaa = i * 2; save(); } }));
        row(col, segPanel("Texture scaling", new String[]{"Off", "xBRZ 2x", "xBRZ 3x"}, tex - 1, new Pick() { public void on(int i) { tex = i + 1; save(); } }),
                 touchPanel());

        LinearLayout t1 = togPanel("Performance", "skip buffer effects", skipBuf, new Pick() { public void on(int i) { skipBuf = i == 1; save(); } });
        LinearLayout t2 = togPanel("FPS overlay", "show FPS counter", fps, new Pick() { public void on(int i) { fps = i == 1; save(); } });
        LinearLayout t3 = togPanel("100% Save", "everything unlocked", full, new Pick() { public void on(int i) { setFull(i == 1); } });
        fullSw = lastSw;
        row(col, t1, t2, t3);

        saveInfo = text("", 13, INK, false); drvInfo = text("", 13, INK, false); modInfo = text("", 13, INK, false);
        row(col, btnPanel("Game save", saveInfo, "Import", new View.OnClickListener() { public void onClick(View v) { pickZip(RQ_SAVE); } }),
                 btnPanel("GPU driver", drvInfo, "Change", new View.OnClickListener() { public void onClick(View v) { driverDialog(); } }));
        gap(col, btnPanel("Mods (textures)", modInfo, "Install", new View.OnClickListener() { public void onClick(View v) { pickZip(RQ_MOD); } }), 10);
        TextView note = text("Data folder: " + psp().getPath(), 11, MUTED, false); gap(col, note, 12);

        if (land) {
            LinearLayout h = new LinearLayout(this); h.setOrientation(LinearLayout.HORIZONTAL); h.setPadding(dp(30), dp(14), dp(30), dp(14)); h.setBackground(bgGradient());
            ScrollView ls = new ScrollView(this); ls.addView(card); h.addView(ls, new LinearLayout.LayoutParams(0, -1, 0.36f));
            ScrollView rs = new ScrollView(this); rs.addView(col);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -1, 0.64f); rp.leftMargin = dp(14); h.addView(rs, rp);
            setContentView(h);
        } else {
            LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(14), dp(18), dp(14), dp(24));
            v.addView(card); gap(v, col, 14);
            ScrollView sv = new ScrollView(this); sv.setBackground(bgGradient()); sv.addView(v); setContentView(sv);
        }
        refreshInfo();
    }

    LinearLayout buildCard() {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(18), dp(18), dp(18), dp(16));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF2A1C44, 0xFF140E1E});
        g.setCornerRadius(dp(22)); g.setStroke(dp(3), ORANGE); c.setBackground(g);

        TextView tr = text("\u25B6  TRAILER", 13, INK, true); tr.setLetterSpacing(0.15f); tr.setPadding(dp(16), dp(8), dp(16), dp(8));
        tr.setBackground(box(0x55000000, ORANGE2, 1, 20));
        tr.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { trailer(); } });
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2); tp.gravity = Gravity.START; c.addView(tr, tp);

        c.addView(new View(this), new LinearLayout.LayoutParams(1, dp(land ? 70 : 40)));
        TextView t = text("Naruto Ultimate Ninja Impact", 26, INK, true); t.setGravity(Gravity.CENTER); t.setShadowLayer(26f, 0, 0, ORANGE); c.addView(t);
        TextView ed = text("PSP  EDITION", 12, MUTED, true); ed.setLetterSpacing(0.35f); ed.setGravity(Gravity.CENTER); c.addView(ed, lp(-2, -2, 0, 6));

        Shape play = new Shape(this, "PLAY", 0, 26); play.setLetterSpacing(0.3f);
        play.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { play(); } });
        c.addView(play, lp(-1, dp(64), 0, 26));

        status = text("", 13, INK, false); status.setGravity(Gravity.CENTER); c.addView(status, lp(-1, -2, 0, 14));
        TextView ch = text("tap to change", 13, ORANGE2, false); ch.setPadding(dp(12), dp(8), dp(12), dp(8));
        ch.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showPick(); } });
        c.addView(ch);
        TextView lic = text("Licenses", 13, MUTED, false); lic.setPadding(dp(12), dp(8), dp(12), dp(4));
        lic.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            new AlertDialog.Builder(LauncherActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Licenses")
                .setMessage("Emulator core: PPSSPP, \u00A9 PPSSPP Project, GNU GPL v2 or later.\nhttps://github.com/hrydgard/ppsspp\n\nThis app is a modified build of PPSSPP. Its source is published together with the app.\n\nNo game files are included. Use your own game dump.")
                .setPositiveButton("OK", null).show(); } });
        c.addView(lic);
        return c;
    }

    // ---------------------------------------------------------------- panels
    LinearLayout panel(String title) {
        LinearLayout p = new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL); p.setPadding(dp(12), dp(10), dp(12), dp(12));
        p.setBackground(box(PANEL, EDGE, 1, 3));
        SpannableString ss = new SpannableString("\u25C6 " + title.toUpperCase()); ss.setSpan(new ForegroundColorSpan(ORANGE), 0, 1, 0);
        TextView h = text("", 13, INK, true); h.setText(ss); h.setLetterSpacing(0.12f); p.addView(h);
        return p;
    }
    LinearLayout segPanel(String title, String[] o, int sel, Pick cb) {
        LinearLayout p = panel(title); p.addView(new Seg(this, o, sel, cb), lp(-1, dp(50), 0, 8)); return p;
    }
    LinearLayout touchPanel() {
        LinearLayout p = panel("Touch buttons");
        addSlider(p, "Opacity", 0, 100, opacity, "%", new Pick() { public void on(int i) { opacity = i; save(); } });
        addSlider(p, "Auto-hide after", 0, 60, hide, " s", new Pick() { public void on(int i) { hide = i; save(); } });
        return p;
    }
    void addSlider(LinearLayout p, String label, final int min, int max, int val, final String unit, final Pick cb) {
        p.addView(text(label, 12, MUTED, false), lp(-1, -2, 0, 8));
        LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL);
        final TextView v = text(fmt(val, unit), 13, INK, true); v.setMinWidth(dp(56)); v.setGravity(Gravity.END);
        SeekBar sb = new SeekBar(this); sb.setMax(max - min); sb.setProgress(val - min);
        if (Build.VERSION.SDK_INT >= 21) { sb.setProgressTintList(ColorStateList.valueOf(ORANGE)); sb.setThumbTintList(ColorStateList.valueOf(ORANGE2)); }
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int pr, boolean u) { v.setText(fmt(pr + min, unit)); cb.on(pr + min); }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        sb.setOnTouchListener(new View.OnTouchListener() { public boolean onTouch(View view, MotionEvent e) {   // keep the parent ScrollView from stealing the drag
            int a = e.getAction();
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) view.getParent().requestDisallowInterceptTouchEvent(true);
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) view.getParent().requestDisallowInterceptTouchEvent(false);
            return false;
        } });
        r.addView(sb, new LinearLayout.LayoutParams(0, -2, 1)); r.addView(v); p.addView(r, lp(-1, -2, 0, 2));
    }
    static String fmt(int v, String unit) { return v == 0 && unit.equals(" s") ? "off" : v + unit; }
    LinearLayout togPanel(String title, String sub, boolean on, final Pick cb) {
        LinearLayout p = panel(title); LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(text(sub, 12, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        if (Build.VERSION.SDK_INT >= 23) {
            sw.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
            sw.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{ORANGE, 0xFF4A3F5C}));
        }
        sw.setChecked(on);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton c, boolean v) { if (!busy) cb.on(v ? 1 : 0); } });
        r.addView(sw); p.addView(r, lp(-1, -2, 0, 6)); lastSw = sw; return p;
    }
    LinearLayout btnPanel(String title, TextView info, String btn, View.OnClickListener l) {
        LinearLayout p = panel(title); LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        Shape b = new Shape(this, btn, 1, 14); b.setPadding(dp(18), dp(8), dp(18), dp(8)); b.setOnClickListener(l);
        r.addView(b, new LinearLayout.LayoutParams(-2, dp(40))); p.addView(r, lp(-1, -2, 0, 8)); return p;
    }
    void row(LinearLayout col, View... ps) {
        if (!land) { for (View v : ps) gap(col, v, 10); return; }
        LinearLayout r = new LinearLayout(this);
        for (int i = 0; i < ps.length; i++) { LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, -1, 1); if (i > 0) l.leftMargin = dp(10); r.addView(ps[i], l); }
        gap(col, r, 10);
    }
    void gap(LinearLayout col, View v, int top) { col.addView(v, lp(-1, -2, 0, top)); }
    LinearLayout.LayoutParams lp(int w, int h, int l, int top) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h); p.leftMargin = dp(l); p.topMargin = dp(top); return p; }

    void refreshInfo() {
        if (status != null) status.setText(discId == null ? "No game selected" : (gamePath != null ? gamePath : gameName) + "  (" + discId + ")");
        if (drvInfo != null) drvInfo.setText(drv == null ? "Default system driver" : drv);
        if (modInfo != null) modInfo.setText(sp.getInt("mods", 0) > 0 ? "Installed: " + sp.getInt("mods", 0) + " files" : "No mods installed");
        if (saveInfo != null) {
            String s = "No save found";
            File sd = new File(psp(), "SAVEDATA"); File[] fs = sd.listFiles();
            if (fs != null && discId != null) for (File f : fs) if (f.isDirectory() && f.getName().startsWith(discId)) {
                File d = new File(f, "DATA.BIN");
                s = "Current save: " + (d.length() / 1024) + " KB \u00B7 " + new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date(d.lastModified()));
            }
            StringBuilder nm = new StringBuilder();
            if (fs != null) for (File f : fs) if (f.isDirectory()) nm.append(nm.length() > 0 ? ", " : "").append(f.getName());
            if (nm.length() > 0) s += "\n" + nm;
            saveInfo.setText(s);
        }
    }

    // ---------------------------------------------------------------- picking
    void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, RQ_FILE);
    }
    void pickZip(int rq) {
        if (discId == null) { toast("Select a game first."); return; }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
        startActivityForResult(i, rq);
    }
    void pickFolder() {
        if (!hasAll()) {
            toast("Allow access to all files, then press Select game folder again.");
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), RQ_TREE);
    }
    boolean hasAll() { return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager(); }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        final Uri u = data.getData();
        if (req == RQ_FILE) {
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
        } else if (req == RQ_TREE) {
            String p = treePath(u);
            if (p == null) { toast("Choose a folder in internal storage or on the SD card."); return; }
            String id = null;
            try {
                FileInputStream in = new FileInputStream(new File(p, "PSP_GAME/PARAM.SFO"));
                byte[] buf = new byte[65536]; int n = in.read(buf); in.close();
                id = parseSfo(Arrays.copyOf(buf, Math.max(n, 0)));
            } catch (Exception e) { }
            accept(id, null, p, new File(p).getName());
        } else {
            final int kind = req;
            new Thread(new Runnable() { public void run() {
                String msg;
                try { msg = kind == RQ_SAVE ? installSave(u) : kind == RQ_MOD ? installMod(u) : installDriver(u); }
                catch (Exception e) { msg = "Failed: " + e.getMessage(); }
                toast(msg);
                runOnUiThread(new Runnable() { public void run() { refreshInfo(); } });
            } }).start();
        }
    }

    void accept(String id, String uri, String path, String name) {
        if (id == null) { toast("Not a valid PSP game. Pick the folder that contains PSP_GAME, or an .iso file."); return; }
        if (ALLOWED.length > 0 && !Arrays.asList(ALLOWED).contains(id)) { toast("Wrong game (" + id + ")."); return; }
        gameUri = uri; gamePath = path; discId = id; gameName = name; save(); showMain();
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

    // ---------------------------------------------------------------- files / installers
    /** PPSSPP's data folder: the path PPSSPP stored in memstick_dir.txt if any, else the app's own external files dir. */
    File psp() {
        File ms = null;
        try {
            File f = new File(getFilesDir(), "memstick_dir.txt");
            if (f.exists()) { String p = readAll(f).trim(); if (p.startsWith("/") && new File(p).isDirectory()) ms = new File(p); }
        } catch (Exception e) { }
        if (ms == null) { File b = getExternalFilesDir(null); ms = b != null ? b : getFilesDir(); }
        return "PSP".equals(ms.getName()) ? ms : new File(ms, "PSP");
    }
    File drvRoot() { return new File(getFilesDir(), "drivers"); }

    String installMod(Uri u) throws IOException {
        File root = psp(); int n = 0; ZipEntry e;
        ZipInputStream z = new ZipInputStream(getContentResolver().openInputStream(u));
        try {
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String rel = modTarget(e.getName().replace('\\', '/')); if (rel == null) continue;
                File out = safe(root, rel); if (out == null) continue;
                out.getParentFile().mkdirs(); copy(z, out); n++;
            }
        } finally { z.close(); }
        if (n > 0) { sp.edit().putInt("mods", sp.getInt("mods", 0) + n).apply(); }
        return n == 0 ? "Nothing to install in this zip." : "Mod installed (" + n + " files).";
    }
    String modTarget(String name) {
        String low = name.toLowerCase();
        if (low.contains("__macosx") || low.endsWith(".ds_store") || low.endsWith(".url")) return null;
        int i = low.indexOf("textures/");
        if (i == 0 || (i > 0 && low.charAt(i - 1) == '/')) return "TEXTURES/" + name.substring(i + 9);
        if (low.startsWith("psp/")) return name.substring(4);
        return "TEXTURES/" + discId + "/" + name;   // plain texture pack
    }

    String installSave(Uri u) throws IOException {
        File root = psp(); int n = 0; ZipEntry e;
        ZipInputStream z = new ZipInputStream(getContentResolver().openInputStream(u));
        try {
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String rel = saveTarget(e.getName().replace('\\', '/')); if (rel == null) continue;
                File out = safe(root, rel); if (out == null) continue;
                out.getParentFile().mkdirs(); copy(z, out); n++;
            }
        } finally { z.close(); }
        return n == 0 ? "No save folder found in this zip." : "Save imported (" + n + " files).";
    }
    String saveTarget(String name) {
        String[] p = name.split("/");
        for (int i = 0; i < p.length - 1; i++) if (p[i].matches("[A-Z]{4}[0-9]{5}[A-Z0-9]*")) {
            StringBuilder sb = new StringBuilder("SAVEDATA");
            for (int j = i; j < p.length; j++) sb.append('/').append(p[j]);
            return sb.toString();
        }
        return null;
    }

    /** 100% save: backs up the current save, installs the bundled one. Turning it off restores the backup. */
    void setFull(boolean on) {
        if (discId == null || !discId.startsWith(SAVE_ID)) {
            toast("The 100% save is for the US version (" + SAVE_ID + "). Your game is " + discId + ".");
            busy = true; fullSw.setChecked(false); busy = false; return;
        }
        File sd = new File(psp(), "SAVEDATA/" + SAVE_DIR), bak = new File(psp(), "launcher_backup/" + SAVE_DIR);
        try {
            if (on) {
                if (sd.exists() && !bak.exists()) { bak.getParentFile().mkdirs(); if (!sd.renameTo(bak)) del(sd); } else del(sd);
                installBundledSave(sd);
                toast("100% save installed. Your previous save is backed up.");
            } else {
                del(sd); if (bak.exists()) bak.renameTo(sd);
                toast("Previous save restored.");
            }
            full = on; save(); refreshInfo();
        } catch (Exception e) { toast("Failed: " + e.getMessage()); busy = true; fullSw.setChecked(!on); busy = false; }
    }
    void raw(String name, File out) throws IOException {
        int id = getResources().getIdentifier(name, "raw", getPackageName());
        if (id == 0) throw new IOException("missing resource " + name);
        InputStream in = getResources().openRawResource(id); try { out.getParentFile().mkdirs(); copy(in, out); } finally { in.close(); }
    }

    // ---------------------------------------------------------------- first-run setup: PSP folder + starter save
    static final String[] PSP_DIRS = {"GAME", "SAVEDATA", "SYSTEM", "TEXTURES", "PPSSPP_STATE", "CHEATS", "VIDEO", "SCREENSHOT"};

    void installBundledSave(File sd) throws IOException {
        sd.mkdirs();
        raw("save_data", new File(sd, "DATA.BIN")); raw("save_icon0", new File(sd, "ICON0.PNG"));
        raw("save_param", new File(sd, "PARAM.SFO")); raw("save_pic1", new File(sd, "PIC1.PNG"));
    }

    void maybeSetup() {
        if (sp.getBoolean("setup", false) || (setupDialog != null && setupDialog.isShowing())) return;
        if (new File(getFilesDir(), "memstick_dir.txt").exists()) { setupData(false); return; }   // PPSSPP already has a data folder: use it
        if (hasAll()) { setupData(true); return; }
        setupDialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setCancelable(false)
            .setTitle("First-time setup")
            .setMessage("The app creates a PSP folder in your storage (saves, mods, textures) and installs the starter save. It needs \"All files access\".")
            .setPositiveButton("Allow", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); } })
            .setNegativeButton("Skip", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { setupData(false); } })
            .show();
    }

    /** shared = true: data folder is /storage/emulated/0/PSP (visible, survives uninstall). false: app-private folder. */
    void setupData(boolean shared) {
        try {
            File mf = new File(getFilesDir(), "memstick_dir.txt");
            if (!mf.exists()) {   // tell PPSSPP where its data lives (this also skips its own storage question)
                File base = shared ? Environment.getExternalStorageDirectory() : getExternalFilesDir(null);
                if (base == null) base = getFilesDir();
                base.mkdirs();
                FileOutputStream o = new FileOutputStream(mf); o.write(base.getPath().getBytes("UTF-8")); o.close();
            }
            File p = psp();
            for (String d : PSP_DIRS) new File(p, d).mkdirs();
            String msg = "PSP folder ready: " + p.getPath();
            File sd = new File(p, "SAVEDATA/" + SAVE_DIR);
            if (!sd.exists()) { installBundledSave(sd); full = true; msg += "\nStarter save installed."; }
            sp.edit().putBoolean("setup", true).apply(); save();
            toast(msg);
            if (discId != null) showMain();
        } catch (Exception e) { toast("Setup failed: " + e.getMessage()); }
    }

    // ---------------------------------------------------------------- GPU driver (AdrenoTools, handled by PPSSPP itself)
    List<String> drivers() {
        List<String> l = new ArrayList<String>(); File[] fs = drvRoot().listFiles();
        if (fs != null) for (File f : fs) if (new File(f, "meta.json").exists()) l.add(f.getName());
        return l;
    }
    void driverDialog() {
        final List<String> d = drivers(); final String[] items = new String[d.size() + 2];
        items[0] = (drv == null ? "\u2713 " : "") + "Default system driver";
        for (int i = 0; i < d.size(); i++) items[i + 1] = (d.get(i).equals(drv) ? "\u2713 " : "") + d.get(i);
        items[items.length - 1] = "+ Install driver (.zip)";
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("GPU driver (Adreno, Vulkan)")
            .setItems(items, new DialogInterface.OnClickListener() { public void onClick(DialogInterface dlg, int w) {
                if (w == items.length - 1) { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i, RQ_DRV); }
                else selectDriver(w == 0 ? null : d.get(w - 1));
            } }).setNegativeButton("Close", null).show();
    }
    void selectDriver(String name) {
        try {
            File sys = new File(psp(), "SYSTEM"); sys.mkdirs(); File g = new File(sys, "ppsspp.ini");
            setIni(g, "Graphics", "CustomDriver", name == null ? "" : name);
            setIni(g, "Graphics", "GraphicsBackend", "3 (VULKAN)");
            drv = name; save(); refreshInfo(); toast(name == null ? "Default driver selected." : "Driver selected: " + name);
        } catch (IOException e) { toast("Failed: " + e.getMessage()); }
    }
    String installDriver(Uri u) throws Exception {
        File tmp = new File(getCacheDir(), "drv_tmp"); del(tmp); tmp.mkdirs(); ZipEntry e;
        ZipInputStream z = new ZipInputStream(getContentResolver().openInputStream(u));
        try { while ((e = z.getNextEntry()) != null) {
            if (e.isDirectory()) continue;
            File out = safe(tmp, e.getName().replace('\\', '/')); if (out == null) continue;
            out.getParentFile().mkdirs(); copy(z, out);
        } } finally { z.close(); }
        File meta = new File(tmp, "meta.json");
        if (!meta.exists()) { del(tmp); return "Not a valid driver zip (meta.json is missing)."; }
        String name = new JSONObject(readAll(meta)).getString("name");
        if (name.length() == 0 || name.contains("/") || name.contains("..")) { del(tmp); return "Invalid driver name."; }
        drvRoot().mkdirs(); File dst = new File(drvRoot(), name); del(dst);
        if (!tmp.renameTo(dst)) return "Could not install the driver.";
        return "Driver installed: " + name + ". Open Change to select it.";
    }

    // ---------------------------------------------------------------- play
    void play() {
        if (discId == null) { toast("Select a game first."); return; }
        if (gamePath != null && !hasAll()) {
            toast("Allow access to all files, then press PLAY again.");
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); return;
        }
        try {
            File sys = new File(psp(), "SYSTEM"); sys.mkdirs(); File g = new File(sys, discId + "_ppsspp.ini");
            String[][] kv = { {"InternalResolution", "" + res}, {"MultiSampleLevel", "" + (msaa == 2 ? 1 : msaa == 4 ? 2 : 0)},
                {"TexScalingLevel", "" + tex}, {"TexScalingType", "0"}, {"SkipBufferEffects", b(skipBuf)}, {"AutoFrameSkip", b(autoSkip)},
                {"iShowStatusFlags", fps ? "2" : "0"}, {"DisplayStretch", b(stretch)} };
            for (String[] p : kv) setIni(g, "Graphics", p[0], p[1]);
            setIni(g, "Control", "TouchButtonOpacity", "" + opacity);
            setIni(g, "Control", "TouchButtonHideSeconds", "" + hide);
            File gi = new File(sys, "ppsspp.ini");
            setIni(gi, "Control", "TouchButtonOpacity", "" + opacity);        // also global, in case this PPSSPP version keeps it there
            setIni(gi, "Control", "TouchButtonHideSeconds", "" + hide);
            setIni(gi, "General", "PauseMenuExitsEmulator", "True");   // Exit in the pause menu closes the emulator -> back to this launcher
            setIni(gi, "General", "ScreenRotation", "1");               // landscape
            setIni(new File(sys, "ppsspp.ini"), "Graphics", "GraphicsBackend", "3 (VULKAN)");
        } catch (IOException e) { toast("Could not write config: " + e.getMessage()); return; }
        Intent i = new Intent(this, PpssppActivity.class); i.setAction(Intent.ACTION_VIEW); i.addCategory(Intent.CATEGORY_DEFAULT);
        i.putExtra("org.ppsspp.ppsspp.Args", "--pause-menu-exit");
        if (gamePath != null) i.setData(Uri.parse(gamePath));
        else { i.setDataAndType(Uri.parse(gameUri), "application/octet-stream"); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        startActivity(i);
    }
    static String b(boolean v) { return v ? "True" : "False"; }

    // ---------------------------------------------------------------- trailer
    void trailer() {
        final Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout f = new FrameLayout(this); f.setBackgroundColor(0xFF000000);
        final WebView w = new WebView(this); WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setMediaPlaybackRequiresUserGesture(false);
        w.setWebChromeClient(new WebChromeClient());
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");
        w.setWebViewClient(new WebViewClient());
        w.loadUrl("https://m.youtube.com/watch?v=" + TRAILER_ID);
        f.addView(w, new FrameLayout.LayoutParams(-1, -1));
        TextView x = text("\u2715", 22, INK, true); x.setPadding(dp(18), dp(10), dp(18), dp(10)); x.setBackgroundColor(0x99000000);
        x.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } });
        f.addView(x, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
        TextView yt = text("Open in YouTube", 14, ORANGE2, true); yt.setPadding(dp(18), dp(10), dp(18), dp(10)); yt.setBackgroundColor(0x99000000);
        yt.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/" + TRAILER_ID))); } });
        f.addView(yt, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        d.setContentView(f);
        d.setOnDismissListener(new DialogInterface.OnDismissListener() { public void onDismiss(DialogInterface i) { w.loadUrl("about:blank"); w.destroy(); } });
        d.show();
    }

    // ---------------------------------------------------------------- ini + io helpers
    static void setIni(File f, String section, String key, String val) throws IOException {
        List<String> L = new ArrayList<String>();
        if (f.exists()) { BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8")); String s; while ((s = r.readLine()) != null) L.add(s); r.close(); }
        int sec = -1, end = L.size();
        for (int i = 0; i < L.size(); i++) {
            String t = L.get(i).trim();
            if (t.startsWith("[")) { if (sec >= 0) { end = i; break; } if (t.equalsIgnoreCase("[" + section + "]")) sec = i; }
        }
        if (sec < 0) { L.add("[" + section + "]"); L.add(key + " = " + val); }
        else {
            boolean done = false;
            for (int i = sec + 1; i < end; i++) { String t = L.get(i); int eq = t.indexOf('=');
                if (eq > 0 && t.substring(0, eq).trim().equals(key)) { L.set(i, key + " = " + val); done = true; break; } }
            if (!done) L.add(end, key + " = " + val);
        }
        f.getParentFile().mkdirs();
        Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        for (String s : L) w.write(s + "\n");
        w.close();
    }
    static void copy(InputStream in, File out) throws IOException {
        FileOutputStream o = new FileOutputStream(out); byte[] b = new byte[65536]; int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        o.close();
    }
    static File safe(File root, String rel) throws IOException {
        File f = new File(root, rel);
        return f.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator) ? f : null;
    }
    static void del(File f) { if (f.isDirectory()) { File[] k = f.listFiles(); if (k != null) for (File x : k) del(x); } f.delete(); }
    static String readAll(File f) throws IOException {
        FileInputStream in = new FileInputStream(f); ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        in.close(); return new String(o.toByteArray(), "UTF-8");
    }

    // ---------------------------------------------------------------- DISC_ID from ISO / PARAM.SFO
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

    // ---------------------------------------------------------------- small UI helpers
    int dp(float v) { return (int) (v * dens * UI + 0.5f); }
    void toast(final String s) { runOnUiThread(new Runnable() { public void run() { Toast.makeText(LauncherActivity.this, s, Toast.LENGTH_LONG).show(); } }); }
    TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size * UI); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
    GradientDrawable box(int fill, int stroke, int strokeDp, int radiusDp) {
        GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setStroke(dp(strokeDp), stroke); g.setCornerRadius(dp(radiusDp)); return g;
    }
    GradientDrawable bgGradient() { return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1F1030, 0xFF130D1D, 0xFF2B1409}); }

    // ---------------------------------------------------------------- custom views
    /** Orange button: mode 0 = hexagon (PLAY), mode 1 = rounded. */
    static class Shape extends TextView {
        final int mode; final Path path = new Path(); final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        Shape(Context c, String t, int mode, float sp) {
            super(c); this.mode = mode; setText(t); setTextColor(DARK); setTextSize(sp * UI); setTypeface(Typeface.DEFAULT_BOLD);
            setGravity(Gravity.CENTER); setClickable(true);
        }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            fill.setShader(new LinearGradient(0, 0, 0, h, ORANGE2, ORANGE, Shader.TileMode.CLAMP));
            path.reset(); float a = w * 0.06f;
            if (mode == 0) { path.moveTo(a, 0); path.lineTo(w - a, 0); path.lineTo(w, h / 2f); path.lineTo(w - a, h); path.lineTo(a, h); path.lineTo(0, h / 2f); path.close(); }
            else path.addRoundRect(new RectF(0, 0, w, h), 10, 10, Path.Direction.CW);
        }
        @Override protected void onDraw(Canvas c) { c.drawPath(path, fill); super.onDraw(c); }
    }

    /** Segmented control with slanted cells, like the reference design. Option text "1x|272p" = main + small line. */
    static class Seg extends View {
        final String[] o; int sel; final Pick cb; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), t = new Paint(Paint.ANTI_ALIAS_FLAG),
            s2 = new Paint(Paint.ANTI_ALIAS_FLAG); final Path path = new Path(); final float d;
        Seg(Context c, String[] o, int sel, Pick cb) {
            super(c); this.o = o; this.sel = sel; this.cb = cb; d = c.getResources().getDisplayMetrics().density * UI;
            t.setTextAlign(Paint.Align.CENTER); t.setTypeface(Typeface.DEFAULT_BOLD); t.setTextSize(14 * d);
            s2.setTextAlign(Paint.Align.CENTER); s2.setTextSize(10 * d);
        }
        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), (int) (50 * d)); }
        @Override protected void onDraw(Canvas c) {
            int W = getWidth(), H = getHeight(); p.setShader(null); p.setColor(CELL); c.drawRect(0, 0, W, H, p);
            float cw = W / (float) o.length, sk = 8 * d, in = 3 * d;
            for (int i = 0; i < o.length; i++) {
                float x0 = i * cw, x1 = x0 + cw; boolean on = i == sel;
                if (on) {
                    path.reset(); path.moveTo(x0 + sk, in); path.lineTo(x1 - in, in); path.lineTo(x1 - sk - in, H - in); path.lineTo(x0 + in, H - in); path.close();
                    p.setShader(new LinearGradient(0, 0, 0, H, ORANGE2, ORANGE, Shader.TileMode.CLAMP)); c.drawPath(path, p); p.setShader(null);
                }
                String[] parts = o[i].split("\\|"); float cx = (x0 + x1) / 2f;
                t.setColor(on ? DARK : MUTED); s2.setColor(on ? DARK : MUTED);
                if (parts.length > 1) { c.drawText(parts[0], cx, H * 0.47f, t); c.drawText(parts[1], cx, H * 0.78f, s2); }
                else c.drawText(parts[0], cx, H / 2f + 5 * d, t);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                int i = (int) (e.getX() / (getWidth() / (float) o.length)); i = Math.max(0, Math.min(o.length - 1, i));
                sel = i; invalidate(); cb.on(i);
            }
            return true;
        }
    }
}
