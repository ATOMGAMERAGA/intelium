package com.intelium.optimization;

import com.intelium.RenderBackend;

/**
 * Small backend-specific adjustments for Intelium's own render budgets.
 *
 * <p>OpenGL pays more driver/main-thread overhead per independently rendered
 * entity and block entity than the newer Vulkan path. Raising the screen-space
 * cutoff by ten percent and trimming only the high-count burst ceilings buys
 * back that CPU time without touching nearby or gameplay-significant objects.
 * Unknown backends stay at the established policy; guessing would be unsafe.
 */
public final class BackendRenderTuning {

    private static final double OPENGL_THRESHOLD_SCALE = 1.10;
    private static final double OPENGL_BLOCK_ENTITY_BUDGET_SCALE = 0.75;
    private static final double OPENGL_PARTICLE_BUDGET_SCALE = 0.875;

    private BackendRenderTuning() {}

    /** Multiplier for apparent-size thresholds (0 remains off). */
    public static double thresholdScale(RenderBackend backend) {
        return backend == RenderBackend.OPENGL ? OPENGL_THRESHOLD_SCALE : 1.0;
    }

    /** OpenGL-specific block-entity draw ceiling; 0 remains unlimited. */
    public static int blockEntityBudget(int baseBudget, RenderBackend backend) {
        return scaleCount(baseBudget, backend == RenderBackend.OPENGL
                ? OPENGL_BLOCK_ENTITY_BUDGET_SCALE : 1.0);
    }

    /** OpenGL-specific particle burst ceiling; 0 remains unlimited. */
    public static int particleBudget(int baseBudget, RenderBackend backend) {
        return scaleCount(baseBudget, backend == RenderBackend.OPENGL
                ? OPENGL_PARTICLE_BUDGET_SCALE : 1.0);
    }

    private static int scaleCount(int baseBudget, double scale) {
        if (baseBudget <= 0) return 0;
        return Math.max(1, (int) Math.round(baseBudget * scale));
    }
}
