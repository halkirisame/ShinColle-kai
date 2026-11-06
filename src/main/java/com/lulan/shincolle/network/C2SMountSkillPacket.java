package com.lulan.shincolle.network;

import com.lulan.shincolle.ai.domain.combat.MountSkillDecision.Skill;
import com.lulan.shincolle.playerskill.ShipSkillHandler;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A target proposal; riding and ownership are resolved on the server at execution. */
public record C2SMountSkillPacket(Skill skill, Target target) {
    public sealed interface Target permits EntityTarget, BlockTarget { }
    public record EntityTarget(int entityId) implements Target { }
    public record BlockTarget(BlockPos pos) implements Target { }

    public static C2SMountSkillPacket decode(FriendlyByteBuf buf) {
        if (buf.readableBytes() != 6 && buf.readableBytes() != 10) throw new DecoderException("Invalid mounted skill size");
        int skillId = buf.readUnsignedByte();
        if (skillId >= Skill.values().length) throw new DecoderException("Unknown mounted skill");
        int targetId = buf.readUnsignedByte();
        Target target;
        if (targetId == 0 && buf.readableBytes() == 4) {
            int entityId = buf.readInt();
            if (entityId < 0) throw new DecoderException("Invalid entity id");
            target = new EntityTarget(entityId);
        } else if (targetId == 1 && buf.readableBytes() == 8) {
            target = new BlockTarget(buf.readBlockPos());
        } else {
            throw new DecoderException("Invalid mounted target");
        }
        return new C2SMountSkillPacket(Skill.values()[skillId], target);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(skill.ordinal());
        if (target instanceof EntityTarget entity) {
            buf.writeByte(0);
            buf.writeInt(entity.entityId());
        } else {
            buf.writeByte(1);
            buf.writeBlockPos(((BlockTarget) target).pos());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        // Registered with consumerMainThread and PLAY_TO_SERVER.
        ShipSkillHandler.handleMountSkill(context.getSender(), skill, target);
        context.setPacketHandled(true);
    }
}
