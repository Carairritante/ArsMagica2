package am2.entities;

import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;

public final class TornadoLanding {
    private static final String OWNER_MOST = "AM2TornadoOwnerMost";
    private static final String OWNER_LEAST = "AM2TornadoOwnerLeast";
    private TornadoLanding() {}

    static void mark(EntityFallingBlock debris, EntityLivingBase caster) {
        // Forge saves entity data across chunk unloads. A zero UUID represents an NPC cast.
        UUID owner = caster instanceof EntityPlayerMP ? caster.getUniqueID() : null;
        long most = owner == null ? 0 : owner.getMostSignificantBits();
        long least = owner == null ? 0 : owner.getLeastSignificantBits();
        NBTTagCompound data = debris.getEntityData();
        if (data.hasKey(OWNER_MOST) && data.getLong(OWNER_MOST) == most && data.getLong(OWNER_LEAST) == least) return;
        data.setLong(OWNER_MOST, most);
        data.setLong(OWNER_LEAST, least);
    }

    public static boolean place(Object level, int x, int y, int z, Object material, int meta, int flags, Object entity) {
        World world = (World)level;
        Block block = (Block)material;
        NBTTagCompound data = ((EntityFallingBlock)entity).getEntityData();
        if (!data.hasKey(OWNER_MOST)) return world.setBlock(x, y, z, block, meta, flags);
        long most = data.getLong(OWNER_MOST), least = data.getLong(OWNER_LEAST);
        if ((most == 0 && least == 0) || world.isRemote || !world.blockExists(x, y, z)) return false;
        EntityPlayer player = world.func_152378_a(new UUID(most, least));
        MinecraftServer server = MinecraftServer.getServer();
        if (!(player instanceof EntityPlayerMP) || server == null || !world.canMineBlock(player, x, y, z)
                || server.isBlockProtected(world, x, y, z, player)
                || !player.canPlayerEdit(x, y, z, 1, new ItemStack(block, 1, meta))) return false;

        Block replaced = world.getBlock(x, y, z);
        int replacedMeta = world.getBlockMetadata(x, y, z);
        if (world.getTileEntity(x, y, z) != null || replaced.hasTileEntity(replacedMeta)) return false;
        // 1.7.10 can call breakBlock and onBlockAdded even during snapshot capture.
        // Describe the proposed placement to Forge before either block gets a callback.
        final Block landingBlock = block;
        BlockSnapshot proposal = new BlockSnapshot(world, x, y, z, replaced, replacedMeta, flags) {
            @Override public Block getCurrentBlock() { return landingBlock; }
        };
        if (ForgeEventFactory.onPlayerBlockPlace(player, proposal, ForgeDirection.UP).isCanceled()) return false;
        if (world.getBlock(x, y, z) != replaced || world.getBlockMetadata(x, y, z) != replacedMeta
                || world.getTileEntity(x, y, z) != null) return false;
        return world.setBlock(x, y, z, block, meta, flags);
    }
}
