package me.SuperRonanCraft.BetterRTP.references.file.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A config option that is a list with an on/off switch, used by {@code Overrides} and {@code DisabledWorlds}:
 * <pre>
 * DisabledWorlds:
 *   enable: false
 *   list:
 *     - prison
 * </pre>
 * Older configs wrote the list directly under the key; that layout is still understood (it counts as enabled, exactly
 * as before) so a config that was not migrated yet, or a hand edited one, keeps working.
 */
public final class ConfigListSetting {

    public final boolean enabled;
    /** The entries as written, whether or not the setting is enabled (the edit command needs them either way). */
    public final List<Object> entries;
    /** True when the old layout (list directly under the key) was found. */
    public final boolean legacy;

    private ConfigListSetting(boolean enabled, List<Object> entries, boolean legacy) {
        this.enabled = enabled;
        this.entries = Collections.unmodifiableList(entries);
        this.legacy = legacy;
    }

    /**
     * @param raw the value found at the key: null, a List (old layout) or a Map with {@code enable} and {@code list}
     *            (a Bukkit section has to be converted to a Map first)
     */
    public static ConfigListSetting resolve(Object raw) {
        if (raw instanceof List<?>)
            return new ConfigListSetting(true, new ArrayList<Object>((List<?>) raw), true);
        if (raw instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) raw;
            boolean enabled = parseBoolean(map.get("enable"));
            Object list = map.get("list");
            List<Object> entries = list instanceof List<?> ? new ArrayList<Object>((List<?>) list) : new ArrayList<>();
            return new ConfigListSetting(enabled, entries, false);
        }
        return new ConfigListSetting(false, new ArrayList<>(), false);
    }

    /** The entries that are in effect: all of them when enabled, none when disabled. */
    public List<Object> active() {
        return enabled ? entries : Collections.emptyList();
    }

    /** Only the plain text entries (world names). Entries that are not text are ignored. */
    public List<String> activeStrings() {
        List<String> out = new ArrayList<>();
        for (Object o : active())
            if (o != null)
                out.add(o.toString());
        return out;
    }

    private static boolean parseBoolean(Object o) {
        if (o instanceof Boolean)
            return (Boolean) o;
        return o instanceof String && "true".equalsIgnoreCase(((String) o).trim());
    }
}
