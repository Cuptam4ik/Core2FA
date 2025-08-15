package com.casmivs.twofa;

import org.bukkit.entity.Player;

public interface SchedulerAdapter {
    void runTask(Runnable task);
    void kickPlayer(Player player, String reason);
    void banPlayer(String playerName, String reason, String source);
    void unbanPlayer(String playerName);
}