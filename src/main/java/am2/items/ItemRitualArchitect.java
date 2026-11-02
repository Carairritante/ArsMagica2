package am2.items;

import am2.AMCore;
import am2.blocks.BlocksCommonProxy;
import am2.ritual.RitualArchitect;
import am2.ritual.RitualArchitectServer;
import am2.texture.ResourceManager;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import java.util.List;

/** Reusable visual planner in survival; structure-only builder in creative mode. */
public class ItemRitualArchitect extends ArsMagicaItem {
    public ItemRitualArchitect() { setMaxStackSize(1); }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        itemIcon = ResourceManager.RegisterTexture("chalk_wizard", register);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack stack, int pass) { return 0xB593FF; }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack) { return true; }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {
        if (!world.isRemote && entity instanceof EntityPlayer && RitualArchitect.token(stack) == 0L) {
            RitualArchitect.ensureToken(stack);
            sync((EntityPlayer)entity);
        }
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) { RitualArchitect.ensureToken(stack); sync(player); }
        else AMCore.proxy.openRitualArchitect(player);
        return stack;
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
                                  int side, float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) {
            if (world.isRemote) AMCore.proxy.openRitualArchitect(player);
            else { RitualArchitect.ensureToken(stack); sync(player); }
            return true;
        }
        if (side != 1) return false;
        // Forward vanilla C08 explicitly before consuming the click: otherwise Forge returns before sending it.
        // This also prevents the clicked Obelisk/container from opening a local GUI ahead of the server's anchor use.
        if (world.isRemote) return AMCore.proxy.forwardRitualArchitectClick(player, x, y, z, side, hitX, hitY, hitZ);
        RitualArchitect.ensureToken(stack);
        RitualArchitect.Entry entry = RitualArchitect.selected(stack);
        if (!RitualArchitect.canUse(player, entry)) { message(player, "am2.architect.locked"); return true; }
        if (world.getBlock(x, y, z) != entry.centerBlock && world.getBlock(x, y, z) != BlocksCommonProxy.wizardChalk
                && world.getBlock(x, y, z) != BlocksCommonProxy.candle) y++;
        if (y < 1 || y > 255 || player.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) > 64.0 || !world.blockExists(x, y, z)) {
            message(player, "am2.architect.too_far"); return true;
        }
        if (!world.canMineBlock(player, x, y, z) || !player.canPlayerEdit(x, y, z, side, stack)) {
            message(player, "am2.architect.protected"); return true;
        }
        RitualArchitect.setAnchor(stack, world, x, y, z);
        sync(player);
        message(player, "am2.architect.anchored");
        return true;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
                             int side, float hitX, float hitY, float hitZ) {
        // Consume any fallback use so an anchor click does not also open the menu.
        return player.isSneaking() || side == 1;
    }

    /** Called from the network receiver; all world/item access is deferred to the next server tick. */
    public static void handleAction(EntityPlayer player, int action, int index, int rotation, int slot, long token) {
        RitualArchitectServer.enqueue(player, action, index, rotation, slot, token);
    }

    public static void handleAction(EntityPlayer player, int action, int index, int rotation, int slot, long token, String entryId) {
        RitualArchitectServer.enqueue(player, action, index, rotation, slot, token, entryId);
    }

    public static void handleAction(EntityPlayer player, int action, int index, int rotation, int slot, long token, String entryId, boolean catalogView) {
        RitualArchitectServer.enqueue(player, action, index, rotation, slot, token, entryId, catalogView);
    }

    public static void applyAction(EntityPlayer player, int action, int index, int rotation, int slot, long token) {
        applyAction(player, action, index, rotation, slot, token, null);
    }

    public static void applyAction(EntityPlayer player, int action, int index, int rotation, int slot, long token, String entryId) {
        applyAction(player, action, index, rotation, slot, token, entryId, false);
    }

    public static void applyAction(EntityPlayer player, int action, int index, int rotation, int slot, long token, String entryId, boolean catalogView) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote || player.isDead
                || action < 0 || action > 2 || rotation < 0 || rotation > 3 || slot < 0 || slot > 8
                || player.inventory.currentItem != slot || token == 0L) return;
        ItemStack stack = player.getHeldItem();
        if (stack == null || !(stack.getItem() instanceof ItemRitualArchitect) || RitualArchitect.token(stack) != token) return;
        if (action == 2) {
            RitualArchitect.clearAnchor(stack);
            sync(player);
            message(player, "am2.architect.cleared");
            return;
        }
        RitualArchitect.Entry entry = entryId == null ? RitualArchitect.getEntry(index) : RitualArchitect.getEntry(entryId);
        if (entry == null || entry.interaction() == null) return;
        if (action == 0) RitualArchitect.updateView(player, token, catalogView);
        if (!RitualArchitect.canUse(player, entry)) { message(player, "am2.architect.locked"); return; }
        if (action == 1 && !player.capabilities.isCreativeMode) { message(player, "am2.architect.creative_only"); return; }
        RitualArchitect.select(stack, entry.index, rotation % entry.orientationCount());
        sync(player);
        if (action == 1) message(player, RitualArchitect.build(player, stack));
    }

    private static void sync(EntityPlayer player) {
        player.inventory.markDirty();
        if (player.openContainer != null) player.openContainer.detectAndSendChanges();
    }

    private static void message(EntityPlayer player, String key) {
        // Forge FakePlayers have no connection; real clients translate these messages in their own language.
        if (player instanceof EntityPlayerMP && ((EntityPlayerMP)player).playerNetServerHandler != null)
            player.addChatMessage(new ChatComponentTranslation(key));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(StatCollector.translateToLocal("am2.architect.tooltip.menu"));
        lines.add(StatCollector.translateToLocal("am2.architect.tooltip.anchor"));
        lines.add(StatCollector.translateToLocal("am2.architect.tooltip.survival"));
        lines.add(StatCollector.translateToLocal("am2.architect.tooltip.hotbar"));
        RitualArchitect.Entry entry = RitualArchitect.selected(stack);
        if (entry != null && RitualArchitect.canUse(player, entry)) lines.add(StatCollector.translateToLocal(entry.nameKey));
    }
}
