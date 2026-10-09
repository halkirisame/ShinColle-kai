package com.lulan.shincolle.ai.command;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.command.AcceptedEntry;
import com.lulan.shincolle.ai.domain.command.AcceptedShipCommand;
import com.lulan.shincolle.ai.domain.command.CommandDecision;
import com.lulan.shincolle.ai.domain.command.CommandDispatchResult;
import com.lulan.shincolle.ai.domain.command.CommandKind;
import com.lulan.shincolle.ai.domain.command.CommandMode;
import com.lulan.shincolle.ai.domain.command.CommandNotification;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandRecipient;
import com.lulan.shincolle.ai.domain.command.CommandRejectReason;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.RecipientSelection;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.ai.domain.command.ShipCommandState;
import com.lulan.shincolle.ai.domain.command.FormationGate;
import com.lulan.shincolle.ai.domain.command.RecipientObservation;
import com.lulan.shincolle.ai.domain.command.RecipientSelector;
import com.lulan.shincolle.ai.domain.command.RecipientValidator;
import com.lulan.shincolle.ai.domain.command.RejectedEntry;
import com.lulan.shincolle.ai.domain.command.RequestedCommand;
import com.lulan.shincolle.ai.domain.command.RequestedEntityRef;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.domain.command.ShipUid;
import com.lulan.shincolle.ai.domain.command.SlotObservation;
import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.ShipSpawnEgg;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.server.CacheDataShip;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.utility.FormationHelper;
import com.lulan.shincolle.utility.TargetHelper;
import com.lulan.shincolle.utility.TeamHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public final class ShipCommandDispatcher {
    private static final Map<MinecraftServer, Long> SEQUENCES = new WeakHashMap<>();
    private static final String PREFIX = "chat.shincolle_kai.pointer.command.";

    private ShipCommandDispatcher() { }

    @FunctionalInterface
    public interface ShipResolver {
        BasicEntityShip resolve(ServerLevel level, CapaTeitoku capa, int team, int slot);
    }

    @FunctionalInterface
    public interface KnownShip {
        boolean known(ServerLevel level, CapaTeitoku capa, int team, int slot);
    }

    @FunctionalInterface
    public interface FuelMarker {
        void mark(ServerPlayer player, List<BasicEntityShip> ships, double x, double y, double z);
    }

    /** A null result means the request itself failed validation. */
    public static CommandDispatchResult dispatch(ServerPlayer player, CommandKind kind, int[] values,
                                                  ShipResolver resolver, KnownShip known, FuelMarker fuelMarker) {
        if (player.getMainHandItem().getItem() != ModItems.POINTER.get()
                && player.getOffhandItem().getItem() != ModItems.POINTER.get()) return null;
        int minLength = kind == CommandKind.MOVE || kind == CommandKind.GUARD_POSITION ? 7 : 3;
        if (kind == CommandKind.TOGGLE_SIT) minLength = 4;
        if (values.length < minLength) return null;
        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        if (capa == null) return null;

        ServerLevel level = player.serverLevel();
        int modeValue = kind == CommandKind.ATTACK || kind == CommandKind.GUARD_ENTITY
                ? values.length >= 4 ? Mth.clamp(values[2], 0, 2) : 2
                : Mth.clamp(values[2], 0, 2);
        CommandMode mode = CommandMode.values()[modeValue];
        int team = capa.getSelectTeam();
        Entity target = null;
        RequestedCommand request;
        CommandPos position = null;
        int guardType = 0;
        if (kind == CommandKind.ATTACK || kind == CommandKind.GUARD_ENTITY) {
            int targetId = values.length >= 4 ? values[3] : values[2];
            request = kind == CommandKind.ATTACK
                    ? new RequestedCommand.Attack(new RequestedEntityRef(targetId))
                    : new RequestedCommand.GuardEntity(new RequestedEntityRef(targetId));
            target = level.getEntity(targetId);
            if (target == null || player.distanceToSqr(target) > 4096D) return null;
            if (kind == CommandKind.ATTACK && (!(target instanceof LivingEntity)
                    || TargetHelper.isEntityInvulnerable(target) || TargetHelper.isInvulnerablePlayer(target)
                    || TeamHelper.checkSameOwner(player, target) || TeamHelper.checkIsAlly(player, target))) return null;
        } else if (kind == CommandKind.TOGGLE_SIT) {
            request = new RequestedCommand.ToggleSit(new RequestedEntityRef(values[3]));
            target = level.getEntity(values[3]);
            if (!(target instanceof BasicEntityShip clicked)
                    || (!TeamHelper.checkSameOwner(player, clicked) && !clicked.isOwnedBy(player))
                    || player.distanceToSqr(clicked) > 4096D) return null;
        } else {
            guardType = Mth.clamp(values[3], 0, 1);
            position = new CommandPos(values[4], values[5], values[6]);
            boolean release = values.length > 7 && values[7] == 1;
            request = guardType == 0
                    ? new RequestedCommand.Move(position, release, values.length > 7)
                    : new RequestedCommand.GuardPosition(position, release, values.length > 7);
            BlockPos block = new BlockPos(position.x(), position.y(), position.z());
            if (!level.isInWorldBounds(block) || player.distanceToSqr(block.getX() + 0.5D,
                    block.getY() + 0.5D, block.getZ() + 0.5D) > 4096D) return null;
        }

        List<SlotObservation> slots = new ArrayList<>();
        // the team list as stored, for the clicked ship's membership below
        List<Integer> storedUids = new ArrayList<>();
        Map<Integer, CacheDataShip> shipRecords = new HashMap<>();
        for (int slot = 0; slot < CapaTeitoku.SLOT_NUM; slot++) {
            int shipUid = capa.getTeamMember(team, slot);
            storedUids.add(shipUid);
            CacheDataShip record = ServerDataManager.getShipWorldData(shipUid);
            if (record != null) {
                shipRecords.put(shipUid, record);
            }
            slots.add(new SlotObservation(slot, ShipUid.fromLegacy(shipUid), capa.isShipSelected(team, slot),
                    mode == CommandMode.SINGLE && known.known(level, capa, team, slot),
                    record != null && record.sunk));
        }
        RecipientSelection selection = RecipientSelector.selectDetailed(mode, slots);
        List<CommandRecipient> recipients = new ArrayList<>(selection.recipients());
        List<RejectedEntry> selectionRejected = new ArrayList<>(selection.rejected());
        if (request instanceof RequestedCommand.ToggleSit && target instanceof BasicEntityShip clicked) {
            int clickedUid = clicked.getStateMinor(ID.M.ShipUID);
            boolean inTeam = recipients.stream().anyMatch(r -> r.shipUid() == clickedUid)
                    || storedUids.contains(clickedUid);
            if (!inTeam) {
                recipients = List.of(new CommandRecipient(-1, clickedUid));
                selectionRejected.clear();
            }
        }
        if (recipients.isEmpty() && selectionRejected.isEmpty()) {
            CommandDispatchResult result = new CommandDispatchResult(List.of(), List.of());
            notifyPlayer(player, result, shipRecords);
            return result;
        }

        Map<CommandRecipient, BasicEntityShip> resolved = new HashMap<>();
        Map<CommandRecipient, CommandRejectReason> failures = new HashMap<>();
        for (CommandRecipient recipient : recipients) {
            CacheDataShip record = shipRecords.get(recipient.shipUid());
            if (record != null && record.sunk) {
                failures.put(recipient, CommandRejectReason.SUNK);
                continue;
            }
            BasicEntityShip ship = recipient.slot() < 0 ? (BasicEntityShip) target
                    : resolver.resolve(level, capa, team, recipient.slot());
            // A known ship in another level is still a fixed recipient, never a fallback.
            BasicEntityShip observed = ship;
            if (observed == null) {
                BasicEntityShip persistent = ServerDataManager.getShipByUID(recipient.shipUid());
                if (persistent != null && persistent.getPlayerUID() == capa.getPlayerUID()) observed = persistent;
            }
            RecipientObservation observation = new RecipientObservation(observed != null,
                    observed != null && (observed.getPlayerUID() == capa.getPlayerUID()
                            || (kind == CommandKind.TOGGLE_SIT && observed.isOwnedBy(player))),
                    observed != null && observed.level() == level,
                    observed == null || observed.level() != level ? 0 : player.distanceToSqr(observed),
                    observed != null && observed.getStateFlag(ID.F.NoFuel));
            RecipientValidator.validate(kind, observation).ifPresent(reason -> failures.put(recipient, reason));
            if (ship != null) resolved.put(recipient, ship);
        }

        int formatId = capa.getFormatID(team);
        boolean formation = mode == CommandMode.FORMATION && formatId > 0;
        boolean formationMove = formation && position != null;
        boolean formationGuard = formation && kind == CommandKind.GUARD_ENTITY;
        if (formationMove) {
            int active = (int) recipients.stream().filter(r -> !failures.containsKey(r)).count();
            boolean mismatch = recipients.stream().filter(r -> !failures.containsKey(r))
                    .anyMatch(r -> com.lulan.shincolle.ai.ShipFormationStateAdapter.active(resolved.get(r))
                            .map(a -> a.pattern().legacyId() != formatId).orElse(true));
            if (!FormationGate.move(active, new ArrayList<>(failures.values()), mismatch)) {
                for (CommandRecipient recipient : recipients) {
                    CommandRejectReason reason = failures.get(recipient);
                    if (reason != CommandRejectReason.SUNK) {
                        failures.put(recipient, FormationGate.rejection(reason));
                    }
                }
            }
        } else if (formationGuard && !FormationGate.guardEntity(capa.getNumberOfShip(level, team))) {
            for (CommandRecipient recipient : recipients) {
                if (failures.get(recipient) != CommandRejectReason.SUNK) {
                    failures.put(recipient, CommandRejectReason.FORMATION_UNSATISFIED);
                }
            }
        }

        // Upstream seats every team recipient opposite to the first recipient; a click outside the
        // team is the only recipient and so toggles itself.
        boolean sit = request instanceof RequestedCommand.ToggleSit && !recipients.stream()
                .map(resolved::get).filter(Objects::nonNull).findFirst()
                .orElse((BasicEntityShip) target).getCommandState().sitting();
        List<AcceptedEntry> accepted = new ArrayList<>();
        List<RejectedEntry> rejected = new ArrayList<>(selectionRejected);
        for (CommandRecipient recipient : recipients) {
            CommandRejectReason reason = failures.get(recipient);
            if (reason != null) {
                rejected.add(new RejectedEntry(recipient, reason));
                continue;
            }
            BasicEntityShip ship = resolved.get(recipient);
            ShipCommand command = decide(request, ship, target, level, position, formationMove, formatId, sit);
            long sequence = SEQUENCES.getOrDefault(level.getServer(), 0L);
            SEQUENCES.put(level.getServer(), sequence + 1L);
            accepted.add(new AcceptedEntry(recipient,
                    new AcceptedShipCommand(player.getUUID(), level.getGameTime(), sequence, command)));
        }
        CommandDispatchResult result = new CommandDispatchResult(accepted, rejected);
        // Apply only after every recipient and the formation gate have been evaluated.
        apply(result, resolved, request, level, position, player, values.length > 7);
        List<BasicEntityShip> noFuel = rejected.stream().filter(e -> e.reason() == CommandRejectReason.NO_FUEL)
                .map(e -> resolved.get(e.recipient())).filter(s -> s != null).toList();
        if (!noFuel.isEmpty()) {
            double x = target != null ? target.getX() : position.x() + 0.5D;
            double y = target != null ? target.getY() + target.getBbHeight() * 0.5D : position.y() + 0.5D;
            double z = target != null ? target.getZ() : position.z() + 0.5D;
            fuelMarker.mark(player, noFuel, x, y, z);
        }
        notifyPlayer(player, result, shipRecords);
        return result;
    }

    private static ShipCommand decide(RequestedCommand request, BasicEntityShip ship, Entity target,
                                      ServerLevel level, CommandPos position, boolean formationMove, int formatId,
                                      boolean sit) {
        if (request instanceof RequestedCommand.ToggleSit) {
            return new ShipCommand.SetSitting(sit);
        }
        ShipCommandState state = ship.getCommandState();
        if (request instanceof RequestedCommand.Attack) {
            return CommandDecision.attack(handle(target), state.manualAttack().orElse(null));
        }
        if (request instanceof RequestedCommand.GuardEntity) {
            return CommandDecision.guardEntity(handle(target), state.movement() instanceof MovementOrder.GuardEntity guard
                    ? guard.target() : null);
        }
        DimensionKey dimension = dimension(level);
        if (formationMove) return new ShipCommand.FormationMove(dimension, position, formatId);
        boolean release = request instanceof RequestedCommand.Move move ? move.releaseOnArrival()
                : ((RequestedCommand.GuardPosition) request).releaseOnArrival();
        boolean explicit = request instanceof RequestedCommand.Move move ? move.explicitArrivalMode()
                : ((RequestedCommand.GuardPosition) request).explicitArrivalMode();
        MovementOrder movement = state.movement();
        boolean active = movement instanceof MovementOrder.MoveTo || movement instanceof MovementOrder.GuardPosition;
        boolean currentRelease = movement instanceof MovementOrder.MoveTo move ? move.releaseOnArrival()
                : movement instanceof MovementOrder.GuardPosition guard && guard.releaseOnArrival();
        boolean change = explicit && active && currentRelease != release;
        DimensionKey currentDimension = movement instanceof MovementOrder.MoveTo move ? move.dimension()
                : movement instanceof MovementOrder.GuardPosition guard ? guard.dimension() : null;
        CommandPos currentPos = movement instanceof MovementOrder.MoveTo move ? move.position()
                : movement instanceof MovementOrder.GuardPosition guard ? guard.position() : null;
        return CommandDecision.position(dimension, position, release, change, active,
                currentDimension, currentPos, request instanceof RequestedCommand.GuardPosition);
    }

    private static void apply(CommandDispatchResult result, Map<CommandRecipient, BasicEntityShip> resolved,
                               RequestedCommand request, ServerLevel level, CommandPos pos,
                               ServerPlayer player, boolean blockPointer) {
        java.util.Set<BasicEntityShip> recallAllowed = new java.util.HashSet<>();
        if (blockPointer) {
            for (BasicEntityShip ship : resolved.values()) {
                if (com.lulan.shincolle.ai.ShipSeparationGate.commandMoveAllowed(ship)) recallAllowed.add(ship);
            }
        }
        List<BasicEntityShip> formationShips = new ArrayList<>();
        Map<BasicEntityShip, AcceptedShipCommand> formationCommands = new HashMap<>();
        for (AcceptedEntry entry : result.accepted()) {
            BasicEntityShip ship = resolved.get(entry.recipient());
            ShipCommand command = entry.command().command();
            CommandIssuer issuer = new CommandIssuer.Player(entry.command().issuer());
            long sequence = entry.command().serverSequence();
            if (command instanceof ShipCommand.SetSitting sit) {
                ship.applyCommandState(issuer, new CommandStateOp.Apply(sit), sequence);
                ship.setRiderAndMountSit(issuer);
            } else if (command instanceof ShipCommand.Attack || command instanceof ShipCommand.CancelAttack) {
                ship.applyCommandState(issuer, new CommandStateOp.Apply(command), sequence);
            } else if (request instanceof RequestedCommand.GuardEntity) {
                ship.applyCommandState(issuer, new CommandStateOp.Apply(command), sequence);
            } else if (command instanceof ShipCommand.FormationMove) {
                formationShips.add(ship);
                formationCommands.put(ship, entry.command());
            } else {
                if (command instanceof ShipCommand.Follow) {
                    ship.setTarget(null);
                    ship.setEntityTarget(null);
                }
                ship.applyCommandState(issuer, new CommandStateOp.Apply(command), sequence);
                if (recallAllowed.contains(ship)) recallPosition(ship, player);
            }
        }
        if (!formationShips.isEmpty()) applyFormation(formationShips, formationCommands, pos, level,
                ((ShipCommand.FormationMove) result.accepted().get(0).command().command()).formationId(), request);
        for (BasicEntityShip ship : formationShips) {
            if (recallAllowed.contains(ship)) recallPosition(ship, player);
        }
    }

    private static void applyFormation(List<BasicEntityShip> ships,
                                       Map<BasicEntityShip, AcceptedShipCommand> commands,
                                       CommandPos pos, ServerLevel level,
                                       int formatId, RequestedCommand request) {
        BasicEntityShip flagship = ships.get(0);
        boolean[] facing = FormationHelper.getFormationDirection(pos.x(), pos.z(), flagship.getX(), flagship.getZ());
        MovementOrder previous = flagship.getCommandState().movement();
        CommandPos old = previous instanceof MovementOrder.MoveTo move ? move.position()
                : previous instanceof MovementOrder.GuardPosition guard ? guard.position() : null;
        int[] cursor = {pos.x(), pos.y(), pos.z()};
        Map<BasicEntityShip, CommandPos> placements = new HashMap<>();
        for (BasicEntityShip ship : ships) {
            var pattern = com.lulan.shincolle.ai.domain.formation.FormationPattern.fromLegacy(formatId).orElseThrow();
            var placement = FormationHelper.calculateFormationPlacementNew(ship, pattern,
                    new com.lulan.shincolle.ai.domain.formation.FormationLayoutPlanner.Facing(facing[0], facing[1]),
                    new CommandPos(cursor[0], cursor[1], cursor[2]), pos, level);
            int[] placed = placement.position();
            cursor = placement.next();
            placements.put(ship, new CommandPos(placed[0], placed[1], placed[2]));
        }
        for (BasicEntityShip ship : ships) {
            AcceptedShipCommand accepted = commands.get(ship);
            CommandPos placed = placements.get(ship);
            ship.applyCommandState(new CommandIssuer.Player(accepted.issuer()), new CommandStateOp.Apply(
                    request instanceof RequestedCommand.Move
                             ? new ShipCommand.Move(dimension(level), placed, false)
                             : new ShipCommand.GuardPosition(dimension(level), placed, false)), accepted.serverSequence());
            ship.applyEmotesReaction(5);
        }
        CommandPos flagshipPos = placements.get(flagship);
        if (com.lulan.shincolle.ai.domain.formation.FormationLayoutPlanner.repeatReturnsToFollow(
                old, flagshipPos, true)) {
            for (BasicEntityShip ship : ships) {
                AcceptedShipCommand accepted = commands.get(ship);
                ship.applyCommandState(new CommandIssuer.Player(accepted.issuer()),
                        new CommandStateOp.Apply(new ShipCommand.Follow()), accepted.serverSequence());
            }
        }
    }

    private static void recallPosition(BasicEntityShip ship, ServerPlayer player) {
        MovementOrder movement = ship.getCommandState().movement();
        if (movement instanceof MovementOrder.MoveTo move) {
            com.lulan.shincolle.ai.ShipSeparationGate.recall(ship, player, move.position());
        } else if (movement instanceof MovementOrder.GuardPosition guard) {
            com.lulan.shincolle.ai.ShipSeparationGate.recall(ship, player, guard.position());
        }
    }

    private static TargetHandle handle(Entity entity) {
        return new TargetHandle(entity.getUUID(), dimension(entity.level().dimension().location()));
    }

    private static DimensionKey dimension(ServerLevel level) {
        return dimension(level.dimension().location());
    }

    private static DimensionKey dimension(net.minecraft.resources.ResourceLocation location) {
        return new DimensionKey(location.getNamespace(), location.getPath());
    }

    private static void notifyPlayer(ServerPlayer player, CommandDispatchResult result,
                                     Map<Integer, CacheDataShip> shipRecords) {
        List<CommandNotification.SunkLocation> sunkLocations = result.rejected().stream()
                .filter(entry -> entry.reason() == CommandRejectReason.SUNK)
                .map(entry -> {
                    CacheDataShip record = shipRecords.get(entry.recipient().shipUid());
                    return record == null ? null : new CommandNotification.SunkLocation(
                            entry.recipient().slot(), entry.recipient().shipUid(),
                            record.sunkX, record.sunkY, record.sunkZ);
                }).filter(java.util.Objects::nonNull).toList();
        CommandNotification.Summary summary = CommandNotification.summarize(result, sunkLocations);
        Component sunkNotice = sunkComponent(summary.sunk(), shipRecords);
        boolean japanese = player.getLanguage().toLowerCase(java.util.Locale.ROOT).startsWith("ja");
        Component message = notificationMessage(summary, sunkNotice, japanese);
        if (message != null) player.displayClientMessage(message, true);
    }

    private static Component notificationMessage(CommandNotification.Summary summary,
                                                 Component sunkNotice, boolean japanese) {
        if (summary.noRecipient()) return Component.translatable(PREFIX + "no_recipient");
        List<Component> lines = new ArrayList<>();
        if (summary.formationUnsatisfied()) {
            lines.add(Component.translatable(PREFIX + "formation_unsatisfied"));
            if (sunkNotice != null) lines.add(sunkNotice);
        } else {
            if (summary.reasons().isEmpty()) return null;
            List<Component> reasons = new ArrayList<>();
            for (CommandNotification.ReasonCount reason : summary.reasons()) {
                if (reason.reason() != CommandRejectReason.SUNK) {
                    reasons.add(Component.translatable(PREFIX + "reason."
                                    + (reason.reason() == CommandRejectReason.NOT_OWNED ? "not_found"
                                    : reason.reason().name().toLowerCase(java.util.Locale.ROOT)),
                            reason.count()));
                }
            }
            if (!reasons.isEmpty()) {
                Component joined = Component.empty();
                for (Component reason : reasons) {
                    if (!joined.getString().isEmpty()) joined = joined.copy().append(japanese ? "、" : ", ");
                    joined = joined.copy().append(reason);
                }
                lines.add(Component.translatable(PREFIX + "not_delivered", joined));
            }
            if (sunkNotice != null) {
                lines.add(reasons.isEmpty() ? Component.translatable(PREFIX + "not_delivered", sunkNotice)
                        : sunkNotice);
            }
        }
        if (lines.isEmpty()) return null;
        var message = lines.get(0).copy();
        for (int i = 1; i < lines.size(); i++) message.append("\n").append(lines.get(i));
        return message;
    }

    private static Component sunkComponent(CommandNotification.SunkSummary sunk,
                                           Map<Integer, CacheDataShip> shipRecords) {
        if (sunk == null || sunk.first() == null) {
            return null;
        }
        CacheDataShip record = shipRecords.get(sunk.first().shipUid());
        if (record == null) {
            return null;
        }
        Component shipName = savedShipName(record);
        String key = PREFIX + "reason.sunk" + (sunk.more() ? "_more" : "");
        return Component.translatable(key, sunk.count(), shipName,
                sunk.first().x(), sunk.first().y(), sunk.first().z());
    }

    private static Component savedShipName(CacheDataShip record) {
        if (record.entityNBT != null && record.entityNBT.contains("CustomName", Tag.TAG_STRING)) {
            String name = record.entityNBT.getString("CustomName");
            if (!name.isEmpty()) {
                try {
                    Component customName = Component.Serializer.fromJson(name);
                    if (customName != null) {
                        return customName;
                    }
                } catch (RuntimeException ignored) {
                    // A malformed cached custom name falls back to the ship type.
                }
            }
        }
        EntityType<?> type = ShipSpawnEgg.getEntityTypeForClass(record.classID);
        return type != null ? Component.translatable(type.getDescriptionId())
                : Component.translatable("item.shincolle_kai.ship_spawn_egg");
    }
}
