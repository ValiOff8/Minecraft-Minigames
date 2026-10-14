package de.vali.minigames;

import de.vali.minigames.api.ServerMinigame;
import de.vali.minigames.hideandseek.HideAndSeekMinigame;
import de.vali.minigames.itemrace.ItemRaceMinigame;
import de.vali.minigames.sound.SoundGuessMinigame;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServerMinigamesMod implements ModInitializer {
    public static final String MOD_ID = "server_minigames";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private final Map<String, ServerMinigame> minigames = new LinkedHashMap<>();

    @Override
    public void onInitialize() {
        register(new SoundGuessMinigame());
        register(new ItemRaceMinigame());
        var hideAndSeek = new HideAndSeekMinigame();
        register(hideAndSeek);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, selection) -> {
            var root = Commands.literal("minigame").executes(context -> {
                context.getSource().sendSuccess(() -> Component.literal(
                        "[Minispiele] Verfügbar: " + String.join(", ", minigames.keySet())
                                + ". Hilfe: /minigame <Spiel>"), false);
                return 1;
            });
            minigames.values().forEach(game -> root.then(game.commands()));
            dispatcher.register(root);
        });

        ServerTickEvents.END_SERVER_TICK.register(server ->
                minigames.values().forEach(game -> game.tick(server)));

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, chatType) -> {
            // Public ordinary chat only; blocked chat never reaches this event.
            if (!chatType.chatType().is(ChatType.CHAT) || message.isFullyFiltered()) {
                return;
            }
            var server = sender.level().getServer();
            if (server != null) {
                server.execute(() -> minigames.values().forEach(game ->
                        game.onChat(server, sender, message.signedContent())));
            }
        });

        // LEAVE runs on the server thread before saving player data.
        ServerPlayerEvents.LEAVE.register(player -> {
            var server = player.level().getServer();
            minigames.values().forEach(game -> game.onDisconnect(server, player));
        });
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) ->
                tag(hideAndSeek, player, target));
        UseEntityCallback.EVENT.register((player, level, hand, target, hit) ->
                tag(hideAndSeek, player, target));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                hideAndSeek.onDeath(player.level().getServer(), player);
            }
        });
        // Restore temporary effects before normal server shutdown saves the players.
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                minigames.values().forEach(ServerMinigame::reset));
        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
                minigames.values().forEach(ServerMinigame::reset));

        LOGGER.info("Server Minigames geladen: {}", minigames.keySet());
    }

    private static InteractionResult tag(HideAndSeekMinigame game, Player player, Entity target) {
        if (player instanceof ServerPlayer actor && target instanceof ServerPlayer other
                && game.tryTag(actor, other)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private void register(ServerMinigame game) {
        if (minigames.putIfAbsent(game.id(), game) != null) {
            throw new IllegalArgumentException("Doppelte Minispiel-ID: " + game.id());
        }
    }
}
