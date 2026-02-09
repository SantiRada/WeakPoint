package Tenzinn.Events;

import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.npc.corecomponents.combat.ActionApplyEntityEffect;

import javax.annotation.Nullable;
import java.util.Random;
import java.awt.*;

public class MiningSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {

    public MiningSystem() { super(DamageBlockEvent.class); }

    @Override
    public void handle(int i, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer, DamageBlockEvent event) {
        if (event.getBlockType() == BlockType.EMPTY) return;

        if(!event.getBlockType().getId().contains("Ore")) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(i);
        Player player = store.getComponent(ref, Player.getComponentType());

        if (player == null) return;

        event.setCancelled(true);

        Vector3i baseTransform = event.getTargetBlock();

        Random random = new Random();
        double newX = baseTransform.x + 0.01 + (random.nextDouble() * 0.98);
        double newY = baseTransform.y + 0.01 + (random.nextDouble() * 0.98);
        double newZ = baseTransform.z + 0.01 + (random.nextDouble() * 0.98);
        Vector3d randomTransform = new Vector3d(newX, newY, newZ);

        // Como colocarle posición a una partícula...
    }
    @Nullable @Override
    public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }

    private Particle CreateParticle() {
        ParticleSpawner spawner = new ParticleSpawner();
        spawner.particle = new Particle();
        spawner.particle.texturePath = "Point.png";

        spawner.particle.frameSize.width = 1;
        spawner.particle.frameSize.height = 1;

        spawner.shape = EmitShape.Sphere;
        spawner.spawnBurst = true;
        spawner.totalParticles = new Range(1, 1);
        spawner.particleLifeSpan = new Rangef(5.0f, 5.0f);
        spawner.initialVelocity = null;

        spawner.particleRotationInfluence = ParticleRotationInfluence.None;

        return spawner.particle;
    }
}