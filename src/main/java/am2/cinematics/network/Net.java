package am2.cinematics.network;

import am2.cinematics.AMCinematics;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class Net {
    public static SimpleNetworkWrapper channel;

    private Net() {}

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("AM2Cinematics");
        channel.registerMessage(PlayMessage.Handler.class, PlayMessage.class, 0, Side.CLIENT);
        channel.registerMessage(StopMessage.Handler.class, StopMessage.class, 1, Side.CLIENT);
        channel.registerMessage(LeaveMessage.Handler.class, LeaveMessage.class, 2, Side.SERVER);
        channel.registerMessage(RitualReportMessage.Handler.class, RitualReportMessage.class, 3, Side.CLIENT);
        channel.registerMessage(GameMessage.Handler.class, GameMessage.class, 4, Side.CLIENT);
        channel.registerMessage(InputMessage.Handler.class, InputMessage.class, 5, Side.SERVER);
    }
}
