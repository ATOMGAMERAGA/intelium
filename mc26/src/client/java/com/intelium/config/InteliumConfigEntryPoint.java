package com.intelium.config;

import com.intelium.Capabilities;
import com.intelium.Capability;
import com.intelium.Intelium;
import com.intelium.RenderBackend;
import com.intelium.client.ChunkLoadingBooster;
import com.intelium.client.FrameReportExporter;
import com.intelium.client.InteliumGame;
import com.intelium.client.RenderBudgetDriver;
import com.intelium.client.RenderTweaks;
import com.intelium.optimization.ChunkLoadingMode;
import com.intelium.optimization.CloudsMode;
import com.intelium.optimization.CullingStrength;
import com.intelium.optimization.OptimizationProfile;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Registers Intelium's page inside Sodium's video settings (26.x).
 *
 * <p>26.x reworked Minecraft's GUI for the Vulkan renderer, so the custom
 * overlay/benchmark screens from the 1.21.11 build are not present here. The core
 * optimization options - everything that actually boosts FPS - are fully wired:
 * enable, profile, chunk workers, the live render tweaks, and fast chunk loading.
 */
public class InteliumConfigEntryPoint implements ConfigEntryPoint {

    private final StorageEventHandler saveHook = InteliumConfigIO::flush;

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("intelium", path);
    }

    /**
     * An option's own explanatory tooltip with the live status line appended,
     * so showing the GPU status no longer costs the option its explanation.
     */
    private static Component tooltipWithStatus(String key) {
        return Component.translatable(key).append("\n\n").append(statusTooltip());
    }

    /**
     * A tooltip that says why an option is greyed out when its hook could not
     * attach.
     *
     * <p>A disabled control with no explanation is the thing this release set
     * out to remove: previously a feature whose hook had silently failed still
     * looked available, and one that was correctly greyed out never said why.
     * The reason comes from the capability registry, so it names the actual
     * class and descriptor that changed.
     */
    private static Component tooltipWithCapability(String key, Capability capability) {
        Component base = tooltipWithStatus(key);
        if (Capabilities.available(capability)) return base;
        String reason = Capabilities.reason(capability);
        Component why = reason == null
                ? Component.translatable("intelium.capability.unavailable.unknown")
                : Component.literal(reason);
        return base.copy()
                .append("\n\n")
                .append(Component.translatable("intelium.capability.unavailable",
                        Component.translatable(capability.displayKey), why));
    }

    /** Live status line shown as the tooltip on the interactive options. */
    private static Component statusTooltip() {
        if (Intelium.IS_COMPATIBLE) {
            if (Intelium.DETECTED_BACKEND != RenderBackend.UNKNOWN) {
                return Component.translatable("intelium.status.active_backend",
                        Intelium.DETECTED_GENERATION.display,
                        Intelium.DETECTED_BACKEND.displayName);
            }
            return Component.translatable("intelium.status.active", Intelium.DETECTED_GENERATION.display);
        }
        Component reason = Intelium.DISABLED_REASON_KEY == null
                ? Component.translatable("intelium.status.pending")
                : Component.translatable(Intelium.DISABLED_REASON_KEY);
        String renderer = Intelium.DETECTED_RENDERER;
        Component gpu = (renderer == null || renderer.isEmpty())
                ? Component.translatable("intelium.gpus.detected.pending")
                : Component.literal(renderer);
        return Component.translatable("intelium.status.unsupported", gpu, reason);
    }

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        try {
            buildPage(builder);
        } catch (Throwable t) {
            Intelium.LOGGER.warn("Intelium: could not register its Sodium config page on this "
                    + "Sodium build; page omitted. Optimizations still work.", t);
        }
    }

    private void buildPage(ConfigBuilder builder) {
        InteliumConfig cfg = InteliumConfigIO.get();

        ModOptionsBuilder mod = builder.registerOwnModOptions()
                .setIcon(id("textures/gui/icon.png"));

        mod.addPage(builder.createOptionPage()
                .setName(Component.translatable("intelium.options.page.general"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("intelium.options.group.core"))
                        .addOption(builder.createBooleanOption(id("enable"))
                                .setName(Component.translatable("intelium.options.enable"))
                                .setTooltip(v -> tooltipWithStatus("intelium.options.enable.tooltip"))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE)
                                .setBinding(v -> { cfg.enabled = v; Intelium.IS_ENABLED = v; },
                                            () -> cfg.enabled)
                                .setApplyHook(state -> { RenderTweaks.apply(); InteliumGame.reloadChunks(); })
                                .setDefaultValue(true)
                        )
                        .addOption(builder.createEnumOption(id("profile"), OptimizationProfile.class)
                                .setName(Component.translatable("intelium.options.profile"))
                                .setTooltip(Component.translatable("intelium.options.profile.tooltip"))
                                .setElementNameProvider(p -> Component.translatable(p.displayKey()))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE)
                                .setBinding(v -> cfg.profile = v.key,
                                            () -> OptimizationProfile.fromKey(cfg.profile))
                                .setApplyHook(state -> InteliumGame.reloadChunks())
                                .setDefaultValue(OptimizationProfile.BALANCED)
                        )
                        .addOption(builder.createIntegerOption(id("chunk_workers"))
                                .setName(Component.translatable("intelium.options.chunk_workers"))
                                .setTooltip(v -> tooltipWithCapability("intelium.options.chunk_workers.tooltip",
                                        Capability.WORKER_TUNING))
                                .setRange(0, 16, 1)
                                .setValueFormatter(value -> value <= 0
                                        ? Component.translatable("intelium.options.chunk_workers.auto")
                                        : Component.literal(Integer.toString(value)))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state ->
                                        Intelium.IS_COMPATIBLE
                                        && com.intelium.Capabilities.available(
                                                com.intelium.Capability.WORKER_TUNING))
                                .setBinding(v -> cfg.chunkBuildWorkers = v,
                                            () -> Math.max(0, cfg.chunkBuildWorkers))
                                .setApplyHook(state -> InteliumGame.reloadChunks())
                                .setDefaultValue(0)
                        )
                )
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("intelium.options.group.render"))
                        .addOption(builder.createBooleanOption(id("tune_frame"))
                                .setName(Component.translatable("intelium.options.tune_frame"))
                                .setTooltip(Component.translatable("intelium.options.tune_frame.tooltip"))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE)
                                .setBinding(v -> cfg.tuneFrameSettings = v, () -> cfg.tuneFrameSettings)
                                .setApplyHook(state -> RenderTweaks.apply())
                                .setDefaultValue(true)
                        )
                        .addOption(builder.createIntegerOption(id("entity_distance"))
                                .setName(Component.translatable("intelium.options.entity_distance"))
                                .setTooltip(Component.translatable("intelium.options.entity_distance.tooltip"))
                                .setRange(50, 100, 5)
                                .setValueFormatter(value -> value >= 100
                                        ? Component.translatable("intelium.options.entity_distance.full")
                                        : Component.literal(value + "%"))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE && cfg.tuneFrameSettings)
                                .setBinding(v -> cfg.maxEntityDistancePercent = v,
                                            () -> clampPercent(cfg.maxEntityDistancePercent))
                                .setApplyHook(state -> RenderTweaks.apply())
                                .setDefaultValue(80)
                        )
                        .addOption(builder.createBooleanOption(id("limit_particles"))
                                .setName(Component.translatable("intelium.options.limit_particles"))
                                .setTooltip(Component.translatable("intelium.options.limit_particles.tooltip"))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE && cfg.tuneFrameSettings)
                                .setBinding(v -> cfg.limitParticles = v, () -> cfg.limitParticles)
                                .setApplyHook(state -> RenderTweaks.apply())
                                .setDefaultValue(true)
                        )
                        .addOption(builder.createBooleanOption(id("disable_shadows"))
                                .setName(Component.translatable("intelium.options.disable_shadows"))
                                .setTooltip(Component.translatable("intelium.options.disable_shadows.tooltip"))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE && cfg.tuneFrameSettings)
                                .setBinding(v -> cfg.disableEntityShadows = v, () -> cfg.disableEntityShadows)
                                .setApplyHook(state -> RenderTweaks.apply())
                                .setDefaultValue(false)
                        )
                        .addOption(builder.createBooleanOption(id("fast_biome_blend"))
                                .setName(Component.translatable("intelium.options.fast_biome_blend"))
                                .setTooltip(Component.translatable("intelium.options.fast_biome_blend.tooltip"))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE && cfg.tuneFrameSettings)
                                .setBinding(v -> cfg.fastBiomeBlend = v, () -> cfg.fastBiomeBlend)
                                .setApplyHook(state -> RenderTweaks.apply())
                                .setDefaultValue(false)
                        )
                )
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("intelium.options.group.gpu"))
                        .addOption(builder.createEnumOption(id("clouds"), CloudsMode.class)
                                .setName(Component.translatable("intelium.options.clouds"))
                                .setTooltip(Component.translatable("intelium.options.clouds.tooltip"))
                                .setElementNameProvider(m -> Component.translatable(m.displayKey()))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE && cfg.tuneFrameSettings)
                                .setBinding(v -> cfg.cloudsMode = v.key,
                                            () -> CloudsMode.fromKey(cfg.cloudsMode))
                                .setApplyHook(state -> RenderTweaks.apply())
                                .setDefaultValue(CloudsMode.DEFAULT)
                        )
                        .addOption(builder.createBooleanOption(id("fast_graphics"))
                                .setName(Component.translatable("intelium.options.fast_graphics"))
                                .setTooltip(Component.translatable("intelium.options.fast_graphics.tooltip"))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE && cfg.tuneFrameSettings)
                                .setBinding(v -> cfg.fastGraphics = v, () -> cfg.fastGraphics)
                                .setApplyHook(state -> RenderTweaks.apply())
                                .setDefaultValue(false)
                        )
                        .addOption(builder.createBooleanOption(id("disable_smooth_lighting"))
                                .setName(Component.translatable("intelium.options.disable_smooth_lighting"))
                                .setTooltip(Component.translatable("intelium.options.disable_smooth_lighting.tooltip"))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE && cfg.tuneFrameSettings)
                                .setBinding(v -> cfg.disableSmoothLighting = v,
                                            () -> cfg.disableSmoothLighting)
                                .setApplyHook(state -> RenderTweaks.apply())
                                .setDefaultValue(false)
                        )
                        .addOption(builder.createBooleanOption(id("disable_vsync"))
                                .setName(Component.translatable("intelium.options.disable_vsync"))
                                .setTooltip(Component.translatable("intelium.options.disable_vsync.tooltip"))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE && cfg.tuneFrameSettings)
                                .setBinding(v -> cfg.disableVsync = v, () -> cfg.disableVsync)
                                .setApplyHook(state -> RenderTweaks.apply())
                                .setDefaultValue(false)
                        )
                        .addOption(builder.createBooleanOption(id("disable_menu_blur"))
                                .setName(Component.translatable("intelium.options.disable_menu_blur"))
                                .setTooltip(Component.translatable("intelium.options.disable_menu_blur.tooltip"))
                                .setStorageHandler(saveHook)
                                // Greys out when this 26.x build no longer
                                // exposes the menu-blur option (fail-soft).
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE
                                        && cfg.tuneFrameSettings
                                        && RenderTweaks.menuBlurAvailable())
                                .setBinding(v -> cfg.disableMenuBlur = v, () -> cfg.disableMenuBlur)
                                .setApplyHook(state -> RenderTweaks.apply())
                                .setDefaultValue(false)
                        )
                        .addOption(builder.createIntegerOption(id("max_render_distance"))
                                .setName(Component.translatable("intelium.options.max_render_distance"))
                                .setTooltip(Component.translatable("intelium.options.max_render_distance.tooltip"))
                                .setRange(0, 32, 1)
                                .setValueFormatter(value -> value <= 0
                                        ? Component.translatable("intelium.options.max_render_distance.off")
                                        : Component.literal(Integer.toString(value)))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE && cfg.tuneFrameSettings)
                                .setBinding(v -> cfg.maxRenderDistance = v,
                                            () -> Math.max(0, cfg.maxRenderDistance))
                                .setApplyHook(state -> RenderTweaks.apply())
                                .setDefaultValue(0)
                        )
                        .addOption(builder.createIntegerOption(id("max_simulation_distance"))
                                .setName(Component.translatable("intelium.options.max_simulation_distance"))
                                .setTooltip(Component.translatable("intelium.options.max_simulation_distance.tooltip"))
                                .setRange(0, 32, 1)
                                .setValueFormatter(value -> value <= 0
                                        ? Component.translatable("intelium.options.max_simulation_distance.off")
                                        : Component.literal(Integer.toString(value)))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE && cfg.tuneFrameSettings)
                                .setBinding(v -> cfg.maxSimulationDistance = v,
                                            () -> Math.max(0, cfg.maxSimulationDistance))
                                .setApplyHook(state -> RenderTweaks.apply())
                                .setDefaultValue(0)
                        )
                )
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("intelium.options.group.adaptive"))
                        .addOption(builder.createBooleanOption(id("adaptive_distance"))
                                .setName(Component.translatable("intelium.options.adaptive_distance"))
                                .setTooltip(Component.translatable("intelium.options.adaptive_distance.tooltip"))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE && cfg.tuneFrameSettings)
                                .setBinding(v -> cfg.adaptiveRenderDistance = v,
                                            () -> cfg.adaptiveRenderDistance)
                                .setApplyHook(state -> RenderTweaks.apply())
                                .setDefaultValue(false)
                        )
                        .addOption(builder.createIntegerOption(id("adaptive_fps_target"))
                                .setName(Component.translatable("intelium.options.adaptive_fps_target"))
                                .setTooltip(Component.translatable("intelium.options.adaptive_fps_target.tooltip"))
                                // Matches InteliumConfig.sanitize's 30-144 range, so a
                                // hand-edited 144 isn't silently rewritten to 120 here.
                                .setRange(30, 144, 6)
                                .setValueFormatter(value -> Component.literal(value + " FPS"))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE
                                        && cfg.tuneFrameSettings && cfg.adaptiveRenderDistance)
                                .setBinding(v -> cfg.adaptiveFpsTarget = v,
                                            () -> Math.max(30, Math.min(144, cfg.adaptiveFpsTarget)))
                                .setDefaultValue(60)
                        )
                        .addOption(builder.createIntegerOption(id("background_fps"))
                                .setName(Component.translatable("intelium.options.background_fps"))
                                .setTooltip(Component.translatable("intelium.options.background_fps.tooltip"))
                                .setRange(0, 60, 10)
                                .setValueFormatter(value -> value <= 0
                                        ? Component.translatable("intelium.options.background_fps.off")
                                        : Component.literal(value + " FPS"))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE && cfg.tuneFrameSettings)
                                .setBinding(v -> cfg.backgroundFpsLimit = v,
                                            () -> Math.max(0, Math.min(60, cfg.backgroundFpsLimit)))
                                .setApplyHook(state -> RenderTweaks.apply())
                                .setDefaultValue(0)
                        )
                        .addOption(builder.createIntegerOption(id("menu_fps"))
                                .setName(Component.translatable("intelium.options.menu_fps"))
                                .setTooltip(Component.translatable("intelium.options.menu_fps.tooltip"))
                                .setRange(0, 60, 10)
                                .setValueFormatter(value -> value <= 0
                                        ? Component.translatable("intelium.options.menu_fps.off")
                                        : Component.literal(value + " FPS"))
                                .setStorageHandler(saveHook)
                                // Greys out when this Minecraft build exposes no
                                // current-screen accessor (see MenuScreenProbe).
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE
                                        && cfg.tuneFrameSettings
                                        && com.intelium.client.MenuScreenProbe.available())
                                .setBinding(v -> cfg.menuFpsLimit = v,
                                            () -> Math.max(0, Math.min(60, cfg.menuFpsLimit)))
                                .setApplyHook(state -> RenderTweaks.apply())
                                .setDefaultValue(0)
                        )
                )
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("intelium.options.group.budget"))
                        .addOption(builder.createBooleanOption(id("render_budget"))
                                .setName(Component.translatable("intelium.options.render_budget"))
                                .setTooltip(Component.translatable("intelium.options.render_budget.tooltip"))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE)
                                .setBinding(v -> cfg.renderBudget = v, () -> cfg.renderBudget)
                                .setApplyHook(state -> RenderBudgetDriver.apply())
                                .setDefaultValue(true)
                        )
                        .addOption(builder.createEnumOption(id("entity_culling"), CullingStrength.class)
                                .setName(Component.translatable("intelium.options.entity_culling"))
                                .setTooltip(tooltipWithCapability("intelium.options.entity_culling.tooltip",
                                        Capability.ENTITY_CULLING))
                                .setElementNameProvider(s -> Component.translatable(s.displayKey()))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE && cfg.renderBudget
                                        && Capabilities.available(Capability.ENTITY_CULLING))
                                .setBinding(v -> cfg.entityCulling = v.key,
                                            () -> CullingStrength.fromKey(cfg.entityCulling))
                                .setApplyHook(state -> RenderBudgetDriver.apply())
                                .setDefaultValue(CullingStrength.BALANCED)
                        )
                        .addOption(builder.createEnumOption(id("block_entity_culling"), CullingStrength.class)
                                .setName(Component.translatable("intelium.options.block_entity_culling"))
                                .setTooltip(tooltipWithCapability("intelium.options.block_entity_culling.tooltip",
                                        Capability.BLOCK_ENTITY_BUDGET))
                                .setElementNameProvider(s -> Component.translatable(s.displayKey()))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE && cfg.renderBudget
                                        && Capabilities.available(Capability.BLOCK_ENTITY_BUDGET))
                                .setBinding(v -> cfg.blockEntityCulling = v.key,
                                            () -> CullingStrength.fromKey(cfg.blockEntityCulling))
                                .setApplyHook(state -> RenderBudgetDriver.apply())
                                .setDefaultValue(CullingStrength.BALANCED)
                        )
                        .addOption(builder.createEnumOption(id("particle_budget"), CullingStrength.class)
                                .setName(Component.translatable("intelium.options.particle_budget"))
                                .setTooltip(tooltipWithCapability("intelium.options.particle_budget.tooltip",
                                        Capability.PARTICLE_LIMITER))
                                .setElementNameProvider(s -> Component.translatable(s.displayKey()))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE && cfg.renderBudget
                                        && Capabilities.available(Capability.PARTICLE_LIMITER))
                                .setBinding(v -> cfg.particleBudget = v.key,
                                            () -> CullingStrength.fromKey(cfg.particleBudget))
                                .setApplyHook(state -> RenderBudgetDriver.apply())
                                .setDefaultValue(CullingStrength.BALANCED)
                        )
                        .addOption(builder.createBooleanOption(id("adaptive_budgets"))
                                .setName(Component.translatable("intelium.options.adaptive_budgets"))
                                .setTooltip(Component.translatable("intelium.options.adaptive_budgets.tooltip"))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE && cfg.renderBudget)
                                .setBinding(v -> cfg.adaptiveCulling = v, () -> cfg.adaptiveCulling)
                                .setApplyHook(state -> RenderBudgetDriver.apply())
                                .setDefaultValue(true)
                        )
                )
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("intelium.options.group.chunks"))
                        .addOption(builder.createEnumOption(id("fast_chunks"), ChunkLoadingMode.class)
                                .setName(Component.translatable("intelium.options.fast_chunks"))
                                .setTooltip(tooltipWithCapability("intelium.options.fast_chunks.tooltip",
                                        Capability.DEFER_TUNING))
                                .setElementNameProvider(m -> Component.translatable(m.displayKey()))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE
                                        && Capabilities.available(Capability.DEFER_TUNING))
                                .setBinding(v -> cfg.chunkLoadingMode = v.key,
                                            () -> ChunkLoadingMode.fromKey(cfg.chunkLoadingMode))
                                .setApplyHook(state -> { ChunkLoadingBooster.apply(); InteliumGame.reloadChunks(); })
                                .setDefaultValue(ChunkLoadingMode.FAST)
                        )
                )
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("intelium.options.group.diagnostics"))
                        // An action button rather than a keybind: no default key
                        // to collide with a third-party client's bindings, and no
                        // extra Fabric API module required at runtime. The screen
                        // is deliberately left as it is - this exports, it does
                        // not navigate.
                        .addOption(builder.createExternalButtonOption(id("frame_report"))
                                .setName(Component.translatable("intelium.options.frame_report"))
                                .setTooltip(Component.translatable(
                                        "intelium.options.frame_report.tooltip"))
                                .setEnabledProvider(state -> Capabilities.available(
                                        Capability.FRAME_BOUNDARY))
                                .setScreenConsumer(parent -> FrameReportExporter.exportAndTell(
                                        net.minecraft.client.Minecraft.getInstance()))
                        )
                )
        );
    }

    private static int clampPercent(int pct) {
        return Math.max(50, Math.min(100, pct));
    }
}
