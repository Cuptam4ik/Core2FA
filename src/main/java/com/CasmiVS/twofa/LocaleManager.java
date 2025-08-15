package com.casmivs.twofa;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LocaleManager {

    private final TwoFA plugin;
    private final Map<String, FileConfiguration> locales = new HashMap<>();
    private String serverLang;

    public LocaleManager(TwoFA plugin) {
        this.plugin = plugin;
        loadLocales();
    }

    public void loadLocales() {
        locales.clear();
        serverLang = plugin.getConfig().getString("language", "ru");

        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        saveDefaultLocale("en.yml");
        saveDefaultLocale("ru.yml");

        File[] langFiles = langFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (langFiles != null) {
            for (File file : langFiles) {
                locales.put(file.getName().replace(".yml", ""), YamlConfiguration.loadConfiguration(file));
            }
        }

        if (!locales.containsKey(serverLang)) {
            plugin.getLogger().severe("Language '" + serverLang + "' not found! Falling back to 'en'.");
            serverLang = "en";
        }
    }

    private void saveDefaultLocale(String name) {
        File file = new File(plugin.getDataFolder(), "lang/" + name);
        if (!file.exists()) {
            plugin.saveResource("lang/" + name, false);
        }
    }
    
    private String getMessage(String key) {
        String message = locales.get(serverLang).getString(key);
        if (message == null) {
            // Fallback to English if key is missing in the current language
            message = locales.get("en").getString(key, "&cMissing translation: " + key);
        }
        return message;
    }

    public String getString(String key, String... replacements) {
        String message = getMessage(key);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace(replacements[i], replacements[i + 1]);
            }
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    public String getPrefixedString(String key, String... replacements) {
        return getString("prefix") + getString(key, replacements);
    }

    public List<String> getStringList(String key, String... replacements) {
        List<String> messages = locales.get(serverLang).getStringList(key);
        if (messages.isEmpty()) {
            messages = locales.get("en").getStringList(key);
        }
        
        return messages.stream().map(line -> {
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 < replacements.length) {
                    line = line.replace(replacements[i], replacements[i + 1]);
                }
            }
            return ChatColor.translateAlternateColorCodes('&', line);
        }).collect(Collectors.toList());
    }

    public String getTelegramString(String key, String... replacements) {
        Object value = locales.get(serverLang).get(key);
        if (value == null) value = locales.get("en").get(key);
        
        String message = (value instanceof List) ? String.join("\n", (List<String>) value) : (value != null ? value.toString() : "Missing translation: " + key);
        
        for (int i = 0; i < replacements.length; i += 2) {
             if (i + 1 < replacements.length) {
                message = message.replace(replacements[i], replacements[i + 1]);
            }
        }
        return message;
    }
}