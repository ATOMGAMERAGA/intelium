package com.intelium.config;

public class InteliumConfig {

    /** Master switch. When false, Sodium runs unmodified. */
    public boolean enabled = true;

    /**
     * Chunk-build worker thread count. {@code 0} (or negative) means
     * "automatic": Intelium picks a generation-aware value. A positive value
     * overrides Sodium's own thread count directly.
     */
    public int chunkBuildWorkers = 0;
}
