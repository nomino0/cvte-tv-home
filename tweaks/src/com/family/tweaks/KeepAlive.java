package com.family.tweaks;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * The firmware doesn't deliver BOOT_COMPLETED to added apps, but it does start enabled accessibility
 * services at boot and keeps their process alive. This one only hosts {@link Tweaks}.
 */
public class KeepAlive extends AccessibilityService {
    @Override
    protected void onServiceConnected() {
        Tweaks.start(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
    }

    @Override
    public void onInterrupt() {
    }
}
