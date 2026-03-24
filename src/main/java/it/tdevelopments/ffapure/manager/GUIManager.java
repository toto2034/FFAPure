package it.tdevelopments.ffapure.manager;

import it.tdevelopments.ffapure.FFAPlugin;
import it.tdevelopments.ffapure.gui.BaseGuiHolder;
import it.tdevelopments.ffapure.model.FFAMode;
import it.tdevelopments.ffapure.model.GUIButton;
import it.tdevelopments.ffapure.model.ItemCategory;
import it.tdevelopments.ffapure.util.ItemStackUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class GUIManager {

    private final FFAPlugin plugin;

    public GUIManager(FFAPlugin plugin) {
        this.plugin = plugin;
    }

    public Inventory createInventory(BaseGuiHolder holder, FFAMode mode, String path, Function<String, String> replacer) {
        ConfigurationSection section = mode.getFiles().getGuiConfig().getConfigurationSection(path);
        int size = section == null ? 54 : section.getInt("size", 54);
        String title = replacer.apply(section == null ? path : section.getString("title", path));
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);
        ItemStack fillItem = getFillItem(mode, path, replacer);
        if (!ItemStackUtil.isAir(fillItem)) {
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, fillItem.clone());
            }
        }
        return inventory;
    }

    public Collection<GUIButton> getButtons(FFAMode mode, String path, Function<String, String> replacer) {
        ConfigurationSection section = mode.getFiles().getGuiConfig().getConfigurationSection(path + ".buttons");
        if (section == null) {
            return List.of();
        }
        List<GUIButton> buttons = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection buttonSection = section.getConfigurationSection(key);
            if (buttonSection == null) {
                continue;
            }
            buttons.add(new GUIButton(
                key,
                buttonSection.getInt("slot"),
                buttonSection.getString("action", key).toUpperCase(Locale.ROOT),
                buttonSection.getString("permission", ""),
                ItemStackUtil.fromSection(buttonSection, replacer)
            ));
        }
        return buttons;
    }

    public ItemStack getFillItem(FFAMode mode, String path, Function<String, String> replacer) {
        return ItemStackUtil.fromSection(mode.getFiles().getGuiConfig().getConfigurationSection(path + ".fill-item"), replacer);
    }

    public List<ItemCategory> getCategories(FFAMode mode, Function<String, String> replacer) {
        ConfigurationSection root = mode.getFiles().getGuiConfig().getConfigurationSection("GUI_ITEM_CATEGORIES.categories");
        if (root == null) {
            return List.of();
        }
        List<ItemCategory> categories = new ArrayList<>();
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            ItemCategory category = new ItemCategory(key);
            category.setDisplayName(section.getString("display-name", key));
            category.setIcon(ItemStackUtil.fromSection(section.getConfigurationSection("icon"), replacer));
            ConfigurationSection items = section.getConfigurationSection("items");
            if (items != null) {
                for (String itemKey : items.getKeys(false)) {
                    category.getItems().add(ItemStackUtil.fromSection(items.getConfigurationSection(itemKey), replacer));
                }
            }
            categories.add(category);
        }
        return categories;
    }

    public ItemCategory getCategory(FFAMode mode, String id, Function<String, String> replacer) {
        return getCategories(mode, replacer).stream()
            .filter(category -> category.getId().equalsIgnoreCase(id))
            .findFirst()
            .orElse(null);
    }

    public boolean isManagedInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        InventoryHolder holder = inventory.getHolder(false);
        return holder instanceof BaseGuiHolder;
    }

    public int closeManagedInventories(boolean notifyPlayers, String message) {
        int closed = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (!isManagedInventory(top)) {
                continue;
            }
            player.closeInventory();
            if (notifyPlayers && message != null && !message.isBlank()) {
                player.sendMessage(message);
            }
            closed++;
        }
        return closed;
    }
}
