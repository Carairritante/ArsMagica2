package am2.blocks;

import am2.AMCore;
import am2.blocks.tileentities.TileEntityVocalAura;
import am2.guis.ArsMagicaGuiIdList;
import am2.playerextensions.VoiceVaultStorage;
import am2.skills.VocalResonance;
import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Floating sanctuary aura modeled after BlockOtherworldAura.
 * Opens the clicking player's personal 8-slot VoiceVaultStorage when VocalResonance is unlocked.
 */
public class BlockVocalAura extends AMBlockContainer {

	public BlockVocalAura() {
		super(Material.circuits);
		setHardness(2.0f);
		setResistance(10.0f);
		setBlockBounds(0.25f, 0.25f, 0.25f, 0.75f, 0.75f, 0.75f);
		setStepSound(soundTypeCloth);
	}

	@Override
	public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
		return new ArrayList<ItemStack>();
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityVocalAura();
	}

	@Override
	public int getLightValue(IBlockAccess world, int x, int y, int z) {
		return 15;
	}

	@Override
	public boolean renderAsNormalBlock() {
		return false;
	}

	@Override
	public boolean isOpaqueCube() {
		return false;
	}

	@Override
	public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB mask, List list, Entity entity) {
	}

	@Override
	public int getRenderType() {
		return BlocksCommonProxy.blockRenderID;
	}

	@Override
	public void registerBlockIcons(IIconRegister iconRegister) {
	}

	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
		super.onBlockPlacedBy(world, x, y, z, placer, stack);
		if (placer instanceof EntityPlayer) {
			TileEntity te = world.getTileEntity(x, y, z);
			if (te instanceof TileEntityVocalAura) {
				((TileEntityVocalAura) te).setPlacedByUsername(((EntityPlayer) placer).getCommandSenderName());
			}
		}
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int face, float hitX, float hitY, float hitZ) {
		super.onBlockActivated(world, x, y, z, player, face, hitX, hitY, hitZ);
		if (player == null) return false;
		if (!world.isRemote) {
			if (!VocalResonance.isUnlocked(player)) {
				player.addChatMessage(new ChatComponentTranslation("am2.vocal_aura.locked"));
				return true;
			}
			if (player instanceof EntityPlayerMP) {
				VoiceVaultStorage.For(player).syncToClient((EntityPlayerMP) player);
			}
			FMLNetworkHandler.openGui(player, AMCore.instance, ArsMagicaGuiIdList.GUI_VOCAL_AURA, world, x, y, z);
		}
		return true;
	}
}
