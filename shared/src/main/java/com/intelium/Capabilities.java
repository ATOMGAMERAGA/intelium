package com.intelium;

import java.util.EnumMap;
import java.util.Map;

/**
 * Which parts of Intelium actually attached to this game, and why the others
 * did not.
 *
 * <h2>What this replaces</h2>
 *
 * <p>Intelium used to carry a single {@code WORKER_TUNING_AVAILABLE} flag and
 * otherwise assume its hooks had landed. Under a client that ships its own
 * Sodium build - Lunar being the case that prompted this - a hook whose target
 * method had changed shape would quietly do nothing, while the settings screen
 * still showed the option enabled and the log said nothing after the first
 * launch. The user saw a feature that was on and had no effect.
 *
 * <p>Each {@link Capability} now records its own state and a reason, set once
 * during mixin application or first use. A feature that could not attach is
 * reported as unavailable, the UI can grey out that option alone, and the
 * startup diagnostic prints the whole picture in one place.
 *
 * <h2>Logging</h2>
 *
 * <p>{@link #disable(Capability, String)} is idempotent and logs only on the
 * transition, so a hook that fails on every entity cannot flood the log with
 * the same line - the failure is recorded once and never mentioned again.
 *
 * <p>Written during load and read from the render path, so state is held in a
 * volatile array. Reads are a plain array load and a compare.
 */
public final class Capabilities {

    private Capabilities() {}

    private static final Capability[] ALL = Capability.values();

    /**
     * Availability per capability. Volatile array reference plus per-slot
     * writes: a stale read can only ever be the previous boolean for one
     * capability, never a torn value, and every write happens during load or
     * once at first failure.
     */
    private static final boolean[] AVAILABLE = new boolean[ALL.length];
    private static final String[] REASONS = new String[ALL.length];

    static {
        // Nothing is assumed to work until whatever provides it says so.
        reset();
    }

    /** Marks a capability as attached and working. */
    public static void enable(Capability capability) {
        AVAILABLE[capability.ordinal()] = true;
        REASONS[capability.ordinal()] = null;
    }

    /**
     * Marks a capability unavailable, recording why. Logs exactly once per
     * capability, on the transition out of "available".
     */
    public static void disable(Capability capability, String reason) {
        int i = capability.ordinal();
        boolean wasAvailable = AVAILABLE[i];
        AVAILABLE[i] = false;
        if (wasAvailable || REASONS[i] == null) {
            REASONS[i] = reason;
            Intelium.LOGGER.warn("Intelium: {} unavailable - {}", capability.name(), reason);
        }
    }

    /** Records a capability's state without logging (load-time gating). */
    public static void set(Capability capability, boolean available, String reason) {
        AVAILABLE[capability.ordinal()] = available;
        REASONS[capability.ordinal()] = available ? null : reason;
    }

    /** Whether this capability attached. */
    public static boolean available(Capability capability) {
        return AVAILABLE[capability.ordinal()];
    }

    /** Why this capability is unavailable, or null when it is available. */
    public static String reason(Capability capability) {
        return REASONS[capability.ordinal()];
    }

    /** Every capability's state, for the diagnostic line and the settings UI. */
    public static Map<Capability, Boolean> snapshot() {
        Map<Capability, Boolean> out = new EnumMap<>(Capability.class);
        for (Capability c : ALL) {
            out.put(c, AVAILABLE[c.ordinal()]);
        }
        return out;
    }

    /** Compact {@code name=on|off} listing for the startup diagnostic. */
    public static String describe() {
        StringBuilder sb = new StringBuilder();
        for (Capability c : ALL) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(c.name().toLowerCase(java.util.Locale.ROOT))
              .append('=')
              .append(AVAILABLE[c.ordinal()] ? "on" : "off");
        }
        return sb.toString();
    }

    /** Restores the initial "nothing attached yet" state. Test support. */
    public static void reset() {
        for (int i = 0; i < ALL.length; i++) {
            AVAILABLE[i] = false;
            REASONS[i] = null;
        }
    }
}
