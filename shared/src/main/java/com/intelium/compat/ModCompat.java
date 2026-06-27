package com.intelium.compat;

import com.intelium.Intelium;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Detects other performance mods that Intelium must not fight.
 *
 * <p>Intelium deliberately stays narrow - it only tunes Sodium's chunk workers,
 * Sodium's defer mode, and a handful of vanilla {@code GameOptions}. It never
 * touches OpenGL/Vulkan buffers, particle containers, or the render pipeline
 * directly. That makes it inherently compatible with GPU-level mods. The one
 * real overlap is the vanilla particle setting, so this class lets the particle
 * lever step aside when a dedicated particle mod is present.
 *
 * <ul>
 *   <li><b>GPUTape / GPUBooster</b> - low-level OpenGL state management (DSA,
 *       buffer pooling, RBO depth, SIMD). No {@code GameOptions} or Sodium
 *       pipeline changes, so there is nothing to gate; detection is informational
 *       only.</li>
 *   <li><b>AsyncParticles</b> - moves particle tick/rendering onto worker threads
 *       and warns about fragile interactions with other particle-manipulation
 *       mods. Intelium therefore does <em>not</em> force the vanilla particle
 *       setting when AsyncParticles is installed; it lets AsyncParticles own
 *       particle performance.</li>
 * </ul>
 *
 * <p>Results are cached after the first lookup: the mod set is fixed once the
 * game has launched, so this is read every frame by {@code RenderTweaks} for free.
 */
public final class ModCompat {

    private ModCompat() {}

    private static volatile Boolean asyncParticles;
    private static volatile Boolean gpuTape;
    private static volatile boolean logged;

    /** True when AsyncParticles is loaded; Intelium yields particle control to it. */
    public static boolean asyncParticlesPresent() {
        Boolean v = asyncParticles;
        if (v == null) {
            v = isLoaded("asyncparticles");
            asyncParticles = v;
        }
        return v;
    }

    /** True when GPUTape / GPUBooster is loaded. Informational; no overlap to gate. */
    public static boolean gpuTapePresent() {
        Boolean v = gpuTape;
        if (v == null) {
            // The Modrinth slug is "gputape"; the mod id has shipped as both
            // "gputape" and "gpubooster" across releases, so check both.
            v = isLoaded("gputape") || isLoaded("gpubooster");
            gpuTape = v;
        }
        return v;
    }

    /** Logs detected companions exactly once, for diagnostics. */
    public static void logOnce() {
        if (logged) return;
        logged = true;
        if (asyncParticlesPresent()) {
            Intelium.LOGGER.info("Intelium: AsyncParticles detected - Intelium will not override "
                    + "the vanilla particle setting, leaving particles to AsyncParticles.");
        }
        if (gpuTapePresent()) {
            Intelium.LOGGER.info("Intelium: GPUTape detected - no overlap (Intelium never touches "
                    + "GL/Vulkan buffers); both run together safely.");
        }
    }

    private static boolean isLoaded(String id) {
        try {
            return FabricLoader.getInstance().isModLoaded(id);
        } catch (Throwable t) {
            return false;
        }
    }
}
