package com.intelium.optimization;

/**
 * The chunk-upload deferral Intelium wants Sodium to use right now.
 *
 * <p>Deliberately not Sodium's own {@code DeferMode}: this enum lives in the
 * dependency-free shared source set so the policy that produces it can be
 * unit-tested without Sodium on the classpath. The per-version client glue maps
 * it onto the real enum constant and is the only code that touches Sodium.
 */
public enum DeferDecision {
    /**
     * Do not manage the setting: restore whatever the user chose and leave it
     * alone. This is what {@link ChunkLoadingMode#OFF} means.
     */
    KEEP_USER,
    /**
     * Sodium's conservative queue ({@code DeferMode.ALWAYS}). Completed meshes
     * wait for a frame with room to upload them, which is the best setting for
     * average FPS and frame-time consistency - at the cost of chunks appearing
     * later.
     */
    ALWAYS,
    /** One-frame deferral: chunks appear quickly at a small pacing cost. */
    ONE_FRAME,
    /** Zero-frame deferral: fastest possible appearance, roughest pacing. */
    ZERO_FRAMES
}
