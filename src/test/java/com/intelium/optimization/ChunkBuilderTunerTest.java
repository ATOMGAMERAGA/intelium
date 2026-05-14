package com.intelium.optimization;

import com.intelium.IntelGpuGeneration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChunkBuilderTuner")
class ChunkBuilderTunerTest {

    @ParameterizedTest
    @EnumSource(IntelGpuGeneration.class)
    @DisplayName("recommendedWorkers returns a positive number for every gen")
    void positiveForEveryGen(IntelGpuGeneration gen) {
        assertTrue(ChunkBuilderTuner.recommendedWorkers(gen) >= 1);
    }

    @ParameterizedTest
    @EnumSource(IntelGpuGeneration.class)
    @DisplayName("recommendedWorkers returns at most 8")
    void atMostEightForEveryGen(IntelGpuGeneration gen) {
        assertTrue(ChunkBuilderTuner.recommendedWorkers(gen) <= 8);
    }

    @Test
    @DisplayName("Skylake gets 1-2 workers")
    void skylakeOneOrTwo() {
        int n = ChunkBuilderTuner.recommendedWorkers(IntelGpuGeneration.GEN9_SKYLAKE);
        assertTrue(n >= 1 && n <= 2, "expected 1-2, got " + n);
    }

    @Test
    @DisplayName("Gen 9.5 gets 1-2 workers")
    void gen95OneOrTwo() {
        int n = ChunkBuilderTuner.recommendedWorkers(IntelGpuGeneration.GEN9_5_KABY_COFFEE);
        assertTrue(n >= 1 && n <= 2, "expected 1-2, got " + n);
    }

    @Test
    @DisplayName("Ice Lake gets 2-3 workers")
    void iceLakeTwoOrThree() {
        int n = ChunkBuilderTuner.recommendedWorkers(IntelGpuGeneration.GEN11_ICE_LAKE);
        assertTrue(n >= 2 && n <= 3, "expected 2-3, got " + n);
    }

    @Test
    @DisplayName("Xe-LP gets 2-4 workers")
    void xeLpTwoToFour() {
        int n = ChunkBuilderTuner.recommendedWorkers(IntelGpuGeneration.GEN12_XE_LP);
        assertTrue(n >= 2 && n <= 4, "expected 2-4, got " + n);
    }

    @Test
    @DisplayName("Arc Alchemist gets 4-6 workers")
    void arcFourToSix() {
        int n = ChunkBuilderTuner.recommendedWorkers(IntelGpuGeneration.XE_HPG_ARC_ALCHEMIST);
        assertTrue(n >= 4 && n <= 6, "expected 4-6, got " + n);
    }

    @Test
    @DisplayName("Battlemage gets 4-8 workers")
    void battlemageFourToEight() {
        int n = ChunkBuilderTuner.recommendedWorkers(IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE);
        assertTrue(n >= 4 && n <= 8, "expected 4-8, got " + n);
    }

    @Test
    @DisplayName("UNKNOWN gets 1-2 (conservative default)")
    void unknownConservative() {
        int n = ChunkBuilderTuner.recommendedWorkers(IntelGpuGeneration.UNKNOWN);
        assertTrue(n >= 1 && n <= 2, "expected 1-2, got " + n);
    }

    @Test
    @DisplayName("PRE_GEN9 gets 1-2 (conservative default)")
    void preGen9Conservative() {
        int n = ChunkBuilderTuner.recommendedWorkers(IntelGpuGeneration.PRE_GEN9);
        assertTrue(n >= 1 && n <= 2, "expected 1-2, got " + n);
    }

    @Test
    @DisplayName("Battlemage >= Skylake worker count")
    void battlemageGreaterThanSkylake() {
        assertTrue(
                ChunkBuilderTuner.recommendedWorkers(IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE)
                        >= ChunkBuilderTuner.recommendedWorkers(IntelGpuGeneration.GEN9_SKYLAKE));
    }

    @Test
    @DisplayName("Worker count never exceeds available CPUs (clamp upper bound 8)")
    void neverExceedsClamp() {
        for (IntelGpuGeneration gen : IntelGpuGeneration.values()) {
            assertTrue(ChunkBuilderTuner.recommendedWorkers(gen) <= 8);
        }
    }

    @Test
    @DisplayName("Worker count is at least 1 always")
    void atLeastOne() {
        for (IntelGpuGeneration gen : IntelGpuGeneration.values()) {
            assertTrue(ChunkBuilderTuner.recommendedWorkers(gen) >= 1);
        }
    }

    @ParameterizedTest
    @EnumSource(IntelGpuGeneration.class)
    @DisplayName("Result is deterministic per gen")
    void deterministicPerGen(IntelGpuGeneration gen) {
        assertEquals(ChunkBuilderTuner.recommendedWorkers(gen),
                ChunkBuilderTuner.recommendedWorkers(gen));
    }

    @Test
    @DisplayName("Class is final / final-style helper")
    void classIsFinal() {
        assertTrue(java.lang.reflect.Modifier.isFinal(ChunkBuilderTuner.class.getModifiers()));
    }

    @Test
    @DisplayName("Constructor is private")
    void constructorPrivate() throws NoSuchMethodException {
        var ctor = ChunkBuilderTuner.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(ctor.getModifiers()));
    }
}
