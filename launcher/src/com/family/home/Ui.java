package com.family.home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Shared look. The TV has a weak GPU and 512MB of RAM, so everything expensive is drawn once:
 * the background is one bitmap, the ambient glow is a small white mask that is only re-tinted,
 * and focus animations run on a hardware layer.
 */
final class Ui {
    static final int TEXT = 0xFFFFFFFF;
    static final int TEXT_2 = 0xB8FFFFFF;
    static final int TEXT_3 = 0x8CFFFFFF;
    static final int TILE = 0xFF1A1D24;
    static final int FOCUS_MS = 160;
    static final Interpolator EASE = new DecelerateInterpolator(1.8f);

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static Typeface light() {
        return Typeface.create("sans-serif-light", Typeface.NORMAL);
    }

    static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    /** Dark backdrop painted once into a screen-sized bitmap, so it costs nothing per frame. */
    static Bitmap backgroundBitmap(Context c) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFF0E1118, 0xFF08090D, 0xFF040507});
        g.setDither(true);
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        Bitmap bm = Bitmap.createBitmap(m.widthPixels, m.heightPixels, Bitmap.Config.ARGB_8888);
        g.setBounds(0, 0, m.widthPixels, m.heightPixels);
        g.draw(new Canvas(bm));
        try {
            Bitmap hw = bm.copy(Bitmap.Config.HARDWARE, false);
            if (hw != null) {
                bm.recycle();
                return hw;
            }
        } catch (Throwable ignored) {
        }
        return bm;
    }

    static Drawable background(Context c) {
        return new BitmapDrawable(c.getResources(), backgroundBitmap(c));
    }

    /**
     * A soft white glow, painted once into a 320x180 bitmap and stretched over the screen.
     * Its colour comes from setTint, so changing colour never repaints it.
     */
    static BitmapDrawable glowMask(Context c, float cx, float cy, float radius) {
        int w = 320, h = 180;
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setDither(true);
        p.setShader(new RadialGradient(w * cx, h * cy, w * radius,
                new int[] {0x99FFFFFF, 0x40FFFFFF, 0x00FFFFFF}, new float[] {0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
        new Canvas(bm).drawRect(0, 0, w, h, p);
        BitmapDrawable d = new BitmapDrawable(c.getResources(), bm);
        d.setGravity(Gravity.FILL);
        d.setFilterBitmap(true);
        return d;
    }

    /** Glass: translucent fill with a light rim; brighter with a white rim when focused. */
    static Drawable glass(Context c, float radius) {
        GradientDrawable rest = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x26FFFFFF, 0x0FFFFFFF});
        rest.setCornerRadius(radius);
        rest.setStroke(Math.max(1, dp(c, 1.5f)), 0x30FFFFFF);
        GradientDrawable focus = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x55FFFFFF, 0x2AFFFFFF});
        focus.setCornerRadius(radius);
        focus.setStroke(dp(c, 2), 0xF0FFFFFF);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_focused}, focus);
        s.addState(new int[] {}, rest);
        s.setEnterFadeDuration(FOCUS_MS);
        s.setExitFadeDuration(FOCUS_MS);
        return s;
    }

    /** White ring drawn over a tile only while it has focus. */
    static Drawable focusRing(Context c, float radius) {
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(radius);
        ring.setStroke(dp(c, 3), 0xF2FFFFFF);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_focused}, ring);
        s.addState(new int[] {}, new ColorDrawable(Color.TRANSPARENT));
        s.setEnterFadeDuration(FOCUS_MS);
        s.setExitFadeDuration(FOCUS_MS);
        return s;
    }

    /**
     * Small lift on focus: grows a little, on a hardware layer only while animating. No elevation:
     * a live shadow under every moving tile is the most expensive thing this GPU could draw.
     */
    static void lift(View v, float scale, View.OnFocusChangeListener extra) {
        v.setFocusable(true);
        v.setOnFocusChangeListener((view, has) -> {
            view.animate().cancel();
            view.animate().scaleX(has ? scale : 1f).scaleY(has ? scale : 1f)
                    .setDuration(FOCUS_MS).setInterpolator(EASE).withLayer().start();
            if (extra != null) extra.onFocusChange(view, has);
        });
    }

    static TextView text(Context c, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    static String label(PackageManager pm, String pkg) {
        if (HomeActivity.isBrowser(pkg)) return "Internet";
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        return i;
    }

    static boolean start(Context c, Intent i) {
        if (i == null) return false;
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** App tile: its TV banner if it has one, otherwise its icon and name on a dark card. */
    static FrameLayout appTile(Context c, String pkg, int w, int h) {
        PackageManager pm = c.getPackageManager();
        FrameLayout tile = new FrameLayout(c);
        float r = dp(c, 14);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(TILE);
        bg.setCornerRadius(r);
        tile.setBackground(bg);
        tile.setClipToOutline(true);
        tile.setForeground(focusRing(c, r));

        Drawable banner = HomeActivity.isBrowser(pkg) ? null : banner(pm, pkg);
        if (HomeActivity.isBrowser(pkg)) {
            // The browser: a plain blue tile with a white globe instead of its own artwork.
            GradientDrawable blue = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[] {0xFF2F80ED, 0xFF1A56C4});
            blue.setCornerRadius(r);
            tile.setBackground(blue);
            ImageView globe = new ImageView(c);
            globe.setImageResource(R.drawable.ic_globe);
            int gs = Math.min(dp(c, 44), h / 2);
            FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(gs, gs);
            gl.gravity = Gravity.CENTER;
            tile.addView(globe, gl);
        } else if (banner != null) {
            ImageView img = new ImageView(c);
            img.setImageDrawable(banner);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            ImageView icon = new ImageView(c);
            try {
                icon.setImageDrawable(pm.getApplicationIcon(pkg));
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            int is = Math.min(dp(c, 48), h / 2);
            col.addView(icon, new LinearLayout.LayoutParams(is, is));
            TextView t = text(c, 15, TEXT, medium());
            t.setText(label(pm, pkg));
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(c, 6), dp(c, 6), dp(c, 6), 0);
            col.addView(t, new LinearLayout.LayoutParams(-1, -2));
            tile.addView(col, new FrameLayout.LayoutParams(-1, -1));
        }
        tile.setLayoutParams(new FrameLayout.LayoutParams(w, h));
        return tile;
    }

    /** The app's TV banner: declared on the application or, for some apps, on its TV activity. */
    static Drawable banner(PackageManager pm, String pkg) {
        try {
            Drawable d = pm.getApplicationBanner(pkg);
            if (d != null) return d;
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            return i == null ? null : pm.getActivityBanner(i);
        } catch (Exception e) {
            return null;
        }
    }

    /** The colour covering most of an app's banner or icon (white, grey and black ignored). */
    static int dominantColor(Context c, String pkg) {
        if (HomeActivity.isBrowser(pkg)) return 0xFF2F80ED;
        PackageManager pm = c.getPackageManager();
        Drawable d = banner(pm, pkg);
        try {
            if (d == null) d = pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        if (d == null) return 0xFF5A5F6E;
        int n = 24;
        Bitmap bm = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        d = d.mutate();
        d.setBounds(0, 0, n, n);
        d.draw(new Canvas(bm));
        float[] weight = new float[12];
        float[][] sum = new float[12][3];
        float[] hsv = new float[3];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bm.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                Color.colorToHSV(p, hsv);
                if (hsv[1] < 0.3f || hsv[2] < 0.25f) continue;
                int bin = (int) (hsv[0] / 30f) % 12;
                float w = hsv[1] * hsv[2];
                weight[bin] += w;
                sum[bin][0] += Color.red(p) * w;
                sum[bin][1] += Color.green(p) * w;
                sum[bin][2] += Color.blue(p) * w;
            }
        }
        bm.recycle();
        int best = 0;
        for (int i = 1; i < 12; i++) if (weight[i] > weight[best]) best = i;
        if (weight[best] < n * n * 0.03f) return 0xFF5A5F6E;
        float w = weight[best];
        return Color.rgb((int) (sum[best][0] / w), (int) (sum[best][1] / w), (int) (sum[best][2] / w));
    }

    /** Makes a sampled colour usable as a glow: some saturation, never too dark or too bright. */
    static int glowColor(int rgb) {
        float[] hsv = new float[3];
        Color.colorToHSV(rgb, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.35f + 0.05f);
        hsv[2] = Math.max(0.35f, Math.min(0.85f, hsv[2] * 1.15f));
        return Color.HSVToColor(hsv);
    }

    static int blend(int a, int b, float t) {
        int r = Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.rgb(r, g, bl);
    }
}
