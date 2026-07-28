package com.intelium.optimization;

/**
 * Adaptive render distance: steps the render distance down when the measured
 * FPS stays below the user's target, and back up when there is comfortable
 * headroom. This is the single most reliable FPS lever there is (fewer chunk
 * sections to build, upload and draw every frame), applied only when it is
 * actually needed - so the world stays as large as the machine can afford
 * <em>right now</em>.
 *
 * <p>Design (the same shape proven by the DynamicRenderDistance family of
 * mods, tuned for iGPUs):
 * <ul>
 *   <li><b>Hysteresis</b> - stepping down requires sustained FPS below
 *       ~92% of the target; stepping back up requires sustained FPS above
 *       ~115%. The dead band between the two prevents oscillation.</li>
 *   <li><b>Hold time</b> - a short dip (a GC pause, a chunk-build burst) never
 *       triggers a step: the FPS must stay on the wrong side of the threshold
 *       for a full hold window. Stepping up is much slower than stepping down,
 *       because raising the distance itself costs a burst of chunk builds.</li>
 *   <li><b>Gentle steps</b> - one chunk at a time, so each change is a small,
 *       cheap rebuild instead of a hitch.</li>
 *   <li><b>Fast reaction when it really hurts</b> - when the FPS falls far
 *       below the target (under ~60% of it: a jungle village, a mob farm, a
 *       redstone burst), waiting the full hold window one chunk at a time
 *       leaves the game slideshow-ing for many seconds. In that severe band
 *       the hold window is halved and each step drops two chunks, so playable
 *       frame rates come back roughly 4x sooner. The floor still applies, and
 *       stepping back up stays slow and single-step.</li>
 *   <li><b>Floor</b> - never reduces below half the user's own distance (and
 *       never below the vanilla minimum), so the world cannot collapse.</li>
 *   <li><b>Settle window</b> - every render-distance change makes the game
 *       rebuild its chunk graph, and the resulting chunk-build burst reads as
 *       "low FPS". After each upward step the controller therefore ignores a
 *       few seconds of samples, so its own step is never mistaken for a
 *       performance problem.</li>
 *   <li><b>Recovery backoff</b> - if a step back up has to be undone shortly
 *       afterwards (the machine could not actually afford the extra chunk),
 *       the wait before the next attempt doubles, up to 8x. Without this the
 *       controller ping-pongs up and down forever, and every swing is a
 *       visible "the mod is loading all chunks again" reload. A recovery that
 *       sticks halves the backoff again, so a machine that genuinely got
 *       faster is not punished for one earlier failure.</li>
 * </ul>
 *
 * <p>The controller is fed once per client tick (20 Hz) with the smoothed FPS
 * from the rolling tracker. Pure logic with no Minecraft dependency, so it is
 * exhaustively unit-tested. The reduction is deliberately <em>not</em>
 * persisted: a fresh launch starts unreduced and re-measures.
 */
public final class AdaptiveDistanceController {

    /** Vanilla's minimum render distance, in chunks. */
    public static final int MIN_DISTANCE = 2;

    /** Step down when smoothed FPS stays below target * this factor. */
    static final double LOW_FACTOR = 0.92;
    /** Step back up when smoothed FPS stays above target * this factor. */
    static final double HIGH_FACTOR = 1.15;
    /** Ticks (20/s) the FPS must stay low before each downward step (~2s). */
    static final int DOWN_HOLD_TICKS = 40;
    /** Ticks the FPS must stay high before each upward step (~10s). */
    static final int UP_HOLD_TICKS = 200;
    /** Below target * this factor the situation is severe: react fast. */
    static final double SEVERE_FACTOR = 0.60;
    /** Halved hold window in the severe band (~1s). */
    static final int SEVERE_HOLD_TICKS = DOWN_HOLD_TICKS / 2;
    /** Chunks shed per step in the severe band. */
    static final int SEVERE_STEP = 2;
    /**
     * Ticks of measurement ignored after each upward step (~3s): raising the
     * distance triggers a chunk-graph rebuild whose build burst reads as low
     * FPS, and counting it would immediately undo the step - the ping-pong
     * that makes the mod visibly re-load chunks over and over.
     */
    static final int SETTLE_TICKS = 60;
    /**
     * An upward step is considered <em>failed</em> when a downward step is
     * needed again within this window (~30s); surviving it marks success.
     */
    static final int PROBATION_TICKS = 600;
    /** Ceiling on the up-hold backoff multiplier (8x = ~80s between tries). */
    static final int MAX_UP_HOLD_MULTIPLIER = 8;

    /** Sentinel: no upward step is currently on probation. */
    private static final int NO_RECENT_UP_STEP = Integer.MAX_VALUE;

    private int lowTicks;
    private int highTicks;
    private int reduction;
    /** Countdown of post-up-step ticks during which samples are ignored. */
    private int settleTicks;
    /** Ticks since the last upward step; {@link #NO_RECENT_UP_STEP} = none. */
    private int ticksSinceUpStep = NO_RECENT_UP_STEP;
    /** Current multiplier on {@link #UP_HOLD_TICKS} (doubles per failure). */
    private int upHoldMultiplier = 1;

    /**
     * Feeds one tick of measurement and returns the current render-distance
     * cap in chunks, or {@code 0} when no reduction is active (leave the
     * user's distance alone).
     *
     * @param targetFps    the FPS the user wants to hold (>= 1)
     * @param smoothedFps  rolling-average FPS; non-positive samples are ignored
     * @param baseDistance the user's own render distance (the restore point)
     */
    public int update(int targetFps, int smoothedFps, int baseDistance) {
        if (targetFps < 1 || smoothedFps <= 0 || baseDistance <= MIN_DISTANCE) {
            // Nothing measurable, or no room to reduce: decay toward "hands off".
            lowTicks = 0;
            highTicks = 0;
            return currentCap(baseDistance);
        }

        int maxReduction = Math.max(0, baseDistance - floorFor(baseDistance));
        reduction = Math.min(reduction, maxReduction);

        if (ticksSinceUpStep != NO_RECENT_UP_STEP) {
            ticksSinceUpStep++;
            if (ticksSinceUpStep >= PROBATION_TICKS) {
                // The last upward step stuck: relax the backoff toward normal.
                upHoldMultiplier = Math.max(1, upHoldMultiplier / 2);
                ticksSinceUpStep = NO_RECENT_UP_STEP;
            }
        }

        if (settleTicks > 0) {
            // Our own upward step is still settling (chunk-graph rebuild in
            // flight); its FPS dip is self-inflicted, so don't measure it.
            settleTicks--;
            lowTicks = 0;
            highTicks = 0;
            return currentCap(baseDistance);
        }

        if (smoothedFps < targetFps * LOW_FACTOR) {
            highTicks = 0;
            boolean severe = smoothedFps < targetFps * SEVERE_FACTOR;
            if (++lowTicks >= (severe ? SEVERE_HOLD_TICKS : DOWN_HOLD_TICKS)) {
                lowTicks = 0;
                reduction = Math.min(maxReduction, reduction + (severe ? SEVERE_STEP : 1));
                if (ticksSinceUpStep != NO_RECENT_UP_STEP) {
                    // The recent upward step didn't hold: wait exponentially
                    // longer before trying again, instead of ping-ponging (and
                    // visibly re-loading chunks) every few seconds.
                    upHoldMultiplier = Math.min(MAX_UP_HOLD_MULTIPLIER, upHoldMultiplier * 2);
                    ticksSinceUpStep = NO_RECENT_UP_STEP;
                }
            }
        } else if (smoothedFps > targetFps * HIGH_FACTOR) {
            lowTicks = 0;
            if (++highTicks >= UP_HOLD_TICKS * upHoldMultiplier) {
                highTicks = 0;
                if (reduction > 0) {
                    reduction--;
                    ticksSinceUpStep = 0;
                    settleTicks = SETTLE_TICKS;
                }
            }
        } else {
            // Inside the dead band: hold steady.
            lowTicks = 0;
            highTicks = 0;
        }
        return currentCap(baseDistance);
    }

    /** Current backoff multiplier on the up-hold window (1 = no backoff). */
    int upHoldMultiplier() {
        return upHoldMultiplier;
    }

    /** The active cap for the given base distance; 0 when not reducing. */
    public int currentCap(int baseDistance) {
        if (reduction <= 0 || baseDistance <= MIN_DISTANCE) return 0;
        return Math.max(floorFor(baseDistance), baseDistance - reduction);
    }

    /** Chunks currently shaved off the user's distance (0 = inactive). */
    public int reduction() {
        return reduction;
    }

    /** Forgets all measurement state (world change, feature toggled off). */
    public void reset() {
        lowTicks = 0;
        highTicks = 0;
        reduction = 0;
        settleTicks = 0;
        ticksSinceUpStep = NO_RECENT_UP_STEP;
        upHoldMultiplier = 1;
    }

    /** Never reduce below half the user's distance, or the vanilla minimum. */
    static int floorFor(int baseDistance) {
        return Math.max(MIN_DISTANCE, baseDistance / 2);
    }
}
