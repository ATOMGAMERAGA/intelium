package com.intelium;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Intelium implements ClientModInitializer {
    public static final String MOD_ID = "intelium";
    public static final Logger LOGGER = LoggerFactory.getLogger("Intelium");

    public static volatile boolean IS_ENABLED = true;

    public static volatile boolean IS_COMPATIBLE = false;

    public static volatile IntelGpuGeneration DETECTED_GENERATION = IntelGpuGeneration.UNKNOWN;

    public static volatile String DISABLED_REASON_KEY = null;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Intelium loading - GPU detection deferred until OpenGL context is ready.");

        if (!FabricLoader.getInstance().isModLoaded("sodium")) {
            IS_COMPATIBLE = false;
            DISABLED_REASON_KEY = "intelium.disabled.sodium_missing";
            LOGGER.error("Sodium not present. Intelium will stay disabled.");
            return;
        }
    }
}
