package am2.blocks.renderers;

import am2.blocks.tileentities.TileEntityArcaneStriker;
import am2.client.vfx.BlockbenchRigModel;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders the Blockbench High Striker machine model with animated meter puck, bell bounce,
 * and a crisp overhead scoreboard readout showing live/last/record metrics.
 */
@SideOnly(Side.CLIENT)
public class ArcaneStrikerRenderer extends TileEntitySpecialRenderer {

	private static final ResourceLocation MODEL_LOC = new ResourceLocation("arsmagica2", "models/blocks/arcane_striker_machine.bbmodel");
	private static BlockbenchRigModel model;
	private static boolean loadAttempted = false;

	private final Map<String, BlockbenchRigModel.BoneOverride> overrides = new HashMap<String, BlockbenchRigModel.BoneOverride>();
	private final BlockbenchRigModel.BoneOverride sliderOverride = new BlockbenchRigModel.BoneOverride();
	private final BlockbenchRigModel.BoneOverride bellOverride = new BlockbenchRigModel.BoneOverride();
	private final BlockbenchRigModel.BoneOverride shardsOverride = new BlockbenchRigModel.BoneOverride();

	public ArcaneStrikerRenderer() {
		overrides.put("meter_slider", sliderOverride);
		overrides.put("top_bell_assembly", bellOverride);
		overrides.put("crystal_shards", shardsOverride);
	}

	private static BlockbenchRigModel getModel() {
		if (!loadAttempted) {
			loadAttempted = true;
			model = BlockbenchRigModel.load(MODEL_LOC, "am2_arcane_striker", "tube_glass");
		}
		return model;
	}

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partialTicks) {
		if (!(tile instanceof TileEntityArcaneStriker)) {
			return;
		}
		TileEntityArcaneStriker striker = (TileEntityArcaneStriker) tile;
		BlockbenchRigModel rig = getModel();
		if (rig == null) {
			return;
		}

		boolean isInventory = striker.getWorldObj() == null;
		int meta = isInventory ? 0 : (striker.getBlockMetadata() & 3);
		float yawDeg;
		switch (meta) {
			case 0: yawDeg = 180.0f; break;
			case 1: yawDeg = 90.0f; break;
			case 2: yawDeg = 0.0f; break;
			case 3: yawDeg = 270.0f; break;
			default: yawDeg = 0.0f; break;
		}

		sliderOverride.reset();
		bellOverride.reset();
		shardsOverride.reset();

		if (isInventory) {
			sliderOverride.dy = 5.2f;
		} else {
			sliderOverride.dy = Math.max(0.0f, Math.min(1.0f, striker.clientSliderProgress)) * 7.6f;
			float bounce = striker.clientBellBounce;
			if (bounce > 0.01f) {
				float wave = (float) Math.sin(bounce * 18.0f) * bounce;
				bellOverride.dy = Math.abs(wave) * 0.9f;
				bellOverride.drz = wave * 10.0f;
			}
			float time = (Minecraft.getMinecraft().theWorld != null ? Minecraft.getMinecraft().theWorld.getTotalWorldTime() : 0) + partialTicks;
			shardsOverride.dy = (float) Math.sin(time * 0.09f) * 0.45f;
		}

		GL11.glPushMatrix();
		GL11.glTranslated(x + 0.5, y, z + 0.5);
		if (isInventory) {
			GL11.glTranslatef(0.0f, -0.45f, 0.0f);
			GL11.glScalef(0.95f, 0.95f, 0.95f);
			GL11.glRotatef(25.0f, 0.0f, 1.0f, 0.0f);
		} else {
			GL11.glRotatef(yawDeg, 0.0f, 1.0f, 0.0f);
		}

		rig.render(null, 0.0f, overrides, 1.0f, 1.0f, 1.0f, 1.0f);
		GL11.glPopMatrix();

		if (!isInventory && striker.getWorldObj() != null) {
			double boardY = striker.isTestActive() ? (y + 3.68) : (y + 1.42);
			renderOverheadScoreboard(striker, x + 0.5, boardY, z + 0.5);
		}
	}

	private void renderOverheadScoreboard(TileEntityArcaneStriker striker, double rx, double ry, double rz) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.thePlayer == null || mc.fontRenderer == null) {
			return;
		}
		double distSq = striker.getDistanceFrom(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
		if (distSq > 225.0) { // > 15 blocks
			return;
		}

		String line1;
		String line2;
		if (striker.isTestActive()) {
			float secLeft = striker.getRemainingTicks() / 20.0f;
			line1 = String.format("\u00a7b[%.1fs] \u00a7fDano: \u00a7e%.1f", secLeft, striker.getCurrentTotalDamage());
			line2 = String.format("\u00a7aDPS: %.1f \u00a77| Max: %.1f", striker.getCurrentDps(), striker.getCurrentPeakHit());
		} else if (striker.getLastTotalDamage() > 0.0) {
			line1 = String.format("\u00a7fDano: \u00a7e%.1f \u00a7a(DPS %.1f)", striker.getLastTotalDamage(), striker.getLastDps());
			line2 = String.format("\u00a76Recorde: %.1f \u00a77(%ds)", striker.getRecordTotalDamage(), striker.getConfiguredDurationSec());
		} else {
			line1 = "\u00a7bArcane Striker \u00a77(" + striker.getConfiguredDurationSec() + "s)";
			line2 = striker.getRecordTotalDamage() > 0.0
					? String.format("\u00a76Recorde: %.1f \u00a77| Clique p/ iniciar", striker.getRecordTotalDamage())
					: "\u00a77Clique p/ invocar o Dummy";
		}

		FontRenderer font = mc.fontRenderer;
		float scale = 0.018f;
		GL11.glPushMatrix();
		GL11.glTranslated(rx, ry, rz);
		GL11.glRotatef(-RenderManager.instance.playerViewY, 0.0f, 1.0f, 0.0f);
		GL11.glRotatef(RenderManager.instance.playerViewX, 1.0f, 0.0f, 0.0f);
		GL11.glScalef(-scale, -scale, scale);

		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDepthMask(false);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		RenderHelper.disableStandardItemLighting();

		int w1 = font.getStringWidth(line1);
		int w2 = font.getStringWidth(line2);
		int halfW = Math.max(w1, w2) / 2 + 5;

		GL11.glDisable(GL11.GL_TEXTURE_2D);
		net.minecraft.client.renderer.Tessellator t = net.minecraft.client.renderer.Tessellator.instance;
		t.startDrawingQuads();
		t.setColorRGBA_F(0.04f, 0.09f, 0.19f, 0.72f);
		t.addVertex(-halfW, -11, 0);
		t.addVertex(-halfW, 11, 0);
		t.addVertex(halfW, 11, 0);
		t.addVertex(halfW, -11, 0);
		t.draw();
		GL11.glEnable(GL11.GL_TEXTURE_2D);

		font.drawStringWithShadow(line1, -w1 / 2, -9, 0xFFFFFF);
		font.drawStringWithShadow(line2, -w2 / 2, 1, 0xFFFFFF);

		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glDepthMask(true);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glPopMatrix();
	}
}
