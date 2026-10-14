package de.vali.minigames.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Stream;

/** Curated vanilla items that can be obtained in ordinary survival play. */
public final class ItemRaceCatalog {
    public static final int MIN_ITEMS = 1;
    public static final int MAX_ITEMS = 30;
    public static final int DEFAULT_ITEMS = 5;

    /** Common Overworld materials, drops and simple recipes. */
    private static final List<String> EASY_ITEMS = items(
            "dirt", "cobblestone", "sand", "gravel", "oak_log", "birch_log", "oak_planks", "oak_sapling",
            "stick", "crafting_table", "chest", "furnace", "stone_pickaxe", "stone_axe", "stone_shovel", "stone_sword",
            "wooden_hoe", "torch", "charcoal", "glass", "bowl", "ladder", "oak_boat", "oak_door", "oak_fence", "white_wool",
            "beef", "porkchop", "chicken", "mutton", "feather", "leather", "wheat_seeds", "dandelion", "poppy", "flint");

    /** Mining, farming and recipes that require more preparation. */
    private static final List<String> NORMAL_ITEMS = items(
            "coal", "iron_ingot", "copper_ingot", "gold_ingot", "redstone", "lapis_lazuli",
            "iron_pickaxe", "iron_axe", "iron_sword", "shield", "bucket", "water_bucket", "lava_bucket", "shears",
            "flint_and_steel", "compass", "clock",
            "bread", "cake", "cookie", "pumpkin_pie", "cooked_beef", "baked_potato", "carrot", "beetroot",
            "sugar", "paper", "book", "painting",
            "piston", "hopper", "rail", "powered_rail", "redstone_torch", "repeater", "fishing_rod");

    /** Expensive equipment, rare drops and items requiring Nether or End exploration. */
    private static final List<String> HARD_ITEMS = items(
            "diamond", "diamond_pickaxe", "diamond_sword", "diamond_chestplate", "diamond_block", "emerald_block",
            "anvil", "enchanting_table", "golden_apple", "golden_carrot", "obsidian",
            "netherrack", "soul_sand", "glowstone", "blaze_rod", "blaze_powder", "ghast_tear", "magma_cream",
            "nether_wart", "nether_brick", "nether_bricks", "quartz", "quartz_block", "crying_obsidian",
            "ender_pearl", "ender_eye", "end_stone", "chorus_fruit", "popped_chorus_fruit", "purpur_block",
            "shulker_shell", "end_rod", "dragon_breath", "netherite_scrap", "netherite_ingot", "nether_star");

    private static final List<String> MIXED_ITEMS = Stream.of(EASY_ITEMS, NORMAL_ITEMS, HARD_ITEMS)
            .flatMap(List::stream).toList();

    private ItemRaceCatalog() {
    }

    public static List<String> pool(ItemRaceDifficulty difficulty) {
        return switch (Objects.requireNonNull(difficulty, "difficulty")) {
            case EASY -> EASY_ITEMS;
            case NORMAL -> NORMAL_ITEMS;
            case HARD -> HARD_ITEMS;
            case MIXED -> MIXED_ITEMS;
        };
    }

    /** Selects distinct targets; the supplied random source makes seeded races reproducible. */
    public static List<String> select(ItemRaceDifficulty difficulty, int count, Random random) {
        Objects.requireNonNull(random, "random");
        List<String> candidates = new ArrayList<>(pool(difficulty));
        if (count < MIN_ITEMS || count > MAX_ITEMS) {
            throw new IllegalArgumentException("Item count must be between " + MIN_ITEMS + " and " + MAX_ITEMS);
        }
        Collections.shuffle(candidates, random);
        return List.copyOf(candidates.subList(0, count));
    }

    private static List<String> items(String... names) {
        return Stream.of(names).map(name -> "minecraft:" + name).toList();
    }
}
