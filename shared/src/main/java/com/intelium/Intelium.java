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

    /**
     * True once Sodium is confirmed present. GPU detection only runs (and may
     * flip {@link #IS_COMPATIBLE} on) when this is true, so a Sodium-missing
     * decision is never clobbered.
     */
    public static volatile boolean SODIUM_OK = false;

    /**
     * Whether the chunk-worker tuning hook could be applied to the running
     * Sodium build (set by {@link com.intelium.mixin.InteliumMixinPlugin}). When
     * false, the feature self-disabled because this Sodium version's internals
     * differ - everything else still works.
     */
    public static volatile boolean WORKER_TUNING_AVAILABLE = false;

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

        // Intelium supports any Sodium that satisfies the fabric.mod.json
        // dependency range. Internal differences between Sodium versions are
        // handled gracefully by InteliumMixinPlugin (hooks self-disable, never
        // crash), so there is no hard version gate here.
        SODIUM_OK = true;
        LOGGER.info("Intelium {}: sodium {} detected. GPU detection deferred until the "
                + "GL context is ready.", version, sodiumVersion());
    }

    private static String sodiumVersion() {
        return FabricLoader.getInstance().getModContainer("sodium")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
