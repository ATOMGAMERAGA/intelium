package com.intelium.mixin;

import com.intelium.Intelium;
import com.intelium.config.InteliumConfigIO;
import com.intelium.optimization.DrawCallBatcher;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class MixinDefaultChunkRenderer {

    @Inject(method = "render", at = @At("HEAD"))
    private void intelium$prepareBatches(CallbackInfo ci) {
        if (!Intelium.IS_ENABLED || !Intelium.IS_COMPATIBLE) return;
        if (!InteliumConfigIO.get().drawCallBatching) return;
        DrawCallBatcher.beginFrame(Intelium.DETECTED_GENERATION);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void intelium$flushBatches(CallbackInfo ci) {
        if (!Intelium.IS_ENABLED || !Intelium.IS_COMPATIBLE) return;
        if (!InteliumConfigIO.get().drawCallBatching) return;
        DrawCallBatcher.endFrame();
    }
}
