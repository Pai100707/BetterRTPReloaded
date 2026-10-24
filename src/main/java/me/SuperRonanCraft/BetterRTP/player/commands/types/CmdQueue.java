package me.SuperRonanCraft.BetterRTP.player.commands.types;

import me.SuperRonanCraft.BetterRTP.BetterRTP;
import me.SuperRonanCraft.BetterRTP.player.commands.RTPCommand;
import me.SuperRonanCraft.BetterRTP.player.rtp.RTPSetupInformation;
import me.SuperRonanCraft.BetterRTP.references.PermissionNode;
import me.SuperRonanCraft.BetterRTP.references.helpers.HelperRTP;
import me.SuperRonanCraft.BetterRTP.references.messages.Message;
import me.SuperRonanCraft.BetterRTP.references.messages.Message_RTP;
import me.SuperRonanCraft.BetterRTP.references.preload.PreloadPosition;
import me.SuperRonanCraft.BetterRTP.references.preload.PreloadSettings;
import me.SuperRonanCraft.BetterRTP.references.rtpinfo.QueueData;
import me.SuperRonanCraft.BetterRTP.references.rtpinfo.QueueHandler;
import me.SuperRonanCraft.BetterRTP.references.rtpinfo.worlds.WorldPlayer;
import me.SuperRonanCraft.BetterRTP.references.web.LogUploader;
import me.SuperRonanCraft.BetterRTP.versions.AsyncHandler;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class CmdQueue implements RTPCommand {

    public String getName() {
        return "queue";
    }

    public void execute(CommandSender sendi, String label, String[] args) {
        //Works for the console too: nothing here needs a Player
        World world = args.length > 1 ? Bukkit.getWorld(args[1]) : null;
        AsyncHandler.async(() -> {
            //The prepared positions come from preload-positions.yml
            List<PreloadPosition> saved = BetterRTP.getInstance().getPreload().readSavedPositions();
            List<String> info = new ArrayList<>();
            int total = 0;
            if (world != null) {
                total += queueGetWorld(sendi, world.getName(), saved, info);
            } else {
                Set<String> names = new LinkedHashSet<>();
                for (World w : Bukkit.getWorlds())
                    names.add(w.getName());
                for (PreloadPosition p : saved) //Worlds that are not loaded right now but still have prepared positions
                    names.add(p.world);
                for (String name : names)
                    total += queueGetWorld(sendi, name, saved, info);
                info.add("&eTotal of &a%amount% &egenerated locations".replace("%amount%", String.valueOf(total)));
            }
            sendInfo(sendi, info, label, args);
        });
    }

    //World
    public static void sendInfo(CommandSender sendi, List<String> list, String label, String[] args) { //Send info
        boolean upload = Arrays.asList(args).contains("_UPLOAD_");
        List<String> lines = new ArrayList<>(list);
        lines.add(0, "&e&m-----&6 BetterRTP &8| Queue &e&m-----");
        String cmd = "/" + label + " " + String.join(" ", args);
        if (!upload) {
            List<String> colored = new ArrayList<>();
            for (String str : lines)
                colored.add(Message.color(str));
            sendi.sendMessage(colored.toArray(new String[0]));
            if (sendi instanceof Player) {
                TextComponent component = new TextComponent(Message.color("&7- &7Click to upload command log to &flogs.ronanplugins.com"));
                component.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd + " _UPLOAD_"));
                component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(Message.color("&6Suggested command&f: &7" + "/betterrtp " + String.join(" ", args) + " _UPLOAD_")).create()));
                ((Player) sendi).spigot().sendMessage(component);
            } else {
                sendi.sendMessage("Execute `" + cmd + " _UPLOAD_`" + " to upload command log to https://logs.ronanplugins.com");
            }
        } else {
            List<String> plain = new ArrayList<>();
            plain.add("Command: " + cmd);
            for (String str : lines)
                plain.add(ChatColor.stripColor(Message.color(str)));
            CompletableFuture.runAsync(() -> {
                String key = LogUploader.post(plain);
                if (key == null) {
                    Message.sms(sendi, new ArrayList<>(Collections.singletonList("&cAn error occured attempting to upload log!")), null);
                } else {
                    try {
                        JSONObject json = (JSONObject) new JSONParser().parse(key);
                        Message.sms(sendi, Arrays.asList(" ", Message.getPrefix(Message_RTP.msg) + "&aLog uploaded! &fView&7: &6https://logs.ronanplugins.com/" + json.get("key")), null);
                    } catch (ParseException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    /**
     * Adds the lines of one world to {@code info}.
     *
     * @return how many locations were listed
     */
    private static int queueGetWorld(CommandSender sendi, String worldName, List<PreloadPosition> saved, List<String> info) {
        int count = 0;
        PreloadSettings settings = BetterRTP.getInstance().getPreload().getSettings().get(worldName);
        List<String> lines = new ArrayList<>();
        for (PreloadPosition pos : saved) {
            if (!pos.world.equals(worldName))
                continue;
            lines.add("&8- &7x= &b%x&7, y= &b%y&7, z= &b%z".replace("%x", String.valueOf(pos.x)).replace("%y", String.valueOf(pos.y)).replace("%z", String.valueOf(pos.z)));
            count++;
        }
        //The older queue (Settings.Queue) is bound to a player's RTP settings, so only a player can see it
        World world = Bukkit.getWorld(worldName);
        if (sendi instanceof Player && world != null) {
            Player player = (Player) sendi;
            RTPSetupInformation setup_info = new RTPSetupInformation(HelperRTP.getActualWorld(player, world), player, player, true);
            WorldPlayer pWorld = HelperRTP.getPlayerWorld(setup_info);
            for (QueueData queue : QueueHandler.getApplicableAsync(pWorld)) {
                Location loc = queue.getLocation();
                lines.add("&8- &7x= &b%x, &7z= &b%z".replace("%x", String.valueOf(loc.getBlockX())).replace("%z", String.valueOf(loc.getBlockZ())));
                count++;
            }
        }
        String header = "&eWorld: &6" + worldName;
        if (settings != null && settings.enabled)
            header += " &7(&a" + saved.stream().filter(p -> p.world.equals(worldName)).count() + "&7/&e" + settings.positions + " &7prepared)";
        info.add(header);
        info.addAll(lines);
        return count;
    }

    public List<String> tabComplete(CommandSender sendi, String[] args) {
        List<String> info = new ArrayList<>();
        if (args.length == 2) {
            for (World world : Bukkit.getWorlds())
                if (world.getName().startsWith(args[1]))
                    info.add(world.getName());
        }
        return info;
    }

    @NotNull public PermissionNode permission() {
        return PermissionNode.ADMIN;
    }
}
