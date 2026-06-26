package com.intelium.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * These tests exercise Gson round-tripping against InteliumConfig
 * without actually invoking InteliumConfigIO (which needs a Fabric runtime).
 */
@DisplayName("InteliumConfig <-> JSON round-trip")
class ConfigRoundtripTest {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Test
    @DisplayName("Default config serializes to valid JSON")
    void serializeDefault() {
        String json = GSON.toJson(new InteliumConfig());
        assertNotNull(json);
        assertFalse(json.isEmpty());
        assertDoesNotThrow(() -> JsonParser.parseString(json));
    }

    @Test
    @DisplayName("Default config round-trips identically")
    void roundtripDefault() {
        InteliumConfig original = new InteliumConfig();
        String json = GSON.toJson(original);
        InteliumConfig parsed = GSON.fromJson(json, InteliumConfig.class);
        assertEquals(original.enabled, parsed.enabled);
        assertEquals(original.chunkBuildWorkers, parsed.chunkBuildWorkers);
    }

    @Test
    @DisplayName("Modified config round-trips identically")
    void roundtripModified() {
        InteliumConfig original = new InteliumConfig();
        original.enabled = false;
        original.chunkBuildWorkers = 4;
        original.overlayEnabled = true;
        original.overlayX = 120;
        original.overlayY = 48;

        String json = GSON.toJson(original);
        InteliumConfig parsed = GSON.fromJson(json, InteliumConfig.class);

        assertFalse(parsed.enabled);
        assertEquals(4, parsed.chunkBuildWorkers);
        assertTrue(parsed.overlayEnabled);
        assertEquals(120, parsed.overlayX);
        assertEquals(48, parsed.overlayY);
    }

    @Test
    @DisplayName("Serialized JSON contains all field names")
    void serializedContainsAllKeys() {
        String json = GSON.toJson(new InteliumConfig());
        for (String key : new String[]{
                "enabled", "chunkBuildWorkers", "overlayEnabled", "overlayX", "overlayY"}) {
            assertTrue(json.contains(key), "missing key: " + key);
        }
    }

    @Test
    @DisplayName("Missing keys fall back to Java defaults")
    void missingKeysFallback() {
        InteliumConfig parsed = GSON.fromJson("{}", InteliumConfig.class);
        assertNotNull(parsed);
        assertTrue(parsed.enabled);
        assertEquals(0, parsed.chunkBuildWorkers);
    }

    @Test
    @DisplayName("Unknown JSON fields are ignored")
    void unknownFieldsIgnored() {
        InteliumConfig parsed = GSON.fromJson(
                "{\"enabled\":false,\"future_field\":\"x\"}", InteliumConfig.class);
        assertFalse(parsed.enabled);
    }

    @Test
    @DisplayName("Legacy config with removed placebo fields still parses")
    void legacyFieldsIgnored() {
        // Configs written by older Intelium builds had these keys; they must be
        // silently ignored, not cause a parse failure.
        InteliumConfig parsed = GSON.fromJson(
                "{\"enabled\":true,\"drawCallBatching\":true,\"persistentBuffers\":true,"
                        + "\"aggressiveCulling\":true,\"indirectBufferBytes\":0,"
                        + "\"chunkBuildWorkers\":-1}", InteliumConfig.class);
        assertNotNull(parsed);
        assertTrue(parsed.enabled);
        assertEquals(-1, parsed.chunkBuildWorkers);
    }

    @Test
    @DisplayName("Integer chunkBuildWorkers serialized correctly")
    void serializesInt() {
        InteliumConfig c = new InteliumConfig();
        c.chunkBuildWorkers = 5;
        JsonObject o = JsonParser.parseString(GSON.toJson(c)).getAsJsonObject();
        assertEquals(5, o.get("chunkBuildWorkers").getAsInt());
    }

    @Test
    @DisplayName("Gson is null-tolerant for null JSON parse")
    void nullJsonIsNull() {
        assertNull(GSON.fromJson("null", InteliumConfig.class));
    }

    @Test
    @DisplayName("Pretty-printed JSON is multi-line")
    void prettyMultiline() {
        String json = GSON.toJson(new InteliumConfig());
        assertTrue(json.contains("\n"));
    }
}
