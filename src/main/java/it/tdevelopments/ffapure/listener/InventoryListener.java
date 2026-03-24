package it.tdevelopments.ffapure.listener;

import it.tdevelopments.ffapure.FFAPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class InventoryListener implements Listener {

    private final FFAPlugin plugin;

    public InventoryListener(FFAPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        plugin.getKitEditorManager().handleClick(event);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        plugin.getKitEditorManager().handleClose(event);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        plugin.getKitEditorManager().handleDrag(event);
    }
}
