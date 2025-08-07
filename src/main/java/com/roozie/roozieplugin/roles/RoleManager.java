package com.roozie.roozieplugin.roles;

import com.roozie.roozieplugin.RooziesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.geysermc.floodgate.api.FloodgateApi;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RoleManager implements Listener {

    private final RooziesPlugin plugin;
    private File rolesFile;
    private FileConfiguration rolesConfig;
    private final Map<UUID, String> spelerRollen = new HashMap<>();

    public RoleManager(RooziesPlugin plugin) {
        this.plugin = plugin;
        createRolesConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void createRolesConfig() {
        rolesFile = plugin.getRolesFile();
        if (!rolesFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                rolesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Kon roles.yml niet aanmaken!");
                e.printStackTrace();
            }
        }
        rolesConfig = YamlConfiguration.loadConfiguration(rolesFile);
    }

    private void saveRolesConfig() {
        try {
            rolesConfig.save(rolesFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player speler = event.getPlayer();
        UUID uuid = speler.getUniqueId();

        if (!rolesConfig.contains(uuid.toString()) || speler.hasPermission("roozie.rol.herkiezen")) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> openRoleMenu(speler), 20L);
            return;
        }

        String rol = rolesConfig.getString(uuid.toString());
        spelerRollen.put(uuid, rol);
        speler.sendMessage("§aWelkom terug! Je rol is: §e" + rol);
    }

    public void openRoleMenu(Player speler) {
        FileConfiguration menuConfig = YamlConfiguration.loadConfiguration(plugin.getMenuConfigFile());
        ConfigurationSection rollenSectie = menuConfig.getConfigurationSection("rollen");

        if (rollenSectie == null) {
            speler.sendMessage("§cEr zijn geen rollen geconfigureerd! Neem contact op met een admin.");
            return;
        }

        Inventory menu = Bukkit.createInventory(null, 9, "Kies je rol");
        int slot = 0;

        for (String rolNaam : rollenSectie.getKeys(false)) {
            ConfigurationSection rolSectie = rollenSectie.getConfigurationSection(rolNaam);
            if (rolSectie == null) continue;

            Material materiaal = Material.getMaterial(rolSectie.getString("materiaal", "STONE"));
            if (materiaal == null) materiaal = Material.STONE;

            String lore = rolSectie.getString("lore", "");
            String permissie = rolSectie.getString("permissie", "");

            if (permissie.isEmpty() || speler.hasPermission(permissie)) {
                menu.setItem(slot++, createMenuItem(materiaal, rolNaam, lore));
            }
        }

        speler.openInventory(menu);
    }

    private ItemStack createMenuItem(Material materiaal, String naam, String lore) {
        ItemStack item = new ItemStack(materiaal);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e" + naam);
            if (!lore.isEmpty()) {
                meta.setLore(Collections.singletonList("§7" + lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("Kies je rol")) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR || !clickedItem.hasItemMeta()) return;

        Player speler = (Player) event.getWhoClicked();
        String gekozenRol = clickedItem.getItemMeta().getDisplayName().replace("§e", "");

        FileConfiguration menuConfig = YamlConfiguration.loadConfiguration(plugin.getMenuConfigFile());
        ConfigurationSection rolSectie = menuConfig.getConfigurationSection("rollen." + gekozenRol);
        if (rolSectie == null) {
            speler.sendMessage("§cDeze rol bestaat niet (meer).");
            return;
        }

        String permissie = rolSectie.getString("permissie", "");
        if (!permissie.isEmpty() && !speler.hasPermission(permissie)) {
            speler.sendMessage("§cJe hebt geen permissie voor deze rol.");
            return;
        }

        UUID uuid = speler.getUniqueId();
        rolesConfig.set(uuid.toString(), gekozenRol);
        saveRolesConfig();

        spelerRollen.put(uuid, gekozenRol);
        speler.sendMessage("§aJe hebt gekozen voor de rol: §e" + gekozenRol);
        speler.closeInventory();
    }

    public void resetRol(Player speler) {
        UUID uuid = speler.getUniqueId();

        if (!rolesConfig.contains(uuid.toString())) {
            speler.sendMessage("§eJe hebt nog geen rol gekozen.");
            return;
        }

        rolesConfig.set(uuid.toString(), null);
        saveRolesConfig();

        speler.sendMessage("§aJe rol is gereset. Kies opnieuw...");
        openRoleMenu(speler);
    }

    public boolean handleCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player speler = (Player) sender;

        if (command.getName().equalsIgnoreCase("resetrol")) {
            resetRol(speler);
            return true;
        }

        return false;
    }
}
