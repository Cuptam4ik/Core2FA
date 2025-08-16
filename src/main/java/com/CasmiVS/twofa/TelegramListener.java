package com.casmivs.twofa;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class TelegramListener implements Runnable {

    private final TwoFA plugin;
    private final String botToken;
    private long offset = 0;
    private volatile boolean running = true;

    public TelegramListener(TwoFA plugin) {
        this.plugin = plugin;
        this.botToken = plugin.getConfig().getString("telegram.bot-token");
    }

    public void stop() {
        this.running = false;
    }

    @Override
    public void run() {
        if (botToken == null || botToken.equals("ВАШ_ТОКЕН_БОТА_СЮДА") || botToken.isEmpty()) {
            plugin.getLogger().severe("Telegram Bot Token is not set in config.yml! The bot will not start.");
            return;
        }
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                HttpResponse<String> response = Unirest.get("https://api.telegram.org/bot" + botToken + "/getUpdates")
                        .queryString("offset", offset)
                        .queryString("timeout", 0)
                        .asString();
                if (response.getStatus() == 200) {
                    JSONObject responseObject = new JSONObject(response.getBody());
                    if (responseObject.getBoolean("ok")) {
                        JSONArray updates = responseObject.getJSONArray("result");
                        for (int i = 0; i < updates.length(); i++) {
                            if (!running) break;
                            JSONObject update = updates.getJSONObject(i);
                            offset = update.getLong("update_id") + 1;
                            if (update.has("message") && update.getJSONObject("message").has("text")) {
                                handleMessage(update.getJSONObject("message"));
                            } else if (update.has("callback_query")) {
                                handleCallbackQuery(update.getJSONObject("callback_query"));
                            }
                        }
                    } else {
                        plugin.getLogger().severe("Telegram API returned an error: " + responseObject.optString("description"));
                        if (responseObject.optInt("error_code") == 401) {
                            plugin.getLogger().severe("This error (401 Unauthorized) usually means your Bot Token is incorrect.");
                            running = false;
                        }
                    }
                }
            } catch (Exception e) {
                if (!running) break;
                plugin.getLogger().severe("An error occurred while polling Telegram updates: " + e.getMessage());
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void handleMessage(JSONObject message) {
        String text = message.getString("text");
        long chatId = message.getJSONObject("chat").getLong("id");
        UUID playerUUID = plugin.getDataManager().findUUIDbyTelegramId(chatId);
        if (text.startsWith("/start") || text.startsWith("/menu")) {
            if (playerUUID != null) {
                plugin.getBotManager().sendMenuMessage(chatId);
            } else {
                if (text.startsWith("/start")) {
                    String token = UUID.randomUUID().toString().substring(0, 8);
                    CommandManager.pendingTokens.put(token, chatId);
                    plugin.getCommandManager().scheduleTokenRemoval(token, 5, TimeUnit.MINUTES);
                    String messageText = plugin.getLocaleManager().getTelegramString("telegram.link_code_message", "%token%", token);
                    plugin.getBotManager().sendMessage(chatId, messageText, true);
                } else {
                    plugin.getBotManager().sendMessage(chatId, plugin.getLocaleManager().getTelegramString("telegram.not_linked_yet"), false);
                }
            }
        }
    }

    private void handleCallbackQuery(JSONObject callbackQuery) {
        String data = callbackQuery.getString("data");
        long chatId = callbackQuery.getJSONObject("from").getLong("id");
        long messageId = callbackQuery.getJSONObject("message").getLong("message_id");
        String callbackQueryId = callbackQuery.getString("id");
        LocaleManager lm = plugin.getLocaleManager();

        if (data.startsWith("confirm_login") || data.startsWith("deny_login")) {
            handleLoginConfirmation(callbackQuery);
            return;
        }
        
        if (data.startsWith("notification:")) {
            UUID playerUUID = plugin.getDataManager().findUUIDbyTelegramId(chatId);
            if (playerUUID == null) return;
            Player onlinePlayer = Bukkit.getPlayer(playerUUID);
            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                String kickMsg = lm.getString("telegram.action.terminate_kick");
                plugin.getSchedulerAdapter().kickPlayer(onlinePlayer, kickMsg);
                plugin.getBotManager().editMessage(chatId, messageId, lm.getTelegramString("telegram.action.terminate_success"));
            } else {
                plugin.getBotManager().editMessage(chatId, messageId, lm.getTelegramString("telegram.action.terminate_fail"));
            }
            return;
        }

        UUID playerUUID = plugin.getDataManager().findUUIDbyTelegramId(chatId);
        if (playerUUID == null) return;

        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUUID);
        if (player.getName() == null) return;

        String[] parts = data.split(":");
        if (!parts[0].equals("menu")) return;
        String action = parts.length > 1 ? parts[1] : "";

        switch (action) {
            case "info": {
                String prefix = lm.getTelegramString("prefix");
                String infoBody = lm.getTelegramString("telegram.info_message", "%player_name%", player.getName(), "%player_uuid%", playerUUID.toString());
                String fullMessage = prefix + "\n" + infoBody;
                String processedMessage = BotManager.minecraftToTelegramHTML(fullMessage);
                plugin.getBotManager().editMessageWithKeyboard(chatId, messageId, processedMessage, buildBackButton());
                break;
            }
            case "toggle_2fa": {
                boolean currentStatus = plugin.getDataManager().is2faEnabled(playerUUID);
                plugin.getDataManager().set2faEnabled(playerUUID, !currentStatus);
                plugin.getBotManager().editToMenuMessage(chatId, messageId);
                String key = !currentStatus ? "telegram.action.toggle_2fa_on_success" : "telegram.action.toggle_2fa_off_success";
                answerCallbackQuery(callbackQueryId, lm.getTelegramString(key), false);
                break;
            }
            case "terminate": {
                String key = "telegram.action.terminate_fail";
                Player onlinePlayer = player.getPlayer();
                if (onlinePlayer != null) {
                    plugin.getSchedulerAdapter().kickPlayer(onlinePlayer, lm.getString("telegram.action.terminate_kick"));
                    key = "telegram.action.terminate_success";
                }
                plugin.getBotManager().editMessageWithKeyboard(chatId, messageId, lm.getTelegramString(key), buildBackButton());
                plugin.runAsyncTaskLater(() -> plugin.getBotManager().editToMenuMessage(chatId, messageId), 60L);
                break;
            }
            case "unlink": {
                if (!plugin.getConfig().getBoolean("telegram-menu.settings.allow-unlinking", false)) {
                     answerCallbackQuery(callbackQueryId, lm.getTelegramString("telegram.action.unlink_disabled"), true);
                     break;
                }
                String text = lm.getTelegramString("telegram.action.unlink_confirm");
                plugin.getBotManager().editMessageWithKeyboard(chatId, messageId, text, buildConfirmCancelKeyboard("unlink_confirm"));
                break;
            }
            case "unlink_confirm": {
                 if (!plugin.getConfig().getBoolean("telegram-menu.settings.allow-unlinking", false)) break;
                 plugin.getDataManager().unlinkAccount(playerUUID);
                 plugin.getBotManager().editMessage(chatId, messageId, lm.getTelegramString("telegram.action.unlink_success"));
                 break;
            }
            case "toggle_ban": {
                if (!plugin.getConfig().getBoolean("telegram-menu.settings.allow-remote-ban", false)) {
                    answerCallbackQuery(callbackQueryId, lm.getTelegramString("telegram.action.ban_disabled"), true);
                    break;
                }
                boolean isBanned = player.isBanned();
                String text = isBanned ? lm.getTelegramString("telegram.action.unban_confirm") : lm.getTelegramString("telegram.action.ban_confirm");
                String confirmAction = isBanned ? "unban_confirm" : "ban_confirm";
                plugin.getBotManager().editMessageWithKeyboard(chatId, messageId, text, buildConfirmCancelKeyboard(confirmAction));
                break;
            }
            case "ban_confirm": {
                String reason = lm.getString("telegram.action.ban_reason");
                plugin.getSchedulerAdapter().banPlayer(player.getName(), reason, "Core2FA");
                Player onlinePlayer = player.getPlayer();
                if (onlinePlayer != null) {
                    plugin.getSchedulerAdapter().kickPlayer(onlinePlayer, lm.getString("telegram.action.ban_kick_message"));
                }
                answerCallbackQuery(callbackQueryId, lm.getTelegramString("telegram.action.ban_success"), false);
                plugin.getBotManager().editMessage(chatId, messageId, lm.getTelegramString("telegram.action.ban_success"));
                plugin.runAsyncTaskLater(() -> plugin.getBotManager().editToMenuMessage(chatId, messageId), 60L);
                break;
            }
            case "unban_confirm": {
                plugin.getSchedulerAdapter().unbanPlayer(player.getName());
                answerCallbackQuery(callbackQueryId, lm.getTelegramString("telegram.action.unban_success"), false);
                plugin.getBotManager().editMessage(chatId, messageId, lm.getTelegramString("telegram.action.unban_success"));
                plugin.runAsyncTaskLater(() -> plugin.getBotManager().editToMenuMessage(chatId, messageId), 60L);
                break;
            }
            case "back_to_menu": {
                plugin.getBotManager().editToMenuMessage(chatId, messageId);
                break;
            }
        }
    }

    private void handleLoginConfirmation(JSONObject callbackQuery) {
        String data = callbackQuery.getString("data");
        long chatId = callbackQuery.getJSONObject("from").getLong("id");
        long messageId = callbackQuery.getJSONObject("message").getLong("message_id");
        String callbackQueryId = callbackQuery.getString("id");
        LocaleManager lm = plugin.getLocaleManager();

        UUID playerUUID = plugin.getDataManager().findUUIDbyTelegramId(chatId);
        String originalText = callbackQuery.getJSONObject("message").getString("text");
        
        if (data.startsWith("confirm_login")) {
            if (playerUUID != null && plugin.unverifiedPlayers.contains(playerUUID)) {
                plugin.unverifiedPlayers.remove(playerUUID);
                
                plugin.getSchedulerAdapter().runTask(() -> {
                    Player player = Bukkit.getPlayer(playerUUID);
                    if (player != null && player.isOnline()) {
                        String ipAddress = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "N/A";
                        player.removePotionEffect(PotionEffectType.BLINDNESS);
                        player.removePotionEffect(PotionEffectType.SLOW);
                        player.sendTitle(" ", lm.getString("game.access_granted"), 10, 40, 10);
                        plugin.getBotManager().sendSuccessfulLoginNotification(chatId, player.getName(), ipAddress);
                    }
                });
                
                plugin.getBotManager().editMessage(chatId, messageId, originalText + "\n\n<b>" + lm.getTelegramString("telegram.action.login_confirmed") + "</b>");
                answerCallbackQuery(callbackQueryId, lm.getTelegramString("telegram.action.login_confirmed"), false);
            } else {
                plugin.getBotManager().editMessage(chatId, messageId, originalText);
                answerCallbackQuery(callbackQueryId, lm.getTelegramString("telegram.action.login_error"), true);
            }
        } else if (data.startsWith("deny_login")) {
            plugin.getBotManager().editMessage(chatId, messageId, originalText + "\n\n<b>" + lm.getTelegramString("telegram.action.login_denied") + "</b>");
            answerCallbackQuery(callbackQueryId, lm.getTelegramString("telegram.action.login_denied"), false);
        }
    }
    
    private String buildConfirmCancelKeyboard(String confirmAction) {
        LocaleManager lm = plugin.getLocaleManager();
        JSONObject keyboard = new JSONObject();
        JSONArray row = new JSONArray();
        row.put(new JSONObject().put("text", lm.getTelegramString("telegram.menu.buttons.confirm")).put("callback_data", "menu:" + confirmAction));
        row.put(new JSONObject().put("text", lm.getTelegramString("telegram.menu.buttons.cancel")).put("callback_data", "menu:back_to_menu"));
        keyboard.put("inline_keyboard", new JSONArray().put(row));
        return keyboard.toString();
    }
    
    private String buildBackButton() {
        LocaleManager lm = plugin.getLocaleManager();
        JSONObject keyboard = new JSONObject();
        JSONArray row = new JSONArray();
        row.put(new JSONObject().put("text", "⬅️ " + lm.getTelegramString("telegram.menu.buttons.back")).put("callback_data", "menu:back_to_menu"));
        keyboard.put("inline_keyboard", new JSONArray().put(row));
        return keyboard.toString();
    }

    private void answerCallbackQuery(String callbackQueryId, String text, boolean showAlert) {
        if (botToken == null) return;
        Unirest.post("https://api.telegram.org/bot" + botToken + "/answerCallbackQuery")
                .field("callback_query_id", callbackQueryId)
                .field("text", text)
                .field("show_alert", String.valueOf(showAlert))
                .asJsonAsync();
    }
}