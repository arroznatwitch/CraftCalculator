package com.craftcalculator;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Entry point for the CraftCalculator mod.
 * Registers the /cc command and its aliases.
 * All calculation logic lives in {@link RecipeCalculator}.
 * All display logic lives in {@link DisplayHelper}.
 */
public final class CraftCalculatorMod implements ClientModInitializer {

    private static final String MODID = "craft_calculator";

    private static final SimpleCommandExceptionType ERR_INVALID_ITEM = new SimpleCommandExceptionType(
            Component.translatable(MODID + ".error.invalid_item"));
    private static final SimpleCommandExceptionType ERR_SINGLEPLAYER = new SimpleCommandExceptionType(
            Component.translatable(MODID + ".error.singleplayer_only"));
    private static final SimpleCommandExceptionType ERR_NO_WORLD = new SimpleCommandExceptionType(
            Component.translatable(MODID + ".error.no_world"));

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            for (String alias : new String[]{"cc", "craftcalculator", "craftc"}) {
                dispatcher.register(
                        ClientCommands.literal(alias)
                                .then(ClientCommands.argument("item", StringArgumentType.string())
                                        .suggests(CraftCalculatorMod::suggestItems)
                                        .then(ClientCommands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(CraftCalculatorMod::execute))));
            }
        });
    }

    // ─── Command handler ──────────────────────────────────────────────────────────

    private static int execute(CommandContext<FabricClientCommandSource> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) throw ERR_NO_WORLD.create();
        if (!mc.isLocalServer())                    throw ERR_SINGLEPLAYER.create();
        if (mc.getSingleplayerServer() == null)     throw ERR_SINGLEPLAYER.create();

        String itemStr = StringArgumentType.getString(ctx, "item").trim();
        int    amount  = IntegerArgumentType.getInteger(ctx, "amount");

        // Allow bare names without namespace (e.g. "oak_planks" → "minecraft:oak_planks")
        Identifier id = Identifier.tryParse(itemStr);
        if (id == null && !itemStr.contains(":")) id = Identifier.tryParse("minecraft:" + itemStr);
        if (id == null) throw ERR_INVALID_ITEM.create();

        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null || item == Items.AIR) throw ERR_INVALID_ITEM.create();

        RecipeManager rm = mc.getSingleplayerServer().getRecipeManager();
        Map<Item, CraftingRecipe> recipeIndex = RecipeCalculator.buildRecipeIndex(rm);

        RecipeCalculator.Requirements req = new RecipeCalculator.Requirements();
        boolean hadRecipe = RecipeCalculator.collectRequirements(
                RecipeCalculator.itemKey(item), amount, recipeIndex, req, new HashSet<>(), true);

        if (!hadRecipe) {
            DisplayHelper.sendError(ctx.getSource(), Component.translatable(MODID + ".error.no_recipe"));
            return 0;
        }

        // Merge craftable sub-ingredients into the totals map
        Map<String, Long> totals = new HashMap<>(req.rawMaterials);
        req.craftItems.forEach((k, v) -> totals.merge(k, v, Long::sum));

        DisplayHelper.sendResult(ctx.getSource(), item, amount, totals);
        return Command.SINGLE_SUCCESS;
    }

    // ─── Tab-completion ───────────────────────────────────────────────────────────

    private static CompletableFuture<Suggestions> suggestItems(
            CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        if (remaining.indexOf(' ') >= 0) return builder.buildFuture();
        for (Identifier key : BuiltInRegistries.ITEM.keySet()) {
            String v = key.toString();
            if (v.startsWith(remaining) || v.contains(":" + remaining)) builder.suggest(v);
        }
        return builder.buildFuture();
    }
}
