package com.intelium.optimization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CullingStrength")
class CullingStrengthTest {

    @ParameterizedTest
    @EnumSource(CullingStrength.class)
    @DisplayName("Round-trips through its persisted key")
    void roundTrip(CullingStrength strength) {
        assertEquals(strength, CullingStrength.fromKey(strength.key));
    }

    @ParameterizedTest
    @ValueSource(strings = {"BALANCED", "  balanced", "Balanced  ", "bAlAnCeD"})
    @DisplayName("Parsing tolerates case and surrounding whitespace")
    void lenientParsing(String key) {
        assertEquals(CullingStrength.BALANCED, CullingStrength.fromKey(key));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "extreme", "1", "null"})
    @DisplayName("An unknown key falls back to OFF, never to something that culls")
    void unknownFallsBackToOff(String key) {
        assertEquals(CullingStrength.OFF, CullingStrength.fromKey(key));
    }

    @Test
    @DisplayName("A null key falls back to OFF")
    void nullFallsBackToOff() {
        assertEquals(CullingStrength.OFF, CullingStrength.fromKey(null));
    }

    @Test
    @DisplayName("Only OFF reports itself as doing nothing")
    void isOn() {
        assertFalse(CullingStrength.OFF.isOn());
        assertTrue(CullingStrength.LIGHT.isOn());
        assertTrue(CullingStrength.BALANCED.isOn());
        assertTrue(CullingStrength.AGGRESSIVE.isOn());
    }

    @ParameterizedTest
    @EnumSource(CullingStrength.class)
    @DisplayName("Display keys are namespaced and unique")
    void displayKeys(CullingStrength strength) {
        assertTrue(strength.displayKey().startsWith("intelium.options.culling."));
        assertTrue(strength.displayKey().endsWith(strength.key));
    }

    @Test
    @DisplayName("Keys are lowercase and distinct")
    void keysAreDistinct() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (CullingStrength s : CullingStrength.values()) {
            assertEquals(s.key.toLowerCase(java.util.Locale.ROOT), s.key);
            assertTrue(keys.add(s.key), "duplicate key: " + s.key);
        }
    }
}
