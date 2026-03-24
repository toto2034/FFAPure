package it.tdevelopments.ffapure.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerData {

    private final UUID uniqueId;
    private final Map<String, ModePlayerData> modes = new LinkedHashMap<>();

    public PlayerData(UUID uniqueId) {
        this.uniqueId = uniqueId;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public Map<String, ModePlayerData> getModes() {
        return modes;
    }
}
