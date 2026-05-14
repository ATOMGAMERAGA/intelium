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
        assertEquals(original.drawCallBatching, parsed.drawCallBatching);
        assertEquals(original.persistentBuffers, parsed.persistentBuffers);
        assertEquals(original.chunkBuildWorkers, parsed.chunkBuildWorkers);
        assertEquals(original.aggressiveCulling, parsed.aggressiveCulling);
        assertEquals(original.indirectBufferBytes, parsed.indirectBufferBytes);
    }

    @Test
    @DisplayName("Modified config round-trips identically")
    void roundtripModified() {
        InteliumConfig original = new InteliumConfig();
        original.enabled = false;
        original.drawCallBatching = false;
        original.persistentBuffers = false;
        original.chunkBuildWorkers = 4;
        original.aggressiveCulling = false;
        original.indirectBufferBytes = 8 * 1024 * 1024;

        String json = GSON.toJson(original);
        InteliumConfig parsed = GSON.fromJson(json, InteliumConfig.class);

        assertFalse(parsed.enabled);
        assertFalse(parsed.drawCallBatching);
        assertFalse(parsed.persistentBuffers);
        assertEquals(4, parsed.chunkBuildWorkers);
        assertFalse(parsed.aggressiveCulling);
        assertEquals(8 * 1024 * 1024, parsed.indirectBufferBytes);
    }

    @Test
    @DisplayName("Serialized JSON contains all field names")
    void serializedContainsAllKeys() {
        String json = GSON.toJson(new InteliumConfig());
        for (String key : new String[]{
                "enabled", "drawCallBatching", "persistentBuffers",
                "chunkBuildWorkers", "aggressiveCulling", "indirectBufferBytes"
        }) {
            assertTrue(json.contains(key), "missing key: " + key);
        }
    }

    @Test
    @DisplayName("Missing keys fall back to Java defaults (or 0/false)")
    void missingKeysFallback() {
        // Gson behaviour: missing fields keep their Java defaults.
        InteliumConfig parsed = GSON.fromJson("{}", InteliumConfig.class);
        assertNotNull(parsed);
        assertTrue(parsed.enabled);
        assertTrue(parsed.drawCallBatching);
        assertTrue(parsed.persistentBuffers);
        assertEquals(-1, parsed.chunkBuildWorkers);
        assertTrue(parsed.aggressiveCulling);
        assertEquals(0, parsed.indirectBufferBytes);
    }

    @Test
    @DisplayName("Empty / null JSON results in fallback or null")
    void emptyOrNull() {
        InteliumConfig fromEmpty = GSON.fromJson("{}", InteliumConfig.class);
        assertNotNull(fromEmpty);
    }

    @Test
    @DisplayName("Unknown JSON fields are ignored")
    void unknownFieldsIgnored() {
        InteliumConfig parsed = GSON.fromJson(
                "{\"enabled\":false,\"future_field\":\"x\"}", InteliumConfig.class);
        assertFalse(parsed.enabled);
    }

    @Test
    @DisplayName("Booleans serialized as true/false")
    void serializesBooleansCorrectly() {
        String json = GSON.toJson(new InteliumConfig());
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(o.get("enabled").getAsBoolean());
        assertTrue(o.get("drawCallBatching").getAsBoolean());
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
    @DisplayName("Mutated then re-parsed: chunkBuildWorkers preserved")
    void mutatedWorkers() {
        InteliumConfig c = new InteliumConfig();
        c.chunkBuildWorkers = 6;
        InteliumConfig back = GSON.fromJson(GSON.toJson(c), InteliumConfig.class);
        assertEquals(6, back.chunkBuildWorkers);
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
