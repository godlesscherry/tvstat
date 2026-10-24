package dev.godlesscherry.tvstat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Locale;

/** A small always-on-top HUD: CPU clock, RAM and /data, refreshed every 2 seconds. */
public class MonitorService extends Service {

    public static volatile boolean RUNNING = false;

    private static final String CHANNEL = "hud";
    private static final int NOTIF_ID = 42;

    private WindowManager wm;
    private TextView hud;
    private WindowManager.LayoutParams params;
    private final Handler handler = new Handler();
    private final CpuUsage cpuUsage = new CpuUsage();

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            update();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        RUNNING = true;
        startForeground(NOTIF_ID, buildNotification());
        attachHud();
        handler.post(ticker);
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL, "tvstat HUD",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, flags);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return b.setContentTitle("tvstat HUD")
                .setContentText("Overlay running — tap to open")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void attachHud() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        hud = new TextView(this);
        hud.setTextColor(0xFFECEEF1);
        hud.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hud.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                int px = dp(12);
        hud.setPadding(px, dp(9), px, dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(222, 16, 18, 21));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dpHair(), 0xFF2E333B);
        hud.setBackground(bg);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(26);
        params.y = dp(22);

        wm.addView(hud, params);
        update();
    }

    private void update() {
        if (hud == null) return;
        double usage = cpuUsage.sample();
        double ghz = Metrics.clockGhz();
        double ram = Metrics.ramPercent(this);
        double[] disk = Metrics.disk(this);

        String cpu = (usage < 0 || ghz < 0)
                ? "CPU n/a"
                : String.format(Locale.US, "CPU %.0f%% (%.2fG)", usage, ghz);
        String ramS = ram < 0 ? "RAM n/a" : String.format(Locale.US, "RAM %.0f%%", ram);
        String diskS = disk[0] < 0 ? "DISK n/a" : String.format(Locale.US, "DISK %.0f%%", disk[0]);

        hud.setText(cpu + "   " + ramS + "   " + diskS);
    }

    private int dpHair() {
        return Math.max(1, dp(1));
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        RUNNING = false;
        handler.removeCallbacks(ticker);
        try { if (wm != null && hud != null) wm.removeView(hud); } catch (Exception ignored) { }
        hud = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
