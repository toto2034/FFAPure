package it.tdevelopments.ffapure.manager;

import it.tdevelopments.ffapure.FFAPlugin;
import it.tdevelopments.ffapure.model.FFAMode;
import it.tdevelopments.ffapure.model.ModeFiles;
import it.tdevelopments.ffapure.util.ItemStackUtil;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class ModeManager {

    private static final Pattern VALID_MODE_ID = Pattern.compile("^[a-z0-9_-]{2,32}$");

    private final FFAPlugin plugin;
    private final ModeConfigLoader loader;
    private final Map<String, FFAMode> modes = new LinkedHashMap<>();

    public ModeManager(FFAPlugin plugin) {
        this.plugin = plugin;
        this.loader = new ModeConfigLoader(plugin);
    }

    public void load() {
        modes.clear();
        for (ModeFiles files : loader.loadAll()) {
            YamlConfiguration section = files.getModeConfig();
            String id = normalizeModeId(section.getString("id", files.getFolder().getName()));
            if (!isValidModeId(id)) {
                plugin.getLogger().warning("Skipping invalid mode id '" + id + "' in folder " + files.getFolder().getName());
                continue;
            }
            FFAMode mode = new FFAMode(id, files);
            mode.setDisplayName(section.getString("display-name", ItemStackUtil.titleCase(id)));
            mode.getEnabledWorlds().addAll(section.getStringList("enabled-worlds"));
            mode.setLayoutSlots(section.getInt("layout-slots", 3));
            mode.getLayoutNames().addAll(section.getStringList("layout-names"));
            mode.getSpawnData().setFfaSpawn(section.getLocation("ffa-spawn"));
            mode.getSpawnData().setLobbySpawn(section.getLocation("lobby-spawn"));
            mode.setRespawnDelaySeconds(section.getInt("settings.respawn-delay-seconds", plugin.getConfig().getInt("defaults.respawn-delay-seconds", 1)));
            mode.setSpawnProtectionSeconds(section.getInt("settings.spawn-protection-seconds", plugin.getConfig().getInt("defaults.spawn-protection-seconds", 3)));
            mode.setClearInventoryOnJoin(section.getBoolean("settings.clear-inventory-on-join", true));
            mode.setClearInventoryOnRespawn(section.getBoolean("settings.clear-inventory-on-respawn", true));
            mode.setHealOnRespawn(section.getBoolean("settings.heal-on-respawn", true));
            mode.setOpenKitOnFirstJoin(section.getBoolean("kit-selection.open-on-first-join", true));
            mode.setOpenKitOnRespawn(section.getBoolean("kit-selection.open-on-respawn", false));
            mode.setGiveLastSelectedKitOnRespawn(section.getBoolean("kit-selection.give-last-selected-kit-on-respawn", true));
            modes.put(id, mode);
        }
    }

    public void save() {
        for (FFAMode mode : modes.values()) {
            YamlConfiguration config = mode.getFiles().getModeConfig();
            config.set("id", mode.getId());
            config.set("display-name", mode.getDisplayName());
            config.set("enabled-worlds", mode.getEnabledWorlds());
            config.set("ffa-spawn", mode.getSpawnData().getFfaSpawn());
            config.set("lobby-spawn", mode.getSpawnData().getLobbySpawn());
            config.set("layout-slots", mode.getLayoutSlots());
            config.set("layout-names", mode.getLayoutNames());
            config.set("settings.respawn-delay-seconds", mode.getRespawnDelaySeconds());
            config.set("settings.spawn-protection-seconds", mode.getSpawnProtectionSeconds());
            config.set("settings.clear-inventory-on-join", mode.isClearInventoryOnJoin());
            config.set("settings.clear-inventory-on-respawn", mode.isClearInventoryOnRespawn());
            config.set("settings.heal-on-respawn", mode.isHealOnRespawn());
            config.set("kit-selection.open-on-first-join", mode.isOpenKitOnFirstJoin());
            config.set("kit-selection.open-on-respawn", mode.isOpenKitOnRespawn());
            config.set("kit-selection.give-last-selected-kit-on-respawn", mode.isGiveLastSelectedKitOnRespawn());
            loader.save(mode.getFiles());
        }
    }

    public FFAMode getMode(String id) {
        return id == null ? null : modes.get(normalizeModeId(id));
    }

    public Collection<FFAMode> getModes() {
        return Collections.unmodifiableCollection(modes.values());
    }

    public List<String> getModeIds() {
        return modes.values().stream().map(FFAMode::getId).collect(Collectors.toList());
    }

    public boolean isFFAWorld(World world) {
        return getModeByWorld(world) != null;
    }

    public FFAMode getModeByWorld(World world) {
        if (world == null) {
            return null;
        }
        String worldName = world.getName();
        return modes.values().stream()
            .filter(mode -> mode.getSpawnData().getFfaSpawn() != null)
            .filter(mode -> mode.getEnabledWorlds().stream().anyMatch(name -> name.equalsIgnoreCase(worldName)))
            .findFirst()
            .orElse(null);
    }

    public boolean canUseFFACommandsHere(Player player) {
        boolean restricted = plugin.getConfig().getBoolean("commands.restrict-outside-ffa-worlds", true);
        return player != null && (!restricted || isFFAWorld(player.getWorld()));
    }

    public boolean isJoinCommandAllowedOutsideFFA(String rootCommand) {
        if (!plugin.getConfig().getBoolean("commands.restrict-outside-ffa-worlds", true)) {
            return true;
        }
        return "join".equalsIgnoreCase(rootCommand);
    }

    public boolean isCreationBlockedWorld(World world) {
        if (world == null) {
            return false;
        }
        return plugin.getConfig().getStringList("restricted-worlds.creation-blocked").stream()
            .anyMatch(name -> name.equalsIgnoreCase(world.getName()));
    }

    public ModeOperationResult createMode(String rawId, Location baseLocation) {
        String id = normalizeModeId(rawId);
        if (!isValidModeId(id)) {
            return ModeOperationResult.failure("invalid-mode-id", id);
        }
        if (loader.exists(id) || modes.containsKey(id)) {
            return ModeOperationResult.failure("mode-already-exists", id);
        }
        if (baseLocation == null || baseLocation.getWorld() == null) {
            plugin.getLogger().warning("Cannot create mode '" + id + "' without a valid world location.");
            return ModeOperationResult.failure("mode-create-failed", id);
        }
        try {
            ModeFiles files = loader.create(id);
            FFAMode mode = new FFAMode(id, files);
            mode.setDisplayName(ItemStackUtil.titleCase(id));
            mode.getEnabledWorlds().add(baseLocation.getWorld().getName());
            mode.getSpawnData().setFfaSpawn(baseLocation);
            modes.put(id, mode);
            save();
            return ModeOperationResult.success(id, mode);
        } catch (Exception exception) {
            plugin.getLogger().warning("Unable to create mode '" + id + "': " + exception.getMessage());
            return ModeOperationResult.failure("mode-create-failed", id);
        }
    }

    public ModeOperationResult deleteMode(String rawId) {
        String id = normalizeModeId(rawId);
        if (!isValidModeId(id)) {
            return ModeOperationResult.failure("invalid-mode-id", id);
        }
        FFAMode mode = modes.get(id);
        if (mode == null && !loader.exists(id)) {
            return ModeOperationResult.failure("mode-not-found", id);
        }
        if (!loader.delete(id)) {
            return ModeOperationResult.failure("mode-delete-failed", id);
        }
        if (mode != null) {
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (id.equalsIgnoreCase(plugin.getPlayerStateManager().getModeId(online.getUniqueId()))) {
                    plugin.leaveMode(online, false, true, true);
                    plugin.getConfigManager().send(online, "mode-removed-player", Map.of("mode", mode.getId(), "mode_display", mode.getDisplayName()));
                }
            }
            modes.remove(id);
        }
        plugin.getPlayerDataManager().purgeMode(id);
        return ModeOperationResult.success(id, mode);
    }

    public String normalizeModeId(String rawId) {
        return rawId == null ? "" : rawId.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isValidModeId(String id) {
        return id != null && VALID_MODE_ID.matcher(id).matches();
    }

    public record ModeOperationResult(boolean success, String messageKey, String modeId, FFAMode mode) {
        public static ModeOperationResult success(String modeId, FFAMode mode) {
            return new ModeOperationResult(true, "", modeId, mode);
        }

        public static ModeOperationResult failure(String messageKey, String modeId) {
            return new ModeOperationResult(false, messageKey, modeId, null);
        }
    }
}
