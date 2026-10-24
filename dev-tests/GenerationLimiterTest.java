import me.SuperRonanCraft.BetterRTP.references.rtpinfo.GenerationLimiter;
import me.SuperRonanCraft.BetterRTP.references.rtpinfo.GenerationTargets;

import java.util.*;


/** Plain-Java tests for the "stop generating when the limit is reached" policy. */
public class GenerationLimiterTest {

    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("  FAIL: " + what); } }
    static void eq(Object e, Object a, String what) { check(Objects.equals(e, a), what + " (expected <" + e + "> was <" + a + ">)"); }

    /**
     * Small model of the bug seen on the server: a world that never gets a safe spot, and /rtp being used again and again.
     * Every attempt generates one chunk. The old queue restarted its attempt counter on every /rtp.
     *
     * @return how many chunks were generated for the hopeless world over {@code rtpUses} uses of /rtp
     */
    static int simulate(boolean stopAtLimit, int rtpUses, int maxAttempts) {
        GenerationLimiter limiter = new GenerationLimiter(1);
        int generated = 0;
        for (int use = 0; use < rtpUses; use++) {          // every /rtp restarts the generator ...
            if (stopAtLimit && limiter.isExhausted("default_spawn"))
                continue;                                    // ... which now skips a world that was given up on
            for (int attempt = 0; attempt <= maxAttempts; attempt++) {
                generated++;                                 // each attempt generates (and saves) one chunk
                // a world without a safe location: the attempt never succeeds
            }
            if (stopAtLimit)
                limiter.exhaust("default_spawn");            // max attempts reached
        }
        return generated;
    }

    public static void main(String[] args) {
        System.out.println("== limiter ==");
        GenerationLimiter l = new GenerationLimiter(3);
        check(!l.isExhausted("w"), "fresh key is not exhausted");
        check(!l.recordFailure("w") && !l.recordFailure("w"), "two failed cycles in a row are tolerated");
        check(!l.isExhausted("w"), "still running after two failures");
        check(l.recordFailure("w"), "the third failure exhausts it (and says so once)");
        check(l.isExhausted("w"), "exhausted");
        check(!l.recordFailure("w"), "further failures do not report again");
        check(!l.exhaust("w"), "exhaust() on an exhausted key reports nothing new");
        check(!l.isExhausted("other"), "other keys are independent");

        l = new GenerationLimiter(3);
        l.recordFailure("w"); l.recordFailure("w"); l.recordSuccess("w");
        check(!l.recordFailure("w") && !l.recordFailure("w"), "a success starts the failure streak over");
        check(l.recordFailure("w"), "...and three new failures exhaust it");

        l = new GenerationLimiter(1);
        check(l.recordFailure("w"), "limit 1: the first failure stops it immediately");
        l = new GenerationLimiter(0);
        check(l.recordFailure("w"), "limit below 1 is treated as 1");

        l = new GenerationLimiter(2);
        check(l.exhaust("a"), "explicit exhaust is reported the first time"); check(!l.exhaust("a"), "but not again");
        l.recordFailure("b");
        l.reset();
        check(!l.isExhausted("a") && !l.isExhausted("b"), "reload forgets exhausted keys");
        check(!l.recordFailure("b"), "reload also resets the failure streaks");

        System.out.println("== which worlds get positions ==");
        Map<String, String> overrides = new HashMap<>(); overrides.put("spawn", "world");
        List<String> disabled = Arrays.asList("prison");
        eq(null, GenerationTargets.skipReason("world", disabled, overrides), "a normal world is a target");
        check(GenerationTargets.skipReason("spawn", disabled, overrides) != null, "an overridden world (spawn -> world) is skipped");
        check(GenerationTargets.skipReason("spawn", disabled, overrides).contains("'world'"), "the reason names the override target");
        check(GenerationTargets.skipReason("prison", disabled, overrides) != null, "a disabled world is skipped");
        eq(null, GenerationTargets.skipReason("spawn", null, null), "no overrides and nothing disabled: target");
        eq(null, GenerationTargets.skipReason("world", null, overrides), "the override TARGET itself is a target");
        eq(null, GenerationTargets.skipReason("spawn", disabled, new HashMap<String, String>()), "an empty override map skips nothing");
        check(GenerationTargets.skipReason("Spawn", disabled, overrides) == null, "world names are matched exactly (as RTP does)");
        check(GenerationTargets.skipReason(null, disabled, overrides) != null, "null world is never a target");

        System.out.println("== model of the server problem ==");
        int old = simulate(false, 100, 50);
        int fixed = simulate(true, 100, 50);
        eq(100 * 51, old, "before: 51 chunks generated on every one of 100 /rtp uses");
        eq(51, fixed, "after: 51 chunks once, then nothing");
        // the overridden world is not even attempted once
        int skippedWorld = 0;
        for (int use = 0; use < 100; use++)
            if (GenerationTargets.skipReason("spawn", null, overrides) == null) skippedWorld += 51;
        eq(0, skippedWorld, "with the override check an overridden world generates nothing at all");

        System.out.println();
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
