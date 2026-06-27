package com.intelium.config;

import com.intelium.Intelium;
import com.intelium.client.ChunkLoadingBooster;
import com.intelium.client.InteliumGame;
import com.intelium.client.RenderTweaks;
import com.intelium.client.hud.OverlayEditScreen;
import com.intelium.gui.SupportedGpusScreen;
import com.intelium.optimization.ChunkLoadingMode;
import com.intelium.optimization.OptimizationProfile;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Registers Intelium's page inside Sodium's video settings.
 *
 * <p>The config structure is built once at startup (before GPU detection runs),
 * so anything that depends on the detected GPU uses Sodium's dynamic providers -
 * {@code setEnabledProvider} and a tooltip function - which are evaluated when
 * the screen is opened. When the GPU is unsupported every interactive option is
 * greyed out; the "Supported GPUs" button stays enabled so the user can always
 * see the compatibility list and why their GPU was rejected.
 */
public class InteliumConfigEntryPoint implements ConfigEntryPoint {

    private final StorageEventHandler saveHook = InteliumConfigIO::flush;

    private static Identifier id(String path) {
        return Identifier.of("intelium", path);
    }

    /** Live status line shown as the tooltip on the interactive options. */
    private static Text statusTooltip() {
        if (Intelium.IS_COMPATIBLE) {
            return Text.translatable("intelium.status.active", Intelium.DETECTED_GENERATION.display);
        }
        Text reason = Intelium.DISABLED_REASON_KEY == null
                ? Text.translatable("intelium.status.pending")
                : Text.translatable(Intelium.DISABLED_REASON_KEY);
        String renderer = Intelium.DETECTED_RENDERER;
        Text gpu = (renderer == null || renderer.isEmpty())
                ? Text.translatable("intelium.gpus.detected.pending")
                : Text.literal(renderer);
        return Text.translatable("intelium.status.unsupported", gpu, reason);
    }

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        try {
            buildPage(builder);
        } catch (Throwable t) {
            // A future Sodium could change the config API. Fail soft: skip our
            // page rather than crash Sodium's settings screen.
            Intelium.LOGGER.warn("Intelium: could not register its Sodium config page on this "
                    + "Sodium build; page omitted. Optimizations still work.", t);
        }
    }

    private void buildPage(ConfigBuilder builder) {
        InteliumConfig cfg = InteliumConfigIO.get();

        ModOptionsBuilder mod = builder.registerOwnModOptions()
                .setIcon(id("textures/gui/icon.png"));

        // ---- Page 1: General + Render tweaks --------------------------------
        mod.addPage(builder.createOptionPage()
                .setName(Text.translatable("intelium.options.page.general"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Text.translatable("intelium.options.group.core"))
                        .addOption(builder.createBooleanOption(id("enable"))
                                .setName(Text.translatable("intelium.options.enable"))
                                .setTooltip(v -> statusTooltip())
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE)
                                .setBinding(v -> { cfg.enabled = v; Intelium.IS_ENABLED = v; },
                                            () -> cfg.enabled)
                                // Apply live: reconcile render tweaks (restored
                                // when off) and rebuild chunks for the new count.
                                .setApplyHook(state -> { RenderTweaks.apply(); InteliumGame.reloadChunks(); })
                                .setDefaultValue(true)
                        )
                        .addOption(builder.createEnumOption(id("profile"), OptimizationProfile.class)
                                .setName(Text.translatable("intelium.options.profile"))
                                .setTooltip(Text.translatable("intelium.options.profile.tooltip"))
                                .setElementNameProvider(p -> Text.translatable(p.displayKey()))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE)
                                .setBinding(v -> cfg.profile = v.key,
                                            () -> OptimizationProfile.fromKey(cfg.profile))
                                .setApplyHook(state -> InteliumGame.reloadChunks())
                                .setDefaultValue(OptimizationProfile.BALANCED)
                        )
                        .addOption(builder.createIntegerOption(id("chunk_workers"))
                                .setName(Text.translatable("intelium.options.chunk_workers"))
                                .setTooltip(v -> statusTooltip())
                                .setRange(0, 16, 1)
                                .setValueFormatter(value -> value <= 0
                                        ? Text.translatable("intelium.options.chunk_workers.auto")
                                        : Text.literal(Integer.toString(value)))
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
                        .setName(Text.translatable("intelium.options.group.render"))
                        .addOption(builder.createBooleanOption(id("tune_frame"))
                                .setName(Text.translatable("intelium.options.tune_frame"))
                                .setTooltip(Text.translatable("intelium.options.tune_frame.tooltip"))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE)
                                .setBinding(v -> cfg.tuneFrameSettings = v, () -> cfg.tuneFrameSettings)
                                .setApplyHook(state -> RenderTweaks.apply())
                                .setDefaultValue(true)
                        )
                        .addOption(builder.createIntegerOption(id("entity_distance"))
                                .setName(Text.translatable("intelium.options.entity_distance"))
                                .setTooltip(Text.translatable("intelium.options.entity_distance.tooltip"))
                                .setRange(50, 100, 5)
                                .setValueFormatter(value -> value >= 100
                                        ? Text.translatable("intelium.options.entity_distance.full")
                                        : Text.literal(value + "%"))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE && cfg.tuneFrameSettings)
                                .setBinding(v -> cfg.maxEntityDistancePercent = v,
                                            () -> clampPercent(cfg.maxEntityDistancePercent))
                                .setApplyHook(state -> RenderTweaks.apply())
                                .setDefaultValue(80)
                        )
                        .addOption(builder.createBooleanOption(id("limit_particles"))
                                .setName(Text.translatable("intelium.options.limit_particles"))
                                .setTooltip(Text.translatable("intelium.options.limit_particles.tooltip"))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE && cfg.tuneFrameSettings)
                                .setBinding(v -> cfg.limitParticles = v, () -> cfg.limitParticles)
                                .setApplyHook(state -> RenderTweaks.apply())
                                .setDefaultValue(true)
                        )
                        .addOption(builder.createBooleanOption(id("disable_shadows"))
                                .setName(Text.translatable("intelium.options.disable_shadows"))
                                .setTooltip(Text.translatable("intelium.options.disable_shadows.tooltip"))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE && cfg.tuneFrameSettings)
                                .setBinding(v -> cfg.disableEntityShadows = v, () -> cfg.disableEntityShadows)
                                .setApplyHook(state -> RenderTweaks.apply())
                                .setDefaultValue(false)
                        )
                        .addOption(builder.createBooleanOption(id("fast_biome_blend"))
                                .setName(Text.translatable("intelium.options.fast_biome_blend"))
                                .setTooltip(Text.translatable("intelium.options.fast_biome_blend.tooltip"))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE && cfg.tuneFrameSettings)
                                .setBinding(v -> cfg.fastBiomeBlend = v, () -> cfg.fastBiomeBlend)
                                .setApplyHook(state -> RenderTweaks.apply())
                                .setDefaultValue(false)
                        )
                )
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Text.translatable("intelium.options.group.chunks"))
                        .addOption(builder.createEnumOption(id("fast_chunks"), ChunkLoadingMode.class)
                                .setName(Text.translatable("intelium.options.fast_chunks"))
                                .setTooltip(Text.translatable("intelium.options.fast_chunks.tooltip"))
                                .setElementNameProvider(m -> Text.translatable(m.displayKey()))
                                .setStorageHandler(saveHook)
                                .setEnabledProvider(state -> Intelium.IS_COMPATIBLE)
                                .setBinding(v -> cfg.chunkLoadingMode = v.key,
                                            () -> ChunkLoadingMode.fromKey(cfg.chunkLoadingMode))
                                // Defer mode is read live by Sodium; reload so the
                                // change is visible immediately.
                                .setApplyHook(state -> { ChunkLoadingBooster.apply(); InteliumGame.reloadChunks(); })
                                .setDefaultValue(ChunkLoadingMode.FAST)
                        )
                )
        );

        // ---- Page 2: FPS overlay & benchmark --------------------------------
        mod.addPage(builder.createOptionPage()
                .setName(Text.translatable("intelium.options.page.overlay"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Text.translatable("intelium.options.group.overlay"))
                        .addOption(builder.createBooleanOption(id("overlay"))
                                .setName(Text.translatable("intelium.options.overlay"))
                                .setTooltip(Text.translatable("intelium.options.overlay.tooltip"))
                                .setStorageHandler(saveHook)
                                .setBinding(v -> cfg.overlayEnabled = v, () -> cfg.overlayEnabled)
                                .setDefaultValue(false)
                        )
                        .addOption(builder.createBooleanOption(id("overlay_compact"))
                                .setName(Text.translatable("intelium.options.overlay_compact"))
                                .setTooltip(Text.translatable("intelium.options.overlay_compact.tooltip"))
                                .setStorageHandler(saveHook)
                                .setBinding(v -> cfg.overlayCompact = v, () -> cfg.overlayCompact)
                                .setDefaultValue(false)
                        )
                        .addOption(builder.createBooleanOption(id("overlay_lows"))
                                .setName(Text.translatable("intelium.options.overlay_lows"))
                                .setTooltip(Text.translatable("intelium.options.overlay_lows.tooltip"))
                                .setStorageHandler(saveHook)
                                .setBinding(v -> cfg.overlayShowLows = v, () -> cfg.overlayShowLows)
                                .setDefaultValue(true)
                        )
                        .addOption(builder.createExternalButtonOption(id("overlay_edit"))
                                .setName(Text.translatable("intelium.options.overlay_edit"))
                                .setTooltip(Text.translatable("intelium.options.overlay_edit.tooltip"))
                                .setScreenConsumer(parent ->
                                        MinecraftClient.getInstance()
                                                .setScreen(new OverlayEditScreen(parent)))
                        )
                        .addOption(builder.createExternalButtonOption(id("supported_gpus"))
                                .setName(Text.translatable("intelium.options.supported_gpus"))
                                .setTooltip(Text.translatable("intelium.options.supported_gpus.tooltip"))
                                .setScreenConsumer(parent ->
                                        MinecraftClient.getInstance()
                                                .setScreen(new SupportedGpusScreen(parent)))
                        )
                )
        );
    }

    private static int clampPercent(int pct) {
        return Math.max(50, Math.min(100, pct));
    }
}
