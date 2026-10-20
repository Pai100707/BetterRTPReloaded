package me.SuperRonanCraft.BetterRTP.references.preload;

/** Validated settings of one world of the {@code PreloadChunkPosition} config section. Immutable. */
public final class PreloadSettings {

    /** Hard limits that protect the server from accidental gigantic values. */
    public static final int MAX_POSITIONS = 500;
    public static final int MAX_RADIUS = 16;

    public static final int DEFAULT_POSITIONS = 10;
    public static final int DEFAULT_RADIUS = 2;

    public final String world;
    public final boolean enabled;
    public final int positions;
    public final int radius;
    public final boolean keepLoaded;
    public final KeepLoadedType keepLoadedType;

    PreloadSettings(String world, boolean enabled, int positions, int radius, boolean keepLoaded, KeepLoadedType keepLoadedType) {
        this.world = world;
        this.enabled = enabled;
        this.positions = positions;
        this.radius = radius;
        this.keepLoaded = keepLoaded;
        this.keepLoadedType = keepLoadedType;
    }

    /** A world whose preloading is switched off (or whose configuration is invalid). */
    static PreloadSettings disabled(String world) {
        return new PreloadSettings(world, false, DEFAULT_POSITIONS, DEFAULT_RADIUS, false, KeepLoadedType.ACTIVE);
    }

    /** Chunks that make up the area around one position: {@code (2r+1)^2}. */
    public int chunksPerPosition() {
        int side = radius * 2 + 1;
        return side * side;
    }
}
