package it.tdevelopments.ffapure.model;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;

public final class PlayerKitLayout {

    private int slot;
    private String name;
    private String templateId = "";
    private List<ItemStack> inventoryContents = blankList(36);
    private List<ItemStack> armorContents = blankList(4);
    private List<ItemStack> enderChestContents = blankList(27);
    private ItemStack offhand;

    public PlayerKitLayout copy() {
        PlayerKitLayout copy = new PlayerKitLayout();
        copy.slot = slot;
        copy.name = name;
        copy.templateId = templateId;
        copy.inventoryContents = cloneList(inventoryContents);
        copy.armorContents = cloneList(armorContents);
        copy.enderChestContents = cloneList(enderChestContents);
        copy.offhand = offhand == null ? null : offhand.clone();
        return copy;
    }

    private static List<ItemStack> blankList(int size) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(null);
        }
        return list;
    }

    private static List<ItemStack> cloneList(List<ItemStack> source) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack stack : source) {
            list.add(stack == null ? null : stack.clone());
        }
        return list;
    }

    public int getSlot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    public String getName() {
        return name == null ? "Kit " + slot : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTemplateId() {
        return templateId == null ? "" : templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
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
        this.offhand = offhand == null ? null : offhand.clone();
    }
}
