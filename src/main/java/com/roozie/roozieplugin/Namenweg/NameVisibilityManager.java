package com.roozie.roozieplugin.Namenweg;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class NameVisibilityManager implements Listener {

    private final Set<UUID> playersHidingNametags = new HashSet<>();

    public void hideOthersNametagsFor(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard(); // Nieuw scoreboard per speler
        Team team = scoreboard.registerNewTeam("hidden");

        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.setCanSeeFriendlyInvisibles(false);
        team.setAllowFriendlyFire(true);

        // Voeg alle andere spelers toe aan deze team
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                team.addEntry(other.getName());
            }
        }

        player.setScoreboard(scoreboard);
        playersHidingNametags.add(player.getUniqueId());
    }

    public void resetScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        playersHidingNametags.remove(player.getUniqueId());
    }

    public boolean isHidingNametags(Player player) {
        return playersHidingNametags.contains(player.getUniqueId());
    }

    public boolean toggleNameVisibility(Player player) {
        if (isHidingNametags(player)) {
            resetScoreboard(player);
            return true; // Naamtags zijn nu zichtbaar
        } else {
            hideOthersNametagsFor(player);
            return false; // Naamtags zijn nu verborgen
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();

        // Voor alle spelers die anderen verbergen → voeg de nieuwe speler ook toe aan hun team
        for (UUID uuid : playersHidingNametags) {
            Player hider = Bukkit.getPlayer(uuid);
            if (hider != null && hider.isOnline()) {
                Scoreboard scoreboard = hider.getScoreboard();
                Team team = scoreboard.getTeam("hidden");
                if (team != null) {
                    team.addEntry(joiner.getName());
                }
            }
        }
    }
}
