package com.intelium.hud;

import java.util.function.Consumer;

/**
 * A/B FPS benchmark state machine. Measures the average FPS with Intelium's
 * effect enabled versus disabled, so the overlay can show an <em>honest,
 * measured</em> "with vs without" comparison instead of a fabricated number.
 *
 * <p>Because Sodium reads the chunk-worker count when it (re)builds its chunk
 * renderer, the benchmark toggles the effect and asks the game to reload chunks
 * between the two measurement windows. The reload + toggle actions are supplied
 * as callbacks at {@link #start}, keeping this class free of Minecraft types and
 * fully unit-testable with a synthetic clock and FPS source.
 *
 * <p>Timeline per run: WARMUP_ON → MEASURE_ON → (toggle off, reload) →
 * WARMUP_OFF → MEASURE_OFF → DONE.
 */
public final class AbBenchmark {

    public enum Phase { IDLE, WARMUP_ON, MEASURE_ON, WARMUP_OFF, MEASURE_OFF, DONE }

    /** Shared instance driven by the client tick; tests construct their own. */
    public static final AbBenchmark INSTANCE = new AbBenchmark(1500L, 4000L);

    private final long warmupMs;
    private final long measureMs;

    private Phase phase = Phase.IDLE;
    private long phaseStart;
    private long lastNow;

    private double sumOn;
    private double sumOff;
    private int nOn;
    private int nOff;
    private double onFps;
    private double offFps;

    private boolean restoreEnabled = true;
    private Consumer<Boolean> setEnabled = b -> {};
    private Runnable reload = () -> {};

    public AbBenchmark(long warmupMs, long measureMs) {
        this.warmupMs = warmupMs;
        this.measureMs = measureMs;
    }

    /**
     * Begins a run. {@code setEnabled}/{@code reload} apply the toggle and ask
     * the game to rebuild chunks; {@code restoreEnabled} is the value to restore
     * when the run finishes. No-op if a run is already in progress.
     */
    public synchronized void start(long now, boolean restoreEnabled,
                                   Consumer<Boolean> setEnabled, Runnable reload) {
        if (isRunning()) return;
        this.restoreEnabled = restoreEnabled;
        this.setEnabled = setEnabled == null ? b -> {} : setEnabled;
        this.reload = reload == null ? () -> {} : reload;
        this.sumOn = this.sumOff = 0;
        this.nOn = this.nOff = 0;
        this.onFps = this.offFps = 0;
        this.setEnabled.accept(true);
        this.reload.run();
        this.phase = Phase.WARMUP_ON;
        this.phaseStart = now;
        this.lastNow = now;
    }

    /** Advances the state machine. Call once per client tick with the live FPS. */
    public synchronized void tick(long now, int fps) {
        this.lastNow = now;
        long elapsed = now - phaseStart;
        switch (phase) {
            case WARMUP_ON -> {
                if (elapsed >= warmupMs) enter(Phase.MEASURE_ON, now);
            }
            case MEASURE_ON -> {
                sumOn += fps;
                nOn++;
                if (elapsed >= measureMs) {
                    onFps = nOn > 0 ? sumOn / nOn : 0;
                    setEnabled.accept(false);
                    reload.run();
                    enter(Phase.WARMUP_OFF, now);
                }
            }
            case WARMUP_OFF -> {
                if (elapsed >= warmupMs) enter(Phase.MEASURE_OFF, now);
            }
            case MEASURE_OFF -> {
                sumOff += fps;
                nOff++;
                if (elapsed >= measureMs) {
                    offFps = nOff > 0 ? sumOff / nOff : 0;
                    setEnabled.accept(restoreEnabled);
                    reload.run();
                    phase = Phase.DONE;
                }
            }
            default -> { }
        }
    }

    private void enter(Phase p, long now) {
        phase = p;
        phaseStart = now;
    }

    public synchronized void cancel(long now) {
        if (isRunning()) {
            setEnabled.accept(restoreEnabled);
            reload.run();
        }
        phase = Phase.IDLE;
    }

    public synchronized boolean isRunning() {
        return phase != Phase.IDLE && phase != Phase.DONE;
    }

    public synchronized boolean hasResult() {
        return phase == Phase.DONE;
    }

    public synchronized Phase phase() {
        return phase;
    }

    public synchronized double onFps() {
        return onFps;
    }

    public synchronized double offFps() {
        return offFps;
    }

    public synchronized double gain() {
        return onFps - offFps;
    }

    public synchronized double gainPercent() {
        return offFps > 0 ? (onFps - offFps) / offFps * 100.0 : 0.0;
    }

    /** 0..100 progress across the four timed phases. */
    public synchronized int progressPercent() {
        if (phase == Phase.IDLE) return 0;
        if (phase == Phase.DONE) return 100;
        long total = 2 * (warmupMs + measureMs);
        long done = switch (phase) {
            case MEASURE_ON -> warmupMs;
            case WARMUP_OFF -> warmupMs + measureMs;
            case MEASURE_OFF -> warmupMs + measureMs + warmupMs;
            default -> 0L;
        };
        long cur = Math.min(nowSincePhase(), phaseDuration());
        int pct = (int) ((done + cur) * 100 / total);
        return Math.max(0, Math.min(100, pct));
    }

    private long phaseDuration() {
        return switch (phase) {
            case WARMUP_ON, WARMUP_OFF -> warmupMs;
            case MEASURE_ON, MEASURE_OFF -> measureMs;
            default -> 0L;
        };
    }

    private long nowSincePhase() {
        return Math.max(0, lastNow - phaseStart);
    }
}
