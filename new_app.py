#!/usr/bin/env python3
"""Scaffold a BYOK Android app from the kit.
usage: new_app.py <Dir> <AppName> <pkgSuffix> <primaryHex> <secondaryHex> <tertiaryHex> <iconGlyphPath> [neutralHue]
Colors are seeds; light/dark schemes are derived."""
import sys, os, shutil, subprocess, colorsys, re, secrets

K = os.path.dirname(os.path.abspath(__file__))
name, app, pkg, prim, sec, ter, glyph = sys.argv[1:8]
neutral = sys.argv[8] if len(sys.argv) > 8 else prim
root = f"/root/claude/{name}"
PKG = f"com.mohithash.{pkg}"
src = f"{root}/app/src/main/java/{PKG.replace('.', '/')}"

def hx(h): h = h.lstrip('#'); return tuple(int(h[i:i+2], 16)/255 for i in (0, 2, 4))
def to(rgb): return "0xFF" + "".join(f"{max(0,min(255,round(c*255))):02X}" for c in rgb)
def tone(h, l, s=None):
    hh, ll, ss = colorsys.rgb_to_hls(*hx(h)); return to(colorsys.hls_to_rgb(hh, l, ss if s is None else s))

def scheme(dark):
    def role(h):
        if not dark: return dict(main=tone(h, .32), on=to((1,1,1)), cont=tone(h, .88), oncont=tone(h, .10))
        return dict(main=tone(h, .75), on=tone(h, .15), cont=tone(h, .28), oncont=tone(h, .90))
    p, s, t = role(prim), role(sec), role(ter)
    n = lambda l, sat: tone(neutral, l, sat)
    if not dark:
        surf = dict(bg=n(.975,.35), on=n(.11,.15), var=n(.90,.18), onvar=n(.28,.12), lowest=to((1,1,1)), low=n(.955,.3), c=n(.93,.28), high=n(.905,.25), highest=n(.88,.22), out=n(.45,.10), outv=n(.78,.15))
        err = dict(main="0xFFBA1A1A", on="0xFFFFFFFF", cont="0xFFFFDAD6", oncont="0xFF410002")
    else:
        surf = dict(bg=n(.075,.25), on=n(.90,.10), var=n(.28,.12), onvar=n(.78,.10), lowest=n(.05,.25), low=n(.10,.22), c=n(.125,.20), high=n(.16,.18), highest=n(.20,.16), out=n(.55,.10), outv=n(.28,.12))
        err = dict(main="0xFFFFB4AB", on="0xFF690005", cont="0xFF93000A", oncont="0xFFFFDAD6")
    fn = "darkColorScheme" if dark else "lightColorScheme"
    return f"""{fn}(
        primary = Color({p['main']}), onPrimary = Color({p['on']}), primaryContainer = Color({p['cont']}), onPrimaryContainer = Color({p['oncont']}),
        inversePrimary = Color({tone(prim, .75 if not dark else .32)}),
        secondary = Color({s['main']}), onSecondary = Color({s['on']}), secondaryContainer = Color({s['cont']}), onSecondaryContainer = Color({s['oncont']}),
        tertiary = Color({t['main']}), onTertiary = Color({t['on']}), tertiaryContainer = Color({t['cont']}), onTertiaryContainer = Color({t['oncont']}),
        background = Color({surf['bg']}), onBackground = Color({surf['on']}), surface = Color({surf['bg']}), onSurface = Color({surf['on']}),
        surfaceVariant = Color({surf['var']}), onSurfaceVariant = Color({surf['onvar']}),
        surfaceContainerLowest = Color({surf['lowest']}), surfaceContainerLow = Color({surf['low']}), surfaceContainer = Color({surf['c']}),
        surfaceContainerHigh = Color({surf['high']}), surfaceContainerHighest = Color({surf['highest']}),
        outline = Color({surf['out']}), outlineVariant = Color({surf['outv']}),
        error = Color({err['main']}), onError = Color({err['on']}), errorContainer = Color({err['cont']}), onErrorContainer = Color({err['oncont']}),
    )"""

os.makedirs(src, exist_ok=True)
for d in ["gradle", "gradlew", "settings.gradle.kts", "build.gradle.kts", "gradle.properties", "local.properties"]:
    s = f"/root/claude/CalorieBank/{d}"
    (shutil.copytree if os.path.isdir(s) else shutil.copy)(s, f"{root}/{d}")
os.makedirs(f"{root}/app/src/main/res/values", exist_ok=True)
os.makedirs(f"{root}/app/src/main/res/drawable", exist_ok=True)
os.makedirs(f"{root}/app/src/main/res/mipmap-anydpi-v26", exist_ok=True)
os.makedirs(f"{root}/app/src/test/java/{PKG.replace('.', '/')}", exist_ok=True)
w = lambda p, c: open(p, "w").write(c)
w(f"{root}/settings.gradle.kts", open(f"{root}/settings.gradle.kts").read().replace('rootProject.name = "CalorieBank"', f'rootProject.name = "{name}"'))
bg = open("/root/claude/CalorieBank/app/build.gradle.kts").read().replace("com.mohithash.caloriebank", PKG).replace('versionName = "1.2"', 'versionName = "1.0"').replace("versionCode = 3", "versionCode = 1")
w(f"{root}/app/build.gradle.kts", bg)
shutil.copy(f"{K}/template/proguard-rules.pro", f"{root}/app/proguard-rules.pro")
w(f"{root}/app/proguard-rules.pro", open(f"{root}/app/proguard-rules.pro").read().replace("__PKG__", PKG).replace("domain", "**"))
# kit sources
for rel in ["ai/AiClient.kt", "data/JsonStore.kt", "ui/Ui.kt", "ui/Photo.kt", "ui/theme/Theme.kt", "ui/screens/AiSettingsCard.kt"]:
    os.makedirs(os.path.dirname(f"{src}/{rel}"), exist_ok=True)
    w(f"{src}/{rel}", open(f"{K}/template/src/{rel}").read().replace("__PKG__", PKG))
w(f"{src}/ui/theme/Brand.kt", f"""package {PKG}.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Generated brand palette — seeds {prim} / {sec} / {ter}. */
object Brand {{
    val light = {scheme(False)}
    val dark = {scheme(True)}
    val heroDeep = Color({tone(prim, .18)})
}}
""")
w(f"{root}/app/src/main/AndroidManifest.xml", f"""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />
    <application
        android:name=".App"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.App">
        <activity android:name=".MainActivity" android:exported="true" android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""")
w(f"{root}/app/src/main/res/values/strings.xml", f'<resources>\n    <string name="app_name">{app}</string>\n</resources>\n')
w(f"{root}/app/src/main/res/values/themes.xml", """<resources>
    <style name="Theme.App" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
    </style>
</resources>
""")
w(f"{root}/app/src/main/res/values/colors.xml", f'<resources>\n    <color name="ic_launcher_background">#{tone(prim,.30)[4:]}</color>\n</resources>\n')
w(f"{root}/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml", """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
""")
w(f"{root}/app/src/main/res/drawable/ic_launcher_foreground.xml", f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:scaleX="2.1" android:scaleY="2.1" android:translateX="28.8" android:translateY="28.8">
        <path android:fillColor="#{tone(sec,.85)[4:]}" android:pathData="{glyph}" />
    </group>
</vector>
""")
w(f"{root}/.gitignore", "build/\n.gradle/\nlocal.properties\n*.iml\n.idea/\n.kotlin/\n*.apk\n*.aab\nrelease.jks\nkeystore.properties\n")
pw = secrets.token_urlsafe(15)
subprocess.run(["keytool", "-genkeypair", "-keystore", f"{root}/release.jks", "-alias", pkg, "-keyalg", "RSA", "-keysize", "4096", "-validity", "10000",
                "-storepass", pw, "-keypass", pw, "-dname", f"CN={app}, O=Mohithash"], check=True, capture_output=True)
w(f"{root}/keystore.properties", f"storeFile=release.jks\nstorePassword={pw}\nkeyAlias={pkg}\nkeyPassword={pw}\n")
print(root)
