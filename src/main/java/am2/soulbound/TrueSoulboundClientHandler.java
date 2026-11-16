package am2.soulbound;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

@SideOnly(Side.CLIENT)
public class TrueSoulboundClientHandler {

	@SubscribeEvent
	public void onItemTooltip(ItemTooltipEvent event) {
		if (event.itemStack == null || event.toolTip == null || !TrueSoulboundHelper.isTrueSoulbound(event.itemStack)) {
			return;
		}
		String ownerName = TrueSoulboundHelper.getOwnerName(event.itemStack);
		if (ownerName == null || ownerName.isEmpty()) {
			ownerName = "Unknown";
		}

		boolean isOwner = event.entityPlayer == null || TrueSoulboundHelper.isOwner(event.itemStack, event.entityPlayer);

		String header = localize("am2.truesoulbound.tooltip.header", "\u2726 True Soulbound [%s]", ownerName);
		event.toolTip.add(EnumChatFormatting.LIGHT_PURPLE.toString() + EnumChatFormatting.BOLD + header);

		if (!isOwner) {
			event.toolTip.add(EnumChatFormatting.DARK_RED + localize(
					"am2.truesoulbound.tooltip.stolen",
					"WARNING: Bound to %s! Touching it sears thieves with Wither & zero Mana.",
					ownerName));
		}

		if (GuiScreen.isShiftKeyDown()) {
			event.toolTip.add(EnumChatFormatting.AQUA + localize(
					"am2.truesoulbound.tooltip.desc1",
					"Eternally bound to %s's soul.",
					ownerName));
			event.toolTip.add(EnumChatFormatting.GRAY + localize(
					"am2.truesoulbound.tooltip.desc2",
					"Immune to theft, containers, lava, void & death drops."));
			if (isOwner) {
				event.toolTip.add(EnumChatFormatting.DARK_GRAY + localize(
						"am2.truesoulbound.tooltip.release",
						"Shift+Q twice (3s) or /am2unbind to release | /am2destroy confirm"));
			}
		} else {
			event.toolTip.add(EnumChatFormatting.DARK_GRAY.toString() + EnumChatFormatting.ITALIC + localize(
					"am2.truesoulbound.tooltip.hint",
					"Hold Shift for details"));
		}
	}

	private static String localize(String key, String fallback, Object... args) {
		if (StatCollector.canTranslate(key)) {
			return StatCollector.translateToLocalFormatted(key, args);
		}
		return args != null && args.length > 0 ? String.format(fallback, args) : fallback;
	}
}
