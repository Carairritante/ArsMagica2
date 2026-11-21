package am2.spell.components;

import am2.api.spell.component.interfaces.ISpellComponent;
import am2.api.spell.component.interfaces.ISpellModifier;
import am2.api.spell.component.interfaces.ISpellPart;
import am2.api.spell.enums.Affinity;
import am2.api.spell.enums.SpellModifiers;
import am2.entities.EntitySpellEffect;
import am2.items.ItemsCommonProxy;
import am2.spell.SpellUtils;
import am2.spell.modifiers.Damage;
import am2.spell.modifiers.Lunar;
import am2.spell.modifiers.Radius;
import am2.spell.modifiers.Solar;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * Golden Spell (Tier above Silver): summons a towering, destructive Tornado / Hurricane
 * with a 5-tier Fujita Scale progression (F1 -> F5), distinct Solar vs Lunar celestial
 * evolutions, elemental synergies (Fire, Lightning, Frost), real 3D block & tree-canopy
 * devouring with ballistic ejection, and Homing/Speed pursuit.
 */
public class Tornado implements ISpellComponent{

	public static final int COMPONENT_ID = 102;

	public static final byte ELEMENT_AIR = 0;
	public static final byte ELEMENT_FIRE = 1;
	public static final byte ELEMENT_LIGHTNING = 2;
	public static final byte ELEMENT_ICE = 3;
	public static final byte ELEMENT_WATER = 4;

	public static final byte CELESTIAL_NONE = 0;
	public static final byte CELESTIAL_SOLAR = 1;
	public static final byte CELESTIAL_LUNAR = 2;

	public static byte detectElementalSynergy(ItemStack stack){
		return detectElementalSynergy(stack, findTornadoStage(stack));
	}

	public static byte detectElementalSynergy(ItemStack stack, int stage){
		if (!hasStage(stack, stage)) return ELEMENT_AIR;
		if (SpellUtils.instance.componentIsPresent(stack, FireDamage.class, stage)
				|| SpellUtils.instance.componentIsPresent(stack, Ignition.class, stage)
				|| SpellUtils.instance.componentIsPresent(stack, FireRain.class, stage)){
			return ELEMENT_FIRE;
		}
		if (SpellUtils.instance.componentIsPresent(stack, LightningDamage.class, stage)
				|| SpellUtils.instance.componentIsPresent(stack, LightningStrike.class, stage)){
			return ELEMENT_LIGHTNING;
		}
		if (SpellUtils.instance.componentIsPresent(stack, FrostDamage.class, stage)
				|| SpellUtils.instance.componentIsPresent(stack, Freeze.class, stage)
				|| SpellUtils.instance.componentIsPresent(stack, Blizzard.class, stage)){
			return ELEMENT_ICE;
		}
		if (SpellUtils.instance.componentIsPresent(stack, CreateWater.class, stage)
				|| SpellUtils.instance.componentIsPresent(stack, Drown.class, stage)
				|| SpellUtils.instance.componentIsPresent(stack, WateryGrave.class, stage)){
			return ELEMENT_WATER;
		}
		return ELEMENT_AIR;
	}

	private static boolean hasStage(ItemStack stack, int stage){
		return stack != null && stack.hasTagCompound() && stage >= 0
				&& stage < SpellUtils.instance.numStages(stack);
	}

	/**
	 * Server-safe modifier counter for Solar (ID 12), Lunar (ID 10), Radius (ID 5), and Damage (ID 1).
	 * Returns int[4] = {solarCount, lunarCount, radiusModCount, damageModCount}.
	 */
	public static int[] inspectTierModifiers(ItemStack stack){
		return inspectTierModifiers(stack, findTornadoStage(stack));
	}

	public static int[] inspectTierModifiers(ItemStack stack, int stage){
		int[] counts = new int[4];
		if (!hasStage(stack, stage)) return counts;
		ISpellModifier[] mods = SpellUtils.instance.getModifiersForStage(stack, stage);
		if (mods == null) return counts;
		for (ISpellModifier m : mods){
			if (m == null) continue;
			int id = m.getID();
			if (m instanceof Solar || id == 12 || id == 5012){
				counts[0]++;
			}else if (m instanceof Lunar || id == 10 || id == 5010){
				counts[1]++;
			}else if (m instanceof Radius || id == 5 || id == 5005){
				counts[2]++;
			}else if (m instanceof Damage || id == 1 || id == 5001){
				counts[3]++;
			}
		}
		counts[0] = Math.min(3, counts[0]);
		counts[1] = Math.min(3, counts[1]);
		return counts;
	}

	/**
	 * Computes Fujita Scale Tier (1 = F1 .. 5 = F5) from Solar, Lunar, Radius, and Damage modifier counts.
	 */
	public static int computeFujitaTier(int solarCount, int lunarCount, int radiusMods, int damageMods){
		int s = Math.max(0, Math.min(3, solarCount));
		int l = Math.max(0, Math.min(3, lunarCount));
		int celestialMax = Math.max(s, l);
		int celestialTotal = s + l;
		int basicMods = Math.max(0, radiusMods) + Math.max(0, damageMods);

		if (celestialMax >= 3 || celestialTotal >= 3 || (celestialMax >= 2 && basicMods >= 2)){
			return 5;
		}
		if (celestialMax == 2 || celestialTotal == 2 || (celestialMax == 1 && basicMods >= 2) || basicMods >= 4){
			return 4;
		}
		if (celestialMax == 1 || basicMods >= 2){
			return 3;
		}
		if (basicMods == 1){
			return 2;
		}
		return 1;
	}

	/**
	 * Computes Celestial Mode: CELESTIAL_NONE (0), CELESTIAL_SOLAR (1), or CELESTIAL_LUNAR (2).
	 */
	public static byte computeCelestialMode(int solarCount, int lunarCount){
		if (solarCount <= 0 && lunarCount <= 0) return CELESTIAL_NONE;
		return (solarCount >= lunarCount) ? CELESTIAL_SOLAR : CELESTIAL_LUNAR;
	}

	public static int findTornadoStage(ItemStack stack){
		if (stack == null || !stack.hasTagCompound()) return 0;
		int stages = Math.max(1, Math.min(16, SpellUtils.instance.numStages(stack)));
		for (int s = 0; s < stages; s++){
			if (SpellUtils.instance.componentIsPresent(stack, Tornado.class, s)){
				return s;
			}
		}
		return 0;
	}

	public static float getTierBaseHardness(int tier){
		switch (tier){
		case 1: return 1.8f;
		case 2: return 2.5f;
		case 3: return 3.5f;
		case 4: return 5.5f;
		default: return 8.5f;
		}
	}

	public static float getTierMinRadius(int tier){
		switch (tier){
		case 1: return 6.0f;
		case 2: return 9.5f;
		case 3: return 14.5f;
		case 4: return 22.0f;
		default: return 32.0f;
		}
	}

	public static float getTierDamageMultiplier(int tier){
		switch (tier){
		case 1: return 1.00f;
		case 2: return 1.25f;
		case 3: return 1.55f;
		case 4: return 1.95f;
		default: return 2.45f;
		}
	}

	/**
	 * Eye-of-the-storm damage scaling: crushing damage inside the inner vortex core,
	 * moderate wall shear in the mid-funnel, and lighter buffeting at the outer rim.
	 */
	public static float getProximityDamageMultiplier(double distXZ, double radius){
		double safeR = Math.max(1.0, radius);
		double ratio = distXZ / safeR;
		if (ratio <= 0.45) return 1.75f;
		if (ratio <= 0.85) return 1.15f;
		return 0.65f;
	}

	/**
	 * Detects elemental synergy directly from raw Inscription Table recipe parts.
	 */
	public static byte detectElementalSynergyFromParts(List<ISpellPart> parts){
		if (parts == null) return ELEMENT_AIR;
		for (ISpellPart p : parts){
			if (p instanceof FireDamage || p instanceof Ignition || p instanceof FireRain) return ELEMENT_FIRE;
		}
		for (ISpellPart p : parts){
			if (p instanceof LightningDamage || p instanceof LightningStrike) return ELEMENT_LIGHTNING;
		}
		for (ISpellPart p : parts){
			if (p instanceof FrostDamage || p instanceof Freeze || p instanceof Blizzard) return ELEMENT_ICE;
		}
		for (ISpellPart p : parts){
			if (p instanceof CreateWater || p instanceof Drown || p instanceof WateryGrave) return ELEMENT_WATER;
		}
		return ELEMENT_AIR;
	}

	/**
	 * Counts [solar, lunar, radius, damage] modifiers directly from raw Inscription Table recipe parts.
	 */
	public static int[] inspectTierModifiersFromParts(List<ISpellPart> parts){
		int solar = 0, lunar = 0, radius = 0, damage = 0;
		if (parts != null){
			for (ISpellPart p : parts){
				if (p instanceof Solar) solar++;
				else if (p instanceof Lunar) lunar++;
				else if (p instanceof Radius) radius++;
				else if (p instanceof Damage) damage++;
			}
		}
		return new int[]{solar, lunar, radius, damage};
	}

	public static String getElementLangKey(byte element){
		switch (element){
		case ELEMENT_FIRE: return "am2.gui.tornadoElement.fire";
		case ELEMENT_LIGHTNING: return "am2.gui.tornadoElement.lightning";
		case ELEMENT_ICE: return "am2.gui.tornadoElement.ice";
		case ELEMENT_WATER: return "am2.gui.tornadoElement.water";
		default: return "am2.gui.tornadoElement.air";
		}
	}

	/**
	 * Returns true only for common/disposable terrain, foliage, wood, glass, snow, and bulk rock
	 * blocks that the Tornado may destroy outright. Ores, gem/metal blocks, and modded specialty
	 * stone/metal blocks return false so they are thrown physically instead of destroyed.
	 */
	public static boolean isDisposableTerrainBlock(Block block, int meta){
		if (block == null || block == Blocks.air) return false;
		if (block == Blocks.crafting_table
				|| block == Blocks.bookshelf
				|| block == Blocks.jukebox
				|| block == Blocks.noteblock
				|| block == Blocks.chest
				|| block == Blocks.trapped_chest){
			return false;
		}
		if (block == Blocks.dirt
				|| block == Blocks.grass
				|| block == Blocks.mycelium
				|| block == Blocks.farmland
				|| block == Blocks.sand
				|| block == Blocks.gravel
				|| block == Blocks.clay
				|| block == Blocks.soul_sand
				|| block == Blocks.stone
				|| block == Blocks.cobblestone
				|| block == Blocks.mossy_cobblestone
				|| block == Blocks.sandstone
				|| block == Blocks.netherrack
				|| block == Blocks.hardened_clay
				|| block == Blocks.stained_hardened_clay
				|| block == Blocks.snow
				|| block == Blocks.snow_layer
				|| block == Blocks.ice
				|| block == Blocks.packed_ice
				|| block == Blocks.log
				|| block == Blocks.log2
				|| block == Blocks.planks
				|| block == Blocks.leaves
				|| block == Blocks.leaves2
				|| block == Blocks.fence
				|| block == Blocks.nether_brick_fence
				|| block == Blocks.wooden_slab
				|| block == Blocks.double_wooden_slab
				|| block == Blocks.oak_stairs
				|| block == Blocks.spruce_stairs
				|| block == Blocks.birch_stairs
				|| block == Blocks.jungle_stairs
				|| block == Blocks.acacia_stairs
				|| block == Blocks.dark_oak_stairs
				|| block == Blocks.glass
				|| block == Blocks.glass_pane
				|| block == Blocks.stained_glass
				|| block == Blocks.stained_glass_pane
				|| block == Blocks.wool
				|| block == Blocks.carpet
				|| block == Blocks.hay_block
				|| block == Blocks.web){
			return true;
		}
		Material mat = block.getMaterial();
		if (mat == null) return false;
		return mat == Material.plants
				|| mat == Material.vine
				|| mat == Material.leaves
				|| mat == Material.cactus
				|| mat == Material.gourd
				|| mat == Material.coral
				|| mat == Material.web
				|| mat == Material.grass
				|| mat == Material.ground
				|| mat == Material.sand
				|| mat == Material.clay
				|| mat == Material.snow
				|| mat == Material.craftedSnow
				|| mat == Material.ice
				|| mat == Material.packedIce
				|| mat == Material.glass
				|| mat == Material.cloth
				|| mat == Material.carpet
				|| mat == Material.wood;
	}

	/**
	 * Returns true for non-TileEntity valuable blocks (diamond/gold/iron/emerald blocks, ores,
	 * modded metal/gem/decorative solid cubes, anvils, dragon eggs) that can be safely picked up
	 * and flung as real EntityFallingBlock instances without data loss or multiblock splitting.
	 */
	public static boolean isThrowableValuableBlock(Block block, int meta){
		if (block == null || block == Blocks.air) return false;
		if (block == Blocks.anvil || block == Blocks.dragon_egg) return true;
		if (block instanceof BlockDoor || block instanceof BlockBed) return false;
		return block.isOpaqueCube() && block.renderAsNormalBlock();
	}

	@Override
	public Object[] getRecipeItems(){
		return new Object[]{
				new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_PURE),
				new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_HIGH_CORE),
				new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_AIR),
				new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_LIGHTNING),
				new ItemStack(ItemsCommonProxy.itemOre, 1, ItemsCommonProxy.itemOre.META_SUNSTONE),
				new ItemStack(ItemsCommonProxy.itemOre, 1, ItemsCommonProxy.itemOre.META_MOONSTONE),
				Blocks.diamond_block,
				new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_PURE)
		};
	}

	@Override
	public int getID(){
		return COMPONENT_ID;
	}

	private boolean spawnTornado(ItemStack stack, World world, EntityLivingBase caster, Entity target, double x, double y, double z){
		// SpellHelper invokes components in stage zero after earlier shapes have been popped.
		// Searching later stages here lets an unexecuted component modify this cast.
		int stage = 0;
		int[] modCounts = inspectTierModifiers(stack, stage);
		int solarCount = modCounts[0];
		int lunarCount = modCounts[1];
		int fujitaTier = computeFujitaTier(solarCount, lunarCount, modCounts[2], modCounts[3]);
		byte celestialMode = computeCelestialMode(solarCount, lunarCount);

		double modifiedRadius = SpellUtils.instance.getModifiedDouble_Add(7.0D, stack, caster, target, world, stage, SpellModifiers.RADIUS);
		double modifiedDamage = SpellUtils.instance.getModifiedDouble_Mul(7.0D, stack, caster, target, world, stage, SpellModifiers.DAMAGE);
		int modifiedDuration = SpellUtils.instance.getModifiedInt_Mul(260, stack, caster, target, world, stage, SpellModifiers.DURATION);
		double modifiedSpeed = SpellUtils.instance.getModifiedDouble_Mul(1.0D, stack, caster, target, world, stage, SpellModifiers.SPEED);
		int homingMods = SpellUtils.instance.countModifiers(SpellModifiers.HOMING, stack, stage);
		int velocityMods = SpellUtils.instance.countModifiers(SpellModifiers.VELOCITY_ADDED, stack, stage);
		int speedMods = Math.max(0, (int) Math.round((modifiedSpeed - 1.0D) * 2.0D) + velocityMods);
		int miningPower = SpellUtils.instance.getModifiedInt_Add(1, stack, caster, target, world, stage, SpellModifiers.MINING_POWER);
		boolean hasDig = SpellUtils.instance.componentIsPresent(stack, Dig.class, stage);

		// Solar vs Lunar specialization: Solar amplifies damage; Lunar expands duration & gravitational pull
		if (celestialMode == CELESTIAL_SOLAR){
			modifiedDamage *= (1.0D + 0.20D * solarCount);
		}else if (celestialMode == CELESTIAL_LUNAR){
			modifiedDuration = (int) Math.round(modifiedDuration * (1.0D + 0.18D * lunarCount));
		}

		float minTierRad = getTierMinRadius(fujitaTier);
		float radius = (float) Math.max(minTierRad, Math.min(40.0D, modifiedRadius));
		int duration = Math.max(120, Math.min(800, modifiedDuration));
		float damage = (float) Math.max(3.5D, Math.min(140.0D, modifiedDamage));
		float baseHardness = getTierBaseHardness(fujitaTier);
		float maxHardness = (hasDig || miningPower > 1)
				? Math.min(12.0f, baseHardness + Math.max(0, miningPower - 1) * 1.4f + (hasDig ? 2.0f : 0.0f))
				: baseHardness;
		byte element = detectElementalSynergy(stack, stage);

		@SuppressWarnings("unchecked")
		List<EntitySpellEffect> existing = world.getEntitiesWithinAABB(
				EntitySpellEffect.class,
				AxisAlignedBB.getBoundingBox(x - 14.0, y - 14.0, z - 14.0, x + 14.0, y + 14.0, z + 14.0)
		);
		for (EntitySpellEffect fx : existing){
			if (!fx.isDead && fx.isTornado() && canRefreshTornado(fx, caster)){
				if (!world.isRemote){
					fx.refreshTornado(radius, damage, homingMods, speedMods, maxHardness, element, fujitaTier, celestialMode);
				}
				return true;
			}
		}

		if (!world.isRemote){
			double dirX = 0.0;
			double dirZ = 0.0;
			if (caster != null){
				double yawRad = Math.toRadians(caster.rotationYaw);
				dirX = -Math.sin(yawRad);
				dirZ = Math.cos(yawRad);
			}

			EntitySpellEffect tornado = new EntitySpellEffect(world);
			tornado.setTornado(caster, stack, x, y, z, radius, duration, damage, homingMods, speedMods, maxHardness, dirX, dirZ, element, fujitaTier, celestialMode);
			return world.spawnEntityInWorld(tornado);
		}
		return true;
	}

	static boolean canRefreshTornado(EntitySpellEffect effect, EntityLivingBase caster){
		// Anonymous effects have no reliable shared owner; never merge unrelated anonymous casts.
		return effect != null && caster != null && effect.getCasterEntityID() == caster.getEntityId();
	}

	@Override
	public boolean applyEffectBlock(ItemStack stack, World world, int blockx, int blocky, int blockz, int blockFace, double impactX, double impactY, double impactZ, EntityLivingBase caster){
		return spawnTornado(stack, world, caster, caster, impactX, impactY, impactZ);
	}

	@Override
	public boolean applyEffectEntity(ItemStack stack, World world, EntityLivingBase caster, Entity target){
		if (target == null) return false;
		return spawnTornado(stack, world, caster, target, target.posX, target.posY, target.posZ);
	}

	@Override
	public float manaCost(EntityLivingBase caster){
		return 6500.0f;
	}

	@Override
	public float burnout(EntityLivingBase caster){
		return 450.0f;
	}

	@Override
	public ItemStack[] reagents(EntityLivingBase caster){
		return null;
	}

	@Override
	public void spawnParticles(World world, double x, double y, double z, EntityLivingBase caster, Entity target, Random rand, int colorModifier){
	}

	@Override
	public EnumSet<Affinity> getAffinity(){
		return EnumSet.of(Affinity.AIR);
	}

	@Override
	public float getAffinityShift(Affinity affinity){
		return 0.15f;
	}
}
