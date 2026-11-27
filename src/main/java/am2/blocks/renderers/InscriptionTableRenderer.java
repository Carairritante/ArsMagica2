package am2.blocks.renderers;

import am2.blocks.tileentities.TileEntityInscriptionTable;
import am2.models.InscriptionTableRemasterMesh;
import am2.models.ModelInscriptionTableLeft;
import am2.models.ModelInscriptionTableRight;
import am2.texture.ResourceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class InscriptionTableRenderer extends TileEntitySpecialRenderer{

	public static final ResourceLocation inscriptionTableTexture = new ResourceLocation("arsmagica2", ResourceManager.getCustomBlockTexturePath("InscriptionTable.png"));
	/** Original texture, for Classic_Block_Models (the remaster replaced InscriptionTable.png). */
	private static final ResourceLocation classicTexture = new ResourceLocation("arsmagica2", ResourceManager.getCustomBlockTexturePath("InscriptionTable_classic.png"));

	public InscriptionTableRenderer(){
		modelLeft = new ModelInscriptionTableLeft();
		modelRight = new ModelInscriptionTableRight();
	}

	public void renderAModelAt(TileEntityInscriptionTable tile, double d, double d1, double d2, float f){
		int i = 0;

		if (tile.getWorldObj() != null){
			i = tile.getBlockMetadata();
		}
		int j = (i & ~0x8) * 90;
		int upgradeState = tile.getUpgradeState();

		if (am2.AMCore.config.classicBlockModels()){
			renderClassic(tile, d, d1, d2, i, j);
			return;
		}

		Minecraft.getMinecraft().renderEngine.bindTexture(inscriptionTableTexture);
		GL11.glPushMatrix();
		GL11.glEnable(GL12.GL_RESCALE_NORMAL);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glTranslatef((float)d + 0.5F, (float)d1, (float)d2 + 0.5F);
		GL11.glRotatef(j, 0.0F, 1.0F, 0.0F);

		if (d == 0 && d1 == 0 && d2 == 0){
			GL11.glScalef(0.72F, 0.72F, 0.72F);
			GL11.glTranslatef(-0.5F, 0.12F, 0.0F);
			InscriptionTableRemasterMesh.renderLeft(upgradeState);
			GL11.glTranslatef(1.0F, 0.0F, 0.0F);
			InscriptionTableRemasterMesh.renderRight(upgradeState);
		}else{
			if ((i & 0x8) == 0x8)
				InscriptionTableRemasterMesh.renderLeft(upgradeState);
			else
				InscriptionTableRemasterMesh.renderRight(upgradeState);
		}

		GL11.glDisable(GL12.GL_RESCALE_NORMAL);
		GL11.glPopMatrix();
	}

	/** The original AM2 table, as it was before the remaster. */
	private void renderClassic(TileEntityInscriptionTable tile, double d, double d1, double d2, int i, int j){
		Minecraft.getMinecraft().renderEngine.bindTexture(classicTexture);
		GL11.glPushMatrix();
		GL11.glTranslatef((float)d + 0.5F, (float)d1 + 1.5F, (float)d2 + 0.5F);
		GL11.glRotatef(j, 0.0F, 1.0F, 0.0F);
		GL11.glScalef(1.0F, -1F, -1F);
		if (d == 0 && d1 == 0 && d2 == 0){
			GL11.glScalef(0.8f, 0.8f, 0.8f);
			GL11.glTranslatef(-0.4f, 0.3f, 0.0f);
			modelLeft.renderModel(0.0625f, tile.getUpgradeState());
			GL11.glTranslatef(1f, 0.0f, 0.0f);
			modelRight.renderMode(0.0625f, tile.getUpgradeState());
		}else{
			if ((i & 0x8) == 0x8)
				modelLeft.renderModel(0.0625F, tile.getUpgradeState());
			else
				modelRight.renderMode(0.0625F, tile.getUpgradeState());
		}
		GL11.glPopMatrix();
	}

	public void renderTileEntityAt(TileEntity tileentity, double d, double d1, double d2, float f){
		renderAModelAt((TileEntityInscriptionTable)tileentity, d, d1, d2, f); //where to render
	}

	private ModelInscriptionTableLeft modelLeft;
	private ModelInscriptionTableRight modelRight;
}
