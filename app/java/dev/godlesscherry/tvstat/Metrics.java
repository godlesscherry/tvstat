package dev.godlesscherry.tvstat;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

/** Shared readings for the activity and the HUD service. */
public final class Metrics {

    private Metrics() { }

    public static long readLong(String path) {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(path));
            String line = r.readLine();
            return line == null ? -1 : Long.parseLong(line.trim());
        } catch (Exception e) {
            return -1;
        } finally {
            try { if (r != null) r.close(); } catch (Exception ignored) { }
        }
    }

    /** Average current clock in GHz across the online cores, or -1 if cpufreq is unreadable. */
    public static double clockGhz() {
        long sum = 0, n = 0;
        for (int i = 0; i < 8; i++) {
            long cur = readLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            if (cur > 0) { sum += cur; n++; }
        }
        return n == 0 ? -1 : (sum / (double) n) / 1000000.0;
    }

    /** Highest max clock across the cores in GHz, or 1.8 as a sane fallback. */
    /** Lowest min clock across the cores in GHz, or 1.2 as a sane fallback. */
    public static double minGhz() {
        long min = 0;
        for (int i = 0; i < 8; i++) {
            long m = readLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_min_freq");
            if (m > 0 && (min == 0 || m < min)) min = m;
        }
        return min > 0 ? min / 1000000.0 : 1.2;
    }

    public static double maxGhz() {
        long max = 0;
        for (int i = 0; i < 8; i++) {
            long m = readLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
            if (m > max) max = m;
        }
        return max > 0 ? max / 1000000.0 : 1.8;
    }

    public static double ramPercent(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return 100.0 * (mi.totalMem - mi.availMem) / (double) mi.totalMem;
        } catch (Exception e) {
            return -1;
        }
    }

    /** {percentUsed, availBytes, totalBytes} for /data. */
    public static double[] disk(Context ctx) {
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long total = fs.getBlockCountLong() * fs.getBlockSizeLong();
            long avail = fs.getAvailableBlocksLong() * fs.getBlockSizeLong();
            return new double[]{total > 0 ? 100.0 * (total - avail) / (double) total : 0, avail, total};
        } catch (Exception e) {
            return new double[]{-1, 0, 0};
        }
    }

    public static String human(long bytes) {
        if (bytes >= 1073741824L) return String.format(Locale.US, "%.2f GB", bytes / 1073741824.0);
        if (bytes >= 1048576L) return String.format(Locale.US, "%.0f MB", bytes / 1048576.0);
        return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
    }
}
