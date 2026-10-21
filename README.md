# cleanOS for Android TV

[![Build](https://github.com/manasdutta04/cleanOS-androidtv/actions/workflows/build.yml/badge.svg)](https://github.com/manasdutta04/cleanOS-androidtv/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/github/license/manasdutta04/cleanOS-androidtv?color=blue)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%20TV%20%7C%20Google%20TV-3DDC84?logo=android&logoColor=white)](#requirements)
[![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)](#requirements)
[![No root](https://img.shields.io/badge/root-not%20required-success)](#safety)
[![Python 3.8+](https://img.shields.io/badge/python-3.8%2B-3776AB?logo=python&logoColor=white)](tools/cleanos.py)
[![Java 17](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)](launcher/)
[![Last commit](https://img.shields.io/github/last-commit/manasdutta04/cleanOS-androidtv)](https://github.com/manasdutta04/cleanOS-androidtv/commits/main)

**A fast home screen in the Apple TV style for Android TV and Google TV, plus a safe, reversible way to switch off
the bloat your TV shipped with.** No root. Everything runs from your computer over Wi-Fi, and every change can be undone.

Most smart TVs ship with 1–2 GB of RAM, a home screen full of ads and promoted rows, and a dozen
vendor services running in the background. cleanOS replaces the home screen with a tiny native launcher
and helps you switch off what you don't use, so the TV gets back the memory and speed it had in the store.

## What you get

**cleanOS Home** (a native launcher, about 1 MB, no libraries)

- Your picture full-screen, with a greeting where a show title would sit ("Welcome / *your name*", or "Good evening")
- A frosted-glass dock of up to six apps, plus **Apps** (everything installed) and **Settings**
- tvOS-style focus: the tile lifts, a soft light sweeps across it, and its name fades in
- Apps open by growing out of their tile; the dock settles back in when you return
- A status capsule with local weather ([Open-Meteo](https://open-meteo.com/), no account or key), Wi-Fi and the time
- **cleanOS Clock** screensaver: a slow Ken Burns pan over your wallpaper with a large clock that moves slightly each minute (no burn-in)
- Opens the moment the TV's screen turns on (direct boot), even before Android finishes starting

**`cleanos` command-line tool** (Python 3, standard library only)

| Command | What it does |
|---|---|
| `probe` | Model, Android version, memory, current home app and the biggest processes |
| `scan` | Lists your TV's apps: suggested bloat, protected system parts, and the rest to review |
| `debloat LIST --yes` | Switches off the apps in a list file, in small batches (disable, not delete) |
| `install` | Builds and installs cleanOS Home and makes it the home screen |
| `config` | Sets your name, greeting, weather city and dock apps |
| `wallpaper FILE` | Uses your own picture (`--default` for the built-in one) |
| `backup` / `restore` | Saves the TV's state; puts everything back exactly as it was |

Nothing personal is built in: name, city, dock and wallpaper are all empty or default until you set them.

## Requirements

- An Android TV or Google TV device running **Android 11 or newer**
- A computer on the **same network** (Windows, macOS or Linux) with:
  - [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) (`adb`)
  - Python 3.8+
  - To build the launcher yourself: JDK 17+ and the Android SDK (Android Studio includes both).
    Or download a ready-built APK from this repository's **Actions** tab (the `cleanos-home-apk` artifact) and pass it with `install --apk`.

## Quick start

**1. Turn on debugging on the TV**

Settings → System (or Device Preferences) → About → press **Build** seven times. Then in **Developer options**, turn on
**USB debugging**, and **Network debugging** / **Wireless debugging** if your TV has it. Note the TV's IP address
(Settings → Network).

**2. Connect and look around**

```bash
python3 tools/cleanos.py --tv 192.168.1.50 probe
```

The TV asks **"Allow USB debugging?"** the first time. Tick *Always allow* and accept.
(Set `CLEANOS_TV=192.168.1.50` to skip `--tv` from now on.)

**3. Switch off what you don't use**

```bash
python3 tools/cleanos.py debloat lists/google-tv.txt            # shows the plan
python3 tools/cleanos.py debloat lists/google-tv.txt --yes      # applies it
```

Then find your manufacturer's extras:

```bash
python3 tools/cleanos.py scan --write lists/my-tv.txt
```

Open `lists/my-tv.txt`, delete every line you want to **keep**, and run `debloat lists/my-tv.txt --yes`.
`lists/optional.txt` covers things some people use (voice search, Chromecast, phone remote, YouTube Music).

**4. Install the new home screen**

```bash
python3 tools/cleanos.py install
```

On many TVs the stock launcher outranks every other home app. If `install` says so, run it again with
`--replace-stock`. It switches the stock launcher off only after confirming cleanOS Home works, and turns it back on if the
switch fails.

**5. Make it yours**

```bash
python3 tools/cleanos.py config --name "Sam" --city "Lisbon"
python3 tools/cleanos.py config --dock "com.netflix.ninja,com.google.android.youtube.tv,com.android.vending"
python3 tools/cleanos.py wallpaper ~/Pictures/mountains.jpg
```

- `--name`: shown large at the top. Leave it out for a time-of-day greeting.
- `--greeting`: the small line above the name (default "Welcome").
- `--city`: a place ("Springfield, Illinois") or coordinates ("38.72,-9.14"). An empty value hides the weather.
- `--dock`: up to six package names, in order. Find them with `scan --all` or in the Play Store URL (`id=...`).
  With no dock set, popular apps you have installed are used.

**6. Turn USB debugging off again.** While it's on, anyone on your network can control the TV.

## Undo

```bash
python3 tools/cleanos.py restore              # back to the original state (the first backup)
python3 tools/cleanos.py restore --uninstall  # ...and remove cleanOS Home
```

`debloat` and `install` save a backup to `backups/` first, and `restore` uses the oldest one by default. It re-enables
every app that was on, reinstalls anything removed, and restores the original home app, screensaver and animation
settings. Backups list your installed apps, so `backups/` is git-ignored.

## Safety

- Apps are **disabled** (`pm disable-user`), never deleted. `--remove` uninstalls for your user only; the factory copy
  stays and `restore` brings it back.
- `debloat` refuses anything on the protected list: system UI, Settings, Play services, the keyboard, storage and
  networking, plus vendor parts whose names point to the picture, inputs, tuner, setup or factory services
  (`tvinput`, `tvcenter`, `tv.service`, `hdmi`, `factory`, `setupwizard`, overlays). See `protected()` in `tools/cleanos.py`.
- Changes are applied five at a time, so if something you use stops working, the culprit is easy to find.
- Firmware updaters are not on any bundled list. If you switch a vendor updater off with your own list, turn it back on
  to check for updates.

## How it stays fast on weak TV hardware

Most TV graphics chips are slow, so cleanOS Home does as little as possible per frame:

- The wallpaper is cropped to the exact screen size with its shading baked in, and set as the window background: one bitmap draw per frame.
- The frosted glass is a pre-blurred copy of the wallpaper, cut to the dock's shape once. There's no live blur.
- Tile corners and highlights are baked into each tile image. Nothing is clipped at runtime, which many TV GPUs do without anti-aliasing.
- Focus only animates scale, elevation and transparency, which the GPU handles without redrawing anything.
- App banners are loaded at their highest built-in resolution, fitted to the tile without cropping, and cached on disk.
- `install` compiles the app ahead of time, so the first launch skips the JIT warm-up.
- Settings and caches live in device-protected storage, so Home can draw before Android unlocks the user's storage at boot.

## Troubleshooting

| Problem | Fix |
|---|---|
| `Could not connect` | The TV and computer must be on the same network, with debugging on. Some TVs only listen on port 5555 after you toggle USB debugging off and on. |
| `unauthorized` | Accept the prompt on the TV. If it never appears, turn USB debugging off and on, then connect again. |
| The old home screen comes back | Run `install --replace-stock`. |
| An app is missing from the dock | It isn't installed or is disabled. Check with `scan --all`. |
| Weather doesn't show | Check `--city` spelling, or use coordinates. The TV needs internet access. |
| Something stopped working after `debloat` | `restore` puts everything back. Then debloat in smaller lists to find the culprit. |

Fire TV: Fire OS doesn't let third-party apps replace the home screen, so only `debloat` applies there.

## Limits

- Android 14 doesn't let an ordinary app close other apps, so cleanOS can't kill apps when you leave them.
  Android already freezes apps in the background and reclaims their memory when it's needed.
- Boot time before the screen turns on is set by the TV's hardware and firmware.

## Project layout

```
launcher/   cleanOS Home (Java, plain Android views)
tools/      cleanos.py command-line tool and its tests
lists/      debloat lists
licenses/   third-party licenses (fonts)
```

Run the tests with `python3 -m unittest discover -s tools` and `cd launcher && ./gradlew testDebugUnitTest`.

## License

MIT, see [LICENSE](LICENSE). Bundled fonts (Inter, Montserrat) are under the SIL Open Font License; see [licenses/](licenses/).
