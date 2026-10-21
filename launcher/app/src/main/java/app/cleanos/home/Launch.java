package app.cleanos.home;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserManager;
import android.view.View;
import android.widget.Toast;

/**
 * Opens a tile's app or screen; the new window grows out of the tile, like tvOS.
 *
 * Home starts before Android unlocks user storage at boot (direct boot), when other apps can't run
 * yet. A tile pressed in those seconds is remembered and opened as soon as the TV is ready.
 */
final class Launch {

    private static Item pending;

    private Launch() {}

    static void open(Activity a, Item item, View from) {
        UserManager um = a.getSystemService(UserManager.class);
        if (item.pkg != null && um != null && !um.isUserUnlocked()) {
            pending = item;
            Toast.makeText(a, "Opening " + item.title + " as soon as the TV finishes starting…", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent i;
            if (item.pkg != null) {
                PackageManager pm = a.getPackageManager();
                i = pm.getLeanbackLaunchIntentForPackage(item.pkg);
                if (i == null) i = pm.getLaunchIntentForPackage(item.pkg);
                if (i == null) {
                    i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + item.pkg));
                    Toast.makeText(a, item.title + " can't be opened. Showing it in the Play Store.", Toast.LENGTH_SHORT).show();
                }
            } else {
                i = new Intent(item.action);
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Bundle opts = from == null || from.getWidth() == 0 ? null
                    : ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle();
            a.startActivity(i, opts);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(a, "Couldn't open " + item.title, Toast.LENGTH_SHORT).show();
        }
    }

    /** Call once the user is unlocked: opens a tile pressed during boot. */
    static void onUnlocked(Activity a) {
        Item item = pending;
        pending = null;
        if (item != null) open(a, item, null);
    }
}
