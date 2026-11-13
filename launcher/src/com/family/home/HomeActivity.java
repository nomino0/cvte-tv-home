package com.family.home;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Home screen for CVTE / MediaTek Android 9 TVs: greeting and date, round shortcut buttons with Wi-Fi and the
 * clock; a live HDMI 1 preview whose colours light the background next to a glass card naming
 * what is selected; and the pinned apps (hold OK to move, replace or remove one).
 */
public class HomeActivity extends Activity {
    static final String TVBRO = "com.phlox.tvwebbrowser";
    static final String CHROME = AdBlock.CHROME;
    static final String KINDLINK = "com.mediatek.androidbox"; // the TV's own screen-cast receiver
    static final int MAX_PINS = 6;
    private static final int REQ_ADD = 1, REQ_REPLACE = 2;
    private static final String ADD = "+";
    private static final int DEFAULT_A = 0xFF4B3A8C, DEFAULT_B = 0xFF1F4E79;
    private static final int ACCENT = 0xFFFFC857;

    // Layout, in px of the 1280x720 UI.
    private static final int M = 64;
    private static final int MID_Y = 132, MID_H = 360, TV_W = 640, GAP = 32;
    private static final int LABEL_Y = 528, ROW_Y = 566, TILE_W = 176, TILE_H = 99, TILE_GAP = 19;
    private static final float RADIUS = 20;

    private FrameLayout root;
    private Bitmap bgBitmap;
    private BitmapDrawable maskA, maskB;
    private int colorA = DEFAULT_A, colorB = DEFAULT_B, liveA, liveB;
    private boolean haveLive;
    private ValueAnimator glowAnim;

    private TvView preview;
    private TextView previewStatus;
    private FrameLayout tvTile;
    private CornerMask corners;
    private String hdmiId;
    private LiveAmbient live;

    private TextView greeting, kicker, title, subtitle, hint;
    private ImageView wifiIcon;
    private View cover;
    private LinearLayout row, icons;
    private final List<String> pins = new ArrayList<>();
    private final Map<String, Integer> appColor = new HashMap<>();
    private final Map<String, String[]> styles = new HashMap<>(); // res/values/apps.xml
    private String focusKey = "tv";
    private String moving, replacing;
    private View menu;
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        bgBitmap = Ui.backgroundBitmap(this);
        getWindow().setBackgroundDrawable(new BitmapDrawable(getResources(), bgBitmap));
        hdmiId = findHdmi1(this);
        for (String s : getResources().getStringArray(R.array.app_styles)) {
            String[] p = s.split("\\|");
            if (p.length >= 4) styles.put(p[0].trim(), p);
        }
        loadPins();
        live = new LiveAmbient((l, r) -> {
            liveA = Ui.glowColor(l);
            liveB = Ui.glowColor(r);
            haveLive = true;
            if (followsTv()) glowTo(liveA, liveB, 1500);
        });
        Tweaks.start(this);
        if (Ui.launchIntent(getPackageManager(), CHROME) != null) AdBlock.init(this);
        setContentView(buildUi());
        applyTints();
    }

    /** HDMI 1 on this TV is HW4; fall back to the first HDMI input if the firmware ever renames it. */
    static String findHdmi1(Context c) {
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        String first = null;
        if (tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.getType() != TvInputInfo.TYPE_HDMI) continue;
                if (info.getId().endsWith("/HW4")) return info.getId();
                if (first == null) first = info.getId();
            }
        }
        return first;
    }

    // ---------------------------------------------------------------- layout

    private static FrameLayout.LayoutParams at(int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = x;
        lp.topMargin = y;
        return lp;
    }

    private View buildUi() {
        root = new FrameLayout(this);
        root.setClipChildren(false);

        // Ambient light: two stretched white masks, only ever re-tinted.
        maskA = Ui.glowMask(this, 0.22f, 0.18f, 0.75f);
        maskB = Ui.glowMask(this, 0.92f, 1.0f, 0.70f);
        for (Drawable d : new Drawable[] {maskA, maskB}) {
            View v = new View(this);
            v.setBackground(d);
            root.addView(v, new FrameLayout.LayoutParams(-1, -1));
        }

        // Top left: greeting and date.
        greeting = Ui.text(this, 30, Ui.TEXT, Ui.medium());
        root.addView(greeting, at(M, 40, -2, -2));
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        date.setTextSize(19);
        date.setTextColor(Ui.TEXT_2);
        root.addView(date, at(M, 80, -2, -2));

        // Top right: round shortcuts, divider, Wi-Fi (band and speed), clock.
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setClipChildren(false);
        icons = new LinearLayout(this);
        icons.setClipChildren(false);
        icons.addView(iconButton(R.drawable.ic_apps, "apps",
                () -> Ui.start(this, new Intent(this, AppsActivity.class))));
        icons.addView(iconButton(R.drawable.ic_input, "src", this::openSources));
        if (Ui.launchIntent(getPackageManager(), KINDLINK) != null) {
            icons.addView(iconButton(R.drawable.ic_cast, "cast",
                    () -> Ui.start(this, Ui.launchIntent(getPackageManager(), KINDLINK))));
        }
        icons.addView(iconButton(R.drawable.ic_settings, "set", () -> Ui.start(this,
                new Intent().setClassName("com.cvte.settings", "com.cvte.settings.Settings"))));
        top.addView(icons);
        View div = new View(this);
        div.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(this, 1), Ui.dp(this, 36));
        dlp.leftMargin = Ui.dp(this, 10);
        dlp.rightMargin = Ui.dp(this, 22);
        top.addView(div, dlp);
        wifiIcon = new ImageView(this);
        wifiIcon.setImageResource(R.drawable.ic_wifi_4);
        wifiIcon.setImageAlpha(0xD0);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26));
        wlp.rightMargin = Ui.dp(this, 22);
        top.addView(wifiIcon, wlp);
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextSize(44);
        clock.setTextColor(Ui.TEXT);
        clock.setTypeface(Ui.medium());
        clock.setIncludeFontPadding(false);
        top.addView(clock);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-2, Ui.dp(this, 56));
        tlp.gravity = Gravity.TOP | Gravity.END;
        tlp.topMargin = Ui.dp(this, 40);
        tlp.rightMargin = Ui.dp(this, M);
        root.addView(top, tlp);

        root.addView(buildTvTile(), at(M, MID_Y, TV_W, MID_H));

        // Glass card: what is selected.
        FrameLayout card = new FrameLayout(this);
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x1CFFFFFF, 0x0AFFFFFF});
        glass.setCornerRadius(Ui.dp(this, RADIUS));
        glass.setStroke(Math.max(1, Ui.dp(this, 1)), 0x24FFFFFF);
        card.setBackground(glass);
        int cp = Ui.dp(this, 36);
        card.setPadding(cp, cp, cp, Ui.dp(this, 32));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        kicker = Ui.text(this, 16, ACCENT, Ui.medium());
        title = Ui.text(this, 50, Ui.TEXT, Ui.medium());
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, Ui.dp(this, 14), 0, 0);
        subtitle = Ui.text(this, 21, Ui.TEXT_2, null);
        subtitle.setPadding(0, Ui.dp(this, 10), 0, 0);
        // Fixed sizes: changing the text then only repaints the card instead of re-measuring the
        // whole screen on every remote press.
        text.addView(kicker, new LinearLayout.LayoutParams(-1, Ui.dp(this, 24)));
        text.addView(title, new LinearLayout.LayoutParams(-1, Ui.dp(this, 82)));
        text.addView(subtitle, new LinearLayout.LayoutParams(-1, Ui.dp(this, 40)));
        card.addView(text, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout ok = new LinearLayout(this);
        ok.setGravity(Gravity.CENTER_VERTICAL);
        TextView chip = Ui.text(this, 14, Ui.TEXT_2, Ui.medium());
        chip.setText("OK");
        GradientDrawable cb = new GradientDrawable();
        cb.setCornerRadius(Ui.dp(this, 7));
        cb.setStroke(Ui.dp(this, 2), Ui.TEXT_2);
        chip.setBackground(cb);
        chip.setPadding(Ui.dp(this, 9), Ui.dp(this, 3), Ui.dp(this, 9), Ui.dp(this, 3));
        ok.addView(chip);
        hint = Ui.text(this, 17, Ui.TEXT_2, null);
        hint.setPadding(Ui.dp(this, 12), 0, 0, 0);
        hint.setGravity(Gravity.CENTER_VERTICAL);
        ok.addView(hint, new LinearLayout.LayoutParams(Ui.dp(this, 320), Ui.dp(this, 28)));
        FrameLayout.LayoutParams olp = new FrameLayout.LayoutParams(-2, -2);
        olp.gravity = Gravity.BOTTOM | Gravity.START;
        card.addView(ok, olp);
        int cardX = M + TV_W + GAP;
        root.addView(card, at(cardX, MID_Y, 1280 - M - cardX, MID_H));

        // Pinned apps.
        TextView label = Ui.text(this, 17, Ui.TEXT_2, Ui.medium());
        label.setText("Applications");
        root.addView(label, at(M, LABEL_Y, -2, -2));
        row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        root.addView(row, at(M, ROW_Y, -2, TILE_H));

        buildRow();
        return root;
    }

    private View iconButton(int icon, String key, Runnable action) {
        FrameLayout b = new FrameLayout(this);
        b.setBackground(Ui.glass(this, Ui.dp(this, 28)));
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setImageAlpha(0xE0);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24));
        ip.gravity = Gravity.CENTER;
        b.addView(iv, ip);
        Ui.lift(b, 1.06f, (v, has) -> {
            if (has) focus(key);
        });
        b.setOnClickListener(v -> action.run());
        b.setId(View.generateViewId());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56));
        lp.rightMargin = Ui.dp(this, 12);
        b.setLayoutParams(lp);
        return b;
    }

    private View buildTvTile() {
        tvTile = new FrameLayout(this);
        tvTile.setId(View.generateViewId());
        tvTile.setFocusable(true);
        tvTile.setOnFocusChangeListener((v, has) -> {
            corners.setFocused(has);
            if (has) focus("tv");
        });
        tvTile.setOnClickListener(v -> watchTv());

        preview = new TvView(this);
        preview.setFocusable(false);
        preview.setCallback(new TvView.TvInputCallback() {
            @Override
            public void onVideoAvailable(String inputId) {
                previewStatus.setVisibility(View.GONE);
                cover.animate().alpha(0f).setDuration(250).start();
            }

            @Override
            public void onVideoUnavailable(String inputId, int reason) {
                previewStatus.setText(reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING ? "" : "Pas de signal");
                previewStatus.setVisibility(View.VISIBLE);
                cover.animate().cancel();
                cover.setAlpha(1f);
            }
        });
        tvTile.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        // The TV paints its video layer blue while a picture starts or stops: keep it covered until
        // there is real video.
        cover = new View(this);
        cover.setBackgroundColor(0xFF101318);
        tvTile.addView(cover, new FrameLayout.LayoutParams(-1, -1));

        previewStatus = Ui.text(this, 20, Ui.TEXT_2, null);
        previewStatus.setGravity(Gravity.CENTER);
        tvTile.addView(previewStatus, new FrameLayout.LayoutParams(-1, -1));

        TextView live = Ui.text(this, 14, Ui.TEXT, Ui.medium());
        live.setText("●  En direct");
        GradientDrawable lb = new GradientDrawable();
        lb.setColor(0xE6E5484D);
        lb.setCornerRadius(Ui.dp(this, 7));
        live.setBackground(lb);
        live.setPadding(Ui.dp(this, 9), Ui.dp(this, 5), Ui.dp(this, 11), Ui.dp(this, 5));
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(-2, -2);
        llp.leftMargin = llp.topMargin = Ui.dp(this, 16);
        tvTile.addView(live, llp);

        TextView name = Ui.text(this, 20, Ui.TEXT, Ui.medium());
        name.setText("HDMI 1");
        name.setShadowLayer(Ui.dp(this, 6), 0, 0, 0xB0000000);
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(-2, -2);
        nlp.gravity = Gravity.BOTTOM | Gravity.START;
        nlp.leftMargin = Ui.dp(this, 20);
        nlp.bottomMargin = Ui.dp(this, 16);
        tvTile.addView(name, nlp);

        corners = new CornerMask(this, Ui.dp(this, RADIUS), bgBitmap,
                maskA.getBitmap(), maskB.getBitmap(), 1280f / maskA.getBitmap().getWidth());
        tvTile.addView(corners, new FrameLayout.LayoutParams(-1, -1));
        return tvTile;
    }

    /**
     * Full screen HDMI in the TV's own player. Inside our app, the firmware reads the launcher key
     * as "go to TV" (ro.CVT_EN_HOME_KEY_ENTER_TV) and needs a second press; from its player, one
     * press comes home. Our preview keeps the TV's current source on HDMI 1.
     */
    private void watchTv() {
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.TV_HOME")
                .setClassName("com.cvte.tv.setting", "com.cvte.tv.setting.TvSettingActivity");
        if (!Ui.start(this, tv)) startActivity(new Intent(this, HdmiActivity.class));
    }

    // ---------------------------------------------------------------- pinned apps

    /** Either browser gets the blue "Internet" tile; Chrome wins when both are installed. */
    static boolean isBrowser(String pkg) {
        return TVBRO.equals(pkg) || CHROME.equals(pkg);
    }

    static String browser(PackageManager pm) {
        if (Ui.launchIntent(pm, CHROME) != null) return CHROME;
        return Ui.launchIntent(pm, TVBRO) != null ? TVBRO : null;
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("home", MODE_PRIVATE);
    }

    private void loadPins() {
        pins.clear();
        String saved = prefs().getString("pins", null);
        String[] list = saved != null ? (saved.isEmpty() ? new String[0] : saved.split(","))
                : getResources().getStringArray(R.array.default_pins);
        PackageManager pm = getPackageManager();
        String web = browser(pm);
        boolean swapped = false;
        for (String p : list) {
            // One browser replaced by the other: keep its place in the row.
            if (isBrowser(p) && web != null && !p.equals(web) && Ui.launchIntent(pm, p) == null) {
                p = web;
                swapped = true;
            }
            if (pins.size() < MAX_PINS && !pins.contains(p) && Ui.launchIntent(pm, p) != null) pins.add(p);
        }
        // The browser was installed after the row was first saved: pin it once.
        if (saved != null && !prefs().getBoolean("browser_offered", false)
                && web != null && !pins.contains(web) && pins.size() < MAX_PINS) {
            pins.add(web);
            prefs().edit().putBoolean("browser_offered", true).apply();
            swapped = true;
        }
        if (swapped) savePins();
    }

    private void savePins() {
        prefs().edit().putString("pins", TextUtils.join(",", pins)).apply();
    }

    private void buildRow() {
        row.removeAllViews();
        for (String p : pins) row.addView(appTile(p));
        if (pins.size() < MAX_PINS) row.addView(addTile());
        int n = row.getChildCount();
        for (int i = 0; i < n; i++) {
            View v = row.getChildAt(i);
            v.setNextFocusUpId(tvTile.getId());
            v.setNextFocusDownId(v.getId());
            if (i == 0) v.setNextFocusLeftId(v.getId());
            if (i == n - 1) v.setNextFocusRightId(v.getId());
        }
        View first = row.getChildAt(0);
        tvTile.setNextFocusDownId(first.getId());
        tvTile.setNextFocusLeftId(tvTile.getId());
        tvTile.setNextFocusUpId(icons.getChildAt(0).getId());
        tvTile.setNextFocusRightId(icons.getChildAt(0).getId());
        for (int i = 0; i < icons.getChildCount(); i++) {
            View ic = icons.getChildAt(i);
            ic.setNextFocusDownId(tvTile.getId());
            ic.setNextFocusUpId(ic.getId());
            if (i == icons.getChildCount() - 1) ic.setNextFocusRightId(ic.getId());
        }
    }

    private View appTile(String pkg) {
        FrameLayout t = Ui.appTile(this, pkg, TILE_W, TILE_H);
        t.setId(View.generateViewId());
        t.setTag(pkg);
        Ui.lift(t, 1.04f, (v, has) -> {
            if (has) focus(pkg);
        });
        t.setOnClickListener(v -> {
            if (moving != null) endMove();
            else Ui.start(this, Ui.launchIntent(getPackageManager(), pkg));
        });
        t.setOnLongClickListener(v -> {
            if (moving == null) showMenu(pkg, v);
            return true;
        });
        t.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
            // Held OK: the remote repeats the key. (Menu is taken by the firmware's TV menu.)
            boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
            if (ok && ev.getRepeatCount() > 0) {
                if (moving == null) showMenu(pkg, v);
                return true;
            }
            if (pkg.equals(moving)) {
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) { shift(pkg, -1); return true; }
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { shift(pkg, 1); return true; }
                if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            }
            return false;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(TILE_W, TILE_H);
        lp.rightMargin = TILE_GAP;
        t.setLayoutParams(lp);
        return t;
    }

    private View addTile() {
        FrameLayout t = new FrameLayout(this);
        t.setId(View.generateViewId());
        t.setTag(ADD);
        t.setBackground(Ui.glass(this, Ui.dp(this, 14)));
        ImageView plus = new ImageView(this);
        plus.setImageResource(R.drawable.ic_plus);
        plus.setImageAlpha(0xD0);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34));
        ip.gravity = Gravity.CENTER;
        t.addView(plus, ip);
        Ui.lift(t, 1.04f, (v, has) -> {
            if (has) focus(ADD);
        });
        t.setOnClickListener(v -> pickApp(REQ_ADD));
        t.setLayoutParams(new LinearLayout.LayoutParams(TILE_W, TILE_H));
        return t;
    }

    private void pickApp(int req) {
        Intent i = new Intent(this, AppsActivity.class);
        i.putExtra("pick", true);
        i.putExtra("exclude", pins.toArray(new String[0]));
        startActivityForResult(i, req);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        String pkg = res == RESULT_OK && data != null ? data.getStringExtra("pkg") : null;
        if (pkg != null && !pins.contains(pkg)) {
            if (req == REQ_ADD && pins.size() < MAX_PINS) pins.add(pkg);
            else if (req == REQ_REPLACE && replacing != null) {
                int i = pins.indexOf(replacing);
                if (i >= 0) pins.set(i, pkg);
            }
            savePins();
            buildRow();
            focusRow(pkg);
        }
        replacing = null;
    }

    private void focusRow(String key) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (key.equals(v.getTag())) {
                v.requestFocus();
                return;
            }
        }
    }

    // Hold OK on a pinned app: a small glass menu above it.
    private void showMenu(String pkg, View anchor) {
        if (menu != null) return;
        LinearLayout m = new LinearLayout(this);
        m.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF21C2028);
        bg.setCornerRadius(Ui.dp(this, 18));
        bg.setStroke(Math.max(1, Ui.dp(this, 1)), 0x30FFFFFF);
        m.setBackground(bg);
        int p = Ui.dp(this, 8);
        m.setPadding(p, p, p, p);
        m.setElevation(Ui.dp(this, 24));
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (CHROME.equals(pkg)) {
            // Some sites refuse to open with an ad blocker: switch it here, then reopen Chrome.
            boolean on = AdBlock.isOn();
            labels.add(on ? "Autoriser les pubs" : "Bloquer les pubs");
            actions.add(() -> { AdBlock.set(this, !on); focus(pkg); });
        }
        labels.add("Déplacer");
        actions.add(() -> startMove(pkg));
        labels.add("Remplacer");
        actions.add(() -> { replacing = pkg; pickApp(REQ_REPLACE); });
        labels.add("Retirer");
        actions.add(() -> { pins.remove(pkg); savePins(); buildRow(); tvTile.requestFocus(); });
        List<View> rows = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            TextView r = Ui.text(this, 19, Ui.TEXT, null);
            r.setText(labels.get(i));
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
            GradientDrawable on = new GradientDrawable();
            on.setColor(0x33FFFFFF);
            on.setCornerRadius(Ui.dp(this, 12));
            StateListDrawable s = new StateListDrawable();
            s.addState(new int[] {android.R.attr.state_focused}, on);
            r.setBackground(s);
            r.setFocusable(true);
            r.setId(View.generateViewId());
            final Runnable a = actions.get(i);
            r.setOnClickListener(v -> { closeMenu(); a.run(); });
            m.addView(r, new LinearLayout.LayoutParams(Ui.dp(this, 200), Ui.dp(this, 50)));
            rows.add(r);
        }
        for (int i = 0; i < rows.size(); i++) {
            View r = rows.get(i);
            r.setNextFocusLeftId(r.getId());
            r.setNextFocusRightId(r.getId());
            r.setNextFocusUpId(rows.get(Math.max(0, i - 1)).getId());
            r.setNextFocusDownId(rows.get(Math.min(rows.size() - 1, i + 1)).getId());
        }
        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);
        int h = rows.size() * Ui.dp(this, 50) + 2 * p;
        root.addView(m, at(Math.min(loc[0], 1280 - Ui.dp(this, 216) - M), loc[1] - h - Ui.dp(this, 14), -2, -2));
        menu = m;
        menu.setTag(pkg);
        rows.get(0).requestFocus();
    }

    private boolean closeMenu() {
        if (menu == null) return false;
        String pkg = (String) menu.getTag();
        root.removeView(menu);
        menu = null;
        focusRow(pkg);
        return true;
    }

    private void startMove(String pkg) {
        moving = pkg;
        focusRow(pkg);
        hint.setText("◀ ▶  puis OK pour poser");
    }

    private void shift(String pkg, int dir) {
        int i = pins.indexOf(pkg), j = i + dir;
        if (i < 0 || j < 0 || j >= pins.size()) return;
        pins.set(i, pins.get(j));
        pins.set(j, pkg);
        buildRow();
        focusRow(pkg);
        hint.setText("◀ ▶  puis OK pour poser");
    }

    private void endMove() {
        String pkg = moving;
        moving = null;
        savePins();
        if (pkg != null) focus(pkg);
    }

    // ---------------------------------------------------------------- focus, card, glow

    private boolean followsTv() {
        return "tv".equals(focusKey) || "apps".equals(focusKey) || "src".equals(focusKey)
                || "cast".equals(focusKey) || "set".equals(focusKey);
    }

    private void focus(String key) {
        focusKey = key;
        switch (key) {
            case "tv": setCard("Télévision", "Récepteur", "HDMI 1, chaînes satellite", "Regarder en plein écran"); break;
            case "apps": setCard("Raccourci", "Applications", "Tout ce qui est installé", "Ouvrir"); break;
            case "src": setCard("Raccourci", "Sources", "HDMI 2, AV, antenne", "Choisir"); break;
            case "cast": setCard("Raccourci", "Caster", "L'écran d'un téléphone sur la TV", "Ouvrir"); break;
            case "set": setCard("Raccourci", "Réglages", "Image, son, réseau", "Ouvrir"); break;
            case ADD: setCard("Accueil", "Ajouter", "Épingler une application", "Choisir"); break;
            default: setCard("Application", Ui.label(getPackageManager(), key), describe(key), "Ouvrir");
        }
        if (followsTv()) {
            glowTo(haveLive ? liveA : DEFAULT_A, haveLive ? liveB : DEFAULT_B, 400);
        } else if (ADD.equals(key)) {
            glowTo(0xFF2E323C, 0xFF22252D, 400);
        } else {
            int[] c = appGlow(key);
            glowTo(c[0], c[1], 400);
        }
    }

    /**
     * Two quiet colours per app. Many TV apps share the same red, so apps.xml can give each its
     * own pair; others get their logo's main colour and a neighbouring hue.
     */
    private int[] appGlow(String pkg) {
        String[] st = styles.get(pkg);
        if (st != null) {
            try {
                return new int[] {Color.parseColor(st[2].trim()), Color.parseColor(st[3].trim())};
            } catch (IllegalArgumentException ignored) {
            }
        }
        Integer c = appColor.get(pkg);
        if (c == null) {
            c = Ui.blend(Ui.glowColor(Ui.dominantColor(this, pkg)), Color.BLACK, 0.45f);
            appColor.put(pkg, c);
        }
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[0] = (hsv[0] + 40f) % 360f;
        hsv[2] *= 0.7f;
        return new int[] {c, Color.HSVToColor(hsv)};
    }

    private void setCard(String k, String t, String s, String h) {
        kicker.setText(k);
        title.setText(t);
        subtitle.setText(s);
        hint.setText(h);
    }

    private String describe(String pkg) {
        if (CHROME.equals(pkg)) return AdBlock.isOn() ? "Navigateur web, pubs bloquées" : "Navigateur web, pubs autorisées";
        String[] st = styles.get(pkg);
        return st == null ? "" : st[1].trim();
    }

    /** Fades both glows to new colours by re-tinting the masks (no repaint). */
    private void glowTo(int a, int b, long ms) {
        if (glowAnim != null) glowAnim.cancel();
        final int a0 = colorA, b0 = colorB;
        if (a0 == a && b0 == b) return;
        glowAnim = ValueAnimator.ofFloat(0f, 1f);
        glowAnim.setDuration(ms);
        glowAnim.setInterpolator(Ui.EASE);
        glowAnim.addUpdateListener(an -> {
            float t = (float) an.getAnimatedValue();
            colorA = Ui.blend(a0, a, t);
            colorB = Ui.blend(b0, b, t);
            applyTints();
        });
        glowAnim.start();
    }

    private void applyTints() {
        maskA.setTint(colorA);
        maskB.setTint(colorB);
        corners.setTints(colorA, colorB);
    }

    private void updateHeader() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(h >= 18 || h < 5 ? "Bonsoir" : "Bonjour");
        updateWifi();
    }

    private final Runnable wifiTick = new Runnable() {
        @Override
        public void run() {
            updateWifi();
            handler.postDelayed(this, 30000);
        }
    };

    private static final int[] WIFI_ICONS = {
        R.drawable.ic_wifi_0, R.drawable.ic_wifi_1, R.drawable.ic_wifi_2, R.drawable.ic_wifi_3, R.drawable.ic_wifi_4,
    };

    /** Signal strength as bars; an empty fan when offline. */
    private void updateWifi() {
        int level = 0;
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wi = wm == null ? null : wm.getConnectionInfo();
            if (wi != null && wi.getNetworkId() != -1) {
                level = Math.max(1, WifiManager.calculateSignalLevel(wi.getRssi(), 5));
            }
        } catch (Exception ignored) {
        }
        wifiIcon.setImageResource(WIFI_ICONS[Math.min(4, level)]);
    }

    private void openSources() {
        if (!Ui.start(this, new Intent("com.cvte.intent.SHOW_INPUT"))) {
            Ui.start(this, new Intent().setClassName("com.cvte.tv.setting", "com.cvte.tv.setting.TvSettingActivity"));
        }
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        LiveAmbient.lastActivity = System.currentTimeMillis();
        return super.dispatchKeyEvent(ev);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LiveAmbient.lastActivity = System.currentTimeMillis();
        HomeGuard.ensureEnabled(this);
        updateHeader();
        handler.postDelayed(wifiTick, 30000);
        dropUninstalled();
        if (hdmiId != null) {
            cover.animate().cancel();
            cover.setAlpha(1f);
            preview.tune(hdmiId, TvContract.buildChannelUriForPassthroughInput(hdmiId));
            preview.setStreamVolume(0f); // silent preview; sound comes back in full screen
        } else {
            previewStatus.setText("HDMI introuvable");
        }
        root.post(() -> {
            int[] loc = new int[2];
            preview.getLocationOnScreen(loc);
            live.setArea(new Rect(loc[0], loc[1], loc[0] + preview.getWidth(), loc[1] + preview.getHeight()));
            live.start();
        });
        if (menu == null && moving == null && getCurrentFocus() == null) tvTile.requestFocus();
        if (getCurrentFocus() == tvTile) focus("tv");
    }

    /** Apps uninstalled while away drop off the row. */
    private void dropUninstalled() {
        PackageManager pm = getPackageManager();
        boolean gone = false;
        for (String p : pins) if (Ui.launchIntent(pm, p) == null) gone = true;
        if (gone) {
            loadPins();
            buildRow();
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(wifiTick);
        live.stop();
        super.onPause();
    }

    @Override
    protected void onStop() {
        // Release the HDMI picture only once the next app covers us: doing it while still visible
        // made the preview flash blue.
        preview.reset();
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home: close menus and go back to the TV.
        closeMenu();
        if (moving != null) endMove();
        tvTile.requestFocus();
    }

    @Override
    protected void onDestroy() {
        live.quit();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (closeMenu()) return;
        if (moving != null) {
            endMove();
            return;
        }
        tvTile.requestFocus();
    }
}
