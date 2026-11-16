package am2.spell.components;

import cpw.mods.fml.common.eventhandler.Event;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * ChromatiCraft-style server-side block drop & XP consolidator for Dig and Explosion spells.
 * Intercepts per-block EntityItem and EntityXPOrb spawns during batch excavation/explosion
 * and flushes them as merged full stacks (up to maxStackSize) at the center of the spell area.
 */
public final class SpellDropConsolidator {

	private static final double MAX_CAPTURE_DISTANCE_SQ = 128.0D * 128.0D;
	private static final double EXISTING_MERGE_RADIUS = 2.5D;

	private static final ThreadLocal<Deque<BatchScope>> ACTIVE_SCOPES = new ThreadLocal<Deque<BatchScope>>() {
		@Override
		protected Deque<BatchScope> initialValue() {
			return new ArrayDeque<BatchScope>();
		}
	};

	public static final class ConsolidatedStack {
		public final ItemStack prototype;
		public int totalCount;

		public ConsolidatedStack(ItemStack prototype, int totalCount) {
			this.prototype = prototype;
			this.totalCount = totalCount;
		}
	}

	public static final class BatchScope {
		public final World world;
		public final double centerX;
		public final double centerY;
		public final double centerZ;
		public final boolean isExplosion;
		public int depth;
		public int brokenBlocksCount;
		public Block lastBrokenBlock;
		public int lastBrokenMeta;
		public double closestAirX;
		public double closestAirY;
		public double closestAirZ;
		public double closestAirDistSq = Double.MAX_VALUE;
		public int totalXp;
		public final List<ConsolidatedStack> stacks = new ArrayList<ConsolidatedStack>();

		BatchScope(World world, double centerX, double centerY, double centerZ, boolean isExplosion) {
			this.world = world;
			this.centerX = centerX;
			this.centerY = centerY;
			this.centerZ = centerZ;
			this.isExplosion = isExplosion;
			this.depth = 1;
			this.closestAirX = centerX;
			this.closestAirY = centerY;
			this.closestAirZ = centerZ;
		}

		public void addStack(ItemStack incoming) {
			if (incoming == null || incoming.getItem() == null || incoming.stackSize <= 0) {
				return;
			}
			for (int i = 0; i < this.stacks.size(); i++) {
				ConsolidatedStack existing = this.stacks.get(i);
				if (canMergeStacks(existing.prototype, incoming)) {
					existing.totalCount += incoming.stackSize;
					return;
				}
			}
			ItemStack proto = incoming.copy();
			proto.stackSize = 1;
			this.stacks.add(new ConsolidatedStack(proto, incoming.stackSize));
		}

		public void addXp(int xp) {
			if (xp > 0) {
				this.totalXp += xp;
			}
		}

		public void recordBroken(Block block, int meta, int bx, int by, int bz) {
			this.brokenBlocksCount++;
			this.lastBrokenBlock = block;
			this.lastBrokenMeta = meta;
			double cx = bx + 0.5D;
			double cy = by + 0.5D;
			double cz = bz + 0.5D;
			double dx = cx - this.centerX;
			double dy = cy - this.centerY;
			double dz = cz - this.centerZ;
			double distSq = dx * dx + dy * dy + dz * dz;
			if (distSq < this.closestAirDistSq) {
				this.closestAirDistSq = distSq;
				this.closestAirX = cx;
				this.closestAirY = cy;
				this.closestAirZ = cz;
			}
		}
	}

	private SpellDropConsolidator() {
	}

	public static boolean canMergeStacks(ItemStack a, ItemStack b) {
		if (a == null || b == null) return false;
		if (a.getItem() != b.getItem()) return false;
		if (a.getItemDamage() != b.getItemDamage()) return false;
		return ItemStack.areItemStackTagsEqual(a, b);
	}

	public static void beginBatch(World world, double centerX, double centerY, double centerZ, boolean isExplosion) {
		if (world == null || world.isRemote) {
			return;
		}
		Deque<BatchScope> deque = ACTIVE_SCOPES.get();
		BatchScope top = deque.peek();
		if (top != null && top.world == world) {
			top.depth++;
			return;
		}
		deque.push(new BatchScope(world, centerX, centerY, centerZ, isExplosion));
	}

	public static void recordBrokenBlock(World world, Block block, int meta, int bx, int by, int bz) {
		if (world == null || world.isRemote) {
			return;
		}
		BatchScope top = ACTIVE_SCOPES.get().peek();
		if (top != null && top.world == world) {
			top.recordBroken(block, meta, bx, by, bz);
		}
	}

	public static void endBatch(World world) {
		if (world == null || world.isRemote) {
			return;
		}
		Deque<BatchScope> deque = ACTIVE_SCOPES.get();
		BatchScope top = deque.peek();
		if (top == null || top.world != world) {
			return;
		}
		top.depth--;
		if (top.depth > 0) {
			return;
		}
		deque.pop();
		flushScope(top);
	}

	public static boolean onEntityJoinWorld(EntityJoinWorldEvent event) {
		if (event == null || event.world == null || event.world.isRemote || event.entity == null) {
			return false;
		}
		BatchScope top = ACTIVE_SCOPES.get().peek();
		if (top == null || top.world != event.world) {
			return false;
		}
		double dx = event.entity.posX - top.centerX;
		double dy = event.entity.posY - top.centerY;
		double dz = event.entity.posZ - top.centerZ;
		if (dx * dx + dy * dy + dz * dz > MAX_CAPTURE_DISTANCE_SQ) {
			return false;
		}

		if (event.entity instanceof EntityItem) {
			EntityItem itemEntity = (EntityItem) event.entity;
			ItemStack stack = itemEntity.getEntityItem();
			if (stack != null && stack.getItem() != null && stack.stackSize > 0) {
				top.addStack(stack);
			}
			itemEntity.setDead();
			event.setCanceled(true);
			return true;
		}

		if (event.entity instanceof EntityXPOrb) {
			EntityXPOrb xpOrb = (EntityXPOrb) event.entity;
			if (xpOrb.xpValue > 0) {
				top.addXp(xpOrb.xpValue);
			}
			xpOrb.setDead();
			event.setCanceled(true);
			return true;
		}

		return false;
	}

	/**
	 * Posts Forge's BlockEvent.BreakEvent safely without casting DummyEntityPlayer to EntityPlayerMP
	 * and without sending premature per-block S23PacketBlockChange packets.
	 */
	public static BreakEvent fireBlockBreakEvent(World world, EntityPlayer player, Block block, int meta, int x, int y, int z) {
		boolean preCancel = false;
		if (player instanceof EntityPlayerMP) {
			EntityPlayerMP playerMP = (EntityPlayerMP) player;
			if (playerMP.theItemInWorldManager != null) {
				GameType gameType = playerMP.theItemInWorldManager.getGameType();
				if (gameType != null) {
					if (gameType.isAdventure() && !playerMP.isCurrentToolAdventureModeExempt(x, y, z)) {
						preCancel = true;
					} else if (gameType.isCreative() && playerMP.getHeldItem() != null && playerMP.getHeldItem().getItem() instanceof ItemSword) {
						preCancel = true;
					}
				}
			}
		}
		BreakEvent event = new BreakEvent(x, y, z, world, block, meta, player);
		event.setCanceled(preCancel);
		MinecraftForge.EVENT_BUS.post(event);
		return event;
	}

	@SuppressWarnings("unchecked")
	private static void flushScope(BatchScope scope) {
		World world = scope.world;
		double spawnX = scope.centerX;
		double spawnY = scope.centerY;
		double spawnZ = scope.centerZ;

		int bx = MathHelper.floor_double(spawnX);
		int by = MathHelper.floor_double(spawnY);
		int bz = MathHelper.floor_double(spawnZ);
		if (!world.isAirBlock(bx, by, bz) && world.getBlock(bx, by, bz).isNormalCube()) {
			if (world.isAirBlock(bx, by + 1, bz)) {
				spawnY = by + 1.25D;
			} else if (scope.closestAirDistSq < Double.MAX_VALUE) {
				spawnX = scope.closestAirX;
				spawnY = scope.closestAirY;
				spawnZ = scope.closestAirZ;
			}
		}

		if (!scope.isExplosion && scope.brokenBlocksCount > 0) {
			if (scope.lastBrokenBlock != null && scope.lastBrokenBlock.stepSound != null) {
				String breakSound = scope.lastBrokenBlock.stepSound.getBreakSound();
				if (breakSound != null && !breakSound.isEmpty()) {
					world.playSoundEffect(spawnX, spawnY, spawnZ, breakSound, 1.0F, 0.85F + world.rand.nextFloat() * 0.15F);
				}
			}
			if (scope.brokenBlocksCount > 1) {
				float vol = Math.min(0.9F, 0.25F + scope.brokenBlocksCount * 0.015F);
				float pitch = Math.max(0.9F, 1.55F - scope.brokenBlocksCount * 0.01F);
				world.playSoundEffect(spawnX, spawnY, spawnZ, "random.explode", vol, pitch);
			}
		}

		if (!scope.stacks.isEmpty()) {
			AxisAlignedBB mergeBox = AxisAlignedBB.getBoundingBox(
					spawnX - EXISTING_MERGE_RADIUS, spawnY - EXISTING_MERGE_RADIUS, spawnZ - EXISTING_MERGE_RADIUS,
					spawnX + EXISTING_MERGE_RADIUS, spawnY + EXISTING_MERGE_RADIUS, spawnZ + EXISTING_MERGE_RADIUS);
			List<EntityItem> nearbyItems = world.getEntitiesWithinAABB(EntityItem.class, mergeBox);

			for (int i = 0; i < scope.stacks.size(); i++) {
				ConsolidatedStack entry = scope.stacks.get(i);
				int remaining = entry.totalCount;
				int maxStack = Math.max(1, entry.prototype.getMaxStackSize());

				if (maxStack > 1 && nearbyItems != null && !nearbyItems.isEmpty()) {
					for (int j = 0; j < nearbyItems.size() && remaining > 0; j++) {
						EntityItem existingEntity = nearbyItems.get(j);
						if (existingEntity == null || existingEntity.isDead) continue;
						ItemStack existingStack = existingEntity.getEntityItem();
						if (existingStack == null || existingStack.stackSize >= maxStack) continue;
						if (canMergeStacks(existingStack, entry.prototype)) {
							int space = maxStack - existingStack.stackSize;
							int transfer = Math.min(space, remaining);
							if (transfer > 0) {
								ItemStack updated = existingStack.copy();
								updated.stackSize += transfer;
								existingEntity.setEntityItemStack(updated);
								existingEntity.setPosition(spawnX, spawnY, spawnZ);
								existingEntity.motionX = 0.0D;
								existingEntity.motionY = 0.05D;
								existingEntity.motionZ = 0.0D;
								remaining -= transfer;
							}
						}
					}
				}

				while (remaining > 0) {
					int chunk = Math.min(maxStack, remaining);
					remaining -= chunk;
					ItemStack dropStack = entry.prototype.copy();
					dropStack.stackSize = chunk;
					EntityItem itemEntity = new EntityItem(world, spawnX, spawnY, spawnZ, dropStack);
					itemEntity.motionX = 0.0D;
					itemEntity.motionY = 0.05D;
					itemEntity.motionZ = 0.0D;
					itemEntity.delayBeforeCanPickup = 10;
					world.spawnEntityInWorld(itemEntity);
				}
			}
		}

		int xpRemaining = scope.totalXp;
		while (xpRemaining > 0) {
			int split = EntityXPOrb.getXPSplit(xpRemaining);
			xpRemaining -= split;
			EntityXPOrb orb = new EntityXPOrb(world, spawnX, spawnY, spawnZ, split);
			orb.motionX = 0.0D;
			orb.motionY = 0.05D;
			orb.motionZ = 0.0D;
			world.spawnEntityInWorld(orb);
		}
	}
}
