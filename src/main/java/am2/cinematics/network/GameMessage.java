package am2.cinematics.network;

import am2.cinematics.AMCinematics;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Servidor -> cliente: um minigame começou, recebeu uma tecla ou terminou. */
public class GameMessage implements IMessage {
    public static final int START = 0, INPUT = 1, END = 2;

    public int session, kind, gate;
    /** START: o minigame, a semente e há quantos ticks começou. */
    public int type, age;
    public long seed;
    /** INPUT: tecla e tick do jogador. */
    public int key, tick;
    /** END. */
    public boolean passed;

    public GameMessage() {}

    private GameMessage(int session, int kind, int gate) {
        this.session = session;
        this.kind = kind;
        this.gate = gate;
    }

    public static GameMessage start(int session, int gate, int type, long seed, int age) {
        GameMessage message = new GameMessage(session, START, gate);
        message.type = type;
        message.seed = seed;
        message.age = age;
        return message;
    }

    public static GameMessage input(int session, int gate, int key, int tick) {
        GameMessage message = new GameMessage(session, INPUT, gate);
        message.key = key;
        message.tick = tick;
        return message;
    }

    public static GameMessage end(int session, int gate, boolean passed) {
        GameMessage message = new GameMessage(session, END, gate);
        message.passed = passed;
        return message;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(session);
        buf.writeByte(kind);
        buf.writeByte(gate);
        buf.writeByte(type);
        buf.writeInt(age);
        buf.writeLong(seed);
        buf.writeByte(key);
        buf.writeInt(tick);
        buf.writeBoolean(passed);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        session = buf.readInt();
        kind = buf.readByte();
        gate = buf.readByte();
        type = buf.readByte();
        age = buf.readInt();
        seed = buf.readLong();
        key = buf.readByte();
        tick = buf.readInt();
        passed = buf.readBoolean();
    }

    public static class Handler implements IMessageHandler<GameMessage, IMessage> {
        @Override
        public IMessage onMessage(GameMessage message, MessageContext context) {
            AMCinematics.proxy.game(message);
            return null;
        }
    }
}
