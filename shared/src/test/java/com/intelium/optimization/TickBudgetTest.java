package com.intelium.optimization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TickBudget")
class TickBudgetTest {

    private TickBudget budget;

    @BeforeEach
    void setUp() {
        budget = new TickBudget();
    }

    /** Asks for {@code n} slots this tick, returning how many were granted. */
    private int spawn(int n, int limit) {
        int allowed = 0;
        for (int i = 0; i < n; i++) {
            if (budget.tryConsume(limit)) allowed++;
        }
        return allowed;
    }

    @Test
    @DisplayName("A limit of zero or less means unlimited")
    void unlimited() {
        assertEquals(5000, spawn(5000, 0));
        assertEquals(5000, spawn(5000, -3));
    }

    @Test
    @DisplayName("An explosion's tail is cut off at the limit")
    void limitApplies() {
        assertEquals(512, spawn(4000, 512));
    }

    @Test
    @DisplayName("Ordinary play never reaches the limit")
    void ordinaryPlayIsUntouched() {
        assertEquals(40, spawn(40, 512));
        assertEquals(0, budget.lastTickSkipped());
    }

    @Test
    @DisplayName("The allowance comes back every tick")
    void allowanceRefills() {
        assertEquals(100, spawn(400, 100));
        budget.beginTick();
        assertEquals(100, spawn(400, 100));
    }

    @Test
    @DisplayName("The previous tick's totals are reported after it ends")
    void reportsPreviousTick() {
        spawn(700, 512);
        budget.beginTick();
        assertEquals(512, budget.lastTickUsed());
        assertEquals(188, budget.lastTickSkipped());
    }

    @Test
    @DisplayName("A quiet tick reports nothing turned away")
    void quietTick() {
        spawn(12, 512);
        budget.beginTick();
        assertEquals(12, budget.lastTickUsed());
        assertEquals(0, budget.lastTickSkipped());
    }

    @Test
    @DisplayName("Reset clears both the running and the reported counts")
    void reset() {
        spawn(700, 512);
        budget.beginTick();
        budget.reset();
        assertEquals(0, budget.lastTickUsed());
        assertEquals(0, budget.lastTickSkipped());
        assertEquals(512, spawn(700, 512));
    }
}
