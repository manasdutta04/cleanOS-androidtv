package app.cleanos.home;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Date;

/**
 * Top-right status capsule: weather, network, clock on dark frosted glass, so it reads over
 * any part of the wallpaper. Kept on its own hardware layer: it changes once a minute, so the
 * GPU just composites a cached texture every other frame.
 */
final class StatusBar extends LinearLayout {

    private final ImageView weatherIcon;
    private final TextView weatherText;
    private final View divider;
    private final ImageView netIcon;
    private final TextView clock;

    StatusBar(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int h = Ui.dp(c, 40);
        setMinimumHeight(h);
        int padH = Ui.dp(c, 18);
        setPadding(padH, 0, padH, 0);

        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x8C1C1C1E, 0xA6101012});
        bg.setCornerRadius(h / 2f);
        bg.setStroke(Ui.dp(c, 1), 0x2EFFFFFF);
        setBackground(bg);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        int icon = Ui.dp(c, 17);
        int gap = Ui.dp(c, 14);

        weatherIcon = new ImageView(c);
        weatherIcon.setImageTintList(ColorStateList.valueOf(0xFFFFD60A));
        addView(weatherIcon, new LayoutParams(icon, icon));

        weatherText = Ui.text(c, 16, Ui.MEDIUM, Color.WHITE);
        weatherText.setFontFeatureSettings("tnum");
        LayoutParams wl = new LayoutParams(-2, -2);
        wl.leftMargin = Ui.dp(c, 7);
        addView(weatherText, wl);

        divider = new View(c);
        divider.setBackgroundColor(0x33FFFFFF);
        LayoutParams dl = new LayoutParams(Ui.dp(c, 1), Ui.dp(c, 18));
        dl.leftMargin = gap;
        addView(divider, dl);

        netIcon = new ImageView(c);
        netIcon.setImageResource(R.drawable.ic_wifi);
        netIcon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        LayoutParams nl = new LayoutParams(icon, icon);
        nl.leftMargin = gap;
        addView(netIcon, nl);

        clock = Ui.text(c, 17, Ui.MEDIUM, Color.WHITE);
        clock.setFontFeatureSettings("tnum");
        clock.setGravity(Gravity.END);
        // Reserve room for the widest time so a tick never clips or shifts the capsule.
        clock.setMinWidth((int) Math.ceil(clock.getPaint().measureText("00:00")) + Ui.dp(c, 2));
        LayoutParams cl = new LayoutParams(-2, -2);
        cl.leftMargin = gap;
        addView(clock, cl);

        setWeather(0, null);
        tick();
    }

    void tick() {
        String pattern = DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "h:mm";
        clock.setText(DateFormat.format(pattern, new Date()));
    }

    void setWeather(int iconRes, String text) {
        boolean show = text != null;
        weatherIcon.setVisibility(show ? VISIBLE : GONE);
        weatherText.setVisibility(show ? VISIBLE : GONE);
        divider.setVisibility(show ? VISIBLE : GONE);
        if (show) {
            weatherIcon.setImageResource(iconRes);
            // Sun/moon warm yellow; clouds, rain and storms white.
            boolean warm = iconRes == R.drawable.ic_sun;
            weatherIcon.setImageTintList(ColorStateList.valueOf(warm ? 0xFFFFD60A : Color.WHITE));
            weatherText.setText(text);
        }
    }

    /** level in 0..1, or -1 for offline. */
    void setNetwork(int iconRes, float level) {
        netIcon.setImageResource(iconRes);
        netIcon.setAlpha(level < 0 ? 0.5f : 0.45f + 0.55f * level);
    }
}
