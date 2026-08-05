package com.intelium.optimization;

/**
 * A per-frame allowance of "things I am allowed to draw", with no dependency on
 * anything that knows when a frame begins.
 *
 * <h2>How the frame boundary is found</h2>
 *
 * <p>The consumers of this budget (block-entity renders) all happen back to
 * back inside one render pass - consecutive calls are microseconds apart -
 * while the gap between one frame's pass and the next is the whole rest of the
 * frame. So a pause longer than {@link #newFrameGapNanos} is a frame boundary,
 * and the counter resets. That keeps the budget honest without hooking a
 * frame-start event in a Minecraft version whose render loop keeps being
 * rewritten.
 *
 * <h2>Why the threshold is small, and why there is a second one</h2>
 *
 * <p>The two ways this can misread a boundary are not equally bad. Splitting one
 * frame in two costs nothing - that frame simply draws up to twice its
 * allowance. <em>Missing</em> a boundary is the dangerous one: the count never
 * resets, and once it passes the limit every block entity is refused from then
 * on, so chests and signs stop drawing entirely. So the threshold sits far below
 * the smallest plausible frame (0.5 ms - fine past 1000 FPS) rather than near
 * it, and a second, absolute cap on how long one accounting window may stay open
 * guarantees the count cannot get stuck even if the first test somehow never
 * fires. Both safety nets fail in the harmless direction.
 *
 * <p>A clock that jumps backwards also counts as a boundary, so a nanoTime
 * anomaly can only ever cost one frame's accounting.
 *
 * <p>Not thread-safe by design: only ever touched from the render thread.
 */
public final class FrameBudget {

    /** Default boundary: 0.5 ms of silence means a new frame started. */
    public static final long DEFAULT_FRAME_GAP_NANOS = 500_000L;

    /**
     * Longest an accounting window may stay open before it is force-rolled,
     * whatever the gaps look like. 20 ms is longer than any single frame's
     * block-entity pass and shorter than a blink, so this only ever fires when
     * the gap test has failed to.
     */
    private static final long DEFAULT_MAX_WINDOW_NANOS = 20_000_000L;

    private final long newFrameGapNanos;
    private final long maxWindowNanos;

    private long lastCallNanos;
    private long windowStartNanos;
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
        // Scales with a custom gap so a deliberately coarse budget is not
        // force-rolled out from under itself.
        this.maxWindowNanos = Math.max(DEFAULT_MAX_WINDOW_NANOS, this.newFrameGapNanos * 8L);
    }

    /**
     * Claims one slot of this frame's budget.
     *
     * @param nowNanos a monotonic timestamp, normally {@code System.nanoTime()}
     * @param limit    how many slots this frame has; {@code <= 0} means unlimited
     * @return true if the caller may draw
     */
    public boolean tryConsume(long nowNanos, int limit) {
        if (!started
                || nowNanos < lastCallNanos
                || nowNanos - lastCallNanos > newFrameGapNanos
                || nowNanos - windowStartNanos > maxWindowNanos) {
            rollOver();
            windowStartNanos = nowNanos;
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
        windowStartNanos = 0L;
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
