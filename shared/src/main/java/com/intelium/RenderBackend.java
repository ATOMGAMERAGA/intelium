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
 *
 * <p><b>26.2 is an OpenGL release.</b> Minecraft 26.2 ships OpenGL as the
 * default backend and adds Vulkan as an <em>experimental</em> opt-in; that is
 * the ordering Intelium tunes for. Vulkan support is kept intact, but OpenGL is
 * the first-class target.
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
     * Classifies Blaze3D backend text defensively. A name that is present but
     * unrecognised is never guessed at - an unknown future backend keeps the
     * conservative policy rather than being handed OpenGL's tuning.
     */
    public static RenderBackend fromName(String name) {
        if (name == null) return UNKNOWN;
        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.contains("vulkan")) return VULKAN;
        if (normalized.contains("opengl") || normalized.contains("open gl")) return OPENGL;
        return UNKNOWN;
    }

    /**
     * Resolves the backend for a build whose default renderer is OpenGL.
     *
     * <p>Blaze3D exposes the backend name through an optional accessor, and a
     * build that renames or drops it hands us an empty string. Treating that as
     * {@link #UNKNOWN} costs the whole OpenGL tuning path on exactly the
     * configuration Intelium targets, for no safety benefit: on 26.1 and 26.2
     * alike, a client that reports no backend name is running the default
     * OpenGL renderer, because Vulkan is opt-in and identifies itself.
     *
     * <p>So a <em>missing or blank</em> name resolves to {@link #OPENGL}, while
     * a name that is present but unrecognised still resolves to
     * {@link #UNKNOWN} - a renderer that names itself something new is a
     * renderer we know nothing about, and guessing there would be unsafe.
     */
    public static RenderBackend resolveDefaultOpenGl(String name) {
        if (name == null || name.trim().isEmpty()) return OPENGL;
        return fromName(name);
    }
}
