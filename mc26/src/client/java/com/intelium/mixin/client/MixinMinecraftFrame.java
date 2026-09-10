package com.intelium.mixin.client;

import com.intelium.client.FrameTimeSampler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The frame boundary, told rather than guessed.
 *
 * <p>Two Intelium systems need to know when a frame starts: the block-entity
 * budget, which must reset its per-frame count, and the frame-time tracker,
 * which measures the interval between boundaries. Both used to infer it -
 * the budget from a gap in call timing, the tracker not existing at all
 * because {@code Minecraft#getFps()} was the only signal available.
 *
 * <p>{@code Minecraft.renderFrame(Z)V} is called exactly once per rendered
 * frame, verified against the resolved 26.2 client jar
 * ({@code public void renderFrame(boolean)}). Hooking it removes the
 * inference and, with it, a {@code System.nanoTime()} call per block entity.
 *
 * <h2>Composability</h2>
 *
 * <p>This is the least intrusive shape a mixin has: a non-cancellable
 * {@code HEAD} inject that reads nothing, writes nothing on the game side and
 * returns immediately. It cannot swallow another mod's injection, cannot
 * change control flow, and does not compete for a priority - which matters on
 * a client like Lunar, where {@code Minecraft} already carries injections from
 * several sources. {@code require = 0} keeps a build whose render loop moved
 * from crashing; {@code InteliumClientMixinPlugin} verifies the full descriptor
 * first and stands the capability down if it is gone.
 */
@Mixin(value = Minecraft.class, remap = false)
public abstract class MixinMinecraftFrame {

    @Inject(method = "renderFrame(Z)V", at = @At("HEAD"), require = 0)
    private void intelium$frameBoundary(boolean tick, CallbackInfo ci) {
        FrameTimeSampler.onFrame();
    }
}
