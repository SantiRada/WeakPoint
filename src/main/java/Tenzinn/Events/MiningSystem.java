package Tenzinn.Events;

import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;

import java.awt.*;
import java.util.List;
import java.awt.Color;
import java.util.Random;
import java.util.ArrayList;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;

public class MiningSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {

    private Vector3i posBlock;

    private float offsetPunch = 0.32f;

    private Vector3d particlePosition = null;

    private Method writeMethod;

    private ScheduledFuture<?> timerTask;
    private float maxTime = 6f;
    private float currentTime = 0f;

    private World world;

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

        if (world == null) { world = Universe.get().getWorld(playerRef.getWorldUuid()); }

        Vector3i targetBlock = event.getTargetBlock();

        // VERIFICAR SI EL BLOQUE SE VA A ROMPER (ANTES de procesar)
        if (event.getCurrentDamage() < 0.1f) { FinishMining(); return; }

        // Verificar si es el mismo bloque
        boolean isNewBlock = false;

        if (posBlock == null) {
            isNewBlock = true;
            posBlock = targetBlock;
        } else if (posBlock.x != targetBlock.x || posBlock.y != targetBlock.y || posBlock.z != targetBlock.z) {
            event.setCancelled(true);

            isNewBlock = true;

            if(particlePosition != null) {
                if (particlePosition.y != -100) CancelMining(event.getBlockType().getId());
            }
            posBlock = targetBlock;
        }

        try {
            if (isNewBlock || particlePosition == null) {
                DeleteOldParticleSystem();

                SpawnParticleSystem packet = CreateParticle(event, true);
                if (packet != null) writeMethod.invoke(playerRef.getPacketHandler(), packet);

                CreateParticleSystem(playerRef, event);
            } else {
                // Mismo bloque: verificar si acertó
                Vector3f hitPosition = calculateHitPosition(playerRef, targetBlock);
                float distance = hitPosition.distanceTo(particlePosition.toVector3f());

                if (distance < offsetPunch) {
                    // Reproducir sonido
                    SoundUtil.playSoundEvent2d(SoundEvent.getAssetMap().getIndex("SFX_Crystal_Break"), SoundCategory.SFX, buffer);

                    // Eliminar y crear nuevo
                    DeleteOldParticleSystem();

                    SpawnParticleSystem packet = CreateParticle(event, true);
                    if (packet != null) writeMethod.invoke(playerRef.getPacketHandler(), packet);

                    CreateParticleSystem(playerRef, event);
                } else {
                    CancelMining(event.getBlockType().getId());
                }
            }

        } catch (Exception e) {
            event.setCancelled(true);

            playerRef.sendMessage(Message.raw("¡ERROR! " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }
    }

    private void DeleteOldParticleSystem () {
        CancelTimer();

        particlePosition = null;
        currentTime = 0f;
    }

    private void CreateParticleSystem (PlayerRef playerRef, DamageBlockEvent event) {

        if(event.getDamage() >= event.getCurrentDamage()) {
            DeleteOldParticleSystem();
            return;
        }

        timerTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                if(maxTime > currentTime) {
                    SpawnParticleSystem packet = CreateParticle(event, false);
                    if (packet != null) writeMethod.invoke(playerRef.getPacketHandler(), packet);

                    currentTime += 0.35F;
                } else {
                    CancelMining(event.getBlockType().getId());
                }
            } catch (Exception e) { if (timerTask != null) timerTask.cancel(false); }
        }, 350, 350, TimeUnit.MILLISECONDS);
    }

    private SpawnParticleSystem CreateParticle(DamageBlockEvent event, boolean isNew) {

        if(event.getDamage() >= event.getCurrentDamage()) {
            DeleteOldParticleSystem();
            return null;
        }

        if (isNew) { particlePosition = calculateNewPosition(event.getTargetBlock()); }

        if(event.getBlockType().getId().equals("Empty")) return null;

        SpawnParticleSystem packet = new SpawnParticleSystem();
        packet.particleSystemId = "Weak_Point_Particle";
        packet.position = new Position(particlePosition.x, particlePosition.y, particlePosition.z);
        packet.scale = 1.0f;

        return packet;
    }

    private void FinishMining () {
        CancelTimer();

        currentTime = 0f;
        posBlock = new Vector3i(0, -100, 0);
        particlePosition = new Vector3d(0, -100, 0);
    }

    private void CancelMining(String id) {
        FinishMining();

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

            if (hit != null) { return hit; }

            return new Vector3f(blockPosition.x + 0.5f, blockPosition.y + 0.5f, blockPosition.z + 0.5f);

        } catch (Exception e) {
            e.printStackTrace();
            return new Vector3f(blockPosition.x + 0.5f, blockPosition.y + 0.5f, blockPosition.z + 0.5f);
        }
    }

    private void CancelTimer () {
        if (timerTask != null && !timerTask.isDone()) { timerTask.cancel(true); }
        timerTask = null;
    }

    private Vector3d calculateNewPosition(Vector3i baseTransform) {
        List<Integer> availableFaces = new ArrayList<>();

        // Cara 0: X+1 (derecha)
        if (world.getBlockType(new Vector3i(baseTransform.x + 1, baseTransform.y, baseTransform.z)).getId().equals("Empty")) { availableFaces.add(0); }
        // Cara 1: X-1 (izquierda)
        if (world.getBlockType(new Vector3i(baseTransform.x - 1, baseTransform.y, baseTransform.z)).getId().equals("Empty")) { availableFaces.add(1); }
        // Cara 2: Y+1 (arriba)
        if (world.getBlockType(new Vector3i(baseTransform.x, baseTransform.y + 1, baseTransform.z)).getId().equals("Empty")) { availableFaces.add(2); }
        // Cara 3: Y-1 (abajo)
        if (world.getBlockType(new Vector3i(baseTransform.x, baseTransform.y - 1, baseTransform.z)).getId().equals("Empty")) { availableFaces.add(3); }
        // Cara 4: Z+1 (frente)
        if (world.getBlockType(new Vector3i(baseTransform.x, baseTransform.y, baseTransform.z + 1)).getId().equals("Empty")) { availableFaces.add(4); }
        // Cara 5: Z-1 (atrás)
        if (world.getBlockType(new Vector3i(baseTransform.x, baseTransform.y, baseTransform.z - 1)).getId().equals("Empty")) { availableFaces.add(5); }
        // Si no hay caras disponibles, retornar posición central del bloque
        if (availableFaces.isEmpty()) { return new Vector3d(baseTransform.x + 0.5, baseTransform.y + 0.5, baseTransform.z + 0.5); }

        // Elegir una cara aleatoria de las disponibles
        Random random = new Random();
        int selectedFace = availableFaces.get(random.nextInt(availableFaces.size()));

        // Offset pequeño para que la partícula aparezca ligeramente fuera del bloque
        double offset = 0.05;

        // Posición aleatoria dentro de la cara seleccionada
        double randomU = 0.1 + (random.nextDouble() * 0.8); // Entre 0.1 y 0.9
        double randomV = 0.1 + (random.nextDouble() * 0.8); // Entre 0.1 y 0.9

        double newX = baseTransform.x + 0.5;
        double newY = baseTransform.y + 0.5;
        double newZ = baseTransform.z + 0.5;

        switch (selectedFace) {
            case 0: // X+1 (cara derecha)
                newX = baseTransform.x + 1 + offset;
                newY = baseTransform.y + randomU;
                newZ = baseTransform.z + randomV;
                break;

            case 1: // X-1 (cara izquierda)
                newX = baseTransform.x - offset;
                newY = baseTransform.y + randomU;
                newZ = baseTransform.z + randomV;
                break;

            case 2: // Y+1 (cara superior)
                newX = baseTransform.x + randomU;
                newY = baseTransform.y + 1 + offset;
                newZ = baseTransform.z + randomV;
                break;

            case 3: // Y-1 (cara inferior)
                newX = baseTransform.x + randomU;
                newY = baseTransform.y - offset;
                newZ = baseTransform.z + randomV;
                break;

            case 4: // Z+1 (cara frontal)
                newX = baseTransform.x + randomU;
                newY = baseTransform.y + randomV;
                newZ = baseTransform.z + 1 + offset;
                break;

            case 5: // Z-1 (cara trasera)
                newX = baseTransform.x + randomU;
                newY = baseTransform.y + randomV;
                newZ = baseTransform.z - offset;
                break;
        }

        return new Vector3d(newX, newY, newZ);
    }

    @Nullable @Override
    public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }
}