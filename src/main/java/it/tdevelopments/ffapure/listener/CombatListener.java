package it.tdevelopments.ffapure.listener;

import it.tdevelopments.ffapure.FFAPlugin;
import it.tdevelopments.ffapure.model.FFAMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class CombatListener implements Listener {

    private final FFAPlugin plugin;

    public CombatListener(FFAPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player damager = resolveDamager(event.getDamager());
        FFAMode victimMode = plugin.getModeManager().getModeByWorld(victim.getWorld());
        if (victimMode == null) {
            return;
        }
        if (damager == null) {
            return;
        }
        FFAMode damagerMode = plugin.getModeManager().getModeByWorld(damager.getWorld());
        if (damagerMode == null || !damagerMode.getId().equalsIgnoreCase(victimMode.getId())) {
            event.setCancelled(true);
            return;
        }
        if (!plugin.getPlayerStateManager().isPlayerInFFA(victim) || !plugin.getPlayerStateManager().isPlayerInFFA(damager)) {
            event.setCancelled(true);
            return;
        }
        if (plugin.getPlayerStateManager().hasSpawnProtection(victim) || plugin.getPlayerStateManager().hasSpawnProtection(damager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!plugin.getPlayerStateManager().isPlayerInFFA(event.getPlayer())) {
            return;
        }
        if (plugin.getConfig().getBoolean("anti-item-drop", true)) {
            event.setCancelled(true);
        }
    }

    private Player resolveDamager(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
