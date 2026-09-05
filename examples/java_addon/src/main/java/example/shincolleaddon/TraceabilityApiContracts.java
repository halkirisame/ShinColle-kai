package example.shincolleaddon;

import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.api.attribute.ShipAttributeCombiners;
import com.lulan.shincolle.api.attribute.ShipAttributeDisplayFormat;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeRegistries;
import com.lulan.shincolle.api.attribute.ShipAttributeType;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.api.equipment.IShipEquipment;
import com.lulan.shincolle.api.equipment.ResolvedShipEquipment;
import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.api.equipment.ShipEquipmentContext;
import com.lulan.shincolle.api.ship.PlayerOwnedShip;
import com.lulan.shincolle.api.target.TargetTrait;
import com.lulan.shincolle.api.target.TargetTraits;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/** Compile-only contracts for every Public API member used by the addon documentation. */
final class TraceabilityApiContracts {

    private TraceabilityApiContracts() {
    }

    static ResolvedShipEquipment dynamicEquipmentContract(ItemStack stack, ShipAttributeLayout layout) {
        Object registryKey = ShipAttributeRegistries.REGISTRY_KEY;
        ShipEquipmentContext context = new ShipEquipmentContext(stack, layout);
        ShipAttributeType type = ShipAttributeType.builder()
                .combiner(ShipAttributeCombiners.ADDITIVE)
                .minimum(0F)
                .maximum(1F)
                .displayFormat(ShipAttributeDisplayFormat.PERCENT)
                .translationKey("ship_attribute.example.contract")
                .build();
        ShipAttributeValues values = ShipAttributeValues.builder(context.layout())
                .set(CoreShipAttributes.HIT, type.constrain(context.stack().getCount()))
                .build();
        IShipEquipment equipment = new ContractEquipment();
        equipment.resolveShipEquipment(context);
        ResourceLocation glowing = ResourceLocation.fromNamespaceAndPath("minecraft", "glowing");
        ShipAttackEffect effect = new ShipAttackEffect(glowing, 0, 100, 25);
        return new ResolvedShipEquipment(values, ResolvedShipEquipment.DEFAULT_COMPATIBILITY,
                Map.of(glowing, effect));
    }

    static void callbackAndOwnershipContracts(PlayerOwnedShip ship, Player owner,
                                              LivingEntity target, ItemStack stack) {
        IShipEquipment equipment = new ContractEquipment();
        equipment.onShipHit(owner, target, 1F, stack);
        boolean ownedByPlayer = ship.isOwnedByPlayer(owner);
    }

    static void targetTraitContract(EntityType<?> addonEntityType) {
        TargetTraits.registerTargetTrait(addonEntityType, TargetTrait.ANTI_AIR_ELIGIBLE);
    }

    private static final class ContractEquipment implements IShipEquipment {

        @Override
        public ResolvedShipEquipment resolveShipEquipment(ShipEquipmentContext context) {
            return new ResolvedShipEquipment(ShipAttributeValues.zero(context.layout()),
                    ResolvedShipEquipment.DEFAULT_COMPATIBILITY);
        }

        @Override
        public void onShipHit(LivingEntity ship, Entity target, float attackAmount, ItemStack stack) {
        }
    }
}
