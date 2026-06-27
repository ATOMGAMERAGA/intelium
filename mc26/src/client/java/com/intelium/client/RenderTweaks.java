package com.intelium.client;

import com.intelium.Intelium;
import com.intelium.compat.ModCompat;
import com.intelium.config.InteliumConfig;
import com.intelium.config.InteliumConfigIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.OptionInstance;
import net.minecraft.server.level.ParticleStatus;

/**
 * Applies Intelium's live render tweaks to the vanilla {@link Options} (26.x).
 *
 * <p>These cut per-frame GPU/CPU cost while moving and looking around (entity
 * draw distance, particles, entity shadows, biome blend). Each is opt-in; when a
 * lever is on, Intelium captures the user's original value once and forces its
 * own; when the lever (or Intelium) is turned off, the captured value is
 * restored. Values are only written when they actually differ.
 */
public final class RenderTweaks {

    private RenderTweaks() {}

    // Captured originals. null == "not currently managing this lever".
    private static Double origEntityDistance;
    private static ParticleStatus origParticles;
    private static Boolean origEntityShadows;
    private static Integer origBiomeBlend;

    /** Lowest entity-distance scaling vanilla allows (50%). */
    private static final double MIN_ENTITY_SCALE = 0.5;

    public static synchronized void apply() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        Options o = mc.options;

        InteliumConfig cfg = InteliumConfigIO.get();
        boolean master = Intelium.IS_ENABLED && Intelium.IS_COMPATIBLE && cfg.tuneFrameSettings;

        applyEntityDistance(o, master && cfg.maxEntityDistancePercent < 100,
                clampPercent(cfg.maxEntityDistancePercent) / 100.0);
        // Yield particle control to AsyncParticles when it is installed.
        applyParticles(o, master && cfg.limitParticles && !ModCompat.asyncParticlesPresent());
        applyEntityShadows(o, master && cfg.disableEntityShadows);
        applyBiomeBlend(o, master && cfg.fastBiomeBlend);
    }

    private static void applyEntityDistance(Options o, boolean on, double cap) {
        OptionInstance<Double> opt = o.entityDistanceScaling();
        if (on) {
            if (origEntityDistance == null) origEntityDistance = opt.get();
            double target = Math.max(MIN_ENTITY_SCALE, cap);
            double desired = Math.min(origEntityDistance, target);
            setIfChanged(opt, desired);
        } else if (origEntityDistance != null) {
            setIfChanged(opt, origEntityDistance);
            origEntityDistance = null;
        }
    }

    private static void applyParticles(Options o, boolean on) {
        OptionInstance<ParticleStatus> opt = o.particles();
        if (on) {
            if (origParticles == null) origParticles = opt.get();
            if (opt.get() == ParticleStatus.ALL) {
                setIfChanged(opt, ParticleStatus.DECREASED);
            }
        } else if (origParticles != null) {
            setIfChanged(opt, origParticles);
            origParticles = null;
        }
    }

    private static void applyEntityShadows(Options o, boolean on) {
        OptionInstance<Boolean> opt = o.entityShadows();
        if (on) {
            if (origEntityShadows == null) origEntityShadows = opt.get();
            setIfChanged(opt, Boolean.FALSE);
        } else if (origEntityShadows != null) {
            setIfChanged(opt, origEntityShadows);
            origEntityShadows = null;
        }
    }

    private static void applyBiomeBlend(Options o, boolean on) {
        OptionInstance<Integer> opt = o.biomeBlendRadius();
        if (on) {
            if (origBiomeBlend == null) origBiomeBlend = opt.get();
            setIfChanged(opt, 0);
        } else if (origBiomeBlend != null) {
            setIfChanged(opt, origBiomeBlend);
            origBiomeBlend = null;
        }
    }

    private static <T> void setIfChanged(OptionInstance<T> opt, T value) {
        if (!java.util.Objects.equals(opt.get(), value)) {
            opt.set(value);
        }
    }

    private static int clampPercent(int pct) {
        return Math.max(50, Math.min(100, pct));
    }
}
