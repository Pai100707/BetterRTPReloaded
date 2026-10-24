package me.SuperRonanCraft.BetterRTP.references.preload;

import java.util.Locale;

/** Value of {@code keep-loaded-chunk-type}. */
public enum KeepLoadedType {
    ACTIVE, LAZY;

    /** @return the matching type or null for an unknown value */
    public static KeepLoadedType fromString(String value) {
        if (value == null)
            return null;
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "active": return ACTIVE;
            case "lazy": return LAZY;
            default: return null;
        }
    }
}
