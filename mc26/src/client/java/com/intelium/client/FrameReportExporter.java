package com.intelium.client;

import com.intelium.Capabilities;
import com.intelium.Intelium;
import com.intelium.RenderBackend;
import com.intelium.config.InteliumConfigIO;
import com.intelium.perf.FrameTimeTracker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Writes the frame-time distribution to disk so a run can be compared against
 * another run.
 *
 * <h2>Why a file and not an overlay</h2>
 *
 * <p>The 1.21.11 build has a movable FPS overlay; 26.x replaced immediate-mode
 * GUI rendering and porting it is a large piece of work for a small payoff.
 * What a benchmark actually needs is not a number on screen - it is a record
 * that survives the session and can be diffed against the build before it. An
 * overlay also costs frames in the measurement it is reporting on, which is the
 * problem this whole release is about.
 *
 * <p>So a keypress writes a JSON file and a CSV row: the distribution, the
 * settings that produced it, and the environment it ran in. Two runs, two
 * files, and the comparison is arithmetic rather than memory.
 *
 * <h2>Why a button and not a keybind</h2>
 *
 * <p>A keybind would need a default key, and claiming one on a client that has
 * its own bindings - Lunar, again, being the case in mind - is how a mod
 * silently breaks something the user already had bound. It would also add a
 * Fabric API module to the set this jar requires at runtime, for a diagnostic.
 * The button lives on Intelium's existing Sodium settings page instead, so it
 * costs nothing and can conflict with nothing.
 *
 * <p>Opening that menu does pause measurement, but the tracker deliberately
 * keeps its recorded window across an invalidation - so the report describes
 * the last several seconds of actual gameplay, not the menu.
 *
 * <p>The snapshot is taken on the client thread, where it is a copy and a sort
 * of a small array; the file write happens on a short-lived daemon thread,
 * because a disk write on the render thread would put a stutter into the very
 * measurement the user is trying to record.
 */
public final class FrameReportExporter {

    private FrameReportExporter() {}

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    /** Directory reports land in, under the game's config directory. */
    private static final String REPORT_DIR = "intelium-reports";

    /**
     * Captures the current distribution and writes it out.
     *
     * @return a chat-ready message describing what happened
     */
    public static Component export() {
        if (!FrameTimeSampler.available()) {
            return Component.translatable("intelium.report.unavailable");
        }
        FrameTimeTracker.Snapshot snapshot = FrameTimeSampler.snapshot();
        if (snapshot.samples() == 0) {
            return Component.translatable("intelium.report.empty");
        }

        Instant now = Instant.now();
        String stamp = STAMP.format(now);
        String json = toJson(snapshot, now);
        String csvRow = csvRow(snapshot, now);

        Path dir = FabricLoader.getInstance().getConfigDir().resolve(REPORT_DIR);
        Path jsonFile = dir.resolve("intelium-frametimes-" + stamp + ".json");
        Path csvFile = dir.resolve("intelium-frametimes.csv");

        Thread writer = new Thread(() -> writeFiles(dir, jsonFile, csvFile, json, csvRow),
                "Intelium-frame-report");
        writer.setDaemon(true);
        writer.start();

        Intelium.LOGGER.info("Intelium frame report: {}", snapshot.toCompactString());
        return Component.translatable("intelium.report.written",
                Component.literal(jsonFile.getFileName().toString()));
    }

    private static void writeFiles(Path dir, Path jsonFile, Path csvFile,
                                   String json, String csvRow) {
        try {
            Files.createDirectories(dir);
            Files.writeString(jsonFile, json, StandardCharsets.UTF_8);
            boolean fresh = !Files.exists(csvFile);
            StringBuilder appended = new StringBuilder();
            if (fresh) {
                appended.append("timestamp,").append(FrameTimeTracker.Snapshot.csvHeader())
                        .append(",backend,gpu,profile,chunk_mode,workers,cap_active\n");
            }
            appended.append(csvRow).append('\n');
            Files.writeString(csvFile, appended.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            Intelium.LOGGER.warn("Intelium: could not write the frame report", e);
        }
    }

    private static String toJson(FrameTimeTracker.Snapshot s, Instant now) {
        var cfg = InteliumConfigIO.get();
        RenderBackend backend = Intelium.DETECTED_BACKEND;
        StringBuilder sb = new StringBuilder(768);
        sb.append("{\n");
        line(sb, "timestamp", now.toString());
        line(sb, "intelium_version", modVersion("intelium"));
        line(sb, "minecraft_version", modVersion("minecraft"));
        line(sb, "fabric_loader", modVersion("fabricloader"));
        line(sb, "fabric_api", modVersion("fabric-api"));
        line(sb, "sodium", modVersion("sodium"));
        line(sb, "iris", modVersion("iris"));
        line(sb, "backend", backend.displayName);
        line(sb, "gpu", Intelium.DETECTED_RENDERER);
        line(sb, "generation", Intelium.DETECTED_GENERATION.display);
        line(sb, "profile", cfg.profile);
        line(sb, "chunk_loading_mode", cfg.chunkLoadingMode);
        line(sb, "capabilities", Capabilities.describe());
        sb.append("  \"chunk_build_workers\": ").append(cfg.chunkBuildWorkers).append(",\n");
        sb.append("  \"adaptive_fps_target\": ").append(cfg.adaptiveFpsTarget).append(",\n");
        sb.append("  \"cap_active\": ").append(FrameTimeSampler.capActive()).append(",\n");
        sb.append("  \"warm\": ").append(s.warm()).append(",\n");
        sb.append("  \"samples\": ").append(s.samples()).append(",\n");
        sb.append("  \"discarded\": ").append(s.discarded()).append(",\n");
        num(sb, "average_fps", s.averageFps());
        num(sb, "average_frametime_ms", s.averageFrameTimeMs());
        num(sb, "median_frametime_ms", s.medianFrameTimeMs());
        num(sb, "p95_frametime_ms", s.p95FrameTimeMs());
        num(sb, "p99_frametime_ms", s.p99FrameTimeMs());
        num(sb, "one_percent_low_fps", s.onePercentLowFps());
        sb.append("  \"point_one_percent_low_fps\": ")
          .append(String.format(Locale.ROOT, "%.3f", s.pointOnePercentLowFps()))
          .append('\n');
        sb.append("}\n");
        return sb.toString();
    }

    private static String csvRow(FrameTimeTracker.Snapshot s, Instant now) {
        var cfg = InteliumConfigIO.get();
        return now + "," + s.toCsvRow() + ","
                + Intelium.DETECTED_BACKEND.displayName + ","
                + csvSafe(Intelium.DETECTED_RENDERER) + ","
                + cfg.profile + "," + cfg.chunkLoadingMode + ","
                + cfg.chunkBuildWorkers + "," + FrameTimeSampler.capActive();
    }

    /** Commas and quotes in a GPU name must not shift every later column. */
    private static String csvSafe(String value) {
        if (value == null || value.isEmpty()) return "unknown";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static void line(StringBuilder sb, String key, String value) {
        sb.append("  \"").append(key).append("\": \"").append(jsonEscape(value)).append("\",\n");
    }

    private static void num(StringBuilder sb, String key, double value) {
        sb.append("  \"").append(key).append("\": ")
          .append(String.format(Locale.ROOT, "%.3f", value)).append(",\n");
    }

    private static String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ").replace("\t", " ");
    }

    private static String modVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("absent");
    }

    /** Sends the result to chat when there is a player to tell. */
    public static void exportAndTell(Minecraft client) {
        Component message = export();
        try {
            if (client != null && client.player != null) {
                client.player.sendSystemMessage(message);
            }
        } catch (Throwable ignored) {
            // The log line has already been written; chat is a nicety.
        }
    }
}
