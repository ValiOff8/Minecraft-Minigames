package de.vali.minigames.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SoundCatalogLanguageTest {
    @ParameterizedTest
    @CsvSource(textBlock = """
            entity.cat.ambient, Cat, Katze
            entity.wolf.ambient, Wolf, Wolf
            entity.cow.ambient, Cow, Kuh
            entity.pig.ambient, Pig, Schwein
            entity.sheep.ambient, Sheep, Schaf
            entity.chicken.ambient, Chicken, Huhn
            entity.villager.ambient, Villager, Dorfbewohner
            entity.zombie.ambient, Zombie, Zombie
            entity.skeleton.ambient, Skeleton, Skelett
            entity.creeper.primed, Creeper, Creeper
            entity.enderman.scream, Enderman, Enderman
            entity.ghast.ambient, Ghast, Ghast
            block.anvil.land, Anvil, Amboss
            block.chest.open, Chest, Truhe
            entity.experience_orb.pickup, Experience Orb, Erfahrungskugel
            entity.lightning_bolt.thunder, Thunder, Donner
            block.enchantment_table.use, Enchanting Table, Verzauberungstisch
            block.brewing_stand.brew, Brewing Stand, Braustand
            block.piston.extend, Piston, Kolben
            block.bell.use, Bell, Glocke
            block.portal.travel, Nether Portal, Netherportal
            block.beacon.activate, Beacon, Leuchtfeuer
            item.totem.use, Totem of Undying, Totem der Unsterblichkeit
            entity.ender_eye.launch, Eye of Ender, Enderauge
            block.lava.extinguish, Lava Extinguishing, Lava löschen
            block.honey_block.slide, Honey Block, Honigblock
            entity.witch.ambient, Witch, Hexe
            entity.guardian.ambient, Guardian, Wächter
            block.conduit.attack.target, Conduit, Aquisator
            block.respawn_anchor.charge, Respawn Anchor, Respawnanker
            block.lodestone.place, Lodestone, Leitstein
            block.sculk_sensor.clicking, Sculk Sensor, Sculk-Sensor
            block.sculk_shrieker.shriek, Sculk Shrieker, Sculk-Kreischer
            block.amethyst_block.resonate, Block of Amethyst, Amethystblock
            block.pointed_dripstone.drip_water, Pointed Dripstone, Spitzer Tropfstein
            entity.turtle.egg_crack, Turtle Egg, Schildkrötenei
            block.trial_spawner.spawn_mob, Trial Spawner, Prüfungsspawner
            block.vault.activate, Vault, Tresor
            item.brush.brushing.generic, Brush, Pinsel
            item.bundle.insert, Bundle, Bündel
            item.goat_horn.sound.0, Goat Horn (Ponder), Ziegenhorn Nachdenken
            entity.sniffer.digging, Sniffer, Schnüffler
            entity.armadillo.roll, Armadillo, Gürteltier
            block.decorated_pot.insert, Decorated Pot, Verzierter Krug
            """)
    void englishDisplayAnswersStillAcceptGermanGuesses(String soundId, String english, String german) {
        var riddle = SoundCatalog.ALL_RIDDLES.stream()
                .filter(sound -> sound.soundId().equals("minecraft:" + soundId)).findFirst().orElseThrow();
        assertEquals(english, riddle.answer());
        assertTrue(riddle.matching(english), english);
        assertTrue(riddle.matching(german), german);
        assertTrue(riddle.matching(german.toUpperCase(Locale.ROOT) + "!"), german);
        assertTrue(riddle.matching(german.replace("ä", "ae").replace("ö", "oe")
                .replace("ü", "ue").replace("ß", "ss")), german);
    }
}
