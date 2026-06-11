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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.util.context.ContextMap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class CraftCalculatorMod implements ClientModInitializer {

    private static final String MODID = "craft_calculator";

    // ─── Sentinel keys for multi-choice ingredient groups ───────────────────────
    // Keys starting with "__" are never looked up in the item registry.

    private static final String KEY_ANY_WOOD_LOG    = "__any_wood_log__";
    private static final String KEY_ANY_WOOD_PLANKS = "__any_wood_planks__";
    private static final String KEY_ANY_STICK       = "__any_stick__";

    // Per-wood-type sentinels (used when a recipe restricts to a specific wood family)
    private static final String KEY_OAK_WOOD      = "__oak_wood__";
    private static final String KEY_SPRUCE_WOOD   = "__spruce_wood__";
    private static final String KEY_BIRCH_WOOD    = "__birch_wood__";
    private static final String KEY_JUNGLE_WOOD   = "__jungle_wood__";
    private static final String KEY_ACACIA_WOOD   = "__acacia_wood__";
    private static final String KEY_DARK_OAK_WOOD = "__dark_oak_wood__";
    private static final String KEY_MANGROVE_WOOD = "__mangrove_wood__";
    private static final String KEY_CHERRY_WOOD   = "__cherry_wood__";
    private static final String KEY_BAMBOO_WOOD   = "__bamboo_wood__";
    private static final String KEY_CRIMSON_WOOD  = "__crimson_wood__";
    private static final String KEY_WARPED_WOOD   = "__warped_wood__";

    // ─── Item group sets (populated in the static initializer) ──────────────────

    private static final Set<Item> WOOD_LOGS   = new HashSet<>();
    private static final Set<Item> WOOD_PLANKS = new HashSet<>();
    private static final Set<Item> STICK_ITEMS = new HashSet<>();

    /** Items that should never be expanded further — these are the leaf ingredients. */
    private static final Set<Item> BASE_MATERIALS = Set.of(
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

    private static final Map<String, Set<Item>> WOOD_TYPE_LOGS      = new HashMap<>();
    private static final Map<String, Set<Item>> WOOD_TYPE_PLANKS    = new HashMap<>();
    private static final Map<String, String>    WOOD_TYPE_SENTINELS = new HashMap<>();
    private static final Map<String, String>    WOOD_TYPE_DISPLAY   = new HashMap<>();

    static {
        String[] types = {"oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","bamboo","crimson","warped"};

        Map<String, String[]> exceptions = new HashMap<>();
        exceptions.put("bamboo",  new String[]{"BAMBOO_BLOCK", "BAMBOO_PLANKS", "BAMBOO"});
        exceptions.put("crimson", new String[]{"CRIMSON_STEM", "CRIMSON_PLANKS", "CRIMSON_HYPHAE"});
        exceptions.put("warped",  new String[]{"WARPED_STEM",  "WARPED_PLANKS",  "WARPED_HYPHAE"});

        for (String type : types) {
            Set<Item> logs   = new HashSet<>();
            Set<Item> planks = new HashSet<>();
            String upper = type.toUpperCase(Locale.ROOT);

            if (exceptions.containsKey(type)) {
                for (String fname : exceptions.get(type)) {
                    tryAddItem(fname, planks, logs);
                }
            } else {
                for (String ln : new String[]{upper + "_LOG", "STRIPPED_" + upper + "_LOG", upper + "_WOOD"}) {
                    tryAddItem(ln, null, logs);
                }
                tryAddItem(upper + "_PLANKS", planks, null);
            }

            if (!logs.isEmpty())   WOOD_TYPE_LOGS.put(type, Set.copyOf(logs));
            if (!planks.isEmpty()) WOOD_TYPE_PLANKS.put(type, Set.copyOf(planks));
            WOOD_LOGS.addAll(logs);
            WOOD_PLANKS.addAll(planks);

            String sentinel = switch (type) {
                case "oak"      -> KEY_OAK_WOOD;
                case "spruce"   -> KEY_SPRUCE_WOOD;
                case "birch"    -> KEY_BIRCH_WOOD;
                case "jungle"   -> KEY_JUNGLE_WOOD;
                case "acacia"   -> KEY_ACACIA_WOOD;
                case "dark_oak" -> KEY_DARK_OAK_WOOD;
                case "mangrove" -> KEY_MANGROVE_WOOD;
                case "cherry"   -> KEY_CHERRY_WOOD;
                case "bamboo"   -> KEY_BAMBOO_WOOD;
                case "crimson"  -> KEY_CRIMSON_WOOD;
                case "warped"   -> KEY_WARPED_WOOD;
                default         -> "__" + type + "_wood__";
            };
            WOOD_TYPE_SENTINELS.put(type, sentinel);

            String display = "dark_oak".equals(type)
                    ? "Dark Oak Wood"
                    : Character.toUpperCase(type.charAt(0)) + type.substring(1).replace('_', ' ') + " Wood";
            WOOD_TYPE_DISPLAY.put(type, display);
        }

        STICK_ITEMS.add(Items.STICK);
        STICK_ITEMS.add(Items.BAMBOO);
    }

    /** Helper to avoid repeating try/catch in the static block. */
    private static void tryAddItem(String fieldName, Set<Item> planks, Set<Item> logs) {
        try {
            Object v = Items.class.getField(fieldName).get(null);
            if (v instanceof Item it) {
                if (planks != null && fieldName.contains("PLANK")) planks.add(it);
                else if (logs != null) logs.add(it);
            }
        } catch (ReflectiveOperationException ignored) {}
    }

    // ─── Cached reflection handle for resolveForStacks ──────────────────────────
    // Looked up once per display type and reused to avoid per-ingredient overhead.
    private static final Map<Class<?>, Method> RESOLVE_STACKS_CACHE = new HashMap<>();

    // ─── Command exception types ─────────────────────────────────────────────────

    private static final SimpleCommandExceptionType ERR_INVALID_ITEM = new SimpleCommandExceptionType(
            Component.translatable(MODID + ".error.invalid_item"));
    private static final SimpleCommandExceptionType ERR_SINGLEPLAYER = new SimpleCommandExceptionType(
            Component.translatable(MODID + ".error.singleplayer_only"));
    private static final SimpleCommandExceptionType ERR_NO_WORLD = new SimpleCommandExceptionType(
            Component.translatable(MODID + ".error.no_world"));

    // ─── Mod initializer ────────────────────────────────────────────────────────

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            for (String alias : new String[]{"cc", "craftcalculator", "craftc"}) {
                registerCommand(dispatcher, alias);
            }
        });
    }

    private static void registerCommand(
            com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> dispatcher,
            String literal) {
        dispatcher.register(
                ClientCommands.literal(literal)
                        .then(ClientCommands.argument("item", StringArgumentType.string())
                                .suggests(CraftCalculatorMod::suggestItems)
                                .then(ClientCommands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(CraftCalculatorMod::execute))));
    }

    // ─── Main command handler ────────────────────────────────────────────────────

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

        // Build a recipe index once per command invocation — avoids O(n) scans per ingredient.
        Map<Item, CraftingRecipe> recipeIndex = buildRecipeIndex(rm);

        Requirements req = new Requirements();
        boolean hadRecipe = collectRequirements(itemKey(item), amount, recipeIndex, req, new HashSet<>(), true);
        if (!hadRecipe) {
            sendError(ctx, Component.translatable(MODID + ".error.no_recipe"));
            return 0;
        }

        // Merge craftable sub-ingredients into the totals map
        Map<String, Long> totals = new HashMap<>(req.rawMaterials);
        req.craftItems.forEach((k, v) -> totals.merge(k, v, Long::sum));

        // ─── Display ────────────────────────────────────────────────────────────
        String itemName = item.getName(new ItemStack(item)).getString();
        sendLine(ctx, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", ChatFormatting.YELLOW);
        ctx.getSource().sendFeedback(
                Component.translatable(MODID + ".result.header", amount, itemName)
                        .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        sendLine(ctx, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", ChatFormatting.YELLOW);
        ctx.getSource().sendFeedback(Component.literal(""));

        // Sort by display name (what the player actually sees) instead of the internal registry key
        totals.entrySet().stream()
                .sorted(Comparator.comparing(e -> resolveDisplayName(e.getKey())))
                .forEach(e -> {
                    String name     = resolveDisplayName(e.getKey());
                    long   qty      = e.getValue();
                    String packInfo = computePackInfo(e.getKey(), qty);
                    String line     = "  ✦ " + qty + "x " + name + (packInfo.isEmpty() ? "" : " " + packInfo);
                    ctx.getSource().sendFeedback(Component.literal(line).withStyle(ChatFormatting.YELLOW));
                });

        ctx.getSource().sendFeedback(Component.literal(""));
        sendLine(ctx, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", ChatFormatting.YELLOW);
        return Command.SINGLE_SUCCESS;
    }

    // ─── Recipe index ─────────────────────────────────────────────────────────────

    /**
     * Builds a map of Item → CraftingRecipe by scanning the RecipeManager once.
     * This replaces the original O(n) linear scan that was repeated for every ingredient.
     * When an item has multiple recipes, the first one found is used (vanilla behaviour).
     */
    private static Map<Item, CraftingRecipe> buildRecipeIndex(RecipeManager rm) {
        Map<Item, CraftingRecipe> index = new HashMap<>();
        for (RecipeHolder<?> h : rm.getRecipes()) {
            if (!(h.value() instanceof CraftingRecipe cr)) continue;
            ItemStack out = getOutputStack(cr);
            if (!out.isEmpty()) {
                index.putIfAbsent(out.getItem(), cr);
            }
        }
        return index;
    }

    // ─── Requirements collection ─────────────────────────────────────────────────

    private static final class Requirements {
        final Map<String, Long> craftItems   = new HashMap<>();
        final Map<String, Long> rawMaterials = new HashMap<>();
    }

    /**
     * Recursively collects raw materials and craftable sub-items for {@code targetKey}.
     * When {@code isRoot} is true, the target itself is not added to craftItems (avoids
     * "1x Dispenser" appearing when calculating a Dispenser).
     */
    private static boolean collectRequirements(
            String targetKey, long need,
            Map<Item, CraftingRecipe> index, Requirements out,
            Set<String> guard, boolean isRoot) {

        if (isSentinel(targetKey)) {
            out.rawMaterials.merge(targetKey, need, Long::sum);
            return true;
        }

        Item item = resolveItem(targetKey);
        if (item == null) {
            out.rawMaterials.merge(targetKey, need, Long::sum);
            return false;
        }

        if (BASE_MATERIALS.contains(item)) {
            out.rawMaterials.merge(targetKey, need, Long::sum);
            return true;
        }

        CraftingRecipe recipe = index.get(item);
        if (recipe == null) {
            out.rawMaterials.merge(targetKey, need, Long::sum);
            return false;
        }

        if (!isRoot) {
            // Non-root craftable items are shown as-is (e.g. "Bow" needed for a Dispenser)
            out.craftItems.merge(targetKey, need, Long::sum);
            return true;
        }

        if (!guard.add(targetKey)) return true; // already visited — cycle guard
        
        ItemStack output   = getOutputStack(recipe);
        int       produced = Math.max(1, output.getCount());
        long      crafts   = (long) Math.ceil((double) need / produced);

        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            if (ingredient.isEmpty()) continue;
            List<Item> options = ingredient.items()
                    .map(h -> h.value())
                    .filter(i -> i != Items.AIR)
                    .toList();
            if (options.isEmpty()) continue;

            if (options.size() > 1) {
                String groupKey = classifyGroup(options);
                if (groupKey != null) {
                    out.rawMaterials.merge(groupKey, crafts, Long::sum);
                } else {
                    collectRequirements(itemKey(options.get(0)), crafts, index, out, guard, false);
                }
            } else {
                collectRequirements(itemKey(options.get(0)), crafts, index, out, guard, false);
            }
        }

        guard.remove(targetKey);
        return true;
    }

    // ─── Shulker Box / pack info ──────────────────────────────────────────────────

    private static final int SHULKER_CAPACITY = 27 * 64; // 1728 items per shulker box

    private static String computePackInfo(String key, long qty) {
        if (isSentinel(key) || resolveItem(key) == null) return "";
        if (qty < SHULKER_CAPACITY) return "";
        return "[" + String.format(Locale.ROOT, "%.1f", (double) qty / SHULKER_CAPACITY) + " SB]";
    }

    // ─── Wood-group classification ────────────────────────────────────────────────

    private static String classifyGroup(List<Item> options) {
        Set<Item> optionSet = Set.copyOf(options); // deduplicated, cheap containsAll target

        // Check specific wood family first
        for (Map.Entry<String, Set<Item>> entry : WOOD_TYPE_LOGS.entrySet()) {
            String type = entry.getKey();
            Set<Item> planks = WOOD_TYPE_PLANKS.get(type);

            // options must be a subset of this type's logs+planks
            boolean allMatch = optionSet.stream().allMatch(entry.getValue()::contains)
                    || (planks != null && optionSet.stream().allMatch(planks::contains));
            if (allMatch) return WOOD_TYPE_SENTINELS.get(type);
        }

        // Generic groups
        if (WOOD_LOGS.containsAll(optionSet))   return KEY_ANY_WOOD_LOG;
        if (WOOD_PLANKS.containsAll(optionSet)) return KEY_ANY_WOOD_PLANKS;
        if (STICK_ITEMS.containsAll(optionSet)) return KEY_ANY_STICK;
        return null;
    }

    // ─── Recipe utilities ─────────────────────────────────────────────────────────

    private static ItemStack getOutputStack(CraftingRecipe recipe) {
        for (RecipeDisplay display : recipe.display()) {
            try {
                List<ItemStack> stacks = resolveStacks(display.result());
                if (!stacks.isEmpty()) {
                    ItemStack s = stacks.get(0);
                    if (s != null && !s.isEmpty()) return s;
                }
            } catch (Exception ignored) {}
        }
        return ItemStack.EMPTY;
    }

    /**
     * Resolves the ItemStack list from a SlotDisplay using reflection.
     * The Method lookup is cached per display type to avoid repeated reflection overhead.
     */
    @SuppressWarnings("unchecked")
    private static List<ItemStack> resolveStacks(Object slotDisplay) {
        Class<?> cls = slotDisplay.getClass();

        Method m = RESOLVE_STACKS_CACHE.computeIfAbsent(cls, c -> {
            // Try no-arg version first (most common)
            try { return c.getMethod("resolveForStacks"); }
            catch (NoSuchMethodException ignored) {}
            // Fallback: version that takes ContextMap
            try { return c.getMethod("resolveForStacks", ContextMap.class); }
            catch (NoSuchMethodException ignored) {}
            return null;
        });

        if (m == null) return List.of();

        try {
            Object result = m.getParameterCount() == 0
                    ? m.invoke(slotDisplay)
                    : m.invoke(slotDisplay, getEmptyContextMap());
            if (result instanceof List<?> l) return (List<ItemStack>) l;
        } catch (ReflectiveOperationException ignored) {}

        return List.of();
    }

    private static ContextMap getEmptyContextMap() {
        try {
            Object v = ContextMap.class.getField("EMPTY").get(null);
            if (v instanceof ContextMap m) return m;
        } catch (ReflectiveOperationException ignored) {}
        return null;
    }

    // ─── Key / display-name helpers ───────────────────────────────────────────────

    private static String itemKey(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static Item resolveItem(String key) {
        Identifier id = Identifier.tryParse(key);
        if (id == null) return null;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return (item == null || item == Items.AIR) ? null : item;
    }

    private static boolean isSentinel(String key) {
        return key.startsWith("__");
    }

    private static String resolveDisplayName(String key) {
        return switch (key) {
            case KEY_ANY_WOOD_LOG    -> Component.translatable(MODID + ".group.any_wood_log").getString();
            case KEY_ANY_WOOD_PLANKS -> Component.translatable(MODID + ".group.any_wood").getString();
            case KEY_ANY_STICK       -> Component.translatable(MODID + ".group.any_stick").getString();
            case KEY_OAK_WOOD        -> WOOD_TYPE_DISPLAY.get("oak")      + " (Any Logs/Planks)";
            case KEY_SPRUCE_WOOD     -> WOOD_TYPE_DISPLAY.get("spruce")   + " (Any Logs/Planks)";
            case KEY_BIRCH_WOOD      -> WOOD_TYPE_DISPLAY.get("birch")    + " (Any Logs/Planks)";
            case KEY_JUNGLE_WOOD     -> WOOD_TYPE_DISPLAY.get("jungle")   + " (Any Logs/Planks)";
            case KEY_ACACIA_WOOD     -> WOOD_TYPE_DISPLAY.get("acacia")   + " (Any Logs/Planks)";
            case KEY_DARK_OAK_WOOD   -> WOOD_TYPE_DISPLAY.get("dark_oak") + " (Any Logs/Planks)";
            case KEY_MANGROVE_WOOD   -> WOOD_TYPE_DISPLAY.get("mangrove") + " (Any Logs/Planks)";
            case KEY_CHERRY_WOOD     -> WOOD_TYPE_DISPLAY.get("cherry")   + " (Any Logs/Planks)";
            case KEY_BAMBOO_WOOD     -> WOOD_TYPE_DISPLAY.get("bamboo")   + " (Any Logs/Planks)";
            case KEY_CRIMSON_WOOD    -> WOOD_TYPE_DISPLAY.get("crimson")  + " (Any Stems/Hyphae)";
            case KEY_WARPED_WOOD     -> WOOD_TYPE_DISPLAY.get("warped")   + " (Any Stems/Hyphae)";
            default -> {
                Item item = resolveItem(key);
                yield item != null ? item.getName(new ItemStack(item)).getString() : key;
            }
        };
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

    // ─── UI helpers ───────────────────────────────────────────────────────────────

    private static void sendLine(CommandContext<FabricClientCommandSource> ctx, String text, ChatFormatting color) {
        ctx.getSource().sendFeedback(Component.literal(text).withStyle(color));
    }

    private static void sendError(CommandContext<FabricClientCommandSource> ctx, Component msg) {
        ctx.getSource().sendFeedback(msg.copy().withStyle(ChatFormatting.RED));
    }
}
