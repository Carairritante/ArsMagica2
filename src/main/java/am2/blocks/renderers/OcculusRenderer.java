package am2.blocks.renderers;

import am2.AMCore;
import am2.blocks.tileentities.TileEntityOcculus;
import am2.texture.ResourceManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Fills an {@link OcculusView} (facing, interpolated eye direction, how awake it is) and
 * hands it to {@link OcculusStyle}. The eye itself is tracked in {@link TileEntityOcculus}.
 */
public class OcculusRenderer extends TileEntitySpecialRenderer{

	private final ResourceLocation rLoc;
	private final OcculusView view = new OcculusView();

	public OcculusRenderer(){
		rLoc = new ResourceLocation("arsmagica2", ResourceManager.getCustomBlockTexturePath("Occulus.png"));
	}

	@Override
	public void renderTileEntityAt(TileEntity var1, double var2, double var4, double var6, float var8){
		renderTileEntityOcculusAt((TileEntityOcculus)var1, var2, var4, var6, var8);
	}

	public void renderTileEntityOcculusAt(TileEntityOcculus podium, double d, double d1, double d2, float f1){
		boolean inWorld = podium.getWorldObj() != null;
		int meta = inWorld ? podium.getBlockMetadata() : 2;
		view.facing = meta * 90;
		view.inventory = !inWorld;
		view.eyeYaw = lerp(podium.prevEyeYaw, podium.eyeYaw, f1);
		view.eyePitch = lerp(podium.prevEyePitch, podium.eyePitch, f1);
		view.open = lerp(podium.prevOpen, podium.open, f1);
		view.awake = lerp(podium.prevAwake, podium.awake, f1);
		view.time = inWorld ? (podium.getWorldObj().getTotalWorldTime() + f1) / 20F : 0;

		bindTexture(rLoc);
		GL11.glPushMatrix();
		GL11.glTranslatef((float)d + 0.5F, (float)d1, (float)d2 + 0.5F);
		if (AMCore.config.classicModels())
			OcculusStyle.drawClassic(view);
		else
			OcculusStyle.drawRemaster(view);
		GL11.glPopMatrix();
	}

	private static float lerp(float from, float to, float partial){
		return from + (to - from) * partial;
	}
}
