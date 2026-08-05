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

    /**
     * Fast chunk loading: {@code "off"}, {@code "fast"} or {@code "turbo"}.
     * Overrides Sodium's defer mode so freshly meshed chunks appear sooner, and
     * boosts chunk-build throughput. {@code "fast"} (one-frame deferral) is the
     * default; {@code "turbo"} (zero-frame) is the fastest but may cost some
     * smoothness. See {@code ChunkLoadingMode}.
     */
    public String chunkLoadingMode = "fast";

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

    /**
     * Cloud handling: {@code "default"} (don't touch), {@code "fast"} (cap
     * fancy/volumetric clouds to flat) or {@code "off"} (no clouds). Clouds are
     * a translucent layer redrawn every frame - pure overdraw on an iGPU. See
     * {@code CloudsMode}.
     */
    public String cloudsMode = "default";

    /**
     * Force the vanilla Graphics setting to "Fast" (fast leaves, simpler
     * weather/translucency). One of the biggest single levers on a weak iGPU;
     * off by default because it visibly changes how the game looks.
     */
    public boolean fastGraphics = false;

    /**
     * Turn off smooth lighting (ambient occlusion). Skips the per-vertex AO
     * darkening done during chunk meshing - cheaper chunk builds and slightly
     * cheaper frames, at a visible cost. Off by default.
     */
    public boolean disableSmoothLighting = false;

    /**
     * Force VSync off while Intelium is active, uncapping the frame rate from
     * the display's refresh rate. Off by default (some users want the cap);
     * the user's own VSync choice is restored when turned off.
     */
    public boolean disableVsync = false;

    /**
     * Force the menu background blur to 0. The gaussian blur behind menus
     * re-filters the whole frame every menu frame - real GPU cost on an iGPU
     * whenever the pause screen, inventory or settings are open. Off by
     * default (it visibly changes how menus look); the user's own blurriness
     * is restored when turned off.
     */
    public boolean disableMenuBlur = false;

    /**
     * Cap on the vanilla render distance, in chunks. {@code 0} means "do not
     * touch". A positive value caps the user's render distance downward (never
     * raises it) - fewer chunk sections to build, upload and draw every frame,
     * the single most reliable FPS lever there is.
     */
    public int maxRenderDistance = 0;

    /**
     * Cap on the vanilla simulation distance, in chunks. {@code 0} means "do
     * not touch". A positive value caps it downward (never raises it) - fewer
     * ticked chunks means real CPU savings in singleplayer, which on an iGPU
     * sharing a power budget with the CPU also frees thermal headroom for
     * frames. See {@code AdaptiveDistanceController} for why downward-only.
     */
    public int maxSimulationDistance = 0;

    // ---- Adaptive performance ------------------------------------------

    /**
     * Adaptive render distance: when the measured FPS stays below
     * {@link #adaptiveFpsTarget}, the render distance is stepped down (one
     * chunk at a time, never below half the user's own setting); when there is
     * sustained headroom it is stepped back up. Off by default - it changes a
     * visible setting on its own, so the user must opt in. See
     * {@code AdaptiveDistanceController}.
     */
    public boolean adaptiveRenderDistance = false;

    /** The FPS the adaptive controller tries to hold (30-144). */
    public int adaptiveFpsTarget = 60;

    /**
     * Frame-rate cap applied while the game window is unfocused. {@code 0}
     * means "off". On an iGPU this is a double win: background frames stop
     * burning the shared CPU/GPU power budget, so the game also comes back
     * cooler when refocused. The user's own FPS limit is captured and restored
     * the moment the window regains focus. Intelium yields this lever entirely
     * when a dedicated mod (Dynamic FPS, FPS Reducer) is installed.
     */
    public int backgroundFpsLimit = 0;

    /**
     * Frame-rate cap applied while a full-screen menu (pause screen, inventory,
     * settings) is open. {@code 0} means "off". Menus redraw the whole frame -
     * world included - at full rate even though nothing on screen needs it;
     * capping them is pure savings, the technique FPS Reducer proved. The
     * user's own FPS limit is captured and restored the instant the menu
     * closes. Yields entirely when a dedicated frame limiter mod is installed.
     */
    public int menuFpsLimit = 0;

    // ---- Render Budget Engine ---------------------------------------------
    // Intelium's own culling systems. Unlike everything above, these do not
    // flip a vanilla setting - they sit in the render path and decide, per
    // entity / per block entity / per particle, whether the work is worth
    // doing at all. See com.intelium.optimization.RenderBudget.

    /** Master switch for the three budgets below. */
    public boolean renderBudget = true;

    /**
     * Screen-space entity culling: {@code "off"}, {@code "light"},
     * {@code "balanced"} or {@code "aggressive"}. Skips drawing entities whose
     * on-screen size falls below a threshold in pixels - so distant dropped
     * items, XP orbs and arrows stop being drawn long before anything you could
     * actually make out does. Unlike vanilla's Entity Distance this scales with
     * the entity's real size and with your resolution and FOV. Nothing within
     * 12 blocks is ever culled, and players and named entities are exempt.
     */
    public String entityCulling = "balanced";

    /**
     * Per-frame block-entity budget: {@code "off"} .. {@code "aggressive"}.
     * Chests, signs, banners, item frames and beacons are the one part of the
     * world Sodium cannot batch into the chunk mesh - each is drawn
     * individually, every frame. A normal scene never reaches the ceiling; a
     * storage room does, and that is where it buys back the frames.
     */
    public String blockEntityCulling = "balanced";

    /**
     * Per-tick particle spawn budget: {@code "off"} .. {@code "aggressive"}.
     * Ordinary play spawns a handful of particles per tick and never notices
     * this; a TNT chain or a splash-potion volley spawns thousands in one tick,
     * each costing CPU to tick and GPU to draw for its whole life. Capping the
     * burst keeps the effect and drops the freeze.
     */
    public String particleBudget = "balanced";

    /**
     * Whether the three budgets tighten further when the frame rate falls short
     * of {@link #adaptiveFpsTarget}, and relax again once it recovers. On by
     * default: it only ever trades detail for frames when there are no frames
     * to spare.
     */
    public boolean adaptiveCulling = true;

    // ---- FPS test overlay ------------------------------------------------

    /** Whether the movable on-screen FPS test overlay is shown. */
    public boolean overlayEnabled = false;

    /** Compact overlay: title + FPS line only (hides benchmark/status lines). */
    public boolean overlayCompact = false;

    /** Show the 1% low / minimum FPS line (a stutter indicator). */
    public boolean overlayShowLows = true;

    /** Show the frame-time line (current and rolling-average milliseconds). */
    public boolean overlayShowFrameTime = false;

    /** Overlay top-left position, in scaled GUI pixels. Set by drag/edit mode. */
    public int overlayX = 4;
    public int overlayY = 4;

    // ---- Restore cache -----------------------------------------------------

    /**
     * The user's own option values, captured the moment a lever first forces a
     * setting and cleared when it is restored. Persisted so that "turn the
     * lever off" still restores the <em>user's</em> value even across a game
     * restart - without this, the forced value would be saved into
     * {@code options.txt} on quit and silently become the new "original".
     * Enum values are stored by name so the cache stays version-tolerant.
     */
    public CapturedOptions captured = new CapturedOptions();

    /** Nullable captured originals; {@code null} = "not managing this lever". */
    public static class CapturedOptions {
        public Double entityDistance;
        public String particles;
        public Boolean entityShadows;
        public Integer biomeBlend;
        public String clouds;
        public String graphics;
        public Boolean smoothLighting;
        public Boolean vsync;
        /** The user's own menu-background blurriness (menu blur lever). */
        public Integer menuBlur;
        public Integer renderDistance;
        public Integer simulationDistance;
        /** The user's own max-FPS limit (background FPS limit lever). */
        public Integer fpsLimit;
        /** Sodium's chunk-build defer mode (fast chunk loading lever). */
        public String sodiumDeferMode;
    }

    /**
     * Clamps values into their valid ranges and replaces nulls with defaults,
     * so a hand-edited or truncated config file cannot misconfigure the mod.
     * Returns the same instance for chaining. Pure logic - unit-testable.
     */
    public static InteliumConfig sanitize(InteliumConfig cfg) {
        InteliumConfig defaults = new InteliumConfig();
        if (cfg.profile == null) cfg.profile = defaults.profile;
        if (cfg.chunkLoadingMode == null) cfg.chunkLoadingMode = defaults.chunkLoadingMode;
        if (cfg.cloudsMode == null) cfg.cloudsMode = defaults.cloudsMode;
        if (cfg.entityCulling == null) cfg.entityCulling = defaults.entityCulling;
        if (cfg.blockEntityCulling == null) cfg.blockEntityCulling = defaults.blockEntityCulling;
        if (cfg.particleBudget == null) cfg.particleBudget = defaults.particleBudget;
        cfg.chunkBuildWorkers = clamp(cfg.chunkBuildWorkers, 0, 16);
        cfg.maxEntityDistancePercent = clamp(cfg.maxEntityDistancePercent, 50, 100);
        cfg.maxRenderDistance = cfg.maxRenderDistance <= 0
                ? 0 : clamp(cfg.maxRenderDistance, 2, 32);
        cfg.maxSimulationDistance = cfg.maxSimulationDistance <= 0
                ? 0 : clamp(cfg.maxSimulationDistance, 5, 32);
        cfg.adaptiveFpsTarget = clamp(cfg.adaptiveFpsTarget, 30, 144);
        cfg.backgroundFpsLimit = cfg.backgroundFpsLimit <= 0
                ? 0 : clamp(cfg.backgroundFpsLimit, 10, 60);
        cfg.menuFpsLimit = cfg.menuFpsLimit <= 0
                ? 0 : clamp(cfg.menuFpsLimit, 10, 60);
        cfg.overlayX = Math.max(0, cfg.overlayX);
        cfg.overlayY = Math.max(0, cfg.overlayY);
        if (cfg.captured == null) cfg.captured = new CapturedOptions();
        return cfg;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
