package app.cleanos.home;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * User settings. Nothing personal is built in: name, city and dock start empty and are set from a
 * computer with one adb command (see README), e.g.
 * <pre>adb shell am start -n app.cleanos.home/.HomeActivity --es name "Sam" --es city "Lisbon"</pre>
 * Stored in device-protected storage so Home can read them at boot, before the user unlocks.
 */
final class Config {

    static final int CHANGED_TEXT = 1, CHANGED_WEATHER = 2, CHANGED_DOCK = 4, CHANGED_WALLPAPER = 8;

    /** Used when no dock is configured: popular TV apps, shown only if installed. */
    static final List<String> DEFAULT_DOCK = Collections.unmodifiableList(java.util.Arrays.asList(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.spotify.tv.android",
            "com.android.vending"));

    static final int MAX_DOCK_APPS = 6;

    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");

    private Config() {}

    static SharedPreferences prefs(Context c) {
        return c.createDeviceProtectedStorageContext().getSharedPreferences("cleanos", Context.MODE_PRIVATE);
    }

    static String name(Context c) {
        return prefs(c).getString("name", "").trim();
    }

    /** Small line above the name. Empty means "pick a greeting for the time of day". */
    static String greeting(Context c) {
        return prefs(c).getString("greeting", "").trim();
    }

    static String city(Context c) {
        return prefs(c).getString("city", "").trim();
    }

    /** Configured dock packages, or an empty list to use {@link #DEFAULT_DOCK}. */
    static List<String> dock(Context c) {
        return parseList(prefs(c).getString("dock", ""));
    }

    /**
     * Applies settings passed as intent extras: name, greeting, city, dock (comma separated
     * packages), reset (clears everything) and reload_wallpaper. Returns CHANGED_* flags.
     */
    static int apply(Context c, Intent i) {
        if (i == null || i.getExtras() == null) return 0;
        SharedPreferences.Editor e = prefs(c).edit();
        int changed = 0;
        if (i.getBooleanExtra("reset", false)) {
            e.clear();
            changed = CHANGED_TEXT | CHANGED_WEATHER | CHANGED_DOCK | CHANGED_WALLPAPER;
        }
        if (i.hasExtra("name")) {
            e.putString("name", clean(i.getStringExtra("name"), 24));
            changed |= CHANGED_TEXT;
        }
        if (i.hasExtra("greeting")) {
            e.putString("greeting", clean(i.getStringExtra("greeting"), 32));
            changed |= CHANGED_TEXT;
        }
        if (i.hasExtra("city")) {
            e.putString("city", clean(i.getStringExtra("city"), 80));
            changed |= CHANGED_WEATHER;
        }
        if (i.hasExtra("dock")) {
            e.putString("dock", String.join(",", parseList(i.getStringExtra("dock"))));
            changed |= CHANGED_DOCK;
        }
        if (i.getBooleanExtra("reload_wallpaper", false)) changed |= CHANGED_WALLPAPER;
        e.apply();
        return changed;
    }

    /** Splits "a, b c" into valid, de-duplicated package names, in order. */
    static List<String> parseList(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String part : raw.split("[,\\s]+")) {
            String p = part.trim();
            if (PACKAGE.matcher(p).matches()) seen.add(p);
        }
        out.addAll(seen);
        return out;
    }

    /** "Good morning" / "Good afternoon" / "Good evening" for an hour of day (0-23). */
    static String timeGreeting(int hour) {
        if (hour >= 5 && hour < 12) return "Good morning";
        if (hour >= 12 && hour < 17) return "Good afternoon";
        return "Good evening";
    }

    private static String clean(String s, int max) {
        if (s == null) return "";
        s = s.replaceAll("\\p{Cntrl}", "").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}
