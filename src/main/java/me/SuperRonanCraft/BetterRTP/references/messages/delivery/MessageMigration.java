package me.SuperRonanCraft.BetterRTP.references.messages.delivery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Converts language files written for BetterRTP 3.6.x (one plain string per message) to the delivery-type schema
 * introduced in 3.7.1, without touching anything the administrator already customized.
 *
 * <ul>
 *   <li>legacy {@code Paid: 'text'} becomes {@code Paid: {chat: {enable: true, text: 'text'}, ...}}, the original text is
 *       kept byte for byte (placeholders and color codes included)</li>
 *   <li>shorthand nodes ({@code chat: 'text'}) are expanded to {@code {enable: true, text: 'text'}}</li>
 *   <li>missing delivery types / keys are added from the bundled defaults and never overwrite an existing node</li>
 *   <li>running the migration again on an already migrated tree changes nothing</li>
 * </ul>
 * Help, Usage, Prefix and any other path that is not in the supplied path list are never touched.
 */
public final class MessageMigration {

    private MessageMigration() {
    }

    public static final class Result {
        /** Legacy plain-string messages that were converted. */
        public int migratedLegacy;
        /** Shorthand string nodes that were expanded. */
        public int expandedShorthand;
        /** Missing keys that were filled in. */
        public int addedNodes;
        /** Paths that hold something that is neither a string nor a section and were left untouched. */
        public final List<String> skipped = new ArrayList<>();

        public boolean changed() {
            return migratedLegacy > 0 || expandedShorthand > 0 || addedNodes > 0;
        }
    }

    /** True when at least one message is still in the legacy (plain string) form or uses shorthand nodes. */
    public static boolean needsMigration(MessageTree cfg, Collection<String> messagePaths) {
        for (String path : messagePaths) {
            MessageTree.Kind kind = cfg.kind(path);
            if (kind == MessageTree.Kind.STRING)
                return true;
            if (kind == MessageTree.Kind.SECTION)
                for (String shorthand : shorthandNodes(path))
                    if (cfg.kind(shorthand) == MessageTree.Kind.STRING)
                        return true;
        }
        return false;
    }

    /**
     * @param cfg           the tree to modify (the player's file, read without defaults)
     * @param defaults      bundled default tree for the same language, may be null
     * @param messagePaths  full dot paths of every migratable message, e.g. {@code Messages.Success.Paid}
     */
    public static Result migrate(MessageTree cfg, MessageTree defaults, Collection<String> messagePaths) {
        Result result = new Result();
        for (String path : messagePaths) {
            MessageTree.Kind kind = cfg.kind(path);
            String legacyText = null;
            switch (kind) {
                case STRING:
                    legacyText = cfg.string(path);
                    cfg.remove(path);
                    cfg.set(path + ".chat." + MessageSchema.ENABLE, true);
                    cfg.set(path + ".chat." + MessageSchema.TEXT, legacyText);
                    result.migratedLegacy++;
                    break;
                case SECTION:
                    for (String node : shorthandNodes(path))
                        if (cfg.kind(node) == MessageTree.Kind.STRING) {
                            String text = cfg.string(node);
                            cfg.remove(node);
                            cfg.set(node + "." + MessageSchema.ENABLE, true);
                            cfg.set(node + "." + MessageSchema.TEXT, text);
                            result.expandedShorthand++;
                        }
                    break;
                case ABSENT:
                    break;
                default:
                    result.skipped.add(path);
                    continue;
            }
            result.addedNodes += fillMissing(cfg, defaults, path, legacyText);
        }
        return result;
    }

    /** The nodes of a message that are allowed to be written as a plain string. */
    private static List<String> shorthandNodes(String path) {
        List<String> nodes = new ArrayList<>();
        nodes.add(path + ".chat");
        nodes.add(path + ".actionbar");
        nodes.add(path + ".title");
        nodes.add(path + ".title." + MessageSchema.SUBTITLE);
        return nodes;
    }

    /** Adds every leaf that is absent. Existing leaves, valid or not, are never replaced. */
    private static int fillMissing(MessageTree cfg, MessageTree defaults, String path, String legacyText) {
        int added = 0;
        for (String leaf : MessageSchema.LEAVES) {
            String full = path + "." + leaf;
            if (cfg.kind(full) != MessageTree.Kind.ABSENT)
                continue;
            Object value;
            if (MessageSchema.isEnableLeaf(leaf)) {
                Boolean def = defaults == null ? null : defaults.bool(full);
                value = def != null ? def : MessageSchema.enableDefault(leaf);
            } else {
                String def = defaults == null ? null : defaults.string(full);
                if (def != null)
                    value = def;
                else if (leaf.startsWith("title." + MessageSchema.SUBTITLE))
                    value = "";
                else
                    value = legacyText != null ? legacyText : "";
            }
            cfg.set(full, value);
            added++;
        }
        return added;
    }
}
