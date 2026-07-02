package com.intelium.buildmeta;

import com.intelium.resources.TestPaths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        workflow = TestPaths.releaseWorkflow();
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
    @DisplayName("Workflow builds both Java baselines (21 for 1.21.11, 25 for 26.x)")
    void setsUpBothJavas() {
        assertTrue(content.contains("'21'") || content.contains("java-version: 21"),
                "must build the 1.21.11 jar on Java 21");
        assertTrue(content.contains("'25'") || content.contains("java-version: 25"),
                "must build the 26.x jar on Java 25");
    }

    @Test
    @DisplayName("Workflow builds both targets via their gradlew")
    void buildsBothTargets() {
        assertTrue(content.contains("./${{ matrix.target }}/gradlew")
                        || (content.contains("mc1.21.11") && content.contains("mc26")),
                "must build both mc1.21.11 and mc26");
        assertTrue(content.contains("build"), "must run the gradle build task");
    }

    @Test
    @DisplayName("Workflow publishes the fixed 1.2.1 release")
    void usesFixedVersion() {
        assertTrue(content.contains("1.2.1"), "release version must be 1.2.1");
        assertTrue(content.contains("v${{ env.INTELIUM_VERSION }}") || content.contains("v1.2.1"),
                "release must be tagged v1.2.1");
        assertTrue(content.contains("prerelease: false"),
                "1.2.1 must be a full release, not a prerelease");
    }

    @Test
    @DisplayName("Release attaches both per-version jars")
    void attachesBothJars() {
        assertTrue(content.contains("Intelium-v1.2.1-1.21.11.jar")
                        || content.contains("-1.21.11.jar"),
                "must attach the 1.21.11 jar");
        assertTrue(content.contains("Intelium-v1.2.1-26.x.jar")
                        || content.contains("-26.x.jar"),
                "must attach the 26.x jar");
    }

    @Test
    @DisplayName("Workflow uploads a GitHub release")
    void uploadsRelease() {
        assertTrue(content.contains("softprops/action-gh-release")
                || content.contains("gh release create"));
    }

    @Test
    @DisplayName("Release body carries a copyable Modrinth changelog (EN + TR)")
    void hasCopyableChangelog() {
        assertTrue(content.contains("## English"), "changelog must have an English section");
        assertTrue(content.contains("## Türkçe"), "changelog must have a Turkish section");
        assertTrue(content.contains("```markdown") || content.contains("````markdown"),
                "changelog must be in a copyable markdown code block");
    }

    @Test
    @DisplayName("Workflow has permissions: contents write")
    void hasContentsWrite() {
        assertTrue(content.contains("contents: write"));
    }

    @Test
    @DisplayName("Workflow passes -PinteliumVersion to gradle")
    void passesVersion() {
        assertTrue(content.contains("-PinteliumVersion"));
    }
}
