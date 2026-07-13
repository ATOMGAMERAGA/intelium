package com.intelium.client;

import com.intelium.IntelGpuDetector;
import com.intelium.Intelium;
import com.intelium.compat.ModCompat;
import com.intelium.config.InteliumConfigIO;
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
        // Honour the persisted master switch from the very first frame. Without
        // this, a user who disabled Intelium got it silently re-enabled on the
        // next launch (IS_ENABLED was only written by the settings screen).
        Intelium.IS_ENABLED = InteliumConfigIO.get().enabled;

        // Report any companion performance mods (AsyncParticles, GPUTape) once.
        ModCompat.logOnce();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            IntelGpuDetector.detectOnce();
            // Feed the adaptive render-distance controller first so the cap it
            // publishes is applied by RenderTweaks in the same tick.
            AdaptiveDistance.tick(client);
            // Keep the live render tweaks reconciled with the config.
            RenderTweaks.apply();
            // Keep Sodium's defer mode in sync with the fast-chunk-loading mode.
            ChunkLoadingBooster.apply();
        });
    }
}
