package com.intelium.client;

import com.intelium.IntelGpuDetector;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Client-side initializer. Triggers GPU detection at the first client tick that
 * runs on the render thread - independent of world load - so Intelium's status
 * is correct on the main menu. {@link IntelGpuDetector#detectOnce()} is
 * idempotent; the Sodium world-renderer mixin is a fallback for the same call.
 *
 * <p>This lives in the client source set because {@code ClientTickEvents} is a
 * client-only Fabric API surface, unavailable to the common (main) source set.
 */
public class InteliumClientInit implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> IntelGpuDetector.detectOnce());
    }
}
