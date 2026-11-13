#!/usr/bin/env bash
# Cleans up a CVTE / MediaTek Android 9 TV and installs the Accueil home screen, or only the
# Optimiseur TV helper if you keep your own launcher.
#   sh install.sh <TV_IP> [list]        (default list: lists/cvte-sk518d-mt5862.txt)
# The TV must be on the same network with ADB on (see README: factory menu > Debug > ADB Enable).
# Every step asks first. undo.sh reverses all of it.
set -euo pipefail
cd "$(dirname "$0")"

IP="${1:?usage: sh install.sh <TV_IP> [list]}"
LIST="${2:-lists/cvte-sk518d-mt5862.txt}"
TV="$IP:5555"
STATE="state-$IP.txt" # what was changed, for undo.sh
export MSYS_NO_PATHCONV=1 # Git Bash: don't rewrite /data/... paths

ask() { printf '%s [y/N] ' "$1"; read -r a; [ "$a" = y ] || [ "$a" = Y ]; }
sh_tv() { adb -s "$TV" shell "$@"; }

adb connect "$TV" >/dev/null
adb -s "$TV" wait-for-device
echo "Connected: $(sh_tv getprop ro.product.board | tr -d '\r'), Android $(sh_tv getprop ro.build.version.release | tr -d '\r')"
sh_tv "grep MemAvailable /proc/meminfo; df -h /data | tail -1"
: > "$STATE"

# 1. Junk apps
PKGS=$(grep -vE '^\s*(#|$)' "$LIST" | awk '{print $1}')
echo; echo "Apps in $LIST:"; echo "$PKGS" | sed 's/^/  /'
if ask "Remove these for the TV's user (restorable)?"; then
  mkdir -p backup
  for p in $PKGS; do
    path=$(sh_tv pm path "$p" 2>/dev/null | head -1 | tr -d '\r' | sed 's/^package://')
    [ -z "$path" ] && { echo "  $p: not installed"; continue; }
    adb -s "$TV" pull "$path" "backup/$p.apk" >/dev/null 2>&1 || echo "  $p: backup copy failed (Wi-Fi?)"
    echo "  $p: $(sh_tv pm uninstall -k --user 0 "$p" | tr -d '\r')"
    echo "removed $p" >> "$STATE"
  done
fi

# 2. Bigger app memory limit: some IPTV apps crash loading their channel list at 48MB.
if ask "Raise the per-app memory limit from 48MB to 96MB (needs root ADB, userdebug firmware)?"; then
  adb -s "$TV" root >/dev/null; sleep 4; adb connect "$TV" >/dev/null; adb -s "$TV" wait-for-device
  sh_tv "echo dalvik.vm.heapgrowthlimit=96m > /data/local.prop && chmod 644 /data/local.prop" \
    && echo "local.prop" >> "$STATE" && echo "  done, applies after a restart"
fi

# 3. Animations: off for app switches, half speed elsewhere.
if ask "Turn off window animations (apps open instantly)?"; then
  sh_tv "settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0.5"
  echo "animations" >> "$STATE"
fi

# 4. Home screen
if ask "Build and install the Accueil home screen?"; then
  sh launcher/build.sh
  adb -s "$TV" install -r launcher/Accueil.apk
  sh_tv "cmd package set-home-activity com.family.home/.HomeActivity"
  # Lets the home screen turn its Home guard back on after updates.
  sh_tv "pm grant com.family.home android.permission.WRITE_SECURE_SETTINGS"
  # The guard reopens Accueil when the firmware forces its own launcher (at boot, some keys).
  sh_tv "settings put secure enabled_accessibility_services com.family.home/com.family.home.HomeGuard; settings put secure accessibility_enabled 1"
  echo "launcher" >> "$STATE"
  echo
  echo "Last step, on the TV: press Home. If Android asks which app to use, pick Accueil, then 'Always'."
# 5. Or keep the TV's own launcher and only add the background fixes the home screen would apply.
elif ask "Keep your launcher, but install the invisible Optimiseur TV helper (cache flusher off, Wi-Fi power save off)?"; then
  sh tweaks/build.sh
  adb -s "$TV" install -r tweaks/Optimiseur.apk
  # Started by the firmware at boot as an accessibility service; it listens to no events.
  sh_tv "settings put secure enabled_accessibility_services com.family.tweaks/com.family.tweaks.KeepAlive; settings put secure accessibility_enabled 1"
  echo "tweaks" >> "$STATE"
fi

echo; sh_tv "grep MemAvailable /proc/meminfo; df -h /data | tail -1"
echo "Changes recorded in $STATE. Restart the TV (unplug it for 10 s) and check it starts normally."
