package it.tdevelopments.ffapure.manager;

import it.tdevelopments.ffapure.FFAPlugin;
import it.tdevelopments.ffapure.model.PlayerStats;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class StatsManager {

    private final FFAPlugin plugin;
    private final Map<String, Map<UUID, Integer>> currentStreaks = new LinkedHashMap<>();

    public StatsManager(FFAPlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerStats getStats(UUID playerId, String modeId) {
        return plugin.getPlayerDataManager().getStats(playerId, modeId);
    }

    public int registerKill(UUID killerId, String modeId) {
        PlayerStats stats = getStats(killerId, modeId);
        stats.addKill();
        int streak = currentStreaks.computeIfAbsent(modeId.toLowerCase(), ignored -> new LinkedHashMap<>())
            .merge(killerId, 1, Integer::sum);
        if (streak > stats.getBestKillstreak()) {
            stats.setBestKillstreak(streak);
        }
        plugin.getPlayerDataManager().save(killerId);
        return streak;
    }

    public void registerDeath(UUID playerId, String modeId) {
        getStats(playerId, modeId).addDeath();
        resetCurrentStreak(playerId, modeId);
        plugin.getPlayerDataManager().save(playerId);
    }

    public int getCurrentStreak(UUID playerId, String modeId) {
        return currentStreaks.getOrDefault(modeId.toLowerCase(), Map.of()).getOrDefault(playerId, 0);
    }

    public void resetCurrentStreak(UUID playerId, String modeId) {
        currentStreaks.computeIfAbsent(modeId.toLowerCase(), ignored -> new LinkedHashMap<>()).put(playerId, 0);
    }

    public List<Map.Entry<UUID, PlayerStats>> topByKills(String modeId, int limit) {
        return plugin.getPlayerDataManager().getOrLoadMap().entrySet().stream()
            .map(entry -> Map.entry(entry.getKey(), plugin.getPlayerDataManager().getStats(entry.getKey(), modeId)))
            .sorted(Comparator.comparingInt((Map.Entry<UUID, PlayerStats> entry) -> entry.getValue().getKills()).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    public List<Map.Entry<UUID, PlayerStats>> topByStreak(String modeId, int limit) {
        return plugin.getPlayerDataManager().getOrLoadMap().entrySet().stream()
            .map(entry -> Map.entry(entry.getKey(), plugin.getPlayerDataManager().getStats(entry.getKey(), modeId)))
            .sorted(Comparator.comparingInt((Map.Entry<UUID, PlayerStats> entry) -> entry.getValue().getBestKillstreak()).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }
}
