# Architecture

What each file does, and why it was built that way. The target is a 512MB TV running Android 9
(API 28) with a debug (`userdebug`) firmware, so every choice favours little memory and no
dependencies.

## Layout

| Path | What |
|---|---|
| `VERSION` | The project version (SemVer). Both apps and the changelog follow it. |
| `install.sh` | Interactive setup over ADB: removes apps from `lists/`, raises the app memory limit, speeds up animations, then installs either the home screen or the helper. Writes `state-<ip>.txt`. |
| `undo.sh` | Reverses exactly what `state-<ip>.txt` records. |
| `lists/cvte-sk518d-mt5862.txt` | Apps safe to remove on this board, one per line, with what each one is. |
| `launcher/` | **Accueil**, the home screen (`com.family.home`). |
| `tweaks/` | **Optimiseur TV**, the background fixes on their own (`com.family.tweaks`). |
| `docs/home.png` | The README screenshot. |

## Build

Both apps build with the plain SDK tools (`aapt2`, `javac`, `d8`, `zipalign`, `apksigner`) from
`build.sh`, without Gradle or libraries.

- **Why no Gradle:** a first build needs no download beyond the SDK, and the APKs stay small (the
  helper is 17KB). Nothing here needs AndroidX: the framework APIs of API 23–28 are enough.
- **Java 8 bytecode, `core-lambda-stubs.jar` on the boot classpath:** lets `d8` desugar lambdas for
  `--min-api 23`.
- **Version:** `versionName` is `VERSION`; `versionCode` is `MAJOR*10000 + MINOR*100 + PATCH`,
  so it always grows, as Android requires for updates.
- **Signing:** each app makes its own key on the first build (`*.keystore`, git-ignored). Updates
  must be signed with the same key, so keep it.

## Home screen (`launcher/src/com/family/home`)

| File | Role |
|---|---|
| `HomeActivity.java` | The screen: greeting and clock, shortcut buttons, live HDMI 1 preview, glass card, pinned apps (hold OK for move, replace, remove), glow colours. |
| `Ui.java` | Shared colours, sizes, the background bitmap, glass and focus drawables, app tiles, banner and colour helpers. |
| `CornerMask.java` | Draws rounded corners **over** the HDMI video (a hardware layer that views can't clip) with an exact copy of the background and glow behind them, plus the focus ring. |
| `LiveAmbient.java` | Samples ~150 pixels of the preview every 3.5s from `screencap` over the TV's own ADB, for the glow. Also runs the shell commands of `Tweaks`. |
| `Tweaks.java` | Stops the factory cache flusher and keeps Wi-Fi power save off (see below). |
| `AdBlock.java` | Writes or deletes Chrome's command-line file with a `--host-resolver-rules` blocklist from `res/values/adblock.xml`. |
| `HomeGuard.java` | Accessibility service: reopens Accueil when the firmware forces its own launcher and starts `Tweaks` at boot. Its `ensureEnabled()`, called by `HomeActivity`, turns it back on after an update. |
| `AppsActivity.java` | Grid of all apps; also the picker for adding or replacing a pin. |
| `HdmiActivity.java` | Full-screen HDMI fallback if the firmware's TV player is missing. |
| `BootReceiver.java` | Opens Accueil on `BOOT_COMPLETED`. This firmware never sends it to added apps (`HomeGuard` does the job here); kept for units that do. |
| `res/values/apps.xml` | Your household's pinned apps and per-app glow colours. |
| `res/values/adblock.xml` | The ad networks Chrome can't reach while ad blocking is on. |

## Helper (`tweaks/src/com/family/tweaks`)

| File | Role |
|---|---|
| `KeepAlive.java` | Accessibility service that listens to nothing; it exists so the firmware starts the app at boot and keeps it alive. Starts `Tweaks`. |
| `Tweaks.java` | Same fixes as the home screen's `Tweaks`. |
| `Adb.java` | The ADB wire protocol, reduced to running one command at a time (no screen capture). |

`Tweaks` exists twice (home screen and helper) on purpose: the two apps never run together
(`install.sh` installs one or the other), and sharing code between two Gradle-free builds would need
a third build step for a 60-line class.

## Decisions

1. **Accessibility service as the way to start at boot.** The firmware doesn't deliver
   `BOOT_COMPLETED` to added apps, but starts enabled accessibility services and keeps their process
   alive. The services declare no events of interest beyond what they need and can't read windows
   (`canRetrieveWindowContent="false"`).
2. **System changes through the TV's own ADB (127.0.0.1:5555).** These firmwares ship network ADB
   with no key (`ro.adb.secure=0`). Running `setprop ctl.stop`, `iw` and `screencap` through it needs
   no root exploit and no system-partition change. Root mode (`root:`) is only asked for `iw`; it
   adds no exposure, since anyone who can reach ADB can already ask for it.
3. **Stop the cache flusher instead of editing it.** `/vendor/bin/drop_caches.sh` (started from
   `/vendor/etc/init/init.fusion.rc`) runs `echo 2 > /proc/sys/vm/drop_caches` every 10s. It is
   stopped after each boot with `setprop ctl.stop drop_caches`, leaving the vendor partition untouched.
4. **Wi-Fi power save off on every reconnect.** Android re-enables it after each DHCP, so it is turned
   off at start, 5s after each Wi-Fi `onAvailable`, and every 5 minutes.
5. **App memory limit in `/data/local.prop`.** On debug firmware, init reads it at boot:
   `dalvik.vm.heapgrowthlimit=96m` (stock 48m, `heapsize` 128m). Removing the file undoes it.
6. **`pm uninstall -k --user 0`, not `pm disable-user`.** The firmware re-enables disabled apps when
   Android restarts; uninstalling for user 0 sticks and `cmd package install-existing` reverses it.
7. **Ad blocking only inside Chrome.** System-wide DNS blocking (Private DNS) breaks some IPTV apps and,
   on a mobile 5G line, failed DNS-over-TLS cut all lookups. Chrome reads
   `/data/local/chrome-command-line` on debug firmware, so the blocklist reaches Chrome alone.
8. **Draw once, tint later.** The background is drawn once into a hardware bitmap; glows are a small
   white mask stretched and tinted, so moving between items changes a tint, not pixels.
