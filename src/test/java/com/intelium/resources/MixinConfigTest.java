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

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("intelium.mixins.json validation")
class MixinConfigTest {

    private static JsonObject mixins;

    @BeforeAll
    static void load() throws IOException {
        mixins = JsonParser.parseString(
                Files.readString(TestPaths.mixinsJson())
        ).getAsJsonObject();
    }

    @Test
    @DisplayName("File exists")
    void fileExists() {
        assertTrue(Files.exists(TestPaths.mixinsJson()));
    }

    @Test
    @DisplayName("required is true")
    void required() {
        assertTrue(mixins.get("required").getAsBoolean());
    }

    @Test
    @DisplayName("minVersion is at least 0.8.5")
    void minVersion() {
        String v = mixins.get("minVersion").getAsString();
        assertTrue(v.startsWith("0.8") || v.startsWith("0.9") || v.startsWith("1"));
    }

    @Test
    @DisplayName("package is com.intelium.mixin")
    void packageName() {
        assertEquals("com.intelium.mixin", mixins.get("package").getAsString());
    }

    @Test
    @DisplayName("compatibilityLevel is JAVA_21")
    void compatibilityLevel() {
        assertEquals("JAVA_21", mixins.get("compatibilityLevel").getAsString());
    }

    @Test
    @DisplayName("client mixins array contains MixinSodiumWorldRenderer")
    void hasSodiumWorldRenderer() {
        assertTrue(clientList().contains("MixinSodiumWorldRenderer"));
    }

    @Test
    @DisplayName("client mixins array contains MixinChunkBuilder")
    void hasChunkBuilder() {
        assertTrue(clientList().contains("MixinChunkBuilder"));
    }

    @Test
    @DisplayName("Placebo mixins were removed")
    void noPlaceboMixins() {
        assertFalse(clientList().contains("MixinDefaultChunkRenderer"));
        assertFalse(clientList().contains("MixinRenderSectionManager"));
    }

    @Test
    @DisplayName("client mixins array has exactly 2 entries")
    void exactlyTwoMixins() {
        assertEquals(2, clientList().size());
    }

    @Test
    @DisplayName("injectors.defaultRequire is 1 (targets are verified; fail loudly if absent)")
    void defaultRequireOne() {
        assertEquals(1, mixins.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
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
            "MixinSodiumWorldRenderer",
            "MixinChunkBuilder"
    })
    @DisplayName("Each declared mixin class file exists on disk")
    void mixinFileExists(String className) {
        Path p = TestPaths.projectRoot()
                .resolve("src/main/java/com/intelium/mixin")
                .resolve(className + ".java");
        assertTrue(Files.exists(p), "missing mixin source: " + p);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "MixinSodiumWorldRenderer",
            "MixinChunkBuilder"
    })
    @DisplayName("Each declared mixin file declares correct package")
    void mixinPackageCorrect(String className) throws IOException {
        Path p = TestPaths.projectRoot()
                .resolve("src/main/java/com/intelium/mixin")
                .resolve(className + ".java");
        String src = Files.readString(p);
        assertTrue(src.startsWith("package com.intelium.mixin;"),
                className + " must start with 'package com.intelium.mixin;'");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "MixinSodiumWorldRenderer",
            "MixinChunkBuilder"
    })
    @DisplayName("Each mixin uses @Mixin annotation")
    void mixinHasAnnotation(String className) throws IOException {
        Path p = TestPaths.projectRoot()
                .resolve("src/main/java/com/intelium/mixin")
                .resolve(className + ".java");
        String src = Files.readString(p);
        assertTrue(src.contains("@Mixin"), className + " must use @Mixin annotation");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "MixinSodiumWorldRenderer",
            "MixinChunkBuilder"
    })
    @DisplayName("Each mixin sets remap = false (Sodium classes are not remapped)")
    void mixinRemapFalse(String className) throws IOException {
        Path p = TestPaths.projectRoot()
                .resolve("src/main/java/com/intelium/mixin")
                .resolve(className + ".java");
        String src = Files.readString(p);
        assertTrue(src.contains("remap = false"),
                className + " must include remap = false in its @Mixin annotation");
    }

    @Test
    @DisplayName("MixinChunkBuilder short-circuits when Intelium is disabled")
    void chunkBuilderShortCircuits() throws IOException {
        Path p = TestPaths.projectRoot()
                .resolve("src/main/java/com/intelium/mixin/MixinChunkBuilder.java");
        String src = Files.readString(p);
        assertTrue(src.contains("IS_ENABLED") && src.contains("IS_COMPATIBLE"),
                "MixinChunkBuilder must check IS_ENABLED && IS_COMPATIBLE");
    }

    private static java.util.List<String> clientList() {
        JsonArray a = mixins.getAsJsonArray("client");
        java.util.List<String> out = new java.util.ArrayList<>();
        for (JsonElement el : a) out.add(el.getAsString());
        return out;
    }
}
