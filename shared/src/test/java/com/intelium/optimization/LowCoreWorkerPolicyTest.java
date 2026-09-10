package com.intelium.optimization;

import com.intelium.IntelGpuGeneration;
import com.intelium.RenderBackend;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The worker policy for the machine this mod is named after: an HD Graphics 520
 * in a two-core, four-thread mobile CPU, rendering through OpenGL.
 */
@DisplayName("Low-core Gen 9 OpenGL worker policy")
class LowCoreWorkerPolicyTest {

    private static int workers(IntelGpuGeneration gen, OptimizationProfile profile,
                               int cpu, boolean fastLoad, RenderBackend backend) {
        return ChunkBuilderTuner.recommendedWorkers(gen, profile, cpu, fastLoad, backend);
    }

    @Nested
    @DisplayName("HD 520 class: 2 and 4 logical processors")
    class Gen9LowCore {

        @ParameterizedTest
        @ValueSource(ints = {2, 3, 4})
        @DisplayName("Max FPS uses a single worker, leaving a core for render and driver")
        void maxFpsUsesOneWorker(int cpu) {
            assertEquals(1, workers(IntelGpuGeneration.GEN9_SKYLAKE,
                    OptimizationProfile.MAX_FPS, cpu, true, RenderBackend.OPENGL));
            assertEquals(1, workers(IntelGpuGeneration.GEN9_SKYLAKE,
                    OptimizationProfile.MAX_FPS, cpu, false, RenderBackend.OPENGL));
        }

        @ParameterizedTest
        @CsvSource({
            "2, BALANCED, 1",
            "2, SMOOTH,   1",
            "4, BALANCED, 2",
            "4, SMOOTH,   2",
        })
        @DisplayName("Balanced and Smooth never exceed two workers")
        void balancedAndSmoothCapAtTwo(int cpu, OptimizationProfile profile, int expected) {
            assertEquals(expected, workers(IntelGpuGeneration.GEN9_SKYLAKE,
                    profile, cpu, true, RenderBackend.OPENGL));
        }

        @Test
        @DisplayName("Smooth on 4 logical processors no longer takes three of them")
        void smoothNoLongerStarvesTheRenderThread() {
            // The regression this exists for: three mesh threads on a two-core
            // SMT part, contending with the render thread and the Intel driver.
            int result = workers(IntelGpuGeneration.GEN9_SKYLAKE,
                    OptimizationProfile.SMOOTH, 4, true, RenderBackend.OPENGL);
            assertTrue(result <= 2, "expected at most 2 workers, got " + result);
        }

        @ParameterizedTest
        @EnumSource(OptimizationProfile.class)
        @DisplayName("Gen 9.5 is treated the same as Gen 9")
        void gen95SharesThePolicy(OptimizationProfile profile) {
            assertTrue(workers(IntelGpuGeneration.GEN9_5_KABY_COFFEE,
                    profile, 4, true, RenderBackend.OPENGL) <= 2);
        }

        @ParameterizedTest
        @EnumSource(OptimizationProfile.class)
        @DisplayName("Always at least one worker: chunks must still get built")
        void neverZero(OptimizationProfile profile) {
            assertTrue(workers(IntelGpuGeneration.GEN9_SKYLAKE, profile, 1, true,
                    RenderBackend.OPENGL) >= 1);
            assertTrue(workers(IntelGpuGeneration.GEN9_SKYLAKE, profile, 2, false,
                    RenderBackend.OPENGL) >= 1);
        }
    }

    @Nested
    @DisplayName("The policy is a ceiling, and narrow")
    class Scope {

        @ParameterizedTest
        @ValueSource(ints = {6, 8, 12, 16})
        @DisplayName("A Gen 9 desktop with more processors is not affected")
        void higherCoreCountsUnaffected(int cpu) {
            int maxFps = workers(IntelGpuGeneration.GEN9_SKYLAKE,
                    OptimizationProfile.MAX_FPS, cpu, true, RenderBackend.OPENGL);
            assertTrue(maxFps > 1,
                    "an 8-thread machine should not be held to the 2-core policy");
        }

        @ParameterizedTest
        @EnumSource(OptimizationProfile.class)
        @DisplayName("Vulkan keeps its own reservation policy")
        void vulkanUntouched(OptimizationProfile profile) {
            int vulkan = workers(IntelGpuGeneration.GEN9_SKYLAKE, profile, 4, true,
                    RenderBackend.VULKAN);
            // Vulkan's own cpu-2 reservation, not the low-core OpenGL ceiling.
            assertEquals(2, vulkan);
        }

        @Test
        @DisplayName("An unknown backend is never handed the OpenGL ceiling")
        void unknownBackendUntouched() {
            int unknown = workers(IntelGpuGeneration.GEN9_SKYLAKE,
                    OptimizationProfile.SMOOTH, 4, true, RenderBackend.UNKNOWN);
            assertEquals(3, unknown, "the pre-existing policy must be preserved");
        }

        @ParameterizedTest
        @EnumSource(value = IntelGpuGeneration.class,
                names = {"GEN11_ICE_LAKE", "GEN12_XE_LP", "XE_HPG_ARC_ALCHEMIST",
                         "XE2_LUNAR_BATTLEMAGE"})
        @DisplayName("Newer generations keep their existing counts")
        void newerGenerationsUntouched(IntelGpuGeneration gen) {
            int smooth = workers(gen, OptimizationProfile.SMOOTH, 4, true,
                    RenderBackend.OPENGL);
            assertEquals(3, smooth);
        }

        @Test
        @DisplayName("Never raises a count the generic policy set lower")
        void onlyEverLowers() {
            for (int cpu = 1; cpu <= 4; cpu++) {
                for (OptimizationProfile profile : OptimizationProfile.values()) {
                    int gen9 = workers(IntelGpuGeneration.GEN9_SKYLAKE, profile, cpu, true,
                            RenderBackend.OPENGL);
                    int gen11 = workers(IntelGpuGeneration.GEN11_ICE_LAKE, profile, cpu, true,
                            RenderBackend.OPENGL);
                    assertTrue(gen9 <= gen11,
                            "cpu=" + cpu + " profile=" + profile
                                    + ": the low-core ceiling must only ever lower");
                }
            }
        }
    }
}
