package com.intelium.optimization;

/**
 * A per-frame allowance of "things I am allowed to draw".
 *
 * <h2>How the frame boundary is found</h2>
 *
 * <p>Preferably, it is told. {@link #beginFrame()} is driven by a verified
 * once-per-frame hook ({@code Minecraft.renderFrame(Z)V} on 26.x), and from the
 * first call onwards this budget is exact and costs nothing: the consume path
 * is an increment and a compare, with no clock read at all.
 *
 * <h2>The fallback, and why it is still here</h2>
 *
 * <p>If that hook could not be applied - a future render loop, another mod, a
 * client shipping its own Minecraft build - the budget falls back to inferring
 * boundaries from timing, which is what it did before the hook existed. The
 * consumers of this budget all run back to back inside one render pass, so a
 * pause longer than {@link #DEFAULT_FRAME_GAP_NANOS} is a frame boundary.
 * In that mode, and only in that mode, the consume path reads the clock.
 *
 * <p>The two ways the fallback can misread a boundary are not equally bad.
 * Splitting one frame in two costs nothing - that frame draws up to twice its
 * allowance. <em>Missing</em> a boundary is the dangerous one: the count never
 * resets and every block entity is refused from then on, so chests and signs
 * stop drawing entirely. So the threshold sits far below the smallest
 * plausible frame (0.5 ms - fine past 1000 FPS) rather than near it, and a
 * second, absolute cap on how long one accounting window may stay open
 * guarantees the count cannot get stuck even if the first test never fires.
 * Both safety nets fail in the harmless direction, and a clock that jumps
 * backwards also counts as a boundary.
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

    /**
     * Set the first time a real frame boundary is announced. From then on the
     * timing heuristic is switched off permanently: a hook that fired once will
     * keep firing, and mixing the two could roll the window twice per frame.
     */
    private boolean explicitBoundaries;

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
     * Announces a real frame boundary. Called from the per-frame hook; the
     * first call switches this budget from inferring boundaries to being told
     * them.
     */
    public void beginFrame() {
        explicitBoundaries = true;
        rollOver();
    }

    /** Whether boundaries are being announced rather than inferred. */
    public boolean hasExplicitBoundaries() {
        return explicitBoundaries;
    }

    /**
     * Claims one slot of this frame's budget.
     *
     * <p>With explicit boundaries this reads no clock. Without them it reads
     * {@code System.nanoTime()} itself, so the caller never pays for a
     * timestamp the budget may not need.
     *
     * @param limit how many slots this frame has; {@code <= 0} means unlimited
     * @return true if the caller may draw
     */
    public boolean tryConsume(int limit) {
        if (!explicitBoundaries) {
            return tryConsume(System.nanoTime(), limit);
        }
        return consume(limit);
    }

    /**
     * Claims one slot using a caller-supplied timestamp. Retained for the
     * inferred-boundary path and for deterministic tests.
     *
     * @param nowNanos a monotonic timestamp, normally {@code System.nanoTime()}
     * @param limit    how many slots this frame has; {@code <= 0} means unlimited
     */
    public boolean tryConsume(long nowNanos, int limit) {
        if (!explicitBoundaries) {
            if (!started
                    || nowNanos < lastCallNanos
                    || nowNanos - lastCallNanos > newFrameGapNanos
                    || nowNanos - windowStartNanos > maxWindowNanos) {
                rollOver();
                windowStartNanos = nowNanos;
            }
            lastCallNanos = nowNanos;
            started = true;
        }
        return consume(limit);
    }

    /**
     * Records a draw that is happening whatever the budget says - a block
     * entity close enough that refusing it would be visible - so the accounting
     * still reflects what the frame actually drew.
     */
    public void consumeExempt() {
        if (used < Integer.MAX_VALUE) used++;
    }

    private boolean consume(int limit) {
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
        explicitBoundaries = false;
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
