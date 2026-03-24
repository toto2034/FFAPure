package it.tdevelopments.ffapure.model;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;

public final class PremadeKitDefinition {

    private final String id;
    private String displayName;
    private ItemStack iconItem;
    private List<ItemStack> inventoryContents = new ArrayList<>();
    private List<ItemStack> armorContents = new ArrayList<>();
    private List<ItemStack> enderChestContents = new ArrayList<>();
    private ItemStack offhand;

    public PremadeKitDefinition(String id) {
        this.id = id;
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

    public ItemStack getIconItem() {
        return iconItem == null ? null : iconItem.clone();
    }

    public void setIconItem(ItemStack iconItem) {
        this.iconItem = iconItem == null ? null : iconItem.clone();
    }

    public List<ItemStack> getInventoryContents() {
        return inventoryContents;
    }

    public void setInventoryContents(List<ItemStack> inventoryContents) {
        this.inventoryContents = inventoryContents;
    }

    public List<ItemStack> getArmorContents() {
        return armorContents;
    }

    public void setArmorContents(List<ItemStack> armorContents) {
        this.armorContents = armorContents;
    }

    public List<ItemStack> getEnderChestContents() {
        return enderChestContents;
    }

    public void setEnderChestContents(List<ItemStack> enderChestContents) {
        this.enderChestContents = enderChestContents;
    }

    public ItemStack getOffhand() {
        return offhand;
    }

    public void setOffhand(ItemStack offhand) {
        this.offhand = offhand;
    }
}
