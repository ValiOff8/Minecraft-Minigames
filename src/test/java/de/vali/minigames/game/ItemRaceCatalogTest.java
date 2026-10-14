package de.vali.minigames.game;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemRaceCatalogTest {
    @Test
    void difficultyNamesSupportGermanAndEnglishCommands() {
        assertEquals("leicht", ItemRaceDifficulty.EASY.germanName());
        assertEquals("easy", ItemRaceDifficulty.EASY.commandName());
        assertEquals("normal", ItemRaceDifficulty.NORMAL.germanName());
        assertEquals("normal", ItemRaceDifficulty.NORMAL.commandName());
        assertEquals("schwer", ItemRaceDifficulty.HARD.germanName());
        assertEquals("hard", ItemRaceDifficulty.HARD.commandName());
        assertEquals("gemischt", ItemRaceDifficulty.MIXED.germanName());
        assertEquals("mixed", ItemRaceDifficulty.MIXED.commandName());
    }

    @Test
    void tiersHaveEnoughDistinctVanillaIdsAndDoNotOverlap() {
        Set<String> allIds = new HashSet<>();
        for (ItemRaceDifficulty difficulty : List.of(
                ItemRaceDifficulty.EASY, ItemRaceDifficulty.NORMAL, ItemRaceDifficulty.HARD)) {
            List<String> pool = ItemRaceCatalog.pool(difficulty);
            assertTrue(pool.size() >= ItemRaceCatalog.MAX_ITEMS, difficulty.name());
            for (String id : pool) {
                assertTrue(id.matches("minecraft:[a-z0-9_]+"), id);
                assertTrue(allIds.add(id), "Duplicate item: " + id);
            }
        }
        assertEquals(allIds, new HashSet<>(ItemRaceCatalog.pool(ItemRaceDifficulty.MIXED)));
        assertEquals(allIds.size(), ItemRaceCatalog.pool(ItemRaceDifficulty.MIXED).size());
    }

    @Test
    void mixedContainsAllThreePoolsInStableOrder() {
        List<String> combined = Stream.of(ItemRaceDifficulty.EASY, ItemRaceDifficulty.NORMAL, ItemRaceDifficulty.HARD)
                .flatMap(difficulty -> ItemRaceCatalog.pool(difficulty).stream()).toList();
        assertEquals(combined, ItemRaceCatalog.pool(ItemRaceDifficulty.MIXED));
    }

    @Test
    void catalogExcludesUnobtainableBlocksAndUncontainedLiquids() {
        Set<String> unavailable = Set.of("minecraft:air", "minecraft:bedrock", "minecraft:barrier",
                "minecraft:command_block", "minecraft:structure_block", "minecraft:spawner",
                "minecraft:trial_spawner", "minecraft:water", "minecraft:lava", "minecraft:debug_stick");
        for (String id : ItemRaceCatalog.pool(ItemRaceDifficulty.MIXED)) {
            assertFalse(unavailable.contains(id), id);
            assertFalse(id.endsWith("_spawn_egg"), id);
        }
    }

    @Test
    void selectsOnlyUniqueTargetsFromTheRequestedTierAtEverySupportedSize() {
        for (ItemRaceDifficulty difficulty : ItemRaceDifficulty.values()) {
            for (int count = ItemRaceCatalog.MIN_ITEMS; count <= ItemRaceCatalog.MAX_ITEMS; count++) {
                List<String> selected = ItemRaceCatalog.select(difficulty, count, new Random(count));
                assertEquals(count, selected.size());
                assertEquals(count, new HashSet<>(selected).size());
                assertTrue(ItemRaceCatalog.pool(difficulty).containsAll(selected));
            }
        }
    }

    @Test
    void seededSelectionsAreReproducibleAndLeaveThePoolUnchanged() {
        List<String> originalPool = List.copyOf(ItemRaceCatalog.pool(ItemRaceDifficulty.NORMAL));
        List<String> first = ItemRaceCatalog.select(ItemRaceDifficulty.NORMAL, 20, new Random(12345));
        List<String> repeated = ItemRaceCatalog.select(ItemRaceDifficulty.NORMAL, 20, new Random(12345));

        assertEquals(first, repeated);
        assertEquals(originalPool, ItemRaceCatalog.pool(ItemRaceDifficulty.NORMAL));
        assertFalse(first.equals(ItemRaceCatalog.select(ItemRaceDifficulty.NORMAL, 20, new Random(54321))));
    }

    @Test
    void poolsAndSelectionsAreImmutable() {
        for (ItemRaceDifficulty difficulty : ItemRaceDifficulty.values()) {
            assertThrows(UnsupportedOperationException.class,
                    () -> ItemRaceCatalog.pool(difficulty).add("minecraft:stone"));
            assertThrows(UnsupportedOperationException.class,
                    () -> ItemRaceCatalog.pool(difficulty).set(0, "minecraft:stone"));
        }
        List<String> selected = ItemRaceCatalog.select(ItemRaceDifficulty.EASY, 5, new Random(1));
        assertThrows(UnsupportedOperationException.class, () -> selected.add("minecraft:stone"));
        assertThrows(UnsupportedOperationException.class, () -> selected.set(0, "minecraft:stone"));
    }

    @Test
    void rejectsOutOfRangeCountsAndMissingArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> ItemRaceCatalog.select(ItemRaceDifficulty.EASY, 0, new Random(1)));
        assertThrows(IllegalArgumentException.class,
                () -> ItemRaceCatalog.select(ItemRaceDifficulty.EASY, -1, new Random(1)));
        assertThrows(IllegalArgumentException.class,
                () -> ItemRaceCatalog.select(ItemRaceDifficulty.MIXED, ItemRaceCatalog.MAX_ITEMS + 1, new Random(1)));
        assertThrows(NullPointerException.class, () -> ItemRaceCatalog.pool(null));
        assertThrows(NullPointerException.class, () -> ItemRaceCatalog.select(null, 5, new Random(1)));
        assertThrows(NullPointerException.class,
                () -> ItemRaceCatalog.select(ItemRaceDifficulty.EASY, 5, null));
    }
}
