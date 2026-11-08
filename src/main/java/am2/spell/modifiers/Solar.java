package am2.spell.modifiers;

import am2.api.spell.component.interfaces.ISpellModifier;
import am2.api.spell.enums.SpellModifiers;
import am2.items.ItemsCommonProxy;
import am2.playerextensions.ExtendedProperties;
import am2.spell.SolarLunarTierScaling;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.EnumSet;

public class Solar implements ISpellModifier{
	private static final float MAX_SOLAR_BURNOUT_CAPACITY = ExtendedProperties.maxMagicLevel * 10.0f + 1.0f;

	@Override
	public int getID(){
		return 12;
	}

	@Override
	public EnumSet<SpellModifiers> getAspectsModified(){
		return EnumSet.of(SpellModifiers.RANGE, SpellModifiers.RADIUS, SpellModifiers.DAMAGE, SpellModifiers.DURATION, SpellModifiers.HEALING);
	}
	@SuppressWarnings("incomplete-switch")
	@Override
	public float getModifier(SpellModifiers type, EntityLivingBase caster, Entity target, World world, byte[] metadata){
		if (caster == null){
			return 1.0f;
		}
		ExtendedProperties properties = ExtendedProperties.For(caster);
		if (properties == null){
			return 1.0f;
		}
		// Scale Solar by the caster's burnout capacity (magic level), not their
		// currently accumulated burnout. Level 99 reaches 991 by default.
		float maxBurnout = properties.getMaxFatigue();
		float burnoutRatio = Float.isNaN(maxBurnout) || Float.isInfinite(maxBurnout)
				? 0.0f
				: Math.max(0.0f, Math.min(1.0f, maxBurnout / MAX_SOLAR_BURNOUT_CAPACITY));
		if (type == SpellModifiers.RADIUS){
			// AoE adds this modifier to its base radius; keep the level-99 radius below 21.
			return SolarLunarTierScaling.scale(1.0f + 18.8f * (float)Math.pow(burnoutRatio, 0.9d), metadata);
		}
		float spellBonus = getSpellTypeBonus(type);

		float modifier = (float)Math.max(1,
				Math.pow(Math.pow((burnoutRatio * spellBonus), (burnoutRatio+1)),(burnoutRatio+1))* 5);
		return SolarLunarTierScaling.scale(modifier, metadata);
	}

	@SuppressWarnings("incomplete-switch")
	@Override
	public Object[] getRecipeItems(){
		return new Object[]{
				new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_NATURE),
				new ItemStack(ItemsCommonProxy.itemOre, 1, ItemsCommonProxy.itemOre.META_SUNSTONE),
				Items.clock
		};
	}

	@Override
	public float getManaCostMultiplier(ItemStack spellStack, int stage, int quantity, EntityLivingBase caster){
		if (caster == null || caster.worldObj == null){
			return quantity * 2.5f;
		}
		World world = caster.worldObj;
		float multiplier = 2.5f;

		if (caster.dimension == -1)
			multiplier = 1.5f;
		else if (!world.provider.hasNoSky && world.isDaytime()){
			double time = world.getWorldTime() % 24000;

			//Returns a decreasing value between 2.4 and 2.0 as it approaches midday.
			multiplier = (float) Math.round((
					1.0f + Math.exp(0.058 * (Math.abs((time - 6000)/1000)))
			) * 100)/100;
		}
		return quantity * multiplier;
	}
	@Override
	public byte[] getModifierMetadata(ItemStack[] matchedRecipe){
		return null;
	}

	public float getSpellTypeBonus(SpellModifiers type){
		switch (type){
		case HEALING:
			return 1.3f; //bonus at 90% = 881%
		case DAMAGE:
			return 1.4f; //bonus at 90% = 1152%
		case RADIUS:
			return 1.5f; //bonus at 90% = 1477%
		case RANGE:
			return 1.6f; //bonus at 90% = 1865%
		case DURATION:
			return 1.7f; //bonus at 90% = 2321%
		}
		return 1.2f; //bonus at 90% = 660%
	}
	@Override
	public boolean canStack(){
		return false;
	}

}
