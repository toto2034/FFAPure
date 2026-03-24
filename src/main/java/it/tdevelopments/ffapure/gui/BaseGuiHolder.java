package it.tdevelopments.ffapure.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public abstract class BaseGuiHolder implements InventoryHolder {

    private Inventory inventory;
    private final String guiId;
    private final String modeId;

    protected BaseGuiHolder(String guiId, String modeId) {
        this.guiId = guiId;
        this.modeId = modeId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public String getGuiId() {
        return guiId;
    }

    public String getModeId() {
        return modeId;
    }
}
