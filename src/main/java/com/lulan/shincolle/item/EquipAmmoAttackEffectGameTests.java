package com.lulan.shincolle.item;

import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.api.equipment.ResolvedShipEquipment;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.ShipInnateAttackEffects;
import com.lulan.shincolle.equip.ShipEquipmentInternalEffects;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.equipdata.EquipDataRegistry;
import com.lulan.shincolle.equipdata.EquipDefinition;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;

/**
 * The shipped ammo's on-hit effects live in its equipment definitions, not in Java.
 *
 * <p>They were a switch in {@link EquipAmmo}, which meant the one part of the equipment system
 * a datapack could not reach was the only part that used on-hit effects at all. These tests pin
 * both halves: the definitions carry the upstream values, and the item no longer supplies any.</p>
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EquipAmmoAttackEffectGameTests {

    private EquipAmmoAttackEffectGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shippedAmmoDefinitionsCarryTheUpstreamAttackEffects(GameTestHelper helper) {
        assertDefinitionEffect(0, "poison", 0, 120, 50);
        assertDefinitionEffect(1, "poison", 1, 120, 70);
        assertDefinitionEffect(3, "nausea", 0, 120, 50);
        assertDefinitionEffect(4, "wither", 0, 100, 25);
        assertDefinitionEffect(6, "levitation", 0, 100, 50);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void ammoWithoutAnUpstreamEffectDeclaresNone(GameTestHelper helper) {
        for (int variant : new int[] {2, 5, 7, 8}) {
            Map<ResourceLocation, ShipAttackEffect> effects = definition(variant).attackEffects();
            if (!effects.isEmpty()) {
                throw new AssertionError("Ammo variant " + variant
                        + " gained an attack effect it never had upstream: " + effects);
            }
        }
        helper.succeed();
    }

    /** The definition value has to survive the whole resolve path, not just the loader. */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equippedAmmoAppliesItsDefinitionEffectToTheShip(GameTestHelper helper) {
        BasicEntityShip ship = ModEntities.BB_KONGOU.get().create(helper.getLevel());
        if (ship == null) {
            throw new AssertionError("Failed to create the test ship");
        }

        ship.getCapaShipInventory().setStackInSlot(0,
                ((BasicEquip) ModItems.EQUIP_AMMO.get()).createStack(1));
        ship.calcShipAttributes(2, false);
        assertEffect(ship.getAttackEffectMap(), effectId("poison"), 1, 120, 70,
                "type 1 shell effect from its definition");

        ship.getCapaShipInventory().setStackInSlot(0, ItemStack.EMPTY);
        ship.calcShipAttributes(2, false);
        if (ship.getAttackEffectMap().containsKey(effectId("poison"))) {
            throw new AssertionError("Removing the ammo left its effect behind: "
                    + ship.getAttackEffectMap());
        }
        helper.succeed();
    }

    /** Ammo with no declared effect must not pick one up from a neighbouring definition. */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equippedAntiAirShellAppliesNoAttackEffect(GameTestHelper helper) {
        BasicEntityShip ship = ModEntities.BB_KONGOU.get().create(helper.getLevel());
        if (ship == null) {
            throw new AssertionError("Failed to create the test ship");
        }

        ship.getCapaShipInventory().setStackInSlot(0,
                ((BasicEquip) ModItems.EQUIP_AMMO.get()).createStack(2));
        ship.calcShipAttributes(2, false);

        Map<ResourceLocation, ShipAttackEffect> applied = ship.getAttackEffectMap();
        if (applied.size() != 1 || !applied.containsKey(ShipInnateAttackEffects.WEAKNESS)) {
            throw new AssertionError("The anti-air shell added an attack effect of its own: " + applied);
        }
        helper.succeed();
    }

    /** Definition effects intentionally replace an enchant shell's PList entry with the same ID. */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void definitionAttackEffectOverridesEnchantShellEffectWithSameId(GameTestHelper helper) {
        BasicEntityShip ship = ModEntities.BB_KONGOU.get().create(helper.getLevel());
        if (ship == null) {
            throw new AssertionError("Failed to create the test ship");
        }

        ResourceLocation poison = effectId("poison");
        ItemStack shell = ((BasicEquip) ModItems.EQUIP_AMMO.get()).createStack(7);
        CompoundTag stored = new CompoundTag();
        stored.putInt(EquipAmmo.PID, MobEffect.getId(MobEffects.POISON));
        stored.putInt(EquipAmmo.PLEVEL, 0);
        stored.putInt(EquipAmmo.PTIME, 40);
        stored.putInt(EquipAmmo.PCHANCE, 20);
        ListTag effects = new ListTag();
        effects.add(stored);
        shell.getOrCreateTag().put(EquipAmmo.PLIST, effects);

        ShipAttackEffect definitionEffect = new ShipAttackEffect(poison, 2, 120, 70);
        ResolvedShipEquipment resolved = new ResolvedShipEquipment(
                ShipAttributeValues.zero(ShipAttributeLayout.current()),
                Set.of(ResolvedShipEquipment.CANNON_COMPATIBILITY), Map.of(poison, definitionEffect));
        ShipEquipmentInternalEffects.apply(ship, ship, shell, resolved);

        if (!definitionEffect.equals(ship.getAttackEffectMap().get(poison))) {
            throw new AssertionError("Definition effect did not override PList effect: "
                    + ship.getAttackEffectMap());
        }
        helper.succeed();
    }

    private static void assertDefinitionEffect(int variant, String path, int amplifier,
                                               int duration, int chance) {
        Map<ResourceLocation, ShipAttackEffect> effects = definition(variant).attackEffects();
        if (effects.size() != 1) {
            throw new AssertionError("Ammo variant " + variant + " definition had " + effects.size()
                    + " attack effects instead of exactly one: " + effects);
        }
        assertEffect(effects, effectId(path), amplifier, duration, chance,
                "ammo variant " + variant + " definition effect");
    }

    private static EquipDefinition definition(int variant) {
        ResourceLocation item = ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "equip_ammo");
        EquipDefinition definition = EquipDataRegistry.server().byItemVariant(item, variant);
        if (definition == null) {
            throw new AssertionError("No shipped definition for ammo variant " + variant);
        }
        return definition;
    }

    private static void assertEffect(Map<ResourceLocation, ShipAttackEffect> effects, ResourceLocation id,
                                     int amplifier, int duration, int chance, String name) {
        ShipAttackEffect value = effects.get(id);
        if (value == null || value.amplifier() != amplifier || value.durationTicks() != duration
                || value.chancePercent() != chance) {
            throw new AssertionError(name + " was not " + amplifier + "/" + duration + "/" + chance
                    + " but " + value + "; whole map was " + effects);
        }
    }

    private static ResourceLocation effectId(String path) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
    }
}
