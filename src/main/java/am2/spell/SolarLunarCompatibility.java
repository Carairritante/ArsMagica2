package am2.spell;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Cast-time compatibility rules for the Solar and Lunar spell modifiers. */
public final class SolarLunarCompatibility{
	private static final int SOLAR_MODIFIER_ID = 5012;
	private static final int LUNAR_MODIFIER_ID = 5010;

	private SolarLunarCompatibility(){
	}

	/**
	 * If both modifiers and an AoE or Zone shape are present, removes Lunar from
	 * the temporary cast stack so Solar is the only one applied. The stored spell
	 * item is not changed; the filtered stack is carried forward by projectiles
	 * and zones. Reflection keeps this small compatibility helper independent of
	 * the launcher's mapped Minecraft classes.
	 */
	public static void prepareSpell(Object stack, Object world, Object caster){
		try{
			if (suppressLunar(stack))
				notifyCaster(world, caster);
		}catch (Exception error){
			// A compatibility check must never prevent the spell itself from casting.
			System.err.println("[Ars Magica 2] Could not apply the Solar/Lunar AoE/Zone rule: " + error);
		}
	}

	private static boolean suppressLunar(Object stack) throws Exception{
		if (stack == null)
			return false;

		Field tagField = findTagField(stack.getClass());
		if (tagField == null)
			return false;
		tagField.setAccessible(true);
		Object tag = tagField.get(stack);
		if (tag == null)
			return false;

		int stages = getInt(tag, "NumStages");
		if (!hasKey(tag, "NumStages"))
			stages = getInt(tag, "ShapeOrdinal_");
		if (stages <= 0)
			return false;

		boolean hasSolar = false;
		boolean hasLunar = false;
		boolean hasAreaOrZone = false;
		for (int stage = 0; stage < stages; ++stage){
			int shapeID = getInt(tag, "ShapeOrdinal_" + stage);
			hasAreaOrZone |= shapeID == 0 || shapeID == 10;

			for (int modifierID : getIntArray(tag, "SpellModifierIDs_" + stage)){
				hasSolar |= modifierID == SOLAR_MODIFIER_ID;
				hasLunar |= modifierID == LUNAR_MODIFIER_ID;
			}
		}

		if (!hasSolar || !hasLunar || !hasAreaOrZone)
			return false;

		for (int stage = 0; stage < stages; ++stage){
			String key = "SpellModifierIDs_" + stage;
			int[] modifierIDs = getIntArray(tag, key);
			int lunarCount = 0;
			for (int modifierID : modifierIDs){
				if (modifierID == LUNAR_MODIFIER_ID)
					++lunarCount;
			}
			if (lunarCount == 0)
				continue;

			int[] effectiveModifierIDs = new int[modifierIDs.length - lunarCount];
			int index = 0;
			for (int modifierID : modifierIDs){
				if (modifierID != LUNAR_MODIFIER_ID)
					effectiveModifierIDs[index++] = modifierID;
			}
			setIntArray(tag, key, effectiveModifierIDs);
		}
		return true;
	}

	private static void notifyCaster(Object world, Object caster) throws Exception{
		if (world == null || caster == null || isRemote(world))
			return;

		Class<?> playerClass = Class.forName("net.minecraft.entity.player.EntityPlayer");
		if (!playerClass.isInstance(caster))
			return;

		Class<?> chatComponentClass = Class.forName("net.minecraft.util.IChatComponent");
		Class<?> textClass = Class.forName("net.minecraft.util.ChatComponentText");
		Object message = textClass.getConstructor(String.class).newInstance(
				"Esta magia contém Solar e Lunar com AoE/Zone; apenas Solar será aplicado.");
		invoke(caster, new String[]{"addChatMessage", "func_145747_a"},
				new Class<?>[]{chatComponentClass}, message);
	}

	private static boolean isRemote(Object world) throws Exception{
		Field field = findField(world.getClass(), "isRemote", "field_72995_K");
		if (field == null)
			return false;
		field.setAccessible(true);
		return field.getBoolean(world);
	}

	private static Field findTagField(Class<?> type){
		Field field = findField(type, "stackTagCompound", "field_77990_d");
		if (field != null)
			return field;
		for (Class<?> current = type; current != null; current = current.getSuperclass()){
			for (Field candidate : current.getDeclaredFields()){
				if (candidate.getType().getName().equals("net.minecraft.nbt.NBTTagCompound"))
					return candidate;
			}
		}
		return null;
	}

	private static Field findField(Class<?> type, String... names){
		for (Class<?> current = type; current != null; current = current.getSuperclass()){
			for (String name : names){
				try{
					return current.getDeclaredField(name);
				}catch (NoSuchFieldException ignored){
				}
			}
		}
		return null;
	}

	private static boolean hasKey(Object tag, String key) throws Exception{
		Object result = invoke(tag, new String[]{"hasKey", "func_74764_b"},
				new Class<?>[]{String.class}, key);
		return Boolean.TRUE.equals(result);
	}

	private static int getInt(Object tag, String key) throws Exception{
		Object result = invoke(tag, new String[]{"getInteger", "func_74762_e"},
				new Class<?>[]{String.class}, key);
		return ((Number)result).intValue();
	}

	private static int[] getIntArray(Object tag, String key) throws Exception{
		Object result = invoke(tag, new String[]{"getIntArray", "func_74759_k"},
				new Class<?>[]{String.class}, key);
		return result instanceof int[] ? (int[])result : new int[0];
	}

	private static void setIntArray(Object tag, String key, int[] value) throws Exception{
		invoke(tag, new String[]{"setIntArray", "func_74783_a"},
				new Class<?>[]{String.class, int[].class}, key, value);
	}

	private static Object invoke(Object target, String[] names, Class<?>[] parameterTypes, Object... arguments) throws Exception{
		Method method = null;
		for (String name : names){
			for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()){
				try{
					method = current.getDeclaredMethod(name, parameterTypes);
					break;
				}catch (NoSuchMethodException ignored){
				}
			}
			if (method != null)
				break;
		}
		if (method == null)
			throw new NoSuchMethodException(target.getClass().getName());
		method.setAccessible(true);
		return method.invoke(target, arguments);
	}
}
