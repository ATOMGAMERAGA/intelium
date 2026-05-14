package com.intelium.mixin;

import com.intelium.Intelium;
import com.intelium.config.InteliumConfigIO;
import com.intelium.optimization.ChunkBuilderTuner;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = ChunkBuilder.class, remap = false)
public abstract class MixinChunkBuilder {

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static int intelium$tuneWorkerCount(int original) {
        if (!Intelium.IS_ENABLED || !Intelium.IS_COMPATIBLE) return original;
        int desired = InteliumConfigIO.get().chunkBuildWorkers;
        if (desired <= 0) {
            desired = ChunkBuilderTuner.recommendedWorkers(Intelium.DETECTED_GENERATION);
        }
        return Math.max(1, Math.min(desired, original));
    }
}
