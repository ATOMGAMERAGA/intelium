package com.intelium.mixin.client;

import com.intelium.optimization.RenderBudget;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Particle Burst Limiter - the third of Intelium's Render Budget Engine
 * systems (26.x).
 *
 * <p>Every route into the particle system - the effect overloads, the emitters,
 * the server's particle packets - ends up calling this one method, so capping it
 * caps everything without needing to know where the particles came from.
 *
 * <p>What this is for is the burst, not the drizzle: ordinary play spawns a
 * handful of particles a tick and never comes near the budget. A TNT chain or a
 * splash-potion volley spawns thousands in a single tick, and each one then
 * costs CPU to tick and GPU to draw for its entire lifetime - which is why the
 * frame rate stays down for seconds after the bang, not just during it. Refusing
 * the tail of that one tick keeps the explosion looking like an explosion.
 */
@Mixin(value = ParticleEngine.class, remap = false)
public abstract class MixinParticleEngine {

    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void intelium$budgetParticles(Particle particle, CallbackInfo ci) {
        if (RenderBudget.shouldRejectParticle()) {
            ci.cancel();
        }
    }
}
