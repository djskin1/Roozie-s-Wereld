package com.roozie.roozieplugin;

import com.roozie.roozieplugin.emotes.EmoteManager;
import com.roozie.roozieplugin.roles.RoleManager;
import com.roozie.roozieplugin.Namenweg.NameVisibilityManager;
import com.roozie.roozieplugin.Namenweg.ToggleNameCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class RooziesPlugin extends JavaPlugin {

    private RoleManager roleManager;
    private EmoteManager emoteManager;
    private NameVisibilityManager nameVisibilityManager;

    @Override
    public void onEnable() {
        // 📁 Configs en resources
        saveDefaultConfig();
        saveResource("menu_config.yml", false);
        saveResource("emotes.yml", false);

        // 🧠 Managers
        this.roleManager = new RoleManager(this);
        this.emoteManager = new EmoteManager(this);
        this.nameVisibilityManager = new NameVisibilityManager();

        // 📌 Events registreren
        Bukkit.getPluginManager().registerEvents(roleManager, this);
        Bukkit.getPluginManager().registerEvents(nameVisibilityManager, this);

        // ⚙️ Commands
        getCommand("togglename").setExecutor(new ToggleNameCommand(nameVisibilityManager));

        // ✅ Emote commands
        String[] emotes = {
                "wave", "cry", "sit", "kiss", "holdhand", "blush", "love", "pain", "angry",
                "shocked", "ashamed", "laugh", "lie_down", "scared", "jealous", "shy",
                "surprised", "proud", "serious", "calm", "dream", "sleep", "kneel",
                "hug", "push", "grab", "fall"
        };

        for (String emote : emotes) {
            getCommand(emote).setExecutor((sender, command, label, args) ->
                    emoteManager.handleCommand(sender, command, label, new String[]{emote}));
        }

        // 🧍 Zet naamzichtbaarheid per speler (voor al online spelers bij herstart)
        for (Player speler : Bukkit.getOnlinePlayers()) {
            if (nameVisibilityManager.isHidingNametags(speler)) {
                nameVisibilityManager.hideOthersNametagsFor(speler);
            } else {
                nameVisibilityManager.resetScoreboard(speler);
            }
        }

        getLogger().info("✅ RooziesPlugin succesvol ingeschakeld.");
    }

    @Override
    public void onDisable() {
        getLogger().info("⛔ RooziesPlugin is uitgeschakeld.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (emoteManager.handleCommand(sender, command, label, args)) return true;
        if (roleManager.handleCommand(sender, command, label, args)) return true;
        return false;
    }

    // 📂 Bestanden
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
