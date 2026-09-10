package com.intelium.optimization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Frame budget boundaries")
class FrameBudgetBoundaryTest {

    @Nested
    @DisplayName("Told, not guessed")
    class Explicit {

        @Test
        @DisplayName("beginFrame resets the allowance without any clock")
        void explicitBoundaryResets() {
            FrameBudget budget = new FrameBudget();
            budget.beginFrame();
            assertTrue(budget.tryConsume(2));
            assertTrue(budget.tryConsume(2));
            assertFalse(budget.tryConsume(2));

            budget.beginFrame();
            assertTrue(budget.tryConsume(2), "the new frame gets its full allowance back");
        }

        @Test
        @DisplayName("Timestamps are ignored once boundaries are announced")
        void timestampsCannotDoubleRoll() {
            FrameBudget budget = new FrameBudget();
            budget.beginFrame();
            // Timestamps a whole second apart would each look like a new frame
            // to the heuristic. With a real boundary hook they must not.
            assertTrue(budget.tryConsume(1_000_000_000L, 2));
            assertTrue(budget.tryConsume(2_000_000_000L, 2));
            assertFalse(budget.tryConsume(3_000_000_000L, 2),
                    "the announced frame's allowance must not be reset by a gap");
        }

        @Test
        @DisplayName("Reports which mode it is in")
        void modeIsVisible() {
            FrameBudget budget = new FrameBudget();
            assertFalse(budget.hasExplicitBoundaries());
            budget.beginFrame();
            assertTrue(budget.hasExplicitBoundaries());
        }

        @Test
        @DisplayName("Previous-frame accounting rolls over on the boundary")
        void accountingRollsOver() {
            FrameBudget budget = new FrameBudget();
            budget.beginFrame();
            budget.tryConsume(2);
            budget.tryConsume(2);
            budget.tryConsume(2); // refused
            budget.beginFrame();
            assertEquals(2, budget.lastFrameUsed());
            assertEquals(1, budget.lastFrameSkipped());
        }

        @Test
        @DisplayName("An exempt draw is counted but never refused")
        void exemptDrawsAreCounted() {
            FrameBudget budget = new FrameBudget();
            budget.beginFrame();
            budget.consumeExempt();
            budget.consumeExempt();
            budget.beginFrame();
            assertEquals(2, budget.lastFrameUsed());
            assertEquals(0, budget.lastFrameSkipped());
        }
    }

    @Nested
    @DisplayName("Inferred fallback, for a build without the hook")
    class Inferred {

        @Test
        @DisplayName("A long gap is read as a new frame")
        void gapStartsNewFrame() {
            FrameBudget budget = new FrameBudget();
            long t = 1_000_000_000L;
            assertTrue(budget.tryConsume(t, 2));
            assertTrue(budget.tryConsume(t + 1_000L, 2));
            assertFalse(budget.tryConsume(t + 2_000L, 2));

            long nextFrame = t + FrameBudget.DEFAULT_FRAME_GAP_NANOS + 1_000_000L;
            assertTrue(budget.tryConsume(nextFrame, 2));
        }

        @Test
        @DisplayName("Back-to-back calls stay inside one frame")
        void tightCallsShareAFrame() {
            FrameBudget budget = new FrameBudget();
            long t = 5_000_000_000L;
            assertTrue(budget.tryConsume(t, 3));
            assertTrue(budget.tryConsume(t + 100L, 3));
            assertTrue(budget.tryConsume(t + 200L, 3));
            assertFalse(budget.tryConsume(t + 300L, 3));
        }

        @Test
        @DisplayName("A backwards clock costs one frame's accounting, never the budget")
        void backwardsClockIsSafe() {
            FrameBudget budget = new FrameBudget();
            assertTrue(budget.tryConsume(10_000_000_000L, 1));
            assertFalse(budget.tryConsume(10_000_000_100L, 1));
            // Clock jumps back: treated as a boundary, so drawing resumes.
            assertTrue(budget.tryConsume(1_000_000_000L, 1));
        }

        @Test
        @DisplayName("A window that never sees a gap is force-rolled rather than sticking")
        void windowCannotGetStuck() {
            FrameBudget budget = new FrameBudget();
            long t = 1_000_000_000L;
            // Calls 100us apart forever: the gap test never fires, so without
            // the absolute window cap every block entity would be refused from
            // the limit onwards - chests would simply stop drawing.
            int allowed = 0;
            for (int i = 0; i < 2_000; i++) {
                if (budget.tryConsume(t + i * 100_000L, 4)) allowed++;
            }
            assertTrue(allowed > 4,
                    "the absolute window cap must let later frames draw; allowed=" + allowed);
        }

        @Test
        @DisplayName("Unlimited means unlimited")
        void zeroLimitNeverRefuses() {
            FrameBudget budget = new FrameBudget();
            long t = 1_000_000_000L;
            for (int i = 0; i < 1_000; i++) {
                assertTrue(budget.tryConsume(t + i, 0));
            }
        }
    }

    @Test
    @DisplayName("Reset returns to inferring boundaries")
    void resetClearsExplicitMode() {
        FrameBudget budget = new FrameBudget();
        budget.beginFrame();
        assertTrue(budget.hasExplicitBoundaries());
        budget.reset();
        assertFalse(budget.hasExplicitBoundaries());
        assertEquals(0, budget.lastFrameUsed());
        assertEquals(0, budget.lastFrameSkipped());
    }
}
