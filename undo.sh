#!/usr/bin/env bash
# Reverses install.sh, using the state file it wrote.
#   sh undo.sh <TV_IP>
set -euo pipefail
cd "$(dirname "$0")"

IP="${1:?usage: sh undo.sh <TV_IP>}"
TV="$IP:5555"
STATE="state-$IP.txt"
export MSYS_NO_PATHCONV=1
sh_tv() { adb -s "$TV" shell "$@"; }

[ -f "$STATE" ] || { echo "No $STATE: nothing recorded for this TV." >&2; exit 1; }
adb connect "$TV" >/dev/null
adb -s "$TV" wait-for-device

if grep -q '^launcher$' "$STATE"; then
  sh_tv "settings delete secure enabled_accessibility_services; settings put secure accessibility_enabled 0"
  adb -s "$TV" uninstall com.family.home || true # the firmware's own launcher takes Home again
  sh_tv setprop ctl.start drop_caches # the home screen had switched off the factory cache flusher
  sh_tv rm -f /data/local/chrome-command-line # and given Chrome its ad blocklist
fi

if grep -q '^tweaks$' "$STATE"; then
  sh_tv "settings delete secure enabled_accessibility_services; settings put secure accessibility_enabled 0"
  adb -s "$TV" uninstall com.family.tweaks || true
  sh_tv setprop ctl.start drop_caches # the helper had switched off the factory cache flusher
fi

for p in $(awk '$1 == "removed" {print $2}' "$STATE"); do
  out=$(sh_tv cmd package install-existing "$p" | tr -d '\r')
  echo "  $p: $out"
  # Apps that were normal installs are gone from the TV: put back the copy install.sh made.
  case "$out" in *"doesn't exist"*|*"not installed"*|*Error*|*Fail*)
    [ -f "backup/$p.apk" ] && adb -s "$TV" install "backup/$p.apk" ;;
  esac
done

if grep -q '^animations$' "$STATE"; then
  sh_tv "settings put global window_animation_scale 1; settings put global transition_animation_scale 1; settings put global animator_duration_scale 1"
fi

if grep -q '^local.prop$' "$STATE"; then
  adb -s "$TV" root >/dev/null; sleep 4; adb connect "$TV" >/dev/null; adb -s "$TV" wait-for-device
  sh_tv rm -f /data/local.prop
fi

echo "Done. Restart the TV to finish."
