package com.intelium.optimization;

import java.util.Locale;

/**
 * How Intelium treats the vanilla cloud setting while active. Clouds are pure
 * overdraw on a weak iGPU - a translucent layer redrawn every frame - so
 * flattening or removing them is one of the cheapest real FPS wins available.
 *
 * <ul>
 *   <li>{@link #DEFAULT} - leave the user's cloud setting untouched.</li>
 *   <li>{@link #FAST} - cap "Fancy" (volumetric) clouds to "Fast" (flat).
 *       Never raises a stricter setting: a user who already picked Off stays
 *       Off.</li>
 *   <li>{@link #OFF} - do not render clouds at all while Intelium is active.
 *       The user's original setting is restored when the lever (or Intelium)
 *       is turned off.</li>
 * </ul>
 *
 * <p>The mapping to the vanilla cloud enum lives in the per-version
 * {@code RenderTweaks}; this enum stays dependency-free and unit-testable.
 */
public enum CloudsMode {
    DEFAULT("default"),
    FAST("fast"),
    OFF("off");

    /** Stable key persisted in the config JSON and used in lang keys. */
    public final String key;

    CloudsMode(String key) {
        this.key = key;
    }

    /** The lang key for this mode's display name. */
    public String displayKey() {
        return "intelium.options.clouds." + key;
    }

    /**
     * Parses a persisted key back to a mode, tolerating null / unknown /
     * differently-cased values by falling back to {@link #DEFAULT}. Never throws.
     */
    public static CloudsMode fromKey(String key) {
        if (key == null) return DEFAULT;
        String k = key.trim().toLowerCase(Locale.ROOT);
        for (CloudsMode m : values()) {
            if (m.key.equals(k)) return m;
        }
        return DEFAULT;
    }
}
