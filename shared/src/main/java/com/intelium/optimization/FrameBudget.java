package com.intelium.optimization;

/**
 * A per-frame allowance of "things I am allowed to draw", with no dependency on
 * anything that knows when a frame begins.
 *
 * <h2>How the frame boundary is found</h2>
 *
 * <p>The consumers of this budget (block-entity renders) all happen back to
 * back inside one render pass - consecutive calls are microseconds apart -
 * while the gap between one frame's pass and the next is milliseconds. So a
 * pause longer than {@link #newFrameGapNanos} is a frame boundary, and the
 * counter resets. That keeps the budget honest without hooking a frame-start
 * event in a Minecraft version whose render loop keeps being rewritten.
 *
 * <p>A clock that jumps backwards also counts as a boundary, so a nanoTime
 * anomaly can only ever cost one frame's accounting.
 *
 * <p>Not thread-safe by design: only ever touched from the render thread.
 */
public final class FrameBudget {

    /** Default boundary: 2 ms of silence means a new frame started. */
    public static final long DEFAULT_FRAME_GAP_NANOS = 2_000_000L;

    private final long newFrameGapNanos;

    private long lastCallNanos;
    private boolean started;

    private int used;
    private int skipped;
    private int lastFrameUsed;
    private int lastFrameSkipped;

    public FrameBudget() {
        this(DEFAULT_FRAME_GAP_NANOS);
    }

    public FrameBudget(long newFrameGapNanos) {
        this.newFrameGapNanos = Math.max(1L, newFrameGapNanos);
    }

    /**
     * Claims one slot of this frame's budget.
     *
     * @param nowNanos a monotonic timestamp, normally {@code System.nanoTime()}
     * @param limit    how many slots this frame has; {@code <= 0} means unlimited
     * @return true if the caller may draw
     */
    public boolean tryConsume(long nowNanos, int limit) {
        if (!started || nowNanos < lastCallNanos || nowNanos - lastCallNanos > newFrameGapNanos) {
            rollOver();
        }
        lastCallNanos = nowNanos;
        started = true;

        if (limit <= 0 || used < limit) {
            used++;
            return true;
        }
        skipped++;
        return false;
    }

    /** Forgets the current and previous frame's accounting. */
    public void reset() {
        started = false;
        lastCallNanos = 0L;
        used = 0;
        skipped = 0;
        lastFrameUsed = 0;
        lastFrameSkipped = 0;
    }

    /** How many slots the previous complete frame used. */
    public int lastFrameUsed() {
        return lastFrameUsed;
    }

    /** How many draws the previous complete frame skipped. */
    public int lastFrameSkipped() {
        return lastFrameSkipped;
    }

    private void rollOver() {
        lastFrameUsed = used;
        lastFrameSkipped = skipped;
        used = 0;
        skipped = 0;
    }
}
