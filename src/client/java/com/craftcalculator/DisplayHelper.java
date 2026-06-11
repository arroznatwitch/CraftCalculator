package com.craftcalculator;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

/**
 * Handles all chat output and display-name resolution for the CraftCalculator command.
 */
public final class DisplayHelper {

    private static final String MODID = "craft_calculator";

    private static final String SEPARATOR = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

    /** Number of items that fit in one Shulker Box (27 slots × 64 per stack). */
    private static final int SHULKER_CAPACITY = 27 * 64;

    // ─── Public API ───────────────────────────────────────────────────────────────

    /**
     * Sends the full calculation result to the player's chat.
     *
     * @param source   Command source (the player)
     * @param item     The item that was calculated
     * @param amount   The requested quantity
     * @param totals   Map of ingredient key → quantity needed
     */
    public static void sendResult(FabricClientCommandSource source, Item item, int amount, Map<String, Long> totals) {
        String itemName = item.getName(new ItemStack(item)).getString();

        sendLine(source, SEPARATOR, ChatFormatting.YELLOW);
        source.sendFeedback(
                Component.translatable(MODID + ".result.header", amount, itemName)
                        .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        sendLine(source, SEPARATOR, ChatFormatting.YELLOW);
        source.sendFeedback(Component.literal(""));

        // Sort alphabetically by display name (what the player sees)
        totals.entrySet().stream()
                .sorted(Comparator.comparing(e -> resolveDisplayName(e.getKey())))
                .forEach(e -> {
                    String name     = resolveDisplayName(e.getKey());
                    long   qty      = e.getValue();
                    String packInfo = computePackInfo(e.getKey(), qty);
                    String line     = "  ✦ " + qty + "x " + name + (packInfo.isEmpty() ? "" : " " + packInfo);
                    source.sendFeedback(Component.literal(line).withStyle(ChatFormatting.YELLOW));
                });

        source.sendFeedback(Component.literal(""));
        sendLine(source, SEPARATOR, ChatFormatting.YELLOW);
    }

    /** Sends a red error message to the player. */
    public static void sendError(FabricClientCommandSource source, Component msg) {
        source.sendFeedback(msg.copy().withStyle(ChatFormatting.RED));
    }

    // ─── Display name resolution ──────────────────────────────────────────────────

    /**
     * Returns the human-readable name for an ingredient key or sentinel.
     */
    public static String resolveDisplayName(String key) {
        return switch (key) {
            case WoodGroups.KEY_ANY_WOOD_LOG    -> Component.translatable(MODID + ".group.any_wood_log").getString();
            case WoodGroups.KEY_ANY_WOOD_PLANKS -> Component.translatable(MODID + ".group.any_wood").getString();
            case WoodGroups.KEY_ANY_STICK       -> Component.translatable(MODID + ".group.any_stick").getString();
            case WoodGroups.KEY_OAK_WOOD        -> WoodGroups.WOOD_TYPE_DISPLAY.get("oak")      + " (Any Logs/Planks)";
            case WoodGroups.KEY_SPRUCE_WOOD     -> WoodGroups.WOOD_TYPE_DISPLAY.get("spruce")   + " (Any Logs/Planks)";
            case WoodGroups.KEY_BIRCH_WOOD      -> WoodGroups.WOOD_TYPE_DISPLAY.get("birch")    + " (Any Logs/Planks)";
            case WoodGroups.KEY_JUNGLE_WOOD     -> WoodGroups.WOOD_TYPE_DISPLAY.get("jungle")   + " (Any Logs/Planks)";
            case WoodGroups.KEY_ACACIA_WOOD     -> WoodGroups.WOOD_TYPE_DISPLAY.get("acacia")   + " (Any Logs/Planks)";
            case WoodGroups.KEY_DARK_OAK_WOOD   -> WoodGroups.WOOD_TYPE_DISPLAY.get("dark_oak") + " (Any Logs/Planks)";
            case WoodGroups.KEY_MANGROVE_WOOD   -> WoodGroups.WOOD_TYPE_DISPLAY.get("mangrove") + " (Any Logs/Planks)";
            case WoodGroups.KEY_CHERRY_WOOD     -> WoodGroups.WOOD_TYPE_DISPLAY.get("cherry")   + " (Any Logs/Planks)";
            case WoodGroups.KEY_BAMBOO_WOOD     -> WoodGroups.WOOD_TYPE_DISPLAY.get("bamboo")   + " (Any Logs/Planks)";
            case WoodGroups.KEY_CRIMSON_WOOD    -> WoodGroups.WOOD_TYPE_DISPLAY.get("crimson")  + " (Any Stems/Hyphae)";
            case WoodGroups.KEY_WARPED_WOOD     -> WoodGroups.WOOD_TYPE_DISPLAY.get("warped")   + " (Any Stems/Hyphae)";
            default -> {
                Item item = RecipeCalculator.resolveItem(key);
                yield item != null ? item.getName(new ItemStack(item)).getString() : key;
            }
        };
    }

    // ─── Shulker Box info ─────────────────────────────────────────────────────────

    /**
     * Returns a "[X.X SB]" string if {@code qty} is large enough to fill at least one Shulker Box,
     * or an empty string otherwise.
     */
    private static String computePackInfo(String key, long qty) {
        if (WoodGroups.isSentinel(key) || RecipeCalculator.resolveItem(key) == null) return "";
        if (qty < SHULKER_CAPACITY) return "";
        return "[" + String.format(Locale.ROOT, "%.1f", (double) qty / SHULKER_CAPACITY) + " SB]";
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────────

    private static void sendLine(FabricClientCommandSource source, String text, ChatFormatting color) {
        source.sendFeedback(Component.literal(text).withStyle(color));
    }

    private DisplayHelper() {}
}
