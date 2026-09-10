package com.intelium.client;

import com.intelium.Capabilities;
import com.intelium.Capability;
import com.intelium.compat.ModCompat;
import com.intelium.config.InteliumConfig;
import com.intelium.config.InteliumConfigIO;
import com.intelium.perf.FrameTimeTracker;
import net.minecraft.client.Minecraft;

/**
 * Owns Intelium's frame-time measurement on 26.x and decides which frames are
 * allowed to mean anything.
 *
 * <h2>Where the samples come from</h2>
 *
 * <p>{@code Minecraft.renderFrame(Z)V} runs exactly once per frame, so
 * {@link #onFrame()} is a real frame boundary rather than an inference from
 * timing. If that hook cannot be applied on some future build, the
 * {@link Capability#FRAME_BOUNDARY} capability reports unavailable, no frame
 * times are recorded, and every consumer falls back to the game's own FPS
 * counter - the pre-1.3.4 behaviour, which is worse but never wrong.
 *
 * <h2>Which frames are thrown away, and why</h2>
 *
 * <p>A frame time is evidence about how hard the machine is working only if
 * nothing else was deliberately holding the frame rate down. These are not:
 *
 * <ul>
 *   <li><b>Unfocused window</b> - the game throttles itself, and Intelium's own
 *       background cap may be throttling it further.</li>
 *   <li><b>A capped menu</b> - the menu FPS limit exists precisely to make
 *       these frames slow.</li>
 *   <li><b>No world loaded</b> - and, because {@link #onWorldChanged()} is
 *       called when one loads, the warm-up restarts there too, so the seconds
 *       while terrain streams in never count.</li>
 * </ul>
 *
 * <p>Any of those calls {@link FrameTimeTracker#invalidate()}, which drops the
 * in-flight interval and restarts the warm-up. Adaptive consumers check
 * {@link #warm()} and change nothing until it is true again, so alt-tabbing
 * back into the game cannot land the player in the harshest settings the
 * config allows.
 *
 * <h2>VSync and FPS caps are flagged, not discarded</h2>
 *
 * <p>A frame pinned to 16.67 ms by VSync says what the display is doing, not
 * what the GPU could do - so it is not evidence of <em>headroom</em>. But
 * discarding capped frames outright would be worse than useless: VSync is on by
 * default, so the tracker would never warm up on the majority of machines and
 * every measurement would be lost, including the spikes.
 *
 * <p>The frames are therefore recorded and the cap is reported instead, through
 * {@link #capActive()}. A frame that <em>misses</em> a cap is still real
 * evidence of trouble - that is exactly what a 1% low under VSync means - while
 * a consumer that would otherwise read "sitting exactly at the cap" as spare
 * capacity can see that it must not. Reports say which state they were measured
 * in rather than quietly mixing the two.
 *
 * <p>Only ever touched from the render thread.
 */
public final class FrameTimeSampler {

    private FrameTimeSampler() {}

    /**
     * Ten seconds at 120 FPS. Long enough for a 0.1% low to mean something
     * (it covers the worst two frames), short enough to still be about now.
     * 1200 ints is under 5 KB, allocated once.
     */
    private static final int WINDOW_FRAMES = 1200;

    /** Two seconds at 60 FPS before any measurement is trusted. */
    private static final int WARMUP_FRAMES = 120;

    private static final FrameTimeTracker TRACKER =
            new FrameTimeTracker(WINDOW_FRAMES, WARMUP_FRAMES);

    /** Set by the frame hook, so a build without it is detectable. */
    private static volatile boolean hookFired;

    /** Whether the most recent recorded frame was paced by VSync or an FPS cap. */
    private static volatile boolean capped;

    /**
     * Whether frames rendered right now count. Written once per client tick,
     * read once per frame - so the render path never touches the config or a
     * reflective probe.
     */
    private static volatile boolean measurable;

    /**
     * Called once per frame from the render-frame hook.
     *
     * <p>Everything this does is a volatile read, a subtract and two array
     * stores. It deliberately does <em>not</em> read the config, invoke
     * reflection or call {@code Minecraft.getInstance()}: whether these frames
     * count is decided once per client tick in {@link #tick(Minecraft)} and
     * published to {@link #measurable}. A measurement tool that reflects on
     * every frame is measuring itself.
     */
    public static void onFrame() {
        if (!hookFired) {
            hookFired = true;
            Capabilities.enable(Capability.FRAME_BOUNDARY);
        }
        // The block-entity budget gets its exact frame boundary from here too,
        // which is what lets its hook stop reading the clock per block entity.
        com.intelium.optimization.RenderBudget.beginFrame();

        if (!measurable) {
            TRACKER.invalidate();
            return;
        }
        TRACKER.frame(System.nanoTime());
    }

    /**
     * Decides, once per client tick, whether the frames being rendered right now
     * are evidence of anything - and whether something is capping them.
     *
     * <p>This is where the config read, the menu probe and the throttle check
     * live, because at 20 Hz they are free and at 300 Hz they would not be.
     * Deliberately conservative about {@link #measurable}: a false "we are fine"
     * is far more damaging than a missed measurement.
     */
    public static void tick(Minecraft client) {
        if (client == null) {
            measurable = false;
            return;
        }
        boolean active = client.isWindowActive() && client.level != null;
        if (active) {
            InteliumConfig cfg = InteliumConfigIO.get();
            boolean menuCapped = cfg.menuFpsLimit > 0
                    && MenuScreenProbe.menuOpen(client)
                    && !ModCompat.frameLimiterPresent();
            active = !menuCapped;
        }
        measurable = active;
        capped = active && RenderTweaks.fpsDeliberatelyThrottled(client);
    }

    /**
     * Restarts the warm-up because the world changed. Terrain streaming in is
     * the single largest source of frame times that describe loading rather
     * than rendering.
     */
    public static void onWorldChanged() {
        TRACKER.invalidate();
    }

    /** Whether enough consecutive measurable frames have been seen to act on. */
    public static boolean warm() {
        return hookFired && TRACKER.warm();
    }

    /** Whether the per-frame hook is attached at all. */
    public static boolean available() {
        return hookFired;
    }

    /**
     * Whether VSync or an FPS limit was pacing the most recent frame. When
     * true, the average and the high percentiles describe capped delivery: a
     * frame time <em>at</em> the cap proves nothing about headroom, though one
     * <em>past</em> it is still a real spike.
     */
    public static boolean capActive() {
        return capped;
    }

    /** Average FPS over the window, or 0 when there is nothing to report. */
    public static double averageFps() {
        return TRACKER.averageFps();
    }

    /** The 1% low over the window, in FPS. */
    public static double onePercentLowFps() {
        return TRACKER.onePercentLowFps();
    }

    /** A full reading of the distribution. Allocates; for reports only. */
    public static FrameTimeTracker.Snapshot snapshot() {
        return TRACKER.snapshot();
    }

    /** Drops every recorded frame and restarts the warm-up. */
    public static void reset() {
        TRACKER.reset();
        capped = false;
        measurable = false;
    }
}
