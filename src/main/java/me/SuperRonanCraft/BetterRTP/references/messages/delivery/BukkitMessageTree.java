package me.SuperRonanCraft.BetterRTP.references.messages.delivery;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** {@link MessageTree} backed by a Bukkit {@link ConfigurationSection}. */
public class BukkitMessageTree implements MessageTree {

    private final ConfigurationSection root;
    private final boolean includeDefaults;

    /** Reads exactly what is stored in the file (used by the migration). */
    public BukkitMessageTree(ConfigurationSection root) {
        this(root, false);
    }

    /**
     * @param includeDefaults true = values missing from the file fall back to the bundled defaults of the
     *                        configuration (used when sending messages), false = only what the file really contains.
     */
    public BukkitMessageTree(ConfigurationSection root, boolean includeDefaults) {
        this.root = root;
        this.includeDefaults = includeDefaults;
    }

    /**
     * Reads the node exactly as it is stored in the file. {@code get(path, null)} deliberately skips the configured
     * defaults (plain {@code get(path)} would fall back to the bundled defaults and hide legacy/missing nodes).
     */
    private Object raw(String path) {
        return includeDefaults ? root.get(path) : root.get(path, null);
    }

    @Override
    public Kind kind(String path) {
        Object o = raw(path);
        if (o == null) return Kind.ABSENT;
        if (o instanceof ConfigurationSection) return Kind.SECTION;
        if (o instanceof String) return Kind.STRING;
        if (o instanceof Boolean) return Kind.BOOLEAN;
        if (o instanceof Number) return Kind.NUMBER;
        return Kind.OTHER;
    }

    @Override
    public String string(String path) {
        Object o = raw(path);
        return o instanceof String ? (String) o : null;
    }

    @Override
    public Boolean bool(String path) {
        Object o = raw(path);
        return o instanceof Boolean ? (Boolean) o : null;
    }

    @Override
    public Integer number(String path) {
        Object o = raw(path);
        if (!(o instanceof Number))
            return null;
        double d = ((Number) o).doubleValue();
        if (d != Math.rint(d) || d > Integer.MAX_VALUE || d < Integer.MIN_VALUE)
            return null; //fractional or out of range
        return (int) d;
    }

    @Override
    public void set(String path, Object value) {
        root.set(path, value);
    }

    @Override
    public void remove(String path) {
        root.set(path, null);
    }

    @Override
    public Set<String> keys(String path) {
        Object o = raw(path);
        if (!(o instanceof ConfigurationSection))
            return Collections.emptySet();
        return new LinkedHashSet<>(((ConfigurationSection) o).getKeys(false));
    }
}
