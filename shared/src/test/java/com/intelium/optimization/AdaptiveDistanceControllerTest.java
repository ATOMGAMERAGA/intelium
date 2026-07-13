package com.intelium.optimization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AdaptiveDistanceController behaviour")
class AdaptiveDistanceControllerTest {

    private static final int TARGET = 60;
    private static final int BASE = 12;

    /** Feeds {@code ticks} identical samples, returning the last cap. */
    private static int feed(AdaptiveDistanceController c, int fps, int ticks) {
        int cap = 0;
        for (int i = 0; i < ticks; i++) cap = c.update(TARGET, fps, BASE);
        return cap;
    }

    @Test
    @DisplayName("Starts hands-off (no cap)")
    void startsInactive() {
        AdaptiveDistanceController c = new AdaptiveDistanceController();
        assertEquals(0, c.currentCap(BASE));
        assertEquals(0, c.reduction());
    }

    @Test
    @DisplayName("FPS at the target: never reduces")
    void steadyAtTarget() {
        AdaptiveDistanceController c = new AdaptiveDistanceController();
        assertEquals(0, feed(c, TARGET, 1000));
    }

    @Test
    @DisplayName("FPS inside the dead band (between 92% and 115%): never reduces")
    void deadBandHoldsSteady() {
        AdaptiveDistanceController c = new AdaptiveDistanceController();
        assertEquals(0, feed(c, 58, 1000)); // 58 > 60*0.92=55.2
        assertEquals(0, feed(c, 66, 1000)); // 66 < 60*1.15=69
    }

    @Test
    @DisplayName("Sustained low FPS steps the distance down one chunk")
    void stepsDownAfterHold() {
        AdaptiveDistanceController c = new AdaptiveDistanceController();
        assertEquals(0, feed(c, 40, AdaptiveDistanceController.DOWN_HOLD_TICKS - 1));
        assertEquals(BASE - 1, feed(c, 40, 1));
    }

    @Test
    @DisplayName("A short dip never triggers a step")
    void shortDipIgnored() {
        AdaptiveDistanceController c = new AdaptiveDistanceController();
        feed(c, 40, AdaptiveDistanceController.DOWN_HOLD_TICKS - 1);
        feed(c, TARGET, 1); // recovery resets the hold counter
        assertEquals(0, feed(c, 40, AdaptiveDistanceController.DOWN_HOLD_TICKS - 1));
    }

    @Test
    @DisplayName("Keeps stepping down while FPS stays low, but never below half the base")
    void floorsAtHalfBase() {
        AdaptiveDistanceController c = new AdaptiveDistanceController();
        int cap = feed(c, 20, AdaptiveDistanceController.DOWN_HOLD_TICKS * 100);
        assertEquals(AdaptiveDistanceController.floorFor(BASE), cap);
        assertEquals(BASE / 2, cap);
    }

    @Test
    @DisplayName("Sustained headroom steps the distance back up (slowly)")
    void stepsBackUp() {
        AdaptiveDistanceController c = new AdaptiveDistanceController();
        feed(c, 40, AdaptiveDistanceController.DOWN_HOLD_TICKS * 3); // down 3
        assertEquals(BASE - 3, c.currentCap(BASE));
        assertEquals(BASE - 3, feed(c, 90, AdaptiveDistanceController.UP_HOLD_TICKS - 1));
        assertEquals(BASE - 2, feed(c, 90, 1));
        // Fully recovers to hands-off with enough sustained headroom.
        feed(c, 90, AdaptiveDistanceController.UP_HOLD_TICKS * 3);
        assertEquals(0, c.currentCap(BASE));
    }

    @Test
    @DisplayName("Non-positive FPS samples are ignored")
    void ignoresZeroFps() {
        AdaptiveDistanceController c = new AdaptiveDistanceController();
        assertEquals(0, feed(c, 0, 1000));
        assertEquals(0, c.reduction());
    }

    @Test
    @DisplayName("Tiny base distances (at the vanilla minimum) are left alone")
    void tinyBaseUntouched() {
        AdaptiveDistanceController c = new AdaptiveDistanceController();
        for (int i = 0; i < 1000; i++) {
            assertEquals(0, c.update(TARGET, 10, AdaptiveDistanceController.MIN_DISTANCE));
        }
    }

    @Test
    @DisplayName("Base distance shrinking mid-flight clamps the reduction")
    void baseShrinkClamps() {
        AdaptiveDistanceController c = new AdaptiveDistanceController();
        feed(c, 20, AdaptiveDistanceController.DOWN_HOLD_TICKS * 100); // maxed out on BASE
        // User drops their own distance to 6: floor is 3, cap obeys the new base.
        int cap = c.update(TARGET, 20, 6);
        assertTrue(cap == 0 || cap >= AdaptiveDistanceController.floorFor(6));
        assertTrue(c.currentCap(6) <= 6);
    }

    @Test
    @DisplayName("reset() returns to hands-off")
    void resetClears() {
        AdaptiveDistanceController c = new AdaptiveDistanceController();
        feed(c, 20, AdaptiveDistanceController.DOWN_HOLD_TICKS * 5);
        assertTrue(c.reduction() > 0);
        c.reset();
        assertEquals(0, c.reduction());
        assertEquals(0, c.currentCap(BASE));
    }

    @Test
    @DisplayName("Floor never goes below the vanilla minimum")
    void floorRespectsMinimum() {
        assertEquals(2, AdaptiveDistanceController.floorFor(2));
        assertEquals(2, AdaptiveDistanceController.floorFor(4));
        assertEquals(2, AdaptiveDistanceController.floorFor(5));
        assertEquals(8, AdaptiveDistanceController.floorFor(16));
        assertEquals(16, AdaptiveDistanceController.floorFor(32));
    }
}
