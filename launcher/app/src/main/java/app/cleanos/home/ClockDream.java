package app.cleanos.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Date;

/**
 * Screensaver: the wallpaper drifts in a slow Ken Burns pan/zoom with a large thin clock
 * in the corner, which also moves a little every minute against burn-in. The photo sits on a hardware layer, so the GPU only re-composites it.
 */
public class ClockDream extends DreamService {

    private static final long DRIFT_MS = 40_000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private ImageView photo;
    private LinearLayout clockBlock;
    private TextView time, date, weatherText;
    private ImageView weatherIcon;
    private boolean zoomIn = true;
    private int minuteShift;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateClock();
            // Nudge the clock a little each minute against burn-in.
            minuteShift++;
            float d = Ui.dp(ClockDream.this, 10);
            clockBlock.animate().translationX((float) Math.sin(minuteShift * 0.7) * d * 2)
                    .translationY((float) Math.cos(minuteShift * 0.9) * d).setDuration(2000).start();
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        Ui.init(this);
        setInteractive(false);
        setFullscreen(true);
        setContentView(build());

        registerReceiver(tick, new IntentFilter(Intent.ACTION_TIME_TICK));
        updateClock();
        Weather.load(this, (icon, text) -> {
            boolean show = text != null;
            weatherIcon.setVisibility(show ? View.VISIBLE : View.GONE);
            weatherText.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                weatherIcon.setImageResource(icon);
                weatherText.setText(text);
            }
        });

        // Same picture as Home (the user's or the built-in one), unshaded. Decode off the main thread.
        new Thread(() -> {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            Wallpaper.Set set = Wallpaper.cached(this, dm.widthPixels, dm.heightPixels);
            if (set == null) set = Wallpaper.build(this, dm.widthPixels, dm.heightPixels);
            Bitmap b = set.saver;
            main.post(() -> {
                if (photo == null) return;
                photo.setImageBitmap(b);
                photo.animate().alpha(1f).setDuration(1200).withEndAction(this::drift).start();
            });
        }, "dream-wallpaper").start();
    }

    @Override
    public void onDetachedFromWindow() {
        unregisterReceiver(tick);
        if (photo != null) photo.animate().cancel();
        photo = null;
        super.onDetachedFromWindow();
    }

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setAlpha(0f);
        root.addView(photo, new FrameLayout.LayoutParams(-1, -1));

        // Soft shade bottom-left so the clock always reads, without darkening the whole photo.
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BL_TR,
                new int[]{0xB3000000, 0x40000000, 0x00000000, 0x00000000}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        clockBlock = new LinearLayout(this);
        clockBlock.setOrientation(LinearLayout.VERTICAL);

        time = Ui.text(this, 104, Ui.THIN, Color.WHITE);
        time.setShadowLayer(Ui.dp(this, 12), 0, Ui.dp(this, 2), 0x66000000);
        time.setIncludeFontPadding(true);
        clockBlock.addView(time, new LinearLayout.LayoutParams(-1, -2));

        date = Ui.text(this, 22, Ui.LIGHT, 0xEBFFFFFF);
        date.setLetterSpacing(0.03f);
        date.setShadowLayer(Ui.dp(this, 8), 0, Ui.dp(this, 1), 0x80000000);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.leftMargin = Ui.dp(this, 6);
        clockBlock.addView(date, dl);

        LinearLayout w = new LinearLayout(this);
        w.setOrientation(LinearLayout.HORIZONTAL);
        w.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new ImageView(this);
        weatherIcon.setImageTintList(ColorStateList.valueOf(0xE6FFFFFF));
        int is = Ui.dp(this, 20);
        w.addView(weatherIcon, new LinearLayout.LayoutParams(is, is));
        weatherText = Ui.text(this, 19, Ui.MEDIUM, 0xE6FFFFFF);
        weatherText.setShadowLayer(Ui.dp(this, 8), 0, Ui.dp(this, 1), 0x80000000);
        LinearLayout.LayoutParams wtl = new LinearLayout.LayoutParams(-2, -2);
        wtl.leftMargin = Ui.dp(this, 8);
        w.addView(weatherText, wtl);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
        wl.topMargin = Ui.dp(this, 14);
        wl.leftMargin = Ui.dp(this, 6);
        clockBlock.addView(w, wl);

        // Fixed width: a wrap_content block re-measured on every tick could clip the clock.
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(Ui.dp(this, 520), -2, Gravity.BOTTOM | Gravity.START);
        cl.leftMargin = Ui.dp(this, 64);
        cl.bottomMargin = Ui.dp(this, 56);
        root.addView(clockBlock, cl);
        return root;
    }

    /** Endless slow zoom/pan, alternating direction. */
    private void drift() {
        if (photo == null) return;
        float pan = Ui.dp(this, 26);
        photo.animate()
                .scaleX(zoomIn ? 1.12f : 1.02f).scaleY(zoomIn ? 1.12f : 1.02f)
                .translationX(zoomIn ? -pan : pan)
                .translationY(zoomIn ? -pan / 2 : 0)
                .setDuration(DRIFT_MS)
                .setInterpolator(new LinearInterpolator())
                .withLayer()
                .withEndAction(() -> {
                    zoomIn = !zoomIn;
                    drift();
                })
                .start();
    }

    private void updateClock() {
        Date now = new Date();
        time.setText(DateFormat.format(DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm", now));
        date.setText(DateFormat.format("EEEE, d MMMM", now));
    }
}
