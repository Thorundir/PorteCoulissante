package org.reliqcraft;

import java.util.Set;
import org.bukkit.Material;

/**
 * MaterialGroups - Centralized definitions of default material sets used
 * throughout the plugin. Improves readability, consistency, and teachability.
 */
public class MaterialGroups {

    /**
     * Default materials allowed for portcullis gates.
     * Includes fences and iron bars from Minecraft 1.20.2.
     */
    public static final Set<Material> PORTCULLIS = Set.of(
            Material.OAK_FENCE,
            Material.SPRUCE_FENCE,
            Material.BIRCH_FENCE,
            Material.JUNGLE_FENCE,
            Material.ACACIA_FENCE,
            Material.DARK_OAK_FENCE,
            Material.MANGROVE_FENCE,
            Material.BAMBOO_FENCE,
            Material.CRIMSON_FENCE,
            Material.WARPED_FENCE,
            Material.NETHER_BRICK_FENCE,
            Material.IRON_BARS);

    /**
     * Default materials considered conductive for redstone activation.
     * Includes buttons, plates, sensors, and redstone components.
     */
    public static final Set<Material> CONDUCTIVE = Set.of(
            Material.COAL_BLOCK,
            Material.IRON_BLOCK,
            Material.GOLD_BLOCK,
            Material.COPPER_BLOCK,
            Material.NETHERITE_BLOCK);

    /**
     * Default materials allowed for wall frames.
     * Includes all solid blocks typically used for structure.
     * This set can be overridden or extended via config.
     */
    public static final Set<Material> SUPPORTING = Set.of(
            Material.STONE,
            Material.COBBLESTONE,
            Material.ANDESITE,
            Material.GRANITE,
            Material.DIORITE,
            Material.DEEPSLATE,
            Material.BLACKSTONE,
            Material.END_STONE,
            Material.NETHER_BRICKS,
            Material.BRICKS,
            Material.MOSSY_COBBLESTONE,
            Material.POLISHED_ANDESITE,
            Material.POLISHED_DIORITE,
            Material.POLISHED_GRANITE,
            Material.POLISHED_BLACKSTONE,
            Material.POLISHED_DEEPSLATE,
            Material.SMOOTH_STONE,
            Material.SMOOTH_QUARTZ,
            Material.SMOOTH_BASALT,
            Material.QUARTZ_BLOCK,
            Material.PURPUR_BLOCK,
            Material.CRYING_OBSIDIAN,
            Material.OBSIDIAN,
            Material.BASALT,
            Material.TUFF);

    private MaterialGroups() {
        // Utility class - no instantiation
    }
}