package com.intelium;

import org.lwjgl.opengl.GL11;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Identifies the host GPU from the OpenGL vendor/renderer strings and decides
 * whether Intelium should apply its Intel-specific tuning.
 *
 * <p>The decision logic lives in {@link #decide(String, String)}, a pure
 * function with no OpenGL or Fabric dependencies, so it can be unit-tested
 * exhaustively. {@link #detectOnce()} is the thin runtime wrapper that gathers
 * the GL strings on the render thread and publishes the result to the static
 * fields on {@link Intelium}.
 *
 * <p>Support policy (see README): Intel Gen 9 "Skylake" (HD Graphics 520 and
 * its generation-mates) and everything newer are supported. Anything older -
 * Broadwell and back - is recognized but reported unsupported. Unrecognized
 * Intel parts and all non-Intel vendors are reported unsupported; Intelium
 * never guesses a tuning profile for hardware it cannot place.
 */
public final class IntelGpuDetector {

    private static final AtomicBoolean DETECTED = new AtomicBoolean(false);

    private IntelGpuDetector() {}

    /** Outcome of a detection decision. {@code reasonKey} is null when active. */
    public static final class Result {
        public final IntelGpuGeneration generation;
        public final boolean compatible;
        public final String reasonKey;

        Result(IntelGpuGeneration generation, boolean compatible, String reasonKey) {
            this.generation = generation;
            this.compatible = compatible;
            this.reasonKey = reasonKey;
        }
    }

    /**
     * Runs detection exactly once. Both call sites (the first client tick and
     * the Sodium world-renderer constructor) run on the render thread with a
     * current GL context, so {@code glGetString} is safe. Subsequent calls are
     * no-ops.
     */
    public static void detectOnce() {
        if (!DETECTED.compareAndSet(false, true)) return;
        // Sodium missing / unsupported was already decided at init; do not let
        // GPU detection overwrite that environment decision.
        if (!Intelium.SODIUM_OK) return;

        String vendor = safeGetString(GL11.GL_VENDOR);
        String renderer = safeGetString(GL11.GL_RENDERER);

        Result r = decide(vendor, renderer);

        Intelium.DETECTED_RENDERER = renderer;
        Intelium.DETECTED_GENERATION = r.generation;
        Intelium.IS_COMPATIBLE = r.compatible;
        Intelium.DISABLED_REASON_KEY = r.reasonKey;

        Intelium.LOGGER.info(
                "Intelium status: gpu='{}' renderer='{}' detected={} active={}{}",
                vendor, renderer, r.generation.display, r.compatible,
                r.reasonKey == null ? "" : " (reason=" + r.reasonKey + ")");
    }

    /**
     * Pure detection decision. Given the GL vendor/renderer strings, returns
     * whether Intelium should be active and, if not, the lang key explaining
     * why. Never throws and never returns null.
     */
    static Result decide(String vendor, String renderer) {
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

    private static String safeGetString(int name) {
        try {
            String s = GL11.glGetString(name);
            return s == null ? "" : s;
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * Classifies an Intel GL_RENDERER string into a generation. Handles both
     * Windows driver marketing strings and Linux Mesa codename strings.
     * Returns {@link IntelGpuGeneration#UNKNOWN} for anything it cannot place,
     * and {@link IntelGpuGeneration#PRE_GEN9} for recognized-but-too-old parts.
     * Callers must NOT treat UNKNOWN as a supported part.
     */
    static IntelGpuGeneration classifyIntelRenderer(String renderer) {
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
