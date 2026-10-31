package am2.spell.modifiers;

import am2.api.spell.component.interfaces.ISpellModifier;
import am2.api.spell.enums.SpellModifiers;
import am2.items.ItemsCommonProxy;
import am2.playerextensions.ExtendedProperties;
import am2.spell.SpellHelper;
import am2.spell.SolarLunarTierScaling;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.EnumSet;

public class Lunar implements ISpellModifier{
	// Level 99's normal mana maximum plus Mana Capacity III, before other bonuses.
	private static final float MAX_LUNAR_MANA_CAPACITY = (float)
			((Math.pow(ExtendedProperties.maxMagicLevel, 1.5f) * 85.0f + 500.0f) / 2.0f) + 15000.0f;

	@Override
	public int getID(){
		return 10;
	}

	@Override
	public EnumSet<SpellModifiers> getAspectsModified(){
		return EnumSet.of(SpellModifiers.RANGE, SpellModifiers.RADIUS, SpellModifiers.DAMAGE, SpellModifiers.DURATION, SpellModifiers.HEALING);
	}

	@SuppressWarnings("incomplete-switch")
	@Override
	public float getModifier(SpellModifiers type, EntityLivingBase caster, Entity target, World world, byte[] metadata){
		ExtendedProperties properties = ExtendedProperties.For(caster);
		if (properties == null){
			return 1.0f;
		}

		float currentMana = properties.getCurrentMana();
		float maxMana = properties.getMaxMana();
		if (Float.isNaN(currentMana) || Float.isInfinite(currentMana)
				|| Float.isNaN(maxMana) || Float.isInfinite(maxMana) || maxMana <= 0.0f){
			return 1.0f;
		}

		// Current mana is validated above for bad sync data, but does not determine
		// Lunar's strength. Capacity controls the bonus, even when current mana is 0.
		float manaRatio = maxMana / MAX_LUNAR_MANA_CAPACITY;
		if (Float.isNaN(manaRatio) || Float.isInfinite(manaRatio)){
			return 1.0f;
		}
		manaRatio = Math.max(0.0f, Math.min(1.0f, manaRatio));
		if (type == SpellModifiers.RADIUS){
			// AoE adds this modifier to its base radius; keep the level-99 radius below 21.
			return SolarLunarTierScaling.scale(1.0f + 18.8f * (float)Math.pow(manaRatio, 0.9d), metadata);
		}
		float spellBonus = getSpellTypeBonus(type);

		// Match Solar's strength curve, including its x5 scale factor.
		double modifier = Math.max(1,
				Math.pow(Math.pow((manaRatio * spellBonus), (manaRatio+1)),(manaRatio+1)) * 5.0
		);
		if (Double.isNaN(modifier) || Double.isInfinite(modifier)){
			return 1.0f;
		}
		return SolarLunarTierScaling.scale((float)modifier, metadata);
	}

	@Override
	public Object[] getRecipeItems(){
		return new Object[]{
				new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_NATURE),
				new ItemStack(ItemsCommonProxy.itemOre, 1, ItemsCommonProxy.itemOre.META_MOONSTONE),
				new ItemStack(ItemsCommonProxy.itemOre, 1, ItemsCommonProxy.itemOre.META_IMBUEDMOONFLOWER),
				Items.clock
		};
	}

	@Override
	public float getManaCostMultiplier(ItemStack spellStack, int stage, int quantity, EntityLivingBase caster){
		World world = caster.worldObj;

		float multiplier = 3.5f;

		if (caster.dimension == 1)
			multiplier = 2.0f;
		else if (!world.provider.hasNoSky && !world.isDaytime()){
			double time = world.getWorldTime() % 24000;

			//Returns a decreasing value between 3.4 and 2.5 as it approaches midnight.
			multiplier = (float)Math.round((
					1.5 + Math.exp(0.13 * (Math.abs((time - 18000) / 1000)))
			) * 100) / 100;
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
			return 1.3f; //bonus at 100% = 286%
		case DAMAGE:
			return 1.4f; //bonus at 100% = 384%
		case RADIUS:
			return 1.5f; //bonus at 100% = 500%
		case RANGE:
			return 1.6f; //bonus at 100% = 655%
		case DURATION:
			return 1.7f; //bonus at 100% = 835%
		}
		return 1.2f; //bonus at 100% = 207%
	}

	@Override
	public boolean canStack(){
		return false;
	}

}
