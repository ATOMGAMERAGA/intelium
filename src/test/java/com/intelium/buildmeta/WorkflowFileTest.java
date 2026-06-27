package com.intelium.buildmeta;

import com.intelium.resources.TestPaths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GitHub Actions release workflow validation")
class WorkflowFileTest {

    private static Path workflow;
    private static String content;

    @BeforeAll
    static void load() throws IOException {
        workflow = TestPaths.projectRoot().resolve(".github/workflows/release.yml");
        if (Files.exists(workflow)) {
            content = Files.readString(workflow);
        }
    }

    @Test
    @DisplayName("Workflow file exists")
    void exists() {
        assertTrue(Files.exists(workflow),
                "Expected .github/workflows/release.yml to exist");
    }

    @Test
    @DisplayName("Workflow declares 'name:' line")
    void hasName() {
        assertNotNull(content);
        assertTrue(content.contains("name:"));
    }

    @Test
    @DisplayName("Workflow targets ubuntu-latest")
    void usesUbuntu() {
        assertTrue(content.contains("ubuntu-latest"));
    }

    @Test
    @DisplayName("Workflow sets up Java 21")
    void setsUpJava21() {
        assertTrue(content.contains("java-version: 21")
                || content.contains("java-version: '21'")
                || content.contains("java-version: \"21\""));
    }

    @Test
    @DisplayName("Workflow runs the Gradle test task")
    void runsTests() {
        assertTrue(content.contains("./gradlew") && content.contains("test"));
    }

    @Test
    @DisplayName("Workflow publishes the fixed 1.0.1 release")
    void usesFixedVersion() {
        assertTrue(content.contains("VERSION=\"1.0.1\""),
                "release version must be the fixed 1.0.1");
        assertTrue(content.contains("TAG=\"v${VERSION}\"")
                        || content.contains("v1.0.1"),
                "release must be tagged v1.0.1");
        assertTrue(content.contains("prerelease: false"),
                "1.0.1 must be a full release, not a prerelease");
    }

    @Test
    @DisplayName("Workflow signs jar with jarsigner")
    void hasJarsigner() {
        assertTrue(content.contains("jarsigner"));
    }

    @Test
    @DisplayName("Workflow generates GPG detached signature")
    void hasGpgSign() {
        assertTrue(content.contains("gpg") && content.contains("detach-sign"));
    }

    @Test
    @DisplayName("Workflow computes SHA-256 checksum")
    void hasSha256() {
        assertTrue(content.contains("sha256sum") || content.contains("SHA-256"));
    }

    @Test
    @DisplayName("Workflow computes SHA-512 checksum")
    void hasSha512() {
        assertTrue(content.contains("sha512sum") || content.contains("SHA-512"));
    }

    @Test
    @DisplayName("Workflow uploads release artifacts")
    void uploadsRelease() {
        assertTrue(content.contains("softprops/action-gh-release")
                || content.contains("create-release")
                || content.contains("gh release create"));
    }

    @Test
    @DisplayName("Workflow has permissions: contents write")
    void hasContentsWrite() {
        assertTrue(content.contains("contents: write"));
    }

    @Test
    @DisplayName("Workflow runs writeDependencyList")
    void runsDependencyList() {
        assertTrue(content.contains("writeDependencyList")
                || content.contains("dependencies.txt"));
    }

    @Test
    @DisplayName("Workflow passes -PinteliumVersion to gradle")
    void passesVersion() {
        assertTrue(content.contains("-PinteliumVersion"));
    }
}
