package com.roozie.roozieplugin.Namenweg;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ToggleNameCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Alleen spelers kunnen dit gebruiken.");
            return true;
        }

        boolean currentlyHidden = NameVisibilityManager.isNameHidden(player);
        boolean newStatus = !currentlyHidden;

        NameVisibilityManager.setNameHidden(player, newStatus);
        NameVisibilityManager.applyNameVisibility(player);

        player.sendMessage("Namenweergave is nu " + (newStatus ? "uitgeschakeld" : "ingeschakeld") + ".");
        return true;
    }
}
