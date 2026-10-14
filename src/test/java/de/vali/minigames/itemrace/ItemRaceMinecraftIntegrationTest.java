package de.vali.minigames.itemrace;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import de.vali.minigames.game.ItemRaceCatalog;
import de.vali.minigames.game.ItemRaceDifficulty;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises the Minecraft inventory and Brigadier contracts without a running server. */
class ItemRaceMinecraftIntegrationTest {
    private static final String PREFIX = "minigame itemrace ";

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void registersTheItemRaceIdentifier() {
        assertEquals("itemrace", new ItemRaceMinigame().id());
    }

    @Test
    void everyCatalogEntryResolvesToItsActualVanillaItem() {
        assertAll(ItemRaceCatalog.pool(ItemRaceDifficulty.MIXED).stream().map(itemId -> () -> {
            var id = Identifier.parse(itemId);
            var registered = BuiltInRegistries.ITEM.get(id);
            assertEquals("minecraft", id.getNamespace());
            assertTrue(registered.isPresent(), () -> "Unknown vanilla item: " + id);
            var item = registered.orElseThrow().value();
            assertFalse(item == Items.AIR, () -> "Item must not resolve to air: " + id);
            assertEquals(id, BuiltInRegistries.ITEM.getKey(item));
        }));
    }

    @ParameterizedTest
    @CsvSource({"0, false", "1, false", "2, true", "3, true", "4, true"})
    void administrationRequiresOperatorLevelTwo(int level, boolean allowed) {
        var dispatcher = dispatcher();
        var source = source(LevelBasedPermissionSet.forLevel(PermissionLevel.byId(level)));
        for (var command : new String[]{"start Alice Bob", "start Alice Bob 10 schwer", "stop"}) {
            assertEquals(allowed, fullyParsed(dispatcher.parse(PREFIX + command, source)), command);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "liste", "list", "verlassen", "leave"})
    void playerCommandsAreAccessibleWithoutOperatorPermission(String subcommand) {
        assertTrue(fullyParsed(dispatcher().parse(PREFIX + subcommand,
                source(PermissionSet.NO_PERMISSIONS))));
    }

    @Test
    void helpIsAccessibleWithoutOperatorPermission() {
        assertTrue(fullyParsed(dispatcher().parse("minigame itemrace",
                source(PermissionSet.NO_PERMISSIONS))));
    }

    @Test
    void consoleCanParseAdministrationAndOptionalSettings() {
        var dispatcher = dispatcher();
        var console = source(LevelBasedPermissionSet.OWNER);
        assertAll(
                () -> assertTrue(fullyParsed(dispatcher.parse(PREFIX + "start Alice Bob", console))),
                () -> assertTrue(fullyParsed(dispatcher.parse(PREFIX + "start Alice Bob 10", console))),
                () -> assertTrue(fullyParsed(dispatcher.parse(PREFIX + "start Alice Bob schwer", console))),
                () -> assertTrue(fullyParsed(dispatcher.parse(PREFIX + "start Alice Bob 10 schwer", console))),
                () -> assertTrue(fullyParsed(dispatcher.parse(PREFIX + "stop", console))));
    }

    @ParameterizedTest
    @CsvSource({"1, true", "30, true", "0, false", "31, false", "-1, false"})
    void commandParserEnforcesListSizeBounds(int count, boolean valid) {
        var dispatcher = dispatcher();
        var operator = source(LevelBasedPermissionSet.GAMEMASTER);
        for (var suffix : new String[]{"", " leicht", " normal", " schwer", " gemischt"}) {
            var command = PREFIX + "start Alice Bob " + count + suffix;
            assertEquals(valid, fullyParsed(dispatcher.parse(command, operator)), command);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"leicht", "normal", "schwer", "gemischt", "easy", "hard", "mixed"})
    void difficultiesAreAcceptedWithAndWithoutAnExplicitSize(String difficulty) {
        var dispatcher = dispatcher();
        var operator = source(LevelBasedPermissionSet.GAMEMASTER);
        for (var settings : new String[]{difficulty, "1 " + difficulty, "30 " + difficulty}) {
            var command = PREFIX + "start Alice Bob " + settings;
            assertTrue(fullyParsed(dispatcher.parse(command, operator)), command);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"expert", "10 expert", "schwer 10", "5 leicht extra", "1.5"})
    void unknownDifficultyAndUnsupportedSettingsAreRejected(String settings) {
        assertFalse(fullyParsed(dispatcher().parse(PREFIX + "start Alice Bob " + settings,
                source(LevelBasedPermissionSet.GAMEMASTER))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"@a", "@e"})
    void eachParticipantArgumentRequiresASinglePlayer(String selector) {
        var dispatcher = dispatcher();
        var operator = source(LevelBasedPermissionSet.GAMEMASTER);
        assertFalse(fullyParsed(dispatcher.parse(PREFIX + "start " + selector + " Bob", operator)));
        assertFalse(fullyParsed(dispatcher.parse(PREFIX + "start Alice " + selector, operator)));
    }

    @Test
    void inventoryScanIgnoresEmptyStacksDeduplicatesItemsAndIncludesTheLastSlot() {
        var inventory = new SimpleContainer(6);
        inventory.setItem(0, ItemStack.EMPTY);
        inventory.setItem(1, stack(Items.IRON_INGOT, 3));
        inventory.setItem(2, stack(Items.IRON_INGOT, 1));
        inventory.setItem(3, stack(Items.DIRT, 0));
        inventory.setItem(5, stack(Items.DIAMOND, 1));

        assertEquals(Set.of("minecraft:iron_ingot", "minecraft:diamond"),
                ItemRaceMinigame.inventoryItemIds(inventory));
    }

    @Test
    void emptyInventoryContainsNoItemIds() {
        assertTrue(ItemRaceMinigame.inventoryItemIds(new SimpleContainer(6)).isEmpty());
    }

    @Test
    void itemNamesAreEnglishLiteralsRatherThanClientTranslations() {
        assertEquals("Iron Ingot", ItemRaceMinigame.itemName("minecraft:iron_ingot").getString());
        assertEquals("White Wool", ItemRaceMinigame.itemName("minecraft:white_wool").getString());
        assertEquals("Nether Star", ItemRaceMinigame.itemName("minecraft:nether_star").getString());
        for (var itemId : ItemRaceCatalog.pool(ItemRaceDifficulty.MIXED)) {
            var item = BuiltInRegistries.ITEM.get(Identifier.parse(itemId)).orElseThrow().value();
            assertTrue(Language.DEFAULT_INSTANCE.has(item.getDescriptionId()), itemId);
            assertFalse(ItemRaceMinigame.itemName(itemId).getContents() instanceof TranslatableContents,
                    "Client language must not change the displayed item name: " + itemId);
        }
    }

    private static ItemStack stack(Item item, int count) {
        // Built-in holder components are bound by resource loading, beyond this registry-only bootstrap.
        // A direct holder retains the registered item value without changing shared registry fixtures.
        return new ItemStack(Holder.direct(item, DataComponentMap.EMPTY), count);
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(Commands.literal("minigame").then(new ItemRaceMinigame().commands()));
        return dispatcher;
    }

    private static CommandSourceStack source(PermissionSet permissions) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO,
                null, permissions, Component.literal("test"), null);
    }

    private static boolean fullyParsed(ParseResults<CommandSourceStack> parsed) {
        return !parsed.getReader().canRead()
                && parsed.getExceptions().isEmpty()
                && parsed.getContext().getCommand() != null;
    }
}
