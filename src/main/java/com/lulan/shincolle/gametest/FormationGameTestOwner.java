package com.lulan.shincolle.gametest;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import java.lang.reflect.Field;
import java.util.List;

/** Registers a fixture owner for the production server's capability lookup. */
final class FormationGameTestOwner implements AutoCloseable {
    private final List<ServerPlayer> players;
    private final ServerPlayer owner;

    @SuppressWarnings("unchecked")
    FormationGameTestOwner(ServerPlayer owner) {
        this.owner = owner;
        try {
            Field field = PlayerList.class.getDeclaredField("players");
            field.setAccessible(true);
            this.players = (List<ServerPlayer>) field.get(owner.server.getPlayerList());
            this.players.add(owner);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot register formation owner", error);
        }
    }

    @Override
    public void close() {
        this.players.remove(this.owner);
    }
}
