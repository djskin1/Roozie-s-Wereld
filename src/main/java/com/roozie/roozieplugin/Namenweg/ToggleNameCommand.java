package com.roozie.roozieplugin.Namenweg;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ToggleNameCommand implements CommandExecutor {

    private final NameVisibilityManager visibilityManager;

    public ToggleNameCommand(NameVisibilityManager visibilityManager) {
        this.visibilityManager = visibilityManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Deze command is alleen voor spelers.");
            return true;
        }

        boolean nowVisible = visibilityManager.toggleNameVisibility(player);
        player.sendMessage(ChatColor.AQUA + "Jouw zicht op naamlabels is nu: " +
                (nowVisible ? ChatColor.GREEN + "ZICHTBAAR" : ChatColor.RED + "VERBORGEN"));

        return true;
    }
}
