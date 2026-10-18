package me.SuperRonanCraft.BetterRTP.references.messages.delivery;

import java.util.Set;

/**
 * Minimal read/write view of a YAML tree.
 * <p>
 * The message migration only needs these few operations, so it is written against this interface. The plugin
 * uses {@link BukkitMessageTree} (a thin wrapper around a Bukkit ConfigurationSection); the migration tests use a plain
 * in-memory implementation, which keeps the migration logic testable without a running server.
 */
public interface MessageTree {

    enum Kind {
        ABSENT, STRING, BOOLEAN, NUMBER, SECTION, OTHER
    }

    /** What kind of node lives at the (dot separated) path. */
    Kind kind(String path);

    /** The string at the path, or null if the node is not a string. */
    String string(String path);

    /** The boolean at the path, or null if the node is not a boolean. */
    Boolean bool(String path);

    /** The whole number at the path, or null if the node is not a number or not a whole number (2.5, 1e20). */
    Integer number(String path);

    /** Sets (or replaces) the node at the path. Missing parent sections are created. */
    void set(String path, Object value);

    /** Removes the node at the path (used to turn a legacy string into a section). */
    void remove(String path);

    /** Direct child keys of the section at the path (empty when it is not a section). */
    Set<String> keys(String path);
}
