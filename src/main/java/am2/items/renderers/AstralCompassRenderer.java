package am2.items.renderers;

import am2.client.vfx.BlockbenchRigModel;
import am2.items.ItemAstralCompass;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

/**
 * 3D Blockbench astrolabe renderer for the Astral Structure Compass item.
 * Animates dual gimbal rings, pulsing star core, and a live 3D needle pointing toward locked structures.
 */
@SideOnly(Side.CLIENT)
public final class AstralCompassRenderer implements IItemRenderer {

	private static final ResourceLocation MODEL_LOC = new ResourceLocation("arsmagica2", "models/items/astral_compass.bbmodel");
	private static BlockbenchRigModel model;
	private static boolean loadAttempted = false;

	private final Map<String, BlockbenchRigModel.BoneOverride> overrides = new HashMap<String, BlockbenchRigModel.BoneOverride>();
	private final BlockbenchRigModel.BoneOverride outerGimbal = new BlockbenchRigModel.BoneOverride();
	private final BlockbenchRigModel.BoneOverride innerGimbal = new BlockbenchRigModel.BoneOverride();
	private final BlockbenchRigModel.BoneOverride astralCore = new BlockbenchRigModel.BoneOverride();
	private final BlockbenchRigModel.BoneOverride compassNeedle = new BlockbenchRigModel.BoneOverride();

	public AstralCompassRenderer() {
		overrides.put("outer_gimbal", outerGimbal);
		overrides.put("inner_gimbal", innerGimbal);
		overrides.put("astral_core", astralCore);
		overrides.put("compass_needle", compassNeedle);
	}

	private static BlockbenchRigModel getModel() {
		if (!loadAttempted) {
			loadAttempted = true;
			model = BlockbenchRigModel.load(MODEL_LOC, "am2_astral_compass", "crystal_lens");
		}
		return model;
	}

	@Override
	public boolean handleRenderType(ItemStack item, ItemRenderType type) {
		return type == ItemRenderType.EQUIPPED
				|| type == ItemRenderType.EQUIPPED_FIRST_PERSON
				|| type == ItemRenderType.ENTITY;
	}

	@Override
	public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
		return type == ItemRenderType.ENTITY && (helper == ItemRendererHelper.ENTITY_ROTATION || helper == ItemRendererHelper.ENTITY_BOBBING);
	}

	@Override
	public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
		BlockbenchRigModel rig = getModel();
		if (rig == null || item == null) {
			return;
		}

		Minecraft mc = Minecraft.getMinecraft();
		double timeSec = (System.currentTimeMillis() % 1000000L) / 1000.0D;

		outerGimbal.reset();
		innerGimbal.reset();
		astralCore.reset();
		compassNeedle.reset();

		ItemAstralCompass.TargetData target = ItemAstralCompass.getTarget(item);
		boolean hasTarget = target != null;

		float ringSpeed = hasTarget ? 32.0f : 12.0f;
		outerGimbal.dry = (float) ((timeSec * ringSpeed) % 360.0);
		outerGimbal.drx = (float) Math.sin(timeSec * 1.4) * 6.0f;
		innerGimbal.dry = (float) ((-timeSec * ringSpeed * 1.6) % 360.0);
		innerGimbal.drz = (float) Math.cos(timeSec * 1.7) * 8.0f;
		astralCore.dy = (float) Math.sin(timeSec * 3.0) * 0.25f;
		astralCore.dry = (float) ((timeSec * 45.0) % 360.0);

		compassNeedle.dry = computeNeedleAngleDeg(type, mc, target, timeSec, data);

		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
		GL11.glPushMatrix();
		try {
			if (type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
				// Position comfortably in first-person right hand tilted so the 3D dial & needle are clearly legible
				GL11.glTranslatef(0.70f, 0.45f, 0.35f);
				GL11.glRotatef(36.0f, 1.0f, 0.0f, 0.0f);
				GL11.glRotatef(-12.0f, 0.0f, 0.0f, 1.0f);
				GL11.glScalef(0.88f, 0.88f, 0.88f);
			} else if (type == ItemRenderType.EQUIPPED) {
				GL11.glTranslatef(0.55f, 0.30f, 0.45f);
				GL11.glRotatef(52.0f, 1.0f, 0.0f, 0.0f);
				GL11.glRotatef(-15.0f, 0.0f, 1.0f, 0.0f);
				GL11.glScalef(0.75f, 0.75f, 0.75f);
			} else if (type == ItemRenderType.ENTITY) {
				GL11.glTranslatef(0.0f, 0.10f, 0.0f);
				GL11.glScalef(0.78f, 0.78f, 0.78f);
			}

			rig.render(null, 0.0f, overrides, 1.0f, 1.0f, 1.0f, 1.0f);
		} finally {
			GL11.glPopMatrix();
			GL11.glPopAttrib();
		}
	}

	private static float computeNeedleAngleDeg(ItemRenderType type, Minecraft mc, ItemAstralCompass.TargetData target, double timeSec, Object... data) {
		if (target == null || mc == null || mc.theWorld == null) {
			return (float) ((timeSec * 28.0) % 360.0);
		}
		if (mc.theWorld.provider.dimensionId != target.dimension) {
			// Rapid celestial wobble when attuned to a different dimension
			return (float) ((timeSec * 140.0) % 360.0);
		}

		double refX = mc.thePlayer != null ? mc.thePlayer.posX : 0.0;
		double refZ = mc.thePlayer != null ? mc.thePlayer.posZ : 0.0;
		float refYaw = mc.thePlayer != null ? MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw) : 0.0f;

		if (type == ItemRenderType.ENTITY && data != null && data.length > 1 && data[1] instanceof Entity) {
			Entity itemEntity = (Entity) data[1];
			refX = itemEntity.posX;
			refZ = itemEntity.posZ;
			refYaw = 180.0f;
		} else if (type == ItemRenderType.EQUIPPED && data != null && data.length > 1 && data[1] instanceof EntityLivingBase) {
			EntityLivingBase holder = (EntityLivingBase) data[1];
			refX = holder.posX;
			refZ = holder.posZ;
			refYaw = MathHelper.wrapAngleTo180_float(holder.rotationYaw);
		}

		double dx = (target.x + 0.5D) - refX;
		double dz = (target.z + 0.5D) - refZ;
		double worldDeg = Math.toDegrees(Math.atan2(-dx, -dz));
		if (type == ItemRenderType.ENTITY) {
			return (float) worldDeg;
		}
		return (float) (worldDeg + refYaw + 180.0D);
	}
}
