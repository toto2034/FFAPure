package it.tdevelopments.ffapure.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

public final class ItemStackUtil {

    private ItemStackUtil() {
    }

    public static ItemStack fromSection(ConfigurationSection section, java.util.function.Function<String, String> replacer) {
        if (section == null) {
            return new ItemStack(Material.AIR);
        }
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null) {
            material = Material.STONE;
        }
        ItemStack item = new ItemStack(material, Math.max(1, section.getInt("amount", 1)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (section.contains("name")) {
                meta.setDisplayName(replacer.apply(section.getString("name", "")));
            }
            List<String> lore = section.getStringList("lore");
            if (!lore.isEmpty()) {
                List<String> translated = new ArrayList<>();
                for (String line : lore) {
                    translated.add(replacer.apply(line));
                }
                meta.setLore(translated);
            }
            int modelData = section.contains("model_data") ? section.getInt("model_data") : section.getInt("custom-model-data", Integer.MIN_VALUE);
            if (modelData != Integer.MIN_VALUE) {
                meta.setCustomModelData(modelData);
            }
            meta.setUnbreakable(section.getBoolean("unbreakable", false));
            List<String> flags = new ArrayList<>(section.getStringList("item-flags"));
            flags.addAll(section.getStringList("flags"));
            for (String flag : flags) {
                try {
                    meta.addItemFlags(ItemFlag.valueOf(flag.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            ConfigurationSection enchants = section.getConfigurationSection("enchantments");
            if (enchants != null) {
                for (String key : enchants.getKeys(false)) {
                    Enchantment enchantment = resolveEnchantment(key);
                    if (enchantment != null) {
                        meta.addEnchant(enchantment, enchants.getInt(key), true);
                    } else {
                        Bukkit.getLogger().warning("[FFAPure] Invalid enchantment '" + key + "' in item config path " + section.getCurrentPath());
                    }
                }
            }
            if (section.getBoolean("glow", false)) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            if (meta instanceof LeatherArmorMeta leather && section.isConfigurationSection("color")) {
                ConfigurationSection colorSection = section.getConfigurationSection("color");
                leather.setColor(Color.fromRGB(colorSection.getInt("r"), colorSection.getInt("g"), colorSection.getInt("b")));
            }
            if (meta instanceof PotionMeta potionMeta && section.isConfigurationSection("potion")) {
                ConfigurationSection potion = section.getConfigurationSection("potion");
                if (potion.contains("base-type")) {
                    try {
                        potionMeta.setBasePotionType(PotionType.valueOf(potion.getString("base-type").toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (potion.isConfigurationSection("custom-effect")) {
                    ConfigurationSection effect = potion.getConfigurationSection("custom-effect");
                    PotionEffectType type = PotionEffectType.getByName(effect.getString("type", ""));
                    if (type != null) {
                        potionMeta.addCustomEffect(new PotionEffect(type, effect.getInt("duration", 200), effect.getInt("amplifier", 0)), true);
                    }
                }
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack fromSectionOrAir(ConfigurationSection section, java.util.function.Function<String, String> replacer) {
        if (!hasConfiguredItem(section)) {
            return new ItemStack(Material.AIR);
        }
        return fromSection(section, replacer);
    }

    public static boolean hasConfiguredItem(ConfigurationSection section) {
        return section != null && (
            section.contains("material")
                || section.contains("name")
                || section.contains("lore")
                || section.contains("amount")
                || section.contains("model_data")
                || section.contains("custom-model-data")
                || section.contains("glow")
                || section.contains("enchantments")
                || section.contains("unbreakable")
                || section.contains("color")
                || section.contains("potion")
                || section.contains("item-flags")
                || section.contains("flags")
        );
    }

    public static ItemStack cloneOrAir(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }

    public static boolean isAir(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR;
    }

    public static List<ItemStack> cloneList(List<ItemStack> source, int size) {
        List<ItemStack> clone = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            ItemStack stack = index < source.size() ? source.get(index) : null;
            clone.add(stack == null ? null : stack.clone());
        }
        return clone;
    }

    public static String titleCase(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String[] parts = input.replace('-', ' ').replace('_', ' ').split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT))
                .append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }

    private static Enchantment resolveEnchantment(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Enchantment direct = Enchantment.getByName(raw.toUpperCase(Locale.ROOT));
        if (direct != null) {
            return direct;
        }
        String normalized = raw.toLowerCase(Locale.ROOT).replace(' ', '_');
        Enchantment namespaced = Enchantment.getByKey(NamespacedKey.minecraft(normalized));
        if (namespaced != null) {
            return namespaced;
        }
        if (normalized.startsWith("minecraft:")) {
            return Enchantment.getByKey(NamespacedKey.fromString(normalized));
        }
        return Enchantment.getByKey(NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT)));
    }
}
