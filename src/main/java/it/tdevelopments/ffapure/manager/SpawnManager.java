package it.tdevelopments.ffapure.manager;

import it.tdevelopments.ffapure.FFAPlugin;
import it.tdevelopments.ffapure.model.FFAMode;
import org.bukkit.Location;

public final class SpawnManager {

    private final FFAPlugin plugin;

    public SpawnManager(FFAPlugin plugin) {
        this.plugin = plugin;
    }

    public Location getFFASpawn(FFAMode mode) {
        return mode == null ? null : mode.getSpawnData().getFfaSpawn();
    }

    public Location getLobby(FFAMode mode) {
        return mode == null ? null : mode.getSpawnData().getLobbySpawn();
    }

    public boolean setFFASpawn(String modeId, Location location) {
        FFAMode mode = plugin.getModeManager().getMode(modeId);
        if (mode == null) {
            return false;
        }
        mode.getSpawnData().setFfaSpawn(location);
        plugin.getModeManager().save();
        return true;
    }

    public boolean setLobby(String modeId, Location location) {
        FFAMode mode = plugin.getModeManager().getMode(modeId);
        if (mode == null) {
            return false;
        }
        mode.getSpawnData().setLobbySpawn(location);
        plugin.getModeManager().save();
        return true;
    }
}
