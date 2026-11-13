package com.family.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * The CVTE firmware opens its own launcher at startup whatever the default Home is. Once boot is
 * done, bring ours to the front. The stock launcher stays installed as the safety net.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Intent home = new Intent(c, HomeActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(home);
        } catch (Exception ignored) {
        }
    }
}
