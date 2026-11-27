package am2.items.renderers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Arcane Guardian Spellbook: the remastered 3D book ({@link ArcaneSpellbook3D}) everywhere.
 * In the hand it always goes through {@link SpellScrollRenderer}, which opens it while
 * casting, so there is no jump between closed and open. Classic_Models keeps the original.
 */
@SideOnly(Side.CLIENT)
public final class ArcaneSpellbookRenderer implements IItemRenderer {
	@Override
	public boolean handleRenderType(ItemStack item, ItemRenderType type) {
		return CustomItemRenderer.instance.handleRenderType(item, type);
	}

	@Override
	public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
		if (!ArcaneSpellbook3D.active()) return CustomItemRenderer.instance.shouldUseRenderHelper(type, item, helper);
		if (type == ItemRenderType.ENTITY)
			return helper == ItemRendererHelper.ENTITY_ROTATION || helper == ItemRendererHelper.ENTITY_BOBBING;
		if (type == ItemRenderType.INVENTORY) return helper == ItemRendererHelper.INVENTORY_BLOCK;
		return SpellScrollRenderer.instance.shouldUseRenderHelper(type, item, helper);
	}

	@Override
	public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
		if (!ArcaneSpellbook3D.active()) {
			CustomItemRenderer.instance.renderItem(type, item, data);
			return;
		}
		if (type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
			SpellScrollRenderer.instance.renderItem(type, item, data);
			return;
		}
		if (type == ItemRenderType.ENTITY) {
			EntityLivingBase holder = data.length > 1 && data[1] instanceof EntityLivingBase ? (EntityLivingBase) data[1] : null;
			if (holder != null && holder.isPotionActive(Potion.invisibility.id)) return;
		}
		float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		try {
			GL11.glEnable(GL12.GL_RESCALE_NORMAL);
			GL11.glColor4f(1, 1, 1, 1);
			if (type == ItemRenderType.INVENTORY) ArcaneSpellbook3D.renderInventory();
			else ArcaneSpellbook3D.renderEntity((Minecraft.getSystemTime() % 3600000L) / 1000f);
		} finally {
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
			GL11.glPopAttrib();
			GL11.glPopMatrix();
		}
	}
}
