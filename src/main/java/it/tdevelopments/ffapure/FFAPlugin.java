package it.tdevelopments.ffapure;

import it.tdevelopments.ffapure.command.FFACommand;
import it.tdevelopments.ffapure.command.KitCommand;
import it.tdevelopments.ffapure.listener.CombatListener;
import it.tdevelopments.ffapure.listener.InventoryListener;
import it.tdevelopments.ffapure.listener.PlayerCommandListener;
import it.tdevelopments.ffapure.listener.PlayerLifecycleListener;
import it.tdevelopments.ffapure.manager.ConfigManager;
import it.tdevelopments.ffapure.manager.GUIManager;
import it.tdevelopments.ffapure.manager.KitEditorManager;
import it.tdevelopments.ffapure.manager.KitManager;
import it.tdevelopments.ffapure.manager.ModeManager;
import it.tdevelopments.ffapure.manager.PlaceholderResolver;
import it.tdevelopments.ffapure.manager.PlaceholderService;
import it.tdevelopments.ffapure.manager.PlayerDataManager;
import it.tdevelopments.ffapure.manager.PlayerStateManager;
import it.tdevelopments.ffapure.manager.PremadeKitManager;
import it.tdevelopments.ffapure.manager.SpawnManager;
import it.tdevelopments.ffapure.manager.StatsManager;
import it.tdevelopments.ffapure.model.FFAMode;
import it.tdevelopments.ffapure.model.PlayerKitLayout;
import it.tdevelopments.ffapure.placeholder.PlaceholderHook;
import java.io.File;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class FFAPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private ModeManager modeManager;
    private SpawnManager spawnManager;
    private KitManager kitManager;
    private PremadeKitManager premadeKitManager;
    private GUIManager guiManager;
    private KitEditorManager kitEditorManager;
    private PlayerDataManager playerDataManager;
    private StatsManager statsManager;
    private PlayerStateManager playerStateManager;
    private PlaceholderResolver placeholderResolver;
    private PlaceholderService placeholderService;
    private PlaceholderHook placeholderHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("messages.yml");

        this.configManager = new ConfigManager(this);
        this.modeManager = new ModeManager(this);
        this.spawnManager = new SpawnManager(this);
        this.kitManager = new KitManager(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.statsManager = new StatsManager(this);
        this.playerStateManager = new PlayerStateManager(this);
        this.placeholderService = new PlaceholderService(this);
        this.placeholderResolver = new PlaceholderResolver(this);
        this.guiManager = new GUIManager(this);
        this.premadeKitManager = new PremadeKitManager(this);
        this.kitEditorManager = new KitEditorManager(this);

        reloadAll();
        registerPlaceholderHookIfAvailable();

        FFACommand ffaCommand = new FFACommand(this);
        if (getCommand("ffa") != null) {
            getCommand("ffa").setExecutor(ffaCommand);
            getCommand("ffa").setTabCompleter(ffaCommand);
        }
        KitCommand kitCommand = new KitCommand(this);
        if (getCommand("kit") != null) {
            getCommand("kit").setExecutor(kitCommand);
            getCommand("kit").setTabCompleter(kitCommand);
        }

        Bukkit.getPluginManager().registerEvents(new PlayerLifecycleListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new InventoryListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerCommandListener(this), this);
    }

    @Override
    public void onDisable() {
        if (modeManager != null) {
            modeManager.save();
        }
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
    }

    public void reloadPlugin() {
        reloadAll();
    }

    public void reloadAll() {
        getLogger().info("Starting FFAPure full reload...");
        try {
            reloadGlobalConfigs();
            closeInvalidatedInventories();
            reloadMessages();
            reloadModes();
            reloadKits();
            reloadPremadeKits();
            reloadGuis();
            refreshOnlineRuntimeState();
            registerPlaceholderHookIfAvailable();
            getLogger().info("FFAPure reload completed successfully.");
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "FFAPure reload failed: " + exception.getMessage(), exception);
        }
    }

    public void reloadGlobalConfigs() {
        getLogger().info("Reloading global config and messages...");
        configManager.reload();
    }

    public void reloadMessages() {
        getLogger().info("Messages are ready from messages.yml.");
    }

    public void reloadModes() {
        getLogger().info("Reloading modes from modes/...");
        modeManager.load();
    }

    public void reloadKits() {
        getLogger().info("Reloading per-mode kit templates...");
        kitManager.load();
    }

    public void reloadPremadeKits() {
        getLogger().info("Reloading per-mode premade kits...");
        premadeKitManager.load();
    }

    public void reloadGuis() {
        getLogger().info("Refreshing GUI runtime state...");
        kitEditorManager.clearRuntimeState();
    }

    private void closeInvalidatedInventories() {
        boolean closeOpenGuis = getConfig().getBoolean("reload.close-open-guis", true);
        if (!closeOpenGuis) {
            return;
        }
        String message = getConfig().getBoolean("reload.notify-player-on-gui-close", true)
            ? configManager.message("reload-gui-closed", java.util.Map.of())
            : "";
        int closed = guiManager.closeManagedInventories(!message.isBlank(), message);
        if (closed > 0) {
            getLogger().info("Closed " + closed + " managed GUI inventories before reload.");
        }
    }

    private void refreshOnlineRuntimeState() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                playerDataManager.getOrLoad(player.getUniqueId());
                playerStateManager.syncWorldState(player);
            } catch (Exception exception) {
                getLogger().warning("Failed to refresh runtime state for " + player.getName() + ": " + exception.getMessage());
            }
        }
    }

    public boolean joinMode(Player player, String modeId, boolean fromCommand) {
        FFAMode mode = modeManager.getMode(modeId);
        if (mode == null) {
            configManager.send(player, "mode-not-found");
            return false;
        }
        if (!player.hasPermission("ffa.mode." + mode.getId())) {
            configManager.send(player, "no-permission");
            return false;
        }
        if (spawnManager.getFFASpawn(mode) == null) {
            configManager.send(player, "mode-no-spawn");
            return false;
        }

        kitEditorManager.clearPlayerRuntimeState(player, true);
        boolean firstJoin = !playerDataManager.hasJoinedBefore(player.getUniqueId(), mode.getId());
        playerStateManager.activateMode(player, mode, !playerStateManager.isPlayerInsideFFAMode(player));
        player.setGameMode(GameMode.SURVIVAL);
        player.setFireTicks(0);
        if (mode.isClearInventoryOnJoin()) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);
        }
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        }
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.teleport(spawnManager.getFFASpawn(mode));
        applySelectedLayout(player, mode);
        playerStateManager.applySpawnProtection(player, mode);
        playerDataManager.setJoinedBefore(player.getUniqueId(), mode.getId(), true);
        configManager.send(player, "joined-mode", configManager.placeholders(player, mode));
        if (firstJoin && mode.isOpenKitOnFirstJoin()) {
            kitEditorManager.openKitBrowser(player, mode.getId());
        }
        return true;
    }

    public void leaveMode(Player player, boolean announce) {
        leaveMode(player, announce, true, true);
    }

    public void leaveMode(Player player, boolean announce, boolean restoreSnapshot, boolean teleportToLobby) {
        leaveMode(player, announce, restoreSnapshot, true, teleportToLobby);
    }

    public void leaveMode(Player player, boolean announce, boolean restoreSnapshot, boolean restoreLocation, boolean teleportToLobby) {
        FFAMode mode = playerStateManager.getMode(player.getUniqueId());
        if (mode == null) {
            if (announce) {
                configManager.send(player, "not-in-mode");
            }
            return;
        }
        kitEditorManager.clearPlayerRuntimeState(player, true);
        playerStateManager.leaveFFA(player, restoreSnapshot, restoreLocation);
        if (teleportToLobby && spawnManager.getLobby(mode) != null) {
            player.teleport(spawnManager.getLobby(mode));
        }
        if (announce) {
            configManager.send(player, "left-mode", configManager.placeholders(player, mode));
        }
    }

    public void handleWorldChange(Player player, World from, World to) {
        PlayerStateManager.WorldChangeResult result = playerStateManager.handleWorldChange(player, from, to);
        switch (result.action()) {
            case LEFT_FFA, SWITCHED_MODE, STALE_STATE_CLEARED -> kitEditorManager.clearPlayerRuntimeState(player, true);
            default -> {
            }
        }
    }

    public void prepareRespawn(Player player, FFAMode mode) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
        }, Math.max(0, mode.getRespawnDelaySeconds()) * 20L);
    }

    public void applySelectedLayout(Player player, FFAMode mode) {
        int selectedSlot = playerDataManager.getSelectedLayoutSlot(player.getUniqueId(), mode.getId());
        int accessibleSlot = kitEditorManager.resolveAccessibleBrowserLayoutSlot(player, mode, selectedSlot);
        if (accessibleSlot != selectedSlot) {
            playerDataManager.setSelectedLayoutSlot(player.getUniqueId(), mode.getId(), accessibleSlot);
        }
        selectedSlot = accessibleSlot;
        PlayerKitLayout layout = playerDataManager.getLayout(player.getUniqueId(), mode.getId(), selectedSlot);
        kitManager.applyLayout(player, layout);
    }

    private void saveResourceIfMissing(String resource) {
        File file = new File(getDataFolder(), resource);
        if (!file.exists()) {
            saveResource(resource, false);
        }
    }

    private void registerPlaceholderHookIfAvailable() {
        Plugin placeholderApi = getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (placeholderApi == null || !placeholderApi.isEnabled()) {
            getLogger().info("PlaceholderAPI not found, external placeholders remain disabled.");
            return;
        }
        if (placeholderHook != null) {
            return;
        }
        placeholderHook = new PlaceholderHook(this);
        placeholderHook.register();
        getLogger().info("Registered PlaceholderAPI expansion for FFAPure.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ModeManager getModeManager() {
        return modeManager;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public KitManager getKitManager() {
        return kitManager;
    }

    public PremadeKitManager getPremadeKitManager() {
        return premadeKitManager;
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    public KitEditorManager getKitEditorManager() {
        return kitEditorManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public PlayerStateManager getPlayerStateManager() {
        return playerStateManager;
    }

    public PlaceholderResolver getPlaceholderResolver() {
        return placeholderResolver;
    }

    public PlaceholderService getPlaceholderService() {
        return placeholderService;
    }
}
