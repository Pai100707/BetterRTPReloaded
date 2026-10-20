package me.SuperRonanCraft.BetterRTP.references.messages.delivery;

import java.util.Locale;

/**
 * The delivery types a single message can be sent with.
 * <p>
 * The {@link #key()} is the YAML node name used inside every message section of the language files.
 */
public enum MessageType {

    CHAT("chat"),
    ACTIONBAR("actionbar"),
    TITLE("title");

    private final String key;

    MessageType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** @return the matching type, or null when the key is not a known delivery type. */
    public static MessageType fromKey(String key) {
        if (key == null)
            return null;
        String lower = key.toLowerCase(Locale.ROOT);
        for (MessageType type : values())
            if (type.key.equals(lower))
                return type;
        return null;
    }
}
