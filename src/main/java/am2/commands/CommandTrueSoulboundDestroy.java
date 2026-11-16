package am2.commands;

import am2.soulbound.TrueSoulboundHandler;
import am2.soulbound.TrueSoulboundHelper;
import am2.soulbound.TrueSoulboundVault;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

public class CommandTrueSoulboundDestroy extends CommandBase {

	@Override
	public String getCommandName() {
		return "am2destroy";
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
		return "/am2destroy confirm";
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
		if (args == null || args.length != 1 || !"confirm".equalsIgnoreCase(args[0])) {
			TrueSoulboundHelper.notifyLocalized(player, "cmd", EnumChatFormatting.YELLOW, "am2.truesoulbound.cmd.destroy_confirm");
			return;
		}

		String itemName = held.getDisplayName();
		String uid = TrueSoulboundHelper.getItemUID(held);
		TrueSoulboundHandler.forgetTrackedUID(uid);
		player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
		player.inventory.markDirty();
		TrueSoulboundVault.removePersistedEntry(player, uid);
		TrueSoulboundVault vault = TrueSoulboundVault.get(player.worldObj);
		if (vault != null) {
			vault.removeByUID(uid);
		}
		TrueSoulboundHelper.clearPendingUnbind(player.getUniqueID());
		TrueSoulboundHelper.syncPlayerContainers(player);
		TrueSoulboundHelper.notifyLocalized(player, "cmd", EnumChatFormatting.RED, "am2.truesoulbound.cmd.destroyed", itemName);
	}
}
