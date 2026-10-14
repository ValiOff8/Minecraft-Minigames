package de.vali.minigames.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Server-independent state for two players collecting the same list of items.
 * All methods must be called on the Minecraft server thread.
 */
public final class ItemRaceGame {
    public static final int MAX_TARGETS = 30;

    private List<String> targets = List.of();
    private final Map<UUID, Set<String>> collectedByPlayer = new LinkedHashMap<>();

    public boolean start(UUID first, UUID second, List<String> targets) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(targets, "targets");
        if (first.equals(second)) {
            throw new IllegalArgumentException("An item race needs two different players.");
        }
        if (targets.isEmpty() || targets.size() > MAX_TARGETS) {
            throw new IllegalArgumentException("The target count must be between 1 and " + MAX_TARGETS + ".");
        }
        Set<String> uniqueTargets = new LinkedHashSet<>();
        for (String target : targets) {
            Objects.requireNonNull(target, "target");
            if (target.isBlank() || !uniqueTargets.add(target)) {
                throw new IllegalArgumentException("Target item IDs must be nonblank and unique.");
            }
        }
        if (running()) {
            return false;
        }
        this.targets = List.copyOf(targets);
        collectedByPlayer.put(first, new LinkedHashSet<>());
        collectedByPlayer.put(second, new LinkedHashSet<>());
        return true;
    }

    public boolean running() {
        return !collectedByPlayer.isEmpty();
    }

    public Set<UUID> participants() {
        return immutableSet(collectedByPlayer.keySet());
    }

    public List<String> targets() {
        return targets;
    }

    public boolean isParticipant(UUID playerId) {
        return collectedByPlayer.containsKey(playerId);
    }

    public Set<String> collected(UUID playerId) {
        Set<String> collected = collectedByPlayer.get(playerId);
        if (collected == null) {
            return Set.of();
        }
        Set<String> ordered = new LinkedHashSet<>();
        for (String target : targets) {
            if (collected.contains(target)) {
                ordered.add(target);
            }
        }
        return immutableSet(ordered);
    }

    /** Records discoveries permanently, even when the player later uses or drops an item. */
    public List<String> collect(UUID playerId, Collection<String> itemIds) {
        Objects.requireNonNull(itemIds, "itemIds");
        Set<String> collected = collectedByPlayer.get(playerId);
        if (collected == null) {
            return List.of();
        }
        List<String> newlyCollected = new ArrayList<>();
        for (String target : targets) {
            if (itemIds.contains(target) && collected.add(target)) {
                newlyCollected.add(target);
            }
        }
        return List.copyOf(newlyCollected);
    }

    /**
     * The adapter records both inventories before reading this set, so completing
     * the list in the same server tick can be scored as a draw.
     */
    public Set<UUID> finishers() {
        Set<UUID> finishers = new LinkedHashSet<>();
        collectedByPlayer.forEach((playerId, collected) -> {
            if (collected.size() == targets.size()) {
                finishers.add(playerId);
            }
        });
        return immutableSet(finishers);
    }

    public boolean stop() {
        if (!running()) {
            return false;
        }
        collectedByPlayer.clear();
        targets = List.of();
        return true;
    }

    private static <T> Set<T> immutableSet(Collection<T> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
