package com.intelium.config;

import com.intelium.Intelium;
import com.intelium.client.ChunkLoadingBooster;
import com.intelium.client.InteliumGame;
import com.intelium.client.RenderTweaks;
import com.intelium.optimization.ChunkLoadingMode;
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

    /** Live status line shown as the tooltip on the interactive options. */
    private static Component statusTooltip() {
        if (Intelium.IS_COMPATIBLE) {
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
                                .setTooltip(v -> statusTooltip())
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
                                .setTooltip(v -> statusTooltip())
                                .setRange(0, 16, 1)
                                .setValueFormatter(value -> value <= 0
                                        ? Component.translatable("intelium.options.chunk_workers.auto")
                                        : Component.literal(Integer.toString(value)))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state ->
                                        Intelium.IS_COMPATIBLE && Intelium.WORKER_TUNING_AVAILABLE)
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
                        .setName(Component.translatable("intelium.options.group.chunks"))
                        .addOption(builder.createEnumOption(id("fast_chunks"), ChunkLoadingMode.class)
                                .setName(Component.translatable("intelium.options.fast_chunks"))
                                .setTooltip(Component.translatable("intelium.options.fast_chunks.tooltip"))
                                .setElementNameProvider(m -> Component.translatable(m.displayKey()))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE)
                                .setBinding(v -> cfg.chunkLoadingMode = v.key,
                                            () -> ChunkLoadingMode.fromKey(cfg.chunkLoadingMode))
                                .setApplyHook(state -> { ChunkLoadingBooster.apply(); InteliumGame.reloadChunks(); })
                                .setDefaultValue(ChunkLoadingMode.FAST)
                        )
                )
        );
    }

    private static int clampPercent(int pct) {
        return Math.max(50, Math.min(100, pct));
    }
}
