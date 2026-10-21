package app.cleanos.home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;

import java.util.HashMap;
import java.util.Map;

/** One tile: an installed app, or a built-in screen (Settings, All apps). */
final class Item {

    final String title;
    final int color;      // placeholder colour while the art loads
    final String pkg;     // app, or null for a built-in screen
    final Intent action;  // built-in screen
    final int glyphRes;   // icon for built-in screens
    volatile ArtCache.Art art;

    private Item(String title, int color, String pkg, Intent action, int glyphRes) {
        this.title = title;
        this.color = color;
        this.pkg = pkg;
        this.action = action;
        this.glyphRes = glyphRes;
    }

    /** Brand colours for a few common apps (only used as the placeholder behind the art). */
    private static final Map<String, Integer> BRAND = new HashMap<>();

    static {
        BRAND.put("com.google.android.youtube.tv", 0xFFE62117);
        BRAND.put("com.netflix.ninja", 0xFFB20710);
        BRAND.put("com.amazon.amazonvideo.livingroom", 0xFF0F79AF);
        BRAND.put("com.disney.disneyplus", 0xFF0A1F5C);
        BRAND.put("com.spotify.tv.android", 0xFF1DB954);
        BRAND.put("com.android.vending", 0xFF0B8F5C);
    }

    /** An installed, enabled app, or null. */
    static Item app(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, ArtCache.ANY_BOOT);
            if (!ai.enabled) return null;
            if (pm.getLeanbackLaunchIntentForPackage(pkg) == null && pm.getLaunchIntentForPackage(pkg) == null
                    && isUnlocked(c)) return null;
            String label = String.valueOf(pm.getApplicationLabel(ai));
            Integer color = BRAND.get(pkg);
            return new Item(label, color != null ? color : 0xFF2C2C30, pkg, null, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    static Item settings() {
        return new Item("Settings", 0xFF8E8E93, null, new Intent(Settings.ACTION_SETTINGS), R.drawable.ic_settings);
    }

    private static boolean isUnlocked(Context c) {
        android.os.UserManager um = c.getSystemService(android.os.UserManager.class);
        return um == null || um.isUserUnlocked();
    }
}
