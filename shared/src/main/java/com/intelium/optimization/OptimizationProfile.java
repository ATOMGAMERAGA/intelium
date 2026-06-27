package com.intelium.optimization;

import java.util.Locale;

/**
 * High-level performance intent the user picks, independent of which GPU they
 * have. The profile shifts the trade-off between raw frame rate and frame-time
 * smoothness; concrete tuning (e.g. {@link ChunkBuilderTuner}) reads it.
 *
 * <ul>
 *   <li>{@link #MAX_FPS} - favour peak average FPS. Fewer chunk-build workers so
 *       the render/main thread keeps more CPU; best for static scenes.</li>
 *   <li>{@link #BALANCED} - the default. A middle ground that keeps chunks
 *       loading quickly without starving the render thread.</li>
 *   <li>{@link #SMOOTH} - favour frame-time stability (fewer hitches when
 *       walking and turning). More chunk-build workers so newly visible chunks
 *       are ready before they enter the frustum.</li>
 * </ul>
 *
 * <p>Pure data + parsing only - no Minecraft dependency, so it is unit-testable.
 */
public enum OptimizationProfile {
    MAX_FPS("max_fps"),
    BALANCED("balanced"),
    SMOOTH("smooth");

    /** Stable key persisted in the config JSON and used in lang keys. */
    public final String key;

    OptimizationProfile(String key) {
        this.key = key;
    }

    /** The lang key for this profile's display name. */
    public String displayKey() {
        return "intelium.options.profile." + key;
    }

    /**
     * Parses a persisted key back to a profile, tolerating null / unknown /
     * differently-cased values by falling back to {@link #BALANCED}. Never throws.
     */
    public static OptimizationProfile fromKey(String key) {
        if (key == null) return BALANCED;
        String k = key.trim().toLowerCase(Locale.ROOT);
        for (OptimizationProfile p : values()) {
            if (p.key.equals(k)) return p;
        }
        return BALANCED;
    }
}
