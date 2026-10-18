import me.SuperRonanCraft.BetterRTP.references.messages.delivery.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Plain-Java tests for the message migration/parsing core (no server, no JUnit, no Bukkit needed).
 * Run with dev-tests/run.sh
 */
public class MessageMigrationTest {

    // ---------- tiny in-memory tree ----------
    static class MapTree implements MessageTree {
        final Map<String, Object> root = new LinkedHashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> parent(String path, boolean create) {
            String[] parts = path.split("\\.");
            Map<String, Object> cur = root;
            for (int i = 0; i < parts.length - 1; i++) {
                Object next = cur.get(parts[i]);
                if (!(next instanceof Map)) {
                    if (!create) return null;
                    next = new LinkedHashMap<String, Object>();
                    cur.put(parts[i], next);
                }
                cur = (Map<String, Object>) next;
            }
            return cur;
        }

        String last(String path) { return path.substring(path.lastIndexOf('.') + 1); }

        Object raw(String path) {
            Map<String, Object> p = parent(path, false);
            return p == null ? null : p.get(last(path));
        }

        public Kind kind(String path) {
            Object o = raw(path);
            if (o == null) return Kind.ABSENT;
            if (o instanceof Map) return Kind.SECTION;
            if (o instanceof String) return Kind.STRING;
            if (o instanceof Boolean) return Kind.BOOLEAN;
            if (o instanceof Number) return Kind.NUMBER;
            return Kind.OTHER;
        }
        public String string(String path) { Object o = raw(path); return o instanceof String ? (String) o : null; }
        public Boolean bool(String path) { Object o = raw(path); return o instanceof Boolean ? (Boolean) o : null; }
        public Integer number(String path) {
            Object o = raw(path);
            if (!(o instanceof Number)) return null;
            double d = ((Number) o).doubleValue();
            return d != Math.rint(d) || d > Integer.MAX_VALUE || d < Integer.MIN_VALUE ? null : Integer.valueOf((int) d);
        }
        public void set(String path, Object value) { parent(path, true).put(last(path), value); }
        public void remove(String path) { Map<String, Object> p = parent(path, false); if (p != null) p.remove(last(path)); }
        @SuppressWarnings("unchecked")
        public Set<String> keys(String path) { Object o = raw(path); return o instanceof Map ? new LinkedHashSet<>(((Map<String, Object>) o).keySet()) : Collections.emptySet(); }

        /** Flatten to path=value lines for comparisons. */
        void flatten(String prefix, Map<String, Object> m, Map<String, Object> out) {
            for (Map.Entry<String, Object> e : m.entrySet()) {
                String k = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
                if (e.getValue() instanceof Map) { @SuppressWarnings("unchecked") Map<String, Object> mm = (Map<String, Object>) e.getValue(); flatten(k, mm, out); }
                else out.put(k, e.getValue());
            }
        }
        Map<String, Object> flat() { Map<String, Object> out = new LinkedHashMap<>(); flatten("", root, out); return out; }
    }

    // ---------- mini YAML reader (subset used by the lang files: nested maps, scalars, no lists) ----------
    static MapTree loadYaml(String file) throws IOException {
        MapTree tree = new MapTree();
        Deque<Integer> indents = new ArrayDeque<>();
        Deque<String> keys = new ArrayDeque<>();
        for (String line : Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') indent++;
            while (!indents.isEmpty() && indents.peek() >= indent) { indents.pop(); keys.pop(); }
            String body = line.substring(indent);
            int colon = body.indexOf(':');
            String key = body.substring(0, colon).trim();
            String val = body.substring(colon + 1).trim();
            List<String> path = new ArrayList<>(keys);
            Collections.reverse(path);
            path.add(key);
            String full = String.join(".", path);
            if (val.isEmpty() || val.startsWith("#")) { // section
                tree.set(full + ".__tmp", "x"); tree.remove(full + ".__tmp");
                indents.push(indent); keys.push(key);
            } else if (val.startsWith("'")) {
                int end = val.length() - 1;
                while (end > 0 && val.charAt(end) != '\'') end--;
                tree.set(full, val.substring(1, end).replace("''", "'"));
            } else if (val.equals("true") || val.equals("false")) tree.set(full, Boolean.parseBoolean(val));
            else if (val.matches("-?\\d+")) tree.set(full, Integer.parseInt(val));
            else tree.set(full, val);
        }
        return tree;
    }

    // ---------- assertions ----------
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) {
        checks++;
        if (!ok) { failures++; System.out.println("  FAIL: " + what); }
    }
    static void eq(Object expected, Object actual, String what) {
        check(Objects.equals(expected, actual), what + " (expected <" + expected + "> but was <" + actual + ">)");
    }

    static final List<String> PATHS = new ArrayList<>();
    static {
        for (String s : new String[]{"Success.Paid", "Success.Bypass", "Success.Loading", "Success.Teleport", "Failed.NotSafe", "Failed.Price",
                "Failed.Hunger", "Other.NotSafe", "Other.Success", "Other.Biome", "NotExist", "Reload", "Update", "NoPermission.Basic",
                "NoPermission.World", "DisabledWorld", "Cooldown", "Locked", "Invalid", "NotOnline", "Delay", "Sign", "Moved", "Already",
                "Edit.Error", "Edit.Set", "Edit.Remove"})
            PATHS.add("Messages." + s);
    }

    public static void main(String[] args) throws Exception {
        String legacyFile = args[0];   // legacy 3.6.x en.yml
        String newFile = args[1];      // bundled 3.7.1 en.yml
        MapTree legacy = loadYaml(legacyFile);
        MapTree defaults = loadYaml(newFile);

        System.out.println("== 1. bundled default file is fully migrated and has every legacy message ==");
        eq(false, MessageMigration.needsMigration(defaults, PATHS), "bundled defaults need no migration");
        MapTree legacyCopy0 = loadYaml(legacyFile);
        for (String p : PATHS) {
            check(legacyCopy0.kind(p) == MessageTree.Kind.STRING, "legacy has " + p);
            eq(legacyCopy0.string(p), defaults.string(p + ".chat.text"), "default chat text equals original wording for " + p);
            eq(true, defaults.bool(p + ".chat.enable"), "chat enabled by default " + p);
            eq(false, defaults.bool(p + ".actionbar.enable"), "actionbar disabled by default " + p);
            eq(false, defaults.bool(p + ".title.enable"), "title disabled by default " + p);
        }
        eq(legacyCopy0.string("Messages.Prefix"), defaults.string("Messages.Prefix"), "prefix unchanged");
        for (Map.Entry<String, Object> e : legacyCopy0.flat().entrySet())
            if (e.getKey().startsWith("Help.") || e.getKey().startsWith("Usage."))
                eq(e.getValue(), defaults.flat().get(e.getKey()), "Help/Usage preserved: " + e.getKey());

        System.out.println("== 2. legacy file migrates, keeps customizations ==");
        MapTree cfg = loadYaml(legacyFile);
        cfg.set("Messages.Success.Paid", "&6MY CUSTOM PAID %x% %price%"); // administrator customization
        cfg.set("Messages.Cooldown", "&cWait %time%!");
        eq(true, MessageMigration.needsMigration(cfg, PATHS), "legacy needs migration");
        MessageMigration.Result r = MessageMigration.migrate(cfg, defaults, PATHS);
        eq(PATHS.size(), r.migratedLegacy, "all messages migrated");
        eq("&6MY CUSTOM PAID %x% %price%", cfg.string("Messages.Success.Paid.chat.text"), "custom text preserved");
        eq("&cWait %time%!", cfg.string("Messages.Cooldown.chat.text"), "custom cooldown preserved");
        eq(true, cfg.bool("Messages.Success.Paid.chat.enable"), "migrated chat enabled");
        eq(false, cfg.bool("Messages.Success.Paid.actionbar.enable"), "migrated actionbar disabled");
        eq(defaults.string("Messages.Success.Paid.actionbar.text"), cfg.string("Messages.Success.Paid.actionbar.text"), "actionbar text from defaults");
        eq(false, cfg.bool("Messages.Success.Paid.title.subtitle.enable"), "subtitle disabled");
        eq(legacyCopy0.string("Messages.Prefix"), cfg.string("Messages.Prefix"), "prefix untouched");
        eq(legacyCopy0.string("Help.Main"), cfg.string("Help.Main"), "help untouched");
        eq(legacyCopy0.string("Usage.Edit.Base"), cfg.string("Usage.Edit.Base"), "usage untouched");
        for (String p : PATHS) {
            eq(MessageTree.Kind.SECTION, cfg.kind(p), p + " is a section after migration");
            // every leaf exists
            for (String leaf : MessageSchema.LEAVES)
                check(cfg.kind(p + "." + leaf) != MessageTree.Kind.ABSENT, p + "." + leaf + " exists");
        }

        System.out.println("== 3. migration is idempotent ==");
        Map<String, Object> before = new LinkedHashMap<>(cfg.flat());
        eq(false, MessageMigration.needsMigration(cfg, PATHS), "migrated file needs no migration");
        MessageMigration.Result again = MessageMigration.migrate(cfg, defaults, PATHS);
        eq(false, again.changed(), "second run changes nothing");
        eq(before, cfg.flat(), "tree identical after second run");

        System.out.println("== 4. partially migrated / mixed file ==");
        MapTree mixed = loadYaml(legacyFile);
        // Paid: already new format but only chat, with a custom text and chat disabled on purpose
        mixed.remove("Messages.Success.Paid");
        mixed.set("Messages.Success.Paid.chat.enable", false);
        mixed.set("Messages.Success.Paid.chat.text", "&bcustom paid");
        // Bypass: shorthand nodes
        mixed.remove("Messages.Success.Bypass");
        mixed.set("Messages.Success.Bypass.chat", "&ashort chat");
        mixed.set("Messages.Success.Bypass.title", "&aShort title");
        // Reload: removed (new key introduced later)
        mixed.remove("Messages.Reload");
        // Delay: invalid type must not be overwritten
        mixed.remove("Messages.Delay");
        mixed.set("Messages.Delay.chat.enable", "yes");
        mixed.set("Messages.Delay.chat.text", "&7delay %time%");
        eq(true, MessageMigration.needsMigration(mixed, PATHS), "mixed needs migration");
        MessageMigration.Result mr = MessageMigration.migrate(mixed, defaults, PATHS);
        eq(PATHS.size() - 4, mr.migratedLegacy, "remaining legacy strings migrated");
        eq(false, mixed.bool("Messages.Success.Paid.chat.enable"), "administrator's disabled chat kept");
        eq("&bcustom paid", mixed.string("Messages.Success.Paid.chat.text"), "custom paid text kept");
        eq(false, mixed.bool("Messages.Success.Paid.actionbar.enable"), "missing actionbar added (disabled)");
        eq(true, mixed.bool("Messages.Success.Bypass.chat.enable"), "chat shorthand expanded (enabled)");
        eq("&ashort chat", mixed.string("Messages.Success.Bypass.chat.text"), "chat shorthand text kept");
        eq(true, mixed.bool("Messages.Success.Bypass.title.enable"), "title shorthand expanded (enabled)");
        eq("&aShort title", mixed.string("Messages.Success.Bypass.title.text"), "title shorthand text kept");
        eq(MessageTree.Kind.SECTION, mixed.kind("Messages.Reload"), "missing key added from defaults");
        eq(defaults.string("Messages.Reload.chat.text"), mixed.string("Messages.Reload.chat.text"), "added key text from defaults");
        eq("yes", mixed.string("Messages.Delay.chat.enable"), "invalid value not overwritten");
        eq(false, MessageMigration.migrate(mixed, defaults, PATHS).changed(), "mixed: second run changes nothing");

        System.out.println("== 5. fully migrated file untouched ==");
        MapTree full = loadYaml(newFile);
        full.set("Messages.Success.Paid.chat.text", "&dedited");
        Map<String, Object> fullBefore = new LinkedHashMap<>(full.flat());
        eq(false, MessageMigration.migrate(full, defaults, PATHS).changed(), "fully migrated: nothing changed");
        eq(fullBefore, full.flat(), "fully migrated tree identical");

        System.out.println("== 6. migration without bundled defaults (unknown language) ==");
        MapTree nodef = loadYaml(legacyFile);
        MessageMigration.migrate(nodef, null, PATHS);
        eq(legacyCopy0.string("Messages.Reload"), nodef.string("Messages.Reload.chat.text"), "text kept w/o defaults");
        eq(false, nodef.bool("Messages.Reload.title.enable"), "title off w/o defaults");

        System.out.println("== 7. entry parsing ==");
        MessageEntry e = MessageEntry.read(defaults, "Messages.Success.Paid");
        check(e.chat.active(), "chat active");
        check(!e.actionbar.active(), "actionbar inactive");
        check(!e.titleActive(), "title inactive");
        MapTree t = new MapTree();
        t.set("M.chat.enable", true); t.set("M.chat.text", "c");
        t.set("M.actionbar.enable", true); t.set("M.actionbar.text", "a");
        t.set("M.title.enable", true); t.set("M.title.text", "T");
        t.set("M.title.subtitle.enable", true); t.set("M.title.subtitle.text", "S");
        t.set("M.title.fade-in", 5); t.set("M.title.stay", 40); t.set("M.title.fade-out", -3);
        MessageEntry multi = MessageEntry.read(t, "M");
        check(multi.chat.active() && multi.actionbar.active() && multi.title.active() && multi.subtitle.active(), "multiple types active at once");
        eq(Integer.valueOf(5), multi.fadeIn, "fade-in"); eq(Integer.valueOf(40), multi.stay, "stay"); eq(null, multi.fadeOut, "negative fade-out ignored");
        t.set("M.title.subtitle.enable", false);
        check(!MessageEntry.read(t, "M").subtitle.active(), "subtitle independently disabled");
        check(MessageEntry.read(t, "M").title.active(), "title still active when subtitle disabled");
        t.set("M.mystery.enable", true); // unknown delivery type
        check(MessageEntry.read(t, "M").chat.active(), "unknown type does not break parsing");
        eq("legacy %x%", MessageEntry.read(legacyTree("legacy %x%"), "L").chat.text, "unmigrated legacy string still sends as chat");
        check(MessageEntry.read(new MapTree(), "Nope").chat.text.contains("Nope"), "missing message does not throw");
        MapTree bad = new MapTree(); bad.set("B", 5);
        check(MessageEntry.read(bad, "B").chat.active(), "invalid node type does not throw");
        MapTree off = new MapTree();
        off.set("O.chat.enable", false); off.set("O.chat.text", "x");
        check(!MessageEntry.read(off, "O").anyActive(), "all types disabled sends nothing");

        System.out.println("== 8. validator ==");
        MapTree v = loadYaml(newFile);
        eq(0, MessageValidator.validate(v, PATHS).size(), "bundled defaults have no warnings");
        v.set("Messages.Success.Paid.sms.enable", true);
        v.set("Messages.Reload.chat.enable", "maybe");
        v.set("Messages.Moved", 7);
        List<String> warnings = MessageValidator.validate(v, PATHS);
        eq(3, warnings.size(), "three problems reported: " + warnings);

        System.out.println();
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }

    static MapTree legacyTree(String text) { MapTree t = new MapTree(); t.set("L", text); return t; }
}
