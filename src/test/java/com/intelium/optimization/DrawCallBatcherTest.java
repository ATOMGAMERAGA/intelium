package com.intelium.optimization;

import com.intelium.IntelGpuGeneration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DrawCallBatcher")
class DrawCallBatcherTest {

    @Test
    @DisplayName("Battlemage selects 256 batch size")
    void battlemage256() {
        DrawCallBatcher.beginFrame(IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE);
        assertEquals(256, DrawCallBatcher.currentBatchSize());
    }

    @Test
    @DisplayName("Arc Alchemist selects 256 batch size")
    void arc256() {
        DrawCallBatcher.beginFrame(IntelGpuGeneration.XE_HPG_ARC_ALCHEMIST);
        assertEquals(256, DrawCallBatcher.currentBatchSize());
    }

    @Test
    @DisplayName("Xe-LP selects 128 batch size")
    void xeLp128() {
        DrawCallBatcher.beginFrame(IntelGpuGeneration.GEN12_XE_LP);
        assertEquals(128, DrawCallBatcher.currentBatchSize());
    }

    @Test
    @DisplayName("Ice Lake selects 96 batch size")
    void iceLake96() {
        DrawCallBatcher.beginFrame(IntelGpuGeneration.GEN11_ICE_LAKE);
        assertEquals(96, DrawCallBatcher.currentBatchSize());
    }

    @Test
    @DisplayName("Skylake selects 64 batch size (default)")
    void skylake64() {
        DrawCallBatcher.beginFrame(IntelGpuGeneration.GEN9_SKYLAKE);
        assertEquals(64, DrawCallBatcher.currentBatchSize());
    }

    @Test
    @DisplayName("Gen 9.5 selects 64 batch size (default)")
    void gen95Default() {
        DrawCallBatcher.beginFrame(IntelGpuGeneration.GEN9_5_KABY_COFFEE);
        assertEquals(64, DrawCallBatcher.currentBatchSize());
    }

    @Test
    @DisplayName("UNKNOWN selects default 64")
    void unknownDefault() {
        DrawCallBatcher.beginFrame(IntelGpuGeneration.UNKNOWN);
        assertEquals(64, DrawCallBatcher.currentBatchSize());
    }

    @Test
    @DisplayName("PRE_GEN9 selects default 64")
    void preGen9Default() {
        DrawCallBatcher.beginFrame(IntelGpuGeneration.PRE_GEN9);
        assertEquals(64, DrawCallBatcher.currentBatchSize());
    }

    @ParameterizedTest
    @EnumSource(IntelGpuGeneration.class)
    @DisplayName("beginFrame never crashes for any gen")
    void beginFrameNeverCrashes(IntelGpuGeneration gen) {
        assertDoesNotThrow(() -> DrawCallBatcher.beginFrame(gen));
    }

    @Test
    @DisplayName("endFrame never crashes")
    void endFrameNeverCrashes() {
        assertDoesNotThrow(DrawCallBatcher::endFrame);
    }

    @ParameterizedTest
    @EnumSource(IntelGpuGeneration.class)
    @DisplayName("currentBatchSize is positive after begin for any gen")
    void positiveAfterBegin(IntelGpuGeneration gen) {
        DrawCallBatcher.beginFrame(gen);
        assertTrue(DrawCallBatcher.currentBatchSize() > 0);
    }

    @ParameterizedTest
    @EnumSource(IntelGpuGeneration.class)
    @DisplayName("Batch size is a power-of-two-ish multiple of 32")
    void batchSizeReasonable(IntelGpuGeneration gen) {
        DrawCallBatcher.beginFrame(gen);
        int s = DrawCallBatcher.currentBatchSize();
        assertTrue(s >= 32 && s <= 1024,
                "expected batch size in [32, 1024], got " + s);
    }

    @Test
    @DisplayName("Larger GPU -> larger or equal batch size")
    void monotonicity() {
        DrawCallBatcher.beginFrame(IntelGpuGeneration.GEN9_SKYLAKE);
        int skylake = DrawCallBatcher.currentBatchSize();
        DrawCallBatcher.beginFrame(IntelGpuGeneration.GEN11_ICE_LAKE);
        int iceLake = DrawCallBatcher.currentBatchSize();
        DrawCallBatcher.beginFrame(IntelGpuGeneration.GEN12_XE_LP);
        int xeLp = DrawCallBatcher.currentBatchSize();
        DrawCallBatcher.beginFrame(IntelGpuGeneration.XE_HPG_ARC_ALCHEMIST);
        int arc = DrawCallBatcher.currentBatchSize();
        assertTrue(skylake <= iceLake);
        assertTrue(iceLake <= xeLp);
        assertTrue(xeLp <= arc);
    }

    @Test
    @DisplayName("Calling endFrame after beginFrame keeps batch size readable")
    void readableAfterEnd() {
        DrawCallBatcher.beginFrame(IntelGpuGeneration.GEN12_XE_LP);
        int before = DrawCallBatcher.currentBatchSize();
        DrawCallBatcher.endFrame();
        int after = DrawCallBatcher.currentBatchSize();
        assertEquals(before, after); // endFrame does not reset the size
    }

    @Test
    @DisplayName("Class is final / utility helper")
    void classIsFinal() {
        assertTrue(java.lang.reflect.Modifier.isFinal(DrawCallBatcher.class.getModifiers()));
    }

    @Test
    @DisplayName("Constructor is private")
    void constructorPrivate() throws NoSuchMethodException {
        var ctor = DrawCallBatcher.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(ctor.getModifiers()));
    }

    @Test
    @DisplayName("Multiple beginFrame calls switch correctly")
    void multipleBeginFrameCalls() {
        DrawCallBatcher.beginFrame(IntelGpuGeneration.GEN9_SKYLAKE);
        assertEquals(64, DrawCallBatcher.currentBatchSize());
        DrawCallBatcher.beginFrame(IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE);
        assertEquals(256, DrawCallBatcher.currentBatchSize());
        DrawCallBatcher.beginFrame(IntelGpuGeneration.GEN11_ICE_LAKE);
        assertEquals(96, DrawCallBatcher.currentBatchSize());
    }
}
