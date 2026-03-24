package it.tdevelopments.ffapure.listener;

import it.tdevelopments.ffapure.FFAPlugin;
import it.tdevelopments.ffapure.model.FFAMode;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class PlayerLifecycleListener implements Listener {

    private final FFAPlugin plugin;

    public PlayerLifecycleListener(FFAPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getPlayerDataManager().getOrLoad(event.getPlayer().getUniqueId());
        plugin.getPlayerStateManager().syncWorldState(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPlayerDataManager().save(event.getPlayer().getUniqueId());
        plugin.getKitEditorManager().clearPlayerRuntimeState(event.getPlayer(), false);
        plugin.getPlayerStateManager().clearActiveState(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        FFAMode mode = plugin.getModeManager().getModeByWorld(victim.getWorld());
        if (mode == null) {
            return;
        }
        event.setDeathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);
        Player killer = victim.getKiller();
        plugin.getStatsManager().registerDeath(victim.getUniqueId(), mode.getId());
        if (killer != null && plugin.getPlayerStateManager().isPlayerInFFA(killer)
            && mode.getId().equalsIgnoreCase(plugin.getPlayerStateManager().getModeId(killer.getUniqueId()))
            && !plugin.getPlayerStateManager().isFarmKill(killer.getUniqueId(), victim.getUniqueId())) {
            int streak = plugin.getStatsManager().registerKill(killer.getUniqueId(), mode.getId());
            plugin.getConfigManager().broadcastMode(mode, "kill-message", plugin.getConfigManager().placeholders(killer, mode));
            if (streak > 0 && streak % Math.max(1, plugin.getConfig().getInt("killstreak.broadcast-every", 5)) == 0) {
                plugin.getConfigManager().broadcastMode(mode, "killstreak-message", plugin.getConfigManager().placeholders(killer, mode));
            }
        }
        plugin.prepareRespawn(victim, mode);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        FFAMode mode = plugin.getModeManager().getModeByWorld(player.getWorld());
        if (mode == null) {
            mode = plugin.getPlayerStateManager().getMode(player.getUniqueId());
        }
        if (mode == null) {
            return;
        }
        FFAMode finalMode = mode;
        if (plugin.getSpawnManager().getFFASpawn(finalMode) != null) {
            event.setRespawnLocation(plugin.getSpawnManager().getFFASpawn(finalMode));
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (finalMode.isClearInventoryOnRespawn()) {
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                player.getInventory().setItemInOffHand(null);
            }
            if (finalMode.isHealOnRespawn() && player.getAttribute(Attribute.MAX_HEALTH) != null) {
                player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
                player.setFoodLevel(20);
                player.setSaturation(20f);
            }
            plugin.getPlayerStateManager().applySpawnProtection(player, finalMode);
            if (finalMode.isGiveLastSelectedKitOnRespawn()) {
                plugin.applySelectedLayout(player, finalMode);
            }
            if (finalMode.isOpenKitOnRespawn()) {
                plugin.getKitEditorManager().openKitBrowser(player, finalMode.getId());
            }
        });
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        plugin.handleWorldChange(event.getPlayer(), event.getFrom(), event.getPlayer().getWorld());
    }
}
