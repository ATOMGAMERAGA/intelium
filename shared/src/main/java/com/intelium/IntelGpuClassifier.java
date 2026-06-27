package com.intelium;

import java.util.Locale;

/**
 * Pure GPU-classification logic, shared by every Minecraft target.
 *
 * <p>Given the vendor and renderer strings gathered by a version-specific
 * detector ({@code IntelGpuDetector} - OpenGL on older targets, Vulkan on 26.2+),
 * this decides whether Intelium should be active and, if not, why. It has no
 * OpenGL/Vulkan, Minecraft or Fabric dependency, so it is exhaustively
 * unit-tested and reused unchanged across the 1.21.11 and 26.x builds.
 *
 * <p>Support policy (see README): Intel Gen 9 "Skylake" (HD Graphics 520 and its
 * generation-mates) and everything newer are supported. Anything older -
 * Broadwell and back - is recognized but reported unsupported. Unrecognized Intel
 * parts and all non-Intel vendors are reported unsupported; Intelium never
 * guesses a tuning profile for hardware it cannot place.
 */
public final class IntelGpuClassifier {

    private IntelGpuClassifier() {}

    /** Outcome of a detection decision. {@code reasonKey} is null when active. */
    public static final class Result {
        public final IntelGpuGeneration generation;
        public final boolean compatible;
        public final String reasonKey;

        public Result(IntelGpuGeneration generation, boolean compatible, String reasonKey) {
            this.generation = generation;
            this.compatible = compatible;
            this.reasonKey = reasonKey;
        }
    }

    /**
     * Pure detection decision. Given the GPU vendor/renderer strings, returns
     * whether Intelium should be active and, if not, the lang key explaining
     * why. Never throws and never returns null.
     */
    public static Result decide(String vendor, String renderer) {
        String v = vendor == null ? "" : vendor.toLowerCase(Locale.ROOT);

        // Confirmed non-Intel vendors are always refused - applying Intel
        // profiles to NVIDIA/AMD silicon is never correct.
        if (v.contains("nvidia")) {
            return new Result(IntelGpuGeneration.UNKNOWN, false, "intelium.disabled.nvidia");
        }
        if (v.contains("amd") || v.contains("ati")
                || v.contains("advanced micro devices") || v.contains("radeon")) {
            return new Result(IntelGpuGeneration.UNKNOWN, false, "intelium.disabled.amd");
        }
        if (!v.contains("intel")) {
            return new Result(IntelGpuGeneration.UNKNOWN, false, "intelium.disabled.unknown_gpu");
        }

        IntelGpuGeneration gen = classifyIntelRenderer(renderer);

        // Unrecognized Intel part: stay out of the way rather than guess.
        if (gen == IntelGpuGeneration.UNKNOWN) {
            return new Result(IntelGpuGeneration.UNKNOWN, false,
                    "intelium.disabled.unrecognized_intel");
        }
        // Recognized but too old (Broadwell and earlier).
        if (!gen.supported) {
            return new Result(gen, false, "intelium.disabled.too_old");
        }
        return new Result(gen, true, null);
    }

    /**
     * Classifies an Intel renderer string into a generation. Handles both
     * Windows driver marketing strings and Linux Mesa codename strings.
     * Returns {@link IntelGpuGeneration#UNKNOWN} for anything it cannot place,
     * and {@link IntelGpuGeneration#PRE_GEN9} for recognized-but-too-old parts.
     * Callers must NOT treat UNKNOWN as a supported part.
     */
    public static IntelGpuGeneration classifyIntelRenderer(String renderer) {
        if (renderer == null) return IntelGpuGeneration.UNKNOWN;
        // Strip trademark markers like "(R)"/"(TM)" and collapse whitespace so
        // keyword checks can be plain substrings.
        String r = renderer.toLowerCase(Locale.ROOT)
                .replace("(r)", "")
                .replace("(tm)", "")
                .replaceAll("\\s+", " ")
                .trim();

        // ---- Xe2: Battlemage (discrete) + Lunar Lake (integrated) ----
        if (r.contains("battlemage") || r.contains("bmg")
                || r.contains("lunar") || r.contains("lnl")) {
            return IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE;
        }
        // Discrete Arc B-series, e.g. "Arc B580".
        if (r.matches(".*\\barc\\b.*\\bb\\d.*") || r.matches(".*\\bb[3-9]\\d0\\b.*")) {
            return IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE;
        }
        // Lunar Lake iGPU is branded "Arc Graphics 130V/140V" (Xe2 class).
        if (r.contains("140v") || r.contains("130v")
                || r.matches(".*\\barc graphics 1\\d0v\\b.*")) {
            return IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE;
        }

        // ---- Xe-HPG: discrete Arc Alchemist (A310-A770) ----
        // Match A-series numbers / explicit codenames only, NOT bare "Arc",
        // so integrated "Arc Graphics" parts do not land here.
        if (r.matches(".*\\barc\\b.*\\ba\\d.*") || r.matches(".*\\ba[3-9]\\d0m?\\b.*")
                || r.contains("alchemist") || r.contains("dg2")) {
            return IntelGpuGeneration.XE_HPG_ARC_ALCHEMIST;
        }

        // ---- Gen 12 / Xe-LP(G): integrated Arc Graphics (Meteor/Arrow Lake) ----
        // Branded "Intel Arc Graphics" with no A/B number. Any remaining bare
        // "arc" token is one of these integrated parts.
        if (r.contains("meteor lake") || r.contains("mtl")
                || r.contains("arrow lake") || r.contains("arl")
                || r.contains("arc")) {
            return IntelGpuGeneration.GEN12_XE_LP;
        }

        // ---- Gen 11 Ice Lake (before generic "xe" so Iris Plus is caught) ----
        if (r.contains("iris plus") || r.contains("ice lake") || r.contains("icl")) {
            return IntelGpuGeneration.GEN11_ICE_LAKE;
        }

        // ---- Gen 12 Xe-LP (Tiger/Alder/Raptor/Rocket Lake, Iris Xe, UHD 7xx) ----
        if (r.contains("xe") || r.contains("tgl") || r.contains("tiger lake")
                || r.contains("alder lake") || r.contains("adl")
                || r.contains("raptor lake") || r.contains("rpl")
                || r.contains("rocket lake") || r.contains("dg1")
                || r.contains("uhd graphics 7")
                || r.contains("12th gen") || r.contains("13th gen") || r.contains("14th gen")) {
            return IntelGpuGeneration.GEN12_XE_LP;
        }

        // ---- Pre-Gen 9 (Broadwell and older): any 4-digit HD/Iris number ----
        // Supported Gen 9/9.5 parts use 3-digit numbers (5xx/6xx); Sandy Bridge
        // through Broadwell use 4-digit numbers (2000-6200). No supported part
        // is branded "Graphics <4 digits>", so this cleanly excludes them.
        if (r.matches(".*\\bgraphics \\d{4}\\b.*")) {
            return IntelGpuGeneration.PRE_GEN9;
        }
        if (r.contains("broadwell") || r.contains("bdw")
                || r.contains("haswell") || r.contains("hsw")
                || r.contains("ivy bridge") || r.contains("ivb")
                || r.contains("sandy bridge") || r.contains("snb")
                || r.contains("ironlake") || r.contains("westmere")
                || r.contains("bay trail")) {
            return IntelGpuGeneration.PRE_GEN9;
        }

        // ---- Gen 9.5 Kaby/Coffee/Comet/Whiskey Lake (HD/UHD 6xx) ----
        if (r.contains("uhd graphics 6") || r.matches(".*\\bhd graphics 6[0-4]\\d\\b.*")
                || r.contains("kaby lake") || r.contains("kbl")
                || r.contains("coffee lake") || r.contains("cfl")
                || r.contains("comet lake") || r.contains("cml")
                || r.contains("whiskey lake")) {
            return IntelGpuGeneration.GEN9_5_KABY_COFFEE;
        }

        // ---- Gen 9 Skylake (HD 5xx, Iris 540/550/580) ----
        if (r.matches(".*\\bgraphics 5[0-8]\\d\\b.*")
                || r.contains("skylake") || r.contains("skl")) {
            return IntelGpuGeneration.GEN9_SKYLAKE;
        }

        // Bare "Intel HD Graphics" with no model number is the original
        // (Ironlake/Westmere) part - recognized but unsupported.
        if (r.contains("hd graphics")) {
            return IntelGpuGeneration.PRE_GEN9;
        }

        // Unrecognized Intel renderer: do NOT guess a profile.
        return IntelGpuGeneration.UNKNOWN;
    }
}
