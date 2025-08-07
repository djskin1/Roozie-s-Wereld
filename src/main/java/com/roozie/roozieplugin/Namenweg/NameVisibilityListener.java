package com.roozie.roozieplugin.Namenweg;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class NameVisibilityListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        NameVisibilityManager.applyNameVisibility(event.getPlayer());
    }
}
