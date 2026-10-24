package me.SuperRonanCraft.BetterRTP.references.rtpinfo;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stops background generation for a world (or any other key) that keeps failing.
 * <p>
 * Every failed attempt to find a safe RTP spot still generates the chunk it looked at, and generated chunks are saved to
 * the world. A world that can never produce a safe spot (a void/lobby world, a radius that is all ocean...) would therefore
 * grow the world files forever if the generator simply tried again later. Once a key is exhausted it is skipped until
 * {@link #reset()} is called (on {@code /rtp reload}); no further chunks are generated for it.
 */
public final class GenerationLimiter {

    private final int maxFailures;
    private final Set<String> exhausted = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> failures = new ConcurrentHashMap<>();

    /** @param maxFailures failed cycles in a row after which the key is exhausted (1 = at the first failed cycle) */
    public GenerationLimiter(int maxFailures) {
        this.maxFailures = Math.max(1, maxFailures);
    }

    public boolean isExhausted(String key) {
        return exhausted.contains(key);
    }

    /**
     * Stops generation for the key right now.
     *
     * @return true the first time (so the caller logs the reason only once)
     */
    public boolean exhaust(String key) {
        return exhausted.add(key);
    }

    /**
     * Records a failed cycle.
     *
     * @return true when this failure exhausted the key
     */
    public boolean recordFailure(String key) {
        int count = failures.merge(key, 1, Integer::sum);
        return count >= maxFailures && exhaust(key);
    }

    /** A cycle worked: the failure streak starts from zero again. */
    public void recordSuccess(String key) {
        failures.remove(key);
    }

    /** Forget everything (reload). */
    public void reset() {
        exhausted.clear();
        failures.clear();
    }
}
