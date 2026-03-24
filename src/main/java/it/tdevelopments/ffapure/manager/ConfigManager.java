package it.tdevelopments.ffapure.manager;

import it.tdevelopments.ffapure.FFAPlugin;
import it.tdevelopments.ffapure.model.FFAMode;
import it.tdevelopments.ffapure.util.ColorUtil;
import java.io.File;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class ConfigManager {

    private final FFAPlugin plugin;
    private FileConfiguration messages;

    public ConfigManager(FFAPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        this.messages = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    public String color(String input) {
        return ColorUtil.colorize(input);
    }

    public String applyPlaceholders(String input, Map<String, String> placeholders) {
        return color(plugin.getPlaceholderResolver().resolve(input, null, null, placeholders));
    }

    public String message(String key, Map<String, String> placeholders) {
        String raw = messages.getString(key, "&cMissing message: " + key);
        java.util.Map<String, String> merged = new java.util.LinkedHashMap<>(placeholders == null ? java.util.Map.of() : placeholders);
        merged.put("prefix", messages.getString("prefix", "&8[&6FFA&8] &r"));
        return applyPlaceholders(raw, merged);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(message(key, placeholders));
    }

    public Map<String, String> placeholders(Player player, FFAMode mode) {
        return plugin.getPlaceholderResolver().create(player, mode);
    }

    public void broadcastMode(FFAMode mode, String key, Map<String, String> placeholders) {
        String rendered = message(key, placeholders);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getPlayerStateManager().isPlayerInFFA(player)
                && mode != null
                && mode.getId().equalsIgnoreCase(plugin.getPlayerStateManager().getModeId(player.getUniqueId()))) {
                player.sendMessage(rendered);
            }
        }
    }
}
