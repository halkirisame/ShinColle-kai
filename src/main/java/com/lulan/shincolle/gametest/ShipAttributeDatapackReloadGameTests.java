package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.equipdata.EquipDataLoader;
import com.lulan.shincolle.equipdata.EquipDataRegistry;
import com.lulan.shincolle.equipdata.EquipDataSnapshot;
import com.lulan.shincolle.equipdata.EquipDefinition;
import com.lulan.shincolle.handler.ServerEventHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.AttrsAdv;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Regression tests for refreshing loaded ship attributes after a datapack reload.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipAttributeDatapackReloadGameTests {

    private static final int VARIANT = 12;
    private static final float RELOAD_HP_DELTA = 25F;

    private ShipAttributeDatapackReloadGameTests() {
    }

    @GameTest(template = "arena", batch = "equipment_reload_config")
    public static void reloadRecalculatesLoadedShipBeforeNextTick(GameTestHelper helper) {
        EquipDataSnapshot originalSnapshot = EquipDataRegistry.server();
        BasicEntityShip ship = createLoadedShip(helper);
        try {
            ItemStack cannon = new ItemStack(ModItems.EQUIP_CANNON.get());
            BasicEquip.setEquipMeta(cannon, VARIANT);
            ship.getCapaShipInventory().setStackInSlot(0, cannon);
            ship.calcShipAttributes(31, false);

            AttrsAdv attrs = requireAdvancedAttrs(ship);
            float oldEquipHp = attrs.getAttrsEquip(ID.Attrs.HP);
            EquipDefinition originalDefinition = originalSnapshot.byItemVariant(
                    ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "equip_cannon"), VARIANT);
            if (originalDefinition == null || oldEquipHp <= 0F) {
                throw new AssertionError("Reload fixture equipment definition is missing or has no HP bonus");
            }

            installServerSnapshot(replaceDefinition(originalSnapshot, withExtraHp(originalDefinition)));
            ServerEventHandler.onDatapackSync(new OnDatapackSyncEvent(
                    helper.getLevel().getServer().getPlayerList(), null));

            float newEquipHp = attrs.getAttrsEquip(ID.Attrs.HP);
            if (Float.compare(oldEquipHp + RELOAD_HP_DELTA, newEquipHp) != 0) {
                throw new AssertionError("Reload did not immediately recalculate loaded equipment attributes: old="
                        + oldEquipHp + " new=" + newEquipHp);
            }
            double maxHealth = Objects.requireNonNull(ship.getAttribute(Attributes.MAX_HEALTH)).getBaseValue();
            if (Double.compare(attrs.getAttrsBuffed(ID.Attrs.HP), maxHealth) != 0) {
                throw new AssertionError("Reloaded HP did not reach the Minecraft MAX_HEALTH attribute");
            }
        } finally {
            installServerSnapshot(originalSnapshot);
            ship.calcShipAttributes(31, false);
        }
        helper.succeed();
    }

    private static BasicEntityShip createLoadedShip(GameTestHelper helper) {
        Entity entity = ModEntities.BB_KONGOU.get().create(helper.getLevel());
        if (!(entity instanceof BasicEntityShip ship) || !helper.getLevel().addFreshEntity(ship)) {
            throw new AssertionError("Failed to create a loaded friendly ship for reload test");
        }
        return ship;
    }

    private static AttrsAdv requireAdvancedAttrs(BasicEntityShip ship) {
        if (!(ship.getAttrs() instanceof AttrsAdv attrs)) {
            throw new AssertionError("Reload test ship does not have AttrsAdv");
        }
        return attrs;
    }

    private static EquipDefinition withExtraHp(EquipDefinition source) {
        var stats = source.stats().toBuilder()
                .add(CoreShipAttributes.HP, RELOAD_HP_DELTA)
                .build();
        return new EquipDefinition(source.id(), source.item(), source.variant(), source.equipType(),
                source.legacyEquipId(), stats, source.compatible(), source.enchantType(),
                source.developMaterial(), source.developAmount(), source.rareMean(), source.rollType());
    }

    private static EquipDataSnapshot replaceDefinition(EquipDataSnapshot source, EquipDefinition replacement) {
        Map<ResourceLocation, EquipDefinition> byId = new HashMap<>(source.byId());
        byId.put(replacement.id(), replacement);

        Map<ResourceLocation, Map<Integer, EquipDefinition>> byItemVariant = new HashMap<>();
        source.byItemVariant().forEach((item, variants) ->
                byItemVariant.put(item, new HashMap<>(variants)));
        byItemVariant.computeIfAbsent(replacement.item(), unused -> new HashMap<>())
                .put(replacement.variant(), replacement);

        Map<Integer, EquipDefinition> byLegacyId = new HashMap<>(source.byLegacyId());
        if (replacement.legacyEquipId() != null) {
            byLegacyId.put(replacement.legacyEquipId(), replacement);
        }
        return new EquipDataSnapshot(byId, byItemVariant, byLegacyId);
    }

    private static void installServerSnapshot(EquipDataSnapshot snapshot) {
        try {
            Field field = EquipDataLoader.class.getDeclaredField("serverSnapshot");
            field.setAccessible(true);
            field.set(null, snapshot);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to install isolated equipment reload snapshot", e);
        }
    }
}
