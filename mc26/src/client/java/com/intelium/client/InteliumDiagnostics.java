package com.intelium.client;

import com.intelium.Capabilities;
import com.intelium.Capability;
import com.intelium.Intelium;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * The one thing Intelium prints about itself, printed once.
 *
 * <h2>Why it exists</h2>
 *
 * <p>Every bug report about a performance mod is really the same question:
 * <em>what was actually running?</em> Which Minecraft, which Sodium, which
 * renderer, which GPU, and which of the mod's features attached at all. On a
 * third-party client that ships its own modified Sodium - Lunar Client being
 * the case that prompted this - the answer is frequently not what either side
 * assumed, and without it a report is unactionable.
 *
 * <p>So this emits one block, after detection has settled, listing exactly
 * that. It reads only what the loader and Intelium's own state already know:
 * there is no probing of a third-party client's internals, no guessing at a mod
 * id that may not exist, and nothing here changes behaviour.
 *
 * <p>It runs once. A line repeated every tick is not diagnostics, it is a log
 * flood that hides the real failure.
 */
public final class InteliumDiagnostics {

    private InteliumDiagnostics() {}

    private static volatile boolean printed;

    /**
     * Prints the report the first time it is called with detection complete.
     * Safe to call every tick; does nothing after the first success.
     */
    public static void logOnce() {
        if (printed) return;
        if (!Capabilities.available(Capability.GPU_DETECTION)
                && Intelium.DETECTED_BACKEND == com.intelium.RenderBackend.UNKNOWN
                && Intelium.DISABLED_REASON_KEY == null) {
            // Detection has not finished and has not given up. Wait.
            return;
        }
        printed = true;

        Intelium.LOGGER.info("Intelium {} environment report", version("intelium"));
        Intelium.LOGGER.info("  minecraft={} fabric-loader={} fabric-api={}",
                version("minecraft"), loaderVersion(), version("fabric-api"));
        Intelium.LOGGER.info("  sodium={} iris={}", version("sodium"), irisStatus());
        Intelium.LOGGER.info("  backend={} gpu='{}' generation={} active={}",
                Intelium.DETECTED_BACKEND.displayName,
                Intelium.DETECTED_RENDERER.isEmpty() ? "unknown" : Intelium.DETECTED_RENDERER,
                Intelium.DETECTED_GENERATION.display,
                Intelium.IS_COMPATIBLE);
        Intelium.LOGGER.info("  capabilities: {}", Capabilities.describe());
        for (Capability capability : Capability.values()) {
            String reason = Capabilities.reason(capability);
            if (reason != null) {
                Intelium.LOGGER.info("    {} is off: {}",
                        capability.name().toLowerCase(java.util.Locale.ROOT), reason);
            }
        }
        if (Intelium.DISABLED_REASON_KEY != null) {
            Intelium.LOGGER.info("  inactive because: {}", Intelium.DISABLED_REASON_KEY);
        }
    }

    /**
     * A mod's version from the loader, or {@code "absent"}.
     *
     * <p>Only ids that are genuinely standard are ever asked for. Inventing an
     * id for a third-party client and reporting on whether it "was found" would
     * produce confident-looking nonsense.
     */
    private static String version(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(InteliumDiagnostics::friendly)
                .orElse("absent");
    }

    private static String friendly(ModContainer container) {
        return container.getMetadata().getVersion().getFriendlyString();
    }

    private static String loaderVersion() {
        try {
            return FabricLoader.getInstance().getModContainer("fabricloader")
                    .map(InteliumDiagnostics::friendly)
                    .orElse("unknown");
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /**
     * Iris's presence and version. Reported because a shader pipeline changes
     * what every other number here means - and because Intelium must not crash
     * with it loaded, which is worth being able to confirm from a log.
     */
    private static String irisStatus() {
        return FabricLoader.getInstance().getModContainer("iris")
                .map(c -> "present " + friendly(c))
                .orElse("absent");
    }

    /** Test/reload support: allows the report to be emitted again. */
    static void resetForTesting() {
        printed = false;
    }
}
