package it.tdevelopments.ffapure.model;

import java.util.UUID;

public final class PlayerStats {

    private final UUID uniqueId;
    private int kills;
    private int deaths;
    private int bestKillstreak;
    private String selectedKit = "";

    public PlayerStats(UUID uniqueId) {
        this.uniqueId = uniqueId;
    }

    public void addKill() {
        this.kills++;
    }

    public void addDeath() {
        this.deaths++;
    }

    public double getKdr() {
        return deaths <= 0 ? kills : (double) kills / (double) deaths;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public int getKills() {
        return kills;
    }

    public void setKills(int kills) {
        this.kills = kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public void setDeaths(int deaths) {
        this.deaths = deaths;
    }

    public int getBestKillstreak() {
        return bestKillstreak;
    }

    public void setBestKillstreak(int bestKillstreak) {
        this.bestKillstreak = bestKillstreak;
    }

    public String getSelectedKit() {
        return selectedKit == null ? "" : selectedKit;
    }

    public void setSelectedKit(String selectedKit) {
        this.selectedKit = selectedKit;
    }
}
