package am2.spell.components;

import am2.AMCore;
import am2.api.ArsMagicaApi;
import am2.api.spell.component.interfaces.ISpellComponent;
import am2.api.spell.enums.Affinity;
import am2.blocks.AMFlower;
import am2.blocks.BlocksCommonProxy;
import am2.items.ItemsCommonProxy;
import am2.particles.AMParticle;
import am2.particles.ParticleFadeOut;
import am2.particles.ParticleFloatUpward;
import am2.entities.EntitySpellEffect;
import net.minecraft.util.AxisAlignedBB;
import java.util.List;
import am2.particles.ParticleOrbitPoint;
import am2.utility.DummyEntityPlayer;
import cpw.mods.fml.common.eventhandler.Event.Result;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.BonemealEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Random;


public class Grow implements ISpellComponent{

	private final static ArrayList<AMFlower> growableAMflowers = new ArrayList<AMFlower>(Arrays.asList(
			BlocksCommonProxy.cerublossom, BlocksCommonProxy.desertNova, BlocksCommonProxy.wakebloom, BlocksCommonProxy.aum, BlocksCommonProxy.tarmaRoot));

	@Override
	public boolean applyEffectBlock(ItemStack stack, World world, int blockx, int blocky, int blockz, int blockFace, double impactX, double impactY, double impactZ, EntityLivingBase caster){

		Block block = world.getBlock(blockx, blocky, blockz);

		BonemealEvent event = new BonemealEvent(DummyEntityPlayer.fromEntityLiving(caster), world, block, blockx, blocky, blockz);
		if (MinecraftForge.EVENT_BUS.post(event)){
			return false;
		}
		if (event.getResult() == Result.ALLOW){
			return true;
		}

		//EoD: Spawn AM2 flowers with 3% chance. This has to be the first one in the list to override all others
		if (world.rand.nextInt(100) < 3 && block.isNormalCube() &&
				(world.getBlock(blockx, blocky + 1, blockz).isAir(null, 0, 0, 0) || world.getBlock(blockx, blocky + 1, blockz) == Blocks.tallgrass)){
			// shuffle the flower list every time we want to try to find one.
			Collections.shuffle(growableAMflowers);

			for (AMFlower flower : growableAMflowers){
				if (flower.canGrowOn(world, blockx, blocky + 1, blockz)){
					if (!world.isRemote){
						world.setBlock(blockx, blocky + 1, blockz, flower, 0, 2);
					}
					return true;
				}
			}
			// We did not find a flower or we have been executed on the wrong block. Either way, we continue
		}

		//Grow huge mushrooms 10% of the time.
		if (block instanceof BlockMushroom){
			if (!world.isRemote && world.rand.nextInt(10) < 1){
				((BlockMushroom)block).func_149884_c(world, blockx, blocky, blockz, world.rand);
			}

			return true;
		}


		//If the spell is executed in water, check if we have space for a wakebloom above and create one 3% of the time.
		if (block == Blocks.water){
			if (world.getBlock(blockx, blocky + 1, blockz) == Blocks.air){
				if (!world.isRemote && world.rand.nextInt(100) < 3){
					world.setBlock(blockx, blocky + 1, blockz, BlocksCommonProxy.wakebloom);
				}
				return true;
			}
		}

		//EoD: If there is already tallgrass present, let's grow it further 20% of the time.
		if (block == Blocks.tallgrass){
			if (Blocks.tallgrass.canBlockStay(world, blockx, blocky + 1, blockz)){
				if (!world.isRemote && world.rand.nextInt(10) < 2){
					world.setBlock(blockx, blocky, blockz, Blocks.tallgrass, 1, 2);
				}
				return true;
			}
		}

		//EoD: If there is already deadbush present, let's revitalize it 20% of the time.
		//     This works only on podzol in vanilla MC.
		if (block == Blocks.deadbush){
			if (Blocks.tallgrass.canBlockStay(world, blockx, blocky, blockz)){
				if (!world.isRemote && world.rand.nextInt(10) < 2){
					world.setBlock(blockx, blocky, blockz, Blocks.tallgrass, 1, 2);
				}
				return true;
			}
		}

		// EoD: Apply vanilla bonemeal effect to growables 30% of the time. This is the generic grow section.
		//      See ItemDye.applyBonemeal().
		if (block instanceof IGrowable){
			IGrowable igrowable = (IGrowable)block;
			//AMCore.log.getLogger().info("Grow component found IGrowable");

			if (igrowable.func_149851_a(world, blockx, blocky, blockz, world.isRemote)){
				if (!world.isRemote && world.rand.nextInt(10) < 3){
					if (igrowable.func_149852_a(world, world.rand, blockx, blocky, blockz)){
						igrowable.func_149853_b(world, world.rand, blockx, blocky, blockz);
					}
				}
				return true;
			}
		}

		return true;
	}

	@Override
	public boolean applyEffectEntity(ItemStack stack, World world, EntityLivingBase caster, Entity target){
		return false;
	}

	@Override
	public float manaCost(EntityLivingBase caster){
		return 17.4f;
	}

	@Override
	public float burnout(EntityLivingBase caster){
		return ArsMagicaApi.getBurnoutFromMana(manaCost(caster));
	}

	@Override
	public ItemStack[] reagents(EntityLivingBase caster){
		return null;
	}

	@Override
	public void spawnParticles(World world, double x, double y, double z, EntityLivingBase caster, Entity target, Random rand, int colorModifier){
		if (!world.isRemote) return;

		double groundY = y + 1.02;
		if (world.getBlock((int)Math.floor(x), (int)Math.floor(y + 1), (int)Math.floor(z)).isOpaqueCube()){
			groundY = y + 2.02;
		}else if (!world.getBlock((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z)).isOpaqueCube()
				&& world.getBlock((int)Math.floor(x), (int)Math.floor(y - 1), (int)Math.floor(z)).isOpaqueCube()){
			groundY = y + 0.02;
		}

		// 1. Starfall-style Fusion: Coalesce all Grow activations in a wide 24-block radius into ONE evolving mandala
		double fusionRadius = 24.0;
		@SuppressWarnings("unchecked")
		List<EntitySpellEffect> nearby = world.getEntitiesWithinAABB(EntitySpellEffect.class,
				AxisAlignedBB.getBoundingBox(x - fusionRadius, y - 10.0, z - fusionRadius, x + fusionRadius, y + 10.0, z + fusionRadius));

		EntitySpellEffect primary = null;
		for (EntitySpellEffect effect : nearby){
			if (effect.isGrowMandala() && !effect.isDead){
				if (primary == null){
					primary = effect;
				}else{
					// Merge secondary mandala into primary!
					primary.addMerged(effect.getMergeCount());
					effect.setDead();
				}
			}
		}

		if (primary != null){
			int oldTier = primary.getGrowTier();
			primary.addMerged(1);
			int newTier = primary.getGrowTier();

			// Smoothly gravitate the mandala center towards the average center of all merged blocks!
			double count = primary.getMergeCount();
			primary.posX = (primary.posX * (count - 1.0) + (x + 0.5)) / count;
			primary.posZ = (primary.posZ * (count - 1.0) + (z + 0.5)) / count;
			primary.posY = Math.max(primary.posY, groundY);
			primary.setPosition(primary.posX, primary.posY, primary.posZ);

			// Refresh lifetime so sustained zone / multi-block casts stay active and fade out cleanly
			primary.setTicksToExist(Math.max(primary.getTicksToExist(), primary.ticksExisted + 30));

			// Sound feedback on tier progression (subtle nature harmonic surge)
			if (newTier > oldTier){
				float pitch = newTier >= 4 ? 0.70f : (newTier >= 3 ? 0.85f : 1.05f);
				world.playSound(primary.posX, primary.posY, primary.posZ, "arsmagica2:spell.cast.nature", 1.2f, pitch, false);
			}
		}else{
			// Spawn the initial botanical magic circle
			EntitySpellEffect mandala = new EntitySpellEffect(world);
			mandala.setPosition(x + 0.5, groundY, z + 0.5);
			mandala.setGrowMandala(3.0f, colorModifier);
			world.spawnEntityInWorld(mandala);
		}

		// 2. Subtle ambient sparkle: only 1 in 8 blocks spawns 1 gentle floating sparkle (zero orbit controllers)
		if (rand.nextInt(8) == 0){
			AMParticle particle = (AMParticle)AMCore.proxy.particleManager.spawn(world, "plant", x + 0.5, y + 1.0, z + 0.5);
			if (particle != null){
				particle.addRandomOffset(0.6, 0.4, 0.6);
				particle.AddParticleController(new ParticleFloatUpward(particle, 0, 0.05f, 1, false));
				particle.AddParticleController(new ParticleFadeOut(particle, 1, false).setFadeSpeed(0.06f).setKillParticleOnFinish(true));
				particle.setMaxAge(24);
				particle.setParticleScale(0.12f);
				if (colorModifier > -1){
					particle.setRGBColorF(((colorModifier >> 16) & 0xFF) / 255.0f, ((colorModifier >> 8) & 0xFF) / 255.0f, (colorModifier & 0xFF) / 255.0f);
				}else{
					particle.setRGBColorF(0.40f, 1.0f, 0.55f);
				}
			}
		}
	}

	@Override
	public EnumSet<Affinity> getAffinity(){
		return EnumSet.of(Affinity.NATURE);
	}

	@Override
	public int getID(){
		return 22;
	}

	@Override
	public Object[] getRecipeItems(){
		return new Object[]{
				new ItemStack(ItemsCommonProxy.rune, 1, ItemsCommonProxy.rune.META_GREEN),
				new ItemStack(Items.dye, 1, 15),
				BlocksCommonProxy.witchwoodLog
		};
	}

	@Override
	public float getAffinityShift(Affinity affinity){
		return 0.02f;
	}
}
