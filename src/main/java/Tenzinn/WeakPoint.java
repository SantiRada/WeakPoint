package Tenzinn;

import Tenzinn.Events.MiningSystem;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;

public class WeakPoint extends JavaPlugin {

    public WeakPoint(@Nonnull JavaPluginInit init) { super(init); }
    
    @Override
    protected void setup() {
        WeakPointConfig.load();

        getEntityStoreRegistry().registerSystem(new MiningSystem());
    }
}