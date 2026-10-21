package app.cleanos.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Every launchable app in a grid of tvOS-style tiles, over the blurred wallpaper. */
public class AppsActivity extends Activity {

    private static final int COLUMNS = 5;

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenW = dm.widthPixels, screenH = dm.heightPixels;

        Wallpaper.Set wp = Wallpaper.cached(this, screenW, screenH);
        Drawable dim = new ColorDrawable(0x8C000000);
        getWindow().setBackgroundDrawable(wp == null ? new ColorDrawable(getColor(R.color.backdrop))
                : new LayerDrawable(new Drawable[]{new BitmapDrawable(getResources(), wp.blur), dim}));

        int side = Ui.dp(this, 56);
        int gap = Ui.dp(this, 26);
        int tileW = (screenW - 2 * side - gap * (COLUMNS - 1)) / COLUMNS;
        int tileH = tileW * 3 / 5;

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        column.setPadding(side, Ui.dp(this, 40), side, Ui.dp(this, 48));

        TextView title = Ui.text(this, 30, Ui.LIGHT, Color.WHITE);
        title.setText("Apps");
        column.addView(title);

        List<Item> items = list(this);
        List<AppTile> tiles = new ArrayList<>();
        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.topMargin = Ui.dp(this, i == 0 ? 28 : 18);
                column.addView(row, rl);
            }
            Item item = items.get(i);
            AppTile tile = new AppTile(this, item, tileW, tileH, true);
            tile.setOnClickListener(v -> tile.press(() -> Launch.open(this, item, tile.card)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, -2);
            if (i % COLUMNS > 0) lp.leftMargin = gap;
            row.addView(tile, lp);
            tiles.add(tile);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setSmoothScrollingEnabled(true);
        scroll.addView(column, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        if (!tiles.isEmpty()) tiles.get(0).requestFocus();

        Context app = getApplicationContext();
        bg.execute(() -> {
            for (AppTile t : tiles) {
                ArtCache.Art art = ArtCache.get(app, t.item.pkg, t.item.color);
                if (art != null) main.post(() -> t.setArt(art));
            }
        });
    }

    @Override
    protected void onDestroy() {
        bg.shutdownNow();
        super.onDestroy();
    }

    /** Launchable apps (TV and phone-style), one per package, sorted by name. Excludes this launcher. */
    static List<Item> list(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, Boolean> pkgs = new LinkedHashMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) pkgs.put(ri.activityInfo.packageName, true);
        }
        pkgs.remove(c.getPackageName());
        List<Item> items = new ArrayList<>();
        for (String p : pkgs.keySet()) {
            Item it = Item.app(c, p);
            if (it != null) items.add(it);
        }
        items.sort((a, b) -> a.title.toLowerCase(Locale.ROOT).compareTo(b.title.toLowerCase(Locale.ROOT)));
        return items;
    }
}
