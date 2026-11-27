package am2.cinematics.network;

import am2.cinematics.server.CinematicServer;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Cliente -> servidor: o jogador pulou ou saiu da cinemática. */
public class LeaveMessage implements IMessage {
    public int session;

    public LeaveMessage() {}

    public LeaveMessage(int session) {
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

    public static class Handler implements IMessageHandler<LeaveMessage, IMessage> {
        @Override
        public IMessage onMessage(LeaveMessage message, MessageContext context) {
            // Thread de rede: apenas enfileira.
            CinematicServer.INSTANCE.queueLeave(context.getServerHandler().playerEntity.getUniqueID(), message.session);
            return null;
        }
    }
}
