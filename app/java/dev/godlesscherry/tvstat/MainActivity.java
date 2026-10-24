package dev.godlesscherry.tvstat;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

public class MainActivity extends Activity {

    // ---- palette: neutral greys, one accent, amber only for a genuine threshold ----
    private static final int BG      = 0xFF0E0F11;
    private static final int TEXT    = 0xFFECEEF1;
    private static final int MUTED   = 0xFF8A9099;
    private static final int FAINT   = 0xFF5F656D;
    private static final int LINE    = 0xFF23272D;
    private static final int ACCENT  = 0xFF4C8DFF;
    private static final int WARN    = 0xFFE0A146;

    private final Typeface sans = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface sansMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private TextView cpuVal, cpuSub, ramVal, ramSub, diskVal, diskSub, upVal, hudStatus, hudButton;
    private BarView cpuBar, ramBar, diskBar;

    private long lastTotal = -1, lastIdle = -1;
    private final CpuUsage cpuUsage = new CpuUsage();
    private final Handler handler = new Handler();

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    private static class Metric {
        TextView value;
        TextView sub;
        BarView bar;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(64), dp(26), dp(64), dp(18));

        // header: title left, state right
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.BOTTOM);

        TextView title = new TextView(this);
        title.setText("tvstat");
        title.setTextColor(TEXT);
        title.setTypeface(sansMedium);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        header.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        hudStatus = new TextView(this);
        hudStatus.setTextColor(MUTED);
        hudStatus.setTypeface(sans);
        hudStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        hudStatus.setGravity(Gravity.END);
        header.addView(hudStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(header);

        View rule = new View(this);
        rule.setBackgroundColor(LINE);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        rlp.topMargin = dp(14);
        root.addView(rule, rlp);

        Metric cpu = addMetric(root, "Processor");
        cpuVal = cpu.value; cpuSub = cpu.sub; cpuBar = cpu.bar;
        Metric ram = addMetric(root, "RAM");
        ramVal = ram.value; ramSub = ram.sub; ramBar = ram.bar;
        Metric disk = addMetric(root, "Storage");
        diskVal = disk.value; diskSub = disk.sub; diskBar = disk.bar;

        // uptime: no bar, it is not a proportion of anything
        LinearLayout upRow = new LinearLayout(this);
        upRow.setOrientation(LinearLayout.HORIZONTAL);
        upRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView upLabel = new TextView(this);
        upLabel.setText("Uptime");
        upLabel.setTextColor(MUTED);
        upLabel.setTypeface(sans);
        upLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        upRow.addView(upLabel, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        upVal = new TextView(this);
        upVal.setTextColor(TEXT);
        upVal.setTypeface(sansMedium);
        upVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        upRow.addView(upVal);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ulp.topMargin = dp(18);
        root.addView(upRow, ulp);

        hudButton = new TextView(this);
        hudButton.setTypeface(sansMedium);
        hudButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        hudButton.setTextColor(TEXT);
        hudButton.setGravity(Gravity.CENTER);
        hudButton.setPadding(dp(26), dp(12), dp(26), dp(13));
        hudButton.setFocusable(true);
        hudButton.setFocusableInTouchMode(true);
        hudButton.setClickable(true);
        hudButton.setBackground(outlineButton());
        hudButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleHud();
                refresh();
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(20);
        root.addView(hudButton, blp);

        TextView foot = new TextView(this);
        foot.setText("D-pad down, then OK");
        foot.setTextColor(FAINT);
        foot.setTypeface(sans);
        foot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        flp.topMargin = dp(12);
        root.addView(foot, flp);

        // a FrameLayout so the watermark can sit in the bottom-right corner, outside the flow
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        frame.addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        TextView mark = new TextView(this);
        mark.setText("github.com/godlesscherry");
        mark.setTextColor(FAINT);
        mark.setTypeface(sans);
        mark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        mlp.setMargins(0, 0, dp(64), dp(22));
        frame.addView(mark, mlp);

        setContentView(frame);

        if (getIntent() != null && getIntent().getBooleanExtra("hud", false)) startHudIfPossible();
    }

    /** Label left, value right, thin track underneath, one muted sub-line. */
    private Metric addMetric(LinearLayout root, String name) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(this);
        label.setText(name);
        label.setTextColor(MUTED);
        label.setTypeface(sans);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        head.addView(label, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(this);
        value.setTextColor(TEXT);
        value.setTypeface(sansMedium);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        value.setGravity(Gravity.END);
        head.addView(value);
        block.addView(head);

        BarView bar = new BarView(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(2, dp(4)));
        blp.topMargin = dp(10);
        block.addView(bar, blp);

        TextView sub = new TextView(this);
        sub.setTextColor(FAINT);
        sub.setTypeface(sans);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(9);
        block.addView(sub, slp);

        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(15);
        root.addView(block, mlp);

        Metric m = new Metric();
        m.value = value;
        m.sub = sub;
        m.bar = bar;
        return m;
    }

    /** Hairline outline, fills in slightly on focus. */
    private Drawable outlineButton() {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(0x00000000);
        normal.setCornerRadius(dp(8));
        normal.setStroke(Math.max(1, dp(1)), LINE);

        GradientDrawable focus = new GradientDrawable();
        focus.setColor(0xFF191C21);
        focus.setCornerRadius(dp(8));
        focus.setStroke(Math.max(1, dp(1)), ACCENT);

        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focus);
        s.addState(new int[]{android.R.attr.state_pressed}, focus);
        s.addState(new int[]{}, normal);
        return s;
    }

    private void toggleHud() {
        if (MonitorService.RUNNING) {
            stopService(new Intent(this, MonitorService.class));
        } else {
            startHudIfPossible();
        }
    }

    private void startHudIfPossible() {
        if (!Settings.canDrawOverlays(this)) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) { }
            return;
        }
        Intent i = new Intent(this, MonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private static int level(double pct) {
        return pct >= 85 ? WARN : ACCENT;
    }

    private double[] readCpu() {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader("/proc/stat"));
            String line = r.readLine();
            String[] p = line.trim().split("\\s+");
            long total = 0, idle = 0;
            for (int i = 1; i < p.length && i <= 8; i++) {
                long v = Long.parseLong(p[i]);
                total += v;
                if (i == 4 || i == 5) idle += v;
            }
            return new double[]{total, idle};
        } catch (Exception e) {
            return null;
        } finally {
            try { if (r != null) r.close(); } catch (Exception ignored) { }
        }
    }

    private void refresh() {
        // Processor
        double usage = -1;
        double[] cpu = readCpu();
        if (cpu != null) {
            long total = (long) cpu[0], idle = (long) cpu[1];
            if (lastTotal >= 0) {
                long dt = total - lastTotal, di = idle - lastIdle;
                usage = dt > 0 ? 100.0 * (1.0 - (double) di / (double) dt) : 0.0;
            }
            lastTotal = total;
            lastIdle = idle;
        } else {
            usage = cpuUsage.sample();
        }
        double ghz = Metrics.clockGhz();
        if (usage < 0) {
            cpuVal.setText("--");
            cpuSub.setText("measuring");
            cpuBar.set(0, ACCENT);
        } else {
            cpuVal.setText(String.format(Locale.US, "%.0f%%", usage));
            cpuBar.set((float) (usage / 100.0), level(usage));
            cpuSub.setText(String.format(Locale.US, "%.2f GHz  ·  range %.2f - %.2f GHz  ·  schedutil",
                    ghz < 0 ? 0 : ghz, Metrics.minGhz(), Metrics.maxGhz()));
        }

        // Memory
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        double ramPct = 100.0 * (mi.totalMem - mi.availMem) / (double) mi.totalMem;
        ramVal.setText(String.format(Locale.US, "%.1f%%", ramPct));
        ramBar.set((float) (ramPct / 100.0), level(ramPct));
        ramSub.setText(Metrics.human(mi.totalMem - mi.availMem) + " of "
                + Metrics.human(mi.totalMem) + " used  ·  " + Metrics.human(mi.availMem) + " free");

        // Storage
        double[] disk = Metrics.disk(this);
        if (disk[0] >= 0) {
            diskVal.setText(String.format(Locale.US, "%.1f%%", disk[0]));
            diskBar.set((float) (disk[0] / 100.0), level(disk[0]));
            diskSub.setText(Metrics.human((long) disk[1]) + " free of " + Metrics.human((long) disk[2]));
        } else {
            diskVal.setText("--");
            diskSub.setText("unavailable");
        }

        long up = SystemClock.elapsedRealtime() / 1000L;
        upVal.setText(String.format(Locale.US, "%dh %02dm %02ds",
                up / 3600, (up % 3600) / 60, up % 60));

        hudStatus.setText((Settings.canDrawOverlays(this) ? "Overlay ready" : "Overlay blocked")
                + "  ·  " + (MonitorService.RUNNING ? "HUD on" : "HUD off"));
        hudButton.setText(MonitorService.RUNNING ? "Stop overlay" : "Start overlay");
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }
}
