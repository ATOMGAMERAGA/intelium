package com.intelium.config;

import com.intelium.Intelium;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class InteliumConfigEntryPoint implements ConfigEntryPoint {

    private final StorageEventHandler saveHook = InteliumConfigIO::flush;

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        InteliumConfig cfg = InteliumConfigIO.get();

        builder.registerOwnModOptions()
                .setIcon(Identifier.of("intelium", "textures/gui/icon.png"))
                .addPage(builder.createOptionPage()
                        .setName(Text.translatable("intelium.options.page.general"))
                        .addOptionGroup(builder.createOptionGroup()
                                .addOption(builder.createBooleanOption(Identifier.of("intelium", "enable"))
                                        .setName(Text.translatable("intelium.options.enable"))
                                        .setTooltip(Text.translatable("intelium.options.enable.tooltip"))
                                        .setStorageHandler(saveHook)
                                        .setBinding(v -> { cfg.enabled = v; Intelium.IS_ENABLED = v; },
                                                    () -> cfg.enabled)
                                        .setDefaultValue(true)
                                )
                                .addOption(builder.createBooleanOption(Identifier.of("intelium", "draw_call_batching"))
                                        .setName(Text.translatable("intelium.options.draw_call_batching"))
                                        .setTooltip(Text.translatable("intelium.options.draw_call_batching.tooltip"))
                                        .setStorageHandler(saveHook)
                                        .setBinding(v -> cfg.drawCallBatching = v, () -> cfg.drawCallBatching)
                                        .setDefaultValue(true)
                                )
                                .addOption(builder.createBooleanOption(Identifier.of("intelium", "persistent_buffers"))
                                        .setName(Text.translatable("intelium.options.persistent_buffers"))
                                        .setTooltip(Text.translatable("intelium.options.persistent_buffers.tooltip"))
                                        .setStorageHandler(saveHook)
                                        .setBinding(v -> cfg.persistentBuffers = v, () -> cfg.persistentBuffers)
                                        .setDefaultValue(true)
                                )
                                .addOption(builder.createBooleanOption(Identifier.of("intelium", "aggressive_culling"))
                                        .setName(Text.translatable("intelium.options.aggressive_culling"))
                                        .setTooltip(Text.translatable("intelium.options.aggressive_culling.tooltip"))
                                        .setStorageHandler(saveHook)
                                        .setBinding(v -> cfg.aggressiveCulling = v, () -> cfg.aggressiveCulling)
                                        .setDefaultValue(true)
                                )
                        )
                );
    }
}
