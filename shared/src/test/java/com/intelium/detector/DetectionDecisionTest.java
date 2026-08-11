package com.intelium.detector;

import com.intelium.IntelGpuClassifier;
import com.intelium.IntelGpuGeneration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IntelGpuDetector.decide")
class DetectionDecisionTest {

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
    @DisplayName("NVIDIA vendor is disabled with the nvidia reason")
    void nvidiaDisabled() {
        IntelGpuClassifier.Result r = decide("NVIDIA Corporation", "NVIDIA GeForce RTX 4080");
        assertFalse(r.compatible);
        assertEquals("intelium.disabled.nvidia", r.reasonKey);
        assertEquals(IntelGpuGeneration.UNKNOWN, r.generation);
    }

    @Test
    @DisplayName("AMD vendor is disabled with the amd reason")
    void amdDisabled() {
        IntelGpuClassifier.Result r = decide("ATI Technologies Inc.", "AMD Radeon RX 7900 XTX");
        assertFalse(r.compatible);
        assertEquals("intelium.disabled.amd", r.reasonKey);
    }

    @Test
    @DisplayName("Unknown non-Intel vendor is disabled with the unknown_gpu reason")
    void unknownVendorDisabled() {
        IntelGpuClassifier.Result r = decide("Apple", "Apple M3");
        assertFalse(r.compatible);
        assertEquals("intelium.disabled.unknown_gpu", r.reasonKey);
    }

    @Test
    @DisplayName("Unrecognized Intel part is disabled, NOT given a guessed profile")
    void unrecognizedIntelDisabled() {
        IntelGpuClassifier.Result r = decide("Intel", "Intel(R) Future Graphics XYZ");
        assertFalse(r.compatible);
        assertEquals("intelium.disabled.unrecognized_intel", r.reasonKey);
        assertEquals(IntelGpuGeneration.UNKNOWN, r.generation);
    }

    @Test
    @DisplayName("Recognized-but-too-old Intel part is disabled with too_old reason")
    void tooOldDisabled() {
        IntelGpuClassifier.Result r = decide("Intel", "Intel(R) HD Graphics 4000");
        assertFalse(r.compatible);
        assertEquals("intelium.disabled.too_old", r.reasonKey);
        assertEquals(IntelGpuGeneration.PRE_GEN9, r.generation);
    }

    @Test
    @DisplayName("Supported Intel part (HD 520) is active with no reason")
    void supportedActive() {
        IntelGpuClassifier.Result r = decide("Intel", "Intel(R) HD Graphics 520");
        assertTrue(r.compatible);
        assertNull(r.reasonKey);
        assertEquals(IntelGpuGeneration.GEN9_SKYLAKE, r.generation);
    }

    @Test
    @DisplayName("Mesa Intel vendor string with Arc iGPU is active as Gen12")
    void mesaArcIgpuActive() {
        IntelGpuClassifier.Result r = decide("Intel", "Mesa Intel(R) Arc(TM) Graphics (MTL)");
        assertTrue(r.compatible);
        assertEquals(IntelGpuGeneration.GEN12_XE_LP, r.generation);
    }

    @Test
    @DisplayName("Null vendor/renderer is handled as unknown vendor")
    void nullsHandled() {
        IntelGpuClassifier.Result r = decide(null, null);
        assertFalse(r.compatible);
        assertEquals("intelium.disabled.unknown_gpu", r.reasonKey);
    }

    // ===== Issue #9: Ice Lake UHD Graphics G1 =====

    @Test
    @DisplayName("UHD Graphics G1 (Ice Lake) is active as Gen 11, not 'too old'")
    void uhdG1Active() {
        IntelGpuClassifier.Result r = decide("Intel", "Intel(R) UHD Graphics G1");
        assertTrue(r.compatible);
        assertNull(r.reasonKey);
        assertEquals(IntelGpuGeneration.GEN11_ICE_LAKE, r.generation);
    }

    @Test
    @DisplayName("Bare 'Intel(R) UHD Graphics' is active (conservative Gen 9.5), not 'too old'")
    void bareUhdActive() {
        IntelGpuClassifier.Result r = decide("Intel", "Intel(R) UHD Graphics");
        assertTrue(r.compatible);
        assertEquals(IntelGpuGeneration.GEN9_5_KABY_COFFEE, r.generation);
    }

    // ===== Issue #7: VirGL virtual GPUs (ChromeOS Crostini / Linux VMs) =====

    @Test
    @DisplayName("VirGL with host Intel renderer passed through is active")
    void virglIntelPassthroughActive() {
        IntelGpuClassifier.Result r = decide("Red Hat",
                "virgl (Mesa Intel(R) UHD Graphics 600 (GLK 2))");
        assertTrue(r.compatible);
        assertNull(r.reasonKey);
        assertEquals(IntelGpuGeneration.GEN9_5_KABY_COFFEE, r.generation);
    }

    @Test
    @DisplayName("VirGL with a supported Gen 12 host part is active as Gen 12")
    void virglGen12PassthroughActive() {
        IntelGpuClassifier.Result r = decide("Red Hat",
                "virgl (Mesa Intel(R) Xe Graphics (TGL GT2))");
        assertTrue(r.compatible);
        assertEquals(IntelGpuGeneration.GEN12_XE_LP, r.generation);
    }

    @Test
    @DisplayName("Bare 'virgl' with the host GPU hidden is disabled with the virgl reason")
    void virglHiddenHostDisabled() {
        IntelGpuClassifier.Result r = decide("Red Hat", "virgl");
        assertFalse(r.compatible);
        assertEquals("intelium.disabled.virgl", r.reasonKey);
        assertEquals(IntelGpuGeneration.UNKNOWN, r.generation);
    }

    @Test
    @DisplayName("VirGL with an NVIDIA host GPU is refused with the nvidia reason")
    void virglNvidiaHostRefused() {
        IntelGpuClassifier.Result r = decide("Red Hat", "virgl (NVIDIA GeForce GTX 1650)");
        assertFalse(r.compatible);
        assertEquals("intelium.disabled.nvidia", r.reasonKey);
    }

    @Test
    @DisplayName("VirGL with an AMD host GPU is refused with the amd reason")
    void virglAmdHostRefused() {
        IntelGpuClassifier.Result r = decide("Red Hat", "virgl (AMD Radeon Vega 8)");
        assertFalse(r.compatible);
        assertEquals("intelium.disabled.amd", r.reasonKey);
    }

    @Test
    @DisplayName("VirGL with a too-old Intel host GPU is disabled with too_old")
    void virglTooOldHostDisabled() {
        IntelGpuClassifier.Result r = decide("Red Hat",
                "virgl (Mesa Intel(R) HD Graphics 4000 (IVB GT2))");
        assertFalse(r.compatible);
        assertEquals("intelium.disabled.too_old", r.reasonKey);
        assertEquals(IntelGpuGeneration.PRE_GEN9, r.generation);
    }

    @Test
    @DisplayName("Vendor 'Intel Corporation' is Intel, not AMD ('ati' in 'Corporation')")
    void intelCorporationIsIntel() {
        // Regression: "Corpor-ati-on" used to satisfy the raw "ati" substring
        // check, refusing supported Intel hardware with the AMD message.
        IntelGpuClassifier.Result r = decide("Intel Corporation",
                "Intel(R) UHD Graphics 620");
        assertTrue(r.compatible);
        assertEquals(IntelGpuGeneration.GEN9_5_KABY_COFFEE, r.generation);
    }

    @Test
    @DisplayName("Vendor 'Microsoft Corporation' is unknown, not AMD")
    void microsoftCorporationIsUnknown() {
        IntelGpuClassifier.Result r = decide("Microsoft Corporation", "D3D12 (WARP)");
        assertFalse(r.compatible);
        assertEquals("intelium.disabled.unknown_gpu", r.reasonKey);
    }

    @Test
    @DisplayName("Real ATI vendor strings are still refused as AMD")
    void realAtiStillRefused() {
        IntelGpuClassifier.Result r = decide("ATI Technologies Inc.",
                "ATI Radeon HD 5770");
        assertFalse(r.compatible);
        assertEquals("intelium.disabled.amd", r.reasonKey);
    }

    @Test
    @DisplayName("Workstation Xeon iGPU (HD Graphics P630) is supported")
    void workstationIgpuSupported() {
        IntelGpuClassifier.Result r = decide("Intel", "Intel(R) HD Graphics P630");
        assertTrue(r.compatible);
        assertEquals(IntelGpuGeneration.GEN9_5_KABY_COFFEE, r.generation);
    }
}
