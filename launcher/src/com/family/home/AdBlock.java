package com.family.home;

import android.app.ActivityManager;
import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Ad blocking for Chrome only, switched from the home screen.
 *
 * On debug firmware (ro.debuggable=1, as on these TVs) Chrome reads extra switches from
 * /data/local/chrome-command-line when it starts, and that folder is writable. We write a
 * --host-resolver-rules switch that makes the ad networks in adblock.xml unreachable for Chrome.
 * Other apps keep the normal DNS: system-wide blockers break some IPTV apps.
 */
final class AdBlock {
    static final String CHROME = "com.android.chrome";
    private static final File FILE = new File("/data/local/chrome-command-line");

    static boolean isOn() {
        return FILE.exists();
    }

    /** First start: blocking on. Afterwards the file itself is the switch, so it survives reinstalls. */
    static void init(Context c) {
        if (c.getSharedPreferences("home", Context.MODE_PRIVATE).getBoolean("adblock_set", false)) return;
        set(c, true);
    }

    static boolean set(Context c, boolean on) {
        c.getSharedPreferences("home", Context.MODE_PRIVATE).edit().putBoolean("adblock_set", true).apply();
        FILE.delete(); // may belong to another user (adb shell): replace it rather than overwrite
        boolean ok = true;
        if (on) {
            StringBuilder rules = new StringBuilder();
            for (String d : c.getResources().getStringArray(R.array.ad_domains)) {
                d = d.trim();
                if (d.isEmpty()) continue;
                if (rules.length() > 0) rules.append(", ");
                rules.append("MAP ").append(d).append(" ~NOTFOUND, MAP *.").append(d).append(" ~NOTFOUND");
            }
            // The first word stands for the program name and is ignored.
            String line = "_ --host-resolver-rules=\"" + rules + "\"\n";
            try (FileOutputStream out = new FileOutputStream(FILE)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                ok = false;
            }
            FILE.setReadable(true, false);
        }
        // Chrome reads the file only when it starts: close it if it's waiting in the background.
        ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).killBackgroundProcesses(CHROME);
        return ok;
    }
}
