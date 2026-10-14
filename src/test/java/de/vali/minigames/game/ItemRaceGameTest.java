package de.vali.minigames.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ItemRaceGameTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OUTSIDER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final List<String> TARGETS = List.of("minecraft:apple", "minecraft:iron_ingot", "minecraft:diamond");
    private final ItemRaceGame game = new ItemRaceGame();

    @Test
    void bothPlayersShareTheChosenTargetsAndKeepIndependentCumulativeProgress() {
        assertTrue(game.start(ALICE, BOB, TARGETS));
        assertTrue(game.running());
        assertEquals(List.of(ALICE, BOB), new ArrayList<>(game.participants()));
        assertEquals(TARGETS, game.targets());
        assertTrue(game.isParticipant(ALICE));
        assertTrue(game.isParticipant(BOB));
        assertFalse(game.isParticipant(OUTSIDER));

        assertEquals(List.of("minecraft:iron_ingot"), game.collect(ALICE, List.of("minecraft:iron_ingot")));
        assertTrue(game.collected(BOB).isEmpty());
        assertEquals(List.of("minecraft:apple"), game.collect(BOB, List.of("minecraft:apple")));
        assertEquals(List.of("minecraft:apple", "minecraft:diamond"),
                game.collect(ALICE, List.of("minecraft:diamond", "minecraft:apple")));
        assertEquals(TARGETS, new ArrayList<>(game.collected(ALICE)));
        assertEquals(Set.of("minecraft:apple"), game.collected(BOB));
        assertEquals(Set.of(ALICE), game.finishers());
        assertTrue(game.running(), "The adapter decides when to award the outcome and stop.");
    }

    @Test
    void repeatedItemsNonTargetsAndOutsidersCannotChangeProgress() {
        game.start(ALICE, BOB, TARGETS);
        assertTrue(game.collect(OUTSIDER, TARGETS).isEmpty());
        assertTrue(game.collect(null, TARGETS).isEmpty());
        assertTrue(game.collected(OUTSIDER).isEmpty());
        assertTrue(game.collect(ALICE, List.of("minecraft:dirt")).isEmpty());
        assertEquals(List.of("minecraft:apple"), game.collect(ALICE,
                Arrays.asList("minecraft:apple", "minecraft:apple", "minecraft:dirt", null)));
        assertTrue(game.collect(ALICE, List.of("minecraft:apple")).isEmpty());
        assertTrue(game.collect(ALICE, List.of()).isEmpty());
        assertEquals(Set.of("minecraft:apple"), game.collected(ALICE));
        assertTrue(game.finishers().isEmpty());
    }

    @Test
    void recordingBothInventoriesBeforeScoringAllowsSimultaneousFinishers() {
        game.start(ALICE, BOB, TARGETS);
        game.collect(ALICE, TARGETS.subList(0, 2));
        game.collect(BOB, TARGETS.subList(1, 3));
        assertTrue(game.finishers().isEmpty());

        game.collect(ALICE, List.of("minecraft:diamond"));
        game.collect(BOB, List.of("minecraft:apple"));

        assertEquals(List.of(ALICE, BOB), new ArrayList<>(game.finishers()));
        assertTrue(game.running());
    }

    @Test
    void anActiveRaceCannotBeReplacedAndStoppingAllowsAFreshRace() {
        assertIdle();
        assertFalse(game.stop());
        game.start(ALICE, BOB, TARGETS);
        game.collect(ALICE, TARGETS);
        assertFalse(game.start(BOB, OUTSIDER, List.of("minecraft:bread")));
        assertEquals(TARGETS, game.targets());
        assertEquals(Set.of(ALICE, BOB), game.participants());
        assertEquals(Set.of(ALICE), game.finishers());

        assertTrue(game.stop());
        assertIdle();
        assertFalse(game.stop());
        assertTrue(game.collect(ALICE, TARGETS).isEmpty());

        assertTrue(game.start(BOB, OUTSIDER, List.of("minecraft:bread")));
        assertEquals(List.of(BOB, OUTSIDER), new ArrayList<>(game.participants()));
        assertTrue(game.collected(BOB).isEmpty());
        assertTrue(game.collected(OUTSIDER).isEmpty());
        assertTrue(game.finishers().isEmpty());
    }

    @Test
    void invalidPlayersAndTargetsCannotCreateARace() {
        assertThrows(NullPointerException.class, () -> game.start(null, BOB, TARGETS));
        assertThrows(NullPointerException.class, () -> game.start(ALICE, null, TARGETS));
        assertThrows(IllegalArgumentException.class, () -> game.start(ALICE, ALICE, TARGETS));
        assertThrows(NullPointerException.class, () -> game.start(ALICE, BOB, null));
        assertThrows(NullPointerException.class, () -> game.start(ALICE, BOB, Arrays.asList("minecraft:apple", null)));
        assertThrows(IllegalArgumentException.class, () -> game.start(ALICE, BOB, List.of()));
        assertThrows(IllegalArgumentException.class, () -> game.start(ALICE, BOB, List.of("")));
        assertThrows(IllegalArgumentException.class, () -> game.start(ALICE, BOB, List.of(" \t\n")));
        assertThrows(IllegalArgumentException.class,
                () -> game.start(ALICE, BOB, List.of("minecraft:apple", "minecraft:apple")));
        assertThrows(IllegalArgumentException.class,
                () -> game.start(ALICE, BOB, targetsOfSize(31)));
        assertIdle();
        assertTrue(game.start(ALICE, BOB, targetsOfSize(30)));
        assertEquals(30, game.targets().size());
    }

    @Test
    void inputListsAreCopiedAndExposedCollectionsAreImmutableSnapshots() {
        List<String> input = new ArrayList<>(TARGETS);
        game.start(ALICE, BOB, input);
        input.clear();
        assertEquals(TARGETS, game.targets());
        Set<UUID> participants = game.participants();
        List<String> targets = game.targets();
        List<String> newlyCollected = game.collect(ALICE, List.of("minecraft:apple"));
        Set<String> collected = game.collected(ALICE);
        game.collect(ALICE, TARGETS);
        Set<UUID> finishers = game.finishers();

        assertThrows(UnsupportedOperationException.class, participants::clear);
        assertThrows(UnsupportedOperationException.class, targets::clear);
        assertThrows(UnsupportedOperationException.class, newlyCollected::clear);
        assertThrows(UnsupportedOperationException.class, collected::clear);
        assertThrows(UnsupportedOperationException.class, finishers::clear);
        game.stop();
        assertEquals(Set.of(ALICE, BOB), participants);
        assertEquals(TARGETS, targets);
        assertEquals(List.of("minecraft:apple"), newlyCollected);
        assertEquals(Set.of("minecraft:apple"), collected);
        assertEquals(Set.of(ALICE), finishers);
    }

    private static List<String> targetsOfSize(int size) {
        return IntStream.range(0, size).mapToObj(index -> "minecraft:item_" + index).toList();
    }

    private void assertIdle() {
        assertFalse(game.running());
        assertTrue(game.participants().isEmpty());
        assertTrue(game.targets().isEmpty());
        assertFalse(game.isParticipant(ALICE));
        assertTrue(game.collected(ALICE).isEmpty());
        assertTrue(game.finishers().isEmpty());
    }
}
