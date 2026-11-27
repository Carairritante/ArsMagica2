package am2.blocks.tileentities;

import am2.api.math.AMVector3;
import am2.lore.Story;
import am2.playerextensions.ExtendedProperties;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemWritableBook;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;

public class TileEntityOcculus extends TileEntity implements IInventory{

	private ItemStack[] inventory;
	private final ArrayList<String> log;

	public TileEntityOcculus(){
		inventory = new ItemStack[this.getSizeInventory()];
		log = new ArrayList<String>();
		MinecraftForge.EVENT_BUS.register(this);
	}

	/** Client only, for the renderer: eye direction (degrees, model space), openness and awareness. */
	public float eyeYaw, eyePitch, prevEyeYaw, prevEyePitch, open, prevOpen, awake, prevAwake;
	private static final double SIGHT = 8;
	private static final float MAX_YAW = 70, MAX_PITCH = 40, FOLLOW = 0.25F;

	@Override
	public void updateEntity(){
		if (!hasBook() && log.size() > 0) log.clear();
		if (worldObj != null && worldObj.isRemote) followNearestPlayer();
	}

	private void followNearestPlayer(){
		prevEyeYaw = eyeYaw;
		prevEyePitch = eyePitch;
		prevOpen = open;
		prevAwake = awake;
		double ex = xCoord + 0.5, ey = yCoord + 0.83, ez = zCoord + 0.5;
		EntityPlayer player = worldObj.getClosestPlayer(ex, ey, ez, SIGHT);
		float yaw = 0, pitch = 0, wantOpen = 0, wantAwake = 0;
		if (player != null){
			double dx = player.posX - ex, dy = player.boundingBox.minY + 1.62 - ey, dz = player.posZ - ez;
			// Into the model's space: undo the block turn, then the renderer's (1, -1, -1) and 0.6 squash.
			double turn = Math.toRadians(getBlockMetadata() * 90);
			double c = Math.cos(turn), s = Math.sin(turn);
			double lx = dx * c - dz * s, lz = -(dx * s + dz * c), ly = -dy / 0.6;
			float wantYaw = (float)Math.toDegrees(Math.atan2(lx, lz));
			if (Math.abs(wantYaw) <= 110){
				yaw = clamp(wantYaw, MAX_YAW);
				pitch = clamp((float)-Math.toDegrees(Math.atan2(ly, Math.sqrt(lx * lx + lz * lz))), MAX_PITCH);
			}
			wantOpen = 1;
			wantAwake = ExtendedProperties.For(player).getMagicLevel() > 0 ? 1 : 0;
		}
		eyeYaw += (yaw - eyeYaw) * FOLLOW;
		eyePitch += (pitch - eyePitch) * FOLLOW;
		open += (wantOpen - open) * 0.15F;
		awake += (wantAwake - awake) * 0.1F;
	}

	private static float clamp(float value, float limit){
		return Math.max(-limit, Math.min(limit, value));
	}

	private boolean eventIsCloseEnough(AMVector3 eventSource){
		return new AMVector3(this).distanceSqTo(eventSource) < 64D;
	}

	private boolean hasBook(){
		return inventory[0] != null && inventory[0].getItem() instanceof ItemWritableBook;
	}

	private void logMessage(String message){
		log.add(message);
		if (!inventory[0].hasTagCompound()) inventory[0].stackTagCompound = new NBTTagCompound();
		Story.WritePartToNBT(inventory[0].stackTagCompound, Story.splitStoryPartIntoPages(concatLog()));
	}

	private String concatLog(){
		StringBuilder sb = new StringBuilder();
		for (String s : log){
			sb.append(s + "\n\n");
		}
		return sb.toString();
	}

	@Override
	public int getSizeInventory(){
		return 2;
	}

	@Override
	public ItemStack getStackInSlot(int slot){
		if (slot >= inventory.length)
			return null;
		return inventory[slot];
	}

	@Override
	public ItemStack decrStackSize(int i, int j){
		if (inventory[i] != null){
			if (inventory[i].stackSize <= j){
				ItemStack itemstack = inventory[i];
				inventory[i] = null;
				return itemstack;
			}
			ItemStack itemstack1 = inventory[i].splitStack(j);
			if (inventory[i].stackSize == 0){
				inventory[i] = null;
			}
			return itemstack1;
		}else{
			return null;
		}
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int i){
		if (inventory[i] != null){
			ItemStack itemstack = inventory[i];
			inventory[i] = null;
			return itemstack;
		}else{
			return null;
		}
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack itemstack){
		inventory[i] = itemstack;
		if (itemstack != null && itemstack.stackSize > getInventoryStackLimit()){
			itemstack.stackSize = getInventoryStackLimit();
		}
	}

	@Override
	public String getInventoryName(){
		return "Occulus";
	}

	@Override
	public boolean hasCustomInventoryName(){
		return false;
	}

	@Override
	public int getInventoryStackLimit(){
		return 1;
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer entityplayer){
		if (worldObj.getTileEntity(xCoord, yCoord, zCoord) != this){
			return false;
		}
		return entityplayer.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 64D;
	}

	@Override
	public void openInventory(){
	}

	@Override
	public void closeInventory(){
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemstack){
		//TODO: validate new spell item
		if (i == 1 && itemstack.getItem() instanceof ItemWritableBook){
			return true;
		}
		return false;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound){
		super.readFromNBT(nbttagcompound);
		NBTTagList nbttaglist = nbttagcompound.getTagList("OcculusInventory", Constants.NBT.TAG_COMPOUND);
		inventory = new ItemStack[getSizeInventory()];
		for (int i = 0; i < nbttaglist.tagCount(); i++){
			String tag = String.format("ArrayIndex", i);
			NBTTagCompound nbttagcompound1 = (NBTTagCompound)nbttaglist.getCompoundTagAt(i);
			byte byte0 = nbttagcompound1.getByte(tag);
			if (byte0 >= 0 && byte0 < inventory.length){
				inventory[byte0] = ItemStack.loadItemStackFromNBT(nbttagcompound1);
			}
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound){
		super.writeToNBT(nbttagcompound);
		NBTTagList nbttaglist = new NBTTagList();
		for (int i = 0; i < inventory.length; i++){
			if (inventory[i] != null){
				String tag = String.format("ArrayIndex", i);
				NBTTagCompound nbttagcompound1 = new NBTTagCompound();
				nbttagcompound1.setByte(tag, (byte)i);
				inventory[i].writeToNBT(nbttagcompound1);
				nbttaglist.appendTag(nbttagcompound1);
			}
		}

		nbttagcompound.setTag("OcculusInventory", nbttaglist);
	}
}
