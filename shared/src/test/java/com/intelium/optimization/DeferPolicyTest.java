package com.intelium.optimization;

import com.intelium.RenderBackend;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Chunk defer policy")
class DeferPolicyTest {

    private static DeferDecision decide(ChunkLoadingMode mode, OptimizationProfile profile,
                                        RenderBackend backend, boolean throttled) {
        return DeferPolicy.decide(mode, profile, backend, throttled);
    }

    @Nested
    @DisplayName("Profile names match behaviour on OpenGL")
    class OpenGlProfiles {

        @Test
        @DisplayName("Max FPS defers conservatively, whatever the frame rate")
        void maxFpsAlwaysDefers() {
            // The bug this exists to prevent: Max FPS forcing an upload into a
            // frame that had no room for it, which costs the very thing the
            // profile is named after.
            assertEquals(DeferDecision.ALWAYS, decide(ChunkLoadingMode.FAST,
                    OptimizationProfile.MAX_FPS, RenderBackend.OPENGL, false));
            assertEquals(DeferDecision.ALWAYS, decide(ChunkLoadingMode.FAST,
                    OptimizationProfile.MAX_FPS, RenderBackend.OPENGL, true));
        }

        @Test
        @DisplayName("Smooth keeps one-frame deferral even under pressure")
        void smoothNeverSlowsChunkArrival() {
            // Smooth exists to keep terrain ahead of the player. Slowing chunk
            // arrival to protect frame pacing would be the other profile.
            assertEquals(DeferDecision.ONE_FRAME, decide(ChunkLoadingMode.FAST,
                    OptimizationProfile.SMOOTH, RenderBackend.OPENGL, false));
            assertEquals(DeferDecision.ONE_FRAME, decide(ChunkLoadingMode.FAST,
                    OptimizationProfile.SMOOTH, RenderBackend.OPENGL, true));
        }

        @Test
        @DisplayName("Balanced is the only profile the governor steers")
        void balancedFollowsTheGovernor() {
            assertEquals(DeferDecision.ONE_FRAME, decide(ChunkLoadingMode.FAST,
                    OptimizationProfile.BALANCED, RenderBackend.OPENGL, false));
            assertEquals(DeferDecision.ALWAYS, decide(ChunkLoadingMode.FAST,
                    OptimizationProfile.BALANCED, RenderBackend.OPENGL, true));
        }

        @Test
        @DisplayName("Max FPS and Smooth no longer produce identical behaviour")
        void profilesAreDistinguishable() {
            DeferDecision maxFps = decide(ChunkLoadingMode.FAST,
                    OptimizationProfile.MAX_FPS, RenderBackend.OPENGL, false);
            DeferDecision smooth = decide(ChunkLoadingMode.FAST,
                    OptimizationProfile.SMOOTH, RenderBackend.OPENGL, false);
            assertFalse(maxFps == smooth,
                    "choosing Max FPS and choosing Smooth must not do the same thing");
        }
    }

    @Nested
    @DisplayName("Turbo is a user decision")
    class Turbo {

        @ParameterizedTest
        @EnumSource(OptimizationProfile.class)
        @DisplayName("Zero-frame under every profile, on OpenGL")
        void turboIgnoresProfile(OptimizationProfile profile) {
            assertEquals(DeferDecision.ZERO_FRAMES, decide(ChunkLoadingMode.TURBO,
                    profile, RenderBackend.OPENGL, false));
        }

        @ParameterizedTest
        @EnumSource(RenderBackend.class)
        @DisplayName("Never adaptively slowed, on any backend")
        void turboIsNeverThrottled(RenderBackend backend) {
            assertEquals(DeferDecision.ZERO_FRAMES, decide(ChunkLoadingMode.TURBO,
                    OptimizationProfile.MAX_FPS, backend, true));
        }
    }

    @Nested
    @DisplayName("Backend isolation")
    class Backends {

        @ParameterizedTest
        @EnumSource(OptimizationProfile.class)
        @DisplayName("Vulkan keeps the established one-frame behaviour")
        void vulkanIsUntouched(OptimizationProfile profile) {
            assertEquals(DeferDecision.ONE_FRAME, decide(ChunkLoadingMode.FAST,
                    profile, RenderBackend.VULKAN, false));
            assertEquals(DeferDecision.ONE_FRAME, decide(ChunkLoadingMode.FAST,
                    profile, RenderBackend.VULKAN, true));
        }

        @ParameterizedTest
        @EnumSource(OptimizationProfile.class)
        @DisplayName("An unknown backend is never handed OpenGL's policy")
        void unknownBackendIsUntouched(OptimizationProfile profile) {
            assertEquals(DeferDecision.ONE_FRAME, decide(ChunkLoadingMode.FAST,
                    profile, RenderBackend.UNKNOWN, true));
        }

        @Test
        @DisplayName("Only Balanced on OpenGL Fast is governed")
        void governedCombination() {
            assertTrue(DeferPolicy.governed(ChunkLoadingMode.FAST,
                    OptimizationProfile.BALANCED, RenderBackend.OPENGL));
            assertFalse(DeferPolicy.governed(ChunkLoadingMode.FAST,
                    OptimizationProfile.MAX_FPS, RenderBackend.OPENGL));
            assertFalse(DeferPolicy.governed(ChunkLoadingMode.FAST,
                    OptimizationProfile.SMOOTH, RenderBackend.OPENGL));
            assertFalse(DeferPolicy.governed(ChunkLoadingMode.FAST,
                    OptimizationProfile.BALANCED, RenderBackend.VULKAN));
            assertFalse(DeferPolicy.governed(ChunkLoadingMode.TURBO,
                    OptimizationProfile.BALANCED, RenderBackend.OPENGL));
            assertFalse(DeferPolicy.governed(ChunkLoadingMode.OFF,
                    OptimizationProfile.BALANCED, RenderBackend.OPENGL));
        }
    }

    @Nested
    @DisplayName("Off and malformed input")
    class SafeDirections {

        @ParameterizedTest
        @EnumSource(OptimizationProfile.class)
        @DisplayName("Off restores the user's own setting under every profile")
        void offKeepsUserSetting(OptimizationProfile profile) {
            assertEquals(DeferDecision.KEEP_USER, decide(ChunkLoadingMode.OFF,
                    profile, RenderBackend.OPENGL, false));
        }

        @Test
        @DisplayName("A null mode is treated as Off, never as consent to override")
        void nullModeIsOff() {
            assertEquals(DeferDecision.KEEP_USER,
                    decide(null, OptimizationProfile.MAX_FPS, RenderBackend.OPENGL, false));
        }

        @Test
        @DisplayName("A null profile falls back to Balanced, a null backend to unknown")
        void nullsFallBack() {
            assertEquals(DeferDecision.ONE_FRAME,
                    decide(ChunkLoadingMode.FAST, null, RenderBackend.OPENGL, false));
            assertEquals(DeferDecision.ALWAYS,
                    decide(ChunkLoadingMode.FAST, null, RenderBackend.OPENGL, true));
            assertEquals(DeferDecision.ONE_FRAME,
                    decide(ChunkLoadingMode.FAST, OptimizationProfile.MAX_FPS, null, false));
        }
    }
}
