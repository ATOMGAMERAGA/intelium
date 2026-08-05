package com.intelium.diag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * TEMPORARY diagnostic. Dumps the exact method names and JVM descriptors of the
 * classes Intelium's mixins target, so the {@code method = "..."} strings can be
 * written against the real 1.21.11 mappings instead of guessed. Deleted again
 * once the descriptors are pinned down.
 */
@DisplayName("API probe (temporary)")
class ApiProbeTest {

    private static final String[][] TARGETS = {
            {"net.minecraft.client.render.entity.EntityRenderDispatcher", "shouldRender"},
            {"net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher", "render|get"},
            {"net.minecraft.client.particle.ParticleManager", "add"},
            {"net.minecraft.client.option.GameOptions", "ov"},
            {"net.minecraft.client.util.Window", "eight"},
            {"net.minecraft.entity.Entity", "getBoundingBox|hasCustomName|getWidth|getHeight"},
            {"net.minecraft.entity.player.PlayerEntity", "!"},
            {"net.minecraft.util.math.Box", "!"},
    };

    @Test
    @DisplayName("Dump mixin target signatures")
    void dump() {
        StringBuilder sb = new StringBuilder("\n===== INTELIUM API PROBE (1.21.11) =====\n");
        for (String[] target : TARGETS) {
            sb.append("\n--- ").append(target[0]).append('\n');
            Class<?> c;
            try {
                c = Class.forName(target[0], false, ApiProbeTest.class.getClassLoader());
            } catch (Throwable t) {
                sb.append("  NOT FOUND: ").append(t).append('\n');
                continue;
            }
            if ("!".equals(target[1])) {
                sb.append("  present\n");
                continue;
            }
            List<String> lines = new ArrayList<>();
            for (Method m : c.getDeclaredMethods()) {
                if (!matches(m.getName(), target[1])) continue;
                lines.add("  " + (java.lang.reflect.Modifier.isStatic(m.getModifiers()) ? "static " : "")
                        + m.getName() + descriptor(m));
            }
            if (lines.isEmpty()) sb.append("  (no matching methods)\n");
            lines.stream().sorted().forEach(l -> sb.append(l).append('\n'));
        }
        sb.append("\n===== END PROBE =====\n");
        // A file, not stdout or an assertion: Gradle swallows both a passing
        // test's streams and a failing test's message, so the workflow cats
        // this file instead.
        try {
            java.nio.file.Path out = java.nio.file.Paths.get(
                    System.getProperty("project.rootDir", "."), "build", "api-probe.txt");
            java.nio.file.Files.createDirectories(out.getParent());
            java.nio.file.Files.writeString(out, sb.toString());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static boolean matches(String name, String filter) {
        for (String part : filter.split("\\|")) {
            if (name.contains(part)) return true;
        }
        return false;
    }

    private static String descriptor(Method m) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : m.getParameterTypes()) sb.append(typeDescriptor(p));
        return sb.append(')').append(typeDescriptor(m.getReturnType())).toString();
    }

    private static String typeDescriptor(Class<?> c) {
        if (c.isArray()) return "[" + typeDescriptor(c.getComponentType());
        if (!c.isPrimitive()) return "L" + c.getName().replace('.', '/') + ";";
        if (c == void.class) return "V";
        if (c == boolean.class) return "Z";
        if (c == byte.class) return "B";
        if (c == char.class) return "C";
        if (c == short.class) return "S";
        if (c == int.class) return "I";
        if (c == long.class) return "J";
        if (c == float.class) return "F";
        return "D";
    }
}
