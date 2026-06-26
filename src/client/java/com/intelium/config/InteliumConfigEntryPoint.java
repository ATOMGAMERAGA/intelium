package com.intelium.config;

import com.intelium.Intelium;
import com.intelium.gui.SupportedGpusScreen;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
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
        InteliumConfig cfg = InteliumConfigIO.get();

        builder.registerOwnModOptions()
                .setIcon(id("textures/gui/icon.png"))
                .addPage(builder.createOptionPage()
                        .setName(Text.translatable("intelium.options.page.general"))
                        .addOptionGroup(builder.createOptionGroup()
                                .addOption(builder.createBooleanOption(id("enable"))
                                        .setName(Text.translatable("intelium.options.enable"))
                                        .setTooltip(v -> statusTooltip())
                                        .setStorageHandler(saveHook)
                                        .setEnabledProvider(state -> Intelium.IS_COMPATIBLE)
                                        .setBinding(v -> { cfg.enabled = v; Intelium.IS_ENABLED = v; },
                                                    () -> cfg.enabled)
                                        .setDefaultValue(true)
                                )
                                .addOption(builder.createIntegerOption(id("chunk_workers"))
                                        .setName(Text.translatable("intelium.options.chunk_workers"))
                                        .setTooltip(v -> statusTooltip())
                                        .setRange(0, 16, 1)
                                        .setValueFormatter(value -> value <= 0
                                                ? Text.translatable("intelium.options.chunk_workers.auto")
                                                : Text.literal(Integer.toString(value)))
                                        .setStorageHandler(saveHook)
                                        .setEnabledProvider(state -> Intelium.IS_COMPATIBLE)
                                        .setBinding(v -> cfg.chunkBuildWorkers = v,
                                                    () -> Math.max(0, cfg.chunkBuildWorkers))
                                        .setDefaultValue(0)
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
}
