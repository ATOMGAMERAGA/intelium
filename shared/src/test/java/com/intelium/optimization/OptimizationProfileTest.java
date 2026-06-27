package com.intelium.optimization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OptimizationProfile")
class OptimizationProfileTest {

    @Test
    @DisplayName("fromKey parses every known key")
    void parsesKnown() {
        assertEquals(OptimizationProfile.MAX_FPS, OptimizationProfile.fromKey("max_fps"));
        assertEquals(OptimizationProfile.BALANCED, OptimizationProfile.fromKey("balanced"));
        assertEquals(OptimizationProfile.SMOOTH, OptimizationProfile.fromKey("smooth"));
    }

    @Test
    @DisplayName("fromKey is case-insensitive and trims")
    void caseInsensitive() {
        assertEquals(OptimizationProfile.SMOOTH, OptimizationProfile.fromKey("  SMOOTH "));
        assertEquals(OptimizationProfile.MAX_FPS, OptimizationProfile.fromKey("Max_Fps"));
    }

    @Test
    @DisplayName("fromKey falls back to BALANCED for null/unknown")
    void fallback() {
        assertEquals(OptimizationProfile.BALANCED, OptimizationProfile.fromKey(null));
        assertEquals(OptimizationProfile.BALANCED, OptimizationProfile.fromKey(""));
        assertEquals(OptimizationProfile.BALANCED, OptimizationProfile.fromKey("nonsense"));
    }

    @ParameterizedTest
    @EnumSource(OptimizationProfile.class)
    @DisplayName("key round-trips through fromKey")
    void roundTrip(OptimizationProfile p) {
        assertEquals(p, OptimizationProfile.fromKey(p.key));
    }

    @ParameterizedTest
    @EnumSource(OptimizationProfile.class)
    @DisplayName("displayKey is namespaced under intelium.options.profile")
    void displayKeyNamespaced(OptimizationProfile p) {
        assertTrue(p.displayKey().startsWith("intelium.options.profile."));
        assertTrue(p.displayKey().endsWith(p.key));
    }
}
