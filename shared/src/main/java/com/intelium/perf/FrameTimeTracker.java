package com.intelium.perf;

import java.util.Arrays;

/**
 * A frame-time recorder built for the thing it measures: it must not cost the
 * frames it is reporting on.
 *
 * <h2>Why not {@code Minecraft#getFps()}</h2>
 *
 * <p>The game's FPS counter is a trailing whole-second average. It is fine for
 * a HUD number and useless for the two questions that actually matter on a weak
 * iGPU - "how bad are the spikes?" and "has this genuinely settled, or did one
 * second happen to look good?" A one-second average cannot show a 40 ms hitch
 * at all: it shows 58 instead of 60. Intelium's adaptive systems were being
 * steered by exactly that signal, so they reacted late and to the wrong thing.
 *
 * <p>This records individual frame intervals instead, and derives the
 * distribution: median, p95, p99, 1% low and 0.1% low. A frame-time
 * distribution is what tells you whether a change removed a stutter or merely
 * moved it.
 *
 * <h2>Cost</h2>
 *
 * <p>{@link #frame(long)} is called once per frame and does a subtract, a
 * bounds check and two array stores. There is no allocation anywhere on that
 * path: the ring is a preallocated {@code int[]}, and so is the scratch buffer
 * the percentile pass sorts into. Percentiles are never computed per frame -
 * the sample marks the distribution dirty and the sort happens at most once per
 * new sample, only if something actually asks for a percentile. Nothing here
 * asks unless a report is being produced, so in normal play the sort never runs
 * at all.
 *
 * <h2>Samples that must not steer anything</h2>
 *
 * <p>Frames rendered while the window is unfocused, while a menu FPS cap is
 * active, while VSync or a user FPS limit is pinning the frame rate, or while
 * the world is still streaming in are all real frames and all meaningless as
 * evidence of how hard the machine is working. {@link #invalidate()} drops the
 * in-flight interval and restarts the warm-up, so those frames neither enter
 * the distribution nor let an adaptive system believe it has a measurement.
 * {@link #warm()} stays false until enough consecutive good frames have been
 * seen, and every adaptive consumer is expected to check it.
 *
 * <p>Frame times are stored in <b>microseconds</b> as {@code int}: 35 minutes
 * of headroom per frame, half the memory of {@code long}, and finer resolution
 * than any display can show. Not thread-safe by design - it is fed from the
 * render thread only.
 */
public final class FrameTimeTracker {

    /** Frames needed after a reset/invalidate before {@link #warm()} is true. */
    public static final int DEFAULT_WARMUP_FRAMES = 120;

    /**
     * Intervals longer than this are not frames in any useful sense - they are
     * a world load, a resource reload, a driver stall or the gap across an
     * alt-tab. They are dropped rather than recorded, because a single 4-second
     * entry would dominate every high percentile in the window for as long as
     * it stayed in it.
     */
    private static final int MAX_PLAUSIBLE_FRAME_MICROS = 1_000_000;

    /** Intervals below this are a clock artefact, not a frame. */
    private static final int MIN_PLAUSIBLE_FRAME_MICROS = 1;

    private static final long NANOS_PER_MICRO = 1_000L;
    private static final double MICROS_PER_SECOND = 1_000_000.0;
    private static final double MICROS_PER_MILLI = 1_000.0;

    private final int[] micros;
    /** Reused sort scratch: the percentile pass must not allocate either. */
    private final int[] scratch;
    private final int warmupFrames;

    private int idx;
    private int count;
    private long sumMicros;

    private long lastFrameNanos;
    private boolean primed;

    private int goodFrames;
    private int discarded;

    private boolean distributionDirty = true;
    private int sortedCount;

    public FrameTimeTracker(int capacity) {
        this(capacity, DEFAULT_WARMUP_FRAMES);
    }

    public FrameTimeTracker(int capacity, int warmupFrames) {
        int size = Math.max(1, capacity);
        this.micros = new int[size];
        this.scratch = new int[size];
        this.warmupFrames = Math.max(0, warmupFrames);
    }

    /**
     * Records one frame boundary.
     *
     * @param nowNanos a monotonic timestamp, normally {@code System.nanoTime()}
     */
    public void frame(long nowNanos) {
        if (!primed) {
            // The first boundary after a reset has no interval behind it.
            lastFrameNanos = nowNanos;
            primed = true;
            return;
        }
        long deltaNanos = nowNanos - lastFrameNanos;
        lastFrameNanos = nowNanos;
        if (deltaNanos <= 0L) {
            // A backwards or stalled clock. Not evidence of anything.
            discarded++;
            return;
        }
        long deltaMicros = deltaNanos / NANOS_PER_MICRO;
        if (deltaMicros < MIN_PLAUSIBLE_FRAME_MICROS || deltaMicros > MAX_PLAUSIBLE_FRAME_MICROS) {
            discarded++;
            return;
        }
        record((int) deltaMicros);
    }

    /** Records an already-measured frame duration. Exposed for tests. */
    public void recordMicros(int frameMicros) {
        if (frameMicros < MIN_PLAUSIBLE_FRAME_MICROS
                || frameMicros > MAX_PLAUSIBLE_FRAME_MICROS) {
            discarded++;
            return;
        }
        primed = true;
        record(frameMicros);
    }

    private void record(int frameMicros) {
        if (count == micros.length) {
            sumMicros -= micros[idx];
        } else {
            count++;
        }
        micros[idx] = frameMicros;
        sumMicros += frameMicros;
        idx = idx + 1 == micros.length ? 0 : idx + 1;
        if (goodFrames < Integer.MAX_VALUE) goodFrames++;
        distributionDirty = true;
    }

    /**
     * Declares that whatever happened since the last frame boundary was not a
     * measurement: the in-flight interval is dropped and the warm-up restarts.
     *
     * <p>Recorded history is deliberately kept. The point is to stop adaptive
     * systems acting on the gap, not to throw away a window that was valid
     * right up until the window lost focus.
     */
    public void invalidate() {
        primed = false;
        goodFrames = 0;
    }

    /** Forgets every sample and restarts the warm-up. */
    public void reset() {
        idx = 0;
        count = 0;
        sumMicros = 0L;
        primed = false;
        goodFrames = 0;
        discarded = 0;
        sortedCount = 0;
        distributionDirty = true;
        Arrays.fill(micros, 0);
    }

    /**
     * Whether enough consecutive measurable frames have been seen for the
     * numbers here to be worth acting on. Adaptive systems must not change
     * anything while this is false.
     */
    public boolean warm() {
        return count > 0 && goodFrames >= warmupFrames;
    }

    /** How many frames are in the window. */
    public int sampleCount() {
        return count;
    }

    /** Consecutive measurable frames since the last reset or invalidate. */
    public int consecutiveGoodFrames() {
        return goodFrames;
    }

    /** Frames rejected as implausible (stalls, clock anomalies, load gaps). */
    public int discardedCount() {
        return discarded;
    }

    /** Mean frame time over the window, in milliseconds. 0 when empty. */
    public double averageFrameTimeMs() {
        if (count == 0) return 0.0;
        return (sumMicros / (double) count) / MICROS_PER_MILLI;
    }

    /** Average FPS over the window, derived from the mean frame time. */
    public double averageFps() {
        if (count == 0 || sumMicros <= 0L) return 0.0;
        return MICROS_PER_SECOND / (sumMicros / (double) count);
    }

    /** Median frame time, in milliseconds. 0 when empty. */
    public double medianFrameTimeMs() {
        return percentileFrameTimeMs(50.0);
    }

    /**
     * The frame time at a percentile of the window, in milliseconds - so p95 is
     * the frame time only 5% of frames exceeded. Higher is worse.
     *
     * @param percentile 0..100; clamped
     */
    public double percentileFrameTimeMs(double percentile) {
        if (count == 0) return 0.0;
        refreshDistribution();
        double p = clamp(percentile, 0.0, 100.0);
        // Nearest-rank: the smallest value at or above the requested rank.
        int rank = (int) Math.ceil(p / 100.0 * sortedCount);
        int index = clampIndex(rank - 1);
        return scratch[index] / MICROS_PER_MILLI;
    }

    /**
     * The "1% low": the average frame rate across the slowest 1% of frames.
     * A large gap between this and {@link #averageFps()} is what a player
     * experiences as stutter even when the headline number looks fine.
     */
    public double onePercentLowFps() {
        return lowFps(0.01);
    }

    /** The "0.1% low": the same statistic over the slowest 0.1% of frames. */
    public double pointOnePercentLowFps() {
        return lowFps(0.001);
    }

    /**
     * Average FPS across the slowest {@code fraction} of the window. Always
     * covers at least one frame, so a short window still answers.
     */
    public double lowFps(double fraction) {
        if (count == 0) return 0.0;
        refreshDistribution();
        double bounded = Double.isFinite(fraction) ? clamp(fraction, 0.0, 1.0) : 0.0;
        int n = Math.max(1, (int) Math.ceil(sortedCount * bounded));
        // scratch is ascending, so the slowest frames are the tail.
        long worstSum = 0L;
        for (int i = sortedCount - n; i < sortedCount; i++) {
            worstSum += scratch[i];
        }
        if (worstSum <= 0L) return 0.0;
        return MICROS_PER_SECOND / (worstSum / (double) n);
    }

    /**
     * An immutable copy of every statistic, for a report, a log line or a
     * JSON/CSV row. This is the only method here that allocates, and it is
     * never called from the render path.
     */
    public Snapshot snapshot() {
        return new Snapshot(count, discarded, warm(),
                averageFps(), averageFrameTimeMs(), medianFrameTimeMs(),
                percentileFrameTimeMs(95.0), percentileFrameTimeMs(99.0),
                onePercentLowFps(), pointOnePercentLowFps());
    }

    /** Sorts the window at most once per new sample, and only on demand. */
    private void refreshDistribution() {
        if (!distributionDirty) return;
        System.arraycopy(micros, 0, scratch, 0, count);
        Arrays.sort(scratch, 0, count);
        sortedCount = count;
        distributionDirty = false;
    }

    private int clampIndex(int index) {
        if (index < 0) return 0;
        return index >= sortedCount ? sortedCount - 1 : index;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * One reading of the whole distribution. Frame times are milliseconds,
     * lows are frames per second.
     */
    public record Snapshot(int samples, int discarded, boolean warm,
                           double averageFps, double averageFrameTimeMs,
                           double medianFrameTimeMs, double p95FrameTimeMs,
                           double p99FrameTimeMs, double onePercentLowFps,
                           double pointOnePercentLowFps) {

        /** A single-line form for the log and the diagnostic command. */
        public String toCompactString() {
            return String.format(java.util.Locale.ROOT,
                    "samples=%d discarded=%d warm=%s avg=%.1ffps median=%.2fms "
                            + "p95=%.2fms p99=%.2fms 1%%low=%.1ffps 0.1%%low=%.1ffps",
                    samples, discarded, warm, averageFps, medianFrameTimeMs,
                    p95FrameTimeMs, p99FrameTimeMs, onePercentLowFps, pointOnePercentLowFps);
        }

        /** CSV row matching {@link #csvHeader()}. */
        public String toCsvRow() {
            return String.format(java.util.Locale.ROOT,
                    "%d,%d,%s,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f",
                    samples, discarded, warm, averageFps, averageFrameTimeMs,
                    medianFrameTimeMs, p95FrameTimeMs, p99FrameTimeMs,
                    onePercentLowFps, pointOnePercentLowFps);
        }

        /** Header for {@link #toCsvRow()}. */
        public static String csvHeader() {
            return "samples,discarded,warm,avg_fps,avg_frametime_ms,median_frametime_ms,"
                    + "p95_frametime_ms,p99_frametime_ms,one_percent_low_fps,"
                    + "point_one_percent_low_fps";
        }
    }
}
