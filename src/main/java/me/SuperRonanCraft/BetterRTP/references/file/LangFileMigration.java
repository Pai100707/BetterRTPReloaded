package me.SuperRonanCraft.BetterRTP.references.file;

import me.SuperRonanCraft.BetterRTP.references.messages.MessagesCore;
import me.SuperRonanCraft.BetterRTP.references.messages.delivery.BukkitMessageTree;
import me.SuperRonanCraft.BetterRTP.references.messages.delivery.MessageMigration;
import me.SuperRonanCraft.BetterRTP.references.messages.delivery.MessageTree;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Applies {@link MessageMigration} to a language file when it is loaded.
 * <p>
 * Only runs when the file still contains messages in the 3.6.x layout (or shorthand nodes). Before anything is changed
 * the original file is copied to {@code <name>.pre-3.7.1.bak}; an existing backup is never overwritten, so the very first
 * (original) version of the administrator's file always survives repeated runs. If the backup cannot be written or the
 * migration fails, nothing is changed and the file keeps working through the legacy fallback of the message reader.
 */
public final class LangFileMigration {

    public static final String BACKUP_SUFFIX = ".pre-3.7.1.bak";

    private LangFileMigration() {
    }

    /**
     * Must be called after the file was loaded into {@code config} and <b>before</b> defaults are attached to it,
     * so that only what really is in the file is looked at.
     *
     * @param defaults bundled defaults for this language (new layout), may be null
     * @return true when the in-memory configuration was migrated (the caller saves it afterwards as usual)
     */
    public static boolean migrate(File file, YamlConfiguration config, YamlConfiguration defaults, Logger log) {
        List<String> paths = MessagesCore.paths();
        MessageTree tree = new BukkitMessageTree(config);
        try {
            if (!MessageMigration.needsMigration(tree, paths))
                return false;
        } catch (Throwable t) {
            log.warning("Could not inspect " + file.getName() + " for message migration: " + t);
            return false;
        }
        File backup = new File(file.getParentFile(), file.getName() + BACKUP_SUFFIX);
        try {
            if (!backup.exists())
                Files.copy(file.toPath(), backup.toPath());
            MessageTree defaultTree = defaults == null ? null : new BukkitMessageTree(defaults);
            MessageMigration.Result result = MessageMigration.migrate(tree, defaultTree, paths);
            log.info("Migrated " + file.getName() + " to the 3.7.1 message format ("
                    + result.migratedLegacy + " messages converted, " + result.expandedShorthand + " shorthand nodes expanded, "
                    + result.addedNodes + " missing nodes added). Backup: " + backup.getName());
            for (String skipped : result.skipped)
                log.warning(file.getName() + ": " + skipped + " is not a text or a section and was left untouched.");
            return result.changed();
        } catch (Throwable t) {
            log.warning("Could not migrate " + file.getName() + " to the 3.7.1 message format, it was left unchanged: " + t);
            restore(file, config, log);
            return false;
        }
    }

    /** Throws away any half-done in-memory changes by reading the untouched file again. */
    private static void restore(File file, YamlConfiguration config, Logger log) {
        try {
            for (String key : new ArrayList<>(config.getKeys(false)))
                config.set(key, null);
            config.load(file);
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            log.warning("Could not reload " + file.getName() + " after a failed migration: " + e);
        }
    }
}
