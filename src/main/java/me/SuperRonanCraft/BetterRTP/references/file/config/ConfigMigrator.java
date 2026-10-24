package me.SuperRonanCraft.BetterRTP.references.file.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Brings the layout of an older config.yml up to date. Works on plain Java maps/lists (what a YAML parser produces)
 * so it can be tested without a server; {@code ConfigFileMigration} connects it to the real file.
 *
 * <p>Every step is written to be safe to run on a config that already has the new layout (it checks what is there
 * before touching anything), so a wrong or hand edited {@code config-version} can never damage a file: it only decides
 * whether steps are attempted.
 *
 * <h3>Adding a migration for a later release</h3>
 * Add a step with the new layout version to {@link #STEPS}, raise {@link #CURRENT} and the {@code config-version} in the
 * bundled config.yml (a test checks that both agree).
 */
public final class ConfigMigrator {

    public static final String VERSION_KEY = "config-version";
    /** Layout version of the config written by this plugin version: 3.7.2 -> 372. */
    public static final int CURRENT = 372;

    private ConfigMigrator() {
    }

    public static final class Result {
        public ConfigVersion from;
        public ConfigVersion to;
        /** The stored value was not a readable number and was replaced. */
        public boolean unreadableVersion;
        /** The file was written by a newer plugin: nothing was touched. */
        public boolean newer;
        public final List<String> changes = new ArrayList<>();

        /** Is there anything to write back to the file? */
        public boolean changed() {
            return !newer && (!changes.isEmpty());
        }
    }

    private interface Step {
        ConfigVersion version();

        void apply(Map<String, Object> root, List<String> changes);
    }

    private static final List<Step> STEPS = new ArrayList<>();

    static {
        STEPS.add(new Step372());
    }

    /**
     * @param root the top level keys of the config (only the keys the steps care about are needed). Modified in place.
     */
    public static Result migrate(Map<String, Object> root) {
        Result result = new Result();
        Object raw = root.get(VERSION_KEY);
        ConfigVersion found = ConfigVersion.read(raw);
        if (found == null) {
            result.unreadableVersion = raw != null;
            found = ConfigVersion.LEGACY; //No key (or garbage) = written before the key existed
        }
        ConfigVersion current = ConfigVersion.fromNumber(CURRENT);
        result.from = found;
        result.to = current;
        if (found.compareTo(current) > 0) {
            result.newer = true;
            return result;
        }
        for (Step step : STEPS)
            if (found.compareTo(step.version()) < 0)
                step.apply(root, result.changes);
        boolean stored = raw instanceof Number && ((Number) raw).doubleValue() == CURRENT;
        if (!stored) {
            root.put(VERSION_KEY, CURRENT);
            result.changes.add(VERSION_KEY + " set to " + CURRENT);
        }
        return result;
    }

    // ---------------------------------------------------------------------------------------------- 3.7.2
    private static final class Step372 implements Step {

        @Override
        public ConfigVersion version() {
            return new ConfigVersion(3, 7, 2);
        }

        @Override
        public void apply(Map<String, Object> root, List<String> changes) {
            switchableList(root, "Overrides", changes);
            switchableList(root, "DisabledWorlds", changes);
            addBlockedBlock(root, "powder_snow", changes);
            customWorldsEnable(root, changes);
        }

        /**
         * {@code Key: [a, b]} becomes {@code Key: {enable: true, list: [a, b]}}. A list that was in use stays in use,
         * an empty one becomes disabled, so nothing behaves differently after the update.
         */
        private void switchableList(Map<String, Object> root, String key, List<String> changes) {
            Object value = root.get(key);
            if (!(value instanceof List<?>))
                return; //Absent (defaults will add it) or already the new layout
            List<?> list = (List<?>) value;
            Map<String, Object> section = new LinkedHashMap<>();
            section.put("enable", !list.isEmpty());
            section.put("list", new ArrayList<Object>(list));
            root.put(key, section);
            changes.add(key + " converted to enable/list layout (enable: " + !list.isEmpty() + ", " + list.size() + " entries kept)");
        }

        private void addBlockedBlock(Map<String, Object> root, String block, List<String> changes) {
            Object value = root.get("BlacklistedBlocks");
            if (!(value instanceof List<?>))
                return;
            for (Object o : (List<?>) value)
                if (o != null && block.equalsIgnoreCase(o.toString()))
                    return;
            List<Object> list = new ArrayList<Object>((List<?>) value);
            list.add(block);
            root.put("BlacklistedBlocks", list);
            changes.add("BlacklistedBlocks: added " + block);
        }

        /** Every configured custom world keeps being used: it gets {@code enable: true}. */
        private void customWorldsEnable(Map<String, Object> root, List<String> changes) {
            Object value = root.get("CustomWorlds");
            if (!(value instanceof List<?>))
                return;
            List<Object> out = new ArrayList<>();
            boolean modified = false;
            for (Object entry : (List<?>) value) {
                if (!(entry instanceof Map<?, ?>)) {
                    out.add(entry);
                    continue;
                }
                Map<Object, Object> newEntry = new LinkedHashMap<>();
                for (Map.Entry<?, ?> world : ((Map<?, ?>) entry).entrySet()) {
                    Object settings = world.getValue();
                    if (settings instanceof Map<?, ?> && !((Map<?, ?>) settings).containsKey("enable")) {
                        Map<Object, Object> withEnable = new LinkedHashMap<>();
                        withEnable.put("enable", true);
                        withEnable.putAll((Map<?, ?>) settings);
                        newEntry.put(world.getKey(), withEnable);
                        modified = true;
                        changes.add("CustomWorlds." + world.getKey() + ": added enable: true");
                    } else
                        newEntry.put(world.getKey(), settings);
                }
                out.add(newEntry);
            }
            if (modified)
                root.put("CustomWorlds", out);
        }
    }
}
