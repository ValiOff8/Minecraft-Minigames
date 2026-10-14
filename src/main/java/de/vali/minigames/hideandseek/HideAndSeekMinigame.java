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
                        .then(Commands.argument("seeker", EntityArgument.player())
                                .then(Commands.argument("hider", EntityArgument.player())
                                        .executes(context -> start(context,
                                                DEFAULT_HIDING_SECONDS, DEFAULT_SEEKING_SECONDS))
                                        .then(Commands.argument("hide_seconds", IntegerArgumentType.integer(1, 300))
                                                .executes(context -> start(context,
                                                        IntegerArgumentType.getInteger(context, "hide_seconds"),
                                                        DEFAULT_SEEKING_SECONDS))
                                                .then(Commands.argument("seek_seconds", IntegerArgumentType.integer(1, 1800))
                                                        .executes(context -> start(context,
                                                                IntegerArgumentType.getInteger(context, "hide_seconds"),
                                                                IntegerArgumentType.getInteger(context, "seek_seconds"))))))))
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
        source.sendSuccess(() -> text("Hide and Seek for two players. "
                + "Operator: /minigame hideandseek start <seeker> <hider> [hide_seconds] [seek_seconds]. "
                + "Hider: /minigame hideandseek ready. "
                + "Other commands: status | leave | stop (operator). "
                + "Find the hider by clicking them from at most three blocks away."), false);
        return 1;
    }

    private int start(CommandContext<CommandSourceStack> context, int hidingSeconds, int seekingSeconds)
            throws CommandSyntaxException {
        var source = context.getSource();
        if (game.phase() != HideAndSeekGame.Phase.IDLE) {
            return error(source, "A Hide and Seek round is already running.");
        }
        var newSeeker = EntityArgument.getPlayer(context, "seeker");
        var newHider = EntityArgument.getPlayer(context, "hider");
        if (newSeeker == newHider) {
            return error(source, "The seeker and hider must be two different players.");
        }
        if (!eligible(newSeeker) || !eligible(newHider)) {
            return error(source, "Both players must be alive, awake, and not riding anything "
                    + "in Survival or Adventure mode.");
        }
        if (newSeeker.level() != newHider.level()) {
            return error(source, "Both players must be in the same dimension.");
        }
        game.open(newSeeker.getUUID(), newHider.getUUID(), hidingSeconds, seekingSeconds);
        seeker = newSeeker;
        hider = newHider;
        dimension = newSeeker.level().dimension();
        broadcast(source.getServer(), "New round! Seeker: " + seeker.getName().getString()
                + ", hider: " + hider.getName().getString() + ".");
        hider.sendSystemMessage(text("You are hiding. Start within 60 seconds with "
                + "/minigame hideandseek ready. You will then have " + hidingSeconds
                + " seconds to hide, followed by " + seekingSeconds + " seconds of seeking."));
        seeker.sendSystemMessage(text("You are seeking. Once the hider is ready, "
                + "you will get Blindness and wait at the starting position until the hiding phase ends."));
        return 1;
    }

    private int ready(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (game.phase() != HideAndSeekGame.Phase.WAITING_READY) {
            return error(source, "No round is waiting for the hider to be ready.");
        }
        if (!game.hider().orElseThrow().equals(player.getUUID())) {
            return error(source, "Only the selected hider can start the hiding phase.");
        }
        if (!participantsValid(source.getServer())) {
            finish(source.getServer(), "Round canceled: The players are no longer ready to play.", null);
            return 0;
        }
        try {
            protection = new HideAndSeekProtection(source.getServer(), seeker, hider);
            game.ready(player.getUUID());
            protection.beginHiding();
        } catch (RuntimeException exception) {
            LOGGER.error("Could not start Hide and Seek protection", exception);
            finish(source.getServer(), "Round canceled: Visibility protection could not be enabled.", null);
            return 0;
        }
        seeker.sendSystemMessage(text("Eyes closed! Wait another " + game.remainingSeconds()
                + " seconds at the starting position. The hider is invisible to you during this phase."));
        hider.sendSystemMessage(text("Go hide! You have " + game.remainingSeconds()
                + " seconds. Your name tag and location marker are now hidden."));
        return 1;
    }

    private int leave(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (!game.isParticipant(player.getUUID())) {
            return error(source, "You are not taking part in a Hide and Seek round.");
        }
        finish(source.getServer(), player.getName().getString() + " left the round. "
                + "Hide and Seek was canceled.", null);
        return 1;
    }

    private int status(CommandSourceStack source) {
        String phase = switch (game.phase()) {
            case IDLE -> "No Hide and Seek round is running.";
            case WAITING_READY -> "Waiting for /minigame hideandseek ready: ";
            case HIDING -> "Hiding time: ";
            case SEEKING -> "Seeking time: ";
        };
        String description = phase;
        if (game.phase() != HideAndSeekGame.Phase.IDLE) {
            description += game.remainingSeconds() + " seconds. Seeker: "
                    + seeker.getName().getString() + ", hider: " + hider.getName().getString() + ".";
        }
        String message = description;
        source.sendSuccess(() -> text(message), false);
        return 1;
    }

    private int stop(CommandSourceStack source) {
        if (game.phase() == HideAndSeekGame.Phase.IDLE) {
            return error(source, "No Hide and Seek round is running.");
        }
        finish(source.getServer(), "Hide and Seek was canceled by an operator.", null);
        return 1;
    }

    @Override
    public void tick(MinecraftServer server) {
        if (game.phase() == HideAndSeekGame.Phase.IDLE) {
            return;
        }
        if (!participantsValid(server)) {
            finish(server, "Hide and Seek canceled: A player is no longer available, "
                    + "changed dimension, or changed game mode.", null);
            return;
        }
        if (game.phase() == HideAndSeekGame.Phase.HIDING) {
            protection.keepSeekerWaiting();
        }
        int previousSeconds = game.remainingSeconds();
        switch (game.tick()) {
            case READY_TIMED_OUT -> finish(server,
                    "Hide and Seek canceled: The hider did not get ready in time.", null);
            case SEEKING_STARTED -> {
                protection.beginSeeking();
                seeker.sendSystemMessage(text("You can seek now! You have " + game.remainingSeconds()
                        + " seconds. Click the hider at close range to win."));
                hider.sendSystemMessage(text("Seeking begins! Your name tag and location marker stay hidden."));
            }
            case HIDER_WON -> finish(server, hider.getName().getString()
                    + " wins as the hider! Seeking time has run out.", null);
            case NONE -> {
                int seconds = game.remainingSeconds();
                if (seconds != previousSeconds && seconds > 0
                        && (seconds <= 5 || game.phase() == HideAndSeekGame.Phase.SEEKING
                        && (seconds == 30 || seconds == 10))) {
                    sendToParticipants("Only " + seconds + " seconds left "
                            + (game.phase() == HideAndSeekGame.Phase.HIDING ? "to hide." :
                            game.phase() == HideAndSeekGame.Phase.SEEKING ? "to seek." : "to get ready."));
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
                    + " found " + target.getName().getString() + " and wins!", null);
        }
        // Touching during the round never deals combat damage between these two players.
        return true;
    }

    public void onDeath(MinecraftServer server, ServerPlayer player) {
        if (game.isParticipant(player.getUUID())) {
            finish(server, "Hide and Seek canceled: " + player.getName().getString() + " died.", null);
        }
    }

    @Override
    public void onDisconnect(MinecraftServer server, ServerPlayer player) {
        if (game.isParticipant(player.getUUID())) {
            finish(server, "Hide and Seek canceled: " + player.getName().getString()
                    + " left the server.", player.getUUID());
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
            LOGGER.error("Could not restore Hide and Seek protection", exception);
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
