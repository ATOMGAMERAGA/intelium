package com.intelium.resources;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates {@code intelium.client.mixins.json} - the Render Budget Engine's
 * hooks into vanilla rendering.
 *
 * <p>These live in the <em>client</em> source set, not next to the Sodium hooks:
 * with split environment source sets, {@code src/main} compiles against common
 * Minecraft only, so anything touching {@code net.minecraft.client} has to be
 * over here, and a source set needs its own mixin config to get its own refmap.
 */
@DisplayName("intelium.client.mixins.json validation")
class ClientMixinConfigTest {

    private static final String[] DECLARED = {
            "MixinBlockEntityRenderer",
            "MixinEntityRenderer",
            "MixinParticleManager",
    };

    private static JsonObject mixins;

    private static Path configPath() {
        return TestPaths.projectRoot().resolve("src/client/resources/intelium.client.mixins.json");
    }

    private static Path sourcePath(String className) {
        return TestPaths.projectRoot()
                .resolve("src/client/java/com/intelium/mixin/client")
                .resolve(className + ".java");
    }

    @BeforeAll
    static void load() throws IOException {
        mixins = JsonParser.parseString(Files.readString(configPath())).getAsJsonObject();
    }

    @Test
    @DisplayName("File exists")
    void fileExists() {
        assertTrue(Files.exists(configPath()));
    }

    @Test
    @DisplayName("required is false - a budget that cannot attach must not take the game with it")
    void notRequired() {
        // Every hook in this config is an optimization, never a prerequisite.
        // If a future Minecraft reshapes one of the methods badly enough that
        // Mixin cannot apply the hook at all, the right outcome is one lost
        // budget and a line in the log, not a game that refuses to start.
        assertFalse(mixins.get("required").getAsBoolean());
    }

    @Test
    @DisplayName("package is com.intelium.mixin.client")
    void packageName() {
        assertEquals("com.intelium.mixin.client", mixins.get("package").getAsString());
    }

    @Test
    @DisplayName("compatibilityLevel is JAVA_21")
    void compatibilityLevel() {
        assertEquals("JAVA_21", mixins.get("compatibilityLevel").getAsString());
    }

    @Test
    @DisplayName("Every Render Budget Engine hook is declared, and nothing else")
    void declaredMixins() {
        assertEquals(List.of(DECLARED), clientList());
    }

    @Test
    @DisplayName("injectors.defaultRequire is 0 - a moved target never crashes the game")
    void defaultRequireZero() {
        assertEquals(0, mixins.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
    }

    @Test
    @DisplayName("server-side mixins array is absent or empty")
    void noServerMixins() {
        if (mixins.has("mixins")) {
            assertEquals(0, mixins.getAsJsonArray("mixins").size());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "MixinBlockEntityRenderer", "MixinEntityRenderer", "MixinParticleManager"})
    @DisplayName("Each declared mixin source exists and declares the right package")
    void sourceExists(String className) throws IOException {
        Path p = sourcePath(className);
        assertTrue(Files.exists(p), "missing mixin source: " + p);
        assertTrue(Files.readString(p).startsWith("package com.intelium.mixin.client;"),
                className + " must start with 'package com.intelium.mixin.client;'");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "MixinBlockEntityRenderer", "MixinEntityRenderer", "MixinParticleManager"})
    @DisplayName("Each hook injects softly, so a moved target no-ops instead of crashing")
    void injectsSoftly(String className) throws IOException {
        String src = Files.readString(sourcePath(className));
        assertTrue(src.contains("@Mixin"), className + " must use @Mixin");
        assertTrue(src.contains("require = 0"),
                className + " must inject with require = 0");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "MixinBlockEntityRenderer", "MixinEntityRenderer", "MixinParticleManager"})
    @DisplayName("Each hook is inert until the budget engine says otherwise")
    void checksTheBudgetFirst(String className) throws IOException {
        String src = Files.readString(sourcePath(className));
        assertTrue(src.contains("RenderBudget."),
                className + " must consult RenderBudget before culling anything");
    }

    @Test
    @DisplayName("The vanilla hooks are remapped (unlike the Sodium ones)")
    void vanillaHooksAreRemapped() throws IOException {
        for (String className : DECLARED) {
            assertFalse(Files.readString(sourcePath(className)).contains("remap = false"),
                    className + " targets vanilla, which IS remapped on 1.21.11");
        }
    }

    @Test
    @DisplayName("fabric.mod.json references this config for the client environment")
    void referencedFromModJson() throws IOException {
        JsonObject modJson = JsonParser.parseString(
                Files.readString(TestPaths.fabricModJson())).getAsJsonObject();
        boolean found = false;
        for (JsonElement el : modJson.getAsJsonArray("mixins")) {
            JsonObject m = el.getAsJsonObject();
            if ("intelium.client.mixins.json".equals(m.get("config").getAsString())) {
                found = true;
                assertEquals("client", m.get("environment").getAsString());
            }
        }
        assertTrue(found, "fabric.mod.json must reference intelium.client.mixins.json");
    }

    private static List<String> clientList() {
        JsonArray a = mixins.getAsJsonArray("client");
        List<String> out = new ArrayList<>();
        for (JsonElement el : a) out.add(el.getAsString());
        return out;
    }
}
