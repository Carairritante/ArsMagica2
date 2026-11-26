package am2.interop;

import am2.items.ItemEssence;
import cpw.mods.fml.common.Loader;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional bridge to amcinematics.api.RitualGuide, read by reflection so the AM2.5 never
 * depends on AMCinematics. When that mod replaces the World Fusion ritual, the Compendium,
 * the grimoire and the Ritual Architect describe the new one; otherwise nothing changes.
 * ManaLink.getReagents() is deliberately left alone (the Playground consecration uses it).
 */
public final class CinematicsRitualGuide {
	public static final String WORLD_FUSION = "world_fusion";
	private static final String GUIDE = "amcinematics.api.RitualGuide";
	private static final long ACTIVE_CACHE_MS = 1000L;

	private static boolean resolved;
	private static Method active, groundReagents, catalyst, description, activation;
	private static boolean lastActive;
	private static long checkedAt;

	private CinematicsRitualGuide() {}

	private static synchronized boolean resolve() {
		if (!resolved) {
			resolved = true;
			try {
				if (Loader.isModLoaded("amcinematics")) {
					Class<?> guide = Class.forName(GUIDE, false, CinematicsRitualGuide.class.getClassLoader());
					active = guide.getMethod("active");
					groundReagents = guide.getMethod("groundReagents");
					catalyst = guide.getMethod("catalyst");
					description = guide.getMethod("description");
					activation = guide.getMethod("activation");
				}
			} catch (Throwable missing) { // older AMCinematics without the guide
				active = null;
			}
		}
		return active != null;
	}

	/** True only when AMCinematics is present, exposes the guide and has the new ritual enabled. */
	public static boolean active() {
		if (!resolve()) return false;
		long now = System.currentTimeMillis();
		if (now - checkedAt > ACTIVE_CACHE_MS) {
			checkedAt = now;
			lastActive = Boolean.TRUE.equals(call(active));
		}
		return lastActive;
	}

	/** Whether this Compendium / Architect id is World Fusion and the new ritual applies. */
	public static boolean appliesTo(String id) {
		return WORLD_FUSION.equals(id) && active();
	}

	/** Reagents dropped on the ground, quantity in stackSize; copies, empty when inactive. */
	public static ItemStack[] groundReagents() {
		if (!active()) return new ItemStack[0];
		Object value = call(groundReagents);
		if (!(value instanceof ItemStack[])) return new ItemStack[0];
		List<ItemStack> copies = new ArrayList<ItemStack>();
		for (ItemStack stack : (ItemStack[]) value) if (stack != null && stack.getItem() != null) copies.add(stack.copy());
		return copies.toArray(new ItemStack[copies.size()]);
	}

	/** Item held to activate the ritual (consumed), or null. */
	public static ItemStack catalyst() {
		if (!active()) return null;
		Object value = call(catalyst);
		return value instanceof ItemStack && ((ItemStack) value).getItem() != null ? ((ItemStack) value).copy() : null;
	}

	/** Catalyst first, then the ground reagents. */
	public static ItemStack[] allReagents() {
		List<ItemStack> all = new ArrayList<ItemStack>();
		ItemStack orb = catalyst();
		if (orb != null) all.add(orb);
		for (ItemStack stack : groundReagents()) all.add(stack);
		return all.toArray(new ItemStack[all.size()]);
	}

	/** Catalyst, then essences, then the rest: the grimoire card only shows a few icons. */
	public static ItemStack[] previewReagents() {
		List<ItemStack> essences = new ArrayList<ItemStack>(), others = new ArrayList<ItemStack>();
		for (ItemStack stack : groundReagents()) (stack.getItem() instanceof ItemEssence ? essences : others).add(stack);
		List<ItemStack> all = new ArrayList<ItemStack>();
		ItemStack orb = catalyst();
		if (orb != null) all.add(orb);
		all.addAll(essences);
		all.addAll(others);
		return all.toArray(new ItemStack[all.size()]);
	}

	/** Full translated text with !l breaks, or null when inactive. */
	public static String description() {
		return text(description);
	}

	/** Short translated activation instruction, or null when inactive. */
	public static String activation() {
		return text(activation);
	}

	private static String text(Method method) {
		if (!active()) return null;
		Object value = call(method);
		return value instanceof String && !((String) value).trim().isEmpty() ? (String) value : null;
	}

	private static Object call(Method method) {
		try {
			return method == null ? null : method.invoke(null);
		} catch (Throwable failure) {
			return null;
		}
	}
}
