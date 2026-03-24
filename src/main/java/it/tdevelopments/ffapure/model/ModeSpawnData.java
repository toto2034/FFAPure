package it.tdevelopments.ffapure.model;

import org.bukkit.Location;

public final class ModeSpawnData {

    private Location ffaSpawn;
    private Location lobbySpawn;

    public Location getFfaSpawn() {
        return ffaSpawn;
    }

    public void setFfaSpawn(Location ffaSpawn) {
        this.ffaSpawn = ffaSpawn;
    }

    public Location getLobbySpawn() {
        return lobbySpawn;
    }

    public void setLobbySpawn(Location lobbySpawn) {
        this.lobbySpawn = lobbySpawn;
    }
}
