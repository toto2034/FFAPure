package it.tdevelopments.ffapure.manager;

import it.tdevelopments.ffapure.FFAPlugin;
import it.tdevelopments.ffapure.gui.KitBrowserGui;
import it.tdevelopments.ffapure.model.ModeFiles;
import it.tdevelopments.ffapure.util.ItemStackUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ModeConfigLoader {

    private static final int KIT_BROWSER_SCHEMA_VERSION = 1;
    private static final int KIT_EDITOR_SCHEMA_VERSION = 1;

    private final FFAPlugin plugin;
    private final File modesFolder;

    public ModeConfigLoader(FFAPlugin plugin) {
        this.plugin = plugin;
        this.modesFolder = new File(plugin.getDataFolder(), "modes");
        if (!modesFolder.exists()) {
            modesFolder.mkdirs();
        }
    }

    public List<ModeFiles> loadAll() {
        ensureBundledModesOnFirstSetup();
        List<ModeFiles> files = new ArrayList<>();
        File[] folders = modesFolder.listFiles(File::isDirectory);
        if (folders == null) {
            return files;
        }
        Arrays.sort(folders, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File folder : folders) {
            files.add(load(folder.getName()));
        }
        return files;
    }

    public boolean exists(String modeId) {
        return resolveModeFolder(modeId).exists();
    }

    public ModeFiles load(String modeId) {
        File folder = resolveModeFolder(modeId);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File modeFile = new File(folder, "mode.yml");
        File kitsFile = new File(folder, "kits.yml");
        File guiFile = new File(folder, "gui.yml");
        File premadeKitsFile = new File(folder, "premade-kits.yml");
        ensureModeFile(folder.getName(), "mode.yml", modeFile);
        ensureModeFile(folder.getName(), "kits.yml", kitsFile);
        ensureModeFile(folder.getName(), "gui.yml", guiFile);
        ensureModeFile(folder.getName(), "premade-kits.yml", premadeKitsFile);
        YamlConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);
        YamlConfiguration kitsConfig = YamlConfiguration.loadConfiguration(kitsFile);
        YamlConfiguration guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        YamlConfiguration premadeConfig = YamlConfiguration.loadConfiguration(premadeKitsFile);

        ModeFiles files = new ModeFiles(
            folder,
            modeFile,
            kitsFile,
            guiFile,
            premadeKitsFile,
            modeConfig,
            kitsConfig,
            guiConfig,
            premadeConfig
        );
        if (migrateBrowserConfiguration(folder.getName(), files)) {
            save(files);
        }
        return files;
    }

    public ModeFiles create(String modeId) {
        File folder = new File(modesFolder, modeId.toLowerCase(Locale.ROOT));
        if (!folder.exists()) {
            folder.mkdirs();
        }
        ModeFiles files = load(folder.getName());
        YamlConfiguration modeConfig = files.getModeConfig();
        if (!modeConfig.contains("id")) {
            modeConfig.set("id", modeId.toLowerCase(Locale.ROOT));
            modeConfig.set("display-name", modeId);
            save(files);
        }
        return files;
    }

    public boolean delete(String modeId) {
        File folder = resolveModeFolder(modeId);
        if (!folder.exists()) {
            return false;
        }
        try (Stream<Path> stream = Files.walk(folder.toPath())) {
            stream.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                });
            return true;
        } catch (RuntimeException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            plugin.getLogger().warning("Unable to delete mode folder '" + folder.getName() + "': " + cause.getMessage());
            return false;
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to delete mode folder '" + folder.getName() + "': " + exception.getMessage());
            return false;
        }
    }

    public void save(ModeFiles files) {
        try {
            files.getModeConfig().save(files.getModeFile());
            files.getKitsConfig().save(files.getKitsFile());
            files.getGuiConfig().save(files.getGuiFile());
            files.getPremadeKitsConfig().save(files.getPremadeKitsFile());
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to save mode files for " + files.getFolder().getName() + ": " + exception.getMessage());
        }
    }

    private File resolveModeFolder(String modeId) {
        if (modeId == null || modeId.isBlank()) {
            return new File(modesFolder, "");
        }
        File[] folders = modesFolder.listFiles(File::isDirectory);
        if (folders != null) {
            for (File folder : folders) {
                if (folder.getName().equalsIgnoreCase(modeId)) {
                    return folder;
                }
            }
        }
        return new File(modesFolder, modeId.toLowerCase(Locale.ROOT));
    }

    private void ensureBundledModesOnFirstSetup() {
        File[] folders = modesFolder.listFiles(File::isDirectory);
        if (folders != null && folders.length > 0) {
            return;
        }
        saveModeResourceIfMissing("crystal", "mode.yml");
        saveModeResourceIfMissing("crystal", "kits.yml");
        saveModeResourceIfMissing("crystal", "gui.yml");
        saveModeResourceIfMissing("crystal", "premade-kits.yml");
        saveModeResourceIfMissing("guns", "mode.yml");
        saveModeResourceIfMissing("guns", "kits.yml");
        saveModeResourceIfMissing("guns", "gui.yml");
        saveModeResourceIfMissing("guns", "premade-kits.yml");
    }

    private void saveModeResourceIfMissing(String modeId, String fileName) {
        File targetFolder = new File(modesFolder, modeId);
        if (!targetFolder.exists()) {
            targetFolder.mkdirs();
        }
        File targetFile = new File(targetFolder, fileName);
        ensureModeFile(modeId, fileName, targetFile);
    }

    private void ensureModeFile(String modeId, String fileName, File targetFile) {
        if (targetFile.exists()) {
            return;
        }
        String resourcePath = "modes/" + modeId + "/" + fileName;
        if (plugin.getResource(resourcePath) != null) {
            plugin.saveResource(resourcePath, false);
            return;
        }
        try {
            Files.writeString(targetFile.toPath(), "# Generated empty mode file\n");
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to create " + targetFile.getName() + " for mode " + modeId + ": " + exception.getMessage());
        }
    }

    private boolean migrateBrowserConfiguration(String modeId, ModeFiles files) {
        boolean layoutChanged = ensureBrowserLayoutSlots(files.getModeConfig());
        boolean browserChanged = ensureKitBrowserSection(modeId, files.getGuiConfig());
        boolean editorChanged = ensureKitEditorSection(modeId, files.getGuiConfig());
        if (layoutChanged || browserChanged || editorChanged) {
            plugin.getLogger().info("Applied targeted GUI migration for mode '" + modeId + "'.");
        }
        return layoutChanged || browserChanged || editorChanged;
    }

    private boolean ensureBrowserLayoutSlots(YamlConfiguration modeConfig) {
        boolean changed = false;

        if (modeConfig.getInt("layout-slots", 3) < 7) {
            modeConfig.set("layout-slots", 7);
            changed = true;
        }

        List<String> currentNames = new ArrayList<>(modeConfig.getStringList("layout-names"));
        List<String> migratedNames = new ArrayList<>(currentNames);
        while (migratedNames.size() < 3) {
            migratedNames.add("Kit " + (migratedNames.size() + 1));
        }
        for (int index = 0; index < 3; index++) {
            if (migratedNames.get(index) == null || migratedNames.get(index).isBlank()) {
                migratedNames.set(index, "Kit " + (index + 1));
            }
        }
        while (migratedNames.size() < 7) {
            migratedNames.add("Premium Kit " + Math.max(1, migratedNames.size() - 2));
        }
        for (int index = 3; index < 7; index++) {
            if (migratedNames.get(index) == null || migratedNames.get(index).isBlank()) {
                migratedNames.set(index, "Premium Kit " + (index - 2));
            }
        }
        if (!currentNames.equals(migratedNames)) {
            modeConfig.set("layout-names", migratedNames);
            changed = true;
        }

        return changed;
    }

    private boolean ensureKitBrowserSection(String modeId, YamlConfiguration guiConfig) {
        String basePath = KitBrowserGui.ID;
        if (guiConfig.getInt(basePath + ".schema-version", 0) >= KIT_BROWSER_SCHEMA_VERSION) {
            return false;
        }

        if (guiConfig.getString(basePath + ".title", "").isBlank()) {
            guiConfig.set(basePath + ".title", ItemStackUtil.titleCase(modeId) + " Kit Browser");
        }
        guiConfig.set(basePath + ".size", 54);
        guiConfig.set(basePath + ".fill-item.material", "GRAY_STAINED_GLASS_PANE");
        guiConfig.set(basePath + ".fill-item.name", " ");
        guiConfig.set(basePath + ".normal-kits", null);
        writeBrowserKitEntry(guiConfig, basePath + ".normal-kits.kit_1", 1, 10, "Kit 1", List.of());
        writeBrowserKitEntry(guiConfig, basePath + ".normal-kits.kit_2", 2, 11, "Kit 2", List.of());
        writeBrowserKitEntry(guiConfig, basePath + ".normal-kits.kit_3", 3, 12, "Kit 3", List.of());
        guiConfig.set(basePath + ".premium-kits", null);
        writeBrowserKitEntry(guiConfig, basePath + ".premium-kits.premium_1", 4, 13, "Premium Kit 1", List.of(
            "ffapure.vip",
            "ffapure.kit.premium",
            "ffapure.kit.vip",
            "ffapure.kit.premium.1"
        ));
        writeBrowserKitEntry(guiConfig, basePath + ".premium-kits.premium_2", 5, 14, "Premium Kit 2", List.of(
            "ffapure.vip",
            "ffapure.kit.premium",
            "ffapure.kit.vip",
            "ffapure.kit.premium.2"
        ));
        writeBrowserKitEntry(guiConfig, basePath + ".premium-kits.premium_3", 6, 15, "Premium Kit 3", List.of(
            "ffapure.vip",
            "ffapure.kit.premium",
            "ffapure.kit.vip",
            "ffapure.kit.premium.3"
        ));
        writeBrowserKitEntry(guiConfig, basePath + ".premium-kits.premium_4", 7, 16, "Premium Kit 4", List.of(
            "ffapure.vip",
            "ffapure.kit.premium",
            "ffapure.kit.vip",
            "ffapure.kit.premium.4"
        ));
        ensureBrowserActionSection(
            guiConfig,
            basePath + ".premade-kit",
            38,
            "BOOKSHELF",
            "&6Premade Kit",
            List.of("&7Premade Kit", "&aLeft click to load"),
            true
        );
        ensureBrowserActionSection(
            guiConfig,
            basePath + ".premade-kit-disabled",
            38,
            "BARRIER",
            "&cPremade Kit Unavailable",
            List.of("&7No premade kit is configured", "&7for this mode."),
            false
        );
        ensureBrowserActionSection(
            guiConfig,
            basePath + ".clear-inventory",
            42,
            "REDSTONE_BLOCK",
            "&cClear Inventory",
            List.of("&7Clear Inventory"),
            false
        );
        guiConfig.set(basePath + ".schema-version", KIT_BROWSER_SCHEMA_VERSION);
        return true;
    }

    private void writeBrowserKitEntry(YamlConfiguration guiConfig, String path, int layoutSlot, int guiSlot, String name, List<String> permissions) {
        guiConfig.set(path + ".layout-slot", layoutSlot);
        guiConfig.set(path + ".slot", guiSlot);
        guiConfig.set(path + ".material", "CHEST");
        guiConfig.set(path + ".name", name);
        guiConfig.set(path + ".lore", List.of(
            "&aLEFT CLICK TO LOAD",
            "&eRIGHT CLICK TO EDIT"
        ));
        if (permissions.isEmpty()) {
            guiConfig.set(path + ".permissions", null);
            guiConfig.set(path + ".permission", null);
            guiConfig.set(path + ".locked-message", null);
            return;
        }
        guiConfig.set(path + ".permissions", permissions);
        guiConfig.set(path + ".permission", null);
        guiConfig.set(path + ".locked-message", "premium-kit-locked");
    }

    private void ensureBrowserActionSection(
        YamlConfiguration guiConfig,
        String path,
        int slot,
        String material,
        String name,
        List<String> lore,
        boolean glow
    ) {
        guiConfig.set(path + ".slot", slot);
        if (!guiConfig.contains(path + ".material")) {
            guiConfig.set(path + ".material", material);
        }
        if (!guiConfig.contains(path + ".name")) {
            guiConfig.set(path + ".name", name);
        }
        if (!guiConfig.contains(path + ".lore")) {
            guiConfig.set(path + ".lore", lore);
        }
        if (glow && !guiConfig.contains(path + ".glow")) {
            guiConfig.set(path + ".glow", true);
        }
    }

    private boolean ensureKitEditorSection(String modeId, YamlConfiguration guiConfig) {
        String basePath = "GUI_KIT_EDITOR";
        if (guiConfig.getInt(basePath + ".schema-version", 0) >= KIT_EDITOR_SCHEMA_VERSION) {
            return false;
        }

        if (guiConfig.getString(basePath + ".title", "").isBlank()) {
            guiConfig.set(basePath + ".title", ItemStackUtil.titleCase(modeId) + " Kit Editor");
        }
        guiConfig.set(basePath + ".size", 54);
        guiConfig.set(basePath + ".fill-item.material", "AIR");
        guiConfig.set(basePath + ".fill-item.name", " ");

        guiConfig.set(basePath + ".decorative-items", null);
        writeDecorativePane(guiConfig, basePath + ".decorative-items.top-left", 0);
        writeDecorativePane(guiConfig, basePath + ".decorative-items.top-middle-left", 5);
        writeDecorativePane(guiConfig, basePath + ".decorative-items.top-middle-center", 6);
        writeDecorativePane(guiConfig, basePath + ".decorative-items.slot-8-technical-filler", 8);
        writeDecorativePane(guiConfig, basePath + ".decorative-items.bottom-left", 45);
        writeDecorativePane(guiConfig, basePath + ".decorative-items.bottom-center-left", 46);
        writeDecorativePane(guiConfig, basePath + ".decorative-items.bottom-center", 47);
        writeDecorativePane(guiConfig, basePath + ".decorative-items.bottom-right-left", 51);
        writeDecorativePane(guiConfig, basePath + ".decorative-items.bottom-right-center", 52);
        writeDecorativePane(guiConfig, basePath + ".decorative-items.bottom-right", 53);

        guiConfig.set(basePath + ".editable-slots", null);
        guiConfig.set(basePath + ".editable-slots.armor.helmet", 1);
        guiConfig.set(basePath + ".editable-slots.armor.chestplate", 2);
        guiConfig.set(basePath + ".editable-slots.armor.leggings", 3);
        guiConfig.set(basePath + ".editable-slots.armor.boots", 4);
        guiConfig.set(basePath + ".editable-slots.offhand", 7);
        for (int inventoryIndex = 9; inventoryIndex <= 35; inventoryIndex++) {
            guiConfig.set(basePath + ".editable-slots.content." + inventoryIndex, inventoryIndex);
        }
        guiConfig.set(basePath + ".editable-slots.content.8", 36);
        for (int hotbarIndex = 0; hotbarIndex <= 7; hotbarIndex++) {
            guiConfig.set(basePath + ".editable-slots.hotbar." + hotbarIndex, 37 + hotbarIndex);
        }

        guiConfig.set(basePath + ".slot-preview", null);
        guiConfig.set(basePath + ".slot-preview.empty-items.default.material", "AIR");
        guiConfig.set(basePath + ".slot-preview.empty-items.hotbar.material", "AIR");
        guiConfig.set(basePath + ".slot-preview.empty-items.content.material", "AIR");
        guiConfig.set(basePath + ".slot-preview.empty-items.helmet.material", "CYAN_STAINED_GLASS_PANE");
        guiConfig.set(basePath + ".slot-preview.empty-items.helmet.name", "&b ");
        guiConfig.set(basePath + ".slot-preview.empty-items.chestplate.material", "CYAN_STAINED_GLASS_PANE");
        guiConfig.set(basePath + ".slot-preview.empty-items.chestplate.name", "&b ");
        guiConfig.set(basePath + ".slot-preview.empty-items.leggings.material", "CYAN_STAINED_GLASS_PANE");
        guiConfig.set(basePath + ".slot-preview.empty-items.leggings.name", "&b ");
        guiConfig.set(basePath + ".slot-preview.empty-items.boots.material", "CYAN_STAINED_GLASS_PANE");
        guiConfig.set(basePath + ".slot-preview.empty-items.boots.name", "&b ");
        guiConfig.set(basePath + ".slot-preview.empty-items.offhand.material", "ORANGE_STAINED_GLASS_PANE");
        guiConfig.set(basePath + ".slot-preview.empty-items.offhand.name", "&6 ");

        guiConfig.set(basePath + ".slot-preview.empty-lore.default", List.of("&7Content Slot"));
        guiConfig.set(basePath + ".slot-preview.empty-lore.hotbar", List.of("&7Hotbar Slot"));
        guiConfig.set(basePath + ".slot-preview.empty-lore.content", List.of("&7Content Slot"));
        guiConfig.set(basePath + ".slot-preview.empty-lore.helmet", List.of("&7Helmet Slot"));
        guiConfig.set(basePath + ".slot-preview.empty-lore.chestplate", List.of("&7Chestplate Slot"));
        guiConfig.set(basePath + ".slot-preview.empty-lore.leggings", List.of("&7Leggings Slot"));
        guiConfig.set(basePath + ".slot-preview.empty-lore.boots", List.of("&7Boots Slot"));
        guiConfig.set(basePath + ".slot-preview.empty-lore.offhand", List.of("&7Second hand slot"));

        List<String> contentOccupiedLore = List.of(
            "&7Content Slot",
            "&eLeft click to pick up / place",
            "&eRight click to browse items"
        );
        List<String> hotbarOccupiedLore = List.of(
            "&7Hotbar Slot",
            "&eLeft click to pick up / place",
            "&eRight click to browse items"
        );
        guiConfig.set(basePath + ".slot-preview.occupied-lore.default", contentOccupiedLore);
        guiConfig.set(basePath + ".slot-preview.occupied-lore.hotbar", hotbarOccupiedLore);
        guiConfig.set(basePath + ".slot-preview.occupied-lore.content", contentOccupiedLore);
        guiConfig.set(basePath + ".slot-preview.occupied-lore.helmet", List.of(
            "&7Helmet Slot",
            "&eLeft click to pick up / place",
            "&eRight click to browse items"
        ));
        guiConfig.set(basePath + ".slot-preview.occupied-lore.chestplate", List.of(
            "&7Chestplate Slot",
            "&eLeft click to pick up / place",
            "&eRight click to browse items"
        ));
        guiConfig.set(basePath + ".slot-preview.occupied-lore.leggings", List.of(
            "&7Leggings Slot",
            "&eLeft click to pick up / place",
            "&eRight click to browse items"
        ));
        guiConfig.set(basePath + ".slot-preview.occupied-lore.boots", List.of(
            "&7Boots Slot",
            "&eLeft click to pick up / place",
            "&eRight click to browse items"
        ));
        guiConfig.set(basePath + ".slot-preview.occupied-lore.offhand", List.of(
            "&7Second hand slot",
            "&eLeft click to pick up / place",
            "&eRight click to browse items"
        ));

        guiConfig.set(basePath + ".buttons", null);
        writeEditorButton(guiConfig, basePath + ".buttons.save", 48, "SAVE_LAYOUT", "LIME_DYE", "&aSave", List.of("&7Save"));
        writeEditorButton(guiConfig, basePath + ".buttons.import", 49, "IMPORT_LAYOUT", "ARMOR_STAND", "&eImport", List.of("&7Import"));
        writeEditorButton(guiConfig, basePath + ".buttons.reset", 50, "RESET_LAYOUT", "ANVIL", "&6Reset", List.of("&7Reset"));

        guiConfig.set(basePath + ".schema-version", KIT_EDITOR_SCHEMA_VERSION);
        return true;
    }

    private void writeDecorativePane(YamlConfiguration guiConfig, String path, int slot) {
        guiConfig.set(path + ".slot", slot);
        guiConfig.set(path + ".material", "DARK_GRAY_STAINED_GLASS_PANE");
        guiConfig.set(path + ".name", "&8");
    }

    private void writeEditorButton(
        YamlConfiguration guiConfig,
        String path,
        int slot,
        String action,
        String material,
        String name,
        List<String> lore
    ) {
        guiConfig.set(path + ".slot", slot);
        guiConfig.set(path + ".action", action);
        guiConfig.set(path + ".material", material);
        guiConfig.set(path + ".name", name);
        guiConfig.set(path + ".lore", lore);
    }
}
