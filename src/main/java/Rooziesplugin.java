import org.bukkit.plugin.java.JavaPlugin;

public  class Rooziesplugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("Roozies plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("Roozies plugin has been disabled!");
    }
}