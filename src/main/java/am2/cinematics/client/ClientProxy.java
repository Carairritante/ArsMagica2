package am2.cinematics.client;

import am2.cinematics.CommonProxy;
import am2.cinematics.network.GameMessage;
import am2.cinematics.network.PlayMessage;
import am2.cinematics.network.RitualReportMessage;
import am2.cinematics.network.StopMessage;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {
    private final ClientDirector director = new ClientDirector();
    private final RitualClient ritual = new RitualClient();

    @Override
    public void init() {
        ClientRegistry.registerKeyBinding(director.skipKey);
        FMLCommonHandler.instance().bus().register(director);
        MinecraftForge.EVENT_BUS.register(director);
        FMLCommonHandler.instance().bus().register(ritual);
        MinecraftForge.EVENT_BUS.register(ritual);
    }

    @Override
    public void report(RitualReportMessage message) {
        ritual.enqueue(message);
    }

    @Override
    public void game(GameMessage message) {
        director.enqueue(message);
    }

    @Override
    public void play(PlayMessage message) {
        director.enqueue(message);
    }

    @Override
    public void stop(StopMessage message) {
        director.enqueue(message);
    }
}
