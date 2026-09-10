package com.intelium.optimization;

import com.intelium.RenderBackend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The block-entity budget's two promises: it never drops something you are
 * standing next to, and it resets exactly once per frame.
 */
@DisplayName("Block-entity budget fairness")
class BlockEntityFairnessTest {

    /** Comfortably inside the never-cull radius (12 blocks). */
    private static final double NEAR_SQ = 4.0 * 4.0;
    /** Far enough to be a candidate for the ceiling, close enough not to be culled. */
    private static final double FAR_SQ = 20.0 * 20.0;

    @BeforeEach
    void enableBudget() {
        // A tall framebuffer and a narrow FOV keep the apparent-size test from
        // culling the far sample, so only the count is under test here.
        RenderBudget.update(true, 4320, 30.0,
                CullingStrength.OFF, CullingStrength.OFF, CullingStrength.OFF,
                false, 0.0, RenderBackend.UNKNOWN);
    }

    @AfterEach
    void standDown() {
        RenderBudget.disable();
    }

    private static void withBlockEntityLimit(CullingStrength level) {
        RenderBudget.update(true, 4320, 30.0,
                CullingStrength.OFF, level, CullingStrength.OFF,
                false, 0.0, RenderBackend.UNKNOWN);
    }

    @Nested
    @DisplayName("Nearby block entities")
    class NearbyAlwaysDraw {

        @Test
        @DisplayName("A chest at your feet draws even after the frame's allowance is gone")
        void nearSurvivesAnExhaustedBudget() {
            withBlockEntityLimit(CullingStrength.AGGRESSIVE);
            RenderBudget.beginFrame();

            // Burn the whole allowance on distant block entities first - the
            // worst case for a first-come-first-served counter.
            int exhausted = 0;
            for (int i = 0; i < 10_000; i++) {
                if (RenderBudget.shouldSkipBlockEntity(FAR_SQ)) {
                    exhausted++;
                }
            }
            assertTrue(exhausted > 0, "the ceiling must actually bind for this test to mean anything");

            assertFalse(RenderBudget.shouldSkipBlockEntity(NEAR_SQ),
                    "a block entity inside the never-cull radius must draw regardless of "
                            + "where it fell in the iteration");
        }

        @Test
        @DisplayName("Iteration order cannot decide which nearby chest disappears")
        void orderIndependentForNearby() {
            withBlockEntityLimit(CullingStrength.AGGRESSIVE);
            RenderBudget.beginFrame();
            for (int i = 0; i < 5_000; i++) {
                assertFalse(RenderBudget.shouldSkipBlockEntity(NEAR_SQ),
                        "nearby block entity " + i + " was refused");
            }
        }
    }

    @Nested
    @DisplayName("Frame boundaries")
    class Boundaries {

        @Test
        @DisplayName("The allowance comes back the next frame")
        void budgetResetsPerFrame() {
            withBlockEntityLimit(CullingStrength.AGGRESSIVE);
            RenderBudget.beginFrame();
            int firstFrameDrawn = drawUntilRefused();
            assertTrue(firstFrameDrawn > 0);

            RenderBudget.beginFrame();
            int secondFrameDrawn = drawUntilRefused();
            assertEquals(firstFrameDrawn, secondFrameDrawn,
                    "each frame must get the same allowance");
        }

        @Test
        @DisplayName("Once told a boundary, it stops inferring them")
        void explicitBoundariesTakeOver() {
            withBlockEntityLimit(CullingStrength.BALANCED);
            RenderBudget.beginFrame();
            assertTrue(RenderBudget.hasFrameBoundaryHook());
        }

        private int drawUntilRefused() {
            int drawn = 0;
            for (int i = 0; i < 10_000; i++) {
                if (RenderBudget.shouldSkipBlockEntity(FAR_SQ)) return drawn;
                drawn++;
            }
            return drawn;
        }
    }

    @Nested
    @DisplayName("Off means off")
    class Disabled {

        @Test
        @DisplayName("A disabled budget refuses nothing")
        void offDrawsEverything() {
            withBlockEntityLimit(CullingStrength.OFF);
            RenderBudget.beginFrame();
            for (int i = 0; i < 5_000; i++) {
                assertFalse(RenderBudget.shouldSkipBlockEntity(FAR_SQ));
            }
        }

        @Test
        @DisplayName("A stood-down engine refuses nothing")
        void engineOffDrawsEverything() {
            RenderBudget.disable();
            for (int i = 0; i < 1_000; i++) {
                assertFalse(RenderBudget.shouldSkipBlockEntity(FAR_SQ));
            }
        }
    }

    @Nested
    @DisplayName("Camera entity publication")
    class CameraEntity {

        @Test
        @DisplayName("An unknown camera exempts nothing")
        void unknownCameraExemptsNothing() {
            RenderBudget.setCameraEntityId(RenderBudget.NO_CAMERA_ENTITY);
            assertFalse(RenderBudget.isCameraEntity(0));
            assertFalse(RenderBudget.isCameraEntity(RenderBudget.NO_CAMERA_ENTITY));
            assertFalse(RenderBudget.isCameraEntity(12345));
        }

        @Test
        @DisplayName("The published id, and only it, is the camera")
        void publishedIdMatches() {
            RenderBudget.setCameraEntityId(4242);
            assertTrue(RenderBudget.isCameraEntity(4242));
            assertFalse(RenderBudget.isCameraEntity(4243));
            assertEquals(4242, RenderBudget.cameraEntityId());
        }
    }
}
