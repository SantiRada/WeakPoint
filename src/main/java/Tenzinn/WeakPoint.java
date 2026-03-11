package Tenzinn;

import Tenzinn.Commands.Particles.TestSpawnerCommand;
import Tenzinn.Events.MiningSystem;
import Tenzinn.Commands.UI.ReviewCommand;
import Tenzinn.Commands.UI.DashboardCommand;
import Tenzinn.Commands.Mining.MiningCommand;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;

public class WeakPoint extends JavaPlugin {

    public WeakPoint(@Nonnull JavaPluginInit init) { super(init); }
    
    @Override
    protected void setup() {
        MiningLimits.load();
        WeakPointConfig.load();

        getEntityStoreRegistry().registerSystem(new MiningSystem());

        getCommandRegistry().registerCommand(new ReviewCommand("review", "Visual dashboard for mining system"));
        getCommandRegistry().registerCommand(new MiningCommand("mining", "data of the mining system for all players"));
        getCommandRegistry().registerCommand(new DashboardCommand("dashboard", "Visual dashboard with all data for mining system"));

        getCommandRegistry().registerCommand(new TestSpawnerCommand("spawner", "Test particles with exact ID"));
    }
}