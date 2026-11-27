package am2.cinematics.network;

import am2.cinematics.server.CinematicServer;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Cliente -> servidor: tecla apertada num minigame. */
public class InputMessage implements IMessage {
    public int session, gate, key, tick;

    public InputMessage() {}

    public InputMessage(int session, int gate, int key, int tick) {
        this.session = session;
        this.gate = gate;
        this.key = key;
        this.tick = tick;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(session);
        buf.writeByte(gate);
        buf.writeByte(key);
        buf.writeInt(tick);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        session = buf.readInt();
        gate = buf.readByte();
        key = buf.readByte();
        tick = buf.readInt();
    }

    public static class Handler implements IMessageHandler<InputMessage, IMessage> {
        @Override
        public IMessage onMessage(InputMessage message, MessageContext context) {
            // Thread de rede: apenas enfileira.
            CinematicServer.INSTANCE.queueInput(context.getServerHandler().playerEntity.getUniqueID(), message);
            return null;
        }
    }
}
