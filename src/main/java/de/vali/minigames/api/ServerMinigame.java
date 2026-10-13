package de.vali.minigames.api;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** One command branch and its lifecycle. All methods run on the server thread. */
public interface ServerMinigame {
    String id();

    LiteralArgumentBuilder<CommandSourceStack> commands();

    void tick(MinecraftServer server);

    void onChat(MinecraftServer server, ServerPlayer player, String message);

    void onDisconnect(MinecraftServer server, ServerPlayer player);

    void reset();
}
