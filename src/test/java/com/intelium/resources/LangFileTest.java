package com.intelium.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Language file validation")
class LangFileTest {

    private static JsonObject en;
    private static JsonObject tr;

    @BeforeAll
    static void load() throws IOException {
        en = JsonParser.parseString(Files.readString(TestPaths.langEnUs())).getAsJsonObject();
        tr = JsonParser.parseString(Files.readString(TestPaths.langTrTr())).getAsJsonObject();
    }

    @Test
    @DisplayName("en_us.json exists")
    void enExists() {
        assertTrue(Files.exists(TestPaths.langEnUs()));
    }

    @Test
    @DisplayName("tr_tr.json exists")
    void trExists() {
        assertTrue(Files.exists(TestPaths.langTrTr()));
    }

    @Test
    @DisplayName("en_us.json parses as JSON object")
    void enParses() {
        assertNotNull(en);
    }

    @Test
    @DisplayName("tr_tr.json parses as JSON object")
    void trParses() {
        assertNotNull(tr);
    }

    @Test
    @DisplayName("Both languages have identical key sets")
    void identicalKeys() {
        Set<String> enKeys = new TreeSet<>(en.keySet());
        Set<String> trKeys = new TreeSet<>(tr.keySet());
        assertEquals(enKeys, trKeys,
                "en_us and tr_tr should share the same keys; diff: en - tr = "
                        + diff(enKeys, trKeys) + "; tr - en = " + diff(trKeys, enKeys));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "intelium.options.title",
            "intelium.options.page.general",
            "intelium.options.enable",
            "intelium.options.enable.tooltip",
            "intelium.options.chunk_workers",
            "intelium.options.chunk_workers.tooltip",
            "intelium.options.chunk_workers.auto",
            "intelium.options.supported_gpus",
            "intelium.options.supported_gpus.tooltip",
            "intelium.status.active",
            "intelium.status.unsupported",
            "intelium.status.pending",
            "intelium.gpus.title",
            "intelium.gpus.heading",
            "intelium.gpus.detected",
            "intelium.gpus.detected.pending",
            "intelium.gpus.status.supported",
            "intelium.gpus.status.unsupported",
            "intelium.gpus.scroll",
            "intelium.disabled.nvidia",
            "intelium.disabled.amd",
            "intelium.disabled.unknown_gpu",
            "intelium.disabled.unrecognized_intel",
            "intelium.disabled.too_old",
            "intelium.disabled.sodium_missing",
            "intelium.disabled.incompatible_sodium"
    })
    @DisplayName("Required key exists in en_us.json")
    void enHasKey(String key) {
        assertTrue(en.has(key), "en_us.json missing key: " + key);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "intelium.options.title",
            "intelium.options.page.general",
            "intelium.options.enable",
            "intelium.options.enable.tooltip",
            "intelium.options.chunk_workers",
            "intelium.options.chunk_workers.tooltip",
            "intelium.options.chunk_workers.auto",
            "intelium.options.supported_gpus",
            "intelium.options.supported_gpus.tooltip",
            "intelium.status.active",
            "intelium.status.unsupported",
            "intelium.status.pending",
            "intelium.gpus.title",
            "intelium.gpus.heading",
            "intelium.gpus.detected",
            "intelium.gpus.detected.pending",
            "intelium.gpus.status.supported",
            "intelium.gpus.status.unsupported",
            "intelium.gpus.scroll",
            "intelium.disabled.nvidia",
            "intelium.disabled.amd",
            "intelium.disabled.unknown_gpu",
            "intelium.disabled.unrecognized_intel",
            "intelium.disabled.too_old",
            "intelium.disabled.sodium_missing",
            "intelium.disabled.incompatible_sodium"
    })
    @DisplayName("Required key exists in tr_tr.json")
    void trHasKey(String key) {
        assertTrue(tr.has(key), "tr_tr.json missing key: " + key);
    }

    @Test
    @DisplayName("All en_us values are non-empty strings")
    void enValuesNonEmpty() {
        for (var e : en.entrySet()) {
            String v = e.getValue().getAsString();
            assertNotNull(v);
            assertFalse(v.isBlank(), "empty value in en_us: " + e.getKey());
        }
    }

    @Test
    @DisplayName("All tr_tr values are non-empty strings")
    void trValuesNonEmpty() {
        for (var e : tr.entrySet()) {
            String v = e.getValue().getAsString();
            assertNotNull(v);
            assertFalse(v.isBlank(), "empty value in tr_tr: " + e.getKey());
        }
    }

    @Test
    @DisplayName("en_us has at least 20 keys")
    void enKeyCount() {
        assertTrue(en.size() >= 20);
    }

    @Test
    @DisplayName("tr_tr has at least 20 keys")
    void trKeyCount() {
        assertTrue(tr.size() >= 20);
    }

    @Test
    @DisplayName("en and tr keys count matches")
    void keyCountsMatch() {
        assertEquals(en.size(), tr.size());
    }

    @Test
    @DisplayName("All keys start with 'intelium.'")
    void allKeysNamespaced() {
        for (String key : en.keySet()) {
            assertTrue(key.startsWith("intelium."),
                    "key does not start with 'intelium.': " + key);
        }
    }

    @Test
    @DisplayName("Tooltip keys have non-tooltip parents")
    void tooltipKeysHaveParents() {
        for (String key : en.keySet()) {
            if (key.endsWith(".tooltip")) {
                String parent = key.substring(0, key.length() - ".tooltip".length());
                assertTrue(en.has(parent),
                        "tooltip without parent: " + key);
            }
        }
    }

    @Test
    @DisplayName("Turkish translation differs from English for the main switch")
    void turkishDiffers() {
        assertNotEquals(en.get("intelium.options.enable").getAsString(),
                tr.get("intelium.options.enable").getAsString());
    }

    @Test
    @DisplayName("en_us file is valid UTF-8")
    void enUtf8() throws IOException {
        byte[] data = Files.readAllBytes(TestPaths.langEnUs());
        assertDoesNotThrow(() -> new String(data, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("tr_tr file is valid UTF-8")
    void trUtf8() throws IOException {
        byte[] data = Files.readAllBytes(TestPaths.langTrTr());
        assertDoesNotThrow(() -> new String(data, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Turkish file contains a Turkish-specific character")
    void turkishHasTurkishChar() throws IOException {
        String src = Files.readString(TestPaths.langTrTr());
        // Look for at least one Turkish-specific letter
        assertTrue(src.indexOf((char) 0x0131) >= 0 // ı
                        || src.indexOf((char) 0x015F) >= 0 // ş
                        || src.indexOf((char) 0x011F) >= 0 // ğ
                        || src.indexOf((char) 0x00FC) >= 0 // ü
                        || src.indexOf((char) 0x00E7) >= 0, // ç
                "tr_tr.json should contain at least one Turkish-specific letter");
    }

    @Test
    @DisplayName("Both files end with newline")
    void newlineTerminated() throws IOException {
        assertTrue(Files.readString(TestPaths.langEnUs()).endsWith("\n"));
        assertTrue(Files.readString(TestPaths.langTrTr()).endsWith("\n"));
    }

    @Test
    @DisplayName("intelium.status.unsupported contains two %s placeholders in both languages")
    void unsupportedStatusHasPlaceholders() {
        assertTrue(en.get("intelium.status.unsupported").getAsString().contains("%s"));
        assertTrue(tr.get("intelium.status.unsupported").getAsString().contains("%s"));
        assertTrue(en.get("intelium.gpus.detected").getAsString().contains("%s"));
    }

    private static <T> Set<T> diff(Set<T> a, Set<T> b) {
        Set<T> out = new HashSet<>(a);
        out.removeAll(b);
        return out;
    }
}
