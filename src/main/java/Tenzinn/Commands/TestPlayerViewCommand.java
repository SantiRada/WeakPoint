package Tenzinn.Commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;


import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.awt.*;

public class TestPlayerViewCommand extends CommandBase {

    public TestPlayerViewCommand(@NonNullDecl String name, @NonNullDecl String description) { super(name, description); }
    @Override
    protected void executeSync(@NonNullDecl CommandContext commandContext) {
        Player player = commandContext.senderAs(Player.class);
        PlayerRef playerRef = Universe.get().getPlayerByUsername(player.getDisplayName(), NameMatching.EXACT);

        Vector3d position = playerRef.getTransform().getPosition();
        Vector3f rotation = playerRef.getTransform().getRotation();
        Vector3f headRotation = playerRef.getHeadRotation();

        player.sendMessage(Message.raw("Position: (" + position.x + ", " + position.y + ", " + position.z + ")").color(Color.orange));
        player.sendMessage(Message.raw("Rotation: (" + rotation.x + ", " + rotation.y + ", " + rotation.z + ")").color(Color.orange));
        player.sendMessage(Message.raw("HeadRotation: (" + headRotation.x + ", " + headRotation.y + ", " + headRotation.z + ")").color(Color.orange));
        player.sendMessage(Message.raw("------------------").color(Color.cyan));

        playerRef.getTransform().setRotation(new Vector3f());
    }
}
