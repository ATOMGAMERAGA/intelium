package com.intelium.client;

import com.intelium.Intelium;
import net.minecraft.client.Minecraft;

/** Small client-side helpers for applying changes to the running game (26.x). */
public final class InteliumGame {

    private InteliumGame() {}

    /**
     * Asks the game to rebuild its chunk renderer. This is how a chunk-worker
     * count change (or the A/B benchmark toggle) takes effect live: Sodium reads
     * the worker count when it recreates the chunk builder during a reload.
     */
    public static void reloadChunks() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc != null && mc.levelRenderer != null && mc.level != null) {
                // 26.x renamed allChanged(); resetLevelRenderData() rebuilds the
                // section render data so a new chunk-worker count takes effect.
                mc.levelRenderer.resetLevelRenderData();
            }
        } catch (Throwable t) {
            Intelium.LOGGER.warn("Intelium: chunk reload failed.", t);
        }
    }
}
