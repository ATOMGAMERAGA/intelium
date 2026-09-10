package com.intelium.optimization;

import com.intelium.IntelGpuGeneration;
import com.intelium.RenderBackend;

/**
 * Chooses how many CPU threads Sodium uses to build chunk meshes.
 *
 * <p><b>Why this matters for stutter.</b> On an Intel iGPU the GPU is the
 * bottleneck, not chunk meshing - so the goal here is to let chunk building keep
 * up with the player (so newly visible chunks are ready before they enter the
 * frustum when you walk or turn) <em>without</em> stealing so much CPU that the
 * render/main thread starves and average FPS drops.
 *
 * <p>The previous version hard-capped the older generations at 2 workers
 * regardless of how many cores were available. On a quad-core that left chunk
 * meshing unable to keep up while moving, producing exactly the kind of hitch
 * this mod is supposed to remove. This version instead scales with the core
 * count, always reserves headroom for the render/main thread, and lets the
 * {@link OptimizationProfile} shift the balance:
 *
 * <ul>
 *   <li>{@code MAX_FPS} - fewer workers (more CPU for the render thread).</li>
 *   <li>{@code BALANCED} - reserve ~2 cores for the game, rest build chunks.</li>
 *   <li>{@code SMOOTH} - reserve ~1 core, maximise chunk throughput.</li>
 * </ul>
 *
 * <p>A per-generation ceiling keeps weaker iGPUs from spawning more workers than
 * they can usefully feed, while letting Arc / Xe2 use more. Pure logic with no
 * Minecraft dependency, so it is exhaustively unit-tested.
 *
 * <p>Backend scheduling matters as well. Minecraft 26.2's Vulkan path runs
 * Sodium's asynchronous graph culling on a dedicated executor, while OpenGL
 * performs driver work and completed-mesh uploads on the render path. Automatic
 * mode therefore preserves two logical processors on both backends for Max FPS
 * and Balanced; OpenGL Smooth may deliberately use one more worker because that
 * profile explicitly prioritises streaming throughput. Manual overrides remain
 * exact.
 */
public final class ChunkBuilderTuner {

    /**
     * At or below this many logical processors, a Gen 9 part is assumed to be a
     * two-core SMT mobile chip and the low-core OpenGL ceiling applies.
     */
    static final int LOW_CORE_LOGICAL_PROCESSORS = 4;

    private ChunkBuilderTuner() {}

    /** Back-compat entry point: the {@link OptimizationProfile#BALANCED} value. */
    public static int recommendedWorkers(IntelGpuGeneration gen) {
        return recommendedWorkers(gen, OptimizationProfile.BALANCED);
    }

    /**
     * Generation- and profile-aware worker count, derived from the host core
     * count. Always {@code >= 1} and never more than the available CPUs.
     */
    public static int recommendedWorkers(IntelGpuGeneration gen, OptimizationProfile profile) {
        return recommendedWorkers(gen, profile, false);
    }

    /**
     * As {@link #recommendedWorkers(IntelGpuGeneration, OptimizationProfile)},
     * but {@code fastLoad} raises throughput (more workers, higher ceiling) so
     * chunks mesh and appear faster - used when "fast chunk loading" is on.
     */
    public static int recommendedWorkers(IntelGpuGeneration gen, OptimizationProfile profile,
                                         boolean fastLoad) {
        int cpu = Math.max(1, Runtime.getRuntime().availableProcessors());
        return recommendedWorkers(gen, profile, cpu, fastLoad, RenderBackend.UNKNOWN);
    }

    /**
     * Backend-aware automatic worker count. Vulkan reserves extra scheduling
     * headroom for Blaze3D submission and Sodium's asynchronous culling work.
     */
    public static int recommendedWorkers(IntelGpuGeneration gen, OptimizationProfile profile,
                                         boolean fastLoad, RenderBackend backend) {
        int cpu = Math.max(1, Runtime.getRuntime().availableProcessors());
        return recommendedWorkers(gen, profile, cpu, fastLoad, backend);
    }

    /** Back-compat / test entry point without the fast-load boost. */
    static int recommendedWorkers(IntelGpuGeneration gen, OptimizationProfile profile, int cpu) {
        return recommendedWorkers(gen, profile, cpu, false, RenderBackend.UNKNOWN);
    }

    /**
     * Core algorithm, with the CPU count injected so it can be tested
     * deterministically across machine sizes.
     */
    static int recommendedWorkers(IntelGpuGeneration gen, OptimizationProfile profile, int cpu,
                                  boolean fastLoad) {
        return recommendedWorkers(gen, profile, cpu, fastLoad, RenderBackend.UNKNOWN);
    }

    /** Backend-aware core algorithm with an injected CPU count for tests. */
    static int recommendedWorkers(IntelGpuGeneration gen, OptimizationProfile profile, int cpu,
                                  boolean fastLoad, RenderBackend backend) {
        cpu = Math.max(1, cpu);
        if (profile == null) profile = OptimizationProfile.BALANCED;
        if (backend == null) backend = RenderBackend.UNKNOWN;

        // Headroom: how many cores to leave for the render + main + audio
        // threads. SMOOTH gives chunk building one more core than BALANCED;
        // MAX_FPS gives the render thread the most room.
        int target;
        switch (profile) {
            case MAX_FPS:  target = cpu / 2;       break;  // protect the render thread
            case SMOOTH:   target = cpu - 1;       break;  // chunks keep up while moving
            case BALANCED:
            default:       target = cpu - 2;       break;
        }

        int ceiling = ceilingFor(gen);
        if (fastLoad) {
            // Favour throughput and lift the per-generation ceiling, but do
            // not erase the profile the user chose. In particular, the old
            // unconditional cpu-1 target made MAX_FPS almost identical to
            // SMOOTH whenever Fast Chunk Loading (the default) was enabled.
            if (profile == OptimizationProfile.MAX_FPS) {
                // A modest one-worker boost, still leaving at least two
                // logical processors to the game where the CPU allows it.
                int protectedCeiling = Math.max(1, cpu - 2);
                target = Math.min(protectedCeiling, target + 1);
            } else {
                target = Math.max(target, cpu - 1);
            }
            ceiling += 2;
        }
        // Never drop below a usable floor, never exceed the core count. On a
        // dual-core the floor is 1: two workers there would hand every core to
        // chunk meshing and starve the render thread - the opposite of what
        // every profile promises.
        int floor = cpu >= 3 ? 2 : 1;
        target = Math.max(floor, Math.min(target, cpu));

        // Low-core Gen 9 on OpenGL: the one configuration this mod is named
        // for, and the one where the generic "logical processors minus
        // headroom" arithmetic is most wrong. See lowCoreOpenGlCeiling.
        int lowCore = lowCoreOpenGlCeiling(gen, profile, cpu, backend);
        if (lowCore > 0) {
            target = Math.min(target, lowCore);
        }

        if (backend == RenderBackend.VULKAN) {
            // Vulkan submission and Sodium 0.9's async graph culling need CPU
            // time independently of chunk meshing. Preserve two logical
            // processors where possible; on 1-3 processor systems, one worker
            // is safer than oversubscribing the render path. This only affects
            // Auto because manual values bypass this tuner in the mixin.
            int vulkanCeiling = Math.max(1, cpu - 2);
            target = Math.min(target, vulkanCeiling);
        } else if (backend == RenderBackend.OPENGL
                && fastLoad && profile != OptimizationProfile.SMOOTH) {
            // Mesh uploads and a meaningful share of Intel OpenGL driver work
            // land on the render path. The old default Fast+Balanced policy
            // used cpu-1 workers, leaving only one logical processor for game,
            // driver and uploads; chunk arrival was quick but its frame-time
            // spikes were needlessly harsh. Smooth remains the explicit
            // throughput choice and is allowed to keep cpu-1.
            int openGlCeiling = Math.max(1, cpu - 2);
            target = Math.min(target, openGlCeiling);
        }

        return clamp(1, Math.min(target, ceiling), cpu);
    }

    /**
     * The worker ceiling for a low-core Gen 9 machine on OpenGL, or 0 when this
     * policy does not apply.
     *
     * <p><b>Why logical processors mislead here.</b> {@code availableProcessors()}
     * reports logical threads, not cores. The parts this mod exists for - an
     * HD Graphics 520 in a Core i5-6200U, and its generation-mates - are
     * <em>two physical cores with SMT</em>, so a machine that reports four
     * processors has two real cores to divide between chunk meshing, the render
     * thread, the game thread, and the Intel OpenGL driver's own work. The
     * generic policy hands three of those four logical processors to meshing on
     * Smooth, and two on Balanced, which is how a mod meant to remove hitches
     * ends up causing them: chunk workers and the render thread contend for the
     * same two cores, and the 1% low is what pays.
     *
     * <p>So on Gen 9 / Gen 9.5 with at most four logical processors, on OpenGL:
     *
     * <ul>
     *   <li><b>Max FPS</b> gets a single worker. One mesh thread on one core
     *       leaves the other core to the render thread and the driver, which is
     *       the entire point of the profile.</li>
     *   <li><b>Balanced and Smooth</b> get at most two. Two workers already
     *       saturate both physical cores; a third only adds scheduling
     *       overhead and cache pressure to a machine that has neither to
     *       spare.</li>
     * </ul>
     *
     * <p>This is a ceiling, never a floor: it can only take workers away from
     * the generic policy, so a machine that would already have been given fewer
     * keeps its lower number. Manual worker counts never reach this method -
     * the mixin honours an explicit value directly - and Vulkan, unknown
     * backends and newer generations are left entirely alone.
     */
    private static int lowCoreOpenGlCeiling(IntelGpuGeneration gen, OptimizationProfile profile,
                                            int cpu, RenderBackend backend) {
        if (backend != RenderBackend.OPENGL) return 0;
        if (cpu > LOW_CORE_LOGICAL_PROCESSORS) return 0;
        if (gen != IntelGpuGeneration.GEN9_SKYLAKE
                && gen != IntelGpuGeneration.GEN9_5_KABY_COFFEE) {
            return 0;
        }
        return profile == OptimizationProfile.MAX_FPS ? 1 : 2;
    }

    /**
     * Upper bound on workers per generation. Weaker iGPUs cannot usefully feed
     * many mesh-build threads (and over-subscribing only adds scheduling
     * overhead); discrete Arc / Xe2 can.
     */
    private static int ceilingFor(IntelGpuGeneration gen) {
        switch (gen) {
            case GEN9_SKYLAKE:           return 3;
            case GEN9_5_KABY_COFFEE:     return 3;
            case GEN11_ICE_LAKE:         return 4;
            case GEN12_XE_LP:            return 6;
            case XE_HPG_ARC_ALCHEMIST:   return 6;
            case XE2_LUNAR_BATTLEMAGE:   return 8;
            default:                     return 2; // UNKNOWN / PRE_GEN9: stay conservative
        }
    }

    private static int clamp(int lo, int v, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
