package com.casmivs.twofa;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class TwoFA extends JavaPlugin {

    private static TwoFA instance;
    private DataManager dataManager;
    private BotManager botManager;
    private TelegramListener telegramListener;
    private BukkitTask listenerTask;
    private CommandManager commandManager;
    private LocaleManager localeManager;
    private SchedulerAdapter schedulerAdapter;

    public final Set<UUID> unverifiedPlayers = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void onEnable() {
        instance = this;
        
        initializeScheduler();
        
        saveDefaultConfig();
        
        localeManager = new LocaleManager(this);
        dataManager = new DataManager(this);
        botManager = new BotManager(this);
        commandManager = new CommandManager(this);
        
        PluginCommand command = getCommand("2fa");
        if (command != null) {
            command.setExecutor(commandManager);
            command.setTabCompleter(commandManager);
        }

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        
        telegramListener = new TelegramListener(this);
        startAsyncTasks();
        
        getLogger().info("2FA (v" + getDescription().getVersion() + ") successfully enabled on " + schedulerAdapter.getClass().getSimpleName().replace("SchedulerAdapter", "") + "!");
    }
    
    private void initializeScheduler() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            this.schedulerAdapter = new FoliaSchedulerAdapter(this);
        } catch (ClassNotFoundException e) {
            this.schedulerAdapter = new BukkitSchedulerAdapter(this);
        }
    }

    @Override
    public void onDisable() {
        stopAsyncTasks();
        getLogger().info("2FA disabled correctly.");
    }
    
    public void reloadPluginLogic() {
        getLogger().info("Reloading 2FA plugin...");
        
        stopAsyncTasks();
        
        reloadConfig();
        localeManager.loadLocales();
        dataManager.reloadConfig();
        
        botManager = new BotManager(this);
        
        telegramListener = new TelegramListener(this);
        startAsyncTasks();
        
        getLogger().info("2FA plugin successfully reloaded.");
    }
    
    private void startAsyncTasks() {
        if (telegramListener != null) {
            listenerTask = getServer().getScheduler().runTaskAsynchronously(this, telegramListener);
        }
    }
    
    private void stopAsyncTasks() {
        if (commandManager != null) {
            commandManager.clearTokens();
        }
        if (telegramListener != null) {
            telegramListener.stop();
            telegramListener = null;
        }
        if (listenerTask != null && !listenerTask.isCancelled()) {
            listenerTask.cancel();
            listenerTask = null;
        }
    }

    public static TwoFA getInstance() { return instance; }
    public DataManager getDataManager() { return dataManager; }
    public BotManager getBotManager() { return botManager; }
    public LocaleManager getLocaleManager() { return localeManager; }
    public CommandManager getCommandManager() { return commandManager; }
    public SchedulerAdapter getSchedulerAdapter() { return schedulerAdapter; }
}