package com.craftcalculator;

import net.minecraft.core.registries.BuiltInRegistries;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core recipe calculation logic.
 *
 * Given a target item and quantity, produces two maps:
 *   - rawMaterials: items that cannot (or should not) be crafted further
 *   - craftItems:   craftable sub-items shown as-is (e.g. "Bow" for a Dispenser)
 */
public final class RecipeCalculator {

    /** Result of a calculation. */
    public static final class Requirements {
        public final Map<String, Long> craftItems   = new HashMap<>();
        public final Map<String, Long> rawMaterials = new HashMap<>();
    }

    // Cached reflection lookups for SlotDisplay.resolveForStacks, keyed by display class.
    private static final Map<Class<?>, Method> RESOLVE_STACKS_CACHE = new HashMap<>();

    // ─── Public API ───────────────────────────────────────────────────────────────

    /**
     * Builds a recipe index from the RecipeManager (O(n) scan, done once per command).
     * Returns a map of Item → first CraftingRecipe that produces it.
     */
    public static Map<Item, CraftingRecipe> buildRecipeIndex(RecipeManager rm) {
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

    /**
     * Recursively collects raw materials and craftable sub-items for {@code targetKey}.
     *
     * @param targetKey  Registry key of the item to calculate (e.g. "minecraft:dispenser")
     * @param need       How many of this item are needed
     * @param index      Recipe index built by {@link #buildRecipeIndex}
     * @param out        Accumulator for results
     * @param guard      Cycle-detection set (pass a new empty HashSet from the call site)
     * @param isRoot     True only for the top-level item — prevents it appearing as its own ingredient
     * @return true if a recipe was found for this item, false otherwise
     */
    public static boolean collectRequirements(
            String targetKey, long need,
            Map<Item, CraftingRecipe> index, Requirements out,
            Set<String> guard, boolean isRoot) {

        if (WoodGroups.isSentinel(targetKey)) {
            out.rawMaterials.merge(targetKey, need, Long::sum);
            return true;
        }

        Item item = resolveItem(targetKey);
        if (item == null) {
            out.rawMaterials.merge(targetKey, need, Long::sum);
            return false;
        }

        if (BaseMaterials.ALL.contains(item)) {
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

        if (!guard.add(targetKey)) return true; // cycle guard

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
                String groupKey = WoodGroups.classifyGroup(options);
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

    // ─── Recipe / ItemStack utilities ─────────────────────────────────────────────

    public static ItemStack getOutputStack(CraftingRecipe recipe) {
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
     * Resolves the ItemStack list from a SlotDisplay via reflection.
     * The Method handle is cached per display class to avoid repeated lookups.
     */
    @SuppressWarnings("unchecked")
    private static List<ItemStack> resolveStacks(Object slotDisplay) {
        Class<?> cls = slotDisplay.getClass();

        Method m = RESOLVE_STACKS_CACHE.computeIfAbsent(cls, c -> {
            try { return c.getMethod("resolveForStacks"); }
            catch (NoSuchMethodException ignored) {}
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

    // ─── Item key / resolve helpers ───────────────────────────────────────────────

    public static String itemKey(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    public static Item resolveItem(String key) {
        Identifier id = Identifier.tryParse(key);
        if (id == null) return null;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return (item == null || item == Items.AIR) ? null : item;
    }

    private RecipeCalculator() {}
}
