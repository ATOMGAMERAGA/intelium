package com.intelium.mixin;

import com.intelium.Capabilities;
import com.intelium.Capability;
import com.intelium.Intelium;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Makes Intelium compatible with <em>any</em> Sodium version that runs on the
 * supported Minecraft versions, without ever crashing on internal differences.
 *
 * <p>Intelium's Sodium-facing mixins target internals that can change between
 * releases. Rather than pin to one Sodium version (and refuse others) or apply
 * blindly (and hard-crash when a target is missing), this plugin verifies at
 * load time that each mixin's target class - and, for the worker-count hook,
 * the exact method <em>and descriptor</em> - is present. If not, the mixin is
 * simply not applied: the affected feature reports itself unavailable while
 * everything else keeps working.
 *
 * <p>Checking the descriptor and not merely the name is the point. A hook whose
 * target changed shape used to be applied anyway, match nothing, and pass
 * silently under {@code defaultRequire: 0} while the settings screen still
 * offered the feature. See {@link ClassMemberProbe}.
 *
 * <p>Each gate publishes its result to {@link Capabilities}, so the startup
 * diagnostic and the settings UI can say which feature is off and why, instead
 * of the whole mod going dark or, worse, pretending to work.
 */
public class InteliumMixinPlugin implements IMixinConfigPlugin {

    private static final String CHUNK_BUILDER =
            "net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder";
    private static final String WORLD_RENDERER =
            "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer";

    /**
     * Verified against Sodium 0.9.1+mc26.2:
     * {@code private static int getThreadCount()} -> {@code ()I}.
     */
    private static final String GET_THREAD_COUNT = "getThreadCount";
    private static final String GET_THREAD_COUNT_DESC = "()I";

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("MixinChunkBuilder")) {
            return gate(Capability.WORKER_TUNING, CHUNK_BUILDER,
                    GET_THREAD_COUNT, GET_THREAD_COUNT_DESC);
        }
        if (mixinClassName.endsWith("MixinSodiumWorldRenderer")) {
            // Detection also runs from the client tick, so this is a fallback:
            // the class existing is the whole requirement, and the constructor
            // is not a signature Intelium depends on.
            boolean ok = ClassMemberProbe.classExists(WORLD_RENDERER);
            if (!ok) {
                Capabilities.disable(Capability.GPU_DETECTION,
                        "Sodium's SodiumWorldRenderer is absent; detection falls back to "
                                + "the client tick");
            }
            return ok;
        }
        return true;
    }

    /**
     * Verifies one hook's target and records the capability either way.
     *
     * <p>The failure message names the class, the member and the descriptor
     * that was expected, because the only useful bug report for this is one
     * that says what the running Sodium has instead.
     */
    private static boolean gate(Capability capability, String className,
                                String methodName, String descriptor) {
        if (!ClassMemberProbe.classExists(className)) {
            Capabilities.set(capability, false, className + " is not present");
            Intelium.LOGGER.warn("Intelium: {} not found on this Sodium build - {} disabled "
                    + "(no crash).", className, capability.name());
            return false;
        }
        if (!ClassMemberProbe.methodExists(className, methodName, descriptor)) {
            int overloads = ClassMemberProbe.countMethods(className, methodName);
            String reason = overloads == 0
                    ? className + "." + methodName + " is gone"
                    : className + "." + methodName + " changed signature (expected "
                            + descriptor + "; " + overloads + " overload(s) present)";
            Capabilities.set(capability, false, reason);
            Intelium.LOGGER.warn("Intelium: {} - {} disabled (no crash).",
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
