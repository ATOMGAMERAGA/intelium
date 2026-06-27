package com.intelium.buildmeta;

import com.intelium.resources.TestPaths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("build.gradle validation")
class BuildScriptTest {

    private static String buildGradle;
    private static String settingsGradle;

    @BeforeAll
    static void load() throws IOException {
        buildGradle = Files.readString(TestPaths.buildGradle());
        settingsGradle = Files.readString(TestPaths.settingsGradle());
    }

    @Test
    @DisplayName("build.gradle uses fabric-loom plugin")
    void usesFabricLoom() {
        assertTrue(buildGradle.contains("fabric-loom"));
    }

    @Test
    @DisplayName("build.gradle uses Loom 1.14 or newer")
    void loomVersion() {
        // Sodium 0.8.11 is built with Loom 1.16.1, so we track that line.
        // Accept any "1.1[4-9]-SNAPSHOT" or "1.2x" for forward compat.
        assertTrue(buildGradle.matches("(?s).*fabric-loom['\"]\\s+version\\s+['\"]1\\.(1[4-9]|2\\d)-SNAPSHOT.*"),
                "must reference Loom 1.14 or newer");
    }

    @Test
    @DisplayName("build.gradle declares CaffeineMC maven repo")
    void caffeineMavenRepo() {
        assertTrue(buildGradle.contains("maven.caffeinemc.net"));
    }

    @Test
    @DisplayName("build.gradle declares Modrinth maven repo")
    void modrinthMavenRepo() {
        assertTrue(buildGradle.contains("api.modrinth.com"));
    }

    @Test
    @DisplayName("build.gradle uses modImplementation for Sodium")
    void sodiumIsModImplementation() {
        // Either the API-only artifact or the full Modrinth jar.
        // Intelium needs the full jar to mixin Sodium internals.
        assertTrue(buildGradle.contains("modImplementation \"net.caffeinemc:sodium-fabric-api")
                        || buildGradle.contains("modImplementation \"maven.modrinth:sodium"),
                "build.gradle must declare a Sodium modImplementation dependency");
    }

    @Test
    @DisplayName("build.gradle uses JUnit 5")
    void hasJunit5() {
        assertTrue(buildGradle.contains("junit-jupiter"));
    }

    @Test
    @DisplayName("build.gradle has test useJUnitPlatform")
    void useJunitPlatform() {
        assertTrue(buildGradle.contains("useJUnitPlatform"));
    }

    @Test
    @DisplayName("build.gradle compiles for Java 21")
    void java21() {
        assertTrue(buildGradle.contains("VERSION_21"));
        assertTrue(buildGradle.contains("release = 21"));
    }

    @Test
    @DisplayName("build.gradle has writeDependencyList task")
    void hasDependencyListTask() {
        assertTrue(buildGradle.contains("writeDependencyList"));
    }

    @Test
    @DisplayName("build.gradle has writeChecksums task")
    void hasChecksumsTask() {
        assertTrue(buildGradle.contains("writeChecksums"));
    }

    @Test
    @DisplayName("build.gradle injects manifest metadata")
    void hasManifestMetadata() {
        assertTrue(buildGradle.contains("Implementation-Title"));
        assertTrue(buildGradle.contains("Implementation-Version"));
        assertTrue(buildGradle.contains("Build-Timestamp"));
    }

    @Test
    @DisplayName("build.gradle supports inteliumVersion override")
    void supportsVersionOverride() {
        assertTrue(buildGradle.contains("inteliumVersion"));
    }

    @Test
    @DisplayName("build.gradle expands ${version} in fabric.mod.json")
    void expandsVersion() {
        assertTrue(buildGradle.contains("fabric.mod.json"));
        assertTrue(buildGradle.contains("expand"));
    }

    @Test
    @DisplayName("build.gradle declares loom splitEnvironmentSourceSets")
    void splitSourceSets() {
        assertTrue(buildGradle.contains("splitEnvironmentSourceSets"));
    }

    @Test
    @DisplayName("settings.gradle references fabricmc maven")
    void settingsHasFabricRepo() {
        assertTrue(settingsGradle.contains("fabricmc"));
    }
}
