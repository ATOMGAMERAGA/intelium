package com.intelium.optimization;

/**
 * The live state of Intelium's Render Budget Engine - the one object the
 * render-path hooks talk to.
 *
 * <h2>Shape of the thing</h2>
 *
 * <p>The hooks that consume this run inside the hottest loops the game has:
 * once per entity, once per block entity, once per particle spawn. So all the
 * deciding - reading the config, reading the camera, working out how hard to
 * push - happens exactly once per client tick in {@link #update}, and what is
 * left at the call site is a couple of field reads and a compare.
 *
 * <p>Everything is static because there is one game and one camera; the fields
 * are volatile because they are written from the client tick and read from the
 * render path, which are the same thread in vanilla Minecraft but need not stay
 * that way. The two counters ({@link FrameBudget}, {@link TickBudget}) are only
 * ever touched from their own thread, as documented on each.
 *
 * <p>Pure logic, no Minecraft types - the per-version glue supplies the camera
 * numbers and the mixins ask the questions.
 */
public final class RenderBudget {

    private RenderBudget() {}

    /** Never squeeze the block-entity budget below this, however bad it gets. */
    private static final int BLOCK_ENTITY_FLOOR = 64;
    /** Never squeeze the particle spawn budget below this. */
    private static final int PARTICLE_FLOOR = 128;

    private static volatile boolean active;
    private static volatile double pixelScale;
    private static volatile double entityMinPixels;
    private static volatile int blockEntityLimit;
    private static volatile int particleLimit;

    private static final FrameBudget BLOCK_ENTITIES = new FrameBudget();
    private static final TickBudget PARTICLES = new TickBudget();

    /**
     * Recomputes every budget from the current config and camera. Called once
     * per client tick, before anything renders.
     *
     * @param on                 master state: Intelium enabled, compatible and
     *                           the engine switched on
     * @param framebufferHeight  height of the render target, in real pixels
     * @param fovDegrees         the camera's vertical field of view
     * @param entities           how hard to push the entity budget
     * @param blockEntities      how hard to push the block-entity budget
     * @param particles          how hard to push the particle budget
     * @param adaptive           whether to tighten further under FPS pressure
     * @param pressure           how far short of target the frame rate is, 0..1
     */
    public static void update(boolean on, int framebufferHeight, double fovDegrees,
                              CullingStrength entities, CullingStrength blockEntities,
                              CullingStrength particles, boolean adaptive, double pressure) {
        if (!on) {
            disable();
            return;
        }
        double p = adaptive ? Math.max(0.0, Math.min(1.0, pressure)) : 0.0;

        pixelScale = RenderBudgetTuning.pixelScale(framebufferHeight, fovDegrees);
        entityMinPixels = RenderBudgetTuning.tightenThreshold(
                RenderBudgetTuning.entityMinPixels(entities), p);
        blockEntityLimit = RenderBudgetTuning.tightenBudget(
                RenderBudgetTuning.blockEntityBudget(blockEntities), p, BLOCK_ENTITY_FLOOR);
        particleLimit = RenderBudgetTuning.tightenBudget(
                RenderBudgetTuning.particleBudget(particles), p, PARTICLE_FLOOR);
        active = true;
    }

    /**
     * Stands every system down: from here on nothing is culled and nothing is
     * counted, until the next {@link #update}. This is what "turn Intelium off"
     * means for the engine, and it is instant.
     */
    public static void disable() {
        active = false;
        entityMinPixels = 0.0;
        blockEntityLimit = 0;
        particleLimit = 0;
        BLOCK_ENTITIES.reset();
        PARTICLES.reset();
    }

    /** Whether the engine is live at all. */
    public static boolean isActive() {
        return active;
    }

    // ---- Entity budget ---------------------------------------------------

    /** Whether the entity budget is doing anything (cheap pre-check for hooks). */
    public static boolean entityCullingOn() {
        return active && entityMinPixels > 0.0 && pixelScale > 0.0;
    }

    /**
     * Whether an entity {@code sizeBlocks} tall, {@code distanceSq} squared
     * blocks from the camera, is too small on screen to be worth drawing.
     * Answers false whenever anything is unknown.
     */
    public static boolean shouldCullEntity(double sizeBlocks, double distanceSq) {
        if (!active) return false;
        return RenderBudgetTuning.tooSmallToDraw(sizeBlocks, distanceSq,
                pixelScale, entityMinPixels);
    }

    // ---- Block-entity budget ---------------------------------------------

    /** Whether the block-entity budget is doing anything. */
    public static boolean blockEntityBudgetOn() {
        return active && blockEntityLimit > 0;
    }

    /**
     * Claims one block-entity draw out of this frame's allowance.
     *
     * @param nowNanos {@code System.nanoTime()}; used to spot the frame boundary
     * @return true if this block entity may be drawn
     */
    public static boolean allowBlockEntity(long nowNanos) {
        if (!active || blockEntityLimit <= 0) return true;
        return BLOCK_ENTITIES.tryConsume(nowNanos, blockEntityLimit);
    }

    /** How many block-entity draws the previous frame skipped. */
    public static int blockEntitiesSkipped() {
        return BLOCK_ENTITIES.lastFrameSkipped();
    }

    /** How many block entities the previous frame drew. */
    public static int blockEntitiesDrawn() {
        return BLOCK_ENTITIES.lastFrameUsed();
    }

    // ---- Particle budget --------------------------------------------------

    /** Opens a new tick for the particle spawn budget. */
    public static void beginTick() {
        PARTICLES.beginTick();
    }

    /** Whether the particle budget is doing anything. */
    public static boolean particleBudgetOn() {
        return active && particleLimit > 0;
    }

    /** Claims one particle spawn out of this tick's allowance. */
    public static boolean allowParticle() {
        if (!active || particleLimit <= 0) return true;
        return PARTICLES.tryConsume(particleLimit);
    }

    /** How many particle spawns the previous tick turned away. */
    public static int particlesSkipped() {
        return PARTICLES.lastTickSkipped();
    }

    /** How many particles the previous tick let through. */
    public static int particlesSpawned() {
        return PARTICLES.lastTickUsed();
    }

    // ---- Introspection (status text, tests) -------------------------------

    /** The effective minimum on-screen entity height, in pixels. 0 = off. */
    public static double effectiveEntityMinPixels() {
        return entityMinPixels;
    }

    /** The effective per-frame block-entity ceiling. 0 = unlimited. */
    public static int effectiveBlockEntityLimit() {
        return blockEntityLimit;
    }

    /** The effective per-tick particle spawn ceiling. 0 = unlimited. */
    public static int effectiveParticleLimit() {
        return particleLimit;
    }
}
