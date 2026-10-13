package de.vali.minigames.sound;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.vali.minigames.api.ServerMinigame;
import de.vali.minigames.game.SoundCatalog;
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
    private final SoundGuessGame game = new SoundGuessGame(
            SoundCatalog.DEFAULT_RIDDLES, new java.util.Random());
    private long roundSoundSeed;

    @Override
    public String id() {
        return "sound";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal(id())
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("start")
                        .requires(source -> source.permissions()
                                .hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(context -> start(context.getSource(),
                                DEFAULT_LOBBY_SECONDS, DEFAULT_ROUND_SECONDS))
                        .then(Commands.argument("anmeldezeit", IntegerArgumentType.integer(1, 300))
                                .executes(context -> start(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "anmeldezeit"),
                                        DEFAULT_ROUND_SECONDS))
                                .then(Commands.argument("rundenzeit", IntegerArgumentType.integer(1, 600))
                                        .executes(context -> start(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "anmeldezeit"),
                                                IntegerArgumentType.getInteger(context, "rundenzeit"))))))
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

    private int help(CommandSourceStack source) {
        source.sendSuccess(() -> text("Errate den Minecraft-Sound! "
                + "Befehle: /minigame sound beitreten | verlassen | tipp | nochmal | abstimmen | status. "
                + "Für Operatoren: /minigame sound start [Anmeldezeit] [Rundenzeit] und stop."), false);
        return 1;
    }

    private int start(CommandSourceStack source, int lobbySeconds, int roundSeconds) {
        if (!game.openLobby(lobbySeconds, roundSeconds)) {
            return error(source, "Es läuft bereits ein Sound-Event. Nutze /minigame sound status.");
        }
        broadcast(source.getServer(), "Ein Sound-Rätsel startet in " + lobbySeconds
                + " Sekunden! Beitreten: /minigame sound beitreten. "
                + "Danach habt ihr " + roundSeconds + " Sekunden zum Raten.");
        return 1;
    }

    private int join(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        return switch (game.join(player.getUUID())) {
            case JOINED -> {
                player.sendSystemMessage(text("Du machst mit! Der Sound ertönt in "
                        + game.remainingSeconds() + " Sekunden. Schreibe die Antwort in den normalen Chat."));
                yield 1;
            }
            case ALREADY_JOINED -> error(source, "Du bist bereits angemeldet.");
            case NO_LOBBY -> error(source, "Gerade ist keine Anmeldung offen. "
                    + "Ein Operator startet das Event mit /minigame sound start.");
        };
    }

    private int leave(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var previousRiddle = game.currentRiddle();
        boolean wasRunning = game.phase() == SoundGuessGame.Phase.RUNNING;
        if (!game.leave(player.getUUID())) {
            return error(source, "Du nimmst an keinem Sound-Event teil.");
        }
        player.sendSystemMessage(text("Du hast das Sound-Event verlassen."));
        if (wasRunning && game.phase() == SoundGuessGame.Phase.IDLE) {
            broadcast(source.getServer(), "Sound-Rätsel beendet: Alle Teilnehmer haben verlassen. "
                    + "Die Antwort war: " + previousRiddle.orElseThrow().answer() + ".");
        } else if (wasRunning) {
            updateReplayVoteAfterDeparture(source.getServer());
        }
        return 1;
    }

    private int hint(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var hint = game.nextHint(player.getUUID());
        if (hint.isEmpty()) {
            return error(source, "Tipps gibt es für Teilnehmer, sobald der Sound abgespielt wurde.");
        }
        player.sendSystemMessage(text("Dein Tipp: " + hint.get()));
        return 1;
    }

    private int replay(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (game.phase() != SoundGuessGame.Phase.RUNNING
                || !game.participants().contains(player.getUUID())) {
            return error(source, "Du kannst den Sound nur als Teilnehmer einer laufenden Runde wiederholen.");
        }
        if (!game.requestReplay(player.getUUID())) {
            return error(source, "Du kannst den Sound höchstens alle 5 Sekunden wiederholen.");
        }
        playSound(player, game.currentRiddle().orElseThrow());
        player.sendSystemMessage(text("Der Sound wird für dich erneut abgespielt."));
        return 1;
    }

    private int voteReplay(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        return switch (game.voteReplay(player.getUUID())) {
            case NOT_RUNNING -> error(source, "Abstimmen kannst du erst während einer laufenden Runde.");
            case NOT_PARTICIPANT -> error(source, "Nur Teilnehmer können über die Wiederholung abstimmen.");
            case COOLDOWN -> error(source, "Die nächste Abstimmung ist in "
                    + game.sharedReplayCooldownSeconds() + " Sekunden möglich.");
            case ALREADY_VOTED -> error(source, "Du hast bereits für diese Wiederholung gestimmt.");
            case VOTED -> {
                sendToParticipants(source.getServer(), player.getName().getString()
                        + " möchte den Sound für alle wiederholen: " + game.replayVoteCount()
                        + "/" + game.requiredReplayVotes()
                        + " Stimmen. Stimme mit /minigame sound abstimmen ab.");
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
        sendToParticipants(server, "Mehrheit erreicht! Der Sound wird für alle Teilnehmer erneut abgespielt.");
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
            sendToParticipants(server, "Abstimmung nach Austritt aktualisiert: "
                    + game.replayVoteCount() + "/" + game.requiredReplayVotes()
                    + " Stimmen. Abstimmen: /minigame sound abstimmen");
        }
    }

    private int status(CommandSourceStack source) {
        String description = switch (game.phase()) {
            case IDLE -> "Gerade läuft kein Sound-Event.";
            case LOBBY -> "Anmeldung offen: " + game.remainingSeconds() + " Sekunden, "
                    + game.participants().size() + " Teilnehmer. /minigame sound beitreten";
            case RUNNING -> "Sound-Rätsel läuft: " + game.remainingSeconds() + " Sekunden übrig, "
                    + game.participants().size() + " Teilnehmer. Abstimmung zur Wiederholung: "
                    + game.replayVoteCount() + "/" + game.requiredReplayVotes() + " Stimmen.";
        };
        source.sendSuccess(() -> text(description), false);
        return 1;
    }

    private int stop(CommandSourceStack source) {
        var riddle = game.currentRiddle();
        if (!game.stop()) {
            return error(source, "Gerade läuft kein Sound-Event.");
        }
        roundSoundSeed = 0;
        broadcast(source.getServer(), "Das Sound-Event wurde abgebrochen."
                + riddle.map(value -> " Die Antwort war: " + value.answer() + ".").orElse(""));
        return 1;
    }

    @Override
    public void tick(MinecraftServer server) {
        int previousSeconds = game.remainingSeconds();
        var previousRiddle = game.currentRiddle();
        switch (game.tick()) {
            case STARTED -> {
                roundSoundSeed = ThreadLocalRandom.current().nextLong();
                var riddle = game.currentRiddle().orElseThrow();
                broadcast(server, "Das Sound-Rätsel beginnt mit " + game.participants().size()
                        + " Teilnehmern! Die erste richtige Antwort im Chat gewinnt.");
                for (var uuid : game.participants()) {
                    var player = server.getPlayerList().getPlayer(uuid);
                    if (player != null) {
                        player.sendSystemMessage(text("Hör genau hin! Du hast "
                                + game.remainingSeconds() + " Sekunden. "
                                + "Hilfe: /minigame sound tipp | Persönlich wiederholen: /minigame sound nochmal"
                                + " | Für alle abstimmen: /minigame sound abstimmen"));
                        playSound(player, riddle);
                    }
                }
            }
            case NO_PARTICIPANTS -> broadcast(server,
                    "Das Sound-Event wurde abgesagt: Niemand hat sich angemeldet.");
            case TIMED_OUT -> broadcast(server, "Die Zeit ist um! Niemand hat gewonnen. "
                    + "Die Antwort war: " + previousRiddle.orElseThrow().answer() + ".");
            case NONE -> {
                int seconds = game.remainingSeconds();
                if (seconds == previousSeconds || seconds <= 0) {
                    return;
                }
                if (game.phase() == SoundGuessGame.Phase.LOBBY && seconds <= 5) {
                    sendToParticipants(server, "Der Sound ertönt in " + seconds + " Sekunden …");
                } else if (game.phase() == SoundGuessGame.Phase.RUNNING
                        && (seconds == 30 || seconds == 10 || seconds <= 5)) {
                    sendToParticipants(server, "Noch " + seconds + " Sekunden!");
                }
            }
        }
    }

    @Override
    public void onChat(MinecraftServer server, ServerPlayer player, String message) {
        var riddle = game.currentRiddle();
        if (game.guess(player.getUUID(), message)) {
            broadcast(server, player.getName().getString() + " hat gewonnen! Die Antwort war: "
                    + riddle.orElseThrow().answer() + ".");
        }
    }

    @Override
    public void onDisconnect(MinecraftServer server, ServerPlayer player) {
        var riddle = game.currentRiddle();
        boolean wasRunning = game.phase() == SoundGuessGame.Phase.RUNNING;
        if (!game.leave(player.getUUID())) {
            return;
        }
        if (wasRunning && game.phase() == SoundGuessGame.Phase.IDLE) {
            broadcast(server, "Sound-Rätsel beendet: Kein Teilnehmer mehr online. Die Antwort war: "
                    + riddle.orElseThrow().answer() + ".");
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
        return Component.literal("[Sound-Rätsel] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(message).withStyle(ChatFormatting.YELLOW));
    }
}
