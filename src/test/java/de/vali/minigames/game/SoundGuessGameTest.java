package de.vali.minigames.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static de.vali.minigames.game.SoundGuessGame.JoinResult.ALREADY_JOINED;
import static de.vali.minigames.game.SoundGuessGame.JoinResult.JOINED;
import static de.vali.minigames.game.SoundGuessGame.JoinResult.NO_LOBBY;
import static de.vali.minigames.game.SoundGuessGame.Phase.IDLE;
import static de.vali.minigames.game.SoundGuessGame.Phase.LOBBY;
import static de.vali.minigames.game.SoundGuessGame.Phase.RUNNING;
import static de.vali.minigames.game.SoundGuessGame.ReplayVoteResult.ALREADY_VOTED;
import static de.vali.minigames.game.SoundGuessGame.ReplayVoteResult.APPROVED;
import static de.vali.minigames.game.SoundGuessGame.ReplayVoteResult.COOLDOWN;
import static de.vali.minigames.game.SoundGuessGame.ReplayVoteResult.NOT_PARTICIPANT;
import static de.vali.minigames.game.SoundGuessGame.ReplayVoteResult.NOT_RUNNING;
import static de.vali.minigames.game.SoundGuessGame.ReplayVoteResult.VOTED;
import static de.vali.minigames.game.SoundGuessGame.TickResult.NONE;
import static de.vali.minigames.game.SoundGuessGame.TickResult.NO_PARTICIPANTS;
import static de.vali.minigames.game.SoundGuessGame.TickResult.STARTED;
import static de.vali.minigames.game.SoundGuessGame.TickResult.TIMED_OUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundGuessGameTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OUTSIDER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID CHARLIE = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID DANA = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final SoundRiddle CAT = new SoundRiddle("minecraft:entity.cat.ambient", "Katze",
            List.of("Ein Haustier", "Es miaut"), Set.of("cat"));

    private final SoundGuessGame game = new SoundGuessGame(List.of(CAT), new Random(1));

    @Test
    void initiallyIdleAndOnlyAcceptsJoinsInALobby() {
        assertEquals(IDLE, game.phase());
        assertEquals(0, game.remainingSeconds());
        assertEquals(NO_LOBBY, game.join(ALICE));
        assertFalse(game.stop());
        assertEquals(NONE, game.tick());
        assertTrue(game.currentRiddle().isEmpty());

        assertTrue(game.openLobby(1, 10));
        assertEquals(LOBBY, game.phase());
        assertEquals(JOINED, game.join(ALICE));
        assertEquals(ALREADY_JOINED, game.join(ALICE));
        assertEquals(Set.of(ALICE), game.participants());
        assertFalse(game.openLobby(2, 20));
        advance(19);
        assertEquals(STARTED, game.tick());
        assertEquals(RUNNING, game.phase());
        assertEquals(NO_LOBBY, game.join(BOB));
        assertFalse(game.openLobby(2, 20));
    }

    @Test
    void lobbyStartsAfterExactlyItsConfiguredTicks() {
        game.openLobby(2, 7);
        game.join(ALICE);
        assertEquals(2, game.remainingSeconds());
        advance(19);
        assertEquals(2, game.remainingSeconds());
        assertEquals(NONE, game.tick());
        assertEquals(1, game.remainingSeconds());
        assertTrue(game.currentRiddle().isEmpty());
        advance(19);
        assertEquals(LOBBY, game.phase());
        assertEquals(STARTED, game.tick());
        assertEquals(7, game.remainingSeconds());
        assertEquals(CAT, game.currentRiddle().orElseThrow());
    }

    @Test
    void lobbyWithNoParticipantsAbortsAndResets() {
        game.openLobby(1, 10);
        advance(19);
        assertEquals(NO_PARTICIPANTS, game.tick());
        assertReset();
        assertEquals(NONE, game.tick());
        assertTrue(game.openLobby(1, 1));
    }

    @Test
    void participantsCannotGuessBeforeTheRoundStarts() {
        game.openLobby(1, 10);
        game.join(ALICE);
        assertFalse(game.guess(ALICE, "Katze"));
        assertEquals(LOBBY, game.phase());
        assertTrue(game.nextHint(ALICE).isEmpty());
        assertFalse(game.requestReplay(ALICE));
    }

    @Test
    void onlyParticipantsCanWinAndTheFirstCorrectGuessEndsTheRound() {
        start(10, ALICE, BOB);
        assertFalse(game.guess(OUTSIDER, "Katze"));
        assertFalse(game.guess(ALICE, "Wolf"));
        assertFalse(game.guess(ALICE, "Ich glaube Katze"));
        assertEquals(RUNNING, game.phase());
        assertTrue(game.guess(BOB, "CAT!"));
        assertFalse(game.guess(ALICE, "Katze"));
        assertReset();
    }

    @Test
    void runningRoundTimesOutAfterExactlyItsConfiguredTicks() {
        start(2, ALICE);
        advance(20);
        assertEquals(1, game.remainingSeconds());
        advance(19);
        assertEquals(RUNNING, game.phase());
        assertEquals(TIMED_OUT, game.tick());
        assertFalse(game.guess(ALICE, "Katze"));
        assertReset();
    }

    @Test
    void hintsProgressPerParticipantAndRepeatTheFinalHint() {
        start(10, ALICE, BOB);
        assertTrue(game.nextHint(OUTSIDER).isEmpty());
        assertEquals("Ein Haustier", game.nextHint(ALICE).orElseThrow());
        assertEquals("Es miaut", game.nextHint(ALICE).orElseThrow());
        assertEquals("Es miaut", game.nextHint(ALICE).orElseThrow());
        assertEquals("Ein Haustier", game.nextHint(BOB).orElseThrow());
    }

    @Test
    void replayCooldownUsesExactlyOneHundredRunningTicksPerPlayer() {
        start(20, ALICE, BOB);
        assertFalse(game.requestReplay(OUTSIDER));
        assertTrue(game.requestReplay(ALICE));
        assertFalse(game.requestReplay(ALICE));
        assertTrue(game.requestReplay(BOB));
        advance(99);
        assertFalse(game.requestReplay(ALICE));
        assertEquals(NONE, game.tick());
        assertTrue(game.requestReplay(ALICE));
        assertFalse(game.requestReplay(ALICE));
        assertTrue(game.requestReplay(BOB));
    }

    @Test
    void replayVotesRequireCurrentParticipantsInARunningRoundAndRejectDuplicates() {
        assertEquals(NOT_RUNNING, game.voteReplay(ALICE));
        assertEquals(0, game.requiredReplayVotes());
        assertFalse(game.consumeApprovedReplayVote());

        game.openLobby(1, 10);
        game.join(ALICE);
        game.join(BOB);
        assertEquals(NOT_RUNNING, game.voteReplay(ALICE));
        assertEquals(0, game.requiredReplayVotes());
        assertFalse(game.consumeApprovedReplayVote());
        advance(19);
        assertEquals(STARTED, game.tick());

        assertEquals(NOT_PARTICIPANT, game.voteReplay(OUTSIDER));
        assertEquals(0, game.replayVoteCount());
        assertEquals(VOTED, game.voteReplay(ALICE));
        assertEquals(ALREADY_VOTED, game.voteReplay(ALICE));
        assertEquals(1, game.replayVoteCount());
        assertTrue(game.leave(ALICE));
        assertEquals(NOT_PARTICIPANT, game.voteReplay(ALICE));
        assertEquals(0, game.replayVoteCount());
        assertFalse(game.consumeApprovedReplayVote());
    }

    @Test
    void replayVotesRequireStrictMajoritiesForTwoThreeAndFourPlayers() {
        start(20, ALICE, BOB);
        assertEquals(2, game.requiredReplayVotes());
        assertEquals(VOTED, game.voteReplay(ALICE));
        assertEquals(APPROVED, game.voteReplay(BOB));
        assertEquals(0, game.replayVoteCount());
        assertFalse(game.consumeApprovedReplayVote());

        game.stop();
        start(20, ALICE, BOB, CHARLIE);
        assertEquals(2, game.requiredReplayVotes());
        assertEquals(VOTED, game.voteReplay(ALICE));
        assertEquals(APPROVED, game.voteReplay(BOB));

        game.stop();
        start(20, ALICE, BOB, CHARLIE, DANA);
        assertEquals(3, game.requiredReplayVotes());
        assertEquals(VOTED, game.voteReplay(ALICE));
        assertEquals(VOTED, game.voteReplay(BOB));
        assertEquals(2, game.replayVoteCount());
        assertFalse(game.consumeApprovedReplayVote());
        assertEquals(APPROVED, game.voteReplay(CHARLIE));
        assertEquals(0, game.replayVoteCount());
        assertFalse(game.consumeApprovedReplayVote());
    }

    @Test
    void loneParticipantCanApproveAndSharedCooldownEndsAfterExactlyOneHundredTicks() {
        start(20, ALICE);
        assertEquals(1, game.requiredReplayVotes());
        assertEquals(0, game.sharedReplayCooldownSeconds());
        assertFalse(game.consumeApprovedReplayVote());
        assertEquals(APPROVED, game.voteReplay(ALICE));
        assertEquals(5, game.sharedReplayCooldownSeconds());
        assertEquals(COOLDOWN, game.voteReplay(ALICE));
        assertEquals(0, game.replayVoteCount());
        assertFalse(game.consumeApprovedReplayVote());
        advance(1);
        assertEquals(5, game.sharedReplayCooldownSeconds());
        advance(19);
        assertEquals(4, game.sharedReplayCooldownSeconds());
        advance(79);
        assertEquals(1, game.sharedReplayCooldownSeconds());
        assertEquals(COOLDOWN, game.voteReplay(ALICE));
        assertEquals(NONE, game.tick());
        assertEquals(0, game.sharedReplayCooldownSeconds());
        assertEquals(APPROVED, game.voteReplay(ALICE));
    }

    @Test
    void sharedReplaysPreservePersonalCooldownHintsAndRoundDeadline() {
        start(10, ALICE, BOB);
        assertTrue(game.requestReplay(ALICE));
        assertEquals("Ein Haustier", game.nextHint(ALICE).orElseThrow());
        assertEquals(VOTED, game.voteReplay(ALICE));
        advance(20);
        assertTrue(game.requestReplay(BOB));
        assertEquals(1, game.replayVoteCount());
        assertEquals(APPROVED, game.voteReplay(BOB));
        assertEquals(9, game.remainingSeconds());
        assertEquals(CAT, game.currentRiddle().orElseThrow());
        assertEquals("Es miaut", game.nextHint(ALICE).orElseThrow());
        assertEquals("Ein Haustier", game.nextHint(BOB).orElseThrow());
        assertFalse(game.requestReplay(ALICE));
        advance(79);
        assertFalse(game.requestReplay(ALICE));
        assertEquals(NONE, game.tick());
        assertTrue(game.requestReplay(ALICE));
        assertEquals(1, game.sharedReplayCooldownSeconds());
        assertEquals(COOLDOWN, game.voteReplay(ALICE));
        advance(20);
        assertEquals(VOTED, game.voteReplay(ALICE));
        assertTrue(game.requestReplay(BOB));
        assertEquals(1, game.replayVoteCount());
        advance(79);
        assertEquals(TIMED_OUT, game.tick());
        assertReset();
    }

    @Test
    void leavingANonvoterCanApprovePendingVotesButOnlyOnce() {
        start(20, ALICE, BOB, CHARLIE, DANA);
        assertEquals(VOTED, game.voteReplay(ALICE));
        assertEquals(VOTED, game.voteReplay(BOB));
        assertTrue(game.leave(DANA));
        assertEquals(2, game.requiredReplayVotes());
        assertEquals(2, game.replayVoteCount());
        assertTrue(game.consumeApprovedReplayVote());
        assertEquals(0, game.replayVoteCount());
        assertEquals(5, game.sharedReplayCooldownSeconds());
        assertFalse(game.consumeApprovedReplayVote());
        assertEquals(COOLDOWN, game.voteReplay(CHARLIE));
    }

    @Test
    void leavingAVoterRemovesTheirVoteFromTheMajority() {
        start(20, ALICE, BOB, CHARLIE, DANA);
        assertEquals(VOTED, game.voteReplay(ALICE));
        assertEquals(VOTED, game.voteReplay(BOB));
        assertTrue(game.leave(ALICE));
        assertEquals(1, game.replayVoteCount());
        assertEquals(2, game.requiredReplayVotes());
        assertFalse(game.consumeApprovedReplayVote());
        assertEquals(APPROVED, game.voteReplay(CHARLIE));
    }

    @Test
    void pendingVotesDoNotExpireDuringTheRound() {
        start(20, ALICE, BOB);
        assertEquals(VOTED, game.voteReplay(ALICE));
        advance(200);
        assertEquals(1, game.replayVoteCount());
        assertEquals(ALREADY_VOTED, game.voteReplay(ALICE));
        assertEquals(APPROVED, game.voteReplay(BOB));
    }

    @Test
    void leavingRemovesEligibilityAndTheLastDepartureEndsARunningRound() {
        start(10, ALICE, BOB);
        assertFalse(game.leave(OUTSIDER));
        assertTrue(game.leave(ALICE));
        assertEquals(RUNNING, game.phase());
        assertEquals(Set.of(BOB), game.participants());
        assertFalse(game.guess(ALICE, "Katze"));
        assertTrue(game.nextHint(ALICE).isEmpty());
        assertFalse(game.requestReplay(ALICE));
        assertTrue(game.leave(BOB));
        assertReset();
    }

    @Test
    void leavingAnEmptyLobbyStillAllowsNewPlayersBeforeTheDeadline() {
        game.openLobby(1, 10);
        game.join(ALICE);
        assertTrue(game.leave(ALICE));
        assertEquals(LOBBY, game.phase());
        assertEquals(JOINED, game.join(BOB));
        advance(19);
        assertEquals(STARTED, game.tick());
        assertEquals(Set.of(BOB), game.participants());
    }

    @Test
    void stoppingAndRestartingResetsHintsReplaysVotesAndMembership() {
        start(10, ALICE, BOB);
        game.nextHint(ALICE);
        assertTrue(game.requestReplay(ALICE));
        assertEquals(VOTED, game.voteReplay(ALICE));
        assertTrue(game.stop());
        assertReset();
        assertFalse(game.stop());

        start(10, ALICE);
        assertEquals("Ein Haustier", game.nextHint(ALICE).orElseThrow());
        assertTrue(game.requestReplay(ALICE));
        assertEquals(0, game.replayVoteCount());
        assertEquals(0, game.sharedReplayCooldownSeconds());
        assertEquals(APPROVED, game.voteReplay(ALICE));
        assertEquals(10, game.remainingSeconds());
    }

    @Test
    void winTimeoutAndLastDepartureEachPermitAFreshRound() {
        start(10, ALICE);
        game.nextHint(ALICE);
        game.requestReplay(ALICE);
        assertEquals(APPROVED, game.voteReplay(ALICE));
        assertTrue(game.guess(ALICE, "Katze"));
        assertReset();
        assertFreshPrivateState();

        game.stop();
        assertReset();
        start(1, ALICE);
        game.nextHint(ALICE);
        game.requestReplay(ALICE);
        assertEquals(APPROVED, game.voteReplay(ALICE));
        advance(19);
        assertEquals(TIMED_OUT, game.tick());
        assertReset();
        assertFreshPrivateState();

        game.leave(ALICE);
        assertReset();
        assertFreshPrivateState();
    }

    @Test
    void participantSnapshotsAreImmutableAndSurviveTheEndOfARound() {
        start(10, ALICE);
        Set<UUID> snapshot = game.participants();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(BOB));
        game.guess(ALICE, "Katze");
        assertEquals(Set.of(ALICE), snapshot);
        assertTrue(game.participants().isEmpty());
    }

    @Test
    void rejectsInvalidDurationsAndAnEmptyCatalog() {
        assertThrows(IllegalArgumentException.class, () -> game.openLobby(0, 60));
        assertThrows(IllegalArgumentException.class, () -> game.openLobby(301, 60));
        assertThrows(IllegalArgumentException.class, () -> game.openLobby(15, 0));
        assertThrows(IllegalArgumentException.class, () -> game.openLobby(15, 601));
        assertThrows(IllegalArgumentException.class, () -> new SoundGuessGame(List.of(), new Random(1)));
        assertReset();
        assertTrue(game.openLobby(300, 600));
        assertEquals(300, game.remainingSeconds());
    }

    @Test
    void catalogIsDefensivelyCopiedAndRandomChoiceCanSelectEveryEntry() {
        SoundRiddle wolf = new SoundRiddle("minecraft:entity.wolf.ambient", "Wolf", List.of("Wald"), Set.of());
        List<SoundRiddle> catalog = new ArrayList<>(List.of(CAT, wolf));
        SoundGuessGame chooseLast = new SoundGuessGame(catalog, new Random(0) {
            @Override
            public int nextInt(int bound) {
                return bound - 1;
            }
        });
        catalog.clear();
        chooseLast.openLobby(1, 10);
        chooseLast.join(ALICE);
        for (int tick = 0; tick < 20; tick++) {
            chooseLast.tick();
        }
        assertEquals(wolf, chooseLast.currentRiddle().orElseThrow());
    }

    private void assertFreshPrivateState() {
        start(10, ALICE);
        assertEquals("Ein Haustier", game.nextHint(ALICE).orElseThrow());
        assertTrue(game.requestReplay(ALICE));
        assertEquals(0, game.replayVoteCount());
        assertEquals(0, game.sharedReplayCooldownSeconds());
        assertEquals(APPROVED, game.voteReplay(ALICE));
        assertEquals(Set.of(ALICE), game.participants());
    }

    private void start(int roundSeconds, UUID... playerIds) {
        assertTrue(game.openLobby(1, roundSeconds));
        for (UUID playerId : playerIds) {
            assertEquals(JOINED, game.join(playerId));
        }
        advance(19);
        assertEquals(STARTED, game.tick());
    }

    private void advance(int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            assertEquals(NONE, game.tick());
        }
    }

    private void assertReset() {
        assertEquals(IDLE, game.phase());
        assertEquals(0, game.remainingSeconds());
        assertTrue(game.currentRiddle().isEmpty());
        assertTrue(game.participants().isEmpty());
        assertTrue(game.nextHint(ALICE).isEmpty());
        assertFalse(game.requestReplay(ALICE));
        assertEquals(0, game.replayVoteCount());
        assertEquals(0, game.requiredReplayVotes());
        assertEquals(0, game.sharedReplayCooldownSeconds());
        assertEquals(NOT_RUNNING, game.voteReplay(ALICE));
        assertFalse(game.consumeApprovedReplayVote());
    }
}
