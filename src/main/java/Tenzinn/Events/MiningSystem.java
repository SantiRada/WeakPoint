package Tenzinn.Events;

import Tenzinn.MiningLimits;
import Tenzinn.WeakPointConfig;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.awt.Color;
import java.util.Map;
import java.util.Random;
import java.util.ArrayList;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ConcurrentHashMap;

public class MiningSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {

    private static class BlockState {
        PlayerRef owner;
        Vector3f blockPosition;
        Vector3d  particlePosition;
        int       elapsedSeconds;
        int       maxSeconds;
        int       successHits;
        String    blockId;
        ScheduledFuture<?> particleTask;
        ScheduledFuture<?> countdownTask;
        Ref<EntityStore> hologramRef;

        BlockState(PlayerRef owner, String blockId) {
            this.owner          = owner;
            this.blockId        = blockId;
            this.elapsedSeconds = 0;
            this.maxSeconds     = Math.round(WeakPointConfig.getTimeToChip(blockId));
            this.successHits    = 0;
        }
    }

    private static class RespawnState {
        String originalBlockId;
        int    remainingSeconds;
        ScheduledFuture<?> countdownTask;
        Ref<EntityStore> hologramRef;

        RespawnState(String originalBlockId, int totalSeconds) {
            this.originalBlockId  = originalBlockId;
            this.remainingSeconds = totalSeconds;
        }
    }

    private final Map<String, BlockState>   blockStates   = new ConcurrentHashMap<>();
    private final Map<String, RespawnState> respawnStates = new ConcurrentHashMap<>();

    private final float offsetPunch = 0.32f;
    private Method writeMethod;
    private World world;

    public MiningSystem() {
        super(DamageBlockEvent.class);
        try {
            Class<?> toClientPacketClass = Class.forName("com.hypixel.hytale.protocol.ToClientPacket");
            writeMethod = com.hypixel.hytale.server.core.io.PacketHandler.class.getMethod("write", toClientPacketClass);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static String blockKey(Vector3i pos) { return pos.x + "," + pos.y + "," + pos.z; }

    private static int[] keyToCoords(String key) {
        String[] p = key.split(",");
        return new int[]{ Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]) };
    }

    private static String formatTime(int totalSeconds) {
        int m = Math.max(totalSeconds, 0) / 60;
        int s = Math.max(totalSeconds, 0) % 60;
        return String.format("%02d:%02d", m, s);
    }

    private void launchSound(CommandBuffer<EntityStore> buffer, String sound) {
        if      (sound.equalsIgnoreCase("win"))  SoundUtil.playSoundEvent2d(SoundEvent.getAssetMap().getIndex("SFX_WP_Win"),  SoundCategory.SFX, buffer);
        else if (sound.equalsIgnoreCase("fail")) SoundUtil.playSoundEvent2d(SoundEvent.getAssetMap().getIndex("SFX_WP_Fail"), SoundCategory.SFX, buffer);
        else                                     SoundUtil.playSoundEvent2d(SoundEvent.getAssetMap().getIndex("SFX_WP_Drop"), SoundCategory.SFX, buffer);
    }

    private static void cancelTask(ScheduledFuture<?> task) {
        if (task != null && !task.isDone()) task.cancel(false);
    }

    @Override
    public void handle(int i, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer, DamageBlockEvent event) {
        if (event.getBlockType() == BlockType.EMPTY) return;

        // VALIDADORES
        String blockTypeId = event.getBlockType().getId();
        String key = blockKey(event.getTargetBlock());

        boolean isValidOre    = WeakPointConfig.isValidBlock(blockTypeId);
        boolean isRespawning  = respawnStates.containsKey(key);

        if (isValidOre || isRespawning) { event.setCancelled(true); }
        else { return; }

        if (!isValidOre) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(i);
        Player    player    = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        if (world == null) {
            assert playerRef.getWorldUuid() != null;
            world = Universe.get().getWorld(playerRef.getWorldUuid());
        }

        Vector3i targetBlock = event.getTargetBlock();

        // --- Validación de pico ---
        // Obtener el item en la mano del jugador
        ItemStack heldItem = player.getInventory().getItemInHand();
        String heldItemId  = (heldItem != null) ? heldItem.getItemId() : null;
        int hitsNeeded     = WeakPointConfig.getHitsForPickaxe(blockTypeId, heldItemId);

        // --- Ownership ---
        BlockState state = blockStates.get(key);
        if (state != null) {
            if (!state.owner.equals(playerRef)) {
                playerRef.sendMessage(Message.raw("Otro jugador ya está extrayendo este bloque.").color(Color.CYAN));
                return;
            }
        } else {
            state = new BlockState(playerRef, blockTypeId);
            blockStates.put(key, state);
        }

        state.blockPosition = new Vector3f(targetBlock.x + 0.5f, targetBlock.y + 0.85f, targetBlock.z + 0.5f);

        if (hitsNeeded < 0) {
            // Pico incorrecto o sin pico: no puede minar este bloque
            String pickaxeName = (heldItemId != null) ? heldItemId : "manos";

            SpawnParticleSystem fail = buildParticlePacket(state, blockTypeId, "fail");
            if (fail != null) {
                try { writeMethod.invoke(playerRef.getPacketHandler(), fail); }
                catch (IllegalAccessException | InvocationTargetException e) { throw new RuntimeException(e); }
            }

            playerRef.sendMessage(Message.raw("No puedes minar " + blockTypeId + " con pico de " + pickaxeName).color(Color.ORANGE));
            return;
        }



        final BlockState finalState = state;
        final int finalHitsNeeded   = hitsNeeded;

        try {
            if (state.particlePosition == null) {
                // Primer golpe
                state.particlePosition = calculateNewPosition(targetBlock);

                SpawnParticleSystem punch = buildParticlePacket(state, blockTypeId, "punch");
                if (punch != null) writeMethod.invoke(playerRef.getPacketHandler(), punch);

                startMiningTimers(key, finalState, playerRef, blockTypeId, targetBlock);

            } else {
                // Golpes siguientes
                Vector3f hitPos   = calculateHitPosition(playerRef, targetBlock);
                float    distance = hitPos.distanceTo(state.particlePosition.toVector3f());

                if (distance < offsetPunch) {
                    launchSound(buffer, "win");

                    SpawnParticleSystem winFx = buildParticlePacket(state, blockTypeId, "win");
                    if (winFx != null) writeMethod.invoke(playerRef.getPacketHandler(), winFx);

                    state.successHits++;

                    if (state.successHits >= finalHitsNeeded) {
                        // Extracción completa
                        cancelTask(state.particleTask);
                        cancelTask(state.countdownTask);
                        blockStates.remove(key);

                        final String fBlockTypeId = blockTypeId;
                        final Store<EntityStore> fStore = store;
                        final Ref<EntityStore> fRef = ref;
                        final Vector3i fTarget = targetBlock;
                        final PlayerRef fPlayerRef = playerRef;

                        world.execute(() -> {
                            // 1. Destruir holograma de minado
                            if (finalState.hologramRef != null) {
                                try { world.getEntityStore().getStore().removeEntity(finalState.hologramRef, RemoveReason.REMOVE); } catch (Exception ignored) {}
                                finalState.hologramRef = null;
                            }
                            // 2. Verificar cuota de minería antes de dar drops
                            String uuid = fPlayerRef.getUuid().toString();
                            MiningLimits.CollectResult quota = MiningLimits.tryCollect(uuid, fBlockTypeId);
                            if (quota != MiningLimits.CollectResult.ALLOWED) {
                                // Bloque extraído pero sin drops
                                int secsLeft = MiningLimits.getSecondsUntilReset(uuid);
                                String reason = quota == MiningLimits.CollectResult.BLOCKED_TOTAL
                                        ? "Alcanzaste tu límite total de minerales. Resetea en " + secsLeft + "s."
                                        : "Alcanzaste el límite de este tipo de mineral por ahora.";
                                fPlayerRef.sendMessage(Message.raw(reason).color(Color.YELLOW));

                                return;
                            }
                            // 3. Dar drops y cambiar bloque
                            giveDropsInGameThread(fTarget, fBlockTypeId, fStore, fRef);
                            // 4. Iniciar respawn
                            startRespawnInGameThread(fTarget, fBlockTypeId);
                        });

                    } else {
                        // Acierto parcial
                        state.particlePosition = calculateNewPosition(targetBlock);

                        SpawnParticleSystem punch = buildParticlePacket(state, blockTypeId, "punch");
                        if (punch != null) writeMethod.invoke(playerRef.getPacketHandler(), punch);
                    }

                } else {
                    state.particlePosition = calculateNewPosition(targetBlock);
                    SpawnParticleSystem fail = buildParticlePacket(state, blockTypeId, "fail");
                    if (fail != null) writeMethod.invoke(playerRef.getPacketHandler(), fail);

                    launchSound(buffer, "fail");
                    cancelMining(key, state);
                }
            }

        } catch (Exception e) {
            playerRef.sendMessage(Message.raw("¡ERROR! " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }
    }

    private void startMiningTimers(String key, BlockState state, PlayerRef playerRef, String blockTypeId, Vector3i blockPos) {

        final Vector3d hologramPos = new Vector3d(blockPos.x + 0.5, blockPos.y + 1.0, blockPos.z + 0.5);
        final String label0 = "\u26CF " + formatTime(state.maxSeconds);

        // Spawnar holograma inicial en el game thread
        world.execute(() -> { state.hologramRef = createHologramEntity(hologramPos, label0); });

        // Task de partículas — 350ms
        state.particleTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                SpawnParticleSystem packet = buildParticlePacket(state, blockTypeId, "punch");
                if (packet != null) writeMethod.invoke(playerRef.getPacketHandler(), packet);
            } catch (Exception ignored) {}
        }, 350, 350, TimeUnit.MILLISECONDS);

        state.countdownTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            state.elapsedSeconds++;
            int rem = state.maxSeconds - state.elapsedSeconds;

            if (rem <= 0) {
                // Expiró
                cancelTask(state.particleTask);
                cancelTask(state.countdownTask);

                world.execute(() -> {
                    // Destruir holograma
                    if (state.hologramRef != null) {
                        try { world.getEntityStore().getStore().removeEntity(state.hologramRef, RemoveReason.REMOVE); } catch (Exception ignored) {}
                        state.hologramRef = null;
                    }
                    // Restaurar bloque
                    int[] c = keyToCoords(key);
                    world.setBlock(c[0], c[1], c[2], "Empty");
                    world.setBlock(c[0], c[1], c[2], state.blockId);
                    blockStates.remove(key);
                });

            } else {
                // Actualizar holograma
                final String newLabel = "\u26CF " + formatTime(rem);
                world.execute(() -> {
                    if (state.hologramRef != null) {
                        try { world.getEntityStore().getStore().removeEntity(state.hologramRef, RemoveReason.REMOVE); } catch (Exception ignored) {}
                    }
                    state.hologramRef = createHologramEntity(hologramPos, newLabel);
                });
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);
    }

    private void cancelMining(String key, BlockState state) {
        cancelTask(state.particleTask);
        cancelTask(state.countdownTask);

        blockStates.remove(key);

        world.execute(() -> {
            if (state.hologramRef != null) {
                try { world.getEntityStore().getStore().removeEntity(state.hologramRef, RemoveReason.REMOVE); } catch (Exception ignored) {}
                state.hologramRef = null;
            }
            int[] c = keyToCoords(key);
            world.setBlock(c[0], c[1], c[2], "Empty");
            world.setBlock(c[0], c[1], c[2], state.blockId);
        });
    }

    private void giveDropsInGameThread(Vector3i pos, String blockId, Store<EntityStore> store, Ref<EntityStore> ref) {
        String defaultBlock = WeakPointConfig.getDefaultBlock(blockId);
        world.setBlock(pos.x, pos.y, pos.z, defaultBlock);

        List<WeakPointConfig.ItemDrop> mainDrops = WeakPointConfig.getMainDrops(blockId);
        for (WeakPointConfig.ItemDrop drop : mainDrops) giveItemToPlayer(drop.itemId, drop.quantity, store, ref);

        List<WeakPointConfig.ExtraDrop> extraDrops = WeakPointConfig.getExtraDrops(blockId);
        for (WeakPointConfig.ExtraDrop drop : extraDrops) giveItemToPlayer(drop.itemId, drop.quantity, store, ref);
    }

    private void giveItemToPlayer(String itemId, int quantity, Store<EntityStore> store, Ref<EntityStore> ref) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        player.getInventory().getCombinedHotbarFirst().addItemStack(new ItemStack(itemId, quantity));
    }

    private void startRespawnInGameThread(Vector3i pos, String originalBlockId) {
        String key = blockKey(pos);

        // Cancelar respawn previo si existiera
        RespawnState old = respawnStates.get(key);
        if (old != null) {
            cancelTask(old.countdownTask);
            if (old.hologramRef != null) {
                try { world.getEntityStore().getStore().removeEntity(old.hologramRef, RemoveReason.REMOVE); } catch (Exception ignored) {}
            }
            respawnStates.remove(key);
        }

        int totalSeconds = Math.round(WeakPointConfig.getRespawnTime(originalBlockId));
        final Vector3d hologramPos = new Vector3d(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5);

        RespawnState respawn = new RespawnState(originalBlockId, totalSeconds);
        // Spawnar holograma inicial
        respawn.hologramRef = createHologramEntity(hologramPos, "\u27F3 " + formatTime(totalSeconds));
        respawnStates.put(key, respawn);

        respawn.countdownTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            respawn.remainingSeconds--;

            if (respawn.remainingSeconds <= 0) {
                cancelTask(respawn.countdownTask);

                world.execute(() -> {
                    // Destruir holograma de respawn
                    if (respawn.hologramRef != null) {
                        try { world.getEntityStore().getStore().removeEntity(respawn.hologramRef, RemoveReason.REMOVE); } catch (Exception ignored) {}
                        respawn.hologramRef = null;
                    }
                    // Restaurar mineral
                    world.setBlock(pos.x, pos.y, pos.z, originalBlockId);
                    respawnStates.remove(key);
                });

            } else {
                // Remove + add en el MISMO world.execute
                final String newLabel = "\u27F3 " + formatTime(respawn.remainingSeconds);
                world.execute(() -> {
                    if (respawn.hologramRef != null) {
                        try { world.getEntityStore().getStore().removeEntity(respawn.hologramRef, RemoveReason.REMOVE); } catch (Exception ignored) {}
                    }
                    respawn.hologramRef = createHologramEntity(hologramPos, newLabel);
                });
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);
    }

    private Ref<EntityStore> createHologramEntity(Vector3d position, String label) {
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

        ProjectileComponent proj = new ProjectileComponent("Projectile");
        holder.putComponent(ProjectileComponent.getComponentType(), proj);
        holder.putComponent(TransformComponent.getComponentType(),
                new TransformComponent(position, new Vector3f()));
        holder.ensureComponent(UUIDComponent.getComponentType());

        if (proj.getProjectile() == null) { proj.initialize(); if (proj.getProjectile() == null) return null; }

        holder.addComponent(NetworkId.getComponentType(), new NetworkId(world.getEntityStore().getStore().getExternalData().takeNextNetworkId()));
        holder.addComponent(Nameplate.getComponentType(), new Nameplate(label));

        return world.getEntityStore().getStore().addEntity(holder, AddReason.SPAWN);
    }

    private SpawnParticleSystem buildParticlePacket(BlockState state, String blockTypeId, String particle) {
        if (state.particlePosition == null) return null;
        if (blockTypeId.equals("Empty")) return null;

        return assembleParticlePacket(particle.equalsIgnoreCase("punch") ? state.particlePosition : state.blockPosition.toVector3d(), particle);
    }

    private SpawnParticleSystem assembleParticlePacket(Vector3d pos, String particle) {
        SpawnParticleSystem packet = new SpawnParticleSystem();
        switch (particle.toLowerCase()) {
            case "win":  packet.particleSystemId = "WeakPoint_Spawner_Win";  break;
            case "fail": packet.particleSystemId = "WeakPoint_Spawner_Fail"; break;
            default:     packet.particleSystemId = "Weak_Point_Particle";    break;
        }
        packet.position = new Position(pos.x, pos.y, pos.z);
        packet.scale    = 1.0f;
        return packet;
    }

    private Vector3f calculateHitPosition(PlayerRef playerRef, Vector3i blockPosition) {
        try {
            World w = Universe.get().getWorld(playerRef.getWorldUuid());
            if (w == null) return center(blockPosition);

            Store<EntityStore> store = w.getEntityStore().getStore();
            TransformComponent transform = store.getComponent(playerRef.getReference(), TransformComponent.getComponentType());
            if (transform == null) return center(blockPosition);

            Vector3d playerPos = transform.getPosition();
            float eyeHeight = 1.62f;

            Direction look = transform.getSentTransform().lookOrientation;
            float pitch = look.pitch, yaw = look.yaw;

            double dirX = -Math.sin(yaw) * Math.cos(pitch);
            double dirY =  Math.sin(pitch);
            double dirZ = -Math.cos(yaw) * Math.cos(pitch);
            double len  = Math.sqrt(dirX*dirX + dirY*dirY + dirZ*dirZ);
            dirX /= len; dirY /= len; dirZ /= len;

            double rayX = playerPos.x, rayY = playerPos.y + eyeHeight, rayZ = playerPos.z;
            double minX = blockPosition.x, maxX = blockPosition.x + 1.0;
            double minY = blockPosition.y, maxY = blockPosition.y + 1.0;
            double minZ = blockPosition.z, maxZ = blockPosition.z + 1.0;

            Vector3f hit = null;
            double   minT = Double.MAX_VALUE;

            if (Math.abs(dirX) > 0.0001) {
                double t = (minX - rayX) / dirX;
                if (t > 0 && t < minT) { double y = rayY+dirY*t, z = rayZ+dirZ*t; if (y>=minY&&y<=maxY&&z>=minZ&&z<=maxZ) { minT=t; hit=new Vector3f((float)minX,(float)y,(float)z); } }
                t = (maxX - rayX) / dirX;
                if (t > 0 && t < minT) { double y = rayY+dirY*t, z = rayZ+dirZ*t; if (y>=minY&&y<=maxY&&z>=minZ&&z<=maxZ) { minT=t; hit=new Vector3f((float)maxX,(float)y,(float)z); } }
            }
            if (Math.abs(dirY) > 0.0001) {
                double t = (minY - rayY) / dirY;
                if (t > 0 && t < minT) { double x = rayX+dirX*t, z = rayZ+dirZ*t; if (x>=minX&&x<=maxX&&z>=minZ&&z<=maxZ) { minT=t; hit=new Vector3f((float)x,(float)minY,(float)z); } }
                t = (maxY - rayY) / dirY;
                if (t > 0 && t < minT) { double x = rayX+dirX*t, z = rayZ+dirZ*t; if (x>=minX&&x<=maxX&&z>=minZ&&z<=maxZ) { minT=t; hit=new Vector3f((float)x,(float)maxY,(float)z); } }
            }
            if (Math.abs(dirZ) > 0.0001) {
                double t = (minZ - rayZ) / dirZ;
                if (t > 0 && t < minT) { double x = rayX+dirX*t, y = rayY+dirY*t; if (x>=minX&&x<=maxX&&y>=minY&&y<=maxY) { minT=t; hit=new Vector3f((float)x,(float)y,(float)minZ); } }
                t = (maxZ - rayZ) / dirZ;
                if (t > 0 && t < minT) { double x = rayX+dirX*t, y = rayY+dirY*t; if (x>=minX&&x<=maxX&&y>=minY&&y<=maxY) { minT=t; hit=new Vector3f((float)x,(float)y,(float)maxZ); } }
            }

            return hit != null ? hit : center(blockPosition);

        } catch (Exception e) {
            e.printStackTrace();
            return center(blockPosition);
        }
    }

    private static Vector3f center(Vector3i p) {
        return new Vector3f(p.x + 0.5f, p.y + 0.5f, p.z + 0.5f);
    }

    private Vector3d calculateNewPosition(Vector3i b) {
        List<Integer> faces = new ArrayList<>();
        if (world.getBlockType(new Vector3i(b.x+1, b.y, b.z)).getId().equals("Empty")) faces.add(0);
        if (world.getBlockType(new Vector3i(b.x-1, b.y, b.z)).getId().equals("Empty")) faces.add(1);
        if (world.getBlockType(new Vector3i(b.x, b.y+1, b.z)).getId().equals("Empty")) faces.add(2);
        if (world.getBlockType(new Vector3i(b.x, b.y-1, b.z)).getId().equals("Empty")) faces.add(3);
        if (world.getBlockType(new Vector3i(b.x, b.y, b.z+1)).getId().equals("Empty")) faces.add(4);
        if (world.getBlockType(new Vector3i(b.x, b.y, b.z-1)).getId().equals("Empty")) faces.add(5);

        if (faces.isEmpty()) return new Vector3d(b.x+0.5, b.y+0.5, b.z+0.5);

        Random rng = new Random();
        int    face = faces.get(rng.nextInt(faces.size()));
        double off  = 0.05, u = 0.1 + rng.nextDouble()*0.8, v = 0.1 + rng.nextDouble()*0.8;
        double nx = b.x+0.5, ny = b.y+0.5, nz = b.z+0.5;

        switch (face) {
            case 0: nx = b.x+1+off; ny = b.y+u;     nz = b.z+v;     break;
            case 1: nx = b.x-off;   ny = b.y+u;     nz = b.z+v;     break;
            case 2: nx = b.x+u;     ny = b.y+1+off; nz = b.z+v;     break;
            case 3: nx = b.x+u;     ny = b.y-off;   nz = b.z+v;     break;
            case 4: nx = b.x+u;     ny = b.y+v;     nz = b.z+1+off; break;
            case 5: nx = b.x+u;     ny = b.y+v;     nz = b.z-off;   break;
        }

        return new Vector3d(nx, ny, nz);
    }

    @Nullable @Override
    public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }
}