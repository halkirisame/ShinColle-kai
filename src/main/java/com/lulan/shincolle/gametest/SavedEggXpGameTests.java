package com.lulan.shincolle.gametest;

import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.ShipSpawnEgg;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SavedEggXpGameTests {

    private SavedEggXpGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void savedEggTooltipMatchesConsumedXp(GameTestHelper helper) throws ReflectiveOperationException {
        ShipSpawnEgg item = (ShipSpawnEgg) ModItems.SHIP_SPAWN_EGG.get();
        RecordingPlayer player = new RecordingPlayer(helper.getLevel());
        player.getAbilities().instabuild = false;
        for (int[] example : new int[][]{{90, 30}, {91, 30}, {92, 30}, {1, 0}, {2, 0}, {3, 1}}) {
            ItemStack egg = new ItemStack(item);
            CompoundTag tag = egg.getOrCreateTag();
            tag.putIntArray("StateMinor", new int[]{example[0]});
            player.experienceLevel = 100;
            helper.assertTrue(tag.getIntArray("StateMinor")[0] == example[0]
                    && player.experienceLevel == 100 && !player.getAbilities().instabuild,
                    "fixture must contain the raw ship level and a survival player with 100 levels");

            List<Component> tooltip = new ArrayList<>();
            item.appendHoverText(egg, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
            String expected = ChatFormatting.AQUA
                    + Component.translatable("gui.shincolle_kai.eggText").getString() + " " + example[1];
            helper.assertTrue(tooltip.size() == 1 && tooltip.get(0).getString().equals(expected),
                    "ship level " + example[0] + " must show cost " + example[1] + ", got " + tooltip);
            helper.assertTrue(consumeXp(player, tag), "sufficient experience must allow the saved egg");
            helper.assertTrue(player.experienceLevel == 100 - example[1],
                    "the consumed experience must match the displayed cost for ship level " + example[0]);
        }
        helper.assertTrue(player.messages.isEmpty(), "successful consumption must not send a failure message");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void savedEggXpPreservesExemptionsAndInsufficientLevelMessage(GameTestHelper helper)
            throws ReflectiveOperationException {
        ShipSpawnEgg item = (ShipSpawnEgg) ModItems.SHIP_SPAWN_EGG.get();
        RecordingPlayer player = new RecordingPlayer(helper.getLevel());
        player.getAbilities().instabuild = false;
        player.experienceLevel = 29;
        for (boolean emptyState : new boolean[]{false, true}) {
            ItemStack egg = new ItemStack(item);
            CompoundTag tag = egg.getOrCreateTag();
            if (emptyState) {
                tag.putIntArray("StateMinor", new int[0]);
            }
            List<Component> tooltip = new ArrayList<>();
            item.appendHoverText(egg, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
            helper.assertTrue(tooltip.isEmpty(), "missing or empty saved state must not show a required level");
            helper.assertTrue(consumeXp(player, tag) && player.experienceLevel == 29,
                    "missing or empty saved state must not consume experience");
        }
        helper.assertTrue(consumeXp(player, null) && player.experienceLevel == 29,
                "an egg without NBT must not consume experience");

        CompoundTag saved = new CompoundTag();
        saved.putIntArray("StateMinor", new int[]{90});
        helper.assertTrue(!consumeXp(player, saved) && player.experienceLevel == 29,
                "insufficient experience must reject the egg without consuming experience");
        helper.assertTrue(player.messages.size() == 1 && player.messages.get(0).equals(
                        Component.translatable("chat.shincolle_kai.levelfail")),
                "insufficient experience must send the existing failure message");

        player.messages.clear();
        player.getAbilities().instabuild = true;
        helper.assertTrue(consumeXp(player, saved) && player.experienceLevel == 29 && player.messages.isEmpty(),
                "creative use must not consume experience or send a failure message");
        player.getAbilities().instabuild = false;
        player.experienceLevel = 30;
        helper.assertTrue(consumeXp(player, saved) && player.experienceLevel == 0 && player.messages.isEmpty(),
                "exactly the required experience must be accepted and consumed");
        helper.succeed();
    }

    private static boolean consumeXp(Player player, CompoundTag tag) throws ReflectiveOperationException {
        Method consume = ShipSpawnEgg.class.getDeclaredMethod("consumeSavedEggXpCost", Player.class, CompoundTag.class);
        consume.setAccessible(true);
        return (boolean) consume.invoke(null, player, tag);
    }

    private static final class RecordingPlayer extends FakePlayer {
        private final List<Component> messages = new ArrayList<>();

        private RecordingPlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "SavedEggXpFixture"));
        }

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message);
        }
    }
}
