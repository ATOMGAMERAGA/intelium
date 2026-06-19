package com.intelium.mixin;

import com.intelium.Intelium;
import com.intelium.config.InteliumConfig;
import com.intelium.config.InteliumConfigIO;
import com.intelium.optimization.OcclusionTuner;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class MixinRenderSectionManager {

    @Inject(method = "update", at = @At("HEAD"), require = 0)
    private void intelium$adjustCulling(CallbackInfo ci) {
        try {
            if (!Intelium.IS_ENABLED || !Intelium.IS_COMPATIBLE) return;
            InteliumConfig cfg = InteliumConfigIO.get();
            if (cfg.aggressiveCulling) {
                OcclusionTuner.applyForCurrentFrame(Intelium.DETECTED_GENERATION);
            }
        } catch (Throwable t) {
            Intelium.LOGGER.warn("Intelium occlusion tuning failed; skipping.", t);
        }
    }
}
