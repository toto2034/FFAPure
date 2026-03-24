package it.tdevelopments.ffapure.model;

import java.util.ArrayList;
import java.util.List;

public final class FFAMode {

    private final String id;
    private final ModeFiles files;
    private String displayName;
    private final List<String> enabledWorlds = new ArrayList<>();
    private final List<String> layoutNames = new ArrayList<>();
    private final ModeSpawnData spawnData = new ModeSpawnData();
    private int layoutSlots = 3;
    private int respawnDelaySeconds = 1;
    private int spawnProtectionSeconds = 2;
    private boolean clearInventoryOnJoin = true;
    private boolean clearInventoryOnRespawn = true;
    private boolean healOnRespawn = true;
    private boolean openKitOnFirstJoin = true;
    private boolean openKitOnRespawn = false;
    private boolean giveLastSelectedKitOnRespawn = true;

    public FFAMode(String id, ModeFiles files) {
        this.id = id;
        this.files = files;
        this.displayName = id;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<String> getEnabledWorlds() {
        return enabledWorlds;
    }

    public ModeFiles getFiles() {
        return files;
    }

    public List<String> getLayoutNames() {
        return layoutNames;
    }

    public ModeSpawnData getSpawnData() {
        return spawnData;
    }

    public int getLayoutSlots() {
        return layoutSlots;
    }

    public void setLayoutSlots(int layoutSlots) {
        this.layoutSlots = layoutSlots;
    }

    public int getRespawnDelaySeconds() {
        return respawnDelaySeconds;
    }

    public void setRespawnDelaySeconds(int respawnDelaySeconds) {
        this.respawnDelaySeconds = respawnDelaySeconds;
    }

    public int getSpawnProtectionSeconds() {
        return spawnProtectionSeconds;
    }

    public void setSpawnProtectionSeconds(int spawnProtectionSeconds) {
        this.spawnProtectionSeconds = spawnProtectionSeconds;
    }

    public boolean isClearInventoryOnJoin() {
        return clearInventoryOnJoin;
    }

    public void setClearInventoryOnJoin(boolean clearInventoryOnJoin) {
        this.clearInventoryOnJoin = clearInventoryOnJoin;
    }

    public boolean isClearInventoryOnRespawn() {
        return clearInventoryOnRespawn;
    }

    public void setClearInventoryOnRespawn(boolean clearInventoryOnRespawn) {
        this.clearInventoryOnRespawn = clearInventoryOnRespawn;
    }

    public boolean isHealOnRespawn() {
        return healOnRespawn;
    }

    public void setHealOnRespawn(boolean healOnRespawn) {
        this.healOnRespawn = healOnRespawn;
    }

    public boolean isOpenKitOnFirstJoin() {
        return openKitOnFirstJoin;
    }

    public void setOpenKitOnFirstJoin(boolean openKitOnFirstJoin) {
        this.openKitOnFirstJoin = openKitOnFirstJoin;
    }

    public boolean isOpenKitOnRespawn() {
        return openKitOnRespawn;
    }

    public void setOpenKitOnRespawn(boolean openKitOnRespawn) {
        this.openKitOnRespawn = openKitOnRespawn;
    }

    public boolean isGiveLastSelectedKitOnRespawn() {
        return giveLastSelectedKitOnRespawn;
    }

    public void setGiveLastSelectedKitOnRespawn(boolean giveLastSelectedKitOnRespawn) {
        this.giveLastSelectedKitOnRespawn = giveLastSelectedKitOnRespawn;
    }

    public String layoutName(int slot) {
        int index = slot - 1;
        if (index >= 0 && index < layoutNames.size()) {
            return layoutNames.get(index);
        }
        return "Kit " + slot;
    }
}
