package me.SuperRonanCraft.BetterRTP.references.preload;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reference counts the chunks the plugin keeps loaded. Neighbouring positions can share chunks, so the real chunk ticket
 * may only be added by the first user and removed by the last one.
 */
public final class ChunkTicketCounter {

    public static final class Key {
        public final String world;
        public final int x, z;

        public Key(String world, int x, int z) {
            this.world = world;
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return x == k.x && z == k.z && world.equals(k.world);
        }

        @Override
        public int hashCode() {
            return Objects.hash(world, x, z);
        }
    }

    private final Map<Key, Integer> counts = new HashMap<>();

    /** @return true when this is the first user, i.e. the real ticket has to be added now */
    public synchronized boolean acquire(Key key) {
        return counts.merge(key, 1, Integer::sum) == 1;
    }

    /** @return true when this was the last user, i.e. the real ticket has to be removed now. False if nothing was held. */
    public synchronized boolean release(Key key) {
        Integer count = counts.get(key);
        if (count == null)
            return false;
        if (count <= 1) {
            counts.remove(key);
            return true;
        }
        counts.put(key, count - 1);
        return false;
    }

    public synchronized int count(Key key) {
        return counts.getOrDefault(key, 0);
    }

    /** Removes everything and returns every key that held a real ticket. */
    public synchronized java.util.List<Key> clear() {
        java.util.List<Key> keys = new java.util.ArrayList<>(counts.keySet());
        counts.clear();
        return keys;
    }

    public synchronized int size() {
        return counts.size();
    }
}
