package am2.blocks;

import am2.AMCore;
import am2.blocks.tileentities.TileEntityArcaneStriker;
import am2.texture.ResourceManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * Amusement-park style Arcane High Striker machine that summons an {@link am2.entities.EntityArcaneTargetDummy}
 * for a user-configured duration (up to 60s) and records total damage, DPS, and peak hit.
 */
public class BlockArcaneStriker extends BlockContainer {

	public BlockArcaneStriker() {
		super(Material.iron);
		setHardness(3.5f);
		setResistance(15.0f);
		setStepSound(soundTypeMetal);
		setBlockBounds(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityArcaneStriker();
	}

	@Override
	public boolean isOpaqueCube() {
		return false;
	}

	@Override
	public boolean renderAsNormalBlock() {
		return false;
	}

	@Override
	public int getRenderType() {
		return BlocksCommonProxy.blockRenderID;
	}

	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
		int dir = MathHelper.floor_double((placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
		int meta = (dir + 2) & 3;
		world.setBlockMetadataWithNotify(x, y, z, meta, 2);
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
		TileEntity te = world.getTileEntity(x, y, z);
		if (!(te instanceof TileEntityArcaneStriker)) {
			return false;
		}
		if (world.isRemote) {
			AMCore.proxy.openArcaneStrikerGui(player, (TileEntityArcaneStriker) te);
		}
		return true;
	}

	@Override
	public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
		if (!world.isRemote) {
			TileEntity te = world.getTileEntity(x, y, z);
			if (te instanceof TileEntityArcaneStriker) {
				((TileEntityArcaneStriker) te).finishTest(true);
				((TileEntityArcaneStriker) te).discardActiveDummy();
			}
		}
		super.breakBlock(world, x, y, z, block, meta);
	}

	@Override
	public void registerBlockIcons(IIconRegister register) {
		this.blockIcon = ResourceManager.RegisterTexture("arcane_striker_icon", register);
	}
}
