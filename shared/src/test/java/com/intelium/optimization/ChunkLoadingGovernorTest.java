package com.intelium.optimization;

import com.intelium.RenderBackend;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ChunkLoadingGovernor")
class ChunkLoadingGovernorTest {

    private final ChunkLoadingGovernor governor = new ChunkLoadingGovernor();

    @Test
    @DisplayName("OpenGL Fast engages only after sustained low FPS")
    void sustainedLowFpsEngages() {
        for (int i = 1; i < ChunkLoadingGovernor.LOW_HOLD_TICKS; i++) {
            assertFalse(update(45), "engaged early at tick " + i);
        }
        assertTrue(update(45));
    }

    @Test
    @DisplayName("A single hitch does not change the defer mode")
    void singleHitchIgnored() {
        assertFalse(update(20));
        assertFalse(update(60));
        for (int i = 0; i < ChunkLoadingGovernor.LOW_HOLD_TICKS - 1; i++) {
            assertFalse(update(45));
        }
    }

    @Test
    @DisplayName("A severe collapse reacts faster but still rejects one sample")
    void severeCollapseFastPath() {
        for (int i = 1; i < ChunkLoadingGovernor.SEVERE_HOLD_TICKS; i++) {
            assertFalse(update(20), "engaged early at severe tick " + i);
        }
        assertTrue(update(20));
    }

    @Test
    @DisplayName("Recovery requires sustained headroom and survives a relapse")
    void recoveryHasHysteresis() {
        engage();
        for (int i = 0; i < ChunkLoadingGovernor.RECOVERY_HOLD_TICKS - 1; i++) {
            assertTrue(update(60));
        }
        assertTrue(update(45), "one relapse must reset the recovery window");
        for (int i = 1; i < ChunkLoadingGovernor.RECOVERY_HOLD_TICKS; i++) {
            assertTrue(update(60), "recovered early at tick " + i);
        }
        assertFalse(update(60));
    }

    @Test
    @DisplayName("Vulkan, Turbo and Off never receive OpenGL adaptive deferral")
    void scopeIsStrict() {
        for (int i = 0; i < 100; i++) {
            assertFalse(governor.update(ChunkLoadingMode.FAST, RenderBackend.VULKAN,
                    10, 60, true));
            assertFalse(governor.update(ChunkLoadingMode.TURBO, RenderBackend.OPENGL,
                    10, 60, true));
            assertFalse(governor.update(ChunkLoadingMode.OFF, RenderBackend.OPENGL,
                    10, 60, true));
        }
    }

    @Test
    @DisplayName("Unmeasurable samples hold state without advancing transitions")
    void unmeasurableSamplesHold() {
        for (int i = 0; i < ChunkLoadingGovernor.LOW_HOLD_TICKS - 1; i++) update(45);
        for (int i = 0; i < 100; i++) {
            assertFalse(governor.update(ChunkLoadingMode.FAST, RenderBackend.OPENGL,
                    5, 60, false));
        }
        // The pending 19 low ticks were discarded while measurement paused.
        assertFalse(update(45));

        engage();
        for (int i = 0; i < 100; i++) {
            assertTrue(governor.update(ChunkLoadingMode.FAST, RenderBackend.OPENGL,
                    120, 60, false));
        }
        assertTrue(governor.isThrottled());
    }

    @Test
    @DisplayName("Reset immediately restores the normal path")
    void reset() {
        engage();
        governor.reset();
        assertFalse(governor.isThrottled());
    }

    private boolean update(int fps) {
        return governor.update(ChunkLoadingMode.FAST, RenderBackend.OPENGL,
                fps, 60, true);
    }

    private void engage() {
        for (int i = 0; i < ChunkLoadingGovernor.LOW_HOLD_TICKS; i++) update(45);
        assertTrue(governor.isThrottled());
    }
}
