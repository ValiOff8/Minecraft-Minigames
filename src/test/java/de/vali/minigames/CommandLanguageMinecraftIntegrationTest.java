package de.vali.minigames;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import de.vali.minigames.api.ServerMinigame;
import de.vali.minigames.game.SoundDifficulty;
import de.vali.minigames.hideandseek.HideAndSeekMinigame;
import de.vali.minigames.itemrace.ItemRaceMinigame;
import de.vali.minigames.sound.SoundGuessMinigame;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CommandLanguageMinecraftIntegrationTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    static Stream<Object[]> games() {
        return Stream.of(
                new Object[]{new SoundGuessMinigame(), "[Sound Guess] ", "Guess the Minecraft sound!",
                        "No Sound Guess game is running."},
                new Object[]{new HideAndSeekMinigame(), "[Hide and Seek] ", "Hide and Seek for two players.",
                        "No Hide and Seek round is running."},
                new Object[]{new ItemRaceMinigame(), "[Item Race] ", "Be the first to collect every item",
                        "No Item Race is running."});
    }

    @ParameterizedTest
    @MethodSource("games")
    void helpStatusAndErrorsAreEnglish(ServerMinigame game, String prefix, String help, String idle)
            throws Exception {
        var output = new CapturedOutput();
        var source = new CommandSourceStack(output, Vec3.ZERO, Vec2.ZERO, null,
                LevelBasedPermissionSet.GAMEMASTER, Component.literal("test"), null);
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(Commands.literal("minigame").then(game.commands()));
        var command = "minigame " + game.id();

        assertEquals(1, dispatcher.execute(command, source));
        assertTrue(output.messages.getFirst().startsWith(prefix + help));
        assertTrue(output.messages.getFirst().contains("Operator:"));
        assertEquals(1, dispatcher.execute(command + " status", source));
        assertEquals(prefix + idle, output.messages.getLast());
        assertEquals(0, dispatcher.execute(command + " stop", source));
        assertEquals(prefix + idle, output.messages.getLast());
    }

    @Test
    void soundDifficultyDisplayNamesAreEnglish() {
        assertEquals(List.of("easy", "normal", "hard", "mixed"),
                Arrays.stream(SoundDifficulty.values()).map(SoundDifficulty::commandName).toList());
        assertEquals(List.of("leicht", "normal", "schwer", "gemischt"),
                Arrays.stream(SoundDifficulty.values()).map(SoundDifficulty::germanName).toList());
    }

    private static final class CapturedOutput implements CommandSource {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
