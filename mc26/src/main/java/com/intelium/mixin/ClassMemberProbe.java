package com.intelium.mixin;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;

/**
 * Answers "does this class still declare this exact method?" during mixin
 * config preparation, without loading the class.
 *
 * <h2>Why the descriptor matters</h2>
 *
 * <p>Intelium used to check the method <em>name</em> only. That is not enough
 * to know a hook will attach. A name survives a signature change, so
 * {@code shouldRender} losing a parameter, or gaining an overload the injector
 * then binds to the wrong one, both passed the old check - the mixin was
 * applied, the injector matched nothing, {@code require = 0} let it pass in
 * silence, and the settings screen went on offering a feature that did
 * nothing. On a client shipping its own Sodium or Minecraft build - Lunar being
 * the case that prompted this - that is not hypothetical.
 *
 * <p>Matching the full descriptor turns a silent no-op into an honest
 * "unavailable, here is why", which the capability registry then reports.
 *
 * <h2>Why not {@code Class.forName}</h2>
 *
 * <p>Forcing a class to load here - during mixin-config preparation, before the
 * DEFAULT mixin phase - <em>defines</em> it in the class loader too early.
 * Other mods' mixins targeting the same class (notably Iris's
 * {@code MixinSodiumWorldRenderer}) then fail with
 * {@code MixinTargetAlreadyLoadedException} and crash the game on startup.
 * Reading the class file as a classpath resource answers the question without
 * ever loading it.
 */
public final class ClassMemberProbe {

    private ClassMemberProbe() {}

    /** Whether the class is on the classpath, without loading it. */
    public static boolean classExists(String binaryName) {
        return ClassMemberProbe.class.getClassLoader()
                .getResource(resourcePath(binaryName)) != null;
    }

    /**
     * Whether {@code binaryName} declares a method with exactly this name and
     * descriptor.
     *
     * @param descriptor JVM method descriptor, e.g. {@code (Z)V}
     */
    public static boolean methodExists(String binaryName, String name, String descriptor) {
        ClassNode node = read(binaryName);
        if (node == null || node.methods == null) return false;
        for (MethodNode m : node.methods) {
            if (m.name.equals(name) && m.desc.equals(descriptor)) return true;
        }
        return false;
    }

    /**
     * Whether {@code binaryName} declares a method with this name, ignoring the
     * descriptor. Used only where a hook genuinely does not care about the
     * signature; prefer {@link #methodExists(String, String, String)}.
     */
    public static boolean methodNameExists(String binaryName, String name) {
        ClassNode node = read(binaryName);
        if (node == null || node.methods == null) return false;
        for (MethodNode m : node.methods) {
            if (m.name.equals(name)) return true;
        }
        return false;
    }

    /**
     * How many methods named {@code name} the class declares. A count above one
     * means the injector's selector is ambiguous unless it carries a descriptor.
     */
    public static int countMethods(String binaryName, String name) {
        ClassNode node = read(binaryName);
        if (node == null || node.methods == null) return 0;
        int n = 0;
        for (MethodNode m : node.methods) {
            if (m.name.equals(name)) n++;
        }
        return n;
    }

    private static ClassNode read(String binaryName) {
        try (InputStream in = ClassMemberProbe.class.getClassLoader()
                .getResourceAsStream(resourcePath(binaryName))) {
            if (in == null) return null;
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node,
                    ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String resourcePath(String binaryName) {
        return binaryName.replace('.', '/') + ".class";
    }
}
