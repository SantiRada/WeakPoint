package Tenzinn.Commands.Mining;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class MiningCommand extends AbstractCommandCollection {

    public MiningCommand(@NonNullDecl String name, @NonNullDecl String description) {
        super(name, description);

        addSubCommand(new SetCommand("set", "Change mining system data for all players or only one"));
        addSubCommand(new GetCommand("get", "Review status of the mining system for this player"));
    }
}