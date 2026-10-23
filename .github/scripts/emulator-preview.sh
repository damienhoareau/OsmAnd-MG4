#!/usr/bin/env bash
# Runs inside the Android emulator (started by reactivecircus/android-emulator-runner).
# Installs the APK, enables the "car interface" in demo mode, launches the app and
# saves screenshots + UI dump + logcat to $OUT.
set -u
APK="$1"
OUT="${2:-preview-out}"
PKG=net.osmand.dev
ACT=net.osmand.plus.activities.MapActivity
PREFS=shared_prefs/net.osmand.settings.xml
mkdir -p "$OUT"

shot() { adb exec-out screencap -p > "$OUT/$1.png" || true; }

# Screen = the area OsmAnd gets on the head unit (about 90% of the 1920x720 panel, estimated from
# photos). Hide the emulator's own status/navigation bars: the head unit has none in that area.
adb shell wm size "${SCREEN_SIZE:-1744x640}"
adb shell wm density "${SCREEN_DENSITY:-240}"
adb shell settings put global policy_control "immersive.full=*" || true

adb install -r "$APK" || { echo "install failed"; exit 1; }
for perm in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE; do
  adb shell pm grant "$PKG" "android.permission.$perm" || true
done

# First launch creates the app's data dir and settings file, then stop it.
adb shell am start -n "$PKG/$ACT"
sleep 25
shot 00-first-launch
adb shell am force-stop "$PKG"
sleep 2

# Merge our two booleans into the settings XML (create it if missing).
adb exec-out run-as "$PKG" cat "$PREFS" > "$OUT/prefs-before.xml" 2>/dev/null || true
python3 - "$OUT/prefs-before.xml" "$OUT/prefs-after.xml" <<'PY'
import re, sys
src, dst = sys.argv[1], sys.argv[2]
try:
    xml = open(src, encoding="utf-8").read()
except OSError:
    xml = ""
if "<map" not in xml:
    xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n</map>\n"
for key in ("car_mode_ui", "car_mode_ui_demo"):
    xml = re.sub(r'\s*<boolean name="%s"[^>]*/>' % key, "", xml)
    xml = xml.replace("</map>", '    <boolean name="%s" value="true" />\n</map>' % key)
open(dst, "w", encoding="utf-8").write(xml)
PY
adb exec-in run-as "$PKG" sh -c "cat > $PREFS" < "$OUT/prefs-after.xml" || echo "writing prefs failed" | tee -a "$OUT/notes.txt"
adb exec-out run-as "$PKG" cat "$PREFS" > "$OUT/prefs-on-device.xml" 2>&1 || true
adb exec-out run-as "$PKG" ls -la shared_prefs > "$OUT/shared-prefs-ls.txt" 2>&1 || true

adb shell am start -n "$PKG/$ACT"
sleep 20
# Dismiss the "Welcome / download maps" sheet shown on first start by tapping SKIP DOWNLOAD.
adb shell uiautomator dump /sdcard/ui-skip.xml >/dev/null 2>&1 && adb pull /sdcard/ui-skip.xml "$OUT/ui-skip.xml" >/dev/null 2>&1 || true
python3 - "$OUT/ui-skip.xml" > "$OUT/skip-tap.txt" 2>&1 <<'PY' || true
import re, sys
x = open(sys.argv[1], encoding="utf-8").read()
m = re.search(r'text="SKIP DOWNLOAD"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
if m:
    l, t, r, b = map(int, m.groups())
    print((l + r) // 2, (t + b) // 2)
PY
if [ -s "$OUT/skip-tap.txt" ]; then adb shell input tap $(cat "$OUT/skip-tap.txt"); fi
for t in 15 30 45; do
  sleep 15
  shot "car-ui-$t"s
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/ui-$t.xml" >/dev/null 2>&1 || true
done
adb logcat -d -t 4000 > "$OUT/logcat.txt" || true
adb logcat -d -s CarModeOverlay:I > "$OUT/carmode-log.txt" || true
adb shell wm size > "$OUT/screen.txt"; adb shell wm density >> "$OUT/screen.txt"
echo "done"
