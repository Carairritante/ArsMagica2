package am2.cinematics.network;

import am2.cinematics.AMCinematics;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;

/**
 * Servidor -> cliente: o que falta para o ritual. Os nomes dos itens são
 * montados no cliente, no idioma dele.
 */
public class RitualReportMessage implements IMessage {
    public static final class Line {
        /** Chave de tradução, ou vazia numa linha de item. */
        public final String key;
        public final int a, b;
        /** Reagente que falta; {@code stackSize} é a quantidade. */
        public final ItemStack item;

        private Line(String key, int a, int b, ItemStack item) {
            this.key = key;
            this.a = a;
            this.b = b;
            this.item = item;
        }

        public static Line text(String key) {
            return new Line(key, 0, 0, null);
        }

        public static Line text(String key, int a, int b) {
            return new Line(key, a, b, null);
        }

        public static Line item(ItemStack item) {
            return new Line("", 0, 0, item);
        }
    }

    public final List<Line> lines = new ArrayList<Line>();

    public RitualReportMessage() {}

    public RitualReportMessage(List<Line> lines) {
        this.lines.addAll(lines);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(lines.size());
        for (Line line : lines) {
            ByteBufUtils.writeUTF8String(buf, line.key);
            if (line.item == null) {
                buf.writeInt(line.a);
                buf.writeInt(line.b);
            } else {
                ByteBufUtils.writeItemStack(buf, line.item);
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        for (int n = buf.readUnsignedByte(); n > 0; n--) {
            String key = ByteBufUtils.readUTF8String(buf);
            if (key.isEmpty()) {
                lines.add(Line.item(ByteBufUtils.readItemStack(buf)));
            } else {
                int a = buf.readInt();
                int b = buf.readInt();
                lines.add(Line.text(key, a, b));
            }
        }
    }

    public static class Handler implements IMessageHandler<RitualReportMessage, IMessage> {
        @Override
        public IMessage onMessage(RitualReportMessage message, MessageContext context) {
            AMCinematics.proxy.report(message);
            return null;
        }
    }
}
