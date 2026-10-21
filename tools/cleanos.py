#!/usr/bin/env python3
"""cleanOS command-line tool.

Talks to an Android TV over ADB (Wi-Fi or USB) to inspect it, back it up, switch off bloat,
install the cleanOS Home launcher and configure it. Standard library only; needs `adb`.

    python3 tools/cleanos.py --tv 192.168.1.50 probe

Run `python3 tools/cleanos.py -h` for all commands.
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PKG = "app.cleanos.home"
HOME_ACTIVITY = PKG + "/.HomeActivity"
DREAM = PKG + "/.ClockDream"


class AdbError(RuntimeError):
    pass


def find_adb():
    """adb from $ADB, PATH, or the Android SDK."""
    candidates = [os.environ.get("ADB"), shutil.which("adb")]
    for env in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(env):
            candidates.append(str(Path(os.environ[env]) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")))
    if os.name == "nt" and os.environ.get("LOCALAPPDATA"):
        candidates.append(str(Path(os.environ["LOCALAPPDATA"]) / "Android" / "Sdk" / "platform-tools" / "adb.exe"))
    for c in candidates:
        if c and Path(c).is_file():
            return c
    raise AdbError("adb not found. Install Android platform-tools or set $ADB.")


class Tv:
    """One connected TV."""

    def __init__(self, adb, serial):
        self.adb_path = adb
        self.serial = serial

    def adb(self, *args, check=True, timeout=120):
        cmd = [self.adb_path, "-s", self.serial, *args]
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        out = (p.stdout or "") + (p.stderr or "")
        if check and p.returncode != 0:
            raise AdbError("adb %s failed: %s" % (" ".join(args[:2]), out.strip()))
        return out.strip()

    def sh(self, command, check=False):
        """Run a shell command on the TV. `command` is passed as one string."""
        return self.adb("shell", command, check=check)

    def prop(self, name):
        return self.sh("getprop " + name)

    def packages(self, flags=""):
        out = self.sh("pm list packages " + flags)
        return {line[8:].strip() for line in out.splitlines() if line.startswith("package:")}

    def home(self):
        out = self.sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        lines = [l.strip() for l in out.splitlines() if "/" in l]
        return lines[-1] if lines else ""


def connect(target):
    """Connect to `target` (host[:port]) or use the only device adb already sees."""
    adb = find_adb()
    if target:
        serial = target if ":" in target else target + ":5555"
        out = subprocess.run([adb, "connect", serial], capture_output=True, text=True, timeout=30).stdout
        for _ in range(20):
            state = subprocess.run([adb, "-s", serial, "get-state"], capture_output=True, text=True).stdout.strip()
            if state == "device":
                return Tv(adb, serial)
            if "unauthorized" in (out + state):
                print("Accept the 'Allow USB debugging?' prompt on the TV (tick 'Always allow')...")
            time.sleep(1.5)
        raise AdbError("Could not connect to %s. Is USB/network debugging on and the TV on this network?" % serial)
    devices = subprocess.run([adb, "devices"], capture_output=True, text=True).stdout.splitlines()[1:]
    ready = [d.split()[0] for d in devices if d.strip().endswith("device")]
    if len(ready) != 1:
        raise AdbError("Pass --tv <ip> (adb sees %d devices)." % len(ready))
    return Tv(adb, ready[0])


# ---------------------------------------------------------------------------------------- commands

COMMANDS = {}


def command(name, help_text, args=()):
    """Register a sub-command. `args` is a list of (flags, kwargs) for argparse."""
    def wrap(fn):
        COMMANDS[name] = (fn, help_text, args)
        return fn
    return wrap


def mem_kb(tv, key):
    m = re.search(r"%s:\s+(\d+)" % key, tv.sh("cat /proc/meminfo"))
    return int(m.group(1)) if m else 0


@command("probe", "Show what the TV is and what is using its memory")
def cmd_probe(tv, args):
    print("Model        %s %s" % (tv.prop("ro.product.brand"), tv.prop("ro.product.model")))
    print("Android      %s (API %s), %s" % (tv.prop("ro.build.version.release"), tv.prop("ro.build.version.sdk"),
                                             tv.prop("ro.product.cpu.abi")))
    print("Screen       %s" % tv.sh("wm size").replace("\n", ", "))
    total, free = mem_kb(tv, "MemTotal"), mem_kb(tv, "MemAvailable")
    print("Memory       %d MB total, %d MB available" % (total // 1024, free // 1024))
    print("Home app     %s" % tv.home())
    print("\nBiggest processes (PSS):")
    out = tv.sh("dumpsys meminfo")
    section = out.split("Total PSS by process:")[-1].split("Total PSS by OOM")[0]
    for line in [l.strip() for l in section.splitlines() if l.strip()][:12]:
        print("  " + line)


BACKUP_DIR = ROOT / "backups"


def take_backup(tv, reason="manual"):
    """Saves what restore needs: package states, Home app, screensaver and animation settings."""

    data = {
        "taken": time.strftime("%Y-%m-%d %H:%M:%S"),
        "reason": reason,
        "android": tv.prop("ro.build.version.release"),
        "enabled": sorted(tv.packages("-e")),
        "disabled": sorted(tv.packages("-d")),
        "home": tv.home(),
        "screensaver_components": tv.sh("settings get secure screensaver_components"),
        "screensaver_enabled": tv.sh("settings get secure screensaver_enabled"),
        "window_animation_scale": tv.sh("settings get global window_animation_scale"),
        "transition_animation_scale": tv.sh("settings get global transition_animation_scale"),
        "animator_duration_scale": tv.sh("settings get global animator_duration_scale"),
    }
    BACKUP_DIR.mkdir(exist_ok=True)
    path = BACKUP_DIR / ("%s-%s.json" % (time.strftime("%Y%m%d-%H%M%S"), reason))
    path.write_text(json.dumps(data, indent=2), encoding="utf-8")
    print("Backup saved: %s" % path.relative_to(ROOT))
    return path


def latest_backup():
    files = sorted(BACKUP_DIR.glob("*.json")) if BACKUP_DIR.is_dir() else []
    # The oldest backup is the original state; prefer it for a full restore.
    return files[0] if files else None


@command("backup", "Save the TV's current app and settings state to backups/")
def cmd_backup(tv, args):
    take_backup(tv)


@command("restore", "Undo everything: re-enable apps, restore Home, screensaver and animations", args=[
    (["backup"], {"nargs": "?", "help": "backup file (default: the oldest one, i.e. the original state)"}),
    (["--uninstall"], {"action": "store_true", "help": "also uninstall cleanOS Home"}),
])
def cmd_restore(tv, args):

    path = Path(args.backup) if args.backup else latest_backup()
    if not path or not path.is_file():
        raise AdbError("No backup found. Pass a file from backups/.")
    data = json.loads(path.read_text(encoding="utf-8"))
    print("Restoring from %s (taken %s)" % (path.name, data["taken"]))

    enabled_now = tv.packages("-e")
    installed_now = tv.packages()
    for pkg in data["enabled"]:
        if pkg == PKG:
            continue
        if pkg not in installed_now:
            print("  reinstall %s: %s" % (pkg, tv.sh("cmd package install-existing --user 0 " + pkg)))
        elif pkg not in enabled_now:
            print("  enable    %s: %s" % (pkg, tv.sh("pm enable " + pkg)))

    home = data.get("home", "")
    if home and "/" in home and not home.startswith(PKG):
        tv.sh("cmd package set-home-activity --user 0 " + home)
    for key in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        val = data.get(key, "null")
        tv.sh("settings %s global %s %s" % ("delete" if val == "null" else "put", key, "" if val == "null" else val))
    saver = data.get("screensaver_components", "null")
    if saver and saver != "null":
        tv.sh("settings put secure screensaver_components " + saver)
    if args.uninstall:
        print("  uninstall cleanOS Home: %s" % tv.adb("uninstall", PKG, check=False))
    tv.sh("input keyevent KEYCODE_HOME")
    print("Done. Home is now: %s" % tv.home())


# Packages the TV needs to work. debloat never touches these.
PROTECTED_EXACT = {
    "android", PKG,
    "com.android.systemui", "com.android.settings", "com.android.tv.settings", "com.android.shell",
    "com.android.vending", "com.google.android.gms", "com.google.android.gsf",
    "com.google.android.webview", "com.android.webview", "com.google.android.ext.services",
    "com.google.android.inputmethod.latin", "com.android.inputdevices", "com.android.bluetooth",
    "com.android.permissioncontroller", "com.google.android.permissioncontroller",
    "com.android.packageinstaller", "com.google.android.packageinstaller", "com.android.externalstorage",
    "com.android.keychain", "com.android.location.fused", "com.android.se", "com.android.intentresolver",
    "com.android.tv.frameworkpackagestubs", "com.android.htmlviewer", "com.android.certinstaller",
    # Google TV setup: disabling it can make the TV forget the Wi-Fi it was set up with.
    "com.google.android.tungsten.setupwraith",
}
PROTECTED_PREFIXES = ("com.android.providers.", "com.android.networkstack", "com.google.android.overlay.",
                      "com.android.server.", "com.google.android.modulemetadata")
# Vendor parts that drive the picture, inputs, tuner and remote on most TVs.
PROTECTED_WORDS = ("tvinput", "tvcenter", "tv.service", "tvservice", "factory", "setupwizard", "overlay",
                   ".rro", "auto_generated", "framework", "hdmi", "inputmethod", "keyboard", "remote.control",
                   "systemui")


def protected(pkg):
    """Why `pkg` must not be disabled, or None if it may be."""
    if pkg in PROTECTED_EXACT:
        return "core system"
    if pkg.startswith(PROTECTED_PREFIXES):
        return "core system"
    low = pkg.lower()
    for w in PROTECTED_WORDS:
        if w in low:
            return "contains '%s' (picture, input, setup or system part)" % w
    return None


def read_list(path):
    """Package names from a list file: one per line, '#' starts a comment."""
    pkgs = []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        name = line.split("#", 1)[0].strip()
        if name and re.fullmatch(r"[A-Za-z][\w]*(\.[\w]+)+", name) and name not in pkgs:
            pkgs.append(name)
    return pkgs


@command("debloat", "Switch off the apps in one or more list files (reversible)", args=[
    (["lists"], {"nargs": "+", "help": "list files, e.g. lists/google-tv.txt"}),
    (["--yes"], {"action": "store_true", "help": "apply the changes (otherwise only show the plan)"}),
    (["--remove"], {"action": "store_true", "help": "uninstall for this user instead of disabling (still restorable)"}),
])
def cmd_debloat(tv, args):
    wanted = []
    for f in args.lists:
        wanted += [p for p in read_list(f) if p not in wanted]
    installed, enabled = tv.packages(), tv.packages("-e")

    plan, skipped = [], []
    for p in wanted:
        why = protected(p)
        if why:
            skipped.append((p, "protected: " + why))
        elif p not in installed:
            skipped.append((p, "not on this TV"))
        elif p not in enabled and not args.remove:
            skipped.append((p, "already off"))
        else:
            plan.append(p)

    for p, why in skipped:
        print("  skip   %-50s %s" % (p, why))
    for p in plan:
        print("  %s %s" % ("remove" if args.remove else "disable", p))
    if not plan:
        print("Nothing to do.")
        return
    if not args.yes:
        print("\n%d app(s) would be switched off. Run again with --yes to apply." % len(plan))
        return

    take_backup(tv, "before-debloat")
    for i in range(0, len(plan), 5):  # small batches: easy to see what broke, if anything does
        for p in plan[i:i + 5]:
            cmd = ("pm uninstall -k --user 0 " if args.remove else "pm disable-user --user 0 ") + p
            print("  %s -> %s" % (p, tv.sh(cmd)))
        for p in plan[i:i + 5]:
            tv.sh("am force-stop " + p)
    free = mem_kb(tv, "MemAvailable") // 1024
    print("\nDone. %d MB of memory available now. Undo any time with: cleanos restore" % free)


# --- end of commands ---


def main(argv=None):
    parser = argparse.ArgumentParser(prog="cleanos", description="Clean up an Android TV and give it a fast, tvOS-style home screen.")
    parser.add_argument("--tv", default=os.environ.get("CLEANOS_TV"), help="TV address, host[:port] (or set CLEANOS_TV)")
    sub = parser.add_subparsers(dest="command", required=True)
    for name, (fn, help_text, extra) in COMMANDS.items():
        p = sub.add_parser(name, help=help_text, description=help_text)
        for flags, kwargs in extra:
            p.add_argument(*flags, **kwargs)
    args = parser.parse_args(argv)
    fn = COMMANDS[args.command][0]
    try:
        tv = None if getattr(fn, "offline", False) else connect(args.tv)
        return fn(tv, args) or 0
    except AdbError as e:
        print("error: %s" % e, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
