package am2.cinematics.client;

import am2.cinematics.network.RitualReportMessage;
import am2.cinematics.ritual.Activation;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** Parte do cliente no ritual: protege o Orbe e escreve no chat o que falta. */
public class RitualClient {
    private final ConcurrentLinkedQueue<RitualReportMessage> reports = new ConcurrentLinkedQueue<RitualReportMessage>();

    void enqueue(RitualReportMessage report) {
        reports.add(report);
    }

    /**
     * Depois do clique no bloco o jogo ainda "usa" o item no ar, o que gastaria o
     * Orbe em um ponto de habilidade. No gesto do ritual esse segundo uso não
     * acontece; o clique no bloco segue para o servidor.
     */
    @SubscribeEvent
    public void onUse(PlayerInteractEvent event) {
        if (!event.world.isRemote || event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR) return;
        Minecraft mc = Minecraft.getMinecraft();
        MovingObjectPosition target = mc.objectMouseOver;
        if (event.entityPlayer != mc.thePlayer || target == null
                || target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        if (Activation.matches(event.world, event.entityPlayer, target.blockX, target.blockY, target.blockZ)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        RitualReportMessage report;
        while ((report = reports.poll()) != null) show(report);
    }

    private static void show(RitualReportMessage report) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        say(mc, EnumChatFormatting.GOLD, I18n.format("amcinematics.ritual.falta"));
        for (RitualReportMessage.Line line : report.lines) {
            String text = line.item == null ? I18n.format(line.key, line.a, line.b)
                    : line.item.getItem() == null ? "?" : line.item.stackSize + "x " + line.item.getDisplayName();
            say(mc, EnumChatFormatting.GRAY, " - " + text);
        }
    }

    private static void say(Minecraft mc, EnumChatFormatting color, String text) {
        ChatComponentText line = new ChatComponentText(text);
        line.getChatStyle().setColor(color);
        mc.thePlayer.addChatMessage(line);
    }
}
