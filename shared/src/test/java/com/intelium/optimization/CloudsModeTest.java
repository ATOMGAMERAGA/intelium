package com.intelium.optimization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CloudsMode parsing and keys")
class CloudsModeTest {

    @Test
    @DisplayName("Keys are stable")
    void stableKeys() {
        assertEquals("default", CloudsMode.DEFAULT.key);
        assertEquals("fast", CloudsMode.FAST.key);
        assertEquals("off", CloudsMode.OFF.key);
    }

    @Test
    @DisplayName("fromKey round-trips every mode")
    void roundTrip() {
        for (CloudsMode m : CloudsMode.values()) {
            assertSame(m, CloudsMode.fromKey(m.key));
        }
    }

    @Test
    @DisplayName("fromKey tolerates case and whitespace")
    void caseAndWhitespace() {
        assertSame(CloudsMode.OFF, CloudsMode.fromKey("  OFF "));
        assertSame(CloudsMode.FAST, CloudsMode.fromKey("Fast"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "bogus", "fancy", "42"})
    @DisplayName("Unknown keys fall back to DEFAULT")
    void unknownFallsBack(String key) {
        assertSame(CloudsMode.DEFAULT, CloudsMode.fromKey(key));
    }

    @Test
    @DisplayName("Null key falls back to DEFAULT and never throws")
    void nullFallsBack() {
        assertSame(CloudsMode.DEFAULT, CloudsMode.fromKey(null));
    }

    @Test
    @DisplayName("displayKey follows the lang-key convention")
    void displayKeys() {
        for (CloudsMode m : CloudsMode.values()) {
            assertEquals("intelium.options.clouds." + m.key, m.displayKey());
        }
    }
}
