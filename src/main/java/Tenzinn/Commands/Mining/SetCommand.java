package Tenzinn.Commands.Mining;

import Tenzinn.MiningLimits;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.awt.Color;
import java.util.*;

public class SetCommand extends AbstractPlayerCommand {

    private final OptionalArg<String> playerArg;

    private final OptionalArg<Integer> totalArg;
    private final OptionalArg<Integer> resetTimeArg;
    private final OptionalArg<Integer> idleResetArg;

    private final OptionalArg<Integer> copperArg;
    private final OptionalArg<Integer> ironArg;
    private final OptionalArg<Integer> silverArg;
    private final OptionalArg<Integer> goldArg;
    private final OptionalArg<Integer> thoriumArg;
    private final OptionalArg<Integer> cobaltArg;
    private final OptionalArg<Integer> adamantiteArg;
    private final OptionalArg<Integer> mithrilArg;
    private final OptionalArg<Integer> onyxiumArg;

    public SetCommand(String name, String description) {
        super(name, description);

        playerArg     = withOptionalArg("player",     "Nombre del jugador o 'all'", ArgTypes.STRING);
        totalArg      = withOptionalArg("total",     "Límite total de minerales",  ArgTypes.INTEGER);
        resetTimeArg  = withOptionalArg("resetTime", "Segundos hasta reset por cupo lleno", ArgTypes.INTEGER);
        idleResetArg  = withOptionalArg("idleReset", "Segundos de inactividad hasta reset", ArgTypes.INTEGER);
        copperArg     = withOptionalArg("copper",     "Límite de cobre",            ArgTypes.INTEGER);
        ironArg       = withOptionalArg("iron",       "Límite de hierro",           ArgTypes.INTEGER);
        silverArg     = withOptionalArg("silver",     "Límite de plata",            ArgTypes.INTEGER);
        goldArg       = withOptionalArg("gold",       "Límite de oro",              ArgTypes.INTEGER);
        thoriumArg    = withOptionalArg("thorium",    "Límite de thorium",          ArgTypes.INTEGER);
        cobaltArg     = withOptionalArg("cobalt",     "Límite de cobalto",          ArgTypes.INTEGER);
        adamantiteArg = withOptionalArg("adamantite", "Límite de adamantita",       ArgTypes.INTEGER);
        mithrilArg    = withOptionalArg("mithril",    "Límite de mithril",          ArgTypes.INTEGER);
        onyxiumArg    = withOptionalArg("onyxium",    "Límite de onyxium",          ArgTypes.INTEGER);
    }

    @Override
    protected void execute(@NonNullDecl CommandContext context, @NonNullDecl Store<EntityStore> store, @NonNullDecl Ref<EntityStore> ref, @NonNullDecl PlayerRef playerRef, @NonNullDecl World world) {
        if (!playerArg.provided(context)) {
            playerRef.sendMessage(Message.raw("Uso: /mining set --player <nombre|all> [--total <n>] [--<mineral> <n>]").color(Color.RED));
            return;
        }

        String target = playerArg.get(context).trim();
        Map<String, Integer> changes = buildChanges(context);

        if (changes.isEmpty()) {
            playerRef.sendMessage(Message.raw("Debés especificar al menos un parámetro a modificar (ej. --total 50).").color(Color.RED));
            return;
        }

        if (target.equalsIgnoreCase("all")) {
            applyGlobal(playerRef, changes);
        } else {
            applyToPlayer(playerRef, target, changes);
        }
    }

    private void applyGlobal(PlayerRef sender, Map<String, Integer> changes) {
        MiningLimits.PlayerConfig current = MiningLimits.getGlobalConfig();

        int newTotal     = changes.getOrDefault("total",     current.totalMinerals);
        int newResetTime = changes.getOrDefault("resetTime", current.timeToReset);
        int newIdleReset = changes.getOrDefault("idleReset", current.idleResetSeconds);
        Map<String, Integer> minerals = new LinkedHashMap<>(current.minerals);
        applyMineralChanges(minerals, changes);

        MiningLimits.saveGlobalConfig(new MiningLimits.PlayerConfig(newTotal, newResetTime, newIdleReset, minerals));

        sender.sendMessage(buildFeedback("[Mining] Global actualizado →", changes).color(Color.GREEN));
    }

    private void applyToPlayer(PlayerRef sender, String targetName, Map<String, Integer> changes) {
        PlayerRef targetRef = findOnlinePlayer(targetName);
        if (targetRef == null) {
            sender.sendMessage(Message.raw("El jugador '" + targetName + "' no está activo en la partida.").color(Color.RED));
            return;
        }

        String targetUuid = targetRef.getUuid().toString();

        MiningLimits.PlayerDiff diff = MiningLimits.getPlayerDiff(targetUuid);

        if (changes.containsKey("total"))     diff.totalMinerals    = changes.get("total");
        if (changes.containsKey("resetTime")) diff.timeToReset      = changes.get("resetTime");
        if (changes.containsKey("idleReset")) diff.idleResetSeconds = changes.get("idleReset");
        for (Map.Entry<String, Integer> e : changes.entrySet()) {
            if (!e.getKey().equals("total") && !e.getKey().equals("resetTime") && !e.getKey().equals("idleReset")) diff.minerals.put(e.getKey(), e.getValue());
        }

        MiningLimits.savePlayerOverride(targetUuid, diff);

        if (!sender.getUuid().equals(targetRef.getUuid())) {
            targetRef.sendMessage(buildFeedback("Un administrador modificó tu configuración de minería:", changes).color(Color.CYAN));
        }

        sender.sendMessage(buildFeedback("[Mining] " + targetName + " actualizado →", changes).color(Color.GREEN));
    }

    private Map<String, Integer> buildChanges(CommandContext ctx) {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (totalArg.provided(ctx))      m.put("total",      totalArg.get(ctx));
        if (resetTimeArg.provided(ctx))  m.put("resetTime",  resetTimeArg.get(ctx));
        if (idleResetArg.provided(ctx))  m.put("idleReset",  idleResetArg.get(ctx));
        if (copperArg.provided(ctx))     m.put("copper",     copperArg.get(ctx));
        if (ironArg.provided(ctx))       m.put("iron",       ironArg.get(ctx));
        if (silverArg.provided(ctx))     m.put("silver",     silverArg.get(ctx));
        if (goldArg.provided(ctx))       m.put("gold",       goldArg.get(ctx));
        if (thoriumArg.provided(ctx))    m.put("thorium",    thoriumArg.get(ctx));
        if (cobaltArg.provided(ctx))     m.put("cobalt",     cobaltArg.get(ctx));
        if (adamantiteArg.provided(ctx)) m.put("adamantite", adamantiteArg.get(ctx));
        if (mithrilArg.provided(ctx))    m.put("mithril",    mithrilArg.get(ctx));
        if (onyxiumArg.provided(ctx))    m.put("onyxium",    onyxiumArg.get(ctx));
        return m;
    }

    private void applyMineralChanges(Map<String, Integer> minerals, Map<String, Integer> changes) {
        for (Map.Entry<String, Integer> e : changes.entrySet()) {
            String k = e.getKey();
            if (!k.equals("total") && !k.equals("resetTime") && !k.equals("idleReset")) minerals.put(k, e.getValue());
        }
    }

    private Message buildFeedback(String prefix, Map<String, Integer> changes) {
        StringBuilder sb = new StringBuilder(prefix);
        for (Map.Entry<String, Integer> e : changes.entrySet()) { sb.append("  ").append(e.getKey()).append("=").append(e.getValue()); }
        return Message.raw(sb.toString());
    }

    private PlayerRef findOnlinePlayer(String name) {
        try {
            World w = Universe.get().getDefaultWorld();

            for (PlayerRef p : w.getPlayerRefs()) {
                if (name.equalsIgnoreCase(p.getUsername())) return p;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}