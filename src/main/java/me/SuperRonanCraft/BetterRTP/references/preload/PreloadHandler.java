package me.SuperRonanCraft.BetterRTP.references.preload;

import com.tcoded.folialib.wrapper.task.WrappedTask;
import io.papermc.lib.PaperLib;
import me.SuperRonanCraft.BetterRTP.BetterRTP;
import me.SuperRonanCraft.BetterRTP.player.rtp.RTP;
import me.SuperRonanCraft.BetterRTP.player.rtp.RTPPlayer;
import me.SuperRonanCraft.BetterRTP.references.file.FileOther;
import me.SuperRonanCraft.BetterRTP.references.helpers.HelperRTP;
import me.SuperRonanCraft.BetterRTP.references.messages.delivery.BukkitMessageTree;
import me.SuperRonanCraft.BetterRTP.references.rtpinfo.RandomLocation;
import me.SuperRonanCraft.BetterRTP.references.rtpinfo.worlds.RTPWorld;
import me.SuperRonanCraft.BetterRTP.references.rtpinfo.worlds.WorldCustom;
import me.SuperRonanCraft.BetterRTP.versions.AsyncHandler;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a pool of prepared RTP positions per world ({@code PreloadChunkPosition} in config.yml).
 *
 * <h3>What "prepared" means</h3>
 * A position is only added to the pool after the chunk area around it ({@code chunks-gen-radius}) was really
 * <b>generated</b>, i.e. loaded to full status through {@code PaperLib.getChunkAtAsync(world, x, z, true)} which
 * creates missing terrain. Generated chunks are written to the world by the server when they unload.
 * That is separate from {@code keep-loaded}: only when it is enabled the chunks additionally get a Paper plugin chunk ticket
 * (see {@link PreloadTickets}) so they are not unloaded.
 *
 * <h3>Pacing</h3>
 * Exactly one position is prepared at a time and its chunks are requested with a small fixed concurrency (4 on Paper,
 * 1 per few ticks on servers without async chunk loading). Nothing blocks the main thread longer than a single chunk request.
 *
 * <h3>Lifecycle</h3>
 * {@link #load()} (startup and {@code /rtp reload}) reads the config, restores saved positions, re-applies chunk tickets and
 * starts a maintenance loop that refills the pools. {@link #unload()} stops everything, releases every ticket and saves.
 * Work that is still running during a reload notices the changed {@code token} and stops.
 */
public class PreloadHandler {

    private static final String CONFIG_PATH = "PreloadChunkPosition";
    private static final String FILE_NAME = "preload-positions.yml";
    private static final int FILE_VERSION = 1;

    private static final long MAINTENANCE_TICKS = 100L; //5 seconds
    private static final long NEXT_POSITION_TICKS = 20L; //pause between two prepared positions
    private static final long RELEASE_AFTER_USE_TICKS = 20L * 30; //keep a used position's chunks loaded while the player arrives
    private static final long SAVE_DELAY_TICKS = 40L;
    private static final int LOCATION_ATTEMPTS = 40;
    private static final int POLL_ATTEMPTS = 5;
    private static final long BACKOFF_SHORT_MS = 30_000L, BACKOFF_LONG_MS = 120_000L;

    private final PositionPool pool = new PositionPool();
    private final ChunkTicketCounter tickets = new ChunkTicketCounter();
    private final Map<PreloadPosition, List<ChunkTicketCounter.Key>> positionTickets = new HashMap<>();
    private final Map<String, Long> backoffUntil = new ConcurrentHashMap<>();
    private final Set<String> warnedOnce = ConcurrentHashMap.newKeySet();
    private final Object fileLock = new Object();

    private volatile Map<String, PreloadSettings> settings = Collections.emptyMap();
    /** Bumped on load/unload; asynchronous work started under an older token stops by itself. */
    private volatile int token = 0;
    private boolean poolLoaded;
    private boolean fillRunning;      //guarded by this
    private boolean fillScheduled;    //guarded by this
    private boolean saveScheduled;    //guarded by this
    private int roundRobin;           //guarded by this
    private WrappedTask maintenance;  //guarded by this
    private boolean ticketsWarned;

    private BetterRTP pl() {
        return BetterRTP.getInstance();
    }

    // ------------------------------------------------------------------------------------------------ lifecycle

    /** (Re)loads the configuration and restarts pre-generation. Call from the main thread. */
    public void load() {
        int myToken;
        synchronized (this) {
            myToken = ++token;
            cancelMaintenance();
            fillRunning = false;
            fillScheduled = false;
        }
        releaseAllTickets();
        warnedOnce.clear();
        backoffUntil.clear();

        Map<String, PreloadSettings> parsed = PreloadSettingsParser.parse(
                new BukkitMessageTree(FileOther.FILETYPE.CONFIG.getConfig(), true), CONFIG_PATH,
                msg -> pl().getLogger().warning(msg));
        settings = Collections.unmodifiableMap(parsed);

        if (!poolLoaded) {
            readFile();
            poolLoaded = true;
        }
        boolean anyEnabled = false;
        for (PreloadSettings s : parsed.values()) {
            if (!s.enabled)
                continue;
            anyEnabled = true;
            if (s.keepLoaded && !PreloadTickets.supported() && warnOnce("tickets"))
                pl().getLogger().warning("keep-loaded is enabled but this server has no Paper plugin chunk tickets "
                        + "(World#addPluginChunkTicket). Chunks will be pre-generated but cannot be kept loaded.");
            if (s.keepLoaded && s.keepLoadedType == KeepLoadedType.LAZY && warnOnce("lazy"))
                pl().getLogger().info("keep-loaded-chunk-type 'lazy': the Paper API offers a single kind of plugin chunk "
                        + "ticket, so 'lazy' and 'active' behave the same on this server.");
            List<PreloadPosition> dropped = pool.trim(s.world, s.positions); //Admin lowered the amount
            for (PreloadPosition p : dropped)
                releaseFor(p);
            if (!dropped.isEmpty())
                scheduleSave();
        }
        if (anyEnabled) {
            restoreTickets(myToken);
            synchronized (this) {
                scheduleMaintenance(myToken, 100L); //give worlds (Multiverse...) time to load
            }
        }
    }

    /** Stops all work, releases every chunk ticket and saves the pool. */
    public void unload() {
        synchronized (this) {
            token++;
            cancelMaintenance();
            fillRunning = false;
        }
        releaseAllTickets();
        writeFile();
    }

    private synchronized void cancelMaintenance() {
        if (maintenance != null) {
            maintenance.cancel();
            maintenance = null;
        }
    }

    private boolean warnOnce(String key) {
        return warnedOnce.add(key);
    }

    // ------------------------------------------------------------------------------------------------ consuming

    /**
     * Hands out a prepared position that fits the RTP settings of the requesting player, or null.
     * Safe to call from any thread. The position is removed from the pool, so it can never be handed out twice, and the
     * pool is refilled in the background afterwards.
     */
    @Nullable
    public Location poll(RTPWorld rtpWorld) {
        World world = rtpWorld.getWorld();
        PreloadSettings s = settings.get(world.getName());
        if (s == null || !s.enabled)
            return null;
        for (int i = 0; i < POLL_ATTEMPTS; i++) {
            PreloadPosition p = pool.take(world.getName(), pos -> PositionRange.contains(
                    rtpWorld.getShape(), pos.x, pos.z, rtpWorld.getCenterX(), rtpWorld.getCenterZ(), rtpWorld.getMinRadius(), rtpWorld.getMaxRadius()));
            if (p == null)
                break;
            scheduleRelease(p);
            scheduleSave();
            if (!insideBorder(world, p.x, p.z)) { //Border shrank since it was prepared
                BetterRTP.debug("Discarded preloaded position outside the world border: " + p);
                continue;
            }
            requestFill();
            BetterRTP.debug("Using preloaded position " + p + " (" + pool.size(world.getName()) + " left)");
            return new Location(world, p.x, p.y, p.z);
        }
        requestFill();
        return null;
    }

    /** Releases the chunks of a used position after a short delay, so they stay loaded while the player arrives. */
    private void scheduleRelease(PreloadPosition p) {
        int myToken = token;
        AsyncHandler.syncLater(() -> {
            if (myToken == token) //otherwise unload/load already released everything
                releaseFor(p);
        }, RELEASE_AFTER_USE_TICKS);
    }

    private boolean insideBorder(World world, int x, int z) {
        try {
            WorldBorder border = world.getWorldBorder();
            Location center = border.getCenter();
            return ChunkMath.blockInsideBorder(x, z, center.getX(), center.getZ(), border.getSize());
        } catch (Throwable t) {
            return true;
        }
    }

    // ------------------------------------------------------------------------------------------------ filling

    /** Asks for the pools to be topped up soon (called after a position was used). */
    private void requestFill() {
        synchronized (this) {
            if (fillScheduled || fillRunning)
                return;
            fillScheduled = true;
        }
        int myToken = token;
        AsyncHandler.asyncLater(() -> {
            synchronized (this) {
                fillScheduled = false;
            }
            if (myToken == token)
                fillStep(myToken);
        }, NEXT_POSITION_TICKS);
    }

    private synchronized void scheduleMaintenance(int myToken, long delayTicks) {
        if (myToken != token)
            return;
        maintenance = AsyncHandler.asyncLater(() -> {
            if (myToken != token)
                return;
            fillStep(myToken);
            synchronized (this) {
                scheduleMaintenance(myToken, MAINTENANCE_TICKS);
            }
        }, delayTicks);
    }

    /** Starts preparing one position for the next world that is below its target, if nothing is running already. */
    private void fillStep(int myToken) {
        PreloadSettings target = null;
        synchronized (this) {
            if (fillRunning || myToken != token)
                return;
            List<PreloadSettings> list = new ArrayList<>(settings.values());
            for (int i = 0; i < list.size(); i++) {
                PreloadSettings s = list.get((roundRobin + i) % list.size());
                if (!needsPositions(s))
                    continue;
                target = s;
                roundRobin = (roundRobin + i + 1) % list.size();
                break;
            }
            if (target == null)
                return;
            fillRunning = true;
        }
        findPosition(target, myToken, 0);
    }

    private boolean needsPositions(PreloadSettings s) {
        if (!s.enabled || pool.size(s.world) >= s.positions)
            return false;
        World world = Bukkit.getWorld(s.world);
        if (world == null) {
            if (warnOnce("missing:" + s.world))
                BetterRTP.debug("Pre-generation waiting for world '" + s.world + "' to load");
            return false;
        }
        RTP rtp = pl().getRTP();
        if (rtp.getDisabledWorlds() != null && rtp.getDisabledWorlds().contains(s.world)) {
            if (warnOnce("disabled:" + s.world))
                pl().getLogger().warning("PreloadChunkPosition: world '" + s.world + "' is listed in DisabledWorlds, no positions are prepared for it.");
            return false;
        }
        Long until = backoffUntil.get(s.world);
        return until == null || System.currentTimeMillis() >= until;
    }

    private RTPWorld resolve(World world) {
        RTP rtp = pl().getRTP();
        RTPWorld custom = rtp.getRTPcustomWorld().get(world.getName());
        return custom != null ? custom : new WorldCustom(world, rtp.getRTPdefaultWorld());
    }

    /** Step 1 (async): pick a random candidate that is not a duplicate and lies inside the border. */
    private void findPosition(PreloadSettings s, int myToken, int attempt) {
        if (myToken != token) {
            return; //Reloaded or disabled, a new fill is started by the new maintenance loop
        }
        World world = Bukkit.getWorld(s.world);
        if (world == null || attempt >= LOCATION_ATTEMPTS) {
            if (world != null)
                BetterRTP.debug("Pre-generation for '" + s.world + "' found no usable position after " + attempt + " attempts, pausing");
            endFill(s, myToken, false, BACKOFF_LONG_MS);
            return;
        }
        RTPWorld rtpWorld = resolve(world);
        Location loc;
        try {
            loc = RandomLocation.generateLocation(rtpWorld);
        } catch (Throwable t) {
            loc = null;
        }
        if (loc == null) {
            endFill(s, myToken, false, BACKOFF_LONG_MS);
            return;
        }
        if (pool.conflicts(s.world, loc.getBlockX(), loc.getBlockZ()) || !insideBorder(world, loc.getBlockX(), loc.getBlockZ())) {
            AsyncHandler.async(() -> findPosition(s, myToken, attempt + 1));
            return;
        }
        final Location candidate = loc;
        //Step 2 (main thread): generate the center chunk, then check the spot like a normal RTP does
        AsyncHandler.sync(() -> {
            if (myToken != token) {
                return;
            }
            CompletableFuture<Chunk> center;
            try {
                center = PaperLib.getChunkAtAsync(world, candidate.getBlockX() >> 4, candidate.getBlockZ() >> 4, true);
            } catch (Throwable t) {
                endFill(s, myToken, false, BACKOFF_SHORT_MS);
                return;
            }
            center.whenComplete((chunk, err) -> AsyncHandler.sync(() -> {
                if (myToken != token)
                    return;
                Location safe = null;
                if (err == null && chunk != null) {
                    safe = RandomLocation.getSafeLocation(HelperRTP.getWorldType(world), world, candidate,
                            rtpWorld.getMinY(), rtpWorld.getMaxY(), rtpWorld.getBiomes());
                    if (safe != null && !RTPPlayer.checkDepends(safe)) //claims, region plugins...
                        safe = null;
                }
                if (safe == null)
                    AsyncHandler.async(() -> findPosition(s, myToken, attempt + 1));
                else
                    prepareArea(s, myToken, world, safe);
            }));
        });
    }

    /** Step 3 (main thread, paced): generate the rest of the chunk area, then add the position to the pool. */
    private void prepareArea(PreloadSettings s, int myToken, World world, Location safe) {
        int cx = safe.getBlockX() >> 4, cz = safe.getBlockZ() >> 4;
        Deque<int[]> queue = new ArrayDeque<>();
        List<int[]> area = ChunkMath.area(cx, cz, s.radius);
        WorldBorder border = world.getWorldBorder();
        Location bc = border.getCenter();
        for (int[] c : area)
            if ((c[0] == cx && c[1] == cz) || ChunkMath.chunkInsideBorder(c[0], c[1], bc.getX(), bc.getZ(), border.getSize()))
                queue.add(c);
        boolean keep = s.keepLoaded && PreloadTickets.supported();
        ChunkJob job = new ChunkJob(s.world, queue, true, keep, myToken);
        final PreloadPosition position = new PreloadPosition(s.world, safe.getBlockX(), safe.getBlockY(), safe.getBlockZ(), System.currentTimeMillis());
        job.onDone = ok -> {
            if (!ok) {
                BetterRTP.debug("Pre-generation of " + position + " failed, " + job.failed + " chunk(s) could not be generated");
                releaseKeys(job.acquired);
                endFill(s, myToken, false, BACKOFF_SHORT_MS);
                return;
            }
            if (!pool.add(position)) { //Same spot was added in the meantime
                releaseKeys(job.acquired);
            } else {
                synchronized (positionTickets) {
                    positionTickets.put(position, new ArrayList<>(job.acquired));
                }
                scheduleSave();
                BetterRTP.debug("Pre-generated position " + position + " (" + pool.size(s.world) + "/" + s.positions + ", "
                        + queue.size() + " chunks, keep-loaded=" + keep + ")");
            }
            endFill(s, myToken, true, 0L);
        };
        pump(job);
    }

    /** Ends the running fill and, after a success, continues with the next position soon. */
    private void endFill(PreloadSettings s, int myToken, boolean success, long backoffMs) {
        if (!success && backoffMs > 0)
            backoffUntil.put(s.world, System.currentTimeMillis() + backoffMs);
        synchronized (this) {
            if (myToken != token)
                return;
            fillRunning = false;
        }
        if (success)
            requestFill();
    }

    // ------------------------------------------------------------------------------------------------ chunk jobs

    /** A list of chunks that is worked off with limited concurrency. */
    private static final class ChunkJob {
        final String world;
        final Deque<int[]> queue;
        final boolean generate, keep;
        final int token;
        final List<ChunkTicketCounter.Key> acquired = new ArrayList<>();
        int inFlight, failed;
        java.util.function.Consumer<Boolean> onDone;
        boolean finished;

        ChunkJob(String world, Deque<int[]> queue, boolean generate, boolean keep, int token) {
            this.world = world;
            this.queue = queue;
            this.generate = generate;
            this.keep = keep;
            this.token = token;
        }
    }

    private int maxInFlight() {
        return PaperLib.isPaper() ? 4 : 1; //Without async chunks every request is a synchronous load
    }

    /** Main thread only. */
    private void pump(ChunkJob job) {
        if (job.finished)
            return;
        if (job.token != token) {
            abort(job);
            return;
        }
        World world = Bukkit.getWorld(job.world);
        if (world == null) {
            job.failed++;
            job.queue.clear();
        }
        while (world != null && job.inFlight < maxInFlight() && !job.queue.isEmpty()) {
            int[] c = job.queue.poll();
            job.inFlight++;
            CompletableFuture<Chunk> future;
            try {
                future = PaperLib.getChunkAtAsync(world, c[0], c[1], job.generate);
            } catch (Throwable t) {
                job.inFlight--;
                job.failed++;
                continue;
            }
            future.whenComplete((chunk, err) -> AsyncHandler.sync(() -> chunkDone(job, c, chunk, err)));
        }
        if (job.inFlight == 0 && job.queue.isEmpty()) {
            job.finished = true;
            if (job.onDone != null)
                job.onDone.accept(job.failed == 0);
        }
    }

    private void chunkDone(ChunkJob job, int[] c, @Nullable Chunk chunk, @Nullable Throwable err) {
        job.inFlight--;
        if (job.token != token) { //Reloaded meanwhile: nothing was acquired for this chunk, pump() aborts the job
            pump(job);
            return;
        }
        if (err != null || (chunk == null && job.generate))
            job.failed++;
        else if (chunk != null && job.keep)
            acquire(job, c[0], c[1]);
        if (PaperLib.isPaper())
            pump(job);
        else
            AsyncHandler.syncLater(() -> pump(job), 3L); //Spread synchronous loads over time
    }

    /**
     * Only called for jobs of an older token. Their tickets were already handed back by {@link #releaseAllTickets()}
     * (it clears the counter), so releasing them again would take away references that belong to the new generation.
     */
    private void abort(ChunkJob job) {
        job.finished = true;
        job.queue.clear();
        job.acquired.clear();
    }

    private void acquire(ChunkJob job, int x, int z) {
        World world = Bukkit.getWorld(job.world);
        if (world == null)
            return;
        ChunkTicketCounter.Key key = new ChunkTicketCounter.Key(job.world, x, z);
        if (tickets.acquire(key) && !PreloadTickets.add(world, x, z, pl())) {
            tickets.release(key);
            if (!ticketsWarned) {
                ticketsWarned = true;
                pl().getLogger().warning("Could not add a chunk ticket (are you on Folia?). Chunks are pre-generated but may unload.");
            }
            return;
        }
        job.acquired.add(key);
    }

    // ------------------------------------------------------------------------------------------------ tickets

    private void releaseKeys(List<ChunkTicketCounter.Key> keys) {
        for (ChunkTicketCounter.Key key : new ArrayList<>(keys))
            if (tickets.release(key)) {
                World world = Bukkit.getWorld(key.world);
                if (world != null)
                    PreloadTickets.remove(world, key.x, key.z, pl());
            }
    }

    private void releaseFor(PreloadPosition position) {
        List<ChunkTicketCounter.Key> keys;
        synchronized (positionTickets) {
            keys = positionTickets.remove(position);
        }
        if (keys != null)
            releaseKeys(keys);
    }

    private void releaseAllTickets() {
        synchronized (positionTickets) {
            positionTickets.clear();
        }
        for (ChunkTicketCounter.Key key : tickets.clear()) {
            World world = Bukkit.getWorld(key.world);
            if (world != null)
                PreloadTickets.remove(world, key.x, key.z, pl());
        }
    }

    /** After a restart/reload the saved positions of keep-loaded worlds get their chunk tickets back (already generated chunks only). */
    private void restoreTickets(int myToken) {
        if (!PreloadTickets.supported())
            return;
        for (PreloadSettings s : settings.values()) {
            if (!s.enabled || !s.keepLoaded)
                continue;
            for (PreloadPosition p : pool.snapshot(s.world)) {
                Deque<int[]> queue = new ArrayDeque<>(ChunkMath.area(p.chunkX(), p.chunkZ(), s.radius));
                ChunkJob job = new ChunkJob(s.world, queue, false, true, myToken);
                job.onDone = ok -> {
                    synchronized (positionTickets) {
                        positionTickets.put(p, new ArrayList<>(job.acquired));
                    }
                };
                AsyncHandler.syncLater(() -> pump(job), 40L); //after the worlds are up
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ persistence

    private File file() {
        return new File(pl().getDataFolder(), FILE_NAME);
    }

    private void readFile() {
        File file = file();
        if (!file.exists())
            return;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception e) { //Corrupt YAML: keep the damaged file for the admin instead of overwriting it
            File corrupt = new File(file.getParentFile(), FILE_NAME + ".corrupt");
            try {
                Files.copy(file.toPath(), corrupt.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
            pl().getLogger().warning("Could not read " + FILE_NAME + " (" + e.getMessage() + "), starting with no saved positions. A copy of the file was kept as " + corrupt.getName());
            return;
        }
        int[] skipped = new int[1];
        int loaded = pool.load(yaml.getMapList("positions"), skipped);
        if (loaded > 0 || skipped[0] > 0)
            pl().getLogger().info("Loaded " + loaded + " prepared RTP position(s) from " + FILE_NAME
                    + (skipped[0] > 0 ? " (" + skipped[0] + " invalid or duplicate entries skipped)" : ""));
    }

    private void scheduleSave() {
        synchronized (this) {
            if (saveScheduled)
                return;
            saveScheduled = true;
        }
        AsyncHandler.asyncLater(() -> {
            synchronized (this) {
                saveScheduled = false;
            }
            writeFile();
        }, SAVE_DELAY_TICKS);
    }

    /** Writes through a temporary file so a crash while saving can't leave a half written file behind. */
    private void writeFile() {
        synchronized (fileLock) {
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.set("version", FILE_VERSION);
                yaml.set("positions", pool.toMaps());
                String data = "# Prepared RTP positions (generated by BetterRTP, see PreloadChunkPosition in config.yml).\n"
                        + "# Safe to delete: positions are prepared again. Invalid entries are ignored on startup.\n"
                        + yaml.saveToString();
                File file = file();
                File parent = file.getParentFile();
                if (parent != null && !parent.exists())
                    parent.mkdirs();
                File tmp = new File(parent, FILE_NAME + ".tmp");
                Files.write(tmp.toPath(), data.getBytes(StandardCharsets.UTF_8));
                try {
                    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Throwable t) {
                pl().getLogger().warning("Could not save " + FILE_NAME + ": " + t);
            }
        }
    }
}
