package com.intelium.optimization;

import com.intelium.IntelGpuGeneration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.lwjgl.opengl.GL44;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BufferStrategy")
class BufferStrategyTest {

    @Test
    @DisplayName("PERSISTENT_FLAGS includes GL_MAP_PERSISTENT_BIT")
    void hasPersistentBit() {
        assertNotEquals(0, BufferStrategy.PERSISTENT_FLAGS & GL44.GL_MAP_PERSISTENT_BIT);
    }

    @Test
    @DisplayName("PERSISTENT_FLAGS includes GL_MAP_COHERENT_BIT")
    void hasCoherentBit() {
        assertNotEquals(0, BufferStrategy.PERSISTENT_FLAGS & GL44.GL_MAP_COHERENT_BIT);
    }

    @Test
    @DisplayName("PERSISTENT_FLAGS includes GL_MAP_WRITE_BIT")
    void hasWriteBit() {
        assertNotEquals(0, BufferStrategy.PERSISTENT_FLAGS & GL44.GL_MAP_WRITE_BIT);
    }

    @Test
    @DisplayName("PERSISTENT_FLAGS is the OR of all three bits")
    void persistentFlagsValue() {
        int expected = GL44.GL_MAP_PERSISTENT_BIT
                | GL44.GL_MAP_COHERENT_BIT
                | GL44.GL_MAP_WRITE_BIT;
        assertEquals(expected, BufferStrategy.PERSISTENT_FLAGS);
    }

    @ParameterizedTest
    @EnumSource(IntelGpuGeneration.class)
    @DisplayName("usePersistent matches gen.supported")
    void usePersistentMatchesSupported(IntelGpuGeneration gen) {
        assertEquals(gen.supported, BufferStrategy.usePersistent(gen));
    }

    @Test
    @DisplayName("UNKNOWN does not use persistent buffers")
    void unknownNoPersistent() {
        assertFalse(BufferStrategy.usePersistent(IntelGpuGeneration.UNKNOWN));
    }

    @Test
    @DisplayName("PRE_GEN9 does not use persistent buffers")
    void preGen9NoPersistent() {
        assertFalse(BufferStrategy.usePersistent(IntelGpuGeneration.PRE_GEN9));
    }

    @Test
    @DisplayName("Skylake uses persistent buffers")
    void skylakePersistent() {
        assertTrue(BufferStrategy.usePersistent(IntelGpuGeneration.GEN9_SKYLAKE));
    }

    @Test
    @DisplayName("Gen 9.5 uses persistent buffers")
    void gen95Persistent() {
        assertTrue(BufferStrategy.usePersistent(IntelGpuGeneration.GEN9_5_KABY_COFFEE));
    }

    @Test
    @DisplayName("Ice Lake uses persistent buffers")
    void iceLakePersistent() {
        assertTrue(BufferStrategy.usePersistent(IntelGpuGeneration.GEN11_ICE_LAKE));
    }

    @Test
    @DisplayName("Xe-LP uses persistent buffers")
    void xeLpPersistent() {
        assertTrue(BufferStrategy.usePersistent(IntelGpuGeneration.GEN12_XE_LP));
    }

    @Test
    @DisplayName("Arc uses persistent buffers")
    void arcPersistent() {
        assertTrue(BufferStrategy.usePersistent(IntelGpuGeneration.XE_HPG_ARC_ALCHEMIST));
    }

    @Test
    @DisplayName("Battlemage uses persistent buffers")
    void battlemagePersistent() {
        assertTrue(BufferStrategy.usePersistent(IntelGpuGeneration.XE2_LUNAR_BATTLEMAGE));
    }

    @Test
    @DisplayName("Class is final / utility helper")
    void classIsFinal() {
        assertTrue(java.lang.reflect.Modifier.isFinal(BufferStrategy.class.getModifiers()));
    }

    @Test
    @DisplayName("Constructor is private")
    void constructorPrivate() throws NoSuchMethodException {
        var ctor = BufferStrategy.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(ctor.getModifiers()));
    }

    @Test
    @DisplayName("PERSISTENT_FLAGS is positive (bitmask)")
    void persistentFlagsPositive() {
        assertTrue(BufferStrategy.PERSISTENT_FLAGS > 0);
    }
}
