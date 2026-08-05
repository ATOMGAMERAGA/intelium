package com.intelium.diag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * TEMPORARY diagnostic. Finds the classes Intelium's mixins target by scanning
 * the Minecraft jar for them - the render dispatchers have moved packages more
 * than once - and dumps their method names and JVM descriptors, so the mixin
 * {@code method = "..."} selectors can be written against reality rather than
 * guessed. Deleted once the descriptors are pinned down.
 */
@DisplayName("API probe (temporary)")
class ApiProbeTest {

    /** A class that is certainly in the Minecraft jar, used to find the jar. */
    private static final String SEED = "net.minecraft.client.particle.Particle";

    /** Simple-name fragments of the classes worth looking at. */
    private static final String[] CLASS_FILTERS = {
            "RenderDispatcher", "EntityRenderers", "BlockEntityRenderers",
    };

    @Test
    @DisplayName("Dump mixin target signatures")
    void dump() {
        StringBuilder sb = new StringBuilder("\n===== INTELIUM API PROBE (1.21.11) =====\n");
        TreeSet<String> candidates = new TreeSet<>();
        try {
            candidates.addAll(scanForClasses());
        } catch (Exception e) {
            sb.append("SCAN FAILED: ").append(e).append('\n');
        }

        sb.append("\n--- candidates (").append(candidates.size()).append(")\n");
        for (String name : candidates) sb.append("  ").append(name).append('\n');

        for (String name : candidates) {
            if (!name.contains("RenderDispatcher")) continue;
            sb.append("\n--- ").append(name).append('\n');
            Class<?> c;
            try {
                c = Class.forName(name, false, ApiProbeTest.class.getClassLoader());
            } catch (Throwable t) {
                sb.append("  NOT LOADABLE: ").append(t).append('\n');
                continue;
            }
            List<String> lines = new ArrayList<>();
            for (Method m : c.getDeclaredMethods()) {
                lines.add("  " + (Modifier.isStatic(m.getModifiers()) ? "static " : "")
                        + m.getName() + descriptor(m));
            }
            lines.stream().sorted().forEach(l -> sb.append(l).append('\n'));
        }
        sb.append("\n===== END PROBE =====\n");
        write(sb);
    }

    /** Class names in the Minecraft jar whose simple name matches a filter. */
    private static List<String> scanForClasses() throws Exception {
        Class<?> seed = Class.forName(SEED, false, ApiProbeTest.class.getClassLoader());
        URI location = seed.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path path = Paths.get(location);
        List<String> found = new ArrayList<>();
        if (Files.isDirectory(path)) {
            try (var stream = Files.walk(path)) {
                stream.filter(p -> p.toString().endsWith(".class"))
                        .map(p -> path.relativize(p).toString())
                        .forEach(rel -> collect(rel, found));
            }
        } else {
            try (JarFile jar = new JarFile(path.toFile())) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    collect(entries.nextElement().getName(), found);
                }
            }
        }
        return found;
    }

    private static void collect(String entryName, List<String> out) {
        if (!entryName.endsWith(".class") || entryName.contains("$")) return;
        String binary = entryName.substring(0, entryName.length() - ".class".length())
                .replace('\\', '/').replace('/', '.');
        String simple = binary.substring(binary.lastIndexOf('.') + 1);
        for (String filter : CLASS_FILTERS) {
            if (simple.contains(filter)) {
                out.add(binary);
                return;
            }
        }
    }

    private static void write(CharSequence report) {
        try {
            Path out = Paths.get(System.getProperty("project.rootDir", "."),
                    "build", "api-probe.txt");
            Files.createDirectories(out.getParent());
            Files.writeString(out, report.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
