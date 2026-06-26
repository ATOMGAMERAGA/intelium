package com.intelium;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Intelium implements ClientModInitializer {
    public static final String MOD_ID = "intelium";
    public static final Logger LOGGER = LoggerFactory.getLogger("Intelium");

    /** Sodium major.minor series Intelium is built and tested against. */
    static final String SUPPORTED_SODIUM_SERIES = "0.8.";

    public static volatile boolean IS_ENABLED = true;

    public static volatile boolean IS_COMPATIBLE = false;

    /**
     * True once Sodium is confirmed present and on a supported version. GPU
     * detection only runs (and may flip {@link #IS_COMPATIBLE} on) when this is
     * true, so a Sodium-missing / unsupported-Sodium decision is never clobbered.
     */
    public static volatile boolean SODIUM_OK = false;

    public static volatile IntelGpuGeneration DETECTED_GENERATION = IntelGpuGeneration.UNKNOWN;

    /** Raw GL_RENDERER string captured at detection time, for status display. */
    public static volatile String DETECTED_RENDERER = "";

    public static volatile String DISABLED_REASON_KEY = null;

    @Override
    public void onInitializeClient() {
        String version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("?");

        if (!FabricLoader.getInstance().isModLoaded("sodium")) {
            IS_COMPATIBLE = false;
            DISABLED_REASON_KEY = "intelium.disabled.sodium_missing";
            LOGGER.error("Intelium {}: Sodium not present. Intelium will stay disabled.", version);
            return;
        }

        if (!isSupportedSodium()) {
            IS_COMPATIBLE = false;
            DISABLED_REASON_KEY = "intelium.disabled.incompatible_sodium";
            LOGGER.error("Intelium {}: unsupported Sodium build '{}' (need {}x). "
                            + "Intelium disabled cleanly; Sodium runs unmodified.",
                    version, sodiumVersion(), SUPPORTED_SODIUM_SERIES);
            return;
        }

        SODIUM_OK = true;
        LOGGER.info("Intelium {}: sodium {} OK. GPU detection deferred until the "
                + "GL context is ready.", version, sodiumVersion());
    }

    private static boolean isSupportedSodium() {
        String v = sodiumVersion();
        return v != null && v.startsWith(SUPPORTED_SODIUM_SERIES);
    }

    private static String sodiumVersion() {
        return FabricLoader.getInstance().getModContainer("sodium")
                .map(ModContainer::getMetadata)
                .map(m -> m.getVersion().getFriendlyString())
                .orElse(null);
    }
}
