package am2.entities;

import am2.soulbound.TrueSoulboundHelper;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public class EntityTrueSoulboundItem extends EntityItem {

	private boolean suppressRescueOnSetDead = false;

	public EntityTrueSoulboundItem(World world) {
		super(world);
		initSoulboundProperties();
	}

	public EntityTrueSoulboundItem(World world, double x, double y, double z) {
		super(world, x, y, z);
		initSoulboundProperties();
	}

	public EntityTrueSoulboundItem(World world, double x, double y, double z, ItemStack stack) {
		super(world, x, y, z, stack);
		initSoulboundProperties();
	}

	private void initSoulboundProperties() {
		this.isImmuneToFire = true;
		this.lifespan = Integer.MAX_VALUE;
		this.delayBeforeCanPickup = 0;
	}

	@Override
	public boolean isEntityInvulnerable() {
		return true;
	}

	@Override
	public boolean attackEntityFrom(DamageSource source, float amount) {
		if (worldObj != null && !worldObj.isRemote && TrueSoulboundHelper.isTrueSoulbound(getEntityItem())) {
			if (source == DamageSource.outOfWorld
					|| source == DamageSource.lava
					|| source == DamageSource.inFire
					|| source == DamageSource.onFire
					|| source == DamageSource.cactus
					|| (source != null && source.isExplosion())) {
				rescueToOwnerOrVault();
			}
		}
		return false;
	}

	@Override
	protected void dealFireDamage(int amount) {
	}

	@Override
	protected void setOnFireFromLava() {
		if (worldObj != null && !worldObj.isRemote) {
			rescueToOwnerOrVault();
		}
	}

	@Override
	public void setFire(int seconds) {
	}

	@Override
	protected void kill() {
		if (worldObj != null && !worldObj.isRemote) {
			rescueToOwnerOrVault();
		}
	}

	@Override
	public void travelToDimension(int dimensionId) {
		if (worldObj != null && !worldObj.isRemote && TrueSoulboundHelper.isTrueSoulbound(getEntityItem())) {
			rescueToOwnerOrVault();
			return;
		}
		super.travelToDimension(dimensionId);
	}

	@Override
	public void onUpdate() {
		super.onUpdate();
		if (worldObj == null || worldObj.isRemote) {
			return;
		}

		this.lifespan = Integer.MAX_VALUE;
		if (this.age > 100) {
			this.age = 0;
		}

		ItemStack stack = getEntityItem();
		if (!TrueSoulboundHelper.isTrueSoulbound(stack)) {
			return;
		}

		if (isInHazard() || this.ticksExisted >= 5) {
			rescueToOwnerOrVault();
		}
	}

	@Override
	public void onCollideWithPlayer(EntityPlayer player) {
		if (worldObj == null || worldObj.isRemote) {
			super.onCollideWithPlayer(player);
			return;
		}

		ItemStack stack = getEntityItem();
		if (TrueSoulboundHelper.isTrueSoulbound(stack) && !TrueSoulboundHelper.isOwner(stack, player)) {
			TrueSoulboundHelper.punishThief(player, worldObj);
			rescueToOwnerOrVault();
			return;
		}

		boolean wasSuppressing = this.suppressRescueOnSetDead;
		this.suppressRescueOnSetDead = true;
		try {
			super.onCollideWithPlayer(player);
		} finally {
			if (!this.isDead) {
				this.suppressRescueOnSetDead = wasSuppressing;
			}
		}
	}

	@Override
	public void setDead() {
		if (!suppressRescueOnSetDead
				&& worldObj != null
				&& !worldObj.isRemote
				&& !this.isDead
				&& TrueSoulboundHelper.isTrueSoulbound(getEntityItem())) {
			ItemStack stack = getEntityItem();
			if (stack != null && stack.stackSize > 0) {
				TrueSoulboundHelper.rescueToOwnerOrVault(worldObj, stack, -1, true);
			}
		}
		super.setDead();
	}

	private boolean isInHazard() {
		if (this.posY < 1.0 || this.isBurning() || this.handleLavaMovement()) {
			return true;
		}
		int bx = MathHelper.floor_double(this.posX);
		int by = MathHelper.floor_double(this.posY);
		int bz = MathHelper.floor_double(this.posZ);
		Material mat = worldObj.getBlock(bx, by, bz).getMaterial();
		return mat == Material.lava || mat == Material.fire;
	}

	public void rescueToOwnerOrVault() {
		if (worldObj == null || worldObj.isRemote || this.isDead) {
			return;
		}
		ItemStack stack = getEntityItem();
		if (TrueSoulboundHelper.isTrueSoulbound(stack)
				&& TrueSoulboundHelper.rescueToOwnerOrVault(worldObj, stack, -1, true)) {
			this.suppressRescueOnSetDead = true;
			super.setDead();
		}
	}
}
