package com.roozie.roozieplugin;

//std imports
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.PrivateKey;
import java.util.*;
import java.io.File;
import java.io.IOException;

//imports voor plugin
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.yaml.snakeyaml.Yaml;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;


public class RooziesPlugin extends JavaPlugin implements Listener {

    private File rolesFiles;
    private FileConfiguration rolesConfig;
    private final Map<UUID, String> spelerRollen = new HashMap<>();

    @Override
    public void onEnable() {
        // Plugin startup logic
        getServer().getPluginManager().registerEvents(this, this);
        setupConfig();
        loadRoles();
        getLogger().info("Roozies plugin has been enabled!");
    }

    private void setupConfig(){
        rolesFiles = new File(getDataFolder(), "Roles.yml");
        if (!rolesFiles.exists()){
            rolesFiles.getParentFile().mkdirs();
            saveResource("roles.yml", false);
        }
        rolesConfig = YamlConfiguration.loadConfiguration(rolesFiles);
    }

    private void saveRoles(){
        try {
            rolesConfig.save(rolesFiles);
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        Player speler = event.getPlayer();
        UUID uuid = speler.getUniqueId();

        if(!rolesConfig.contains(uuid.toString()) || speler.hasPermission("roozie.rol.herkiezen")){
            Bukkit.getScheduler().runTaskLater(this, () -> openRoleMenu(speler), 40L);
        } else {
            String rol = rolesConfig.getString(uuid.toString());
            speler.sendMessage("§aWelkom terug, je rol is: §e" + rol);
        }

        if(!spelerRollen.containsKey(speler.getUniqueId())){
            Bukkit.getScheduler().runTaskLater(this, () -> openRoleMenu(speler), 40L);
        }
    }

    public void openRoleMenu(Player speler) {
        Inventory menu = Bukkit.createInventory(null, 9, "Kies je rol");

        menu.setItem(0, createMenuItem(Material.WOLF_SPAWN_EGG, "Weerwolf"));
        menu.setItem(1, createMenuItem(Material.FEATHER, "Engel"));
        menu.setItem(2, createMenuItem(Material.POTION, "Heks"));
        menu.setItem(3, createMenuItem(Material.BREAD, "Mens"));

        if (speler.hasPermission("roozie.rol.Ultima")) {
            menu.setItem(4, createMenuItem(Material.NETHER_STAR, "Ultima Weerwolf"));
        }

        speler.openInventory(menu);
    }

    private ItemStack createMenuItem (Material materiaal, String naam){
        ItemStack item = new ItemStack(materiaal);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(naam);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event){
        if (event.getView().getTitle().equals("Kies je rol")){
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getItemMeta() == null){
                Player speler = (Player) event.getWhoClicked();
                String gekozenRol = event.getCurrentItem().getItemMeta().getDisplayName();

                if (gekozenRol.equals("Ultima Weerwolf") && !speler.hasPermission("roozie.rol.ultima")){
                    speler.sendMessage("§cJe hebt geen permissie voor deze rol.");
                    return;
                }
                spelerRollen.put(speler.getUniqueId(), gekozenRol);
                speler.sendMessage("§aJe hebt gekozen voor de rol: §e" + gekozenRol);
                speler.closeInventory();
            }
        }
    }

    public void loadRoles() {
        File rolesFile = new File(getDataFolder(), "roles.yml");
        if (!rolesFile.exists()) {
            saveResource("roles.yml", false);
        }
        rolesConfig = YamlConfiguration.loadConfiguration(rolesFile);
    }


    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("Roozies plugin has been disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if (command.getName().equalsIgnoreCase("help")){
            sender.sendMessage("Help menu");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Alleen spelers kunnen dit commando gebruiken.");
            return true;
        }

        Player speler = (Player) sender;

        if (!speler.hasPermission("roozie.rol.reset")) {
            speler.sendMessage("§cJe hebt geen permissie om je rol te resetten.");
            return true;
        }

        UUID uuid = speler.getUniqueId();

        if (!rolesConfig.contains(uuid.toString())) {
            speler.sendMessage("§eJe hebt nog geen rol gekozen.");
            return true;
        }

        rolesConfig.set(uuid.toString(), null);
        saveRoles();

        speler.sendMessage("§aJe rol is gereset. Kies opnieuw...");
        openRoleMenu(speler);

        return true;
    }
}