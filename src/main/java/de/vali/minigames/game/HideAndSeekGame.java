package de.vali.minigames.game;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-independent state for a two-player round of hide and seek.
 * All methods must be called on the Minecraft server thread.
 */
public final class HideAndSeekGame {
    private static final int TICKS_PER_SECOND = 20;
    private static final int READY_TIMEOUT_SECONDS = 60;

    public enum Phase { IDLE, WAITING_READY, HIDING, SEEKING }

    public enum ReadyResult { READY, NOT_WAITING, NOT_HIDER }

    public enum TickResult { NONE, READY_TIMED_OUT, SEEKING_STARTED, HIDER_WON }

    private Phase phase = Phase.IDLE;
    private UUID seekerId;
    private UUID hiderId;
    private int remainingTicks;
    private int hidingDurationTicks;
    private int seekingDurationTicks;

    public boolean open(UUID seekerId, UUID hiderId, int hidingSeconds, int seekingSeconds) {
        Objects.requireNonNull(seekerId, "seekerId");
        Objects.requireNonNull(hiderId, "hiderId");
        if (seekerId.equals(hiderId)) {
            throw new IllegalArgumentException("Seeker and hider must be different players.");
        }
        requireRange(hidingSeconds, 1, 300, "hidingSeconds");
        requireRange(seekingSeconds, 1, 1800, "seekingSeconds");
        if (phase != Phase.IDLE) {
            return false;
        }
        this.seekerId = seekerId;
        this.hiderId = hiderId;
        hidingDurationTicks = hidingSeconds * TICKS_PER_SECOND;
        seekingDurationTicks = seekingSeconds * TICKS_PER_SECOND;
        remainingTicks = READY_TIMEOUT_SECONDS * TICKS_PER_SECOND;
        phase = Phase.WAITING_READY;
        return true;
    }

    /** Only the assigned hider can start the hiding phase. */
    public ReadyResult ready(UUID playerId) {
        if (phase != Phase.WAITING_READY) {
            return ReadyResult.NOT_WAITING;
        }
        if (!hiderId.equals(playerId)) {
            return ReadyResult.NOT_HIDER;
        }
        phase = Phase.HIDING;
        remainingTicks = hidingDurationTicks;
        return ReadyResult.READY;
    }

    /** Advances exactly one server tick; each phase receives its full duration. */
    public TickResult tick() {
        if (phase == Phase.IDLE || --remainingTicks > 0) {
            return TickResult.NONE;
        }
        return switch (phase) {
            case WAITING_READY -> {
                reset();
                yield TickResult.READY_TIMED_OUT;
            }
            case HIDING -> {
                phase = Phase.SEEKING;
                remainingTicks = seekingDurationTicks;
                yield TickResult.SEEKING_STARTED;
            }
            case SEEKING -> {
                reset();
                yield TickResult.HIDER_WON;
            }
            case IDLE -> TickResult.NONE;
        };
    }

    /** The adapter checks distance and line of sight before trying a tag. */
    public boolean tag(UUID playerId, UUID targetId) {
        if (phase != Phase.SEEKING || !seekerId.equals(playerId) || !hiderId.equals(targetId)) {
            return false;
        }
        reset();
        return true;
    }

    /** A departure aborts the round, without awarding a win. */
    public boolean leave(UUID playerId) {
        if (!isParticipant(playerId)) {
            return false;
        }
        reset();
        return true;
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

    public Optional<UUID> seeker() {
        return Optional.ofNullable(seekerId);
    }

    public Optional<UUID> hider() {
        return Optional.ofNullable(hiderId);
    }

    public boolean isParticipant(UUID playerId) {
        return phase != Phase.IDLE && (seekerId.equals(playerId) || hiderId.equals(playerId));
    }

    public int remainingSeconds() {
        return (remainingTicks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }

    private void reset() {
        phase = Phase.IDLE;
        seekerId = null;
        hiderId = null;
        remainingTicks = 0;
        hidingDurationTicks = 0;
        seekingDurationTicks = 0;
    }

    private static void requireRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum + ".");
        }
    }
}
