package com.intelium.client;

import com.intelium.Intelium;
import com.intelium.config.InteliumConfigIO;
import com.intelium.optimization.ChunkLoadingGovernor;
import com.intelium.optimization.ChunkLoadingMode;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import net.caffeinemc.mods.sodium.client.render.chunk.DeferMode;

/**
 * "Fast chunk loading" — overrides Sodium's chunk-build <em>defer mode</em> so
 * freshly meshed chunks become visible sooner.
 *
 * <p>Sodium defaults to {@link DeferMode#ALWAYS} (most deferred / smoothest but
 * slowest to appear). Intelium normally turns that latency down to one frame
 * ({@link ChunkLoadingMode#FAST}) or zero frames ({@link ChunkLoadingMode#TURBO});
 * Fast can temporarily defer more when the OpenGL governor sees sustained FPS
 * pressure, while Turbo is never adaptively changed.
 * The setting is read by Sodium's render-section manager every frame, so the
 * change takes effect immediately - no reload needed - though Intelium asks for
 * one anyway when the option is toggled so the difference is visible at once.
 *
 * <p>This is the only place that touches Sodium's <em>internal</em> (non-API)
 * classes, so every access is wrapped: if a future Sodium renames or moves them,
 * the feature self-disables (logged once) instead of crashing, matching the rest
 * of Intelium's "never crash on a Sodium change" philosophy.
 */
public final class ChunkLoadingBooster {

    private ChunkLoadingBooster() {}

    /** Set false the first time Sodium's internals can't be reached. */
    private static volatile boolean available = true;

    /** OpenGL-only, hysteresis-protected upload pacing. */
    private static final ChunkLoadingGovernor GOVERNOR = new ChunkLoadingGovernor();

    /**
     * Feeds the OpenGL pacing governor, then reconciles Sodium's live setting.
     * Unmeasurable FPS samples hold the current decision without moving either
     * transition window; leaving a world resets it so protection cannot leak
     * into the next server/session.
     */
    public static synchronized void tick(int fps, boolean measurable, boolean worldLoaded) {
        var cfg = InteliumConfigIO.get();
        ChunkLoadingMode mode = Intelium.IS_ENABLED && Intelium.IS_COMPATIBLE
                ? ChunkLoadingMode.fromKey(cfg.chunkLoadingMode)
                : ChunkLoadingMode.OFF;
        if (!worldLoaded) {
            // A fresh world gets the normal fast path; never carry a protected
            // upload state over from a previous server/session.
            GOVERNOR.reset();
        } else {
            GOVERNOR.update(mode, Intelium.DETECTED_BACKEND, fps,
                    cfg.adaptiveFpsTarget, measurable);
        }
        applyGuarded();
    }

    /**
     * Reconciles Sodium's defer mode with the configured chunk-loading mode.
     * Safe to call every tick; only writes when the value actually changes.
     */
    public static synchronized void apply() {
        applyGuarded();
    }

    private static void applyGuarded() {
        if (!available) return;
        try {
            applyUnsafe();
        } catch (Throwable t) {
            available = false;
            Intelium.LOGGER.warn("Intelium: Sodium's chunk defer mode isn't reachable on this "
                    + "Sodium build - fast chunk loading disabled (no crash).", t);
            // With the internals unreachable, the restore path above can never
            // run again either. If a capture is pending, Sodium may be left on
            // the forced value; say so instead of silently stranding it, and
            // drop the orphaned capture so it can't be mistaken for live state.
            var cap = InteliumConfigIO.get().captured;
            if (cap.sodiumDeferMode != null) {
                Intelium.LOGGER.warn("Intelium: could not restore Sodium's original chunk defer "
                        + "mode ({}); check Sodium's video settings if chunk loading feels "
                        + "different.", cap.sodiumDeferMode);
                cap.sodiumDeferMode = null;
                InteliumConfigIO.flush();
            }
        }
    }

    private static void applyUnsafe() {
        SodiumOptions opts = SodiumClientMod.options();
        if (opts == null || opts.performance == null) return;

        ChunkLoadingMode mode = Intelium.IS_ENABLED && Intelium.IS_COMPATIBLE
                ? ChunkLoadingMode.fromKey(InteliumConfigIO.get().chunkLoadingMode)
                : ChunkLoadingMode.OFF;

        if (mode != ChunkLoadingMode.FAST
                || Intelium.DETECTED_BACKEND != com.intelium.RenderBackend.OPENGL) {
            GOVERNOR.reset();
        }

        // The captured original is persisted in intelium.json (not a static):
        // Sodium saves the overridden value into its own config file, so without
        // persistence the user's real defer mode would be lost across a restart.
        var cap = InteliumConfigIO.get().captured;

        if (mode == ChunkLoadingMode.OFF) {
            // Restore the user's setting if we previously changed it.
            if (cap.sodiumDeferMode != null) {
                setIfChanged(opts, parseDeferMode(cap.sodiumDeferMode,
                        opts.performance.chunkBuildDeferMode));
                cap.sodiumDeferMode = null;
                InteliumConfigIO.flush();
            }
            return;
        }

        if (cap.sodiumDeferMode == null) {
            cap.sodiumDeferMode = opts.performance.chunkBuildDeferMode.name();
            InteliumConfigIO.flush();
        }
        DeferMode desired;
        if (mode == ChunkLoadingMode.TURBO) {
            desired = DeferMode.ZERO_FRAMES;
        } else {
            // Normal OpenGL Fast uses one frame. Sustained FPS pressure falls
            // back to Sodium's conservative queue until the hysteresis window
            // confirms recovery, preventing chunk uploads from amplifying a
            // struggling frame. Vulkan never enters this branch's governor.
            desired = GOVERNOR.isThrottled() ? DeferMode.ALWAYS : DeferMode.ONE_FRAME;
        }
        setIfChanged(opts, desired);
    }

    private static DeferMode parseDeferMode(String name, DeferMode fallback) {
        try {
            return DeferMode.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }

    private static void setIfChanged(SodiumOptions opts, DeferMode value) {
        if (opts.performance.chunkBuildDeferMode != value) {
            opts.performance.chunkBuildDeferMode = value;
        }
    }
}
