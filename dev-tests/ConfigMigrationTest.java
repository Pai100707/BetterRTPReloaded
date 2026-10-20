import me.SuperRonanCraft.BetterRTP.references.file.config.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Plain-Java tests for config-version handling, the 3.7.2 config migration and the enable/list readers. */
public class ConfigMigrationTest {

    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("  FAIL: " + what); } }
    static void eq(Object e, Object a, String what) { check(Objects.equals(e, a), what + " (expected <" + e + "> was <" + a + ">)"); }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
    static List<Object> list(Object... items) { return new ArrayList<>(Arrays.asList(items)); }

    /** The values of the 3.6.x default config.yml (the layout every existing server has). */
    static Map<String, Object> legacyDefaultRoot() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Overrides", list(map("master_world", "world"), map("creative_world", "world")));
        root.put("DisabledWorlds", list("prison", "creative"));
        root.put("BlacklistedBlocks", list("stationary_water", "water", "lava", "cactus", "air", "bedrock", "kelp"));
        root.put("CustomWorlds", list(
                map("custom_world_1", map("UseWorldBorder", false, "MaxRadius", 1000, "MinRadius", 100)),
                map("other_custom_world", map("MaxRadius", 10000, "MinRadius", 150, "Biomes", list("desert")))));
        return root;
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        System.out.println("== config-version numbers ==");
        eq(361, new ConfigVersion(3, 6, 1).toNumber(), "3.6.1 -> 361");
        eq(372, new ConfigVersion(3, 7, 2).toNumber(), "3.7.2 -> 372");
        eq(999, new ConfigVersion(9, 9, 9).toNumber(), "9.9.9 -> 999");
        eq(379, ConfigVersion.fromString("3.7.9").toNumber(), "3.7.9 -> 379");
        eq(381, ConfigVersion.fromString("3.8.1").toNumber(), "3.8.1 -> 381 (after 3.7.9 comes 3.8.1)");
        eq(new ConfigVersion(3, 7, 2), ConfigVersion.fromNumber(372), "372 -> 3.7.2");
        eq(new ConfigVersion(3, 8, 1), ConfigVersion.fromNumber(381), "381 -> 3.8.1");
        eq(null, ConfigVersion.fromString("3.7"), "bad version string"); eq(null, ConfigVersion.fromString("a.b.c"), "bad version string 2");
        boolean threw = false;
        try { ConfigVersion.LEGACY.toNumber(); } catch (IllegalStateException e) { threw = true; }
        check(threw, "3.6.14 can not be written as a config-version (patch > 9)");
        check(ConfigVersion.LEGACY.compareTo(new ConfigVersion(3, 7, 2)) < 0, "3.6.14 is older than 3.7.2 (tuple compare, not number)");
        check(ConfigVersion.LEGACY.compareTo(new ConfigVersion(3, 6, 9)) > 0, "3.6.14 is newer than 3.6.9");
        eq(ConfigVersion.fromNumber(372), ConfigVersion.read(372), "read Integer"); eq(ConfigVersion.fromNumber(372), ConfigVersion.read("372"), "read numeric string");
        eq(null, ConfigVersion.read("abc"), "read garbage"); eq(null, ConfigVersion.read(null), "read null"); eq(null, ConfigVersion.read(3.5), "read fractional");
        check(ConfigVersion.fromNumber(7).compareTo(ConfigVersion.LEGACY) < 0, "tiny numbers count as older than everything");

        System.out.println("== bundled config.yml / build version agree ==");
        String cfg = new String(Files.readAllBytes(Paths.get("src/main/resources/config.yml")), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("(?m)^config-version:\\s*(\\d+)\\s*$").matcher(cfg);
        check(m.find(), "bundled config.yml has config-version");
        eq(ConfigMigrator.CURRENT, Integer.parseInt(m.group(1)), "bundled config-version equals ConfigMigrator.CURRENT");
        check(cfg.contains("DO NOT CHANGE THIS VALUE"), "config-version has the do-not-change comment");
        String gradle = new String(Files.readAllBytes(Paths.get("build.gradle.kts")), StandardCharsets.UTF_8);
        Matcher v = Pattern.compile("(?m)^version\\s*=\\s*\"([^\"]+)\"").matcher(gradle);
        check(v.find(), "build.gradle.kts has a version");
        ConfigVersion plugin = ConfigVersion.fromString(v.group(1));
        check(plugin != null && ConfigVersion.fromNumber(ConfigMigrator.CURRENT).compareTo(plugin) <= 0, "layout version " + ConfigMigrator.CURRENT + " is not newer than the plugin version " + v.group(1));
        eq("3.7.2", v.group(1), "plugin version is 3.7.2");

        System.out.println("== enable/list reader ==");
        ConfigListSetting s = ConfigListSetting.resolve(list("a", "b"));
        check(s.enabled && s.legacy, "old plain list = enabled (behaves as before)"); eq(Arrays.asList("a", "b"), s.activeStrings(), "old list entries");
        s = ConfigListSetting.resolve(map("enable", false, "list", list("a")));
        check(!s.enabled && !s.legacy, "enable: false"); eq(0, s.activeStrings().size(), "disabled -> nothing active"); eq(1, s.entries.size(), "entries still readable for the edit command");
        s = ConfigListSetting.resolve(map("enable", true, "list", list("a", "b")));
        eq(Arrays.asList("a", "b"), s.activeStrings(), "enabled -> active");
        s = ConfigListSetting.resolve(map("enable", false, "list", null));
        check(!s.enabled && s.entries.isEmpty(), "empty list (all entries commented out)");
        s = ConfigListSetting.resolve(map("enable", "true", "list", list("x"))); check(s.enabled, "enable written as text true");
        s = ConfigListSetting.resolve(map("list", list("x"))); check(!s.enabled, "enable missing -> false in the new layout");
        s = ConfigListSetting.resolve(map("enable", true, "list", "not a list")); check(s.enabled && s.entries.isEmpty(), "garbage list tolerated");
        s = ConfigListSetting.resolve(null); check(!s.enabled && s.entries.isEmpty(), "null");
        s = ConfigListSetting.resolve(42); check(!s.enabled, "garbage value tolerated");

        System.out.println("== custom world enable ==");
        check(CustomWorldSettings.isEnabled(map("enable", true)), "true"); check(!CustomWorldSettings.isEnabled(map("enable", false)), "false -> Default settings");
        check(CustomWorldSettings.isEnabled(map("MaxRadius", 100)), "missing enable = enabled (old configs)");
        check(!CustomWorldSettings.isEnabled(map("enable", "false")), "text false"); check(CustomWorldSettings.isEnabled(map("enable", "banana")), "unreadable -> enabled");
        check(CustomWorldSettings.isEnabled(null), "no settings map = enabled");

        System.out.println("== migration of a 3.6.14 config (no config-version) ==");
        Map<String, Object> root = legacyDefaultRoot();
        ConfigMigrator.Result r = ConfigMigrator.migrate(root);
        check(r.changed() && !r.newer, "changed"); eq(ConfigVersion.LEGACY, r.from, "missing key is read as 3.6.14"); eq(372, root.get("config-version"), "stamped with 372");
        Map<String, Object> ov = (Map<String, Object>) root.get("Overrides");
        eq(true, ov.get("enable"), "overrides that were in use stay enabled");
        eq(list(map("master_world", "world"), map("creative_world", "world")), ov.get("list"), "override entries preserved exactly");
        Map<String, Object> dw = (Map<String, Object>) root.get("DisabledWorlds");
        eq(true, dw.get("enable"), "disabled worlds stay in effect"); eq(list("prison", "creative"), dw.get("list"), "disabled worlds preserved");
        List<Object> blocks = (List<Object>) root.get("BlacklistedBlocks");
        check(blocks.contains("powder_snow"), "powder_snow added"); eq("kelp", blocks.get(blocks.size() - 2), "existing blocks untouched, powder_snow appended"); eq(8, blocks.size(), "one block added");
        List<Object> cw = (List<Object>) root.get("CustomWorlds");
        Map<String, Object> w1 = (Map<String, Object>) ((Map<String, Object>) cw.get(0)).get("custom_world_1");
        eq(true, w1.get("enable"), "existing custom worlds get enable: true"); eq("enable", w1.keySet().iterator().next(), "enable is written first");
        eq(1000, w1.get("MaxRadius"), "custom world values untouched"); eq(100, w1.get("MinRadius"), "custom world values untouched 2");
        Map<String, Object> w2 = (Map<String, Object>) ((Map<String, Object>) cw.get(1)).get("other_custom_world");
        eq(list("desert"), w2.get("Biomes"), "nested lists preserved");

        System.out.println("== empty / customized legacy values ==");
        root = new LinkedHashMap<>(); root.put("Overrides", list()); root.put("DisabledWorlds", list());
        ConfigMigrator.migrate(root);
        eq(false, ((Map<String, Object>) root.get("Overrides")).get("enable"), "empty override list -> disabled"); eq(false, ((Map<String, Object>) root.get("DisabledWorlds")).get("enable"), "empty disabled list -> disabled");
        root = new LinkedHashMap<>(); root.put("BlacklistedBlocks", list("AIR", "POWDER_SNOW"));
        r = ConfigMigrator.migrate(root);
        eq(2, ((List<Object>) root.get("BlacklistedBlocks")).size(), "powder snow in any case is not added twice");
        root = new LinkedHashMap<>(); // nothing configured at all (new options come from the defaults)
        r = ConfigMigrator.migrate(root);
        eq(1, root.size(), "only config-version is written for an empty config"); eq(372, root.get("config-version"), "stamped");

        System.out.println("== idempotent, partially migrated, newer, unreadable ==");
        root = legacyDefaultRoot(); ConfigMigrator.migrate(root);
        Map<String, Object> snapshot = deepCopy(root);
        r = ConfigMigrator.migrate(root);
        check(!r.changed(), "second run changes nothing"); eq(snapshot, root, "tree identical after second run");
        // version key lost but layout already new
        root.remove("config-version"); r = ConfigMigrator.migrate(root);
        eq(snapshot, root, "layout already new + missing version: only the version is stamped back"); eq(1, r.changes.size(), "one change reported: " + r.changes);
        // administrator disabled things after the migration, version says 372: must not be touched
        root = deepCopy(snapshot); ((Map<String, Object>) root.get("Overrides")).put("enable", false); ((List<Object>) root.get("BlacklistedBlocks")).remove("powder_snow");
        Map<String, Object> admin = deepCopy(root);
        r = ConfigMigrator.migrate(root);
        check(!r.changed(), "up to date config is never touched"); eq(admin, root, "admin's choices survive (powder_snow stays removed, overrides stay off)");
        // newer than the plugin
        root = legacyDefaultRoot(); root.put("config-version", 381); Map<String, Object> newer = deepCopy(root);
        r = ConfigMigrator.migrate(root);
        check(r.newer && !r.changed(), "newer config is left alone"); eq(newer, root, "nothing modified for a newer config");
        // unreadable version
        root = legacyDefaultRoot(); root.put("config-version", "banana");
        r = ConfigMigrator.migrate(root);
        check(r.unreadableVersion && r.changed(), "unreadable version treated as legacy"); eq(372, root.get("config-version"), "unreadable version replaced");
        check(root.get("Overrides") instanceof Map, "migration ran for unreadable version");
        // partially migrated: one option new, one still old
        root = legacyDefaultRoot(); root.put("Overrides", map("enable", false, "list", list(map("a", "b"))));
        ConfigMigrator.migrate(root);
        eq(false, ((Map<String, Object>) root.get("Overrides")).get("enable"), "already converted Overrides left as the admin set it");
        check(root.get("DisabledWorlds") instanceof Map, "other options still converted");
        // custom world that already has enable: false keeps it
        root = new LinkedHashMap<>(); root.put("CustomWorlds", list(map("world_nether", map("enable", false, "MaxY", 120))));
        ConfigMigrator.migrate(root);
        Map<String, Object> nether = (Map<String, Object>) ((Map<String, Object>) ((List<Object>) root.get("CustomWorlds")).get(0)).get("world_nether");
        eq(false, nether.get("enable"), "existing enable: false is kept"); eq(120, nether.get("MaxY"), "values kept");
        // 3.7.1 config (has the preload section, no version) behaves like legacy
        root = legacyDefaultRoot(); root.remove("config-version");
        check(ConfigMigrator.migrate(root).changed(), "3.7.1 config (no version key) is updated");

        System.out.println();
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> deepCopy(Map<String, Object> m) { return (Map<String, Object>) copy(m); }
    static Object copy(Object o) {
        if (o instanceof Map) { Map<Object, Object> n = new LinkedHashMap<>(); for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) n.put(e.getKey(), copy(e.getValue())); return n; }
        if (o instanceof List) { List<Object> n = new ArrayList<>(); for (Object x : (List<?>) o) n.add(copy(x)); return n; }
        return o;
    }
}
