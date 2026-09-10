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

@DisplayName("26.2 1.3.4 release metadata")
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
        assertEquals("1.3.4", properties.getProperty("mod_version"));
        assertEquals("26.2", properties.getProperty("minecraft_version"));
        assertEquals("0.19.3", properties.getProperty("loader_version"));
        assertEquals("0.158.0+26.2", properties.getProperty("fabric_version"));
        assertEquals("mc26.2-0.9.1-fabric", properties.getProperty("sodium_version"));
    }

    @Test
    @DisplayName("Declares 26.2 only, because that is what this jar compiles against")
    void runtimeRangeIsHonest() throws IOException {
        String modJson = Files.readString(project.resolve("src/main/resources/fabric.mod.json"));
        // The 26.x sources compile directly against 26.2 class and method names
        // and 26.1 was never runtime-tested, so a wider claim would be one the
        // build cannot back.
        assertTrue(modJson.contains("\"minecraft\": \">=26.2 <26.3\""),
                "the 26.x jar must declare 26.2 only");
        assertFalse(modJson.contains(">=26.1"),
                "the untested 26.1 compatibility claim must not come back");
        assertTrue(modJson.contains("\"sodium\": \">=0.8.0\""));
    }

    @Test
    @DisplayName("Describes 26.2 as an OpenGL target")
    void describesOpenGlFirst() throws IOException {
        String modJson = Files.readString(project.resolve("src/main/resources/fabric.mod.json"));
        assertTrue(modJson.contains("OpenGL"),
                "26.2's default renderer is OpenGL and the description must say so");
    }

    @Test
    @DisplayName("Detection uses DeviceInfo with a guarded OpenGL fallback")
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
    @DisplayName("A device that reports no backend name resolves to OpenGL, not Unknown")
    void detectorAssumesOpenGlByDefault() throws IOException {
        String detector = Files.readString(project.resolve(
                "src/main/java/com/intelium/IntelGpuDetector.java"));
        assertTrue(detector.contains("resolveDefaultOpenGl"),
                "26.2 defaults to OpenGL, so a missing backend name must not disable "
                        + "the OpenGL tuning path");
    }

    @Test
    @DisplayName("Sodium worker hook passes the detected render backend")
    void workerHookIsBackendAware() throws IOException {
        String mixin = Files.readString(project.resolve(
                "src/main/java/com/intelium/mixin/MixinChunkBuilder.java"));
        assertTrue(mixin.contains("Intelium.DETECTED_BACKEND"));
    }

    /**
     * Joins Java source-level string concatenation so a descriptor split across
     * lines for readability still matches as one literal.
     */
    private static String joinLiterals(String source) {
        return source.replaceAll("\"\\s*\\+\\s*\"", "");
    }

    @Test
    @DisplayName("Every vanilla hook is gated on a full method descriptor")
    void clientHooksAreDescriptorGated() throws IOException {
        String plugin = joinLiterals(Files.readString(project.resolve(
                "src/client/java/com/intelium/mixin/client/InteliumClientMixinPlugin.java")));
        // Every descriptor below was verified against the resolved 26.2 client
        // jar with javap -s. Matching the name alone is what used to let a hook
        // apply, match nothing and pass silently under require = 0.
        assertTrue(plugin.contains(
                "(Lnet/minecraft/world/entity/Entity;"
                        + "Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"),
                "EntityRenderer.shouldRender must be descriptor-gated");
        assertTrue(plugin.contains(
                "(Lnet/minecraft/world/level/block/entity/BlockEntity;"
                        + "Lnet/minecraft/world/phys/Vec3;)Z"),
                "BlockEntityRenderer.shouldRender must be descriptor-gated");
        assertTrue(plugin.contains("(Lnet/minecraft/client/particle/Particle;)V"),
                "ParticleEngine.add must be descriptor-gated");
        assertTrue(plugin.contains("\"(Z)V\""),
                "Minecraft.renderFrame(Z)V must be descriptor-gated");
    }

    @Test
    @DisplayName("The Sodium worker hook is gated on getThreadCount()I")
    void sodiumHookIsDescriptorGated() throws IOException {
        String plugin = Files.readString(project.resolve(
                "src/main/java/com/intelium/mixin/InteliumMixinPlugin.java"));
        assertTrue(plugin.contains("\"getThreadCount\""));
        assertTrue(plugin.contains("\"()I\""));
    }

    @Test
    @DisplayName("Release workflow publishes both 1.3.4 jars and checksums")
    void releaseWorkflowMatches() throws IOException {
        String workflow = Files.readString(project.resolve("../.github/workflows/release.yml")
                .normalize());
        assertTrue(workflow.contains("INTELIUM_VERSION: \"1.3.4\""));
        assertTrue(workflow.contains("Intelium-v1.3.4-1.21.11.jar"));
        assertTrue(workflow.contains("Intelium-v1.3.4-26.2.jar"));
        assertTrue(workflow.contains(".sha256"));
        assertTrue(workflow.contains(".sha512"));
    }
}
