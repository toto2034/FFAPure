package it.tdevelopments.ffapure.model;

import org.bukkit.inventory.ItemStack;

public record GUIButton(String id, int slot, String action, String permission, ItemStack itemStack) {
}
