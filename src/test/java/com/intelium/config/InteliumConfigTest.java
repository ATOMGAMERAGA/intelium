package com.intelium.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InteliumConfig defaults")
class InteliumConfigTest {

    @Test
    @DisplayName("Default enabled is true")
    void defaultEnabled() {
        assertTrue(new InteliumConfig().enabled);
    }

    @Test
    @DisplayName("Default drawCallBatching is true")
    void defaultDrawCallBatching() {
        assertTrue(new InteliumConfig().drawCallBatching);
    }

    @Test
    @DisplayName("Default persistentBuffers is true")
    void defaultPersistentBuffers() {
        assertTrue(new InteliumConfig().persistentBuffers);
    }

    @Test
    @DisplayName("Default chunkBuildWorkers is -1 (auto)")
    void defaultChunkBuildWorkers() {
        assertEquals(-1, new InteliumConfig().chunkBuildWorkers);
    }

    @Test
    @DisplayName("Default aggressiveCulling is true")
    void defaultAggressiveCulling() {
        assertTrue(new InteliumConfig().aggressiveCulling);
    }

    @Test
    @DisplayName("Default indirectBufferBytes is 0 (let Sodium decide)")
    void defaultIndirectBufferBytes() {
        assertEquals(0, new InteliumConfig().indirectBufferBytes);
    }

    @Test
    @DisplayName("Two fresh configs are equal by field")
    void freshConfigsEqualByField() {
        InteliumConfig a = new InteliumConfig();
        InteliumConfig b = new InteliumConfig();
        assertEquals(a.enabled, b.enabled);
        assertEquals(a.drawCallBatching, b.drawCallBatching);
        assertEquals(a.persistentBuffers, b.persistentBuffers);
        assertEquals(a.chunkBuildWorkers, b.chunkBuildWorkers);
        assertEquals(a.aggressiveCulling, b.aggressiveCulling);
        assertEquals(a.indirectBufferBytes, b.indirectBufferBytes);
    }

    @Test
    @DisplayName("Fields are public for GSON access")
    void fieldsArePublic() throws NoSuchFieldException {
        for (String name : new String[]{
                "enabled", "drawCallBatching", "persistentBuffers",
                "chunkBuildWorkers", "aggressiveCulling", "indirectBufferBytes"
        }) {
            var f = InteliumConfig.class.getDeclaredField(name);
            assertTrue(java.lang.reflect.Modifier.isPublic(f.getModifiers()),
                    name + " should be public for GSON");
        }
    }

    @Test
    @DisplayName("Mutability: enabled can be toggled")
    void mutableEnabled() {
        InteliumConfig c = new InteliumConfig();
        c.enabled = false;
        assertFalse(c.enabled);
    }

    @Test
    @DisplayName("Mutability: chunkBuildWorkers can be set to positive value")
    void mutableChunkWorkers() {
        InteliumConfig c = new InteliumConfig();
        c.chunkBuildWorkers = 4;
        assertEquals(4, c.chunkBuildWorkers);
    }

    @Test
    @DisplayName("Mutability: indirectBufferBytes accepts large values")
    void mutableLargeBuffer() {
        InteliumConfig c = new InteliumConfig();
        c.indirectBufferBytes = 16 * 1024 * 1024;
        assertEquals(16 * 1024 * 1024, c.indirectBufferBytes);
    }

    @Test
    @DisplayName("Class has public no-arg constructor (for GSON)")
    void hasNoArgCtor() throws NoSuchMethodException {
        var ctor = InteliumConfig.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPublic(ctor.getModifiers()));
    }

    @Test
    @DisplayName("Field count is 6")
    void fieldCount() {
        int publicFields = 0;
        for (var f : InteliumConfig.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isPublic(f.getModifiers())
                    && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                publicFields++;
            }
        }
        assertEquals(6, publicFields);
    }
}
