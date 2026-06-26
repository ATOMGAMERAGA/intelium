package com.intelium.client;

import com.intelium.IntelGpuDetector;
import com.intelium.client.hud.InteliumOverlay;
import com.intelium.hud.AbBenchmark;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * Client-side initializer. Runs GPU detection at the first render-thread tick
 * (independent of world load), feeds the FPS tracker / A/B benchmark each tick,
 * and registers the movable FPS test overlay.
 *
 * <p>Lives in the client source set because the FPS overlay, HUD callback and
 * client tick events are client-only Fabric/Minecraft surfaces.
 */
public class InteliumClientInit implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            IntelGpuDetector.detectOnce();
            int fps = client.getCurrentFps();
            InteliumOverlay.TRACKER.push(fps);
            AbBenchmark.INSTANCE.tick(System.currentTimeMillis(), fps);
        });

        HudRenderCallback.EVENT.register((context, tickCounter) ->
                InteliumOverlay.renderHud(context));
    }
}
