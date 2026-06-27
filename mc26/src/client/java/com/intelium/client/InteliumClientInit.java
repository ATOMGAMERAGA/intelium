package com.intelium.client;

import com.intelium.IntelGpuDetector;
import com.intelium.compat.ModCompat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Client-side initializer (26.x). Runs GPU detection at the first render-thread
 * tick and keeps the live optimizations reconciled with the config each tick.
 *
 * <p>Note: the movable FPS test overlay and the custom in-game screens from the
 * 1.21.11 build are not present on 26.x yet - Minecraft 26.x replaced immediate-
 * mode GUI rendering ({@code GuiGraphics}) with a retained-mode system for the
 * Vulkan renderer. The core FPS optimizations and the Sodium settings page are
 * fully functional; the overlay/benchmark UI will return once the new 26.x GUI
 * API is implemented.
 */
public class InteliumClientInit implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Report any companion performance mods (AsyncParticles, GPUTape) once.
        ModCompat.logOnce();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            IntelGpuDetector.detectOnce();
            // Keep the live render tweaks reconciled with the config.
            RenderTweaks.apply();
            // Keep Sodium's defer mode in sync with the fast-chunk-loading mode.
            ChunkLoadingBooster.apply();
        });
    }
}
