package com.intelium.client;

import com.intelium.Intelium;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Resolves "is a menu screen open right now?" on 26.x without compiling
 * against a specific {@code Minecraft} member. The 26.x line reworked the GUI
 * stack for the Vulkan renderer and the classic {@code screen} field is gone;
 * rather than hard-code whichever name this week's snapshot uses (and crash on
 * the next), the accessor is resolved reflectively once: the first field - or
 * failing that, zero-arg method - on {@code Minecraft} whose type is named
 * {@code Screen}. If nothing matches, {@link #available()} stays false and the
 * menu FPS limit self-disables cleanly (and its option greys out), the same
 * fail-soft contract the Sodium mixin hooks follow.
 */
public final class MenuScreenProbe {

    private static final Field SCREEN_FIELD;
    private static final Method SCREEN_METHOD;

    static {
        Field f = null;
        Method m = null;
        try {
            for (Field candidate : Minecraft.class.getDeclaredFields()) {
                if (Modifier.isStatic(candidate.getModifiers())) continue;
                if ("Screen".equals(candidate.getType().getSimpleName())) {
                    candidate.setAccessible(true);
                    f = candidate;
                    break;
                }
            }
            if (f == null) {
                for (Method candidate : Minecraft.class.getMethods()) {
                    if (Modifier.isStatic(candidate.getModifiers())) continue;
                    if (candidate.getParameterCount() == 0
                            && "Screen".equals(candidate.getReturnType().getSimpleName())) {
                        m = candidate;
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            Intelium.LOGGER.warn("Intelium: could not probe Minecraft for the current-screen "
                    + "accessor; the menu FPS limit is disabled on this build.", t);
            f = null;
            m = null;
        }
        SCREEN_FIELD = f;
        SCREEN_METHOD = m;
        if (f == null && m == null) {
            Intelium.LOGGER.info("Intelium: no current-screen accessor found on this Minecraft "
                    + "build; the menu FPS limit is unavailable (everything else works).");
        }
    }

    private MenuScreenProbe() {}

    /** Whether this Minecraft build exposes the current screen at all. */
    public static boolean available() {
        return SCREEN_FIELD != null || SCREEN_METHOD != null;
    }

    /** True when a menu screen is open; false when unknown or unavailable. */
    public static boolean menuOpen(Minecraft mc) {
        try {
            if (SCREEN_FIELD != null) return SCREEN_FIELD.get(mc) != null;
            if (SCREEN_METHOD != null) return SCREEN_METHOD.invoke(mc) != null;
        } catch (Throwable t) {
            // Never let a reflective hiccup reach the render loop.
        }
        return false;
    }
}
