package com.family.home;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * The CVTE firmware opens its own launcher at startup (and from some keys) by name, ignoring the
 * default Home, and it doesn't deliver BOOT_COMPLETED to added apps. This service only listens to
 * that launcher's package (see res/xml/home_guard.xml): when its main screen comes up, ours opens
 * over it. It reads nothing on screen. If ours keeps failing, it stops redirecting for a while so
 * the TV can never loop between the two.
 */
public class HomeGuard extends AccessibilityService {
    static final String STOCK = "com.cvte.tv.launcher.saturn";
    private static final long WINDOW_MS = 20_000, PAUSE_MS = 120_000;
    private static final int MAX_IN_WINDOW = 3;

    private final long[] recent = new long[MAX_IN_WINDOW];
    private int n;
    private long pausedUntil;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (e.getPackageName() == null || !STOCK.contentEquals(e.getPackageName())) return;
        CharSequence cls = e.getClassName();
        if (cls == null || !cls.toString().endsWith(".home.MainActivity")) return;

        long now = SystemClock.elapsedRealtime();
        if (now < pausedUntil) return;
        recent[n++ % MAX_IN_WINDOW] = now;
        if (n >= MAX_IN_WINDOW && now - recent[n % MAX_IN_WINDOW] < WINDOW_MS) {
            pausedUntil = now + PAUSE_MS; // ours isn't staying up: leave the stock one alone
            n = 0;
            return;
        }
        Intent home = new Intent(this, HomeActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(home);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onServiceConnected() {
        Tweaks.start(this); // at boot this runs before the home screen opens
    }

    @Override
    public void onInterrupt() {
    }

    /**
     * Android drops an accessibility service from the enabled list while its app is being updated.
     * If adb granted WRITE_SECURE_SETTINGS, put ours back (leaving any other service as it is).
     */
    static void ensureEnabled(android.content.Context c) {
        if (c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        android.content.ContentResolver cr = c.getContentResolver();
        String me = c.getPackageName() + "/" + HomeGuard.class.getName();
        try {
            String list = android.provider.Settings.Secure.getString(cr,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (list == null || "null".equals(list)) list = "";
            boolean listed = (":" + list + ":").contains(":" + me + ":");
            if (!listed) {
                android.provider.Settings.Secure.putString(cr,
                        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        list.isEmpty() ? me : list + ":" + me);
            }
            if (android.provider.Settings.Secure.getInt(cr,
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) {
                android.provider.Settings.Secure.putInt(cr,
                        android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            }
        } catch (Exception ignored) {
        }
    }
}
