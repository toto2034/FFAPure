package it.tdevelopments.ffapure.manager;

import it.tdevelopments.ffapure.FFAPlugin;
import it.tdevelopments.ffapure.model.FFAMode;
import it.tdevelopments.ffapure.model.PremadeKitDefinition;
import it.tdevelopments.ffapure.util.ItemStackUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class PremadeKitManager {

    private static final List<String> ARMOR_ORDER = List.of("boots", "leggings", "chestplate", "helmet");

    private final FFAPlugin plugin;
    private final Map<String, PremadeKitDefinition> premadeByMode = new LinkedHashMap<>();

    public PremadeKitManager(FFAPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        premadeByMode.clear();
        for (FFAMode mode : plugin.getModeManager().getModes()) {
            PremadeKitDefinition definition = loadForMode(mode);
            if (definition != null) {
                premadeByMode.put(mode.getId().toLowerCase(Locale.ROOT), definition);
            }
        }
    }

    public PremadeKitDefinition getPremade(FFAMode mode) {
        if (mode == null) {
            return null;
        }
        return premadeByMode.get(mode.getId().toLowerCase(Locale.ROOT));
    }

    public boolean applyPremade(Player player, FFAMode mode) {
        PremadeKitDefinition definition = getPremade(mode);
        if (definition == null) {
            plugin.getLogger().warning("No premade kit loaded for mode '" + (mode == null ? "unknown" : mode.getId()) + "'.");
            return false;
        }
        if (isEmpty(definition)) {
            plugin.getLogger().warning("Premade kit for mode '" + mode.getId() + "' is empty or misconfigured.");
            return false;
        }
        player.getInventory().clear();
        player.getInventory().setStorageContents(ItemStackUtil.cloneList(definition.getInventoryContents(), 36).toArray(new ItemStack[0]));
        player.getInventory().setArmorContents(ItemStackUtil.cloneList(definition.getArmorContents(), 4).toArray(new ItemStack[0]));
        player.getInventory().setItemInOffHand(ItemStackUtil.cloneOrAir(definition.getOffhand()));
        player.getEnderChest().setContents(ItemStackUtil.cloneList(definition.getEnderChestContents(), 27).toArray(new ItemStack[0]));
        player.updateInventory();
        return true;
    }

    private PremadeKitDefinition loadForMode(FFAMode mode) {
        ConfigurationSection singular = mode.getFiles().getPremadeKitsConfig().getConfigurationSection("premade-kit");
        if (singular != null) {
            return readDefinition("premade", singular);
        }

        ConfigurationSection legacy = mode.getFiles().getPremadeKitsConfig().getConfigurationSection("premade-kits");
        if (legacy == null) {
            return null;
        }

        PremadeKitDefinition selected = null;
        for (String key : legacy.getKeys(false)) {
            ConfigurationSection section = legacy.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            if (selected == null) {
                selected = readDefinition(key, section);
                continue;
            }
            plugin.getLogger().warning("Mode '" + mode.getId() + "' has multiple premade kits. Only the first one will be loaded.");
            break;
        }
        return selected;
    }

    private PremadeKitDefinition readDefinition(String id, ConfigurationSection section) {
        PremadeKitDefinition definition = new PremadeKitDefinition(id);
        definition.setDisplayName(section.getString("display-name", id));
        definition.setIconItem(ItemStackUtil.fromSection(section.getConfigurationSection("icon"), plugin.getConfigManager()::color));
        definition.setInventoryContents(readIndexedItems(section, "inventory", 36));
        definition.setArmorContents(readArmor(section.getConfigurationSection("armor")));
        definition.setEnderChestContents(readIndexedItems(section, "enderchest", 27));
        definition.setOffhand(readSingleItem(section, "offhand"));
        return definition;
    }

    private java.util.List<ItemStack> readIndexedItems(ConfigurationSection section, String path, int size) {
        ConfigurationSection indexed = section.getConfigurationSection(path);
        if (indexed != null) {
            java.util.List<ItemStack> items = new java.util.ArrayList<>();
            for (int slot = 0; slot < size; slot++) {
                ConfigurationSection itemSection = indexed.getConfigurationSection(Integer.toString(slot));
                ItemStack built = ItemStackUtil.fromSection(itemSection, plugin.getConfigManager()::color);
                items.add(ItemStackUtil.isAir(built) ? null : built);
            }
            return items;
        }

        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        List<?> raw = section.getList(path, List.of());
        for (int slot = 0; slot < size; slot++) {
            Object value = slot < raw.size() ? raw.get(slot) : null;
            items.add(value instanceof ItemStack itemStack ? itemStack : null);
        }
        return items;
    }

    private java.util.List<ItemStack> readArmor(ConfigurationSection armorSection) {
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        if (armorSection == null) {
            for (int index = 0; index < 4; index++) {
                items.add(null);
            }
            return items;
        }
        for (String key : ARMOR_ORDER) {
            items.add(readSingleItem(armorSection, key));
        }
        return items;
    }

    private ItemStack readSingleItem(ConfigurationSection section, String path) {
        if (section == null) {
            return null;
        }
        Object raw = section.get(path);
        if (raw instanceof ItemStack itemStack) {
            return itemStack.clone();
        }
        ConfigurationSection itemSection = section.getConfigurationSection(path);
        ItemStack built = ItemStackUtil.fromSection(itemSection, plugin.getConfigManager()::color);
        return ItemStackUtil.isAir(built) ? null : built;
    }

    private boolean isEmpty(PremadeKitDefinition definition) {
        return definition.getInventoryContents().stream().allMatch(ItemStackUtil::isAir)
            && definition.getArmorContents().stream().allMatch(ItemStackUtil::isAir)
            && definition.getEnderChestContents().stream().allMatch(ItemStackUtil::isAir)
            && ItemStackUtil.isAir(definition.getOffhand());
    }
}
