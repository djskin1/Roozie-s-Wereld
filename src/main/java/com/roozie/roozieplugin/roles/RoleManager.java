package com.roozie.roozieplugin.roles;

import com.roozie.roozieplugin.RooziesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
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
import java.util.stream.Collectors;

public class RoleManager implements Listener {

    private final RooziesPlugin plugin;
    private File rolesFile;
    private FileConfiguration rolesConfig;
    private final Map<UUID, String> spelerRollen = new HashMap<>();

    // Titel van het GUI-menu
    private static final String MENU_TITLE = ChatColor.YELLOW + "Kies je rol";

    public RoleManager(RooziesPlugin plugin) {
        this.plugin = plugin;
        createRolesConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /* =======================
       Config roles.yml laden
       ======================= */

    private void createRolesConfig() {
        rolesFile = plugin.getRolesFile();
        if (!rolesFile.exists()) {
            try {
                if (!plugin.getDataFolder().exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    plugin.getDataFolder().mkdirs();
                }
                //noinspection ResultOfMethodCallIgnored
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
            plugin.getLogger().severe("Fout bij opslaan van roles.yml");
            e.printStackTrace();
        }
    }

    /* =======================
       Events
       ======================= */

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player speler = event.getPlayer();
        UUID uuid = speler.getUniqueId();

        // (Optioneel) Floodgate melding
        try {
            if (isFloodgateEnabled() && FloodgateApi.getInstance().isFloodgatePlayer(uuid)) {
                speler.sendMessage(color("&eWelkom Bedrock speler!"));
            }
        } catch (Throwable ignored) {
            // Floodgate niet aanwezig
        }

        boolean herkiezen = speler.hasPermission("roozie.rol.herkiezen");
        if (!rolesConfig.contains(uuid.toString()) || herkiezen) {
            // Even delay zodat inventory openen niet botst met join
            Bukkit.getScheduler().runTaskLater(plugin, () -> openRoleMenu(speler), 20L);
            return;
        }

        String rol = rolesConfig.getString(uuid.toString());
        spelerRollen.put(uuid, rol != null ? rol : "");
        speler.sendMessage(color("&aWelkom terug! Je rol is: &e" + rol));
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        HumanEntity who = event.getWhoClicked();
        if (!(who instanceof Player)) return;

        // Check menu titel (legacy string; werkt op veel serverversies)
        if (!event.getView().getTitle().equals(MENU_TITLE)) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR || !clickedItem.hasItemMeta()) return;

        Player speler = (Player) who;
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String gekozenRol = ChatColor.stripColor(meta.getDisplayName()); // weghalen kleur voor lookup

        FileConfiguration menuConfig = YamlConfiguration.loadConfiguration(plugin.getMenuConfigFile());
        ConfigurationSection rolSectie = menuConfig.getConfigurationSection("rollen." + gekozenRol);
        if (rolSectie == null) {
            speler.sendMessage(color("&cDeze rol bestaat niet (meer)."));
            return;
        }

        // permissie die nodig is om deze rol te kiezen
        String vereistePermissie = rolSectie.getString("permissie", "");
        if (vereistePermissie != null && !vereistePermissie.isEmpty() && !speler.hasPermission(vereistePermissie)) {
            speler.sendMessage(color("&cJe hebt geen permissie voor deze rol."));
            return;
        }

        // LuckPerms group-naam (optioneel in menu.yml); fallback = rolnaam
        String lpGroup = rolSectie.getString("lp_group", gekozenRol);

        // Sla rol op en pas LuckPerms toe
        UUID uuid = speler.getUniqueId();
        rolesConfig.set(uuid.toString(), gekozenRol);
        saveRolesConfig();
        spelerRollen.put(uuid, gekozenRol);

        // LuckPerms toewijzing (indien aanwezig)
        applyLuckPermsGroup(speler.getUniqueId(), lpGroup);

        speler.sendMessage(color("&aJe hebt gekozen voor de rol: &e" + gekozenRol));
        speler.closeInventory();
    }

    /* =======================
       Publieke API
       ======================= */

    public void openRoleMenu(Player speler) {
        FileConfiguration menuConfig = YamlConfiguration.loadConfiguration(plugin.getMenuConfigFile());
        ConfigurationSection rollenSectie = menuConfig.getConfigurationSection("rollen");

        if (rollenSectie == null || rollenSectie.getKeys(false).isEmpty()) {
            speler.sendMessage(color("&cEr zijn geen rollen geconfigureerd! Neem contact op met een admin."));
            return;
        }

        // Filter op permissie en maak items
        List<ItemStack> items = new ArrayList<>();
        for (String rolNaam : rollenSectie.getKeys(false)) {
            ConfigurationSection rolSectie = rollenSectie.getConfigurationSection(rolNaam);
            if (rolSectie == null) continue;

            String vereistePermissie = rolSectie.getString("permissie", "");
            if (vereistePermissie != null && !vereistePermissie.isEmpty() && !speler.hasPermission(vereistePermissie)) {
                continue; // speler mag deze rol niet kiezen -> toon niet
            }

            String materiaalStr = rolSectie.getString("materiaal", "STONE");
            Material materiaal = Material.matchMaterial(materiaalStr != null ? materiaalStr : "STONE");
            if (materiaal == null) materiaal = Material.STONE;

            // lore kan 1 string of een lijst zijn
            List<String> loreRaw = rolSectie.isList("lore")
                    ? rolSectie.getStringList("lore")
                    : (rolSectie.contains("lore") ? Collections.singletonList(rolSectie.getString("lore", "")) : Collections.emptyList());

            ItemStack item = createMenuItem(materiaal, rolNaam, loreRaw);
            items.add(item);
        }

        if (items.isEmpty()) {
            speler.sendMessage(color("&cEr zijn geen rollen die jij kunt kiezen."));
            return;
        }

        int size = calcInventorySize(items.size());
        Inventory menu = Bukkit.createInventory(null, size, MENU_TITLE);

        // Zet items links naar rechts, boven naar beneden
        for (int i = 0; i < items.size() && i < size; i++) {
            menu.setItem(i, items.get(i));
        }

        speler.openInventory(menu);
    }

    public void resetRol(Player speler) {
        UUID uuid = speler.getUniqueId();

        if (!rolesConfig.contains(uuid.toString())) {
            speler.sendMessage(color("&eJe hebt nog geen rol gekozen."));
            return;
        }

        // LuckPerms: verwijder huidige group als die overeenkomt met de rol
        String huidigeRol = rolesConfig.getString(uuid.toString());
        if (huidigeRol != null && !huidigeRol.isEmpty()) {
            removeLuckPermsGroup(uuid, huidigeRol);
        }

        rolesConfig.set(uuid.toString(), null);
        saveRolesConfig();

        speler.sendMessage(color("&aJe rol is gereset. Kies opnieuw..."));
        openRoleMenu(speler);
    }

    public boolean handleCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player speler = (Player) sender;

        if (command.getName().equalsIgnoreCase("resetrol")) {
            resetRol(speler);
            return true;
        }

        if (command.getName().equalsIgnoreCase("rolmenu")) {
            openRoleMenu(speler);
            return true;
        }

        return false;
    }

    /* =======================
       Helpers
       ======================= */

    private ItemStack createMenuItem(Material materiaal, String naam, List<String> loreRaw) {
        ItemStack item = new ItemStack(materiaal);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&e" + naam));
            if (loreRaw != null && !loreRaw.isEmpty()) {
                List<String> colored = loreRaw.stream()
                        .filter(Objects::nonNull)
                        .map(this::color)
                        .collect(Collectors.toList());
                meta.setLore(colored);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private int calcInventorySize(int itemCount) {
        // rond omhoog naar dichtstbijzijnde veelvoud van 9, max 54
        int size = ((Math.max(1, itemCount) - 1) / 9 + 1) * 9;
        return Math.min(size, 54);
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private boolean isLuckPermsEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("LuckPerms");
    }

    private boolean isFloodgateEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("floodgate") || Bukkit.getPluginManager().isPluginEnabled("Floodgate");
    }

    private void applyLuckPermsGroup(UUID uuid, String groupName) {
        try {
            if (!isLuckPermsEnabled()) return;
            LuckPerms lp = LuckPermsProvider.get();

            lp.getUserManager().modifyUser(uuid, (User user) -> {
                // Verwijder alle bestaande "group.*" nodes die door dit systeem gezet zouden kunnen zijn
                // (optioneel: je kunt dit beperken tot bekende rolnamen)
                List<Node> toRemove = user.data().toCollection().stream()
                        .filter(n -> n.getKey().startsWith("group."))
                        .collect(Collectors.toList());
                toRemove.forEach(n -> user.data().remove(n));

                // Voeg nieuwe group toe
                user.data().add(Node.builder("group." + groupName.toLowerCase()).build());
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("Kon LuckPerms group niet toepassen voor " + uuid + ": " + t.getMessage());
        }
    }

    private void removeLuckPermsGroup(UUID uuid, String groupName) {
        try {
            if (!isLuckPermsEnabled()) return;
            LuckPerms lp = LuckPermsProvider.get();

            lp.getUserManager().modifyUser(uuid, (User user) -> {
                // Verwijder de specifieke group node
                List<Node> toRemove = user.data().toCollection().stream()
                        .filter(n -> n.getKey().equalsIgnoreCase("group." + groupName.toLowerCase()))
                        .collect(Collectors.toList());
                toRemove.forEach(n -> user.data().remove(n));
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("Kon LuckPerms group niet verwijderen voor " + uuid + ": " + t.getMessage());
        }
    }
}
