#!/usr/bin/env python3
"""cleanOS command-line tool.

Talks to an Android TV over ADB (Wi-Fi or USB) to inspect it, back it up, switch off bloat,
install the cleanOS Home launcher and configure it. Standard library only; needs `adb`.

    python3 tools/cleanos.py --tv 192.168.1.50 probe

Run `python3 tools/cleanos.py -h` for all commands.
"""

import argparse
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
