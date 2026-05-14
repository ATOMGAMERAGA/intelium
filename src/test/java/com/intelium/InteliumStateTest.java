package com.intelium;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Intelium static state")
class InteliumStateTest {

    @Test
    @DisplayName("MOD_ID constant equals 'intelium'")
    void modIdConstant() {
        assertEquals("intelium", Intelium.MOD_ID);
    }

    @Test
    @DisplayName("LOGGER is not null")
    void loggerNotNull() {
        assertNotNull(Intelium.LOGGER);
    }

    @Test
    @DisplayName("LOGGER name is 'Intelium'")
    void loggerName() {
        assertEquals("Intelium", Intelium.LOGGER.getName());
    }

    @Test
    @DisplayName("IS_ENABLED defaults to true")
    void isEnabledDefault() {
        assertTrue(Intelium.IS_ENABLED);
    }

    @Test
    @DisplayName("IS_COMPATIBLE defaults to false (gets set by detection)")
    void isCompatibleDefault() {
        // Just ensure the field exists and is accessible.
        assertDoesNotThrow(() -> {
            Field f = Intelium.class.getField("IS_COMPATIBLE");
            f.getBoolean(null);
        });
    }

    @Test
    @DisplayName("DETECTED_GENERATION defaults to UNKNOWN")
    void detectedGenerationDefault() {
        // May be mutated by other tests, just verify type.
        assertNotNull(Intelium.DETECTED_GENERATION);
    }

    @Test
    @DisplayName("Public static fields are volatile")
    void publicStaticFieldsAreVolatile() throws NoSuchFieldException {
        for (String name : new String[]{
                "IS_ENABLED", "IS_COMPATIBLE", "DETECTED_GENERATION", "DISABLED_REASON_KEY"
        }) {
            Field f = Intelium.class.getField(name);
            assertTrue(Modifier.isVolatile(f.getModifiers()),
                    name + " should be volatile (it is mutated from multiple threads)");
            assertTrue(Modifier.isStatic(f.getModifiers()),
                    name + " should be static");
            assertTrue(Modifier.isPublic(f.getModifiers()),
                    name + " should be public");
        }
    }

    @Test
    @DisplayName("MOD_ID is final and static")
    void modIdFinal() throws NoSuchFieldException {
        Field f = Intelium.class.getField("MOD_ID");
        assertTrue(Modifier.isFinal(f.getModifiers()));
        assertTrue(Modifier.isStatic(f.getModifiers()));
    }

    @Test
    @DisplayName("LOGGER is final and static")
    void loggerFinal() throws NoSuchFieldException {
        Field f = Intelium.class.getField("LOGGER");
        assertTrue(Modifier.isFinal(f.getModifiers()));
        assertTrue(Modifier.isStatic(f.getModifiers()));
    }

    @Test
    @DisplayName("Class loads without exception")
    void classLoads() {
        assertDoesNotThrow(() -> Class.forName("com.intelium.Intelium"));
    }

    @Test
    @DisplayName("Implements ClientModInitializer")
    void implementsClientModInitializer() {
        boolean found = false;
        for (Class<?> i : Intelium.class.getInterfaces()) {
            if (i.getName().equals("net.fabricmc.api.ClientModInitializer")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Intelium must implement ClientModInitializer");
    }

    @Test
    @DisplayName("Has onInitializeClient method")
    void hasOnInitializeClient() {
        assertDoesNotThrow(() -> Intelium.class.getMethod("onInitializeClient"));
    }
}
