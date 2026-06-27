package com.intelium.client;

import com.intelium.Intelium;
import com.intelium.config.InteliumConfigIO;
import com.intelium.optimization.ChunkLoadingMode;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import net.caffeinemc.mods.sodium.client.render.chunk.DeferMode;

/**
 * "Fast chunk loading" — overrides Sodium's chunk-build <em>defer mode</em> so
 * freshly meshed chunks become visible sooner.
 *
 * <p>Sodium defaults to {@link DeferMode#ALWAYS} (most deferred / smoothest but
 * slowest to appear). Intelium turns that latency down to one frame
 * ({@link ChunkLoadingMode#FAST}) or zero frames ({@link ChunkLoadingMode#TURBO}).
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
    /** The user's original Sodium defer mode, captured before we change it. */
    private static DeferMode original;

    /**
     * Reconciles Sodium's defer mode with the configured chunk-loading mode.
     * Safe to call every tick; only writes when the value actually changes.
     */
    public static synchronized void apply() {
        if (!available) return;
        try {
            applyUnsafe();
        } catch (Throwable t) {
            available = false;
            Intelium.LOGGER.warn("Intelium: Sodium's chunk defer mode isn't reachable on this "
                    + "Sodium build - fast chunk loading disabled (no crash).", t);
        }
    }

    private static void applyUnsafe() {
        SodiumOptions opts = SodiumClientMod.options();
        if (opts == null || opts.performance == null) return;

        ChunkLoadingMode mode = Intelium.IS_ENABLED && Intelium.IS_COMPATIBLE
                ? ChunkLoadingMode.fromKey(InteliumConfigIO.get().chunkLoadingMode)
                : ChunkLoadingMode.OFF;

        if (mode == ChunkLoadingMode.OFF) {
            // Restore the user's setting if we previously changed it.
            if (original != null) {
                setIfChanged(opts, original);
                original = null;
            }
            return;
        }

        if (original == null) original = opts.performance.chunkBuildDeferMode;
        setIfChanged(opts, mode == ChunkLoadingMode.TURBO
                ? DeferMode.ZERO_FRAMES
                : DeferMode.ONE_FRAME);
    }

    private static void setIfChanged(SodiumOptions opts, DeferMode value) {
        if (opts.performance.chunkBuildDeferMode != value) {
            opts.performance.chunkBuildDeferMode = value;
        }
    }
}
