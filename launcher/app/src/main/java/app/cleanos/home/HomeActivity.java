package app.cleanos.home;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Home screen, in the Apple TV style: a full-screen wallpaper (set as the window background,
 * so it costs one bitmap draw per frame), a Top Shelf-style greeting, a status capsule and one
 * frosted-glass dock of app tiles.
 */
public class HomeActivity extends Activity {

    private static final int SIDE_DP = 44;

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<AppTile> tiles = new ArrayList<>();

    private int screenW, screenH;
    private Wallpaper.Set wallpaper;
    private StatusBar status;
    private TextView greetSmall, greetBig, dateLine;
    private FrameLayout dockArea;
    private ImageView glass;
    private boolean leftHome; // something covered Home; animate the dock back in

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            status.tick();
            updateGreeting();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            rebuildDock();
        }
    };

    /** Home may start before unlock (direct boot): finish the parts that need user storage. */
    private final BroadcastReceiver unlockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            rebuildDock();
            loadWallpaper(false); // picks up the user's own picture if the cache was built while locked
            Launch.onUnlocked(HomeActivity.this);
        }
    };

    private final ConnectivityManager.NetworkCallback netCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onCapabilitiesChanged(Network n, NetworkCapabilities caps) {
            int icon;
            float level;
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = getSystemService(WifiManager.class);
                int rssi = caps.getSignalStrength();
                icon = R.drawable.ic_wifi;
                level = (wm == null || rssi == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) ? 1f
                        : wm.calculateSignalLevel(rssi) / (float) Math.max(1, wm.getMaxSignalLevel());
            } else {
                icon = R.drawable.ic_ethernet;
                level = 1f;
            }
            main.post(() -> status.setNetwork(icon, level));
        }

        @Override
        public void onLost(Network n) {
            main.post(() -> status.setNetwork(R.drawable.ic_wifi_off, -1f));
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        Config.apply(this, getIntent());

        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;

        // Cached wallpaper is a few small decodes: show it in the very first frame.
        wallpaper = Wallpaper.cached(this, screenW, screenH);
        if (wallpaper != null) getWindow().setBackgroundDrawable(new BitmapDrawable(getResources(), wallpaper.home));
        else loadWallpaper(false);

        setContentView(build());
        rebuildDock();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packageReceiver, pf);
        registerReceiver(unlockReceiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));

        status.setNetwork(R.drawable.ic_wifi_off, -1f);
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(netCallback);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        int changed = Config.apply(this, intent);
        if ((changed & Config.CHANGED_TEXT) != 0) updateGreeting();
        if ((changed & Config.CHANGED_WEATHER) != 0) {
            Weather.clear(this);
            Weather.load(this, status::setWeather);
        }
        if ((changed & Config.CHANGED_DOCK) != 0) rebuildDock();
        if ((changed & Config.CHANGED_WALLPAPER) != 0) loadWallpaper(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        status.tick();
        updateGreeting();
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, tf);
        Weather.load(this, status::setWeather);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (leftHome) {
            leftHome = false;
            dockArea.setTranslationY(Ui.dp(this, 14));
            dockArea.setAlpha(0.6f);
            dockArea.animate().translationY(0).alpha(1f).setDuration(260)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        }
    }

    @Override
    protected void onStop() {
        leftHome = true;
        unregisterReceiver(timeReceiver);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        unregisterReceiver(unlockReceiver);
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.unregisterNetworkCallback(netCallback);
        bg.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Home has nowhere to go back to.
        if (keyCode == KeyEvent.KEYCODE_BACK) return true;
        return super.onKeyDown(keyCode, event);
    }

    // ---------------------------------------------------------------- wallpaper

    /** Builds (or rebuilds) the wallpaper off the main thread, then fades it in. */
    private void loadWallpaper(boolean force) {
        bg.execute(() -> {
            Wallpaper.Set set = force ? null : Wallpaper.cached(this, screenW, screenH);
            if (set == null) set = Wallpaper.build(this, screenW, screenH);
            Wallpaper.Set s = set;
            main.post(() -> {
                Drawable from = getWindow().getDecorView().getBackground();
                if (from == null) from = new ColorDrawable(getColor(R.color.backdrop));
                TransitionDrawable t = new TransitionDrawable(new Drawable[]{from, new BitmapDrawable(getResources(), s.home)});
                t.setCrossFadeEnabled(true);
                getWindow().setBackgroundDrawable(t);
                t.startTransition(400);
                wallpaper = s;
                renderGlass();
            });
        });
    }

    // ---------------------------------------------------------------- layout

    private View build() {
        Context c = this;
        int side = Ui.dp(c, SIDE_DP);

        FrameLayout root = new FrameLayout(c);
        root.setClipChildren(false);

        // Top Shelf chevron, as on tvOS.
        ImageView chevron = new ImageView(c);
        chevron.setImageResource(R.drawable.ic_chevron_up);
        chevron.setImageTintList(ColorStateList.valueOf(0x99FFFFFF));
        int cs = Ui.dp(c, 22);
        FrameLayout.LayoutParams chl = new FrameLayout.LayoutParams(cs, cs, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        chl.topMargin = Ui.dp(c, 10);
        root.addView(chevron, chl);

        status = new StatusBar(c);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        sl.topMargin = Ui.dp(c, 30);
        sl.rightMargin = side;
        root.addView(status, sl);

        // Greeting, styled like a Top Shelf show title. Static, so it lives on a cached layer.
        LinearLayout greet = new LinearLayout(c);
        greet.setOrientation(LinearLayout.VERTICAL);
        greet.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        greetSmall = Ui.text(c, 30, Ui.LIGHT, Color.WHITE);
        greetSmall.setLetterSpacing(0.01f);
        greetSmall.setShadowLayer(Ui.dp(c, 12), 0, Ui.dp(c, 2), 0x73000000);
        greet.addView(greetSmall);
        greetBig = Ui.text(c, 72, Ui.DISPLAY, Color.WHITE);
        greetBig.setLetterSpacing(0.035f);
        greetBig.setShadowLayer(Ui.dp(c, 18), 0, Ui.dp(c, 3), 0x73000000);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        bl.topMargin = Ui.dp(c, 2);
        greet.addView(greetBig, bl);
        dateLine = Ui.text(c, 13, Ui.MEDIUM, 0xD9FFFFFF);
        dateLine.setLetterSpacing(0.16f);
        dateLine.setShadowLayer(Ui.dp(c, 8), 0, Ui.dp(c, 1), 0x80000000);
        LinearLayout.LayoutParams dtl = new LinearLayout.LayoutParams(-2, -2);
        dtl.topMargin = Ui.dp(c, 12);
        dtl.leftMargin = Ui.dp(c, 3);
        greet.addView(dateLine, dtl);
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(-2, -2);
        gl.leftMargin = side + Ui.dp(c, 4);
        gl.topMargin = Ui.dp(c, 54);
        root.addView(greet, gl);

        // Dock area: glass slab + tile row, rebuilt whenever the app list changes.
        dockArea = new FrameLayout(c);
        dockArea.setClipChildren(false);
        dockArea.setClipToPadding(false);
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dl.bottomMargin = Ui.dp(c, 28); // room under the slab for the focused tile's name
        root.addView(dockArea, dl);
        return root;
    }

    /** Name if set, otherwise a greeting for the time of day; the date underneath. */
    private void updateGreeting() {
        if (greetBig == null) return;
        String name = Config.name(this);
        String greeting = Config.greeting(this);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (name.isEmpty()) {
            greetSmall.setVisibility(View.GONE);
            greetBig.setText(greeting.isEmpty() ? Config.timeGreeting(hour) : greeting);
        } else {
            greetSmall.setVisibility(View.VISIBLE);
            greetSmall.setText(greeting.isEmpty() ? "Welcome" : greeting);
            greetBig.setText(name);
        }
        // Long names shrink to fit beside the status capsule.
        float max = screenW * 0.6f;
        greetBig.setTextSize(TypedValue.COMPLEX_UNIT_SP, 72);
        float wNow = greetBig.getPaint().measureText(String.valueOf(greetBig.getText()));
        if (wNow > max) greetBig.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(36f, 72f * max / wNow));
        dateLine.setText(DateFormat.format("EEEE, d MMMM", new Date()).toString().toUpperCase(Locale.getDefault()));
    }

    // ---------------------------------------------------------------- dock

    private List<Item> dockItems() {
        List<String> pkgs = Config.dock(this);
        if (pkgs.isEmpty()) pkgs = Config.DEFAULT_DOCK;
        List<Item> items = new ArrayList<>();
        for (String p : pkgs) {
            if (items.size() >= Config.MAX_DOCK_APPS) break;
            if (p.equals(getPackageName())) continue;
            Item it = Item.app(this, p);
            if (it != null) items.add(it);
        }
        // No configured or default app installed: fill with the first apps on the TV.
        if (items.isEmpty()) {
            for (Item it : AppsActivity.list(this)) {
                if (items.size() >= Config.MAX_DOCK_APPS - 1) break;
                items.add(it);
            }
        }
        items.add(Item.allApps(this));
        items.add(Item.settings());
        return items;
    }

    private void rebuildDock() {
        Context c = this;
        View focused = getCurrentFocus();
        int focusIndex = focused instanceof AppTile ? tiles.indexOf(focused) : 0;
        dockArea.removeAllViews();
        tiles.clear();

        List<Item> items = dockItems();
        int side = Ui.dp(c, SIDE_DP);
        int pad = Ui.dp(c, 16);
        int gap = Ui.dp(c, 18);
        int maxW = screenW - 2 * side;
        // Tiles are sized for a full dock; fewer apps make a shorter, centred dock, not giant tiles.
        int slots = Math.max(items.size(), Config.MAX_DOCK_APPS + 2);
        int tileW = (maxW - 2 * pad - gap * (slots - 1)) / slots;
        int tileH = tileW * 3 / 5;
        int dockW = items.size() * tileW + (items.size() - 1) * gap + 2 * pad;
        int dockH = tileH + 2 * pad;

        glass = new ImageView(c);
        glass.setScaleType(ImageView.ScaleType.FIT_XY);
        glass.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l > 0 && (r - l != or - ol || b - t != ob - ot || glass.getDrawable() == null)) renderGlass();
        });
        dockArea.addView(glass, new FrameLayout.LayoutParams(dockW, dockH));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(pad, pad, pad, 0);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            AppTile tile = new AppTile(c, item, tileW, tileH);
            tile.setOnClickListener(v -> tile.press(() -> Launch.open(this, item, tile.card)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, -2);
            if (i > 0) lp.leftMargin = gap;
            row.addView(tile, lp);
            tiles.add(tile);
        }
        dockArea.addView(row, new FrameLayout.LayoutParams(dockW, -2));

        loadArt();
        tiles.get(Math.min(Math.max(focusIndex, 0), tiles.size() - 1)).requestFocus();
    }

    private void renderGlass() {
        if (glass == null || wallpaper == null || glass.getWidth() == 0) return;
        int[] at = new int[2];
        glass.getLocationInWindow(at);
        glass.setImageBitmap(Glass.render(wallpaper.blur, screenW, screenH, at[0], at[1],
                glass.getWidth(), glass.getHeight(), Ui.dp(this, 22), Ui.dp(this, 1)));
    }

    private void loadArt() {
        Context app = getApplicationContext();
        List<AppTile> snapshot = new ArrayList<>(tiles);
        bg.execute(() -> {
            for (AppTile tile : snapshot) {
                Item item = tile.item;
                ArtCache.Art art = item.pkg == null
                        ? ArtCache.glyph(app, item.glyphRes, item.color, Ui.darken(item.color, 0.55f))
                        : ArtCache.get(app, item.pkg, item.color);
                item.art = art;
                main.post(() -> {
                    if (art != null) tile.setArt(art);
                });
            }
        });
    }
}
