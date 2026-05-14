package com.intelium;

import org.lwjgl.opengl.GL11;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class IntelGpuDetector {

    private static final AtomicBoolean DETECTED = new AtomicBoolean(false);

    private IntelGpuDetector() {}

    public static void detectOnce() {
        if (!DETECTED.compareAndSet(false, true)) return;

        String vendor = safeGetString(GL11.GL_VENDOR);
        String renderer = safeGetString(GL11.GL_RENDERER);

        Intelium.LOGGER.info("GL_VENDOR='{}' GL_RENDERER='{}'", vendor, renderer);

        String vendorLower = vendor.toLowerCase(Locale.ROOT);

        if (vendorLower.contains("nvidia")) {
            Intelium.IS_COMPATIBLE = false;
            Intelium.DISABLED_REASON_KEY = "intelium.disabled.nvidia";
            Intelium.LOGGER.info("NVIDIA GPU detected. Intelium disabled.");
            return;
        }

        if (vendorLower.contains("amd") || vendorLower.contains("ati") ||
            vendorLower.contains("advanced micro devices") || vendorLower.contains("radeon")) {
            Intelium.IS_COMPATIBLE = false;
            Intelium.DISABLED_REASON_KEY = "intelium.disabled.amd";
            Intelium.LOGGER.info("AMD GPU detected. Intelium disabled.");
            return;
        }

        if (!vendorLower.contains("intel")) {
            Intelium.IS_COMPATIBLE = false;
            Intelium.DISABLED_REASON_KEY = "intelium.disabled.unknown_gpu";
            Intelium.LOGGER.info("Non-Intel GPU detected. Intelium disabled.");
            return;
        }

        IntelGpuGeneration gen = classifyIntelRenderer(renderer);
        Intelium.DETECTED_GENERATION = gen;

        if (!gen.supported) {
            Intelium.IS_COMPATIBLE = false;
            Intelium.DISABLED_REASON_KEY = "intelium.disabled.too_old";
            Intelium.LOGGER.info("Intel GPU '{}' is too old ({}). Intelium disabled.",
                                 renderer, gen.display);
            return;
        }

        Intelium.IS_COMPATIBLE = true;
        Intelium.LOGGER.info("Intel {} detected. Intelium ACTIVE.", gen.display);
    }

    private static String safeGetString(int name) {
        try {
            String s = GL11.glGetString(name);
            return s == null ? "" : s;
        } catch (Throwable t) {
            return "";
        }
    }

    static IntelGpuGeneration classifyIntelRenderer(String renderer) {
        if (renderer == null) return IntelGpuGeneration.UNKNOWN;
        // Strip trademark markers like "(R)" and "(TM)" so "Iris(R) Plus"
        // becomes "Iris Plus" - this lets our keyword checks be plain substrings.
        String r = renderer.toLowerCase(Locale.ROOT)
                .replace("(r)", "")
                .replace("(tm)", "")
                .replaceAll("\\s+", " ");

        // Battlemage / Xe2 first - mention of "battlemage", "bmg", or Arc B-series.
        if (r.contains("battlemage") || r.contains("bmg") || r.contains("lunar"))
            return IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE;
        if (r.contains("arc") && (r.contains("b3") || r.contains("b4") ||
                                  r.contains("b5") || r.contains("b7")))
            return IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE;

        // Alchemist (first-gen Arc, Xe-HPG).
        if (r.contains("arc") || r.contains("alchemist") || r.contains("dg2"))
            return IntelGpuGeneration.XE_HPG_ARC_ALCHEMIST;

        // Ice Lake / Iris Plus before generic Xe match - some Iris Plus strings
        // ("Iris Plus Graphics 640/655") otherwise fall through.
        if (r.contains("iris plus") || r.contains("ice lake") || r.contains("icl"))
            return IntelGpuGeneration.GEN11_ICE_LAKE;

        // Xe-LP / Gen 12.
        if (r.contains("xe") || r.contains("tgl") || r.contains("tiger lake") ||
            r.contains("alder lake") || r.contains("raptor lake") || r.contains("meteor lake"))
            return IntelGpuGeneration.GEN12_XE_LP;

        if (r.contains("uhd graphics 6") || r.contains("uhd graphics 7") ||
            r.contains("kaby lake") || r.contains("coffee lake") || r.contains("comet lake") ||
            r.contains("whiskey lake"))
            return IntelGpuGeneration.GEN9_5_KABY_COFFEE;
        if (r.contains("hd graphics 5") || r.contains("hd graphics 6") ||
            r.contains("skylake") || r.contains("skl"))
            return IntelGpuGeneration.GEN9_SKYLAKE;

        if (r.contains("hd graphics 4") || r.contains("hd graphics 3") ||
            r.contains("hd graphics 2") || r.contains("haswell") || r.contains("ivy bridge") ||
            r.contains("sandy bridge") || r.contains("bay trail"))
            return IntelGpuGeneration.PRE_GEN9;

        return IntelGpuGeneration.GEN12_XE_LP;
    }
}
