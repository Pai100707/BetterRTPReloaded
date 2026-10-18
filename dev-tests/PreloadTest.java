import me.SuperRonanCraft.BetterRTP.player.rtp.RTP_SHAPE;
import me.SuperRonanCraft.BetterRTP.references.messages.delivery.MessageTree;
import me.SuperRonanCraft.BetterRTP.references.preload.*;

import java.util.*;

/** Plain-Java tests for the pure parts of the chunk/position pre-generation (no server needed). */
public class PreloadTest {

    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("  FAIL: " + what); } }
    static void eq(Object e, Object a, String what) { check(Objects.equals(e, a), what + " (expected <" + e + "> was <" + a + ">)"); }

    public static void main(String[] args) {
        MessageMigrationTest.MapTree t;

        System.out.println("== settings parser ==");
        t = new MessageMigrationTest.MapTree();
        t.set("PreloadChunkPosition.world.enable", true);
        t.set("PreloadChunkPosition.world.positions", 10);
        t.set("PreloadChunkPosition.world.chunks-gen-radius", 2);
        t.set("PreloadChunkPosition.world.keep-loaded", false);
        t.set("PreloadChunkPosition.world.keep-loaded-chunk-type", "active");
        List<String> w = new ArrayList<>();
        Map<String, PreloadSettings> s = PreloadSettingsParser.parse(t, "PreloadChunkPosition", w::add);
        eq(0, w.size(), "default section has no warnings: " + w);
        PreloadSettings ps = s.get("world");
        check(ps != null && ps.enabled, "world enabled");
        eq(10, ps.positions, "positions"); eq(2, ps.radius, "radius"); eq(25, ps.chunksPerPosition(), "5x5 = 25 chunks");
        eq(false, ps.keepLoaded, "keep-loaded"); eq(KeepLoadedType.ACTIVE, ps.keepLoadedType, "type");

        // per world settings + case-insensitive type
        t.set("PreloadChunkPosition.world_nether.enable", true);
        t.set("PreloadChunkPosition.world_nether.positions", 3);
        t.set("PreloadChunkPosition.world_nether.chunks-gen-radius", 0);
        t.set("PreloadChunkPosition.world_nether.keep-loaded", true);
        t.set("PreloadChunkPosition.world_nether.keep-loaded-chunk-type", "LAZY");
        t.set("PreloadChunkPosition.off.enable", false);
        w.clear();
        s = PreloadSettingsParser.parse(t, "PreloadChunkPosition", w::add);
        eq(0, w.size(), "no warnings: " + w);
        eq(3, s.get("world_nether").positions, "separate positions per world");
        eq(1, s.get("world_nether").chunksPerPosition(), "radius 0 = single chunk");
        eq(KeepLoadedType.LAZY, s.get("world_nether").keepLoadedType, "type is case-insensitive");
        check(!s.get("off").enabled, "disabled world");
        check(s.get("world").enabled, "other worlds unaffected");

        // invalid values
        String[][] bad = {
                {"positions", "0"}, {"positions", "-4"}, {"positions", "abc"}, {"positions", "2.5"},
                {"chunks-gen-radius", "-1"}, {"chunks-gen-radius", "x"}, {"enable", "maybe"}};
        for (String[] b : bad) {
            MessageMigrationTest.MapTree bt = new MessageMigrationTest.MapTree();
            bt.set("P.w.enable", true); bt.set("P.w.positions", 5); bt.set("P.w.chunks-gen-radius", 1);
            Object v = b[1].matches("-?\\d+") ? (Object) Integer.parseInt(b[1]) : b[1].equals("2.5") ? (Object) 2.5 : b[1];
            bt.set("P.w." + b[0], v);
            List<String> ww = new ArrayList<>();
            PreloadSettings r = PreloadSettingsParser.parse(bt, "P", ww::add).get("w");
            check(!r.enabled, "invalid " + b[0] + "=" + b[1] + " disables the world");
            check(ww.size() >= 1, "invalid " + b[0] + "=" + b[1] + " is reported");
        }
        // soft problems keep the world enabled
        MessageTree soft = new MessageMigrationTest.MapTree();
        soft.set("P.w.enable", true); soft.set("P.w.keep-loaded", "yes"); soft.set("P.w.keep-loaded-chunk-type", "weird");
        soft.set("P.w.typo-option", 1);
        List<String> sw = new ArrayList<>();
        PreloadSettings sr = PreloadSettingsParser.parse(soft, "P", sw::add).get("w");
        check(sr.enabled, "soft problems keep world enabled");
        eq(false, sr.keepLoaded, "invalid keep-loaded -> false"); eq(KeepLoadedType.ACTIVE, sr.keepLoadedType, "invalid type -> active");
        eq(10, sr.positions, "missing positions -> default"); eq(2, sr.radius, "missing radius -> default");
        eq(3, sw.size(), "3 warnings: " + sw);
        // limits
        MessageTree big = new MessageMigrationTest.MapTree();
        big.set("P.w.enable", true); big.set("P.w.positions", 100000); big.set("P.w.chunks-gen-radius", 500);
        PreloadSettings br = PreloadSettingsParser.parse(big, "P", x -> { }).get("w");
        eq(PreloadSettings.MAX_POSITIONS, br.positions, "positions capped"); eq(PreloadSettings.MAX_RADIUS, br.radius, "radius capped");
        // missing enable / broken structure
        MessageTree me = new MessageMigrationTest.MapTree(); me.set("P.w.positions", 3); me.set("P.x", "notasection");
        List<String> mw = new ArrayList<>();
        Map<String, PreloadSettings> mr = PreloadSettingsParser.parse(me, "P", mw::add);
        check(!mr.get("w").enabled && !mr.get("x").enabled, "missing enable / non-section disabled");
        eq(2, mw.size(), "both reported");
        eq(0, PreloadSettingsParser.parse(new MessageMigrationTest.MapTree(), "P", x -> { }).size(), "absent section = nothing");

        System.out.println("== chunk math ==");
        List<int[]> area = ChunkMath.area(10, -3, 2);
        eq(25, area.size(), "radius 2 -> 25 chunks");
        check(area.get(0)[0] == 10 && area.get(0)[1] == -3, "center first");
        Set<String> uniq = new HashSet<>(); for (int[] c : area) uniq.add(c[0] + "," + c[1]);
        eq(25, uniq.size(), "no duplicate chunks");
        check(uniq.contains("8,-5") && uniq.contains("12,-1") && !uniq.contains("13,-3"), "covers exactly 5x5");
        eq(1, ChunkMath.area(0, 0, 0).size(), "radius 0 -> 1 chunk");
        eq(289, ChunkMath.area(0, 0, 8).size(), "radius 8 -> 289");
        int prev = 0; boolean ordered = true;
        for (int[] c : area) { int ring = Math.max(Math.abs(c[0] - 10), Math.abs(c[1] + 3)); if (ring < prev) ordered = false; prev = ring; }
        check(ordered, "inside to outside order");
        check(ChunkMath.chunkInsideBorder(0, 0, 0, 0, 100), "chunk well inside border");
        check(!ChunkMath.chunkInsideBorder(3, 0, 0, 0, 50), "chunk sticking out of the border is rejected");
        check(ChunkMath.blockInsideBorder(10, 10, 0, 0, 100) && !ChunkMath.blockInsideBorder(60, 0, 0, 0, 100), "block border check");

        System.out.println("== position range ==");
        check(PositionRange.contains(RTP_SHAPE.SQUARE, 500, 500, 0, 0, 10, 1000), "square inside");
        check(!PositionRange.contains(RTP_SHAPE.SQUARE, 1001, 0, 0, 0, 10, 1000), "square outside max");
        check(!PositionRange.contains(RTP_SHAPE.SQUARE, 3, 4, 0, 0, 10, 1000), "square inside min radius");
        check(PositionRange.contains(RTP_SHAPE.SQUARE, 3, 40, 0, 0, 10, 1000), "square outside min on one axis");
        check(PositionRange.contains(RTP_SHAPE.CIRCLE, 600, 600, 0, 0, 10, 1000), "circle inside");
        check(!PositionRange.contains(RTP_SHAPE.CIRCLE, 800, 800, 0, 0, 10, 1000), "circle: corner outside (1131 > 1000)");
        check(PositionRange.contains(RTP_SHAPE.SQUARE, 1100, 1100, 100, 100, 10, 1000), "center offset respected");

        System.out.println("== position pool ==");
        PositionPool pool = new PositionPool();
        check(pool.add(new PreloadPosition("w", 100, 70, 100, 1)), "add");
        check(!pool.add(new PreloadPosition("w", 100, 80, 100, 2)), "same block rejected");
        check(!pool.add(new PreloadPosition("w", 101, 70, 105, 3)), "same chunk rejected");
        check(pool.add(new PreloadPosition("w", 300, 70, 300, 4)), "other chunk accepted");
        check(pool.add(new PreloadPosition("n", 100, 70, 100, 5)), "same coords in another world accepted");
        check(pool.conflicts("w", 102, 102), "conflicts() sees same chunk");
        check(!pool.conflicts("w", 1000, 1000), "conflicts() free spot");
        eq(2, pool.size("w"), "size per world"); eq(1, pool.size("n"), "size other world");
        check(pool.take("w", p -> false) == null, "take with nothing suitable");
        eq(2, pool.size("w"), "nothing removed when unsuitable");
        PreloadPosition taken = pool.take("w", p -> p.x > 200);
        check(taken != null && taken.x == 300, "take respects predicate");
        eq(1, pool.size("w"), "taken position is removed (can't be used twice)");
        check(pool.take("missing", p -> true) == null, "take from unknown world");
        for (int i = 0; i < 6; i++) pool.add(new PreloadPosition("w", 1000 + i * 100, 70, 0, 10 + i));
        List<PreloadPosition> dropped = pool.trim("w", 3);
        eq(4, dropped.size(), "trim drops extras"); eq(3, pool.size("w"), "trimmed to max");
        long minKept = Long.MAX_VALUE; for (PreloadPosition p : pool.snapshot("w")) minKept = Math.min(minKept, p.generated);
        for (PreloadPosition p : dropped) check(p.generated <= minKept, "oldest dropped first");
        int before = pool.size("w");
        List<PreloadPosition> gone = pool.retain("w", p -> p.x < 0); // keep nothing
        eq(before, gone.size(), "retain returns everything it removed"); eq(0, pool.size("w"), "retain emptied the world");
        eq(0, pool.retain("nowhere", p -> false).size(), "retain on unknown world is harmless");

        System.out.println("== persistence round trip & damaged files ==");
        PositionPool a = new PositionPool();
        a.add(new PreloadPosition("my.world", -5, 64, 12, 111L)); a.add(new PreloadPosition("w", 7, 70, 7000, 222L));
        PositionPool b = new PositionPool();
        int[] skipped = new int[1];
        eq(2, b.load(a.toMaps(), skipped), "round trip loads both"); eq(0, skipped[0], "nothing skipped");
        eq(1, b.size("my.world"), "world name with dot survives (stored as value)");
        List<Object> damaged = new ArrayList<>();
        damaged.add("garbage"); damaged.add(null); damaged.add(new HashMap<>());
        Map<String, Object> noY = new HashMap<>(); noY.put("world", "w"); noY.put("x", 1); noY.put("z", 1); damaged.add(noY);
        Map<String, Object> frac = new HashMap<>(); frac.put("world", "w"); frac.put("x", 1.5); frac.put("y", 1); frac.put("z", 1); damaged.add(frac);
        Map<String, Object> nan = new HashMap<>(); nan.put("world", "w"); nan.put("x", Double.NaN); nan.put("y", 1); nan.put("z", 1); damaged.add(nan);
        Map<String, Object> noWorld = new HashMap<>(); noWorld.put("world", ""); noWorld.put("x", 1); noWorld.put("y", 1); noWorld.put("z", 1); damaged.add(noWorld);
        Map<String, Object> good = new HashMap<>(); good.put("world", "w"); good.put("x", 4000L); good.put("y", 70); good.put("z", 4000.0); damaged.add(good);
        damaged.add(new HashMap<>(good)); // duplicate of the good one
        PositionPool c = new PositionPool(); skipped = new int[1];
        eq(1, c.load(damaged, skipped), "only the one valid entry loads"); eq(damaged.size() - 1, skipped[0], "rest skipped, no exception");
        eq(0, new PositionPool().load(null, null), "null list tolerated");

        System.out.println("== ticket counter ==");
        ChunkTicketCounter tc = new ChunkTicketCounter();
        ChunkTicketCounter.Key k = new ChunkTicketCounter.Key("w", 1, 2);
        check(tc.acquire(k), "first acquire adds the real ticket");
        check(!tc.acquire(new ChunkTicketCounter.Key("w", 1, 2)), "shared chunk: no second ticket");
        check(!tc.release(k), "first release keeps the ticket (still shared)");
        check(tc.release(k), "last release removes the ticket");
        check(!tc.release(k), "releasing something not held is harmless");
        tc.acquire(k); tc.acquire(new ChunkTicketCounter.Key("n", 0, 0));
        eq(2, tc.clear().size(), "clear returns every held ticket"); eq(0, tc.size(), "empty after clear");

        System.out.println();
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
