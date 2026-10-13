package de.vali.minigames.game;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Server-independent state for a single round of sound guessing.
 * All methods must be called on the Minecraft server thread.
 */
public final class SoundGuessGame {
    private static final int TICKS_PER_SECOND = 20;
    private static final int REPLAY_COOLDOWN_TICKS = 5 * TICKS_PER_SECOND;

    public enum Phase { IDLE, LOBBY, RUNNING }

    public enum JoinResult { JOINED, ALREADY_JOINED, NO_LOBBY }

    public enum TickResult { NONE, STARTED, NO_PARTICIPANTS, TIMED_OUT }

    public enum ReplayVoteResult { NOT_RUNNING, NOT_PARTICIPANT, COOLDOWN, ALREADY_VOTED, VOTED, APPROVED }

    private final List<SoundRiddle> riddles;
    private final RandomGenerator random;
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Map<UUID, Integer> hintIndexes = new HashMap<>();
    private final Map<UUID, Integer> nextReplayTicks = new HashMap<>();
    private final Set<UUID> replayVotes = new LinkedHashSet<>();

    private Phase phase = Phase.IDLE;
    private SoundRiddle currentRiddle;
    private int remainingTicks;
    private int roundDurationTicks;
    private int elapsedRoundTicks;
    private int nextSharedReplayTick;

    public SoundGuessGame(List<SoundRiddle> riddles, RandomGenerator random) {
        this.riddles = List.copyOf(Objects.requireNonNull(riddles, "riddles"));
        this.random = Objects.requireNonNull(random, "random");
        if (this.riddles.isEmpty()) {
            throw new IllegalArgumentException("At least one sound riddle is required.");
        }
    }

    public boolean openLobby(int lobbySeconds, int roundSeconds) {
        requireRange(lobbySeconds, 1, 300, "lobbySeconds");
        requireRange(roundSeconds, 1, 600, "roundSeconds");
        if (phase != Phase.IDLE) {
            return false;
        }
        reset();
        phase = Phase.LOBBY;
        remainingTicks = lobbySeconds * TICKS_PER_SECOND;
        roundDurationTicks = roundSeconds * TICKS_PER_SECOND;
        return true;
    }

    public JoinResult join(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (phase != Phase.LOBBY) {
            return JoinResult.NO_LOBBY;
        }
        return participants.add(playerId) ? JoinResult.JOINED : JoinResult.ALREADY_JOINED;
    }

    public boolean leave(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!participants.remove(playerId)) {
            return false;
        }
        hintIndexes.remove(playerId);
        nextReplayTicks.remove(playerId);
        replayVotes.remove(playerId);
        if (phase == Phase.RUNNING && participants.isEmpty()) {
            reset();
        }
        return true;
    }

    /** Advances exactly one server tick, including while a lobby is open. */
    public TickResult tick() {
        if (phase == Phase.IDLE) {
            return TickResult.NONE;
        }
        remainingTicks--;
        if (phase == Phase.LOBBY) {
            if (remainingTicks > 0) {
                return TickResult.NONE;
            }
            if (participants.isEmpty()) {
                reset();
                return TickResult.NO_PARTICIPANTS;
            }
            currentRiddle = riddles.get(random.nextInt(riddles.size()));
            phase = Phase.RUNNING;
            remainingTicks = roundDurationTicks;
            elapsedRoundTicks = 0;
            return TickResult.STARTED;
        }

        elapsedRoundTicks++;
        if (remainingTicks <= 0) {
            reset();
            return TickResult.TIMED_OUT;
        }
        return TickResult.NONE;
    }

    /** A successful guess ends the round immediately, so only one player can win. */
    public boolean guess(UUID playerId, String guess) {
        if (phase != Phase.RUNNING || !participants.contains(playerId)
                || !currentRiddle.matching(guess)) {
            return false;
        }
        reset();
        return true;
    }

    public Optional<String> nextHint(UUID playerId) {
        if (phase != Phase.RUNNING || !participants.contains(playerId)) {
            return Optional.empty();
        }
        List<String> hints = currentRiddle.hints();
        int index = hintIndexes.getOrDefault(playerId, 0);
        hintIndexes.put(playerId, Math.min(index + 1, hints.size() - 1));
        return Optional.of(hints.get(index));
    }

    /** The initial replay is allowed; subsequent replays require five game seconds. */
    public boolean requestReplay(UUID playerId) {
        if (phase != Phase.RUNNING || !participants.contains(playerId)
                || elapsedRoundTicks < nextReplayTicks.getOrDefault(playerId, 0)) {
            return false;
        }
        nextReplayTicks.put(playerId, elapsedRoundTicks + REPLAY_COOLDOWN_TICKS);
        return true;
    }

    /** A strict majority of the current participants can replay the sound for everyone. */
    public ReplayVoteResult voteReplay(UUID playerId) {
        if (phase != Phase.RUNNING) {
            return ReplayVoteResult.NOT_RUNNING;
        }
        if (!participants.contains(playerId)) {
            return ReplayVoteResult.NOT_PARTICIPANT;
        }
        if (elapsedRoundTicks < nextSharedReplayTick) {
            return ReplayVoteResult.COOLDOWN;
        }
        if (!replayVotes.add(playerId)) {
            return ReplayVoteResult.ALREADY_VOTED;
        }
        return consumeApprovedReplayVote() ? ReplayVoteResult.APPROVED : ReplayVoteResult.VOTED;
    }

    /** Also called after a departure, which may reduce the number of votes required. */
    public boolean consumeApprovedReplayVote() {
        if (phase != Phase.RUNNING || participants.isEmpty() || replayVotes.isEmpty()
                || elapsedRoundTicks < nextSharedReplayTick || replayVotes.size() < requiredReplayVotes()) {
            return false;
        }
        replayVotes.clear();
        nextSharedReplayTick = elapsedRoundTicks + REPLAY_COOLDOWN_TICKS;
        return true;
    }

    public int replayVoteCount() {
        return replayVotes.size();
    }

    public int requiredReplayVotes() {
        return phase == Phase.RUNNING ? participants.size() / 2 + 1 : 0;
    }

    public int sharedReplayCooldownSeconds() {
        if (phase != Phase.RUNNING) {
            return 0;
        }
        int ticks = Math.max(0, nextSharedReplayTick - elapsedRoundTicks);
        return (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }

    public boolean stop() {
        if (phase == Phase.IDLE) {
            return false;
        }
        reset();
        return true;
    }

    public Phase phase() {
        return phase;
    }

    public int remainingSeconds() {
        return (remainingTicks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }

    /** Returns an immutable snapshot, so callers can retain it across round transitions. */
    public Set<UUID> participants() {
        return Set.copyOf(participants);
    }

    public Optional<SoundRiddle> currentRiddle() {
        return Optional.ofNullable(currentRiddle);
    }

    private void reset() {
        phase = Phase.IDLE;
        currentRiddle = null;
        participants.clear();
        hintIndexes.clear();
        nextReplayTicks.clear();
        replayVotes.clear();
        remainingTicks = 0;
        roundDurationTicks = 0;
        elapsedRoundTicks = 0;
        nextSharedReplayTick = 0;
    }

    private static void requireRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum + ".");
        }
    }
}
