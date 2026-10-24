package me.SuperRonanCraft.BetterRTP.references.preload;

import java.util.ArrayList;
import java.util.List;

/** Pure helpers for the chunk area around a position and for world-border checks. */
public final class ChunkMath {

    private ChunkMath() {
    }

    /**
     * All chunk coordinates of the square area around {@code (cx, cz)}, the center chunk first, then the rest ordered
     * from the inside to the outside so the nearest chunks are prepared first. {@code radius 2} = 5x5 = 25 chunks.
     */
    public static List<int[]> area(int cx, int cz, int radius) {
        List<int[]> chunks = new ArrayList<>();
        for (int ring = 0; ring <= radius; ring++)
            for (int dx = -ring; dx <= ring; dx++)
                for (int dz = -ring; dz <= ring; dz++)
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring)
                        chunks.add(new int[]{cx + dx, cz + dz});
        return chunks;
    }

    /**
     * Is the whole chunk inside the border? The border is a square of side {@code size} around its center.
     * Using the chunk's far corners keeps chunks that stick out of the border from being generated for nothing.
     */
    public static boolean chunkInsideBorder(int chunkX, int chunkZ, double centerX, double centerZ, double size) {
        double half = size / 2.0;
        double minX = chunkX * 16, maxX = chunkX * 16 + 15.999;
        double minZ = chunkZ * 16, maxZ = chunkZ * 16 + 15.999;
        return minX >= centerX - half && maxX <= centerX + half
                && minZ >= centerZ - half && maxZ <= centerZ + half;
    }

    public static boolean blockInsideBorder(int x, int z, double centerX, double centerZ, double size) {
        double half = size / 2.0;
        return x + 0.5 >= centerX - half && x + 0.5 <= centerX + half
                && z + 0.5 >= centerZ - half && z + 0.5 <= centerZ + half;
    }
}
