package me.SuperRonanCraft.BetterRTP.references.file.config;

import java.util.Map;

/** Reads the {@code enable} switch of a {@code CustomWorlds} entry. */
public final class CustomWorldSettings {

    private CustomWorldSettings() {
    }

    /**
     * {@code true}: the custom world settings are used. {@code false}: the world uses the Default settings.
     * A missing or unreadable switch means enabled, so custom worlds written before the switch existed keep working.
     *
     * @param settings the value of the world entry (the map below the world name)
     */
    public static boolean isEnabled(Object settings) {
        if (!(settings instanceof Map<?, ?>))
            return true;
        Object enable = ((Map<?, ?>) settings).get("enable");
        if (enable instanceof Boolean)
            return (Boolean) enable;
        if (enable instanceof String)
            return !"false".equalsIgnoreCase(((String) enable).trim());
        return true;
    }
}
