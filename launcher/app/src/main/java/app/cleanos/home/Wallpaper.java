package app.cleanos.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.UserManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Wallpaper pipeline. The source is the user's own picture, pushed with the CLI to
 * {@code /sdcard/Android/data/app.cleanos.home/files/wallpaper.(jpg|png|webp)}, or the built-in
 * {@link DefaultWallpaper}. From it we make, once per screen size:
 * <ul>
 *   <li>home: cropped to the exact screen with soft shading baked in (drawn 1:1, no per-frame work)</li>
 *   <li>blur: a small, heavily blurred copy for the frosted-glass dock</li>
 *   <li>saver: the unshaded picture for the screensaver</li>
 * </ul>
 * Results are cached in device-protected storage, so they load at boot before the user unlocks.
 */
final class Wallpaper {

    static final class Set {
        final Bitmap home, blur, saver;

        Set(Bitmap home, Bitmap blur, Bitmap saver) {
            this.home = home;
            this.blur = blur;
            this.saver = saver;
        }
    }

    private static final String[] NAMES = {"wallpaper.jpg", "wallpaper.jpeg", "wallpaper.png", "wallpaper.webp"};
    private static final String VERSION = "v1";

    private Wallpaper() {}

    /** The user's picture, if one was pushed. Only readable after unlock. */
    static File userFile(Context c) {
        File dir = c.getExternalFilesDir(null);
        if (dir == null) return null;
        for (String n : NAMES) {
            File f = new File(dir, n);
            if (f.isFile() && f.length() > 0) return f;
        }
        return null;
    }

    private static File cacheDir(Context c) {
        File d = new File(c.createDeviceProtectedStorageContext().getFilesDir(), "wallpaper");
        d.mkdirs();
        return d;
    }

    /** Cached set for this screen size, or null. Fast: three small decodes. Main thread OK. */
    static Set cached(Context c, int w, int h) {
        File d = cacheDir(c);
        File key = new File(d, "key"), home = new File(d, "home.jpg"), blur = new File(d, "blur.png"), saver = new File(d, "saver.jpg");
        if (!key.isFile() || !home.isFile() || !blur.isFile() || !saver.isFile()) return null;
        String k = read(key);
        if (k == null || !k.endsWith("|" + w + "x" + h)) return null;
        // Source changed since? (Only checkable once unlocked; before that, the cache is all we have.)
        if (isUnlocked(c) && !k.equals(sourceKey(c) + "|" + w + "x" + h)) return null;
        Bitmap hb = BitmapFactory.decodeFile(home.getPath());
        Bitmap bb = BitmapFactory.decodeFile(blur.getPath());
        Bitmap sb = BitmapFactory.decodeFile(saver.getPath());
        if (hb == null || bb == null || sb == null) return null;
        return new Set(hb, bb, sb);
    }

    /** Builds the set from the current source and caches it. Slow: call off the main thread. */
    static Set build(Context c, int w, int h) {
        Bitmap src = null;
        File f = isUnlocked(c) ? userFile(c) : null;
        if (f != null) src = decodeForScreen(f, w, h);
        Bitmap screen = src != null ? cropTo(src, w, h) : DefaultWallpaper.render(w, h);
        if (src != null && src != screen) src.recycle();

        Bitmap home = shade(screen);
        Bitmap blur = blur(home);
        Set set = new Set(home, blur, screen);

        File d = cacheDir(c);
        write(new File(d, "home.jpg"), home, Bitmap.CompressFormat.JPEG, 92);
        write(new File(d, "blur.png"), blur, Bitmap.CompressFormat.PNG, 100);
        write(new File(d, "saver.jpg"), screen, Bitmap.CompressFormat.JPEG, 92);
        writeText(new File(d, "key"), sourceKey(c) + "|" + w + "x" + h);
        return set;
    }

    private static String sourceKey(Context c) {
        File f = userFile(c);
        return VERSION + ":" + (f == null ? "default" : f.getName() + ":" + f.length() + ":" + f.lastModified());
    }

    private static boolean isUnlocked(Context c) {
        UserManager um = c.getSystemService(UserManager.class);
        return um == null || um.isUserUnlocked();
    }

    /** Decodes no larger than needed (keeps memory low on 1-2 GB TVs). */
    private static Bitmap decodeForScreen(File f, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    /** Centre-crop and scale to exactly w x h. */
    static Bitmap cropTo(Bitmap src, int w, int h) {
        float s = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        Matrix m = new Matrix();
        m.setScale(s, s);
        m.postTranslate((w - src.getWidth() * s) / 2f, (h - src.getHeight() * s) / 2f);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    /** Soft shading so the greeting (top-left), status (top-right) and dock (bottom) always read. */
    private static Bitmap shade(Bitmap screen) {
        int w = screen.getWidth(), h = screen.getHeight();
        Bitmap out = screen.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h,
                new int[]{0x8C000000, 0x26000000, 0x00000000, 0x00000000},
                new float[]{0f, 0.33f, 0.66f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new LinearGradient(0, 0, 0, h / 4f, 0x6E000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h / 4f, p);
        p.setShader(new LinearGradient(0, h * 0.55f, 0, h, 0x00000000, 0x5A000000, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.55f, w, h, p);
        return out;
    }

    /** Cheap, smooth blur: shrink hard with filtering, then grow back partially. */
    static Bitmap blur(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        Bitmap tiny = Bitmap.createScaledBitmap(src, Math.max(8, w / 40), Math.max(4, h / 40), true);
        Bitmap small = Bitmap.createScaledBitmap(tiny, Math.max(16, w / 12), Math.max(9, h / 12), true);
        Bitmap smaller = Bitmap.createScaledBitmap(small, Math.max(8, w / 30), Math.max(4, h / 30), true);
        Bitmap out = Bitmap.createScaledBitmap(smaller, Math.max(32, w / 5), Math.max(18, h / 5), true);
        tiny.recycle();
        small.recycle();
        smaller.recycle();
        return out;
    }

    private static void write(File f, Bitmap b, Bitmap.CompressFormat fmt, int q) {
        try (FileOutputStream out = new FileOutputStream(f)) {
            b.compress(fmt, q, out);
        } catch (IOException ignored) {
        }
    }

    private static void writeText(File f, String s) {
        try {
            Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private static String read(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return null;
        }
    }
}
