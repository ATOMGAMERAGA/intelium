package com.intelium.mixin.client;

import com.intelium.Intelium;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Keeps the Render Budget Engine's hooks from ever crashing a 26.x build whose
 * render path has moved on.
 *
 * <p>The three hooks target vanilla methods, and the 26.x line rewrites its
 * render path between releases - the entity and block-entity dispatchers have
 * already moved once. Rather than pin one Minecraft build or apply blindly, this
 * plugin checks at load time that each hook's target class <em>and</em> method
 * are still there, and skips the mixin if not: that budget disables itself
 * cleanly (with a line in the log saying so) while everything else keeps
 * working. Same contract as {@code com.intelium.mixin.InteliumMixinPlugin} for
 * the Sodium hooks.
 *
 * <p>This check is only meaningful because 26.x is unobfuscated - the names
 * below are the runtime names. The 1.21.11 build has no equivalent plugin: there
 * the mixin annotation processor validates every selector against the mappings
 * at compile time, which is a stronger guarantee than a load-time lookup could
 * be.
 */
public class InteliumClientMixinPlugin implements IMixinConfigPlugin {

    private static final String ENTITY_RENDERER =
            "net.minecraft.client.renderer.entity.EntityRenderer";
    private static final String BLOCK_ENTITY_RENDERER =
            "net.minecraft.client.renderer.blockentity.BlockEntityRenderer";
    private static final String PARTICLE_ENGINE =
            "net.minecraft.client.particle.ParticleEngine";

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("MixinEntityRenderer")) {
            return gate(ENTITY_RENDERER, "shouldRender", "entity culling");
        }
        if (mixinClassName.endsWith("MixinBlockEntityRenderer")) {
            return gate(BLOCK_ENTITY_RENDERER, "shouldRender", "the block-entity budget");
        }
        if (mixinClassName.endsWith("MixinParticleEngine")) {
            return gate(PARTICLE_ENGINE, "add", "the particle burst limiter");
        }
        return true;
    }

    private static boolean gate(String className, String methodName, String feature) {
        boolean ok = classExists(className) && methodExists(className, methodName);
        if (!ok) {
            Intelium.LOGGER.warn("Intelium: {}.{} not found on this Minecraft build - "
                    + "{} is disabled (no crash).", className, methodName, feature);
        }
        return ok;
    }

    /**
     * Reports whether a class is present <em>without loading it</em>. Loading a
     * vanilla class this early defines it in the class loader before other mods'
     * mixins can transform it, which fails their injections outright; looking the
     * class file up as a classpath resource answers the question without that.
     */
    private static boolean classExists(String name) {
        return InteliumClientMixinPlugin.class.getClassLoader()
                .getResource(resourcePath(name)) != null;
    }

    /**
     * Reports whether {@code className} declares {@code methodName}, by parsing
     * the class bytes with ASM rather than loading the class (see
     * {@link #classExists(String)} for why loading is unsafe here).
     */
    private static boolean methodExists(String className, String methodName) {
        try (InputStream in = classResourceStream(className)) {
            if (in == null) return false;
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node,
                    ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (node.methods == null) return false;
            for (MethodNode m : node.methods) {
                if (m.name.equals(methodName)) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String resourcePath(String binaryName) {
        return binaryName.replace('.', '/') + ".class";
    }

    private static InputStream classResourceStream(String binaryName) {
        return InteliumClientMixinPlugin.class.getClassLoader()
                .getResourceAsStream(resourcePath(binaryName));
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
