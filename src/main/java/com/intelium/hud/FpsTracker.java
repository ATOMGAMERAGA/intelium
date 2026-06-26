package com.intelium.hud;

/**
 * Small rolling-window FPS tracker. Pure logic (no Minecraft dependency) so it
 * can be unit-tested. Fed each client tick with {@code MinecraftClient.getCurrentFps()}.
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

    public int sampleCount() {
        return count;
    }

    public void reset() {
        idx = 0;
        count = 0;
    }
}
