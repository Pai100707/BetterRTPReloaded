package me.SuperRonanCraft.BetterRTP.references.messages.delivery;

/**
 * One configured message: which delivery types are enabled and what each of them says.
 * Immutable, parsed from the language file by {@link #read(MessageTree, String)}.
 */
public final class MessageEntry {

    /** One piece of text together with its enabled flag. */
    public static final class Part {
        public final boolean enabled;
        public final String text;

        Part(boolean enabled, String text) {
            this.enabled = enabled;
            this.text = text == null ? "" : text;
        }

        public boolean active() {
            return enabled && !text.isEmpty();
        }
    }

    public final Part chat, actionbar, title, subtitle;
    /** Optional title timings in ticks, null = server default. */
    public final Integer fadeIn, stay, fadeOut;

    private MessageEntry(Part chat, Part actionbar, Part title, Part subtitle, Integer fadeIn, Integer stay, Integer fadeOut) {
        this.chat = chat;
        this.actionbar = actionbar;
        this.title = title;
        this.subtitle = subtitle;
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
    }

    /** A message that is only sent to chat (used for legacy values and for messages that are not configured). */
    public static MessageEntry chatOnly(String text) {
        Part off = new Part(false, "");
        return new MessageEntry(new Part(true, text), off, off, off, null, null, null);
    }

    /** A copy of this entry with {@code transform} applied to the text of every delivery type. */
    public MessageEntry withText(java.util.function.UnaryOperator<String> transform) {
        return new MessageEntry(
                new Part(chat.enabled, transform.apply(chat.text)),
                new Part(actionbar.enabled, transform.apply(actionbar.text)),
                new Part(title.enabled, transform.apply(title.text)),
                new Part(subtitle.enabled, transform.apply(subtitle.text)),
                fadeIn, stay, fadeOut);
    }

    public boolean titleActive() {
        return title.active() || subtitle.active();
    }

    /** True when at least one delivery type would actually send something. */
    public boolean anyActive() {
        return chat.active() || actionbar.active() || titleActive();
    }

    /**
     * Parses the message stored at {@code path}. Never throws: legacy plain strings become chat-only messages,
     * invalid nodes fall back to the defaults (chat on, everything else off) and unknown nodes are ignored.
     */
    public static MessageEntry read(MessageTree tree, String path) {
        MessageTree.Kind kind = tree.kind(path);
        if (kind == MessageTree.Kind.STRING) //Legacy (not yet migrated) message
            return chatOnly(tree.string(path));
        if (kind != MessageTree.Kind.SECTION)
            return chatOnly("&cMessage not configured: &7" + path);

        Part chat = readPart(tree, path + ".chat", true);
        Part actionbar = readPart(tree, path + ".actionbar", false);

        String titlePath = path + ".title";
        Part title, subtitle = new Part(false, "");
        Integer fadeIn = null, stay = null, fadeOut = null;
        if (tree.kind(titlePath) == MessageTree.Kind.SECTION) {
            title = readPart(tree, titlePath, false);
            subtitle = readPart(tree, titlePath + "." + MessageSchema.SUBTITLE, false);
            fadeIn = positive(tree.number(titlePath + "." + MessageSchema.FADE_IN));
            stay = positive(tree.number(titlePath + "." + MessageSchema.STAY));
            fadeOut = positive(tree.number(titlePath + "." + MessageSchema.FADE_OUT));
        } else
            title = readPart(tree, titlePath, false); //Shorthand `title: 'text'` or absent
        return new MessageEntry(chat, actionbar, title, subtitle, fadeIn, stay, fadeOut);
    }

    private static Integer positive(Integer value) {
        return value != null && value >= 0 ? value : null;
    }

    /** A part is either a section {enable, text}, a shorthand string (enabled) or missing. */
    private static Part readPart(MessageTree tree, String path, boolean defaultEnabled) {
        switch (tree.kind(path)) {
            case STRING:
                return new Part(true, tree.string(path));
            case SECTION:
                boolean enabled = defaultEnabled;
                Boolean flag = tree.bool(path + "." + MessageSchema.ENABLE);
                if (flag != null)
                    enabled = flag;
                else if (tree.kind(path + "." + MessageSchema.ENABLE) == MessageTree.Kind.STRING)
                    enabled = parseLenientBoolean(tree.string(path + "." + MessageSchema.ENABLE), defaultEnabled);
                String text = tree.string(path + "." + MessageSchema.TEXT);
                return new Part(enabled, text);
            default:
                return new Part(false, "");
        }
    }

    private static boolean parseLenientBoolean(String value, boolean def) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return def;
    }
}
