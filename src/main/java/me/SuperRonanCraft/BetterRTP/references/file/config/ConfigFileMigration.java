package me.SuperRonanCraft.BetterRTP.references.file.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Applies {@link ConfigMigrator} to config.yml when it is loaded.
 * <p>
 * Runs after the file was read and <b>before</b> the bundled defaults are attached (otherwise the defaults would fill in
 * {@code config-version} and hide that the file is old). Only the options a migration step touches are rewritten; the rest of
 * the file, including every value the administrator changed, stays as it is. The original file is copied to
 * {@code config.yml.pre-3.7.2.bak} first (an existing backup is never overwritten). If anything goes wrong the file is left alone.
 */
public final class ConfigFileMigration {

    public static final String BACKUP_SUFFIX = ".pre-3.7.2.bak";

    /** Top level options the migration steps read or write. */
    private static final List<String> KEYS = Arrays.asList(
            ConfigMigrator.VERSION_KEY, "Overrides", "DisabledWorlds", "BlacklistedBlocks", "CustomWorlds");

    private ConfigFileMigration() {
    }

    /**
     * @param bundled the config.yml shipped in the jar, used to restore the explanatory comments of rewritten options
     * @return true when the in-memory configuration was changed (the caller saves it as usual)
     */
    public static boolean migrate(File file, YamlConfiguration config, YamlConfiguration bundled, Logger log) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            Map<String, Object> before = new LinkedHashMap<>();
            for (String key : KEYS) {
                Object raw = config.get(key, null); //Exactly what is in the file, no defaults
                if (raw != null) {
                    root.put(key, toPlain(raw));
                    before.put(key, toPlain(raw));
                }
            }
            ConfigMigrator.Result result = ConfigMigrator.migrate(root);
            if (result.newer) {
                log.warning(file.getName() + " was written by a newer BetterRTP (config-version is newer than "
                        + ConfigMigrator.CURRENT + "). It is left untouched; consider updating the plugin.");
                return false;
            }
            if (result.unreadableVersion)
                log.warning(file.getName() + ": config-version is not a number, treating the file as written by " + ConfigVersion.LEGACY + ".");
            if (!result.changed())
                return false;

            File backup = new File(file.getParentFile(), file.getName() + BACKUP_SUFFIX);
            if (!backup.exists())
                Files.copy(file.toPath(), backup.toPath());

            List<String> rewritten = new ArrayList<>();
            for (String key : KEYS) {
                Object after = root.get(key);
                if (Objects.equals(after, before.get(key)))
                    continue;
                if (after instanceof Map<?, ?>) {
                    config.set(key, null);
                    config.createSection(key, (Map<?, ?>) after);
                } else
                    config.set(key, after);
                rewritten.add(key);
            }
            for (String key : rewritten) { //Bring back the comments that describe the (possibly new) options
                YamlComments.copy(bundled, config, key);
                YamlComments.copy(bundled, config, key + ".enable");
                YamlComments.copy(bundled, config, key + ".list");
            }
            log.info("Updated " + file.getName() + " from config-version " + result.from + " to " + ConfigMigrator.CURRENT
                    + ". Backup: " + backup.getName());
            for (String change : result.changes)
                log.info("  - " + change);
            return true;
        } catch (Throwable t) {
            log.warning("Could not update " + file.getName() + " to config-version " + ConfigMigrator.CURRENT + ", it was left unchanged: " + t);
            restore(file, config, log);
            return false;
        }
    }

    /** Sections become maps (recursively), lists are copied; everything else is kept. */
    private static Object toPlain(Object raw) {
        if (raw instanceof ConfigurationSection) {
            ConfigurationSection section = (ConfigurationSection) raw;
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : section.getKeys(false))
                map.put(key, toPlain(section.get(key, null)));
            return map;
        }
        if (raw instanceof List<?>) {
            List<Object> list = new ArrayList<>();
            for (Object o : (List<?>) raw)
                list.add(toPlain(o));
            return list;
        }
        if (raw instanceof Map<?, ?>) {
            Map<Object, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet())
                map.put(e.getKey(), toPlain(e.getValue()));
            return map;
        }
        return raw;
    }

    /** Throws away half-done in-memory changes by reading the untouched file again. */
    private static void restore(File file, YamlConfiguration config, Logger log) {
        try {
            for (String key : new ArrayList<>(config.getKeys(false)))
                config.set(key, null);
            config.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            log.warning("Could not reload " + file.getName() + " after a failed update: " + e);
        }
    }
}
