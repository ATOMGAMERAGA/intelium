package com.intelium.mixin;

import com.intelium.IntelGpuDetector;
import com.intelium.Intelium;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fallback GPU-detection hook. Primary detection runs from the first client
 * tick (see {@link Intelium#onInitializeClient()}); this guarantees detection
 * has happened by the time Sodium starts rendering a world, even if the tick
 * hook was somehow missed. {@link IntelGpuDetector#detectOnce()} is idempotent.
 */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class MixinSodiumWorldRenderer {

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void intelium$detectGpu(CallbackInfo ci) {
        try {
            IntelGpuDetector.detectOnce();
        } catch (Throwable t) {
            Intelium.LOGGER.error("GPU detection failed; Intelium will stay disabled.", t);
            Intelium.IS_COMPATIBLE = false;
        }
    }
}
