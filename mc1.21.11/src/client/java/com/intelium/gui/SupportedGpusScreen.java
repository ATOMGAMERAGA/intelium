package com.intelium.gui;

import com.intelium.Intelium;
import com.intelium.SupportedGpus;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game screen listing the Intel GPU families Intelium supports, oldest to
 * newest, with a live header showing the detected GPU and its support status.
 * Opened from the "Supported GPUs" button on the Intelium config page.
 */
public class SupportedGpusScreen extends Screen {

    private static final int GOLD = 0xFFFFAA00;
    private static final int GRAY = 0xFFBBBBBB;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int LINE_HEIGHT = 12;

    private final Screen parent;
    private final List<Line> lines = new ArrayList<>();
    private int viewportTop;
    private int viewportBottom;
    private int scrollY;
    private int maxScroll;

    public SupportedGpusScreen(Screen parent) {
        super(Text.translatable("intelium.gpus.title"));
        this.parent = parent;
    }

    private record Line(Text text, int color, int indent) {}

    @Override
    protected void init() {
        lines.clear();

        // ---- Live detection status ----
        String renderer = Intelium.DETECTED_RENDERER;
        lines.add(new Line(Text.translatable("intelium.gpus.detected",
                renderer == null || renderer.isEmpty()
                        ? Text.translatable("intelium.gpus.detected.pending")
                        : Text.literal(renderer)), WHITE, 0));

        if (Intelium.IS_COMPATIBLE) {
            lines.add(new Line(Text.translatable("intelium.gpus.status.supported",
                    Intelium.DETECTED_GENERATION.display), GREEN, 0));
        } else {
            Text reason = Intelium.DISABLED_REASON_KEY == null
                    ? Text.translatable("intelium.gpus.status.pending")
                    : Text.translatable(Intelium.DISABLED_REASON_KEY);
            lines.add(new Line(Text.translatable("intelium.gpus.status.unsupported", reason), RED, 0));
        }

        lines.add(new Line(Text.empty(), WHITE, 0));
        lines.add(new Line(Text.translatable("intelium.gpus.heading")
                .formatted(Formatting.BOLD), WHITE, 0));
        lines.add(new Line(Text.empty(), WHITE, 0));

        // ---- Supported families, oldest to newest ----
        for (SupportedGpus.Family f : SupportedGpus.SUPPORTED) {
            lines.add(new Line(Text.literal(f.generation() + "  (" + f.architecture()
                    + ", " + f.years() + ")").formatted(Formatting.BOLD), GOLD, 0));
            for (String example : f.examples().split(" · ")) {
                lines.add(new Line(Text.literal("• " + example.trim()), GRAY, 10));
            }
            lines.add(new Line(Text.empty(), WHITE, 0));
        }

        viewportTop = 44;
        viewportBottom = this.height - 36;
        int contentHeight = lines.size() * LINE_HEIGHT;
        maxScroll = Math.max(0, contentHeight - (viewportBottom - viewportTop));
        scrollY = Math.min(scrollY, maxScroll);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
                .dimensions(this.width / 2 - 100, this.height - 28, 200, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, 18, WHITE);

        int left = this.width / 2 - 170;
        int right = this.width / 2 + 170;
        context.enableScissor(left, viewportTop, right, viewportBottom);
        int y = viewportTop - scrollY;
        for (Line line : lines) {
            if (y + LINE_HEIGHT >= viewportTop && y <= viewportBottom) {
                context.drawTextWithShadow(this.textRenderer, line.text(),
                        left + line.indent(), y, line.color());
            }
            y += LINE_HEIGHT;
        }
        context.disableScissor();

        if (maxScroll > 0) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("intelium.gpus.scroll"),
                    this.width / 2, viewportBottom + 2, GRAY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        scrollY = clampScroll(scrollY - (int) (verticalAmount * LINE_HEIGHT * 2));
        return true;
    }

    private int clampScroll(int v) {
        if (v < 0) return 0;
        return Math.min(v, maxScroll);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
