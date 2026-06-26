package com.intelium.hud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FpsTracker")
class FpsTrackerTest {

    @Test
    @DisplayName("Empty tracker reports zero")
    void emptyZero() {
        FpsTracker t = new FpsTracker(10);
        assertEquals(0, t.smoothed());
        assertEquals(0, t.last());
        assertEquals(0, t.sampleCount());
    }

    @Test
    @DisplayName("last() returns the most recent sample")
    void lastSample() {
        FpsTracker t = new FpsTracker(10);
        t.push(60);
        t.push(120);
        assertEquals(120, t.last());
    }

    @Test
    @DisplayName("smoothed() averages the window")
    void smoothedAverage() {
        FpsTracker t = new FpsTracker(4);
        t.push(100);
        t.push(200);
        assertEquals(150, t.smoothed());
    }

    @Test
    @DisplayName("Rolling window discards old samples")
    void rollingWindow() {
        FpsTracker t = new FpsTracker(2);
        t.push(10);
        t.push(20);
        t.push(30); // evicts 10
        assertEquals(25, t.smoothed());
        assertEquals(2, t.sampleCount());
    }

    @Test
    @DisplayName("Negative FPS is clamped to zero")
    void negativeClamped() {
        FpsTracker t = new FpsTracker(2);
        t.push(-5);
        assertEquals(0, t.last());
    }

    @Test
    @DisplayName("reset clears samples")
    void reset() {
        FpsTracker t = new FpsTracker(4);
        t.push(60);
        t.reset();
        assertEquals(0, t.sampleCount());
        assertEquals(0, t.smoothed());
    }
}
