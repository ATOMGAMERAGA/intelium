package com.intelium.structure;

import com.intelium.resources.TestPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Source-tree layout")
class SourceLayoutTest {

    @Test
    @DisplayName("src/main/java/com/intelium directory exists")
    void mainPackageExists() {
        Path p = TestPaths.projectRoot().resolve("src/main/java/com/intelium");
        assertTrue(Files.isDirectory(p), p + " should exist");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Intelium.java",
            "IntelGpuDetector.java",
            "IntelGpuGeneration.java"
    })
    @DisplayName("Top-level source file exists")
    void topLevelSourcePresent(String name) {
        Path p = TestPaths.projectRoot().resolve("src/main/java/com/intelium").resolve(name);
        assertTrue(Files.exists(p), "missing source file: " + p);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "InteliumConfig.java",
            "InteliumConfigIO.java",
            "InteliumConfigEntryPoint.java"
    })
    @DisplayName("Config source file exists")
    void configSourcePresent(String name) {
        Path p = TestPaths.projectRoot()
                .resolve("src/main/java/com/intelium/config").resolve(name);
        assertTrue(Files.exists(p), "missing config source file: " + p);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ChunkBuilderTuner.java",
            "DrawCallBatcher.java",
            "BufferStrategy.java",
            "OcclusionTuner.java"
    })
    @DisplayName("Optimization source file exists")
    void optimizationSourcePresent(String name) {
        Path p = TestPaths.projectRoot()
                .resolve("src/main/java/com/intelium/optimization").resolve(name);
        assertTrue(Files.exists(p), "missing optimization source file: " + p);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "MixinRenderSectionManager.java",
            "MixinSodiumWorldRenderer.java",
            "MixinChunkBuilder.java",
            "MixinDefaultChunkRenderer.java"
    })
    @DisplayName("Mixin source file exists")
    void mixinSourcePresent(String name) {
        Path p = TestPaths.projectRoot()
                .resolve("src/main/java/com/intelium/mixin").resolve(name);
        assertTrue(Files.exists(p), "missing mixin source file: " + p);
    }

    @Test
    @DisplayName("All Java source files use Unix line endings")
    void unixLineEndings() throws IOException {
        Path src = TestPaths.projectRoot().resolve("src/main/java");
        try (Stream<Path> walk = Files.walk(src)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    String content = Files.readString(p);
                    assertFalse(content.contains("\r\n"),
                            "CRLF found in " + p);
                } catch (IOException e) {
                    fail(e);
                }
            });
        }
    }

    @Test
    @DisplayName("All Java source files have a package declaration")
    void allHavePackage() throws IOException {
        Path src = TestPaths.projectRoot().resolve("src/main/java");
        try (Stream<Path> walk = Files.walk(src)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    String content = Files.readString(p);
                    assertTrue(content.contains("package com.intelium"),
                            "missing package declaration in " + p);
                } catch (IOException e) {
                    fail(e);
                }
            });
        }
    }

    @Test
    @DisplayName("Package declaration matches directory")
    void packageMatchesDir() throws IOException {
        Path src = TestPaths.projectRoot().resolve("src/main/java");
        try (Stream<Path> walk = Files.walk(src)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    String content = Files.readString(p);
                    // derive expected package from path
                    Path rel = src.relativize(p.getParent());
                    String expected = rel.toString()
                            .replace(java.io.File.separatorChar, '.');
                    assertTrue(content.contains("package " + expected + ";"),
                            "Expected 'package " + expected + ";' in " + p);
                } catch (IOException e) {
                    fail(e);
                }
            });
        }
    }

    @Test
    @DisplayName("No 'TODO FIXME' markers in main sources")
    void noTodoMarkers() throws IOException {
        // We allow plain TODO/FIXME but flag the combined marker which indicates
        // a placeholder that must not ship.
        Path src = TestPaths.projectRoot().resolve("src/main/java");
        try (Stream<Path> walk = Files.walk(src)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    String content = Files.readString(p);
                    assertFalse(content.contains("TODO FIXME"),
                            "leftover marker in " + p);
                } catch (IOException e) {
                    fail(e);
                }
            });
        }
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
            assertFalse(content.contains("\t"),
                    "tabs found in " + p);
        }
    }

    @Test
    @DisplayName("LICENSE file is present")
    void licensePresent() {
        Path p = TestPaths.projectRoot().resolve("LICENSE");
        assertTrue(Files.exists(p));
    }

    @Test
    @DisplayName("LICENSE is a GPL-family license")
    void licenseIsGplFamily() throws IOException {
        Path p = TestPaths.projectRoot().resolve("LICENSE");
        // Collapse whitespace so cross-line phrases ("Lesser General\nPublic
        // License") still match a single-line search.
        String content = Files.readString(p).replaceAll("\\s+", " ").toLowerCase();
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
        Path p = TestPaths.projectRoot().resolve("README.md");
        assertTrue(Files.exists(p));
    }
}
