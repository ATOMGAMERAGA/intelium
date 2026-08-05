package com.intelium.optimization;

/**
 * Turns a stream of raw FPS samples into a single "how badly are we struggling
 * right now?" number in {@code [0, 1]}, which the render-budget systems use to
 * decide how hard to push.
 *
 * <p>An exponential moving average is used rather than a ring buffer: the
 * budgets are re-evaluated every client tick, so what matters is reacting
 * within a second or two without letting one bad frame swing anything. Pressure
 * is 0 while the frame rate holds its target and ramps to 1 as it falls to half
 * of it - beyond that it stays 1, because there is nothing further to say.
 *
 * <p>Pure logic, no Minecraft types. Not thread-safe; fed from the client tick.
 */
public final class FramePressure {

    /** EMA weight of each new sample. ~1s to settle at 20 samples/second. */
    private static final double ALPHA = 0.12;

    /** The frame rate is considered fully saturated at half the target. */
    private static final double FULL_PRESSURE_FRACTION = 0.5;

    private double average;
    private boolean primed;

    /** Feeds one FPS sample. Negative samples are treated as 0. */
    public void push(int fps) {
        double v = Math.max(0, fps);
        if (!primed) {
            average = v;
            primed = true;
        } else {
            average += ALPHA * (v - average);
        }
    }

    /** Forgets everything measured so far. */
    public void reset() {
        average = 0.0;
        primed = false;
    }

    /** The smoothed frame rate, or 0 before the first sample. */
    public double smoothedFps() {
        return primed ? average : 0.0;
    }

    /** Whether at least one sample has been fed. */
    public boolean primed() {
        return primed;
    }

    /**
     * How far short of {@code targetFps} the smoothed frame rate is falling, as
     * {@code 0} (at or above target) to {@code 1} (at or below half of it).
     * Returns 0 before the first sample, so nothing tightens during warm-up.
     */
    public double pressure(int targetFps) {
        if (!primed || targetFps <= 0) return 0.0;
        double deficit = targetFps - average;
        if (deficit <= 0.0) return 0.0;
        double span = targetFps * FULL_PRESSURE_FRACTION;
        return Math.min(1.0, deficit / span);
    }
}
