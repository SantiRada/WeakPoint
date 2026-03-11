package Tenzinn.Commands.Particles;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class TestSpawnerCommand extends AbstractPlayerCommand {

    private Method writeMethod;

    private final OptionalArg<String> systemId;

    public TestSpawnerCommand(@NonNullDecl String name, @NonNullDecl String description) {
        super(name, description);

        systemId = withOptionalArg("id", "Test particles with exact ID", ArgTypes.STRING);
    }

    @Override
    protected void execute(@NonNullDecl CommandContext commandContext, @NonNullDecl Store<EntityStore> store, @NonNullDecl Ref<EntityStore> ref, @NonNullDecl PlayerRef playerRef, @NonNullDecl World world) {
        Player player = commandContext.senderAs(Player.class);
        assert player != null;

        String system = commandContext.get(systemId);

        Vector3d positionPlayer = player.getTransformComponent().getPosition();
        Vector3d pos = new Vector3d(positionPlayer.x, positionPlayer.y + 1, positionPlayer.z + 3);

        Class<?> toClientPacketClass = null;
        try {
            toClientPacketClass = Class.forName("com.hypixel.hytale.protocol.ToClientPacket");
            writeMethod = com.hypixel.hytale.server.core.io.PacketHandler.class.getMethod("write", toClientPacketClass);
        } catch (ClassNotFoundException | NoSuchMethodException e) { throw new RuntimeException(e); }

        SpawnParticleSystem packet = assembleParticlePacket(pos, system);

        try { writeMethod.invoke(playerRef.getPacketHandler(), packet); }
        catch (IllegalAccessException | InvocationTargetException e) { throw new RuntimeException(e); }
    }

    private SpawnParticleSystem assembleParticlePacket(Vector3d pos, String system) {
        SpawnParticleSystem packet = new SpawnParticleSystem();

        packet.particleSystemId = system;

        packet.position = new Position(pos.x, pos.y, pos.z);
        packet.scale    = 1.0f;
        return packet;
    }
}