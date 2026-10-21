package app.cleanos.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/**
 * The built-in wallpaper, drawn in code so the app ships no photo: a night sky with soft aurora
 * light over two mountain ridges. Rendered once per screen size and cached by {@link Wallpaper}.
 */
final class DefaultWallpaper {

    private DefaultWallpaper() {}

    static Bitmap render(int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

        // Sky.
        p.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF070B1C, 0xFF14214A, 0xFF233A6B, 0xFF0C1020},
                new float[]{0f, 0.45f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);

        // Aurora glows.
        glow(c, p, w * 0.28f, h * 0.30f, w * 0.55f, 0x6633E0C8);
        glow(c, p, w * 0.62f, h * 0.22f, w * 0.50f, 0x557A5CFF);
        glow(c, p, w * 0.86f, h * 0.42f, w * 0.30f, 0x40FF6FA8);
        glow(c, p, w * 0.50f, h * 0.62f, w * 0.60f, 0x332A8CFF);

        // Two ridges for depth: far (blue-grey), near (almost black).
        ridge(c, p, w, h, 0.66f, 0.10f, 1.3f, 0xFF1A2342, 0xFF121830, 11);
        ridge(c, p, w, h, 0.78f, 0.12f, 2.1f, 0xFF0A0D18, 0xFF05060B, 23);
        p.setShader(null);
        return out;
    }

    private static void glow(Canvas c, Paint p, float x, float y, float r, int color) {
        p.setShader(new RadialGradient(x, y, r, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, c.getWidth(), c.getHeight(), p);
    }

    /** A ridge line built from a few sines (deterministic, no assets). */
    private static void ridge(Canvas c, Paint p, int w, int h, float base, float amp, float freq,
                              int top, int bottom, int seed) {
        Path path = new Path();
        path.moveTo(0, h);
        int steps = 64;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            double y = Math.sin(t * Math.PI * freq + seed) * 0.55
                    + Math.sin(t * Math.PI * freq * 2.7 + seed * 0.3) * 0.30
                    + Math.sin(t * Math.PI * freq * 6.1 + seed * 1.7) * 0.15;
            path.lineTo(t * w, (float) (h * (base - amp * y)));
        }
        path.lineTo(w, h);
        path.close();
        p.setShader(new LinearGradient(0, h * (base - amp), 0, h, top, bottom, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
    }
}
