package de.vali.minigames.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static de.vali.minigames.game.HideAndSeekGame.Phase.HIDING;
import static de.vali.minigames.game.HideAndSeekGame.Phase.IDLE;
import static de.vali.minigames.game.HideAndSeekGame.Phase.SEEKING;
import static de.vali.minigames.game.HideAndSeekGame.Phase.WAITING_READY;
import static de.vali.minigames.game.HideAndSeekGame.ReadyResult.NOT_HIDER;
import static de.vali.minigames.game.HideAndSeekGame.ReadyResult.NOT_WAITING;
import static de.vali.minigames.game.HideAndSeekGame.ReadyResult.READY;
import static de.vali.minigames.game.HideAndSeekGame.TickResult.HIDER_WON;
import static de.vali.minigames.game.HideAndSeekGame.TickResult.NONE;
import static de.vali.minigames.game.HideAndSeekGame.TickResult.READY_TIMED_OUT;
import static de.vali.minigames.game.HideAndSeekGame.TickResult.SEEKING_STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HideAndSeekGameTest {
    private static final UUID SEEKER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HIDER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OUTSIDER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final HideAndSeekGame game = new HideAndSeekGame();

    @Test
    void idleHasNoParticipantsAndCannotProduceAnOutcome() {
        assertReset();
        assertEquals(NOT_WAITING, game.ready(HIDER));
        assertFalse(game.tag(SEEKER, HIDER));
        assertFalse(game.leave(SEEKER));
        assertFalse(game.stop());
        assertEquals(NONE, game.tick());
        assertReset();
    }

    @Test
    void openingAssignsExactlyTwoRolesAndDoesNotAllowAnActiveRoundToBeReplaced() {
        assertTrue(game.open(SEEKER, HIDER, 30, 180));
        assertEquals(WAITING_READY, game.phase());
        assertEquals(SEEKER, game.seeker().orElseThrow());
        assertEquals(HIDER, game.hider().orElseThrow());
        assertEquals(60, game.remainingSeconds());
        assertTrue(game.isParticipant(SEEKER));
        assertTrue(game.isParticipant(HIDER));
        assertFalse(game.isParticipant(OUTSIDER));
        assertFalse(game.isParticipant(null));
        assertFalse(game.open(OUTSIDER, SEEKER, 1, 1));
        assertEquals(SEEKER, game.seeker().orElseThrow());
        assertEquals(HIDER, game.hider().orElseThrow());
        assertEquals(60, game.remainingSeconds());
    }

    @Test
    void readyTimeoutOccursOnExactlyTheTwelveHundredthTickAndFullyResets() {
        game.open(SEEKER, HIDER, 30, 180);
        advance(19);
        assertEquals(60, game.remainingSeconds());
        assertEquals(NONE, game.tick());
        assertEquals(59, game.remainingSeconds());
        advance(1179);
        assertEquals(WAITING_READY, game.phase());
        assertEquals(1, game.remainingSeconds());
        assertEquals(READY_TIMED_OUT, game.tick());
        assertReset();
        assertEquals(NONE, game.tick());
        assertEquals(NOT_WAITING, game.ready(HIDER));
        assertTrue(game.open(HIDER, SEEKER, 1, 1));
        assertEquals(60, game.remainingSeconds());
    }

    @Test
    void onlyTheAssignedHiderCanStartHidingAndRepeatedReadinessCannotResetTheClock() {
        game.open(SEEKER, HIDER, 30, 180);
        advance(1199);
        assertEquals(NOT_HIDER, game.ready(SEEKER));
        assertEquals(NOT_HIDER, game.ready(OUTSIDER));
        assertEquals(NOT_HIDER, game.ready(null));
        assertEquals(WAITING_READY, game.phase());
        assertEquals(1, game.remainingSeconds());
        assertEquals(READY, game.ready(HIDER));
        assertEquals(HIDING, game.phase());
        assertEquals(30, game.remainingSeconds());
        advance(20);
        assertEquals(NOT_WAITING, game.ready(HIDER));
        assertEquals(29, game.remainingSeconds());
    }

    @Test
    void hidingAndSeekingEachReceiveTheirFullConfiguredDuration() {
        game.open(SEEKER, HIDER, 2, 3);
        assertEquals(READY, game.ready(HIDER));
        advance(19);
        assertEquals(2, game.remainingSeconds());
        assertEquals(NONE, game.tick());
        assertEquals(1, game.remainingSeconds());
        advance(19);
        assertEquals(HIDING, game.phase());
        assertEquals(SEEKING_STARTED, game.tick());
        assertEquals(SEEKING, game.phase());
        assertEquals(3, game.remainingSeconds());
        assertEquals(NOT_WAITING, game.ready(HIDER));
        advance(59);
        assertEquals(SEEKING, game.phase());
        assertEquals(1, game.remainingSeconds());
        assertEquals(HIDER_WON, game.tick());
        assertReset();
        assertEquals(NONE, game.tick());
        assertFalse(game.tag(SEEKER, HIDER));
    }

    @Test
    void seekerCannotTagWhileWaitingOrDuringHiding() {
        game.open(SEEKER, HIDER, 1, 1);
        assertFalse(game.tag(SEEKER, HIDER));
        assertEquals(WAITING_READY, game.phase());
        game.ready(HIDER);
        advance(19);
        assertFalse(game.tag(SEEKER, HIDER));
        assertEquals(HIDING, game.phase());
        assertEquals(SEEKING_STARTED, game.tick());
        assertTrue(game.tag(SEEKER, HIDER));
        assertReset();
    }

    @Test
    void tagRequiresTheExactSeekerAndHiderAndAwardsOnlyOneWin() {
        startSeeking(5);
        assertFalse(game.tag(HIDER, SEEKER));
        assertFalse(game.tag(OUTSIDER, HIDER));
        assertFalse(game.tag(SEEKER, OUTSIDER));
        assertFalse(game.tag(SEEKER, SEEKER));
        assertFalse(game.tag(null, HIDER));
        assertFalse(game.tag(SEEKER, null));
        assertEquals(SEEKING, game.phase());
        advance(99);
        assertTrue(game.tag(SEEKER, HIDER));
        assertReset();
        assertFalse(game.tag(SEEKER, HIDER));
        assertEquals(NONE, game.tick());
        assertTrue(game.open(HIDER, SEEKER, 2, 3));
        assertEquals(HIDER, game.seeker().orElseThrow());
        assertEquals(SEEKER, game.hider().orElseThrow());
        assertEquals(READY, game.ready(SEEKER));
        assertEquals(2, game.remainingSeconds());
    }

    @ParameterizedTest
    @EnumSource(value = HideAndSeekGame.Phase.class, names = {"WAITING_READY", "HIDING", "SEEKING"})
    void outsidersCannotAbortAndEitherParticipantCanLeaveDuringEveryActivePhase(HideAndSeekGame.Phase phase) {
        for (UUID departingPlayer : new UUID[] {SEEKER, HIDER}) {
            enter(phase);
            assertFalse(game.leave(OUTSIDER));
            assertFalse(game.leave(null));
            assertEquals(phase, game.phase());
            assertTrue(game.leave(departingPlayer));
            assertReset();
            assertFalse(game.leave(departingPlayer));
            assertEquals(NONE, game.tick());
        }
    }

    @ParameterizedTest
    @EnumSource(value = HideAndSeekGame.Phase.class, names = {"WAITING_READY", "HIDING", "SEEKING"})
    void stoppingEveryActivePhaseAllowsAFreshRoundWithFreshDurations(HideAndSeekGame.Phase phase) {
        enter(phase);
        assertTrue(game.stop());
        assertReset();
        assertFalse(game.stop());
        assertTrue(game.open(OUTSIDER, SEEKER, 3, 4));
        assertEquals(60, game.remainingSeconds());
        assertEquals(READY, game.ready(SEEKER));
        assertEquals(3, game.remainingSeconds());
        advance(59);
        assertEquals(SEEKING_STARTED, game.tick());
        assertEquals(4, game.remainingSeconds());
        assertEquals(OUTSIDER, game.seeker().orElseThrow());
        assertEquals(SEEKER, game.hider().orElseThrow());
        assertFalse(game.isParticipant(HIDER));
    }

    @ParameterizedTest
    @CsvSource({"0,180", "301,180", "30,0", "30,1801", "-1,180", "30,-1"})
    void rejectsOutOfRangeDurationsWithoutOpeningARound(int hidingSeconds, int seekingSeconds) {
        assertThrows(IllegalArgumentException.class,
                () -> game.open(SEEKER, HIDER, hidingSeconds, seekingSeconds));
        assertReset();
    }

    @Test
    void rejectsNullAndIdenticalPlayersAndAcceptsMaximumDurations() {
        assertThrows(NullPointerException.class, () -> game.open(null, HIDER, 30, 180));
        assertThrows(NullPointerException.class, () -> game.open(SEEKER, null, 30, 180));
        assertThrows(IllegalArgumentException.class, () -> game.open(SEEKER, SEEKER, 30, 180));
        assertReset();
        assertTrue(game.open(SEEKER, HIDER, 300, 1800));
        assertEquals(READY, game.ready(HIDER));
        assertEquals(300, game.remainingSeconds());
        advance(5999);
        assertEquals(SEEKING_STARTED, game.tick());
        assertEquals(1800, game.remainingSeconds());
    }

    @Test
    void timeoutOutcomeCanOnlyHappenOnceAndTheNextRoundStartsFresh() {
        startSeeking(1);
        advance(19);
        assertEquals(HIDER_WON, game.tick());
        assertReset();
        assertEquals(NONE, game.tick());
        assertTrue(game.open(OUTSIDER, SEEKER, 2, 4));
        assertEquals(60, game.remainingSeconds());
        assertEquals(READY, game.ready(SEEKER));
        advance(39);
        assertEquals(SEEKING_STARTED, game.tick());
        assertEquals(4, game.remainingSeconds());
        assertTrue(game.tag(OUTSIDER, SEEKER));
        assertReset();
    }

    private void startSeeking(int seekingSeconds) {
        assertTrue(game.open(SEEKER, HIDER, 1, seekingSeconds));
        assertEquals(READY, game.ready(HIDER));
        advance(19);
        assertEquals(SEEKING_STARTED, game.tick());
    }

    private void enter(HideAndSeekGame.Phase phase) {
        assertTrue(game.open(SEEKER, HIDER, 1, 2));
        if (phase != WAITING_READY) {
            assertEquals(READY, game.ready(HIDER));
        }
        if (phase == SEEKING) {
            advance(19);
            assertEquals(SEEKING_STARTED, game.tick());
        }
        assertEquals(phase, game.phase());
    }

    private void advance(int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            assertEquals(NONE, game.tick());
        }
    }

    private void assertReset() {
        assertEquals(IDLE, game.phase());
        assertEquals(0, game.remainingSeconds());
        assertTrue(game.seeker().isEmpty());
        assertTrue(game.hider().isEmpty());
        assertFalse(game.isParticipant(SEEKER));
        assertFalse(game.isParticipant(HIDER));
        assertFalse(game.isParticipant(OUTSIDER));
        assertFalse(game.isParticipant(null));
    }
}
