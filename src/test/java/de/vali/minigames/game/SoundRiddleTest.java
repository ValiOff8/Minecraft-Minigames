package de.vali.minigames.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundRiddleTest {
    private final SoundRiddle riddle = new SoundRiddle("minecraft:entity.cat.ambient", "Katze",
            List.of("Erster Tipp", "Zweiter Tipp"), Set.of("cat", "süße Katze", "Straße"));

    @Test
    void matchesGermanAndEnglishAnswersIgnoringCaseAndSurroundingPunctuation() {
        assertTrue(riddle.matching("  KATZE!  "));
        assertTrue(riddle.matching("Cat"));
        assertTrue(riddle.matching("SUESSE_KATZE"));
        assertTrue(riddle.matching("su\u0308ße.Katze"));
        assertTrue(riddle.matching("STRASSE"));
    }

    @Test
    void acceptsCanonicalAndBareSoundIdentifiersWithEquivalentSeparators() {
        assertTrue(riddle.matching("minecraft:entity.cat.ambient"));
        assertTrue(riddle.matching("entity.cat.ambient"));
        assertTrue(riddle.matching("ENTITY_CAT_AMBIENT"));
        assertTrue(riddle.matching("minecraft entity cat ambient"));
    }

    @Test
    void requiresAnEntireAnswerAndRejectsEmptyOrNullGuesses() {
        assertFalse(riddle.matching("Das ist eine Katze"));
        assertFalse(riddle.matching("catapult"));
        assertFalse(riddle.matching("Katze oder Wolf"));
        assertFalse(riddle.matching("foreign:entity.cat.ambient"));
        assertFalse(riddle.matching(""));
        assertFalse(riddle.matching("!!!"));
        assertFalse(riddle.matching(null));
    }

    @Test
    void defensivelyCopiesHintsAndAliases() {
        List<String> hints = new ArrayList<>(List.of("Tipp"));
        Set<String> aliases = new HashSet<>(Set.of("cat"));
        SoundRiddle copied = new SoundRiddle("minecraft:entity.cat.ambient", "Katze", hints, aliases);
        hints.add("Weiterer Tipp");
        aliases.add("dog");

        assertEquals(List.of("Tipp"), copied.hints());
        assertFalse(copied.matching("dog"));
        assertThrows(UnsupportedOperationException.class, () -> copied.hints().add("Tipp"));
        assertThrows(UnsupportedOperationException.class, () -> copied.aliases().add("Hund"));
    }

    @Test
    void requiresAnAnswerAndAtLeastOneUsableHint() {
        assertThrows(IllegalArgumentException.class,
                () -> new SoundRiddle("minecraft:entity.cat.ambient", "Katze", List.of(), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new SoundRiddle("minecraft:entity.cat.ambient", " ", List.of("Tipp"), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new SoundRiddle("minecraft:entity.cat.ambient", "Katze", List.of(" "), Set.of()));
    }

    @Test
    void defaultCatalogHasUniqueVanillaSoundIdsAndUsableAnswers() {
        Set<String> soundIds = new HashSet<>();
        assertTrue(SoundCatalog.DEFAULT_RIDDLES.size() >= 15);
        for (SoundRiddle sound : SoundCatalog.ALL_RIDDLES) {
            assertTrue(sound.soundId().startsWith("minecraft:"));
            assertTrue(soundIds.add(sound.soundId()), sound.soundId());
            assertTrue(sound.matching(sound.answer()));
            assertTrue(sound.matching(sound.soundId()));
            assertFalse(sound.hints().isEmpty());
        }
    }
}
