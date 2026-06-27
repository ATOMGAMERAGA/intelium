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

    public FpsTracker(int window) {
        this.samples = new int[Math.max(1, window)];
    }

    public void push(int fps) {
        if (fps < 0) fps = 0;
        samples[idx] = fps;
        idx = (idx + 1) % samples.length;
        if (count < samples.length) count++;
    }

    /** Rolling average of the recent samples, rounded. 0 if no samples yet. */
    public int smoothed() {
        if (count == 0) return 0;
        long sum = 0;
        for (int i = 0; i < count; i++) sum += samples[i];
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
        int m = Integer.MAX_VALUE;
        for (int i = 0; i < count; i++) m = Math.min(m, samples[i]);
        return m;
    }

    /**
     * The "1% low": the average of the worst 1% of frames in the window (at
     * least one sample). A standard stutter metric - a big gap between this and
     * {@link #smoothed()} means the experience is hitchy even if the average
     * looks fine. Returns 0 when there are no samples.
     */
    public int onePercentLow() {
        return lowAverage(0.01);
    }

    /**
     * Average of the worst {@code fraction} of samples (e.g. 0.01 for the 1%
     * low). Always includes at least one sample. 0 when empty.
     */
    int lowAverage(double fraction) {
        if (count == 0) return 0;
        int[] sorted = Arrays.copyOf(samples, count);
        Arrays.sort(sorted); // ascending: worst frames first
        int n = Math.max(1, (int) Math.ceil(count * fraction));
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
    }
}
