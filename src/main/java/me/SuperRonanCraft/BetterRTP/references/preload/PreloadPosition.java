package me.SuperRonanCraft.BetterRTP.references.preload;

import java.util.Objects;

/** One prepared RTP position (a safe block position) of a world. Immutable. */
public final class PreloadPosition {

    public final String world;
    public final int x, y, z;
    /** When it was prepared (epoch millis). */
    public final long generated;

    public PreloadPosition(String world, int x, int y, int z, long generated) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.generated = generated;
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PreloadPosition)) return false;
        PreloadPosition p = (PreloadPosition) o;
        return x == p.x && y == p.y && z == p.z && world.equals(p.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, y, z);
    }

    @Override
    public String toString() {
        return world + " " + x + "," + y + "," + z;
    }
}
