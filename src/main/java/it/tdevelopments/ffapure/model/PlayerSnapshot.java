package it.tdevelopments.ffapure.model;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public record PlayerSnapshot(
    ItemStack[] contents,
    ItemStack[] armor,
    ItemStack offhand,
    double health,
    int food,
    float saturation,
    int level,
    float exp,
    GameMode gameMode,
    Location location
) {

    public static PlayerSnapshot capture(Player player) {
        return new PlayerSnapshot(
            player.getInventory().getContents(),
            player.getInventory().getArmorContents(),
            player.getInventory().getItemInOffHand(),
            player.getHealth(),
            player.getFoodLevel(),
            player.getSaturation(),
            player.getLevel(),
            player.getExp(),
            player.getGameMode(),
            player.getLocation()
        );
    }

    public void restore(Player player) {
        restore(player, true);
    }

    public void restore(Player player, boolean restoreLocation) {
        player.getInventory().setContents(contents);
        player.getInventory().setArmorContents(armor);
        player.getInventory().setItemInOffHand(offhand);
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            player.setHealth(Math.min(health, player.getAttribute(Attribute.MAX_HEALTH).getValue()));
        }
        player.setFoodLevel(food);
        player.setSaturation(saturation);
        player.setLevel(level);
        player.setExp(exp);
        player.setGameMode(gameMode);
        if (restoreLocation && location != null) {
            player.teleport(location);
        }
    }
}
