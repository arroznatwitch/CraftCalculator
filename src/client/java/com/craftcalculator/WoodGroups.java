package com.craftcalculator;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Manages wood-type group sentinels and classification logic.
 *
 * Sentinel keys (starting with "__") represent ingredient groups where any item
 * from the group is acceptable — e.g. any plank type, any log type.
 * They are never looked up in the item registry.
 */
public final class WoodGroups {

    // Generic group sentinels
    public static final String KEY_ANY_WOOD_LOG    = "__any_wood_log__";
    public static final String KEY_ANY_WOOD_PLANKS = "__any_wood_planks__";
    public static final String KEY_ANY_STICK       = "__any_stick__";

    // Per-wood-type sentinels
    public static final String KEY_OAK_WOOD      = "__oak_wood__";
    public static final String KEY_SPRUCE_WOOD   = "__spruce_wood__";
    public static final String KEY_BIRCH_WOOD    = "__birch_wood__";
    public static final String KEY_JUNGLE_WOOD   = "__jungle_wood__";
    public static final String KEY_ACACIA_WOOD   = "__acacia_wood__";
    public static final String KEY_DARK_OAK_WOOD = "__dark_oak_wood__";
    public static final String KEY_MANGROVE_WOOD = "__mangrove_wood__";
    public static final String KEY_CHERRY_WOOD   = "__cherry_wood__";
    public static final String KEY_BAMBOO_WOOD   = "__bamboo_wood__";
    public static final String KEY_CRIMSON_WOOD  = "__crimson_wood__";
    public static final String KEY_WARPED_WOOD   = "__warped_wood__";

    // Global sets of all logs/planks/sticks across every wood type
    static final Set<Item> WOOD_LOGS   = new HashSet<>();
    static final Set<Item> WOOD_PLANKS = new HashSet<>();
    static final Set<Item> STICK_ITEMS = new HashSet<>();

    // Per-type breakdowns
    static final Map<String, Set<Item>> WOOD_TYPE_LOGS      = new HashMap<>();
    static final Map<String, Set<Item>> WOOD_TYPE_PLANKS    = new HashMap<>();
    static final Map<String, String>    WOOD_TYPE_SENTINELS = new HashMap<>();

    /** Human-readable display name for each wood type (e.g. "Oak Wood", "Dark Oak Wood"). */
    public static final Map<String, String> WOOD_TYPE_DISPLAY = new HashMap<>();

    static {
        String[] types = {"oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","bamboo","crimson","warped"};

        // Wood types whose Items fields don't follow the standard {TYPE}_LOG / {TYPE}_PLANKS pattern
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

    /**
     * Returns true if {@code key} is a sentinel (never a real registry ID).
     */
    public static boolean isSentinel(String key) {
        return key.startsWith("__");
    }

    /**
     * Given a list of ingredient options, returns the matching group sentinel,
     * or {@code null} if the options don't match any known group.
     */
    public static String classifyGroup(List<Item> options) {
        Set<Item> optionSet = Set.copyOf(options);

        // Check specific wood family first
        for (Map.Entry<String, Set<Item>> entry : WOOD_TYPE_LOGS.entrySet()) {
            String type   = entry.getKey();
            Set<Item> pl  = WOOD_TYPE_PLANKS.get(type);
            boolean match = entry.getValue().containsAll(optionSet)
                    || (pl != null && pl.containsAll(optionSet));
            if (match) return WOOD_TYPE_SENTINELS.get(type);
        }

        if (WOOD_LOGS.containsAll(optionSet))   return KEY_ANY_WOOD_LOG;
        if (WOOD_PLANKS.containsAll(optionSet)) return KEY_ANY_WOOD_PLANKS;
        if (STICK_ITEMS.containsAll(optionSet)) return KEY_ANY_STICK;
        return null;
    }

    // ─── Internal helper ──────────────────────────────────────────────────────────

    private static void tryAddItem(String fieldName, Set<Item> planks, Set<Item> logs) {
        try {
            Object v = Items.class.getField(fieldName).get(null);
            if (v instanceof Item it) {
                if (planks != null && fieldName.contains("PLANK")) planks.add(it);
                else if (logs != null) logs.add(it);
            }
        } catch (ReflectiveOperationException ignored) {}
    }

    private WoodGroups() {}
}
