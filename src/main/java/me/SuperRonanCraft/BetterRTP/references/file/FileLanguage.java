package me.SuperRonanCraft.BetterRTP.references.file;

import me.SuperRonanCraft.BetterRTP.BetterRTP;
import me.SuperRonanCraft.BetterRTP.references.messages.MessagesCore;
import me.SuperRonanCraft.BetterRTP.references.messages.delivery.BukkitMessageTree;
import me.SuperRonanCraft.BetterRTP.references.messages.delivery.MessageValidator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class FileLanguage implements FileData {
    private final YamlConfiguration config = new YamlConfiguration();

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }

    @Override
    public File getFile() {
        return null;
    }

    @Override
    public String fileName() {
        return null;
    }

    @Override
    public Plugin plugin() {
        return BetterRTP.getInstance();
    }

    @Override
    public void load() {
        generateDefaults();
        String fileName = "lang" + File.separator + FileOther.FILETYPE.CONFIG.getString("Language-File");
        File file = new File(plugin().getDataFolder(), fileName);
        if (!file.exists()) {
            fileName = "lang" + File.separator + defaultLangs[0]; //Default to english
            file = new File(plugin().getDataFolder(), fileName);
        }
        try {
            config.load(file);
            InputStream in = plugin().getResource(fileName);
            if (in == null)
                in = plugin().getResource(fileName.replace(File.separator, "/"));
            YamlConfiguration bundled = null;
            if (in != null) {
                bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                in.close();
            }
            //Custom language files have no bundled twin, the english one provides the texts for newly added delivery types
            LangFileMigration.migrate(file, config, bundled != null ? bundled : bundledDefaults(defaultLangs[0]), plugin().getLogger());
            if (bundled != null) {
                config.setDefaults(bundled);
                config.options().copyDefaults(true);
            }
            config.save(file);
            for (String warning : MessageValidator.validate(new BukkitMessageTree(config, true), MessagesCore.paths()))
                plugin().getLogger().warning("[" + file.getName() + "] " + warning);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Loads a bundled language file, null if it is not part of the jar. */
    private YamlConfiguration bundledDefaults(String fName) {
        String resource = "lang/" + fName;
        try (InputStream in = plugin().getResource(resource)) {
            if (in == null)
                return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private final String[] defaultLangs = {
            "en.yml", // English - KEEP AS FIRST IN THE LIST
            "br.yml", //Portuguese
            "chs.yml", //Chinese Simplified (OasisAkari)
            "cht.yml", //Chinese (OasisAkari & kamiya10)
            "cs.yml", //Czech (Lewisparkle)
            "da.yml", //Danish (Janbchr)
            "de.yml", //German (IBimsDaNico#8690)
            "es.yml", //Spanish (emgv)
            "fr.yml", //French (At0micA55 & Mrflo67)
            "he.yml", //Hebrew (thefourcraft)
            "hu.yml", //Hungarian (Has-X)
            "it.yml", //Italian (iVillager)
            "ja.yml", //Japanese (ViaSnake)
            "nl.yml", //Dutch (QuestalNetwork) (GeleVla)
            "no.yml", //Norwegian (Fraithor & Janbchr)
            "pl.yml", //Polish (Farum & TeksuSiK)
            "ro.yml", //Romanian (GamingXBlood)
            "ru.yml", //Russian (Logan)
            "th.yml", //Thai
            "tr.yml", //Turkish (Erissos)
            "vi.yml", //Vietnamese (VoChiDanh#0862)
    };

    private void generateDefaults() {
        // Generate all language files
        for (String yaml : defaultLangs) {
            generateDefaultConfig(yaml, yaml); // Generate defaults of this language

            // Not english, make sure all options are present
            if (!yaml.equals(defaultLangs[0]))
                // Generate the english defaults (in case some options are missing)
                generateDefaultConfig(yaml, defaultLangs[0]);
        }
    }

    private void generateDefaultConfig(String fName, String fNameDef /*Name of file to generate defaults*/) {
        String fileName = "lang" + File.separator + fName;
        File file = new File(plugin().getDataFolder(), fileName);
        if (!file.exists())
            plugin().saveResource(fileName, false);
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.load(file);
            String fileNameDef = "lang" + File.separator + fNameDef;
            InputStream in = plugin().getResource(fileNameDef);
            if (in == null)
                in = plugin().getResource(fileNameDef.replace(File.separator, "/"));
            YamlConfiguration bundled = null;
            if (in != null) {
                bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                in.close();
            }
            //Convert 3.6.x messages BEFORE the defaults are attached, otherwise the defaults would hide the old nodes
            LangFileMigration.migrate(file, config, bundled, plugin().getLogger());
            if (bundled != null) {
                config.setDefaults(bundled);
                config.options().copyDefaults(true);
            }
            config.save(file);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
