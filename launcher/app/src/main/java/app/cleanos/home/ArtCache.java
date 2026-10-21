package app.cleanos.home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.UserManager;
import android.util.DisplayMetrics;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Renders each app's art once into a crisp, complete 5:3 (tvOS tile) bitmap and keeps it on disk,
 * so later cold starts (e.g. right after the TV boots) only decode a small PNG.
 *
 * Banners are loaded at the highest density the app ships (not the TV's 213 dpi bucket),
 * scaled to fit without cropping, and padded with the banner's own edge colour.
 */
final class ArtCache {

    static final int W = 500, H = 300;

    static final class Art {
        final Bitmap bitmap;
        final int edgeColor;

        Art(Bitmap bitmap) {
            this.bitmap = bitmap;
            this.edgeColor = bitmap.getPixel(2, H / 2);
        }
    }

    /** Also match apps that can't run until the user unlocks, so lookups work during direct boot. */
    static final int ANY_BOOT = PackageManager.MATCH_DIRECT_BOOT_AWARE | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;

    private ArtCache() {}

    /** Slow path does package resource I/O: call off the main thread. Returns null if the app is missing. */
    static Art get(Context c, String pkg, int brandColor) {
        PackageManager pm = c.getPackageManager();
        ApplicationInfo ai;
        long updated;
        try {
            ai = pm.getApplicationInfo(pkg, ANY_BOOT);
            updated = pm.getPackageInfo(pkg, ANY_BOOT).lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }

        File dir = new File(c.createDeviceProtectedStorageContext().getCacheDir(), "art");
        File file = new File(dir, pkg + "_" + updated + "_v1.png");
        if (file.exists()) {
            Bitmap b = BitmapFactory.decodeFile(file.getPath());
            if (b != null && b.getWidth() == W) return new Art(b);
        }

        // Before unlock, other apps' launch info isn't visible yet; render properly after unlock.
        UserManager um = c.getSystemService(UserManager.class);
        if (um != null && !um.isUserUnlocked()) return null;

        Bitmap b = render(pm, ai, pkg, brandColor);
        if (b == null) return null;
        dir.mkdirs();
        File[] old = dir.listFiles((d, name) -> name.startsWith(pkg + "_"));
        if (old != null) for (File f : old) f.delete();
        try (FileOutputStream out = new FileOutputStream(file)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception ignored) {
        }
        return new Art(b);
    }

    private static Bitmap render(PackageManager pm, ApplicationInfo ai, String pkg, int brandColor) {
        Resources res;
        try {
            res = pm.getResourcesForApplication(ai);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }

        int bannerId = ai.banner;
        if (bannerId == 0) {
            Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (li != null && li.getComponent() != null) {
                try {
                    ActivityInfo a = pm.getActivityInfo(li.getComponent(), 0);
                    bannerId = a.banner;
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
        }

        Drawable d = load(res, bannerId);
        boolean isBanner = d != null;
        if (d == null) d = load(res, ai.icon);
        if (d == null) d = pm.getApplicationIcon(ai);

        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        int iw = Math.max(1, d.getIntrinsicWidth()), ih = Math.max(1, d.getIntrinsicHeight());

        if (isBanner) {
            // Banners are 16:9, tiles 5:3. Scale most of the way from "fit" toward "cover" (crops
            // only ~2% per side, logos stay whole), then fill the thin leftover strips by
            // extending the banner's own edge pixels (CLAMP), so gradients continue seamlessly
            // instead of meeting a flat band.
            float fit = Math.min(W / (float) iw, H / (float) ih);
            float cover = Math.max(W / (float) iw, H / (float) ih);
            float s = fit + 0.6f * (cover - fit);
            int dw = Math.max(1, Math.round(iw * s)), dh = Math.max(1, Math.round(ih * s));
            Bitmap banner = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, dw, dh);
            d.draw(new Canvas(banner));

            canvas.drawColor(Ui.darken(brandColor, 0.15f)); // shows through any transparent banner pixels
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            BitmapShader shader = new BitmapShader(banner, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setTranslate((W - dw) / 2f, (H - dh) / 2f);
            shader.setLocalMatrix(m);
            p.setShader(shader);
            canvas.drawRect(0, 0, W, H, p);
            banner.recycle();
        } else {
            // Square icon centred on the brand colour.
            canvas.drawColor(Ui.darken(brandColor, 0.15f));
            int size = H * 3 / 5;
            int left = (W - size) / 2, top = (H - size) / 2;
            d.setBounds(left, top, left + size, top + size);
            d.draw(canvas);
        }
        return out;
    }

    /** A tvOS-style system tile: vertical gradient with a centred white glyph (e.g. Settings). */
    static Art glyph(Context c, int iconRes, int top, int bottom) {
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        bg.setBounds(0, 0, W, H);
        bg.draw(canvas);
        Drawable icon = c.getDrawable(iconRes).mutate();
        icon.setTint(Color.WHITE);
        int size = H / 2;
        int left = (W - size) / 2, t = (H - size) / 2;
        icon.setBounds(left, t, left + size, t + size);
        icon.draw(canvas);
        return new Art(out);
    }

    private static Drawable load(Resources res, int id) {
        if (id == 0) return null;
        try {
            return res.getDrawableForDensity(id, DisplayMetrics.DENSITY_XXXHIGH, null);
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }
}
