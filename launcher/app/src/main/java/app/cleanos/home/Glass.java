package app.cleanos.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Frosted glass, rendered once into a bitmap: the blurred wallpaper that lies under the slab, a
 * light tint, a hairline edge and rounded corners. On screen it is then one plain bitmap draw,
 * instead of a live blur plus clipping every frame (too much for most TV GPUs).
 */
final class Glass {

    private Glass() {}

    /**
     * @param blur   blurred wallpaper covering the whole screen (any size, stretched to screen)
     * @param x,y    slab position on screen, px
     * @param w,h    slab size, px
     */
    static Bitmap render(Bitmap blur, int screenW, int screenH, int x, int y, int w, int h, float radius, float hairline) {
        Matrix m = new Matrix();
        m.setScale(screenW / (float) blur.getWidth(), screenH / (float) blur.getHeight());
        m.postTranslate(-x, -y);
        Bitmap out = Ui.roundedBitmap(blur, m, w, h, radius, 0);

        Canvas canvas = new Canvas(out);
        RectF rect = new RectF(0, 0, w, h);
        Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tint.setShader(new LinearGradient(0, 0, 0, h, 0x52FFFFFF, 0x29FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, radius, radius, tint);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(hairline);
        stroke.setShader(new LinearGradient(0, 0, 0, h, 0x66FFFFFF, 0x1AFFFFFF, Shader.TileMode.CLAMP));
        rect.inset(hairline / 2, hairline / 2);
        canvas.drawRoundRect(rect, radius - hairline / 2, radius - hairline / 2, stroke);
        return out;
    }
}
