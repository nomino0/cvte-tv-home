package com.family.home;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Every launchable app. Opened from the "Applis" pill to launch something, or with "pick" from
 * the + tile / Replace to choose an app for the dock (the choice is returned, nothing launches).
 */
public class AppsActivity extends Activity {
    private static final int COLS = 5, TW = 200, TH = 112, GAP = 24;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setBackgroundDrawable(Ui.background(this));
        final boolean pick = getIntent().getBooleanExtra("pick", false);
        String[] ex = getIntent().getStringArrayExtra("exclude");
        List<String> exclude = ex == null ? new ArrayList<>() : Arrays.asList(ex);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 64), Ui.dp(this, 40), Ui.dp(this, 64), 0);
        TextView title = Ui.text(this, 34, Ui.TEXT, Ui.light());
        title.setText(pick ? "Choisir une application" : "Applications");
        root.addView(title);
        TextView sub = Ui.text(this, 16, Ui.TEXT_2, null);
        sub.setText(pick ? "Elle sera épinglée sur l'accueil" : "Tout ce qui est installé sur la TV");
        sub.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 24));
        root.addView(sub);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(COLS);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 40));
        PackageManager pm = getPackageManager();
        int tw = Ui.dp(this, TW), th = Ui.dp(this, TH);
        for (String pkg : launchablePackages()) {
            if (exclude.contains(pkg)) continue;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setClipChildren(false);
            FrameLayout tile = Ui.appTile(this, pkg, tw, th);
            TextView name = Ui.text(this, 15, Ui.TEXT_2, null);
            name.setText(Ui.label(pm, pkg));
            name.setGravity(Gravity.CENTER_HORIZONTAL);
            name.setPadding(0, Ui.dp(this, 10), 0, 0);
            Ui.lift(tile, 1.05f, (v, has) -> name.setTextColor(has ? Ui.TEXT : Ui.TEXT_2));
            tile.setOnClickListener(v -> {
                if (pick) {
                    setResult(RESULT_OK, new Intent().putExtra("pkg", pkg));
                    finish();
                } else {
                    Ui.start(this, Ui.launchIntent(pm, pkg));
                }
            });
            cell.addView(tile, new LinearLayout.LayoutParams(tw, th));
            cell.addView(name, new LinearLayout.LayoutParams(tw, -2));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.setMargins(0, 0, Ui.dp(this, GAP), Ui.dp(this, GAP));
            grid.addView(cell, lp);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(grid);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        setContentView(root);
        if (grid.getChildCount() > 0) ((LinearLayout) grid.getChildAt(0)).getChildAt(0).requestFocus();
    }

    private List<String> launchablePackages() {
        PackageManager pm = getPackageManager();
        Set<String> seen = new LinkedHashSet<>();
        for (String cat : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) seen.add(ri.activityInfo.packageName);
        }
        seen.remove(getPackageName());
        seen.remove("com.cvte.tv.launcher.saturn"); // the old home screen
        List<String> out = new ArrayList<>(seen);
        Collections.sort(out, (a, z) -> Ui.label(pm, a).compareToIgnoreCase(Ui.label(pm, z)));
        return out;
    }
}
