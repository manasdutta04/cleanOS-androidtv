package app.cleanos.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Shader;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 5:3 app card, tvOS style: focus lifts and enlarges it, sweeps a soft light across it and
 * fades the name in underneath. Missing/disabled apps are faded.
 *
 * Built for a weak GPU and clean edges: the art is a pre-rounded, anti-aliased bitmap; the light
 * sweep is drawn as an anti-aliased rounded rect with a moving gradient. Nothing is ever clipped,
 * because this GPU clips rounded outlines without anti-aliasing (jagged corners).
 */
final class AppTile extends LinearLayout {

    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(1.8f);
    static final float FOCUS_SCALE = 1.12f;

    final Item item;
    final FrameLayout card;
    private final ImageView art;
    private final Sheen sheen;
    private final TextView label;
    private final int cardW, cardH;
    private final float radius;
    private final float liftPx;
    private final boolean alwaysLabel;
    private boolean installed = true;

    AppTile(Context c, Item item, int widthPx, int heightPx) {
        this(c, item, widthPx, heightPx, false);
    }

    /** @param alwaysLabel show the name at all times (grid), not only on focus (dock). */
    AppTile(Context c, Item item, int widthPx, int heightPx, boolean alwaysLabel) {
        super(c);
        this.item = item;
        this.alwaysLabel = alwaysLabel;
        this.cardW = widthPx;
        this.cardH = heightPx;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setFocusable(true);
        setClipChildren(false);
        setClipToPadding(false);

        radius = Ui.dp(c, 12);
        liftPx = Ui.dp(c, 16);

        card = new FrameLayout(c);
        // Placeholder until the art arrives; the outline only casts the shadow (no clipping).
        card.setBackground(Ui.rounded(Ui.darken(item.color, 0.15f), radius));
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        card.setElevation(Ui.dp(c, 3));

        art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.FIT_XY);
        card.addView(art, new FrameLayout.LayoutParams(-1, -1));

        sheen = new Sheen(c, radius);
        card.addView(sheen, new FrameLayout.LayoutParams(-1, -1));

        addView(card, new LayoutParams(widthPx, heightPx));

        label = Ui.text(c, 14, Ui.MEDIUM, Color.WHITE);
        label.setText(item.title);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setShadowLayer(Ui.dp(c, 6), 0, Ui.dp(c, 1), 0x99000000);
        label.setAlpha(alwaysLabel ? 0.75f : 0f);
        LayoutParams lp = new LayoutParams(widthPx + Ui.dp(c, 24), -2);
        lp.topMargin = Ui.dp(c, alwaysLabel ? 12 : 16);
        addView(label, lp);

        setOnFocusChangeListener((v, focused) -> animateFocus(focused));
    }

    void setArt(ArtCache.Art a) {
        Matrix m = new Matrix();
        m.setScale(cardW / (float) a.bitmap.getWidth(), cardH / (float) a.bitmap.getHeight());
        Bitmap rounded = Ui.roundedBitmap(a.bitmap, m, cardW, cardH, radius, 0x2EFFFFFF);
        art.setImageBitmap(rounded);
        card.setBackground(null);
        art.setAlpha(0f);
        art.animate().alpha(installed ? 1f : 0.45f).setDuration(180).start();
    }

    void setInstalled(boolean installed) {
        this.installed = installed;
        art.setAlpha(installed ? 1f : 0.45f);
    }

    /** Quick press-in before an app opens. */
    void press(Runnable then) {
        card.animate().scaleX(FOCUS_SCALE * 0.94f).scaleY(FOCUS_SCALE * 0.94f).setDuration(70)
                .setInterpolator(EASE)
                .withEndAction(() -> {
                    card.animate().scaleX(FOCUS_SCALE).scaleY(FOCUS_SCALE).setDuration(110).start();
                    then.run();
                }).start();
    }

    private void animateFocus(boolean focused) {
        float s = focused ? FOCUS_SCALE : 1f;
        card.animate().scaleX(s).scaleY(s)
                .translationZ(focused ? liftPx : 0f)
                .setInterpolator(EASE)
                .setDuration(Ui.FOCUS_MS).start();
        label.animate().alpha(focused ? 1f : (alwaysLabel ? 0.75f : 0f))
                .translationY(focused ? Ui.dp(getContext(), 8) : 0f)
                .setInterpolator(EASE)
                .setDuration(Ui.FOCUS_MS).start();
        if (focused) sheen.sweep();
        else sheen.stop();
    }

    /** Diagonal light band moving across the card, painted inside an anti-aliased rounded rect. */
    private static final class Sheen extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final float radius;
        private LinearGradient shader;
        private ValueAnimator anim;
        private float progress = -1f; // < 0: idle

        Sheen(Context c, float radius) {
            super(c);
            this.radius = radius;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            float band = w * 0.45f;
            shader = new LinearGradient(0, 0, band, 0,
                    new int[]{0x00FFFFFF, 0x45FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
            paint.setShader(shader);
        }

        void sweep() {
            stop();
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(720);
            anim.setStartDelay(90);
            anim.setInterpolator(EASE);
            anim.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                invalidate();
            });
            anim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator a) {
                    progress = -1f;
                    invalidate();
                }
            });
            anim.start();
        }

        void stop() {
            if (anim != null) anim.cancel();
            anim = null;
            progress = -1f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (progress < 0f || shader == null) return;
            int w = getWidth(), h = getHeight();
            float band = w * 0.45f;
            float x = -band * 1.4f + (w + band * 2.4f) * progress;
            matrix.setRotate(18f);
            matrix.postTranslate(x, 0);
            shader.setLocalMatrix(matrix);
            canvas.drawRoundRect(0, 0, w, h, radius, radius, paint);
        }
    }
}
