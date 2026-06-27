package com.intelium.client;

import com.intelium.Intelium;
import com.intelium.config.InteliumConfig;
import com.intelium.config.InteliumConfigIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.particle.ParticlesMode;

/**
 * Applies Intelium's live render tweaks to the vanilla {@link GameOptions}.
 *
 * <p>These are the levers that cut per-frame GPU/CPU cost while you walk and
 * look around (entity draw distance, particles, entity shadows, biome blend) -
 * the things a chunk-worker tweak alone cannot touch. Each is opt-in; when a
 * lever is on, Intelium captures the user's original value once and forces its
 * own; when the lever (or Intelium itself) is turned off, the captured value is
 * restored. Values are only written when they actually differ, so the (sometimes
 * expensive) vanilla change-callbacks fire at most once per transition - never
 * every tick.
 *
 * <p>All access happens on the client/render thread (apply hooks and the client
 * tick), where touching {@code GameOptions} is safe.
 */
public final class RenderTweaks {

    private RenderTweaks() {}

    // Captured originals. null == "not currently managing this lever".
    private static Double origEntityDistance;
    private static ParticlesMode origParticles;
    private static Boolean origEntityShadows;
    private static Integer origBiomeBlend;

    /** Lowest entity-distance scaling vanilla allows (50%). */
    private static final double MIN_ENTITY_SCALE = 0.5;

    /**
     * Reconciles the live game options with the current config. Safe to call
     * every tick and from option apply-hooks; cheap when nothing changed.
     */
    public static synchronized void apply() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        GameOptions o = mc.options;

        InteliumConfig cfg = InteliumConfigIO.get();
        boolean master = Intelium.IS_ENABLED && Intelium.IS_COMPATIBLE && cfg.tuneFrameSettings;

        applyEntityDistance(o, master && cfg.maxEntityDistancePercent < 100,
                clampPercent(cfg.maxEntityDistancePercent) / 100.0);
        applyParticles(o, master && cfg.limitParticles);
        applyEntityShadows(o, master && cfg.disableEntityShadows);
        applyBiomeBlend(o, master && cfg.fastBiomeBlend);
    }

    private static void applyEntityDistance(GameOptions o, boolean on, double cap) {
        SimpleOption<Double> opt = o.getEntityDistanceScaling();
        if (on) {
            if (origEntityDistance == null) origEntityDistance = opt.getValue();
            double target = Math.max(MIN_ENTITY_SCALE, cap);
            // Only cap downward; never raise a value the user already set lower.
            double desired = Math.min(origEntityDistance, target);
            setIfChanged(opt, desired);
        } else if (origEntityDistance != null) {
            setIfChanged(opt, origEntityDistance);
            origEntityDistance = null;
        }
    }

    private static void applyParticles(GameOptions o, boolean on) {
        SimpleOption<ParticlesMode> opt = o.getParticles();
        if (on) {
            if (origParticles == null) origParticles = opt.getValue();
            // Cap to DECREASED, but don't loosen a stricter (MINIMAL) setting.
            if (opt.getValue() == ParticlesMode.ALL) {
                setIfChanged(opt, ParticlesMode.DECREASED);
            }
        } else if (origParticles != null) {
            setIfChanged(opt, origParticles);
            origParticles = null;
        }
    }

    private static void applyEntityShadows(GameOptions o, boolean on) {
        SimpleOption<Boolean> opt = o.getEntityShadows();
        if (on) {
            if (origEntityShadows == null) origEntityShadows = opt.getValue();
            setIfChanged(opt, Boolean.FALSE);
        } else if (origEntityShadows != null) {
            setIfChanged(opt, origEntityShadows);
            origEntityShadows = null;
        }
    }

    private static void applyBiomeBlend(GameOptions o, boolean on) {
        SimpleOption<Integer> opt = o.getBiomeBlendRadius();
        if (on) {
            if (origBiomeBlend == null) origBiomeBlend = opt.getValue();
            setIfChanged(opt, 0);
        } else if (origBiomeBlend != null) {
            setIfChanged(opt, origBiomeBlend);
            origBiomeBlend = null;
        }
    }

    private static <T> void setIfChanged(SimpleOption<T> opt, T value) {
        if (!java.util.Objects.equals(opt.getValue(), value)) {
            opt.setValue(value);
        }
    }

    private static int clampPercent(int pct) {
        return Math.max(50, Math.min(100, pct));
    }
}
