package com.intelium.perf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Frame-time tracker")
class FrameTimeTrackerTest {

    private static final double EPS = 0.001;

    /** 60 FPS is 16666 microseconds per frame. */
    private static final int MICROS_60FPS = 16_666;

    private static FrameTimeTracker fed(int capacity, int warmup, int... framesMicros) {
        FrameTimeTracker t = new FrameTimeTracker(capacity, warmup);
        for (int micros : framesMicros) t.recordMicros(micros);
        return t;
    }

    @Nested
    @DisplayName("Ring buffer")
    class Ring {

        @Test
        @DisplayName("Holds at most its capacity and forgets the oldest first")
        void wrapsAround() {
            FrameTimeTracker t = fed(3, 0, 1000, 2000, 3000, 4000);
            assertEquals(3, t.sampleCount());
            // 2000, 3000, 4000 remain: mean 3000us -> 3ms
            assertEquals(3.0, t.averageFrameTimeMs(), EPS);
        }

        @Test
        @DisplayName("The running mean survives wraparound")
        void meanSurvivesWraparound() {
            FrameTimeTracker t = new FrameTimeTracker(4, 0);
            for (int i = 0; i < 40; i++) t.recordMicros(10_000);
            assertEquals(10.0, t.averageFrameTimeMs(), EPS);
            assertEquals(100.0, t.averageFps(), 0.01);
        }

        @Test
        @DisplayName("An empty window answers zero rather than dividing by it")
        void emptyIsZero() {
            FrameTimeTracker t = new FrameTimeTracker(8);
            assertEquals(0, t.sampleCount());
            assertEquals(0.0, t.averageFps(), EPS);
            assertEquals(0.0, t.medianFrameTimeMs(), EPS);
            assertEquals(0.0, t.onePercentLowFps(), EPS);
            assertEquals(0.0, t.pointOnePercentLowFps(), EPS);
            assertFalse(t.warm());
        }
    }

    @Nested
    @DisplayName("Percentiles")
    class Percentiles {

        @Test
        @DisplayName("Median is the middle frame time")
        void median() {
            FrameTimeTracker t = fed(16, 0, 5000, 1000, 3000, 2000, 4000);
            // sorted: 1000 2000 3000 4000 5000 -> nearest-rank p50 = 3000us
            assertEquals(3.0, t.medianFrameTimeMs(), EPS);
        }

        @Test
        @DisplayName("p95 and p99 report the slow tail, not the average")
        void tailPercentiles() {
            FrameTimeTracker t = new FrameTimeTracker(100, 0);
            for (int i = 0; i < 99; i++) t.recordMicros(10_000);
            t.recordMicros(100_000); // one 100 ms hitch
            assertEquals(10.0, t.averageFrameTimeMs(), 1.0);
            // The single worst frame is the 100th of 100, so p99 still lands on
            // a good frame while p100 catches the hitch.
            assertEquals(10.0, t.percentileFrameTimeMs(99.0), EPS);
            assertEquals(100.0, t.percentileFrameTimeMs(100.0), EPS);
        }

        @Test
        @DisplayName("Percentile arguments outside 0..100 are clamped, not thrown")
        void percentilesAreClamped() {
            FrameTimeTracker t = fed(8, 0, 1000, 2000, 3000);
            assertEquals(1.0, t.percentileFrameTimeMs(-50.0), EPS);
            assertEquals(3.0, t.percentileFrameTimeMs(500.0), EPS);
        }

        @Test
        @DisplayName("Percentiles are stable when nothing new arrives")
        void repeatedReadsAgree() {
            FrameTimeTracker t = fed(64, 0, 8000, 9000, 40_000, 8500);
            double first = t.percentileFrameTimeMs(95.0);
            assertEquals(first, t.percentileFrameTimeMs(95.0), EPS);
            assertEquals(first, t.percentileFrameTimeMs(95.0), EPS);
        }

        @Test
        @DisplayName("A new sample changes the answer")
        void newSampleInvalidatesCache() {
            FrameTimeTracker t = fed(64, 0, 8000, 8000, 8000);
            assertEquals(8.0, t.percentileFrameTimeMs(100.0), EPS);
            t.recordMicros(50_000);
            assertEquals(50.0, t.percentileFrameTimeMs(100.0), EPS);
        }
    }

    @Nested
    @DisplayName("Lows")
    class Lows {

        @Test
        @DisplayName("The 1% low reports the worst frames, so it sits below the average")
        void onePercentLowIsWorseThanAverage() {
            FrameTimeTracker t = new FrameTimeTracker(200, 0);
            for (int i = 0; i < 198; i++) t.recordMicros(MICROS_60FPS);
            t.recordMicros(200_000); // 5 FPS
            t.recordMicros(200_000);
            assertTrue(t.onePercentLowFps() < t.averageFps(),
                    "a 1% low above the average would mean the metric is inverted");
            assertEquals(5.0, t.onePercentLowFps(), 0.1);
        }

        @Test
        @DisplayName("The 0.1% low is no better than the 1% low")
        void pointOneIsAtLeastAsHarsh() {
            FrameTimeTracker t = new FrameTimeTracker(2000, 0);
            for (int i = 0; i < 1990; i++) t.recordMicros(MICROS_60FPS);
            for (int i = 0; i < 10; i++) t.recordMicros(100_000 + i * 1000);
            assertTrue(t.pointOnePercentLowFps() <= t.onePercentLowFps() + EPS);
        }

        @Test
        @DisplayName("A steady frame rate makes average and lows agree")
        void steadyRateHasNoGap() {
            FrameTimeTracker t = new FrameTimeTracker(300, 0);
            for (int i = 0; i < 300; i++) t.recordMicros(MICROS_60FPS);
            assertEquals(t.averageFps(), t.onePercentLowFps(), 0.1);
        }

        @Test
        @DisplayName("Always covers at least one frame, however small the fraction")
        void tinyFractionStillAnswers() {
            FrameTimeTracker t = fed(8, 0, 10_000, 20_000);
            assertTrue(t.lowFps(0.0) > 0.0);
            assertTrue(t.lowFps(Double.NaN) > 0.0);
        }
    }

    @Nested
    @DisplayName("Bad samples")
    class BadSamples {

        @Test
        @DisplayName("A backwards clock is discarded, not recorded as a frame")
        void backwardsClock() {
            FrameTimeTracker t = new FrameTimeTracker(16, 0);
            t.frame(1_000_000L);
            t.frame(500_000L);
            assertEquals(0, t.sampleCount());
            assertEquals(1, t.discardedCount());
        }

        @Test
        @DisplayName("A multi-second stall is discarded rather than skewing every percentile")
        void loadingStallIsDiscarded() {
            FrameTimeTracker t = new FrameTimeTracker(16, 0);
            t.frame(0L);
            t.frame(16_666_000L);          // one 60 FPS frame
            t.frame(5_016_666_000L);       // a five-second world load
            assertEquals(1, t.sampleCount());
            assertEquals(1, t.discardedCount());
            assertEquals(16.666, t.averageFrameTimeMs(), 0.01);
        }

        @Test
        @DisplayName("The first boundary after a reset primes rather than recording")
        void firstFrameHasNoInterval() {
            FrameTimeTracker t = new FrameTimeTracker(16, 0);
            t.frame(1_000_000L);
            assertEquals(0, t.sampleCount());
            assertEquals(0, t.discardedCount());
            t.frame(1_016_666_000L - 1_000_000_000L + 1_000_000L);
            assertEquals(1, t.sampleCount());
        }
    }

    @Nested
    @DisplayName("Warm-up gating")
    class WarmUp {

        @Test
        @DisplayName("Nothing is warm until enough frames have been seen")
        void warmsUpAfterEnoughFrames() {
            FrameTimeTracker t = new FrameTimeTracker(200, 10);
            for (int i = 0; i < 9; i++) t.recordMicros(MICROS_60FPS);
            assertFalse(t.warm(), "nine frames must not authorise an adaptive change");
            t.recordMicros(MICROS_60FPS);
            assertTrue(t.warm());
        }

        @Test
        @DisplayName("Alt-tabbing restarts the warm-up but keeps the window")
        void invalidateRestartsWarmup() {
            FrameTimeTracker t = new FrameTimeTracker(200, 5);
            for (int i = 0; i < 20; i++) t.recordMicros(MICROS_60FPS);
            assertTrue(t.warm());

            t.invalidate();
            assertFalse(t.warm(), "the frames after a refocus are not yet evidence");
            assertEquals(20, t.sampleCount(), "history that was valid stays valid");

            for (int i = 0; i < 4; i++) t.recordMicros(MICROS_60FPS);
            assertFalse(t.warm());
            t.recordMicros(MICROS_60FPS);
            assertTrue(t.warm());
        }

        @Test
        @DisplayName("Invalidating drops the in-flight interval across the gap")
        void invalidateDropsTheGap() {
            FrameTimeTracker t = new FrameTimeTracker(16, 0);
            t.frame(0L);
            t.frame(16_666_000L);
            t.invalidate();
            // A long unfocused stretch; the next boundary must not record it.
            t.frame(30_016_666_000L);
            assertEquals(1, t.sampleCount());
            assertEquals(0, t.discardedCount());
        }

        @Test
        @DisplayName("Reset forgets everything")
        void resetClears() {
            FrameTimeTracker t = new FrameTimeTracker(16, 0);
            for (int i = 0; i < 16; i++) t.recordMicros(MICROS_60FPS);
            t.reset();
            assertEquals(0, t.sampleCount());
            assertEquals(0, t.discardedCount());
            assertEquals(0.0, t.averageFps(), EPS);
            assertFalse(t.warm());
        }
    }

    @Nested
    @DisplayName("Snapshot")
    class Snapshots {

        @Test
        @DisplayName("Reports every statistic together")
        void snapshotIsConsistent() {
            FrameTimeTracker t = new FrameTimeTracker(200, 10);
            for (int i = 0; i < 200; i++) t.recordMicros(MICROS_60FPS);
            FrameTimeTracker.Snapshot s = t.snapshot();
            assertEquals(200, s.samples());
            assertTrue(s.warm());
            assertEquals(60.0, s.averageFps(), 0.1);
            assertEquals(16.666, s.medianFrameTimeMs(), 0.01);
            assertEquals(16.666, s.p95FrameTimeMs(), 0.01);
            assertEquals(16.666, s.p99FrameTimeMs(), 0.01);
        }

        @Test
        @DisplayName("CSV row has one field per header column")
        void csvShapeMatches() {
            FrameTimeTracker t = fed(8, 0, MICROS_60FPS, MICROS_60FPS);
            int headers = FrameTimeTracker.Snapshot.csvHeader().split(",").length;
            int fields = t.snapshot().toCsvRow().split(",").length;
            assertEquals(headers, fields);
        }

        @Test
        @DisplayName("The compact line names each statistic")
        void compactLineIsReadable() {
            FrameTimeTracker t = fed(8, 0, MICROS_60FPS);
            String line = t.snapshot().toCompactString();
            assertTrue(line.contains("avg="));
            assertTrue(line.contains("median="));
            assertTrue(line.contains("p95="));
            assertTrue(line.contains("p99="));
            assertTrue(line.contains("1%low="));
            assertTrue(line.contains("0.1%low="));
        }
    }
}
