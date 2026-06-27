package com.intelium;

import com.intelium.config.InteliumConfig;
import com.intelium.optimization.ChunkBuilderTuner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fast end-to-end style smoke tests. Each touches multiple subsystems
 * to make sure they cooperate. Capped at one second each so that a
 * broken dependency surfaces as a timeout rather than a hang.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Cross-component smoke tests")
class SmokeTest {

    private static IntelGpuClassifier.Result decide(String vendor, String renderer) {
        try {
            Method m = IntelGpuClassifier.class.getDeclaredMethod("decide", String.class, String.class);
            m.setAccessible(true);
            return (IntelGpuClassifier.Result) m.invoke(null, vendor, renderer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    @DisplayName("Skylake HD 520 detection + tuner pipeline produces an active, sane result")
    void skylakePipeline() {
        InteliumConfig cfg = new InteliumConfig();
        assertTrue(cfg.enabled);
        assertEquals(0, cfg.chunkBuildWorkers);

        IntelGpuClassifier.Result r = decide("Intel", "Intel(R) HD Graphics 520");
        assertTrue(r.compatible);
        assertEquals(IntelGpuGeneration.GEN9_SKYLAKE, r.generation);

        int workers = ChunkBuilderTuner.recommendedWorkers(r.generation);
        assertTrue(workers >= 1 && workers <= 2);
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    @DisplayName("Battlemage pipeline detects and yields a higher worker count")
    void battlemagePipeline() {
        IntelGpuClassifier.Result r = decide("Intel", "Intel(R) Arc(TM) B580 Graphics");
        assertTrue(r.compatible);
        assertEquals(IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE, r.generation);
        assertTrue(ChunkBuilderTuner.recommendedWorkers(r.generation) >= 4
                || Runtime.getRuntime().availableProcessors() < 8);
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    @DisplayName("All eight generations cycle through the tuner without crashing")
    void allGenerationsCycle() {
        for (IntelGpuGeneration g : IntelGpuGeneration.values()) {
            assertTrue(ChunkBuilderTuner.recommendedWorkers(g) >= 1);
        }
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    @DisplayName("Non-Intel GPUs are refused (no tuning applied)")
    void nonIntelRefused() {
        assertFalse(decide("NVIDIA Corporation", "NVIDIA GeForce RTX 4080").compatible);
        assertFalse(decide("ATI Technologies Inc.", "AMD Radeon RX 7900 XTX").compatible);
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    @DisplayName("Mod ID never gets corrupted")
    void modIdStable() {
        for (int i = 0; i < 1000; i++) {
            assertEquals("intelium", Intelium.MOD_ID);
        }
    }
}
