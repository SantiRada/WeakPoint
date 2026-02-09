package Tenzinn.Events;

import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

import javax.annotation.Nullable;
import java.util.Random;
import java.awt.Color;

public class MiningSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {

    public MiningSystem() { super(DamageBlockEvent.class); }

    @Override
    public void handle(int i, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer, DamageBlockEvent event) {
        if (event.getBlockType() == BlockType.EMPTY) return;
        if(!event.getBlockType().getId().contains("Ore")) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(i);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        event.setCancelled(true);

        Vector3i baseTransform = event.getTargetBlock();
        Position particlePosition = calculateNewPosition(baseTransform);
        Direction particleDirection = calculateNewDirection(baseTransform, particlePosition);

        SpawnParticleSystem packet = new SpawnParticleSystem();
        packet.particleSystemId = "Weak_Point_Particle";
        packet.rotation = particleDirection;
        packet.position = particlePosition;
        packet.scale = 1.0f;

        player.sendMessage(Message.raw("Direction: (" + particleDirection.yaw + ", " + particleDirection.pitch + ", " + particleDirection.roll + ")").color(Color.cyan));
        playerRef.getPacketHandler().write(packet);
    }

    private Position calculateNewPosition(Vector3i baseTransform) {
        Random random = new Random();
        double newX = baseTransform.x + 0.01 + (random.nextDouble() * 0.98);
        double newY = baseTransform.y + 0.01 + (random.nextDouble() * 0.98);
        double newZ = baseTransform.z + 0.01 + (random.nextDouble() * 0.98);
        double offset = 0.05f;

        Random randomValue = new Random();
        int rnd = randomValue.nextInt(100);

        if(rnd >= 0 && rnd < 20) { newX = baseTransform.x + 1 + offset; }
        else if (rnd >= 20 && rnd < 40) { newX = baseTransform.x - offset; }
        else if(rnd >= 40 && rnd < 60) { newY = baseTransform.y + 1 + offset; }
        else if (rnd >= 60 && rnd < 80) { newZ = baseTransform.z + 1 + offset; }
        else { newZ = baseTransform.z - offset; }

        return new Position(newX, newY, newZ);
    }

    private Direction calculateNewDirection(Vector3i baseTransform, Position pos) {
        Direction newDirection = new Direction(0.0f, 0.0f, 0.0f);

        if (pos.x <= baseTransform.x) { newDirection.yaw = -90; }
        else if (pos.x >= baseTransform.x+1) { newDirection.yaw = 90; }

        if (pos.y >= baseTransform.y+1) { newDirection.pitch = 90; }

        if (pos.z <= baseTransform.z) { newDirection.roll = -90; }
        else if (pos.z >= baseTransform.z+1) { newDirection.roll = 90; }

        return newDirection;
    }

    @Nullable @Override
    public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }
}