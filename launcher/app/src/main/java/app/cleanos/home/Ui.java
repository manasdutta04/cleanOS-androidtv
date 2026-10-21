package app.cleanos.home;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.widget.TextView;

/** Shared look: fonts, sizes and a few drawing helpers. */
final class Ui {
    // Inter + Montserrat (SIL OFL, see /licenses). Loaded once by init().
    static Typeface THIN, LIGHT, MEDIUM, DISPLAY;

    static final long FOCUS_MS = 150;

    private Ui() {}

    static void init(Context c) {
        if (MEDIUM != null) return;
        Resources r = c.getResources();
        THIN = r.getFont(R.font.inter_extralight);
        LIGHT = r.getFont(R.font.inter_light);
        MEDIUM = r.getFont(R.font.inter_medium);
        DISPLAY = r.getFont(R.font.montserrat_extrabold);
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, float sp, Typeface face, int color) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(face);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    /** Blend a colour toward black by {@code amount} (0..1). */
    static int darken(int color, float amount) {
        float k = 1f - amount;
        return 0xFF000000
                | (Math.round(((color >> 16) & 0xFF) * k) << 16)
                | (Math.round(((color >> 8) & 0xFF) * k) << 8)
                | Math.round((color & 0xFF) * k);
    }

    /**
     * Draws {@code src} through {@code srcToDst} into a new w x h bitmap with anti-aliased, transparent
     * rounded corners and an optional top highlight. Baking the corners in means views never clip
     * per frame, which many TV GPUs do without anti-aliasing (jagged corners).
     */
    static Bitmap roundedBitmap(Bitmap src, Matrix srcToDst, int w, int h, float radius, int highlight) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        RectF rect = new RectF(0, 0, w, h);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        BitmapShader shader = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shader.setLocalMatrix(srcToDst);
        p.setShader(shader);
        canvas.drawRoundRect(rect, radius, radius, p);
        if (highlight != 0) {
            Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG);
            hp.setShader(new LinearGradient(0, 0, 0, h * 0.45f, highlight, highlight & 0x00FFFFFF, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, radius, radius, hp);
        }
        return out;
    }
}
