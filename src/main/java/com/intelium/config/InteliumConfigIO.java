package com.intelium.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intelium.Intelium;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class InteliumConfigIO {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("intelium.json");

    private static InteliumConfig cached;

    private InteliumConfigIO() {}

    public static synchronized InteliumConfig get() {
        if (cached != null) return cached;
        cached = load();
        return cached;
    }

    public static synchronized void flush() {
        if (cached == null) return;
        try (Writer w = Files.newBufferedWriter(PATH)) {
            GSON.toJson(cached, w);
        } catch (IOException e) {
            Intelium.LOGGER.error("Failed to save Intelium config", e);
        }
    }

    private static InteliumConfig load() {
        if (!Files.exists(PATH)) {
            InteliumConfig fresh = new InteliumConfig();
            cached = fresh;
            flush();
            return fresh;
        }
        try (Reader r = Files.newBufferedReader(PATH)) {
            InteliumConfig cfg = GSON.fromJson(r, InteliumConfig.class);
            return cfg == null ? new InteliumConfig() : cfg;
        } catch (IOException e) {
            Intelium.LOGGER.error("Failed to read Intelium config, using defaults", e);
            return new InteliumConfig();
        }
    }
}
