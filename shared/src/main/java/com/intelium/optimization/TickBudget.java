package com.intelium.optimization;

/**
 * A per-tick allowance, for work whose natural rhythm is the 20 Hz client tick
 * rather than the frame - particle spawns, in Intelium's case.
 *
 * <p>Unlike {@link FrameBudget} there is no boundary to guess at here: the
 * client tick hook calls {@link #beginTick()} explicitly.
 *
 * <p>Not thread-safe by design: only ever touched from the client thread.
 */
public final class TickBudget {

    private int used;
    private int skipped;
    private int lastTickUsed;
    private int lastTickSkipped;

    /** Starts a new tick, rolling this tick's counters into the reported ones. */
    public void beginTick() {
        lastTickUsed = used;
        lastTickSkipped = skipped;
        used = 0;
        skipped = 0;
    }

    /**
     * Claims one slot of this tick's budget.
     *
     * @param limit how many slots this tick has; {@code <= 0} means unlimited
     * @return true if the caller may proceed
     */
    public boolean tryConsume(int limit) {
        if (limit <= 0 || used < limit) {
            used++;
            return true;
        }
        skipped++;
        return false;
    }

    /** Forgets the current and previous tick's accounting. */
    public void reset() {
        used = 0;
        skipped = 0;
        lastTickUsed = 0;
        lastTickSkipped = 0;
    }

    /** How many slots the previous complete tick used. */
    public int lastTickUsed() {
        return lastTickUsed;
    }

    /** How many spawns the previous complete tick turned away. */
    public int lastTickSkipped() {
        return lastTickSkipped;
    }
}
