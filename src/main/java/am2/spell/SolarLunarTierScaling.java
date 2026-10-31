package am2.spell;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Applies a capped, linear tier to repeated Solar or Lunar modifiers. */
public final class SolarLunarTierScaling {
	private static final int SOLAR_ID = 12;
	private static final int LUNAR_ID = 10;

	private SolarLunarTierScaling(){ }

	/** Replaces empty Solar/Lunar metadata with the active count for this stage. */
	public static byte[] withModifierCount(byte[] original, Object stack, Object modifier, int stage){
		try{
			if (stack == null || modifier == null) return original;
			int targetID = getID(modifier);
			if (targetID != SOLAR_ID && targetID != LUNAR_ID) return original;

			Class<?> utilsClass = Class.forName("am2.spell.SpellUtils");
			Field instanceField = utilsClass.getField("instance");
			Object utils = instanceField.get(null);
			Class<?> stackClass = Class.forName("net.minecraft.item.ItemStack");
			Method getModifiers = utilsClass.getMethod("getModifiersForStage", stackClass, int.class);
			Object[] modifiers = (Object[])getModifiers.invoke(utils, stack, Integer.valueOf(stage));
			int count = 0;
			if (modifiers != null){
				for (Object candidate : modifiers){
					if (candidate != null && getID(candidate) == targetID) count++;
				}
			}
			count = Math.max(1, Math.min(3, count));
			return new byte[]{(byte)count};
		}catch (Throwable ignored){
			return new byte[]{1};
		}
	}

	/** One copy grants one-third of the bonus; three grant the full bonus. */
	public static float scale(float fullModifier, byte[] metadata){
		int count = metadata != null && metadata.length > 0 ? metadata[0] : 1;
		count = Math.max(1, Math.min(3, count));
		if (Float.isNaN(fullModifier) || Float.isInfinite(fullModifier)) return 1.0f;
		return 1.0f + (fullModifier - 1.0f) * (count / 3.0f);
	}

	private static int getID(Object modifier) throws Exception{
		Method method = modifier.getClass().getMethod("getID");
		Object value = method.invoke(modifier);
		return ((Number)value).intValue();
	}
}
