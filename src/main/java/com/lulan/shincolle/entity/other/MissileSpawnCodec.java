package com.lulan.shincolle.entity.other;

import com.lulan.shincolle.ai.domain.combat.ProjectileTrailDelay;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

/** Internal additional-spawn schema. Incompatible payloads are never guessed as an older format. */
final class MissileSpawnCodec {
    private static final int MAGIC = 0x53434D53;
    /** Network payload version. Unrelated to the {@code "schema"} of published JSON data; it is raised independently. */
    private static final int WIRE_VERSION = 1;

    private MissileSpawnCodec() { }

    record Snapshot(int type, int move, int life, int owner, double x, double y, double z,
                    double speed, double acc1, double acc2, double t0, double t1, ProjectileTrailDelay delay) { }

    static void write(FriendlyByteBuf buffer, Snapshot state) {
        buffer.writeInt(MAGIC);
        buffer.writeVarInt(WIRE_VERSION);
        buffer.writeVarInt(state.type());
        buffer.writeVarInt(state.move());
        buffer.writeVarInt(state.life());
        buffer.writeVarInt(state.owner());
        buffer.writeDouble(state.x());
        buffer.writeDouble(state.y());
        buffer.writeDouble(state.z());
        buffer.writeDouble(state.speed());
        buffer.writeDouble(state.acc1());
        buffer.writeDouble(state.acc2());
        buffer.writeDouble(state.t0());
        buffer.writeDouble(state.t1());
        buffer.writeVarInt(state.delay().remainingTicks());
    }

    static Snapshot read(FriendlyByteBuf buffer) {
        try {
            if (buffer.readInt() != MAGIC) throw new DecoderException("Invalid missile spawn magic");
            int version = buffer.readVarInt();
            if (version != WIRE_VERSION) throw new DecoderException("Unsupported missile spawn schema: " + version);
            return new Snapshot(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    ProjectileTrailDelay.fromSnapshot(buffer.readVarInt()));
        } catch (DecoderException invalid) {
            throw invalid;
        } catch (RuntimeException invalid) {
            throw new DecoderException("Invalid or incomplete missile spawn payload", invalid);
        }
    }
}
