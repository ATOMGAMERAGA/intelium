package com.intelium;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("RenderBackend")
class RenderBackendTest {

    @Test
    @DisplayName("Recognizes Blaze3D backend labels without case sensitivity")
    void recognizesKnownBackends() {
        assertEquals(RenderBackend.VULKAN, RenderBackend.fromName("Vulkan"));
        assertEquals(RenderBackend.VULKAN, RenderBackend.fromName("VULKAN 1.3"));
        assertEquals(RenderBackend.OPENGL, RenderBackend.fromName("OpenGL"));
        assertEquals(RenderBackend.OPENGL, RenderBackend.fromName("Open GL 4.6"));
    }

    @Test
    @DisplayName("Unknown and absent labels stay conservative")
    void unknownStaysUnknown() {
        assertEquals(RenderBackend.UNKNOWN, RenderBackend.fromName(null));
        assertEquals(RenderBackend.UNKNOWN, RenderBackend.fromName(""));
        assertEquals(RenderBackend.UNKNOWN, RenderBackend.fromName("FutureGPU API"));
    }
}
