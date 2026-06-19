package com.intelium.mixin;

import com.intelium.Intelium;
import com.intelium.config.InteliumConfig;
import com.intelium.config.InteliumConfigIO;
import com.intelium.optimization.ChunkBuilderTuner;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tunes the number of chunk-build worker threads Sodium spins up.
 *
 * <p>Sodium 0.8 sizes its worker pool from {@code ChunkBuilder.getThreadCount()},
 * which is called once from the constructor. Earlier versions of this mod tried
 * to modify a (non-existent) {@code int} argument on the constructor, so the
 * tuning never actually ran. We hook the real source of the thread count
 * instead.
 */
@Mixin(value = ChunkBuilder.class, remap = false)
public abstract class MixinChunkBuilder {

    @Inject(method = "getThreadCount", at = @At("RETURN"), cancellable = true, require = 0)
    private static void intelium$tuneWorkerCount(CallbackInfoReturnable<Integer> cir) {
        try {
            if (!Intelium.IS_ENABLED || !Intelium.IS_COMPATIBLE) return;

            InteliumConfig cfg = InteliumConfigIO.get();
            int desired;
            if (cfg.chunkBuildWorkers > 0) {
                // Explicit Intelium override always wins.
                desired = cfg.chunkBuildWorkers;
            } else {
                // Auto mode: respect an explicit Sodium thread-count choice so we
                // never silently fight a value the user picked in Sodium's menu.
                // chunkBuilderThreads == 0 means "Sodium auto", which is exactly
                // where a generation-aware default helps the most.
                int sodiumChoice = SodiumClientMod.options().performance.chunkBuilderThreads;
                if (sodiumChoice != 0) return;
                desired = ChunkBuilderTuner.recommendedWorkers(Intelium.DETECTED_GENERATION);
            }

            int cores = Runtime.getRuntime().availableProcessors();
            int tuned = Math.max(1, Math.min(desired, cores));
            if (tuned != cir.getReturnValueI()) {
                Intelium.LOGGER.info("Intelium: chunk-build workers {} -> {} ({})",
                        cir.getReturnValueI(), tuned, Intelium.DETECTED_GENERATION.display);
            }
            cir.setReturnValue(tuned);
        } catch (Throwable t) {
            // Never let a tuning failure take down chunk building.
            Intelium.LOGGER.warn("Intelium worker tuning skipped; using Sodium default.", t);
        }
    }
}
