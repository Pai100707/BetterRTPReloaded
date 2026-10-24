package me.SuperRonanCraft.BetterRTP.references.preload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/**
 * The prepared positions of every world. Thread safe: positions are consumed from async RTP threads while the
 * generator adds new ones from the main thread.
 */
public final class PositionPool {

    private final Map<String, List<PreloadPosition>> byWorld = new LinkedHashMap<>();
    private final Random random = new Random();

    /**
     * Adds a position unless it is a duplicate. A position counts as a duplicate when it has the same block
     * <i>or</i> the same chunk as one that is already prepared (it would only cost generation time for nothing).
     *
     * @return true if it was added
     */
    public synchronized boolean add(PreloadPosition position) {
        List<PreloadPosition> list = byWorld.computeIfAbsent(position.world, w -> new ArrayList<>());
        for (PreloadPosition existing : list)
            if (isDuplicate(existing, position))
                return false;
        list.add(position);
        return true;
    }

    /** Would this candidate duplicate a position that is already prepared? */
    public synchronized boolean conflicts(String world, int x, int z) {
        List<PreloadPosition> list = byWorld.get(world);
        if (list == null)
            return false;
        PreloadPosition candidate = new PreloadPosition(world, x, 0, z, 0);
        for (PreloadPosition existing : list)
            if (isDuplicate(existing, candidate))
                return true;
        return false;
    }

    private static boolean isDuplicate(PreloadPosition a, PreloadPosition b) {
        return (a.x == b.x && a.z == b.z) || (a.chunkX() == b.chunkX() && a.chunkZ() == b.chunkZ());
    }

    /**
     * Removes and returns a random position that satisfies {@code suitable}; null when there is none.
     * The predicate runs while the pool is locked, keep it cheap.
     */
    public synchronized PreloadPosition take(String world, Predicate<PreloadPosition> suitable) {
        List<PreloadPosition> list = byWorld.get(world);
        if (list == null || list.isEmpty())
            return null;
        List<PreloadPosition> candidates = new ArrayList<>();
        for (PreloadPosition p : list)
            if (suitable.test(p))
                candidates.add(p);
        if (candidates.isEmpty())
            return null;
        PreloadPosition chosen = candidates.get(random.nextInt(candidates.size()));
        list.remove(chosen);
        return chosen;
    }

    public synchronized boolean remove(PreloadPosition position) {
        List<PreloadPosition> list = byWorld.get(position.world);
        return list != null && list.remove(position);
    }

    /** Removes every position of the world that does not satisfy {@code keep} and returns the removed ones. */
    public synchronized List<PreloadPosition> retain(String world, Predicate<PreloadPosition> keep) {
        List<PreloadPosition> removed = new ArrayList<>();
        List<PreloadPosition> list = byWorld.get(world);
        if (list == null)
            return removed;
        for (PreloadPosition p : new ArrayList<>(list))
            if (!keep.test(p)) {
                list.remove(p);
                removed.add(p);
            }
        return removed;
    }

    /** Drops the oldest positions until at most {@code max} are left; returns the dropped ones. */
    public synchronized List<PreloadPosition> trim(String world, int max) {
        List<PreloadPosition> dropped = new ArrayList<>();
        List<PreloadPosition> list = byWorld.get(world);
        if (list == null)
            return dropped;
        while (list.size() > max) {
            int oldest = 0;
            for (int i = 1; i < list.size(); i++)
                if (list.get(i).generated < list.get(oldest).generated)
                    oldest = i;
            dropped.add(list.remove(oldest));
        }
        return dropped;
    }

    public synchronized int size(String world) {
        List<PreloadPosition> list = byWorld.get(world);
        return list == null ? 0 : list.size();
    }

    public synchronized List<PreloadPosition> snapshot(String world) {
        List<PreloadPosition> list = byWorld.get(world);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    public synchronized List<PreloadPosition> snapshotAll() {
        List<PreloadPosition> all = new ArrayList<>();
        for (List<PreloadPosition> list : byWorld.values())
            all.addAll(list);
        return all;
    }

    // -------------------------------------------------------------------------------------------- persistence

    /** Flat list of maps, safe to hand to a YAML writer. World names are values, so any world name works. */
    public synchronized List<Map<String, Object>> toMaps() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PreloadPosition p : snapshotAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("world", p.world);
            m.put("x", p.x);
            m.put("y", p.y);
            m.put("z", p.z);
            m.put("generated", p.generated);
            out.add(m);
        }
        return out;
    }

    /**
     * Reads saved positions. Anything that is not a well formed entry is skipped (and counted in {@code skipped[0]}),
     * duplicates are skipped too, so a hand edited or damaged file can never break the plugin.
     */
    public synchronized int load(List<?> entries, int[] skipped) {
        int loaded = 0;
        if (entries == null)
            return 0;
        for (Object entry : entries) {
            PreloadPosition p = parse(entry);
            if (p == null || !add(p)) {
                if (skipped != null && skipped.length > 0)
                    skipped[0]++;
                continue;
            }
            loaded++;
        }
        return loaded;
    }

    static PreloadPosition parse(Object entry) {
        if (!(entry instanceof Map))
            return null;
        Map<?, ?> m = (Map<?, ?>) entry;
        Object world = m.get("world");
        if (!(world instanceof String) || ((String) world).isEmpty())
            return null;
        Integer x = whole(m.get("x")), y = whole(m.get("y")), z = whole(m.get("z"));
        if (x == null || y == null || z == null)
            return null;
        Object gen = m.get("generated");
        long generated = gen instanceof Number ? ((Number) gen).longValue() : 0L;
        return new PreloadPosition((String) world, x, y, z, generated);
    }

    private static Integer whole(Object o) {
        if (!(o instanceof Number))
            return null;
        double d = ((Number) o).doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.rint(d) || d > Integer.MAX_VALUE || d < Integer.MIN_VALUE)
            return null;
        return (int) d;
    }
}
