package com.intelium.optimization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FrameBudget")
class FrameBudgetTest {

    private static final long GAP = FrameBudget.DEFAULT_FRAME_GAP_NANOS;
    /** Consecutive draws inside one frame are microseconds apart. */
    private static final long WITHIN_FRAME = 1_000L;

    private FrameBudget budget;
    private long clock;

    @BeforeEach
    void setUp() {
        budget = new FrameBudget();
        clock = 1_000_000_000L;
    }

    /** Consumes {@code n} slots inside one frame, returning how many passed. */
    private int drawWithinFrame(int n, int limit) {
        int allowed = 0;
        for (int i = 0; i < n; i++) {
            clock += WITHIN_FRAME;
            if (budget.tryConsume(clock, limit)) allowed++;
        }
        return allowed;
    }

    private void nextFrame() {
        clock += GAP * 4;
    }

    @Test
    @DisplayName("A limit of zero or less means unlimited")
    void unlimited() {
        assertEquals(1000, drawWithinFrame(1000, 0));
        assertEquals(1000, drawWithinFrame(1000, -7));
    }

    @Test
    @DisplayName("Draws beyond the limit are turned away")
    void limitApplies() {
        assertEquals(10, drawWithinFrame(25, 10));
    }

    @Test
    @DisplayName("The allowance comes back every frame")
    void allowanceRefills() {
        assertEquals(10, drawWithinFrame(25, 10));
        nextFrame();
        assertEquals(10, drawWithinFrame(25, 10));
        nextFrame();
        assertEquals(10, drawWithinFrame(25, 10));
    }

    @Test
    @DisplayName("A pause longer than the gap is what marks a new frame")
    void gapMarksTheFrame() {
        assertEquals(3, drawWithinFrame(3, 3));
        // Still inside the same frame: the fourth draw is refused.
        clock += WITHIN_FRAME;
        assertFalse(budget.tryConsume(clock, 3));
        // A real frame boundary lets it through.
        clock += GAP + 1;
        assertTrue(budget.tryConsume(clock, 3));
    }

    @Test
    @DisplayName("A frame boundary is not declared while draws keep arriving")
    void noFalseBoundaryWithinAFrame() {
        // 5000 back-to-back draws are one frame, not five thousand.
        assertEquals(64, drawWithinFrame(5000, 64));
    }

    @Test
    @DisplayName("A clock that jumps backwards costs one frame, not correctness")
    void backwardsClock() {
        assertEquals(4, drawWithinFrame(10, 4));
        clock -= 5 * GAP;
        assertTrue(budget.tryConsume(clock, 4), "a backwards jump reads as a new frame");
    }

    @Test
    @DisplayName("The previous frame's totals are reported once it ends")
    void reportsPreviousFrame() {
        drawWithinFrame(30, 10);
        nextFrame();
        // Reading happens on the first call of the next frame, which rolls over.
        budget.tryConsume(clock, 10);
        assertEquals(10, budget.lastFrameUsed());
        assertEquals(20, budget.lastFrameSkipped());
    }

    @Test
    @DisplayName("A frame under budget reports nothing skipped")
    void quietFrameSkipsNothing() {
        drawWithinFrame(5, 256);
        nextFrame();
        budget.tryConsume(clock, 256);
        assertEquals(5, budget.lastFrameUsed());
        assertEquals(0, budget.lastFrameSkipped());
    }

    @Test
    @DisplayName("Reset clears both the running and the reported counts")
    void reset() {
        drawWithinFrame(30, 10);
        nextFrame();
        budget.tryConsume(clock, 10);
        budget.reset();
        assertEquals(0, budget.lastFrameUsed());
        assertEquals(0, budget.lastFrameSkipped());
        // And the very first call after a reset starts a fresh frame.
        assertEquals(10, drawWithinFrame(25, 10));
    }

    @Test
    @DisplayName("A custom gap is honoured")
    void customGap() {
        FrameBudget slow = new FrameBudget(50_000_000L); // 50 ms
        long t = 0;
        assertTrue(slow.tryConsume(t, 1));
        t += 10_000_000L; // 10 ms later: still the same frame by this budget's reckoning
        assertFalse(slow.tryConsume(t, 1));
        t += 60_000_000L;
        assertTrue(slow.tryConsume(t, 1));
    }

    @Test
    @DisplayName("A non-positive gap is clamped rather than dividing frames forever")
    void gapIsClamped() {
        FrameBudget odd = new FrameBudget(0L);
        assertTrue(odd.tryConsume(0L, 1));
        assertFalse(odd.tryConsume(0L, 1), "the same instant is still the same frame");
    }
}
