package com.intelium.client;

import com.intelium.Intelium;
import com.intelium.compat.ModCompat;
import com.intelium.config.InteliumConfig;
import com.intelium.config.InteliumConfigIO;
import com.intelium.hud.AbBenchmark;
import com.intelium.hud.FpsTracker;
import com.intelium.optimization.AdaptiveDistanceController;
import net.minecraft.client.MinecraftClient;

/**
 * Client driver for the adaptive render distance: feeds the pure-logic
 * {@link AdaptiveDistanceController} with the smoothed FPS once per tick and
 * publishes the resulting cap for {@link RenderTweaks} to apply through the
 * usual capture/restore path.
 *
 * <p>Measurement fully resets (and any reduction is dropped) only when the
 * feature is off or no world is loaded. While the window is unfocused, a menu
 * FPS cap is active, or the A/B benchmark is running (it toggles Intelium
 * itself, so its FPS swings must not steer the controller), the reduction is
 * <em>held</em> and only measurement pauses: those frames read as artificially
 * low FPS, but dropping the reduction (as earlier versions did) forced a full
 * chunk re-load on every alt-tab - and another one on refocus when the
 * controller re-reduced. The reduction is intentionally not persisted: every
 * launch starts unreduced and re-measures.
 */
public final class AdaptiveDistance {

    private AdaptiveDistance() {}

    /** ~5s rolling window at 20 ticks/s; smooths chunk-build bursts away. */
    private static final FpsTracker TRACKER = new FpsTracker(100);
    /** Samples needed before the controller may act (~2s warm-up). */
    private static final int WARMUP_SAMPLES = 40;

    /**
     * Ticks to keep skipping samples after a hold ends: the game's FPS counter
     * is a trailing ~1s average, so the first second after refocus / menu
     * close still reflects the throttled frames.
     */
    private static final int RECOVERY_TICKS = 20;

    private static final AdaptiveDistanceController CONTROLLER = new AdaptiveDistanceController();
    private static volatile int cap = 0;
    private static int recoveryTicks;

    /** Called once per client tick, before {@link RenderTweaks#apply()}. */
    public static void tick(MinecraftClient client) {
        InteliumConfig cfg = InteliumConfigIO.get();
        // The benchmark check must come before the feature gate: the benchmark
        // toggles Intelium.IS_ENABLED itself for its OFF half, and reading
        // that as "feature turned off" dropped the reduction mid-run - a full
        // chunk re-load in the middle of the measurement it was skewing.
        if (AbBenchmark.INSTANCE.isRunning()) {
            TRACKER.reset();
            recoveryTicks = RECOVERY_TICKS;
            return;
        }
        boolean featureOn = Intelium.IS_ENABLED && Intelium.IS_COMPATIBLE
                && cfg.tuneFrameSettings && cfg.adaptiveRenderDistance
                && client.world != null;
        if (!featureOn) {
            if (cap != 0 || CONTROLLER.reduction() > 0 || TRACKER.sampleCount() > 0) {
                CONTROLLER.reset();
                TRACKER.reset();
                cap = 0;
            }
            return;
        }
        if (!client.isWindowFocused() || menuCapActive(client, cfg)) {
            // Unfocused or menu-capped frames read as artificially low (or
            // meaningless) FPS: hold the current reduction (dropping it would
            // force a full chunk re-load on every alt-tab / menu) and forget
            // the tainted samples, then re-warm up once the game is front and
            // centre again.
            TRACKER.reset();
            recoveryTicks = RECOVERY_TICKS;
            return;
        }
        if (recoveryTicks > 0) {
            // The FPS counter is a trailing average; let the throttled frames
            // age out of it before measuring again.
            recoveryTicks--;
            return;
        }
        TRACKER.push(client.getCurrentFps());
        if (TRACKER.sampleCount() < WARMUP_SAMPLES) return;
        cap = CONTROLLER.update(cfg.adaptiveFpsTarget, TRACKER.smoothed(), baseDistance(client, cfg));
    }

    /** The current adaptive render-distance cap in chunks; 0 = hands off. */
    public static int currentCap() {
        return cap;
    }

    /** Whether the menu FPS limit is capping the frame rate right now. */
    private static boolean menuCapActive(MinecraftClient mc, InteliumConfig cfg) {
        return cfg.menuFpsLimit > 0 && RenderTweaks.menuScreenOpen(mc)
                && !ModCompat.frameLimiterPresent();
    }

    /**
     * The distance the controller works down from: the user's own setting
     * (the captured original when a cap already manages the option), further
     * bounded by the static Max Render Distance lever when that is set.
     */
    private static int baseDistance(MinecraftClient mc, InteliumConfig cfg) {
        Integer captured = cfg.captured.renderDistance;
        int user = captured != null ? captured : mc.options.getViewDistance().getValue();
        return cfg.maxRenderDistance > 0 ? Math.min(user, cfg.maxRenderDistance) : user;
    }
}
