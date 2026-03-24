package it.tdevelopments.ffapure.manager;

import it.tdevelopments.ffapure.FFAPlugin;
import it.tdevelopments.ffapure.gui.BaseGuiHolder;
import it.tdevelopments.ffapure.gui.ItemCategoriesGui;
import it.tdevelopments.ffapure.gui.ItemPickerGui;
import it.tdevelopments.ffapure.gui.KitBrowserGui;
import it.tdevelopments.ffapure.gui.KitEditorGui;
import it.tdevelopments.ffapure.model.FFAKitDefinition;
import it.tdevelopments.ffapure.model.FFAMode;
import it.tdevelopments.ffapure.model.GUIButton;
import it.tdevelopments.ffapure.model.ItemCategory;
import it.tdevelopments.ffapure.model.PlayerKitLayout;
import it.tdevelopments.ffapure.model.PremadeKitDefinition;
import it.tdevelopments.ffapure.util.ItemStackUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class KitEditorManager {

    private final FFAPlugin plugin;
    private final Map<UUID, EditorSession> sessions = new HashMap<>();
    private final NamespacedKey editorPreviewKey;
    private final NamespacedKey editorPlaceholderKey;
    private final NamespacedKey editorPreviewLoreCountKey;

    public KitEditorManager(FFAPlugin plugin) {
        this.plugin = plugin;
        this.editorPreviewKey = new NamespacedKey(plugin, "editor-preview");
        this.editorPlaceholderKey = new NamespacedKey(plugin, "editor-placeholder");
        this.editorPreviewLoreCountKey = new NamespacedKey(plugin, "editor-preview-lore-count");
    }

    public void clearRuntimeState() {
        sessions.clear();
    }

    public void clearPlayerRuntimeState(Player player, boolean closeManagedInventory) {
        if (player == null) {
            return;
        }
        sessions.remove(player.getUniqueId());
        if (!ItemStackUtil.isAir(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
        if (closeManagedInventory && plugin.getGuiManager().isManagedInventory(player.getOpenInventory().getTopInventory())) {
            player.closeInventory();
        }
    }

    public void openKitBrowser(Player player, String modeId) {
        FFAMode mode = plugin.getModeManager().getMode(modeId);
        if (mode == null) {
            plugin.getConfigManager().send(player, "mode-not-found");
            return;
        }

        sessions.remove(player.getUniqueId());
        player.setItemOnCursor(null);

        KitBrowserGui holder = new KitBrowserGui(mode.getId());
        Inventory inventory = plugin.getGuiManager().createInventory(holder, mode, KitBrowserGui.ID, replacer(player, mode, Map.of()));
        renderKitBrowser(inventory, player, mode);
        player.openInventory(inventory);
    }

    public void openEditor(Player player, String modeId, int layoutSlot) {
        FFAMode mode = plugin.getModeManager().getMode(modeId);
        if (mode == null) {
            plugin.getConfigManager().send(player, "mode-not-found");
            return;
        }
        if (layoutSlot < 1 || layoutSlot > mode.getLayoutSlots()) {
            plugin.getConfigManager().send(player, "invalid-slot");
            return;
        }

        EditorSession session = new EditorSession(
            mode.getId(),
            layoutSlot,
            plugin.getPlayerDataManager().getLayout(player.getUniqueId(), mode.getId(), layoutSlot).copy()
        );
        sessions.put(player.getUniqueId(), session);
        player.setItemOnCursor(null);
        openEditor(player, mode, session);
    }

    public int resolveAccessibleBrowserLayoutSlot(Player player, FFAMode mode, int preferredSlot) {
        if (mode == null || mode.getLayoutSlots() <= 0) {
            return 1;
        }
        if (preferredSlot >= 1 && preferredSlot <= mode.getLayoutSlots() && canAccessBrowserLayout(player, mode, preferredSlot)) {
            return preferredSlot;
        }
        return browserKitEntries(mode).stream()
            .filter(entry -> entry.layoutSlot() >= 1 && entry.layoutSlot() <= mode.getLayoutSlots())
            .sorted(Comparator.comparingInt(BrowserKitEntry::layoutSlot))
            .filter(entry -> canAccessBrowserEntry(player, entry))
            .map(BrowserKitEntry::layoutSlot)
            .findFirst()
            .orElse(1);
    }

    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder(false) instanceof BaseGuiHolder holder)) {
            return;
        }

        FFAMode mode = plugin.getModeManager().getMode(holder.getModeId());
        if (mode == null) {
            event.setCancelled(true);
            player.closeInventory();
            plugin.getConfigManager().send(player, "layout-editor-missing");
            return;
        }

        switch (holder.getGuiId()) {
            case KitBrowserGui.ID -> handleBrowserClick(event, player, mode);
            case KitEditorGui.ID -> handleEditorClick(event, player, mode, (KitEditorGui) holder);
            case ItemCategoriesGui.ID -> handleCategoriesClick(event, player, mode, (ItemCategoriesGui) holder);
            case ItemPickerGui.ID -> handlePickerClick(event, player, mode, (ItemPickerGui) holder);
            default -> event.setCancelled(true);
        }
    }

    public void handleDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder(false) instanceof BaseGuiHolder holder)) {
            return;
        }
        if (event.getRawSlots().stream().noneMatch(raw -> raw < topInventory.getSize())) {
            return;
        }
        if (!KitEditorGui.ID.equals(holder.getGuiId())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        FFAMode mode = plugin.getModeManager().getMode(holder.getModeId());
        if (mode == null) {
            event.setCancelled(true);
            return;
        }
        Map<Integer, EditableSlot> editableSlots = editableSlots(mode);
        boolean valid = event.getRawSlots().stream()
            .filter(raw -> raw < topInventory.getSize())
            .allMatch(editableSlots::containsKey);
        if (!valid) {
            event.setCancelled(true);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            EditorSession session = sessions.get(player.getUniqueId());
            if (session == null) {
                return;
            }
            syncSessionFromEditorInventory(session, mode, topInventory);
        });
    }

    public void handleClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.getGuiManager().isManagedInventory(player.getOpenInventory().getTopInventory())) {
                return;
            }
            sessions.remove(player.getUniqueId());
            if (!ItemStackUtil.isAir(player.getItemOnCursor())) {
                player.setItemOnCursor(null);
            }
        });
    }

    private void handleBrowserClick(InventoryClickEvent event, Player player, FFAMode mode) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) {
            return;
        }

        BrowserKitEntry entry = browserEntryAtSlot(mode, event.getSlot());
        if (entry != null) {
            if (!canAccessBrowserEntry(player, entry)) {
                plugin.getConfigManager().send(player, entry.lockedMessage(), browserPlaceholders(player, mode, entry));
                return;
            }
            if (event.isRightClick()) {
                openEditor(player, mode.getId(), entry.layoutSlot());
                return;
            }
            if (!event.isLeftClick()) {
                return;
            }
            PlayerKitLayout layout = plugin.getPlayerDataManager().getLayout(player.getUniqueId(), mode.getId(), entry.layoutSlot());
            plugin.getPlayerDataManager().setSelectedLayoutSlot(player.getUniqueId(), mode.getId(), entry.layoutSlot());
            plugin.getKitManager().applyLayout(player, layout);
            plugin.getConfigManager().send(player, "layout-loaded", Map.of("kit", layout.getName()));
            return;
        }

        if (event.getSlot() == browserPremadeSlot(mode)) {
            if (!event.isLeftClick()) {
                return;
            }
            loadPremade(player, mode);
            return;
        }

        if (event.getSlot() == browserClearSlot(mode)) {
            if (!event.isLeftClick()) {
                return;
            }
            clearPlayerInventory(player);
            return;
        }
    }

    private void handleEditorClick(InventoryClickEvent event, Player player, FFAMode mode, KitEditorGui holder) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.matches(mode.getId(), holder.getLayoutSlot())) {
            event.setCancelled(true);
            player.closeInventory();
            plugin.getConfigManager().send(player, "layout-editor-missing");
            return;
        }

        if (!isTopInventoryClick(event)) {
            if (event.getClickedInventory() == event.getView().getBottomInventory()) {
                if (event.isShiftClick()) {
                    event.setCancelled(true);
                    return;
                }
                event.setCancelled(false);
                return;
            }
            event.setCancelled(true);
            return;
        }

        GUIButton button = buttonAt(mode, KitEditorGui.ID, event.getSlot(), player, session.placeholders());
        if (button != null) {
            event.setCancelled(true);
            handleEditorButton(player, mode, session, button.action());
            return;
        }

        EditableSlot editableSlot = editableSlots(mode).get(event.getSlot());
        if (editableSlot == null) {
            event.setCancelled(true);
            return;
        }

        handleEditableSlotInteraction(event, player, mode, session, editableSlot);
    }

    private void handleCategoriesClick(InventoryClickEvent event, Player player, FFAMode mode, ItemCategoriesGui holder) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) {
            return;
        }

        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.matches(mode.getId(), holder.getLayoutSlot())) {
            player.closeInventory();
            plugin.getConfigManager().send(player, "layout-editor-missing");
            return;
        }

        GUIButton button = buttonAt(mode, ItemCategoriesGui.ID, event.getSlot(), player, session.placeholders());
        if (button != null && "BACK_TO_EDITOR".equals(button.action())) {
            openEditor(player, mode, session);
            return;
        }

        List<Integer> categorySlots = mode.getFiles().getGuiConfig().getIntegerList("GUI_ITEM_CATEGORIES.category-slots");
        List<ItemCategory> categories = new ArrayList<>(plugin.getGuiManager().getCategories(mode, replacer(player, mode, session.placeholders())));
        for (int index = 0; index < categorySlots.size() && index < categories.size(); index++) {
            if (categorySlots.get(index) == event.getSlot()) {
                openItemPicker(player, mode, session, holder.getTargetSlotKey(), categories.get(index).getId());
                return;
            }
        }
    }

    private void handlePickerClick(InventoryClickEvent event, Player player, FFAMode mode, ItemPickerGui holder) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) {
            return;
        }

        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.matches(mode.getId(), holder.getLayoutSlot())) {
            player.closeInventory();
            plugin.getConfigManager().send(player, "layout-editor-missing");
            return;
        }

        GUIButton button = buttonAt(mode, ItemPickerGui.ID, event.getSlot(), player, session.placeholders());
        if (button != null && "BACK_TO_CATEGORIES".equals(button.action())) {
            openCategories(player, mode, session, holder.getTargetSlotKey());
            return;
        }

        ItemCategory category = plugin.getGuiManager().getCategory(mode, holder.getCategoryId(), replacer(player, mode, session.placeholders()));
        if (category == null) {
            return;
        }

        List<Integer> itemSlots = mode.getFiles().getGuiConfig().getIntegerList("GUI_ITEM_PICKER.item-slots");
        for (int index = 0; index < itemSlots.size() && index < category.getItems().size(); index++) {
            if (itemSlots.get(index) != event.getSlot()) {
                continue;
            }
            EditableSlot editableSlot = editableSlot(mode, holder.getTargetSlotKey());
            if (editableSlot == null) {
                player.closeInventory();
                plugin.getConfigManager().send(player, "layout-editor-missing");
                return;
            }
            setLayoutItem(session.workingCopy(), editableSlot, category.getItems().get(index));
            openEditor(player, mode, session);
            return;
        }
    }

    private void handleBrowserButton(Player player, FFAMode mode, String action) {
        switch (action) {
            case "CLOSE" -> player.closeInventory();
            case "LOAD_PREMADE_KIT" -> loadPremade(player, mode);
            case "CLEAR_PLAYER_INVENTORY" -> clearPlayerInventory(player);
            case "COMING_SOON" -> plugin.getConfigManager().send(player, "feature-coming-soon");
            default -> {
            }
        }
    }

    private void loadPremade(Player player, FFAMode mode) {
        PremadeKitDefinition premade = plugin.getPremadeKitManager().getPremade(mode);
        if (premade == null || !plugin.getPremadeKitManager().applyPremade(player, mode)) {
            plugin.getConfigManager().send(player, "premade-empty");
            return;
        }
        plugin.getConfigManager().send(player, "premade-loaded", Map.of("premade", premade.getDisplayName()));
    }

    private void clearPlayerInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
        plugin.getConfigManager().send(player, "inventory-cleared");
    }

    private void handleEditorButton(Player player, FFAMode mode, EditorSession session, String action) {
        switch (action) {
            case "LOAD_LAYOUT" -> {
                stashCursorIntoLayout(player, mode, session);
                PlayerKitLayout saved = plugin.getPlayerDataManager().getLayout(player.getUniqueId(), mode.getId(), session.layoutSlot()).copy();
                plugin.getPlayerDataManager().setSelectedLayoutSlot(player.getUniqueId(), mode.getId(), session.layoutSlot());
                plugin.getKitManager().applyLayout(player, saved);
                plugin.getConfigManager().send(player, "layout-loaded", Map.of("kit", saved.getName()));
            }
            case "SAVE_LAYOUT" -> {
                stashCursorIntoLayout(player, mode, session);
                plugin.getPlayerDataManager().setLayout(player.getUniqueId(), mode.getId(), session.layoutSlot(), session.workingCopy().copy());
                plugin.getPlayerDataManager().setSelectedLayoutSlot(player.getUniqueId(), mode.getId(), session.layoutSlot());
                plugin.getConfigManager().send(player, "layout-saved");
                refreshEditorInventory(player, mode, session);
            }
            case KitEditorGui.IMPORT_ACTION -> {
                if (!ItemStackUtil.isAir(player.getItemOnCursor())) {
                    plugin.getConfigManager().send(player, "editor-clear-cursor-first");
                    return;
                }
                importPlayerInventory(player, session);
                plugin.getConfigManager().send(player, "layout-imported");
                refreshEditorInventory(player, mode, session);
            }
            case "RESET_LAYOUT" -> {
                stashCursorIntoLayout(player, mode, session);
                PlayerKitLayout reset = plugin.getKitManager().createLayoutFromTemplate(mode, session.layoutSlot(), session.workingCopy().getTemplateId());
                session.replaceWorkingCopy(reset);
                refreshEditorInventory(player, mode, session);
            }
            case "CLEAR_LAYOUT" -> {
                stashCursorIntoLayout(player, mode, session);
                clearWorkingCopy(session.workingCopy());
                refreshEditorInventory(player, mode, session);
            }
            case "BACK_TO_BROWSER" -> {
                stashCursorIntoLayout(player, mode, session);
                openKitBrowser(player, mode.getId());
            }
            default -> {
            }
        }
    }

    private void handleEditableSlotInteraction(InventoryClickEvent event, Player player, FFAMode mode, EditorSession session, EditableSlot slot) {
        event.setCancelled(true);

        if (event.isShiftClick()) {
            return;
        }

        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }

        ItemStack current = sanitizeEditorItem(event.getCurrentItem());
        ItemStack cursor = sanitizeEditorItem(event.getCursor());

        if (event.getClick() == ClickType.RIGHT) {
            if (!ItemStackUtil.isAir(cursor)) {
                plugin.getConfigManager().send(player, "editor-clear-cursor-first");
                return;
            }
            openCategories(player, mode, session, slot.key());
            return;
        }

        if (ItemStackUtil.isAir(cursor) && ItemStackUtil.isAir(current)) {
            return;
        }

        if (ItemStackUtil.isAir(cursor)) {
            player.setItemOnCursor(cloneItem(current));
            setLayoutItem(session.workingCopy(), slot, null);
            refreshEditorInventory(player, mode, session);
            return;
        }

        if (ItemStackUtil.isAir(current)) {
            setLayoutItem(session.workingCopy(), slot, cursor);
            player.setItemOnCursor(null);
            refreshEditorInventory(player, mode, session);
            return;
        }

        if (canStack(current, cursor)) {
            int moved = moveToStack(current, cursor, cursor.getAmount());
            if (moved > 0) {
                setLayoutItem(session.workingCopy(), slot, current);
                updateCursorAfterTransfer(player, cursor, moved);
                refreshEditorInventory(player, mode, session);
            }
            return;
        }

        if (event.getClick() == ClickType.LEFT) {
            setLayoutItem(session.workingCopy(), slot, cursor);
            player.setItemOnCursor(cloneItem(current));
            refreshEditorInventory(player, mode, session);
        }
    }

    private void importPlayerInventory(Player player, EditorSession session) {
        PlayerKitLayout imported = session.workingCopy().copy();
        imported.setInventoryContents(ItemStackUtil.cloneList(Arrays.asList(player.getInventory().getStorageContents()), 36));
        imported.setArmorContents(ItemStackUtil.cloneList(Arrays.asList(player.getInventory().getArmorContents()), 4));
        imported.setOffhand(ItemStackUtil.cloneOrAir(player.getInventory().getItemInOffHand()));
        session.replaceWorkingCopy(imported);
    }

    private void openEditor(Player player, FFAMode mode, EditorSession session) {
        KitEditorGui holder = new KitEditorGui(mode.getId(), session.layoutSlot());
        Inventory inventory = plugin.getGuiManager().createInventory(holder, mode, KitEditorGui.ID, replacer(player, mode, session.placeholders()));
        renderEditorInventory(inventory, player, mode, session);
        player.openInventory(inventory);
    }

    private void openCategories(Player player, FFAMode mode, EditorSession session, String slotKey) {
        ItemCategoriesGui holder = new ItemCategoriesGui(mode.getId(), session.layoutSlot(), slotKey);
        Inventory inventory = plugin.getGuiManager().createInventory(holder, mode, ItemCategoriesGui.ID, replacer(player, mode, session.placeholders()));
        renderCategoriesInventory(inventory, player, mode, session);
        player.openInventory(inventory);
    }

    private void openItemPicker(Player player, FFAMode mode, EditorSession session, String slotKey, String categoryId) {
        ItemPickerGui holder = new ItemPickerGui(mode.getId(), session.layoutSlot(), slotKey, categoryId);
        Inventory inventory = plugin.getGuiManager().createInventory(holder, mode, ItemPickerGui.ID, replacer(player, mode, session.placeholders()));
        renderPickerInventory(inventory, player, mode, session, categoryId);
        player.openInventory(inventory);
    }

    private void renderKitBrowser(Inventory inventory, Player player, FFAMode mode) {
        renderBrowserKitEntries(inventory, player, mode);
        renderPremade(inventory, player, mode);
        renderClearInventory(inventory, player, mode);
    }

    private void renderEditorInventory(Inventory inventory, Player player, FFAMode mode, EditorSession session) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, null);
        }
        renderDecorativeItems(inventory, mode, "GUI_KIT_EDITOR", player, session.placeholders());
        applyButtons(inventory, player, mode, KitEditorGui.ID, session.placeholders());

        for (EditableSlot slot : editableSlots(mode).values()) {
            inventory.setItem(slot.guiSlot(), buildEditorDisplayItem(player, mode, session, slot, getLayoutItem(session.workingCopy(), slot)));
        }
    }

    private void renderCategoriesInventory(Inventory inventory, Player player, FFAMode mode, EditorSession session) {
        renderDecorativeItems(inventory, mode, "GUI_ITEM_CATEGORIES", player, session.placeholders());
        applyButtons(inventory, player, mode, ItemCategoriesGui.ID, session.placeholders());

        List<Integer> categorySlots = mode.getFiles().getGuiConfig().getIntegerList("GUI_ITEM_CATEGORIES.category-slots");
        List<ItemCategory> categories = new ArrayList<>(plugin.getGuiManager().getCategories(mode, replacer(player, mode, session.placeholders())));
        for (int index = 0; index < categorySlots.size() && index < categories.size(); index++) {
            int guiSlot = categorySlots.get(index);
            if (guiSlot < 0 || guiSlot >= inventory.getSize()) {
                continue;
            }
            ItemStack icon = cloneItem(categories.get(index).getIcon());
            inventory.setItem(guiSlot, ItemStackUtil.isAir(icon) ? new ItemStack(Material.CHEST) : icon);
        }
    }

    private void renderPickerInventory(Inventory inventory, Player player, FFAMode mode, EditorSession session, String categoryId) {
        renderDecorativeItems(inventory, mode, "GUI_ITEM_PICKER", player, session.placeholders());
        applyButtons(inventory, player, mode, ItemPickerGui.ID, session.placeholders());

        ItemCategory category = plugin.getGuiManager().getCategory(mode, categoryId, replacer(player, mode, session.placeholders()));
        if (category == null) {
            return;
        }
        List<Integer> itemSlots = mode.getFiles().getGuiConfig().getIntegerList("GUI_ITEM_PICKER.item-slots");
        for (int index = 0; index < itemSlots.size() && index < category.getItems().size(); index++) {
            int guiSlot = itemSlots.get(index);
            if (guiSlot < 0 || guiSlot >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(guiSlot, cloneItem(category.getItems().get(index)));
        }
    }

    private void fillInventory(Inventory inventory, FFAMode mode, String path, Player player, Map<String, String> placeholders) {
        ItemStack fillItem = plugin.getGuiManager().getFillItem(mode, path, replacer(player, mode, placeholders));
        if (ItemStackUtil.isAir(fillItem)) {
            return;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, fillItem.clone());
        }
    }

    private void renderDecorativeItems(Inventory inventory, FFAMode mode, String path, Player player, Map<String, String> placeholders) {
        ConfigurationSection section = mode.getFiles().getGuiConfig().getConfigurationSection(path + ".decorative-items");
        if (section == null) {
            return;
        }
        Function<String, String> replacer = replacer(player, mode, placeholders);
        for (String key : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection == null || !itemSection.contains("slot")) {
                continue;
            }
            int guiSlot = itemSection.getInt("slot", -1);
            if (guiSlot < 0 || guiSlot >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(guiSlot, ItemStackUtil.fromSection(itemSection, replacer));
        }
    }

    private void renderBrowserKitEntries(Inventory inventory, Player player, FFAMode mode) {
        for (BrowserKitEntry entry : browserKitEntries(mode)) {
            if (entry.guiSlot() < 0 || entry.guiSlot() >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(entry.guiSlot(), buildBrowserEntryItem(player, mode, entry));
        }
    }

    private ItemStack buildBrowserEntryItem(Player player, FFAMode mode, BrowserKitEntry entry) {
        Map<String, String> placeholders = browserPlaceholders(player, mode, entry);
        ItemStack configured = buildOptionalConfiguredItem(mode, entry.configPath(), player, placeholders);
        if (!ItemStackUtil.isAir(configured)) {
            return configured;
        }

        Material material = Material.CHEST;
        List<String> lore = List.of(
            "&aLEFT CLICK TO LOAD",
            "&eRIGHT CLICK TO EDIT"
        );
        return createFallbackItem(material, browserEntryDisplayName(entry), lore);
    }

    private void renderPremade(Inventory inventory, Player player, FFAMode mode) {
        int premadeSlot = browserPremadeSlot(mode);
        if (premadeSlot < 0 || premadeSlot >= inventory.getSize()) {
            return;
        }
        PremadeKitDefinition premade = plugin.getPremadeKitManager().getPremade(mode);
        if (premade == null) {
            ItemStack disabled = buildOptionalConfiguredItem(mode, KitBrowserGui.PREMADE_DISABLED_PATH, player, Map.of());
            inventory.setItem(premadeSlot, ItemStackUtil.isAir(disabled) ? createFallbackItem(
                Material.BARRIER,
                "&cPremade Kit",
                List.of("&7No premade kit configured", "&7for this mode.")
            ) : disabled);
            return;
        }
        ItemStack configured = buildOptionalConfiguredItem(mode, KitBrowserGui.PREMADE_PATH, player, Map.of("premade", premade.getDisplayName()));
        if (ItemStackUtil.isAir(configured)) {
            inventory.setItem(premadeSlot, createFallbackItem(
                Material.BOOKSHELF,
                "&6Premade Kit",
                List.of("&7Premade Kit", "&aLeft click to load")
            ));
            return;
        }
        inventory.setItem(premadeSlot, configured);
    }

    private void renderClearInventory(Inventory inventory, Player player, FFAMode mode) {
        int slot = browserClearSlot(mode);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        ItemStack configured = buildOptionalConfiguredItem(mode, KitBrowserGui.CLEAR_INVENTORY_PATH, player, Map.of());
        inventory.setItem(slot, ItemStackUtil.isAir(configured) ? createFallbackItem(
            Material.REDSTONE_BLOCK,
            "&cClear Inventory",
            List.of("&7Clear Inventory")
        ) : configured);
    }

    private void applyButtons(Inventory inventory, Player player, FFAMode mode, String path, Map<String, String> extraPlaceholders) {
        for (GUIButton button : buttons(mode, path, player, extraPlaceholders)) {
            if (button.slot() < 0 || button.slot() >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(button.slot(), cloneItem(button.itemStack()));
        }
    }

    private Collection<GUIButton> buttons(FFAMode mode, String path, Player player, Map<String, String> extraPlaceholders) {
        return plugin.getGuiManager().getButtons(mode, path, replacer(player, mode, extraPlaceholders)).stream()
            .filter(button -> button.permission().isBlank() || player.hasPermission(button.permission()))
            .toList();
    }

    private boolean hasButton(FFAMode mode, String path, String buttonId) {
        ConfigurationSection buttons = mode.getFiles().getGuiConfig().getConfigurationSection(path + ".buttons");
        return buttons != null && buttons.contains(buttonId);
    }

    private GUIButton buttonAt(FFAMode mode, String path, int slot, Player player, Map<String, String> extraPlaceholders) {
        return buttons(mode, path, player, extraPlaceholders).stream()
            .filter(button -> button.slot() == slot)
            .findFirst()
            .orElse(null);
    }

    private ItemStack buildConfiguredItem(FFAMode mode, String path, Player player, Map<String, String> placeholders) {
        ConfigurationSection section = mode.getFiles().getGuiConfig().getConfigurationSection(path);
        ItemStack item = ItemStackUtil.fromSection(section, replacer(player, mode, placeholders));
        return ItemStackUtil.isAir(item) ? new ItemStack(Material.CHEST) : item;
    }

    private Map<Integer, EditableSlot> editableSlots(FFAMode mode) {
        Map<Integer, EditableSlot> slots = new LinkedHashMap<>();
        ConfigurationSection root = mode.getFiles().getGuiConfig().getConfigurationSection("GUI_KIT_EDITOR.editable-slots");
        if (root == null) {
            return slots;
        }

        addInventorySlots(slots, root.getConfigurationSection("hotbar"), EditableType.HOTBAR, "Hotbar Slot");
        addInventorySlots(slots, root.getConfigurationSection("content"), EditableType.CONTENT, "Content Slot");
        addInventorySlots(slots, root.getConfigurationSection("extra"), EditableType.EXTRA, "Extra Slot");

        if (root.contains("offhand")) {
            int guiSlot = root.getInt("offhand", -1);
            if (guiSlot >= 0) {
                slots.put(guiSlot, new EditableSlot(guiSlot, "offhand", EditableType.OFFHAND, 0, new SlotInfo("Second hand slot", "offhand", 1)));
            }
        }

        ConfigurationSection armor = root.getConfigurationSection("armor");
        if (armor != null) {
            putArmorSlot(slots, armor, "helmet", EditableType.HELMET, 3, "Helmet Slot");
            putArmorSlot(slots, armor, "chestplate", EditableType.CHESTPLATE, 2, "Chestplate Slot");
            putArmorSlot(slots, armor, "leggings", EditableType.LEGGINGS, 1, "Leggings Slot");
            putArmorSlot(slots, armor, "boots", EditableType.BOOTS, 0, "Boots Slot");
        }

        if (!slots.isEmpty()) {
            return slots;
        }

        List<Integer> legacyInventory = root.getIntegerList("inventory");
        for (int index = 0; index < legacyInventory.size(); index++) {
            EditableType type = index < 9 ? EditableType.HOTBAR : EditableType.CONTENT;
            String name = index < 9 ? "Hotbar Slot" : "Inventory Slot";
            int guiSlot = legacyInventory.get(index);
            slots.put(guiSlot, new EditableSlot(guiSlot, "inventory:" + index, type, index, createInventorySlotInfo(type, index, name)));
        }
        return slots;
    }

    private void addInventorySlots(Map<Integer, EditableSlot> slots, ConfigurationSection section, EditableType type, String displayName) {
        if (section == null) {
            return;
        }
        section.getKeys(false).stream()
            .sorted(Comparator.comparingInt(this::parseIntSafe))
            .forEach(indexKey -> {
                int inventoryIndex = parseIntSafe(indexKey);
                int guiSlot = section.getInt(indexKey, -1);
                if (inventoryIndex < 0 || guiSlot < 0) {
                    return;
                }
                slots.put(guiSlot, new EditableSlot(guiSlot, "inventory:" + inventoryIndex, type, inventoryIndex, createInventorySlotInfo(type, inventoryIndex, displayName)));
            });
    }

    private void putArmorSlot(Map<Integer, EditableSlot> slots, ConfigurationSection armor, String key, EditableType type, int armorIndex, String displayName) {
        int guiSlot = armor.getInt(key, -1);
        if (guiSlot < 0) {
            return;
        }
        slots.put(guiSlot, new EditableSlot(guiSlot, key, type, armorIndex, new SlotInfo(displayName, type.configKey(), 1)));
    }

    private SlotInfo createInventorySlotInfo(EditableType type, int inventoryIndex, String fallbackName) {
        return switch (type) {
            case HOTBAR -> new SlotInfo("Hotbar Slot", type.configKey(), inventoryIndex + 1);
            case CONTENT -> new SlotInfo("Content Slot", type.configKey(), inventoryIndex + 1);
            case EXTRA -> new SlotInfo(fallbackName, type.configKey(), inventoryIndex + 1);
            default -> new SlotInfo(fallbackName, type.configKey(), inventoryIndex + 1);
        };
    }

    private EditableSlot editableSlot(FFAMode mode, String key) {
        return editableSlots(mode).values().stream()
            .filter(slot -> slot.key().equalsIgnoreCase(key))
            .findFirst()
            .orElse(null);
    }

    private List<BrowserKitEntry> browserKitEntries(FFAMode mode) {
        List<BrowserKitEntry> entries = new ArrayList<>();
        collectBrowserEntries(mode, KitBrowserGui.NORMAL_KITS_PATH, false, entries);
        collectBrowserEntries(mode, KitBrowserGui.PREMIUM_KITS_PATH, true, entries);
        entries.sort(Comparator.comparingInt(BrowserKitEntry::guiSlot));
        return entries;
    }

    private void collectBrowserEntries(FFAMode mode, String path, boolean premium, List<BrowserKitEntry> entries) {
        ConfigurationSection root = mode.getFiles().getGuiConfig().getConfigurationSection(path);
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            int layoutSlot = section.getInt("layout-slot", parseIntSafe(key));
            int guiSlot = section.getInt("slot", -1);
            if (layoutSlot < 1 || guiSlot < 0) {
                continue;
            }
            entries.add(new BrowserKitEntry(
                key,
                path + "." + key,
                layoutSlot,
                guiSlot,
                premium,
                permissionNodes(section),
                section.getString(KitBrowserGui.LOCKED_MESSAGE_KEY, "premium-kit-locked")
            ));
        }
    }

    private BrowserKitEntry browserEntryAtSlot(FFAMode mode, int guiSlot) {
        return browserKitEntries(mode).stream()
            .filter(entry -> entry.guiSlot() == guiSlot)
            .findFirst()
            .orElse(null);
    }

    private BrowserKitEntry browserEntryForLayout(FFAMode mode, int layoutSlot) {
        return browserKitEntries(mode).stream()
            .filter(entry -> entry.layoutSlot() == layoutSlot)
            .findFirst()
            .orElse(null);
    }

    private boolean canAccessBrowserLayout(Player player, FFAMode mode, int layoutSlot) {
        BrowserKitEntry entry = browserEntryForLayout(mode, layoutSlot);
        return entry == null || canAccessBrowserEntry(player, entry);
    }

    private boolean canAccessBrowserEntry(Player player, BrowserKitEntry entry) {
        if (entry == null) {
            return false;
        }
        if (entry.permissions().isEmpty()) {
            return true;
        }
        return entry.permissions().stream().anyMatch(player::hasPermission);
    }

    private List<String> permissionNodes(ConfigurationSection section) {
        List<String> permissions = new ArrayList<>();
        permissions.addAll(section.getStringList(KitBrowserGui.PERMISSIONS_KEY));
        if (permissions.isEmpty()) {
            String inlinePermissions = section.getString(KitBrowserGui.PERMISSIONS_KEY, "");
            if (!inlinePermissions.isBlank()) {
                permissions.add(inlinePermissions);
            }
        }
        String permission = section.getString(KitBrowserGui.PERMISSION_KEY, "");
        if (!permission.isBlank()) {
            permissions.add(permission);
        }
        return permissions.stream()
            .filter(node -> node != null && !node.isBlank())
            .distinct()
            .toList();
    }

    private Map<String, String> browserPlaceholders(Player player, FFAMode mode, BrowserKitEntry entry) {
        PlayerKitLayout layout = plugin.getPlayerDataManager().getLayout(player.getUniqueId(), mode.getId(), entry.layoutSlot());
        FFAKitDefinition template = plugin.getKitManager().getDefinition(mode.getId(), layout.getTemplateId());
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("layout_name", layout.getName());
        placeholders.put("browser_name", browserEntryDisplayName(entry));
        placeholders.put("kit", browserEntryDisplayName(entry));
        placeholders.put("template", template == null ? "Custom" : template.getDisplayName());
        placeholders.put("template_id", layout.getTemplateId());
        placeholders.put("slot", Integer.toString(entry.layoutSlot()));
        placeholders.put("browser_slot", Integer.toString(entry.guiSlot()));
        placeholders.put("permissions", String.join(", ", entry.permissions()));
        return placeholders;
    }

    private String browserEntryDisplayName(BrowserKitEntry entry) {
        if (entry == null) {
            return "Kit";
        }
        if (!entry.premium()) {
            return "Kit " + entry.layoutSlot();
        }
        return "Premium Kit " + Math.max(1, entry.layoutSlot() - 3);
    }

    private ItemStack buildOptionalConfiguredItem(FFAMode mode, String path, Player player, Map<String, String> placeholders) {
        ConfigurationSection section = mode.getFiles().getGuiConfig().getConfigurationSection(path);
        return ItemStackUtil.fromSectionOrAir(section, replacer(player, mode, placeholders));
    }

    private int browserPremadeSlot(FFAMode mode) {
        ConfigurationSection section = mode.getFiles().getGuiConfig().getConfigurationSection(KitBrowserGui.PREMADE_PATH);
        if (section != null) {
            return section.getInt("slot", 38);
        }
        int legacy = mode.getFiles().getGuiConfig().getInt("GUI_KIT_BROWSER.premade-slot", -1);
        return legacy >= 0 ? legacy : 38;
    }

    private int browserClearSlot(FFAMode mode) {
        ConfigurationSection section = mode.getFiles().getGuiConfig().getConfigurationSection(KitBrowserGui.CLEAR_INVENTORY_PATH);
        if (section != null) {
            return section.getInt("slot", 42);
        }
        ConfigurationSection buttons = mode.getFiles().getGuiConfig().getConfigurationSection(KitBrowserGui.ID + ".buttons.clear_inventory");
        if (buttons != null) {
            return buttons.getInt("slot", 42);
        }
        return 42;
    }

    private ItemStack getLayoutItem(PlayerKitLayout layout, EditableSlot slot) {
        return switch (slot.type()) {
            case HOTBAR, CONTENT, EXTRA -> slot.index() < layout.getInventoryContents().size() ? layout.getInventoryContents().get(slot.index()) : null;
            case HELMET, CHESTPLATE, LEGGINGS, BOOTS -> slot.index() < layout.getArmorContents().size() ? layout.getArmorContents().get(slot.index()) : null;
            case OFFHAND -> layout.getOffhand();
        };
    }

    private void setLayoutItem(PlayerKitLayout layout, EditableSlot slot, ItemStack item) {
        ItemStack clone = cloneItem(item);
        switch (slot.type()) {
            case HOTBAR, CONTENT, EXTRA -> ensureSize(layout.getInventoryContents(), 36).set(slot.index(), clone);
            case HELMET, CHESTPLATE, LEGGINGS, BOOTS -> ensureSize(layout.getArmorContents(), 4).set(slot.index(), clone);
            case OFFHAND -> layout.setOffhand(clone);
        }
    }

    private List<ItemStack> ensureSize(List<ItemStack> items, int size) {
        while (items.size() < size) {
            items.add(null);
        }
        return items;
    }

    private void refreshEditorInventory(Player player, FFAMode mode, EditorSession session) {
        refreshEditorInventory(player, mode, session, player.getOpenInventory().getTopInventory());
    }

    private void refreshEditorInventory(Player player, FFAMode mode, EditorSession session, Inventory inventory) {
        if (!(inventory.getHolder(false) instanceof KitEditorGui)) {
            return;
        }
        renderEditorInventory(inventory, player, mode, session);
        player.updateInventory();
    }

    private void stashCursorIntoLayout(Player player, FFAMode mode, EditorSession session) {
        ItemStack cursor = sanitizeEditorItem(player.getItemOnCursor());
        if (ItemStackUtil.isAir(cursor)) {
            return;
        }
        for (EditableSlot slot : editableSlots(mode).values()) {
            if (!ItemStackUtil.isAir(getLayoutItem(session.workingCopy(), slot))) {
                continue;
            }
            setLayoutItem(session.workingCopy(), slot, cursor);
            player.setItemOnCursor(null);
            return;
        }
        player.setItemOnCursor(null);
    }

    private void syncSessionFromEditorInventory(EditorSession session, FFAMode mode, Inventory inventory) {
        if (inventory == null || !(inventory.getHolder(false) instanceof KitEditorGui)) {
            return;
        }
        for (EditableSlot editableSlot : editableSlots(mode).values()) {
            setLayoutItem(session.workingCopy(), editableSlot, sanitizeEditorItem(inventory.getItem(editableSlot.guiSlot())));
        }
    }

    private void clearWorkingCopy(PlayerKitLayout layout) {
        ensureSize(layout.getInventoryContents(), 36).replaceAll(ignored -> null);
        ensureSize(layout.getArmorContents(), 4).replaceAll(ignored -> null);
        layout.setOffhand(null);
    }

    private Function<String, String> replacer(Player player, FFAMode mode, Map<String, String> extraPlaceholders) {
        return input -> plugin.getConfigManager().color(plugin.getPlaceholderResolver().resolve(input, player, mode, extraPlaceholders));
    }

    private boolean isTopInventoryClick(InventoryClickEvent event) {
        return event.getClickedInventory() != null && event.getClickedInventory() == event.getView().getTopInventory();
    }

    private ItemStack cloneItem(ItemStack stack) {
        return ItemStackUtil.isAir(stack) ? null : stack.clone();
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private ItemStack createFallbackItem(Material material, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getConfigManager().color(displayName));
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(plugin.getConfigManager().color(line));
            }
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildEditorDisplayItem(Player player, FFAMode mode, EditorSession session, EditableSlot slot, ItemStack source) {
        Map<String, String> placeholders = slotPlaceholders(slot, session.placeholders());
        if (ItemStackUtil.isAir(source)) {
            return buildEmptySlotPreview(player, mode, slot, placeholders);
        }
        return buildOccupiedSlotPreview(player, mode, slot, placeholders, source);
    }

    private ItemStack buildEmptySlotPreview(Player player, FFAMode mode, EditableSlot slot, Map<String, String> placeholders) {
        ItemStack preview = ItemStackUtil.fromSection(emptyPreviewSection(mode, slot), replacer(player, mode, placeholders));
        if (ItemStackUtil.isAir(preview)) {
            return null;
        }
        ItemMeta meta = preview.getItemMeta();
        if (meta == null) {
            return preview;
        }
        meta.setDisplayName(replacer(player, mode, placeholders).apply(meta.hasDisplayName() ? meta.getDisplayName() : "&7Empty ({slot_name})"));
        meta.setLore(resolveSlotLore(mode, slot, "empty-lore", player, placeholders));
        markPreviewMeta(meta, true, 0);
        preview.setItemMeta(meta);
        return preview;
    }

    private ItemStack buildOccupiedSlotPreview(Player player, FFAMode mode, EditableSlot slot, Map<String, String> placeholders, ItemStack source) {
        ItemStack preview = source.clone();
        ItemMeta meta = preview.getItemMeta();
        if (meta == null) {
            return preview;
        }
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        List<String> previewLore = resolveSlotLore(mode, slot, "occupied-lore", player, placeholders);
        int appendedLoreCount = 0;
        if (!previewLore.isEmpty()) {
            if (!lore.isEmpty()) {
                lore.add(plugin.getConfigManager().color("&8"));
                appendedLoreCount++;
            }
            lore.addAll(previewLore);
            appendedLoreCount += previewLore.size();
            meta.setLore(lore);
        }
        markPreviewMeta(meta, false, appendedLoreCount);
        preview.setItemMeta(meta);
        return preview;
    }

    private ConfigurationSection emptyPreviewSection(FFAMode mode, EditableSlot slot) {
        ConfigurationSection root = mode.getFiles().getGuiConfig().getConfigurationSection("GUI_KIT_EDITOR.slot-preview.empty-items");
        if (root == null) {
            return null;
        }
        ConfigurationSection exact = root.getConfigurationSection(slot.type().configKey());
        return exact != null ? exact : root.getConfigurationSection("default");
    }

    private List<String> resolveSlotLore(FFAMode mode, EditableSlot slot, String sectionKey, Player player, Map<String, String> placeholders) {
        List<String> configured = mode.getFiles().getGuiConfig().getStringList("GUI_KIT_EDITOR.slot-preview." + sectionKey + "." + slot.type().configKey());
        if (configured.isEmpty()) {
            configured = mode.getFiles().getGuiConfig().getStringList("GUI_KIT_EDITOR.slot-preview." + sectionKey + ".default");
        }
        if (configured.isEmpty()) {
            return List.of();
        }
        List<String> resolved = new ArrayList<>();
        Function<String, String> replacer = replacer(player, mode, placeholders);
        for (String line : configured) {
            resolved.add(replacer.apply(line));
        }
        return resolved;
    }

    private Map<String, String> slotPlaceholders(EditableSlot slot, Map<String, String> basePlaceholders) {
        Map<String, String> placeholders = new LinkedHashMap<>(basePlaceholders);
        placeholders.put("slot_name", slot.info().displayName());
        placeholders.put("slot_type", slot.info().typeId());
        placeholders.put("slot_index", Integer.toString(slot.info().index()));
        return placeholders;
    }

    private Material fallbackPreviewMaterial(EditableSlot slot) {
        return switch (slot.type()) {
            case HELMET, CHESTPLATE, LEGGINGS, BOOTS -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case EXTRA -> Material.ORANGE_STAINED_GLASS_PANE;
            case OFFHAND -> Material.GRAY_STAINED_GLASS_PANE;
            case HOTBAR, CONTENT -> Material.LIGHT_GRAY_STAINED_GLASS_PANE;
        };
    }
    private void markPreviewMeta(ItemMeta meta, boolean placeholder, int appendedLoreCount) {
        meta.getPersistentDataContainer().set(editorPreviewKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(editorPlaceholderKey, PersistentDataType.BYTE, placeholder ? (byte) 1 : (byte) 0);
        if (appendedLoreCount > 0) {
            meta.getPersistentDataContainer().set(editorPreviewLoreCountKey, PersistentDataType.INTEGER, appendedLoreCount);
        } else {
            meta.getPersistentDataContainer().remove(editorPreviewLoreCountKey);
        }
    }

    private ItemStack sanitizeEditorItem(ItemStack stack) {
        if (ItemStackUtil.isAir(stack)) {
            return null;
        }
        ItemStack clone = stack.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(editorPreviewKey, PersistentDataType.BYTE)) {
            return clone;
        }
        boolean placeholder = meta.getPersistentDataContainer().getOrDefault(editorPlaceholderKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
        if (placeholder) {
            return null;
        }
        int appendedLoreCount = meta.getPersistentDataContainer().getOrDefault(editorPreviewLoreCountKey, PersistentDataType.INTEGER, 0);
        if (meta.hasLore() && appendedLoreCount > 0) {
            List<String> lore = new ArrayList<>(meta.getLore());
            if (lore.size() >= appendedLoreCount) {
                lore = new ArrayList<>(lore.subList(0, lore.size() - appendedLoreCount));
            }
            meta.setLore(lore.isEmpty() ? null : lore);
        }
        meta.getPersistentDataContainer().remove(editorPreviewKey);
        meta.getPersistentDataContainer().remove(editorPlaceholderKey);
        meta.getPersistentDataContainer().remove(editorPreviewLoreCountKey);
        clone.setItemMeta(meta);
        return clone;
    }

    private ItemStack takeHalf(ItemStack current) {
        if (ItemStackUtil.isAir(current)) {
            return null;
        }
        int takenAmount = (current.getAmount() + 1) / 2;
        ItemStack taken = current.clone();
        taken.setAmount(takenAmount);
        int remaining = current.getAmount() - takenAmount;
        if (remaining <= 0) {
            current.setAmount(0);
        } else {
            current.setAmount(remaining);
        }
        return taken;
    }

    private ItemStack splitSingle(ItemStack cursor) {
        ItemStack placed = cursor.clone();
        placed.setAmount(1);
        return placed;
    }

    private void updateCursorAfterPlaceOne(Player player, ItemStack cursor) {
        int remaining = cursor.getAmount() - 1;
        if (remaining <= 0) {
            player.setItemOnCursor(null);
            return;
        }
        ItemStack updated = cursor.clone();
        updated.setAmount(remaining);
        player.setItemOnCursor(updated);
    }

    private boolean canStack(ItemStack target, ItemStack cursor) {
        return !ItemStackUtil.isAir(target)
            && !ItemStackUtil.isAir(cursor)
            && target.isSimilar(cursor)
            && target.getAmount() < target.getMaxStackSize();
    }

    private int moveToStack(ItemStack target, ItemStack cursor, int requestedAmount) {
        if (!canStack(target, cursor)) {
            return 0;
        }
        int transferable = Math.min(requestedAmount, Math.min(cursor.getAmount(), target.getMaxStackSize() - target.getAmount()));
        target.setAmount(target.getAmount() + transferable);
        return transferable;
    }

    private void updateCursorAfterTransfer(Player player, ItemStack cursor, int moved) {
        int remaining = cursor.getAmount() - moved;
        if (remaining <= 0) {
            player.setItemOnCursor(null);
            return;
        }
        ItemStack updated = cursor.clone();
        updated.setAmount(remaining);
        player.setItemOnCursor(updated);
    }

    private enum EditableType {
        HOTBAR("hotbar"),
        CONTENT("content"),
        EXTRA("extra"),
        HELMET("helmet"),
        CHESTPLATE("chestplate"),
        LEGGINGS("leggings"),
        BOOTS("boots"),
        OFFHAND("offhand");

        private final String configKey;

        EditableType(String configKey) {
            this.configKey = configKey;
        }

        public String configKey() {
            return configKey;
        }
    }

    private record SlotInfo(String displayName, String typeId, int index) {
    }

    private record EditableSlot(int guiSlot, String key, EditableType type, int index, SlotInfo info) {
    }

    private record BrowserKitEntry(
        String id,
        String configPath,
        int layoutSlot,
        int guiSlot,
        boolean premium,
        List<String> permissions,
        String lockedMessage
    ) {
    }

    private static final class EditorSession {

        private final String modeId;
        private final int layoutSlot;
        private PlayerKitLayout workingCopy;

        private EditorSession(String modeId, int layoutSlot, PlayerKitLayout workingCopy) {
            this.modeId = modeId;
            this.layoutSlot = layoutSlot;
            this.workingCopy = workingCopy;
        }

        public boolean matches(String modeId, int layoutSlot) {
            return this.layoutSlot == layoutSlot && this.modeId.equalsIgnoreCase(modeId);
        }

        public int layoutSlot() {
            return layoutSlot;
        }

        public PlayerKitLayout workingCopy() {
            return workingCopy;
        }

        public void replaceWorkingCopy(PlayerKitLayout workingCopy) {
            this.workingCopy = workingCopy;
        }

        public Map<String, String> placeholders() {
            return Map.of(
                "layout_name", workingCopy.getName(),
                "slot", Integer.toString(layoutSlot)
            );
        }
    }
}
