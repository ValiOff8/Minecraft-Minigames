package de.vali.minigames.sound;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.vali.minigames.api.ServerMinigame;
import de.vali.minigames.game.SoundCatalog;
import de.vali.minigames.game.SoundDifficulty;
import de.vali.minigames.game.SoundGuessGame;
import de.vali.minigames.game.SoundRiddle;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundSource;

/** Minecraft commands and packets around the independently testable game rules. */
public final class SoundGuessMinigame implements ServerMinigame {
    private static final int DEFAULT_LOBBY_SECONDS = 15;
    private static final int DEFAULT_ROUND_SECONDS = 60;
    private static final int DEFAULT_COUNTDOWN_SECONDS = 10;
    private final SoundGuessGame game = new SoundGuessGame(
            SoundCatalog.ALL_RIDDLES, new java.util.Random());
    private long roundSoundSeed;

    @Override
    public String id() {
        return "sound";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> commands() {
        var startCommand = Commands.literal("start")
                        .requires(source -> source.permissions()
                                .hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(context -> start(context.getSource(),
                                DEFAULT_LOBBY_SECONDS, DEFAULT_ROUND_SECONDS))
                        .then(Commands.argument("signup_seconds", IntegerArgumentType.integer(1, 300))
                                .executes(context -> start(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "signup_seconds"),
                                        DEFAULT_ROUND_SECONDS))
                                .then(Commands.argument("guess_seconds", IntegerArgumentType.integer(1, 600))
                                        .executes(context -> start(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "signup_seconds"),
                                                IntegerArgumentType.getInteger(context, "guess_seconds")))));
        for (var difficulty : SoundDifficulty.values()) {
            startCommand.then(configuredStartCommand(difficulty, difficulty.commandName()));
            if (!difficulty.germanName().equals(difficulty.commandName())) {
                startCommand.then(configuredStartCommand(difficulty, difficulty.germanName()));
            }
        }
        return Commands.literal(id())
                .executes(context -> help(context.getSource()))
                .then(startCommand)
                .then(Commands.literal("stop")
                        .requires(source -> source.permissions()
                                .hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(context -> stop(context.getSource())))
                .then(Commands.literal("beitreten").executes(context -> join(context.getSource())))
                .then(Commands.literal("join").executes(context -> join(context.getSource())))
                .then(Commands.literal("verlassen").executes(context -> leave(context.getSource())))
                .then(Commands.literal("leave").executes(context -> leave(context.getSource())))
                .then(Commands.literal("tipp").executes(context -> hint(context.getSource())))
                .then(Commands.literal("hint").executes(context -> hint(context.getSource())))
                .then(Commands.literal("nochmal").executes(context -> replay(context.getSource())))
                .then(Commands.literal("replay").executes(context -> replay(context.getSource())))
                .then(Commands.literal("abstimmen").executes(context -> voteReplay(context.getSource())))
                .then(Commands.literal("vote").executes(context -> voteReplay(context.getSource())))
                .then(Commands.literal("status").executes(context -> status(context.getSource())));
    }

    private LiteralArgumentBuilder<CommandSourceStack> configuredStartCommand(
            SoundDifficulty difficulty, String name) {
        var countdown = Commands.argument("countdown_seconds", IntegerArgumentType.integer(1, 60))
                .executes(context -> start(context.getSource(),
                        IntegerArgumentType.getInteger(context, "signup_seconds"),
                        IntegerArgumentType.getInteger(context, "guess_seconds"), difficulty,
                        IntegerArgumentType.getInteger(context, "rounds"),
                        IntegerArgumentType.getInteger(context, "countdown_seconds")));
        var roundTime = Commands.argument("guess_seconds", IntegerArgumentType.integer(1, 600))
                .executes(context -> start(context.getSource(),
                        IntegerArgumentType.getInteger(context, "signup_seconds"),
                        IntegerArgumentType.getInteger(context, "guess_seconds"), difficulty,
                        IntegerArgumentType.getInteger(context, "rounds"), DEFAULT_COUNTDOWN_SECONDS))
                .then(countdown);
        var lobbyTime = Commands.argument("signup_seconds", IntegerArgumentType.integer(1, 300))
                .executes(context -> start(context.getSource(),
                        IntegerArgumentType.getInteger(context, "signup_seconds"),
                        DEFAULT_ROUND_SECONDS, difficulty,
                        IntegerArgumentType.getInteger(context, "rounds"), DEFAULT_COUNTDOWN_SECONDS))
                .then(roundTime);
        var rounds = Commands.argument("rounds", IntegerArgumentType.integer(1, 100))
                .executes(context -> start(context.getSource(), DEFAULT_LOBBY_SECONDS,
                        DEFAULT_ROUND_SECONDS, difficulty,
                        IntegerArgumentType.getInteger(context, "rounds"), DEFAULT_COUNTDOWN_SECONDS))
                .then(lobbyTime);
        return Commands.literal(name)
                .executes(context -> start(context.getSource(), DEFAULT_LOBBY_SECONDS,
                        DEFAULT_ROUND_SECONDS, difficulty, 1, DEFAULT_COUNTDOWN_SECONDS))
                .then(rounds);
    }

    private int help(CommandSourceStack source) {
        source.sendSuccess(() -> text("Guess the Minecraft sound! "
                + "Commands: /minigame sound join | leave | hint | replay | vote | status. "
                + "Operator: /minigame sound start <easy|normal|hard|mixed> "
                + "[rounds] [signup_seconds] [guess_seconds] [countdown_seconds] or stop. "
                + "Example: /minigame sound start hard 5"), false);
        return 1;
    }

    private int start(CommandSourceStack source, int lobbySeconds, int roundSeconds) {
        return start(source, lobbySeconds, roundSeconds, SoundDifficulty.EASY, 1, DEFAULT_COUNTDOWN_SECONDS);
    }

    private int start(CommandSourceStack source, int lobbySeconds, int roundSeconds,
            SoundDifficulty difficulty, int rounds, int countdownSeconds) {
        if (!game.openLobby(lobbySeconds, roundSeconds, difficulty, rounds, countdownSeconds)) {
            return error(source, "A Sound Guess game is already running. Use /minigame sound status.");
        }
        broadcast(source.getServer(), "A Sound Guess game starts in " + lobbySeconds
                + " seconds! Join with /minigame sound join. "
                + "Difficulty: " + difficulty.commandName() + ", " + rounds + " rounds, "
                + roundSeconds + " seconds per sound, " + countdownSeconds + " seconds between rounds.");
        return 1;
    }

    private int join(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        return switch (game.join(player.getUUID())) {
            case JOINED -> {
                player.sendSystemMessage(text("You have joined! The sound will play in "
                        + game.remainingSeconds() + " seconds. Write your answer in public chat."));
                yield 1;
            }
            case ALREADY_JOINED -> error(source, "You have already joined.");
            case NO_LOBBY -> error(source, "Signup is not open right now. "
                    + "An operator can start a game with /minigame sound start.");
        };
    }

    private int leave(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var previousRiddle = game.currentRiddle();
        boolean wasRunning = game.phase() == SoundGuessGame.Phase.RUNNING;
        boolean wasBetweenRounds = game.phase() == SoundGuessGame.Phase.BETWEEN_ROUNDS;
        if (!game.leave(player.getUUID())) {
            return error(source, "You are not taking part in a Sound Guess game.");
        }
        player.sendSystemMessage(text("You have left the Sound Guess game."));
        if ((wasRunning || wasBetweenRounds) && game.phase() == SoundGuessGame.Phase.IDLE) {
            broadcast(source.getServer(), "Sound Guess ended: All participants have left."
                    + previousRiddle.map(riddle -> " The answer was: " + riddle.answer() + ".").orElse(""));
        } else if (wasRunning) {
            updateReplayVoteAfterDeparture(source.getServer());
        }
        return 1;
    }

    private int hint(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var hint = game.nextHint(player.getUUID());
        if (hint.isEmpty()) {
            return error(source, "Hints are available to participants once the sound has played.");
        }
        player.sendSystemMessage(text("Your hint: " + hint.get()));
        return 1;
    }

    private int replay(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (game.phase() != SoundGuessGame.Phase.RUNNING
                || !game.participants().contains(player.getUUID())) {
            return error(source, "You can only replay the sound while taking part in an active round.");
        }
        if (!game.requestReplay(player.getUUID())) {
            return error(source, "You can replay the sound once every 5 seconds.");
        }
        playSound(player, game.currentRiddle().orElseThrow());
        player.sendSystemMessage(text("The sound is playing again for you."));
        return 1;
    }

    private int voteReplay(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        return switch (game.voteReplay(player.getUUID())) {
            case NOT_RUNNING -> error(source, "You can only vote during an active round.");
            case NOT_PARTICIPANT -> error(source, "Only participants can vote to replay the sound.");
            case COOLDOWN -> error(source, "The next vote is available in "
                    + game.sharedReplayCooldownSeconds() + " seconds.");
            case ALREADY_VOTED -> error(source, "You have already voted for this replay.");
            case VOTED -> {
                sendToParticipants(source.getServer(), player.getName().getString()
                        + " wants to replay the sound for everyone: " + game.replayVoteCount()
                        + "/" + game.requiredReplayVotes()
                        + " votes. Vote with /minigame sound vote.");
                yield 1;
            }
            case APPROVED -> {
                replayForAll(source.getServer());
                yield 1;
            }
        };
    }

    private void replayForAll(MinecraftServer server) {
        var riddle = game.currentRiddle().orElseThrow();
        sendToParticipants(server, "Majority reached! The sound is playing again for all participants.");
        for (var uuid : game.participants()) {
            var player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                playSound(player, riddle);
            }
        }
    }

    private void updateReplayVoteAfterDeparture(MinecraftServer server) {
        if (game.consumeApprovedReplayVote()) {
            replayForAll(server);
        } else if (game.replayVoteCount() > 0) {
            sendToParticipants(server, "Vote updated after a player left: "
                    + game.replayVoteCount() + "/" + game.requiredReplayVotes()
                    + " votes. Vote with /minigame sound vote");
        }
    }

    private int status(CommandSourceStack source) {
        String description = switch (game.phase()) {
            case IDLE -> "No Sound Guess game is running.";
            case LOBBY -> "Signup open: " + game.remainingSeconds() + " seconds, "
                    + game.participants().size() + " participants, " + game.totalRounds() + " rounds ("
                    + game.difficulty().commandName() + "). /minigame sound join";
            case BETWEEN_ROUNDS -> "Next sound in " + game.remainingSeconds() + " seconds. Round "
                    + (game.roundNumber() + 1) + "/" + game.totalRounds() + " ("
                    + game.difficulty().commandName() + "), " + game.participants().size() + " participants.";
            case RUNNING -> "Round " + game.roundNumber() + "/" + game.totalRounds() + " ("
                    + game.difficulty().commandName() + "): " + game.remainingSeconds() + " seconds left, "
                    + game.participants().size() + " participants. Replay vote: "
                    + game.replayVoteCount() + "/" + game.requiredReplayVotes() + " votes.";
        };
        source.sendSuccess(() -> text(description), false);
        return 1;
    }

    private int stop(CommandSourceStack source) {
        var riddle = game.currentRiddle();
        if (!game.stop()) {
            return error(source, "No Sound Guess game is running.");
        }
        roundSoundSeed = 0;
        broadcast(source.getServer(), "The Sound Guess game was canceled."
                + riddle.map(value -> " The answer was: " + value.answer() + ".").orElse(""));
        return 1;
    }

    @Override
    public void tick(MinecraftServer server) {
        int previousSeconds = game.remainingSeconds();
        var previousRiddle = game.currentRiddle();
        int completedRound = game.roundNumber();
        int totalRounds = game.totalRounds();
        switch (game.tick()) {
            case STARTED -> {
                roundSoundSeed = ThreadLocalRandom.current().nextLong();
                var riddle = game.currentRiddle().orElseThrow();
                broadcast(server, "Round " + game.roundNumber() + "/" + game.totalRounds() + " ("
                        + game.difficulty().commandName() + ") starts with " + game.participants().size()
                        + " participants! The first correct answer in chat wins.");
                for (var uuid : game.participants()) {
                    var player = server.getPlayerList().getPlayer(uuid);
                    if (player != null) {
                        player.sendSystemMessage(text("Listen carefully! You have "
                                + game.remainingSeconds() + " seconds. "
                                + "Hint: /minigame sound hint | Private replay: /minigame sound replay"
                                + " | Vote to replay for everyone: /minigame sound vote"));
                        playSound(player, riddle);
                    }
                }
            }
            case NO_PARTICIPANTS -> broadcast(server,
                    "Sound Guess canceled: Nobody joined.");
            case TIMED_OUT -> {
                broadcast(server, "Round " + completedRound + "/" + totalRounds
                        + ": Time is up! Nobody won. The answer was: "
                        + previousRiddle.orElseThrow().answer() + ".");
                announceRoundTransition(server, totalRounds);
            }
            case NONE -> {
                int seconds = game.remainingSeconds();
                if (seconds == previousSeconds || seconds <= 0) {
                    return;
                }
                if ((game.phase() == SoundGuessGame.Phase.LOBBY
                        || game.phase() == SoundGuessGame.Phase.BETWEEN_ROUNDS) && seconds <= 5) {
                    sendToParticipants(server, "The sound will play in " + seconds + " seconds ...");
                } else if (game.phase() == SoundGuessGame.Phase.RUNNING
                        && (seconds == 30 || seconds == 10 || seconds <= 5)) {
                    sendToParticipants(server, "Only " + seconds + " seconds left!");
                }
            }
        }
    }

    @Override
    public void onChat(MinecraftServer server, ServerPlayer player, String message) {
        var riddle = game.currentRiddle();
        int roundNumber = game.roundNumber();
        int totalRounds = game.totalRounds();
        if (game.guess(player.getUUID(), message)) {
            broadcast(server, player.getName().getString() + " wins round " + roundNumber + "/"
                    + totalRounds + "! The answer was: "
                    + riddle.orElseThrow().answer() + ".");
            announceRoundTransition(server, totalRounds);
        }
    }

    private void announceRoundTransition(MinecraftServer server, int totalRounds) {
        roundSoundSeed = 0;
        if (game.phase() == SoundGuessGame.Phase.BETWEEN_ROUNDS) {
            broadcast(server, "Round " + (game.roundNumber() + 1) + "/" + totalRounds + " starts in "
                    + game.remainingSeconds() + " seconds. All participants stay in the game.");
        } else {
            broadcast(server, "The Sound Guess game has ended (" + totalRounds
                    + (totalRounds == 1 ? " round)." : " rounds)."));
        }
    }

    @Override
    public void onDisconnect(MinecraftServer server, ServerPlayer player) {
        var riddle = game.currentRiddle();
        boolean wasRunning = game.phase() == SoundGuessGame.Phase.RUNNING;
        boolean wasBetweenRounds = game.phase() == SoundGuessGame.Phase.BETWEEN_ROUNDS;
        if (!game.leave(player.getUUID())) {
            return;
        }
        if ((wasRunning || wasBetweenRounds) && game.phase() == SoundGuessGame.Phase.IDLE) {
            broadcast(server, "Sound Guess ended: No participants are online."
                    + riddle.map(value -> " The answer was: " + value.answer() + ".").orElse(""));
        } else if (wasRunning) {
            updateReplayVoteAfterDeparture(server);
        }
    }

    @Override
    public void reset() {
        game.stop();
        roundSoundSeed = 0;
    }

    private void playSound(ServerPlayer player, SoundRiddle riddle) {
        var sound = BuiltInRegistries.SOUND_EVENT.get(Identifier.parse(riddle.soundId())).orElseThrow();
        // Send only to this player, at their position. The seed fixes vanilla sample variations.
        player.connection.send(new ClientboundSoundPacket(sound, SoundSource.MASTER,
                player.getX(), player.getY(), player.getZ(), 1.0F, 1.0F, roundSoundSeed));
    }

    private void sendToParticipants(MinecraftServer server, String message) {
        for (var uuid : game.participants()) {
            var player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.sendSystemMessage(text(message));
            }
        }
    }

    private static void broadcast(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(text(message), false);
    }

    private static int error(CommandSourceStack source, String message) {
        source.sendFailure(text(message));
        return 0;
    }

    private static Component text(String message) {
        return Component.literal("[Sound Guess] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(message).withStyle(ChatFormatting.YELLOW));
    }
}
