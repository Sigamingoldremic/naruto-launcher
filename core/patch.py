import re, sys, shutil, pathlib

here = pathlib.Path(__file__).parent
app = pathlib.Path(sys.argv[1]) / "android"

# 1) new application id so it can live next to the normal PPSSPP
bg = app / "build.gradle"
s = bg.read_text()
s2 = re.sub(r"applicationId\s+(['\"])org\.ppsspp\.ppsspp\1", "applicationId 'com.example.narutocore'", s)
assert s2 != s, "applicationId line not found in android/build.gradle"
bg.write_text(s2)

# 2) manifest: hide PPSSPP's own icon, add our launcher screen, make sure all-files permission exists
mf = app / "AndroidManifest.xml"
m = mf.read_text()
m = re.sub(r'<category\s+android:name="android\.intent\.category\.LAUNCHER"\s*/>', '', m, count=1)
m = m.replace('android:authorities="org.ppsspp.ppsspp', 'android:authorities="com.example.narutocore')
if "MANAGE_EXTERNAL_STORAGE" not in m:
    assert "<application" in m
    m = m.replace("<application", '<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />\n<application', 1)
act = '''<activity android:name=".LauncherActivity" android:label="Naruto Launcher" android:icon="@mipmap/ic_naruto" android:exported="true"
    android:theme="@android:style/Theme.DeviceDefault.NoActionBar">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
'''
assert "</application>" in m
m = m.replace("</application>", act + "</application>", 1)
mf.write_text(m)

# 2b) our icon resources
if (here / "res").is_dir():
    shutil.copytree(here / "res", app / "res", dirs_exist_ok=True)

# 3) launcher screen source
dst = app / "src" / "org" / "ppsspp" / "ppsspp"
assert dst.is_dir(), "source dir not found: %s" % dst
shutil.copy(here / "LauncherActivity.java", dst / "LauncherActivity.java")
print("patched ok")
