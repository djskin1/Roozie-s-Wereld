package com.roozie.roozieplugin.teams;

import com.roozie.roozieplugin.RooziesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TeamManager {

    private final RooziesPlugin plugin;
    private final File teamsFile;
    private final FileConfiguration teamsConfig;

    // Pending invites: targetUUID -> teamName
    private final Map<UUID, String> invites = new HashMap<>();

    public TeamManager(RooziesPlugin plugin) {
        this.plugin = plugin;
        this.teamsFile = plugin.getTeamsFile();
        this.teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);
    }

    /* ===== Storage helpers ===== */

    private void save() {
        try { teamsConfig.save(teamsFile); }
        catch (IOException e) { plugin.getLogger().severe("Kon teams.yml niet opslaan"); e.printStackTrace(); }
    }

    private String findTeamByOwner(UUID owner) {
        for (String team : teamsConfig.getConfigurationSection("teams").getKeys(false)) {
            String o = teamsConfig.getString("teams." + team + ".owner");
            if (o != null && o.equalsIgnoreCase(owner.toString())) return team;
        }
        return null;
    }

    private String findTeamByMember(UUID member) {
        if (!teamsConfig.isConfigurationSection("teams")) return null;
        for (String team : teamsConfig.getConfigurationSection("teams").getKeys(false)) {
            List<String> members = teamsConfig.getStringList("teams." + team + ".members");
            if (members.stream().anyMatch(s -> s.equalsIgnoreCase(member.toString()))) return team;
        }
        return null;
    }

    public boolean isOwner(UUID uuid) {
        if (!teamsConfig.isConfigurationSection("teams")) return false;
        return findTeamByOwner(uuid) != null;
    }

    public boolean isInTeam(UUID uuid) {
        return findTeamByMember(uuid) != null || isOwner(uuid);
    }

    /* ===== Commands ===== */

    public boolean handleCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("team")) return false;
        if (!(sender instanceof Player)) { sender.sendMessage("§cAlleen spelers."); return true; }

        Player p = (Player) sender;
        if (args.length == 0) {
            sender.sendMessage("§e/team create <naam>, /team invite <speler>, /team accept, /team leave, /team disband, /team info [naam]");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create":
                if (args.length < 2) { p.sendMessage("§cGebruik: /team create <naam>"); return true; }
                createTeam(p, args[1]);
                return true;
            case "invite":
                if (args.length < 2) { p.sendMessage("§cGebruik: /team invite <speler>"); return true; }
                invite(p, args[1]);
                return true;
            case "accept":
                accept(p);
                return true;
            case "leave":
                leave(p);
                return true;
            case "disband":
                disband(p);
                return true;
            case "info":
                info(p, args.length >= 2 ? args[1] : null);
                return true;
            default:
                p.sendMessage("§e/team create <naam>, /team invite <speler>, /team accept, /team leave, /team disband, /team info [naam]");
                return true;
        }
    }

    /* ===== Operations ===== */

    private void createTeam(Player owner, String name) {
        if (isInTeam(owner.getUniqueId())) { owner.sendMessage("§cJe zit al in een team."); return; }
        if (name.length() < 3 || name.length() > 16) { owner.sendMessage("§cNaam moet 3-16 chars zijn."); return; }
        if (!name.matches("[A-Za-z0-9_]+")) { owner.sendMessage("§cAlleen letters, cijfers en _."); return; }

        if (!teamsConfig.isConfigurationSection("teams"))
            teamsConfig.createSection("teams");

        if (teamsConfig.isConfigurationSection("teams." + name)) {
            owner.sendMessage("§cTeam bestaat al.");
            return;
        }

        teamsConfig.set("teams." + name + ".owner", owner.getUniqueId().toString());
        teamsConfig.set("teams." + name + ".members", new ArrayList<>(Collections.singletonList(owner.getUniqueId().toString())));
        teamsConfig.set("teams." + name + ".created", System.currentTimeMillis());
        save();

        owner.sendMessage("§aTeam §e" + name + " §aaangemaakt! Je bent nu de owner.");
    }

    private void invite(Player owner, String targetName) {
        if (!isOwner(owner.getUniqueId())) { owner.sendMessage("§cAlleen team-owners kunnen uitnodigen."); return; }
        Player t = Bukkit.getPlayerExact(targetName);
        if (t == null) { owner.sendMessage("§cSpeler niet gevonden."); return; }
        if (isInTeam(t.getUniqueId())) { owner.sendMessage("§cDie speler zit al in een team."); return; }

        String team = findTeamByOwner(owner.getUniqueId());
        invites.put(t.getUniqueId(), team);
        owner.sendMessage("§aUitnodiging gestuurd naar §e" + t.getName());
        t.sendMessage("§eJe bent uitgenodigd voor team §6" + team + "§e. Gebruik §a/team accept§e om te joinen.");
    }

    private void accept(Player p) {
        String team = invites.remove(p.getUniqueId());
        if (team == null) { p.sendMessage("§cJe hebt geen openstaande uitnodiging."); return; }

        List<String> members = teamsConfig.getStringList("teams." + team + ".members");
        if (members.stream().anyMatch(s -> s.equalsIgnoreCase(p.getUniqueId().toString()))) {
            p.sendMessage("§cJe zit al in dat team.");
            return;
        }
        members.add(p.getUniqueId().toString());
        teamsConfig.set("teams." + team + ".members", members);
        save();

        p.sendMessage("§aJe bent gejoined bij team §e" + team);
        notifyTeam(team, "§7[§a+§7] §f" + p.getName() + " §7is gejoined.");
    }

    private void leave(Player p) {
        // Owner kan niet leaven (eerst disbanden)
        String ownTeam = findTeamByOwner(p.getUniqueId());
        if (ownTeam != null) { p.sendMessage("§cOwners kunnen niet leaven. Gebruik §e/team disband§c."); return; }

        String team = findTeamByMember(p.getUniqueId());
        if (team == null) { p.sendMessage("§cJe zit niet in een team."); return; }

        List<String> members = teamsConfig.getStringList("teams." + team + ".members");
        members.removeIf(s -> s.equalsIgnoreCase(p.getUniqueId().toString()));
        teamsConfig.set("teams." + team + ".members", members);
        save();

        p.sendMessage("§eJe hebt team §6" + team + " §everlaten.");
        notifyTeam(team, "§7[§c-§7] §f" + p.getName() + " §7heeft het team verlaten.");
    }

    private void disband(Player p) {
        String team = findTeamByOwner(p.getUniqueId());
        if (team == null) { p.sendMessage("§cJe bent geen team-owner."); return; }
        teamsConfig.set("teams." + team, null);
        save();
        p.sendMessage("§cTeam §e" + team + " §cis opgeheven.");
    }

    private void info(Player p, String teamName) {
        String team = teamName;
        if (team == null) {
            team = findTeamByMember(p.getUniqueId());
            if (team == null) team = findTeamByOwner(p.getUniqueId());
            if (team == null) { p.sendMessage("§eJe zit niet in een team."); return; }
        } else {
            if (!teamsConfig.isConfigurationSection("teams." + team)) { p.sendMessage("§cTeam bestaat niet."); return; }
        }

        String owner = teamsConfig.getString("teams." + team + ".owner");
        List<String> members = teamsConfig.getStringList("teams." + team + ".members");
        p.sendMessage("§6Team: §e" + team);
        p.sendMessage("§6Owner: §f" + nameOf(owner));
        p.sendMessage("§6Leden: §f" + String.join(", ", namesOf(members)));
    }

    private void notifyTeam(String team, String msg) {
        List<String> members = teamsConfig.getStringList("teams." + team + ".members");
        for (String s : members) {
            try {
                UUID u = UUID.fromString(s);
                Player p = Bukkit.getPlayer(u);
                if (p != null && p.isOnline()) p.sendMessage(msg);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private String nameOf(String uuid) {
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
            return op.getName() != null ? op.getName() : uuid;
        } catch (IllegalArgumentException e) { return uuid; }
    }

    private List<String> namesOf(List<String> uuids) {
        List<String> list = new ArrayList<>();
        for (String s : uuids) list.add(nameOf(s));
        return list;
    }
}
