package de.vali.minigames.sound;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import de.vali.minigames.game.SoundCatalog;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Checks the actual Minecraft registries and Brigadier tree without starting a server. */
class SoundGuessMinecraftIntegrationTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyCatalogSoundExistsInMinecraft() {
        assertAll(SoundCatalog.ALL_RIDDLES.stream().map(riddle -> () -> {
            var id = Identifier.parse(riddle.soundId());
            var registered = BuiltInRegistries.SOUND_EVENT.get(id);
            assertTrue(registered.isPresent(), () -> "Unknown vanilla sound: " + id);
            assertEquals(id, registered.orElseThrow().value().location());
        }));
    }

    @ParameterizedTest
    @CsvSource({"0, false", "1, false", "2, true", "3, true", "4, true"})
    void administrationRequiresAtLeastOperatorLevelTwo(int level, boolean allowed) {
        var dispatcher = dispatcher();
        var source = source(LevelBasedPermissionSet.forLevel(PermissionLevel.byId(level)));
        for (var command : new String[]{"minigame sound start", "minigame sound start schwer 5",
                "minigame sound stop"}) {
            assertEquals(allowed, fullyParsed(dispatcher.parse(command, source)), command);
        }
    }

    @Test
    void consoleCanStartAndStopEvents() {
        var dispatcher = dispatcher();
        var console = source(LevelBasedPermissionSet.OWNER);
        assertTrue(fullyParsed(dispatcher.parse("minigame sound start", console)));
        assertTrue(fullyParsed(dispatcher.parse("minigame sound stop", console)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"beitreten", "join", "verlassen", "leave", "tipp", "hint",
            "nochmal", "replay", "abstimmen", "vote", "status"})
    void participationAndHelpAreAccessibleWithoutOperatorPermission(String subcommand) {
        assertTrue(fullyParsed(dispatcher().parse("minigame sound " + subcommand,
                source(PermissionSet.NO_PERMISSIONS))));
    }

    @ParameterizedTest
    @CsvSource({"1, 1, true", "300, 600, true", "0, 60, false", "301, 60, false",
            "15, 0, false", "15, 601, false", "-1, 60, false", "15, -1, false"})
    void commandParserEnforcesLobbyAndRoundDurationBounds(int lobby, int round, boolean valid) {
        var parsed = dispatcher().parse("minigame sound start " + lobby + " " + round,
                source(LevelBasedPermissionSet.GAMEMASTER));
        assertEquals(valid, fullyParsed(parsed));
    }

    @ParameterizedTest
    @ValueSource(strings = {"leicht", "normal", "schwer", "gemischt", "easy", "hard", "mixed"})
    void difficultiesSupportEveryOptionalSeriesArgument(String difficulty) {
        var dispatcher = dispatcher();
        var operator = source(LevelBasedPermissionSet.GAMEMASTER);
        for (var settings : new String[]{"", " 5", " 5 15", " 5 15 60", " 5 15 60 10"}) {
            var command = "minigame sound start " + difficulty + settings;
            assertTrue(fullyParsed(dispatcher.parse(command, operator)), command);
        }
    }

    @ParameterizedTest
    @CsvSource({"schwer 1 1 1 1, true", "gemischt 100 300 600 60, true",
            "schwer 0, false", "schwer 101, false", "schwer -1, false",
            "normal 5 0, false", "normal 5 301, false",
            "leicht 5 15 0, false", "leicht 5 15 601, false",
            "gemischt 5 15 60 0, false", "gemischt 5 15 60 61, false"})
    void seriesCommandEnforcesEverySettingBound(String settings, boolean valid) {
        assertEquals(valid, fullyParsed(dispatcher().parse("minigame sound start " + settings,
                source(LevelBasedPermissionSet.GAMEMASTER))));
    }

    @Test
    void unknownDifficultyIsRejected() {
        assertFalse(fullyParsed(dispatcher().parse("minigame sound start expert 5",
                source(LevelBasedPermissionSet.GAMEMASTER))));
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(Commands.literal("minigame").then(new SoundGuessMinigame().commands()));
        return dispatcher;
    }

    private static CommandSourceStack source(PermissionSet permissions) {
        // Parsing only needs permissions; no world, player, server or networking is accessed.
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO,
                null, permissions, Component.literal("test"), null);
    }

    private static boolean fullyParsed(ParseResults<CommandSourceStack> parsed) {
        return !parsed.getReader().canRead()
                && parsed.getExceptions().isEmpty()
                && parsed.getContext().getCommand() != null;
    }
}
