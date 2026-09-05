package com.lulan.shincolle.entity;

import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.item.EquipAmmo;
import com.lulan.shincolle.item.IShipEffectItem;
import com.lulan.shincolle.reference.Enums.EnumEquipEffectSP;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.unitclass.MissileData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * Internal compatibility adapter for the non-attribute behavior of legacy {@link BasicEquip} items.
 *
 * <p>Legacy numeric IDs survive only at the old enchanted-shell NBT boundary. Runtime values use the
 * same ResourceLocation-based immutable type as the canonical equipment API.</p>
 */
public final class LegacyBasicEquipEffects {

    private static final int MISSILE_VARIANTS = 5;

    private LegacyBasicEquipEffects() {
    }

    /**
     * Applies all legacy BasicEquip-only effects after a stack has successfully resolved.
     *
     * <p>This is the 1.20.1 equivalent of the non-attribute portion of the original
     * {@code EquipCalc#calcEquipAttrs}: special state, attack effects, and missile data.</p>
     */
    public static void apply(BasicEntityShip ship, ItemStack stack) {
        if (ship == null || stack == null || stack.isEmpty()
                || !(stack.getItem() instanceof BasicEquip equipItem)) {
            return;
        }

        applySpecialEffect(ship, equipItem, stack);
        int meta = BasicEquip.getEquipMeta(stack);
        applyAttackEffects(ship.getAttackEffectMap(), stack, meta);
        if (stack.getItem() instanceof IShipEffectItem effectItem) {
            applyMissileEffects(ship, effectItem, meta);
        }
    }

    private static void applySpecialEffect(BasicEntityShip ship, BasicEquip equipItem, ItemStack stack) {
        EnumEquipEffectSP effect = equipItem.getSpecialEffect(stack);
        switch (effect) {
            case DRUM:
            case DRUM_LIQUID:
            case DRUM_EU:
                ship.setStateMinor(ID.M.DrumState, ship.getStateMinor(ID.M.DrumState) + 1);
                break;
            case COMPASS:
                ship.setStateMinor(ID.M.LevelChunkLoader, ship.getStateMinor(ID.M.LevelChunkLoader) + 1);
                break;
            case FLARE:
                ship.setStateMinor(ID.M.LevelFlare, ship.getStateMinor(ID.M.LevelFlare) + 1);
                break;
            case SEARCHLIGHT:
                ship.setStateMinor(ID.M.LevelSearchlight, ship.getStateMinor(ID.M.LevelSearchlight) + 1);
                break;
            default:
                break;
        }
    }

    /**
     * Adds legacy attack effects while detaching every stored array from its item or NBT source.
     *
     * <p>Package visibility is intentional: this is a focused test seam, not canonical API.</p>
     */
    static void applyAttackEffects(Map<ResourceLocation, ShipAttackEffect> target, ItemStack stack, int meta) {
        if (target == null || stack == null) {
            return;
        }

        if (meta == 7 && stack.getItem() == ModItems.EQUIP_AMMO.get()) {
            addEnchantShellEffects(target, stack);
        }
    }

    private static void addEnchantShellEffects(Map<ResourceLocation, ShipAttackEffect> target, ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }

        ListTag effects = tag.getList(EquipAmmo.PLIST, Tag.TAG_COMPOUND);
        for (int index = 0; index < effects.size(); index++) {
            CompoundTag effect = effects.getCompound(index);
            MobEffect mobEffect = MobEffect.byId(effect.getInt(EquipAmmo.PID));
            ResourceLocation effectId = mobEffect == null ? null : ForgeRegistries.MOB_EFFECTS.getKey(mobEffect);
            if (effectId != null) {
                target.put(effectId, new ShipAttackEffect(effectId,
                        effect.getInt(EquipAmmo.PLEVEL), effect.getInt(EquipAmmo.PTIME),
                        effect.getInt(EquipAmmo.PCHANCE)));
            }
        }
    }

    private static void applyMissileEffects(BasicEntityShip ship, IShipEffectItem effectItem, int meta) {
        int missileType = effectItem.getMissileType(meta);
        if (missileType > 0) {
            forEachMissileData(ship, data -> data.type = missileType);
        }

        int moveType = effectItem.getMissileMoveType(meta);
        if (moveType >= 0) {
            forEachMissileData(ship, data -> data.movetype = moveType);
        }

        int speedLevel = effectItem.getMissileSpeedLevel(meta);
        if (speedLevel != 0) {
            float addSpeed = speedLevel * 0.025F;
            float addAcceleration = speedLevel * 0.004F;
            forEachMissileData(ship, data -> {
                data.vel0 += addSpeed;
                data.accY1 += addAcceleration;
                data.accY2 = data.accY1;
            });
        }
    }

    private static void forEachMissileData(BasicEntityShip ship, MissileDataConsumer action) {
        for (int index = 0; index < MISSILE_VARIANTS; index++) {
            action.accept(ship.getMissileData(index));
        }
    }

    @FunctionalInterface
    private interface MissileDataConsumer {
        void accept(MissileData data);
    }
}
