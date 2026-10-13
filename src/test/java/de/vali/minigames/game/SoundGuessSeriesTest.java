package de.vali.minigames.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SoundGuessSeriesTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OUTSIDER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final SoundRiddle CAT = new SoundRiddle("minecraft:entity.cat.ambient", "Katze",
            List.of("Ein Haustier", "Es miaut"), Set.of("cat"));
    private final SoundGuessGame game = new SoundGuessGame(List.of(CAT), new Random(1));

    @Test
    void threeWinsKeepParticipantsAndWaitForTheFullCountdownBeforeEachNewSound() {
        start(game, SoundDifficulty.EASY, 3, 2, 7);
        for (int round = 1; round <= 3; round++) {
            assertEquals(round, game.roundNumber());
            assertEquals(3, game.totalRounds());
            assertEquals(7, game.remainingSeconds());
            assertTrue(game.guess(ALICE, "Katze"));
            assertFalse(game.guess(BOB, "Katze"), "A round must have exactly one winner");
            if (round < 3) {
                assertEquals(SoundGuessGame.Phase.BETWEEN_ROUNDS, game.phase());
                assertEquals(Set.of(ALICE, BOB), game.participants());
                assertEquals(SoundGuessGame.JoinResult.NO_LOBBY, game.join(OUTSIDER));
                assertTrue(game.currentRiddle().isEmpty());
                assertTrue(game.nextHint(ALICE).isEmpty());
                assertFalse(game.requestReplay(ALICE));
                assertEquals(SoundGuessGame.ReplayVoteResult.NOT_RUNNING, game.voteReplay(ALICE));
                advance(game, 39);
                assertEquals(SoundGuessGame.Phase.BETWEEN_ROUNDS, game.phase());
                assertEquals(SoundGuessGame.TickResult.STARTED, game.tick());
            }
        }
        assertIdle(game);
        advance(game, 200);
        assertIdle(game);
    }

    @Test
    void timeoutsCountAsRoundsAndContinueUntilTheConfiguredEnd() {
        start(game, SoundDifficulty.EASY, 2, 2, 1);
        advance(game, 19);
        assertEquals(SoundGuessGame.TickResult.TIMED_OUT, game.tick());
        assertEquals(SoundGuessGame.Phase.BETWEEN_ROUNDS, game.phase());
        assertEquals(1, game.roundNumber());
        assertEquals(2, game.remainingSeconds());
        assertFalse(game.guess(ALICE, "Katze"));
        advance(game, 39);
        assertEquals(SoundGuessGame.TickResult.STARTED, game.tick());
        assertEquals(2, game.roundNumber());
        assertEquals(1, game.remainingSeconds());
        advance(game, 19);
        assertEquals(SoundGuessGame.TickResult.TIMED_OUT, game.tick());
        assertIdle(game);
    }

    @Test
    void hintsReplaysCooldownsAndPendingVotesResetForEachSound() {
        start(game, SoundDifficulty.EASY, 3, 1, 10);
        assertEquals("Ein Haustier", game.nextHint(ALICE).orElseThrow());
        assertEquals("Es miaut", game.nextHint(ALICE).orElseThrow());
        assertTrue(game.requestReplay(ALICE));
        assertEquals(SoundGuessGame.ReplayVoteResult.VOTED, game.voteReplay(ALICE));
        assertEquals(SoundGuessGame.ReplayVoteResult.APPROVED, game.voteReplay(BOB));
        assertEquals(5, game.sharedReplayCooldownSeconds());
        assertTrue(game.guess(ALICE, "Katze"));
        assertEquals(0, game.sharedReplayCooldownSeconds());
        nextRound(game);
        assertEquals("Ein Haustier", game.nextHint(ALICE).orElseThrow());
        assertTrue(game.requestReplay(ALICE));
        assertEquals(SoundGuessGame.ReplayVoteResult.VOTED, game.voteReplay(ALICE));
        assertEquals(1, game.replayVoteCount());
        assertTrue(game.guess(BOB, "Katze"));
        assertEquals(0, game.replayVoteCount());
        nextRound(game);
        assertEquals(0, game.replayVoteCount());
        assertEquals(SoundGuessGame.ReplayVoteResult.VOTED, game.voteReplay(ALICE));
    }

    @Test
    void stoppingBetweenSoundsCancelsTheEntireRemainingSeries() {
        start(game, SoundDifficulty.EASY, 5, 1, 10);
        assertTrue(game.guess(ALICE, "Katze"));
        assertTrue(game.stop());
        assertIdle(game);
        advance(game, 100);
        assertIdle(game);
    }

    @Test
    void lastDepartureDuringCountdownCancelsAllFutureRounds() {
        start(game, SoundDifficulty.EASY, 5, 1, 10);
        game.guess(ALICE, "Katze");
        assertTrue(game.leave(ALICE));
        assertEquals(SoundGuessGame.Phase.BETWEEN_ROUNDS, game.phase());
        assertTrue(game.leave(BOB));
        assertIdle(game);
        advance(game, 100);
        assertIdle(game);
    }

    @Test
    void departureBetweenSoundsRemovesEligibilityInLaterRounds() {
        start(game, SoundDifficulty.EASY, 2, 1, 10);
        game.guess(ALICE, "Katze");
        game.leave(ALICE);
        nextRound(game);
        assertEquals(Set.of(BOB), game.participants());
        assertFalse(game.guess(ALICE, "Katze"));
        assertTrue(game.guess(BOB, "Katze"));
        assertIdle(game);
    }

    @ParameterizedTest
    @EnumSource(SoundDifficulty.class)
    void everyRoundUsesTheChosenDifficulty(SoundDifficulty difficulty) {
        var selectedGame = new SoundGuessGame(SoundCatalog.ALL_RIDDLES, new Random(3));
        start(selectedGame, difficulty, 4, 1, 10);
        for (int round = 1; round <= 4; round++) {
            var riddle = selectedGame.currentRiddle().orElseThrow();
            assertTrue(difficulty.includes(riddle));
            assertEquals(difficulty, selectedGame.difficulty());
            assertTrue(selectedGame.guess(ALICE, riddle.answer()));
            if (round < 4) {
                nextRound(selectedGame);
            }
        }
        assertIdle(selectedGame);
    }

    @Test
    void allSoundsPlayBeforeThePoolRepeatsAndCycleBoundariesNeverRepeatImmediately() {
        var wolf = new SoundRiddle("minecraft:entity.wolf.ambient", "Wolf", List.of("Wald"), Set.of());
        var cow = new SoundRiddle("minecraft:entity.cow.ambient", "Kuh", List.of("Milch"), Set.of());
        var series = new SoundGuessGame(List.of(CAT, wolf, cow), new Random(1) {
            private int draws;
            @Override
            public int nextInt(int bound) {
                // Force the first fresh-pool draw to select the immediately preceding sound.
                return draws++ == 3 ? bound - 1 : 0;
            }
        });
        start(series, SoundDifficulty.EASY, 12, 1, 10);
        var cycle = new HashSet<String>();
        String previous = null;
        for (int round = 1; round <= 12; round++) {
            var riddle = series.currentRiddle().orElseThrow();
            assertNotEquals(previous, riddle.soundId());
            assertTrue(cycle.add(riddle.soundId()));
            previous = riddle.soundId();
            series.guess(ALICE, riddle.answer());
            if (round % 3 == 0) {
                assertEquals(3, cycle.size());
                cycle.clear();
            }
            if (round < 12) {
                nextRound(series);
            }
        }
        assertIdle(series);
    }

    @Test
    void invalidSeriesSettingsCannotCreateAnEvent() {
        assertThrows(IllegalArgumentException.class, () -> game.openLobby(1, 10, SoundDifficulty.EASY, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> game.openLobby(1, 10, SoundDifficulty.EASY, 101, 1));
        assertThrows(IllegalArgumentException.class, () -> game.openLobby(1, 10, SoundDifficulty.EASY, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> game.openLobby(1, 10, SoundDifficulty.EASY, 1, 61));
        assertThrows(IllegalArgumentException.class, () -> game.openLobby(1, 10, SoundDifficulty.HARD, 1, 1));
        assertThrows(NullPointerException.class, () -> game.openLobby(1, 10, null, 1, 1));
        assertIdle(game);
    }

    @Test
    void aLaterEventCanChooseANewDifficultyAndDoesNotInheritItsPredecessorsSettings() {
        var series = new SoundGuessGame(SoundCatalog.ALL_RIDDLES, new Random(1));
        start(series, SoundDifficulty.NORMAL, 1, 60, 1);
        assertFalse(series.openLobby(1, 1, SoundDifficulty.HARD, 5, 1));
        assertEquals(SoundDifficulty.NORMAL, series.difficulty());
        assertEquals(1, series.totalRounds());
        series.guess(ALICE, series.currentRiddle().orElseThrow().answer());
        assertIdle(series);
        start(series, SoundDifficulty.HARD, 2, 1, 10);
        assertEquals(SoundDifficulty.HARD, series.currentRiddle().orElseThrow().difficulty());
        assertEquals(1, series.roundNumber());
        assertEquals(2, series.totalRounds());
        assertEquals(10, series.remainingSeconds());
        assertEquals(0, series.replayVoteCount());
    }

    @Test
    void difficultyPoolsAreSeparateAndMixedContainsEverySound() {
        var ids = new HashSet<String>();
        for (var difficulty : List.of(SoundDifficulty.EASY, SoundDifficulty.NORMAL, SoundDifficulty.HARD)) {
            var sounds = SoundCatalog.forDifficulty(difficulty);
            assertFalse(sounds.isEmpty());
            for (var sound : sounds) {
                assertEquals(difficulty, sound.difficulty());
                assertTrue(ids.add(sound.soundId()));
            }
            assertThrows(UnsupportedOperationException.class, sounds::clear);
        }
        assertEquals(ids.size(), SoundCatalog.forDifficulty(SoundDifficulty.MIXED).size());
        assertThrows(IllegalArgumentException.class, () -> new SoundRiddle(
                "minecraft:entity.cat.ambient", "Katze", List.of("Tipp"), Set.of(), SoundDifficulty.MIXED));
    }

    private static void start(SoundGuessGame game, SoundDifficulty difficulty, int rounds,
            int countdownSeconds, int roundSeconds) {
        assertTrue(game.openLobby(1, roundSeconds, difficulty, rounds, countdownSeconds));
        game.join(ALICE);
        game.join(BOB);
        advance(game, 19);
        assertEquals(SoundGuessGame.TickResult.STARTED, game.tick());
    }

    private static void nextRound(SoundGuessGame game) {
        advance(game, 19);
        assertEquals(SoundGuessGame.TickResult.STARTED, game.tick());
    }

    private static void advance(SoundGuessGame game, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            assertEquals(SoundGuessGame.TickResult.NONE, game.tick());
        }
    }

    private static void assertIdle(SoundGuessGame game) {
        assertEquals(SoundGuessGame.Phase.IDLE, game.phase());
        assertEquals(0, game.roundNumber());
        assertEquals(0, game.totalRounds());
        assertEquals(0, game.remainingSeconds());
        assertTrue(game.currentRiddle().isEmpty());
        assertTrue(game.participants().isEmpty());
    }
}
