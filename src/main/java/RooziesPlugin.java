package com.roozie.roozieplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class RooziesPlugin extends JavaPlugin implements Listener {

    private File rolesFile;
    private FileConfiguration rolesConfig;

    private File menuConfigFile;
    private FileConfiguration menuConfig;

    // Houdt gekozen rollen tijdelijk bij tijdens runtime
    private final Map<UUID, String> spelerRollen = new HashMap<>();

    @Override
    public void onEnable() {
        // Plugin startup logic
        getServer().getPluginManager().registerEvents(this, this);
        createRolesConfig();
        createMenuConfig();

        for (Player speler : Bukkit.getOnlinePlayers()){
            zetSpelerInVerborgenTeam(speler);
        }

        getLogger().info("RooziesPlugin is ingeschakeld!");
    }

    @Override
    public void onDisable() {
        getLogger().info("RooziesPlugin is uitgeschakeld!");
    }

    // --- Configuratie handling ---

    private void createRolesConfig() {
        rolesFile = new File(getDataFolder(), "roles.yml");
        if (!rolesFile.exists()) {
            try {
                getDataFolder().mkdirs();
                rolesFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Kon roles.yml niet aanmaken!");
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

    private void createMenuConfig() {
        menuConfigFile = new File(getDataFolder(), "menu_config.yml");
        if (!menuConfigFile.exists()) {
            saveResource("menu_config.yml", false); // kopieert van jar naar datafolder
        }
        menuConfig = YamlConfiguration.loadConfiguration(menuConfigFile);
    }

    public FileConfiguration getMenuConfig() {
        return menuConfig;
    }

    // --- Speler join event: check rol en open menu indien nodig ---

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player speler = event.getPlayer();
        UUID uuid = speler.getUniqueId();
        zetSpelerInVerborgenTeam(event.getPlayer());

        // Als speler nog geen rol heeft of permissie heeft om te herkiezenn, open rolmenu
        if (!rolesConfig.contains(uuid.toString()) || speler.hasPermission("roozie.rol.herkiezen")) {
            Bukkit.getScheduler().runTaskLater(this, () -> openRoleMenu(speler), 20L);
            return;
        }

        String rol = rolesConfig.getString(uuid.toString());
        speler.sendMessage("§aWelkom terug! Je rol is: §e" + rol);
    }

    // --- Menu openen met rollen uit menu_config.yml ---

    public void openRoleMenu(Player speler) {
        ConfigurationSection rollenSectie = getMenuConfig().getConfigurationSection("rollen");
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
            String lore = rolSectie.getString("lore", "");
            String permissie = rolSectie.getString("permissie", "");

            if (materiaal == null) materiaal = Material.STONE;

            // Alleen tonen als speler permissie heeft voor deze rol
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
            meta.setDisplayName(naam);
            if (!lore.isEmpty()) {
                meta.setLore(java.util.Collections.singletonList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // --- Klik event in rol menu ---

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("Kies je rol")) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR || !clickedItem.hasItemMeta()) return;

        Player speler = (Player) event.getWhoClicked();
        String gekozenRol = clickedItem.getItemMeta().getDisplayName();

        ConfigurationSection rolSectie = getMenuConfig().getConfigurationSection("rollen." + gekozenRol);
        if (rolSectie == null) {
            speler.sendMessage("§cDeze rol bestaat niet (meer).");
            return;
        }

        String permissie = rolSectie.getString("permissie", "");
        if (!permissie.isEmpty() && !speler.hasPermission(permissie)) {
            speler.sendMessage("§cJe hebt geen permissie voor deze rol.");
            return;
        }

        // Sla rol op in roles.yml
        UUID uuid = speler.getUniqueId();
        rolesConfig.set(uuid.toString(), gekozenRol);
        saveRolesConfig();

        spelerRollen.put(uuid, gekozenRol);

        speler.sendMessage("§aJe hebt gekozen voor de rol: §e" + gekozenRol);
        speler.closeInventory();
    }

    // --- Command om rol te resetten ---

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cDit commando kan alleen door spelers worden gebruikt.");
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
        saveRolesConfig();

        speler.sendMessage("§aJe rol is gereset! Kies een nieuwe rol.");
        openRoleMenu(speler);

        return true;
    }

    //verborgen namen
    public void zetSpelerInVerborgenTeam(Player speler) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam("verborgen");

        if (team == null) {
            team = scoreboard.registerNewTeam("verborgen");
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }

        if (!team.hasEntry(speler.getName())) {
            team.addEntry(speler.getName());
        }
    }


}
