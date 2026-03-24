package it.tdevelopments.ffapure.manager;

import it.tdevelopments.ffapure.FFAPlugin;
import it.tdevelopments.ffapure.model.FFAMode;
import it.tdevelopments.ffapure.model.PlayerStats;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;

public final class PlaceholderResolver {

    private static final Pattern MODE_COUNT_PATTERN = Pattern.compile("\\{ffa_players_([a-zA-Z0-9_-]+)}");

    private final FFAPlugin plugin;

    public PlaceholderResolver(FFAPlugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, String> create(Player player, FFAMode mode) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("player", player == null ? "" : player.getName());
        placeholders.put("mode", mode == null ? "" : mode.getId());
        placeholders.put("mode_display", mode == null ? "" : mode.getDisplayName());
        placeholders.put("world", player == null || player.getWorld() == null ? "" : player.getWorld().getName());
        if (player != null && mode != null) {
            PlayerStats stats = plugin.getStatsManager().getStats(player.getUniqueId(), mode.getId());
            placeholders.put("kills", Integer.toString(stats.getKills()));
            placeholders.put("deaths", Integer.toString(stats.getDeaths()));
            placeholders.put("kdr", String.format(Locale.US, "%.2f", stats.getKdr()));
            placeholders.put("streak", Integer.toString(plugin.getStatsManager().getCurrentStreak(player.getUniqueId(), mode.getId())));
            placeholders.put("best_streak", Integer.toString(stats.getBestKillstreak()));
            placeholders.put("selected_kit", plugin.getPlayerDataManager().getSelectedLayoutName(player.getUniqueId(), mode.getId()));
            placeholders.put("kit", plugin.getPlayerDataManager().getSelectedLayoutName(player.getUniqueId(), mode.getId()));
            placeholders.put("ffa_players", Integer.toString(plugin.getPlayerStateManager().countPlayersInMode(mode.getId())));
        } else {
            placeholders.put("kills", "0");
            placeholders.put("deaths", "0");
            placeholders.put("kdr", "0.00");
            placeholders.put("streak", "0");
            placeholders.put("best_streak", "0");
            placeholders.put("selected_kit", "");
            placeholders.put("kit", "");
            placeholders.put("ffa_players", "0");
        }
        return placeholders;
    }

    public String resolve(String input, Player player, FFAMode mode, Map<String, String> extra) {
        String output = input;
        Map<String, String> merged = new LinkedHashMap<>(create(player, mode));
        if (extra != null) {
            merged.putAll(extra);
        }
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", entry.getValue());
            output = output.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        Matcher matcher = MODE_COUNT_PATTERN.matcher(output);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String modeId = matcher.group(1);
            String replacement = Integer.toString(plugin.getPlayerStateManager().countPlayersInMode(modeId));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
