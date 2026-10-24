package dev.godlesscherry.tvstat;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

/**
 * CPU usage from the kernel's time_in_state counters: time spent at each frequency, weighted by
 * that frequency, then normalised between the core's minimum and maximum clock.
 *
 * Normalising against min-max (not against max alone) is the whole point on this SoC: its minimum
 * clock is 1.2 GHz and its maximum is 1.8, so clock/max can never read below ~67% even on an idle
 * TV. Against the real range, idle reads low and pinned cores read 100%.
 *
 * Falls back to the instantaneous clock when time_in_state is unavailable.
 */
public final class CpuUsage {

    private final HashMap<Long, Long> prev = new HashMap<>();
    private double last = -1;

    public double sample() {
        HashMap<Long, Long> now = readAll();
        if (!now.isEmpty()) {
            if (prev.isEmpty()) {
                prev.putAll(now);
                return last;                      // need two samples for a delta
            }
            double num = 0, den = 0;
            for (Map.Entry<Long, Long> e : now.entrySet()) {
                Long p = prev.get(e.getKey());
                long d = e.getValue() - (p == null ? 0L : p);
                if (d > 0) {
                    num += (double) d * e.getKey();   // kHz * ticks
                    den += d;
                }
            }
            prev.clear();
            prev.putAll(now);
            if (den > 0) {
                last = normalise(num / den / 1000.0);   // -> MHz
                return last;
            }
        }
        double ghz = Metrics.clockGhz();
        if (ghz > 0) last = normalise(ghz * 1000.0);
        return last;
    }

    private static double normalise(double avgMhz) {
        double min = Metrics.minGhz() * 1000.0;
        double max = Metrics.maxGhz() * 1000.0;
        if (max <= min) return -1;
        double p = 100.0 * (avgMhz - min) / (max - min);
        return Math.max(0, Math.min(100, p));
    }

    private static HashMap<Long, Long> readAll() {
        HashMap<Long, Long> m = new HashMap<>();
        for (int i = 0; i < 8; i++) {
            BufferedReader r = null;
            try {
                r = new BufferedReader(new FileReader(
                        "/sys/devices/system/cpu/cpu" + i + "/cpufreq/stats/time_in_state"));
                String line;
                while ((line = r.readLine()) != null) {
                    String[] p = line.trim().split("\\s+");
                    if (p.length < 2) continue;
                    long f = Long.parseLong(p[0]);
                    long t = Long.parseLong(p[1]);
                    Long cur = m.get(f);
                    m.put(f, (cur == null ? 0L : cur) + t);
                }
            } catch (Exception ignored) {
            } finally {
                try { if (r != null) r.close(); } catch (Exception ignored) { }
            }
        }
        return m;
    }
}
