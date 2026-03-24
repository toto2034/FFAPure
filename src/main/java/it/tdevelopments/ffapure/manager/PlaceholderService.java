package it.tdevelopments.ffapure.manager;

import it.tdevelopments.ffapure.FFAPlugin;
import it.tdevelopments.ffapure.model.FFAMode;
import it.tdevelopments.ffapure.model.PlayerStats;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class PlaceholderService {

    private final FFAPlugin plugin;

    public PlaceholderService(FFAPlugin plugin) {
        this.plugin = plugin;
    }

    public String resolveExternal(Player player, String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return "";
        }

        int separator = identifier.indexOf('_');
        if (separator <= 0 || separator >= identifier.length() - 1) {
            return "";
        }

        String stat = identifier.substring(0, separator).toLowerCase(Locale.ROOT);
        String modeId = identifier.substring(separator + 1).toLowerCase(Locale.ROOT);
        FFAMode mode = plugin.getModeManager().getMode(modeId);
        if (mode == null) {
            return "0";
        }

        return switch (stat) {
            case "players" -> Integer.toString(plugin.getPlayerStateManager().countPlayersInMode(mode.getId()));
            case "kill", "kills" -> Integer.toString(stats(player, mode).getKills());
            case "death", "deaths" -> Integer.toString(stats(player, mode).getDeaths());
            case "kd", "kdr" -> String.format(Locale.US, "%.2f", stats(player, mode).getKdr());
            case "streak" -> Integer.toString(currentStreak(player, mode));
            case "beststreak", "best_streak" -> Integer.toString(stats(player, mode).getBestKillstreak());
            default -> "";
        };
    }

    private PlayerStats stats(Player player, FFAMode mode) {
        if (player == null || mode == null) {
            return new PlayerStats(new UUID(0L, 0L));
        }
        return plugin.getStatsManager().getStats(player.getUniqueId(), mode.getId());
    }

    private int currentStreak(Player player, FFAMode mode) {
        if (player == null || mode == null) {
            return 0;
        }
        return plugin.getStatsManager().getCurrentStreak(player.getUniqueId(), mode.getId());
    }
}
