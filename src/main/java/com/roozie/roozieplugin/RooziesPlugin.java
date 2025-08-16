package com.roozie.roozieplugin;

import com.roozie.roozieplugin.emotes.EmoteManager;
import com.roozie.roozieplugin.roles.RoleManager;
import com.roozie.roozieplugin.Namenweg.NameVisibilityManager;
import com.roozie.roozieplugin.Namenweg.ToggleNameCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.io.File;

public class RooziesPlugin extends JavaPlugin {

    private RoleManager roleManager;
    private EmoteManager emoteManager;
    private NameVisibilityManager nameVisibilityManager;

    // Bestanden & config voor menu_config.yml
    private File menuConfigFile;
    private FileConfiguration menuConfig;

    // roles.yml runtime-bestand
    private File rolesFile;

    @Override
    public void onEnable() {
        // 📁 Zorg dat pluginmap bestaat
        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        // 📄 config.yml (indien aanwezig in resources)
        saveDefaultConfig();

        // 📄 menu_config.yml: ALLEEN kopiëren als 'ie nog niet bestaat
        saveResource("menu_config.yml", false);
        menuConfigFile = new File(getDataFolder(), "menu_config.yml");
        menuConfig = YamlConfiguration.loadConfiguration(menuConfigFile);

        // Merge defaults uit JAR -> vult ontbrekende keys aan, overschrijft GEEN user-waarden
        try (InputStream in = getResource("menu_config.yml")) {
            if (in != null) {
                YamlConfiguration def = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                menuConfig.setDefaults(def);
                menuConfig.options().copyDefaults(true);
                menuConfig.save(menuConfigFile);
            }
        } catch (IOException e) {
            getLogger().warning("Kon menu_config.yml defaults niet mergen: " + e.getMessage());
        }

        // 📄 emotes.yml: ALLEEN kopiëren als 'ie nog niet bestaat (geen merge nodig tenzij jij dat wilt)
        saveResource("emotes.yml", false);

        // 📄 roles.yml: NOOIT uit JAR schrijven; alleen runtime-bestand aanmaken
        rolesFile = new File(getDataFolder(), "roles.yml");
        try {
            if (!rolesFile.exists()) rolesFile.createNewFile();
        } catch (IOException e) {
            getLogger().severe("Kon roles.yml niet aanmaken!");
            e.printStackTrace();
        }

        // 🧠 Managers
        this.roleManager = new RoleManager(this);   // registreert zichzelf als Listener in de constructor
        this.emoteManager = new EmoteManager(this);
        this.nameVisibilityManager = new NameVisibilityManager();

        // 📌 Events registreren (NIET nog eens RoleManager, om dubbel afvuren te voorkomen)
        Bukkit.getPluginManager().registerEvents(nameVisibilityManager, this);

        // ⚙️ Commands
        // ToggleName via eigen executor
        if (getCommand("togglename") != null) {
            getCommand("togglename").setExecutor(new ToggleNameCommand(nameVisibilityManager));
        }

        // Emote commands -> doorgeven aan EmoteManager
        String[] emotes = {
                "wave", "cry", "sit", "kiss", "holdhand", "blush", "love", "pain", "angry",
                "shocked", "ashamed", "laugh", "lie_down", "scared", "jealous", "shy",
                "surprised", "proud", "serious", "calm", "dream", "sleep", "kneel",
                "hug", "push", "grab", "fall"
        };
        for (String emote : emotes) {
            if (getCommand(emote) != null) {
                getCommand(emote).setExecutor((sender, command, label, args) ->
                        emoteManager.handleCommand(sender, command, label, new String[]{emote}));
            }
        }

        // Role commands -> expliciet aan RoleManager koppelen (alternatief voor onCommand fallback)
        if (getCommand("rolmenu") != null) {
            getCommand("rolmenu").setExecutor((sender, cmd, label, args) -> roleManager.handleCommand(sender, cmd, label, args));
        }
        if (getCommand("resetrol") != null) {
            getCommand("resetrol").setExecutor((sender, cmd, label, args) -> roleManager.handleCommand(sender, cmd, label, args));
        }

        // /roozie reload -> herlaadt alleen menu_config.yml (handig voor live tweaks)
        if (getCommand("roozie") != null) {
            getCommand("roozie").setExecutor((sender, cmd, label, args) -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                    reloadMenuConfig();
                    sender.sendMessage("§amenu_config.yml herladen.");
                    return true;
                }
                sender.sendMessage("§eGebruik: /roozie reload");
                return true;
            });
        }

        // 🧍 Naamzichtbaarheid initialiseren voor al online spelers (bij herstart/reload)
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

    // Fallback router voor commands die geen specifieke executor kregen
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (emoteManager != null && emoteManager.handleCommand(sender, command, label, args)) return true;
        if (roleManager != null && roleManager.handleCommand(sender, command, label, args)) return true;
        return false;
    }

    /* =======================
       Getters & reloaders
       ======================= */

    public File getEmotesFile() {
        return new File(getDataFolder(), "emotes.yml");
    }

    public File getRolesFile() {
        return rolesFile;
    }

    public File getMenuConfigFile() {
        return menuConfigFile;
    }

    public FileConfiguration getMenuConfig() {
        return menuConfig;
    }

    public void reloadMenuConfig() {
        this.menuConfig = YamlConfiguration.loadConfiguration(menuConfigFile);
    }
}
