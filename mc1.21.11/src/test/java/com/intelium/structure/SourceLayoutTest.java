package com.intelium.structure;

import com.intelium.resources.TestPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-tree layout checks for the split, multi-version repo:
 * <ul>
 *   <li>{@code shared/src/main/java} - pure, version-agnostic logic compiled into
 *       every per-version build;</li>
 *   <li>{@code <target>/src/main/java} + {@code <target>/src/client/java} - the
 *       Minecraft/Sodium-facing glue for this build (1.21.11).</li>
 * </ul>
 */
@DisplayName("Source-tree layout")
class SourceLayoutTest {

    /** Java source roots that make up this build: shared + this target's own. */
    private static List<Path> javaRoots() {
        List<Path> roots = new ArrayList<>();
        roots.add(TestPaths.repoRoot().resolve("shared/src/main/java"));
        roots.add(TestPaths.projectRoot().resolve("src/main/java"));
        roots.add(TestPaths.projectRoot().resolve("src/client/java"));
        List<Path> existing = new ArrayList<>();
        for (Path p : roots) {
            if (Files.isDirectory(p)) existing.add(p);
        }
        return existing;
    }

    /** True if {@code com/intelium/<rel>} exists under any source root. */
    private static boolean sourceExists(String relUnderComIntelium) {
        for (Path root : javaRoots()) {
            if (Files.exists(root.resolve("com/intelium").resolve(relUnderComIntelium))) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("shared and target source roots exist")
    void rootsExist() {
        assertTrue(Files.isDirectory(TestPaths.repoRoot().resolve("shared/src/main/java/com/intelium")),
                "shared/src/main/java/com/intelium should exist");
        assertTrue(Files.isDirectory(TestPaths.projectRoot().resolve("src/main/java/com/intelium")),
                "target src/main/java/com/intelium should exist");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Intelium.java",
            "IntelGpuClassifier.java",
            "IntelGpuGeneration.java",
            "SupportedGpus.java",
            "IntelGpuDetector.java"
    })
    @DisplayName("Top-level source file exists (shared or target)")
    void topLevelSourcePresent(String name) {
        assertTrue(sourceExists(name), "missing source file: com/intelium/" + name);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "config/InteliumConfig.java",
            "config/InteliumConfigIO.java"
    })
    @DisplayName("Config source file exists (shared)")
    void configSourcePresent(String name) {
        assertTrue(sourceExists(name), "missing config source file: com/intelium/" + name);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "config/InteliumConfigEntryPoint.java",
            "gui/SupportedGpusScreen.java"
    })
    @DisplayName("Client-only source file exists in the client source set")
    void clientSourcePresent(String name) {
        Path p = TestPaths.projectRoot()
                .resolve("src/client/java/com/intelium").resolve(name);
        assertTrue(Files.exists(p), "missing client source file: " + p);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "optimization/ChunkBuilderTuner.java"
    })
    @DisplayName("Optimization source file exists (shared)")
    void optimizationSourcePresent(String name) {
        assertTrue(sourceExists(name), "missing optimization source file: com/intelium/" + name);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "MixinSodiumWorldRenderer.java",
            "MixinChunkBuilder.java"
    })
    @DisplayName("Mixin source file exists (target)")
    void mixinSourcePresent(String name) {
        Path p = TestPaths.projectRoot()
                .resolve("src/main/java/com/intelium/mixin").resolve(name);
        assertTrue(Files.exists(p), "missing mixin source file: " + p);
    }

    @Test
    @DisplayName("All Java source files use Unix line endings")
    void unixLineEndings() throws IOException {
        forEachJava(p -> {
            try {
                assertFalse(Files.readString(p).contains("\r\n"), "CRLF found in " + p);
            } catch (IOException e) {
                fail(e);
            }
        });
    }

    @Test
    @DisplayName("All Java source files have a package declaration")
    void allHavePackage() throws IOException {
        forEachJava(p -> {
            try {
                assertTrue(Files.readString(p).contains("package com.intelium"),
                        "missing package declaration in " + p);
            } catch (IOException e) {
                fail(e);
            }
        });
    }

    @Test
    @DisplayName("Package declaration matches directory")
    void packageMatchesDir() throws IOException {
        for (Path root : javaRoots()) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                    try {
                        String content = Files.readString(p);
                        String expected = root.relativize(p.getParent())
                                .toString().replace(java.io.File.separatorChar, '.');
                        assertTrue(content.contains("package " + expected + ";"),
                                "Expected 'package " + expected + ";' in " + p);
                    } catch (IOException e) {
                        fail(e);
                    }
                });
            }
        }
    }

    @Test
    @DisplayName("No 'TODO FIXME' markers in sources")
    void noTodoMarkers() throws IOException {
        forEachJava(p -> {
            try {
                assertFalse(Files.readString(p).contains("TODO FIXME"), "leftover marker in " + p);
            } catch (IOException e) {
                fail(e);
            }
        });
    }

    @Test
    @DisplayName("No tabs in resource JSON files (style)")
    void noTabsInJson() throws IOException {
        for (Path p : new Path[]{
                TestPaths.fabricModJson(),
                TestPaths.mixinsJson(),
                TestPaths.langEnUs(),
                TestPaths.langTrTr()
        }) {
            String content = Files.readString(p);
            assertFalse(content.contains("\t"), "tabs found in " + p);
        }
    }

    @Test
    @DisplayName("LICENSE file is present")
    void licensePresent() {
        assertTrue(Files.exists(TestPaths.license()));
    }

    @Test
    @DisplayName("LICENSE is a GPL-family license")
    void licenseIsGplFamily() throws IOException {
        // Collapse whitespace so cross-line phrases ("Lesser General\nPublic
        // License") still match a single-line search.
        String content = Files.readString(TestPaths.license()).replaceAll("\\s+", " ").toLowerCase();
        assertTrue(
                content.contains("gnu general public license")
                        || content.contains("gnu lesser general public license")
                        || content.contains("gpl")
                        || content.contains("lgpl"),
                "LICENSE must declare a GPL-family license");
    }

    @Test
    @DisplayName("README is present")
    void readmePresent() {
        assertTrue(Files.exists(TestPaths.readme()));
    }

    /** Runs an assertion over every .java file in every source root. */
    private static void forEachJava(java.util.function.Consumer<Path> check) throws IOException {
        for (Path root : javaRoots()) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(check);
            }
        }
    }
}
