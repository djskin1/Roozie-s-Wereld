package com.roozie.roozieplugin.roles;

import com.roozie.roozieplugin.RooziesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
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
import net.luckperms.api.node.types.InheritanceNode;

import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

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
    private static final String STAFF_PREFIX = "group.s_"; // rollen met dit prefix kun je niet resetten

    public RoleManager(RooziesPlugin plugin) {
        this.plugin = plugin;
        createRolesConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /* roles.yml */
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
        try { rolesConfig.save(rolesFile); }
        catch (IOException e) { plugin.getLogger().severe("Fout bij opslaan van roles.yml"); e.printStackTrace(); }
    }

    /* Events */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player speler = event.getPlayer();
        try {
            if (isFloodgateEnabled() && FloodgateApi.getInstance().isFloodgatePlayer(speler.getUniqueId()))
                speler.sendMessage(color("&eWelkom Bedrock speler!"));
        } catch (Throwable ignored) {}

        String storageKey = getStorageKey(speler);
        String rol = getExistingRoleAndMigrate(speler, storageKey);

        boolean herkiezen = speler.hasPermission("roozie.rol.herkiezen");
        if (rol == null || herkiezen) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> openRoleMenu(speler), 20L);
            return;
        }

        spelerRollen.put(speler.getUniqueId(), rol);
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
        if (rolSectie == null) { speler.sendMessage(color("&cDeze rol bestaat niet (meer).")); return; }

        String vereistePermissie = rolSectie.getString("permissie", "");
        if (vereistePermissie != null && !vereistePermissie.isEmpty() && !speler.hasPermission(vereistePermissie)) {
            speler.sendMessage(color("&cJe hebt geen permissie voor deze rol."));
            return;
        }

        String lpGroup = rolSectie.getString("lp_group", gekozenRol);

        // opslaan
        String storageKey = getStorageKey(speler);
        rolesConfig.set(storageKey, gekozenRol);
        saveRolesConfig();

        spelerRollen.put(speler.getUniqueId(), gekozenRol);

        // LuckPerms (Java UUID als gelinkt)
        applyLuckPermsGroup(resolveLuckPermsTargetUuid(speler), lpGroup);

        speler.sendMessage(color("&aJe hebt gekozen voor de rol: &e" + gekozenRol));
        speler.closeInventory();
    }

    /* Publieke API */
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

        if (items.isEmpty()) { speler.sendMessage(color("&cEr zijn geen rollen die jij kunt kiezen.")); return; }

        int size = calcInventorySize(items.size());
        Inventory menu = Bukkit.createInventory(null, size, MENU_TITLE);
        for (int i = 0; i < items.size() && i < size; i++) menu.setItem(i, items.get(i));
        speler.openInventory(menu);
    }

    public void resetRol(Player speler) {
        String storageKey = getStorageKey(speler);

        if (!rolesConfig.contains(storageKey)) {
            speler.sendMessage(color("&eJe hebt nog geen rol gekozen."));
            return;
        }

        String currentRole = rolesConfig.getString(storageKey, null);
        if (isProtectedRoleName(currentRole)) {
            speler.sendMessage(color("&cDeze rol kan niet gereset worden."));
            return;
        }

        // terug naar default group (LP) + opslag legen
        setLuckPermsToDefault(resolveLuckPermsTargetUuid(speler));
        rolesConfig.set(storageKey, null);
        saveRolesConfig();

        speler.sendMessage(color("&aJe bent teruggezet naar de standaardgroep. Kies opnieuw..."));
        openRoleMenu(speler);
    }

    public boolean handleCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("resetrol")) {
            if (!(sender instanceof Player)) { sender.sendMessage(color("&cAlleen spelers kunnen hun eigen rol resetten.")); return true; }
            resetRol((Player) sender);
            return true;
        }
        if (command.getName().equalsIgnoreCase("rolmenu")) {
            if (!(sender instanceof Player)) { sender.sendMessage(color("&cAlleen spelers kunnen dit menu openen.")); return true; }
            openRoleMenu((Player) sender);
            return true;
        }
        return false;
    }

    /* Helpers */
    private boolean isProtectedRoleName(String roleName) {
        return roleName != null && roleName.toLowerCase(Locale.ROOT).startsWith(STAFF_PREFIX);
    }

    private String getStorageKey(Player speler) {
        UUID uuid = speler.getUniqueId();
        if (isFloodgateEnabled() && FloodgateApi.getInstance().isFloodgatePlayer(uuid)) {
            try {
                FloodgatePlayer fp = FloodgateApi.getInstance().getPlayer(uuid);
                if (fp != null) {
                    try {
                        if (fp.isLinked() && fp.getLinkedPlayer() != null) {
                            UUID javaId = fp.getLinkedPlayer().getJavaUniqueId();
                            if (javaId != null) return javaId.toString();
                        }
                    } catch (Throwable ignored) {}
                    String xuid = fp.getXuid();
                    if (xuid != null && !xuid.isEmpty()) return "xuid:" + xuid;
                }
            } catch (Throwable ignored) {}
            return uuid.toString();
        }
        return uuid.toString();
    }

    private String getExistingRoleAndMigrate(Player speler, String targetKey) {
        if (rolesConfig.contains(targetKey)) return rolesConfig.getString(targetKey);
        List<String> candidateKeys = new ArrayList<>();
        candidateKeys.add(speler.getUniqueId().toString());
        if (isFloodgateEnabled()) {
            try {
                if (FloodgateApi.getInstance().isFloodgatePlayer(speler.getUniqueId())) {
                    FloodgatePlayer fp = FloodgateApi.getInstance().getPlayer(speler.getUniqueId());
                    if (fp != null) {
                        String xuid = fp.getXuid();
                        if (xuid != null && !xuid.isEmpty()) candidateKeys.add("xuid:" + xuid);
                        candidateKeys.add("floodgate:" + speler.getUniqueId());
                    }
                }
            } catch (Throwable ignored) {}
        }
        for (String key : candidateKeys) {
            if (rolesConfig.contains(key)) {
                String rol = rolesConfig.getString(key);
                rolesConfig.set(targetKey, rol);
                if (!key.equals(targetKey)) rolesConfig.set(key, null);
                saveRolesConfig();
                return rol;
            }
        }
        return null;
    }

    private ItemStack createMenuItem(Material materiaal, String naam, List<String> loreRaw, ConfigurationSection rolSectie) {
        ItemStack item = new ItemStack(materiaal);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&e" + naam));
            if (loreRaw != null && !loreRaw.isEmpty()) {
                List<String> colored = loreRaw.stream().filter(Objects::nonNull).map(this::color).collect(Collectors.toList());
                meta.setLore(colored);
            }
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
            for (Map.Entry<Enchantment, Integer> en : enchants.entrySet()) meta.addEnchant(en.getKey(), en.getValue(), true);
            if (glow && enchants.isEmpty()) meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            if (hide) meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private int calcInventorySize(int itemCount) {
        int size = ((Math.max(1, itemCount) - 1) / 9 + 1) * 9;
        return Math.min(size, 54);
    }

    private String color(String input) { return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input); }

    private boolean isFloodgateEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("floodgate")
                || Bukkit.getPluginManager().isPluginEnabled("Floodgate");
    }

    private UUID resolveLuckPermsTargetUuid(Player speler) {
        UUID uuid = speler.getUniqueId();
        if (isFloodgateEnabled()) {
            try {
                if (FloodgateApi.getInstance().isFloodgatePlayer(uuid)) {
                    FloodgatePlayer fp = FloodgateApi.getInstance().getPlayer(uuid);
                    if (fp != null) {
                        try {
                            if (fp.isLinked() && fp.getLinkedPlayer() != null) {
                                UUID javaId = fp.getLinkedPlayer().getJavaUniqueId();
                                if (javaId != null) return javaId;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        }
        return uuid;
    }

    private boolean isLuckPermsEnabled() { return Bukkit.getPluginManager().isPluginEnabled("LuckPerms"); }

    // === Reset naar default group (configurable) ===
    private String getDefaultGroupName() {
        try { return plugin.getConfig().getString("roles.default_group", "default"); }
        catch (Throwable t) { return "default"; }
    }

    private void setLuckPermsToDefault(UUID targetUuid) {
        try {
            if (!isLuckPermsEnabled()) return;
            final String defGroup = getDefaultGroupName();
            LuckPerms lp = LuckPermsProvider.get();
            lp.getUserManager().modifyUser(targetUuid, (User user) -> {
                List<InheritanceNode> existing = user.data().toCollection().stream()
                        .filter(n -> n instanceof InheritanceNode)
                        .map(n -> (InheritanceNode) n)
                        .collect(Collectors.toList());
                for (InheritanceNode n : existing) {
                    if (!n.getGroupName().equalsIgnoreCase(defGroup)) user.data().remove(n);
                }
                user.data().add(InheritanceNode.builder(defGroup).build());
                try { user.setPrimaryGroup(defGroup); } catch (Throwable ignored) {}
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("Kon speler niet naar default group zetten: " + t.getMessage());
        }
    }

    private void applyLuckPermsGroup(UUID targetUuid, String groupName) {
        try {
            if (!isLuckPermsEnabled()) return;
            LuckPerms lp = LuckPermsProvider.get();
            lp.getUserManager().modifyUser(targetUuid, (User user) -> {
                List<InheritanceNode> existing = user.data().toCollection().stream()
                        .filter(n -> n instanceof InheritanceNode)
                        .map(n -> (InheritanceNode) n)
                        .collect(Collectors.toList());
                for (InheritanceNode n : existing) user.data().remove(n);
                user.data().add(InheritanceNode.builder(groupName.toLowerCase()).build());
                try { user.setPrimaryGroup(groupName.toLowerCase()); } catch (Throwable ignored) {}
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("Kon LuckPerms group niet toepassen: " + t.getMessage());
        }
    }

    private int parseIntSafe(String s, int def) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }

    private Enchantment matchEnchantment(String id) {
        if (id == null) return null;
        try {
            Enchantment byKey = Enchantment.getByKey(NamespacedKey.minecraft(id.toLowerCase()));
            if (byKey != null) return byKey;
        } catch (Throwable ignored) {}
        return Enchantment.getByName(id.toUpperCase());
    }
}
