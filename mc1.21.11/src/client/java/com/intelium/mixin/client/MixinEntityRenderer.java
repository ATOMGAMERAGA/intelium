package com.intelium.mixin.client;

import com.intelium.optimization.RenderBudget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Smart Entity Culling - the entity half of Intelium's Render Budget Engine.
 *
 * <p>{@code shouldRender} is vanilla's own "is this entity worth drawing?"
 * question, asked once per entity per frame for every renderer, so it is exactly
 * the right place to answer "no, it is three pixels tall". Vanilla's own answer
 * is a frustum test plus a flat distance; Intelium adds the one thing neither
 * covers - <em>apparent size</em>, which is what actually decides whether you
 * could have seen it (see {@code RenderBudgetTuning}).
 *
 * <p>Three things are never culled, whatever the numbers say: the entity the
 * camera is attached to, players, and anything wearing a name tag - the last two
 * because their labels are gameplay information, not decoration. Nothing within
 * {@code RenderBudgetTuning.NEVER_CULL_RADIUS} is culled either; that check
 * lives in the shared logic.
 *
 * <p>Injected at HEAD with {@code require = 0}: if a future Minecraft moves this
 * method, the hook simply never fires and everything else keeps working.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void intelium$cullTinyEntities(Entity entity, Frustum frustum,
                                           double cameraX, double cameraY, double cameraZ,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (!RenderBudget.entityCullingOn()) return;
        if (entity instanceof PlayerEntity || entity.hasCustomName()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && entity == mc.getCameraEntity()) return;

        // Bounding-box fields rather than accessors: they are public and have
        // outlived several renames of the size getters around them.
        Box box = entity.getBoundingBox();
        double sizeBlocks = Math.max(box.maxY - box.minY,
                Math.max(box.maxX - box.minX, box.maxZ - box.minZ));

        double dx = (box.minX + box.maxX) * 0.5 - cameraX;
        double dy = (box.minY + box.maxY) * 0.5 - cameraY;
        double dz = (box.minZ + box.maxZ) * 0.5 - cameraZ;

        if (RenderBudget.shouldCullEntity(sizeBlocks, dx * dx + dy * dy + dz * dz)) {
            cir.setReturnValue(false);
        }
    }
}
