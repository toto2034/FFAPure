package it.tdevelopments.ffapure.manager;

import it.tdevelopments.ffapure.FFAPlugin;
import it.tdevelopments.ffapure.model.FFAMode;
import it.tdevelopments.ffapure.model.PlayerSnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

public final class PlayerStateManager {

    private final FFAPlugin plugin;
    private final Map<UUID, ActiveState> activeStates = new HashMap<>();
    private final Map<String, Long> farmKillHistory = new HashMap<>();

    public PlayerStateManager(FFAPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isFFAWorld(World world) {
        return plugin.getModeManager().isFFAWorld(world);
    }

    public FFAMode getModeByWorld(World world) {
        return plugin.getModeManager().getModeByWorld(world);
    }

    public boolean isPlayerInsideFFAMode(Player player) {
        return player != null && activeStates.containsKey(player.getUniqueId());
    }

    public boolean isPlayerInFFA(Player player) {
        if (player == null) {
            return false;
        }
        ActiveState state = activeStates.get(player.getUniqueId());
        if (state == null) {
            return false;
        }
        FFAMode worldMode = getModeByWorld(player.getWorld());
        return worldMode != null && worldMode.getId().equalsIgnoreCase(state.modeId());
    }

    public String getModeId(UUID playerId) {
        ActiveState state = activeStates.get(playerId);
        return state == null ? "" : state.modeId();
    }

    public FFAMode getMode(UUID playerId) {
        return plugin.getModeManager().getMode(getModeId(playerId));
    }

    public int countPlayersInMode(String modeId) {
        if (modeId == null || modeId.isBlank()) {
            return 0;
        }
        int count = 0;
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (!isPlayerInFFA(online)) {
                continue;
            }
            if (modeId.equalsIgnoreCase(getModeId(online.getUniqueId()))) {
                count++;
            }
        }
        return count;
    }

    public void activateMode(Player player, FFAMode mode, boolean captureSnapshot) {
        if (player == null || mode == null) {
            return;
        }
        ActiveState existing = activeStates.get(player.getUniqueId());
        PlayerSnapshot snapshot = existing != null ? existing.snapshot() : null;
        if (snapshot == null && captureSnapshot) {
            snapshot = PlayerSnapshot.capture(player);
        }
        long protectedUntil = existing == null ? 0L : existing.protectedUntil();
        activeStates.put(player.getUniqueId(), new ActiveState(mode.getId(), snapshot, protectedUntil));
    }

    public void switchMode(Player player, FFAMode mode, boolean captureSnapshotIfMissing) {
        if (player == null || mode == null) {
            return;
        }
        ActiveState existing = activeStates.get(player.getUniqueId());
        PlayerSnapshot snapshot = existing == null ? null : existing.snapshot();
        if (snapshot == null && captureSnapshotIfMissing) {
            snapshot = PlayerSnapshot.capture(player);
        }
        long protectedUntil = existing == null ? 0L : existing.protectedUntil();
        activeStates.put(player.getUniqueId(), new ActiveState(mode.getId(), snapshot, protectedUntil));
    }

    public void applySpawnProtection(Player player, FFAMode mode) {
        ActiveState state = activeStates.get(player.getUniqueId());
        if (state == null || mode == null) {
            return;
        }
        activeStates.put(
            player.getUniqueId(),
            new ActiveState(state.modeId(), state.snapshot(), System.currentTimeMillis() + (mode.getSpawnProtectionSeconds() * 1000L))
        );
    }

    public boolean hasSpawnProtection(Player player) {
        ActiveState state = activeStates.get(player.getUniqueId());
        return state != null && state.protectedUntil() > System.currentTimeMillis();
    }

    public LeaveResult leaveFFA(Player player, boolean restoreSnapshot, boolean restoreLocation) {
        if (player == null) {
            return new LeaveResult(null, false);
        }
        ActiveState state = activeStates.remove(player.getUniqueId());
        if (state == null) {
            return new LeaveResult(null, false);
        }

        FFAMode mode = plugin.getModeManager().getMode(state.modeId());
        if (restoreSnapshot && state.snapshot() != null) {
            state.snapshot().restore(player, restoreLocation);
        } else {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);
            if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
                player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
            }
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }
        return new LeaveResult(mode, true);
    }

    public void clearActiveState(Player player) {
        if (player == null) {
            return;
        }
        activeStates.remove(player.getUniqueId());
    }

    public void deactivate(Player player, boolean restoreSnapshot) {
        leaveFFA(player, restoreSnapshot, true);
    }

    public WorldChangeResult handleWorldChange(Player player, World from, World to) {
        FFAMode fromMode = getModeByWorld(from);
        FFAMode toMode = getModeByWorld(to);
        ActiveState current = activeStates.get(player.getUniqueId());

        if (toMode == null) {
            if (current == null) {
                return new WorldChangeResult(WorldChangeAction.NONE, fromMode, null);
            }
            leaveFFA(player, true, false);
            WorldChangeAction action = fromMode == null ? WorldChangeAction.STALE_STATE_CLEARED : WorldChangeAction.LEFT_FFA;
            return new WorldChangeResult(action, fromMode, null);
        }

        if (current == null) {
            activateMode(player, toMode, true);
            return new WorldChangeResult(fromMode == null ? WorldChangeAction.ENTERED_FFA : WorldChangeAction.SWITCHED_MODE, fromMode, toMode);
        }

        if (toMode.getId().equalsIgnoreCase(current.modeId())) {
            WorldChangeAction action;
            if (fromMode != null && fromMode.getId().equalsIgnoreCase(toMode.getId())) {
                action = WorldChangeAction.SAME_MODE_WORLD_CHANGE;
            } else if (fromMode != null) {
                action = WorldChangeAction.SWITCHED_MODE;
            } else {
                action = WorldChangeAction.ENTERED_FFA;
            }
            return new WorldChangeResult(action, fromMode, toMode);
        }

        switchMode(player, toMode, false);
        return new WorldChangeResult(WorldChangeAction.SWITCHED_MODE, fromMode, toMode);
    }

    public void syncWorldState(Player player) {
        handleWorldChange(player, null, player.getWorld());
    }

    public boolean isFarmKill(UUID killerId, UUID victimId) {
        int cooldown = plugin.getConfig().getInt("anti-farm.kill-cooldown-seconds", 20);
        if (!plugin.getConfig().getBoolean("anti-farm.enabled", true) || cooldown <= 0) {
            return false;
        }
        String key = killerId + ":" + victimId;
        long now = System.currentTimeMillis();
        long previous = farmKillHistory.getOrDefault(key, 0L);
        farmKillHistory.put(key, now);
        return previous > 0L && now - previous < cooldown * 1000L;
    }

    public Location resolveRespawnLocation(Player player) {
        FFAMode mode = getMode(player.getUniqueId());
        return mode == null ? player.getLocation() : plugin.getSpawnManager().getFFASpawn(mode);
    }

    public record LeaveResult(FFAMode mode, boolean changed) {
    }

    public record WorldChangeResult(WorldChangeAction action, FFAMode fromMode, FFAMode toMode) {
    }

    public enum WorldChangeAction {
        NONE,
        LEFT_FFA,
        ENTERED_FFA,
        SAME_MODE_WORLD_CHANGE,
        SWITCHED_MODE,
        STALE_STATE_CLEARED
    }

    private record ActiveState(String modeId, PlayerSnapshot snapshot, long protectedUntil) {
    }
}
