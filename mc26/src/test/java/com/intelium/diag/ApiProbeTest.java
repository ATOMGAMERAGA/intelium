package com.intelium.diag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * TEMPORARY diagnostic. Dumps the exact method names and JVM descriptors of the
 * classes Intelium's mixins target, so the {@code method = "..."} strings can be
 * written against the real 26.x (unobfuscated) names instead of guessed. Deleted
 * again once the descriptors are pinned down.
 */
@DisplayName("API probe (temporary)")
class ApiProbeTest {

    private static final String[][] TARGETS = {
            {"net.minecraft.client.renderer.entity.EntityRenderDispatcher", "shouldRender"},
            {"net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher", "render|get"},
            {"net.minecraft.client.particle.ParticleEngine", "add"},
            {"net.minecraft.client.Options", "ov"},
            {"com.mojang.blaze3d.platform.Window", "eight"},
            {"net.minecraft.world.entity.Entity", "getBoundingBox|hasCustomName|getBbWidth|getBbHeight"},
            {"net.minecraft.world.entity.player.Player", "!"},
            {"net.minecraft.world.phys.AABB", "!"},
    };

    @Test
    @DisplayName("Dump mixin target signatures")
    void dump() {
        StringBuilder sb = new StringBuilder("\n===== INTELIUM API PROBE (26.x) =====\n");
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
        fail(sb.toString());
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
