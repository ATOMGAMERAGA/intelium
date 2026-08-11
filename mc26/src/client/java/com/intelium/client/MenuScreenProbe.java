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
    /** {@code Screen.isPauseScreen()} on the resolved screen type, if present. */
    private static final Method PAUSE_METHOD;

    static {
        Field f = null;
        Method m = null;
        Method pause = null;
        try {
            // The exact historical name first: Minecraft.screen. Falling back
            // to "any member whose type is named Screen" is kept, but pinned
            // down - getDeclaredFields() order is unspecified, and a blind
            // "first match" could bind a pending/last-screen field that is
            // usually null, silently breaking the menu FPS limit.
            try {
                Field candidate = Minecraft.class.getDeclaredField("screen");
                if (!Modifier.isStatic(candidate.getModifiers())
                        && isScreenType(candidate.getType())) {
                    candidate.setAccessible(true);
                    f = candidate;
                }
            } catch (NoSuchFieldException ignored) {
                // Renamed; fall through to the scans below.
            }
            if (f == null) {
                for (Field candidate : Minecraft.class.getDeclaredFields()) {
                    if (Modifier.isStatic(candidate.getModifiers())) continue;
                    if (isScreenType(candidate.getType())
                            && candidate.getName().toLowerCase(java.util.Locale.ROOT)
                                    .contains("screen")) {
                        candidate.setAccessible(true);
                        f = candidate;
                        break;
                    }
                }
            }
            if (f == null) {
                // Method fallback: only getter-shaped names declared on
                // Minecraft itself. A blanket scan over all public methods
                // could bind a factory that allocates a screen per call -
                // this probe is invoked every tick.
                for (Method candidate : Minecraft.class.getDeclaredMethods()) {
                    if (Modifier.isStatic(candidate.getModifiers())) continue;
                    if (!Modifier.isPublic(candidate.getModifiers())) continue;
                    String name = candidate.getName().toLowerCase(java.util.Locale.ROOT);
                    boolean getterShaped = name.equals("screen") || name.equals("getscreen")
                            || name.equals("currentscreen") || name.equals("getcurrentscreen");
                    if (getterShaped && candidate.getParameterCount() == 0
                            && isScreenType(candidate.getReturnType())) {
                        m = candidate;
                        break;
                    }
                }
            }
            // Resolve Screen.isPauseScreen() from whichever type we found, so
            // the menu cap can exempt world-visible screens (chat, death
            // screen) instead of throttling gameplay mid-combat.
            Class<?> screenType = f != null ? f.getType() : (m != null ? m.getReturnType() : null);
            if (screenType != null) {
                try {
                    pause = screenType.getMethod("isPauseScreen");
                    if (pause.getReturnType() != boolean.class) pause = null;
                } catch (NoSuchMethodException ignored) {
                    // Renamed on this build: treat every screen as a menu, the
                    // pre-1.3.1 behaviour.
                }
            }
        } catch (Throwable t) {
            Intelium.LOGGER.warn("Intelium: could not probe Minecraft for the current-screen "
                    + "accessor; the menu FPS limit is disabled on this build.", t);
            f = null;
            m = null;
            pause = null;
        }
        SCREEN_FIELD = f;
        SCREEN_METHOD = m;
        PAUSE_METHOD = pause;
        if (f == null && m == null) {
            Intelium.LOGGER.info("Intelium: no current-screen accessor found on this Minecraft "
                    + "build; the menu FPS limit is unavailable (everything else works).");
        }
    }

    private MenuScreenProbe() {}

    /** Whether {@code type} is Minecraft's Screen class (name and package). */
    private static boolean isScreenType(Class<?> type) {
        return "Screen".equals(type.getSimpleName())
                && type.getName().startsWith("net.minecraft.");
    }

    /** Whether this Minecraft build exposes the current screen at all. */
    public static boolean available() {
        return SCREEN_FIELD != null || SCREEN_METHOD != null;
    }

    /**
     * True when a menu that hides gameplay is open; false when unknown or
     * unavailable. Screens the player watches the live world through (chat,
     * the death screen) do not count - capping those would drop the whole
     * game to the menu limit mid-combat.
     */
    public static boolean menuOpen(Minecraft mc) {
        try {
            Object screen = null;
            if (SCREEN_FIELD != null) {
                screen = SCREEN_FIELD.get(mc);
            } else if (SCREEN_METHOD != null) {
                screen = SCREEN_METHOD.invoke(mc);
            }
            if (screen == null) return false;
            return PAUSE_METHOD == null || (Boolean) PAUSE_METHOD.invoke(screen);
        } catch (Throwable t) {
            // Never let a reflective hiccup reach the render loop; failing
            // toward "no cap" is the harmless direction.
            return false;
        }
    }
}
