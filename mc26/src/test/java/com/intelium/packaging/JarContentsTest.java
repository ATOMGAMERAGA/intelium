package com.intelium.packaging;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What must not be inside the jar.
 *
 * <h2>Why this is a test and not a convention</h2>
 *
 * <p>Intelium depends on Sodium, Fabric API and Minecraft classes at compile
 * time. Shipping any of them inside the mod jar would be actively harmful:
 * a bundled Sodium class would shadow the real one on the classpath, which on a
 * client that ships its own <em>modified</em> Sodium - Lunar Client, the
 * environment this release targets - would replace the client's renderer
 * internals with a stock copy and break it in ways that would look like Lunar's
 * fault. Bundled Fabric API would do the same to the loader.
 *
 * <p>The build uses plain {@code implementation} dependencies with no shadow or
 * jar-in-jar step, so today nothing is bundled. This test exists so that a
 * future dependency change cannot quietly make it so.
 */
@DisplayName("Jar contents")
class JarContentsTest {

    private static Path jar;
    private static List<String> entries;

    @BeforeAll
    static void locateJar() throws IOException {
        Path project = Path.of(System.getProperty("project.rootDir"))
                .toAbsolutePath().normalize();
        Path libs = project.resolve("build/libs");
        if (!Files.isDirectory(libs)) return;
        try (Stream<Path> files = Files.list(libs)) {
            jar = files
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().endsWith("-sources.jar"))
                    .findFirst()
                    .orElse(null);
        }
        if (jar == null) return;
        entries = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : zip.stream().toList()) {
                entries.add(entry.getName());
            }
        }
    }

    @Test
    @DisplayName("The mod jar was built and can be read")
    void jarExists() {
        assertNotNull(jar, "no mod jar in build/libs - run the jar task first");
        assertNotNull(entries);
        assertTrue(entries.contains("fabric.mod.json"), "the jar must carry its own metadata");
    }

    @Test
    @DisplayName("No Sodium classes are bundled")
    void noBundledSodium() {
        assertNoPrefix("net/caffeinemc/",
                "bundling Sodium would shadow the real one - and on a client that ships a "
                        + "modified Sodium, replace its renderer internals with a stock copy");
    }

    @Test
    @DisplayName("No Fabric API or loader classes are bundled")
    void noBundledFabric() {
        assertNoPrefix("net/fabricmc/", "Fabric API and the loader are provided by the game");
    }

    @Test
    @DisplayName("No Minecraft or Blaze3D classes are bundled")
    void noBundledMinecraft() {
        assertNoPrefix("net/minecraft/", "Minecraft is provided by the game");
        assertNoPrefix("com/mojang/", "Blaze3D and friends are provided by the game");
    }

    @Test
    @DisplayName("No Iris or shader-pipeline classes are bundled")
    void noBundledIris() {
        assertNoPrefix("net/irisshaders/", "Iris is an optional separate mod");
        assertNoPrefix("net/coderbot/", "Iris is an optional separate mod");
    }

    @Test
    @DisplayName("No third-party client files are bundled")
    void noBundledClientFiles() {
        for (String entry : entries) {
            String lower = entry.toLowerCase(Locale.ROOT);
            assertFalse(lower.contains("lunar"),
                    "Intelium must never ship Lunar Client files: " + entry);
        }
    }

    @Test
    @DisplayName("No nested jars: nothing is shaded or jar-in-jarred")
    void noNestedJars() {
        for (String entry : entries) {
            assertFalse(entry.toLowerCase(Locale.ROOT).endsWith(".jar"),
                    "the mod jar must not contain nested jars: " + entry);
        }
    }

    @Test
    @DisplayName("Every class in the jar is Intelium's own")
    void onlyInteliumClasses() {
        for (String entry : entries) {
            if (!entry.endsWith(".class")) continue;
            assertTrue(entry.startsWith("com/intelium/"),
                    "unexpected class shipped in the mod jar: " + entry);
        }
    }

    private static void assertNoPrefix(String prefix, String why) {
        for (String entry : entries) {
            assertFalse(entry.startsWith(prefix), why + " - found: " + entry);
        }
    }
}
