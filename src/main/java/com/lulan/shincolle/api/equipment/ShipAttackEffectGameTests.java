package com.lulan.shincolle.api.equipment;

import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.BuffHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/** Runtime and value-contract coverage for ResourceLocation-keyed attack effects. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipAttackEffectGameTests {

    private static final ResourceLocation POISON = ResourceLocation.fromNamespaceAndPath("minecraft", "poison");
    private static final ResourceLocation INSTANT_HEALTH =
            ResourceLocation.fromNamespaceAndPath("minecraft", "instant_health");

    private ShipAttackEffectGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void registeredEffectAppliesWithExactValue(GameTestHelper helper) {
        Cow target = EntityType.COW.create(helper.getLevel());
        if (target == null) {
            throw new AssertionError("Failed to create attack-effect target");
        }
        ShipAttackEffect effect = new ShipAttackEffect(POISON, 2, 160, 100);
        BuffHelper.applyBuffOnTarget(target, Map.of(POISON, effect));
        MobEffectInstance applied = target.getEffect(MobEffects.POISON);
        if (applied == null || applied.getAmplifier() != 2 || applied.getDuration() != 160) {
            throw new AssertionError("ResourceLocation attack effect was not applied exactly");
        }
        ShipAttackEffect instant = new ShipAttackEffect(INSTANT_HEALTH, 1, 999, 100);
        BuffHelper.applyBuffOnTarget(target, Map.of(INSTANT_HEALTH, instant));
        MobEffectInstance appliedInstant = target.getEffect(MobEffects.HEAL);
        if (appliedInstant == null || appliedInstant.getAmplifier() != 1 || appliedInstant.getDuration() != 5) {
            throw new AssertionError("Legacy instant-effect duration was not preserved");
        }
        ResourceLocation unknown = ResourceLocation.fromNamespaceAndPath("attack_test", "not_registered");
        BuffHelper.applyBuffOnTarget(target, Map.of(unknown,
                new ShipAttackEffect(unknown, 0, 20, 100)));
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void valueAndResolvedMapRejectInvalidOrMutableState(GameTestHelper helper) {
        assertRejected(() -> new ShipAttackEffect(POISON, -1, 20, 100), "negative amplifier");
        assertRejected(() -> new ShipAttackEffect(POISON, 0, -1, 100), "negative duration");
        assertRejected(() -> new ShipAttackEffect(POISON, 0, 20, 101), "chance overflow");

        ShipAttackEffect poison = new ShipAttackEffect(POISON, 0, 20, 100);
        ResolvedShipEquipment resolved = new ResolvedShipEquipment(
                com.lulan.shincolle.api.attribute.ShipAttributeValues.zero(
                        com.lulan.shincolle.api.attribute.ShipAttributeLayout.current()),
                ResolvedShipEquipment.DEFAULT_COMPATIBILITY, Map.of(POISON, poison));
        assertRejected(() -> resolved.attackEffects().clear(), "mutable resolved map");
        ResourceLocation wither = ResourceLocation.fromNamespaceAndPath("minecraft", "wither");
        assertRejected(() -> new ResolvedShipEquipment(resolved.attributes(), resolved.compatibility(),
                Map.of(wither, poison)), "mismatched map key");
        helper.succeed();
    }

    private static void assertRejected(Runnable action, String name) {
        try {
            action.run();
            throw new AssertionError("Expected rejection for " + name);
        } catch (IllegalArgumentException | UnsupportedOperationException expected) {
            // Expected validation failure.
        }
    }
}
