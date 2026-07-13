package com.intelium.client;

import com.intelium.Intelium;
import com.intelium.compat.ModCompat;
import com.intelium.config.InteliumConfig;
import com.intelium.config.InteliumConfigIO;
import com.intelium.optimization.CloudsMode;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.OptionInstance;
import net.minecraft.server.level.ParticleStatus;

/**
 * Applies Intelium's live render tweaks to the vanilla {@link Options} (26.x).
 *
 * <p>These cut per-frame GPU/CPU cost while moving and looking around (entity
 * draw distance, particles, entity shadows, biome blend, clouds, graphics mode,
 * smooth lighting, VSync, render distance). Each is opt-in; when a lever is on,
 * Intelium captures the user's original value once and forces its own; when the
 * lever (or Intelium itself) is turned off, the captured value is restored.
 * Values are only written when they actually differ.
 *
 * <p>Captured originals live in the persisted config ({@code cfg.captured}),
 * not in static fields: vanilla saves the <em>forced</em> values into
 * {@code options.txt} on quit, so without persistence the user's real settings
 * would be lost across a restart.
 */
public final class RenderTweaks {

    private RenderTweaks() {}

    /** Lowest entity-distance scaling vanilla allows (50%). */
    private static final double MIN_ENTITY_SCALE = 0.5;
    /** Vanilla render-distance bounds, in chunks. */
    private static final int MIN_RENDER_DISTANCE = 2;
    /** Vanilla simulation-distance lower bound, in chunks. */
    private static final int MIN_SIMULATION_DISTANCE = 5;
    /** Lowest max-FPS value the vanilla option accepts. */
    private static final int MIN_FPS_LIMIT = 10;

    public static synchronized void apply() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        Options o = mc.options;

        InteliumConfig cfg = InteliumConfigIO.get();
        InteliumConfig.CapturedOptions cap = cfg.captured;
        boolean master = Intelium.IS_ENABLED && Intelium.IS_COMPATIBLE && cfg.tuneFrameSettings;

        boolean dirty = false;
        dirty |= applyEntityDistance(o, cap, master && cfg.maxEntityDistancePercent < 100,
                clampPercent(cfg.maxEntityDistancePercent) / 100.0);
        // Yield particle control to AsyncParticles when it is installed.
        dirty |= applyParticles(o, cap,
                master && cfg.limitParticles && !ModCompat.asyncParticlesPresent());
        dirty |= applyEntityShadows(o, cap, master && cfg.disableEntityShadows);
        dirty |= applyBiomeBlend(o, cap, master && cfg.fastBiomeBlend);
        dirty |= applyClouds(o, cap, master ? CloudsMode.fromKey(cfg.cloudsMode)
                                            : CloudsMode.DEFAULT);
        dirty |= applyGraphics(o, cap, master && cfg.fastGraphics);
        dirty |= applySmoothLighting(o, cap, master && cfg.disableSmoothLighting);
        dirty |= applyVsync(o, cap, master && cfg.disableVsync);
        // The render-distance cap is the tighter of the static lever and the
        // live adaptive controller (0 = the respective lever is off).
        int rdCap = mergeCaps(master && cfg.maxRenderDistance > 0 ? cfg.maxRenderDistance : 0,
                master ? AdaptiveDistance.currentCap() : 0);
        dirty |= applyRenderDistance(o, cap, rdCap > 0, rdCap);
        dirty |= applySimulationDistance(o, cap, master && cfg.maxSimulationDistance > 0,
                cfg.maxSimulationDistance);
        // Yield the background limit to a dedicated frame limiter mod
        // (Dynamic FPS, FPS Reducer) so the two never fight over max FPS.
        dirty |= applyBackgroundFps(mc, o, cap,
                master && cfg.backgroundFpsLimit > 0 && !ModCompat.frameLimiterPresent(),
                cfg.backgroundFpsLimit);

        // Persist capture/restore transitions so originals survive a restart.
        if (dirty) InteliumConfigIO.flush();
    }

    /** Combines two downward caps where 0 means "off": the tighter one wins. */
    private static int mergeCaps(int a, int b) {
        return (a > 0 && b > 0) ? Math.min(a, b) : Math.max(a, b);
    }

    private static boolean applyEntityDistance(Options o, InteliumConfig.CapturedOptions cap,
                                               boolean on, double capValue) {
        OptionInstance<Double> opt = o.entityDistanceScaling();
        if (on) {
            boolean captured = false;
            if (cap.entityDistance == null) {
                cap.entityDistance = opt.get();
                captured = true;
            }
            double target = Math.max(MIN_ENTITY_SCALE, capValue);
            // Only cap downward; never raise a value the user already set lower.
            setIfChanged(opt, Math.min(cap.entityDistance, target));
            return captured;
        } else if (cap.entityDistance != null) {
            setIfChanged(opt, cap.entityDistance);
            cap.entityDistance = null;
            return true;
        }
        return false;
    }

    private static boolean applyParticles(Options o, InteliumConfig.CapturedOptions cap,
                                          boolean on) {
        OptionInstance<ParticleStatus> opt = o.particles();
        if (on) {
            boolean captured = false;
            if (cap.particles == null) {
                cap.particles = opt.get().name();
                captured = true;
            }
            // Cap to DECREASED, but don't loosen a stricter (MINIMAL) setting.
            if (opt.get() == ParticleStatus.ALL) {
                setIfChanged(opt, ParticleStatus.DECREASED);
            }
            return captured;
        } else if (cap.particles != null) {
            setIfChanged(opt, parseEnum(ParticleStatus.class, cap.particles, opt.get()));
            cap.particles = null;
            return true;
        }
        return false;
    }

    private static boolean applyEntityShadows(Options o, InteliumConfig.CapturedOptions cap,
                                              boolean on) {
        OptionInstance<Boolean> opt = o.entityShadows();
        if (on) {
            boolean captured = false;
            if (cap.entityShadows == null) {
                cap.entityShadows = opt.get();
                captured = true;
            }
            setIfChanged(opt, Boolean.FALSE);
            return captured;
        } else if (cap.entityShadows != null) {
            setIfChanged(opt, cap.entityShadows);
            cap.entityShadows = null;
            return true;
        }
        return false;
    }

    private static boolean applyBiomeBlend(Options o, InteliumConfig.CapturedOptions cap,
                                           boolean on) {
        OptionInstance<Integer> opt = o.biomeBlendRadius();
        if (on) {
            boolean captured = false;
            if (cap.biomeBlend == null) {
                cap.biomeBlend = opt.get();
                captured = true;
            }
            setIfChanged(opt, 0);
            return captured;
        } else if (cap.biomeBlend != null) {
            setIfChanged(opt, cap.biomeBlend);
            cap.biomeBlend = null;
            return true;
        }
        return false;
    }

    private static boolean applyClouds(Options o, InteliumConfig.CapturedOptions cap,
                                       CloudsMode mode) {
        OptionInstance<CloudStatus> opt = o.cloudStatus();
        if (mode != CloudsMode.DEFAULT) {
            boolean captured = false;
            if (cap.clouds == null) {
                cap.clouds = opt.get().name();
                captured = true;
            }
            if (mode == CloudsMode.OFF) {
                setIfChanged(opt, CloudStatus.OFF);
            } else if (opt.get() == CloudStatus.FANCY) {
                // FAST caps volumetric clouds to flat; never raises OFF.
                setIfChanged(opt, CloudStatus.FAST);
            }
            return captured;
        } else if (cap.clouds != null) {
            setIfChanged(opt, parseEnum(CloudStatus.class, cap.clouds, opt.get()));
            cap.clouds = null;
            return true;
        }
        return false;
    }

    private static boolean applyGraphics(Options o, InteliumConfig.CapturedOptions cap,
                                         boolean on) {
        // 26.x split the old Graphics option into individual settings; the
        // "graphics preset" option (Fast / Fancy / Fabulous / Custom) applies
        // them as a group, which is exactly the lever we want.
        OptionInstance<GraphicsPreset> opt = o.graphicsPreset();
        if (on) {
            // Never touch a hand-tuned CUSTOM mix: forcing Fast would overwrite
            // the user's individual choices in a way a restore cannot undo.
            if (cap.graphics == null && opt.get() == GraphicsPreset.CUSTOM) return false;
            boolean captured = false;
            if (cap.graphics == null) {
                cap.graphics = opt.get().name();
                captured = true;
            }
            // Only step down Fancy/Fabulous. If the user tweaks an individual
            // option while this lever is on (the preset flips to CUSTOM), leave
            // their mix alone instead of stomping it back to Fast every tick.
            if (opt.get() == GraphicsPreset.FANCY || opt.get() == GraphicsPreset.FABULOUS) {
                setIfChanged(opt, GraphicsPreset.FAST);
            }
            return captured;
        } else if (cap.graphics != null) {
            setIfChanged(opt, parseEnum(GraphicsPreset.class, cap.graphics, opt.get()));
            cap.graphics = null;
            return true;
        }
        return false;
    }

    private static boolean applySmoothLighting(Options o, InteliumConfig.CapturedOptions cap,
                                               boolean on) {
        OptionInstance<Boolean> opt = o.ambientOcclusion();
        if (on) {
            boolean captured = false;
            if (cap.smoothLighting == null) {
                cap.smoothLighting = opt.get();
                captured = true;
            }
            setIfChanged(opt, Boolean.FALSE);
            return captured;
        } else if (cap.smoothLighting != null) {
            setIfChanged(opt, cap.smoothLighting);
            cap.smoothLighting = null;
            return true;
        }
        return false;
    }

    private static boolean applyVsync(Options o, InteliumConfig.CapturedOptions cap,
                                      boolean on) {
        OptionInstance<Boolean> opt = o.enableVsync();
        if (on) {
            boolean captured = false;
            if (cap.vsync == null) {
                cap.vsync = opt.get();
                captured = true;
            }
            setIfChanged(opt, Boolean.FALSE);
            return captured;
        } else if (cap.vsync != null) {
            setIfChanged(opt, cap.vsync);
            cap.vsync = null;
            return true;
        }
        return false;
    }

    private static boolean applyRenderDistance(Options o, InteliumConfig.CapturedOptions cap,
                                               boolean on, int maxChunks) {
        OptionInstance<Integer> opt = o.renderDistance();
        if (on) {
            boolean captured = false;
            if (cap.renderDistance == null) {
                cap.renderDistance = opt.get();
                captured = true;
            }
            int target = Math.max(MIN_RENDER_DISTANCE, maxChunks);
            // Only cap downward; never raise a distance the user set lower.
            setIfChanged(opt, Math.min(cap.renderDistance, target));
            return captured;
        } else if (cap.renderDistance != null) {
            setIfChanged(opt, cap.renderDistance);
            cap.renderDistance = null;
            return true;
        }
        return false;
    }

    private static boolean applySimulationDistance(Options o, InteliumConfig.CapturedOptions cap,
                                                   boolean on, int maxChunks) {
        OptionInstance<Integer> opt = o.simulationDistance();
        if (on) {
            boolean captured = false;
            if (cap.simulationDistance == null) {
                cap.simulationDistance = opt.get();
                captured = true;
            }
            int target = Math.max(MIN_SIMULATION_DISTANCE, maxChunks);
            // Only cap downward; never raise a distance the user set lower.
            setIfChanged(opt, Math.min(cap.simulationDistance, target));
            return captured;
        } else if (cap.simulationDistance != null) {
            setIfChanged(opt, cap.simulationDistance);
            cap.simulationDistance = null;
            return true;
        }
        return false;
    }

    private static boolean applyBackgroundFps(Minecraft mc, Options o,
                                              InteliumConfig.CapturedOptions cap,
                                              boolean on, int limit) {
        OptionInstance<Integer> opt = o.framerateLimit();
        if (on && !mc.isWindowActive()) {
            boolean captured = false;
            if (cap.fpsLimit == null) {
                cap.fpsLimit = opt.get();
                captured = true;
            }
            int target = Math.max(MIN_FPS_LIMIT, limit);
            // Never raise a max-FPS limit the user already set lower.
            setIfChanged(opt, Math.min(cap.fpsLimit, target));
            return captured;
        } else if (cap.fpsLimit != null) {
            // Focus regained (or the lever turned off): restore immediately.
            setIfChanged(opt, cap.fpsLimit);
            cap.fpsLimit = null;
            return true;
        }
        return false;
    }

    private static <T> void setIfChanged(OptionInstance<T> opt, T value) {
        if (!java.util.Objects.equals(opt.get(), value)) {
            opt.set(value);
        }
    }

    /** Parses a persisted enum name, falling back when the name is unknown. */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }

    private static int clampPercent(int pct) {
        return Math.max(50, Math.min(100, pct));
    }
}
