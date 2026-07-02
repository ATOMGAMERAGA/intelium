package com.intelium.client;

import com.intelium.IntelGpuDetector;
import com.intelium.Intelium;
import com.intelium.client.hud.InteliumOverlay;
import com.intelium.compat.ModCompat;
import com.intelium.config.InteliumConfigIO;
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
        // Honour the persisted master switch from the very first frame. Without
        // this, a user who disabled Intelium got it silently re-enabled on the
        // next launch (IS_ENABLED was only written by the settings screen).
        Intelium.IS_ENABLED = InteliumConfigIO.get().enabled;

        // Report any companion performance mods (AsyncParticles, GPUTape) once,
        // so logs make the compatibility behaviour visible.
        ModCompat.logOnce();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            IntelGpuDetector.detectOnce();
            // Keep the live render tweaks reconciled with the config. Cheap: it
            // only writes a game option when the value actually differs.
            RenderTweaks.apply();
            // Keep Sodium's defer mode in sync with the fast-chunk-loading mode.
            ChunkLoadingBooster.apply();
            int fps = client.getCurrentFps();
            InteliumOverlay.TRACKER.push(fps);
            AbBenchmark.INSTANCE.tick(System.currentTimeMillis(), fps);
        });

        HudRenderCallback.EVENT.register((context, tickCounter) ->
                InteliumOverlay.renderHud(context));
    }
}
