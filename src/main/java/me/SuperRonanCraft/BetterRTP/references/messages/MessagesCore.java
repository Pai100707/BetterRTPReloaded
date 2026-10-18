package me.SuperRonanCraft.BetterRTP.references.messages;

import me.SuperRonanCraft.BetterRTP.references.messages.delivery.BukkitMessageTree;
import me.SuperRonanCraft.BetterRTP.references.messages.delivery.MessageEntry;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public enum MessagesCore {

    SUCCESS_PAID("Success.Paid"),
    SUCCESS_BYPASS("Success.Bypass"),
    SUCCESS_LOADING("Success.Loading"),
    SUCCESS_TELEPORT("Success.Teleport"),
    FAILED_NOTSAFE("Failed.NotSafe"),
    FAILED_PRICE("Failed.Price"),
    FAILED_HUNGER("Failed.Hunger"),
    OTHER_NOTSAFE("Other.NotSafe"),
    OTHER_SUCCESS("Other.Success"),
    OTHER_BIOME("Other.Biome"),
    NOTEXIST("NotExist"),
    RELOAD("Reload"),
    UPDATE("Update"),
    NOPERMISSION("NoPermission.Basic"),
    NOPERMISSION_WORLD("NoPermission.World"),
    DISABLED_WORLD("DisabledWorld"),
    COOLDOWN("Cooldown"),
    LOCKED("Locked"), //Not sent by the plugin itself, kept so the message is migrated and stays configurable
    INVALID("Invalid"),
    NOTONLINE("NotOnline"),
    DELAY("Delay"),
    SIGN("Sign"),
    MOVED("Moved"),
    ALREADY("Already"),
    //EDIT
    EDIT_ERROR("Edit.Error"),
    EDIT_SET("Edit.Set"),
    EDIT_REMOVE("Edit.Remove"),
    ;

    final String section;

    MessagesCore(String section) {
        this.section = section;
    }

    private static final String pre = "Messages.";

    /** Every message path (e.g. {@code Messages.Success.Paid}) that uses the delivery-type schema. */
    public static List<String> paths() {
        List<String> paths = new ArrayList<>();
        for (MessagesCore message : values())
            paths.add(message.path());
        return paths;
    }

    public String path() {
        return pre + section;
    }

    public void send(CommandSender sendi) {
        Message.deliver(Message_RTP.msg, sendi, path(), null);
    }

    public void send(CommandSender sendi, Object placeholderInfo) {
        Message.deliver(Message_RTP.msg, sendi, path(), placeholderInfo);
    }

    public void send(CommandSender sendi, List<Object> placeholderInfo) {
        Message.deliver(Message_RTP.msg, sendi, path(), placeholderInfo);
    }

    /** Sends the message after replacing every key of the map with its value (e.g. {@code %type%} to a setting name). */
    public void send(CommandSender sendi, Map<String, String> replacements) {
        MessageEntry entry = entry();
        Message.deliver(Message_RTP.msg, sendi, replace(entry, replacements), null);
    }

    /** The configured message, read with the bundled defaults as fallback. */
    public MessageEntry entry() {
        return MessageEntry.read(new BukkitMessageTree(Message_RTP.getLang().getConfig(), true), path());
    }

    /**
     * The chat text of this message with placeholders applied, for callers that build their own output.
     * Returns the first enabled text when chat is disabled.
     */
    public String get(CommandSender p, Object placeholderInfo) {
        MessageEntry entry = entry();
        String text = entry.chat.text;
        if (text.isEmpty())
            text = !entry.actionbar.text.isEmpty() ? entry.actionbar.text : entry.title.text;
        return Message.applyInfo(p, text, placeholderInfo);
    }

    private static MessageEntry replace(MessageEntry entry, Map<String, String> replacements) {
        return entry.withText(text -> {
            for (Map.Entry<String, String> r : replacements.entrySet())
                text = text.replace(r.getKey(), r.getValue());
            return text;
        });
    }
}
