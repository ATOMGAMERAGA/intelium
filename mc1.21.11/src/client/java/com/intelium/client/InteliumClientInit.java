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
            // Fail soft: one escaped exception from a tick handler crashes the
            // whole game, which is exactly the failure mode every Intelium
            // feature promises to avoid. Log once and stand down instead.
            try {
                tick(client);
            } catch (Throwable t) {
                if (!tickFailed) {
                    tickFailed = true;
                    Intelium.LOGGER.error(
                            "Intelium: client tick handler failed - disabling Intelium "
                                    + "for this session instead of crashing", t);
                    Intelium.IS_ENABLED = false;
                }
            }
        });

        HudRenderCallback.EVENT.register((context, tickCounter) ->
                InteliumOverlay.renderHud(context));
    }

    /** Latched after the first tick-handler failure; logs exactly once. */
    private static volatile boolean tickFailed;

    private static void tick(net.minecraft.client.MinecraftClient client) {
        IntelGpuDetector.detectOnce();
        // A world change ends any A/B run: its OFF half would otherwise measure
        // the title screen and present the result as a real comparison.
        if (AbBenchmark.INSTANCE.isRunning() && client.world == null) {
            AbBenchmark.INSTANCE.cancel(System.currentTimeMillis());
        }
        // Feed the adaptive render-distance controller first so the cap it
        // publishes is applied by RenderTweaks in the same tick.
        AdaptiveDistance.tick(client);
        // Recompute the render budgets from the config, the camera and the
        // current FPS pressure, so this tick's frames read fresh numbers.
        RenderBudgetDriver.tick(client);
        // Keep the live render tweaks reconciled with the config. Cheap: it
        // only writes a game option when the value actually differs.
        RenderTweaks.apply();
        int fps = client.getCurrentFps();
        // Deliberately throttled frames (background/menu FPS caps) are not
        // stutter: feeding them to the overlay painted a red "1% low" for ten
        // seconds after every alt-tab, and feeding them to the benchmark let a
        // mid-run alt-tab poison the comparison.
        boolean measurable = !RenderTweaks.fpsDeliberatelyThrottled(client);
        // Keep Sodium's defer mode in sync, and let OpenGL Fast temporarily
        // protect frame pacing when sustained FPS pressure says uploads are
        // amplifying a struggling render path.
        boolean worldLoaded = client.world != null;
        ChunkLoadingBooster.tick(fps, measurable && client.isWindowFocused()
                && worldLoaded && !AbBenchmark.INSTANCE.isRunning(), worldLoaded);
        if (measurable) {
            InteliumOverlay.TRACKER.push(fps);
        }
        AbBenchmark.INSTANCE.tick(System.currentTimeMillis(), fps, measurable);
    }
}
