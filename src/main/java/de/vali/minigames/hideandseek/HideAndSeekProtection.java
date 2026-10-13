package de.vali.minigames.hideandseek;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Temporary, reversible Minecraft state for one hide-and-seek round. */
public final class HideAndSeekProtection implements AutoCloseable {
    private static final int BLINDNESS_TICKS = 40;
    private static final double POSITION_EPSILON_SQUARED = 0.000001;
    private static final Identifier NAME_TAG_MODIFIER = modifierId("hide_and_seek_name_tag");
    private static final Identifier BELOW_NAME_MODIFIER = modifierId("hide_and_seek_below_name");
    private static final Identifier TRANSMIT_MODIFIER = modifierId("hide_and_seek_transmit");
    private static final Identifier RECEIVE_MODIFIER = modifierId("hide_and_seek_receive");

    private final MinecraftServer server;
    private final ServerPlayer seeker;
    private final ServerPlayer hider;
    private final List<Suppression> appliedSuppressions = new ArrayList<>();
    private boolean started;
    private boolean hiding;
    private boolean closed;
    private ServerLevel waitingLevel;
    private Vec3 waitingPosition;
    private float waitingYaw;
    private float waitingPitch;
    private MobEffectInstance previousBlindness;
    private MobEffectInstance ownBlindness;
    private int previousBlindnessTick;

    public HideAndSeekProtection(MinecraftServer server, ServerPlayer seeker, ServerPlayer hider) {
        this.server = Objects.requireNonNull(server);
        this.seeker = Objects.requireNonNull(seeker);
        this.hider = Objects.requireNonNull(hider);
    }

    public void beginHiding() {
        if (closed) {
            throw new IllegalStateException("Hide-and-seek protection has already been closed");
        }
        if (started) {
            return;
        }

        // Validate every required attribute before changing any player state.
        List<Suppression> suppressions = List.of(
                suppression(hider, Attributes.NAME_TAG_DISTANCE, NAME_TAG_MODIFIER),
                suppression(hider, Attributes.BELOW_NAME_DISTANCE, BELOW_NAME_MODIFIER),
                suppression(hider, Attributes.WAYPOINT_TRANSMIT_RANGE, TRANSMIT_MODIFIER),
                suppression(seeker, Attributes.WAYPOINT_RECEIVE_RANGE, RECEIVE_MODIFIER));
        waitingLevel = seeker.level();
        waitingPosition = seeker.position();
        waitingYaw = seeker.getYRot();
        waitingPitch = seeker.getXRot();
        previousBlindnessTick = server.getTickCount();
        started = true;
        hiding = true;

        for (Suppression suppression : suppressions) {
            addSuppression(suppression.attribute(), suppression.id());
            appliedSuppressions.add(suppression);
        }
        hider.level().getWaypointManager().untrackWaypoint(hider);
        seeker.level().getWaypointManager().removePlayer(seeker);

        // Removing the entity from this viewer also closes third-person/milk visibility bypasses.
        HideAndSeekVisibility.begin(hider.getUUID(), seeker.getUUID());
        refreshSeekerTracking();
        previousBlindness = seeker.getEffect(MobEffects.BLINDNESS);
        if (previousBlindness != null) {
            seeker.removeEffect(MobEffects.BLINDNESS);
        }
        applyWaitingBlindness();
        seeker.setDeltaMovement(Vec3.ZERO);
        // Send once even if stationary, so the client's outstanding movement prediction is reset.
        seeker.connection.teleport(waitingPosition.x(), waitingPosition.y(), waitingPosition.z(),
                waitingYaw, waitingPitch);
    }

    public void keepSeekerWaiting() {
        if (!hiding || closed) {
            return;
        }
        advancePreviousBlindness();
        applyWaitingBlindness();
        seeker.setDeltaMovement(Vec3.ZERO);
        if (seeker.level() == waitingLevel
                && seeker.position().distanceToSqr(waitingPosition) > POSITION_EPSILON_SQUARED) {
            seeker.connection.teleport(waitingPosition.x(), waitingPosition.y(), waitingPosition.z(),
                    seeker.getYRot(), seeker.getXRot());
        }
    }

    public void beginSeeking() {
        if (!hiding || closed) {
            return;
        }
        hiding = false;
        RuntimeException failure = null;
        try {
            restoreBlindness(null);
        } catch (RuntimeException exception) {
            failure = exception;
        }
        HideAndSeekVisibility.clear(hider.getUUID(), seeker.getUUID());
        try {
            refreshSeekerTracking();
        } catch (RuntimeException exception) {
            failure = retainFailure(failure, exception);
        }
        // Name tags and locator suppression intentionally last through the seeking phase.
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void close() {
        close(null);
    }

    /** Also works during LEAVE, without sending packets to the departing player's connection. */
    public void close(UUID disconnectedId) {
        if (closed) {
            return;
        }
        closed = true;
        hiding = false;
        HideAndSeekVisibility.clear(hider.getUUID(), seeker.getUUID());
        RuntimeException failure = null;
        for (Suppression suppression : appliedSuppressions) {
            try {
                removeSuppression(suppression.attribute(), suppression.id());
            } catch (RuntimeException exception) {
                failure = retainFailure(failure, exception);
            }
        }
        appliedSuppressions.clear();
        try {
            restoreBlindness(disconnectedId);
        } catch (RuntimeException exception) {
            failure = retainFailure(failure, exception);
        }

        if (started) {
            // A departing seeker may still be in the manager until vanilla finishes LEAVE.
            // Attribute callbacks will restore the hider on the following server tick instead.
            if (connected(hider, disconnectedId) && connected(seeker, disconnectedId)
                    && hider.isTransmittingWaypoint()) {
                try {
                    hider.level().getWaypointManager().trackWaypoint(hider);
                } catch (RuntimeException exception) {
                    failure = retainFailure(failure, exception);
                }
            }
            if (connected(seeker, disconnectedId) && seeker.isReceivingWaypoints()) {
                try {
                    seeker.level().getWaypointManager().addPlayer(seeker);
                } catch (RuntimeException exception) {
                    failure = retainFailure(failure, exception);
                }
            }
            if (connected(hider, disconnectedId) && connected(seeker, disconnectedId)
                    && seeker.isAlive()) {
                try {
                    refreshSeekerTracking();
                } catch (RuntimeException exception) {
                    failure = retainFailure(failure, exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    static void addSuppression(AttributeInstance attribute, Identifier id) {
        if (attribute.hasModifier(id)) {
            throw new IllegalStateException("Hide-and-seek attribute modifier already exists: " + id);
        }
        attribute.addTransientModifier(new AttributeModifier(id, -1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    static void removeSuppression(AttributeInstance attribute, Identifier id) {
        attribute.removeModifier(id);
    }

    private static Suppression suppression(ServerPlayer player, Holder<Attribute> attribute,
            Identifier id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            throw new IllegalStateException("Required hide-and-seek attribute is missing: " + attribute);
        }
        if (instance.hasModifier(id)) {
            throw new IllegalStateException("Hide-and-seek attribute modifier already exists: " + id);
        }
        return new Suppression(instance, id);
    }

    private void applyWaitingBlindness() {
        MobEffectInstance current = seeker.getEffect(MobEffects.BLINDNESS);
        if (current == null) {
            ownBlindness = newBlindness();
            seeker.addEffect(ownBlindness);
        } else if (isOwnBlindness(current)) {
            seeker.addEffect(newBlindness());
        } else {
            // A stronger or independently supplied effect already keeps the seeker blind.
            ownBlindness = null;
        }
    }

    private static MobEffectInstance newBlindness() {
        return new MobEffectInstance(MobEffects.BLINDNESS, BLINDNESS_TICKS, 0,
                false, false, false);
    }

    private boolean isOwnBlindness(MobEffectInstance effect) {
        return effect != null && effect == ownBlindness && effect.getAmplifier() == 0
                && !effect.isAmbient() && !effect.isVisible() && !effect.showIcon()
                && !effect.isInfiniteDuration() && effect.getDuration() <= BLINDNESS_TICKS;
    }

    private void advancePreviousBlindness() {
        int tick = server.getTickCount();
        int elapsed = Math.max(0, tick - previousBlindnessTick);
        previousBlindnessTick = tick;
        if (previousBlindness != null) {
            // The detached original retains vanilla's hidden effect chain. This only advances
            // its timers; it does not apply effect logic or callbacks to the player.
            for (int i = 0; i < elapsed; i++) {
                if (!previousBlindness.isInfiniteDuration() && previousBlindness.getDuration() <= 0) {
                    previousBlindness = null;
                    break;
                }
                previousBlindness.tickClient();
            }
        }
    }

    private void restoreBlindness(UUID disconnectedId) {
        advancePreviousBlindness();
        boolean sendPackets = connected(seeker, disconnectedId) && seeker.isAlive();
        RuntimeException failure = null;
        if (isOwnBlindness(seeker.getEffect(MobEffects.BLINDNESS))) {
            try {
                if (sendPackets) {
                    seeker.removeEffect(MobEffects.BLINDNESS);
                } else {
                    seeker.removeEffectNoUpdate(MobEffects.BLINDNESS);
                }
            } catch (RuntimeException exception) {
                failure = exception;
                sendPackets = false;
            }
        }
        ownBlindness = null;
        if (previousBlindness != null
                && (previousBlindness.isInfiniteDuration() || previousBlindness.getDuration() > 0)) {
            if (sendPackets) {
                try {
                    seeker.addEffect(previousBlindness);
                } catch (RuntimeException exception) {
                    failure = retainFailure(failure, exception);
                    restoreBlindnessWithoutPackets();
                }
            } else {
                restoreBlindnessWithoutPackets();
            }
        }
        previousBlindness = null;
        if (failure != null) {
            throw failure;
        }
    }

    private void restoreBlindnessWithoutPackets() {
        // Blindness has no attribute modifiers. Preserve its save data without invoking
        // ServerPlayer's effect callbacks, which send packets to the closed connection.
        MobEffectInstance current = seeker.getEffect(MobEffects.BLINDNESS);
        if (current == null) {
            seeker.getActiveEffectsMap().put(MobEffects.BLINDNESS, previousBlindness);
        } else {
            current.update(previousBlindness);
        }
    }

    private boolean connected(ServerPlayer player, UUID disconnectedId) {
        return !player.getUUID().equals(disconnectedId)
                && !player.hasDisconnected()
                && server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private static RuntimeException retainFailure(RuntimeException previous, RuntimeException next) {
        if (previous == null) {
            return next;
        }
        if (previous != next) {
            previous.addSuppressed(next);
        }
        return previous;
    }

    private void refreshSeekerTracking() {
        seeker.level().getChunkSource().move(seeker);
    }

    private static Identifier modifierId(String path) {
        return Identifier.fromNamespaceAndPath("server_minigames", path);
    }

    private record Suppression(AttributeInstance attribute, Identifier id) {
    }
}
