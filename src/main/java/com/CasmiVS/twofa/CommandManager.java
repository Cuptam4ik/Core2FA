package com.casmivs.twofa;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final TwoFA plugin;
    private final ScheduledExecutorService scheduler; 
    public static final Map<String, Long> pendingTokens = new ConcurrentHashMap<>();

    public CommandManager(TwoFA plugin) {
        this.plugin = plugin;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(); 
    }

    public void scheduleTokenRemoval(String token, long delay, TimeUnit unit) {
        
        if (!scheduler.isShutdown()) {
            scheduler.schedule(() -> pendingTokens.remove(token), delay, unit);
        }
        
    }

  
    public void shutdown() {
       
        scheduler.shutdownNow();
    }
    
    public void clearTokens() {
   
        pendingTokens.clear();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
     
        if (args.length == 0) {
            if (sender instanceof Player) {
                showInstructions((Player) sender);
            } else {
                sender.sendMessage(plugin.getLocaleManager().getString("player_only_command"));
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "reload":
                handleReload(sender);
                break;
            case "link":
                handleLink(sender, args);
                break;
            default:
                if (sender instanceof Player) {
                    showInstructions((Player) sender);
                }
                break;
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        
        if (!sender.hasPermission("twofa.reload")) {
            sender.sendMessage(plugin.getLocaleManager().getPrefixedString("no_permission"));
            return;
        }
        plugin.reloadPluginLogic();
        sender.sendMessage(plugin.getLocaleManager().getPrefixedString("reload_success"));
    }

    private void handleLink(CommandSender sender, String[] args) {
      
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLocaleManager().getString("player_only_command"));
            return;
        }
        if (plugin.getDataManager().isLinked(player.getUniqueId())) {
            player.sendMessage(plugin.getLocaleManager().getPrefixedString("command.already_linked"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.getLocaleManager().getPrefixedString("command.link_usage"));
            return;
        }
        String token = args[1];
        if (pendingTokens.containsKey(token)) {
            long telegramId = pendingTokens.remove(token);
            plugin.getDataManager().linkAccount(player.getUniqueId(), telegramId);
            player.sendMessage(plugin.getLocaleManager().getPrefixedString("command.link_success"));
            plugin.getBotManager().sendMenuMessage(telegramId);
        } else {
            player.sendMessage(plugin.getLocaleManager().getPrefixedString("command.invalid_token"));
        }
    }
    
    private void showInstructions(Player player) {
      
        String botUsername = plugin.getConfig().getString("telegram.bot-username", "YourBotName");
        String serverName = plugin.getConfig().getString("server-name", "YourServer");
        
        String statusText = plugin.getDataManager().isLinked(player.getUniqueId())
                ? plugin.getLocaleManager().getString("command.status_linked")
                : plugin.getLocaleManager().getString("command.status_not_linked");
        
        plugin.getLocaleManager().getStringList("command.instruction",
                "%bot_username%", botUsername,
                "%server_name%", serverName,
                "%status%", statusText
        ).forEach(player::sendMessage);
    }
    
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
  
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(List.of("link"));
            if (sender.hasPermission("twofa.reload")) {
                subCommands.add("reload");
            }
            return StringUtil.copyPartialMatches(args[0], subCommands, new ArrayList<>());
        }
        return Collections.emptyList();
    }
}