package com.intelium.client;

import com.intelium.Intelium;
import com.intelium.compat.ModCompat;
import com.intelium.config.InteliumConfig;
import com.intelium.config.InteliumConfigIO;
import com.intelium.optimization.CullingStrength;
import com.intelium.optimization.FramePressure;
import com.intelium.optimization.RenderBudget;
import net.minecraft.client.Minecraft;

/**
 * Feeds Intelium's Render Budget Engine, once per client tick (26.x).
 *
 * <p>Everything the render-path hooks need to decide anything is computed here
 * and published to {@link RenderBudget}: the camera geometry (framebuffer height
 * and field of view, which together say how many pixels a block covers at a
 * given distance), the configured levels, and how far short of its target the
 * frame rate is running. The hooks themselves then do nothing but read a
 * volatile field and compare - which matters, because they run once per entity,
 * once per block entity and once per particle spawn.
 *
 * <p>The engine stands down whenever Intelium is off, the GPU is unsupported, or
 * there is no world: with no camera to measure against there is no honest answer
 * to "is this too small to see?", and the safe answer is to draw everything.
 */
public final class RenderBudgetDriver {

    private RenderBudgetDriver() {}

    /** Assumed FOV when the option cannot be read; the vanilla default. */
    private static final double FALLBACK_FOV = 70.0;

    private static final FramePressure PRESSURE = new FramePressure();

    /**
     * Ticks to keep skipping samples after a throttled stretch ends: the FPS
     * counter is a trailing ~1s average, so the first second after refocus /
     * menu close still reflects the throttled frames.
     */
    private static final int RECOVERY_TICKS = 20;
    private static int recoveryTicks;

    /** Called once per client tick, before {@link RenderTweaks#apply()}. */
    public static void tick(Minecraft client) {
        if (client == null) return;
        // A fresh tick's particle allowance, whether or not the budget is on -
        // so turning it on mid-explosion starts from a clean count.
        RenderBudget.beginTick();
        if (!measurable(client)) {
            recoveryTicks = RECOVERY_TICKS;
        } else if (recoveryTicks > 0) {
            // Let the throttled frames age out of the trailing FPS counter.
            recoveryTicks--;
        } else {
            PRESSURE.push(client.getFps());
        }
        reconcile(client);
    }

    /**
     * Whether this tick's frame rate says anything about how hard the machine
     * is working.
     *
     * <p>It does not when the window is unfocused or a menu is open with a cap
     * on it: those frames are deliberately throttled, and counting them would
     * peg the pressure signal at maximum, so alt-tabbing back would land you in
     * the most aggressive culling the settings allow for a second or two.
     * Skipping the sample holds the last reading rather than resetting it, so
     * the budgets sit still instead of swinging - the same treatment
     * {@link AdaptiveDistance} gives the render-distance controller.
     */
    private static boolean measurable(Minecraft client) {
        if (!client.isWindowActive()) return false;
        InteliumConfig cfg = InteliumConfigIO.get();
        boolean menuCapped = cfg.menuFpsLimit > 0
                && MenuScreenProbe.menuOpen(client)
                && !ModCompat.frameLimiterPresent();
        return !menuCapped;
    }

    /**
     * Reconciles the engine with the config right now. Called from the settings
     * screen's apply hooks so a changed level takes effect on the next frame
     * rather than the next tick.
     */
    public static void apply() {
        reconcile(Minecraft.getInstance());
    }

    private static void reconcile(Minecraft client) {
        InteliumConfig cfg = InteliumConfigIO.get();
        boolean on = Intelium.IS_ENABLED && Intelium.IS_COMPATIBLE
                && cfg.renderBudget && client != null && client.level != null;
        if (!on) {
            RenderBudget.disable();
            PRESSURE.reset();
            return;
        }
        // Yield the particle budget to AsyncParticles when it is installed, the
        // same way the vanilla particle lever does: it owns particle
        // performance from its own worker threads, and it warns about mods that
        // manipulate particles underneath it. The other two budgets touch
        // nothing it cares about.
        CullingStrength particles = ModCompat.asyncParticlesPresent()
                ? CullingStrength.OFF
                : CullingStrength.fromKey(cfg.particleBudget);

        RenderBudget.update(true, framebufferHeight(client), fov(client),
                CullingStrength.fromKey(cfg.entityCulling),
                CullingStrength.fromKey(cfg.blockEntityCulling),
                particles,
                cfg.adaptiveCulling,
                PRESSURE.pressure(cfg.adaptiveFpsTarget));
    }

    /** Real (not GUI-scaled) height of the render target, in pixels. */
    private static int framebufferHeight(Minecraft client) {
        try {
            return client.getWindow().getHeight();
        } catch (Throwable t) {
            // 0 reads downstream as "camera unknown": nothing gets culled.
            return 0;
        }
    }

    private static double fov(Minecraft client) {
        try {
            return client.options.fov().get();
        } catch (Throwable t) {
            return FALLBACK_FOV;
        }
    }
}
