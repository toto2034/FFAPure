package it.tdevelopments.ffapure.model;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;

public final class ItemCategory {

    private final String id;
    private String displayName;
    private ItemStack icon;
    private final List<ItemStack> items = new ArrayList<>();

    public ItemCategory(String id) {
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

    public ItemStack getIcon() {
        return icon;
    }

    public void setIcon(ItemStack icon) {
        this.icon = icon;
    }

    public List<ItemStack> getItems() {
        return items;
    }
}
