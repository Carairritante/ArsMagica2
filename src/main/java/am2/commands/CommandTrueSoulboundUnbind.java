package am2.commands;

import am2.soulbound.TrueSoulboundHelper;
import am2.soulbound.TrueSoulboundVault;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

public class CommandTrueSoulboundUnbind extends CommandBase {

	@Override
	public String getCommandName() {
		return "am2unbind";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 0;
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) {
		return sender instanceof EntityPlayer;
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/am2unbind";
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) {
		EntityPlayer player = getCommandSenderAsPlayer(sender);
		if (player == null) {
			return;
		}
		ItemStack held = player.getCurrentEquippedItem();
		if (held == null) {
			TrueSoulboundHelper.notifyLocalized(player, "cmd", EnumChatFormatting.RED, "am2.truesoulbound.cmd.no_item");
			return;
		}
		if (!TrueSoulboundHelper.isTrueSoulbound(held)) {
			TrueSoulboundHelper.notifyLocalized(player, "cmd", EnumChatFormatting.RED, "am2.truesoulbound.cmd.not_bound");
			return;
		}
		if (!TrueSoulboundHelper.isOwner(held, player)) {
			String owner = TrueSoulboundHelper.getOwnerName(held);
			TrueSoulboundHelper.notifyLocalized(player, "cmd", EnumChatFormatting.DARK_RED, "am2.truesoulbound.cmd.not_owner", owner);
			return;
		}

		String uid = TrueSoulboundHelper.getItemUID(held);
		if (TrueSoulboundHelper.unbind(held)) {
			TrueSoulboundHelper.clearPendingUnbind(player.getUniqueID());
			TrueSoulboundVault.removePersistedEntry(player, uid);
			TrueSoulboundVault vault = TrueSoulboundVault.get(player.worldObj);
			if (vault != null) {
				vault.removeByUID(uid);
			}
			player.inventory.markDirty();
			TrueSoulboundHelper.syncPlayerContainers(player);
			TrueSoulboundHelper.notifyLocalized(player, "unbound", EnumChatFormatting.GOLD, "am2.truesoulbound.unbound");
		}
	}
}
