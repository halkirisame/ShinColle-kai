package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.command.ShipCommandDispatcher;
import com.lulan.shincolle.ai.domain.command.CommandNotification;
import com.lulan.shincolle.ai.domain.command.CommandRejectReason;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.List;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PointerFeedbackGameTests {
    private static final String PREFIX = "chat.shincolle_kai.pointer.command.";

    private PointerFeedbackGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void notificationKeepsReasonsAndSeparatesSunkLine(GameTestHelper helper) {
        Component sunk = Component.translatable(PREFIX + "reason.sunk", 1, "Kongou", 1, 2, 3);
        CommandNotification.ReasonCount range = new CommandNotification.ReasonCount(
                CommandRejectReason.OUT_OF_RANGE, 1);
        CommandNotification.ReasonCount owned = new CommandNotification.ReasonCount(
                CommandRejectReason.NOT_OWNED, 2);
        CommandNotification.ReasonCount sunkReason = new CommandNotification.ReasonCount(
                CommandRejectReason.SUNK, 1);
        var noRecipient = new CommandNotification.Summary(List.of(), false, true, null);
        var formation = new CommandNotification.Summary(List.of(sunkReason), true, false, null);
        var combined = new CommandNotification.Summary(List.of(range, owned, sunkReason), false, false, null);
        var onlySunk = new CommandNotification.Summary(List.of(sunkReason), false, false, null);
        var onlyRange = new CommandNotification.Summary(List.of(range), false, false, null);

        Component emptyTarget = build(noRecipient, null, false);
        assertKey(helper, emptyTarget, PREFIX + "no_recipient");
        Component formationMessage = build(formation, sunk, false);
        assertKey(helper, formationMessage, PREFIX + "formation_unsatisfied");
        assertSecondLine(helper, formationMessage, sunk);

        Component combinedMessage = build(combined, sunk, false);
        assertKey(helper, combinedMessage, PREFIX + "not_delivered");
        assertSecondLine(helper, combinedMessage, sunk);
        Component others = (Component) ((TranslatableContents) combinedMessage.getContents()).getArgs()[0];
        helper.assertTrue(others.getSiblings().size() == 3, "Sunk must not join other reasons");
        assertKey(helper, others.getSiblings().get(0), PREFIX + "reason.out_of_range");
        helper.assertTrue(others.getSiblings().get(1).getString().equals(", "),
                "English reasons need the original separator");
        assertKey(helper, others.getSiblings().get(2), PREFIX + "reason.not_found");
        Component japanese = build(combined, sunk, true);
        Component japaneseReasons = (Component) ((TranslatableContents) japanese.getContents()).getArgs()[0];
        helper.assertTrue(japaneseReasons.getSiblings().get(1).getString().equals("、"),
                "Japanese reasons need the original separator");

        Component sunkOnly = build(onlySunk, sunk, false);
        assertKey(helper, sunkOnly, PREFIX + "not_delivered");
        helper.assertTrue(sunkOnly.getSiblings().isEmpty(), "Sunk alone should stay on one line");
        Component rangeOnly = build(onlyRange, null, false);
        assertKey(helper, rangeOnly, PREFIX + "not_delivered");
        helper.assertTrue(rangeOnly.getSiblings().isEmpty(), "One reason should stay on one line");
        helper.assertTrue(build(new CommandNotification.Summary(List.of(), false, false, null), null, false)
                        == null, "No reasons must not send a message");
        helper.succeed();
    }

    private static void assertKey(GameTestHelper helper, Component component, String expected) {
        helper.assertTrue(component.getContents() instanceof TranslatableContents translated
                        && translated.getKey().equals(expected), "Wrong first translation key: " + component);
    }

    private static void assertSecondLine(GameTestHelper helper, Component message, Component sunk) {
        helper.assertTrue(message.getSiblings().size() == 2
                        && message.getSiblings().get(0).getString().equals("\n")
                        && message.getSiblings().get(1) == sunk,
                "Sunk notice must follow a newline as its own component");
    }

    private static Component build(CommandNotification.Summary summary, Component sunk, boolean japanese) {
        try {
            Method method = ShipCommandDispatcher.class.getDeclaredMethod("notificationMessage",
                    CommandNotification.Summary.class, Component.class, boolean.class);
            method.setAccessible(true);
            return (Component) method.invoke(null, summary, sunk, japanese);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect pointer notification lines", failure);
        }
    }
}
