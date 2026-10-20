package me.SuperRonanCraft.BetterRTP.references.file.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads and writes the switchable list options ({@code Overrides}, {@code DisabledWorlds}) of config.yml. */
public final class ConfigLists {

    private ConfigLists() {
    }

    /** Understands both the new {@code enable/list} layout and the old plain list. */
    public static ConfigListSetting read(ConfigurationSection config, String path) {
        Object raw = config.get(path);
        if (raw instanceof ConfigurationSection) {
            ConfigurationSection section = (ConfigurationSection) raw;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("enable", section.get("enable"));
            map.put("list", section.get("list"));
            raw = map;
        }
        return ConfigListSetting.resolve(raw);
    }

    /** The entries that are maps ({@code - world: 'other'}), whether or not the option is enabled. */
    public static List<Map<?, ?>> maps(ConfigListSetting setting) {
        List<Map<?, ?>> maps = new ArrayList<>();
        for (Object entry : setting.entries)
            if (entry instanceof Map<?, ?>)
                maps.add((Map<?, ?>) entry);
        return maps;
    }

    /** Only the maps that are in effect (none when the option is disabled). */
    public static List<Map<?, ?>> activeMaps(ConfigListSetting setting) {
        List<Map<?, ?>> maps = new ArrayList<>();
        for (Object entry : setting.active())
            if (entry instanceof Map<?, ?>)
                maps.add((Map<?, ?>) entry);
        return maps;
    }

    /** Writes the new layout. An old plain list at the path is replaced. */
    public static void write(ConfigurationSection config, String path, boolean enable, List<?> entries) {
        if (!(config.get(path, null) instanceof ConfigurationSection))
            config.set(path, null);
        config.set(path + ".enable", enable);
        config.set(path + ".list", entries);
    }
}
