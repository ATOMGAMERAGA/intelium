package com.intelium;

import java.util.Locale;

/**
 * Graphics API selected by Minecraft's Blaze3D device.
 *
 * <p>This deliberately stays in the dependency-free shared source set. The
 * 26.x detector can populate it from Blaze3D's {@code DeviceInfo}, while the
 * 1.21.11 detector records the known OpenGL backend directly. Optimization
 * policy can then account for backend-specific work without linking shared
 * code to either Minecraft or LWJGL.
 */
public enum RenderBackend {
    UNKNOWN("Unknown"),
    OPENGL("OpenGL"),
    VULKAN("Vulkan");

    public final String displayName;

    RenderBackend(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Classifies Blaze3D backend text defensively. Unknown future backends are
     * never guessed, so they retain the existing conservative worker policy.
     */
    public static RenderBackend fromName(String name) {
        if (name == null) return UNKNOWN;
        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.contains("vulkan")) return VULKAN;
        if (normalized.contains("opengl") || normalized.contains("open gl")) return OPENGL;
        return UNKNOWN;
    }
}
