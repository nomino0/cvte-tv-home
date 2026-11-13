package com.family.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Rounds the live video, which is a hardware layer that can't be clipped. The four corners outside
 * the rounded rectangle are painted with exactly what is behind them (the background bitmap and
 * the two tinted glows, sampled at this view's screen position), so the video simply looks rounded,
 * with no frame. Along that same edge it draws a faint glass rim, or the white focus ring, so the
 * ring always follows the video's own corners. Only redrawn when the glow or focus changes.
 */
final class CornerMask extends View {
    private final Path corners = new Path();
    private final RectF rect = new RectF();
    private final float radius;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowA = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint glowB = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float focus; // 0 = glass rim, 1 = white ring
    private android.animation.ValueAnimator focusAnim;
    private final BitmapShader bgShader, aShader, bShader;
    private final float maskScale;
    private final int[] loc = new int[2];
    private final Matrix m = new Matrix();

    CornerMask(Context c, float radius, Bitmap background, Bitmap maskA, Bitmap maskB, float maskScale) {
        super(c);
        this.radius = radius;
        this.maskScale = maskScale;
        bgShader = new BitmapShader(background, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        aShader = new BitmapShader(maskA, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        bShader = new BitmapShader(maskB, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        bg.setShader(bgShader);
        glowA.setShader(aShader);
        glowB.setShader(bShader);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density));
        rim.setColor(0x38FFFFFF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(3f * getResources().getDisplayMetrics().density);
        ring.setColor(0xF2FFFFFF);
        setFocusable(false);
    }

    void setFocused(boolean on) {
        if (focusAnim != null) focusAnim.cancel();
        focusAnim = android.animation.ValueAnimator.ofFloat(focus, on ? 1f : 0f);
        focusAnim.setDuration(Ui.FOCUS_MS);
        focusAnim.setInterpolator(Ui.EASE);
        focusAnim.addUpdateListener(a -> {
            focus = (float) a.getAnimatedValue();
            invalidate();
        });
        focusAnim.start();
    }

    void setTints(int a, int b) {
        glowA.setColorFilter(new PorterDuffColorFilter(a, PorterDuff.Mode.SRC_IN));
        glowB.setColorFilter(new PorterDuffColorFilter(b, PorterDuff.Mode.SRC_IN));
        invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        getLocationInWindow(loc);
        m.setTranslate(-loc[0], -loc[1]);
        bgShader.setLocalMatrix(m);
        Matrix s = new Matrix();
        s.setScale(maskScale, maskScale);
        s.postTranslate(-loc[0], -loc[1]);
        aShader.setLocalMatrix(s);
        bShader.setLocalMatrix(s);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        rect.set(0, 0, w, h);
        corners.reset();
        corners.setFillType(Path.FillType.EVEN_ODD);
        corners.addRect(rect, Path.Direction.CW);
        corners.addRoundRect(rect, radius, radius, Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawPath(corners, bg);
        canvas.drawPath(corners, glowA);
        canvas.drawPath(corners, glowB);
        // Rim and ring sit inside the edge, concentric with the rounded corners.
        float i = rim.getStrokeWidth() / 2f;
        rim.setAlpha(Math.round(0x38 * (1f - focus)));
        if (focus < 1f) canvas.drawRoundRect(i, i, getWidth() - i, getHeight() - i, radius - i, radius - i, rim);
        if (focus > 0f) {
            float j = ring.getStrokeWidth() / 2f;
            ring.setAlpha(Math.round(0xF2 * focus));
            canvas.drawRoundRect(j, j, getWidth() - j, getHeight() - j, radius - j, radius - j, ring);
        }
    }
}
