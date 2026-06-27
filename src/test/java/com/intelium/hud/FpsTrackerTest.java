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

    @Test
    @DisplayName("min() returns the smallest sample in the window")
    void minSample() {
        FpsTracker t = new FpsTracker(8);
        t.push(120);
        t.push(30);
        t.push(90);
        assertEquals(30, t.min());
    }

    @Test
    @DisplayName("min() of empty tracker is 0")
    void minEmpty() {
        assertEquals(0, new FpsTracker(8).min());
    }

    @Test
    @DisplayName("onePercentLow averages the worst frames")
    void onePercentLow() {
        FpsTracker t = new FpsTracker(200);
        for (int i = 0; i < 100; i++) t.push(100);
        t.push(10); // one nasty hitch
        // 1% of 101 samples = 2 worst frames: {10, 100} -> average 55
        assertEquals(55, t.onePercentLow());
    }

    @Test
    @DisplayName("onePercentLow of empty tracker is 0")
    void onePercentLowEmpty() {
        assertEquals(0, new FpsTracker(200).onePercentLow());
    }

    @Test
    @DisplayName("lowAverage always includes at least one sample")
    void lowAverageAtLeastOne() {
        FpsTracker t = new FpsTracker(10);
        t.push(60);
        t.push(20);
        // fraction tiny -> ceil(2 * 1e-9) = 1 -> just the single worst frame
        assertEquals(20, t.lowAverage(1e-9));
    }
}
