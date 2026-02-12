package Tenzinn.Events;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.math.vector.Vector3f;
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

import java.awt.Color;
import java.util.Random;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

public class MiningSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {
    
    private Method writeMethod;

    public MiningSystem() {
        super(DamageBlockEvent.class);

        try {
            Class<?> toClientPacketClass = Class.forName("com.hypixel.hytale.protocol.ToClientPacket");
            writeMethod = com.hypixel.hytale.server.core.io.PacketHandler.class.getMethod("write", toClientPacketClass);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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

        try {
            // Calcular el punto exacto de impacto en la superficie
            Vector3f hitPosition = calculateHitPosition(playerRef, event.getTargetBlock());

            // Crear la partícula en el punto de impacto
            SpawnParticleSystem packet = new SpawnParticleSystem();
            packet.particleSystemId = "Weak_Point_Particle"; // Cambia a tu partícula custom
            packet.position = new Position(hitPosition.x, hitPosition.y, hitPosition.z);
            packet.rotation = new Direction(0.0f, 0.0f, 0.0f); // Rotación neutral
            packet.scale = 1.0f;
            packet.color = null;

            // Enviar el packet usando reflexión
            if (writeMethod != null) {
                writeMethod.invoke(playerRef.getPacketHandler(), packet);
            }

        } catch (Exception e) {
            playerRef.sendMessage(Message.raw("Error: " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }
    }

    private Vector3f calculateHitPosition(PlayerRef playerRef, Vector3i blockPosition) {
        Vector3f rayOrigin = playerRef.getTransform().getPosition().toVector3f();
        rayOrigin = rayOrigin.add(new Vector3f(0, 1.6f, 0));

        // Calcular dirección de la mirada (normalizada)
        double pitch = playerRef.getTransform().getRotation().getPitch();
        double yaw = playerRef.getTransform().getRotation().getYaw();

        double newX = -Math.cos(pitch) * Math.sin(yaw);
        double newY = Math.sin(pitch);
        double newZ = -Math.cos(pitch) * Math.cos(yaw);

        Vector3f rayDir = new Vector3f((float)newX, (float)newY, (float)newZ);

        float length = (float)Math.sqrt(rayDir.x * rayDir.x + rayDir.y * rayDir.y + rayDir.z * rayDir.z);
        if (length > 0) { rayDir = new Vector3f(rayDir.x / length, rayDir.y / length, rayDir.z / length); }

        // Definir los límites del bloque (AABB)
        Vector3f boxMin = new Vector3f(blockPosition.x, blockPosition.y, blockPosition.z);
        Vector3f boxMax = new Vector3f(blockPosition.x + 1, blockPosition.y + 1, blockPosition.z + 1);

        // Algoritmo de intersección rayo-AABB
        float tMin = Float.NEGATIVE_INFINITY;
        float tMax = Float.POSITIVE_INFINITY;

        // Eje X
        if (Math.abs(rayDir.x) > 0.0001f) {
            float t1 = (boxMin.x - rayOrigin.x) / rayDir.x;
            float t2 = (boxMax.x - rayOrigin.x) / rayDir.x;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        // Eje Y
        if (Math.abs(rayDir.y) > 0.0001f) {
            float t1 = (boxMin.y - rayOrigin.y) / rayDir.y;
            float t2 = (boxMax.y - rayOrigin.y) / rayDir.y;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        // Eje Z
        if (Math.abs(rayDir.z) > 0.0001f) {
            float t1 = (boxMin.z - rayOrigin.z) / rayDir.z;
            float t2 = (boxMax.z - rayOrigin.z) / rayDir.z;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        // Verificar si hay intersección
        if (tMax < tMin || tMax < 0) {
            // No hay intersección, usar centro del bloque como fallback
            return new Vector3f(blockPosition.x + 0.5f, blockPosition.y + 0.5f, blockPosition.z + 0.5f);
        }

        // Calcular el punto de intersección (la entrada al bloque)
        float t = tMin > 0 ? tMin : tMax;
        Vector3f hitPosition = new Vector3f(
                rayOrigin.x + rayDir.x * t,
                rayOrigin.y + rayDir.y * t,
                rayOrigin.z + rayDir.z * t
        );

        // Debug
        playerRef.sendMessage(Message.raw("BlockPosition: (" + blockPosition.x + ", " + blockPosition.y + ", " + blockPosition.z + ")").color(Color.cyan));
        playerRef.sendMessage(Message.raw("Origin: (" + rayOrigin.x + ", " + rayOrigin.y + ", " + rayOrigin.z + ")").color(Color.cyan));
        playerRef.sendMessage(Message.raw("Dir: (" + rayDir.x + ", " + rayDir.y + ", " + rayDir.z + ")").color(Color.cyan));
        playerRef.sendMessage(Message.raw("Hit t: " + t).color(Color.magenta));
        playerRef.sendMessage(Message.raw("HitPosition: (" + hitPosition.x + ", " + hitPosition.y + ", " + hitPosition.z + ")").color(Color.orange));

        return hitPosition;
    }

    @Nullable @Override
    public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }
}