package com.craftcalculator;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
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

/**
 * Entry point for the CraftCalculator mod.
 * Command syntax: /cc <item> <amount>
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
                                .then(ClientCommands.argument("item", ItemArgumentType.itemArg())
                                        .suggests((ctx, builder) -> ItemArgumentType.itemArg().listSuggestions(ctx, builder))
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

        String itemStr = ItemArgumentType.getItem(ctx, "item").trim();
        int    amount  = IntegerArgumentType.getInteger(ctx, "amount");

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

        Map<String, Long> totals = new HashMap<>(req.rawMaterials);
        req.craftItems.forEach((k, v) -> totals.merge(k, v, Long::sum));

        DisplayHelper.sendResult(ctx.getSource(), item, amount, totals);
        return Command.SINGLE_SUCCESS;
    }
}