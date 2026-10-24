package me.SuperRonanCraft.BetterRTP.references.preload;

import me.SuperRonanCraft.BetterRTP.references.messages.delivery.MessageTree;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reads and validates the {@code PreloadChunkPosition} section.
 * Never throws: every problem is reported through {@code warn} and the affected world is disabled (or, for optional
 * values, a safe default is used). One broken world never affects the others.
 */
public final class PreloadSettingsParser {

    private PreloadSettingsParser() {
    }

    private static final List<String> KNOWN_KEYS = Arrays.asList("enable", "positions", "chunks-gen-radius", "keep-loaded", "keep-loaded-chunk-type");

    public static Map<String, PreloadSettings> parse(MessageTree tree, String rootPath, Consumer<String> warn) {
        Map<String, PreloadSettings> result = new LinkedHashMap<>();
        MessageTree.Kind rootKind = tree.kind(rootPath);
        if (rootKind == MessageTree.Kind.ABSENT)
            return result;
        if (rootKind != MessageTree.Kind.SECTION) {
            warn.accept(rootPath + " must be a section with one entry per world. Chunk pre-generation is disabled.");
            return result;
        }
        for (String world : tree.keys(rootPath)) {
            PreloadSettings settings = parseWorld(tree, rootPath + "." + world, world, warn);
            result.put(world, settings);
        }
        return result;
    }

    private static PreloadSettings parseWorld(MessageTree tree, String path, String world, Consumer<String> warn) {
        String label = "PreloadChunkPosition for world '" + world + "': ";
        if (tree.kind(path) != MessageTree.Kind.SECTION) {
            warn.accept(label + "must be a section (enable, positions, chunks-gen-radius...). The world is skipped.");
            return PreloadSettings.disabled(world);
        }
        for (String key : tree.keys(path))
            if (!KNOWN_KEYS.contains(key))
                warn.accept(label + "unknown option '" + key + "' (valid: " + String.join(", ", KNOWN_KEYS) + "). It is ignored.");

        //enable
        MessageTree.Kind enableKind = tree.kind(path + ".enable");
        if (enableKind == MessageTree.Kind.ABSENT) {
            warn.accept(label + "'enable' is missing, pre-generation is disabled for this world.");
            return PreloadSettings.disabled(world);
        }
        Boolean enable = tree.bool(path + ".enable");
        if (enable == null) {
            warn.accept(label + "'enable' must be true or false. Pre-generation is disabled for this world.");
            return PreloadSettings.disabled(world);
        }
        if (!enable)
            return PreloadSettings.disabled(world);

        //positions
        int positions = PreloadSettings.DEFAULT_POSITIONS;
        MessageTree.Kind kind = tree.kind(path + ".positions");
        if (kind != MessageTree.Kind.ABSENT) {
            Integer value = tree.number(path + ".positions");
            if (value == null || value < 1) {
                warn.accept(label + "'positions' must be a whole number of at least 1. Pre-generation is disabled for this world.");
                return PreloadSettings.disabled(world);
            }
            positions = value;
            if (positions > PreloadSettings.MAX_POSITIONS) {
                warn.accept(label + "'positions' " + positions + " is too high, using the maximum of " + PreloadSettings.MAX_POSITIONS + ".");
                positions = PreloadSettings.MAX_POSITIONS;
            }
        }

        //chunks-gen-radius
        int radius = PreloadSettings.DEFAULT_RADIUS;
        kind = tree.kind(path + ".chunks-gen-radius");
        if (kind != MessageTree.Kind.ABSENT) {
            Integer value = tree.number(path + ".chunks-gen-radius");
            if (value == null || value < 0) {
                warn.accept(label + "'chunks-gen-radius' must be a whole number of 0 or more. Pre-generation is disabled for this world.");
                return PreloadSettings.disabled(world);
            }
            radius = value;
            if (radius > PreloadSettings.MAX_RADIUS) {
                warn.accept(label + "'chunks-gen-radius' " + radius + " is too high, using the maximum of " + PreloadSettings.MAX_RADIUS + ".");
                radius = PreloadSettings.MAX_RADIUS;
            }
        }

        //keep-loaded
        boolean keepLoaded = false;
        kind = tree.kind(path + ".keep-loaded");
        if (kind != MessageTree.Kind.ABSENT) {
            Boolean value = tree.bool(path + ".keep-loaded");
            if (value == null)
                warn.accept(label + "'keep-loaded' must be true or false. Using false.");
            else
                keepLoaded = value;
        }

        //keep-loaded-chunk-type
        KeepLoadedType type = KeepLoadedType.ACTIVE;
        kind = tree.kind(path + ".keep-loaded-chunk-type");
        if (kind != MessageTree.Kind.ABSENT) {
            KeepLoadedType parsed = KeepLoadedType.fromString(tree.string(path + ".keep-loaded-chunk-type"));
            if (parsed == null)
                warn.accept(label + "'keep-loaded-chunk-type' must be 'active' or 'lazy'. Using 'active'.");
            else
                type = parsed;
        }
        return new PreloadSettings(world, true, positions, radius, keepLoaded, type);
    }
}
