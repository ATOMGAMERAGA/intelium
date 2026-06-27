package com.intelium.mixin;

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
 * Makes Intelium compatible with <em>any</em> Sodium version that runs on the
 * supported Minecraft versions, without ever crashing on internal differences.
 *
 * <p>Intelium's two mixins target Sodium internals that can change between
 * releases. Rather than pin to one Sodium version (and refuse others) or apply
 * blindly (and hard-crash when a target class is missing), this plugin checks at
 * load time whether each mixin's target class - and, for the worker-count hook,
 * the exact method - is present. If not, the mixin is simply not applied: the
 * affected feature disables itself cleanly while everything else keeps working.
 *
 * <p>The injectors use {@code defaultRequire: 0}, so even when a target class
 * exists but its injection point shifted, the hook no-ops instead of crashing.
 * Capability flags are published to {@link Intelium} for status reporting.
 */
public class InteliumMixinPlugin implements IMixinConfigPlugin {

    private static final String CHUNK_BUILDER =
            "net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder";
    private static final String WORLD_RENDERER =
            "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer";

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("MixinChunkBuilder")) {
            boolean ok = classExists(CHUNK_BUILDER) && methodExists(CHUNK_BUILDER, "getThreadCount");
            Intelium.WORKER_TUNING_AVAILABLE = ok;
            if (!ok) {
                Intelium.LOGGER.warn("Intelium: Sodium ChunkBuilder.getThreadCount() not found on "
                        + "this Sodium build - chunk-worker tuning disabled (no crash).");
            }
            return ok;
        }
        if (mixinClassName.endsWith("MixinSodiumWorldRenderer")) {
            // Detection also runs from the client tick, so this is only a fallback.
            return classExists(WORLD_RENDERER);
        }
        return true;
    }

    /**
     * Reports whether a class is present <em>without loading it</em>.
     *
     * <p>We deliberately do NOT use {@code Class.forName}: forcing a Sodium
     * class to load here - during mixin-config preparation, before the DEFAULT
     * mixin phase - <em>defines</em> it in the class loader too early. Other
     * mods' mixins that target the same class (notably Iris's
     * {@code mixins.iris.compat.sodium.json:MixinSodiumWorldRenderer}) then fail
     * to apply with {@code MixinTargetAlreadyLoadedException}, crashing the game
     * on startup. Looking the class file up as a classpath resource answers the
     * "does it exist?" question without ever loading the class.
     */
    private static boolean classExists(String name) {
        return InteliumMixinPlugin.class.getClassLoader()
                .getResource(resourcePath(name)) != null;
    }

    /**
     * Reports whether {@code className} declares a method named
     * {@code methodName}, by parsing the class bytes with ASM rather than
     * loading the class (see {@link #classExists(String)} for why loading is
     * unsafe here).
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
        return InteliumMixinPlugin.class.getClassLoader()
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
