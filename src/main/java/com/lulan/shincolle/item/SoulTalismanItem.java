package com.lulan.shincolle.item;

import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.other.BasicEntityItem;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.server.ServerDataManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reusable multi-ship transport, ordered by the admiral's current fleet slots. */
public class SoulTalismanItem extends BasicItem {
    public static final int MODE_CAPTURE_SINGLE = 0;
    public static final int MODE_CAPTURE_FLEET = 1;
    public static final int MODE_RELEASE_NEXT = 2;
    public static final int MODE_RELEASE_ALL = 3;
    public static final int MAX_NBT_BYTES = 512 * 1024;
    private static final int MAX_STORED_SHIPS = 6;
    public static final int FLEET_CAPTURE_RANGE = 64;
    public static final int BULK_RELEASE_RADIUS = 4;
    private static final String ROOT = "SoulTalisman";
    private static final String SHIPS = "Ships";

    public SoulTalismanItem() {
        super(new Properties().stacksTo(1));
    }

    private static CompoundTag contents(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getCompound(ROOT) : new CompoundTag();
    }

    private static ListTag ships(ItemStack stack) {
        return contents(stack).getList(SHIPS, Tag.TAG_COMPOUND);
    }

    public static int getMode(ItemStack stack) {
        int mode = contents(stack).getInt("Mode");
        return mode >= 0 && mode <= MODE_RELEASE_ALL ? mode : MODE_CAPTURE_SINGLE;
    }

    public static void setMode(ItemStack stack, int mode) {
        if (!(stack.getItem() instanceof SoulTalismanItem) || mode < 0 || mode > MODE_RELEASE_ALL) return;
        stack.getOrCreateTagElement(ROOT).putInt("Mode", mode);
    }

    public static int cycleMode(int mode, int direction) {
        return Math.floorMod(mode + direction, 4);
    }

    public static Component modeName(int mode) {
        return Component.translatable("tooltip.shincolle_kai.soul_talisman.mode" + mode);
    }

    public static int getStoredCount(ItemStack stack) {
        return ships(stack).size();
    }

    public static boolean isFilled(ItemStack stack) {
        return stack.getItem() instanceof SoulTalismanItem && getStoredCount(stack) > 0;
    }

    public static boolean isStoredOwner(ItemStack stack, Player player) {
        if (!isFilled(stack)) return false;
        for (Tag entry : ships(stack)) {
            if (!ShipTransport.isOwner((CompoundTag) entry, player)) return false;
        }
        return true;
    }

    public static boolean tryStoreFilledTalisman(Player player, ItemStack stack) {
        return isFilled(stack) && ShipTransport.storeInInventory(player, stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        return player.isShiftKeyDown() ? InteractionResult.PASS : capture(player, target, hand);
    }

    public InteractionResult capture(Player player, LivingEntity target, InteractionHand hand) {
        if (target.level().isClientSide()) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof SoulTalismanItem) || getMode(stack) >= MODE_RELEASE_NEXT) return InteractionResult.PASS;
        String rejection = ShipTransport.captureRejection(player, target);
        boolean fleetCapture = getMode(stack) == MODE_CAPTURE_FLEET;
        if (rejection != null && !(fleetCapture && (rejection.equals("busy") || rejection.equals("dead")))) {
            return refuse(player, rejection);
        }
        BasicEntityShip ship = (BasicEntityShip) target;
        CapaTeitoku capa = capability(player);
        int team = capa == null ? -1 : capa.findTeamOfShip(ship.getShipUID());
        if (getMode(stack) == MODE_CAPTURE_SINGLE || team < 0) {
            rejection = captureOne(stack, player, ship);
            return rejection == null ? InteractionResult.SUCCESS : refuse(player, rejection);
        }

        int captured = 0;
        int remaining = 0;
        boolean full = false;
        for (int slot = 0; slot < CapaTeitoku.SLOT_NUM; slot++) {
            if (capa.getTeamMember(team, slot) <= 0) continue;
            if (full) {
                remaining++;
                continue;
            }
            BasicEntityShip member = resolveTeamShip((ServerLevel) player.level(), capa, team, slot);
            if (member == null || member.distanceToSqr(player) > (double) FLEET_CAPTURE_RANGE * FLEET_CAPTURE_RANGE) {
                remaining++;
                continue;
            }
            rejection = captureOne(stack, player, member);
            if (rejection == null) {
                captured++;
            } else {
                remaining++;
                full = rejection.equals("full");
            }
        }
        summary(player, "capture", captured, remaining);
        return InteractionResult.SUCCESS;
    }

    private static String captureOne(ItemStack stack, Player player, BasicEntityShip ship) {
        String rejection = ShipTransport.captureRejection(player, ship);
        if (rejection != null) return rejection;
        // Never mix another owner's saves into an already filled talisman.
        if (isFilled(stack) && !isStoredOwner(stack, player)) return "not_owner";
        if (getStoredCount(stack) >= MAX_STORED_SHIPS) return "full";
        ShipTransport.detach(player, ship);
        try {
            CompoundTag stored = ShipTransport.save(player, ship);
            if (stored == null) return "save_failed";
            long order = 0;
            for (Tag entry : ships(stack)) order = Math.max(order, ((CompoundTag) entry).getLong("StoredOrder") + 1);
            stored.putLong("StoredOrder", order);
            CompoundTag candidate = stack.hasTag() ? stack.getTag().copy() : new CompoundTag();
            CompoundTag root = candidate.getCompound(ROOT);
            root.putInt("Mode", getMode(stack));
            ListTag list = root.getList(SHIPS, Tag.TAG_COMPOUND);
            list.add(stored.copy());
            root.put(SHIPS, list);
            candidate.put(ROOT, root);
            if (!fits(candidate)) return "full";
            if (!stored.equals(candidate.getCompound(ROOT).getList(SHIPS, Tag.TAG_COMPOUND).getCompound(list.size() - 1))) {
                return "save_failed";
            }
            stack.setTag(candidate);
            if (!stored.equals(ships(stack).getCompound(list.size() - 1))) return "save_failed";
        } catch (RuntimeException error) {
            return "save_failed";
        }
        ship.discard();
        return null;
    }

    /** Measure actual uncompressed NBT, stopping before an oversized allocation is possible. */
    private static boolean fits(CompoundTag tag) {
        try (DataOutputStream output = new DataOutputStream(new OutputStream() {
            private int bytes;

            @Override
            public void write(int value) throws IOException {
                if (++bytes > MAX_NBT_BYTES) throw new IOException("NBT capacity exceeded");
            }

            @Override
            public void write(byte[] values, int offset, int length) throws IOException {
                if (length > MAX_NBT_BYTES - bytes) throw new IOException("NBT capacity exceeded");
                bytes += length;
            }
        })) {
            NbtIo.write(tag, output);
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    private static BasicEntityShip resolveTeamShip(ServerLevel level, CapaTeitoku capa, int team, int slot) {
        int uid = capa.getTeamMember(team, slot);
        Entity entity = level.getEntity(capa.getTeamSID(team, slot));
        if (entity instanceof BasicEntityShip ship && ship.getShipUID() == uid && ship.getPlayerUID() == capa.getPlayerUID()) {
            return ship;
        }
        BasicEntityShip ship = ServerDataManager.getShipByUID(uid);
        return ship != null && ship.level() == level && ship.getPlayerUID() == capa.getPlayerUID() ? ship : null;
    }

    private static CapaTeitoku capability(Player player) {
        return player == null ? null : player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
    }

    private static int fleetRank(CompoundTag stored, CapaTeitoku capa) {
        int[] state = stored.getCompound("EntityData").getIntArray("StateMinor");
        int uid = state.length > ID.M.ShipUID ? state[ID.M.ShipUID] : 0;
        if (capa != null && uid > 0) {
            for (int team = 0; team < CapaTeitoku.TEAM_NUM; team++) {
                for (int slot = 0; slot < CapaTeitoku.SLOT_NUM; slot++) {
                    if (capa.getTeamMember(team, slot) == uid) return team * CapaTeitoku.SLOT_NUM + slot;
                }
            }
        }
        return -1;
    }

    private static List<CompoundTag> ordered(ItemStack stack, Player player) {
        CapaTeitoku capa = capability(player);
        List<CompoundTag> result = new ArrayList<>();
        for (Tag entry : ships(stack)) result.add((CompoundTag) entry);
        result.sort(Comparator.comparingInt((CompoundTag entry) -> fleetRank(entry, capa))
                .thenComparingLong(entry -> entry.getLong("StoredOrder")));
        return result;
    }

    public static Component nextShipName(ItemStack stack, Player player) {
        List<CompoundTag> order = ordered(stack, player);
        return order.isEmpty() ? Component.translatable("tooltip.shincolle_kai.soul_talisman.empty")
                : Component.literal(order.get(0).getString("Name"));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        int mode = getMode(stack);
        if (mode < MODE_RELEASE_NEXT || !isFilled(stack)) return InteractionResult.PASS;
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;
        if (!isStoredOwner(stack, player)) return refuse(player, "not_owner");
        if (!context.getLevel().mayInteract(player, context.getClickedPos())) return InteractionResult.FAIL;
        BlockPos origin = context.getClickedPos().relative(context.getClickedFace());
        List<CompoundTag> order = ordered(stack, player);
        ListTag remaining = ships(stack);
        int released = 0;
        List<BlockPos> nearby = mode == MODE_RELEASE_ALL ? nearbyPositions(origin) : List.of(origin);
        for (int i = 0; i < order.size(); i++) {
            CompoundTag stored = order.get(i);
            String rejection = ShipTransport.release(player, (ServerLevel) context.getLevel(), stored,
                    i == 0 ? List.of(origin) : nearby, i > 0);
            if (rejection == null) {
                remaining.remove(stored);
                released++;
            } else if (mode == MODE_RELEASE_NEXT) {
                return refuse(player, rejection);
            }
            if (mode == MODE_RELEASE_NEXT) break;
        }
        if (mode == MODE_RELEASE_ALL) summary(player, "release", released, remaining.size());
        return InteractionResult.SUCCESS;
    }

    private static List<BlockPos> nearbyPositions(BlockPos origin) {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = -BULK_RELEASE_RADIUS; x <= BULK_RELEASE_RADIUS; x++) {
            for (int z = -BULK_RELEASE_RADIUS; z <= BULK_RELEASE_RADIUS; z++) {
                if (x * x + z * z > BULK_RELEASE_RADIUS * BULK_RELEASE_RADIUS) continue;
                for (int y = -2; y <= 2; y++) positions.add(origin.offset(x, y, z));
            }
        }
        positions.sort(Comparator.comparingDouble(origin::distSqr));
        return positions;
    }

    private static InteractionResult refuse(Player player, String reason) {
        player.displayClientMessage(Component.translatable("message.shincolle_kai.soul_talisman." + reason), true);
        return InteractionResult.SUCCESS;
    }

    private static void summary(Player player, String action, int changed, int remaining) {
        player.displayClientMessage(Component.translatable("message.shincolle_kai.soul_talisman." + action, changed, remaining), true);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getMode(stack) >= MODE_RELEASE_NEXT;
    }

    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return isFilled(stack);
    }

    @Override
    public Entity createEntity(Level level, Entity location, ItemStack stack) {
        return new BasicEntityItem(ModEntities.BASIC_ENTITY_ITEM.get(), level,
                location.getX(), location.getY(), location.getZ(), stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(modeName(getMode(stack)).copy().withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.shincolle_kai.soul_talisman.count", getStoredCount(stack))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.shincolle_kai.soul_talisman.controls").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.shincolle_kai.soul_talisman.source").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.shincolle_kai.soul_talisman.preview").withStyle(ChatFormatting.DARK_GRAY));
    }
}
