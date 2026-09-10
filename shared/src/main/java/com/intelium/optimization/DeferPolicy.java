package com.intelium.optimization;

import com.intelium.RenderBackend;

/**
 * Decides which chunk-upload deferral Sodium should be using, from the profile
 * the user picked, the backend in use, and the live pacing governor.
 *
 * <h2>Why "Max FPS" stopped meaning "show chunks sooner"</h2>
 *
 * <p>Sodium uploads completed chunk meshes on the render thread. Its default,
 * {@code DeferMode.ALWAYS}, holds a finished mesh until a frame has room for
 * it; the faster settings force the upload into the current frame instead.
 * Forcing uploads buys <em>chunk appearance latency</em> and pays for it in
 * frame time - which is the opposite of what a profile called "Max FPS"
 * promises. Lunar Client's own FPS guidance says the same thing: deferred chunk
 * updates give the best FPS, immediate removes pop-in but costs FPS.
 *
 * <p>Intelium used to apply one-frame deferral to every profile, so choosing
 * Max FPS and choosing Smooth produced the same upload behaviour and the label
 * was simply wrong. Each profile now gets the behaviour its name claims:
 *
 * <ul>
 *   <li><b>Max FPS</b> - conservative deferral. The render thread is never
 *       asked to absorb an upload it has no room for. Chunks appear a little
 *       later; frames arrive sooner and more evenly.</li>
 *   <li><b>Balanced</b> - one-frame deferral while there is headroom, falling
 *       back to conservative deferral under sustained measured pressure and
 *       returning only after the governor's recovery window. This is the only
 *       profile the governor steers.</li>
 *   <li><b>Smooth / streaming</b> - one-frame deferral always. This profile
 *       exists to keep terrain ahead of the player, so it is never slowed for
 *       frame pacing.</li>
 * </ul>
 *
 * <p><b>Turbo stays a user decision.</b> Zero-frame deferral is only ever used
 * when the user explicitly selects {@link ChunkLoadingMode#TURBO}, whatever the
 * profile and whatever the frame rate.
 *
 * <p><b>OpenGL only.</b> Vulkan and unknown backends keep the established
 * one-frame behaviour: their submission and upload paths schedule differently,
 * and Intelium has not measured them, so applying an OpenGL policy there would
 * be a guess. Pure logic, no Minecraft or Sodium types.
 */
public final class DeferPolicy {

    private DeferPolicy() {}

    /**
     * The deferral to apply right now.
     *
     * @param mode      the user's Fast Chunk Loading setting
     * @param profile   the user's optimization profile
     * @param backend   the detected graphics backend
     * @param throttled whether {@link ChunkLoadingGovernor} currently wants
     *                  conservative deferral (only consulted for Balanced on
     *                  OpenGL)
     */
    public static DeferDecision decide(ChunkLoadingMode mode, OptimizationProfile profile,
                                       RenderBackend backend, boolean throttled) {
        if (mode == null) mode = ChunkLoadingMode.OFF;
        if (profile == null) profile = OptimizationProfile.BALANCED;
        if (backend == null) backend = RenderBackend.UNKNOWN;

        if (mode == ChunkLoadingMode.OFF) return DeferDecision.KEEP_USER;
        // An explicit request for the lowest possible appearance latency. Never
        // adaptively slowed, on any backend, under any profile.
        if (mode == ChunkLoadingMode.TURBO) return DeferDecision.ZERO_FRAMES;

        // FAST. Off the OpenGL path, keep the behaviour Intelium has always
        // shipped rather than extrapolating an unmeasured policy.
        if (backend != RenderBackend.OPENGL) return DeferDecision.ONE_FRAME;

        return switch (profile) {
            case MAX_FPS -> DeferDecision.ALWAYS;
            case SMOOTH -> DeferDecision.ONE_FRAME;
            case BALANCED -> throttled ? DeferDecision.ALWAYS : DeferDecision.ONE_FRAME;
        };
    }

    /**
     * Whether the live pacing governor has any say for this combination. Only
     * Balanced on OpenGL Fast is governed; every other combination is a fixed
     * decision, so the governor is reset rather than left holding stale state.
     */
    public static boolean governed(ChunkLoadingMode mode, OptimizationProfile profile,
                                   RenderBackend backend) {
        return mode == ChunkLoadingMode.FAST
                && backend == RenderBackend.OPENGL
                && profile == OptimizationProfile.BALANCED;
    }
}
