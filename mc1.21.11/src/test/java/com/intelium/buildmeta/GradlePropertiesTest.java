package com.intelium.buildmeta;

import com.intelium.resources.TestPaths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("gradle.properties validation")
class GradlePropertiesTest {

    private static Properties props;

    @BeforeAll
    static void load() throws IOException {
        props = new Properties();
        try (InputStream in = Files.newInputStream(TestPaths.gradleProperties())) {
            props.load(in);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "mod_version", "maven_group", "archive_base_name",
            "minecraft_version", "yarn_mappings", "loader_version",
            "fabric_version", "sodium_version"
    })
    @DisplayName("Required property is present")
    void hasProperty(String key) {
        assertNotNull(props.getProperty(key), "missing: " + key);
        assertFalse(props.getProperty(key).isBlank(), "blank: " + key);
    }

    @Test
    @DisplayName("maven_group is com.intelium")
    void mavenGroup() {
        assertEquals("com.intelium", props.getProperty("maven_group"));
    }

    @Test
    @DisplayName("archive_base_name is intelium")
    void archiveName() {
        assertEquals("intelium", props.getProperty("archive_base_name"));
    }

    @Test
    @DisplayName("minecraft_version is 1.21.11")
    void mcVersion() {
        assertEquals("1.21.11", props.getProperty("minecraft_version"));
    }

    @Test
    @DisplayName("loader_version is 0.18.x or later")
    void loaderVersion() {
        String v = props.getProperty("loader_version");
        assertTrue(v.matches("\\d+\\.\\d+\\.\\d+.*"),
                "loader_version must look like semver, was: " + v);
    }

    @Test
    @DisplayName("yarn_mappings starts with minecraft_version")
    void yarnMatchesMc() {
        String yarn = props.getProperty("yarn_mappings");
        String mc = props.getProperty("minecraft_version");
        assertTrue(yarn.startsWith(mc),
                "yarn_mappings must start with mc version, was: " + yarn);
    }

    @Test
    @DisplayName("fabric_version ends with +<minecraft_version>")
    void fabricEndsWithMc() {
        String fabric = props.getProperty("fabric_version");
        String mc = props.getProperty("minecraft_version");
        assertTrue(fabric.endsWith("+" + mc),
                "fabric_version must end with +<mc_version>, was: " + fabric);
    }

    @Test
    @DisplayName("sodium_version ends with +mc<minecraft_version>")
    void sodiumEndsWithMc() {
        String sodium = props.getProperty("sodium_version");
        String mc = props.getProperty("minecraft_version");
        assertTrue(sodium.endsWith("+mc" + mc),
                "sodium_version must end with +mc<mc_version>, was: " + sodium);
    }

    @Test
    @DisplayName("sodium_version is 0.8.x")
    void sodiumIs08x() {
        String sodium = props.getProperty("sodium_version");
        assertTrue(sodium.startsWith("0.8."),
                "sodium_version must be 0.8.x, was: " + sodium);
    }

    @Test
    @DisplayName("mod_version is valid semver-ish (starts with digit-dot)")
    void modVersionFormat() {
        String v = props.getProperty("mod_version");
        assertTrue(v.matches("\\d+\\.\\d+\\.\\d+.*"),
                "mod_version must be semver, was: " + v);
    }

    @Test
    @DisplayName("Properties file ends with newline")
    void endsWithNewline() throws IOException {
        String src = Files.readString(TestPaths.gradleProperties());
        assertTrue(src.endsWith("\n"));
    }

    @Test
    @DisplayName("No tabs in gradle.properties")
    void noTabs() throws IOException {
        String src = Files.readString(TestPaths.gradleProperties());
        assertFalse(src.contains("\t"));
    }
}
