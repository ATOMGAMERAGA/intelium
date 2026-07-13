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
                "cloudsMode", "fastGraphics", "disableSmoothLighting",
                "disableVsync", "maxRenderDistance", "maxSimulationDistance",
                "adaptiveRenderDistance", "adaptiveFpsTarget", "backgroundFpsLimit",
                "overlayEnabled", "overlayCompact", "overlayShowLows",
                "overlayShowFrameTime", "overlayX", "overlayY",
                "captured"
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

    @Test
    @DisplayName("Default GPU savers: everything hands-off (no visual surprises)")
    void defaultGpuSavers() {
        InteliumConfig c = new InteliumConfig();
        assertEquals("default", c.cloudsMode);
        assertFalse(c.fastGraphics);
        assertFalse(c.disableSmoothLighting);
        assertFalse(c.disableVsync);
        assertEquals(0, c.maxRenderDistance);
        assertEquals(0, c.maxSimulationDistance);
    }

    @Test
    @DisplayName("Default adaptive performance: off, sensible 60 FPS target, no background cap")
    void defaultAdaptive() {
        InteliumConfig c = new InteliumConfig();
        assertFalse(c.adaptiveRenderDistance);
        assertEquals(60, c.adaptiveFpsTarget);
        assertEquals(0, c.backgroundFpsLimit);
    }

    @Test
    @DisplayName("sanitize clamps the adaptive/background/simulation settings")
    void sanitizeAdaptive() {
        InteliumConfig c = new InteliumConfig();
        c.adaptiveFpsTarget = 5;
        c.backgroundFpsLimit = 999;
        c.maxSimulationDistance = 1;
        InteliumConfig.sanitize(c);
        assertEquals(30, c.adaptiveFpsTarget);
        assertEquals(60, c.backgroundFpsLimit);
        assertEquals(5, c.maxSimulationDistance);

        c.adaptiveFpsTarget = 500;
        c.backgroundFpsLimit = -3;
        c.maxSimulationDistance = 99;
        InteliumConfig.sanitize(c);
        assertEquals(144, c.adaptiveFpsTarget);
        assertEquals(0, c.backgroundFpsLimit);
        assertEquals(32, c.maxSimulationDistance);
    }

    @Test
    @DisplayName("Restore cache starts present and empty")
    void capturedStartsEmpty() {
        InteliumConfig c = new InteliumConfig();
        assertNotNull(c.captured);
        assertNull(c.captured.entityDistance);
        assertNull(c.captured.particles);
        assertNull(c.captured.clouds);
        assertNull(c.captured.graphics);
        assertNull(c.captured.smoothLighting);
        assertNull(c.captured.vsync);
        assertNull(c.captured.renderDistance);
        assertNull(c.captured.simulationDistance);
        assertNull(c.captured.fpsLimit);
        assertNull(c.captured.sodiumDeferMode);
    }

    @Test
    @DisplayName("sanitize clamps out-of-range values and heals nulls")
    void sanitizeClampsAndHeals() {
        InteliumConfig c = new InteliumConfig();
        c.profile = null;
        c.chunkLoadingMode = null;
        c.cloudsMode = null;
        c.chunkBuildWorkers = 99;
        c.maxEntityDistancePercent = 5;
        c.maxRenderDistance = 1;
        c.overlayX = -100;
        c.overlayY = -1;
        c.captured = null;

        InteliumConfig.sanitize(c);

        assertEquals("balanced", c.profile);
        assertEquals("fast", c.chunkLoadingMode);
        assertEquals("default", c.cloudsMode);
        assertEquals(16, c.chunkBuildWorkers);
        assertEquals(50, c.maxEntityDistancePercent);
        assertEquals(2, c.maxRenderDistance);
        assertEquals(0, c.overlayX);
        assertEquals(0, c.overlayY);
        assertNotNull(c.captured);
    }

    @Test
    @DisplayName("sanitize keeps valid values untouched")
    void sanitizeKeepsValid() {
        InteliumConfig c = new InteliumConfig();
        c.maxRenderDistance = 12;
        c.maxEntityDistancePercent = 75;
        c.chunkBuildWorkers = 4;
        InteliumConfig.sanitize(c);
        assertEquals(12, c.maxRenderDistance);
        assertEquals(75, c.maxEntityDistancePercent);
        assertEquals(4, c.chunkBuildWorkers);
    }

    @Test
    @DisplayName("sanitize maps zero/negative render-distance cap to 'no cap'")
    void sanitizeRenderDistanceOff() {
        InteliumConfig c = new InteliumConfig();
        c.maxRenderDistance = -5;
        InteliumConfig.sanitize(c);
        assertEquals(0, c.maxRenderDistance);
    }
}
