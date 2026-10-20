package me.SuperRonanCraft.BetterRTP.references.messages.delivery;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Names of the nodes that make up one message in the language files.
 *
 * <pre>
 * Messages:
 *   Success:
 *     Paid:
 *       chat:      { enable: true,  text: '...' }
 *       actionbar: { enable: false, text: '...' }
 *       title:
 *         enable: false
 *         text: '...'
 *         subtitle: { enable: false, text: '...' }
 *         # optional: fade-in / stay / fade-out (ticks)
 * </pre>
 */
public final class MessageSchema {

    private MessageSchema() {
    }

    public static final String ENABLE = "enable";
    public static final String TEXT = "text";
    public static final String SUBTITLE = "subtitle";
    public static final String FADE_IN = "fade-in";
    public static final String STAY = "stay";
    public static final String FADE_OUT = "fade-out";

    /** Every leaf a fully migrated message contains, in the order they are written to the file. */
    public static final List<String> LEAVES = Collections.unmodifiableList(Arrays.asList(
            "chat." + ENABLE,
            "chat." + TEXT,
            "actionbar." + ENABLE,
            "actionbar." + TEXT,
            "title." + ENABLE,
            "title." + TEXT,
            "title." + SUBTITLE + "." + ENABLE,
            "title." + SUBTITLE + "." + TEXT
    ));

    /** Optional numeric settings of the title type. */
    public static final List<String> TITLE_TIMINGS = Collections.unmodifiableList(Arrays.asList(
            "title." + FADE_IN, "title." + STAY, "title." + FADE_OUT));

    public static boolean isEnableLeaf(String leaf) {
        return leaf.endsWith("." + ENABLE);
    }

    /** Default for a missing {@code enable} node: only chat is on unless the file says otherwise. */
    public static boolean enableDefault(String leaf) {
        return leaf.startsWith("chat.");
    }
}
