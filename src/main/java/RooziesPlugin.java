package RooziesPlugin;

import org.bukkit.Bukkit;
import com.roozie.roozieplugin.roles.RoleManager;
import com.roozie.roozieplugin.emotes.EmoteManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class RooziesPlugin extends JavaPlugin implements Listener{

    private RoleManager roleManager;
    private EmoteManager emoteManager;

    @Override
    public void onEnable(){
        saveDefaultConfig();
        saveResource("menu_config.yml", false);
        saveResource("emotes.yml", false);

        this.roleManager = new RoleManager(this);
        this.emoteManager = new EmoteManager(this);

        Bukkit.getPluginManager().registerEvent(roleManager, this);

        getLogger().info("RooziesPlugin ingeschakeld!");

        for (Player speler: Bukkit.getOnlinePlayers()){
            roleManager.zetSpelerInVerborgenTeam(speler);
        }
    }

    @Override
    public void onDisable(){
        getLogger().info("RooziesPlugin uitgeschakeld.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (emoteManager.handleCommand(sender, command, label, args)) return true;
        if (roleManager.handleCommand(sender, command, label, args)) return true;
        return false;
    }

    public File getEmotesFile() {
        return new File(getDataFolder(), "emotes.yml");
    }

    public File getRolesFile() {
        return new File(getDataFolder(), "roles.yml");
    }

    public File getMenuConfigFile() {
        return new File(getDataFolder(), "menu_config.yml");
    }
}