package com.intelium;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gathers the host GPU's vendor/renderer strings from the OpenGL context and
 * publishes the {@link IntelGpuClassifier} decision to {@link Intelium}.
 *
 * <p>This is the OpenGL detector used by Minecraft 1.21.11 (and the OpenGL-backed
 * 26.1.x line). The pure classification logic lives in the shared
 * {@link IntelGpuClassifier} so it can be unit-tested and reused unchanged; this
 * class is only the thin, version-specific glue that reads {@code glGetString} on
 * the render thread.
 */
public final class IntelGpuDetector {

    private static final AtomicBoolean DETECTED = new AtomicBoolean(false);
    private static final int MAX_ATTEMPTS = 100;

    /** Only touched by render-thread call sites. */
    private static int attempts;

    private IntelGpuDetector() {}

    /**
     * Runs detection once identity strings are readable. Both call sites (the first client tick and
     * the Sodium world-renderer constructor) run on the render thread with a
     * current GL context. A short retry window prevents a too-early first tick
     * from permanently latching an unknown GPU.
     */
    public static void detectOnce() {
        if (DETECTED.get()) return;
        // Sodium missing / unsupported was already decided at init; do not let
        // GPU detection overwrite that environment decision.
        if (!Intelium.SODIUM_OK) {
            DETECTED.set(true);
            return;
        }

        String vendor = "";
        String renderer = "";
        if (glContextCurrent()) {
            vendor = safeGetString(GL11.GL_VENDOR);
            renderer = safeGetString(GL11.GL_RENDERER);
        }
        if (vendor.isEmpty() && renderer.isEmpty() && ++attempts < MAX_ATTEMPTS) return;
        if (!DETECTED.compareAndSet(false, true)) return;

        IntelGpuClassifier.Result r = IntelGpuClassifier.decide(vendor, renderer);

        Intelium.DETECTED_RENDERER = renderer;
        Intelium.DETECTED_BACKEND = RenderBackend.OPENGL;
        Intelium.DETECTED_GENERATION = r.generation;
        Intelium.IS_COMPATIBLE = r.compatible;
        Intelium.DISABLED_REASON_KEY = r.reasonKey;

        Intelium.LOGGER.info(
                "Intelium status: gpu='{}' renderer='{}' detected={} active={}{}",
                vendor, renderer, r.generation.display, r.compatible,
                r.reasonKey == null ? "" : " (reason=" + r.reasonKey + ")");
    }

    private static boolean glContextCurrent() {
        try {
            GL.getCapabilities();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String safeGetString(int name) {
        try {
            String s = GL11.glGetString(name);
            return s == null ? "" : s;
        } catch (Throwable t) {
            return "";
        }
    }
}
