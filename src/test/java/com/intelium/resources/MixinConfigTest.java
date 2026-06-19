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
    @DisplayName("client mixins array contains MixinRenderSectionManager")
    void hasRenderSectionManager() {
        assertTrue(clientList().contains("MixinRenderSectionManager"));
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
    @DisplayName("client mixins array contains MixinDefaultChunkRenderer")
    void hasDefaultChunkRenderer() {
        assertTrue(clientList().contains("MixinDefaultChunkRenderer"));
    }

    @Test
    @DisplayName("client mixins array has exactly 4 entries")
    void exactlyFourMixins() {
        assertEquals(4, clientList().size());
    }

    @Test
    @DisplayName("injectors.defaultRequire is 0 (don't crash on missing methods)")
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
            "MixinRenderSectionManager",
            "MixinSodiumWorldRenderer",
            "MixinChunkBuilder",
            "MixinDefaultChunkRenderer"
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
            "MixinRenderSectionManager",
            "MixinSodiumWorldRenderer",
            "MixinChunkBuilder",
            "MixinDefaultChunkRenderer"
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
            "MixinRenderSectionManager",
            "MixinSodiumWorldRenderer",
            "MixinChunkBuilder",
            "MixinDefaultChunkRenderer"
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
            "MixinRenderSectionManager",
            "MixinSodiumWorldRenderer",
            "MixinChunkBuilder",
            "MixinDefaultChunkRenderer"
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

    @ParameterizedTest
    @ValueSource(strings = {
            "MixinRenderSectionManager",
            "MixinSodiumWorldRenderer",
            "MixinChunkBuilder",
            "MixinDefaultChunkRenderer"
    })
    @DisplayName("Each mixin short-circuits when Intelium is disabled")
    void mixinShortCircuits(String className) throws IOException {
        Path p = TestPaths.projectRoot()
                .resolve("src/main/java/com/intelium/mixin")
                .resolve(className + ".java");
        String src = Files.readString(p);
        // SodiumWorldRenderer is the detection hook itself, it doesn't short-circuit;
        // every other mixin must check IS_ENABLED and IS_COMPATIBLE.
        if (className.equals("MixinSodiumWorldRenderer")) return;
        assertTrue(src.contains("IS_ENABLED") && src.contains("IS_COMPATIBLE"),
                className + " must check IS_ENABLED && IS_COMPATIBLE");
    }

    @Test
    @DisplayName("MixinChunkBuilder hooks the real getThreadCount source, not a phantom ctor arg")
    void chunkBuilderHooksThreadCount() throws IOException {
        Path p = TestPaths.projectRoot()
                .resolve("src/main/java/com/intelium/mixin/MixinChunkBuilder.java");
        String src = Files.readString(p);
        // Sodium 0.8's ChunkBuilder constructor takes (ClientWorld, ChunkVertexType) -
        // there is NO int worker-count argument. The thread count comes from the
        // static getThreadCount() method. A @ModifyVariable on the constructor would
        // silently match nothing (the original, dead, behaviour).
        assertTrue(src.contains("getThreadCount"),
                "MixinChunkBuilder must hook ChunkBuilder.getThreadCount()");
        assertFalse(src.contains("@ModifyVariable"),
                "MixinChunkBuilder must not rely on a non-existent constructor int argument");
    }

    private static java.util.List<String> clientList() {
        JsonArray a = mixins.getAsJsonArray("client");
        java.util.List<String> out = new java.util.ArrayList<>();
        for (JsonElement el : a) out.add(el.getAsString());
        return out;
    }
}
