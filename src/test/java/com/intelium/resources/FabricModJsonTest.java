package com.intelium.resources;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("fabric.mod.json validation")
class FabricModJsonTest {

    private static String raw;
    private static JsonObject json;

    @BeforeAll
    static void load() throws IOException {
        raw = Files.readString(TestPaths.fabricModJson());
        json = JsonParser.parseString(raw).getAsJsonObject();
    }

    @Test
    @DisplayName("File exists")
    void fileExists() {
        assertTrue(Files.exists(TestPaths.fabricModJson()));
    }

    @Test
    @DisplayName("File is valid JSON")
    void isValidJson() {
        assertNotNull(json);
    }

    @Test
    @DisplayName("schemaVersion equals 1")
    void schemaVersion() {
        assertEquals(1, json.get("schemaVersion").getAsInt());
    }

    @Test
    @DisplayName("id equals 'intelium'")
    void id() {
        assertEquals("intelium", json.get("id").getAsString());
    }

    @Test
    @DisplayName("version is the literal placeholder ${version} (expanded at processResources)")
    void versionIsPlaceholder() {
        // Source file holds the placeholder; Gradle expands it. We must not check
        // an exact version here, but we must make sure the placeholder is present.
        assertEquals("${version}", json.get("version").getAsString());
    }

    @Test
    @DisplayName("name equals 'Intelium'")
    void name() {
        assertEquals("Intelium", json.get("name").getAsString());
    }

    @Test
    @DisplayName("environment equals 'client'")
    void environment() {
        assertEquals("client", json.get("environment").getAsString());
    }

    @Test
    @DisplayName("license is LGPL-3.0-only")
    void license() {
        assertEquals("LGPL-3.0-only", json.get("license").getAsString());
    }

    @Test
    @DisplayName("description is non-empty")
    void descriptionPresent() {
        String d = json.get("description").getAsString();
        assertNotNull(d);
        assertFalse(d.isBlank());
    }

    @Test
    @DisplayName("icon path is set to assets/intelium/icon.png")
    void iconPath() {
        assertEquals("assets/intelium/icon.png", json.get("icon").getAsString());
    }

    @Test
    @DisplayName("authors is a non-empty array")
    void authorsPresent() {
        JsonArray authors = json.getAsJsonArray("authors");
        assertNotNull(authors);
        assertTrue(authors.size() > 0);
    }

    @Test
    @DisplayName("entrypoints object exists")
    void entrypointsExists() {
        assertTrue(json.has("entrypoints"));
        assertTrue(json.get("entrypoints").isJsonObject());
    }

    @Test
    @DisplayName("client entrypoint references com.intelium.Intelium")
    void clientEntrypoint() {
        JsonArray clients = json.getAsJsonObject("entrypoints").getAsJsonArray("client");
        assertNotNull(clients);
        boolean found = false;
        for (JsonElement el : clients) {
            if ("com.intelium.Intelium".equals(el.getAsString())) found = true;
        }
        assertTrue(found, "client entrypoint must include com.intelium.Intelium");
    }

    @Test
    @DisplayName("sodium:config_api_user entrypoint references InteliumConfigEntryPoint")
    void sodiumConfigEntryPoint() {
        JsonArray apiUsers = json.getAsJsonObject("entrypoints")
                .getAsJsonArray("sodium:config_api_user");
        assertNotNull(apiUsers);
        boolean found = false;
        for (JsonElement el : apiUsers) {
            if ("com.intelium.config.InteliumConfigEntryPoint".equals(el.getAsString()))
                found = true;
        }
        assertTrue(found, "sodium:config_api_user entrypoint must reference InteliumConfigEntryPoint");
    }

    @Test
    @DisplayName("mixins array contains intelium.mixins.json")
    void mixinsReferenced() {
        JsonArray mixins = json.getAsJsonArray("mixins");
        assertNotNull(mixins);
        boolean found = false;
        for (JsonElement el : mixins) {
            JsonObject m = el.getAsJsonObject();
            if ("intelium.mixins.json".equals(m.get("config").getAsString())) {
                found = true;
                assertEquals("client", m.get("environment").getAsString());
            }
        }
        assertTrue(found, "must reference intelium.mixins.json");
    }

    @Test
    @DisplayName("depends.fabricloader specifies version >=0.18.0")
    void fabricloaderDep() {
        String v = json.getAsJsonObject("depends").get("fabricloader").getAsString();
        assertTrue(v.contains("0.18.0"), "expected 0.18.0 in fabricloader dep, was: " + v);
    }

    @Test
    @DisplayName("depends.fabric-api is wildcard")
    void fabricApiDep() {
        String v = json.getAsJsonObject("depends").get("fabric-api").getAsString();
        assertEquals("*", v);
    }

    @Test
    @DisplayName("depends.minecraft equals 1.21.11")
    void minecraftDep() {
        String v = json.getAsJsonObject("depends").get("minecraft").getAsString();
        assertEquals("1.21.11", v);
    }

    @Test
    @DisplayName("depends.java specifies >=21")
    void javaDep() {
        String v = json.getAsJsonObject("depends").get("java").getAsString();
        assertTrue(v.contains("21"));
    }

    @Test
    @DisplayName("depends.sodium range is >=0.8.0 <0.9.0")
    void sodiumDep() {
        String v = json.getAsJsonObject("depends").get("sodium").getAsString();
        assertTrue(v.contains("0.8.0"));
        assertTrue(v.contains("0.9.0"));
    }

    @Test
    @DisplayName("contact.homepage is a URL")
    void contactHomepage() {
        String url = json.getAsJsonObject("contact").get("homepage").getAsString();
        assertTrue(url.startsWith("http"));
    }

    @Test
    @DisplayName("contact.issues is a URL")
    void contactIssues() {
        String url = json.getAsJsonObject("contact").get("issues").getAsString();
        assertTrue(url.startsWith("http"));
    }

    @Test
    @DisplayName("Raw JSON does not contain Windows line endings (CRLF normalization)")
    void noCrlf() {
        assertFalse(raw.contains("\r\n"),
                "fabric.mod.json must use LF line endings");
    }

    @Test
    @DisplayName("Raw JSON ends with newline")
    void endsWithNewline() {
        assertTrue(raw.endsWith("\n"), "fabric.mod.json must end with newline");
    }

    @Test
    @DisplayName("All required top-level fields present")
    void requiredTopLevelFields() {
        for (String key : new String[]{
                "schemaVersion", "id", "version", "name",
                "description", "authors", "license", "icon",
                "environment", "entrypoints", "mixins", "depends"
        }) {
            assertTrue(json.has(key), "missing top-level key: " + key);
        }
    }

    @Test
    @DisplayName("id is alphanumeric+lowercase (Fabric convention)")
    void idFormat() {
        String id = json.get("id").getAsString();
        assertTrue(id.matches("[a-z][a-z0-9_-]*"),
                "id must be lowercase alphanumeric, was: " + id);
    }

    @Test
    @DisplayName("depends.sodium upper bound is strict (<0.9.0)")
    void sodiumStrictUpper() {
        String v = json.getAsJsonObject("depends").get("sodium").getAsString();
        assertTrue(v.matches(".*<\\s*0\\.9\\.0.*"),
                "sodium upper bound must be strict (<0.9.0), was: " + v);
    }

    @Test
    @DisplayName("entrypoints.client is non-empty")
    void clientEntrypointsNonEmpty() {
        JsonArray clients = json.getAsJsonObject("entrypoints").getAsJsonArray("client");
        assertTrue(clients.size() > 0);
    }

    @Test
    @DisplayName("entrypoints.sodium:config_api_user is non-empty")
    void sodiumConfigEntrypointsNonEmpty() {
        JsonArray a = json.getAsJsonObject("entrypoints").getAsJsonArray("sodium:config_api_user");
        assertTrue(a.size() > 0);
    }
}
