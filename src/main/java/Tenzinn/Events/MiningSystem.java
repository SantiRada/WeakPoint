package Tenzinn.Events;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.ModelTransform;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.Random;
import javax.annotation.Nullable;

public class MiningSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {

    private Vector3i posBlock;

    private float offsetPunch = 0.2f;

    private Vector3d particlePosition = null;

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
        if (!event.getBlockType().getId().contains("Ore")) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(i);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        Vector3i targetBlock = event.getTargetBlock();

        if (posBlock != null) {
            if (posBlock.x != targetBlock.x || posBlock.y != targetBlock.y || posBlock.z != targetBlock.z) {
                CancelMining(playerRef, event.getBlockType().getId());
                playerRef.sendMessage(Message.raw("NUEVO BLOQUE").color(Color.CYAN));
                posBlock = targetBlock;
                particlePosition = null;
            } else {
                playerRef.sendMessage(Message.raw("OTROS GOLPES AL MISMO BLOQUE").color(Color.GRAY));
            }
        } else {
            posBlock = targetBlock;
            particlePosition = null;
            playerRef.sendMessage(Message.raw("PRIMER BLOQUE").color(Color.GREEN));
        }

        try {
            if (particlePosition == null) {
                playerRef.sendMessage(Message.raw("SE CANCELA EL DAÑO - Generando weak point").color(Color.ORANGE));
                event.setCancelled(true);

                SpawnParticleSystem packet = CreateParticle(playerRef, event);
                if (writeMethod != null) {
                    writeMethod.invoke(playerRef.getPacketHandler(), packet);
                }
            } else {
                Vector3f hitPosition = calculateHitPosition(playerRef, targetBlock);
                float distance = hitPosition.distanceTo(particlePosition.toVector3f());

                if (distance < offsetPunch) {
                    playerRef.sendMessage(Message.raw("¡GOLPE EXITOSO! Distancia: " + String.format("%.3f", distance)).color(Color.GREEN));

                    SpawnParticleSystem packet = CreateParticle(playerRef, event);
                    if (writeMethod != null) {
                        writeMethod.invoke(playerRef.getPacketHandler(), packet);
                    }
                } else {
                    playerRef.sendMessage(Message.raw("¡FALLASTE! Distancia: " + String.format("%.3f", distance)).color(Color.RED));
                    CancelMining(playerRef, event.getBlockType().getId());
                    FinishMining();
                }
            }

        } catch (Exception e) {
            playerRef.sendMessage(Message.raw("¡ERROR! " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }

        if (event.getCurrentDamage() < 0.1f) {
            playerRef.sendMessage(Message.raw("SE ROMPIÓ EL BLOQUE").color(Color.YELLOW));
            FinishMining();
        }
    }

    private SpawnParticleSystem CreateParticle(PlayerRef playerRef, DamageBlockEvent event) {
        particlePosition = calculateNewPosition(event.getTargetBlock());

        SpawnParticleSystem packet = new SpawnParticleSystem();
        packet.particleSystemId = "Weak_Point_Particle";
        packet.position = new Position(particlePosition.x, particlePosition.y, particlePosition.z);
        packet.scale = 1.0f;

        playerRef.sendMessage(Message.raw("¡LO GOLPEASTE!").color(Color.orange));

        return packet;
    }

    private void FinishMining () {
        posBlock = null;
        particlePosition = null;
    }

    private void CancelMining(PlayerRef playerRef, String id) {
        World world = Universe.get().getWorld(playerRef.getWorldUuid());
        world.setBlock(posBlock.x, posBlock.y, posBlock.z, id);
    }

    private Vector3f calculateHitPosition(PlayerRef playerRef, Vector3i blockPosition) {
        try {
            World world = Universe.get().getWorld(playerRef.getWorldUuid());
            if (world == null) return new Vector3f(blockPosition.x + 0.5f, blockPosition.y + 0.5f, blockPosition.z + 0.5f);

            Store<EntityStore> store = world.getEntityStore().getStore();
            Ref<EntityStore> ref = playerRef.getReference();

            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) return new Vector3f(blockPosition.x + 0.5f, blockPosition.y + 0.5f, blockPosition.z + 0.5f);

            Vector3d playerPos = transform.getPosition();
            float eyeHeight = 1.62f;

            ModelTransform modelTransform = transform.getSentTransform();
            Direction lookOrientation = modelTransform.lookOrientation;

            float pitch = lookOrientation.pitch;
            float yaw = lookOrientation.yaw;

            // Calcular dirección
            double dirX = -Math.sin(yaw) * Math.cos(pitch);
            double dirY = Math.sin(pitch);
            double dirZ = -Math.cos(yaw) * Math.cos(pitch);

            // Normalizar (por si acaso)
            double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
            dirX /= length;
            dirY /= length;
            dirZ /= length;

            // Origen del rayo (ojos del jugador)
            double rayX = playerPos.x;
            double rayY = playerPos.y + eyeHeight;
            double rayZ = playerPos.z;

            // Encontrar la cara más cercana del bloque
            Vector3f hit = null;
            double minT = Double.MAX_VALUE;

            // Límites del bloque
            double minX = blockPosition.x;
            double maxX = blockPosition.x + 1.0;
            double minY = blockPosition.y;
            double maxY = blockPosition.y + 1.0;
            double minZ = blockPosition.z;
            double maxZ = blockPosition.z + 1.0;

            // Cara X- (Oeste)
            if (Math.abs(dirX) > 0.0001) {
                double t = (minX - rayX) / dirX;
                if (t > 0 && t < minT) {
                    double y = rayY + dirY * t;
                    double z = rayZ + dirZ * t;
                    if (y >= minY && y <= maxY && z >= minZ && z <= maxZ) {
                        minT = t;
                        hit = new Vector3f((float)minX, (float)y, (float)z);
                    }
                }
            }

            // Cara X+ (Este)
            if (Math.abs(dirX) > 0.0001) {
                double t = (maxX - rayX) / dirX;
                if (t > 0 && t < minT) {
                    double y = rayY + dirY * t;
                    double z = rayZ + dirZ * t;
                    if (y >= minY && y <= maxY && z >= minZ && z <= maxZ) {
                        minT = t;
                        hit = new Vector3f((float)maxX, (float)y, (float)z);
                    }
                }
            }

            // Cara Y- (Abajo)
            if (Math.abs(dirY) > 0.0001) {
                double t = (minY - rayY) / dirY;
                if (t > 0 && t < minT) {
                    double x = rayX + dirX * t;
                    double z = rayZ + dirZ * t;
                    if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                        minT = t;
                        hit = new Vector3f((float)x, (float)minY, (float)z);
                    }
                }
            }

            // Cara Y+ (Arriba)
            if (Math.abs(dirY) > 0.0001) {
                double t = (maxY - rayY) / dirY;
                if (t > 0 && t < minT) {
                    double x = rayX + dirX * t;
                    double z = rayZ + dirZ * t;
                    if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                        minT = t;
                        hit = new Vector3f((float)x, (float)maxY, (float)z);
                    }
                }
            }

            // Cara Z- (Norte)
            if (Math.abs(dirZ) > 0.0001) {
                double t = (minZ - rayZ) / dirZ;
                if (t > 0 && t < minT) {
                    double x = rayX + dirX * t;
                    double y = rayY + dirY * t;
                    if (x >= minX && x <= maxX && y >= minY && y <= maxY) {
                        minT = t;
                        hit = new Vector3f((float)x, (float)y, (float)minZ);
                    }
                }
            }

            // Cara Z+ (Sur)
            if (Math.abs(dirZ) > 0.0001) {
                double t = (maxZ - rayZ) / dirZ;
                if (t > 0 && t < minT) {
                    double x = rayX + dirX * t;
                    double y = rayY + dirY * t;
                    if (x >= minX && x <= maxX && y >= minY && y <= maxY) {
                        minT = t;
                        hit = new Vector3f((float)x, (float)y, (float)maxZ);
                    }
                }
            }

            if (hit != null) {
                playerRef.sendMessage(Message.raw(String.format("Hit! Dist=%.3f Pos=(%.3f, %.3f, %.3f)", minT, hit.x, hit.y, hit.z)));
                return hit;
            }

            playerRef.sendMessage(Message.raw("No hit encontrado"));
            return new Vector3f(blockPosition.x + 0.5f, blockPosition.y + 0.5f, blockPosition.z + 0.5f);

        } catch (Exception e) {
            e.printStackTrace();
            return new Vector3f(blockPosition.x + 0.5f, blockPosition.y + 0.5f, blockPosition.z + 0.5f);
        }
    }

    private Vector3d calculateNewPosition(Vector3i baseTransform) {
        Random random = new Random();
        double newX = baseTransform.x + 0.01 + (random.nextDouble() * 0.98);
        double newY = baseTransform.y + 0.01 + (random.nextDouble() * 0.98);
        double newZ = baseTransform.z + 0.01 + (random.nextDouble() * 0.98);
        Vector3d randomTransform = new Vector3d(newX, newY, newZ);
        double offset = 0.05f;

        Random randomValue = new Random();
        int rnd = randomValue.nextInt(100);

        // Como colocarle posición a una partícula...
        if(rnd >= 0 && rnd < 20) { newX = baseTransform.x + 1 + offset; }
        else if (rnd >= 20 && rnd < 40) { newX = baseTransform.x - offset; }
        else if(rnd >= 40 && rnd < 60) { newY = baseTransform.y + 1 + offset; }
        else if (rnd >= 60 && rnd < 80) { newZ = baseTransform.z + 1 + offset; }
        else { newZ = baseTransform.z - offset; }

        return new Vector3d(newX, newY, newZ);
    }

    @Nullable @Override
    public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }
}