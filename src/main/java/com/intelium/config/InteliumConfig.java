package com.intelium.config;

/**
 * Persisted, user-facing settings. Every field here is wired to a <em>real</em>
 * effect (Sodium worker tuning or a live vanilla render setting) - there are no
 * placebo toggles. Plain public fields so Gson can (de)serialize without
 * adapters; unknown/legacy keys are ignored on load.
 */
public class InteliumConfig {

    /** Master switch. When false, Sodium runs unmodified. */
    public boolean enabled = true;

    /**
     * High-level intent: {@code "max_fps"}, {@code "balanced"} or
     * {@code "smooth"}. Shifts the chunk-worker trade-off (see
     * {@code OptimizationProfile} / {@code ChunkBuilderTuner}). Defaults to
     * balanced.
     */
    public String profile = "balanced";

    /**
     * Chunk-build worker thread count. {@code 0} (or negative) means
     * "automatic": Intelium picks a generation- and profile-aware value. A
     * positive value overrides Sodium's own thread count directly.
     */
    public int chunkBuildWorkers = 0;

    // ---- Live render tweaks ---------------------------------------------
    // Each is applied to the vanilla GameOptions only while Intelium is active
    // and the toggle is on; the user's original value is captured and restored
    // when the toggle (or Intelium) is turned off. These are the levers that
    // cut per-frame GPU/CPU cost while walking and looking around.

    /** Master switch for the live render tweaks below. */
    public boolean tuneFrameSettings = true;

    /**
     * Cap on the vanilla "Entity Distance" scaling, as a percent (50-100).
     * Lowering it culls distant entities sooner - a real frame-rate win in
     * crowded scenes on weak iGPUs. 100 means "do not touch".
     */
    public int maxEntityDistancePercent = 80;

    /** Cap particles to "Decreased" (never raises a stricter user setting). */
    public boolean limitParticles = true;

    /** Turn off entity shadows (cheap overdraw saving). */
    public boolean disableEntityShadows = false;

    /**
     * Force biome-blend radius to 0. Biome blending is done on the chunk-build
     * thread; turning it off makes chunk meshing dramatically cheaper, which
     * cuts the hitches you get when new chunks stream in while moving.
     */
    public boolean fastBiomeBlend = false;

    // ---- FPS test overlay ------------------------------------------------

    /** Whether the movable on-screen FPS test overlay is shown. */
    public boolean overlayEnabled = false;

    /** Compact overlay: title + FPS line only (hides benchmark/status lines). */
    public boolean overlayCompact = false;

    /** Show the 1% low / minimum FPS line (a stutter indicator). */
    public boolean overlayShowLows = true;

    /** Overlay top-left position, in scaled GUI pixels. Set by drag/edit mode. */
    public int overlayX = 4;
    public int overlayY = 4;
}
