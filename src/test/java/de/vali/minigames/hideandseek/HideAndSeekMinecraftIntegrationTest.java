package de.vali.minigames.hideandseek;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises Minecraft's attributes and Brigadier contracts without a running server. */
class HideAndSeekMinecraftIntegrationTest {
    private static final Identifier SUPPRESSION = Identifier.fromNamespaceAndPath(
            "server_minigames", "integration_test_hide_and_seek");
    private static final Identifier OTHER_MOD = Identifier.fromNamespaceAndPath(
            "other_mod", "integration_test_visibility");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void clearVisibilityPolicy() {
        HideAndSeekVisibility.clear();
    }

    @ParameterizedTest
    @CsvSource({"0, false", "1, false", "2, true", "3, true", "4, true"})
    void administrationRequiresOperatorLevelTwo(int level, boolean allowed) {
        var dispatcher = dispatcher();
        var source = source(LevelBasedPermissionSet.forLevel(PermissionLevel.byId(level)));
        for (var command : new String[]{"start Sucher Verstecker", "stop"}) {
            assertEquals(allowed, fullyParsed(dispatcher.parse(prefix() + command, source)), command);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"bereit", "ready", "status", "verlassen", "leave"})
    void playerCommandsAreAccessibleWithoutOperatorPermission(String subcommand) {
        assertTrue(fullyParsed(dispatcher().parse(prefix() + subcommand,
                source(PermissionSet.NO_PERMISSIONS))));
    }

    @Test
    void consoleCanParseAdministrationAndBothOptionalDurations() {
        var dispatcher = dispatcher();
        var console = source(LevelBasedPermissionSet.OWNER);
        assertAll(
                () -> assertTrue(fullyParsed(dispatcher.parse(prefix() + "start Sucher Verstecker", console))),
                () -> assertTrue(fullyParsed(dispatcher.parse(prefix() + "start Sucher Verstecker 30", console))),
                () -> assertTrue(fullyParsed(dispatcher.parse(prefix() + "start Sucher Verstecker 30 300", console))),
                () -> assertTrue(fullyParsed(dispatcher.parse(prefix() + "stop", console))));
    }

    @ParameterizedTest
    @CsvSource({"1, 1, true", "300, 1800, true", "0, 60, false", "301, 60, false",
            "30, 0, false", "30, 1801, false", "-1, 60, false", "30, -1, false"})
    void commandParserEnforcesHidingAndSeekingTimeBounds(int hiding, int seeking, boolean valid) {
        var parsed = dispatcher().parse(prefix() + "start Sucher Verstecker " + hiding + " " + seeking,
                source(LevelBasedPermissionSet.GAMEMASTER));
        assertEquals(valid, fullyParsed(parsed));
    }

    @ParameterizedTest
    @ValueSource(strings = {"@a", "@e"})
    void eachRoleRequiresASinglePlayer(String selector) {
        var dispatcher = dispatcher();
        var operator = source(LevelBasedPermissionSet.GAMEMASTER);
        assertFalse(fullyParsed(dispatcher.parse(prefix() + "start " + selector + " Verstecker", operator)));
        assertFalse(fullyParsed(dispatcher.parse(prefix() + "start Sucher " + selector, operator)));
    }

    @Test
    void zeroNameTagDistancesAreSyncedToVanillaClients() {
        assertTrue(Attributes.NAME_TAG_DISTANCE.value().isClientSyncable());
        assertTrue(Attributes.BELOW_NAME_DISTANCE.value().isClientSyncable());
    }

    @Test
    void suppressionHidesAllFourRangesAndPreservesOtherModifiersAndBaseValues() {
        for (var attribute : List.of(Attributes.NAME_TAG_DISTANCE, Attributes.BELOW_NAME_DISTANCE,
                Attributes.WAYPOINT_TRANSMIT_RANGE, Attributes.WAYPOINT_RECEIVE_RANGE)) {
            var instance = new AttributeInstance(attribute, ignored -> {});
            instance.setBaseValue(100.0);
            var otherModifier = new AttributeModifier(OTHER_MOD, 25.0, AttributeModifier.Operation.ADD_VALUE);
            instance.addPermanentModifier(otherModifier);
            assertEquals(125.0, instance.getValue());

            HideAndSeekProtection.addSuppression(instance, SUPPRESSION);
            assertEquals(0.0, instance.getValue(), attribute.toString());
            assertEquals(100.0, instance.getBaseValue());
            assertEquals(otherModifier, instance.getModifier(OTHER_MOD));
            assertFalse(instance.getPermanentModifiers().contains(instance.getModifier(SUPPRESSION)),
                    "Game suppression must not persist in player saves.");

            // A concurrent change by another mod must survive the end of the game.
            instance.setBaseValue(200.0);
            var changedOtherModifier = new AttributeModifier(OTHER_MOD, 50.0,
                    AttributeModifier.Operation.ADD_VALUE);
            instance.addOrReplacePermanentModifier(changedOtherModifier);
            assertEquals(0.0, instance.getValue());
            HideAndSeekProtection.removeSuppression(instance, SUPPRESSION);
            assertEquals(250.0, instance.getValue());
            assertEquals(200.0, instance.getBaseValue());
            assertEquals(changedOtherModifier, instance.getModifier(OTHER_MOD));
            assertFalse(instance.hasModifier(SUPPRESSION));
        }
    }

    @Test
    void suppressionNeverOverwritesAnExistingModifierWithTheSameIdentifier() {
        var instance = new AttributeInstance(Attributes.NAME_TAG_DISTANCE, ignored -> {});
        var existing = new AttributeModifier(SUPPRESSION, 4.0, AttributeModifier.Operation.ADD_VALUE);
        instance.addTransientModifier(existing);
        assertThrows(IllegalStateException.class, () ->
                HideAndSeekProtection.addSuppression(instance, SUPPRESSION));
        assertEquals(existing, instance.getModifier(SUPPRESSION));
    }

    @Test
    void entityHidingOnlyAffectsTheAssignedSeekerAndCanBeCleared() {
        var hider = UUID.randomUUID();
        var seeker = UUID.randomUUID();
        var bystander = UUID.randomUUID();
        assertFalse(HideAndSeekVisibility.isHiddenFrom(hider, seeker));
        HideAndSeekVisibility.begin(hider, seeker);
        assertAll(
                () -> assertTrue(HideAndSeekVisibility.isHiddenFrom(hider, seeker)),
                () -> assertFalse(HideAndSeekVisibility.isHiddenFrom(hider, bystander)),
                () -> assertFalse(HideAndSeekVisibility.isHiddenFrom(hider, hider)),
                () -> assertFalse(HideAndSeekVisibility.isHiddenFrom(bystander, seeker)),
                () -> assertFalse(HideAndSeekVisibility.isHiddenFrom(seeker, hider)));
        HideAndSeekVisibility.clear();
        assertFalse(HideAndSeekVisibility.isHiddenFrom(hider, seeker));
    }

    private static String prefix() {
        return "minigame " + new HideAndSeekMinigame().id() + " ";
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(Commands.literal("minigame").then(new HideAndSeekMinigame().commands()));
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
