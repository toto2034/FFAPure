package it.tdevelopments.ffapure.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModePlayerData {

    private final PlayerStats stats;
    private final Map<Integer, PlayerKitLayout> layouts = new LinkedHashMap<>();
    private int selectedLayoutSlot = 1;
    private boolean joinedBefore;

    public ModePlayerData(PlayerStats stats) {
        this.stats = stats;
    }

    public PlayerStats getStats() {
        return stats;
    }

    public Map<Integer, PlayerKitLayout> getLayouts() {
        return layouts;
    }

    public int getSelectedLayoutSlot() {
        return selectedLayoutSlot;
    }

    public void setSelectedLayoutSlot(int selectedLayoutSlot) {
        this.selectedLayoutSlot = selectedLayoutSlot;
    }

    public boolean isJoinedBefore() {
        return joinedBefore;
    }

    public void setJoinedBefore(boolean joinedBefore) {
        this.joinedBefore = joinedBefore;
    }
}
