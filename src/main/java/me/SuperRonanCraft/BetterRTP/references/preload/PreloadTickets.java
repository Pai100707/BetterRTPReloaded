package me.SuperRonanCraft.BetterRTP.references.preload;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Keeps chunks loaded with Paper's <i>plugin chunk tickets</i> ({@code World#addPluginChunkTicket}).
 * <p>
 * A plugin ticket is the only mechanism the Paper API offers that keeps a chunk loaded without writing anything to the
 * world data: it is gone after a restart, other plugins' tickets are unaffected, and the chunk unloads normally once
 * the last ticket is removed. The methods do not exist on Spigot / old Paper versions (and the plugin is compiled against
 * a much older API), so they are looked up once through reflection and {@link #supported()} tells whether they are there.
 */
final class PreloadTickets {

    private static boolean looked;
    private static Method add, remove;

    private PreloadTickets() {
    }

    private static synchronized void lookup() {
        if (looked)
            return;
        looked = true;
        try {
            add = World.class.getMethod("addPluginChunkTicket", int.class, int.class, Plugin.class);
            remove = World.class.getMethod("removePluginChunkTicket", int.class, int.class, Plugin.class);
        } catch (Throwable e) {
            add = null;
            remove = null;
        }
    }

    static boolean supported() {
        lookup();
        return add != null && remove != null;
    }

    /** @return true if the ticket was added */
    static boolean add(World world, int x, int z, Plugin plugin) {
        if (!supported())
            return false;
        try {
            Object result = add.invoke(world, x, z, plugin);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable e) {
            return false;
        }
    }

    static void remove(World world, int x, int z, Plugin plugin) {
        if (!supported())
            return;
        try {
            remove.invoke(world, x, z, plugin);
        } catch (Throwable ignored) {
            //The chunk or world is already gone, nothing left to release
        }
    }
}
