package am2.blocks.renderers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Everything the Oculus look needs for one frame, already computed and interpolated by
 * {@link OcculusRenderer}. {@link OcculusStyle} only reads it.
 */
@SideOnly(Side.CLIENT)
public final class OcculusView {
	/** Block facing in degrees (multiples of 90), as the classic renderer turns it. */
	public float facing;
	/** Where the eye looks, in degrees relative to the block front (0, 0 = straight ahead). */
	public float eyeYaw, eyePitch;
	/** 0 = nobody near (resting), 1 = a player is close. */
	public float open;
	/** 0..1: the nearest player can read the Oculus (magic level above 0). */
	public float awake;
	/** Seconds of world time, with partial ticks, for idle motion. */
	public float time;
	/** Drawn as an item (hand, inventory or dropped), without a world. */
	public boolean inventory;
}
