package am2.interop;

import am2.items.ItemEssence;
import am2.cinematics.api.RitualGuide;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets the Compendium, the grimoire and the Ritual Architect describe the hard World Fusion
 * ritual of the built-in cinematics (am2.cinematics) when it replaces the original one;
 * otherwise nothing changes. ManaLink.getReagents() is deliberately left alone (the
 * Playground consecration uses it).
 */
public final class CinematicsRitualGuide {
	public static final String WORLD_FUSION = "world_fusion";
	private static final long ACTIVE_CACHE_MS = 1000L;

	private static boolean lastActive;
	private static long checkedAt;

	private CinematicsRitualGuide() {}

	/** True only when the cinematics are loaded and the new ritual is enabled. */
	public static boolean active() {
		long now = System.currentTimeMillis();
		if (now - checkedAt > ACTIVE_CACHE_MS) {
			checkedAt = now;
			try {
				lastActive = RitualGuide.active();
			} catch (Throwable unavailable) {
				lastActive = false;
			}
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
		ItemStack[] value = RitualGuide.groundReagents();
		if (value == null) return new ItemStack[0];
		List<ItemStack> copies = new ArrayList<ItemStack>();
		for (ItemStack stack : value) if (stack != null && stack.getItem() != null) copies.add(stack.copy());
		return copies.toArray(new ItemStack[copies.size()]);
	}

	/** Item held to activate the ritual (consumed), or null. */
	public static ItemStack catalyst() {
		if (!active()) return null;
		ItemStack value = RitualGuide.catalyst();
		return value != null && value.getItem() != null ? value.copy() : null;
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
		return active() ? text(RitualGuide.description()) : null;
	}

	/** Short translated activation instruction, or null when inactive. */
	public static String activation() {
		return active() ? text(RitualGuide.activation()) : null;
	}

	private static String text(String value) {
		return value != null && !value.trim().isEmpty() ? value : null;
	}
}
