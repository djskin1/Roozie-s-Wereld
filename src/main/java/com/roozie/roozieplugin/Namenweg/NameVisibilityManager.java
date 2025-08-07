package com.roozie.roozieplugin.Namenweg;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class NameVisibilityManager {

    private static final Set<UUID> hiddenPlayers = new HashSet<>();
    private static final String TEAM_ID = "nametoggle_team";

    public static boolean isNameHidden(Player player) {
        return hiddenPlayers.contains(player.getUniqueId());
    }

    public static void setNameHidden(Player player, boolean hidden) {
        if (hidden) {
            hiddenPlayers.add(player.getUniqueId());
        } else {
            hiddenPlayers.remove(player.getUniqueId());
        }
    }

    public static void applyNameVisibility(Player player) {
        boolean hidden = isNameHidden(player)
                || (!hiddenPlayers.contains(player.getUniqueId())
                && Namenweg.getInstance().getPluginConfig().getBoolean("namenweg.standaard-uit", true));

        Scoreboard board = player.getScoreboard();
        Team team = board.getTeam(TEAM_ID);
        if (team == null) {
            team = board.registerNewTeam(TEAM_ID);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }

        // Verwijder speler uit alle andere teams
        for (Team t : board.getTeams()) {
            t.removePlayer(player);
        }

        if (hidden) {
            team.addPlayer(player);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        } else {
            team.removePlayer(player);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        }
    }
}
