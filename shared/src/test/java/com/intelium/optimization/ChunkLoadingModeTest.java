package com.intelium.optimization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChunkLoadingMode")
class ChunkLoadingModeTest {

    @Test
    @DisplayName("fromKey parses every known key")
    void parsesKnown() {
        assertEquals(ChunkLoadingMode.OFF, ChunkLoadingMode.fromKey("off"));
        assertEquals(ChunkLoadingMode.FAST, ChunkLoadingMode.fromKey("fast"));
        assertEquals(ChunkLoadingMode.TURBO, ChunkLoadingMode.fromKey("turbo"));
    }

    @Test
    @DisplayName("fromKey is case-insensitive and trims")
    void caseInsensitive() {
        assertEquals(ChunkLoadingMode.TURBO, ChunkLoadingMode.fromKey("  TURBO "));
        assertEquals(ChunkLoadingMode.OFF, ChunkLoadingMode.fromKey("Off"));
    }

    @Test
    @DisplayName("fromKey: missing falls back to FAST, unreadable to OFF")
    void fallback() {
        // A missing key gets the shipped default; a present but unreadable one
        // must never be taken as permission to actively override Sodium.
        assertEquals(ChunkLoadingMode.FAST, ChunkLoadingMode.fromKey(null));
        assertEquals(ChunkLoadingMode.OFF, ChunkLoadingMode.fromKey(""));
        assertEquals(ChunkLoadingMode.OFF, ChunkLoadingMode.fromKey("nonsense"));
    }

    @Test
    @DisplayName("Only OFF leaves the worker count unboosted")
    void boostsWorkers() {
        assertFalse(ChunkLoadingMode.OFF.boostsWorkers());
        assertTrue(ChunkLoadingMode.FAST.boostsWorkers());
        assertTrue(ChunkLoadingMode.TURBO.boostsWorkers());
    }

    @ParameterizedTest
    @EnumSource(ChunkLoadingMode.class)
    @DisplayName("key round-trips and displayKey is namespaced")
    void roundTripAndDisplay(ChunkLoadingMode m) {
        assertEquals(m, ChunkLoadingMode.fromKey(m.key));
        assertTrue(m.displayKey().startsWith("intelium.options.fast_chunks."));
        assertTrue(m.displayKey().endsWith(m.key));
    }
}
