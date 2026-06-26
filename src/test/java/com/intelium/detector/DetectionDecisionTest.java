package com.intelium.detector;

import com.intelium.IntelGpuDetector;
import com.intelium.IntelGpuGeneration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IntelGpuDetector.decide")
class DetectionDecisionTest {

    private static IntelGpuDetector.Result decide(String vendor, String renderer) {
        try {
            Method m = IntelGpuDetector.class.getDeclaredMethod("decide", String.class, String.class);
            m.setAccessible(true);
            return (IntelGpuDetector.Result) m.invoke(null, vendor, renderer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("NVIDIA vendor is disabled with the nvidia reason")
    void nvidiaDisabled() {
        IntelGpuDetector.Result r = decide("NVIDIA Corporation", "NVIDIA GeForce RTX 4080");
        assertFalse(r.compatible);
        assertEquals("intelium.disabled.nvidia", r.reasonKey);
        assertEquals(IntelGpuGeneration.UNKNOWN, r.generation);
    }

    @Test
    @DisplayName("AMD vendor is disabled with the amd reason")
    void amdDisabled() {
        IntelGpuDetector.Result r = decide("ATI Technologies Inc.", "AMD Radeon RX 7900 XTX");
        assertFalse(r.compatible);
        assertEquals("intelium.disabled.amd", r.reasonKey);
    }

    @Test
    @DisplayName("Unknown non-Intel vendor is disabled with the unknown_gpu reason")
    void unknownVendorDisabled() {
        IntelGpuDetector.Result r = decide("Apple", "Apple M3");
        assertFalse(r.compatible);
        assertEquals("intelium.disabled.unknown_gpu", r.reasonKey);
    }

    @Test
    @DisplayName("Unrecognized Intel part is disabled, NOT given a guessed profile")
    void unrecognizedIntelDisabled() {
        IntelGpuDetector.Result r = decide("Intel", "Intel(R) Future Graphics XYZ");
        assertFalse(r.compatible);
        assertEquals("intelium.disabled.unrecognized_intel", r.reasonKey);
        assertEquals(IntelGpuGeneration.UNKNOWN, r.generation);
    }

    @Test
    @DisplayName("Recognized-but-too-old Intel part is disabled with too_old reason")
    void tooOldDisabled() {
        IntelGpuDetector.Result r = decide("Intel", "Intel(R) HD Graphics 4000");
        assertFalse(r.compatible);
        assertEquals("intelium.disabled.too_old", r.reasonKey);
        assertEquals(IntelGpuGeneration.PRE_GEN9, r.generation);
    }

    @Test
    @DisplayName("Supported Intel part (HD 520) is active with no reason")
    void supportedActive() {
        IntelGpuDetector.Result r = decide("Intel", "Intel(R) HD Graphics 520");
        assertTrue(r.compatible);
        assertNull(r.reasonKey);
        assertEquals(IntelGpuGeneration.GEN9_SKYLAKE, r.generation);
    }

    @Test
    @DisplayName("Mesa Intel vendor string with Arc iGPU is active as Gen12")
    void mesaArcIgpuActive() {
        IntelGpuDetector.Result r = decide("Intel", "Mesa Intel(R) Arc(TM) Graphics (MTL)");
        assertTrue(r.compatible);
        assertEquals(IntelGpuGeneration.GEN12_XE_LP, r.generation);
    }

    @Test
    @DisplayName("Null vendor/renderer is handled as unknown vendor")
    void nullsHandled() {
        IntelGpuDetector.Result r = decide(null, null);
        assertFalse(r.compatible);
        assertEquals("intelium.disabled.unknown_gpu", r.reasonKey);
    }
}
