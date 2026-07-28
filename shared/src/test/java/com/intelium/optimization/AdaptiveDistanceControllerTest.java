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
        // Fully recovers to hands-off with enough sustained headroom (each
        // step now also spends a settle window before measuring again).
        feed(c, 90, (AdaptiveDistanceController.UP_HOLD_TICKS
                + AdaptiveDistanceController.SETTLE_TICKS) * 3);
        assertEquals(0, c.currentCap(BASE));
    }

    @Test
    @DisplayName("The settle window after an up-step ignores the rebuild's FPS dip")
    void settleWindowIgnoresOwnRebuild() {
        AdaptiveDistanceController c = new AdaptiveDistanceController();
        feed(c, 40, AdaptiveDistanceController.DOWN_HOLD_TICKS * 2); // down 2
        feed(c, 90, AdaptiveDistanceController.UP_HOLD_TICKS);       // up 1
        assertEquals(BASE - 1, c.currentCap(BASE));
        // The chunk rebuild caused by that step reads as low FPS for a while:
        // during the settle window it must NOT trigger a step back down.
        assertEquals(BASE - 1, feed(c, 40, AdaptiveDistanceController.SETTLE_TICKS));
        // After settling, a real sustained low still needs the full hold.
        assertEquals(BASE - 1, feed(c, 40, AdaptiveDistanceController.DOWN_HOLD_TICKS - 1));
        assertEquals(BASE - 2, feed(c, 40, 1));
    }

    @Test
    @DisplayName("A failed recovery doubles the wait before the next up attempt")
    void failedRecoveryBacksOff() {
        AdaptiveDistanceController c = new AdaptiveDistanceController();
        feed(c, 40, AdaptiveDistanceController.DOWN_HOLD_TICKS * 2); // down 2
        feed(c, 90, AdaptiveDistanceController.UP_HOLD_TICKS);       // up 1 (probation)
        // The up-step doesn't hold: FPS collapses again -> step back down.
        feed(c, 40, AdaptiveDistanceController.SETTLE_TICKS
                + AdaptiveDistanceController.DOWN_HOLD_TICKS);
        assertEquals(BASE - 2, c.currentCap(BASE));
        assertEquals(2, c.upHoldMultiplier());
        // The next recovery attempt now needs twice the headroom hold.
        assertEquals(BASE - 2, feed(c, 90, AdaptiveDistanceController.UP_HOLD_TICKS * 2 - 1));
        assertEquals(BASE - 1, feed(c, 90, 1));
    }

    @Test
    @DisplayName("Repeated failed recoveries back off exponentially, capped at 8x")
    void backoffIsCapped() {
        AdaptiveDistanceController c = new AdaptiveDistanceController();
        feed(c, 40, AdaptiveDistanceController.DOWN_HOLD_TICKS * 4); // down 4
        for (int i = 0; i < 5; i++) {
            // Exactly one up-step (whatever hold is currently required),
            // then fail it within the probation window.
            feed(c, 90, AdaptiveDistanceController.UP_HOLD_TICKS * c.upHoldMultiplier());
            feed(c, 40, AdaptiveDistanceController.SETTLE_TICKS
                    + AdaptiveDistanceController.DOWN_HOLD_TICKS);
        }
        assertEquals(AdaptiveDistanceController.MAX_UP_HOLD_MULTIPLIER, c.upHoldMultiplier());
    }

    @Test
    @DisplayName("A recovery that sticks relaxes the backoff again")
    void survivedProbationRelaxesBackoff() {
        AdaptiveDistanceController c = new AdaptiveDistanceController();
        feed(c, 40, AdaptiveDistanceController.DOWN_HOLD_TICKS * 3); // down 3
        feed(c, 90, AdaptiveDistanceController.UP_HOLD_TICKS);       // up 1
        feed(c, 40, AdaptiveDistanceController.SETTLE_TICKS
                + AdaptiveDistanceController.DOWN_HOLD_TICKS);       // failed
        assertEquals(2, c.upHoldMultiplier());
        feed(c, 90, AdaptiveDistanceController.UP_HOLD_TICKS * 2);   // up again
        // Hold inside the dead band through the whole probation window: the
        // step stuck, so the backoff halves back to normal.
        feed(c, 58, AdaptiveDistanceController.SETTLE_TICKS
                + AdaptiveDistanceController.PROBATION_TICKS);
        assertEquals(1, c.upHoldMultiplier());
    }

    @Test
    @DisplayName("reset() clears the settle window and backoff too")
    void resetClearsGuards() {
        AdaptiveDistanceController c = new AdaptiveDistanceController();
        feed(c, 40, AdaptiveDistanceController.DOWN_HOLD_TICKS * 2);
        feed(c, 90, AdaptiveDistanceController.UP_HOLD_TICKS);
        feed(c, 40, AdaptiveDistanceController.SETTLE_TICKS
                + AdaptiveDistanceController.DOWN_HOLD_TICKS);
        assertEquals(2, c.upHoldMultiplier());
        c.reset();
        assertEquals(1, c.upHoldMultiplier());
        assertEquals(0, c.reduction());
    }

    @Test
    @DisplayName("FPS far below the target (severe band) steps two chunks after a halved hold")
    void severeDropReactsFaster() {
        AdaptiveDistanceController c = new AdaptiveDistanceController();
        // 20 FPS < 60 * 0.60 = 36: severe. One halved hold window, two chunks.
        assertEquals(0, feed(c, 20, AdaptiveDistanceController.SEVERE_HOLD_TICKS - 1));
        assertEquals(BASE - AdaptiveDistanceController.SEVERE_STEP, feed(c, 20, 1));
    }

    @Test
    @DisplayName("Moderately low FPS (below 92% but above 60%) keeps the gentle single step")
    void moderateLowStaysGentle() {
        AdaptiveDistanceController c = new AdaptiveDistanceController();
        // 40 FPS is low (< 55.2) but not severe (> 36): full hold, one chunk.
        assertEquals(0, feed(c, 40, AdaptiveDistanceController.DOWN_HOLD_TICKS - 1));
        assertEquals(BASE - 1, feed(c, 40, 1));
    }

    @Test
    @DisplayName("Severe stepping never breaches the half-base floor")
    void severeRespectsFloor() {
        AdaptiveDistanceController c = new AdaptiveDistanceController();
        int cap = feed(c, 10, AdaptiveDistanceController.SEVERE_HOLD_TICKS * 100);
        assertEquals(AdaptiveDistanceController.floorFor(BASE), cap);
    }

    @Test
    @DisplayName("Recovery from a severe reduction still steps up one chunk at a time")
    void severeRecoveryStaysSlow() {
        AdaptiveDistanceController c = new AdaptiveDistanceController();
        feed(c, 20, AdaptiveDistanceController.SEVERE_HOLD_TICKS); // down 2
        assertEquals(BASE - 2, c.currentCap(BASE));
        assertEquals(BASE - 2, feed(c, 90, AdaptiveDistanceController.UP_HOLD_TICKS - 1));
        assertEquals(BASE - 1, feed(c, 90, 1));
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
