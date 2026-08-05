package com.intelium.optimization;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RenderBudget")
class RenderBudgetTest {

    private static final int HEIGHT = 1080;
    private static final double FOV = 70.0;
    private static final long FRAME_GAP = FrameBudget.DEFAULT_FRAME_GAP_NANOS * 4;

    private long clock;

    @BeforeEach
    void setUp() {
        RenderBudget.disable();
        clock = 5_000_000_000L;
    }

    @AfterEach
    void tearDown() {
        // Static state: leave nothing behind for the next test.
        RenderBudget.disable();
    }

    private void engage(CullingStrength level) {
        engage(level, level, level, false, 0.0);
    }

    private void engage(CullingStrength entities, CullingStrength blockEntities,
                        CullingStrength particles, boolean adaptive, double pressure) {
        RenderBudget.update(true, HEIGHT, FOV, entities, blockEntities, particles,
                adaptive, pressure);
    }

    /** Draws {@code n} block entities inside one frame; returns how many passed. */
    private int drawBlockEntities(int n) {
        int allowed = 0;
        for (int i = 0; i < n; i++) {
            clock += 1_000L;
            if (RenderBudget.allowBlockEntity(clock)) allowed++;
        }
        return allowed;
    }

    private int spawnParticles(int n) {
        int allowed = 0;
        for (int i = 0; i < n; i++) {
            if (RenderBudget.allowParticle()) allowed++;
        }
        return allowed;
    }

    // ---- Master state -----------------------------------------------------

    @Test
    @DisplayName("Nothing is culled until the engine is engaged")
    void inertBeforeUpdate() {
        assertFalse(RenderBudget.isActive());
        assertFalse(RenderBudget.entityCullingOn());
        assertFalse(RenderBudget.shouldCullEntity(0.25, 1e6));
        assertTrue(RenderBudget.allowBlockEntity(clock));
        assertTrue(RenderBudget.allowParticle());
    }

    @Test
    @DisplayName("Turning the master switch off stands every system down at once")
    void masterOffDisablesEverything() {
        engage(CullingStrength.AGGRESSIVE);
        assertTrue(RenderBudget.shouldCullEntity(0.25, 1e6));

        RenderBudget.update(false, HEIGHT, FOV, CullingStrength.AGGRESSIVE,
                CullingStrength.AGGRESSIVE, CullingStrength.AGGRESSIVE, true, 1.0);

        assertFalse(RenderBudget.isActive());
        assertFalse(RenderBudget.shouldCullEntity(0.25, 1e6));
        assertEquals(5000, drawBlockEntities(5000));
        assertEquals(5000, spawnParticles(5000));
    }

    @Test
    @DisplayName("Every system OFF is active but harmless")
    void allOffIsHarmless() {
        engage(CullingStrength.OFF);
        assertTrue(RenderBudget.isActive());
        assertFalse(RenderBudget.entityCullingOn());
        assertFalse(RenderBudget.blockEntityBudgetOn());
        assertFalse(RenderBudget.particleBudgetOn());
        assertFalse(RenderBudget.shouldCullEntity(0.25, 1e6));
        assertEquals(5000, drawBlockEntities(5000));
        assertEquals(5000, spawnParticles(5000));
    }

    @Test
    @DisplayName("Each system can be set independently")
    void systemsAreIndependent() {
        engage(CullingStrength.BALANCED, CullingStrength.OFF, CullingStrength.OFF, false, 0.0);
        assertTrue(RenderBudget.entityCullingOn());
        assertFalse(RenderBudget.blockEntityBudgetOn());
        assertFalse(RenderBudget.particleBudgetOn());
    }

    // ---- Entity budget ----------------------------------------------------

    @Test
    @DisplayName("A dropped item far off is culled; a zombie at the same spot is not")
    void entityCulling() {
        engage(CullingStrength.BALANCED);
        double distSq = 40.0 * 40.0;
        assertTrue(RenderBudget.shouldCullEntity(0.25, distSq));
        assertFalse(RenderBudget.shouldCullEntity(1.95, distSq));
    }

    @Test
    @DisplayName("Nothing close by is ever culled")
    void nearEntitiesAreSafe() {
        engage(CullingStrength.AGGRESSIVE);
        assertFalse(RenderBudget.shouldCullEntity(0.05, 4.0));
    }

    @Test
    @DisplayName("A stronger level culls strictly more")
    void strongerLevelCullsMore() {
        double distSq = 30.0 * 30.0;
        engage(CullingStrength.LIGHT);
        assertFalse(RenderBudget.shouldCullEntity(0.5, distSq));
        engage(CullingStrength.AGGRESSIVE);
        assertTrue(RenderBudget.shouldCullEntity(0.5, distSq));
    }

    @Test
    @DisplayName("An unknown camera means nothing is culled")
    void unknownCameraCullsNothing() {
        RenderBudget.update(true, 0, FOV, CullingStrength.AGGRESSIVE,
                CullingStrength.OFF, CullingStrength.OFF, false, 0.0);
        assertFalse(RenderBudget.entityCullingOn());
        assertFalse(RenderBudget.shouldCullEntity(0.25, 1e6));
    }

    @Test
    @DisplayName("Turning entity culling off says nothing about chests, and vice versa")
    void entityAndBlockEntityThresholdsAreIndependent() {
        engage(CullingStrength.OFF, CullingStrength.AGGRESSIVE, CullingStrength.OFF,
                false, 0.0);
        assertFalse(RenderBudget.entityCullingOn());
        assertFalse(RenderBudget.shouldCullEntity(0.25, 1e6));
        assertTrue(RenderBudget.shouldCullBlockEntity(1e6),
                "the block-entity level must drive block-entity culling");

        engage(CullingStrength.AGGRESSIVE, CullingStrength.OFF, CullingStrength.OFF,
                false, 0.0);
        assertTrue(RenderBudget.shouldCullEntity(0.25, 1e6));
        assertFalse(RenderBudget.shouldCullBlockEntity(1e6));
    }

    // ---- Block-entity budget ----------------------------------------------

    @Test
    @DisplayName("A block entity is measured as the block-sized thing it is")
    void blockEntityIsBlockSized() {
        engage(CullingStrength.BALANCED);
        // Balanced is 12px: a one-block object at 1080p survives to ~64 blocks.
        assertFalse(RenderBudget.shouldCullBlockEntity(40.0 * 40.0));
        assertTrue(RenderBudget.shouldCullBlockEntity(120.0 * 120.0));
    }

    @Test
    @DisplayName("A block entity underfoot is never culled by distance")
    void nearBlockEntitiesAreSafe() {
        engage(CullingStrength.AGGRESSIVE);
        assertFalse(RenderBudget.shouldCullBlockEntity(4.0));
    }

    @Test
    @DisplayName("A storage room is capped at the budget; a normal room is untouched")
    void blockEntityBudget() {
        engage(CullingStrength.BALANCED);
        int limit = RenderBudget.effectiveBlockEntityLimit();
        assertEquals(limit, drawBlockEntities(limit + 300));

        clock += FRAME_GAP;
        assertEquals(30, drawBlockEntities(30));
    }

    @Test
    @DisplayName("The block-entity allowance refills every frame")
    void blockEntityBudgetRefills() {
        engage(CullingStrength.AGGRESSIVE);
        int limit = RenderBudget.effectiveBlockEntityLimit();
        assertEquals(limit, drawBlockEntities(limit * 2));
        clock += FRAME_GAP;
        assertEquals(limit, drawBlockEntities(limit * 2));
    }

    @Test
    @DisplayName("What the previous frame skipped is reported")
    void blockEntitiesSkippedIsReported() {
        engage(CullingStrength.BALANCED);
        int limit = RenderBudget.effectiveBlockEntityLimit();
        drawBlockEntities(limit + 40);
        clock += FRAME_GAP;
        RenderBudget.allowBlockEntity(clock);
        assertEquals(limit, RenderBudget.blockEntitiesDrawn());
        assertEquals(40, RenderBudget.blockEntitiesSkipped());
    }

    // ---- Particle budget --------------------------------------------------

    @Test
    @DisplayName("A particle burst is cut off at the budget")
    void particleBudget() {
        engage(CullingStrength.BALANCED);
        int limit = RenderBudget.effectiveParticleLimit();
        assertEquals(limit, spawnParticles(limit + 2000));
    }

    @Test
    @DisplayName("The particle allowance refills every tick")
    void particleBudgetRefills() {
        engage(CullingStrength.BALANCED);
        int limit = RenderBudget.effectiveParticleLimit();
        assertEquals(limit, spawnParticles(limit + 100));
        RenderBudget.beginTick();
        assertEquals(limit, spawnParticles(limit + 100));
    }

    @Test
    @DisplayName("What the previous tick turned away is reported")
    void particlesSkippedIsReported() {
        engage(CullingStrength.BALANCED);
        int limit = RenderBudget.effectiveParticleLimit();
        spawnParticles(limit + 60);
        RenderBudget.beginTick();
        assertEquals(limit, RenderBudget.particlesSpawned());
        assertEquals(60, RenderBudget.particlesSkipped());
    }

    // ---- Adaptive behaviour -----------------------------------------------

    @Test
    @DisplayName("Pressure is ignored unless the adaptive switch is on")
    void pressureIgnoredWhenNotAdaptive() {
        engage(CullingStrength.BALANCED, CullingStrength.BALANCED, CullingStrength.BALANCED,
                false, 1.0);
        assertEquals(RenderBudgetTuning.entityMinPixels(CullingStrength.BALANCED),
                RenderBudget.effectiveEntityMinPixels(), 1e-9);
        assertEquals(RenderBudgetTuning.blockEntityBudget(CullingStrength.BALANCED),
                RenderBudget.effectiveBlockEntityLimit());
    }

    @Test
    @DisplayName("Under pressure the engine pushes harder, and eases off again after")
    void adaptiveTightensAndRelaxes() {
        engage(CullingStrength.BALANCED, CullingStrength.BALANCED, CullingStrength.BALANCED,
                true, 0.0);
        double relaxedPixels = RenderBudget.effectiveEntityMinPixels();
        int relaxedBlockEntities = RenderBudget.effectiveBlockEntityLimit();

        engage(CullingStrength.BALANCED, CullingStrength.BALANCED, CullingStrength.BALANCED,
                true, 1.0);
        assertTrue(RenderBudget.effectiveEntityMinPixels() > relaxedPixels);
        assertTrue(RenderBudget.effectiveBlockEntityLimit() < relaxedBlockEntities);

        engage(CullingStrength.BALANCED, CullingStrength.BALANCED, CullingStrength.BALANCED,
                true, 0.0);
        assertEquals(relaxedPixels, RenderBudget.effectiveEntityMinPixels(), 1e-9);
        assertEquals(relaxedBlockEntities, RenderBudget.effectiveBlockEntityLimit());
    }

    @Test
    @DisplayName("Adaptive tightening never switches a system that is OFF on")
    void adaptiveNeverEnablesAnOffSystem() {
        engage(CullingStrength.OFF, CullingStrength.OFF, CullingStrength.OFF, true, 1.0);
        assertEquals(0.0, RenderBudget.effectiveEntityMinPixels());
        assertEquals(0, RenderBudget.effectiveBlockEntityLimit());
        assertEquals(0, RenderBudget.effectiveParticleLimit());
    }

    @Test
    @DisplayName("Even at full pressure the budgets keep a usable floor")
    void budgetsKeepAFloor() {
        engage(CullingStrength.AGGRESSIVE, CullingStrength.AGGRESSIVE,
                CullingStrength.AGGRESSIVE, true, 1.0);
        assertTrue(RenderBudget.effectiveBlockEntityLimit() >= 64);
        assertTrue(RenderBudget.effectiveParticleLimit() >= 128);
    }

    @Test
    @DisplayName("Disabling clears the counters, not just the limits")
    void disableClearsCounters() {
        engage(CullingStrength.BALANCED);
        spawnParticles(RenderBudget.effectiveParticleLimit() + 50);
        RenderBudget.beginTick();
        assertTrue(RenderBudget.particlesSkipped() > 0);

        RenderBudget.disable();
        assertEquals(0, RenderBudget.particlesSkipped());
        assertEquals(0, RenderBudget.blockEntitiesSkipped());
    }
}
