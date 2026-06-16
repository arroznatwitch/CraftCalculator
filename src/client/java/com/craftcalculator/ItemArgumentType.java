package com.craftcalculator;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Custom argument type that reads a single token allowing colons (e.g. "minecraft:stone").
 * Brigadier's built-in word() stops at ':', so we need this to support namespaced IDs.
 */
public final class ItemArgumentType implements ArgumentType<String> {

    private static final ItemArgumentType INSTANCE = new ItemArgumentType();

    public static ItemArgumentType itemArg() {
        return INSTANCE;
    }

    public static String getItem(CommandContext<?> ctx, String name) {
        return ctx.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        StringBuilder sb = new StringBuilder();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
            sb.append(reader.read());
        }
        return sb.toString();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (Identifier key : BuiltInRegistries.ITEM.keySet()) {
            String v = key.toString();
            if (v.startsWith(remaining) || v.contains(":" + remaining)) builder.suggest(v);
        }
        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("stone", "minecraft:stone", "oak_planks");
    }

    private ItemArgumentType() {}
}