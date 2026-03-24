package it.tdevelopments.ffapure.listener;

import it.tdevelopments.ffapure.FFAPlugin;
import java.util.List;
import java.util.Locale;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class PlayerCommandListener implements Listener {

    private final FFAPlugin plugin;

    public PlayerCommandListener(FFAPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String root = normalize(event.getMessage());
        if (root.isEmpty()) {
            return;
        }

        List<String> autoLeave = plugin.getConfig().getStringList("auto-leave-commands");
        if (plugin.getPlayerStateManager().isPlayerInFFA(player) && autoLeave.stream().map(this::normalize).anyMatch(root::equalsIgnoreCase)) {
            plugin.leaveMode(player, false, true, false, false);
            return;
        }

        if (!plugin.getPlayerStateManager().isPlayerInFFA(player)) {
            return;
        }

        for (String blocked : plugin.getConfig().getStringList("blocked-commands")) {
            if (root.equalsIgnoreCase(normalize(blocked))) {
                event.setCancelled(true);
                plugin.getConfigManager().send(player, "blocked-command");
                return;
            }
        }
    }

    private String normalize(String raw) {
        String sanitized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (sanitized.startsWith("/")) {
            sanitized = sanitized.substring(1);
        }
        int space = sanitized.indexOf(' ');
        return space >= 0 ? sanitized.substring(0, space) : sanitized;
    }
}
