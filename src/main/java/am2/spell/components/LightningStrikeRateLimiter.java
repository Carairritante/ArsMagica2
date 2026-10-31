package am2.spell.components;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Limits casts of Lightning Strike, Explosion, and Dig, while throttling only lightning/explosion Zone pulses. */
public final class LightningStrikeRateLimiter {
	private static final int LIGHTNING_COMPONENT_ID = 1100;
	private static final int EXPLOSION_COMPONENT_ID = 1092;
	private static final int DIG_COMPONENT_ID = 1008;
	private static final int ZONE_TICK_RATE = 30;
	private static final int MAX_PLAYER_CASTS = 3;
	private static final long PLAYER_CAST_WINDOW_TICKS = 200L;
	private static final Map<Object, WorldState> WORLDS = new WeakHashMap<Object, WorldState>();
	private static final Map<Object, CastWindow> PLAYER_CAST_WINDOWS = new HashMap<Object, CastWindow>();
	private static final Map<Object, ChannelUse> ACTIVE_CHANNELS = new HashMap<Object, ChannelUse>();
	private static boolean failureLogged;

	private LightningStrikeRateLimiter(){ }

	/** Spawn at most one block lightning bolt per caster, spell stack, and block in a world tick. */
	public static boolean spawnBlockBolt(Object world, int x, int y, int z, Object caster, Object stack){
		if (world == null) return false;
		try {
			long tick = getWorldTick(world);
			synchronized (WORLDS){
				WorldState state = WORLDS.get(world);
				if (state == null || state.tick != tick){
					state = new WorldState(tick);
					WORLDS.put(world, state);
				}
				if (!state.allow(caster, stack, x, y, z)) return false;
			}

			Class<?> boltClass = Class.forName("net.minecraft.entity.effect.EntityLightningBolt");
			Class<?> worldBase = Class.forName("net.minecraft.world.World");
			Constructor<?> constructor = boltClass.getConstructor(worldBase, double.class, double.class, double.class);
			Object bolt = constructor.newInstance(world, (double)x, (double)y, (double)z);
			Method addWeatherEffect = findAddWeatherEffect(world.getClass(), boltClass);
			if (addWeatherEffect == null) throw new NoSuchMethodException("World.addWeatherEffect");
			addWeatherEffect.invoke(world, bolt);
			return true;
		} catch (Throwable error){
			logFailureOnce(error);
			return false;
		}
	}

	/** Set Lightning Strike and Explosion zones to one pulse every 30 ticks. */
	public static void configureZone(Object stack, Object zone){
		if (stack == null || zone == null) return;
		try {
			if (!containsZoneLimitedComponent(stack)) return;
			Method setter = findMethod(zone.getClass(), new String[]{"setTickRate"}, int.class);
			if (setter == null) throw new NoSuchMethodException("EntitySpellEffect.setTickRate");
			setter.invoke(zone, Integer.valueOf(ZONE_TICK_RATE));
		} catch (Throwable error){
			logFailureOnce(error);
		}
	}

	/** Applies the player's fixed 3-casts-per-10-seconds window to a successful pre-cast event. */
	public static void applyCastLimit(Object event, Object stack, Object caster, Object world){
		if (event == null || stack == null || caster == null || world == null) return;
		try {
			Field resultField = findField(event.getClass(), "castResult");
			if (resultField == null) return;
			Object result = resultField.get(event);
			if (!(result instanceof Enum) || !"SUCCESS".equals(((Enum<?>)result).name())) return;
			if (isWorldRemote(world)) return;
			String limitedComponent = getCastLimitedComponentName(stack);
			if (limitedComponent == null) return;

			Object playerKey = getPlayerIdentity(caster);
			long tick = getWorldTick(world);
			boolean usingChannel = isSpellChanneled(stack) && isUsingItem(caster);
			Object itemInUse = usingChannel ? getItemInUse(caster) : null;
			int itemUseCount = usingChannel ? getItemUseCount(caster) : -1;
			boolean allowed;
			boolean notify = false;
			int remainingSeconds = 0;
			synchronized (PLAYER_CAST_WINDOWS){
				ChannelUse activeChannel = ACTIVE_CHANNELS.get(playerKey);
				if (usingChannel && activeChannel != null && activeChannel.matches(itemInUse, itemUseCount)){
					activeChannel.lastUseCount = itemUseCount;
					allowed = activeChannel.allowed;
					if (!allowed){
						CastWindow window = PLAYER_CAST_WINDOWS.get(playerKey);
						if (window != null){
							remainingSeconds = remainingSeconds(window, tick);
						notify = shouldNotify(window, tick);
						}
					}
				} else {
					ACTIVE_CHANNELS.remove(playerKey);
					CastWindow window = PLAYER_CAST_WINDOWS.get(playerKey);
					if (window == null || tick < window.startTick || tick - window.startTick >= PLAYER_CAST_WINDOW_TICKS){
						window = new CastWindow(tick);
						PLAYER_CAST_WINDOWS.put(playerKey, window);
					}
					if (window.lastAcceptedTick == tick){
						allowed = true;
					} else if (window.castCount >= MAX_PLAYER_CASTS){
						allowed = false;
						remainingSeconds = remainingSeconds(window, tick);
						notify = shouldNotify(window, tick);
					} else {
						window.castCount++;
						window.lastAcceptedTick = tick;
						allowed = true;
						if (window.castCount == MAX_PLAYER_CASTS){
							remainingSeconds = remainingSeconds(window, tick);
							notify = shouldNotify(window, tick);
						}
					}
					if (usingChannel) ACTIVE_CHANNELS.put(playerKey, new ChannelUse(itemInUse, itemUseCount, allowed));
				}
			}

			if (notify) sendLimitMessage(caster, limitedComponent, remainingSeconds);
			if (!allowed){
				Class<?> resultType = Class.forName("am2.api.spell.enums.SpellCastResult");
				@SuppressWarnings({"rawtypes", "unchecked"})
				Object failed = Enum.valueOf((Class)resultType, "EFFECT_FAILED");
				resultField.set(event, failed);
			}
		} catch (Throwable error){
			logFailureOnce(error);
		}
	}

	private static boolean isWorldRemote(Object world) throws Exception{
		Field field = findField(world.getClass(), "isRemote", "field_72995_K");
		return field != null && field.getBoolean(world);
	}

	private static Object getPlayerIdentity(Object player) throws Exception{
		Method uniqueId = findMethod(player.getClass(), new String[]{"getUniqueID", "func_110124_au"});
		if (uniqueId != null){
			Object id = uniqueId.invoke(player);
			if (id != null) return id;
		}
		Method entityId = findMethod(player.getClass(), new String[]{"getEntityId", "func_145782_y"});
		if (entityId != null){
			Object id = entityId.invoke(player);
			if (id instanceof Number) return Integer.valueOf(((Number)id).intValue());
		}
		return Integer.valueOf(System.identityHashCode(player));
	}

	private static boolean isUsingItem(Object player) throws Exception{
		Method method = findMethod(player.getClass(), new String[]{"isUsingItem", "func_71039_bw"});
		return method != null && Boolean.TRUE.equals(method.invoke(player));
	}

	private static boolean isSpellChanneled(Object stack) throws Exception{
		Class<?> spellUtilsClass = Class.forName("am2.spell.SpellUtils");
		Field instanceField = findField(spellUtilsClass, "instance");
		if (instanceField == null) return false;
		Object spellUtils = instanceField.get(null);
		Class<?> itemStackClass = Class.forName("net.minecraft.item.ItemStack");
		Method method = findMethod(spellUtilsClass, new String[]{"spellIsChanneled"}, itemStackClass);
		return method != null && Boolean.TRUE.equals(method.invoke(spellUtils, stack));
	}

	private static Object getItemInUse(Object player) throws Exception{
		Method method = findMethod(player.getClass(), new String[]{"getItemInUse", "func_71011_bu"});
		return method == null ? null : method.invoke(player);
	}

	private static int getItemUseCount(Object player) throws Exception{
		Method method = findMethod(player.getClass(), new String[]{"getItemInUseCount", "func_71057_bx"});
		if (method == null) return -1;
		Object value = method.invoke(player);
		return value instanceof Number ? ((Number)value).intValue() : -1;
	}

	private static int remainingSeconds(CastWindow window, long tick){
		long remainingTicks = Math.max(0L, window.startTick + PLAYER_CAST_WINDOW_TICKS - tick);
		return Math.max(1, (int)((remainingTicks + 19L) / 20L));
	}

	private static boolean shouldNotify(CastWindow window, long tick){
		if (window.lastNotificationTick == Long.MIN_VALUE || tick - window.lastNotificationTick >= 20L){
			window.lastNotificationTick = tick;
			return true;
		}
		return false;
	}

	private static void sendLimitMessage(Object player, String componentName, int remainingSeconds){
		try {
			Class<?> componentClass = Class.forName("net.minecraft.util.ChatComponentText");
			Object message = componentClass.getConstructor(String.class).newInstance("\u00A7c" + componentName + " 3/3: aguarde " + remainingSeconds + " s para tentar de novo.");
			Method send = findCompatibleSingleArgMethod(player.getClass(), new String[]{"addChatMessage", "func_145747_a"}, componentClass);
			if (send != null) send.invoke(player, message);
		} catch (Throwable error){
			logFailureOnce(error);
		}
	}

	private static Method findCompatibleSingleArgMethod(Class<?> type, String[] names, Class<?> argumentClass){
		for (Class<?> current = type; current != null; current = current.getSuperclass()){
			for (Method method : current.getDeclaredMethods()){
				boolean matchingName = false;
				for (String name : names) if (method.getName().equals(name)) matchingName = true;
				Class<?>[] parameters = method.getParameterTypes();
				if (matchingName && parameters.length == 1 && parameters[0].isAssignableFrom(argumentClass)){
					try { method.setAccessible(true); } catch (Throwable ignored){ }
					return method;
				}
			}
		}
		return null;
	}

	private static String getCastLimitedComponentName(Object stack) throws Exception{
		String componentName = getComponentNameViaSpellUtils(stack);
		if (componentName != null) return componentName;
		return getComponentNameFromNBT(stack);
	}

	private static boolean containsZoneLimitedComponent(Object stack) throws Exception{
		if (getZoneLimitedComponentNameViaSpellUtils(stack) != null) return true;
		return getZoneLimitedComponentNameFromNBT(stack) != null;
	}

	private static String getZoneLimitedComponentNameFromNBT(Object stack) throws Exception{
		Object tag = invokeNoArgs(stack, "getTagCompound", "func_77978_p");
		if (tag == null) return null;
		Method hasKey = findMethod(tag.getClass(), new String[]{"hasKey", "func_74764_b"}, String.class);
		Method getInt = findMethod(tag.getClass(), new String[]{"getInteger", "func_74762_e"}, String.class);
		Method getIntArray = findMethod(tag.getClass(), new String[]{"getIntArray", "func_74759_k"}, String.class);
		if (hasKey == null || getInt == null || getIntArray == null) return null;
		String stagesKey = ((Boolean)hasKey.invoke(tag, "NumStages")).booleanValue() ? "NumStages" : "ShapeOrdinal_";
		if (!((Boolean)hasKey.invoke(tag, stagesKey)).booleanValue()) return null;
		int stages = Math.min(Math.max(((Integer)getInt.invoke(tag, stagesKey)).intValue(), 0), 64);
		for (int stage = 0; stage < stages; stage++){
			String key = "SpellComponentIDs_" + stage;
			if (!((Boolean)hasKey.invoke(tag, key)).booleanValue()) continue;
			int[] ids = (int[])getIntArray.invoke(tag, key);
			if (ids == null) continue;
			for (int id : ids) if (id == LIGHTNING_COMPONENT_ID || id == EXPLOSION_COMPONENT_ID) return componentNameForId(id);
		}
		return null;
	}

	private static String getZoneLimitedComponentNameViaSpellUtils(Object stack){
		try {
			Class<?> spellUtilsClass = Class.forName("am2.spell.SpellUtils");
			Field instanceField = findField(spellUtilsClass, "instance");
			if (instanceField == null) return null;
			Object spellUtils = instanceField.get(null);
			Class<?> itemStackClass = Class.forName("net.minecraft.item.ItemStack");
			Method numStages = findMethod(spellUtilsClass, new String[]{"numStages"}, itemStackClass);
			Method getComponents = findMethod(spellUtilsClass, new String[]{"getComponentsForStage"}, itemStackClass, int.class);
			if (spellUtils == null || numStages == null || getComponents == null) return null;
			int stages = Math.min(Math.max(((Number)numStages.invoke(spellUtils, stack)).intValue(), 0), 64);
			Class<?> lightningClass = Class.forName("am2.spell.components.LightningStrike");
			Class<?> explosionClass = Class.forName("am2.spell.components.Explosion");
			for (int stage = 0; stage < stages; stage++){
				Object[] components = (Object[])getComponents.invoke(spellUtils, stack, Integer.valueOf(stage));
				if (components == null) continue;
				for (Object component : components){
					if (component != null && lightningClass.isInstance(component)) return "Lightning Strike";
					if (component != null && explosionClass.isInstance(component)) return "Explosion";
				}
			}
		} catch (Throwable ignored){ }
		return null;
	}

	private static String getComponentNameFromNBT(Object stack) throws Exception{
		Object tag = invokeNoArgs(stack, "getTagCompound", "func_77978_p");
		if (tag == null) return null;
		Method hasKey = findMethod(tag.getClass(), new String[]{"hasKey", "func_74764_b"}, String.class);
		Method getInt = findMethod(tag.getClass(), new String[]{"getInteger", "func_74762_e"}, String.class);
		Method getIntArray = findMethod(tag.getClass(), new String[]{"getIntArray", "func_74759_k"}, String.class);
		if (hasKey == null || getInt == null || getIntArray == null) return null;
		String stagesKey = ((Boolean)hasKey.invoke(tag, "NumStages")).booleanValue() ? "NumStages" : "ShapeOrdinal_";
		if (!((Boolean)hasKey.invoke(tag, stagesKey)).booleanValue()) return null;
		int stages = ((Integer)getInt.invoke(tag, stagesKey)).intValue();
		stages = Math.min(Math.max(stages, 0), 64);
		for (int stage = 0; stage < stages; stage++){
			String key = "SpellComponentIDs_" + stage;
			if (!((Boolean)hasKey.invoke(tag, key)).booleanValue()) continue;
			int[] ids = (int[])getIntArray.invoke(tag, key);
			if (ids == null) continue;
			for (int id : ids){
				String name = componentNameForId(id);
				if (name != null) return name;
			}
		}
		return null;
	}

	private static String getComponentNameViaSpellUtils(Object stack){
		try {
			Class<?> spellUtilsClass = Class.forName("am2.spell.SpellUtils");
			Field instanceField = findField(spellUtilsClass, "instance");
			if (instanceField == null) return null;
			Object spellUtils = instanceField.get(null);
			Class<?> itemStackClass = Class.forName("net.minecraft.item.ItemStack");
			Method numStages = findMethod(spellUtilsClass, new String[]{"numStages"}, itemStackClass);
			Method getComponents = findMethod(spellUtilsClass, new String[]{"getComponentsForStage"}, itemStackClass, int.class);
			if (spellUtils == null || numStages == null || getComponents == null) return null;
			int stages = ((Number)numStages.invoke(spellUtils, stack)).intValue();
			stages = Math.min(Math.max(stages, 0), 64);
			Class<?> lightningClass = Class.forName("am2.spell.components.LightningStrike");
			Class<?> explosionClass = Class.forName("am2.spell.components.Explosion");
			Class<?> digClass = Class.forName("am2.spell.components.Dig");
			for (int stage = 0; stage < stages; stage++){
				Object[] components = (Object[])getComponents.invoke(spellUtils, stack, Integer.valueOf(stage));
				if (components == null) continue;
				for (Object component : components){
					if (component != null){
						if (lightningClass.isInstance(component)) return "Lightning Strike";
						if (explosionClass.isInstance(component)) return "Explosion";
						if (digClass.isInstance(component)) return "Dig";
					}
				}
			}
		} catch (Throwable ignored){ }
		return null;
	}

	private static String componentNameForId(int id){
		if (id == LIGHTNING_COMPONENT_ID) return "Lightning Strike";
		if (id == EXPLOSION_COMPONENT_ID) return "Explosion";
		if (id == DIG_COMPONENT_ID) return "Dig";
		return null;
	}

	private static long getWorldTick(Object world) throws Exception{
		Method method = findMethod(world.getClass(), new String[]{"getTotalWorldTime", "func_82737_E"});
		if (method == null) method = findMethod(world.getClass(), new String[]{"getWorldTime", "func_72820_D"});
		if (method != null){
			Object value = method.invoke(world);
			if (value instanceof Number) return ((Number)value).longValue();
		}
		Field field = findField(world.getClass(), "totalWorldTime", "field_72986_A");
		if (field != null){
			Object value = field.get(world);
			if (value instanceof Number) return ((Number)value).longValue();
		}
		throw new NoSuchMethodException("World time getter");
	}

	private static Method findAddWeatherEffect(Class<?> worldClass, Class<?> boltClass){
		for (Class<?> type = worldClass; type != null; type = type.getSuperclass()){
			for (Method method : type.getDeclaredMethods()){
				String name = method.getName();
				if (!(name.equals("addWeatherEffect") || name.equals("func_72942_c"))) continue;
				Class<?>[] parameters = method.getParameterTypes();
				if (parameters.length == 1 && parameters[0].isAssignableFrom(boltClass)){
					try { method.setAccessible(true); } catch (Throwable ignored){ }
					return method;
				}
			}
		}
		return null;
	}

	private static Object invokeNoArgs(Object target, String... names) throws Exception{
		Method method = findMethod(target.getClass(), names);
		return method == null ? null : method.invoke(target);
	}

	private static Method findMethod(Class<?> type, String[] names, Class<?>... parameters){
		for (Class<?> current = type; current != null; current = current.getSuperclass()){
			for (String name : names){
				try {
					Method method = current.getDeclaredMethod(name, parameters);
					try { method.setAccessible(true); } catch (Throwable ignored){ }
					return method;
				} catch (NoSuchMethodException ignored){ }
			}
		}
		return null;
	}

	private static Field findField(Class<?> type, String... names){
		for (Class<?> current = type; current != null; current = current.getSuperclass()){
			for (String name : names){
				try {
					Field field = current.getDeclaredField(name);
					try { field.setAccessible(true); } catch (Throwable ignored){ }
					return field;
				} catch (NoSuchFieldException ignored){ }
			}
		}
		return null;
	}

	private static void logFailureOnce(Throwable error){
		synchronized (LightningStrikeRateLimiter.class){
			if (failureLogged) return;
			failureLogged = true;
			System.err.println("[Ars Magica 2] Spell component limits could not be applied.");
			error.printStackTrace();
		}
	}

	private static final class WorldState{
		private long tick;
		private final IdentityHashMap<Object, IdentityHashMap<Object, Set<BlockPosition>>> blocksByCaster = new IdentityHashMap<Object, IdentityHashMap<Object, Set<BlockPosition>>>();
		private WorldState(long tick){ this.tick = tick; }
		private boolean allow(Object caster, Object stack, int x, int y, int z){
			Object casterKey = caster == null ? this : caster;
			IdentityHashMap<Object, Set<BlockPosition>> stacks = blocksByCaster.get(casterKey);
			if (stacks == null){
				stacks = new IdentityHashMap<Object, Set<BlockPosition>>();
				blocksByCaster.put(casterKey, stacks);
			}
			Object stackKey = stack == null ? this : stack;
			Set<BlockPosition> blocks = stacks.get(stackKey);
			if (blocks == null){
				blocks = new HashSet<BlockPosition>();
				stacks.put(stackKey, blocks);
			}
			return blocks.add(new BlockPosition(x, y, z));
		}
	}

	private static final class CastWindow{
		private final long startTick;
		private int castCount;
		private long lastAcceptedTick = Long.MIN_VALUE;
		private long lastNotificationTick = Long.MIN_VALUE;
		private CastWindow(long startTick){ this.startTick = startTick; }
	}

	private static final class ChannelUse{
		private final Object itemStack;
		private int lastUseCount;
		private final boolean allowed;
		private ChannelUse(Object itemStack, int itemUseCount, boolean allowed){
			this.itemStack = itemStack;
			this.lastUseCount = itemUseCount;
			this.allowed = allowed;
		}
		private boolean matches(Object currentStack, int currentUseCount){
			return currentUseCount >= 0 && currentUseCount <= lastUseCount && itemStack == currentStack;
		}
	}

	private static final class BlockPosition{
		private final int x;
		private final int y;
		private final int z;
		private BlockPosition(int x, int y, int z){ this.x = x; this.y = y; this.z = z; }
		@Override public int hashCode(){
			int result = x;
			result = 31 * result + y;
			return 31 * result + z;
		}
		@Override public boolean equals(Object other){
			if (!(other instanceof BlockPosition)) return false;
			BlockPosition position = (BlockPosition)other;
			return x == position.x && y == position.y && z == position.z;
		}
	}
}

