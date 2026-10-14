package de.vali.minigames.itemrace;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.vali.minigames.api.ServerMinigame;
import de.vali.minigames.game.ItemRaceCatalog;
import de.vali.minigames.game.ItemRaceDifficulty;
import de.vali.minigames.game.ItemRaceGame;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.Container;
import net.minecraft.world.level.GameType;

/** Two survival players race to collect the same randomly selected item list. */
public final class ItemRaceMinigame implements ServerMinigame {
    private final ItemRaceGame game = new ItemRaceGame();
    private final Random random = new Random();
    private final Map<UUID, String> playerNames = new LinkedHashMap<>();
    private ItemRaceDifficulty difficulty = ItemRaceDifficulty.EASY;

    @Override
    public String id() {
        return "itemrace";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> commands() {
        var itemCount = Commands.argument("anzahl",
                        IntegerArgumentType.integer(ItemRaceCatalog.MIN_ITEMS, ItemRaceCatalog.MAX_ITEMS))
                .executes(context -> start(context, ItemRaceDifficulty.EASY,
                        IntegerArgumentType.getInteger(context, "anzahl")));
        var secondPlayer = Commands.argument("spieler2", EntityArgument.player())
                .executes(context -> start(context, ItemRaceDifficulty.EASY, ItemRaceCatalog.DEFAULT_ITEMS));
        for (var option : ItemRaceDifficulty.values()) {
            var names = option.commandName().equals(option.englishName())
                    ? List.of(option.commandName()) : List.of(option.commandName(), option.englishName());
            for (var name : names) {
                itemCount.then(Commands.literal(name).executes(context -> start(context, option,
                        IntegerArgumentType.getInteger(context, "anzahl"))));
                secondPlayer.then(Commands.literal(name).executes(context ->
                        start(context, option, ItemRaceCatalog.DEFAULT_ITEMS)));
            }
        }
        secondPlayer.then(itemCount);
        return Commands.literal(id())
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("start")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.argument("spieler1", EntityArgument.player()).then(secondPlayer)))
                .then(Commands.literal("liste").executes(context -> list(context.getSource())))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("verlassen").executes(context -> leave(context.getSource())))
                .then(Commands.literal("leave").executes(context -> leave(context.getSource())))
                .then(Commands.literal("stop")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(context -> stop(context.getSource())));
    }

    private int help(CommandSourceStack source) {
        source.sendSuccess(() -> text("Sammelt als Erste alle Items derselben Zufallsliste! "
                + "Operator: /minigame itemrace start <Spieler1> <Spieler2> [Anzahl] [Schwierigkeit]. "
                + "Anzahl: 1–30 (Standard: 5). Schwierigkeit: leicht (Standard), normal, schwer oder gemischt. "
                + "Weitere Befehle: liste | status | verlassen | stop (Operator). "
                + "Beispiel: /minigame itemrace start Alice Bob 10 normal"), false);
        return 1;
    }

    private int start(CommandContext<CommandSourceStack> context, ItemRaceDifficulty selectedDifficulty,
            int itemCount) throws CommandSyntaxException {
        var source = context.getSource();
        if (game.running()) {
            return error(source, "Es läuft bereits ein Item-Rennen. Nutze /minigame itemrace status.");
        }
        var first = EntityArgument.getPlayer(context, "spieler1");
        var second = EntityArgument.getPlayer(context, "spieler2");
        if (first.getUUID().equals(second.getUUID())) {
            return error(source, "Wähle zwei verschiedene Spieler aus.");
        }
        if (!availableInSurvival(first) || !availableInSurvival(second)
                || !first.isAlive() || !second.isAlive()) {
            return error(source, "Beide Spieler müssen online und lebend im Überlebensmodus sein.");
        }
        var targets = ItemRaceCatalog.select(selectedDifficulty, itemCount, random);
        game.start(first.getUUID(), second.getUUID(), targets);
        difficulty = selectedDifficulty;
        playerNames.put(first.getUUID(), first.getName().getString());
        playerNames.put(second.getUUID(), second.getName().getString());
        broadcast(source.getServer(), first.getName().getString() + " gegen " + second.getName().getString()
                + ": Das Item-Rennen beginnt! " + itemCount + " Items, Schwierigkeit: "
                + difficulty.commandName() + ". Wer zuerst alle gesammelt hat, gewinnt!");
        for (var player : new ServerPlayer[]{first, second}) {
            player.sendSystemMessage(text("Pro Item genügt ein Exemplar im Inventar. "
                    + "Vorhandene Items zählen mit; gesammelte Items bleiben abgehakt. "
                    + "Deine Liste: /minigame itemrace liste."));
            sendList(player);
        }
        return 1;
    }

    private int list(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (!game.isParticipant(player.getUUID())) {
            return error(source, "Du nimmst gerade an keinem Item-Rennen teil.");
        }
        sendList(player);
        return 1;
    }

    private void sendList(ServerPlayer player) {
        var collected = game.collected(player.getUUID());
        player.sendSystemMessage(text("Deine Sammelliste (" + collected.size() + "/" + game.targets().size()
                + ", " + difficulty.commandName() + "):"));
        int index = 1;
        for (var itemId : game.targets()) {
            boolean done = collected.contains(itemId);
            player.sendSystemMessage(text(Component.literal(index++ + ". " + (done ? "[✓] " : "[ ] "))
                    .append(itemName(itemId)).withStyle(done ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
        }
    }

    private int status(CommandSourceStack source) {
        if (!game.running()) {
            source.sendSuccess(() -> text("Gerade läuft kein Item-Rennen."), false);
            return 1;
        }
        source.sendSuccess(() -> text("Item-Rennen: " + game.targets().size() + " Items, Schwierigkeit: "
                + difficulty.commandName() + ". " + String.join(" | ", game.participants().stream()
                .map(id -> playerNames.get(id) + ": " + game.collected(id).size() + "/" + game.targets().size())
                .toList()) + ". Eigene Liste: /minigame itemrace liste."), false);
        return 1;
    }

    private int leave(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (!game.isParticipant(player.getUUID())) {
            return error(source, "Du nimmst gerade an keinem Item-Rennen teil.");
        }
        finish(source.getServer(), "Item-Rennen abgebrochen: " + player.getName().getString()
                + " hat das Spiel verlassen.");
        return 1;
    }

    private int stop(CommandSourceStack source) {
        if (!game.running()) {
            return error(source, "Gerade läuft kein Item-Rennen.");
        }
        finish(source.getServer(), "Das Item-Rennen wurde vom Operator abgebrochen.");
        return 1;
    }

    @Override
    public void tick(MinecraftServer server) {
        if (!game.running()) {
            return;
        }
        // Resolve current player instances on each tick so death/respawn and portals remain usable.
        var players = new LinkedHashMap<UUID, ServerPlayer>();
        for (var id : game.participants()) {
            var player = server.getPlayerList().getPlayer(id);
            if (player == null || !availableInSurvival(player)) {
                finish(server, "Item-Rennen abgebrochen: " + playerNames.get(id)
                        + " ist nicht mehr online oder im Überlebensmodus.");
                return;
            }
            players.put(id, player);
        }
        for (var entry : players.entrySet()) {
            var player = entry.getValue();
            if (!player.isAlive()) {
                continue;
            }
            var found = game.collect(entry.getKey(), inventoryItemIds(player.getInventory()));
            for (var itemId : found) {
                player.sendSystemMessage(text(Component.literal("Gesammelt: ").append(itemName(itemId))
                        .append(" (" + game.collected(entry.getKey()).size() + "/" + game.targets().size() + ").")));
            }
        }
        // Check both players together: the iteration order must never decide a same-tick finish.
        var finishers = game.finishers();
        if (finishers.size() == 2) {
            finish(server, "Unentschieden! Beide Spieler haben ihre Liste im selben Server-Tick vervollständigt.");
        } else if (finishers.size() == 1) {
            finish(server, playerNames.get(finishers.iterator().next()) + " gewinnt das Item-Rennen! Alle "
                    + game.targets().size() + " Items wurden gesammelt.");
        }
    }

    /** Includes hotbar, main inventory, offhand and equipment; no nested or external storage. */
    static Set<String> inventoryItemIds(Container inventory) {
        var items = new HashSet<String>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                items.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        }
        return items;
    }

    private static boolean availableInSurvival(ServerPlayer player) {
        return !player.hasDisconnected() && player.gameMode() == GameType.SURVIVAL;
    }

    @Override
    public void onDisconnect(MinecraftServer server, ServerPlayer player) {
        if (game.isParticipant(player.getUUID())) {
            finish(server, "Item-Rennen abgebrochen: " + player.getName().getString()
                    + " hat den Server verlassen.");
        }
    }

    @Override
    public void onChat(MinecraftServer server, ServerPlayer player, String message) {
        // Collection is checked against the server inventory, independently of chat messages.
    }

    @Override
    public void reset() {
        game.stop();
        playerNames.clear();
        difficulty = ItemRaceDifficulty.EASY;
    }

    private void finish(MinecraftServer server, String message) {
        reset();
        broadcast(server, message);
    }

    private static Component itemName(String itemId) {
        return BuiltInRegistries.ITEM.get(Identifier.parse(itemId)).orElseThrow()
                .value().getDefaultInstance().getHoverName();
    }

    private static void broadcast(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(text(message), false);
    }

    private static int error(CommandSourceStack source, String message) {
        source.sendFailure(text(message));
        return 0;
    }

    private static MutableComponent text(String message) {
        return text(Component.literal(message));
    }

    private static MutableComponent text(Component message) {
        return Component.literal("[Item-Rennen] ").withStyle(ChatFormatting.GREEN)
                .append(Component.empty().withStyle(ChatFormatting.YELLOW).append(message));
    }
}
