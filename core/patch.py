import re, sys, shutil, pathlib

here = pathlib.Path(__file__).parent
root = pathlib.Path(sys.argv[1])
NEW_ID = "com.example.narutocore"

def files(pattern):
    return [p for p in root.rglob(pattern) if ".git" not in p.parts]

# 1) application id: patch whichever gradle file (groovy or kts) declares it
done = False
for p in files("*.gradle") + files("*.gradle.kts"):
    s = p.read_text(errors="ignore")
    s2 = re.sub(r"applicationId(\s*=?\s*)(['\"])org\.ppsspp\.ppsspp\2",
                lambda m: "applicationId%s%s%s%s" % (m.group(1), m.group(2), NEW_ID, m.group(2)), s)
    if s2 != s:
        p.write_text(s2); done = True; print("applicationId patched in", p)
if not done:
    a = root / "android"
    print("android dir:", sorted(x.name for x in a.iterdir()) if a.exists() else "missing")
    sys.exit("applicationId not found in any gradle file")

# 2) find the manifest that declares PpssppActivity
mfs = sorted([p for p in files("AndroidManifest.xml") if "PpssppActivity" in p.read_text(errors="ignore")],
             key=lambda p: len(str(p)))
if not mfs:
    sys.exit("no AndroidManifest.xml mentioning PpssppActivity")
mf = mfs[0]; print("manifest:", mf)

# 3) find PpssppActivity source to learn its package and folder
srcs = [p for p in files("PpssppActivity.*") if p.suffix in (".java", ".kt")]
if not srcs:
    sys.exit("PpssppActivity source not found")
src = srcs[0]; print("activity source:", src)
pkg = re.search(r"^\s*package\s+([\w.]+)", src.read_text(errors="ignore"), re.M).group(1)

# 4) manifest edits
m = mf.read_text()
m = re.sub(r'<category\s+android:name="android\.intent\.category\.LAUNCHER"\s*/>', '', m, count=1)
m = m.replace('android:authorities="org.ppsspp.ppsspp', 'android:authorities="' + NEW_ID)
if "MANAGE_EXTERNAL_STORAGE" not in m:
    assert "<application" in m
    m = m.replace("<application", '<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />\n<application', 1)
act = '''<activity android:name="%s.LauncherActivity" android:label="Naruto Launcher" android:icon="@mipmap/ic_naruto" android:exported="true"
    android:theme="@android:style/Theme.DeviceDefault.NoActionBar">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
''' % pkg
assert "</application>" in m
m = m.replace("</application>", act + "</application>", 1)
mf.write_text(m)

# 5) icon resources next to the manifest
if (here / "res").is_dir():
    shutil.copytree(here / "res", mf.parent / "res", dirs_exist_ok=True)

# 6) launcher screen source, in the same package/folder as PpssppActivity
j = (here / "LauncherActivity.java").read_text().replace("package org.ppsspp.ppsspp;", "package %s;" % pkg, 1)
(src.parent / "LauncherActivity.java").write_text(j)
print("patched ok")
