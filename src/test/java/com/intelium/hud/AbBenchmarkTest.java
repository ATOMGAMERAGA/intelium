package com.intelium.hud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AbBenchmark state machine")
class AbBenchmarkTest {

    private final List<Boolean> enabledCalls = new ArrayList<>();
    private final AtomicInteger reloads = new AtomicInteger();
    private final Consumer<Boolean> setEnabled = enabledCalls::add;
    private final Runnable reload = reloads::incrementAndGet;

    @Test
    @DisplayName("start enables the effect, reloads, and enters WARMUP_ON")
    void startInitialises() {
        AbBenchmark b = new AbBenchmark(100, 100);
        b.start(0, true, setEnabled, reload);
        assertTrue(b.isRunning());
        assertEquals(AbBenchmark.Phase.WARMUP_ON, b.phase());
        assertEquals(List.of(true), enabledCalls);
        assertEquals(1, reloads.get());
    }

    @Test
    @DisplayName("start is a no-op while a run is in progress")
    void startNoopWhileRunning() {
        AbBenchmark b = new AbBenchmark(100, 100);
        b.start(0, true, setEnabled, reload);
        b.start(10, true, setEnabled, reload); // ignored
        assertEquals(1, reloads.get());
        assertEquals(List.of(true), enabledCalls);
    }

    @Test
    @DisplayName("Full run computes measured ON/OFF averages and gain")
    void fullRun() {
        AbBenchmark b = new AbBenchmark(100, 100);
        b.start(0, true, setEnabled, reload);

        // WARMUP_ON: [0,100)
        b.tick(0, 0);
        b.tick(50, 0);
        b.tick(100, 0); // -> MEASURE_ON
        assertEquals(AbBenchmark.Phase.MEASURE_ON, b.phase());

        // MEASURE_ON: samples 100 fps twice, last tick closes it
        b.tick(150, 100);
        b.tick(200, 100); // avg ON = 100 -> setEnabled(false), reload, WARMUP_OFF
        assertEquals(AbBenchmark.Phase.WARMUP_OFF, b.phase());

        // WARMUP_OFF: [200,300)
        b.tick(250, 0);
        b.tick(300, 0); // -> MEASURE_OFF

        // MEASURE_OFF: samples 50 fps twice
        b.tick(350, 50);
        b.tick(400, 50); // avg OFF = 50 -> restore, reload, DONE

        assertTrue(b.hasResult());
        assertFalse(b.isRunning());
        assertEquals(100.0, b.onFps(), 0.001);
        assertEquals(50.0, b.offFps(), 0.001);
        assertEquals(50.0, b.gain(), 0.001);
        assertEquals(100.0, b.gainPercent(), 0.001);

        // toggled true -> false -> restore(true); reloaded 3 times total
        assertEquals(List.of(true, false, true), enabledCalls);
        assertEquals(3, reloads.get());
        assertEquals(100, b.progressPercent());
    }

    @Test
    @DisplayName("restoreEnabled=false is honored at the end")
    void restoreFalse() {
        AbBenchmark b = new AbBenchmark(50, 50);
        b.start(0, false, setEnabled, reload);
        for (long t = 0; t <= 250; t += 25) {
            b.tick(t, 90);
        }
        assertTrue(b.hasResult());
        assertEquals(Boolean.FALSE, enabledCalls.get(enabledCalls.size() - 1));
    }

    @Test
    @DisplayName("Idle benchmark reports 0% progress and no result")
    void idleState() {
        AbBenchmark b = new AbBenchmark(100, 100);
        assertEquals(0, b.progressPercent());
        assertFalse(b.hasResult());
        assertFalse(b.isRunning());
    }

    @Test
    @DisplayName("cancel restores the effect and returns to idle")
    void cancelRestores() {
        AbBenchmark b = new AbBenchmark(100, 100);
        b.start(0, true, setEnabled, reload);
        enabledCalls.clear();
        b.cancel(10);
        assertFalse(b.isRunning());
        assertEquals(List.of(true), enabledCalls); // restored to true
    }

    @Test
    @DisplayName("gainPercent is zero when baseline is zero")
    void gainPercentZeroBaseline() {
        AbBenchmark b = new AbBenchmark(50, 50);
        b.start(0, true, setEnabled, reload);
        for (long t = 0; t <= 250; t += 25) {
            b.tick(t, 0);
        }
        assertEquals(0.0, b.gainPercent(), 0.001);
    }
}
