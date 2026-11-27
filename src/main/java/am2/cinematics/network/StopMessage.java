package am2.cinematics.network;

import am2.cinematics.AMCinematics;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Servidor -> cliente: encerra uma sessão. */
public class StopMessage implements IMessage {
    public int session;

    public StopMessage() {}

    public StopMessage(int session) {
        this.session = session;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(session);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        session = buf.readInt();
    }

    public static class Handler implements IMessageHandler<StopMessage, IMessage> {
        @Override
        public IMessage onMessage(StopMessage message, MessageContext context) {
            AMCinematics.proxy.stop(message);
            return null;
        }
    }
}
