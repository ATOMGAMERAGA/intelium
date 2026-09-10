package com.intelium.client;

import com.intelium.Capabilities;
import com.intelium.Capability;
import com.intelium.Intelium;
import com.intelium.RenderBackend;
import com.intelium.config.InteliumConfigIO;
import com.intelium.optimization.ChunkLoadingGovernor;
import com.intelium.optimization.ChunkLoadingMode;
import com.intelium.optimization.DeferDecision;
import com.intelium.optimization.DeferPolicy;
import com.intelium.optimization.OptimizationProfile;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import net.caffeinemc.mods.sodium.client.render.chunk.DeferMode;

/**
 * "Fast chunk loading" — overrides Sodium's chunk-build <em>defer mode</em> so
 * freshly meshed chunks become visible sooner.
 *
 * <p>Sodium defaults to {@link DeferMode#ALWAYS}: completed meshes wait for a
 * frame with room to upload them. That is the <em>best</em> setting for average
 * FPS and frame pacing and the worst for how quickly chunks appear, which is
 * why Intelium does not simply override it everywhere any more. Which deferral
 * applies is now decided by {@link DeferPolicy} from the user's profile and the
 * backend, so "Max FPS" defers conservatively and "Smooth" does not - see that
 * class for why the old behaviour contradicted its own labels.
 *
 * <p>The setting is read by Sodium's render-section manager every frame, so the
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
        if (!worldLoaded) {
            // A fresh world gets the normal path; never carry a protected
            // upload state over from a previous server/session.
            GOVERNOR.reset();
        } else {
            GOVERNOR.update(configuredMode(cfg), configuredProfile(cfg),
                    Intelium.DETECTED_BACKEND, fps, cfg.adaptiveFpsTarget, measurable);
        }
        applyGuarded();
    }

    private static ChunkLoadingMode configuredMode(com.intelium.config.InteliumConfig cfg) {
        return Intelium.IS_ENABLED && Intelium.IS_COMPATIBLE
                ? ChunkLoadingMode.fromKey(cfg.chunkLoadingMode)
                : ChunkLoadingMode.OFF;
    }

    private static OptimizationProfile configuredProfile(com.intelium.config.InteliumConfig cfg) {
        return OptimizationProfile.fromKey(cfg.profile);
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
            if (!Capabilities.available(Capability.DEFER_TUNING)) {
                // Reached Sodium's options without throwing: the feature works.
                Capabilities.set(Capability.DEFER_TUNING, true, null);
            }
        } catch (Throwable t) {
            available = false;
            Capabilities.disable(Capability.DEFER_TUNING,
                    "Sodium's chunk defer mode is not reachable on this Sodium build");
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

        var cfg = InteliumConfigIO.get();
        ChunkLoadingMode mode = configuredMode(cfg);
        OptimizationProfile profile = configuredProfile(cfg);
        RenderBackend backend = Intelium.DETECTED_BACKEND;

        // Everything except governed Balanced-on-OpenGL has a fixed decision,
        // so the governor is dropped rather than left holding state that would
        // be applied the moment the user switched back.
        if (!DeferPolicy.governed(mode, profile, backend)) {
            GOVERNOR.reset();
        }

        DeferDecision decision =
                DeferPolicy.decide(mode, profile, backend, GOVERNOR.isThrottled());

        // The captured original is persisted in intelium.json (not a static):
        // Sodium saves the overridden value into its own config file, so without
        // persistence the user's real defer mode would be lost across a restart.
        var cap = cfg.captured;

        if (decision == DeferDecision.KEEP_USER) {
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
        setIfChanged(opts, toSodium(decision, opts.performance.chunkBuildDeferMode));
    }

    /**
     * Maps Intelium's backend-independent decision onto Sodium's own enum. The
     * fallback keeps whatever Sodium already had, so an enum constant that a
     * future Sodium removed cannot leave the setting in a guessed state.
     */
    private static DeferMode toSodium(DeferDecision decision, DeferMode current) {
        return switch (decision) {
            case ALWAYS -> DeferMode.ALWAYS;
            case ONE_FRAME -> DeferMode.ONE_FRAME;
            case ZERO_FRAMES -> DeferMode.ZERO_FRAMES;
            case KEEP_USER -> current;
        };
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
