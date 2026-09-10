package com.intelium;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Backend resolution on an OpenGL-default build")
class RenderBackendFallbackTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @DisplayName("A missing or blank backend name means OpenGL, because that is the default")
    void blankResolvesToOpenGl(String name) {
        // 26.1 and 26.2 both ship OpenGL as the default renderer, with Vulkan an
        // opt-in that names itself. Treating a silent device as UNKNOWN would
        // disable the whole OpenGL tuning path on the machines this mod targets.
        assertEquals(RenderBackend.OPENGL, RenderBackend.resolveDefaultOpenGl(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Vulkan", "vulkan", "VULKAN 1.3", "Blaze3D Vulkan backend"})
    @DisplayName("Vulkan still identifies itself and is honoured")
    void vulkanIsStillDetected(String name) {
        assertEquals(RenderBackend.VULKAN, RenderBackend.resolveDefaultOpenGl(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"OpenGL", "opengl 4.6", "Open GL", "Blaze3D OpenGL backend"})
    @DisplayName("An explicit OpenGL name is honoured")
    void openGlIsDetected(String name) {
        assertEquals(RenderBackend.OPENGL, RenderBackend.resolveDefaultOpenGl(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Metal", "WebGPU", "D3D12", "something-new"})
    @DisplayName("A named but unrecognised renderer stays Unknown rather than being guessed")
    void unknownRenderersAreNotGuessed(String name) {
        // A renderer that names itself something new is one Intelium knows
        // nothing about; handing it OpenGL's tuning would be a guess.
        assertEquals(RenderBackend.UNKNOWN, RenderBackend.resolveDefaultOpenGl(name));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Metal"})
    @DisplayName("The strict parser is unchanged for callers that want no assumption")
    void strictParserIsUnchanged(String name) {
        assertEquals(RenderBackend.UNKNOWN, RenderBackend.fromName(name));
    }
}
