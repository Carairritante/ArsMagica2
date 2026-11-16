package am2.client.vfx;

import am2.AMCore;
import am2.api.spell.ItemSpellBase;
import am2.api.spell.component.interfaces.ISpellModifier;
import am2.api.spell.component.interfaces.ISpellShape;
import am2.api.spell.enums.Affinity;
import am2.api.spell.enums.SpellModifiers;
import am2.items.ItemSpellBook;
import am2.items.ItemSpellStaff;
import am2.spell.SpellUtils;
import am2.spell.modifiers.Colour;
import am2.spell.shapes.Beam;
import am2.utility.MathUtilities;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages live channeled Blockbench anime beams for casters (local + remote + mobs)
 * and renders them during RenderWorldLastEvent without blocking the first-person camera.
 */
@SideOnly(Side.CLIENT)
public final class AnimeBeamVFX {

	private static final int MAX_RENDERED_BEAMS = 16;
	private static final double MAX_RENDER_DIST_SQ = 96.0 * 96.0;
	private static final int FADE_TICKS = 4;

	private static final Map<Integer, ActiveBeam> ACTIVE_BEAMS = new ConcurrentHashMap<Integer, ActiveBeam>();
	private static final Map<EntityPlayer, RemoteSpellCache> REMOTE_CACHE = new WeakHashMap<EntityPlayer, RemoteSpellCache>();
	private static final FloatBuffer MATRIX_BUF = BufferUtils.createFloatBuffer(16);
	private static final FloatBuffer PROJ_BUF = BufferUtils.createFloatBuffer(16);

	private static final class ActiveBeam {
		int casterId;
		EntityLivingBase caster;
		Affinity affinity;
		int customColor;
		double targetX, targetY, targetZ;
		double prevTargetX, prevTargetY, prevTargetZ;
		long burstOriginMs;
		int lastUseCount = Integer.MAX_VALUE;
		int idleTicks;
		float alpha = 1.0f;
		float prevAlpha = 1.0f;
		boolean updatedThisTick;
	}

	private static final class RemoteSpellCache {
		ItemStack held;
		int analyzedTick = Integer.MIN_VALUE;
		boolean isBeam;
		Affinity affinity = Affinity.NONE;
		int customColor = -1;
		ItemStack spellStack;
	}

	private AnimeBeamVFX() {
	}

	public static void clearAll() {
		ACTIVE_BEAMS.clear();
		REMOTE_CACHE.clear();
	}

	public static void updateCasterBeam(
			World world,
			EntityLivingBase caster,
			Affinity affinity,
			double endX,
			double endY,
			double endZ,
			int customColor,
			int useCount
	) {
		if (world == null || !world.isRemote || caster == null || AMCore.config.NoGFX()) {
			return;
		}
		if (affinity == null) {
			affinity = Affinity.NONE;
		}

		long now = Minecraft.getSystemTime();
		int id = caster.getEntityId();
		ActiveBeam beam = ACTIVE_BEAMS.get(id);
		if (beam == null) {
			beam = new ActiveBeam();
			beam.casterId = id;
			beam.caster = caster;
			beam.affinity = affinity;
			beam.customColor = customColor;
			beam.targetX = endX;
			beam.targetY = endY;
			beam.targetZ = endZ;
			beam.prevTargetX = endX;
			beam.prevTargetY = endY;
			beam.prevTargetZ = endZ;
			beam.burstOriginMs = now;
			beam.lastUseCount = useCount;
			beam.alpha = 1.0f;
			beam.prevAlpha = 0.25f;
			beam.updatedThisTick = true;
			ACTIVE_BEAMS.put(id, beam);
			return;
		}

		boolean restarted = beam.affinity != affinity
				|| beam.idleTicks > 1
				|| useCount > beam.lastUseCount + 1;
		beam.caster = caster;
		beam.affinity = affinity;
		beam.customColor = customColor;
		if (!beam.updatedThisTick) {
			beam.prevTargetX = beam.targetX;
			beam.prevTargetY = beam.targetY;
			beam.prevTargetZ = beam.targetZ;
			beam.updatedThisTick = true;
		}
		beam.targetX = endX;
		beam.targetY = endY;
		beam.targetZ = endZ;
		beam.lastUseCount = useCount;
		beam.idleTicks = 0;
		if (restarted) {
			beam.burstOriginMs = now;
			beam.prevTargetX = endX;
			beam.prevTargetY = endY;
			beam.prevTargetZ = endZ;
		}
	}

	public static void onClientTick(Minecraft mc) {
		if (mc == null || mc.theWorld == null || AMCore.config.NoGFX()) {
			if (!ACTIVE_BEAMS.isEmpty()) {
				ACTIVE_BEAMS.clear();
			}
			return;
		}

		updateRemoteCasters(mc);

		Iterator<Map.Entry<Integer, ActiveBeam>> it = ACTIVE_BEAMS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, ActiveBeam> entry = it.next();
			ActiveBeam beam = entry.getValue();
			Entity e = mc.theWorld.getEntityByID(beam.casterId);
			if (!(e instanceof EntityLivingBase) || e.isDead) {
				it.remove();
				continue;
			}
			beam.caster = (EntityLivingBase) e;
			beam.prevAlpha = beam.alpha;
			if (beam.updatedThisTick) {
				beam.updatedThisTick = false;
				beam.idleTicks = 0;
				beam.alpha = Math.min(1.0f, beam.alpha + 0.55f);
			} else {
				beam.idleTicks++;
				beam.prevTargetX = beam.targetX;
				beam.prevTargetY = beam.targetY;
				beam.prevTargetZ = beam.targetZ;
				beam.alpha = Math.max(0.0f, 1.0f - ((float) beam.idleTicks / (float) FADE_TICKS));
				if (beam.idleTicks > FADE_TICKS) {
					it.remove();
				}
			}
		}
	}

	private static void updateRemoteCasters(Minecraft mc) {
		if (mc.theWorld.playerEntities == null) {
			return;
		}
		for (Object obj : mc.theWorld.playerEntities) {
			if (!(obj instanceof EntityPlayer)) {
				continue;
			}
			EntityPlayer player = (EntityPlayer) obj;
			if (player == mc.thePlayer || player.isDead) {
				continue;
			}
			ItemStack held = player.getHeldItem();
			if (!CastingAnimationState.isCasting(player, held)) {
				continue;
			}
			RemoteSpellCache info = inspectRemoteSpell(player, held);
			if (!info.isBeam || info.spellStack == null || !(info.spellStack.getItem() instanceof ItemSpellBase)) {
				continue;
			}
			ActiveBeam existing = ACTIVE_BEAMS.get(player.getEntityId());
			if (existing != null && existing.updatedThisTick) {
				continue;
			}

			ItemSpellBase spellItem = (ItemSpellBase) info.spellStack.getItem();
			double range = SpellUtils.instance.getModifiedDouble_Add(SpellModifiers.RANGE, info.spellStack, player, null, mc.theWorld, 0);
			boolean targetWater = SpellUtils.instance.modifierIsPresent(SpellModifiers.TARGET_NONSOLID_BLOCKS, info.spellStack, 0);
			MovingObjectPosition mop = spellItem.getMovingObjectPosition(player, mc.theWorld, range, true, targetWater);
			Vec3 hit = (mop != null && mop.hitVec != null)
					? mop.hitVec
					: MathUtilities.extrapolateEntityLook(mc.theWorld, player, range);
			if (hit != null) {
				updateCasterBeam(mc.theWorld, player, info.affinity, hit.xCoord, hit.yCoord, hit.zCoord,
						info.customColor, player.getItemInUseCount());
			}
		}
	}

	private static RemoteSpellCache inspectRemoteSpell(EntityPlayer player, ItemStack held) {
		RemoteSpellCache cache = REMOTE_CACHE.get(player);
		if (cache == null) {
			cache = new RemoteSpellCache();
			REMOTE_CACHE.put(player, cache);
		}
		int now = player.ticksExisted;
		if (cache.held == held && (now - cache.analyzedTick) < 5 && now >= cache.analyzedTick) {
			return cache;
		}
		cache.held = held;
		cache.analyzedTick = now;
		cache.isBeam = false;
		cache.spellStack = null;
		cache.affinity = Affinity.NONE;
		cache.customColor = -1;

		try {
			ItemStack spell = null;
			if (held.getItem() instanceof ItemSpellBook) {
				spell = ((ItemSpellBook) held.getItem()).GetActiveItemStack(held);
			} else if (held.getItem() instanceof ItemSpellStaff) {
				if (held.hasTagCompound() && held.stackTagCompound.hasKey("spell_to_cast", 10)) {
					spell = ItemStack.loadItemStackFromNBT(held.stackTagCompound.getCompoundTag("spell_to_cast"));
				}
			} else if (held.getItem() instanceof ItemSpellBase) {
				spell = held;
			}
			if (spell != null && spell.getItem() instanceof ItemSpellBase && SpellUtils.instance.numStages(spell) > 0) {
				ISpellShape shape = SpellUtils.instance.getShapeForStage(spell, 0);
				if (shape instanceof Beam) {
					cache.isBeam = true;
					cache.spellStack = spell;
					cache.affinity = SpellUtils.instance.mainAffinityFor(spell);
					if (SpellUtils.instance.modifierIsPresent(SpellModifiers.COLOR, spell, 0)) {
						ISpellModifier[] mods = SpellUtils.instance.getModifiersForStage(spell, 0);
						int ordinal = 0;
						for (ISpellModifier mod : mods) {
							if (mod instanceof Colour) {
								byte[] meta = SpellUtils.instance.getModifierMetadataFromStack(spell, mod, 0, ordinal++);
								cache.customColor = (int) mod.getModifier(SpellModifiers.COLOR, null, null, null, meta);
							}
						}
					}
				}
			}
		} catch (Throwable ignored) {
			cache.isBeam = false;
		}
		return cache;
	}

	public static void renderAll(float partialTicks) {
		if (ACTIVE_BEAMS.isEmpty() || AMCore.config.NoGFX()) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null || mc.renderViewEntity == null) {
			return;
		}

		Entity view = mc.renderViewEntity;
		double cx = view.prevPosX + (view.posX - view.prevPosX) * partialTicks;
		double cy = view.prevPosY + (view.posY - view.prevPosY) * partialTicks;
		double cz = view.prevPosZ + (view.posZ - view.prevPosZ) * partialTicks;

		float prevLightX = OpenGlHelper.lastBrightnessX;
		float prevLightY = OpenGlHelper.lastBrightnessY;

		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glDepthMask(false);
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);

		long nowMs = Minecraft.getSystemTime();
		boolean lowGfx = AMCore.config.LowGFX();
		int rendered = 0;

		PROJ_BUF.clear();
		GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJ_BUF);
		float projM00 = Math.abs(PROJ_BUF.get(0)) > 0.05f ? PROJ_BUF.get(0) : 0.78f;
		float projM11 = Math.abs(PROJ_BUF.get(5)) > 0.05f ? PROJ_BUF.get(5) : 1.38f;

		for (ActiveBeam beam : ACTIVE_BEAMS.values()) {
			if (rendered >= MAX_RENDERED_BEAMS) {
				break;
			}
			EntityLivingBase caster = beam.caster;
			if (caster == null || caster.isDead) {
				continue;
			}
			float alpha = beam.prevAlpha + (beam.alpha - beam.prevAlpha) * partialTicks;
			if (alpha <= 0.01f) {
				continue;
			}

			boolean firstPerson = (caster == mc.renderViewEntity && mc.gameSettings.thirdPersonView == 0);
			double sx;
			double sy;
			double sz;

			if (firstPerson) {
				sx = cx;
				sy = cy;
				sz = cz;
			} else {
				sx = caster.prevPosX + (caster.posX - caster.prevPosX) * partialTicks;
				double interpY = caster.prevPosY + (caster.posY - caster.prevPosY) * partialTicks;
				sz = caster.prevPosZ + (caster.posZ - caster.prevPosZ) * partialTicks;
				if (caster == mc.thePlayer) {
					double feet = interpY - caster.yOffset;
					sy = feet + (caster.isSneaking() ? 1.18 : 1.38);
				} else {
					sy = interpY + Math.max(0.5, caster.getEyeHeight() - 0.18);
				}
			}

			double ex = beam.prevTargetX + (beam.targetX - beam.prevTargetX) * partialTicks;
			double ey = beam.prevTargetY + (beam.targetY - beam.prevTargetY) * partialTicks;
			double ez = beam.prevTargetZ + (beam.targetZ - beam.prevTargetZ) * partialTicks;

			double relX = sx - cx;
			double relY = sy - cy;
			double relZ = sz - cz;
			if (!firstPerson && (relX * relX + relY * relY + relZ * relZ) > MAX_RENDER_DIST_SQ) {
				continue;
			}

			double dx = ex - sx;
			double dy = ey - sy;
			double dz = ez - sz;
			double lenSq = dx * dx + dy * dy + dz * dz;
			if (lenSq < 0.04) {
				continue;
			}
			float len = (float) Math.sqrt(lenSq);
			float fx = (float) (dx / len);
			float fy = (float) (dy / len);
			float fz = (float) (dz / len);

			// Reference up avoids gimbal flip when aiming straight vertically
			float ux = 0.0f;
			float uy = 1.0f;
			float uz = 0.0f;
			if (Math.abs(fy) > 0.992f) {
				uy = 0.0f;
				uz = 1.0f;
			}
			// Right = Forward x UpRef
			float rx = fy * uz - fz * uy;
			float ry = fz * ux - fx * uz;
			float rz = fx * uy - fy * ux;
			float rLen = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
			if (rLen < 1e-5f) {
				continue;
			}
			rx /= rLen;
			ry /= rLen;
			rz /= rLen;

			if (firstPerson) {
				// Anchor beam muzzle directly to the vanilla first-person bare right hand in screen space
				// regardless of the player's current world FOV setting
				double yawRad = (caster.prevRotationYaw + (caster.rotationYaw - caster.prevRotationYaw) * partialTicks) * (Math.PI / 180.0);
				double pitchRad = (caster.prevRotationPitch + (caster.rotationPitch - caster.prevRotationPitch) * partialTicks) * (Math.PI / 180.0);
				double cosPitch = Math.cos(pitchRad);
				double lookX = -Math.sin(yawRad) * cosPitch;
				double lookY = -Math.sin(pitchRad);
				double lookZ = Math.cos(yawRad) * cosPitch;
				double camRightX = -Math.cos(yawRad);
				double camRightY = 0.0;
				double camRightZ = -Math.sin(yawRad);
				double camUpX = camRightY * lookZ - camRightZ * lookY;
				double camUpY = camRightZ * lookX - camRightX * lookZ;
				double camUpZ = camRightX * lookY - camRightY * lookX;

				double handDepth = Math.min(1.42, Math.max(0.55, len * 0.45));
				double rightOff = (0.48 / projM00) * handDepth;
				double upOff = (-0.43 / projM11) * handDepth;

				sx = cx + camRightX * rightOff + camUpX * upOff + lookX * handDepth;
				sy = cy + camRightY * rightOff + camUpY * upOff + lookY * handDepth;
				sz = cz + camRightZ * rightOff + camUpZ * upOff + lookZ * handDepth;

				relX = sx - cx;
				relY = sy - cy;
				relZ = sz - cz;
				dx = ex - sx;
				dy = ey - sy;
				dz = ez - sz;
				lenSq = dx * dx + dy * dy + dz * dz;
				if (lenSq < 0.04) {
					continue;
				}
				len = (float) Math.sqrt(lenSq);
				fx = (float) (dx / len);
				fy = (float) (dy / len);
				fz = (float) (dz / len);

				ux = 0.0f;
				uy = 1.0f;
				uz = 0.0f;
				if (Math.abs(fy) > 0.992f) {
					uy = 0.0f;
					uz = 1.0f;
				}
				rx = fy * uz - fz * uy;
				ry = fz * ux - fx * uz;
				rz = fx * uy - fy * ux;
				rLen = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
				if (rLen < 1e-5f) {
					continue;
				}
				rx /= rLen;
				ry /= rLen;
				rz /= rLen;
			}

			// Re-orthonormalized Up = Right x Forward
			float u2x = ry * fz - rz * fy;
			float u2y = rz * fx - rx * fz;
			float u2z = rx * fy - ry * fx;

			BlockbenchBeamModel model = BlockbenchBeamModel.get(beam.affinity);
			if (model == null) {
				continue;
			}

			MATRIX_BUF.clear();
			// Column 0: +X (Right)
			MATRIX_BUF.put(rx);
			MATRIX_BUF.put(ry);
			MATRIX_BUF.put(rz);
			MATRIX_BUF.put(0.0f);
			// Column 1: +Y (Up)
			MATRIX_BUF.put(u2x);
			MATRIX_BUF.put(u2y);
			MATRIX_BUF.put(u2z);
			MATRIX_BUF.put(0.0f);
			// Column 2: +Z (-Forward, so local -Z aims along Forward)
			MATRIX_BUF.put(-fx);
			MATRIX_BUF.put(-fy);
			MATRIX_BUF.put(-fz);
			MATRIX_BUF.put(0.0f);
			// Column 3: translation
			MATRIX_BUF.put(0.0f);
			MATRIX_BUF.put(0.0f);
			MATRIX_BUF.put(0.0f);
			MATRIX_BUF.put(1.0f);
			MATRIX_BUF.flip();

			float ageSec = Math.max(0.0f, (nowMs - beam.burstOriginMs) * 0.001f);
			float muzzleStartBlocks = firstPerson ? Math.min(0.18f, len * 0.14f) : 0.875f;

			GL11.glPushMatrix();
			GL11.glTranslated(relX, relY, relZ);
			GL11.glMultMatrix(MATRIX_BUF);
			model.render(len, ageSec, alpha, muzzleStartBlocks, 1.0f, firstPerson, lowGfx, beam.customColor);
			GL11.glPopMatrix();

			rendered++;
		}

		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevLightX, prevLightY);
		GL11.glPopAttrib();
	}
}
