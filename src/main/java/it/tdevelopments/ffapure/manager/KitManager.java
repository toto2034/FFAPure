package it.tdevelopments.ffapure.manager;

import it.tdevelopments.ffapure.FFAPlugin;
import it.tdevelopments.ffapure.model.FFAKitDefinition;
import it.tdevelopments.ffapure.model.FFAMode;
import it.tdevelopments.ffapure.model.PlayerKitLayout;
import it.tdevelopments.ffapure.util.ItemStackUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class KitManager {

    private final FFAPlugin plugin;
    private final Map<String, Map<String, FFAKitDefinition>> definitionsByMode = new LinkedHashMap<>();

    public KitManager(FFAPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        definitionsByMode.clear();
        for (FFAMode mode : plugin.getModeManager().getModes()) {
            Map<String, FFAKitDefinition> modeDefinitions = new LinkedHashMap<>();
            ConfigurationSection root = mode.getFiles().getKitsConfig().getConfigurationSection("templates");
            if (root == null) {
                definitionsByMode.put(mode.getId().toLowerCase(Locale.ROOT), modeDefinitions);
                continue;
            }
            for (String key : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                FFAKitDefinition definition = new FFAKitDefinition(key);
                definition.setDisplayName(section.getString("display-name", key));
                Material icon = Material.matchMaterial(section.getString("icon", "CHEST"));
                definition.setIcon(icon == null ? Material.CHEST : icon);
                definition.setPermission(section.getString("permission", ""));
                definition.getLore().addAll(section.getStringList("lore"));
                definition.setInventoryContents(readItemList(section, "inventory", 36));
                definition.setArmorContents(readItemList(section, "armor", 4));
                definition.setEnderChestContents(readItemList(section, "enderchest", 27));
                Object offhand = section.get("offhand");
                if (offhand instanceof ItemStack itemStack) {
                    definition.setOffhand(itemStack);
                }
                modeDefinitions.put(key.toLowerCase(Locale.ROOT), definition);
            }
            definitionsByMode.put(mode.getId().toLowerCase(Locale.ROOT), modeDefinitions);
        }
    }

    public void save() {
        for (FFAMode mode : plugin.getModeManager().getModes()) {
            ConfigurationSection config = mode.getFiles().getKitsConfig();
            config.set("templates", null);
            Map<String, FFAKitDefinition> definitions = definitionsByMode.getOrDefault(mode.getId().toLowerCase(Locale.ROOT), Map.of());
            for (FFAKitDefinition definition : definitions.values()) {
                String path = "templates." + definition.getId();
                config.set(path + ".display-name", definition.getDisplayName());
                config.set(path + ".icon", definition.getIcon().name());
                config.set(path + ".permission", definition.getPermission());
                config.set(path + ".lore", definition.getLore());
                config.set(path + ".inventory", definition.getInventoryContents());
                config.set(path + ".armor", definition.getArmorContents());
                config.set(path + ".enderchest", definition.getEnderChestContents());
                config.set(path + ".offhand", definition.getOffhand());
            }
        }
        plugin.getModeManager().save();
    }

    public FFAKitDefinition getDefinition(String modeId, String id) {
        return definitionsByMode.getOrDefault(modeId.toLowerCase(Locale.ROOT), Map.of()).get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<FFAKitDefinition> getDefinitionsForMode(FFAMode mode) {
        return Collections.unmodifiableCollection(definitionsByMode.getOrDefault(mode.getId().toLowerCase(Locale.ROOT), Map.of()).values());
    }

    public PlayerKitLayout createLayoutFromTemplate(FFAMode mode, int slot, String templateId) {
        FFAKitDefinition definition = getDefinition(mode.getId(), templateId);
        PlayerKitLayout layout = new PlayerKitLayout();
        layout.setSlot(slot);
        layout.setName(mode.layoutName(slot));
        layout.setTemplateId(templateId == null ? "" : templateId);
        if (definition != null) {
            layout.setInventoryContents(ItemStackUtil.cloneList(definition.getInventoryContents(), 36));
            layout.setArmorContents(ItemStackUtil.cloneList(definition.getArmorContents(), 4));
            layout.setEnderChestContents(ItemStackUtil.cloneList(definition.getEnderChestContents(), 27));
            layout.setOffhand(ItemStackUtil.cloneOrAir(definition.getOffhand()));
        }
        return layout;
    }

    public void applyLayout(Player player, PlayerKitLayout layout) {
        player.getInventory().clear();
        player.getInventory().setStorageContents(ItemStackUtil.cloneList(layout.getInventoryContents(), 36).toArray(new ItemStack[0]));
        player.getInventory().setArmorContents(ItemStackUtil.cloneList(layout.getArmorContents(), 4).toArray(new ItemStack[0]));
        player.getInventory().setItemInOffHand(ItemStackUtil.cloneOrAir(layout.getOffhand()));
        player.getEnderChest().setContents(ItemStackUtil.cloneList(layout.getEnderChestContents(), 27).toArray(new ItemStack[0]));
        player.updateInventory();
    }

    private List<ItemStack> readItemList(ConfigurationSection section, String path, int size) {
        List<ItemStack> items = new ArrayList<>();
        List<?> raw = section.getList(path, List.of());
        for (int index = 0; index < size; index++) {
            Object value = index < raw.size() ? raw.get(index) : null;
            items.add(value instanceof ItemStack itemStack ? itemStack : null);
        }
        return items;
    }
}
