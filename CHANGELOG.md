# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-11-13

First public release, tested on two mt5862_512m TVs.

### Added

- `install.sh` and `undo.sh`: app removal with backups, 96MB app memory limit, faster animations,
  and either the home screen or the helper, with every change recorded for undo.
- Accueil home screen: live HDMI 1 preview with rounded corners, glass card, up to 6 pinned apps
  (move, replace, remove), shortcuts for apps, sources, casting and settings, Wi-Fi signal and clock.
- Live ambient light from the HDMI picture; per-app glow colours in `apps.xml`.
- Chrome-only ad blocking with an on/off switch on Chrome's tile.
- Background fixes: factory cache flusher stopped, Wi-Fi power save kept off.
- Optimiseur TV helper: the background fixes alone, for TVs that keep their own launcher.
- App list for the TP.SK518D.PB806 / mt5862 board.

[1.0.0]: https://github.com/nomino0/cvte-tv-home/releases/tag/v1.0.0
