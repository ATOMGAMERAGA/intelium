package com.intelium.mixin;

import com.intelium.Intelium;
import com.intelium.config.InteliumConfigIO;
import com.intelium.optimization.ChunkBuilderTuner;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tunes Sodium's chunk-build worker thread count for Intel iGPUs.
 *
 * <p>Sodium computes the count in the private static {@code getThreadCount()}
 * helper (which reads the user's {@code chunkBuilderThreads} option, falling
 * back to {@code getOptimalThreadCount()}). We override its return value with a
 * generation-aware figure so the count can be set both up and down - the
 * previous constructor {@code @ModifyVariable} targeted an {@code int} argument
 * that does not exist on {@code ChunkBuilder(ClientLevel, ChunkVertexType)} and
 * therefore did nothing.
 */
@Mixin(value = ChunkBuilder.class, remap = false)
public abstract class MixinChunkBuilder {

    @Inject(method = "getThreadCount", at = @At("HEAD"), cancellable = true, require = 1)
    private static void intelium$tuneWorkerCount(CallbackInfoReturnable<Integer> cir) {
        if (!Intelium.IS_ENABLED || !Intelium.IS_COMPATIBLE) return;
        int desired = InteliumConfigIO.get().chunkBuildWorkers;
        if (desired <= 0) {
            desired = ChunkBuilderTuner.recommendedWorkers(Intelium.DETECTED_GENERATION);
        }
        if (desired >= 1) {
            cir.setReturnValue(desired);
        }
    }
}
