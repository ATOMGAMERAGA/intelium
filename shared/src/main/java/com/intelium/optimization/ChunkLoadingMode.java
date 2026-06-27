package com.intelium.optimization;

import java.util.Locale;

/**
 * How aggressively Intelium speeds up chunk loading by overriding Sodium's
 * <em>defer mode</em> - how many frames a freshly meshed chunk waits before it
 * is uploaded and shown.
 *
 * <p>Sodium ships with the most conservative setting ({@code DeferMode.ALWAYS}):
 * smoothest frame pacing, but built chunks can linger for several frames before
 * appearing, which is the "slow loading" feeling. Intelium dials that latency
 * down:
 *
 * <ul>
 *   <li>{@link #OFF} - leave Sodium's choice untouched.</li>
 *   <li>{@link #FAST} - one-frame deferral. Chunks appear much sooner with
 *       almost no frame-pacing cost. The recommended default.</li>
 *   <li>{@link #TURBO} - zero-frame deferral. Built chunks are uploaded the same
 *       frame, the fastest possible loading; may cost some smoothness when a lot
 *       of terrain streams in at once.</li>
 * </ul>
 *
 * <p>The actual Sodium {@code DeferMode} mapping lives in the client-side
 * booster (which touches Sodium types); this enum stays dependency-free and
 * unit-testable. Both {@link #FAST} and {@link #TURBO} also ask the chunk
 * tuner for extra build throughput (see {@link ChunkBuilderTuner}).
 */
public enum ChunkLoadingMode {
    OFF("off"),
    FAST("fast"),
    TURBO("turbo");

    /** Stable key persisted in the config JSON and used in lang keys. */
    public final String key;

    ChunkLoadingMode(String key) {
        this.key = key;
    }

    /** The lang key for this mode's display name. */
    public String displayKey() {
        return "intelium.options.fast_chunks." + key;
    }

    /** Whether this mode asks the chunk tuner for extra build throughput. */
    public boolean boostsWorkers() {
        return this != OFF;
    }

    /**
     * Parses a persisted key back to a mode, tolerating null / unknown /
     * differently-cased values by falling back to {@link #FAST}. Never throws.
     */
    public static ChunkLoadingMode fromKey(String key) {
        if (key == null) return FAST;
        String k = key.trim().toLowerCase(Locale.ROOT);
        for (ChunkLoadingMode m : values()) {
            if (m.key.equals(k)) return m;
        }
        return FAST;
    }
}
