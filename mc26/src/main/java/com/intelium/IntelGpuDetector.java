package com.intelium;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gathers the host GPU's vendor/renderer strings from the OpenGL context and
 * publishes the {@link IntelGpuClassifier} decision to {@link Intelium}.
 *
 * <p>On the 26.x line this is subtle: 26.1.x still renders through OpenGL, but
 * 26.2 introduced the Vulkan renderer, and under Vulkan there is <em>no GL
 * context current on the render thread at all</em>. Calling {@code glGetString}
 * there is at best an {@code IllegalStateException} and at worst (with LWJGL's
 * runtime checks disabled) a native crash. So this detector first verifies a
 * context is current via {@link GL#getCapabilities()}, retries for a while in
 * case detection simply ran too early, and if no context ever appears it
 * concludes - honestly - that the GPU cannot be identified on this renderer and
 * stays inactive with a dedicated message, instead of reporting a misleading
 * "unknown GPU vendor".
 *
 * <p>The pure classification logic lives in the shared
 * {@link IntelGpuClassifier}; this class is only the version-specific glue that
 * reads {@code glGetString} on the render thread.
 */
public final class IntelGpuDetector {

    private static final AtomicBoolean DETECTED = new AtomicBoolean(false);

    /**
     * How many ticks to keep looking for a current GL context (~5s at 20/s)
     * before concluding this renderer will never provide one. Under OpenGL the
     * context exists by the first tick, so this only ever delays the honest
     * "cannot identify" verdict on the Vulkan renderer.
     */
    private static final int MAX_ATTEMPTS = 100;

    /** Only ever touched on the render thread (both call sites run there). */
    private static int attempts;

    private IntelGpuDetector() {}

    /**
     * Runs detection once it can. Both call sites (the first client tick and
     * the Sodium world-renderer constructor) run on the render thread, so when
     * a GL context exists it is the current one. Calls after a concluded
     * detection are no-ops; failed reads retry on later ticks rather than
     * latching a wrong answer.
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

        if (vendor.isEmpty() && renderer.isEmpty()) {
            // No context (Vulkan renderer) or an unreadable one. Keep trying -
            // this may simply be earlier than context creation - and only
            // conclude once it is clear no GL context is coming.
            if (++attempts < MAX_ATTEMPTS) return;
            if (!DETECTED.compareAndSet(false, true)) return;
            Intelium.DETECTED_RENDERER = "";
            Intelium.DETECTED_GENERATION = IntelGpuGeneration.UNKNOWN;
            Intelium.IS_COMPATIBLE = false;
            Intelium.DISABLED_REASON_KEY = "intelium.disabled.no_gl";
            Intelium.LOGGER.info("Intelium status: no OpenGL context available to identify the "
                    + "GPU (Vulkan renderer?) - staying inactive on this renderer.");
            return;
        }

        if (!DETECTED.compareAndSet(false, true)) return;

        IntelGpuClassifier.Result r = IntelGpuClassifier.decide(vendor, renderer);

        Intelium.DETECTED_RENDERER = renderer;
        Intelium.DETECTED_GENERATION = r.generation;
        Intelium.IS_COMPATIBLE = r.compatible;
        Intelium.DISABLED_REASON_KEY = r.reasonKey;

        Intelium.LOGGER.info(
                "Intelium status: gpu='{}' renderer='{}' detected={} active={}{}",
                vendor, renderer, r.generation.display, r.compatible,
                r.reasonKey == null ? "" : " (reason=" + r.reasonKey + ")");
    }

    /** Whether a GL context is current on this thread (false under Vulkan). */
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
