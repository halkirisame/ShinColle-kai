package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import net.minecraft.server.level.ServerPlayer;
import java.util.concurrent.atomic.AtomicInteger;

/** A real five-member team for movement fixtures that used to supply only raw formation fields. */
final class FormationGameTestTeam implements AutoCloseable {
    private static final AtomicInteger NEXT = new AtomicInteger(8_000_000);
    private final BasicEntityShip host;
    private final CapaTeitoku capa;
    private final FormationGameTestOwner online;

    FormationGameTestTeam(ServerPlayer owner, BasicEntityShip host, GameTestEntities entities) {
        this.host = host;
        this.online = new FormationGameTestOwner(owner);
        this.capa = owner.getCapability(CapaTeitokuProvider.CAPABILITY)
                .orElseThrow(() -> new AssertionError("Formation owner has no capability"));
        int base = NEXT.getAndAdd(10);
        this.capa.setPlayerUID(base);
        int hostSlot = host.getStateMinor(ID.M.FormatPos);
        int type = host.getStateMinor(ID.M.FormatType);
        host.setPlayerUID(base);
        host.setShipUID(base + 1);
        this.capa.setFormatID(0, type);
        int count = 0;
        for (int slot = 0; slot < 6; slot++) {
            this.capa.setTeamMember(0, slot, 0);
            this.capa.setTeamSID(0, slot, -1);
        }
        this.capa.setTeamMember(0, hostSlot, host.getShipUID());
        this.capa.setTeamSID(0, hostSlot, host.getId());
        for (int slot = 0; slot < 6 && count < 4; slot++) {
            if (slot == hostSlot) continue;
            BasicEntityShip member = entities.add(ModEntities.BB_KONGOU.get().create(host.level()));
            member.setNoAi(true);
            member.setNoGravity(true);
            member.setPlayerUID(base);
            member.setShipUID(base + 2 + slot);
            member.moveTo(host.getX(), host.getY() + 20D, host.getZ(), 0F, 0F);
            if (!host.level().addFreshEntity(member)) throw new AssertionError("Cannot add formation fixture member");
            this.capa.setTeamMember(0, slot, member.getShipUID());
            this.capa.setTeamSID(0, slot, member.getId());
            count++;
        }
        host.formationState().invalidate();
    }

    void setFormation(int type) {
        this.capa.setFormatID(0, type);
        this.host.setStateMinor(ID.M.FormatType, type);
    }

    void setSlot(int slot) {
        int previous = this.host.getStateMinor(ID.M.FormatPos);
        int uid = this.capa.getTeamMember(0, slot), sid = this.capa.getTeamSID(0, slot);
        this.capa.setTeamMember(0, previous, uid);
        this.capa.setTeamSID(0, previous, sid);
        this.capa.setTeamMember(0, slot, this.host.getShipUID());
        this.capa.setTeamSID(0, slot, this.host.getId());
        this.host.setStateMinor(ID.M.FormatPos, slot);
    }

    @Override
    public void close() {
        this.capa.setFormatID(0, 0);
        for (int slot = 0; slot < 6; slot++) this.capa.setTeamMember(0, slot, 0);
        this.online.close();
    }
}
