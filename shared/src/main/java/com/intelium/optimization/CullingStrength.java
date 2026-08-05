package com.intelium.optimization;

import java.util.Locale;

/**
 * How hard Intelium's own render-budget systems push. Shared by all three
 * budgets (entities, block entities, particles) so the user only ever learns
 * one scale.
 *
 * <ul>
 *   <li>{@link #OFF} - the system does nothing at all.</li>
 *   <li>{@link #LIGHT} - only removes work that is provably invisible or a
 *       once-in-a-while burst. Safe to leave on forever.</li>
 *   <li>{@link #BALANCED} - the default. Real savings, still tuned so that what
 *       gets dropped is a handful of pixels or a particle you would never have
 *       picked out of a cloud of five hundred.</li>
 *   <li>{@link #AGGRESSIVE} - for iGPUs that need the frames more than the
 *       detail. Distant small mobs and dropped items stop drawing noticeably
 *       sooner.</li>
 * </ul>
 *
 * <p>The numbers each level maps to live in {@link RenderBudgetTuning}; this
 * enum stays dependency-free and unit-testable.
 */
public enum CullingStrength {
    OFF("off"),
    LIGHT("light"),
    BALANCED("balanced"),
    AGGRESSIVE("aggressive");

    /** Stable key persisted in the config JSON and used in lang keys. */
    public final String key;

    CullingStrength(String key) {
        this.key = key;
    }

    /** The lang key for this level's display name (shared by all budgets). */
    public String displayKey() {
        return "intelium.options.culling." + key;
    }

    /** Whether this level does anything at all. */
    public boolean isOn() {
        return this != OFF;
    }

    /**
     * Parses a persisted key back to a level, tolerating null / unknown /
     * differently-cased values by falling back to {@link #OFF}. Never throws.
     *
     * <p>{@link #OFF} is the fallback on purpose: an unreadable value must never
     * silently turn a system that changes what you see <em>on</em>.
     */
    public static CullingStrength fromKey(String key) {
        if (key == null) return OFF;
        String k = key.trim().toLowerCase(Locale.ROOT);
        for (CullingStrength s : values()) {
            if (s.key.equals(k)) return s;
        }
        return OFF;
    }
}
