package it.tdevelopments.ffapure.command;

import it.tdevelopments.ffapure.FFAPlugin;
import it.tdevelopments.ffapure.model.FFAMode;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class KitCommand implements CommandExecutor, TabCompleter {

    private final FFAPlugin plugin;

    public KitCommand(FFAPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getConfigManager().send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("ffa.kit")) {
            plugin.getConfigManager().send(player, "no-permission");
            return true;
        }

        if (!plugin.getModeManager().canUseFFACommandsHere(player)) {
            plugin.getConfigManager().send(player, "ffa-command-only-in-ffa");
            return true;
        }

        FFAMode mode = args.length > 0 ? plugin.getModeManager().getMode(args[0]) : plugin.getModeManager().getModeByWorld(player.getWorld());
        if (mode == null) {
            plugin.getConfigManager().send(player, "kit-mode-required");
            return true;
        }

        plugin.getKitEditorManager().openKitBrowser(player, mode.getId());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return plugin.getModeManager().getModeIds().stream()
                .filter(id -> id.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return List.of();
    }
}
