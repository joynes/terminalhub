#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
EMULATOR="${EMULATOR:-$HOME/Library/Android/sdk/emulator/emulator}"
AVD_NAME="${AVD_NAME:-Medium_Phone_API_36.1}"
OUTPUT_DIR="${1:-$REPO_ROOT/marketing-output/play/phone}"
PACKAGE="se.joynes.terminalhub.diag"
ACTIVITY="$PACKAGE/se.joynes.terminalhub.marketing.MarketingPreviewActivity"

if [[ -z "${JAVA_HOME:-}" && -x "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" ]]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$PATH"

mkdir -p "$OUTPUT_DIR"

SERIAL="${ADB_SERIAL:-$("$ADB" devices | awk '$1 ~ /^emulator-/ { print $1; exit }')}"

if [[ -z "$SERIAL" ]]; then
  "$EMULATOR" -avd "$AVD_NAME" -no-snapshot-save -no-boot-anim >/tmp/terminalhub-marketing-emulator.log 2>&1 &
fi

for _ in $(seq 1 60); do
  SERIAL="${SERIAL:-$("$ADB" devices | awk '$1 ~ /^emulator-/ { print $1; exit }')}"
  [[ -n "$SERIAL" ]] && break
  sleep 1
done

if [[ -z "$SERIAL" ]]; then
  echo "No Android emulator appeared. See /tmp/terminalhub-marketing-emulator.log" >&2
  exit 1
fi

adb_cmd() {
  "$ADB" -s "$SERIAL" "$@"
}

for _ in $(seq 1 90); do
  if [[ "$(adb_cmd shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
    break
  fi
  sleep 1
done

if [[ "$(adb_cmd shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]]; then
  echo "Emulator did not finish booting. See /tmp/terminalhub-marketing-emulator.log" >&2
  exit 1
fi

cd "$REPO_ROOT"
./gradlew :app:assembleDiagnosticDebug
adb_cmd install -r app/build/outputs/apk/diagnostic/debug/app-diagnostic-debug.apk >/dev/null

adb_cmd shell wm size 1080x1920
adb_cmd shell wm density 420
adb_cmd shell settings put global sysui_demo_allowed 1 || true
adb_cmd shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1000 >/dev/null || true
adb_cmd shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null || true
adb_cmd shell am broadcast -a com.android.systemui.demo -e command network -e mobile show -e datatype 5g -e level 4 >/dev/null || true
adb_cmd shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null || true

capture() {
  local index="$1"
  local scene="$2"
  local filename="$3"
  adb_cmd shell am force-stop "$PACKAGE"
  adb_cmd shell am start -W -n "$ACTIVITY" --es scene "$scene" >/dev/null
  sleep 2
  if [[ "$scene" == "prompt" ]]; then
    adb_cmd shell input keyevent 4
    sleep 1
  fi
  adb_cmd exec-out screencap -p > "$OUTPUT_DIR/$index-$filename.png"
}

capture 01 sessions persistent-project-tabs
capture 02 resume tmux-resume
capture 03 prompt multiline-project-input
capture 04 files project-file-transfer
capture 05 opensource open-source-about

echo "Captured Play screenshots in $OUTPUT_DIR"
