package com.lulan.shincolle.network;

import com.lulan.shincolle.client.MountSkillClient;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipCV;
import com.lulan.shincolle.reference.ID;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Only the manual timers and available aircraft, sent to the current controller. */
public record S2CMountSkillPacket(int hostId, int light, int heavy, int airLight, int airHeavy, int planesLight, int planesHeavy) {
    public static S2CMountSkillPacket of(BasicEntityShip ship) {
        return new S2CMountSkillPacket(ship.getId(), ship.getStateTimer(ID.T.MountSkillCD1), ship.getStateTimer(ID.T.MountSkillCD2),
                ship.getStateTimer(ID.T.MountSkillCD3), ship.getStateTimer(ID.T.MountSkillCD4),
                ship instanceof BasicEntityShipCV cv ? cv.getNumAircraftLight() : 0,
                ship instanceof BasicEntityShipCV cv ? cv.getNumAircraftHeavy() : 0);
    }

    public static S2CMountSkillPacket decode(FriendlyByteBuf buf) {
        if (buf.readableBytes() != 28) throw new DecoderException("Invalid mounted skill sync size");
        return new S2CMountSkillPacket(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(hostId);
        buf.writeInt(light);
        buf.writeInt(heavy);
        buf.writeInt(airLight);
        buf.writeInt(airHeavy);
        buf.writeInt(planesLight);
        buf.writeInt(planesHeavy);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MountSkillClient.receive(this));
        supplier.get().setPacketHandled(true);
    }
}
