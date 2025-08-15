package com.casmivs.twofa;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class BukkitSchedulerAdapter implements SchedulerAdapter {

    private final TwoFA plugin;

    public BukkitSchedulerAdapter(TwoFA plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runTask(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void kickPlayer(Player player, String reason) {
        if (player != null && player.isOnline()) {
            runTask(() -> player.kickPlayer(reason));
        }
    }

    @Override
    public void banPlayer(String playerName, String reason, String source) {
        runTask(() -> Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, reason, null, source));
    }

    @Override
    public void unbanPlayer(String playerName) {
        runTask(() -> Bukkit.getBanList(BanList.Type.NAME).pardon(playerName));
    }
}