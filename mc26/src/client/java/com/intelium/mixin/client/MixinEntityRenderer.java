package com.intelium.mixin.client;

import com.intelium.optimization.RenderBudget;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Smart Entity Culling - the entity half of Intelium's Render Budget Engine
 * (26.x).
 *
 * <p>{@code shouldRender} is vanilla's own "is this entity worth drawing?"
 * question, asked once per entity per frame for every renderer, so it is exactly
 * the right place to answer "no, it is three pixels tall". Vanilla's own answer
 * is a frustum test plus a flat distance; Intelium adds the one thing neither
 * covers - <em>apparent size</em>, which is what actually decides whether you
 * could have seen it (see {@code RenderBudgetTuning}).
 *
 * <h2>What is never culled</h2>
 *
 * <p>An optimization that hides something a player needed to see is not an
 * optimization, it is a bug that happens to raise a number. On a UHC or PvP
 * server the list of things that matter is specific, so it is enumerated here
 * rather than left to a distance threshold:
 *
 * <ul>
 *   <li><b>Players</b> - always, at any distance.</li>
 *   <li><b>The camera entity</b> - what you are looking out of.</li>
 *   <li><b>Projectiles</b> - arrows, tridents, thrown potions, fireballs.
 *       Seeing one coming is the whole of the fight.</li>
 *   <li><b>Named entities</b> - name tags are information, and holograms and
 *       labelled armour stands are how servers build their UI.</li>
 *   <li><b>Glowing / outlined entities</b> - something has deliberately marked
 *       these as things to look at, usually a spectator or a team highlight.</li>
 *   <li><b>Vehicles and their riders</b> - the horse you are on, the boat
 *       someone is escaping in, a mob riding another mob.</li>
 *   <li>Anything within {@code RenderBudgetTuning.NEVER_CULL_RADIUS}, which the
 *       shared logic enforces.</li>
 * </ul>
 *
 * <h2>Cost</h2>
 *
 * <p>The gate is one volatile read, and it is first: with the budget off this
 * hook is a load and a branch. The camera test is an int compare against an id
 * the client tick publishes, rather than the {@code Minecraft.getInstance()}
 * call and field chase it used to do for every entity of every frame. The
 * remaining checks are an {@code instanceof} and a few flag reads, all on an
 * object already in cache because vanilla is about to ask it for its bounding
 * box anyway.
 *
 * <p>{@code remap = false}: the 26.x line is unobfuscated, so these names are
 * already the runtime names. {@code InteliumClientMixinPlugin} verifies the
 * target method's full descriptor before this is applied at all.
 */
@Mixin(value = EntityRenderer.class, remap = false)
public abstract class MixinEntityRenderer {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void intelium$cullTinyEntities(Entity entity, Frustum frustum,
                                           double cameraX, double cameraY, double cameraZ,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (!RenderBudget.entityCullingOn()) return;
        if (isProtected(entity)) return;

        // Bounding-box fields rather than accessors: they are public and have
        // outlived several renames of the size getters around them.
        AABB box = entity.getBoundingBox();
        double sizeBlocks = Math.max(box.maxY - box.minY,
                Math.max(box.maxX - box.minX, box.maxZ - box.minZ));

        double dx = (box.minX + box.maxX) * 0.5 - cameraX;
        double dy = (box.minY + box.maxY) * 0.5 - cameraY;
        double dz = (box.minZ + box.maxZ) * 0.5 - cameraZ;

        if (RenderBudget.shouldCullEntity(sizeBlocks, dx * dx + dy * dy + dz * dz)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Whether this entity is exempt from culling whatever its apparent size.
     * Ordered cheapest-and-commonest first, so the usual case (an ordinary mob
     * or a dropped item) exits after two tests.
     */
    private static boolean isProtected(Entity entity) {
        if (entity instanceof Player) return true;
        if (RenderBudget.isCameraEntity(entity.getId())) return true;
        if (entity instanceof Projectile) return true;
        if (entity.hasCustomName()) return true;
        if (entity.isCurrentlyGlowing()) return true;
        // A vehicle carrying someone, or a passenger being carried: both halves
        // of the pair stay visible, so a mount never renders without its rider.
        return entity.isVehicle() || entity.isPassenger();
    }
}
