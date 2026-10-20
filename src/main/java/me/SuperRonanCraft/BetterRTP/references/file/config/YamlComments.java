package me.SuperRonanCraft.BetterRTP.references.file.config;

import org.bukkit.configuration.ConfigurationSection;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Copies the YAML comments of a path from one configuration to another.
 * <p>
 * Comment support was added to the Bukkit API in 1.18.1 and this plugin is compiled against a much older API, so the
 * methods are called through reflection. On servers without them nothing happens (the values are still correct, only
 * the explanatory comments of rewritten options are not refreshed).
 */
final class YamlComments {

    private YamlComments() {
    }

    static void copy(ConfigurationSection from, ConfigurationSection to, String path) {
        try {
            Method getComments = ConfigurationSection.class.getMethod("getComments", String.class);
            Method setComments = ConfigurationSection.class.getMethod("setComments", String.class, List.class);
            Object comments = getComments.invoke(from, path);
            if (comments instanceof List<?> && !((List<?>) comments).isEmpty())
                setComments.invoke(to, path, comments);
            Method getInline = ConfigurationSection.class.getMethod("getInlineComments", String.class);
            Method setInline = ConfigurationSection.class.getMethod("setInlineComments", String.class, List.class);
            Object inline = getInline.invoke(from, path);
            if (inline instanceof List<?> && !((List<?>) inline).isEmpty())
                setInline.invoke(to, path, inline);
        } catch (Throwable ignored) {
            //No comment support on this server version
        }
    }
}
