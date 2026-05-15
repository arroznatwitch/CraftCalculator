package com.example.craftcalc;

import com.mojang.brigadier.Command;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class CraftCalculatorMod implements ClientModInitializer {

    private static final String MODID = "craftcalculator";

    // Sentinel keys for multi-choice ingredients (e.g., any oak log, spruce planks, etc.)
    // Keys starting with "__" are never looked up in the item registry
    private static final String KEY_ANY_WOOD_LOG    = "__any_wood_log__";
    private static final String KEY_ANY_WOOD_PLANKS = "__any_wood_planks__";
    private static final String KEY_ANY_STICK       = "__any_stick__";

    // Type-specific wood sentinels for recipes that need exact wood types (e.g., Spruce Fence)
    private static final String KEY_OAK_WOOD       = "__oak_wood__";
    private static final String KEY_SPRUCE_WOOD    = "__spruce_wood__";
    private static final String KEY_BIRCH_WOOD     = "__birch_wood__";
    private static final String KEY_JUNGLE_WOOD    = "__jungle_wood__";
    private static final String KEY_ACACIA_WOOD    = "__acacia_wood__";
    private static final String KEY_DARK_OAK_WOOD  = "__dark_oak_wood__";
    private static final String KEY_MANGROVE_WOOD  = "__mangrove_wood__";
    private static final String KEY_CHERRY_WOOD    = "__cherry_wood__";
    private static final String KEY_BAMBOO_WOOD    = "__bamboo_wood__";
    private static final String KEY_CRIMSON_WOOD   = "__crimson_wood__";
    private static final String KEY_WARPED_WOOD    = "__warped_wood__";


    // Wood/plank lists and stick set. These are populated programmatically below
    // to keep the source compact and easier to maintain across Minecraft versions.
    private static final Set<Item> WOOD_LOGS = new HashSet<>();
    private static final Set<Item> WOOD_PLANKS = new HashSet<>();
    private static final Set<Item> STICK_ITEMS = new HashSet<>();

    // Base materials that should not be expanded further - these are what players typically craft with
    private static final Set<Item> BASE_MATERIALS = Set.of(
            // Planks (all types)
            Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS,
            Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS,
            Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS, Items.BAMBOO_PLANKS,
            Items.CRIMSON_PLANKS, Items.WARPED_PLANKS,
            // Sticks and similar
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
            // Basic storage and crafting
            Items.CHEST,
            Items.CRAFTING_TABLE,
            // Basic materials
            Items.COBBLESTONE, Items.STONE, Items.DIRT, Items.SAND,
            Items.IRON_ORE, Items.COPPER_ORE, Items.COAL_ORE, Items.GOLD_ORE,
            Items.LAPIS_ORE, Items.REDSTONE_ORE, Items.DIAMOND_ORE, Items.EMERALD_ORE,
            // Ingots/Gems
            Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT,
            Items.DIAMOND, Items.EMERALD, Items.LAPIS_LAZULI
    );

    // Mappings for each wood type to their logs/planks and display names
    private static final Map<String, Set<Item>> WOOD_TYPE_LOGS = new HashMap<>();
    private static final Map<String, Set<Item>> WOOD_TYPE_PLANKS = new HashMap<>();
    private static final Map<String, String> WOOD_TYPE_SENTINELS = new HashMap<>();
    private static final Map<String, String> WOOD_TYPE_DISPLAY = new HashMap<>();

    static {
        // Build wood type maps programmatically to keep the source compact.
        String[] types = new String[] {"oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","bamboo","crimson","warped"};

        Map<String, String[]> exceptions = new HashMap<>();
        // Fields that don't follow the simple naming pattern
        exceptions.put("bamboo", new String[]{"BAMBOO_BLOCK","BAMBOO_PLANKS","BAMBOO"});
        exceptions.put("crimson", new String[]{"CRIMSON_STEM","CRIMSON_PLANKS","CRIMSON_HYPHAE"});
        exceptions.put("warped", new String[]{"WARPED_STEM","WARPED_PLANKS","WARPED_HYPHAE"});

        for (String type : types) {
            Set<Item> logs = new HashSet<>();
            Set<Item> planks = new HashSet<>();
            String upper = type.toUpperCase(Locale.ROOT).replace('-', '_');

            if (exceptions.containsKey(type)) {
                for (String fname : exceptions.get(type)) {
                    try {
                        Field f = Items.class.getField(fname);
                        Object v = f.get(null);
                        if (v instanceof Item it) {
                            if (fname.contains("PLANK")) planks.add(it);
                            else logs.add(it);
                        }
                    } catch (ReflectiveOperationException ignored) {}
                }
            } else {
                // Common naming conventions: {TYPE}_LOG, STRIPPED_{TYPE}_LOG, {TYPE}_WOOD and {TYPE}_PLANKS
                String[] logNames = new String[] { upper + "_LOG", "STRIPPED_" + upper + "_LOG", upper + "_WOOD" };
                for (String ln : logNames) {
                    try { Field f = Items.class.getField(ln); Object v = f.get(null); if (v instanceof Item it) logs.add(it); } catch (Exception ignored) {}
                }
                try { Field p = Items.class.getField(upper + "_PLANKS"); Object pv = p.get(null); if (pv instanceof Item it) planks.add(it); } catch (Exception ignored) {}
            }

            if (!logs.isEmpty()) WOOD_TYPE_LOGS.put(type, Set.copyOf(logs));
            if (!planks.isEmpty()) WOOD_TYPE_PLANKS.put(type, Set.copyOf(planks));
            WOOD_LOGS.addAll(logs);
            WOOD_PLANKS.addAll(planks);

            // Sentinels and display names
            String sentinel = switch (type) {
                case "oak" -> KEY_OAK_WOOD;
                case "spruce" -> KEY_SPRUCE_WOOD;
                case "birch" -> KEY_BIRCH_WOOD;
                case "jungle" -> KEY_JUNGLE_WOOD;
                case "acacia" -> KEY_ACACIA_WOOD;
                case "dark_oak" -> KEY_DARK_OAK_WOOD;
                case "mangrove" -> KEY_MANGROVE_WOOD;
                case "cherry" -> KEY_CHERRY_WOOD;
                case "bamboo" -> KEY_BAMBOO_WOOD;
                case "crimson" -> KEY_CRIMSON_WOOD;
                case "warped" -> KEY_WARPED_WOOD;
                default -> "__" + type + "_wood__";
            };
            WOOD_TYPE_SENTINELS.put(type, sentinel);

            String display = ("dark_oak".equals(type)) ? "Dark Oak Wood" : Character.toUpperCase(type.charAt(0)) + type.substring(1).replace('_', ' ') + " Wood";
            WOOD_TYPE_DISPLAY.put(type, display);
        }

        // Stick-like items
        STICK_ITEMS.add(Items.STICK);
        STICK_ITEMS.add(Items.BAMBOO);
    }

    // Error messages
    private static final SimpleCommandExceptionType INVALID_ITEM = new SimpleCommandExceptionType(
            Component.translatable(MODID + ".error.invalid_item"));
    private static final SimpleCommandExceptionType NOT_SINGLEPLAYER = new SimpleCommandExceptionType(
            Component.translatable(MODID + ".error.singleplayer_only"));
    private static final SimpleCommandExceptionType NO_WORLD = new SimpleCommandExceptionType(
            Component.translatable(MODID + ".error.no_world"));
    private static final SimpleCommandExceptionType INVALID_USAGE = new SimpleCommandExceptionType(
            Component.translatable(MODID + ".error.usage"));

    // Initializer
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            for (String alias : new String[]{"cc", "craftcalculcator", "craftc"}) {
                registerCommand(dispatcher, alias);
            }
        });
    }

    private static void registerCommand(
            com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> dispatcher,
            String literal) {
        dispatcher.register(
                ClientCommands.literal(literal)
                        .then(ClientCommands.literal("testcrafts").executes(CraftCalculatorMod::testCrafts))
                        .then(ClientCommands.argument("input", StringArgumentType.greedyString())
                                .suggests(CraftCalculatorMod::suggestItems)
                                .executes(CraftCalculatorMod::execute)));
    }

    // Command: /cc testcrafts
    private static int testCrafts(CommandContext<FabricClientCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) throw NO_WORLD.create();
        if (!mc.isLocalServer())                   throw NOT_SINGLEPLAYER.create();
        if (mc.getSingleplayerServer() == null)    throw NOT_SINGLEPLAYER.create();

        RecipeManager rm = mc.getSingleplayerServer().getRecipeManager();

        List<String> report = new ArrayList<>();
        for (RecipeHolder<?> h : rm.getRecipes()) {
            if (!(h.value() instanceof CraftingRecipe cr)) continue;
            String idStr = h.id().toString();
            ItemStack out = getOutputStack(cr);
            String outDesc = out.isEmpty() ? "<empty>" : (itemKey(out.getItem()) + " x" + out.getCount());
            List<String> problems = new ArrayList<>();

            if (out.isEmpty()) problems.add("Missing output");

            int slot = 0;
            for (Ingredient ing : cr.placementInfo().ingredients()) {
                slot++;
                if (ing.isEmpty()) continue;
                List<Item> options = ing.items().map(hh -> hh.value()).filter(i -> i != Items.AIR).toList();
                if (options.isEmpty()) {
                    problems.add("Ingredient#" + slot + " has no options");
                }
            }

            String line = idStr + " -> " + outDesc;
            if (!problems.isEmpty()) line += "  PROBLEMS: " + String.join(", ", problems);
            report.add(line);
        }

        try {
            Path gameDir = mc.gameDirectory.toPath();
            Path outDir = gameDir.resolve("mods").resolve("craftcalculator-testcrafts");
            Files.createDirectories(outDir);

            int fileIndex = 0;
            for (int i = 0; i < report.size(); i += 100) {
                List<String> part = report.subList(i, Math.min(i + 100, report.size()));
                Path file = outDir.resolve(String.format("testcrafts_%03d.txt", ++fileIndex));
                Files.write(file, part, StandardCharsets.UTF_8);
            }

            ctx.getSource().sendFeedback(Component.literal("Wrote " + report.size() + " recipes to " + outDir.toString()).withStyle(ChatFormatting.GREEN));
        } catch (IOException e) {
            sendError(ctx, Component.literal("Failed to write report: " + e.getMessage()));
            return 0;
        }

        return Command.SINGLE_SUCCESS;
    }

    // Main command handler - parses input and expands recipes
    private static int execute(CommandContext<FabricClientCommandSource> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) throw NO_WORLD.create();
        if (!mc.isLocalServer())                   throw NOT_SINGLEPLAYER.create();
        if (mc.getSingleplayerServer() == null)    throw NOT_SINGLEPLAYER.create();

        // Parse "<item> <amount>"
        String input = StringArgumentType.getString(ctx, "input").trim();
        int split = input.lastIndexOf(' ');
        if (split <= 0 || split >= input.length() - 1) throw INVALID_USAGE.create();

        String itemStr   = input.substring(0, split).trim();
        String amountStr = input.substring(split + 1).trim();
        int amount;
        try { amount = Integer.parseInt(amountStr); }
        catch (NumberFormatException e) { throw INVALID_USAGE.create(); }
        if (amount < 1) throw INVALID_USAGE.create();

        // Resolve item registry ID
        Identifier id = Identifier.tryParse(itemStr);
        if (id == null && !itemStr.contains(":")) id = Identifier.tryParse("minecraft:" + itemStr);
        if (id == null) throw INVALID_ITEM.create();

        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null || item == Items.AIR) throw INVALID_ITEM.create();

        RecipeManager rm = mc.getSingleplayerServer().getRecipeManager();

        // First pass: collect craftable item counts and direct raw materials
        Requirements req = new Requirements();
        Set<String> guard = new HashSet<>();

        // Collect requirements for the target item specially (root) so we don't
        // record the target itself as a craft requirement (avoids circular "1x Dispenser" entries).
        boolean hadRecipe = collectRequirements(itemKey(item), amount, rm, req, guard, true);
        if (!hadRecipe) {
            sendError(ctx, Component.translatable(MODID + ".error.no_recipe"));
            return 0;
        }


        // Combine raw materials and craftable ingredient items into a single
        // final list. We intentionally do NOT expand craftable ingredients
        // (e.g. Bow) to their raw components here — the user prefers to see
        // the craftable item itself listed.
        Map<String, Long> totalRaw = new HashMap<>();
        totalRaw.putAll(req.rawMaterials);
        // Add craftable ingredients as their own entries (not expanded)
        for (Map.Entry<String, Long> e : req.craftItems.entrySet()) {
            totalRaw.merge(e.getKey(), e.getValue(), Long::sum);
        }

        // Display results
        String itemName = item.getName(new ItemStack(item)).getString();
        ctx.getSource().sendFeedback(Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.YELLOW));
        ctx.getSource().sendFeedback(Component.translatable(MODID + ".result.header", amount, itemName).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        ctx.getSource().sendFeedback(Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.YELLOW));
        ctx.getSource().sendFeedback(Component.literal(""));

        List<Map.Entry<String, Long>> rawEntries = new ArrayList<>(totalRaw.entrySet());
        rawEntries.sort(Comparator.comparing(Map.Entry::getKey));
        for (Map.Entry<String, Long> e : rawEntries) {
            String name = resolveDisplayName(e.getKey());
            long qty = e.getValue();
            String packInfo = computePackInfo(e.getKey(), qty);
            ctx.getSource().sendFeedback(Component.literal("  ✦ " + qty + "x " + name + (packInfo.isEmpty() ? "" : " " + packInfo)).withStyle(ChatFormatting.YELLOW));
        }

        ctx.getSource().sendFeedback(Component.literal(""));
        ctx.getSource().sendFeedback(Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    // First-pass collector and full-expander helpers

    private static final class Requirements {
        final Map<String, Long> craftItems = new HashMap<>();
        final Map<String, Long> rawMaterials = new HashMap<>();
    }

    /**
     * Collect craftable item counts and direct raw materials. When an ingredient is craftable
     * we count the number of items required but do not expand it here. This makes the output
     * list show "Bow" as a required craft for recipes that use a bow (e.g. Dispenser).
     */
    private static boolean collectRequirements(
            String targetKey,
            long need,
            RecipeManager rm,
            Requirements out,
            Set<String> guard,
            boolean isRoot
    ) {
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

        Optional<RecipeHolder<CraftingRecipe>> holder = findRecipeFor(item, rm);
        if (holder.isEmpty()) {
            out.rawMaterials.merge(targetKey, need, Long::sum);
            return false;
        }

        // This item is craftable — record how many items (not craft ops) are needed.
        // However, if this is the original target we're analysing (isRoot==true),
        // we avoid recording the target itself as a craft requirement to prevent
        // showing "1x Dispenser" as an ingredient for crafting a Dispenser.
        if (!isRoot) out.craftItems.merge(targetKey, need, Long::sum);

        if (guard.contains(targetKey)) return true;
        guard.add(targetKey);

        CraftingRecipe recipe = holder.get().value();
        ItemStack output = getOutputStack(recipe);
        int produced = Math.max(1, output.getCount());
        long crafts = (long) Math.ceil((double) need / produced);

        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            if (ingredient.isEmpty()) continue;

            List<Item> options = ingredient.items().map(h -> h.value()).filter(i -> i != Items.AIR).toList();
            if (options.isEmpty()) continue;

            if (options.size() > 1) {
                String groupKey = classifyGroup(options);
                if (groupKey != null) {
                    out.rawMaterials.merge(groupKey, crafts, Long::sum);
                } else {
                    String opt = itemKey(options.get(0));
                    Item optItem = resolveItem(opt);
                    if (optItem != null && findRecipeFor(optItem, rm).isPresent() && !BASE_MATERIALS.contains(optItem)) {
                        // Non-root recursive craftable ingredients should be recorded
                        // as craft requirements.
                        out.craftItems.merge(opt, crafts, Long::sum);
                    } else if (BASE_MATERIALS.contains(optItem)) {
                        out.rawMaterials.merge(opt, crafts, Long::sum);
                    }
                }
            } else {
                String opt = itemKey(options.get(0));
                Item optItem = resolveItem(opt);
                if (optItem != null && findRecipeFor(optItem, rm).isPresent() && !BASE_MATERIALS.contains(optItem)) {
                    out.craftItems.merge(opt, crafts, Long::sum);
                } else if (BASE_MATERIALS.contains(optItem)) {
                    out.rawMaterials.merge(opt, crafts, Long::sum);
                }
            }
        }

        guard.remove(targetKey);
        return true;
    }

    /**
     * Fully expand a craftable item's required count into raw/base materials.
     */
    private static void expandToRaw(String key, long need, RecipeManager rm, Map<String, Long> out, Set<String> guard) {
        if (isSentinel(key)) {
            out.merge(key, need, Long::sum);
            return;
        }
        Item item = resolveItem(key);
        if (item == null) {
            out.merge(key, need, Long::sum);
            return;
        }
        if (BASE_MATERIALS.contains(item)) {
            out.merge(key, need, Long::sum);
            return;
        }
        if (guard.contains(key)) {
            out.merge(key, need, Long::sum);
            return;
        }

        Optional<RecipeHolder<CraftingRecipe>> holder = findRecipeFor(item, rm);
        if (holder.isEmpty()) {
            out.merge(key, need, Long::sum);
            return;
        }

        CraftingRecipe recipe = holder.get().value();
        ItemStack output = getOutputStack(recipe);
        int produced = Math.max(1, output.getCount());
        long ops = (long) Math.ceil((double) need / produced);

        guard.add(key);
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            if (ingredient.isEmpty()) continue;
            List<Item> options = ingredient.items().map(h -> h.value()).filter(i -> i != Items.AIR).toList();
            if (options.isEmpty()) continue;

            if (options.size() > 1) {
                String group = classifyGroup(options);
                if (group != null) out.merge(group, ops, Long::sum);
                else expandToRaw(itemKey(options.get(0)), ops, rm, out, guard);
            } else {
                String opt = itemKey(options.get(0));
                Item optItem = resolveItem(opt);
                if (optItem != null && findRecipeFor(optItem, rm).isPresent() && !BASE_MATERIALS.contains(optItem)) {
                    expandToRaw(opt, ops, rm, out, guard);
                } else {
                    out.merge(opt, ops, Long::sum);
                }
            }
        }
        guard.remove(key);
    }

    private static String computePackInfo(String key, long qty) {
        if (isSentinel(key)) return "";
        Item item = resolveItem(key);
        if (item == null) return "";
        // Use standard max stack 64 as a reasonable default for pack estimates.
        int maxStack = 64;
        int shulkerCapacity = 27 * maxStack; // items per shulker box
        if (qty < shulkerCapacity) return "";

        double sbCount = (double) qty / (double) shulkerCapacity;
        // Format with one decimal place (e.g. 1.1, 1.2) which is more intuitive than showing
        // the raw capacity number (1728). This matches the user's preference.
        String sbStr = String.format(Locale.ROOT, "%.1f", sbCount);
        return "[" + sbStr + " SB]";
    }

    private static String classifyGroup(List<Item> options) {
        // Check if all options belong to a specific wood type
        for (String woodType : WOOD_TYPE_LOGS.keySet()) {
            Set<Item> typeItems = new HashSet<>();
            typeItems.addAll(WOOD_TYPE_LOGS.get(woodType));
            typeItems.addAll(WOOD_TYPE_PLANKS.get(woodType));

            if (options.stream().allMatch(typeItems::contains)) {
                return WOOD_TYPE_SENTINELS.get(woodType);
            }
        }

        // Check for generic groups
        if (options.stream().allMatch(WOOD_LOGS::contains))   return KEY_ANY_WOOD_LOG;
        if (options.stream().allMatch(WOOD_PLANKS::contains)) return KEY_ANY_WOOD_PLANKS;
        if (options.stream().allMatch(STICK_ITEMS::contains)) return KEY_ANY_STICK;
        return null;
    }

    // Recipe utility methods

    private static Optional<RecipeHolder<CraftingRecipe>> findRecipeFor(Item item, RecipeManager rm) {
        for (RecipeHolder<?> h : rm.getRecipes()) {
            if (!(h.value() instanceof CraftingRecipe cr)) continue;
            ItemStack out = getOutputStack(cr);
            if (!out.isEmpty() && out.getItem() == item) {
                return Optional.of(new RecipeHolder<>(h.id(), cr));
            }
        }
        return Optional.empty();
    }

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

    @SuppressWarnings("unchecked")
    private static List<ItemStack> resolveStacks(Object slotDisplay) {
        try {
            Method m = slotDisplay.getClass().getMethod("resolveForStacks");
            Object r = m.invoke(slotDisplay);
            if (r instanceof List<?>) return (List<ItemStack>) r;
        } catch (ReflectiveOperationException ignored) {}
        try {
            Method m = slotDisplay.getClass().getMethod("resolveForStacks", ContextMap.class);
            Object r = m.invoke(slotDisplay, getEmptyContextMap());
            if (r instanceof List<?>) return (List<ItemStack>) r;
        } catch (ReflectiveOperationException ignored) {}
        return List.of();
    }

    private static ContextMap getEmptyContextMap() {
        try {
            Field f = ContextMap.class.getField("EMPTY");
            Object v = f.get(null);
            if (v instanceof ContextMap map) return map;
        } catch (ReflectiveOperationException ignored) {}
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Key and display name helpers
    // ─────────────────────────────────────────────────────────────────────────────

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
            case KEY_ANY_WOOD_LOG    -> "Any Wood Log";
            case KEY_ANY_WOOD_PLANKS -> "Any Wood Planks";
            case KEY_ANY_STICK       -> "Any Stick";
            case KEY_OAK_WOOD        -> WOOD_TYPE_DISPLAY.get("oak") + " (Any Logs/Planks)";
            case KEY_SPRUCE_WOOD     -> WOOD_TYPE_DISPLAY.get("spruce") + " (Any Logs/Planks)";
            case KEY_BIRCH_WOOD      -> WOOD_TYPE_DISPLAY.get("birch") + " (Any Logs/Planks)";
            case KEY_JUNGLE_WOOD     -> WOOD_TYPE_DISPLAY.get("jungle") + " (Any Logs/Planks)";
            case KEY_ACACIA_WOOD     -> WOOD_TYPE_DISPLAY.get("acacia") + " (Any Logs/Planks)";
            case KEY_DARK_OAK_WOOD   -> WOOD_TYPE_DISPLAY.get("dark_oak") + " (Any Logs/Planks)";
            case KEY_MANGROVE_WOOD   -> WOOD_TYPE_DISPLAY.get("mangrove") + " (Any Logs/Planks)";
            case KEY_CHERRY_WOOD     -> WOOD_TYPE_DISPLAY.get("cherry") + " (Any Logs/Planks)";
            case KEY_BAMBOO_WOOD     -> WOOD_TYPE_DISPLAY.get("bamboo") + " (Any Logs/Planks)";
            case KEY_CRIMSON_WOOD    -> WOOD_TYPE_DISPLAY.get("crimson") + " (Any Stems/Hyphae)";
            case KEY_WARPED_WOOD     -> WOOD_TYPE_DISPLAY.get("warped") + " (Any Stems/Hyphae)";
            default -> {
                Item item = resolveItem(key);
                yield item != null ? item.getName(new ItemStack(item)).getString() : key;
            }
        };
    }

    // Tab-completion

    private static CompletableFuture<Suggestions> suggestItems(
            CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        if (remaining.indexOf(' ') >= 0) return builder.buildFuture();
        for (Identifier key : BuiltInRegistries.ITEM.keySet()) {
            String v = key.toString();
            if (v.startsWith(remaining)) builder.suggest(v);
        }
        return builder.buildFuture();
    }

    // Error handler

    private static void sendError(CommandContext<FabricClientCommandSource> ctx, Component msg) {
        ctx.getSource().sendFeedback(msg.copy().withStyle(ChatFormatting.RED));
    }
}