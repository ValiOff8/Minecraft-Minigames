package de.vali.minigames.hideandseek;

import java.util.Objects;
import java.util.UUID;

/** The seeker must never receive the hider's model during the hiding countdown. */
public final class HideAndSeekVisibility {
    private static volatile HiddenPair hiddenPair;

    private HideAndSeekVisibility() {
    }

    public static void begin(UUID hider, UUID seeker) {
        hiddenPair = new HiddenPair(Objects.requireNonNull(hider), Objects.requireNonNull(seeker));
    }

    public static void clear() {
        hiddenPair = null;
    }

    static void clear(UUID hider, UUID seeker) {
        HiddenPair pair = hiddenPair;
        if (pair != null && pair.hider().equals(hider) && pair.seeker().equals(seeker)) {
            hiddenPair = null;
        }
    }

    public static boolean isHiddenFrom(UUID entity, UUID viewer) {
        HiddenPair pair = hiddenPair;
        return pair != null && pair.hider().equals(entity) && pair.seeker().equals(viewer);
    }

    private record HiddenPair(UUID hider, UUID seeker) {
    }
}
