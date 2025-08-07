package com.roozie.roozieplugin.emotes;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class EmoteManager {
    private final File emoteFile;
    private final FileConfiguration emoteConfig;
    private final JavaPlugin plugin;

    public EmoteManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.emoteFile = new File(plugin.getDataFolder(), "emotes.yml");

        if (!emoteFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                emoteFile.createNewFile();
                plugin.saveResource("emotes.yml", false);
            } catch (IOException e) {
                Bukkit.getLogger().severe("Kon com.roozie.roozieplugin.Namenweg.Namenweg.emotes.yml niet aanmaken!");
                e.printStackTrace();
            }
        }

        this.emoteConfig = YamlConfiguration.loadConfiguration(emoteFile);
    }

    public boolean handleCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Alleen spelers kunnen deze command gebruiken.");
            return true;
        }

        Player speler = (Player) sender;

        if (command.getName().equalsIgnoreCase("emote")) {
            if (args.length == 0) {
                speler.sendMessage("Gebruik: /emote <naam>");
                return true;
            }

            String emoteName = args[0].toLowerCase();
            performEmote(speler, emoteName);
            return true;
        }

        return false;
    }

    public void performEmote(Player speler, String emoteName) {
        String path = "com.roozie.roozieplugin.Namenweg.Namenweg.emotes." + emoteName;
        if (!emoteConfig.contains(path)) {
            speler.sendMessage("❌ Deze emote bestaat niet.");
            return;
        }

        // ✅ Message
        String message = emoteConfig.getString(path + ".message", "");
        if (!message.isEmpty()) {
            speler.sendMessage("💬 " + message.replace("{player}", speler.getName()));
        }

        // ✅ Sound
        if (emoteConfig.contains(path + ".sound")) {
            try {
                String soundName = emoteConfig.getString(path + ".sound");
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                speler.playSound(speler.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                speler.sendMessage("⚠️ Onbekend geluid voor emote: " + emoteName);
            }
        }

        // ✅ Actions
        List<?> actions = emoteConfig.getList(path + ".actions");
        if (actions != null) {
            performActionsFromConfig(speler, actions);
        }

        // ✅ Duration
        int duration = emoteConfig.getInt(path + ".duration", 0);
        if (duration > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                            speler.sendMessage("⏳ Emote '" + emoteName + "' is voorbij."),
                    duration * 20L
            );
        }
    }

    private void performActionsFromConfig(Player speler, List<?> actions) {
        for (Object obj : actions) {
            if (obj instanceof String) {
                performLegacyAction(speler, (String) obj);
            } else if (obj instanceof Map) {
                Map<?, ?> actionMap = (Map<?, ?>) obj;

                Object nameObj = actionMap.get("name");
                if (nameObj == null) continue;
                String name = nameObj.toString();

                Object typeObj = actionMap.get("type");
                String type = (typeObj != null ? typeObj.toString() : "message").toLowerCase();

                switch (type) {
                    case "message":
                        Object msgObj = actionMap.get("message");
                        String msg = (msgObj != null ? msgObj.toString() : "* Voert een actie uit.");
                        speler.sendMessage(msg);
                        break;

                    case "bukkit_action":
                        if (name.equalsIgnoreCase("SWING_ARM")) {
                            speler.swingMainHand();
                        }
                        // Voeg hier meer native acties toe indien nodig
                        break;

                    default:
                        speler.sendMessage("⚠️ Onbekend actietype: " + type);
                        break;
                }
            }
        }
    }

    private void performLegacyAction(Player speler, String action) {
        if (action.equalsIgnoreCase("SWING_ARM")) {
            speler.swingMainHand();
        } else {
            speler.sendMessage("* " + action.toLowerCase().replace("_", " "));
        }
    }
}
