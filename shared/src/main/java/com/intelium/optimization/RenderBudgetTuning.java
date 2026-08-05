package com.intelium.optimization;

/**
 * The numbers behind Intelium's render-budget systems, and the geometry that
 * makes the entity budget resolution-aware. Pure math, no Minecraft types.
 *
 * <h2>Why screen-space instead of a distance slider</h2>
 *
 * <p>Vanilla's "Entity Distance" is a single multiplier applied to every entity
 * alike: a dropped item and an ender dragon disappear at the same range. But
 * what actually costs frames is <em>draw calls for things you cannot see</em>,
 * and how visible an entity is depends on how big it lands on your screen - its
 * world size divided by its distance, scaled by the field of view and the
 * resolution you are rendering at.
 *
 * <p>So Intelium culls by <em>apparent size</em>. For a camera with vertical
 * field of view {@code fov} rendering into a framebuffer {@code h} pixels tall,
 * an object {@code s} blocks tall at distance {@code d} covers
 *
 * <pre>{@code   pixels = h / (2 * tan(fov / 2)) * s / d   }</pre>
 *
 * <p>The first factor depends only on the camera, so it is computed once per
 * tick ({@link #pixelScale}) and the per-entity test collapses to one multiply
 * and one compare - cheap enough to run on every entity of every frame.
 *
 * <p>The practical effect at 1080p / 70&deg; FOV with the default
 * {@link CullingStrength#BALANCED} threshold of 12 px: a zombie stops drawing
 * past ~125 blocks (further than most render distances - so it never does),
 * while a dropped item or XP orb stops past ~16 blocks, where it was three
 * pixels of noise. Small-and-far is exactly the population that is numerous and
 * worthless to draw, and it is the only population this removes.
 */
public final class RenderBudgetTuning {

    private RenderBudgetTuning() {}

    /**
     * Entities closer than this (in blocks) are never culled, whatever the
     * numbers say. Guarantees the things you are interacting with - the item
     * you just dropped, the mob hitting you - can never blink out.
     */
    public static final double NEVER_CULL_RADIUS = 12.0;

    /** {@link #NEVER_CULL_RADIUS} squared, for sqrt-free distance tests. */
    public static final double NEVER_CULL_RADIUS_SQ = NEVER_CULL_RADIUS * NEVER_CULL_RADIUS;

    /** Field-of-view values outside this range are clamped before use. */
    private static final double MIN_FOV = 30.0;
    private static final double MAX_FOV = 110.0;

    /**
     * Minimum on-screen height, in pixels, an entity must cover to be drawn.
     * Below this it is a smudge, and at {@link CullingStrength#OFF} the test is
     * skipped entirely (0 = never cull).
     */
    public static double entityMinPixels(CullingStrength strength) {
        return switch (strength) {
            case OFF -> 0.0;
            case LIGHT -> 6.0;
            case BALANCED -> 12.0;
            case AGGRESSIVE -> 24.0;
        };
    }

    /**
     * How many block entities (chests, signs, banners, item frames, beacons...)
     * may be drawn in a single frame. 0 = unlimited.
     *
     * <p>Block entities are the one part of the world that Sodium cannot batch
     * into the chunk mesh: each one is walked and drawn individually every
     * frame. A normal scene has a few dozen and never touches the budget; a
     * storage room or a decorated base has hundreds, and that is exactly where
     * the frame rate collapses and where a ceiling pays for itself.
     */
    public static int blockEntityBudget(CullingStrength strength) {
        return switch (strength) {
            case OFF -> 0;
            case LIGHT -> 512;
            case BALANCED -> 256;
            case AGGRESSIVE -> 128;
        };
    }

    /**
     * How many new particles may be spawned in a single client tick.
     * 0 = unlimited.
     *
     * <p>Ordinary play spawns a handful per tick and never notices this. A TNT
     * chain, a splash potion volley or a big campfire cluster spawns thousands
     * in one tick, and every one of them then costs CPU to tick and GPU to draw
     * for its whole lifetime. Capping the <em>burst</em> keeps the effect
     * looking like the effect while cutting off the tail that turns an
     * explosion into a two-second freeze.
     */
    public static int particleBudget(CullingStrength strength) {
        return switch (strength) {
            case OFF -> 0;
            case LIGHT -> 1024;
            case BALANCED -> 512;
            case AGGRESSIVE -> 256;
        };
    }

    /**
     * Pixels of screen height a one-block-tall object one block away would
     * cover: {@code h / (2 * tan(fov / 2))}. Divide by the distance to get the
     * apparent size of an object of that height.
     *
     * <p>Returns 0 for a degenerate framebuffer, which reads downstream as
     * "no information, do not cull".
     */
    public static double pixelScale(int framebufferHeight, double fovDegrees) {
        if (framebufferHeight <= 0) return 0.0;
        double fov = clamp(fovDegrees, MIN_FOV, MAX_FOV);
        double halfFovTan = Math.tan(Math.toRadians(fov) / 2.0);
        if (halfFovTan <= 0.0) return 0.0;
        return framebufferHeight / (2.0 * halfFovTan);
    }

    /**
     * Whether an object {@code sizeBlocks} tall at {@code distanceSq} (squared
     * blocks) from the camera is too small on screen to be worth drawing.
     *
     * <p>Kept sqrt-free: rather than compare pixels, it compares the squared
     * distance against the squared range at which this object would shrink to
     * {@code minPixels}. Any missing input (culling off, unknown camera, zero
     * size) answers "do not cull" - the safe direction.
     */
    public static boolean tooSmallToDraw(double sizeBlocks, double distanceSq,
                                         double pixelScale, double minPixels) {
        if (minPixels <= 0.0 || pixelScale <= 0.0 || sizeBlocks <= 0.0) return false;
        if (distanceSq <= NEVER_CULL_RADIUS_SQ) return false;
        double maxDistance = pixelScale * sizeBlocks / minPixels;
        return distanceSq > maxDistance * maxDistance;
    }

    /**
     * Scales a pixel threshold up as the frame rate falls short of its target:
     * unchanged at zero pressure, doubled at full pressure. This is what makes
     * the budgets adaptive - when you are already struggling, Intelium trades a
     * little more distant detail for frames, and gives it straight back when the
     * pressure lifts.
     */
    public static double tightenThreshold(double basePixels, double pressure) {
        return basePixels * (1.0 + clamp(pressure, 0.0, 1.0));
    }

    /**
     * Scales a count budget down under the same pressure: unchanged at zero,
     * halved at full. Never returns less than {@code floor}, and passes 0
     * (unlimited) straight through.
     *
     * <p>Where a floor above the base budget would contradict it, the base wins:
     * a method called "tighten" must never hand back more room than it was
     * given, whatever the floor says.
     */
    public static int tightenBudget(int baseBudget, double pressure, int floor) {
        if (baseBudget <= 0) return 0;
        int scaled = (int) Math.round(baseBudget / (1.0 + clamp(pressure, 0.0, 1.0)));
        return Math.min(baseBudget, Math.max(Math.max(1, floor), scaled));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
