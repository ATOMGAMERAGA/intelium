package com.intelium.optimization;

import com.intelium.RenderBackend;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BackendRenderTuning")
class BackendRenderTuningTest {

    @Test
    @DisplayName("OpenGL tightens draw-heavy work without enabling disabled budgets")
    void openGlPolicy() {
        assertTrue(BackendRenderTuning.thresholdScale(RenderBackend.OPENGL) > 1.0);
        assertTrue(BackendRenderTuning.blockEntityBudget(256, RenderBackend.OPENGL) < 256);
        assertTrue(BackendRenderTuning.particleBudget(512, RenderBackend.OPENGL) < 512);
        assertEquals(0, BackendRenderTuning.blockEntityBudget(0, RenderBackend.OPENGL));
        assertEquals(0, BackendRenderTuning.particleBudget(0, RenderBackend.OPENGL));
    }

    @Test
    @DisplayName("Vulkan and unknown future backends keep the established policy")
    void conservativeFallback() {
        for (RenderBackend backend : new RenderBackend[]{
                RenderBackend.VULKAN, RenderBackend.UNKNOWN, null}) {
            assertEquals(1.0, BackendRenderTuning.thresholdScale(backend));
            assertEquals(256, BackendRenderTuning.blockEntityBudget(256, backend));
            assertEquals(512, BackendRenderTuning.particleBudget(512, backend));
        }
    }
}
