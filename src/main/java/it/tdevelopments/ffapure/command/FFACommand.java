package it.tdevelopments.ffapure.command;

import it.tdevelopments.ffapure.FFAPlugin;
import it.tdevelopments.ffapure.manager.ModeManager.ModeOperationResult;
import it.tdevelopments.ffapure.model.FFAMode;
import it.tdevelopments.ffapure.model.PlayerStats;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class FFACommand implements CommandExecutor, TabCompleter {

    private final FFAPlugin plugin;

    public FFACommand(FFAPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args.length == 0) {
                sendHelp(sender);
                return true;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "join" -> handleJoin(sender, args);
                case "leave" -> handleLeave(sender);
                case "spawn" -> handleSpawn(sender, args);
                case "modes", "listmodes" -> handleModes(sender);
                case "mode" -> handleCurrentMode(sender);
                case "stats" -> handleStats(sender, args);
                case "reload" -> handleReload(sender);
                case "setspawn" -> handleSetSpawn(sender, args);
                case "setlobby" -> handleSetLobby(sender, args);
                case "createmode" -> handleCreateMode(sender, args);
                case "deletemode" -> handleDeleteMode(sender, args);
                case "openkiteditor" -> handleOpenKitEditor(sender, args);
                case "debugmode" -> handleDebugMode(sender);
                case "help" -> sendHelp(sender);
                default -> sendHelp(sender);
            }
        } catch (Exception exception) {
            plugin.getLogger().severe("Unhandled /ffa command error: " + exception.getMessage());
            plugin.getConfigManager().send(sender, "internal-error");
        }
        return true;
    }

    private void handleJoin(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (!player.hasPermission("ffa.join")) {
            plugin.getConfigManager().send(player, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.getConfigManager().send(player, "usage-join");
            return;
        }
        plugin.joinMode(player, args[1], true);
    }

    private void handleLeave(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        plugin.leaveMode(player, true);
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (!plugin.getModeManager().canUseFFACommandsHere(player)) {
            plugin.getConfigManager().send(player, "ffa-command-only-in-ffa");
            return;
        }
        if (args.length < 2) {
            plugin.getConfigManager().send(player, "usage-spawn");
            return;
        }
        FFAMode mode = plugin.getModeManager().getMode(args[1]);
        if (mode == null || plugin.getSpawnManager().getFFASpawn(mode) == null) {
            plugin.getConfigManager().send(player, "mode-not-found");
            return;
        }
        player.teleport(plugin.getSpawnManager().getFFASpawn(mode));
        plugin.getConfigManager().send(player, "teleported-spawn", plugin.getConfigManager().placeholders(player, mode));
    }

    private void handleModes(CommandSender sender) {
        if (sender instanceof Player player && !plugin.getModeManager().canUseFFACommandsHere(player)) {
            plugin.getConfigManager().send(player, "ffa-command-only-in-ffa");
            return;
        }
        plugin.getConfigManager().send(sender, "modes-header");
        for (FFAMode mode : plugin.getModeManager().getModes()) {
            Map<String, String> placeholders = new LinkedHashMap<>();
            placeholders.put("mode", mode.getId());
            placeholders.put("mode_display", mode.getDisplayName());
            placeholders.put("ffa_players_mode", Integer.toString(plugin.getPlayerStateManager().countPlayersInMode(mode.getId())));
            plugin.getConfigManager().send(sender, "mode-entry", placeholders);
        }
    }

    private void handleCurrentMode(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (!plugin.getModeManager().canUseFFACommandsHere(player)) {
            plugin.getConfigManager().send(player, "ffa-command-only-in-ffa");
            return;
        }
        FFAMode mode = plugin.getPlayerStateManager().getMode(player.getUniqueId());
        if (mode == null) {
            plugin.getConfigManager().send(player, "not-in-mode");
            return;
        }
        plugin.getConfigManager().send(player, "current-mode", plugin.getConfigManager().placeholders(player, mode));
    }

    private void handleStats(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (!plugin.getModeManager().canUseFFACommandsHere(player)) {
            plugin.getConfigManager().send(player, "ffa-command-only-in-ffa");
            return;
        }
        FFAMode mode = args.length > 1 ? plugin.getModeManager().getMode(args[1]) : plugin.getPlayerStateManager().getMode(player.getUniqueId());
        if (mode == null) {
            plugin.getConfigManager().send(player, "mode-not-found");
            return;
        }
        PlayerStats stats = plugin.getStatsManager().getStats(player.getUniqueId(), mode.getId());
        plugin.getConfigManager().send(player, "stats-header", plugin.getConfigManager().placeholders(player, mode));
        plugin.getConfigManager().send(player, "stats-kills", Map.of("kills", Integer.toString(stats.getKills())));
        plugin.getConfigManager().send(player, "stats-deaths", Map.of("deaths", Integer.toString(stats.getDeaths())));
        plugin.getConfigManager().send(player, "stats-kdr", Map.of("kdr", String.format(Locale.US, "%.2f", stats.getKdr())));
        plugin.getConfigManager().send(player, "stats-streak", Map.of("streak", Integer.toString(stats.getBestKillstreak())));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("ffa.admin.reload")) {
            plugin.getConfigManager().send(sender, "no-permission");
            return;
        }
        plugin.reloadAll();
        plugin.getConfigManager().send(sender, "reload-complete");
    }

    private void handleSetSpawn(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (!player.hasPermission("ffa.admin.spawn")) {
            plugin.getConfigManager().send(player, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.getConfigManager().send(player, "usage-setspawn");
            return;
        }
        if (plugin.getSpawnManager().setFFASpawn(args[1], player.getLocation())) {
            plugin.getConfigManager().send(player, "spawn-set", Map.of("mode", args[1], "mode_display", args[1]));
            return;
        }
        plugin.getConfigManager().send(player, "mode-not-found");
    }

    private void handleSetLobby(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (!player.hasPermission("ffa.admin.spawn")) {
            plugin.getConfigManager().send(player, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.getConfigManager().send(player, "usage-setlobby");
            return;
        }
        if (plugin.getSpawnManager().setLobby(args[1], player.getLocation())) {
            plugin.getConfigManager().send(player, "lobby-set", Map.of("mode", args[1], "mode_display", args[1]));
            return;
        }
        plugin.getConfigManager().send(player, "mode-not-found");
    }

    private void handleCreateMode(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (!player.hasPermission("ffa.admin.mode")) {
            plugin.getConfigManager().send(player, "no-permission");
            return;
        }
        if (plugin.getModeManager().isCreationBlockedWorld(player.getWorld())) {
            plugin.getConfigManager().send(player, "mode-create-blocked-world");
            return;
        }
        if (args.length < 2) {
            plugin.getConfigManager().send(player, "usage-createmode");
            return;
        }
        ModeOperationResult result = plugin.getModeManager().createMode(args[1], player.getLocation());
        if (!result.success()) {
            plugin.getConfigManager().send(player, result.messageKey(), Map.of("mode", result.modeId(), "mode_display", result.modeId()));
            return;
        }
        plugin.reloadAll();
        plugin.getConfigManager().send(player, "mode-created", Map.of("mode", result.modeId(), "mode_display", ItemDisplay(result)));
    }

    private void handleDeleteMode(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ffa.admin.mode")) {
            plugin.getConfigManager().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.getConfigManager().send(sender, "usage-deletemode");
            return;
        }
        ModeOperationResult result = plugin.getModeManager().deleteMode(args[1]);
        if (!result.success()) {
            plugin.getConfigManager().send(sender, result.messageKey(), Map.of("mode", result.modeId(), "mode_display", result.modeId()));
            return;
        }
        plugin.reloadAll();
        plugin.getConfigManager().send(sender, "mode-deleted", Map.of("mode", result.modeId(), "mode_display", ItemDisplay(result)));
    }

    private void handleOpenKitEditor(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ffa.admin.kit")) {
            plugin.getConfigManager().send(sender, "no-permission");
            return;
        }
        if (args.length < 4) {
            plugin.getConfigManager().send(sender, "usage-openkiteditor");
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            plugin.getConfigManager().send(sender, "player-not-found");
            return;
        }
        FFAMode mode = plugin.getModeManager().getMode(args[2]);
        if (mode == null) {
            plugin.getConfigManager().send(sender, "mode-not-found");
            return;
        }
        try {
            int slot = Integer.parseInt(args[3]);
            if (slot < 1 || slot > mode.getLayoutSlots()) {
                plugin.getConfigManager().send(sender, "invalid-slot");
                return;
            }
            plugin.getKitEditorManager().openEditor(target, mode.getId(), slot);
        } catch (NumberFormatException exception) {
            plugin.getConfigManager().send(sender, "invalid-slot");
        }
    }

    private void handleDebugMode(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        FFAMode mode = plugin.getModeManager().getModeByWorld(player.getWorld());
        plugin.getConfigManager().send(player, "debug-mode", Map.of(
            "mode", mode == null ? "none" : mode.getId(),
            "mode_display", mode == null ? "none" : mode.getDisplayName()
        ));
    }

    private void sendHelp(CommandSender sender) {
        plugin.getConfigManager().send(sender, "help-header");
        plugin.getConfigManager().send(sender, "help-join");
        plugin.getConfigManager().send(sender, "help-spawn");
        plugin.getConfigManager().send(sender, "help-stats");
        plugin.getConfigManager().send(sender, "help-modes");
        plugin.getConfigManager().send(sender, "help-kit-command");
        if (sender.hasPermission("ffa.admin")) {
            plugin.getConfigManager().send(sender, "help-admin");
        }
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        plugin.getConfigManager().send(sender, "player-only");
        return null;
    }

    private String ItemDisplay(ModeOperationResult result) {
        return result.mode() == null ? result.modeId() : result.mode().getDisplayName();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("join", "leave", "spawn", "modes", "mode", "stats", "help", "reload", "setspawn", "setlobby", "createmode", "deletemode", "listmodes", "openkiteditor", "debugmode");
        }
        if (args.length == 2 && Arrays.asList("join", "spawn", "stats", "setspawn", "setlobby", "deletemode").contains(args[0].toLowerCase(Locale.ROOT))) {
            return plugin.getModeManager().getModeIds().stream()
                .filter(id -> id.startsWith(args[1].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
