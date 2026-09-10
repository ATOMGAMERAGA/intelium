package com.intelium;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Capability registry")
class CapabilitiesTest {

    @BeforeEach
    void clean() {
        Capabilities.reset();
    }

    @ParameterizedTest
    @EnumSource(Capability.class)
    @DisplayName("Nothing is assumed to work until something says so")
    void defaultsToUnavailable(Capability capability) {
        assertFalse(Capabilities.available(capability));
    }

    @ParameterizedTest
    @EnumSource(Capability.class)
    @DisplayName("Each capability fails on its own, never taking the others down")
    void failuresAreIndependent(Capability failing) {
        for (Capability c : Capability.values()) {
            Capabilities.set(c, true, null);
        }
        Capabilities.set(failing, false, "target method changed signature");

        assertFalse(Capabilities.available(failing));
        for (Capability other : Capability.values()) {
            if (other == failing) continue;
            assertTrue(Capabilities.available(other),
                    other + " must survive " + failing + " being unavailable");
        }
    }

    @Test
    @DisplayName("An unavailable capability carries a reason the UI can show")
    void reasonIsRecorded() {
        Capabilities.set(Capability.ENTITY_CULLING, false,
                "EntityRenderer.shouldRender changed signature");
        assertNotNull(Capabilities.reason(Capability.ENTITY_CULLING));
        assertTrue(Capabilities.reason(Capability.ENTITY_CULLING).contains("shouldRender"));
    }

    @Test
    @DisplayName("An available capability carries no stale reason")
    void enablingClearsTheReason() {
        Capabilities.set(Capability.DEFER_TUNING, false, "not reachable");
        Capabilities.enable(Capability.DEFER_TUNING);
        assertTrue(Capabilities.available(Capability.DEFER_TUNING));
        assertNull(Capabilities.reason(Capability.DEFER_TUNING));
    }

    @Test
    @DisplayName("Repeated failures keep the first reason instead of churning")
    void disableIsIdempotent() {
        Capabilities.disable(Capability.PARTICLE_LIMITER, "first reason");
        Capabilities.disable(Capability.PARTICLE_LIMITER, "second reason");
        Capabilities.disable(Capability.PARTICLE_LIMITER, "third reason");
        // The point is that a hook failing on every particle spawn cannot flood
        // the log: the state is recorded once and never re-announced.
        assertEquals("first reason", Capabilities.reason(Capability.PARTICLE_LIMITER));
        assertFalse(Capabilities.available(Capability.PARTICLE_LIMITER));
    }

    @Test
    @DisplayName("The snapshot covers every capability")
    void snapshotIsComplete() {
        assertEquals(Capability.values().length, Capabilities.snapshot().size());
    }

    @Test
    @DisplayName("The diagnostic line names every capability and its state")
    void describeListsEverything() {
        Capabilities.set(Capability.WORKER_TUNING, true, null);
        String described = Capabilities.describe();
        for (Capability c : Capability.values()) {
            assertTrue(described.contains(c.name().toLowerCase(java.util.Locale.ROOT)),
                    "the report must mention " + c);
        }
        assertTrue(described.contains("worker_tuning=on"));
        assertTrue(described.contains("entity_culling=off"));
    }

    @ParameterizedTest
    @EnumSource(Capability.class)
    @DisplayName("Every capability has a lang key for its greyed-out tooltip")
    void everyCapabilityHasADisplayKey(Capability capability) {
        assertNotNull(capability.displayKey);
        assertTrue(capability.displayKey.startsWith("intelium.capability."));
    }
}
