package com.intelium.buildmeta;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("26.x 1.3.3 release metadata")
class Mc26ReleaseMetadataTest {

    private static Path project;
    private static Properties properties;

    @BeforeAll
    static void load() throws IOException {
        project = Path.of(System.getProperty("project.rootDir")).toAbsolutePath().normalize();
        properties = new Properties();
        try (InputStream input = Files.newInputStream(project.resolve("gradle.properties"))) {
            properties.load(input);
        }
    }

    @Test
    @DisplayName("Pins the stable Minecraft 26.2 dependency baseline")
    void stableDependencyBaseline() {
        assertEquals("1.3.3", properties.getProperty("mod_version"));
        assertEquals("26.2", properties.getProperty("minecraft_version"));
        assertEquals("0.19.3", properties.getProperty("loader_version"));
        assertEquals("0.158.0+26.2", properties.getProperty("fabric_version"));
        assertEquals("mc26.2-0.9.1-fabric", properties.getProperty("sodium_version"));
    }

    @Test
    @DisplayName("Keeps the declared 26.1 through 26.2 runtime range")
    void runtimeRange() throws IOException {
        String modJson = Files.readString(project.resolve("src/main/resources/fabric.mod.json"));
        assertTrue(modJson.contains("\"minecraft\": \">=26.1 <26.3\""));
        assertTrue(modJson.contains("\"sodium\": \">=0.8.0\""));
    }

    @Test
    @DisplayName("Vulkan detection uses DeviceInfo with a guarded OpenGL fallback")
    void deviceInfoDetectionIsWired() throws IOException {
        String detector = Files.readString(project.resolve(
                "src/main/java/com/intelium/IntelGpuDetector.java"));
        assertTrue(detector.contains("getDeviceInfo"));
        assertTrue(detector.contains("vendorName"));
        assertTrue(detector.contains("backendName"));
        assertTrue(detector.contains("driverInfo"));
        assertTrue(detector.contains("glContextCurrent()"));
        assertFalse(detector.contains("intelium.disabled.no_gl"));
    }

    @Test
    @DisplayName("Sodium worker hook passes the detected render backend")
    void workerHookIsBackendAware() throws IOException {
        String mixin = Files.readString(project.resolve(
                "src/main/java/com/intelium/mixin/MixinChunkBuilder.java"));
        assertTrue(mixin.contains("Intelium.DETECTED_BACKEND"));
    }

    @Test
    @DisplayName("Release workflow publishes both 1.3.3 jars and checksums")
    void releaseWorkflowMatches() throws IOException {
        String workflow = Files.readString(project.resolve("../.github/workflows/release.yml")
                .normalize());
        assertTrue(workflow.contains("INTELIUM_VERSION: \"1.3.3\""));
        assertTrue(workflow.contains("Intelium-v1.3.3-1.21.11.jar"));
        assertTrue(workflow.contains("Intelium-v1.3.3-26.x.jar"));
        assertTrue(workflow.contains(".sha256"));
        assertTrue(workflow.contains(".sha512"));
    }
}
