package com.roozie.roozieplugin.roles;

import com.roozie.roozieplugin.RooziesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;

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

    private static final String MENU_TITLE = ChatColor.YELLOW + "Kies je rol";

    public RoleManager(RooziesPlugin plugin) {
        this.plugin = plugin;
        createRolesConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /* =======================
       roles.yml
       ======================= */

    private void createRolesConfig() {
        rolesFile = plugin.getRolesFile();
        if (!rolesFile.exists()) {
            try {
                if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
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

        if (!event.getView().getTitle().equals(MENU_TITLE)) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR || !clickedItem.hasItemMeta()) return;

        Player speler = (Player) who;
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String gekozenRol = ChatColor.stripColor(meta.getDisplayName());

        FileConfiguration menuConfig = YamlConfiguration.loadConfiguration(plugin.getMenuConfigFile());
        ConfigurationSection rolSectie = menuConfig.getConfigurationSection("rollen." + gekozenRol);
        if (rolSectie == null) {
            speler.sendMessage(color("&cDeze rol bestaat niet (meer)."));
            return;
        }

        String vereistePermissie = rolSectie.getString("permissie", "");
        if (vereistePermissie != null && !vereistePermissie.isEmpty() && !speler.hasPermission(vereistePermissie)) {
            speler.sendMessage(color("&cJe hebt geen permissie voor deze rol."));
            return;
        }

        String lpGroup = rolSectie.getString("lp_group", gekozenRol);

        UUID uuid = speler.getUniqueId();
        rolesConfig.set(uuid.toString(), gekozenRol);
        saveRolesConfig();
        spelerRollen.put(uuid, gekozenRol);

        applyLuckPermsGroup(uuid, lpGroup);

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

        List<ItemStack> items = new ArrayList<>();
        for (String rolNaam : rollenSectie.getKeys(false)) {
            ConfigurationSection rolSectie = rollenSectie.getConfigurationSection(rolNaam);
            if (rolSectie == null) continue;

            String vereistePermissie = rolSectie.getString("permissie", "");
            if (vereistePermissie != null && !vereistePermissie.isEmpty() && !speler.hasPermission(vereistePermissie)) {
                continue;
            }

            String materiaalStr = rolSectie.getString("materiaal", "STONE");
            Material materiaal = Material.matchMaterial(materiaalStr != null ? materiaalStr : "STONE");
            if (materiaal == null) materiaal = Material.STONE;

            List<String> loreRaw = rolSectie.isList("lore")
                    ? rolSectie.getStringList("lore")
                    : (rolSectie.contains("lore") ? Collections.singletonList(rolSectie.getString("lore", "")) : Collections.emptyList());

            ItemStack item = createMenuItem(materiaal, rolNaam, loreRaw, rolSectie);
            items.add(item);
        }

        if (items.isEmpty()) {
            speler.sendMessage(color("&cEr zijn geen rollen die jij kunt kiezen."));
            return;
        }

        int size = calcInventorySize(items.size());
        Inventory menu = Bukkit.createInventory(null, size, MENU_TITLE);

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

        removeAllLuckPermsGroups(uuid);

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

    private ItemStack createMenuItem(Material materiaal, String naam, List<String> loreRaw, ConfigurationSection rolSectie) {
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

            // ---- Enchant / glow uit config ----
            boolean glow = rolSectie.getBoolean("glow", false);
            boolean hide = rolSectie.getBoolean("hide_enchants", true);

            Map<Enchantment, Integer> enchants = new HashMap<>();
            if (rolSectie.isConfigurationSection("enchants")) {
                ConfigurationSection cs = rolSectie.getConfigurationSection("enchants");
                for (String key : cs.getKeys(false)) {
                    Enchantment e = matchEnchantment(key);
                    int lvl = cs.getInt(key, 1);
                    if (e != null) enchants.put(e, Math.max(1, lvl));
                }
            } else if (rolSectie.isList("enchants")) {
                for (String line : rolSectie.getStringList("enchants")) {
                    if (line == null || line.isEmpty()) continue;
                    String[] parts = line.split(":", 2);
                    String id = parts[0].trim();
                    int lvl = (parts.length > 1) ? parseIntSafe(parts[1].trim(), 1) : 1;
                    Enchantment e = matchEnchantment(id);
                    if (e != null) enchants.put(e, Math.max(1, lvl));
                }
            }

            // Voeg echte enchants toe
            for (Map.Entry<Enchantment, Integer> en : enchants.entrySet()) {
                meta.addEnchant(en.getKey(), en.getValue(), true);
            }

            // Alleen glow zonder echte enchants -> dummy enchant
            if (glow && enchants.isEmpty()) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            }

            if (hide) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            item.setItemMeta(meta);
        }
        return item;
    }

    private int calcInventorySize(int itemCount) {
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
        return Bukkit.getPluginManager().isPluginEnabled("floodgate")
                || Bukkit.getPluginManager().isPluginEnabled("Floodgate");
    }

    private void applyLuckPermsGroup(UUID uuid, String groupName) {
        try {
            if (!isLuckPermsEnabled()) return;
            LuckPerms lp = LuckPermsProvider.get();

            lp.getUserManager().modifyUser(uuid, (User user) -> {
                // Verwijder bestaande group-inheritances (optioneel — pas aan als je meerdere groups wil toestaan)
                List<Node> toRemove = user.data().toCollection().stream()
                        .filter(n -> n instanceof InheritanceNode)
                        .collect(Collectors.toList());
                toRemove.forEach(n -> user.data().remove(n));

                // Voeg nieuwe group inheritance toe en zet desnoods primary group
                user.data().add(InheritanceNode.builder(groupName.toLowerCase()).build());
                try { user.setPrimaryGroup(groupName.toLowerCase()); } catch (Throwable ignored) {}
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("Kon LuckPerms group niet toepassen voor " + uuid + ": " + t.getMessage());
        }
    }

    private void removeAllLuckPermsGroups(UUID uuid) {
        try {
            if (!isLuckPermsEnabled()) return;
            LuckPerms lp = LuckPermsProvider.get();

            lp.getUserManager().modifyUser(uuid, (User user) -> {
                List<Node> toRemove = user.data().toCollection().stream()
                        .filter(n -> n instanceof InheritanceNode)
                        .collect(Collectors.toList());
                toRemove.forEach(n -> user.data().remove(n));
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("Kon LuckPerms groups niet verwijderen voor " + uuid + ": " + t.getMessage());
        }
    }

    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private Enchantment matchEnchantment(String id) {
        if (id == null) return null;
        try {
            Enchantment byKey = Enchantment.getByKey(NamespacedKey.minecraft(id.toLowerCase()));
            if (byKey != null) return byKey;
        } catch (Throwable ignored) {}
        return Enchantment.getByName(id.toUpperCase());
    }
}
