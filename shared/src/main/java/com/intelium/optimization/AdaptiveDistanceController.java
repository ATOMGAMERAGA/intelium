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
 *   <li><b>Floor</b> - never reduces below half the user's own distance (and
 *       never below the vanilla minimum), so the world cannot collapse.</li>
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

    private int lowTicks;
    private int highTicks;
    private int reduction;

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

        if (smoothedFps < targetFps * LOW_FACTOR) {
            highTicks = 0;
            if (++lowTicks >= DOWN_HOLD_TICKS) {
                lowTicks = 0;
                if (reduction < maxReduction) reduction++;
            }
        } else if (smoothedFps > targetFps * HIGH_FACTOR) {
            lowTicks = 0;
            if (++highTicks >= UP_HOLD_TICKS) {
                highTicks = 0;
                if (reduction > 0) reduction--;
            }
        } else {
            // Inside the dead band: hold steady.
            lowTicks = 0;
            highTicks = 0;
        }
        return currentCap(baseDistance);
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
    }

    /** Never reduce below half the user's distance, or the vanilla minimum. */
    static int floorFor(int baseDistance) {
        return Math.max(MIN_DISTANCE, baseDistance / 2);
    }
}
