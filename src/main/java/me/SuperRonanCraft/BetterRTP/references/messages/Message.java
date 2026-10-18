package me.SuperRonanCraft.BetterRTP.references.messages;

import com.google.common.collect.ImmutableCollection;
import lombok.NonNull;
import me.SuperRonanCraft.BetterRTP.references.file.FileData;
import me.SuperRonanCraft.BetterRTP.references.messages.delivery.BukkitMessageTree;
import me.SuperRonanCraft.BetterRTP.references.messages.delivery.MessageDelivery;
import me.SuperRonanCraft.BetterRTP.references.messages.delivery.MessageEntry;
import me.SuperRonanCraft.BetterRTP.references.messages.placeholder.PlaceholderAnalyzer;
import me.SuperRonanCraft.BetterRTP.versions.AsyncHandler;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface Message {

    FileData lang();

    static void sms(Message messenger, CommandSender sendi, String msg) {
        if (!msg.isEmpty())
            AsyncHandler.sync(() ->
                    sendi.sendMessage(placeholder(sendi, getPrefix(messenger) + msg)));
    }

    static void sms(Message messenger, CommandSender sendi, String msg, Object placeholderInfo) {
        if (!msg.isEmpty())
            AsyncHandler.sync(() ->
                    sendi.sendMessage(Objects.requireNonNull(placeholder(sendi, getPrefix(messenger) + msg, placeholderInfo))));
    }

    static void sms(Message messenger, CommandSender sendi, String msg, List<Object> placeholderInfo) {
        if (!msg.isEmpty())
            AsyncHandler.sync(() ->
                    sendi.sendMessage(placeholder(sendi, getPrefix(messenger) + msg, placeholderInfo)));
    }

    /**
     * Sends the message stored at {@code path} (for example {@code Messages.Success.Paid}) using every delivery type
     * (chat / actionbar / title) the administrator enabled for it.
     *
     * @param placeholderInfo same objects the other sms methods accept; may be a single object, a List or null
     */
    static void deliver(Message messenger, CommandSender sendi, String path, @Nullable Object placeholderInfo) {
        MessageEntry entry = MessageEntry.read(new BukkitMessageTree(messenger.lang().getConfig(), true), path);
        deliver(messenger, sendi, entry, placeholderInfo);
    }

    static void deliver(Message messenger, CommandSender sendi, MessageEntry entry, @Nullable Object placeholderInfo) {
        if (sendi == null || !entry.anyActive())
            return;
        String prefix = getPrefix(messenger);
        AsyncHandler.sync(() -> MessageDelivery.send(sendi, entry, prefix, text -> applyInfo(sendi, text, placeholderInfo)));
    }

    /**
     * Applies placeholders for either a single info object or a list of them. Lists have to go through the
     * List overload explicitly: the Object overload would hand them straight back to itself.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    static String applyInfo(@Nullable CommandSender p, String text, @Nullable Object info) {
        if (info instanceof List<?>)
            return placeholder(p, text, (List<Object>) info);
        return placeholder(p, text, info);
    }

    static void sms(CommandSender sendi, List<String> msg, Object placeholderInfo) {
        if (msg != null && !msg.isEmpty()) {
            AsyncHandler.sync(() -> {
                msg.forEach(str -> msg.set(msg.indexOf(str), placeholder(sendi, str, placeholderInfo)));
                sendi.sendMessage(msg.toArray(new String[0]));
            });
        }
    }

    /*static void smsActionBar(Player sendi, List<String> msg) {
        if (msg == null || msg.isEmpty()) return;
        String str = msg.get(new Random().nextInt(msg.size()));
        smsActionBar(sendi, str);
    }

    static void smsActionBar(Player sendi, String msg) {
        if (msg == null || msg.isEmpty()) return;
        Audience audience = BetterRTP.getInstance().getAdventure().player(sendi);
        audience.sendActionBar(Component.text(msg));
        //sendi.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
    }*/

    /*static void smsTitle(Player sendi, List<String> msg) {
        if (msg == null || msg.isEmpty()) return;
        Audience audience = BetterRTP.getInstance().getAdventure().player(sendi);
        if (msg.size() == 1)
            audience.showTitle(Title.title(Component.text(" "), Component.text(msg.get(0))));
        else
            audience.showTitle(Title.title(Component.text(msg.get(0)), Component.text(msg.get(1))));
    }*/

    static String getPrefix(Message messenger) {
        return messenger.lang().getString("Messages.Prefix");
    }

    /**
     * @param info: Accepts String, PersistentDataContainer
     * **/
    static List<String> placeholder(@Nullable CommandSender p, List<String> str, Object info) {
        if (str instanceof ImmutableCollection)
            return str;
        for (int i = 0; i < str.size(); i++) {
            String s = placeholder(p, str.get(i), info);
            if (s != null)
                str.set(i, s);
            else {
                str.remove(i);
                i--;
            }
        }
        //str.forEach(s -> str.set(str.indexOf(s), placeholder(p, s, info)));
        return str;
    }

    @Nullable
    static String placeholder(@Nullable CommandSender p, String str, @Nullable Object info) {
        if (info instanceof Collection<?>)
            str = placeholder(p, str, Collections.unmodifiableList((List<?>) info));
        else if (str != null)
            str = PlaceholderAnalyzer.applyPlaceholders(p, str, info);
        if (str != null)
            return color(str);
        return null;
    }

    static String placeholder(@Nullable CommandSender p, String str, @NonNull List<Object> info) {
        for (Object obj : info)
            str = placeholder(p, str, obj);
        return str;
    }

    static String placeholder(@Nullable CommandSender p, String str) {
        if (str != null)
            str = PlaceholderAnalyzer.applyPlaceholders(p, str, null);
        if (str != null)
            return color(str);
        return null;
    }

    static String color(String str) {
        return translateHexColorCodes(str);
    }

    //Thank you to zwrumpy on Spigot! (https://www.spigotmc.org/threads/hex-color-code-translate.449748/#post-4270781)
    //Supports 1.8 to 1.18
    static String translateHexColorCodes(String message) {
        Pattern pattern = Pattern.compile("#[a-fA-F0-9]{6}");
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String hexCode = message.substring(matcher.start(), matcher.end());
            String replaceSharp = hexCode.replace('#', 'x');

            char[] ch = replaceSharp.toCharArray();
            StringBuilder builder = new StringBuilder("");
            for (char c : ch) {
                builder.append("&").append(c);
            }

            message = message.replace(hexCode, builder.toString());
            matcher = pattern.matcher(message);
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
