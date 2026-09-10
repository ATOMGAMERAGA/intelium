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

        // The menu probe resolves its accessor in a static initialiser, so its
        // capability is knowable before the first tick.
        com.intelium.Capabilities.set(com.intelium.Capability.MENU_DETECTION,
                MenuScreenProbe.available(),
                MenuScreenProbe.available() ? null
                        : "no current-screen accessor found on this Minecraft build");

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
    }

    /** Latched after the first tick-handler failure; logs exactly once. */
    private static volatile boolean tickFailed;

    /**
     * Identity of the level the previous tick saw, so a world change can
     * restart frame-time warm-up. Compared by reference: a new level object is
     * a new world, and terrain streaming into it must not be measured as if it
     * were steady-state rendering.
     */
    private static Object lastLevel;

    private static void tick(net.minecraft.client.Minecraft client) {
        IntelGpuDetector.detectOnce();
        // One environment report, once detection has settled.
        InteliumDiagnostics.logOnce();
        if (client.level != lastLevel) {
            lastLevel = client.level;
            FrameTimeSampler.onWorldChanged();
        }
        // Decide here, at 20 Hz, whether the frames being rendered count - so
        // the per-frame hook itself reads nothing but a volatile boolean.
        FrameTimeSampler.tick(client);
        // Feed the adaptive render-distance controller first so the cap it
        // publishes is applied by RenderTweaks in the same tick.
        AdaptiveDistance.tick(client);
        // Recompute the render budgets from the config, the camera and the
        // current FPS pressure, so this tick's frames read fresh numbers.
        RenderBudgetDriver.tick(client);
        // Keep the live render tweaks reconciled with the config.
        RenderTweaks.apply();
        // Keep Sodium's defer mode in sync, and let the OpenGL-backed 26.1
        // line protect frame pacing under sustained chunk-upload pressure.
        boolean worldLoaded = client.level != null;
        ChunkLoadingBooster.tick(client.getFps(), client.isWindowActive()
                && worldLoaded && !RenderTweaks.fpsDeliberatelyThrottled(client),
                worldLoaded);
    }
}
