package com.lulan.shincolle.item;

import com.google.gson.JsonParser;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.GenericHostileShipEntity;
import com.lulan.shincolle.entity.GenericShipEntity;
import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.gametest.GameTestEntities;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.network.S2CEntitySyncPacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.shipdata.GenericShipSpawner;
import com.lulan.shincolle.shipdata.HostileVariants;
import com.lulan.shincolle.shipdata.ShipDefinition;
import com.lulan.shincolle.shipdata.ShipDefinitionId;
import com.lulan.shincolle.shipdata.ShipDefinitionRegistry;
import com.lulan.shincolle.shipdata.ShipDefinitionValidator;
import com.lulan.shincolle.shipdata.ShipIndividual;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecruitIndividualGameTests {
    private static final ShipDefinitionId SHIP = ShipDefinitionId.parse("shincolle_test:recruit");
    private static final Vec3 POSITION = new Vec3(2, 24, 2);
    private static final String DEFINITION = """
            {"schema":1,"display_name":"Recruit Test","ship_type":"shincolle_kai:destroyer",
            "visual":"shincolle_test:visual","base_stats":{"hp":80,"atk":12,"def":0.2},
            "growth":{"hp":2,"atk":1.2},"hostile_modifiers":{"hp":2,"atk":2},
            "hostile_variants":{"normal_weight":0,"variants":[{"id":"flagship","weight":1,
            "name":"variant.test.flagship","rarity":"epic","traits":["shincolle_kai:sturdy","shincolle_kai:heavy_hitter"],
            "hostile_modifiers":{"hp":2,"atk":2},"visual_variant":"addon:textures/entity/flagship.png","red_aura":true}]}}
            """;

    private RecruitIndividualGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_recruit_individual")
    public static void actualHostileDeathEggRecruitsLevelOneOwnerAndOnlyAllowlistedIdentity(GameTestHelper helper) {
        fixture(helper, f -> {
            GenericHostileShipEntity enemy = f.enemy();
            enemy.initAttrs(3);
            enemy.selectVariant(definition().hostileVariants().get("flagship"));
            enemy.setCustomName(Component.literal("Do not inherit manual enemy name"));
            enemy.setStateMinor(ID.M.ShipLevel, 99);
            enemy.setStateMinor(ID.M.PlayerUID, 123456);
            enemy.setHealth(1);
            helper.assertTrue(enemy.getScaleLevel() == 3 && enemy.getHealth() == 1, "Hostile fixture ignored");
            helper.assertTrue(enemy.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE)
                    && enemy.isDeadOrDying(), "Hostile was not killed");
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, f.area(),
                    item -> item.getItem().is(ModItems.GENERIC_SHIP_SPAWN_EGG.get()));
            drops.forEach(f.entities::add);
            helper.assertTrue(drops.size() == 1 && !enemy.getStateFlag(ID.F.CanDrop), "Not exactly one immediate death egg");
            ItemStack egg = drops.get(0).getItem().copy();
            helper.assertTrue(egg.getTag().getAllKeys().equals(Set.of("ShipId", "Hostile", ShipIndividual.KEY)),
                    "Recruitment payload missing, or hostile state leaked into egg");
            CompoundTag identity = egg.getTag().getCompound(ShipIndividual.KEY);
            helper.assertTrue(identity.getAllKeys().equals(Set.of("V", "Rolled", "Variant", "Name", "Rarity", "Traits", "Visual", "RedAura"))
                    && !identity.getBoolean("RedAura"), "Recruitment allowlist or aura incorrect");
            CompoundTag forbidden = new CompoundTag();
            forbidden.putInt("ShipLevel", 100);
            forbidden.putString("CustomName", "\"Forbidden\"");
            egg.getTag().put("EntityTag", forbidden);
            GenericShipEntity ally = f.useEgg(egg);
            helper.assertTrue(ally.getLevel() == 1 && ally.getOwner() == f.owner && ally.isTame(), "Fresh level or owner lost");
            helper.assertTrue(ally.individual().save().equals(identity), "Identity lost in recruitment");
            helper.assertTrue(ally.getCustomName() == null && ally.getHealth() == ally.getMaxHealth(), "Fresh HP/name incorrect");
            GenericShipEntity normal = f.ally();
            close(helper, ally.getAttrs().getAttrsRaw(ID.Attrs.HP), normal.getAttrs().getAttrsRaw(ID.Attrs.HP) * 1.1F,
                    "Hostile HP modifiers carried over, or trait missing");
            close(helper, ally.getAttrs().getAttrsRaw(ID.Attrs.ATK_H), normal.getAttrs().getAttrsRaw(ID.Attrs.ATK_H) * 1.1F,
                    "Hostile attack modifiers carried over, or trait missing");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_recruit_individual")
    public static void normalEggShapeAndIndividualDropOddsMatchBuiltInAtEveryScale(GameTestHelper helper) {
        fixture(helper, f -> {
            GenericHostileShipEntity enemy = f.enemy();
            enemy.initAttrs(3);
            helper.assertTrue(enemy.getDropEgg().getTag().equals(GenericShipSpawnEgg.create(SHIP, false).getTag()),
                    "Normal egg shape changed");
            enemy.selectVariant(definition().hostileVariants().get("flagship"));
            var builtIn = f.entities.add(ModEntities.DESTROYER_AKATSUKI_MOB.get().create(helper.getLevel()));
            for (int scale = 0; scale < 4; scale++) {
                enemy.initAttrs(scale);
                builtIn.initAttrs(scale);
                enemy.getRandom().setSeed(731);
                builtIn.getRandom().setSeed(731);
                for (int i = 0; i < 256; i++) {
                    helper.assertTrue(enemy.getDropEgg().isEmpty() == builtIn.getDropEgg().isEmpty(),
                            "Individual changed drop odds at scale " + scale);
                }
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_recruit_individual")
    public static void allFriendlyTraitColumnsRebuildWithoutHealingOrEncounterModifiers(GameTestHelper helper) {
        fixture(helper, f -> {
            GenericShipEntity normal = f.ally();
            GenericShipEntity ally = f.ally();
            int[] raw = {ID.Attrs.HP, ID.Attrs.ATK_L, ID.Attrs.DEF, ID.Attrs.SPD, ID.Attrs.MOV, ID.Attrs.HIT};
            String[] traits = {"sturdy", "heavy_hitter", "armored", "quick_loader", "swift", "keen_eye"};
            for (int column = 0; column < traits.length; column++) {
                CompoundTag identity = selected();
                ListTag list = new ListTag();
                list.add(StringTag.valueOf("shincolle_kai:" + traits[column]));
                identity.put("Traits", list);
                ally.readIndividual(wrap(identity));
                ally.setHealth(3);
                helper.assertTrue(ally.getHealth() == 3, "Health fixture ignored");
                for (int i = 0; i < 3; i++) {
                    ally.calcShipAttributes(31, false);
                    for (int j = 0; j < raw.length; j++) close(helper, ally.getAttrs().getAttrsRaw(raw[j]),
                            normal.getAttrs().getAttrsRaw(raw[j]) * (column == j ? 1.1F : 1), "Trait column " + j);
                    for (int attack : new int[]{ID.Attrs.ATK_H, ID.Attrs.ATK_AL, ID.Attrs.ATK_AH}) {
                        close(helper, ally.getAttrs().getAttrsRaw(attack), normal.getAttrs().getAttrsRaw(attack)
                                * (column == 1 ? 1.1F : 1), "Attack channel lost trait");
                    }
                    helper.assertTrue(ally.getHealth() == 3, "Recalculation healed ship");
                }
            }
            ally.readIndividual(wrap(selected()));
            ally.calcShipAttributes(31, false);
            CompoundTag saved = ally.saveWithoutId(new CompoundTag());
            GenericShipEntity loaded = f.entities.add(ModEntities.GENERIC_SHIP.get().create(helper.getLevel()));
            loaded.load(saved);
            helper.assertTrue(loaded.getHealth() == 3, "Load healed ship");
            publish(DEFINITION.replace("\"hp\":2,\"atk\":2", "\"hp\":1,\"atk\":1"));
            loaded.tickCount = 1;
            loaded.tick();
            helper.assertTrue(loaded.getHealth() == 3, "Reload healed ship");
            close(helper, loaded.getAttrs().getAttrsRaw(ID.Attrs.HP), normal.getAttrs().getAttrsRaw(ID.Attrs.HP) * 1.1F,
                    "Reload used encounter modifiers");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_recruit_individual")
    public static void friendlyIdentitySurvivesDormancyLoadRecoveryAndDimensionCopy(GameTestHelper helper) {
        fixture(helper, f -> {
            GenericShipEntity ally = f.useEgg(f.egg(selected()));
            CompoundTag original = ally.individual().save();
            ally.setHealth(3);
            reload(Map.of());
            ally.tickCount = 1;
            ally.tick();
            helper.assertTrue(ally.definitionState().dormant(), "Missing definition did not sleep");
            CompoundTag sleeping = ally.saveWithoutId(new CompoundTag());
            helper.assertTrue(sleeping.getCompound(ShipIndividual.KEY).equals(original), "Dormant save lost identity");
            GenericShipEntity loaded = f.entities.add(ModEntities.GENERIC_SHIP.get().create(helper.getLevel()));
            loaded.load(sleeping);
            helper.assertTrue(loaded.saveWithoutId(new CompoundTag()).getCompound(ShipIndividual.KEY).equals(original),
                    "Dormant round trip lost identity");
            publish(DEFINITION);
            loaded.tickCount = 1;
            loaded.tick();
            helper.assertTrue(!loaded.definitionState().dormant() && loaded.individual().save().equals(original)
                    && loaded.getHealth() == 3, "Recovery lost identity or healed");
            GenericShipEntity transferred = f.entities.add(ModEntities.GENERIC_SHIP.get().create(helper.getLevel()));
            transferred.restoreFrom(loaded);
            helper.assertTrue(transferred.individual().save().equals(original), "Dimension restoreFrom lost identity");
            publish(DEFINITION.replace("flagship", "replacement"));
            transferred.tickCount = 1;
            transferred.tick();
            helper.assertTrue(transferred.individual().save().equals(original), "Variant removal rewrote snapshot");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_recruit_individual")
    public static void friendlyDeathEggRestoresIndividualAlongsideTrainingInventoryAndOriginalOwner(GameTestHelper helper) {
        fixture(helper, f -> {
            GenericShipEntity ally = f.useEgg(f.egg(selected()));
            ally.setShipLevel(20, true);
            ally.getAttrs().setAttrsBonus(ID.AttrsBase.HP, (byte) 3);
            ally.getCapaShipInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND, 2));
            ally.setCustomName(Component.literal("Saved manual name"));
            CompoundTag identity = ally.individual().save();
            ally.deathTime = ConfigHandler.deathTime() - 1;
            invokeDeath(ally);
            var drops = helper.getLevel().getEntitiesOfClass(BasicEntityItem.class, f.area(),
                    item -> item.getEntityItem().is(ModItems.GENERIC_SHIP_SPAWN_EGG.get()));
            drops.forEach(f.entities::add);
            helper.assertTrue(ally.isRemoved() && drops.size() == 1, "Death did not create saved egg");
            ItemStack egg = drops.get(0).getEntityItem().copy();
            helper.assertTrue(egg.getTag().contains("StateMinor") && egg.getTag().getCompound(ShipIndividual.KEY).equals(identity),
                    "Death egg lost saved values or individual");
            f.owner.getAbilities().instabuild = true;
            GenericShipEntity restored = f.useEgg(egg);
            helper.assertTrue(restored.getLevel() == 20 && restored.getOwner() == f.owner
                    && restored.getAttrs().getAttrsBonus(ID.AttrsBase.HP) == 3, "Saved training or owner lost");
            helper.assertTrue(restored.getCapaShipInventory().getStackInSlot(0).is(Items.DIAMOND)
                    && restored.getCapaShipInventory().getStackInSlot(0).getCount() == 2, "Saved inventory lost");
            helper.assertTrue(restored.individual().save().equals(identity)
                    && restored.getName().getString().equals("Saved manual name"), "Saved identity/manual name lost");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_recruit_individual")
    public static void transportNetAndTalismanRoundTripsKeepIndividualAndWoundedHealth(GameTestHelper helper) {
        fixture(helper, f -> {
            GenericShipEntity ally = f.useEgg(f.egg(selected()));
            ally.setHealth(3);
            CompoundTag identity = ally.individual().save();
            CompoundTag stored = ShipTransport.save(f.owner, ally);
            helper.assertTrue(stored.getCompound("EntityData").getCompound(ShipIndividual.KEY).equals(identity), "Transport save lost identity");
            ally.discard();
            helper.assertTrue(ShipTransport.release(f.owner, helper.getLevel(), stored,
                    List.of(f.destination()), false) == null, "Transport release failed");
            GenericShipEntity direct = f.findNew(List.of(ally));
            helper.assertTrue(direct.individual().save().equals(identity) && direct.getHealth() == 3, "Transport restore lost identity/HP");
            f.owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SAFARI_NET.get()));
            direct.mobInteract(f.owner, InteractionHand.MAIN_HAND);
            ItemStack net = f.owner.getMainHandItem();
            helper.assertTrue(direct.isRemoved() && SafariNetItem.isFilled(net), "Net capture failed");
            helper.assertTrue(net.useOn(f.context()).consumesAction() && !SafariNetItem.isFilled(net), "Net release failed");
            GenericShipEntity fromNet = f.findNew(List.of(ally, direct));
            helper.assertTrue(fromNet.individual().save().equals(identity) && fromNet.getHealth() == 3, "Net lost identity/HP");
            f.owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SOUL_TALISMAN.get()));
            fromNet.mobInteract(f.owner, InteractionHand.MAIN_HAND);
            ItemStack talisman = f.owner.getMainHandItem();
            helper.assertTrue(fromNet.isRemoved() && SoulTalismanItem.getStoredCount(talisman) == 1, "Talisman capture failed");
            SoulTalismanItem.setMode(talisman, SoulTalismanItem.MODE_RELEASE_NEXT);
            helper.assertTrue(talisman.useOn(f.context()).consumesAction() && !SoulTalismanItem.isFilled(talisman), "Talisman release failed");
            GenericShipEntity fromTalisman = f.findNew(List.of(ally, direct, fromNet));
            helper.assertTrue(fromTalisman.individual().save().equals(identity) && fromTalisman.getHealth() == 3, "Talisman lost identity/HP");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_recruit_individual")
    public static void invalidAndUnknownShipEggsRemainUnconsumedAndExplainFailure(GameTestHelper helper) {
        fixture(helper, f -> {
            for (String id : List.of("Bad Id", "addon:missing_ship")) {
                ItemStack egg = f.egg(selected());
                egg.getTag().putString("ShipId", id);
                f.owner.setItemInHand(InteractionHand.MAIN_HAND, egg);
                ItemStack before = egg.copy();
                helper.assertTrue(egg.useOn(f.context()) == InteractionResult.FAIL && ItemStack.matches(before, egg),
                        "Unusable egg was consumed or changed");
                helper.assertTrue(f.owner.message != null && f.owner.message.getContents() instanceof TranslatableContents text
                        && text.getKey().equals("message.shincolle_kai.ship_egg." + (id.equals("Bad Id") ? "invalid_id" : "unknown_ship")),
                        "Egg did not explain failure");
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_recruit_individual")
    public static void joinCancellationObservesCompleteIndividualButConsumesNothingAndLeavesNoAlly(GameTestHelper helper) {
        fixture(helper, f -> {
            ItemStack egg = f.egg(selected());
            f.owner.setItemInHand(InteractionHand.MAIN_HAND, egg);
            ItemStack before = egg.copy();
            List<GenericShipEntity> attempts = new ArrayList<>();
            Consumer<EntityJoinLevelEvent> cancel = event -> {
                if (event.getEntity() instanceof GenericShipEntity ship && ship.getOwnerUUID().equals(f.owner.getUUID())) {
                    attempts.add(ship);
                    event.setCanceled(true);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(cancel);
            try {
                helper.assertTrue(egg.useOn(f.context()) == InteractionResult.FAIL && ItemStack.matches(before, egg), "Rejected join consumed egg");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(cancel);
            }
            helper.assertTrue(attempts.size() == 1, "Did not reach world-add boundary");
            GenericShipEntity rejected = attempts.get(0);
            helper.assertTrue(rejected.individual().variant().equals("flagship") && !rejected.individualPresentation().redAura()
                    && rejected.getHealth() == rejected.getMaxHealth(), "Individual was not fully applied before join");
            helper.assertTrue(rejected.isRemoved() && helper.getLevel().getEntitiesOfClass(GenericShipEntity.class, f.area()).isEmpty(),
                    "Rejected join left a partial ally");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_recruit_individual")
    public static void unknownTraitsFutureDataAndMalformedFieldsUseSharedValidationAndInactiveTooltip(GameTestHelper helper) {
        fixture(helper, f -> {
            CompoundTag identity = selected();
            identity.putString("Variant", "missing_variant");
            ListTag traits = new ListTag();
            traits.add(StringTag.valueOf("addon:missing_trait"));
            identity.put("Traits", traits);
            GenericShipEntity normal = f.ally();
            ItemStack egg = f.egg(identity);
            List<Component> tooltip = new ArrayList<>();
            egg.getItem().appendHoverText(egg, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
            helper.assertTrue(tooltip.stream().anyMatch(c -> c.getContents() instanceof TranslatableContents text
                    && text.getKey().equals("tooltip.shincolle_kai.ship_egg.inactive_trait")), "Unknown trait not shown inactive");
            GenericShipEntity ally = f.useEgg(egg);
            close(helper, ally.getAttrs().getAttrsRaw(ID.Attrs.HP), normal.getAttrs().getAttrsRaw(ID.Attrs.HP), "Unknown trait applied effect");
            identity.putBoolean("RedAura", false);
            helper.assertTrue(ally.individual().save().equals(identity), "Unknown IDs discarded");
            identity.putInt("V", 99);
            identity.putString("Opaque", "preserve");
            GenericShipEntity future = f.useEgg(f.egg(identity));
            helper.assertTrue(future.individualPresentation().equals(ShipIndividual.Presentation.NORMAL)
                    && future.saveWithoutId(new CompoundTag()).getCompound(ShipIndividual.KEY).equals(identity), "Future save not inert/lossless");
            close(helper, future.getAttrs().getAttrsRaw(ID.Attrs.HP), normal.getAttrs().getAttrsRaw(ID.Attrs.HP), "Future trait applied effect");
            identity.putInt("V", 1);
            identity.putInt("Name", 42);
            identity.putString("Visual", "invalid visual");
            GenericShipEntity sanitized = f.useEgg(f.egg(identity));
            helper.assertTrue(sanitized.individualPresentation().name().isEmpty() && sanitized.individualPresentation().visual().isEmpty()
                    && sanitized.individual().traits().size() == 1, "Malformed fields erased valid siblings");
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_recruit_individual")
    public static void friendlyNamePacketAndTooltipExposeOnlyExpectedPresentation(GameTestHelper helper) {
        fixture(helper, f -> {
            GenericShipEntity ally = f.useEgg(f.egg(selected()));
            helper.assertTrue(ally.getName().getContents() instanceof TranslatableContents text
                    && text.getKey().equals("variant.test.flagship") && ally.getName().getString().equals("Recruit Test"),
                    "Individual name or missing-translation fallback lost");
            ally.setCustomName(Component.literal("Manual"));
            helper.assertTrue(ally.getName().getString().equals("Manual"), "Manual name did not win");
            ally.setCustomName(null);
            helper.assertTrue(ally.getName().getContents() instanceof TranslatableContents, "Clearing manual name lost individual name");
            FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
            try {
                S2CEntitySyncPacket.syncShipDefinition(ally).encode(wire);
                helper.assertTrue(wire.readByte() == S2CEntitySyncPacket.SyncShip_Definition
                        && wire.readInt() == ally.getId(), "Wrong packet envelope");
                FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.wrappedBuffer(wire.readByteArray(wire.readVarInt())));
                try {
                    helper.assertTrue(data.readUtf(64).equals(SHIP.toString()) && data.readVarLong() == ShipDefinitionRegistry.server().generation()
                            && !data.readBoolean(), "Identity metadata lost");
                    helper.assertTrue(data.readUtf(128).equals("variant.test.flagship")
                            && data.readEnum(HostileVariants.Rarity.class) == HostileVariants.Rarity.EPIC
                            && data.readUtf(128).equals("addon:textures/entity/flagship.png") && !data.readBoolean() && !data.isReadable(),
                            "Friendly presentation missing, aura enabled, or private IDs leaked");
                } finally {
                    data.release();
                }
            } finally {
                wire.release();
            }
            ItemStack egg = f.egg(selected());
            List<Component> tooltip = new ArrayList<>();
            egg.getItem().appendHoverText(egg, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
            helper.assertTrue(tooltip.size() == 4 && tooltip.get(0).getContents() instanceof TranslatableContents name
                    && name.getKey().equals("variant.test.flagship"), "Individual tooltip incomplete");
            tooltip.clear();
            ItemStack plain = GenericShipSpawnEgg.create(SHIP, false);
            plain.getItem().appendHoverText(plain, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
            helper.assertTrue(tooltip.isEmpty(), "Normal egg tooltip changed");
        });
    }

    private static void fixture(GameTestHelper helper, Consumer<Fixture> check) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            Map<ShipDefinitionId, ShipDefinition> before = ShipDefinitionRegistry.server().all();
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                publish(DEFINITION);
                RecordingPlayer owner = new RecordingPlayer(helper.getLevel());
                helper.getLevel().addNewPlayer(owner);
                try {
                    owner.moveTo(helper.absoluteVec(POSITION));
                    check.accept(new Fixture(helper, entities, owner));
                    helper.succeed();
                } finally {
                    helper.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
                }
            } finally {
                reload(before);
            }
        }, POSITION);
    }

    private static void publish(String json) {
        ShipDefinition value = new ShipDefinitionValidator().validate(SHIP, JsonParser.parseString(json).getAsJsonObject(), ignored -> { });
        reload(Map.of(SHIP, value));
    }

    private static void reload(Map<ShipDefinitionId, ShipDefinition> values) {
        try {
            Method method = ShipDefinitionRegistry.class.getDeclaredMethod("reload", java.util.function.Supplier.class);
            method.setAccessible(true);
            method.invoke(ShipDefinitionRegistry.server(), (java.util.function.Supplier<Map<ShipDefinitionId, ShipDefinition>>) () -> values);
        } catch (ReflectiveOperationException error) {
            throw new GameTestAssertException("Definition fixture failed: " + error);
        }
    }

    private static ShipDefinition definition() { return ShipDefinitionRegistry.server().get(SHIP); }
    private static CompoundTag selected() { return ShipIndividual.selected(definition().hostileVariants().get("flagship")).save(); }

    private static CompoundTag wrap(CompoundTag identity) {
        CompoundTag root = new CompoundTag();
        root.put(ShipIndividual.KEY, identity.copy());
        return root;
    }

    private static void close(GameTestHelper helper, float actual, float expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 0.001F, message + ": " + actual + " != " + expected);
    }

    private static void invokeDeath(BasicEntityShip ship) {
        try {
            Method method = BasicEntityShip.class.getDeclaredMethod("tickDeath");
            method.setAccessible(true);
            method.invoke(ship);
        } catch (ReflectiveOperationException error) {
            throw new GameTestAssertException("Death fixture failed: " + error);
        }
    }

    private record Fixture(GameTestHelper helper, GameTestEntities entities, RecordingPlayer owner) {
        AABB area() { return new AABB(helper.absoluteVec(POSITION), helper.absoluteVec(POSITION)).inflate(12); }
        BlockPos destination() { return helper.absolutePos(new BlockPos(5, 24, 2)); }
        UseOnContext context() {
            return new UseOnContext(owner, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(destination().below()), Direction.UP, destination().below(), false));
        }
        ItemStack egg(CompoundTag identity) {
            ItemStack egg = GenericShipSpawnEgg.create(SHIP, false);
            egg.getOrCreateTag().put(ShipIndividual.KEY, identity.copy());
            return egg;
        }
        GenericShipEntity ally() {
            GenericShipEntity ship = entities.add((GenericShipEntity) GenericShipSpawner.create(helper.getLevel(), SHIP, false, owner));
            ship.setNoAi(true);
            ship.setNoGravity(true);
            ship.setInvulnerable(true);
            helper.assertTrue(ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel), "Ally fuel fixture ignored");
            return ship;
        }
        GenericHostileShipEntity enemy() {
            GenericHostileShipEntity ship = entities.add((GenericHostileShipEntity) GenericShipSpawner.create(helper.getLevel(), SHIP, true, null));
            ship.setNoAi(true);
            ship.setNoGravity(true);
            ship.moveTo(helper.absoluteVec(POSITION));
            helper.assertTrue(helper.getLevel().addFreshEntity(ship), "Enemy fixture not added");
            return ship;
        }
        GenericShipEntity useEgg(ItemStack egg) {
            var before = helper.getLevel().getEntitiesOfClass(GenericShipEntity.class, area());
            owner.setItemInHand(InteractionHand.MAIN_HAND, egg);
            int count = egg.getCount();
            helper.assertTrue(egg.useOn(context()).consumesAction()
                    && egg.getCount() == count - (owner.getAbilities().instabuild ? 0 : 1), "Egg use failed or consumed incorrectly");
            return findNew(before);
        }
        GenericShipEntity findNew(List<GenericShipEntity> before) {
            var ships = helper.getLevel().getEntitiesOfClass(GenericShipEntity.class, area(), ship -> !before.contains(ship));
            ships.forEach(entities::add);
            helper.assertTrue(ships.size() == 1, "Expected one newly restored ally, found " + ships.size());
            var ship = ships.get(0);
            ship.setNoAi(true);
            ship.setNoGravity(true);
            ship.setInvulnerable(true);
            helper.assertTrue(ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel), "Restored ally fuel fixture ignored");
            return ship;
        }
    }

    private static final class RecordingPlayer extends FakePlayer {
        private Component message;
        private RecordingPlayer(ServerLevel level) { super(level, new GameProfile(UUID.randomUUID(), "RecruitOwner")); }
        @Override
        public void displayClientMessage(Component component, boolean actionBar) { message = component; }
    }
}
