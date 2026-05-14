package com.intelium;

import com.intelium.config.InteliumConfig;
import com.intelium.optimization.BufferStrategy;
import com.intelium.optimization.ChunkBuilderTuner;
import com.intelium.optimization.DrawCallBatcher;
import com.intelium.optimization.OcclusionTuner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

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

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    @DisplayName("Config defaults + tuner + occlusion play well for Skylake")
    void skylakePipeline() {
        InteliumConfig cfg = new InteliumConfig();
        assertTrue(cfg.enabled);
        DrawCallBatcher.beginFrame(IntelGpuGeneration.GEN9_SKYLAKE);
        assertEquals(64, DrawCallBatcher.currentBatchSize());
        OcclusionTuner.applyForCurrentFrame(IntelGpuGeneration.GEN9_SKYLAKE);
        assertTrue(OcclusionTuner.multiplier() < 1.0f);
        assertTrue(BufferStrategy.usePersistent(IntelGpuGeneration.GEN9_SKYLAKE));
        int workers = ChunkBuilderTuner.recommendedWorkers(IntelGpuGeneration.GEN9_SKYLAKE);
        assertTrue(workers >= 1 && workers <= 2);
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    @DisplayName("Battlemage pipeline produces aggressive defaults")
    void battlemagePipeline() {
        DrawCallBatcher.beginFrame(IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE);
        assertEquals(256, DrawCallBatcher.currentBatchSize());
        OcclusionTuner.applyForCurrentFrame(IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE);
        assertEquals(1.0f, OcclusionTuner.multiplier(), 0.0001f);
        assertTrue(BufferStrategy.usePersistent(IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE));
        int workers = ChunkBuilderTuner.recommendedWorkers(IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE);
        assertTrue(workers >= 4);
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    @DisplayName("All eight generations cycle through the pipeline without crashing")
    void allGenerationsCycle() {
        for (IntelGpuGeneration g : IntelGpuGeneration.values()) {
            DrawCallBatcher.beginFrame(g);
            DrawCallBatcher.endFrame();
            OcclusionTuner.applyForCurrentFrame(g);
            ChunkBuilderTuner.recommendedWorkers(g);
            BufferStrategy.usePersistent(g);
        }
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    @DisplayName("Repeated cycles converge to the same values (no hidden state leak)")
    void cyclesConverge() {
        DrawCallBatcher.beginFrame(IntelGpuGeneration.GEN12_XE_LP);
        int first = DrawCallBatcher.currentBatchSize();
        for (int i = 0; i < 10; i++) {
            DrawCallBatcher.beginFrame(IntelGpuGeneration.GEN12_XE_LP);
        }
        assertEquals(first, DrawCallBatcher.currentBatchSize());
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
