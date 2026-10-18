package me.SuperRonanCraft.BetterRTP.references.messages.delivery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Looks for configuration mistakes in the message sections and reports them as readable warnings.
 * Nothing here throws or changes the tree; messages with problems simply fall back to safe defaults when sent.
 */
public final class MessageValidator {

    private MessageValidator() {
    }

    public static List<String> validate(MessageTree cfg, Collection<String> messagePaths) {
        List<String> warnings = new ArrayList<>();
        for (String path : messagePaths) {
            MessageTree.Kind kind = cfg.kind(path);
            if (kind == MessageTree.Kind.ABSENT || kind == MessageTree.Kind.STRING)
                continue; //absent = defaults apply, string = legacy message (still works, gets migrated)
            if (kind != MessageTree.Kind.SECTION) {
                warnings.add(path + " must be a text or a section with chat/actionbar/title, found " + kind.name().toLowerCase() + ". Using the default.");
                continue;
            }
            for (String key : cfg.keys(path))
                if (MessageType.fromKey(key) == null)
                    warnings.add("Unknown delivery type '" + key + "' in " + path + " (valid: chat, actionbar, title). It will be ignored.");
            checkPart(cfg, path + ".chat", warnings, false);
            checkPart(cfg, path + ".actionbar", warnings, false);
            checkPart(cfg, path + ".title", warnings, true);
        }
        return warnings;
    }

    private static void checkPart(MessageTree cfg, String path, List<String> warnings, boolean title) {
        MessageTree.Kind kind = cfg.kind(path);
        if (kind == MessageTree.Kind.ABSENT || kind == MessageTree.Kind.STRING)
            return;
        if (kind != MessageTree.Kind.SECTION) {
            warnings.add(path + " must be a section with 'enable' and 'text'. It will be ignored.");
            return;
        }
        checkLeaf(cfg, path + "." + MessageSchema.ENABLE, MessageTree.Kind.BOOLEAN, warnings);
        checkLeaf(cfg, path + "." + MessageSchema.TEXT, MessageTree.Kind.STRING, warnings);
        if (!title)
            return;
        for (String timing : Arrays.asList(MessageSchema.FADE_IN, MessageSchema.STAY, MessageSchema.FADE_OUT))
            checkLeaf(cfg, path + "." + timing, MessageTree.Kind.NUMBER, warnings);
        String sub = path + "." + MessageSchema.SUBTITLE;
        MessageTree.Kind subKind = cfg.kind(sub);
        if (subKind == MessageTree.Kind.SECTION) {
            checkLeaf(cfg, sub + "." + MessageSchema.ENABLE, MessageTree.Kind.BOOLEAN, warnings);
            checkLeaf(cfg, sub + "." + MessageSchema.TEXT, MessageTree.Kind.STRING, warnings);
        } else if (subKind != MessageTree.Kind.ABSENT && subKind != MessageTree.Kind.STRING)
            warnings.add(sub + " must be a section with 'enable' and 'text'. It will be ignored.");
    }

    private static void checkLeaf(MessageTree cfg, String path, MessageTree.Kind expected, List<String> warnings) {
        MessageTree.Kind kind = cfg.kind(path);
        if (kind == MessageTree.Kind.ABSENT || kind == expected)
            return;
        warnings.add(path + " should be a " + expected.name().toLowerCase() + " but is a " + kind.name().toLowerCase() + ". The default is used.");
    }
}
