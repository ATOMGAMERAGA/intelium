package com.intelium.client;

import com.intelium.Intelium;
import com.intelium.compat.ModCompat;
import com.intelium.config.InteliumConfig;
import com.intelium.config.InteliumConfigIO;
import com.intelium.hud.AbBenchmark;
import com.intelium.optimization.CloudsMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.particle.ParticlesMode;

/**
 * Applies Intelium's live render tweaks to the vanilla {@link GameOptions}.
 *
 * <p>These are the levers that cut per-frame GPU/CPU cost while you walk and
 * look around (entity draw distance, particles, entity shadows, biome blend,
 * clouds, graphics mode, smooth lighting, VSync, render distance) - the things
 * a chunk-worker tweak alone cannot touch. Each is opt-in; when a lever is on,
 * Intelium captures the user's original value once and forces its own; when the
 * lever (or Intelium itself) is turned off, the captured value is restored.
 * Values are only written when they actually differ, so the (sometimes
 * expensive) vanilla change-callbacks fire at most once per transition - never
 * every tick.
 *
 * <p>Captured originals live in the persisted config ({@code cfg.captured}),
 * not in static fields: vanilla saves the <em>forced</em> values into
 * {@code options.txt} on quit, so without persistence the user's real settings
 * would be lost across a restart. With it, turning a lever off always restores
 * what the user actually had.
 *
 * <p>All access happens on the client/render thread (apply hooks and the client
 * tick), where touching {@code GameOptions} is safe.
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

    /**
     * Reconciles the live game options with the current config. Safe to call
     * every tick and from option apply-hooks; cheap when nothing changed.
     */
    public static synchronized void apply() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        GameOptions o = mc.options;

        InteliumConfig cfg = InteliumConfigIO.get();
        InteliumConfig.CapturedOptions cap = cfg.captured;
        boolean master = Intelium.IS_ENABLED && Intelium.IS_COMPATIBLE && cfg.tuneFrameSettings;

        boolean dirty = false;
        dirty |= applyEntityDistance(o, cap, master && cfg.maxEntityDistancePercent < 100,
                clampPercent(cfg.maxEntityDistancePercent) / 100.0);
        // Yield particle control to AsyncParticles when it is installed, so we
        // never fight its async pipeline (it warns about particle-mod overlap).
        // When it is absent, this restores any value Intelium previously managed.
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
        // Yield the FPS-limit levers to a dedicated frame limiter mod
        // (Dynamic FPS, FPS Reducer) so the two never fight over max FPS.
        dirty |= applyFpsLimit(o, cap,
                fpsLimitFor(mc, cfg, master && !ModCompat.frameLimiterPresent()));

        // Persist capture/restore transitions so originals survive a restart.
        if (dirty) InteliumConfigIO.flush();
    }

    /** Combines two downward caps where 0 means "off": the tighter one wins. */
    private static int mergeCaps(int a, int b) {
        return (a > 0 && b > 0) ? Math.min(a, b) : Math.max(a, b);
    }

    private static boolean applyEntityDistance(GameOptions o, InteliumConfig.CapturedOptions cap,
                                               boolean on, double capValue) {
        SimpleOption<Double> opt = o.getEntityDistanceScaling();
        if (on) {
            boolean captured = false;
            if (cap.entityDistance == null) {
                cap.entityDistance = opt.getValue();
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

    private static boolean applyParticles(GameOptions o, InteliumConfig.CapturedOptions cap,
                                          boolean on) {
        SimpleOption<ParticlesMode> opt = o.getParticles();
        if (on) {
            boolean captured = false;
            if (cap.particles == null) {
                cap.particles = opt.getValue().name();
                captured = true;
            }
            // Cap to DECREASED, but don't loosen a stricter (MINIMAL) setting.
            if (opt.getValue() == ParticlesMode.ALL) {
                setIfChanged(opt, ParticlesMode.DECREASED);
            }
            return captured;
        } else if (cap.particles != null) {
            setIfChanged(opt, parseEnum(ParticlesMode.class, cap.particles, opt.getValue()));
            cap.particles = null;
            return true;
        }
        return false;
    }

    private static boolean applyEntityShadows(GameOptions o, InteliumConfig.CapturedOptions cap,
                                              boolean on) {
        SimpleOption<Boolean> opt = o.getEntityShadows();
        if (on) {
            boolean captured = false;
            if (cap.entityShadows == null) {
                cap.entityShadows = opt.getValue();
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

    private static boolean applyBiomeBlend(GameOptions o, InteliumConfig.CapturedOptions cap,
                                           boolean on) {
        SimpleOption<Integer> opt = o.getBiomeBlendRadius();
        if (on) {
            boolean captured = false;
            if (cap.biomeBlend == null) {
                cap.biomeBlend = opt.getValue();
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

    private static boolean applyClouds(GameOptions o, InteliumConfig.CapturedOptions cap,
                                       CloudsMode mode) {
        SimpleOption<CloudRenderMode> opt = o.getCloudRenderMode();
        if (mode != CloudsMode.DEFAULT) {
            boolean captured = false;
            if (cap.clouds == null) {
                cap.clouds = opt.getValue().name();
                captured = true;
            }
            if (mode == CloudsMode.OFF) {
                setIfChanged(opt, CloudRenderMode.OFF);
            } else if (opt.getValue() == CloudRenderMode.FANCY) {
                // FAST caps volumetric clouds to flat; never raises OFF.
                setIfChanged(opt, CloudRenderMode.FAST);
            }
            return captured;
        } else if (cap.clouds != null) {
            setIfChanged(opt, parseEnum(CloudRenderMode.class, cap.clouds, opt.getValue()));
            cap.clouds = null;
            return true;
        }
        return false;
    }

    private static boolean applyGraphics(GameOptions o, InteliumConfig.CapturedOptions cap,
                                         boolean on) {
        // 1.21.11 split the old Graphics option into individual settings; the
        // "preset" option (Fast / Fancy / Fabulous / Custom) applies them as a
        // group, which is exactly the lever we want.
        SimpleOption<GraphicsMode> opt = o.getPreset();
        if (on) {
            // Never touch a hand-tuned CUSTOM mix: forcing Fast would overwrite
            // the user's individual choices in a way a restore cannot undo.
            if (cap.graphics == null && opt.getValue() == GraphicsMode.CUSTOM) return false;
            boolean captured = false;
            if (cap.graphics == null) {
                cap.graphics = opt.getValue().name();
                captured = true;
            }
            // Only step down Fancy/Fabulous. If the user tweaks an individual
            // option while this lever is on (the preset flips to CUSTOM), leave
            // their mix alone instead of stomping it back to Fast every tick.
            if (opt.getValue() == GraphicsMode.FANCY || opt.getValue() == GraphicsMode.FABULOUS) {
                setIfChanged(opt, GraphicsMode.FAST);
            }
            return captured;
        } else if (cap.graphics != null) {
            setIfChanged(opt, parseEnum(GraphicsMode.class, cap.graphics, opt.getValue()));
            cap.graphics = null;
            return true;
        }
        return false;
    }

    private static boolean applySmoothLighting(GameOptions o, InteliumConfig.CapturedOptions cap,
                                               boolean on) {
        SimpleOption<Boolean> opt = o.getAo();
        if (on) {
            boolean captured = false;
            if (cap.smoothLighting == null) {
                cap.smoothLighting = opt.getValue();
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

    private static boolean applyVsync(GameOptions o, InteliumConfig.CapturedOptions cap,
                                      boolean on) {
        SimpleOption<Boolean> opt = o.getEnableVsync();
        if (on) {
            boolean captured = false;
            if (cap.vsync == null) {
                cap.vsync = opt.getValue();
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

    private static boolean applyRenderDistance(GameOptions o, InteliumConfig.CapturedOptions cap,
                                               boolean on, int maxChunks) {
        SimpleOption<Integer> opt = o.getViewDistance();
        if (on) {
            boolean captured = false;
            if (cap.renderDistance == null) {
                cap.renderDistance = opt.getValue();
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

    private static boolean applySimulationDistance(GameOptions o, InteliumConfig.CapturedOptions cap,
                                                   boolean on, int maxChunks) {
        SimpleOption<Integer> opt = o.getSimulationDistance();
        if (on) {
            boolean captured = false;
            if (cap.simulationDistance == null) {
                cap.simulationDistance = opt.getValue();
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

    /**
     * The FPS cap that should be active this tick: the background limit while
     * the window is unfocused, the menu limit while a screen is open, the
     * tighter of the two when both apply; {@code 0} = hands off. The menu
     * limit never engages while the A/B benchmark runs - a capped menu frame
     * rate would corrupt the measurement.
     */
    private static int fpsLimitFor(MinecraftClient mc, InteliumConfig cfg, boolean on) {
        if (!on) return 0;
        int background = (cfg.backgroundFpsLimit > 0 && !mc.isWindowFocused())
                ? cfg.backgroundFpsLimit : 0;
        int menu = (cfg.menuFpsLimit > 0 && mc.currentScreen != null
                && !AbBenchmark.INSTANCE.isRunning())
                ? cfg.menuFpsLimit : 0;
        return mergeCaps(background, menu);
    }

    private static boolean applyFpsLimit(GameOptions o, InteliumConfig.CapturedOptions cap,
                                         int limit) {
        SimpleOption<Integer> opt = o.getMaxFps();
        if (limit > 0) {
            boolean captured = false;
            if (cap.fpsLimit == null) {
                cap.fpsLimit = opt.getValue();
                captured = true;
            }
            int target = Math.max(MIN_FPS_LIMIT, limit);
            // Never raise a max-FPS limit the user already set lower.
            setIfChanged(opt, Math.min(cap.fpsLimit, target));
            return captured;
        } else if (cap.fpsLimit != null) {
            // Focus/menu state cleared (or the lever turned off): restore now.
            setIfChanged(opt, cap.fpsLimit);
            cap.fpsLimit = null;
            return true;
        }
        return false;
    }

    private static <T> void setIfChanged(SimpleOption<T> opt, T value) {
        if (!java.util.Objects.equals(opt.getValue(), value)) {
            opt.setValue(value);
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
