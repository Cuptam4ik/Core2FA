package com.casmivs.twofa;

import kong.unirest.Unirest;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BotManager {

    private final TwoFA plugin;

    public BotManager(TwoFA plugin) {
        this.plugin = plugin;
    }

    private String getApiUrl() {
        String botToken = plugin.getConfig().getString("telegram.bot-token");
        if (botToken == null || botToken.isEmpty() || botToken.equals("ВАШ_ТОКЕН_БОТА_СЮДА")) {
            return null;
        }
        return "https://api.telegram.org/bot" + botToken;
    }

    public void sendMenuMessage(long chatId) {
        String apiUrl = getApiUrl();
        if (apiUrl == null) return;
        UUID playerUUID = plugin.getDataManager().findUUIDbyTelegramId(chatId);
        if (playerUUID == null) return;

        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUUID);
        boolean is2faEnabled = plugin.getDataManager().is2faEnabled(playerUUID);
        boolean isBanned = player.isBanned();
        
        String status2fa = plugin.getLocaleManager().getTelegramString(is2faEnabled ? "telegram.menu.status_2fa_enabled" : "telegram.menu.status_2fa_disabled");
        String statusBan = plugin.getLocaleManager().getTelegramString(isBanned ? "telegram.menu.status_ban_active" : "telegram.menu.status_ban_inactive");

        String rawText = plugin.getLocaleManager().getTelegramString("telegram.menu.text",
                "%player_name%", player.getName() != null ? player.getName() : "N/A",
                "%2fa_status%", status2fa,
                "%ban_status%", statusBan
        );
        
        String processedText = minecraftToTelegramHTML(rawText);
        JSONObject keyboard = buildMenuKeyboard(is2faEnabled);

        Unirest.post(apiUrl + "/sendMessage")
                .field("chat_id", String.valueOf(chatId))
                .field("text", processedText)
                .field("parse_mode", "HTML")
                .field("reply_markup", keyboard.toString())
                .asJsonAsync();
    }
    
    public void editToMenuMessage(long chatId, long messageId) {
        String apiUrl = getApiUrl();
        if (apiUrl == null) return;
        UUID playerUUID = plugin.getDataManager().findUUIDbyTelegramId(chatId);
        if (playerUUID == null) return;
        
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUUID);
        boolean is2faEnabled = plugin.getDataManager().is2faEnabled(playerUUID);
        boolean isBanned = player.isBanned();
        
        String status2fa = plugin.getLocaleManager().getTelegramString(is2faEnabled ? "telegram.menu.status_2fa_enabled" : "telegram.menu.status_2fa_disabled");
        String statusBan = plugin.getLocaleManager().getTelegramString(isBanned ? "telegram.menu.status_ban_active" : "telegram.menu.status_ban_inactive");

        String rawText = plugin.getLocaleManager().getTelegramString("telegram.menu.text",
                "%player_name%", player.getName() != null ? player.getName() : "N/A",
                "%2fa_status%", status2fa,
                "%ban_status%", statusBan
        );
        
        String processedText = minecraftToTelegramHTML(rawText);
        JSONObject keyboard = buildMenuKeyboard(is2faEnabled);
        editMessageWithKeyboard(chatId, messageId, processedText, keyboard.toString());
    }

    public void requestLoginConfirmation(long chatId, String ipAddress) {
        String apiUrl = getApiUrl();
        if (apiUrl == null) return;
        String text = plugin.getLocaleManager().getTelegramString("telegram.login_request", "%ip_address%", ipAddress);
        JSONObject confirmButton = new JSONObject().put("text", "✅ " + plugin.getLocaleManager().getTelegramString("telegram.menu.buttons.confirm_login")).put("callback_data", "confirm_login");
        JSONObject denyButton = new JSONObject().put("text", "❌ " + plugin.getLocaleManager().getTelegramString("telegram.menu.buttons.deny_login")).put("callback_data", "deny_login");
        JSONArray row = new JSONArray().put(confirmButton).put(denyButton);
        JSONObject keyboard = new JSONObject().put("inline_keyboard", new JSONArray().put(row));
        Unirest.post(apiUrl + "/sendMessage").field("chat_id", String.valueOf(chatId)).field("text", text).field("parse_mode", "HTML").field("reply_markup", keyboard.toString()).asJsonAsync();
    }

    public void sendSuccessfulLoginNotification(long chatId, String playerName, String ipAddress) {
        String apiUrl = getApiUrl();
        if (apiUrl == null) return;
        String text = plugin.getLocaleManager().getTelegramString("telegram.login_notification", "%player_name%", playerName, "%ip_address%", ipAddress);
        String buttonText = plugin.getLocaleManager().getTelegramString("telegram.menu.buttons.terminate_sessions");
        JSONObject terminateButton = new JSONObject().put("text", buttonText).put("callback_data", "notification:terminate");
        JSONArray row = new JSONArray().put(terminateButton);
        JSONObject keyboard = new JSONObject().put("inline_keyboard", new JSONArray().put(row));
        Unirest.post(apiUrl + "/sendMessage").field("chat_id", String.valueOf(chatId)).field("text", text).field("parse_mode", "HTML").field("reply_markup", keyboard.toString()).asJsonAsync();
    }
    
    private JSONObject buildMenuKeyboard(boolean is2faEnabled) {
        LocaleManager lm = plugin.getLocaleManager();
        JSONObject keyboard = new JSONObject();
        JSONArray rows = new JSONArray();
        rows.put(new JSONArray().put(new JSONObject().put("text", lm.getTelegramString("telegram.menu.buttons.info")).put("callback_data", "menu:info")));
        String toggleText = is2faEnabled ? lm.getTelegramString("telegram.menu.buttons.toggle_2fa_off") : lm.getTelegramString("telegram.menu.buttons.toggle_2fa_on");
        rows.put(new JSONArray().put(new JSONObject().put("text", toggleText).put("callback_data", "menu:toggle_2fa")).put(new JSONObject().put("text", lm.getTelegramString("telegram.menu.buttons.terminate_sessions")).put("callback_data", "menu:terminate")));
        JSONArray thirdRow = new JSONArray();
        if (plugin.getConfig().getBoolean("telegram-menu.settings.allow-unlinking", false)) {
            thirdRow.put(new JSONObject().put("text", lm.getTelegramString("telegram.menu.buttons.unlink_account")).put("callback_data", "menu:unlink"));
        }
        if (plugin.getConfig().getBoolean("telegram-menu.settings.allow-remote-ban", false)) {
            thirdRow.put(new JSONObject().put("text", lm.getTelegramString("telegram.menu.buttons.toggle_ban_account")).put("callback_data", "menu:toggle_ban"));
        }
        if (!thirdRow.isEmpty()) {
            rows.put(thirdRow);
        }
        keyboard.put("inline_keyboard", rows);
        return keyboard;
    }
    
    public void sendMessage(long chatId, String text, boolean markdown) {
        String apiUrl = getApiUrl();
        if (apiUrl == null) return;
        Unirest.post(apiUrl + "/sendMessage").field("chat_id", String.valueOf(chatId)).field("text", text).field("parse_mode", markdown ? "Markdown" : "HTML").asJsonAsync();
    }
    
    public void editMessage(long chatId, long messageId, String text) {
        String apiUrl = getApiUrl();
        if (apiUrl == null) return;
        Unirest.post(apiUrl + "/editMessageText").field("chat_id", String.valueOf(chatId)).field("message_id", String.valueOf(messageId)).field("text", text).field("parse_mode", "HTML").field("reply_markup", "").asJsonAsync();
    }
    
    public void editMessageWithKeyboard(long chatId, long messageId, String text, String keyboard) {
        String apiUrl = getApiUrl();
        if (apiUrl == null) return;
        Unirest.post(apiUrl + "/editMessageText").field("chat_id", String.valueOf(chatId)).field("message_id", String.valueOf(messageId)).field("text", text).field("parse_mode", "HTML").field("reply_markup", keyboard).asJsonAsync();
    }

    public static String minecraftToTelegramHTML(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) return "";

        String text = ChatColor.translateAlternateColorCodes('&', rawMessage);
        text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");

        Pattern pattern = Pattern.compile("§([0-9a-fklmnor])");
        Matcher matcher = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        StringBuilder openTags = new StringBuilder();

        while (matcher.find()) {
            matcher.appendReplacement(sb, "");

            if (openTags.length() > 0) {
                sb.append(openTags.reverse().toString());
                openTags.setLength(0);
            }

            char code = matcher.group(1).toLowerCase().charAt(0);
            switch (code) {
                case 'l':
                    sb.append("<b>");
                    openTags.append("</b>");
                    break;
                case 'o':
                    sb.append("<i>");
                    openTags.append("</i>");
                    break;
                case 'n':
                    sb.append("<u>");
                    openTags.append("</u>");
                    break;
                case 'm':
                    sb.append("<s>");
                    openTags.append("</s>");
                    break;
                case 'r':
                default:
                    break;
            }
        }
        matcher.appendTail(sb);

        if (openTags.length() > 0) {
            sb.append(openTags.reverse().toString());
        }

        return sb.toString();
    }
}