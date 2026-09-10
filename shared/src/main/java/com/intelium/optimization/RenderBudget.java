package com.intelium.optimization;

import com.intelium.RenderBackend;

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
 * <p>Everything is static because there is one game and one camera; the whole
 * budget state is published as one immutable object through a single volatile
 * field, because it is written from the client tick and read from the render
 * path - the same thread in vanilla Minecraft, but it need not stay that way,
 * and a torn read (new threshold against old pixel scale) must be impossible
 * either way. The two counters ({@link FrameBudget}, {@link TickBudget}) are
 * only ever touched from their own thread, as documented on each.
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

    /** A block entity is a block: one block tall, by definition. */
    private static final double BLOCK_ENTITY_SIZE = 1.0;

    /**
     * One tick's computed budgets, published as a single immutable object so a
     * reader can never observe half of one update and half of another (e.g. a
     * new, tighter threshold against the previous camera's pixel scale).
     */
    private record State(boolean active, double pixelScale, double entityMinPixels,
                         double blockEntityMinPixels, int blockEntityLimit, int particleLimit) {
    }

    private static final State DISABLED = new State(false, 0.0, 0.0, 0.0, 0, 0);

    private static volatile State state = DISABLED;

    private static final FrameBudget BLOCK_ENTITIES = new FrameBudget();
    private static final TickBudget PARTICLES = new TickBudget();

    /**
     * Id of the entity the camera is attached to, published once per tick.
     *
     * <p>The entity hook needs to know "is this the thing I am looking out of?"
     * for every entity of every frame. It used to answer that with
     * {@code Minecraft.getInstance().getCameraEntity()} - a static call and a
     * field chase, per entity, per frame, to re-derive something that changes
     * at most once a tick. Publishing the id here turns it into an int compare.
     *
     * <p>{@link #NO_CAMERA_ENTITY} means "unknown", which reads downstream as
     * "exempt nothing on this basis" - the safe direction, since the other
     * exemptions still apply.
     */
    public static final int NO_CAMERA_ENTITY = Integer.MIN_VALUE;

    private static volatile int cameraEntityId = NO_CAMERA_ENTITY;

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
        update(on, framebufferHeight, fovDegrees, entities, blockEntities, particles,
                adaptive, pressure, RenderBackend.UNKNOWN);
    }

    /**
     * Backend-aware update. OpenGL receives a modest draw-call/burst adjustment
     * through {@link BackendRenderTuning}; unknown backends retain the original
     * policy rather than being guessed.
     */
    public static void update(boolean on, int framebufferHeight, double fovDegrees,
                              CullingStrength entities, CullingStrength blockEntities,
                              CullingStrength particles, boolean adaptive, double pressure,
                              RenderBackend backend) {
        if (!on) {
            disable();
            return;
        }
        double p = adaptive ? Math.max(0.0, Math.min(1.0, pressure)) : 0.0;
        double thresholdScale = BackendRenderTuning.thresholdScale(backend);

        // Block entities get the same apparent-size scale, but read it off
        // their own level: whoever turns entity culling off has not thereby
        // said anything about chests.
        state = new State(true,
                RenderBudgetTuning.pixelScale(framebufferHeight, fovDegrees),
                RenderBudgetTuning.tightenThreshold(
                        RenderBudgetTuning.entityMinPixels(entities) * thresholdScale, p),
                RenderBudgetTuning.tightenThreshold(
                        RenderBudgetTuning.entityMinPixels(blockEntities) * thresholdScale, p),
                RenderBudgetTuning.tightenBudget(
                        BackendRenderTuning.blockEntityBudget(
                                RenderBudgetTuning.blockEntityBudget(blockEntities), backend),
                        p, BLOCK_ENTITY_FLOOR),
                RenderBudgetTuning.tightenBudget(
                        BackendRenderTuning.particleBudget(
                                RenderBudgetTuning.particleBudget(particles), backend),
                        p, PARTICLE_FLOOR));
    }

    /**
     * Stands every system down: from here on nothing is culled and nothing is
     * counted, until the next {@link #update}. This is what "turn Intelium off"
     * means for the engine, and it is instant.
     */
    public static void disable() {
        state = DISABLED;
        BLOCK_ENTITIES.reset();
        PARTICLES.reset();
    }

    /** Publishes the camera entity's id for this tick. */
    public static void setCameraEntityId(int entityId) {
        cameraEntityId = entityId;
    }

    /** The camera entity's id, or {@link #NO_CAMERA_ENTITY} when unknown. */
    public static int cameraEntityId() {
        return cameraEntityId;
    }

    /**
     * Whether this entity id is the camera's. False when the camera is unknown,
     * so a missing publication can never exempt an arbitrary entity.
     */
    public static boolean isCameraEntity(int entityId) {
        int id = cameraEntityId;
        return id != NO_CAMERA_ENTITY && id == entityId;
    }

    /** Whether the engine is live at all. */
    public static boolean isActive() {
        return state.active;
    }

    // ---- Entity budget ---------------------------------------------------

    /** Whether the entity budget is doing anything (cheap pre-check for hooks). */
    public static boolean entityCullingOn() {
        State s = state;
        return s.active && s.entityMinPixels > 0.0 && s.pixelScale > 0.0;
    }

    /**
     * Whether an entity {@code sizeBlocks} tall, {@code distanceSq} squared
     * blocks from the camera, is too small on screen to be worth drawing.
     * Answers false whenever anything is unknown.
     */
    public static boolean shouldCullEntity(double sizeBlocks, double distanceSq) {
        State s = state;
        if (!s.active) return false;
        return RenderBudgetTuning.tooSmallToDraw(sizeBlocks, distanceSq,
                s.pixelScale, s.entityMinPixels);
    }

    // ---- Block-entity budget ---------------------------------------------

    /** Whether the block-entity budget is doing anything. */
    public static boolean blockEntityBudgetOn() {
        State s = state;
        return s.active && s.blockEntityLimit > 0;
    }

    /**
     * Whether a block entity {@code distanceSq} squared blocks from the camera
     * is too small on screen to be worth drawing. Same apparent-size test the
     * entity budget uses, against a one-block-tall object, but driven by the
     * block-entity level rather than the entity one.
     */
    public static boolean shouldCullBlockEntity(double distanceSq) {
        State s = state;
        if (!s.active) return false;
        return RenderBudgetTuning.tooSmallToDraw(BLOCK_ENTITY_SIZE, distanceSq,
                s.pixelScale, s.blockEntityMinPixels);
    }

    /**
     * Announces a real frame boundary to the block-entity budget. Driven by the
     * verified per-frame hook; until it is first called, the budget infers
     * boundaries from timing instead.
     */
    public static void beginFrame() {
        BLOCK_ENTITIES.beginFrame();
    }

    /** Whether the frame budget is being told its boundaries rather than guessing. */
    public static boolean hasFrameBoundaryHook() {
        return BLOCK_ENTITIES.hasExplicitBoundaries();
    }

    /**
     * Claims one block-entity draw out of this frame's allowance.
     *
     * @param nowNanos {@code System.nanoTime()}; used to spot the frame boundary
     * @return true if this block entity may be drawn
     */
    public static boolean allowBlockEntity(long nowNanos) {
        State s = state;
        if (!s.active || s.blockEntityLimit <= 0) return true;
        return BLOCK_ENTITIES.tryConsume(nowNanos, s.blockEntityLimit);
    }

    /**
     * The whole block-entity decision in one call: too far to make out, or over
     * this frame's allowance?
     *
     * <h2>Why distance is checked before the count</h2>
     *
     * <p>A pure per-frame counter is first-come-first-served, and nothing
     * promises that block entities arrive in distance order. Whichever ones the
     * iteration happens to reach last are the ones refused - so in a storage
     * room, the chest the player is standing in front of could stop drawing
     * while identical chests forty blocks away kept drawing, and which ones
     * blinked would change as the player turned. That is worse than the frame
     * cost it saves.
     *
     * <p>So anything inside {@link RenderBudgetTuning#NEVER_CULL_RADIUS} is
     * drawn unconditionally, whatever the count says, and only counted so the
     * accounting stays honest. The ceiling then applies to the distant
     * population, where dropping one is invisible and the order does not
     * matter.
     *
     * <p>Reads no clock when the frame-boundary hook is available.
     *
     * @param distanceSq squared distance from the camera, in blocks
     * @return true when this block entity should not be drawn
     */
    public static boolean shouldSkipBlockEntity(double distanceSq) {
        State s = state;
        if (!s.active) return false;
        if (RenderBudgetTuning.tooSmallToDraw(BLOCK_ENTITY_SIZE, distanceSq,
                s.pixelScale, s.blockEntityMinPixels)) {
            return true;
        }
        if (s.blockEntityLimit <= 0) return false;
        if (distanceSq <= RenderBudgetTuning.NEVER_CULL_RADIUS_SQ) {
            // Near enough that refusing it would be seen. Count it, draw it.
            BLOCK_ENTITIES.consumeExempt();
            return false;
        }
        return !BLOCK_ENTITIES.tryConsume(s.blockEntityLimit);
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
        State s = state;
        return s.active && s.particleLimit > 0;
    }

    /** Claims one particle spawn out of this tick's allowance. */
    public static boolean allowParticle() {
        State s = state;
        if (!s.active || s.particleLimit <= 0) return true;
        return PARTICLES.tryConsume(s.particleLimit);
    }

    /**
     * Hot-path combined form for particle mixins. It performs one volatile
     * state read instead of first asking {@link #particleBudgetOn()} and then
     * asking {@link #allowParticle()}, which matters during the large bursts
     * this budget exists to tame.
     *
     * @return true when this particle should be rejected
     */
    public static boolean shouldRejectParticle() {
        State s = state;
        return s.active && s.particleLimit > 0 && !PARTICLES.tryConsume(s.particleLimit);
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
        return state.entityMinPixels;
    }

    /** The effective minimum on-screen block-entity height, in pixels. 0 = off. */
    public static double effectiveBlockEntityMinPixels() {
        return state.blockEntityMinPixels;
    }

    /** The effective per-frame block-entity ceiling. 0 = unlimited. */
    public static int effectiveBlockEntityLimit() {
        return state.blockEntityLimit;
    }

    /** The effective per-tick particle spawn ceiling. 0 = unlimited. */
    public static int effectiveParticleLimit() {
        return state.particleLimit;
    }
}
