package com.lulan.shincolle.gametest;

import com.lulan.shincolle.api.ShinColleApi;
import com.lulan.shincolle.api.attribute.ShipAttributeRegistries;
import com.lulan.shincolle.api.ship.PlayerOwnedShip;
import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModShipAttributes;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Focused tests for the Java-addon-facing Stage 6B1 contracts. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PublicApiBoundaryGameTests {

    private PublicApiBoundaryGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void publicRegistryKeyOwnsCanonicalForgeRegistry(GameTestHelper helper) {
        if (!ShinColleApi.MOD_ID.equals(ShipAttributeRegistries.REGISTRY_ID.getNamespace())) {
            helper.fail("Public registry namespace does not use the canonical ShinColle mod ID");
            return;
        }
        if (!ShipAttributeRegistries.REGISTRY_ID.equals(ModShipAttributes.REGISTRY.get().getRegistryName())) {
            helper.fail("Core ship attributes were not built against the public registry key");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void friendlyShipOwnershipUsesServerAuthoritativeUid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BasicEntityShip ship = ModEntities.BB_KONGOU.get().create(level);
        if (ship == null) {
            helper.fail("Could not create a friendly ship for the public ownership test");
            return;
        }

        FakePlayer owner = player(level, "00000000-0000-0000-0000-000000000061", "api_owner", 1061);
        FakePlayer other = player(level, "00000000-0000-0000-0000-000000000062", "api_other", 1062);
        ship.setPlayerUID(1061);

        PlayerOwnedShip ownership = ship;
        if (!ownership.isOwnedByPlayer(owner)) {
            helper.fail("The public ownership contract rejected the matching ShinColle owner UID");
            return;
        }
        if (ownership.isOwnedByPlayer(other)) {
            helper.fail("The public ownership contract accepted a different ShinColle owner UID");
            return;
        }
        Object hostile = ModEntities.BB_KONGOU_MOB.get().create(level);
        if (hostile instanceof PlayerOwnedShip) {
            helper.fail("Hostile ships must not expose the friendly ownership contract");
            return;
        }
        helper.succeed();
    }

    private static FakePlayer player(ServerLevel level, String uuid, String name, int playerUid) {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString(uuid), name));
        CapaTeitoku capability = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (capability == null) {
            throw new AssertionError("Fake player has no Teitoku capability: " + name);
        }
        capability.setPlayerUID(playerUid);
        return player;
    }
}
