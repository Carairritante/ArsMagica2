package am2.cinematics.network;

import am2.cinematics.AMCinematics;
import am2.cinematics.scene.Scene;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Servidor -> cliente: cena completa, âncora e tempo já decorrido. */
public class PlayMessage implements IMessage {
    public int session, dimension, elapsed;
    /** Id da entidade do jogador-âncora, ou -1. Efeitos no corpo são aplicados nele. */
    public int subject = -1;
    public double x, y, z;
    public float yaw;
    public byte[] json = new byte[0];
    /** Ritual de verdade: a cena para nas pausas; {@code gate} é a próxima por resolver. */
    public boolean interactive;
    public int gate;
    /** Ritual de treino: quem joga também pode pular. */
    public boolean practice;

    public PlayMessage() {}

    public PlayMessage(int session, int dimension, double x, double y, double z, float yaw, int elapsed, byte[] json,
            int subject) {
        this.subject = subject;
        this.session = session;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.elapsed = elapsed;
        this.json = json;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(session);
        buf.writeInt(dimension);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeInt(elapsed);
        buf.writeInt(subject);
        buf.writeBoolean(interactive);
        buf.writeByte(gate);
        buf.writeBoolean(practice);
        buf.writeInt(json.length);
        buf.writeBytes(json);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        session = buf.readInt();
        dimension = buf.readInt();
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
        yaw = buf.readFloat();
        elapsed = buf.readInt();
        subject = buf.readInt();
        interactive = buf.readBoolean();
        gate = buf.readByte();
        practice = buf.readBoolean();
        int length = buf.readInt();
        if (length < 0 || length > Scene.MAX_BYTES || length > buf.readableBytes()) {
            throw new IllegalArgumentException("tamanho de cena inválido: " + length);
        }
        json = new byte[length];
        buf.readBytes(json);
    }

    public static class Handler implements IMessageHandler<PlayMessage, IMessage> {
        @Override
        public IMessage onMessage(PlayMessage message, MessageContext context) {
            AMCinematics.proxy.play(message);
            return null;
        }
    }
}
