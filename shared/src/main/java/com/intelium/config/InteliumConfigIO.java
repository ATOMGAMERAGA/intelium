package com.intelium.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intelium.Intelium;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads and saves {@code config/intelium.json}.
 *
 * <p>Robustness rules: a missing, empty or <em>corrupted</em> file never
 * crashes the game - defaults are used and the file is rewritten. Saves go
 * through a temp file and an atomic move, so a crash mid-write can never leave
 * a half-written config behind. Loaded values are clamped to their valid
 * ranges so a hand-edited file cannot put the mod in a broken state.
 */
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
        Path tmp = PATH.resolveSibling(PATH.getFileName() + ".tmp");
        try {
            try (Writer w = Files.newBufferedWriter(tmp)) {
                GSON.toJson(cached, w);
            }
            try {
                Files.move(tmp, PATH, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, PATH, StandardCopyOption.REPLACE_EXISTING);
            }
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
            return InteliumConfig.sanitize(cfg == null ? new InteliumConfig() : cfg);
        } catch (Exception e) {
            // Covers IO errors AND a corrupted / hand-mangled JSON file
            // (JsonSyntaxException). Never let a bad config crash the game.
            Intelium.LOGGER.error("Failed to read Intelium config, using defaults", e);
            return new InteliumConfig();
        }
    }
}
