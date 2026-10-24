package me.SuperRonanCraft.BetterRTP.references.messages.delivery;

import me.SuperRonanCraft.BetterRTP.BetterRTP;
import me.SuperRonanCraft.BetterRTP.references.messages.Message;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * Sends a {@link MessageEntry} to a receiver using every delivery type that is enabled.
 * <p>
 * Chat works for every {@link CommandSender}. Actionbar and title need a {@link Player}; for any other receiver
 * (console, command blocks...) the message falls back to chat so it is never silently lost.
 * <p>
 * The actionbar and the title-with-timings calls are not part of the oldest Spigot API this plugin still compiles against,
 * so they are looked up reflectively once and fall back gracefully (title without timings, actionbar as chat).
 * Must be called on the thread that is allowed to touch the receiver; {@link Message#deliver} takes care of that.
 */
public final class MessageDelivery {

    private MessageDelivery() {
    }

    /**
     * @param prefix      put in front of the chat text only
     * @param placeholder applies placeholders and colors to a raw configured text (may return null)
     */
    public static void send(CommandSender sendi, MessageEntry entry, String prefix, Function<String, String> placeholder) {
        if (sendi == null || entry == null)
            return;
        boolean player = sendi instanceof Player;
        if (!player) {
            //Console & co can only read chat: use the chat text, or else the first enabled text
            String text = entry.chat.active() ? entry.chat.text
                    : entry.actionbar.active() ? entry.actionbar.text
                    : entry.title.active() ? entry.title.text
                    : entry.subtitle.active() ? entry.subtitle.text : null;
            if (text != null)
                chat(sendi, prefix, text, placeholder);
            return;
        }
        Player p = (Player) sendi;
        if (entry.chat.active())
            chat(p, prefix, entry.chat.text, placeholder);
        if (entry.actionbar.active()) {
            String text = placeholder.apply(entry.actionbar.text);
            if (text != null && !text.isEmpty() && !ActionBar.send(p, text))
                p.sendMessage(text); //No actionbar support on this server, do not lose the message
        }
        if (entry.titleActive()) {
            String title = entry.title.active() ? placeholder.apply(entry.title.text) : "";
            String sub = entry.subtitle.active() ? placeholder.apply(entry.subtitle.text) : "";
            Title.send(p, title == null ? "" : title, sub == null ? "" : sub, entry.fadeIn, entry.stay, entry.fadeOut);
        }
    }

    private static void chat(CommandSender sendi, String prefix, String text, Function<String, String> placeholder) {
        String out = placeholder.apply((prefix == null ? "" : prefix) + text);
        if (out != null && !out.isEmpty())
            sendi.sendMessage(out);
    }

    //---------------------------------------------------------------------------------------------------- title
    private static final class Title {
        private static Method timed; //Player#sendTitle(String, String, int, int, int) (1.11+)
        private static boolean looked;

        static void send(Player p, String title, String subtitle, Integer fadeIn, Integer stay, Integer fadeOut) {
            if (fadeIn != null || stay != null || fadeOut != null) {
                if (!looked) {
                    looked = true;
                    try {
                        timed = Player.class.getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class);
                    } catch (Throwable e) {
                        timed = null;
                    }
                }
                if (timed != null) {
                    try { //Unset values use the vanilla defaults (10, 70, 20 ticks)
                        timed.invoke(p, title, subtitle, fadeIn != null ? fadeIn : 10, stay != null ? stay : 70, fadeOut != null ? fadeOut : 20);
                        return;
                    } catch (Throwable e) {
                        timed = null;
                    }
                }
            }
            p.sendTitle(title, subtitle); //Server default timings (same call the Titles effects already use)
        }
    }

    //------------------------------------------------------------------------------------------------- actionbar
    private static final class ActionBar {
        private static boolean looked, spigotRoute, paperRoute;
        private static Method spigotGetter, spigotSend, fromLegacy, paperSend;
        private static Object actionBarType;
        private static boolean warned;

        private static void init() {
            looked = true;
            try { //Spigot/Paper: player.spigot().sendMessage(ChatMessageType.ACTION_BAR, BaseComponent[])
                Class<?> type = Class.forName("net.md_5.bungee.api.chat.ChatMessageType");
                Class<?> text = Class.forName("net.md_5.bungee.api.chat.TextComponent");
                Class<?> base = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
                Class<?> spigot = Class.forName("org.bukkit.entity.Player$Spigot");
                actionBarType = type.getField("ACTION_BAR").get(null);
                fromLegacy = text.getMethod("fromLegacyText", String.class);
                spigotSend = spigot.getMethod("sendMessage", type, java.lang.reflect.Array.newInstance(base, 0).getClass());
                spigotGetter = Player.class.getMethod("spigot");
                spigotRoute = true;
            } catch (Throwable ignored) {
                spigotRoute = false;
            }
            try { //Paper: Player#sendActionBar(String)
                paperSend = Player.class.getMethod("sendActionBar", String.class);
                paperRoute = true;
            } catch (Throwable ignored) {
                paperRoute = false;
            }
        }

        static boolean send(Player p, String text) {
            if (!looked)
                init();
            if (spigotRoute) {
                try {
                    Object spigot = spigotGetter.invoke(p);
                    spigotSend.invoke(spigot, actionBarType, fromLegacy.invoke(null, text));
                    return true;
                } catch (Throwable e) {
                    spigotRoute = false;
                }
            }
            if (paperRoute) {
                try {
                    paperSend.invoke(p, text);
                    return true;
                } catch (Throwable e) {
                    paperRoute = false;
                }
            }
            if (!warned) {
                warned = true;
                BetterRTP.getInstance().getLogger().warning("This server does not support action bar messages, sending them as chat instead.");
            }
            return false;
        }
    }
}
