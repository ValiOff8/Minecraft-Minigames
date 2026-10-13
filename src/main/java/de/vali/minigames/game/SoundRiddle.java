package de.vali.minigames.game;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** A vanilla sound, its accepted answers, and hints ordered from vague to specific. */
public record SoundRiddle(String soundId, String answer, List<String> hints, Set<String> aliases) {
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");

    public SoundRiddle {
        soundId = requireText(soundId, "soundId");
        answer = requireText(answer, "answer");
        hints = List.copyOf(Objects.requireNonNull(hints, "hints"));
        aliases = Set.copyOf(Objects.requireNonNull(aliases, "aliases"));
        if (hints.isEmpty()) {
            throw new IllegalArgumentException("A sound riddle needs at least one hint.");
        }
        hints.forEach(hint -> requireText(hint, "hint"));
        aliases.forEach(alias -> requireText(alias, "alias"));
    }

    /** Matches an entire answer; mentioning an answer inside a sentence is insufficient. */
    public boolean matching(String guess) {
        if (guess == null) {
            return false;
        }
        String normalizedGuess = normalize(guess);
        if (normalizedGuess.isEmpty()) {
            return false;
        }
        if (normalizedGuess.equals(normalize(answer))
                || normalizedGuess.equals(normalize(soundId))
                || normalizedGuess.equals(normalize(bareSoundId()))) {
            return true;
        }
        return aliases.stream().anyMatch(alias -> normalizedGuess.equals(normalize(alias)));
    }

    private String bareSoundId() {
        return soundId.startsWith("minecraft:") ? soundId.substring("minecraft:".length()) : soundId;
    }

    private static String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss");
        return SEPARATORS.matcher(normalized).replaceAll(" ").strip();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value;
    }
}
