package com.intelium.optimization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FramePressure")
class FramePressureTest {

    private FramePressure pressure;

    @BeforeEach
    void setUp() {
        pressure = new FramePressure();
    }

    /** Feeds one value long enough for the average to settle on it. */
    private void settle(int fps) {
        for (int i = 0; i < 400; i++) pressure.push(fps);
    }

    @Test
    @DisplayName("Before the first sample there is no pressure and no reading")
    void unprimed() {
        assertFalse(pressure.primed());
        assertEquals(0.0, pressure.smoothedFps());
        assertEquals(0.0, pressure.pressure(60));
    }

    @Test
    @DisplayName("The first sample is adopted outright, not blended with zero")
    void firstSampleWins() {
        pressure.push(120);
        assertTrue(pressure.primed());
        assertEquals(120.0, pressure.smoothedFps(), 1e-9);
    }

    @Test
    @DisplayName("Holding the target means no pressure")
    void atTargetNoPressure() {
        settle(60);
        assertEquals(0.0, pressure.pressure(60));
    }

    @Test
    @DisplayName("Comfortably above the target still means no pressure")
    void aboveTargetNoPressure() {
        settle(144);
        assertEquals(0.0, pressure.pressure(60));
    }

    @Test
    @DisplayName("Half the target is full pressure")
    void halfTargetIsFullPressure() {
        settle(30);
        assertEquals(1.0, pressure.pressure(60), 1e-6);
    }

    @Test
    @DisplayName("Pressure is capped at 1 however bad it gets")
    void pressureIsCapped() {
        settle(3);
        assertEquals(1.0, pressure.pressure(60), 1e-9);
    }

    @Test
    @DisplayName("Three quarters of the target is half pressure")
    void partialPressure() {
        settle(45);
        assertEquals(0.5, pressure.pressure(60), 1e-6);
    }

    @Test
    @DisplayName("A single bad frame barely moves the average")
    void oneBadFrameIsAbsorbed() {
        settle(60);
        pressure.push(5);
        assertTrue(pressure.pressure(60) < 0.25,
                "one hitch should not swing the budgets, was " + pressure.pressure(60));
    }

    @Test
    @DisplayName("A sustained drop is picked up within about a second")
    void sustainedDropIsSeen() {
        settle(60);
        for (int i = 0; i < 20; i++) pressure.push(30); // ~1 second of ticks
        assertTrue(pressure.pressure(60) > 0.4,
                "a full second at half rate should register, was " + pressure.pressure(60));
    }

    @Test
    @DisplayName("Recovery relaxes the pressure again")
    void recoveryRelaxes() {
        settle(30);
        assertEquals(1.0, pressure.pressure(60), 1e-6);
        settle(60);
        // An EMA approaches its target rather than landing on it, so the test is
        // "the budgets are back to normal", not "the arithmetic is exact".
        assertEquals(0.0, pressure.pressure(60), 1e-6);
    }

    @Test
    @DisplayName("Negative samples are treated as zero, not as negative frame rates")
    void negativeSamples() {
        pressure.push(-40);
        assertEquals(0.0, pressure.smoothedFps(), 1e-9);
        assertEquals(1.0, pressure.pressure(60), 1e-9);
    }

    @Test
    @DisplayName("A nonsensical target means no pressure rather than a divide by zero")
    void badTarget() {
        settle(10);
        assertEquals(0.0, pressure.pressure(0));
        assertEquals(0.0, pressure.pressure(-30));
    }

    @Test
    @DisplayName("Reset forgets everything")
    void reset() {
        settle(15);
        pressure.reset();
        assertFalse(pressure.primed());
        assertEquals(0.0, pressure.smoothedFps());
        assertEquals(0.0, pressure.pressure(60));
    }
}
