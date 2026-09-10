package com.intelium.mixin.client;

import com.intelium.optimization.RenderBudget;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Block Entity Budget - the second of Intelium's Render Budget Engine
 * systems.
 *
 * <p>Chests, signs, banners, beds, skulls and shulker boxes are the one part of
 * the world Sodium cannot bake into the chunk mesh: each is walked and drawn
 * individually, every frame. An open field has none of them; a storage room has
 * six hundred, and that is where an iGPU's frame rate falls off a cliff.
 *
 * <p>{@code isInRenderDistance} is vanilla's per-block-entity gate, asked once
 * each per frame, so Intelium answers it with two extra questions: is this one
 * far enough to be a smudge (the same apparent-size test the entity budget uses,
 * against a block-sized object), and has this frame already drawn its full
 * allowance? The frame's allowance is generous enough that ordinary scenes never
 * reach it.
 *
 * <p>Renderers that override the default gate - the beacon, whose beam is
 * visible from any distance - keep their own answer, which is the correct
 * outcome: this budget is for the hundreds of small props, not the landmarks.
 */
@Mixin(BlockEntityRenderer.class)
public interface MixinBlockEntityRenderer {

    @Inject(method = "isInRenderDistance", at = @At("HEAD"), cancellable = true, require = 0)
    private void intelium$budgetBlockEntities(BlockEntity blockEntity, Vec3d cameraPos,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (!RenderBudget.blockEntityBudgetOn()) return;

        BlockPos pos = blockEntity.getPos();
        double dx = pos.getX() + 0.5 - cameraPos.x;
        double dy = pos.getY() + 0.5 - cameraPos.y;
        double dz = pos.getZ() + 0.5 - cameraPos.z;

        // One shared call for both questions - too small to make out, and over
        // this frame's allowance - which also guarantees a nearby block entity
        // is never the one dropped because of where it fell in the iteration.
        // 1.21.11 has no frame-boundary hook, so the budget still infers
        // boundaries from call timing here; the fairness guarantee does not
        // depend on that.
        if (RenderBudget.shouldSkipBlockEntity(dx * dx + dy * dy + dz * dz)) {
            cir.setReturnValue(false);
        }
    }
}
