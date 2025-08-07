package com.roozie.roozieplugin.Namenweg;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class Namenweg extends JavaPlugin {

    private static Namenweg instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(new NameVisibilityListener(), this);
        getCommand("togglename").setExecutor(new ToggleNameCommand());

        // Bij het joinen meteen naam aanpassen volgens standaardinstelling
        Bukkit.getOnlinePlayers().forEach(NameVisibilityManager::applyNameVisibility);
    }

    public static Namenweg getInstance() {
        return instance;
    }

    public FileConfiguration getPluginConfig() {
        return getConfig();
    }
}
