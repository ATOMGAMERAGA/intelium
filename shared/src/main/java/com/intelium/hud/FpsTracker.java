package com.intelium.hud;

import java.util.Arrays;

/**
 * Rolling-window FPS tracker. Pure logic (no Minecraft dependency) so it can be
 * unit-tested. Fed each client tick with {@code MinecraftClient.getCurrentFps()}.
 *
 * <p>Besides the smoothed average it exposes stutter indicators - the window
 * minimum and the "1% low" (the average of the worst frames) - so the overlay
 * can show how bad the hitches are, not just the headline number. A bigger
 * window than one second is needed for the 1% low to be meaningful.
 */
public final class FpsTracker {

    private final int[] samples;
    private int idx;
    private int count;
    /** Running total keeps the adaptive controllers' per-tick average O(1). */
    private long sum;

    /**
     * Distribution statistics are invalidated by a new sample, then computed
     * together on first use. The overlay may render several times between the
     * 20 Hz samples; sorting the same window every rendered frame was needless
     * work performed by the FPS tool itself.
     */
    private boolean distributionDirty = true;
    private int cachedMin;
    private int cachedOnePercentLow;

    public FpsTracker(int window) {
        this.samples = new int[Math.max(1, window)];
    }

    public void push(int fps) {
        if (fps < 0) fps = 0;
        if (count == samples.length) {
            sum -= samples[idx];
        }
        samples[idx] = fps;
        sum += fps;
        idx = (idx + 1) % samples.length;
        if (count < samples.length) count++;
        distributionDirty = true;
    }

    /** Rolling average of the recent samples, rounded. 0 if no samples yet. */
    public int smoothed() {
        if (count == 0) return 0;
        return (int) Math.round((double) sum / count);
    }

    /** Most recent sample, or 0 if none. */
    public int last() {
        if (count == 0) return 0;
        return samples[(idx - 1 + samples.length) % samples.length];
    }

    /** Smallest sample in the window, or 0 if none. The worst single hitch. */
    public int min() {
        if (count == 0) return 0;
        refreshDistribution();
        return cachedMin;
    }

    /**
     * The "1% low": the average of the worst 1% of frames in the window (at
     * least one sample). A standard stutter metric - a big gap between this and
     * {@link #smoothed()} means the experience is hitchy even if the average
     * looks fine. Returns 0 when there are no samples.
     */
    public int onePercentLow() {
        if (count == 0) return 0;
        refreshDistribution();
        return cachedOnePercentLow;
    }

    /**
     * Average of the worst {@code fraction} of samples (e.g. 0.01 for the 1%
     * low). Always includes at least one sample. 0 when empty.
     */
    int lowAverage(double fraction) {
        if (count == 0) return 0;
        int[] sorted = Arrays.copyOf(samples, count);
        Arrays.sort(sorted); // ascending: worst frames first
        // Keep this helper total even for a bad caller: fractions outside the
        // documented range cannot index beyond the populated window.
        double bounded = Double.isFinite(fraction)
                ? Math.max(0.0, Math.min(1.0, fraction)) : 0.0;
        int n = Math.max(1, (int) Math.ceil(count * bounded));
        long sum = 0;
        for (int i = 0; i < n; i++) sum += sorted[i];
        return (int) Math.round((double) sum / n);
    }

    public int sampleCount() {
        return count;
    }

    public void reset() {
        idx = 0;
        count = 0;
        sum = 0L;
        cachedMin = 0;
        cachedOnePercentLow = 0;
        distributionDirty = true;
        // Also clear the ring itself, so stale samples cannot resurface if the
        // indexing above ever changes.
        Arrays.fill(samples, 0);
    }

    /** Computes both overlay distribution metrics at most once per sample. */
    private void refreshDistribution() {
        if (!distributionDirty) return;
        if (count == 0) {
            cachedMin = 0;
            cachedOnePercentLow = 0;
            distributionDirty = false;
            return;
        }

        int[] sorted = Arrays.copyOf(samples, count);
        Arrays.sort(sorted);
        cachedMin = sorted[0];

        int n = Math.max(1, (int) Math.ceil(count * 0.01));
        long lowSum = 0L;
        for (int i = 0; i < n; i++) lowSum += sorted[i];
        cachedOnePercentLow = (int) Math.round((double) lowSum / n);
        distributionDirty = false;
    }
}
