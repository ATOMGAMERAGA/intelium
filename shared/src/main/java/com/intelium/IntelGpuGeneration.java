package com.intelium;

public enum IntelGpuGeneration {
    UNKNOWN(0, "Unknown", false),
    PRE_GEN9(0, "Pre-Skylake (unsupported)", false),
    GEN9_SKYLAKE(9, "Gen 9 Skylake (HD 520/530/etc.)", true),
    GEN9_5_KABY_COFFEE(9, "Gen 9.5 Kaby/Coffee Lake (UHD 620/630)", true),
    GEN11_ICE_LAKE(11, "Gen 11 Ice Lake (Iris Plus G7)", true),
    GEN12_XE_LP(12, "Xe-LP / Gen 12 Tiger Lake (Iris Xe)", true),
    XE_HPG_ARC_ALCHEMIST(13, "Xe-HPG Arc Alchemist (A310-A770)", true),
    XE2_LUNAR_BATTLEMAGE(14, "Xe2 Lunar/Battlemage (Arc B-series)", true);

    public final int genNumber;
    public final String display;
    public final boolean supported;

    IntelGpuGeneration(int gen, String display, boolean supported) {
        this.genNumber = gen;
        this.display = display;
        this.supported = supported;
    }
}
