package com.intelium.resources;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Helper to resolve paths under the project root from within JUnit tests.
 * The Gradle test task passes `project.rootDir` as a system property.
 */
public final class TestPaths {

    private TestPaths() {}

    public static Path projectRoot() {
        String prop = System.getProperty("project.rootDir");
        if (prop != null && !prop.isEmpty()) {
            return Paths.get(prop);
        }
        // Fallback: walk upwards looking for settings.gradle
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.exists(p.resolve("settings.gradle"))
                    || Files.exists(p.resolve("settings.gradle.kts"))) {
                return p;
            }
            p = p.getParent();
        }
        throw new IllegalStateException("Cannot find project root");
    }

    public static Path resources() {
        return projectRoot().resolve("src/main/resources");
    }

    public static Path fabricModJson() {
        return resources().resolve("fabric.mod.json");
    }

    public static Path mixinsJson() {
        return resources().resolve("intelium.mixins.json");
    }

    public static Path langEnUs() {
        return resources().resolve("assets/intelium/lang/en_us.json");
    }

    public static Path langTrTr() {
        return resources().resolve("assets/intelium/lang/tr_tr.json");
    }

    public static Path iconMain() {
        return resources().resolve("assets/intelium/icon.png");
    }

    public static Path iconGui() {
        return resources().resolve("assets/intelium/textures/gui/icon.png");
    }

    public static Path gradleProperties() {
        return projectRoot().resolve("gradle.properties");
    }

    public static Path buildGradle() {
        return projectRoot().resolve("build.gradle");
    }

    public static Path settingsGradle() {
        return projectRoot().resolve("settings.gradle");
    }
}
