package com.intelium.client;

import com.intelium.Intelium;
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

    /** Called once per client tick, before {@link RenderTweaks#apply()}. */
    public static void tick(Minecraft client) {
        if (client == null) return;
        // A fresh tick's particle allowance, whether or not the budget is on -
        // so turning it on mid-explosion starts from a clean count.
        RenderBudget.beginTick();
        PRESSURE.push(client.getFps());
        reconcile(client);
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
        RenderBudget.update(true, framebufferHeight(client), fov(client),
                CullingStrength.fromKey(cfg.entityCulling),
                CullingStrength.fromKey(cfg.blockEntityCulling),
                CullingStrength.fromKey(cfg.particleBudget),
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
