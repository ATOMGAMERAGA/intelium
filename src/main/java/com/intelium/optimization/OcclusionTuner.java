package com.intelium.optimization;

import com.intelium.IntelGpuGeneration;

public final class OcclusionTuner {

    private static volatile float cullingMultiplier = 1.0f;

    private OcclusionTuner() {}

    public static void applyForCurrentFrame(IntelGpuGeneration gen) {
        switch (gen) {
            case GEN9_SKYLAKE:
            case GEN9_5_KABY_COFFEE:  cullingMultiplier = 0.92f; break;
            case GEN11_ICE_LAKE:      cullingMultiplier = 0.95f; break;
            case GEN12_XE_LP:         cullingMultiplier = 0.97f; break;
            default:                  cullingMultiplier = 1.00f; break;
        }
    }

    public static float multiplier() { return cullingMultiplier; }
}
