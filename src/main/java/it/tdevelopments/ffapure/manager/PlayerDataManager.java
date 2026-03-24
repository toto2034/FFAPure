package it.tdevelopments.ffapure.manager;

import it.tdevelopments.ffapure.FFAPlugin;
import it.tdevelopments.ffapure.model.FFAMode;
import it.tdevelopments.ffapure.model.ModePlayerData;
import it.tdevelopments.ffapure.model.PlayerData;
import it.tdevelopments.ffapure.model.PlayerKitLayout;
import it.tdevelopments.ffapure.model.PlayerStats;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public final class PlayerDataManager {

    private final FFAPlugin plugin;
    private final File folder;
    private final Map<UUID, PlayerData> cache = new LinkedHashMap<>();

    public PlayerDataManager(FFAPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    public PlayerData getOrLoad(UUID uniqueId) {
        return cache.computeIfAbsent(uniqueId, this::loadPlayerData);
    }

    public ModePlayerData getModeData(UUID uniqueId, String modeId) {
        PlayerData playerData = getOrLoad(uniqueId);
        return playerData.getModes().computeIfAbsent(modeId.toLowerCase(), ignored -> createModeData(uniqueId, plugin.getModeManager().getMode(modeId)));
    }

    public PlayerKitLayout getLayout(UUID uniqueId, String modeId, int slot) {
        ModePlayerData modeData = getModeData(uniqueId, modeId);
        FFAMode mode = plugin.getModeManager().getMode(modeId);
        return modeData.getLayouts().computeIfAbsent(slot, ignored -> plugin.getKitManager().createLayoutFromTemplate(mode, slot, pickTemplateForSlot(mode, slot)));
    }

    public void setLayout(UUID uniqueId, String modeId, int slot, PlayerKitLayout layout) {
        getModeData(uniqueId, modeId).getLayouts().put(slot, layout);
        save(uniqueId);
    }

    public int getSelectedLayoutSlot(UUID uniqueId, String modeId) {
        return getModeData(uniqueId, modeId).getSelectedLayoutSlot();
    }

    public void setSelectedLayoutSlot(UUID uniqueId, String modeId, int slot) {
        getModeData(uniqueId, modeId).setSelectedLayoutSlot(slot);
        save(uniqueId);
    }

    public String getSelectedLayoutName(UUID uniqueId, String modeId) {
        int slot = getSelectedLayoutSlot(uniqueId, modeId);
        return getLayout(uniqueId, modeId, slot).getName();
    }

    public boolean hasJoinedBefore(UUID uniqueId, String modeId) {
        return getModeData(uniqueId, modeId).isJoinedBefore();
    }

    public void setJoinedBefore(UUID uniqueId, String modeId, boolean joinedBefore) {
        getModeData(uniqueId, modeId).setJoinedBefore(joinedBefore);
        save(uniqueId);
    }

    public PlayerStats getStats(UUID uniqueId, String modeId) {
        return getModeData(uniqueId, modeId).getStats();
    }

    public void save(UUID uniqueId) {
        PlayerData playerData = cache.get(uniqueId);
        if (playerData == null) {
            return;
        }
        File file = file(uniqueId);
        YamlConfiguration configuration = new YamlConfiguration();
        for (Map.Entry<String, ModePlayerData> entry : playerData.getModes().entrySet()) {
            String path = "modes." + entry.getKey();
            ModePlayerData modeData = entry.getValue();
            configuration.set(path + ".joined-before", modeData.isJoinedBefore());
            configuration.set(path + ".selected-layout-slot", modeData.getSelectedLayoutSlot());
            configuration.set(path + ".stats.kills", modeData.getStats().getKills());
            configuration.set(path + ".stats.deaths", modeData.getStats().getDeaths());
            configuration.set(path + ".stats.best-killstreak", modeData.getStats().getBestKillstreak());
            for (Map.Entry<Integer, PlayerKitLayout> layoutEntry : modeData.getLayouts().entrySet()) {
                PlayerKitLayout layout = layoutEntry.getValue();
                String layoutPath = path + ".layouts." + layoutEntry.getKey();
                configuration.set(layoutPath + ".name", layout.getName());
                configuration.set(layoutPath + ".template-id", layout.getTemplateId());
                configuration.set(layoutPath + ".inventory", layout.getInventoryContents());
                configuration.set(layoutPath + ".armor", layout.getArmorContents());
                configuration.set(layoutPath + ".enderchest", layout.getEnderChestContents());
                configuration.set(layoutPath + ".offhand", layout.getOffhand());
            }
        }
        try {
            configuration.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to save player data for " + uniqueId + ": " + exception.getMessage());
        }
    }

    public void saveAll() {
        for (UUID uniqueId : cache.keySet()) {
            save(uniqueId);
        }
    }

    public void purgeMode(String modeId) {
        String normalized = modeId == null ? "" : modeId.toLowerCase(Locale.ROOT);
        for (Map.Entry<UUID, PlayerData> entry : cache.entrySet()) {
            if (entry.getValue().getModes().remove(normalized) != null) {
                save(entry.getKey());
            }
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
                if (configuration.contains("modes." + normalized)) {
                    configuration.set("modes." + normalized, null);
                    configuration.save(file);
                }
            } catch (IOException exception) {
                plugin.getLogger().warning("Unable to purge mode data '" + normalized + "' from " + file.getName() + ": " + exception.getMessage());
            }
        }
    }

    public Map<UUID, PlayerData> getOrLoadMap() {
        return cache;
    }

    private PlayerData loadPlayerData(UUID uniqueId) {
        PlayerData playerData = new PlayerData(uniqueId);
        File file = file(uniqueId);
        if (!file.exists()) {
            return playerData;
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection modesSection = configuration.getConfigurationSection("modes");
        if (modesSection == null) {
            return playerData;
        }
        for (String modeId : modesSection.getKeys(false)) {
            ConfigurationSection modeSection = modesSection.getConfigurationSection(modeId);
            if (modeSection == null) {
                continue;
            }
            ModePlayerData modePlayerData = createModeData(uniqueId, plugin.getModeManager().getMode(modeId));
            modePlayerData.setJoinedBefore(modeSection.getBoolean("joined-before", false));
            modePlayerData.setSelectedLayoutSlot(modeSection.getInt("selected-layout-slot", 1));
            modePlayerData.getStats().setKills(modeSection.getInt("stats.kills", 0));
            modePlayerData.getStats().setDeaths(modeSection.getInt("stats.deaths", 0));
            modePlayerData.getStats().setBestKillstreak(modeSection.getInt("stats.best-killstreak", 0));
            ConfigurationSection layouts = modeSection.getConfigurationSection("layouts");
            if (layouts != null) {
                for (String slotKey : layouts.getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(slotKey);
                        ConfigurationSection layoutSection = layouts.getConfigurationSection(slotKey);
                        if (layoutSection == null) {
                            continue;
                        }
                        PlayerKitLayout layout = new PlayerKitLayout();
                        layout.setSlot(slot);
                        layout.setName(layoutSection.getString("name", "Kit " + slot));
                        layout.setTemplateId(layoutSection.getString("template-id", ""));
                        layout.setInventoryContents(readList(layoutSection, "inventory", 36));
                        layout.setArmorContents(readList(layoutSection, "armor", 4));
                        layout.setEnderChestContents(readList(layoutSection, "enderchest", 27));
                        Object offhand = layoutSection.get("offhand");
                        if (offhand instanceof ItemStack itemStack) {
                            layout.setOffhand(itemStack);
                        }
                        modePlayerData.getLayouts().put(slot, layout);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            playerData.getModes().put(modeId.toLowerCase(), modePlayerData);
        }
        return playerData;
    }

    private ModePlayerData createModeData(UUID uniqueId, FFAMode mode) {
        ModePlayerData data = new ModePlayerData(new PlayerStats(uniqueId));
        if (mode != null) {
            for (int slot = 1; slot <= mode.getLayoutSlots(); slot++) {
                data.getLayouts().put(slot, plugin.getKitManager().createLayoutFromTemplate(mode, slot, pickTemplateForSlot(mode, slot)));
            }
        }
        return data;
    }

    private String pickTemplateForSlot(FFAMode mode, int slot) {
        if (mode == null) {
            return "";
        }
        java.util.List<it.tdevelopments.ffapure.model.FFAKitDefinition> definitions = new java.util.ArrayList<>(plugin.getKitManager().getDefinitionsForMode(mode));
        if (definitions.isEmpty()) {
            return "";
        }
        int index = Math.min(slot - 1, definitions.size() - 1);
        return definitions.get(index).getId();
    }

    private List<ItemStack> readList(ConfigurationSection section, String path, int size) {
        List<ItemStack> list = new ArrayList<>();
        List<?> raw = section.getList(path, List.of());
        for (int index = 0; index < size; index++) {
            Object value = index < raw.size() ? raw.get(index) : null;
            list.add(value instanceof ItemStack itemStack ? itemStack : null);
        }
        return list;
    }

    private File file(UUID uniqueId) {
        return new File(folder, uniqueId + ".yml");
    }
}
