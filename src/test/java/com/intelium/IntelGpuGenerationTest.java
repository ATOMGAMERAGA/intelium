package com.intelium;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IntelGpuGeneration enum")
class IntelGpuGenerationTest {

    @Test
    @DisplayName("UNKNOWN value exists and is not supported")
    void unknownIsNotSupported() {
        assertFalse(IntelGpuGeneration.UNKNOWN.supported);
    }

    @Test
    @DisplayName("PRE_GEN9 value exists and is not supported")
    void preGen9IsNotSupported() {
        assertFalse(IntelGpuGeneration.PRE_GEN9.supported);
    }

    @Test
    @DisplayName("GEN9_SKYLAKE is supported")
    void gen9SkylakeSupported() {
        assertTrue(IntelGpuGeneration.GEN9_SKYLAKE.supported);
    }

    @Test
    @DisplayName("GEN9_5_KABY_COFFEE is supported")
    void gen95Supported() {
        assertTrue(IntelGpuGeneration.GEN9_5_KABY_COFFEE.supported);
    }

    @Test
    @DisplayName("GEN11_ICE_LAKE is supported")
    void gen11Supported() {
        assertTrue(IntelGpuGeneration.GEN11_ICE_LAKE.supported);
    }

    @Test
    @DisplayName("GEN12_XE_LP is supported")
    void gen12Supported() {
        assertTrue(IntelGpuGeneration.GEN12_XE_LP.supported);
    }

    @Test
    @DisplayName("XE_HPG_ARC_ALCHEMIST is supported")
    void arcSupported() {
        assertTrue(IntelGpuGeneration.XE_HPG_ARC_ALCHEMIST.supported);
    }

    @Test
    @DisplayName("XE2_LUNAR_BATTLEMAGE is supported")
    void battlemageSupported() {
        assertTrue(IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE.supported);
    }

    @ParameterizedTest
    @EnumSource(IntelGpuGeneration.class)
    @DisplayName("Every enum value has a non-null display name")
    void everyValueHasDisplay(IntelGpuGeneration gen) {
        assertNotNull(gen.display, "display must not be null for " + gen.name());
    }

    @ParameterizedTest
    @EnumSource(IntelGpuGeneration.class)
    @DisplayName("Every enum value has a non-empty display name")
    void everyValueHasNonEmptyDisplay(IntelGpuGeneration gen) {
        assertFalse(gen.display.isEmpty(), "display must not be empty for " + gen.name());
    }

    @ParameterizedTest
    @EnumSource(IntelGpuGeneration.class)
    @DisplayName("Display name trimmed equals itself (no surrounding whitespace)")
    void displayTrimmed(IntelGpuGeneration gen) {
        assertEquals(gen.display, gen.display.trim(),
                "display must be trimmed for " + gen.name());
    }

    @ParameterizedTest
    @EnumSource(IntelGpuGeneration.class)
    @DisplayName("genNumber is non-negative")
    void genNumberNonNegative(IntelGpuGeneration gen) {
        assertTrue(gen.genNumber >= 0, "genNumber must be non-negative for " + gen.name());
    }

    @Test
    @DisplayName("Gen numbers ascend from skylake to battlemage")
    void genNumbersAscend() {
        assertTrue(IntelGpuGeneration.GEN9_SKYLAKE.genNumber
                <= IntelGpuGeneration.GEN9_5_KABY_COFFEE.genNumber);
        assertTrue(IntelGpuGeneration.GEN9_5_KABY_COFFEE.genNumber
                <= IntelGpuGeneration.GEN11_ICE_LAKE.genNumber);
        assertTrue(IntelGpuGeneration.GEN11_ICE_LAKE.genNumber
                <= IntelGpuGeneration.GEN12_XE_LP.genNumber);
        assertTrue(IntelGpuGeneration.GEN12_XE_LP.genNumber
                <= IntelGpuGeneration.XE_HPG_ARC_ALCHEMIST.genNumber);
        assertTrue(IntelGpuGeneration.XE_HPG_ARC_ALCHEMIST.genNumber
                <= IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE.genNumber);
    }

    @Test
    @DisplayName("All supported entries have genNumber >= 9")
    void supportedEntriesAreAtLeastGen9() {
        for (IntelGpuGeneration gen : IntelGpuGeneration.values()) {
            if (gen.supported) {
                assertTrue(gen.genNumber >= 9,
                        gen.name() + " is marked supported but genNumber is " + gen.genNumber);
            }
        }
    }

    @Test
    @DisplayName("Exactly two unsupported entries: UNKNOWN, PRE_GEN9")
    void exactlyTwoUnsupported() {
        Set<IntelGpuGeneration> unsupported = EnumSet.noneOf(IntelGpuGeneration.class);
        for (IntelGpuGeneration gen : IntelGpuGeneration.values()) {
            if (!gen.supported) unsupported.add(gen);
        }
        assertEquals(EnumSet.of(IntelGpuGeneration.UNKNOWN, IntelGpuGeneration.PRE_GEN9),
                unsupported);
    }

    @Test
    @DisplayName("Exactly six supported entries")
    void exactlySixSupported() {
        int supported = 0;
        for (IntelGpuGeneration gen : IntelGpuGeneration.values()) {
            if (gen.supported) supported++;
        }
        assertEquals(6, supported);
    }

    @Test
    @DisplayName("Enum has eight values total")
    void eightValuesTotal() {
        assertEquals(8, IntelGpuGeneration.values().length);
    }

    @Test
    @DisplayName("Enum names are unique")
    void enumNamesUnique() {
        Set<String> seen = new HashSet<>();
        for (IntelGpuGeneration gen : IntelGpuGeneration.values()) {
            assertTrue(seen.add(gen.name()), "duplicate enum name: " + gen.name());
        }
    }

    @Test
    @DisplayName("Enum displays are unique")
    void enumDisplaysUnique() {
        Set<String> seen = new HashSet<>();
        for (IntelGpuGeneration gen : IntelGpuGeneration.values()) {
            assertTrue(seen.add(gen.display), "duplicate display: " + gen.display);
        }
    }

    @Test
    @DisplayName("valueOf works for every enum value")
    void valueOfWorks() {
        for (IntelGpuGeneration gen : IntelGpuGeneration.values()) {
            assertEquals(gen, IntelGpuGeneration.valueOf(gen.name()));
        }
    }

    @Test
    @DisplayName("UNKNOWN is the first ordinal")
    void unknownIsFirst() {
        assertEquals(0, IntelGpuGeneration.UNKNOWN.ordinal());
    }

    @Test
    @DisplayName("XE2_LUNAR_BATTLEMAGE is the last ordinal")
    void battlemageIsLast() {
        assertEquals(IntelGpuGeneration.values().length - 1,
                IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE.ordinal());
    }

    @Test
    @DisplayName("PRE_GEN9 has genNumber 0")
    void preGen9GenZero() {
        assertEquals(0, IntelGpuGeneration.PRE_GEN9.genNumber);
    }

    @Test
    @DisplayName("UNKNOWN has genNumber 0")
    void unknownGenZero() {
        assertEquals(0, IntelGpuGeneration.UNKNOWN.genNumber);
    }

    @Test
    @DisplayName("Display name of GEN9_SKYLAKE mentions HD 520")
    void skylakeDisplayMentionsHd520() {
        assertTrue(IntelGpuGeneration.GEN9_SKYLAKE.display.contains("520"),
                "Expected skylake display to mention HD 520, was: "
                        + IntelGpuGeneration.GEN9_SKYLAKE.display);
    }

    @Test
    @DisplayName("Display name of XE_HPG_ARC_ALCHEMIST mentions Arc")
    void arcDisplayMentionsArc() {
        assertTrue(IntelGpuGeneration.XE_HPG_ARC_ALCHEMIST.display.toLowerCase().contains("arc"));
    }

    @Test
    @DisplayName("Display name of XE2_LUNAR_BATTLEMAGE mentions Battlemage")
    void battlemageDisplayMentionsBattlemage() {
        assertTrue(IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE.display.toLowerCase().contains("battlemage"));
    }

    @Test
    @DisplayName("Display name of GEN12_XE_LP mentions Xe")
    void xeLpDisplayMentionsXe() {
        assertTrue(IntelGpuGeneration.GEN12_XE_LP.display.toLowerCase().contains("xe"));
    }

    @Test
    @DisplayName("Display name of GEN11_ICE_LAKE mentions Ice Lake")
    void iceLakeDisplayMentionsIceLake() {
        assertTrue(IntelGpuGeneration.GEN11_ICE_LAKE.display.toLowerCase().contains("ice lake"));
    }

    @Test
    @DisplayName("Display name of GEN9_5_KABY_COFFEE mentions Kaby or Coffee")
    void gen95DisplayMentions() {
        String d = IntelGpuGeneration.GEN9_5_KABY_COFFEE.display.toLowerCase();
        assertTrue(d.contains("kaby") || d.contains("coffee"));
    }

    @Test
    @DisplayName("UNKNOWN display equals 'Unknown'")
    void unknownDisplayIsUnknown() {
        assertEquals("Unknown", IntelGpuGeneration.UNKNOWN.display);
    }

    @Test
    @DisplayName("Final fields are final by reflection")
    void fieldsAreFinal() throws Exception {
        for (String fieldName : new String[]{"genNumber", "display", "supported"}) {
            var f = IntelGpuGeneration.class.getDeclaredField(fieldName);
            assertTrue(java.lang.reflect.Modifier.isFinal(f.getModifiers()),
                    fieldName + " should be final");
            assertTrue(java.lang.reflect.Modifier.isPublic(f.getModifiers()),
                    fieldName + " should be public");
        }
    }

    @Test
    @DisplayName("Enum is loaded by classloader without errors")
    void enumLoads() {
        assertDoesNotThrow(() -> Class.forName("com.intelium.IntelGpuGeneration"));
    }

    @Test
    @DisplayName("All values reachable from values()")
    void valuesIncludesAll() {
        Set<IntelGpuGeneration> all = EnumSet.allOf(IntelGpuGeneration.class);
        for (IntelGpuGeneration gen : IntelGpuGeneration.values()) {
            assertTrue(all.contains(gen));
        }
    }
}
