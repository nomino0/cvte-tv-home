package com.family.home;

import android.app.Activity;
import android.graphics.Color;
import android.media.tv.TvContract;
import android.media.tv.TvView;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** HDMI 1 full screen with sound. Back returns to the home screen. */
public class HdmiActivity extends Activity {
    private TvView tv;
    private TextView status;
    private String hdmiId;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.BLACK));
        hdmiId = HomeActivity.findHdmi1(this);

        FrameLayout root = new FrameLayout(this);
        tv = new TvView(this);
        root.addView(tv, new FrameLayout.LayoutParams(-1, -1));
        status = Ui.text(this, 28, Color.WHITE, null);
        status.setText("Chargement…");
        status.setSingleLine(false);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new FrameLayout.LayoutParams(-1, -1));
        tv.setCallback(new TvView.TvInputCallback() {
            @Override
            public void onVideoAvailable(String inputId) {
                status.setVisibility(View.GONE);
            }

            @Override
            public void onVideoUnavailable(String inputId, int reason) {
                status.setText("Pas de signal sur HDMI 1\nVérifiez que le récepteur est allumé");
                status.setVisibility(View.VISIBLE);
            }
        });
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hdmiId != null) {
            tv.tune(hdmiId, TvContract.buildChannelUriForPassthroughInput(hdmiId));
            tv.setStreamVolume(1f);
        }
    }

    @Override
    protected void onPause() {
        tv.reset();
        super.onPause();
        finish(); // never linger in the background holding the decoder
    }
}
