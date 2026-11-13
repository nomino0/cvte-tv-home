package com.family.tweaks;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

/**
 * Fixes the firmware undoes on every start, applied through the TV's own ADB. Same as the home
 * screen's Tweaks, for TVs that keep their own launcher.
 */
final class Tweaks {
    private static final long CHECK_MS = 5 * 60 * 1000;
    private static Tweaks running;

    private final Adb adb = new Adb();
    private final Handler main = new Handler(Looper.getMainLooper());

    static synchronized void start(Context c) {
        if (running == null) running = new Tweaks(c.getApplicationContext());
    }

    private Tweaks(Context c) {
        // The firmware empties the file cache every 10 s when memory runs low (drop_caches.sh), so
        // apps keep re-reading their code from storage and 1080p video stutters. Stop it until the
        // next restart; the kernel frees cache on its own when memory is actually needed.
        adb.shell("setprop ctl.stop drop_caches");
        // Wi-Fi power save only adds delay and drops on a TV that is always plugged in. Turning it off
        // needs root, and Android turns it back on after every reconnect: watch for those.
        adb.root();
        powerSaveOff.run();
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        cm.registerNetworkCallback(new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network n) {
                        main.removeCallbacks(powerSaveOff);
                        main.postDelayed(powerSaveOff, 5000); // after DHCP, which switches it back on
                    }
                });
    }

    private final Runnable powerSaveOff = new Runnable() {
        @Override
        public void run() {
            adb.shell("iw dev wlan0 get power_save | grep -q on && iw dev wlan0 set power_save off");
            main.removeCallbacks(this);
            main.postDelayed(this, CHECK_MS);
        }
    };
}
