package com.intelium.optimization;

import com.intelium.IntelGpuGeneration;

public final class ChunkBuilderTuner {

    private ChunkBuilderTuner() {}

    public static int recommendedWorkers(IntelGpuGeneration gen) {
        int cpu = Runtime.getRuntime().availableProcessors();
        switch (gen) {
            case GEN9_SKYLAKE:           return clamp(1, cpu, 2);
            case GEN9_5_KABY_COFFEE:     return clamp(1, cpu, 2);
            case GEN11_ICE_LAKE:         return clamp(2, cpu, 3);
            case GEN12_XE_LP:            return clamp(2, cpu / 2, 4);
            case XE_HPG_ARC_ALCHEMIST:   return clamp(4, cpu / 2, 6);
            case XE2_LUNAR_BATTLEMAGE:   return clamp(4, cpu / 2, 8);
            default:                     return clamp(1, cpu / 2, 2);
        }
    }

    private static int clamp(int lo, int v, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
