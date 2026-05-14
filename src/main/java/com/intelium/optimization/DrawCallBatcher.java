package com.intelium.optimization;

import com.intelium.IntelGpuGeneration;

public final class DrawCallBatcher {

    private static int batchSize = 64;

    private DrawCallBatcher() {}

    public static void beginFrame(IntelGpuGeneration gen) {
        switch (gen) {
            case XE2_LUNAR_BATTLEMAGE:
            case XE_HPG_ARC_ALCHEMIST: batchSize = 256; break;
            case GEN12_XE_LP:          batchSize = 128; break;
            case GEN11_ICE_LAKE:       batchSize = 96;  break;
            default:                   batchSize = 64;  break;
        }
    }

    public static void endFrame() {
        // Flush any partial batch. Real impl: glMultiDrawElementsIndirect on the
        // accumulated command buffer, then reset the pointer.
    }

    public static int currentBatchSize() { return batchSize; }
}
