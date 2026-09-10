package com.intelium.mixin.client;

import com.intelium.Capabilities;
import com.intelium.Capability;
import com.intelium.Intelium;
import com.intelium.mixin.ClassMemberProbe;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Keeps the Render Budget Engine's hooks, and the frame-boundary hook, from
 * ever crashing a 26.x build whose render path has moved on.
 *
 * <p>These hooks target vanilla methods, and the 26.x line rewrites its render
 * path between releases - the entity and block-entity dispatchers have already
 * moved once. Rather than pin one Minecraft build or apply blindly, this plugin
 * verifies at load time that each hook's target class <em>and the exact method
 * descriptor</em> are still there, and skips the mixin if not: that feature
 * reports itself unavailable (with a line in the log saying why) while
 * everything else keeps working.
 *
 * <p>Every descriptor below was verified against the resolved Minecraft 26.2
 * client jar with {@code javap -s}. Checking them, rather than the method names
 * alone, is what turns "the injector silently matched nothing" into a reported
 * capability - see {@link ClassMemberProbe}.
 *
 * <p>This check is only meaningful because 26.x is unobfuscated: the names
 * below are the runtime names. The 1.21.11 build has no equivalent plugin for
 * its vanilla hooks - there the mixin annotation processor validates every
 * selector against the mappings at compile time, which is a stronger guarantee
 * than a load-time lookup could be.
 */
public class InteliumClientMixinPlugin implements IMixinConfigPlugin {

    private static final String ENTITY_RENDERER =
            "net.minecraft.client.renderer.entity.EntityRenderer";
    private static final String BLOCK_ENTITY_RENDERER =
            "net.minecraft.client.renderer.blockentity.BlockEntityRenderer";
    private static final String PARTICLE_ENGINE =
            "net.minecraft.client.particle.ParticleEngine";
    private static final String MINECRAFT = "net.minecraft.client.Minecraft";

    /** {@code boolean shouldRender(Entity, Frustum, double, double, double)}. */
    private static final String ENTITY_SHOULD_RENDER_DESC =
            "(Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z";

    /** {@code default boolean shouldRender(BlockEntity, Vec3)}. */
    private static final String BLOCK_ENTITY_SHOULD_RENDER_DESC =
            "(Lnet/minecraft/world/level/block/entity/BlockEntity;"
                    + "Lnet/minecraft/world/phys/Vec3;)Z";

    /** {@code void add(Particle)}. */
    private static final String PARTICLE_ADD_DESC =
            "(Lnet/minecraft/client/particle/Particle;)V";

    /** {@code void renderFrame(boolean)}. */
    private static final String RENDER_FRAME_DESC = "(Z)V";

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("MixinEntityRenderer")) {
            return gate(Capability.ENTITY_CULLING, ENTITY_RENDERER,
                    "shouldRender", ENTITY_SHOULD_RENDER_DESC);
        }
        if (mixinClassName.endsWith("MixinBlockEntityRenderer")) {
            return gate(Capability.BLOCK_ENTITY_BUDGET, BLOCK_ENTITY_RENDERER,
                    "shouldRender", BLOCK_ENTITY_SHOULD_RENDER_DESC);
        }
        if (mixinClassName.endsWith("MixinParticleEngine")) {
            return gate(Capability.PARTICLE_LIMITER, PARTICLE_ENGINE,
                    "add", PARTICLE_ADD_DESC);
        }
        if (mixinClassName.endsWith("MixinMinecraftFrame")) {
            // Losing this costs frame-time measurement and returns the
            // block-entity budget to inferring its own frame boundaries. Both
            // degrade; neither breaks.
            return gate(Capability.FRAME_BOUNDARY, MINECRAFT,
                    "renderFrame", RENDER_FRAME_DESC);
        }
        return true;
    }

    private static boolean gate(Capability capability, String className,
                                String methodName, String descriptor) {
        if (!ClassMemberProbe.classExists(className)) {
            Capabilities.set(capability, false, className + " is not present");
            Intelium.LOGGER.warn("Intelium: {} not found on this Minecraft build - {} is "
                    + "disabled (no crash).", className, capability.name());
            return false;
        }
        if (!ClassMemberProbe.methodExists(className, methodName, descriptor)) {
            int overloads = ClassMemberProbe.countMethods(className, methodName);
            String reason = overloads == 0
                    ? className + "." + methodName + " is gone"
                    : className + "." + methodName + " changed signature (expected "
                            + descriptor + "; " + overloads + " overload(s) present)";
            Capabilities.set(capability, false, reason);
            Intelium.LOGGER.warn("Intelium: {} - {} is disabled (no crash).",
                    reason, capability.name());
            return false;
        }
        Capabilities.set(capability, true, null);
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {}
}
