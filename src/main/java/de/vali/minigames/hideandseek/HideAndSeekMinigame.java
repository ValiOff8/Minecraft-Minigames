package de.vali.minigames.hideandseek;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.vali.minigames.api.ServerMinigame;
import de.vali.minigames.game.HideAndSeekGame;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Two-player hide and seek. A close-range touch finds the hider without dealing damage. */
public final class HideAndSeekMinigame implements ServerMinigame {
    private static final Logger LOGGER = LoggerFactory.getLogger(HideAndSeekMinigame.class);
    private static final int DEFAULT_HIDING_SECONDS = 30;
    private static final int DEFAULT_SEEKING_SECONDS = 180;
    private final HideAndSeekGame game = new HideAndSeekGame();
    private ServerPlayer seeker;
    private ServerPlayer hider;
    private ResourceKey<Level> dimension;
    private HideAndSeekProtection protection;

    @Override
    public String id() {
        return "hideandseek";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal(id())
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("start")
                        .requires(source -> source.permissions()
                                .hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.argument("sucher", EntityArgument.player())
                                .then(Commands.argument("verstecker", EntityArgument.player())
                                        .executes(context -> start(context,
                                                DEFAULT_HIDING_SECONDS, DEFAULT_SEEKING_SECONDS))
                                        .then(Commands.argument("versteckzeit", IntegerArgumentType.integer(1, 300))
                                                .executes(context -> start(context,
                                                        IntegerArgumentType.getInteger(context, "versteckzeit"),
                                                        DEFAULT_SEEKING_SECONDS))
                                                .then(Commands.argument("suchzeit", IntegerArgumentType.integer(1, 1800))
                                                        .executes(context -> start(context,
                                                                IntegerArgumentType.getInteger(context, "versteckzeit"),
                                                                IntegerArgumentType.getInteger(context, "suchzeit"))))))))
                .then(Commands.literal("bereit").executes(context -> ready(context.getSource())))
                .then(Commands.literal("ready").executes(context -> ready(context.getSource())))
                .then(Commands.literal("verlassen").executes(context -> leave(context.getSource())))
                .then(Commands.literal("leave").executes(context -> leave(context.getSource())))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("stop")
                        .requires(source -> source.permissions()
                                .hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(context -> stop(context.getSource())));
    }

    private int help(CommandSourceStack source) {
        source.sendSuccess(() -> text("Verstecken für zwei Spieler. "
                + "Operator: /minigame hideandseek start <Sucher> <Verstecker> [Versteckzeit] [Suchzeit]. "
                + "Verstecker: /minigame hideandseek bereit. "
                + "Weitere Befehle: status | verlassen | stop (Operator). "
                + "Gefunden wird durch Anklicken aus höchstens drei Blöcken Entfernung."), false);
        return 1;
    }

    private int start(CommandContext<CommandSourceStack> context, int hidingSeconds, int seekingSeconds)
            throws CommandSyntaxException {
        var source = context.getSource();
        if (game.phase() != HideAndSeekGame.Phase.IDLE) {
            return error(source, "Es läuft bereits eine Runde Hide and Seek.");
        }
        var newSeeker = EntityArgument.getPlayer(context, "sucher");
        var newHider = EntityArgument.getPlayer(context, "verstecker");
        if (newSeeker == newHider) {
            return error(source, "Sucher und Verstecker müssen zwei verschiedene Spieler sein.");
        }
        if (!eligible(newSeeker) || !eligible(newHider)) {
            return error(source, "Beide Spieler müssen lebend, wach und ohne Reittier "
                    + "im Überlebens- oder Abenteuermodus sein.");
        }
        if (newSeeker.level() != newHider.level()) {
            return error(source, "Beide Spieler müssen in derselben Dimension sein.");
        }
        game.open(newSeeker.getUUID(), newHider.getUUID(), hidingSeconds, seekingSeconds);
        seeker = newSeeker;
        hider = newHider;
        dimension = newSeeker.level().dimension();
        broadcast(source.getServer(), "Neue Runde! Sucher: " + seeker.getName().getString()
                + ", Verstecker: " + hider.getName().getString() + ".");
        hider.sendSystemMessage(text("Du versteckst dich. Starte innerhalb von 60 Sekunden mit "
                + "/minigame hideandseek bereit. Ab dann hast du " + hidingSeconds
                + " Sekunden zum Verstecken; danach wird " + seekingSeconds + " Sekunden gesucht."));
        seeker.sendSystemMessage(text("Du suchst. Sobald der Verstecker bereit ist, "
                + "bekommst du Blindheit und wartest am Startpunkt, bis die Versteckzeit vorbei ist."));
        return 1;
    }

    private int ready(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (game.phase() != HideAndSeekGame.Phase.WAITING_READY) {
            return error(source, "Gerade wartet keine Runde auf die Bereitschaft des Versteckers.");
        }
        if (!game.hider().orElseThrow().equals(player.getUUID())) {
            return error(source, "Nur der ausgewählte Verstecker kann die Versteckzeit starten.");
        }
        if (!participantsValid(source.getServer())) {
            finish(source.getServer(), "Runde abgebrochen: Die Spieler sind nicht mehr spielbereit.", null);
            return 0;
        }
        try {
            protection = new HideAndSeekProtection(source.getServer(), seeker, hider);
            game.ready(player.getUUID());
            protection.beginHiding();
        } catch (RuntimeException exception) {
            LOGGER.error("Hide-and-Seek-Schutz konnte nicht gestartet werden", exception);
            finish(source.getServer(), "Runde abgebrochen: Der Sichtschutz konnte nicht aktiviert werden.", null);
            return 0;
        }
        seeker.sendSystemMessage(text("Augen zu! Du wartest noch " + game.remainingSeconds()
                + " Sekunden am Startpunkt. Der Verstecker wird dir währenddessen nicht angezeigt."));
        hider.sendSystemMessage(text("Los, versteck dich! Du hast " + game.remainingSeconds()
                + " Sekunden. Dein Nametag und Positionsmarker sind jetzt verborgen."));
        return 1;
    }

    private int leave(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (!game.isParticipant(player.getUUID())) {
            return error(source, "Du nimmst an keiner Runde Hide and Seek teil.");
        }
        finish(source.getServer(), player.getName().getString() + " hat die Runde verlassen. "
                + "Hide and Seek wurde abgebrochen.", null);
        return 1;
    }

    private int status(CommandSourceStack source) {
        String phase = switch (game.phase()) {
            case IDLE -> "Gerade läuft keine Runde Hide and Seek.";
            case WAITING_READY -> "Warten auf /minigame hideandseek bereit: ";
            case HIDING -> "Versteckzeit: ";
            case SEEKING -> "Suchzeit: ";
        };
        String description = phase;
        if (game.phase() != HideAndSeekGame.Phase.IDLE) {
            description += game.remainingSeconds() + " Sekunden. Sucher: "
                    + seeker.getName().getString() + ", Verstecker: " + hider.getName().getString() + ".";
        }
        String message = description;
        source.sendSuccess(() -> text(message), false);
        return 1;
    }

    private int stop(CommandSourceStack source) {
        if (game.phase() == HideAndSeekGame.Phase.IDLE) {
            return error(source, "Gerade läuft keine Runde Hide and Seek.");
        }
        finish(source.getServer(), "Hide and Seek wurde vom Operator abgebrochen.", null);
        return 1;
    }

    @Override
    public void tick(MinecraftServer server) {
        if (game.phase() == HideAndSeekGame.Phase.IDLE) {
            return;
        }
        if (!participantsValid(server)) {
            finish(server, "Hide and Seek abgebrochen: Ein Spieler ist nicht mehr verfügbar, "
                    + "hat die Dimension oder den Spielmodus gewechselt.", null);
            return;
        }
        if (game.phase() == HideAndSeekGame.Phase.HIDING) {
            protection.keepSeekerWaiting();
        }
        int previousSeconds = game.remainingSeconds();
        switch (game.tick()) {
            case READY_TIMED_OUT -> finish(server,
                    "Hide and Seek abgesagt: Der Verstecker hat sich nicht rechtzeitig bereitgemeldet.", null);
            case SEEKING_STARTED -> {
                protection.beginSeeking();
                seeker.sendSystemMessage(text("Du darfst jetzt suchen! Du hast " + game.remainingSeconds()
                        + " Sekunden. Klicke den Verstecker aus nächster Nähe an, um zu gewinnen."));
                hider.sendSystemMessage(text("Die Suche beginnt! Nametag und Positionsmarker bleiben verborgen."));
            }
            case HIDER_WON -> finish(server, hider.getName().getString()
                    + " hat als Verstecker gewonnen! Die Suchzeit ist abgelaufen.", null);
            case NONE -> {
                int seconds = game.remainingSeconds();
                if (seconds != previousSeconds && seconds > 0
                        && (seconds <= 5 || game.phase() == HideAndSeekGame.Phase.SEEKING
                        && (seconds == 30 || seconds == 10))) {
                    sendToParticipants("Noch " + seconds + " Sekunden "
                            + (game.phase() == HideAndSeekGame.Phase.HIDING ? "Versteckzeit." :
                            game.phase() == HideAndSeekGame.Phase.SEEKING ? "Suchzeit." : "zum Bereitmelden."));
                }
            }
        }
    }

    /** Returns true when the normal attack/interaction must be canceled. */
    public boolean tryTag(ServerPlayer actor, ServerPlayer target) {
        if (actor == target || !game.isParticipant(actor.getUUID())
                || !game.isParticipant(target.getUUID())) {
            return false;
        }
        if (game.phase() == HideAndSeekGame.Phase.SEEKING && actor == seeker && target == hider
                && participantsValid(actor.level().getServer())
                && actor.distanceToSqr(target) <= 9.0 && actor.hasLineOfSight(target)
                && game.tag(actor.getUUID(), target.getUUID())) {
            finish(actor.level().getServer(), actor.getName().getString()
                    + " hat " + target.getName().getString() + " gefunden und gewinnt!", null);
        }
        // Touching during the round never deals combat damage between these two players.
        return true;
    }

    public void onDeath(MinecraftServer server, ServerPlayer player) {
        if (game.isParticipant(player.getUUID())) {
            finish(server, "Hide and Seek abgebrochen: " + player.getName().getString() + " ist gestorben.", null);
        }
    }

    @Override
    public void onDisconnect(MinecraftServer server, ServerPlayer player) {
        if (game.isParticipant(player.getUUID())) {
            finish(server, "Hide and Seek abgebrochen: " + player.getName().getString()
                    + " hat den Server verlassen.", player.getUUID());
        }
    }

    @Override
    public void onChat(MinecraftServer server, ServerPlayer player, String message) {
        // Answers in chat belong to other minigames; finding is an in-world touch.
    }

    @Override
    public void reset() {
        cleanup(null);
    }

    private boolean participantsValid(MinecraftServer server) {
        return seeker != null && hider != null
                && server.getPlayerList().getPlayer(seeker.getUUID()) == seeker
                && server.getPlayerList().getPlayer(hider.getUUID()) == hider
                && eligible(seeker) && eligible(hider)
                && seeker.level().dimension().equals(dimension)
                && hider.level().dimension().equals(dimension);
    }

    private static boolean eligible(ServerPlayer player) {
        return !player.hasDisconnected() && player.isAlive() && !player.isDeadOrDying()
                && !player.isSleeping() && !player.isPassenger()
                && (player.gameMode() == GameType.SURVIVAL || player.gameMode() == GameType.ADVENTURE);
    }

    private void finish(MinecraftServer server, String message, UUID disconnectedId) {
        cleanup(disconnectedId);
        broadcast(server, message);
    }

    private void cleanup(UUID disconnectedId) {
        game.stop();
        try {
            if (protection != null) {
                protection.close(disconnectedId);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Fehler beim Wiederherstellen des Hide-and-Seek-Schutzes", exception);
        } finally {
            protection = null;
            seeker = null;
            hider = null;
            dimension = null;
        }
    }

    private void sendToParticipants(String message) {
        seeker.sendSystemMessage(text(message));
        hider.sendSystemMessage(text(message));
    }

    private static void broadcast(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(text(message), false);
    }

    private static int error(CommandSourceStack source, String message) {
        source.sendFailure(text(message));
        return 0;
    }

    private static Component text(String message) {
        return Component.literal("[Hide and Seek] ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(message).withStyle(ChatFormatting.YELLOW));
    }
}
