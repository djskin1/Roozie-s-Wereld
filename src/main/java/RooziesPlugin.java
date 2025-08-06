package com.roozie.roozieplugin;

import com.roozie.roozieplugin.roles.RoleManager;
import com.roozie.roozieplugin.emotes.EmoteManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class RooziesPlugin extends JavaPlugin implements Listener {

    private RoleManager roleManager;
    private EmoteManager emoteManager;

    @Override
    public void onEnable() {
        // ⏳ Setup bestanden
        saveDefaultConfig();
        saveResource("menu_config.yml", false);
        saveResource("emotes.yml", false);

        // ⚙️ Managers initialiseren
        this.roleManager = new RoleManager(this);
        this.emoteManager = new EmoteManager(this);

        // 📌 Events registeren
        Bukkit.getPluginManager().registerEvents(roleManager, this);

        // ✅ Emote commands registreren
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

        // 🛡️ Zet bestaande spelers in hun teams
        for (Player speler : Bukkit.getOnlinePlayers()) {
            roleManager.zetSpelerInVerborgenTeam(speler);
        }

        getLogger().info("✅ RooziesPlugin succesvol ingeschakeld.");
    }

    @Override
    public void onDisable() {
        getLogger().info("⛔ RooziesPlugin uitgeschakeld.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 🌐 Laat managers eerst commands afhandelen
        if (emoteManager.handleCommand(sender, command, label, args)) return true;
        if (roleManager.handleCommand(sender, command, label, args)) return true;
        return false;
    }

    // 📂 Bestandsreferenties
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
