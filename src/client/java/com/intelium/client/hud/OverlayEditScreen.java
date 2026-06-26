package com.intelium.client.hud;

import com.intelium.Intelium;
import com.intelium.client.InteliumGame;
import com.intelium.config.InteliumConfig;
import com.intelium.config.InteliumConfigIO;
import com.intelium.hud.AbBenchmark;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * "Edit mode" for the FPS test overlay: drag the panel to reposition it (saved
 * live) and start the A/B benchmark. Opened from the Intelium config page.
 */
public class OverlayEditScreen extends Screen {

    private final Screen parent;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    public OverlayEditScreen(Screen parent) {
        super(Text.translatable("intelium.edit.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        InteliumConfig cfg = InteliumConfigIO.get();

        // Make sure the overlay is visible while editing it.
        if (!cfg.overlayEnabled) {
            cfg.overlayEnabled = true;
            InteliumConfigIO.flush();
        }

        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("intelium.edit.run"),
                        b -> startBenchmark())
                .dimensions(this.width / 2 - 100, this.height - 52, 200, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
                .dimensions(this.width / 2 - 100, this.height - 28, 200, 20)
                .build());
    }

    private void startBenchmark() {
        InteliumConfig cfg = InteliumConfigIO.get();
        AbBenchmark.INSTANCE.start(System.currentTimeMillis(), cfg.enabled,
                enabled -> Intelium.IS_ENABLED = enabled,
                InteliumGame::reloadChunks);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, 16, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("intelium.edit.hint"),
                this.width / 2, 30, 0xFFB0B0B0);

        InteliumConfig cfg = InteliumConfigIO.get();
        InteliumOverlay.render(context, this.textRenderer, cfg.overlayX, cfg.overlayY, true);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            InteliumConfig cfg = InteliumConfigIO.get();
            double mx = click.x();
            double my = click.y();
            if (mx >= cfg.overlayX && mx <= cfg.overlayX + InteliumOverlay.lastWidth
                    && my >= cfg.overlayY && my <= cfg.overlayY + InteliumOverlay.lastHeight) {
                dragging = true;
                dragOffsetX = (int) (mx - cfg.overlayX);
                dragOffsetY = (int) (my - cfg.overlayY);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragging) {
            InteliumConfig cfg = InteliumConfigIO.get();
            cfg.overlayX = clamp((int) (click.x() - dragOffsetX), 0,
                    Math.max(0, this.width - InteliumOverlay.lastWidth));
            cfg.overlayY = clamp((int) (click.y() - dragOffsetY), 0,
                    Math.max(0, this.height - InteliumOverlay.lastHeight));
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging) {
            dragging = false;
            InteliumConfigIO.flush();
            return true;
        }
        return super.mouseReleased(click);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    public void close() {
        InteliumConfigIO.flush();
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
