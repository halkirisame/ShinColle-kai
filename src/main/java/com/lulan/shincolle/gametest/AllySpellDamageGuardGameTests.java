package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.domain.combat.ProjectileTrailDelay;
import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.team.TeamData;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AllySpellDamageGuardGameTests {

    private AllySpellDamageGuardGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_spell_damage_from_owner_is_cancelled_without_revenge")
    public static void spellDamageFromOwnerIsCancelledWithoutRevenge(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ServerPlayer owner = player(helper, entities, "spell_guard_owner", 6501,
                    "00000000-0000-0000-0000-000000006501");
            ServerPlayer stranger = player(helper, entities, "spell_guard_control", 6502,
                    "00000000-0000-0000-0000-000000006502");
            BasicEntityShip hit = ship(helper, entities, owner, 6501, new Vec3(1D, 4D, 1D));
            BasicEntityShip bystander = ship(helper, entities, owner, 6501, new Vec3(3D, 4D, 1D));
            BasicEntityShip control = ship(helper, entities, owner, 6501, new Vec3(5D, 4D, 1D));
            hit.getAttrs().setAttrsBuffed(ID.Attrs.DODGE, 0F);
            control.getAttrs().setAttrsBuffed(ID.Attrs.DODGE, 0F);
            float health = hit.getHealth();

            helper.assertFalse(hit.hurt(spellSource(owner, "fire_magic"), 1F),
                    "The owner's spell damage entered the ship's damage pipeline");
            helper.assertTrue(hit.getHealth() == health, "The owner's spell damage lowered the ship's health");
            helper.assertTrue(hit.getEntityRevengeTarget() == null,
                    "A blocked spell hit still set a revenge target on the hit ship");
            helper.assertTrue(bystander.getEntityRevengeTarget() == null,
                    "A blocked spell hit still made the owner's other ships take a revenge target");

            control.hurt(spellSource(stranger, "fire_magic"), 1F);
            helper.assertTrue(control.getEntityRevengeTarget() == stranger,
                    "Fixture: a stranger's spell damage must still reach the ship and set its revenge target");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_cancelled_non_spell_attack_still_propagates_revenge")
    public static void cancelledNonSpellAttackStillPropagatesRevenge(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ServerPlayer owner = player(helper, entities, "spell_guard_cancel_owner", 6531,
                    "00000000-0000-0000-0000-000000006531");
            BasicEntityShip hit = ship(helper, entities, owner, 6531, new Vec3(1D, 4D, 1D));
            BasicEntityShip bystander = ship(helper, entities, owner, 6531, new Vec3(3D, 4D, 1D));

            LivingAttackEvent event = new LivingAttackEvent(hit, owner.damageSources().playerAttack(owner), 1F);
            event.setCanceled(true);
            MinecraftForge.EVENT_BUS.post(event);
            helper.assertTrue(bystander.getEntityRevengeTarget() == hit,
                    "A cancelled attack that is not spell damage no longer propagates revenge");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_spell_damage_from_ally_is_cancelled_but_stranger_is_not")
    public static void spellDamageFromAllyIsCancelledButStrangerIsNot(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ServerPlayer owner = player(helper, entities, "spell_guard_ship_owner", 6511,
                    "00000000-0000-0000-0000-000000006511");
            ServerPlayer ally = player(helper, entities, "spell_guard_ally", 6512,
                    "00000000-0000-0000-0000-000000006512");
            ServerPlayer stranger = player(helper, entities, "spell_guard_stranger", 6513,
                    "00000000-0000-0000-0000-000000006513");
            ServerDataManager.setTeamData(new TeamData(6511, "SpellGuardOwner", "spell_guard_owner"));
            ServerDataManager.setTeamData(new TeamData(6512, "SpellGuardAlly", "spell_guard_ally"));
            ServerDataManager.setTeamData(new TeamData(6513, "SpellGuardStranger", "spell_guard_stranger"));
            ServerDataManager.teamAddAlly(6511, 6512);
            BasicEntityShip ship = ship(helper, entities, owner, 6511, new Vec3(1D, 4D, 1D));

            helper.assertTrue(attack(ship, spellSource(ally, "lightning_magic")).isCanceled(),
                    "An ally's spell damage was not cancelled for the ship");
            helper.assertFalse(attack(ship, spellSource(stranger, "lightning_magic")).isCanceled(),
                    "A stranger's spell damage was cancelled; the guard must only protect owners and allies");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_non_spell_damage_is_not_cancelled_by_spell_guard")
    public static void nonSpellDamageIsNotCancelledBySpellGuard(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ServerPlayer owner = player(helper, entities, "spell_guard_melee_owner", 6521,
                    "00000000-0000-0000-0000-000000006521");
            BasicEntityShip ship = ship(helper, entities, owner, 6521, new Vec3(1D, 4D, 1D));

            helper.assertFalse(attack(ship, owner.damageSources().playerAttack(owner)).isCanceled(),
                    "The spell guard cancelled damage that is not from a spell mod");
            helper.assertFalse(attack(ship, spellSource(owner, "fire_magic", "another_mod")).isCanceled(),
                    "The spell guard cancelled damage from an unrelated namespace");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_blocked_spell_leaves_no_counter_effect_on_the_caster")
    public static void blockedSpellLeavesNoCounterEffectOnTheCaster(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ServerPlayer owner = player(helper, entities, "spell_guard_slow_owner", 6541,
                    "00000000-0000-0000-0000-000000006541");
            ServerPlayer ally = player(helper, entities, "spell_guard_slow_ally", 6542,
                    "00000000-0000-0000-0000-000000006542");
            ServerPlayer stranger = player(helper, entities, "spell_guard_slow_stranger", 6543,
                    "00000000-0000-0000-0000-000000006543");
            ServerDataManager.setTeamData(new TeamData(6541, "SpellGuardSlowOwner", "spell_guard_slow_owner"));
            ServerDataManager.setTeamData(new TeamData(6542, "SpellGuardSlowAlly", "spell_guard_slow_ally"));
            ServerDataManager.teamAddAlly(6541, 6542);
            BasicEntityShip atago = ship(helper, entities, ModEntities.CA_ATAGO.get(), owner, 6541,
                    new Vec3(1D, 4D, 1D));
            BasicEntityShip takao = ship(helper, entities, ModEntities.CA_TAKAO.get(), owner, 6541,
                    new Vec3(3D, 4D, 1D));

            atago.hurt(spellSource(ally, "fire_magic"), 1F);
            takao.hurt(spellSource(ally, "fire_magic"), 1F);
            helper.assertFalse(ally.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "A blocked spell hit still slowed the allied caster");

            atago.hurt(spellSource(stranger, "fire_magic"), 1F);
            helper.assertTrue(stranger.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "Fixture: a stranger's spell must still be answered with the slowdown");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_blocked_spell_keeps_a_riding_hime_on_the_owner")
    public static void blockedSpellKeepsARidingHimeOnTheOwner(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ServerPlayer owner = player(helper, entities, "spell_guard_hime_owner", 6551,
                    "00000000-0000-0000-0000-000000006551");
            ServerPlayer stranger = player(helper, entities, "spell_guard_hime_stranger", 6552,
                    "00000000-0000-0000-0000-000000006552");
            BasicEntityShip hime = ship(helper, entities, ModEntities.NORTHERN_HIME.get(), owner, 6551,
                    new Vec3(1D, 4D, 1D));
            BasicEntityShip ssnh = ship(helper, entities, ModEntities.SSNH.get(), owner, 6551,
                    new Vec3(3D, 4D, 1D));
            helper.assertTrue(hime.startRiding(owner, true) && ssnh.startRiding(owner, true),
                    "Fixture: the ships could not ride their owner");

            hime.hurt(spellSource(owner, "fire_magic"), 1F);
            ssnh.hurt(spellSource(owner, "fire_magic"), 1F);
            helper.assertTrue(hime.getVehicle() == owner && ssnh.getVehicle() == owner,
                    "A blocked spell hit still made a riding ship dismount");

            hime.hurt(spellSource(stranger, "fire_magic"), 1F);
            helper.assertTrue(hime.getVehicle() == null,
                    "Fixture: a stranger's spell must still make a riding ship dismount");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft",
            batch = "isolated_blocked_spell_does_not_detonate_an_owned_missile")
    public static void blockedSpellDoesNotDetonateAnOwnedMissile(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            ServerPlayer owner = player(helper, entities, "spell_guard_missile_owner", 6561,
                    "00000000-0000-0000-0000-000000006561");
            BasicEntityShip host = ship(helper, entities, owner, 6561, new Vec3(1D, 4D, 1D));
            EntityAbyssMissile shielded = missile(helper, entities, host, new Vec3(3D, 4D, 1D));
            EntityAbyssMissile control = missile(helper, entities, host, new Vec3(5D, 4D, 1D));

            helper.assertFalse(shielded.hurt(spellSource(owner, "fire_magic"), 10F),
                    "The owner's spell damage entered the missile's damage pipeline");
            helper.assertFalse(shielded.isRemoved(), "The owner's spell detonated the owner's missile");

            helper.assertTrue(control.hurt(owner.damageSources().magic(), 10F),
                    "Fixture: magic damage must still detonate the missile");
            helper.assertTrue(control.isRemoved(),
                    "Fixture: a detonated missile must be removed, or the assertion above proves nothing");
            helper.succeed();
        }
    }

    private static EntityAbyssMissile missile(GameTestHelper helper, GameTestEntities entities,
                                              BasicEntityShip host, Vec3 relativePosition) {
        Vec3 position = helper.absoluteVec(relativePosition);
        EntityAbyssMissile missile = entities.add(EntityAbyssMissile.createBuiltInLaunch(helper.getLevel(), host,
                0, 0, 5F, 0.15F, position, position.x + 20D, (float) position.y, position.z, 140, 0.25F,
                0.5F, 0F, 0F, ProjectileTrailDelay.NONE));
        if (!helper.getLevel().addFreshEntity(missile)) {
            throw new AssertionError("Failed to add missile");
        }
        return missile;
    }

    private static LivingAttackEvent attack(Entity target, DamageSource source) {
        LivingAttackEvent event = new LivingAttackEvent(
                (net.minecraft.world.entity.LivingEntity) target, source, 1F);
        MinecraftForge.EVENT_BUS.post(event);
        return event;
    }

    private static DamageSource spellSource(Entity caster, String path) {
        return spellSource(caster, path, "irons_spellbooks");
    }

    private static DamageSource spellSource(Entity caster, String path, String namespace) {
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE,
                new ResourceLocation(namespace, path));
        return new DamageSource(new KeyedHolder(key, new DamageType(path, 0F)), caster, caster);
    }

    private static ServerPlayer player(GameTestHelper helper, GameTestEntities entities,
                                       String name, int uid, String uuid) {
        ServerPlayer player = entities.add(FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.fromString(uuid), name)));
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElseThrow(
                () -> new AssertionError("Test player has no admiral capability"));
        capa.setPlayerUID(uid);
        player.getAbilities().invulnerable = false;
        player.moveTo(helper.absoluteVec(new Vec3(0.5D, 4D, 0.5D)));
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    private static BasicEntityShip ship(GameTestHelper helper, GameTestEntities entities,
                                        ServerPlayer owner, int uid, Vec3 relativePosition) {
        return ship(helper, entities, ModEntities.BB_KONGOU.get(), owner, uid, relativePosition);
    }

    private static BasicEntityShip ship(GameTestHelper helper, GameTestEntities entities,
                                        EntityType<? extends BasicEntityShip> type,
                                        ServerPlayer owner, int uid, Vec3 relativePosition) {
        Entity entity = entities.add(type.create(helper.getLevel()));
        if (!(entity instanceof BasicEntityShip ship)) {
            throw new AssertionError("Failed to create friendly ship");
        }
        ship.setNoAi(true);
        ship.setPlayerUID(uid);
        ship.setOwnerUUID(owner.getUUID());
        Vec3 position = helper.absoluteVec(relativePosition);
        ship.moveTo(position.x, position.y, position.z);
        if (!helper.getLevel().addFreshEntity(ship)) {
            throw new AssertionError("Failed to add friendly ship");
        }
        return ship;
    }

    /** A damage type holder that reports a registry key, which a built DamageType alone does not. */
    private static final class KeyedHolder implements Holder<DamageType> {
        private final ResourceKey<DamageType> key;
        private final DamageType value;

        private KeyedHolder(ResourceKey<DamageType> key, DamageType value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public DamageType value() {
            return this.value;
        }

        @Override
        public boolean isBound() {
            return true;
        }

        @Override
        public boolean is(ResourceLocation id) {
            return this.key.location().equals(id);
        }

        @Override
        public boolean is(ResourceKey<DamageType> other) {
            return this.key == other || this.key.equals(other);
        }

        @Override
        public boolean is(Predicate<ResourceKey<DamageType>> predicate) {
            return predicate.test(this.key);
        }

        @Override
        public boolean is(TagKey<DamageType> tag) {
            return false;
        }

        @Override
        public Stream<TagKey<DamageType>> tags() {
            return Stream.empty();
        }

        @Override
        public com.mojang.datafixers.util.Either<ResourceKey<DamageType>, DamageType> unwrap() {
            return com.mojang.datafixers.util.Either.left(this.key);
        }

        @Override
        public Optional<ResourceKey<DamageType>> unwrapKey() {
            return Optional.of(this.key);
        }

        @Override
        public Kind kind() {
            return Kind.REFERENCE;
        }

        @Override
        public boolean canSerializeIn(HolderOwner<DamageType> owner) {
            return true;
        }
    }
}
