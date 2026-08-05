package com.intelium.optimization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RenderBudgetTuning")
class RenderBudgetTuningTest {

    private static final double FOV = 70.0;
    private static final int HEIGHT_1080P = 1080;

    // ---- Level tables ----------------------------------------------------

    @Test
    @DisplayName("OFF means every system is inert")
    void offIsInert() {
        assertEquals(0.0, RenderBudgetTuning.entityMinPixels(CullingStrength.OFF));
        assertEquals(0, RenderBudgetTuning.blockEntityBudget(CullingStrength.OFF));
        assertEquals(0, RenderBudgetTuning.particleBudget(CullingStrength.OFF));
    }

    @Test
    @DisplayName("Stronger levels cull more: pixel threshold rises monotonically")
    void pixelThresholdRises() {
        assertTrue(RenderBudgetTuning.entityMinPixels(CullingStrength.LIGHT)
                < RenderBudgetTuning.entityMinPixels(CullingStrength.BALANCED));
        assertTrue(RenderBudgetTuning.entityMinPixels(CullingStrength.BALANCED)
                < RenderBudgetTuning.entityMinPixels(CullingStrength.AGGRESSIVE));
    }

    @Test
    @DisplayName("Stronger levels cull more: count budgets fall monotonically")
    void budgetsFall() {
        assertTrue(RenderBudgetTuning.blockEntityBudget(CullingStrength.LIGHT)
                > RenderBudgetTuning.blockEntityBudget(CullingStrength.BALANCED));
        assertTrue(RenderBudgetTuning.blockEntityBudget(CullingStrength.BALANCED)
                > RenderBudgetTuning.blockEntityBudget(CullingStrength.AGGRESSIVE));
        assertTrue(RenderBudgetTuning.particleBudget(CullingStrength.LIGHT)
                > RenderBudgetTuning.particleBudget(CullingStrength.BALANCED));
        assertTrue(RenderBudgetTuning.particleBudget(CullingStrength.BALANCED)
                > RenderBudgetTuning.particleBudget(CullingStrength.AGGRESSIVE));
    }

    @ParameterizedTest
    @EnumSource(value = CullingStrength.class, names = {"LIGHT", "BALANCED", "AGGRESSIVE"})
    @DisplayName("Every active level leaves room for an ordinary scene")
    void activeLevelsAreRoomy(CullingStrength strength) {
        // A normal view holds a few dozen block entities and spawns a handful of
        // particles a tick; the budgets must not bite until well past that.
        assertTrue(RenderBudgetTuning.blockEntityBudget(strength) >= 128);
        assertTrue(RenderBudgetTuning.particleBudget(strength) >= 256);
    }

    // ---- Camera geometry --------------------------------------------------

    @Test
    @DisplayName("pixelScale matches h / (2 tan(fov/2))")
    void pixelScaleFormula() {
        double expected = HEIGHT_1080P / (2.0 * Math.tan(Math.toRadians(FOV) / 2.0));
        assertEquals(expected, RenderBudgetTuning.pixelScale(HEIGHT_1080P, FOV), 1e-9);
    }

    @Test
    @DisplayName("A taller framebuffer means more pixels per block")
    void tallerScreenSeesMore() {
        assertTrue(RenderBudgetTuning.pixelScale(2160, FOV)
                > RenderBudgetTuning.pixelScale(1080, FOV));
    }

    @Test
    @DisplayName("A wider field of view means fewer pixels per block")
    void widerFovSeesLess() {
        assertTrue(RenderBudgetTuning.pixelScale(HEIGHT_1080P, 100.0)
                < RenderBudgetTuning.pixelScale(HEIGHT_1080P, 50.0));
    }

    @Test
    @DisplayName("An absurd field of view is clamped instead of producing nonsense")
    void fovIsClamped() {
        assertEquals(RenderBudgetTuning.pixelScale(HEIGHT_1080P, 30.0),
                RenderBudgetTuning.pixelScale(HEIGHT_1080P, -400.0), 1e-9);
        assertEquals(RenderBudgetTuning.pixelScale(HEIGHT_1080P, 110.0),
                RenderBudgetTuning.pixelScale(HEIGHT_1080P, 900.0), 1e-9);
    }

    @Test
    @DisplayName("A degenerate framebuffer yields no information")
    void degenerateScreen() {
        assertEquals(0.0, RenderBudgetTuning.pixelScale(0, FOV));
        assertEquals(0.0, RenderBudgetTuning.pixelScale(-1, FOV));
    }

    // ---- The culling decision --------------------------------------------

    @Test
    @DisplayName("Nothing inside the never-cull radius is ever culled")
    void nearThingsAreSafe() {
        double scale = RenderBudgetTuning.pixelScale(HEIGHT_1080P, FOV);
        double justInside = RenderBudgetTuning.NEVER_CULL_RADIUS_SQ - 0.01;
        // A speck of an entity, an absurd threshold - still kept, because it is close.
        assertFalse(RenderBudgetTuning.tooSmallToDraw(0.01, justInside, scale, 1000.0));
    }

    @Test
    @DisplayName("A dropped item far away is culled, the same item up close is not")
    void smallAndFarIsCulled() {
        double scale = RenderBudgetTuning.pixelScale(HEIGHT_1080P, FOV);
        double minPixels = RenderBudgetTuning.entityMinPixels(CullingStrength.BALANCED);
        assertTrue(RenderBudgetTuning.tooSmallToDraw(0.25, 60.0 * 60.0, scale, minPixels));
        assertFalse(RenderBudgetTuning.tooSmallToDraw(0.25, 14.0 * 14.0, scale, minPixels));
    }

    @Test
    @DisplayName("A big mob survives where a small one is culled, at the same distance")
    void sizeDecidesNotJustDistance() {
        double scale = RenderBudgetTuning.pixelScale(HEIGHT_1080P, FOV);
        double minPixels = RenderBudgetTuning.entityMinPixels(CullingStrength.BALANCED);
        double distSq = 40.0 * 40.0;
        assertTrue(RenderBudgetTuning.tooSmallToDraw(0.25, distSq, scale, minPixels),
                "a dropped item at 40 blocks is a few pixels");
        assertFalse(RenderBudgetTuning.tooSmallToDraw(1.95, distSq, scale, minPixels),
                "a zombie at 40 blocks is plainly visible");
    }

    @Test
    @DisplayName("Missing inputs always answer 'do not cull'")
    void missingInputsAreSafe() {
        double scale = RenderBudgetTuning.pixelScale(HEIGHT_1080P, FOV);
        assertFalse(RenderBudgetTuning.tooSmallToDraw(0.25, 1e6, scale, 0.0), "culling off");
        assertFalse(RenderBudgetTuning.tooSmallToDraw(0.25, 1e6, 0.0, 12.0), "camera unknown");
        assertFalse(RenderBudgetTuning.tooSmallToDraw(0.0, 1e6, scale, 12.0), "size unknown");
        assertFalse(RenderBudgetTuning.tooSmallToDraw(-1.0, 1e6, scale, 12.0), "size nonsense");
    }

    @Test
    @DisplayName("The cull range scales with resolution: 4K keeps what 720p drops")
    void resolutionChangesTheRange() {
        double minPixels = RenderBudgetTuning.entityMinPixels(CullingStrength.BALANCED);
        double distSq = 40.0 * 40.0;
        assertTrue(RenderBudgetTuning.tooSmallToDraw(0.5, distSq,
                RenderBudgetTuning.pixelScale(720, FOV), minPixels));
        assertFalse(RenderBudgetTuning.tooSmallToDraw(0.5, distSq,
                RenderBudgetTuning.pixelScale(2160, FOV), minPixels));
    }

    // ---- Adaptive tightening ----------------------------------------------

    @Test
    @DisplayName("Zero pressure changes nothing; full pressure doubles the threshold")
    void thresholdTightening() {
        assertEquals(12.0, RenderBudgetTuning.tightenThreshold(12.0, 0.0), 1e-9);
        assertEquals(24.0, RenderBudgetTuning.tightenThreshold(12.0, 1.0), 1e-9);
        assertEquals(18.0, RenderBudgetTuning.tightenThreshold(12.0, 0.5), 1e-9);
    }

    @Test
    @DisplayName("Out-of-range pressure is clamped, not extrapolated")
    void thresholdTighteningClamps() {
        assertEquals(12.0, RenderBudgetTuning.tightenThreshold(12.0, -5.0), 1e-9);
        assertEquals(24.0, RenderBudgetTuning.tightenThreshold(12.0, 9.0), 1e-9);
    }

    @Test
    @DisplayName("Zero pressure leaves a budget alone; full pressure halves it")
    void budgetTightening() {
        assertEquals(256, RenderBudgetTuning.tightenBudget(256, 0.0, 64));
        assertEquals(128, RenderBudgetTuning.tightenBudget(256, 1.0, 64));
    }

    @Test
    @DisplayName("A budget never falls below its floor")
    void budgetFloor() {
        assertEquals(64, RenderBudgetTuning.tightenBudget(65, 1.0, 64));
        assertEquals(64, RenderBudgetTuning.tightenBudget(128, 1.0, 64));
    }

    @Test
    @DisplayName("Tightening never hands back more room than it was given")
    void tighteningNeverLoosens() {
        // A floor above the base budget must not turn "tighten" into "loosen".
        assertEquals(1, RenderBudgetTuning.tightenBudget(1, 1.0, 64));
        assertEquals(10, RenderBudgetTuning.tightenBudget(10, 0.0, 512));
        for (int base = 1; base <= 600; base++) {
            assertTrue(RenderBudgetTuning.tightenBudget(base, 0.5, 64) <= base,
                    "tightening raised the budget at base " + base);
        }
    }

    @Test
    @DisplayName("Unlimited stays unlimited under any pressure")
    void unlimitedStaysUnlimited() {
        assertEquals(0, RenderBudgetTuning.tightenBudget(0, 1.0, 64));
        assertEquals(0, RenderBudgetTuning.tightenBudget(-5, 0.0, 64));
    }

    @Test
    @DisplayName("Tightening is monotonic in pressure")
    void tighteningIsMonotonic() {
        int previous = Integer.MAX_VALUE;
        for (double p = 0.0; p <= 1.0; p += 0.1) {
            int budget = RenderBudgetTuning.tightenBudget(512, p, 64);
            assertTrue(budget <= previous, "budget rose at pressure " + p);
            previous = budget;
        }
    }
}
