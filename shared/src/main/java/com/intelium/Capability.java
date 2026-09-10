package com.intelium;

/**
 * The independently-failable parts of Intelium.
 *
 * <p>Every one of these depends on something outside Intelium's control - a
 * Sodium internal, a vanilla render-path method, a Blaze3D accessor - and any
 * of them can vanish under a Sodium update, a Minecraft update, or a client
 * like Lunar that ships its own modified Sodium. Historically a missing hook
 * either crashed the game or, worse, silently no-opped while the settings
 * screen went on offering the option as though it worked.
 *
 * <p>Splitting them lets exactly one feature stand down, with a reason, while
 * everything else keeps running - and lets the UI grey out the option that is
 * actually unavailable instead of the whole mod.
 */
public enum Capability {
    /** Reading the graphics device identity (vendor, renderer, backend). */
    GPU_DETECTION("intelium.capability.gpu_detection"),
    /** Overriding Sodium's chunk-build worker count. */
    WORKER_TUNING("intelium.capability.worker_tuning"),
    /** Overriding Sodium's chunk-upload defer mode. */
    DEFER_TUNING("intelium.capability.defer_tuning"),
    /** Skipping entities too small on screen to make out. */
    ENTITY_CULLING("intelium.capability.entity_culling"),
    /** The per-frame block-entity draw ceiling. */
    BLOCK_ENTITY_BUDGET("intelium.capability.block_entity_budget"),
    /** The per-tick particle spawn ceiling. */
    PARTICLE_LIMITER("intelium.capability.particle_limiter"),
    /** Detecting that a menu screen is open (drives the menu FPS cap). */
    MENU_DETECTION("intelium.capability.menu_detection"),
    /** The per-frame boundary hook that drives frame-time measurement. */
    FRAME_BOUNDARY("intelium.capability.frame_boundary");

    /** Lang key for the human-readable feature name. */
    public final String displayKey;

    Capability(String displayKey) {
        this.displayKey = displayKey;
    }
}
