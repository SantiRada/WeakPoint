package Tenzinn.Commands.Mining;

import Tenzinn.MiningLimits;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.awt.Color;

public class GetCommand extends AbstractPlayerCommand {

    public GetCommand(String name, String description) { super(name, description); }

    @Override
    protected void execute(@NonNullDecl CommandContext commandContext, @NonNullDecl Store<EntityStore> store, @NonNullDecl Ref<EntityStore> ref, @NonNullDecl PlayerRef playerRef,@NonNullDecl World world) {

        String uuid = playerRef.getUuid().toString();

        MiningLimits.PlayerData   data = MiningLimits.getPlayerData(uuid);
        MiningLimits.PlayerConfig cfg  = MiningLimits.getEffectiveConfig(uuid);

        // Totales
        int collected = (data != null) ? data.totalCollected : 0;
        playerRef.sendMessage(Message.raw("── Minería ──").color(Color.WHITE));
        playerRef.sendMessage(Message.raw("Total: " + collected + "/" + cfg.totalMinerals).color(Color.YELLOW));

        // Tiempo hasta reset (solo si ya minó algo)
        if (data != null && data.quotaFullTime > 0) {
            int secsLeft = MiningLimits.getSecondsUntilReset(uuid);
            playerRef.sendMessage(Message.raw("Resetea en: " + secsLeft + "s").color(Color.GRAY));
        }
    }
}