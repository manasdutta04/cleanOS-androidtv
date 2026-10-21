package app.cleanos.home;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

/** Opens a tile's app or screen; the new window grows out of the tile, like tvOS. */
final class Launch {

    private Launch() {}

    static void open(Activity a, Item item, View from) {
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
}
