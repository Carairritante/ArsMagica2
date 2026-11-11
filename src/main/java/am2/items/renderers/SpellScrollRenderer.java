package am2.items.renderers;

import am2.api.spell.ItemSpellBase;
import am2.client.vfx.CastingAnimationState;
import am2.api.spell.enums.Affinity;
import am2.items.ItemSpellBook;
import am2.items.ItemsCommonProxy;
import am2.particles.AMParticleIcons;
import am2.spell.SpellUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelBook;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.EnumAction;
import net.minecraft.potion.Potion;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.HashMap;
import java.util.Map;

public class SpellScrollRenderer implements IItemRenderer{

	private static RenderItem renderItem = new RenderItem();
	private final ModelBiped modelBipedMain;
	private final ModelBook modelBook;
	private final Minecraft mc;
	private final Map<Affinity, IIcon> icons;
	private boolean setupIcons = false;
	private static final ResourceLocation rLoc = new ResourceLocation("textures/atlas/items.png");
	private static final ResourceLocation bookTexture = new ResourceLocation("textures/entity/enchanting_table_book.png");
	private static final ResourceLocation arcaneBookTexture = new ResourceLocation("arsmagica2", "textures/models/arcane_book_open.png");

	public static SpellScrollRenderer instance = new SpellScrollRenderer();

	private SpellScrollRenderer(){
		modelBipedMain = new ModelBiped(0.0F);
		modelBook = new ModelBook();
		mc = Minecraft.getMinecraft();
		icons = new HashMap<Affinity, IIcon>();
	}

	@Override
	public boolean handleRenderType(ItemStack item, ItemRenderType type){
		if (type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON){
			if (item != null && (item.getItem() instanceof ItemSpellBase || item.getItem() instanceof ItemSpellBook)){
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper){
		return true;
	}

	@Override
	public void renderItem(ItemRenderType type, ItemStack item, Object... data){

		if (mc.thePlayer != null && mc.thePlayer.isPotionActive(Potion.invisibility.id)) return;

		ItemStack scrollStack = null;
		if (item.getItem() instanceof ItemSpellBase){
			scrollStack = item;
		}else if (item.getItem() instanceof ItemSpellBook){
			scrollStack = ((ItemSpellBook)item.getItem()).GetActiveItemStack(item);
		}

		Affinity affinity = scrollStack != null ? am2.client.vfx.CastingCircleVFX.safeAffinity(scrollStack) : Affinity.NONE;

		if (item.getItem() instanceof ItemSpellBook){
			renderSpellBook(type, item, scrollStack, affinity, data);
		}else if (scrollStack != null){
			renderEffect(affinity, true, data);
		}
	}

	public void renderEffect(Affinity affinity, boolean includeArm, Object... data){

		if (!setupIcons){
			setupAffinityIcons();
			setupIcons = true;
		}

		RenderBlocks renderer = (RenderBlocks)data[0];

		EntityLivingBase entity = (EntityLivingBase)data[1];
		GL11.glPushMatrix();
		GL11.glEnable(32826 /*GL_RESCALE_NORMAL_EXT*/);
		GL11.glEnable(3042);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		float scale = 3f;
		if (entity == mc.thePlayer && Minecraft.getMinecraft().gameSettings.thirdPersonView == 0){

			GL11.glPushMatrix();

			if (((EntityPlayer)entity).getItemInUseCount() > 0){
				GL11.glRotatef(120, 1, 0, 1);
				GL11.glRotatef(-10, 0, 1, 0);
				GL11.glTranslatef(2f, 0f, 0.8f);
			}else{
				GL11.glTranslatef(3f, -1.2f, -2.5f);
				GL11.glRotatef(120, 1, 0, 1);
				GL11.glRotatef(-20, 0, 1, 0);
			}

			GL11.glScalef(scale, scale, scale);
			RenderByAffinity(affinity);

			GL11.glPopMatrix();

			if (includeArm){
				GL11.glPushMatrix();

				if (((EntityPlayer)entity).getItemInUseCount() > 0){
					GL11.glRotatef(-120, 0, 1, 0);
					GL11.glTranslatef(-0.85f, 0.25f, -2.3f);
				}
				GL11.glScalef(scale, scale, scale);
				GL11.glTranslatef(0f, 0.6f, 1.1f);
				Minecraft.getMinecraft().renderEngine.bindTexture(mc.thePlayer.getLocationSkin());
				renderFirstPersonArm(mc.thePlayer);
				GL11.glPopMatrix();
			}

		}else{
			if (entity instanceof EntityPlayer && ((EntityPlayer)entity).isUsingItem()){
				GL11.glTranslatef(-0.1f, 0.3f, 0.8f);
			}else{
				GL11.glTranslatef(0.0f, 0.0f, 1.6f);
			}
			scale = 1.5f;
			GL11.glScalef(scale, scale, scale);
			GL11.glRotatef(45, 0, 1, 0);
			RenderByAffinity(affinity);
		}

		GL11.glDisable(32826 /*GL_RESCALE_NORMAL_EXT*/);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glDisable(3042);

		GL11.glPopMatrix();
	}

	private void renderSpellBook(ItemRenderType type, ItemStack bookStack, ItemStack scrollStack, Affinity affinity, Object... data){
		if (!setupIcons){ setupAffinityIcons(); setupIcons = true; }
		EntityLivingBase entity = data.length > 1 && data[1] instanceof EntityLivingBase ? (EntityLivingBase)data[1] : null;
		if (entity == null && type == ItemRenderType.EQUIPPED_FIRST_PERSON) entity = mc.thePlayer;
		EntityPlayer player = entity instanceof EntityPlayer ? (EntityPlayer)entity : null;
		float partial = CastingAnimationState.getPartialTicks();
		float open = CastingAnimationState.getOpenProgress(entity, bookStack, partial);
		float age = (entity != null ? entity.ticksExisted : 0) + partial;
		float pageLeft = MathHelper.clamp_float((age * 0.065f % 1.0f) * 1.6f - 0.3f, 0, 1);
		float pageRight = MathHelper.clamp_float(((age * 0.065f + 0.5f) % 1.0f) * 1.6f - 0.3f, 0, 1);
		float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;

		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		try {
			GL11.glEnable(GL12.GL_RESCALE_NORMAL);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glColor4f(1, 1, 1, 1);
			if (type == ItemRenderType.EQUIPPED_FIRST_PERSON && player == mc.thePlayer
					&& player.getItemInUseCount() > 0 && bookStack.getItemUseAction() == EnumAction.block){
				// Undo only the vanilla blocking pose, inside Forge's half-block offset.
				// Casting remains unchanged; the book supplies its own continuous pose.
				GL11.glTranslatef(0.5f, 0.5f, 0.5f);
				GL11.glRotatef(-60, 0, 1, 0);
				GL11.glRotatef(80, 1, 0, 0);
				GL11.glRotatef(-30, 0, 1, 0);
				GL11.glTranslatef(0.5f, -0.2f, 0);
				GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
			}
			GL11.glPushMatrix();
			if (type == ItemRenderType.EQUIPPED_FIRST_PERSON){
				GL11.glTranslatef(0.85f - 0.55f * open, 0.65f + 0.45f * open, -0.05f - 0.30f * open);
				GL11.glRotatef(35.0f - 13.0f * open, 1, 0, 0);
				GL11.glRotatef(-45.0f + 10.0f * open, 0, 1, 0);
				GL11.glRotatef(180.0f, 0, 0, 1);
				float scale = 1.15f + 0.20f * open;
				GL11.glScalef(scale, scale, scale);
			}else{
				// RenderPlayer supplies the BLOCK_3D pose because this renderer uses
				// that helper. Return to the hand before applying the book's pose.
				GL11.glTranslatef(0.5f, 0.5f, 0.5f);
				GL11.glScalef(-1.0f / 0.375f, -1.0f / 0.375f, 1.0f / 0.375f);
				GL11.glRotatef(-45, 0, 1, 0);
				GL11.glRotatef(-20, 1, 0, 0);
				GL11.glTranslatef(0, -0.1875f, 0.3125f);
				GL11.glTranslatef(0, 0, -0.12f - 0.08f * open);
				GL11.glRotatef(180, 0, 1, 0);
				float scale = 0.85f + 0.10f * open;
				GL11.glScalef(scale, scale, scale);
			}
			ResourceLocation tex = (bookStack != null && bookStack.getItem() == ItemsCommonProxy.arcaneSpellbook) ? arcaneBookTexture : bookTexture;
			mc.renderEngine.bindTexture(tex);
			GL11.glPushMatrix();
			// ModelBook opens towards +X. Turn that face towards the reader's +Z.
			GL11.glRotatef(-90.0f * open, 0, 1, 0);
			modelBook.render((Entity)null, age, pageLeft, pageRight, open, 0.0f, 0.0625f);
			GL11.glPopMatrix();
			if (scrollStack != null && open > 0.15f){
				GL11.glPushMatrix();
				float hover = (float)Math.sin(age * 0.2f) * 0.025f;
				GL11.glTranslatef(0.0f, hover, 0.18f);
				GL11.glRotatef(age * 2.5f, 0, 0, 1);
				float scale = 0.50f * open;
				GL11.glScalef(scale, scale, scale);
				GL11.glTranslatef(-0.5f, -0.5f, 0);
				GL11.glDisable(GL11.GL_CULL_FACE);
				GL11.glDisable(GL11.GL_LIGHTING);
				GL11.glDepthMask(false);
				OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
				GL11.glColor4f(1, 1, 1, open);
				RenderByAffinity(affinity);
				OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
				GL11.glDepthMask(true);
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
				GL11.glEnable(GL11.GL_LIGHTING);
				GL11.glPopMatrix();
			}
			GL11.glPopMatrix();
			if (type == ItemRenderType.EQUIPPED_FIRST_PERSON && player == mc.thePlayer){
				GL11.glPushMatrix();
				GL11.glTranslatef(0.55f - 0.10f * open, -0.40f + 0.075f * open, 0.52f);
				GL11.glRotatef(-30, 1, 0, 0);
				GL11.glScalef(1.7f, 1.7f, 1.7f);
				GL11.glTranslatef(0.3125f, -0.125f, 0);
				mc.renderEngine.bindTexture(mc.thePlayer.getLocationSkin());
				modelBipedMain.bipedRightArm.rotateAngleX = 0;
				modelBipedMain.bipedRightArm.rotateAngleY = 0;
				modelBipedMain.bipedRightArm.rotateAngleZ = 0;
				modelBipedMain.bipedRightArm.render(0.0625f);
				GL11.glPopMatrix();
			}
		} finally {
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
			GL11.glPopAttrib();
			GL11.glPopMatrix();
		}
	}

	private void setupAffinityIcons(){
		icons.put(Affinity.AIR, AMParticleIcons.instance.getIconByName("air_hand"));
		icons.put(Affinity.ARCANE, AMParticleIcons.instance.getIconByName("arcane_hand"));
		icons.put(Affinity.EARTH, AMParticleIcons.instance.getIconByName("earth_hand"));
		icons.put(Affinity.ENDER, AMParticleIcons.instance.getIconByName("ender_hand"));
		icons.put(Affinity.FIRE, AMParticleIcons.instance.getIconByName("fire_hand"));
		icons.put(Affinity.ICE, AMParticleIcons.instance.getIconByName("ice_hand"));
		icons.put(Affinity.LIFE, AMParticleIcons.instance.getIconByName("life_hand"));
		icons.put(Affinity.LIGHTNING, AMParticleIcons.instance.getIconByName("lightning_hand"));
		icons.put(Affinity.NATURE, AMParticleIcons.instance.getIconByName("nature_hand"));
		icons.put(Affinity.NONE, AMParticleIcons.instance.getIconByName("none_hand"));
		icons.put(Affinity.WATER, AMParticleIcons.instance.getIconByName("water_hand"));
	}

	public void RenderByAffinity(Affinity affinity){

		Minecraft.getMinecraft().renderEngine.bindTexture(rLoc);

		IIcon icon = icons.get(affinity);
		if (icon == null) return;

		float TLX = icon.getMinU();
		float BRX = icon.getMaxU();
		float TLY = icon.getMinV();
		float BRY = icon.getMaxV();

		doRender(TLX, TLY, BRX, BRY);
	}


	private void doRender(float TLX, float TLY, float BRX, float BRY){
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		t.setNormal(0.0F, 0.0F, 1.0F);
		t.addVertexWithUV(0.0D, 0.0D, 0.0D, TLX, BRY);
		t.addVertexWithUV(1.0D, 0.0D, 0.0D, BRX, BRY);
		t.addVertexWithUV(1.0D, 1.0D, 0.0D, BRX, TLY);
		t.addVertexWithUV(0.0D, 1.0D, 0.0D, TLX, TLY);
		t.draw();
	}

	private void renderFirstPersonArm(EntityClientPlayerMP player){

		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		float par1 = 0.5f;

		float f1 = 1.0F;
		EntityClientPlayerMP entityclientplayermp = this.mc.thePlayer;
		float f2 = entityclientplayermp.prevRotationPitch + (entityclientplayermp.rotationPitch - entityclientplayermp.prevRotationPitch) * par1;
		GL11.glPushMatrix();
		GL11.glRotatef(f2, 1.0F, 0.0F, 0.0F);
		GL11.glRotatef(entityclientplayermp.prevRotationYaw + (entityclientplayermp.rotationYaw - entityclientplayermp.prevRotationYaw) * par1, 0.0F, 1.0F, 0.0F);
		RenderHelper.enableStandardItemLighting();
		GL11.glPopMatrix();
		EntityPlayerSP entityplayersp = entityclientplayermp;
		float f3 = entityplayersp.prevRenderArmPitch + (entityplayersp.renderArmPitch - entityplayersp.prevRenderArmPitch) * par1;
		float f4 = entityplayersp.prevRenderArmYaw + (entityplayersp.renderArmYaw - entityplayersp.prevRenderArmYaw) * par1;
		GL11.glRotatef((entityclientplayermp.rotationPitch - f3) * 0.1F, 1.0F, 0.0F, 0.0F);
		GL11.glRotatef((entityclientplayermp.rotationYaw - f4) * 0.1F, 0.0F, 1.0F, 0.0F);
		float f5 = this.mc.theWorld.getLightBrightness(MathHelper.floor_double(entityclientplayermp.posX), MathHelper.floor_double(entityclientplayermp.posY), MathHelper.floor_double(entityclientplayermp.posZ));
		f5 = 1.0F;
		int i = this.mc.theWorld.getLightBrightnessForSkyBlocks(MathHelper.floor_double(entityclientplayermp.posX), MathHelper.floor_double(entityclientplayermp.posY), MathHelper.floor_double(entityclientplayermp.posZ), 0);
		int j = i % 65536;
		int k = i / 65536;
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, j / 1.0F, k / 1.0F);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		float f6;
		float f7;
		float f8;

		GL11.glPushMatrix();
		float f12 = 0.8F;
		f7 = entityclientplayermp.getSwingProgress(par1);
		f8 = MathHelper.sin(f7 * (float)Math.PI);
		f6 = MathHelper.sin(MathHelper.sqrt_float(f7) * (float)Math.PI);
		GL11.glRotatef(-f6 * 20.0F, 0.0F, 1.0F, 0.0F);
		GL11.glRotatef(-f8 * 20.0F, 0.0F, 0.0F, 1.0F);
		GL11.glRotatef(-f8 * 80.0F, 1.0F, 0.0F, 0.0F);
		GL11.glScalef(f12, f12, f12);
		this.modelBipedMain.onGround = 0.0F;
		this.modelBipedMain.setRotationAngles(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0625F, entityclientplayermp);
		this.modelBipedMain.bipedRightArm.render(0.0625F);
		GL11.glPopMatrix();
	}

}
