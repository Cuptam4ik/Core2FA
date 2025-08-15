package com.casmivs.twofa;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class DataManager {

    private final TwoFA plugin;
    private FileConfiguration dataConfig = null;
    private File dataFile = null;

    public DataManager(TwoFA plugin) {
        this.plugin = plugin;
        saveDefaultConfig();
        reloadConfig();
    }

    public void reloadConfig() {
        if (dataFile == null) dataFile = new File(plugin.getDataFolder(), "data.yml");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public FileConfiguration getConfig() {
        if (dataConfig == null) reloadConfig();
        return dataConfig;
    }

    public void saveConfig() {
        if (dataConfig == null || dataFile == null) return;
        try {
            getConfig().save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data to " + dataFile);
        }
    }

    public void saveDefaultConfig() {
        if (dataFile == null) dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) plugin.saveResource("data.yml", false);
    }

    public void linkAccount(UUID playerUUID, long telegramId) {
        getConfig().set("users." + playerUUID.toString() + ".telegramId", telegramId);
        getConfig().set("users." + playerUUID.toString() + ".enabled", true);
        saveConfig();
    }

    public void unlinkAccount(UUID playerUUID) {
        getConfig().set("users." + playerUUID.toString(), null);
        saveConfig();
    }

    public boolean isLinked(UUID playerUUID) {
        return getConfig().isSet("users." + playerUUID.toString());
    }

    public long getTelegramId(UUID playerUUID) {
        return getConfig().getLong("users." + playerUUID.toString() + ".telegramId", -1);
    }

    public boolean is2faEnabled(UUID playerUUID) {
        return getConfig().getBoolean("users." + playerUUID.toString() + ".enabled", false);
    }

    public void set2faEnabled(UUID playerUUID, boolean enabled) {
        getConfig().set("users." + playerUUID.toString() + ".enabled", enabled);
        saveConfig();
    }

    public UUID findUUIDbyTelegramId(long telegramId) {
        if (getConfig().getConfigurationSection("users") == null) return null;
        for (String key : getConfig().getConfigurationSection("users").getKeys(false)) {
            if (getConfig().getLong("users." + key + ".telegramId") == telegramId) {
                try {
                    return UUID.fromString(key);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }
}