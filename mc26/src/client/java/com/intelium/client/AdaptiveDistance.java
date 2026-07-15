package com.intelium.client;

import com.intelium.Intelium;
import com.intelium.compat.ModCompat;
import com.intelium.config.InteliumConfig;
import com.intelium.config.InteliumConfigIO;
import com.intelium.hud.FpsTracker;
import com.intelium.optimization.AdaptiveDistanceController;
import net.minecraft.client.Minecraft;

/**
 * Client driver for the adaptive render distance (26.x): feeds the pure-logic
 * {@link AdaptiveDistanceController} with the smoothed FPS once per tick and
 * publishes the resulting cap for {@link RenderTweaks} to apply through the
 * usual capture/restore path.
 *
 * <p>Measurement pauses (and any reduction is dropped) whenever the feature is
 * off, no world is loaded, or the window is unfocused (a background frame
 * limit would read as "low FPS" and wrongly shrink the world). The reduction
 * is intentionally not persisted: every launch starts unreduced and
 * re-measures.
 */
public final class AdaptiveDistance {

    private AdaptiveDistance() {}

    /** ~5s rolling window at 20 ticks/s; smooths chunk-build bursts away. */
    private static final FpsTracker TRACKER = new FpsTracker(100);
    /** Samples needed before the controller may act (~2s warm-up). */
    private static final int WARMUP_SAMPLES = 40;

    private static final AdaptiveDistanceController CONTROLLER = new AdaptiveDistanceController();
    private static volatile int cap = 0;

    /** Called once per client tick, before {@link RenderTweaks#apply()}. */
    public static void tick(Minecraft client) {
        InteliumConfig cfg = InteliumConfigIO.get();
        boolean active = Intelium.IS_ENABLED && Intelium.IS_COMPATIBLE
                && cfg.tuneFrameSettings && cfg.adaptiveRenderDistance
                && client.level != null
                && client.isWindowActive();
        if (!active) {
            if (cap != 0 || CONTROLLER.reduction() > 0 || TRACKER.sampleCount() > 0) {
                CONTROLLER.reset();
                TRACKER.reset();
                cap = 0;
            }
            return;
        }
        if (menuCapActive(client, cfg)) {
            // The menu FPS limit makes the measured FPS meaningless: hold the
            // current reduction and forget the capped samples, then re-warm up
            // once the menu closes.
            TRACKER.reset();
            return;
        }
        TRACKER.push(client.getFps());
        if (TRACKER.sampleCount() < WARMUP_SAMPLES) return;
        cap = CONTROLLER.update(cfg.adaptiveFpsTarget, TRACKER.smoothed(), baseDistance(client, cfg));
    }

    /** The current adaptive render-distance cap in chunks; 0 = hands off. */
    public static int currentCap() {
        return cap;
    }

    /** Whether the menu FPS limit is capping the frame rate right now. */
    private static boolean menuCapActive(Minecraft mc, InteliumConfig cfg) {
        return cfg.menuFpsLimit > 0 && mc.screen != null
                && !ModCompat.frameLimiterPresent();
    }

    /**
     * The distance the controller works down from: the user's own setting
     * (the captured original when a cap already manages the option), further
     * bounded by the static Max Render Distance lever when that is set.
     */
    private static int baseDistance(Minecraft mc, InteliumConfig cfg) {
        Integer captured = cfg.captured.renderDistance;
        int user = captured != null ? captured : mc.options.renderDistance().get();
        return cfg.maxRenderDistance > 0 ? Math.min(user, cfg.maxRenderDistance) : user;
    }
}
