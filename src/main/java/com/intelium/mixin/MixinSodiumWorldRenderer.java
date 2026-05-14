package com.intelium.mixin;

import com.intelium.IntelGpuDetector;
import com.intelium.Intelium;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class MixinSodiumWorldRenderer {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void intelium$detectGpu(CallbackInfo ci) {
        try {
            IntelGpuDetector.detectOnce();
        } catch (Throwable t) {
            Intelium.LOGGER.error("GPU detection failed; Intelium will stay disabled.", t);
            Intelium.IS_COMPATIBLE = false;
        }
    }
}
