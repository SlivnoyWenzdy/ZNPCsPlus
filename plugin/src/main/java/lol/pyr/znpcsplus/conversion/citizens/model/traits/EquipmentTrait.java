package lol.pyr.znpcsplus.conversion.citizens.model.traits;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.google.common.io.BaseEncoding;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import lol.pyr.znpcsplus.api.entity.EntityProperty;
import lol.pyr.znpcsplus.api.entity.EntityPropertyRegistry;
import lol.pyr.znpcsplus.conversion.citizens.model.SectionCitizensTrait;
import lol.pyr.znpcsplus.npc.NpcImpl;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class EquipmentTrait extends SectionCitizensTrait {
    private static final Logger LOGGER = Logger.getLogger(EquipmentTrait.class.getName());
    private final EntityPropertyRegistry propertyRegistry;
    private final HashMap<String, EquipmentSlot> EQUIPMENT_SLOT_MAP = new HashMap<>();

    public EquipmentTrait(EntityPropertyRegistry propertyRegistry) {
        super("equipment");
        this.propertyRegistry = propertyRegistry;
        EQUIPMENT_SLOT_MAP.put("hand", EquipmentSlot.MAIN_HAND);
        EQUIPMENT_SLOT_MAP.put("offhand", EquipmentSlot.OFF_HAND);
        EQUIPMENT_SLOT_MAP.put("helmet", EquipmentSlot.HELMET);
        EQUIPMENT_SLOT_MAP.put("chestplate", EquipmentSlot.CHEST_PLATE);
        EQUIPMENT_SLOT_MAP.put("leggings", EquipmentSlot.LEGGINGS);
        EQUIPMENT_SLOT_MAP.put("boots", EquipmentSlot.BOOTS);
    }

    @Override
    public @NotNull NpcImpl apply(NpcImpl npc, ConfigurationSection section) {
        if (npc == null || section == null) return npc;

        for (String key : section.getKeys(false)) {
            EquipmentSlot slot = EQUIPMENT_SLOT_MAP.get(key);
            if (slot == null) {
                continue;
            }

            ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection == null) {
                continue;
            }

            try {
                ItemStack itemStack = parseItemStack(itemSection);
                if (itemStack == null) {
                    continue;
                }

                EntityProperty<ItemStack> property = propertyRegistry.getByName(key, ItemStack.class);
                if (property == null) {
                    LOGGER.warning("[ZNPCsPlus] Equipment property not found: " + key);
                    continue;
                }

                npc.setProperty(property, itemStack);
            } catch (Exception e) {
                LOGGER.warning("[ZNPCsPlus] Failed to parse equipment slot " + key + ": " + e.getMessage());
            }
        }

        return npc;
    }

    private ItemStack parseItemStack(ConfigurationSection section) {
        if (section == null) return null;

        Material material = null;
        if (section.isString("type_key")) {
            material = Material.getMaterial(section.getString("type_key").toUpperCase());
        } else if (section.isString("type")) {
            material = Material.matchMaterial(section.getString("type").toUpperCase());
        } else if (section.isString("id")) {
            material = Material.matchMaterial(section.getString("id").toUpperCase());
        }

        if (material == null || material == Material.AIR) {
            return null;
        }

        try {
            org.bukkit.inventory.ItemStack itemStack = new org.bukkit.inventory.ItemStack(
                    material,
                    Math.max(1, section.getInt("amount", 1)),
                    (short) section.getInt("durability", section.getInt("data", 0))
            );

            if (section.isInt("mdata")) {
                itemStack.getData().setData((byte) section.getInt("mdata"));
            }

            if (section.isConfigurationSection("enchantments")) {
                ConfigurationSection enchantments = section.getConfigurationSection("enchantments");
                if (enchantments != null) {
                    itemStack.addUnsafeEnchantments(deserializeEnchantments(enchantments));
                }
            }

            if (section.isConfigurationSection("meta")) {
                ConfigurationSection metaSection = section.getConfigurationSection("meta");
                if (metaSection != null) {
                    ItemMeta itemMeta = deserializeMeta(metaSection);
                    if (itemMeta != null) {
                        itemStack.setItemMeta(itemMeta);
                    }
                }
            }

            ItemStack result = SpigotConversionUtil.fromBukkitItemStack(itemStack);
            return result != null ? result : ItemStack.EMPTY;
        } catch (Exception e) {
            LOGGER.warning("[ZNPCsPlus] Failed to create ItemStack for " + material + ": " + e.getMessage());
            return null;
        }
    }

    private Map<Enchantment, Integer> deserializeEnchantments(ConfigurationSection section) {
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        if (section == null) return enchantments;

        for (String key : section.getKeys(false)) {
            try {
                Enchantment enchantment = Enchantment.getByName(key);
                if (enchantment == null) {
                    continue;
                }
                enchantments.put(enchantment, section.getInt(key));
            } catch (Exception e) {
                LOGGER.warning("[ZNPCsPlus] Failed to parse enchantment: " + key);
            }
        }
        return enchantments;
    }

    private ItemMeta deserializeMeta(ConfigurationSection section) {
        if (section == null) return null;

        if (section.isString("encoded-meta")) {
            String encoded = section.getString("encoded-meta");
            if (encoded == null || encoded.isEmpty()) return null;

            try {
                byte[] raw = BaseEncoding.base64().decode(encoded);
                BukkitObjectInputStream inp = new BukkitObjectInputStream(new ByteArrayInputStream(raw));
                ItemMeta meta = (ItemMeta) inp.readObject();
                inp.close();
                return meta;
            } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
                LOGGER.warning("[ZNPCsPlus] Failed to decode item meta: " + e.getMessage());
            }
        }
        return null;
    }
}