package am2.entities.ai;

import am2.api.blocks.MultiblockStructureDefinition.BlockCoord;
import am2.api.math.AMVector3;
import am2.blocks.tileentities.TileEntityCraftingAltar;
import am2.entities.EntityShadowHelper;
import am2.interop.AE2OtherworldCompat;
import am2.items.ItemsCommonProxy;
import am2.utility.InventoryUtilities;
import cpw.mods.fml.common.Loader;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class EntityAISpellmaking extends EntityAIBase{

	private EntityShadowHelper host; //the host for this AI task

	private int action_state = 0;
	private int wait_counter = 0;
	private int stuck_ticks = 0;
	private double lastDistSqToDrop = Double.MAX_VALUE;

	private static final int DISTANCE_THRESHOLD = 4;
	private static final double INTERACT_HORIZ_SQ = 6.25D;
	private static final double DROPOFF_HORIZ_SQ = 0.5625D; // 0.75 blocks from center of middle altar block
	private static final double INTERACT_VERT_RANGE = 2.75D;
	private static final int MAX_STUCK_TICKS = 160;
	private static final float MOVE_SPEED = 0.5f;
	private static final float WAIT_TIME = 20;

	private static boolean isAE2Loaded(){
		return Loader.isModLoaded("appliedenergistics2");
	}

	public EntityAISpellmaking(EntityShadowHelper host){
		this.host = host;
		this.setMutexBits(1);
	}

	@Override
	public boolean shouldExecute(){
		return host.hasSearchLocation();
	}

	@Override
	public void resetTask(){
		action_state = 0;
		wait_counter = 0;
		stuck_ticks = 0;
		lastDistSqToDrop = Double.MAX_VALUE;
		host.setSearchLocationAndItem(AMVector3.zero(), new ItemStack(Items.paper));
	}

	@Override
	public void updateTask(){
		AMVector3 targetLocation = host.getSearchLocation();
		AMVector3 dropLocation = host.getDropLocation();
		AMVector3 hostLocation = new AMVector3(host);

		ItemStack searchItem = host.getSearchItem();
		if (searchItem != null && searchItem.getItem() == ItemsCommonProxy.essence && searchItem.getItemDamage() > ItemsCommonProxy.essence.META_MAX){
			TileEntityCraftingAltar altar = host.getAltarTarget();
			if (altar == null){
				resetTask();
				return;
			}
			if (altar.switchIsOn())
				return;
			BlockCoord bc = altar.getSwitchLocation();
			if (bc == null){
				resetTask();
				return;
			}
			AMVector3 switchPos = new AMVector3(altar.xCoord + bc.x, altar.yCoord + bc.y, altar.zCoord + bc.z);
			if (action_state == 0 && isWithinInteractReach(switchPos, false)){
				action_state = 1;
				altar.flipSwitch();
				wait_counter = 0;
				stuck_ticks = 0;
			}else if (action_state == 0){
				if (host.getNavigator().noPath()){
					tryMoveNearBlock(switchPos);
				}
				if (hostLocation.distanceSqTo(switchPos) < DISTANCE_THRESHOLD){
					action_state = 1;
					altar.flipSwitch();
					wait_counter = 0;
					stuck_ticks = 0;
				}
			}else if (action_state == 1 && wait_counter < WAIT_TIME){
				wait_counter++;
			}else if (action_state == 1 && wait_counter >= WAIT_TIME){
				resetTask();
			}
		}else{
			if (searchItem == null || searchItem.getItem() == null){
				resetTask();
				return;
			}
			if (action_state == 0){
				if (isWithinInteractReach(targetLocation, false)){
					host.getNavigator().clearPathEntity();
					stuck_ticks = 0;
					lastDistSqToDrop = Double.MAX_VALUE;
					TileEntity te = host.worldObj.getTileEntity((int)targetLocation.x, (int)targetLocation.y, (int)targetLocation.z);
					int neededQty = Math.max(1, searchItem.stackSize);
					ItemStack extracted = null;
					if (te instanceof IInventory){
						((IInventory)te).openInventory();
						if (!host.worldObj.isRemote){
							extracted = deductConcreteFromInventory((IInventory)te, searchItem, neededQty);
						}
					}
					if (extracted == null && te != null && isAE2Loaded() && AE2OtherworldCompat.isTerminalHost(te)){
						if (!host.worldObj.isRemote){
							extracted = AE2OtherworldCompat.extractFromTerminalHost(te, searchItem, neededQty);
						}
					}
					if (extracted == null && !(te instanceof IInventory) && (te == null || !isAE2Loaded() || !AE2OtherworldCompat.isTerminalHost(te))){
						resetTask();
						return;
					}

					if (!host.worldObj.isRemote){
						if (extracted == null){
							if (te instanceof IInventory){
								((IInventory)te).closeInventory();
							}
							resetTask();
							return;
						}
						host.setCarriedItem(extracted.copy());
						host.setHeldItem(extracted.copy());
					}
					action_state = 1;
					wait_counter = 0;
				}else{
					if (host.getNavigator().noPath()){
						tryMoveNearBlock(targetLocation);
					}
					if (++stuck_ticks > MAX_STUCK_TICKS){
						resetTask();
					}
				}
			}else if (action_state == 1 && wait_counter < WAIT_TIME){
				wait_counter++;
			}else if (action_state == 1 && isWithinInteractReach(dropLocation, true)){
				host.getNavigator().clearPathEntity();
				stuck_ticks = 0;
				TileEntity te = host.worldObj.getTileEntity((int)targetLocation.x, (int)targetLocation.y, (int)targetLocation.z);
				if (te instanceof IInventory){
					((IInventory)te).closeInventory();
				}
				if (!host.worldObj.isRemote){
					ItemStack carried = host.getCarriedItem();
					ItemStack toDrop = (carried != null) ? carried.copy() : searchItem.copy();
					dropItemAtAltarCenter(toDrop, dropLocation);
					host.setCarriedItem(null);
					host.setHeldItem(new ItemStack(Items.paper));
				}
				action_state = 2;
				wait_counter = 0;
			}else if (action_state == 1){
				if (wait_counter == WAIT_TIME){
					TileEntity te = host.worldObj.getTileEntity((int)targetLocation.x, (int)targetLocation.y, (int)targetLocation.z);
					if (te instanceof IInventory){
						((IInventory)te).closeInventory();
					}
					wait_counter++;
				}
				tryMoveToAltarCenter(dropLocation);
				double curDistSq = getHorizontalDistSqToCenter(dropLocation);
				if (curDistSq + 0.04D < lastDistSqToDrop){
					lastDistSqToDrop = curDistSq;
					if (stuck_ticks > 0){
						stuck_ticks--;
					}
				}else if (++stuck_ticks > MAX_STUCK_TICKS){
					if (!host.worldObj.isRemote){
						ItemStack carried = host.getCarriedItem();
						ItemStack toDrop = (carried != null) ? carried.copy() : searchItem.copy();
						dropItemAtAltarCenter(toDrop, dropLocation);
						host.setCarriedItem(null);
						host.setHeldItem(new ItemStack(Items.paper));
					}
					resetTask();
				}
			}else if (action_state == 2 && wait_counter < WAIT_TIME){
				wait_counter++;
			}else if (action_state == 2 && wait_counter >= WAIT_TIME){
				resetTask();
			}
		}
	}

	private double getHorizontalDistSqToCenter(AMVector3 target){
		double cx = target.x + 0.5D;
		double cz = target.z + 0.5D;
		double dx = host.posX - cx;
		double dz = host.posZ - cz;
		return dx * dx + dz * dz;
	}

	private boolean isWithinInteractReach(AMVector3 target, boolean isDropoff){
		double cx = target.x + 0.5D;
		double cy = (isDropoff ? resolveAltarCenterGroundY(target) : target.y) + 0.5D;
		double cz = target.z + 0.5D;
		double dx = host.posX - cx;
		double dz = host.posZ - cz;
		double dy = Math.abs((host.posY + (host.height * 0.5D)) - cy);
		double maxHorizSq = isDropoff ? DROPOFF_HORIZ_SQ : INTERACT_HORIZ_SQ;
		double maxVert = isDropoff ? 3.25D : INTERACT_VERT_RANGE;
		return (dx * dx + dz * dz) <= maxHorizSq && dy <= maxVert;
	}

	private int resolveAltarCenterGroundY(AMVector3 dropLocation){
		World world = host.worldObj;
		int tx = (int)dropLocation.x;
		int ty = (int)dropLocation.y;
		int tz = (int)dropLocation.z;
		if (world != null){
			for (int dy = -1; dy <= 3; dy++){
				int py = ty - dy;
				if (py < 1 || py > 254) continue;
				if (world.getBlock(tx, py - 1, tz).getMaterial().isSolid()
						&& !world.getBlock(tx, py, tz).getMaterial().blocksMovement()){
					return py;
				}
			}
		}
		return ty - 1;
	}

	private void tryMoveToAltarCenter(AMVector3 dropLocation){
		int tx = (int)dropLocation.x;
		int tz = (int)dropLocation.z;
		int py = resolveAltarCenterGroundY(dropLocation);
		double centerX = tx + 0.5D;
		double centerZ = tz + 0.5D;
		double distSq = getHorizontalDistSqToCenter(dropLocation);
		if (host.getNavigator().noPath()){
			if (!host.getNavigator().tryMoveToXYZ(centerX, py, centerZ, MOVE_SPEED)){
				tryMoveNearBlock(new AMVector3(tx, py, tz));
			}
		}
		// When already close to the altar center, nudge directly onto the middle block
		if (distSq <= 5.0D && (host.getNavigator().noPath() || distSq <= 2.25D)){
			host.getMoveHelper().setMoveTo(centerX, py, centerZ, MOVE_SPEED);
		}
	}

	private void dropItemAtAltarCenter(ItemStack toDrop, AMVector3 dropLocation){
		if (host.worldObj.isRemote || toDrop == null || toDrop.stackSize <= 0){
			return;
		}
		int tx = (int)dropLocation.x;
		int tz = (int)dropLocation.z;
		int floorY = resolveAltarCenterGroundY(dropLocation);
		EntityItem entityItem = new EntityItem(host.worldObj, tx + 0.5D, floorY + 0.25D, tz + 0.5D, toDrop);
		entityItem.motionX = 0.0D;
		entityItem.motionY = 0.0D;
		entityItem.motionZ = 0.0D;
		entityItem.delayBeforeCanPickup = 10;
		host.worldObj.spawnEntityInWorld(entityItem);
	}

	private void tryMoveNearBlock(AMVector3 target){
		int tx = (int)target.x;
		int ty = (int)target.y;
		int tz = (int)target.z;
		if (host.getNavigator().tryMoveToXYZ(tx + 0.5D, ty, tz + 0.5D, MOVE_SPEED)){
			return;
		}
		World world = host.worldObj;
		int[][] offsets = new int[][]{
				{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
				{1, 1}, {1, -1}, {-1, 1}, {-1, -1}
		};
		for (int[] off : offsets){
			int px = tx + off[0];
			int pz = tz + off[1];
			for (int dy = 0; dy <= 3; dy++){
				int py = ty - dy;
				if (py < 1 || py > 254) continue;
				if (world.getBlock(px, py - 1, pz).getMaterial().isSolid()
						&& !world.getBlock(px, py, pz).getMaterial().blocksMovement()
						&& !world.getBlock(px, py + 1, pz).getMaterial().blocksMovement()){
					if (host.getNavigator().tryMoveToXYZ(px + 0.5D, py, pz + 0.5D, MOVE_SPEED)){
						return;
					}
				}
			}
		}
	}

	static ItemStack deductConcreteFromInventory(IInventory inv, ItemStack search, int quantity){
		int available = 0;
		ItemStack matchedTemplate = null;
		for (int i = 0; i < inv.getSizeInventory(); ++i){
			ItemStack slotStack = inv.getStackInSlot(i);
			if (slotStack != null && InventoryUtilities.compareItemStacks(slotStack, search, true, false, true, true)){
				if (matchedTemplate == null){
					matchedTemplate = slotStack.copy();
					matchedTemplate.stackSize = quantity;
				}
				available += slotStack.stackSize;
				if (available >= quantity){
					break;
				}
			}
		}
		if (available < quantity || matchedTemplate == null){
			return null;
		}
		int remaining = quantity;
		for (int i = 0; i < inv.getSizeInventory() && remaining > 0; ++i){
			ItemStack slotStack = inv.getStackInSlot(i);
			if (slotStack != null && InventoryUtilities.compareItemStacks(slotStack, matchedTemplate, true, false, true, true)){
				remaining -= InventoryUtilities.decrementStackQuantity(inv, i, remaining);
			}
		}
		inv.markDirty();
		return remaining <= 0 ? matchedTemplate : null;
	}

}
