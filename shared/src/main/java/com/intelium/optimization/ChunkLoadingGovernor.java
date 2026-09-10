package com.intelium.optimization;

import com.intelium.RenderBackend;

/**
 * Protects OpenGL frame pacing while chunks are streaming.
 *
 * <p>On Sodium's OpenGL path, completed chunk meshes are uploaded from the
 * render thread. {@link ChunkLoadingMode#FAST} normally keeps the useful
 * one-frame defer, but forcing uploads through a sustained GPU/driver-bound
 * stretch can turn fast chunk appearance into repeated frame-time spikes. This
 * governor temporarily asks the client glue to use Sodium's conservative defer
 * mode, then restores the one-frame path only after FPS has genuinely recovered.
 *
 * <p>Only the <b>Balanced</b> profile is steered here. Max FPS defers
 * conservatively at all times and Smooth never defers beyond one frame, so
 * neither has anything for a governor to decide - see {@link DeferPolicy}.
 *
 * <p>The state machine deliberately has asymmetric hold windows: it reacts in
 * about one second (or faster during a severe collapse), but needs three stable
 * seconds to recover. That hysteresis prevents the defer mode from bouncing
 * between values around the target. Vulkan is left alone because its submission
 * and upload path has different scheduling characteristics, and Turbo remains
 * an explicit user request for minimum latency.
 *
 * <p>Pure state and arithmetic, with no Minecraft or Sodium dependency.
 */
public final class ChunkLoadingGovernor {

    /** Enter protection below 85% of the configured FPS target. */
    static final double LOW_RATIO = 0.85;
    /** A severe collapse gets a shorter activation window. */
    static final double SEVERE_RATIO = 0.60;
    /** Leave protection only once FPS is effectively back at target. */
    static final double RECOVERY_RATIO = 0.97;

    /** Roughly one second at the 20 Hz client tick rate. */
    static final int LOW_HOLD_TICKS = 20;
    /** Roughly 0.3 seconds: enough to reject a single bad FPS sample. */
    static final int SEVERE_HOLD_TICKS = 6;
    /** Roughly three seconds, preventing upload-mode oscillation. */
    static final int RECOVERY_HOLD_TICKS = 60;

    private int lowTicks;
    private int severeTicks;
    private int recoveryTicks;
    private boolean throttled;

    /**
     * Feeds one client-tick FPS sample and returns whether chunk uploads should
     * use the conservative defer mode.
     *
     * <p>Back-compat form: assumes the governed {@link OptimizationProfile#BALANCED}
     * profile.
     *
     * @param mode       configured chunk-loading mode
     * @param backend    selected graphics backend
     * @param fps        current game FPS reading
     * @param targetFps  user's adaptive FPS target
     * @param measurable false for background/menu-capped/benchmark samples;
     *                   the current decision is held while counters are paused
     */
    public boolean update(ChunkLoadingMode mode, RenderBackend backend,
                          int fps, int targetFps, boolean measurable) {
        return update(mode, OptimizationProfile.BALANCED, backend, fps, targetFps, measurable);
    }

    /**
     * Profile-aware form. Only the combination {@link DeferPolicy#governed}
     * accepts is steered here; every other profile has a fixed decision, so the
     * governor is reset rather than left holding state it will never apply.
     *
     * @param mode       configured chunk-loading mode
     * @param profile    configured optimization profile
     * @param backend    selected graphics backend
     * @param fps        current game FPS reading
     * @param targetFps  user's adaptive FPS target
     * @param measurable false for background/menu-capped/benchmark samples;
     *                   the current decision is held while counters are paused
     */
    public boolean update(ChunkLoadingMode mode, OptimizationProfile profile,
                          RenderBackend backend, int fps, int targetFps, boolean measurable) {
        if (!DeferPolicy.governed(mode, profile, backend)) {
            reset();
            return false;
        }

        if (!measurable || fps <= 0 || targetFps <= 0) {
            clearPendingTransition();
            return throttled;
        }

        double ratio = (double) fps / targetFps;
        if (throttled) {
            lowTicks = 0;
            severeTicks = 0;
            if (ratio >= RECOVERY_RATIO) {
                if (++recoveryTicks >= RECOVERY_HOLD_TICKS) {
                    throttled = false;
                    recoveryTicks = 0;
                }
            } else {
                recoveryTicks = 0;
            }
            return throttled;
        }

        recoveryTicks = 0;
        lowTicks = ratio < LOW_RATIO ? lowTicks + 1 : 0;
        severeTicks = ratio < SEVERE_RATIO ? severeTicks + 1 : 0;
        if (lowTicks >= LOW_HOLD_TICKS || severeTicks >= SEVERE_HOLD_TICKS) {
            throttled = true;
            lowTicks = 0;
            severeTicks = 0;
        }
        return throttled;
    }

    /** Current decision, used by the Sodium-facing client glue. */
    public boolean isThrottled() {
        return throttled;
    }

    /** Drops all history and returns to the normal one-frame FAST path. */
    public void reset() {
        throttled = false;
        clearPendingTransition();
    }

    private void clearPendingTransition() {
        lowTicks = 0;
        severeTicks = 0;
        recoveryTicks = 0;
    }
}
