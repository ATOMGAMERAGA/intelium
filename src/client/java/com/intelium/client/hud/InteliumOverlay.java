package com.intelium.client.hud;

import com.intelium.Intelium;
import com.intelium.config.InteliumConfig;
import com.intelium.config.InteliumConfigIO;
import com.intelium.hud.AbBenchmark;
import com.intelium.hud.FpsTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The movable on-screen FPS test panel. Shows live FPS and, after an A/B
 * benchmark, the measured FPS with Intelium on vs off and the gain. Rendered
 * both as an in-game HUD and inside the edit screen (which reuses {@link #render}).
 */
public final class InteliumOverlay {

    public static final FpsTracker TRACKER = new FpsTracker(20);

    private static final int PAD = 4;
    private static final int LINE_H = 10;
    private static final int BG = 0xB0000000;
    private static final int BORDER = 0xFFFFAA00;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFB0B0B0;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF6B6B;
    private static final int CYAN = 0xFF55D7FF;

    /** Size of the most recently rendered panel, for hit-testing in edit mode. */
    public static volatile int lastWidth = 120;
    public static volatile int lastHeight = 60;

    private InteliumOverlay() {}

    private record Line(Text text, int color) {}

    /** HUD entry point. Draws the panel when enabled and in a world. */
    public static void renderHud(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return;
        if (mc.currentScreen instanceof OverlayEditScreen) return;
        InteliumConfig cfg = InteliumConfigIO.get();
        if (!cfg.overlayEnabled) return;
        render(ctx, mc.textRenderer, cfg.overlayX, cfg.overlayY, false);
    }

    /** Draws the panel at (x,y); returns {width,height}. */
    public static int[] render(DrawContext ctx, TextRenderer tr, int x, int y, boolean editMode) {
        List<Line> lines = buildLines();

        int w = 0;
        for (Line l : lines) {
            w = Math.max(w, tr.getWidth((StringVisitable) l.text()));
        }
        w += PAD * 2;
        int h = lines.size() * LINE_H + PAD * 2;
        lastWidth = w;
        lastHeight = h;

        ctx.fill(x, y, x + w, y + h, BG);
        if (editMode) {
            ctx.fill(x, y, x + w, y + 1, BORDER);
            ctx.fill(x, y + h - 1, x + w, y + h, BORDER);
            ctx.fill(x, y, x + 1, y + h, BORDER);
            ctx.fill(x + w - 1, y, x + w, y + h, BORDER);
        }

        int ty = y + PAD;
        for (Line l : lines) {
            ctx.drawTextWithShadow(tr, l.text(), x + PAD, ty, l.color());
            ty += LINE_H;
        }
        return new int[]{w, h};
    }

    private static List<Line> buildLines() {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(Text.translatable("intelium.hud.title"), CYAN));

        int fps = MinecraftClient.getInstance().getCurrentFps();
        lines.add(new Line(Text.translatable("intelium.hud.fps", fps), WHITE));

        AbBenchmark bench = AbBenchmark.INSTANCE;
        if (bench.isRunning()) {
            lines.add(new Line(Text.translatable("intelium.hud.running",
                    bench.progressPercent()), CYAN));
        } else if (bench.hasResult()) {
            lines.add(new Line(Text.translatable("intelium.hud.on", fmt(bench.onFps())), GREEN));
            lines.add(new Line(Text.translatable("intelium.hud.off", fmt(bench.offFps())), GRAY));
            double gain = bench.gain();
            String gainStr = (gain >= 0 ? "+" : "") + fmt(gain)
                    + " (" + (gain >= 0 ? "+" : "") + fmt(bench.gainPercent()) + "%)";
            lines.add(new Line(Text.translatable("intelium.hud.gain", gainStr),
                    gain >= 0 ? GREEN : RED));
        } else {
            lines.add(new Line(Text.translatable("intelium.hud.hint"), GRAY));
        }

        Text status = Intelium.IS_COMPATIBLE
                ? Text.translatable("intelium.status.active", Intelium.DETECTED_GENERATION.display)
                : Text.translatable("intelium.hud.inactive");
        lines.add(new Line(status, Intelium.IS_COMPATIBLE ? GREEN : GRAY));
        return lines;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
