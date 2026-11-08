package am2.client.vfx;

import am2.buffs.BuffList;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderPlayerEvent;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SideOnly(Side.CLIENT)
public class ManaShieldVFX {

	public static final ResourceLocation MANA_SHIELD_TEXTURE = new ResourceLocation("arsmagica2", "textures/misc/vfx/mana_shield_skin.png");

	// Dedicated models sized to contour skin and armor perfectly as a second skin
	private static final ModelBiped modelSkin = new ModelBiped(0.04F);
	private static final ModelBiped modelArmor = new ModelBiped(1.06F);
	private static final ModelBiped modelArmorLegs = new ModelBiped(0.56F);

	private static final Map<Integer, Long> hitFlashes = new ConcurrentHashMap<Integer, Long>();
	private static Field fieldModelBipedMain = null;

	static {
		modelSkin.bipedHeadwear.showModel = false;
		modelArmor.bipedHeadwear.showModel = false;
		modelArmorLegs.bipedHeadwear.showModel = false;

		try {
			fieldModelBipedMain = ReflectionHelper.findField(RenderPlayer.class, "field_77109_a", "modelBipedMain");
			fieldModelBipedMain.setAccessible(true);
		} catch (Throwable t) {
			// fallback
		}
	}

	public static void triggerHitFlash(int entityId) {
		hitFlashes.put(entityId, Minecraft.getSystemTime());
	}

	public static float getHitFlash(Entity entity) {
		if (entity == null) return 0.0f;
		Long timestamp = hitFlashes.get(entity.getEntityId());
		if (timestamp == null) return 0.0f;
		long elapsed = Minecraft.getSystemTime() - timestamp;
		if (elapsed >= 400L) {
			hitFlashes.remove(entity.getEntityId());
			return 0.0f;
		}
		return 1.0f - (elapsed / 400.0f);
	}

	private static ModelBiped getMainModel(RenderPlayer renderer) {
		if (fieldModelBipedMain != null && renderer != null) {
			try {
				return (ModelBiped) fieldModelBipedMain.get(renderer);
			} catch (Throwable t) {
			}
		}
		return null;
	}

	private static void copyModelAngles(ModelRenderer src, ModelRenderer dst) {
		if (src == null || dst == null) return;
		dst.rotateAngleX = src.rotateAngleX;
		dst.rotateAngleY = src.rotateAngleY;
		dst.rotateAngleZ = src.rotateAngleZ;
		dst.rotationPointX = src.rotationPointX;
		dst.rotationPointY = src.rotationPointY;
		dst.rotationPointZ = src.rotationPointZ;
	}

	/**
	 * Renders the minimalist holographic Mana Shield second skin over the player's body and armor.
	 * Hooked into RenderPlayerEvent.Specials.Post where model transforms are active.
	 */
	public static void renderThirdPerson(RenderPlayerEvent.Specials.Post event) {
		EntityPlayer player = event.entityPlayer;
		if (player == null || !player.isPotionActive(BuffList.manaShield)) return;

		ModelBiped mainModel = getMainModel(event.renderer);
		if (mainModel == null) return;

		float age = player.ticksExisted + event.partialRenderTick;
		float pulse = (float) (Math.sin(age * 0.075f) * 0.15f + 0.85f);
		float hitFlash = getHitFlash(player);

		// Alpha breathes between 0.42 and 0.65, spiking on damage absorption
		float alpha = Math.min(1.0f, (0.42f + 0.18f * pulse) + hitFlash * 0.50f);

		// Arcane mana cyan color, flaring white-cyan on hit
		float r = 0.15f + 0.85f * hitFlash;
		float g = 0.82f + 0.18f * hitFlash;
		float b = 1.00f;

		int brightness = player.getBrightnessForRender(event.partialRenderTick);
		int lightX = brightness % 65536;
		int lightY = brightness / 65536;

		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL11.glPushMatrix();

		try {
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); // Additive luminous mana glow
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glDepthMask(false);

			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);
			Minecraft.getMinecraft().getTextureManager().bindTexture(MANA_SHIELD_TEXTURE);

			float scale = 0.0625F;

			// Select models based on whether player has armor equipped on that slot
			ModelBiped headModel = (player.getCurrentArmor(3) != null) ? modelArmor : modelSkin;
			ModelBiped bodyModel = (player.getCurrentArmor(2) != null) ? modelArmor : modelSkin;
			ModelBiped armModel = (player.getCurrentArmor(2) != null) ? modelArmor : modelSkin;
			ModelBiped legModel = (player.getCurrentArmor(1) != null) ? modelArmorLegs : (player.getCurrentArmor(0) != null ? modelArmor : modelSkin);

			// Copy limb angles directly from the currently animated player pose
			copyModelAngles(mainModel.bipedHead, headModel.bipedHead);
			copyModelAngles(mainModel.bipedBody, bodyModel.bipedBody);
			copyModelAngles(mainModel.bipedRightArm, armModel.bipedRightArm);
			copyModelAngles(mainModel.bipedLeftArm, armModel.bipedLeftArm);
			copyModelAngles(mainModel.bipedRightLeg, legModel.bipedRightLeg);
			copyModelAngles(mainModel.bipedLeftLeg, legModel.bipedLeftLeg);

			// Pass 1: Base Second Skin with glowing hexes and runes
			GL11.glColor4f(r, g, b, alpha);
			headModel.bipedHead.render(scale);
			bodyModel.bipedBody.render(scale);
			armModel.bipedRightArm.render(scale);
			armModel.bipedLeftArm.render(scale);
			legModel.bipedRightLeg.render(scale);
			legModel.bipedLeftLeg.render(scale);

			// Pass 2: Holographic Mana Energy Shimmer (drifting UV lattice sheen)
			GL11.glMatrixMode(GL11.GL_TEXTURE);
			GL11.glPushMatrix();
			GL11.glLoadIdentity();
			float scrollY = -(age * 0.008f) % 1.0f;
			GL11.glTranslatef(0.0f, scrollY, 0.0f);
			GL11.glMatrixMode(GL11.GL_MODELVIEW);

			float shimmerAlpha = alpha * 0.40f;
			GL11.glColor4f(0.10f, 0.70f, 1.00f, shimmerAlpha);

			headModel.bipedHead.render(scale);
			bodyModel.bipedBody.render(scale);
			armModel.bipedRightArm.render(scale);
			armModel.bipedLeftArm.render(scale);
			legModel.bipedRightLeg.render(scale);
			legModel.bipedLeftLeg.render(scale);

			GL11.glMatrixMode(GL11.GL_TEXTURE);
			GL11.glPopMatrix();
			GL11.glMatrixMode(GL11.GL_MODELVIEW);

		} finally {
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float) lightX, (float) lightY);
			GL11.glPopMatrix();
			GL11.glPopAttrib();
		}
	}

	/**
	 * Renders the Mana Shield second skin on the first-person arm when casting or holding empty hand.
	 */
	public static void renderFirstPersonArm(EntityPlayer player, RenderPlayer renderer) {
		if (player == null || !player.isPotionActive(BuffList.manaShield)) return;

		ModelBiped mainModel = getMainModel(renderer);
		if (mainModel == null) return;

		float age = player.ticksExisted;
		float pulse = (float) (Math.sin(age * 0.075f) * 0.15f + 0.85f);
		float hitFlash = getHitFlash(player);

		float alpha = Math.min(1.0f, (0.42f + 0.18f * pulse) + hitFlash * 0.50f);
		float r = 0.15f + 0.85f * hitFlash;
		float g = 0.82f + 0.18f * hitFlash;
		float b = 1.00f;

		int brightness = player.getBrightnessForRender(0.0f);
		int lightX = brightness % 65536;
		int lightY = brightness / 65536;

		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL11.glPushMatrix();

		try {
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glDepthMask(false);

			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);
			Minecraft.getMinecraft().getTextureManager().bindTexture(MANA_SHIELD_TEXTURE);

			float scale = 0.0625F;
			ModelBiped armModel = (player.getCurrentArmor(2) != null) ? modelArmor : modelSkin;
			copyModelAngles(mainModel.bipedRightArm, armModel.bipedRightArm);

			GL11.glColor4f(r, g, b, alpha);
			armModel.bipedRightArm.render(scale);

			// Shimmer pass
			GL11.glMatrixMode(GL11.GL_TEXTURE);
			GL11.glPushMatrix();
			GL11.glLoadIdentity();
			float scrollY = -(age * 0.008f) % 1.0f;
			GL11.glTranslatef(0.0f, scrollY, 0.0f);
			GL11.glMatrixMode(GL11.GL_MODELVIEW);

			GL11.glColor4f(0.10f, 0.70f, 1.00f, alpha * 0.40f);
			armModel.bipedRightArm.render(scale);

			GL11.glMatrixMode(GL11.GL_TEXTURE);
			GL11.glPopMatrix();
			GL11.glMatrixMode(GL11.GL_MODELVIEW);

		} finally {
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float) lightX, (float) lightY);
			GL11.glPopMatrix();
			GL11.glPopAttrib();
		}
	}
}
