package com.intelium.detector;

import com.intelium.IntelGpuClassifier;
import com.intelium.IntelGpuGeneration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IntelGpuDetector.classifyIntelRenderer")
class IntelGpuRendererClassificationTest {

    /** Reflective accessor because classifyIntelRenderer is package-private static. */
    private static IntelGpuGeneration classify(String r) {
        try {
            Method m = IntelGpuClassifier.class.getDeclaredMethod("classifyIntelRenderer", String.class);
            m.setAccessible(true);
            return (IntelGpuGeneration) m.invoke(null, r);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ===== Null / empty / unknown: must be UNKNOWN (never a guessed profile) =====

    @Test
    @DisplayName("null returns UNKNOWN")
    void nullReturnsUnknown() {
        assertEquals(IntelGpuGeneration.UNKNOWN, classify(null));
    }

    @Test
    @DisplayName("empty string returns UNKNOWN (no best-guess)")
    void emptyReturnsUnknown() {
        assertEquals(IntelGpuGeneration.UNKNOWN, classify(""));
    }

    @Test
    @DisplayName("Random gibberish returns UNKNOWN")
    void gibberishReturnsUnknown() {
        assertEquals(IntelGpuGeneration.UNKNOWN, classify("zzz qqq aaa"));
    }

    @Test
    @DisplayName("Plain 'Intel' returns UNKNOWN (cannot place it)")
    void plainIntelUnknown() {
        assertEquals(IntelGpuGeneration.UNKNOWN, classify("Intel"));
    }

    @Test
    @DisplayName("'Intel(R)' alone returns UNKNOWN")
    void intelRAloneUnknown() {
        assertEquals(IntelGpuGeneration.UNKNOWN, classify("Intel(R)"));
    }

    // ===== Skylake (Gen 9) =====

    @ParameterizedTest
    @ValueSource(strings = {
            "Intel(R) HD Graphics 510",
            "Intel(R) HD Graphics 515",
            "Intel(R) HD Graphics 520",
            "Intel(R) HD Graphics 530",
            "Intel(R) Iris(R) Graphics 540",
            "Intel(R) Iris(R) Graphics 550",
            "Intel(R) Iris(R) Pro Graphics 580",
            "Mesa Intel(R) HD Graphics 520 (SKL GT2)",
            "Skylake GT2",
            "SKL Intel HD"
    })
    @DisplayName("Skylake HD 5xx / Iris 540-580 renderers map to GEN9_SKYLAKE")
    void classifySkylake(String renderer) {
        assertEquals(IntelGpuGeneration.GEN9_SKYLAKE, classify(renderer));
    }

    // ===== Gen 9.5 Kaby/Coffee/Comet =====

    @ParameterizedTest
    @ValueSource(strings = {
            "Intel(R) UHD Graphics 610",
            "Intel(R) UHD Graphics 620",
            "Intel(R) UHD Graphics 630",
            "Intel(R) HD Graphics 620",
            "Mesa Intel(R) UHD Graphics 620 (KBL GT2)",
            "Intel(R) Kaby Lake Graphics",
            "Intel(R) Coffee Lake Graphics",
            "Whiskey Lake UHD 620",
            "Comet Lake UHD 630"
    })
    @DisplayName("UHD 6xx + Kaby/Coffee/Whiskey/Comet renderers map to GEN9_5")
    void classifyGen95(String renderer) {
        assertEquals(IntelGpuGeneration.GEN9_5_KABY_COFFEE, classify(renderer));
    }

    // ===== Gen 11 Ice Lake =====

    @ParameterizedTest
    @ValueSource(strings = {
            "Intel(R) Iris(R) Plus Graphics",
            "Intel(R) Iris Plus Graphics 640",
            "Intel(R) Iris Plus Graphics 655",
            "Intel(R) Iris Plus G7",
            "Mesa Intel(R) Iris(R) Plus Graphics (ICL GT2)",
            "Ice Lake Iris Plus",
            "ICL Iris"
    })
    @DisplayName("Iris Plus / Ice Lake renderers map to GEN11_ICE_LAKE")
    void classifyGen11(String renderer) {
        assertEquals(IntelGpuGeneration.GEN11_ICE_LAKE, classify(renderer));
    }

    // ===== Gen 12 Xe-LP (incl. UHD 7xx and integrated Arc) =====

    @ParameterizedTest
    @ValueSource(strings = {
            "Intel(R) Iris(R) Xe Graphics",
            "Intel(R) Iris(R) Xe MAX Graphics",
            "Intel(R) UHD Graphics 710",
            "Intel(R) UHD Graphics 730",
            "Intel(R) UHD Graphics 750",
            "Intel(R) UHD Graphics 770",
            "Intel(R) UHD Graphics (Alder Lake)",
            "Intel(R) UHD Graphics for 12th Gen Intel(R) Processors",
            "Mesa Intel(R) Xe Graphics (TGL GT2)",
            "Tiger Lake Xe",
            "Alder Lake graphics",
            "Raptor Lake graphics",
            "Rocket Lake graphics",
            "Mesa Intel(R) graphics (RPL-S)"
    })
    @DisplayName("Xe / Tiger / Alder / Raptor / Rocket / UHD 7xx map to GEN12_XE_LP")
    void classifyGen12(String renderer) {
        assertEquals(IntelGpuGeneration.GEN12_XE_LP, classify(renderer));
    }

    // ===== Integrated Arc (Meteor/Arrow Lake) is iGPU Xe-LP, NOT discrete Arc =====

    @ParameterizedTest
    @ValueSource(strings = {
            "Intel(R) Arc(TM) Graphics",
            "Intel(R) Arc(TM) Graphics (MTL)",
            "Mesa Intel(R) Arc Graphics (MTL)",
            "Meteor Lake Arc Graphics",
            "Arrow Lake Arc Graphics",
            "Mesa Intel(R) Graphics (ARL)"
    })
    @DisplayName("Integrated 'Arc Graphics' (Meteor/Arrow Lake) maps to GEN12_XE_LP, not Alchemist")
    void classifyIntegratedArc(String renderer) {
        assertEquals(IntelGpuGeneration.GEN12_XE_LP, classify(renderer));
    }

    // ===== Arc Alchemist (discrete A-series) =====

    @ParameterizedTest
    @ValueSource(strings = {
            "Intel(R) Arc(TM) A770 Graphics",
            "Intel(R) Arc(TM) A750 Graphics",
            "Intel(R) Arc(TM) A580 Graphics",
            "Intel(R) Arc(TM) A380 Graphics",
            "Intel(R) Arc(TM) A310 Graphics",
            "Intel(R) Arc(TM) A370M Graphics",
            "Alchemist DG2-512",
            "Mesa Intel(R) Arc A770 (DG2)",
            "DG2 Alchemist"
    })
    @DisplayName("Arc A-series / Alchemist / DG2 map to XE_HPG_ARC_ALCHEMIST")
    void classifyArc(String renderer) {
        assertEquals(IntelGpuGeneration.XE_HPG_ARC_ALCHEMIST, classify(renderer));
    }

    // ===== Battlemage / Xe2 (incl. Lunar Lake iGPU 130V/140V) =====

    @ParameterizedTest
    @ValueSource(strings = {
            "Intel(R) Arc(TM) B580 Graphics",
            "Intel(R) Arc(TM) B570 Graphics",
            "Intel(R) Arc(TM) B770 Graphics",
            "Intel(R) Battlemage Graphics",
            "Mesa Intel(R) BMG Graphics",
            "Lunar Lake Xe2",
            "Intel(R) Arc(TM) Graphics 140V",
            "Intel(R) Arc(TM) Graphics 130V",
            "Mesa Intel(R) Graphics (LNL)"
    })
    @DisplayName("Arc B-series / Battlemage / BMG / Lunar 1x0V map to XE2_LUNAR_BATTLEMAGE")
    void classifyBattlemage(String renderer) {
        assertEquals(IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE, classify(renderer));
    }

    // ===== Pre-Gen9 (4-digit HD/Iris numbers, Sandy Bridge through Broadwell) =====

    @ParameterizedTest
    @ValueSource(strings = {
            "Intel(R) HD Graphics 2000",
            "Intel(R) HD Graphics 3000",
            "Intel(R) HD Graphics 2500",
            "Intel(R) HD Graphics 4000",
            "Intel(R) HD Graphics 4400",
            "Intel(R) HD Graphics 4600",
            "Intel(R) HD Graphics 5000",
            "Intel(R) Iris(R) Graphics 5100",
            "Intel(R) Iris(R) Pro Graphics 5200",
            "Intel(R) HD Graphics 5500",
            "Intel(R) HD Graphics 6000",
            "Intel(R) Iris(R) Graphics 6100",
            "Intel(R) Iris(R) Pro Graphics 6200",
            "Haswell Mobile",
            "Broadwell GT2",
            "Ivy Bridge Mobile",
            "Sandy Bridge Mobile",
            "Bay Trail HD Graphics",
            "Intel(R) HD Graphics"
    })
    @DisplayName("4-digit HD/Iris + pre-Skylake codenames map to PRE_GEN9 (unsupported)")
    void classifyPreGen9(String renderer) {
        assertEquals(IntelGpuGeneration.PRE_GEN9, classify(renderer));
    }

    // ===== Case insensitivity =====

    @ParameterizedTest
    @CsvSource({
            "INTEL(R) HD GRAPHICS 520, GEN9_SKYLAKE",
            "intel(r) hd graphics 520, GEN9_SKYLAKE",
            "Intel(R) Hd Graphics 520, GEN9_SKYLAKE",
            "INTEL(R) UHD GRAPHICS 630, GEN9_5_KABY_COFFEE",
            "INTEL(R) ARC(TM) A770 GRAPHICS, XE_HPG_ARC_ALCHEMIST",
            "INTEL(R) ARC(TM) B580 GRAPHICS, XE2_LUNAR_BATTLEMAGE",
            "INTEL(R) ARC(TM) GRAPHICS, GEN12_XE_LP"
    })
    @DisplayName("Classification is case-insensitive")
    void caseInsensitive(String renderer, IntelGpuGeneration expected) {
        assertEquals(expected, classify(renderer));
    }

    // ===== Mesa prefix =====

    @ParameterizedTest
    @CsvSource({
            "Mesa Intel(R) HD Graphics 520, GEN9_SKYLAKE",
            "Mesa Intel(R) UHD Graphics 620, GEN9_5_KABY_COFFEE",
            "Mesa Intel(R) Iris(R) Plus Graphics (ICL GT2), GEN11_ICE_LAKE",
            "Mesa Intel(R) Xe Graphics (TGL GT2), GEN12_XE_LP",
            "Mesa Intel(R) Arc Graphics (MTL), GEN12_XE_LP",
            "Mesa Intel(R) Arc A770, XE_HPG_ARC_ALCHEMIST"
    })
    @DisplayName("Linux Mesa renderer strings classify correctly")
    void mesaRenderers(String renderer, IntelGpuGeneration expected) {
        assertEquals(expected, classify(renderer));
    }

    // ===== Ordering matters (more recent generations win) =====

    @Test
    @DisplayName("Arc B5xx wins over generic Arc -> Battlemage")
    void arcBWinsOverPlainArc() {
        assertEquals(IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE,
                classify("Intel(R) Arc(TM) B580 Graphics"));
    }

    @Test
    @DisplayName("Iris Xe wins over generic Xe -> GEN12 (it's still Xe-LP)")
    void irisXeIsXeLp() {
        assertEquals(IntelGpuGeneration.GEN12_XE_LP, classify("Intel(R) Iris(R) Xe Graphics"));
    }

    // ===== Real-world renderer strings collected from drivers =====

    @ParameterizedTest
    @CsvSource(value = {
            "Intel(R) HD Graphics 520|GEN9_SKYLAKE",
            "Intel(R) HD Graphics 530|GEN9_SKYLAKE",
            "Intel(R) UHD Graphics 620|GEN9_5_KABY_COFFEE",
            "Intel(R) UHD Graphics 630|GEN9_5_KABY_COFFEE",
            "Intel(R) Iris(R) Plus Graphics 640|GEN11_ICE_LAKE",
            "Intel(R) Iris(R) Plus Graphics 655|GEN11_ICE_LAKE",
            "Intel(R) Iris(R) Xe Graphics|GEN12_XE_LP",
            "Intel(R) Iris(R) Xe MAX Graphics|GEN12_XE_LP",
            "Intel(R) UHD Graphics 770|GEN12_XE_LP",
            "Intel(R) UHD Graphics for 12th Gen Intel(R) Processors|GEN12_XE_LP",
            "Intel(R) Arc(TM) Graphics|GEN12_XE_LP",
            "Intel(R) Arc(TM) A380 Graphics|XE_HPG_ARC_ALCHEMIST",
            "Intel(R) Arc(TM) A770 Graphics|XE_HPG_ARC_ALCHEMIST",
            "Intel(R) Arc(TM) B580 Graphics|XE2_LUNAR_BATTLEMAGE",
            "Intel(R) Arc(TM) Graphics 140V|XE2_LUNAR_BATTLEMAGE",
            "Intel(R) HD Graphics 4000|PRE_GEN9",
            "Intel(R) HD Graphics 6000|PRE_GEN9"
    }, delimiter = '|')
    @DisplayName("Real-world Intel renderer strings classify correctly")
    void realWorldStrings(String renderer, IntelGpuGeneration expected) {
        assertEquals(expected, classify(renderer));
    }

    @Test
    @DisplayName("Classification never returns null for non-null input")
    void neverNullForNonNullInput() {
        String[] inputs = {
                "x", "abc", "123", "Intel", "GPU", "Mesa", "Apple",
                "VMware", "llvmpipe", "software", "        ", "\n\t"
        };
        for (String s : inputs) {
            assertNotNull(classify(s), "classify must not return null for: " + s);
        }
    }

    @Test
    @DisplayName("Same input twice gives same output (deterministic)")
    void deterministic() {
        assertEquals(classify("Intel(R) HD Graphics 520"),
                classify("Intel(R) HD Graphics 520"));
        assertEquals(classify("Intel(R) Arc(TM) A770 Graphics"),
                classify("Intel(R) Arc(TM) A770 Graphics"));
    }

    @Test
    @DisplayName("Leading whitespace tolerated")
    void leadingWhitespace() {
        assertEquals(IntelGpuGeneration.GEN9_SKYLAKE,
                classify("   Intel(R) HD Graphics 520"));
    }

    @Test
    @DisplayName("Trailing extra info tolerated")
    void trailingInfo() {
        assertEquals(IntelGpuGeneration.XE_HPG_ARC_ALCHEMIST,
                classify("Intel(R) Arc(TM) A770 Graphics on Windows 11"));
    }
}
