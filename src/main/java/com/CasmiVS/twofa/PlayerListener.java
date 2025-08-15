package com.casmivs.twofa;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public class PlayerListener implements Listener {

    private final TwoFA plugin;

    public PlayerListener(TwoFA plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (plugin.getDataManager().isLinked(playerUUID)) {
            long telegramId = plugin.getDataManager().getTelegramId(playerUUID);
            String ipAddress = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "N/A";

            if (plugin.getDataManager().is2faEnabled(playerUUID)) {
                plugin.unverifiedPlayers.add(playerUUID);

                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 255, false, false));

                String title = plugin.getLocaleManager().getString("game.login_title");
                String subtitle = plugin.getLocaleManager().getString("game.login_subtitle");
                player.sendTitle(title, subtitle, 10, 72000, 20);

                plugin.getBotManager().requestLoginConfirmation(telegramId, ipAddress);
            } else {
                plugin.getBotManager().sendSuccessfulLoginNotification(telegramId, player.getName(), ipAddress);
            }
        }
    }

    private void cancelEvent(Player player) {
        player.sendMessage(plugin.getLocaleManager().getPrefixedString("game.login_reminder"));
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (plugin.unverifiedPlayers.contains(event.getPlayer().getUniqueId())) {
            if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ()) {
                event.getPlayer().teleport(event.getFrom());
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (plugin.unverifiedPlayers.contains(event.getPlayer().getUniqueId())) {
            cancelEvent(event.getPlayer());
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (plugin.unverifiedPlayers.contains(event.getPlayer().getUniqueId()) && !event.getMessage().toLowerCase().startsWith("/2fa link")) {
            cancelEvent(event.getPlayer());
            event.setCancelled(true);
        }
    }
    
    @EventHandler public void onBlockBreak(BlockBreakEvent event) { if (plugin.unverifiedPlayers.contains(event.getPlayer().getUniqueId())) event.setCancelled(true); }
    @EventHandler public void onBlockPlace(BlockPlaceEvent event) { if (plugin.unverifiedPlayers.contains(event.getPlayer().getUniqueId())) event.setCancelled(true); }
    @EventHandler public void onInteract(PlayerInteractEvent event) { if (plugin.unverifiedPlayers.contains(event.getPlayer().getUniqueId())) event.setCancelled(true); }
    @EventHandler public void onInteractEntity(PlayerInteractEntityEvent event) { if (plugin.unverifiedPlayers.contains(event.getPlayer().getUniqueId())) event.setCancelled(true); }
    @EventHandler public void onDropItem(PlayerDropItemEvent event) { if (plugin.unverifiedPlayers.contains(event.getPlayer().getUniqueId())) event.setCancelled(true); }
    
    @EventHandler
    public void onPickupItem(EntityPickupItemEvent event) {
        if (event.getEntityType() == EntityType.PLAYER && plugin.unverifiedPlayers.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && plugin.unverifiedPlayers.contains(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntityType() == EntityType.PLAYER && plugin.unverifiedPlayers.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.unverifiedPlayers.remove(event.getPlayer().getUniqueId());
    }
}