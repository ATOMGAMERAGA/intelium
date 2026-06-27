package com.intelium.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InteliumConfig defaults")
class InteliumConfigTest {

    @Test
    @DisplayName("Default enabled is true")
    void defaultEnabled() {
        assertTrue(new InteliumConfig().enabled);
    }

    @Test
    @DisplayName("Default chunkBuildWorkers is 0 (auto)")
    void defaultChunkBuildWorkers() {
        assertEquals(0, new InteliumConfig().chunkBuildWorkers);
    }

    @Test
    @DisplayName("Overlay defaults: disabled, positioned near top-left")
    void overlayDefaults() {
        InteliumConfig c = new InteliumConfig();
        assertFalse(c.overlayEnabled);
        assertEquals(4, c.overlayX);
        assertEquals(4, c.overlayY);
    }

    @Test
    @DisplayName("Two fresh configs are equal by field")
    void freshConfigsEqualByField() {
        InteliumConfig a = new InteliumConfig();
        InteliumConfig b = new InteliumConfig();
        assertEquals(a.enabled, b.enabled);
        assertEquals(a.chunkBuildWorkers, b.chunkBuildWorkers);
    }

    @Test
    @DisplayName("Fields are public for GSON access")
    void fieldsArePublic() throws NoSuchFieldException {
        for (String name : new String[]{"enabled", "chunkBuildWorkers"}) {
            var f = InteliumConfig.class.getDeclaredField(name);
            assertTrue(java.lang.reflect.Modifier.isPublic(f.getModifiers()),
                    name + " should be public for GSON");
        }
    }

    @Test
    @DisplayName("Mutability: enabled can be toggled")
    void mutableEnabled() {
        InteliumConfig c = new InteliumConfig();
        c.enabled = false;
        assertFalse(c.enabled);
    }

    @Test
    @DisplayName("Mutability: chunkBuildWorkers can be set to positive value")
    void mutableChunkWorkers() {
        InteliumConfig c = new InteliumConfig();
        c.chunkBuildWorkers = 4;
        assertEquals(4, c.chunkBuildWorkers);
    }

    @Test
    @DisplayName("Class has public no-arg constructor (for GSON)")
    void hasNoArgCtor() throws NoSuchMethodException {
        var ctor = InteliumConfig.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPublic(ctor.getModifiers()));
    }

    @Test
    @DisplayName("All settings fields are public, non-static (for GSON) - no placebos")
    void allFieldsArePublicSettings() {
        // Every field is a real, wired setting. They must all be public &
        // non-static so Gson can persist them.
        String[] expected = {
                "enabled", "profile", "chunkBuildWorkers", "chunkLoadingMode",
                "tuneFrameSettings", "maxEntityDistancePercent", "limitParticles",
                "disableEntityShadows", "fastBiomeBlend",
                "overlayEnabled", "overlayCompact", "overlayShowLows",
                "overlayX", "overlayY"
        };
        for (String name : expected) {
            try {
                var f = InteliumConfig.class.getDeclaredField(name);
                assertTrue(java.lang.reflect.Modifier.isPublic(f.getModifiers()), name + " public");
                assertFalse(java.lang.reflect.Modifier.isStatic(f.getModifiers()), name + " non-static");
            } catch (NoSuchFieldException e) {
                fail("missing settings field: " + name);
            }
        }
        long publicFields = java.util.Arrays.stream(InteliumConfig.class.getDeclaredFields())
                .filter(f -> java.lang.reflect.Modifier.isPublic(f.getModifiers())
                        && !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .count();
        assertEquals(expected.length, publicFields,
                "every public field must be a known setting");
    }

    @Test
    @DisplayName("Default profile is balanced")
    void defaultProfile() {
        assertEquals("balanced", new InteliumConfig().profile);
    }

    @Test
    @DisplayName("Default chunk loading mode is fast")
    void defaultChunkLoadingMode() {
        assertEquals("fast", new InteliumConfig().chunkLoadingMode);
    }

    @Test
    @DisplayName("Default live render tweaks: master on, entity distance capped, particles limited")
    void defaultRenderTweaks() {
        InteliumConfig c = new InteliumConfig();
        assertTrue(c.tuneFrameSettings);
        assertEquals(80, c.maxEntityDistancePercent);
        assertTrue(c.limitParticles);
        assertFalse(c.disableEntityShadows);
        assertFalse(c.fastBiomeBlend);
    }
}
