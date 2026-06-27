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
    @DisplayName("recommendedWorkers returns at most 8 (highest ceiling)")
    void atMostEightForEveryGen(IntelGpuGeneration gen) {
        assertTrue(ChunkBuilderTuner.recommendedWorkers(gen) <= 8);
    }

    @ParameterizedTest
    @EnumSource(OptimizationProfile.class)
    @DisplayName("Never exceeds the available CPU count, for any profile")
    void neverExceedsCpu(OptimizationProfile profile) {
        for (int cpu = 1; cpu <= 32; cpu++) {
            for (IntelGpuGeneration gen : IntelGpuGeneration.values()) {
                int n = ChunkBuilderTuner.recommendedWorkers(gen, profile, cpu);
                assertTrue(n >= 1 && n <= cpu,
                        gen + "/" + profile + "/cpu=" + cpu + " -> " + n);
            }
        }
    }

    @Test
    @DisplayName("On a quad-core, Skylake is no longer starved to a single worker")
    void skylakeNotStarvedOnQuadCore() {
        // The old code hard-capped Skylake at 2 regardless of cores, which left
        // chunk meshing unable to keep up while moving. With 4 cores it should
        // use at least 2 build workers under every profile.
        for (OptimizationProfile p : OptimizationProfile.values()) {
            int n = ChunkBuilderTuner.recommendedWorkers(IntelGpuGeneration.GEN9_SKYLAKE, p, 4);
            assertTrue(n >= 2, p + " -> " + n);
        }
    }

    @Test
    @DisplayName("Smooth >= Balanced >= Max FPS for a given gen and CPU")
    void profilesAreOrdered() {
        for (int cpu = 1; cpu <= 32; cpu++) {
            for (IntelGpuGeneration gen : IntelGpuGeneration.values()) {
                int max = ChunkBuilderTuner.recommendedWorkers(gen, OptimizationProfile.MAX_FPS, cpu);
                int bal = ChunkBuilderTuner.recommendedWorkers(gen, OptimizationProfile.BALANCED, cpu);
                int smo = ChunkBuilderTuner.recommendedWorkers(gen, OptimizationProfile.SMOOTH, cpu);
                assertTrue(max <= bal, gen + " cpu=" + cpu + " max=" + max + " bal=" + bal);
                assertTrue(bal <= smo, gen + " cpu=" + cpu + " bal=" + bal + " smo=" + smo);
            }
        }
    }

    @Test
    @DisplayName("Stronger GPUs get at least as many workers as weaker ones")
    void strongerGetMoreOrEqual() {
        int cpu = 16; // plenty of cores so the per-gen ceiling is what differs
        int skylake = ChunkBuilderTuner.recommendedWorkers(
                IntelGpuGeneration.GEN9_SKYLAKE, OptimizationProfile.SMOOTH, cpu);
        int battlemage = ChunkBuilderTuner.recommendedWorkers(
                IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE, OptimizationProfile.SMOOTH, cpu);
        assertTrue(battlemage >= skylake, "battlemage=" + battlemage + " skylake=" + skylake);
    }

    @Test
    @DisplayName("UNKNOWN / PRE_GEN9 stay conservative (ceiling 2)")
    void conservativeForUnsupported() {
        int cpu = 16;
        for (IntelGpuGeneration gen : new IntelGpuGeneration[]{
                IntelGpuGeneration.UNKNOWN, IntelGpuGeneration.PRE_GEN9}) {
            for (OptimizationProfile p : OptimizationProfile.values()) {
                int n = ChunkBuilderTuner.recommendedWorkers(gen, p, cpu);
                assertTrue(n <= 2, gen + "/" + p + " -> " + n);
            }
        }
    }

    @Test
    @DisplayName("Fast load never reduces, and usually raises, the worker count")
    void fastLoadBoostsThroughput() {
        for (int cpu = 1; cpu <= 32; cpu++) {
            for (IntelGpuGeneration gen : IntelGpuGeneration.values()) {
                for (OptimizationProfile p : OptimizationProfile.values()) {
                    int normal = ChunkBuilderTuner.recommendedWorkers(gen, p, cpu, false);
                    int fast = ChunkBuilderTuner.recommendedWorkers(gen, p, cpu, true);
                    assertTrue(fast >= normal,
                            gen + "/" + p + "/cpu=" + cpu + " normal=" + normal + " fast=" + fast);
                    assertTrue(fast >= 1 && fast <= cpu, "fast out of range: " + fast);
                }
            }
        }
    }

    @Test
    @DisplayName("Fast load lifts a supported gen's ceiling on a many-core CPU")
    void fastLoadRaisesCeiling() {
        // Tiger Lake ceiling is 6; fast load lifts it to 8, so on 16 cores the
        // count should climb above the normal cap.
        int normal = ChunkBuilderTuner.recommendedWorkers(
                IntelGpuGeneration.GEN12_XE_LP, OptimizationProfile.BALANCED, 16, false);
        int fast = ChunkBuilderTuner.recommendedWorkers(
                IntelGpuGeneration.GEN12_XE_LP, OptimizationProfile.BALANCED, 16, true);
        assertTrue(fast > normal, "normal=" + normal + " fast=" + fast);
    }

    @Test
    @DisplayName("null profile is treated as BALANCED, never throws")
    void nullProfileIsBalanced() {
        int n = ChunkBuilderTuner.recommendedWorkers(IntelGpuGeneration.GEN12_XE_LP, null, 8);
        int bal = ChunkBuilderTuner.recommendedWorkers(
                IntelGpuGeneration.GEN12_XE_LP, OptimizationProfile.BALANCED, 8);
        assertEquals(bal, n);
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
