package com.intelium.optimization;

import com.intelium.IntelGpuGeneration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OcclusionTuner")
class OcclusionTunerTest {

    @Test
    @DisplayName("Skylake tightens to 0.92")
    void skylakeTight() {
        OcclusionTuner.applyForCurrentFrame(IntelGpuGeneration.GEN9_SKYLAKE);
        assertEquals(0.92f, OcclusionTuner.multiplier(), 0.0001f);
    }

    @Test
    @DisplayName("Gen 9.5 tightens to 0.92")
    void gen95Tight() {
        OcclusionTuner.applyForCurrentFrame(IntelGpuGeneration.GEN9_5_KABY_COFFEE);
        assertEquals(0.92f, OcclusionTuner.multiplier(), 0.0001f);
    }

    @Test
    @DisplayName("Ice Lake uses 0.95")
    void iceLake() {
        OcclusionTuner.applyForCurrentFrame(IntelGpuGeneration.GEN11_ICE_LAKE);
        assertEquals(0.95f, OcclusionTuner.multiplier(), 0.0001f);
    }

    @Test
    @DisplayName("Xe-LP uses 0.97")
    void xeLp() {
        OcclusionTuner.applyForCurrentFrame(IntelGpuGeneration.GEN12_XE_LP);
        assertEquals(0.97f, OcclusionTuner.multiplier(), 0.0001f);
    }

    @Test
    @DisplayName("Arc uses 1.00 (no tightening, GPU is fast)")
    void arc() {
        OcclusionTuner.applyForCurrentFrame(IntelGpuGeneration.XE_HPG_ARC_ALCHEMIST);
        assertEquals(1.00f, OcclusionTuner.multiplier(), 0.0001f);
    }

    @Test
    @DisplayName("Battlemage uses 1.00")
    void battlemage() {
        OcclusionTuner.applyForCurrentFrame(IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE);
        assertEquals(1.00f, OcclusionTuner.multiplier(), 0.0001f);
    }

    @ParameterizedTest
    @EnumSource(IntelGpuGeneration.class)
    @DisplayName("Multiplier is in (0, 1] for every gen")
    void multiplierInRange(IntelGpuGeneration gen) {
        OcclusionTuner.applyForCurrentFrame(gen);
        float m = OcclusionTuner.multiplier();
        assertTrue(m > 0f && m <= 1.0f, "multiplier=" + m + " for " + gen);
    }

    @ParameterizedTest
    @EnumSource(IntelGpuGeneration.class)
    @DisplayName("applyForCurrentFrame never crashes")
    void neverCrashes(IntelGpuGeneration gen) {
        assertDoesNotThrow(() -> OcclusionTuner.applyForCurrentFrame(gen));
    }

    @Test
    @DisplayName("Multiplier increases monotonically from skylake to arc")
    void monotonic() {
        OcclusionTuner.applyForCurrentFrame(IntelGpuGeneration.GEN9_SKYLAKE);
        float sk = OcclusionTuner.multiplier();
        OcclusionTuner.applyForCurrentFrame(IntelGpuGeneration.GEN11_ICE_LAKE);
        float ic = OcclusionTuner.multiplier();
        OcclusionTuner.applyForCurrentFrame(IntelGpuGeneration.GEN12_XE_LP);
        float xe = OcclusionTuner.multiplier();
        OcclusionTuner.applyForCurrentFrame(IntelGpuGeneration.XE_HPG_ARC_ALCHEMIST);
        float arc = OcclusionTuner.multiplier();
        assertTrue(sk <= ic);
        assertTrue(ic <= xe);
        assertTrue(xe <= arc);
    }

    @Test
    @DisplayName("UNKNOWN uses default 1.00")
    void unknownDefault() {
        OcclusionTuner.applyForCurrentFrame(IntelGpuGeneration.UNKNOWN);
        assertEquals(1.00f, OcclusionTuner.multiplier(), 0.0001f);
    }

    @Test
    @DisplayName("PRE_GEN9 uses default 1.00")
    void preGen9Default() {
        OcclusionTuner.applyForCurrentFrame(IntelGpuGeneration.PRE_GEN9);
        assertEquals(1.00f, OcclusionTuner.multiplier(), 0.0001f);
    }

    @Test
    @DisplayName("Class is final / utility helper")
    void classIsFinal() {
        assertTrue(java.lang.reflect.Modifier.isFinal(OcclusionTuner.class.getModifiers()));
    }

    @Test
    @DisplayName("Constructor is private")
    void constructorPrivate() throws NoSuchMethodException {
        var ctor = OcclusionTuner.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(ctor.getModifiers()));
    }

    @Test
    @DisplayName("multiplier method returns the latest applied value (deterministic)")
    void deterministic() {
        OcclusionTuner.applyForCurrentFrame(IntelGpuGeneration.GEN9_SKYLAKE);
        assertEquals(OcclusionTuner.multiplier(), OcclusionTuner.multiplier(), 0.0001f);
    }
}
