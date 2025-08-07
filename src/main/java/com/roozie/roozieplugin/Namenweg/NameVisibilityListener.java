package com.roozie.roozieplugin.Namenweg;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;

public class NameVisibilityListener implements Listener {

    private final NameVisibilityManager visibilityManager;

    public NameVisibilityListener(NameVisibilityManager visibilityManager) {
        this.visibilityManager = visibilityManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player speler = event.getPlayer();

        // Als deze speler ervoor gekozen heeft om geen nametags te zien
        if (visibilityManager.isHidingNametags(speler)) {
            visibilityManager.hideOthersNametagsFor(speler);
        } else {
            visibilityManager.resetScoreboard(speler);
        }
    }
}
