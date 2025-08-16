package com.roozie.roozieplugin;

import com.roozie.roozieplugin.emotes.EmoteManager;
import com.roozie.roozieplugin.roles.RoleManager;
import com.roozie.roozieplugin.teams.TeamManager;
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
    private TeamManager teamManager;
    private EmoteManager emoteManager;
    private NameVisibilityManager nameVisibilityManager;

    // menu_config.yml
    private File menuConfigFile;
    private FileConfiguration menuConfig;

    // roles.yml (runtime)
    private File rolesFile;

    // teams.yml (runtime)
    private File teamsFile;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        saveDefaultConfig(); // roles.default_group en roles.role_upgrades

        // menu_config.yml: eenmalig kopiëren + defaults mergen
        saveResource("menu_config.yml", false);
        menuConfigFile = new File(getDataFolder(), "menu_config.yml");
        menuConfig = YamlConfiguration.loadConfiguration(menuConfigFile);
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

        // emotes.yml: eenmalig kopiëren
        saveResource("emotes.yml", false);

        // roles.yml: runtime
        rolesFile = new File(getDataFolder(), "roles.yml");
        try { if (!rolesFile.exists()) rolesFile.createNewFile(); }
        catch (IOException e) { getLogger().severe("Kon roles.yml niet aanmaken!"); e.printStackTrace(); }

        // teams.yml: runtime
        teamsFile = new File(getDataFolder(), "teams.yml");
        try { if (!teamsFile.exists()) teamsFile.createNewFile(); }
        catch (IOException e) { getLogger().severe("Kon teams.yml niet aanmaken!"); e.printStackTrace(); }

        // Managers
        this.teamManager = new TeamManager(this);   // bevat eigen command-handler
        this.roleManager = new RoleManager(this, teamManager); // registreert zichzelf als Listener
        this.emoteManager = new EmoteManager(this);
        this.nameVisibilityManager = new NameVisibilityManager();

        // Events (RoleManager NIET dubbel registreren)
        Bukkit.getPluginManager().registerEvents(nameVisibilityManager, this);

        // Commands
        if (getCommand("togglename") != null)
            getCommand("togglename").setExecutor(new ToggleNameCommand(nameVisibilityManager));

        if (getCommand("team") != null)
            getCommand("team").setExecutor((s,c,l,a) -> teamManager.handleCommand(s,c,l,a));

        if (getCommand("rolmenu") != null)
            getCommand("rolmenu").setExecutor((s,c,l,a) -> roleManager.handleCommand(s,c,l,a));
        if (getCommand("resetrol") != null)
            getCommand("resetrol").setExecutor((s,c,l,a) -> roleManager.handleCommand(s,c,l,a));
        if (getCommand("rolupgrade") != null)
            getCommand("rolupgrade").setExecutor((s,c,l,a) -> roleManager.handleCommand(s,c,l,a));

        if (getCommand("roozie") != null)
            getCommand("roozie").setExecutor((sender, cmd, label, args) -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                    reloadMenuConfig();
                    sender.sendMessage("§amenu_config.yml herladen.");
                    return true;
                }
                sender.sendMessage("§eGebruik: /roozie reload");
                return true;
            });

        // Emote commands
        String[] emotes = {
                "wave","cry","sit","kiss","holdhand","blush","love","pain","angry",
                "shocked","ashamed","laugh","lie_down","scared","jealous","shy",
                "surprised","proud","serious","calm","dream","sleep","kneel",
                "hug","push","grab","fall"
        };
        for (String emote : emotes) {
            if (getCommand(emote) != null) {
                getCommand(emote).setExecutor((sender, command, label, args) ->
                        emoteManager.handleCommand(sender, command, label, new String[]{emote}));
            }
        }

        // Naamzichtbaarheid herstellen
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (nameVisibilityManager.isHidingNametags(p)) nameVisibilityManager.hideOthersNametagsFor(p);
            else nameVisibilityManager.resetScoreboard(p);
        }

        getLogger().info("✅ RooziesPlugin succesvol ingeschakeld.");
    }

    @Override public void onDisable() { getLogger().info("⛔ RooziesPlugin is uitgeschakeld."); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (emoteManager != null && emoteManager.handleCommand(sender, command, label, args)) return true;
        if (roleManager  != null && roleManager.handleCommand(sender, command, label, args)) return true;
        if (teamManager  != null && teamManager.handleCommand(sender, command, label, args)) return true;
        return false;
    }

    // ==== Getters ====
    public File getEmotesFile() { return new File(getDataFolder(), "emotes.yml"); }
    public File getRolesFile() { return rolesFile; }
    public File getMenuConfigFile() { return menuConfigFile; }
    public FileConfiguration getMenuConfig() { return menuConfig; }
    public void reloadMenuConfig() { this.menuConfig = YamlConfiguration.loadConfiguration(menuConfigFile); }
    public File getTeamsFile() { return teamsFile; }
    public TeamManager getTeamManager() { return teamManager; }
}
