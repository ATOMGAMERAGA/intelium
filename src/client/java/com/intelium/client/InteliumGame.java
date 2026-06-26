package com.intelium.client;

import com.intelium.Intelium;
import net.minecraft.client.MinecraftClient;

/** Small client-side helpers for applying changes to the running game. */
public final class InteliumGame {

    private InteliumGame() {}

    /**
     * Asks the game to rebuild its chunk renderer. This is how a chunk-worker
     * count change (or the A/B benchmark toggle) takes effect live: Sodium reads
     * the worker count when it recreates the chunk builder during a reload.
     */
    public static void reloadChunks() {
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            if (mc != null && mc.worldRenderer != null && mc.world != null) {
                mc.worldRenderer.reload();
            }
        } catch (Throwable t) {
            Intelium.LOGGER.warn("Intelium: chunk reload failed.", t);
        }
    }
}
