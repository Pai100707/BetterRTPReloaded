package me.SuperRonanCraft.BetterRTP.references.file.config;

import java.util.Objects;

/**
 * Version of the <i>layout</i> of config.yml (not necessarily the plugin version: it only changes in releases that change
 * the layout).
 * <p>
 * It is stored as one number, one digit per part: {@code 3.6.1 -> 361}, {@code 3.7.2 -> 372}, {@code 9.9.9 -> 999}.
 * Parts are therefore limited to 0-9 (after {@code 3.7.9} comes {@code 3.8.1}). The only exception is 3.6.14, the last release
 * before this scheme existed: it has no {@code config-version} key at all, so a missing key is read as {@link #LEGACY}.
 * Comparisons use the three parts, not the stored number, so 3.6.14 correctly sorts before 3.7.2.
 */
public final class ConfigVersion implements Comparable<ConfigVersion> {

    /** A config without {@code config-version} was written by 3.6.14 (or older). */
    public static final ConfigVersion LEGACY = new ConfigVersion(3, 6, 14);

    public final int major, minor, patch;

    public ConfigVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    /** Decodes the stored number ({@code 372 -> 3.7.2}). Numbers below 100 are older than any real version. */
    public static ConfigVersion fromNumber(int number) {
        if (number < 100)
            return new ConfigVersion(0, 0, Math.max(number, 0));
        return new ConfigVersion(number / 100, (number / 10) % 10, number % 10);
    }

    /** Parses {@code "3.7.2"}. @return null when it is not three dot separated whole numbers. */
    public static ConfigVersion fromString(String text) {
        if (text == null)
            return null;
        String[] parts = text.trim().split("\\.");
        if (parts.length != 3)
            return null;
        try {
            return new ConfigVersion(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Reads the value found under {@code config-version}.
     *
     * @return null if the value is missing, otherwise the decoded version; {@link #LEGACY} is NOT returned for garbage,
     * callers use {@link #isReadable(Object)} to tell "missing" from "unreadable"
     */
    public static ConfigVersion read(Object raw) {
        if (raw instanceof Number) {
            double d = ((Number) raw).doubleValue();
            if (d != Math.rint(d) || d > Integer.MAX_VALUE)
                return null;
            return fromNumber((int) d);
        }
        if (raw instanceof String) {
            try {
                return fromNumber(Integer.parseInt(((String) raw).trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static boolean isReadable(Object raw) {
        return read(raw) != null;
    }

    /** The stored form. Only valid for versions whose parts are all 0-9. */
    public int toNumber() {
        if (major > 9 || minor > 9 || patch > 9 || major < 0 || minor < 0 || patch < 0)
            throw new IllegalStateException(this + " can not be written as a config-version (every part must be 0-9)");
        return major * 100 + minor * 10 + patch;
    }

    @Override
    public int compareTo(ConfigVersion o) {
        if (major != o.major) return Integer.compare(major, o.major);
        if (minor != o.minor) return Integer.compare(minor, o.minor);
        return Integer.compare(patch, o.patch);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ConfigVersion && compareTo((ConfigVersion) o) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
