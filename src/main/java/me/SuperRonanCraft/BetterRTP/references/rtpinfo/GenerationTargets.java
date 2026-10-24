package me.SuperRonanCraft.BetterRTP.references.rtpinfo;

import java.util.Collection;
import java.util.Map;

/**
 * Decides which worlds are worth generating RTP positions for.
 * <p>
 * A world that is overridden ({@code Overrides: spawn -> world}) is never the destination of an RTP: players who use
 * {@code /rtp} there are sent to the override target. Positions prepared for the overridden world could never be used, but
 * generating them would still create (and save) chunks. Disabled worlds are not RTP destinations either.
 */
public final class GenerationTargets {

    private GenerationTargets() {
    }

    /**
     * @param disabledWorlds worlds where /rtp is disabled (may be null)
     * @param overrides      override source world -> target world (may be null)
     * @return null when positions should be prepared for the world, otherwise a short reason why not
     */
    public static String skipReason(String world, Collection<String> disabledWorlds, Map<String, String> overrides) {
        if (world == null)
            return "no world";
        if (overrides != null && overrides.containsKey(world))
            return "it is overridden to '" + overrides.get(world) + "' (Overrides): players are sent there, positions here would never be used";
        if (disabledWorlds != null && disabledWorlds.contains(world))
            return "it is listed in DisabledWorlds";
        return null;
    }
}
