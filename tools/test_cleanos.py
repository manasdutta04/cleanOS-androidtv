"""Tests for the parts of the CLI that don't need a TV: safety rules and list parsing.

    python3 -m unittest discover -s tools
"""
import tempfile
import unittest
from pathlib import Path

import cleanos


class ProtectedTest(unittest.TestCase):

    def test_core_system_is_protected(self):
        for pkg in ("android", "com.android.systemui", "com.android.tv.settings", "com.google.android.gms",
                    "com.android.vending", "com.google.android.webview", "com.android.providers.tv",
                    "com.android.networkstack.tethering", cleanos.PKG):
            self.assertIsNotNone(cleanos.protected(pkg), pkg)

    def test_vendor_picture_and_input_parts_are_protected(self):
        for pkg in ("com.vendor.tvinput", "com.vendor.wwtv.tvcenter", "com.vendor.tv.service",
                    "com.vendor.factory", "com.vendor.setupwizard", "com.vendor.overlay.tvsettings",
                    "com.vendor.hdmi.cec", "com.google.android.tungsten.setupwraith"):
            self.assertIsNotNone(cleanos.protected(pkg), pkg)

    def test_common_bloat_is_not_protected(self):
        for pkg in ("com.google.android.tvrecommendations", "com.google.android.feedback",
                    "com.google.android.leanbacklauncher.partnercustomizer", "com.google.android.apps.mediashell",
                    "com.google.android.katniss", "com.vendor.store"):
            self.assertIsNone(cleanos.protected(pkg), pkg)

    def test_bundled_lists_contain_nothing_protected(self):
        for f in (cleanos.ROOT / "lists").glob("*.txt"):
            for pkg in cleanos.read_list(f):
                self.assertIsNone(cleanos.protected(pkg), "%s in %s" % (pkg, f.name))


class BloatHintTest(unittest.TestCase):

    def test_hints_match_whole_name_parts(self):
        self.assertIsNotNone(cleanos.bloat_reason("com.vendor.acr.service", set()))
        self.assertIsNotNone(cleanos.bloat_reason("tv.vendor.fast", set()))
        self.assertIsNotNone(cleanos.bloat_reason("com.vendor.appstore.store", set()))

    def test_hints_do_not_misfire(self):
        self.assertIsNone(cleanos.bloat_reason("com.android.providers.downloads", set()))
        self.assertIsNone(cleanos.bloat_reason("com.example.breakfast", set()))
        self.assertIsNone(cleanos.bloat_reason("com.example.loadsaver", set()))

    def test_known_lists_win(self):
        self.assertEqual("listed in lists/", cleanos.bloat_reason("com.a.b", {"com.a.b"}))


class ReadListTest(unittest.TestCase):

    def test_comments_blanks_duplicates_and_junk(self):
        with tempfile.TemporaryDirectory() as d:
            f = Path(d) / "list.txt"
            f.write_text("# header\n\ncom.a.b   # why\ncom.a.b\nnot a package\nrm -rf /\ncom.c.d\n", encoding="utf-8")
            self.assertEqual(["com.a.b", "com.c.d"], cleanos.read_list(f))


if __name__ == "__main__":
    unittest.main()
