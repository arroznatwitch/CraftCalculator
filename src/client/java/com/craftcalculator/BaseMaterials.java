package com.craftcalculator;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Set;

/**
 * Defines the set of items that are treated as raw/leaf materials —
 * they are never expanded further in the recipe tree.
 */
public final class BaseMaterials {

    public static final Set<Item> ALL = Set.of(
            // All plank types
            Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS,
            Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS,
            Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS, Items.BAMBOO_PLANKS,
            Items.CRIMSON_PLANKS, Items.WARPED_PLANKS,
            // Sticks
            Items.STICK, Items.BAMBOO,
            // Logs and wood blocks
            Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG,
            Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.DARK_OAK_LOG,
            Items.MANGROVE_LOG, Items.CHERRY_LOG, Items.BAMBOO_BLOCK,
            Items.CRIMSON_STEM, Items.WARPED_STEM,
            Items.STRIPPED_OAK_LOG, Items.STRIPPED_SPRUCE_LOG, Items.STRIPPED_BIRCH_LOG,
            Items.STRIPPED_JUNGLE_LOG, Items.STRIPPED_ACACIA_LOG, Items.STRIPPED_DARK_OAK_LOG,
            Items.STRIPPED_MANGROVE_LOG, Items.STRIPPED_CHERRY_LOG,
            Items.OAK_WOOD, Items.SPRUCE_WOOD, Items.BIRCH_WOOD,
            Items.JUNGLE_WOOD, Items.ACACIA_WOOD, Items.DARK_OAK_WOOD,
            Items.MANGROVE_WOOD, Items.CHERRY_WOOD,
            Items.CRIMSON_HYPHAE, Items.WARPED_HYPHAE,
            // Ores
            Items.IRON_ORE, Items.COPPER_ORE, Items.COAL_ORE, Items.GOLD_ORE,
            Items.LAPIS_ORE, Items.REDSTONE_ORE, Items.DIAMOND_ORE, Items.EMERALD_ORE,
            // Basic blocks
            Items.COBBLESTONE, Items.STONE, Items.DIRT, Items.SAND,
            // Ingots and gems
            Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT,
            Items.DIAMOND, Items.EMERALD, Items.LAPIS_LAZULI
    );

    private BaseMaterials() {}
}
