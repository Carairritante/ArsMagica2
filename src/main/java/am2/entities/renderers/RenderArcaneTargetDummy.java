package am2.entities.renderers;

import am2.client.vfx.BlockbenchRigModel;
import am2.entities.EntityArcaneTargetDummy;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Renders {@link EntityArcaneTargetDummy} using its Blockbench pixel-art rig, hit recoil animation,
 * overhead live DPS readout, and lightweight client-side floating damage popups.
 */
@SideOnly(Side.CLIENT)
public class RenderArcaneTargetDummy extends Render {

	private static final ResourceLocation MODEL_LOC = new ResourceLocation("arsmagica2", "models/entities/arcane_target_dummy.bbmodel");
	private static final ResourceLocation DUMMY_TEX_FALLBACK = new ResourceLocation("arsmagica2", "textures/blocks/arcane_striker_icon.png");

	private static BlockbenchRigModel model;
	private static boolean loadAttempted = false;

	private final Map<String, BlockbenchRigModel.BoneOverride> overrides = new HashMap<String, BlockbenchRigModel.BoneOverride>();
	private final BlockbenchRigModel.BoneOverride upperBodyOv = new BlockbenchRigModel.BoneOverride();
	private final BlockbenchRigModel.BoneOverride headOv = new BlockbenchRigModel.BoneOverride();
	private final BlockbenchRigModel.BoneOverride leftArmOv = new BlockbenchRigModel.BoneOverride();
	private final BlockbenchRigModel.BoneOverride rightArmOv = new BlockbenchRigModel.BoneOverride();
	private final BlockbenchRigModel.BoneOverride shardsOv = new BlockbenchRigModel.BoneOverride();

	private static final List<DamagePopup> POPUPS = new LinkedList<DamagePopup>();
	private static final int MAX_POPUPS = 48;

	private static final class DamagePopup {
		double x, y, z;
		double vx, vy, vz;
		float damage;
		int color;
		long createdMs;
	}

	public RenderArcaneTargetDummy() {
		this.shadowSize = 0.45F;
		overrides.put("upper_body", upperBodyOv);
		overrides.put("head_group", headOv);
		overrides.put("arm_left", leftArmOv);
		overrides.put("arm_right", rightArmOv);
		overrides.put("crystal_shards", shardsOv);
	}

	private static BlockbenchRigModel getModel() {
		if (!loadAttempted) {
			loadAttempted = true;
			model = BlockbenchRigModel.load(MODEL_LOC, "am2_arcane_dummy");
		}
		return model;
	}

	public static void spawnDamagePopup(double x, double y, double z, float damage, String damageType) {
		long now = System.currentTimeMillis();
		synchronized (POPUPS) {
			while (POPUPS.size() >= MAX_POPUPS) {
				POPUPS.remove(0);
			}
			DamagePopup p = new DamagePopup();
			double jitter = (Math.random() - 0.5) * 0.45;
			p.x = x + jitter;
			p.y = y + Math.random() * 0.25;
			p.z = z + (Math.random() - 0.5) * 0.45;
			p.vx = jitter * 0.15;
			p.vy = 0.85;
			p.vz = (Math.random() - 0.5) * 0.15;
			p.damage = damage;
			p.color = colorForDamageType(damageType, damage);
			p.createdMs = now;
			POPUPS.add(p);
		}
	}

	private static int colorForDamageType(String type, float dmg) {
		if (dmg >= 100.0f) {
			return 0xFFDD44;
		}
		if (type == null) {
			return 0xFFFFFF;
		}
		String t = type.toLowerCase();
		if (t.contains("magic") || t.contains("arcane")) return 0x77EEFF;
		if (t.contains("fire") || t.contains("burn") || t.contains("lava")) return 0xFF7B33;
		if (t.contains("lightn")) return 0xFFFF66;
		if (t.contains("frost") || t.contains("ice")) return 0x99F3FF;
		if (t.contains("wither") || t.contains("dark")) return 0xC67CFF;
		return 0xFFFFFF;
	}

	@Override
	public void doRender(Entity entity, double x, double y, double z, float entityYaw, float partialTicks) {
		if (!(entity instanceof EntityArcaneTargetDummy)) {
			return;
		}
		EntityArcaneTargetDummy dummy = (EntityArcaneTargetDummy) entity;
		BlockbenchRigModel rig = getModel();
		if (rig == null) {
			return;
		}

		float time = dummy.ticksExisted + partialTicks;
		float recoil = Math.max(0.0f, Math.min(1.0f, dummy.clientRecoil));
		float hurtWave = (float) Math.sin(recoil * Math.PI);

		upperBodyOv.reset();
		headOv.reset();
		leftArmOv.reset();
		rightArmOv.reset();
		shardsOv.reset();

		// Idle breathing / floating shards + crisp backward recoil when struck
		upperBodyOv.drx = -hurtWave * 22.0f + (float) Math.sin(time * 0.08f) * 1.2f;
		upperBodyOv.dry = hurtWave * 8.0f;
		headOv.drx = -hurtWave * 14.0f;
		leftArmOv.drx = hurtWave * 18.0f + (float) Math.sin(time * 0.1f) * 2.5f;
		rightArmOv.drx = hurtWave * 18.0f - (float) Math.sin(time * 0.1f) * 2.5f;
		shardsOv.dy = (float) Math.sin(time * 0.12f) * 0.75f;
		shardsOv.dry = (time * 4.5f) % 360.0f;

		float rTint = 1.0f;
		float gTint = 1.0f - hurtWave * 0.45f;
		float bTint = 1.0f - hurtWave * 0.45f;

		GL11.glPushMatrix();
		GL11.glTranslated(x, y, z);
		GL11.glRotatef(180.0f - entityYaw, 0.0f, 1.0f, 0.0f);
		rig.render(null, 0.0f, overrides, rTint, gTint, bTint, 1.0f);
		GL11.glPopMatrix();

		renderOverheadStats(dummy, x, y + 2.32, z);
		renderPopupsNear(dummy, x, y, z);
	}

	private void renderOverheadStats(EntityArcaneTargetDummy dummy, double rx, double ry, double rz) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.fontRenderer == null) {
			return;
		}
		FontRenderer font = mc.fontRenderer;
		float secLeft = dummy.getRemainingTicks() / 20.0f;
		String line1 = String.format("\u00a7bDummy M\u00e1gico \u00a7f| \u00a7e%.1fs", secLeft);
		String line2 = String.format("\u00a7fDano: \u00a7e%.1f \u00a77| \u00a7aDPS: %.1f", dummy.getTotalDamage(), dummy.getDps());

		float scale = 0.017f;
		GL11.glPushMatrix();
		GL11.glTranslated(rx, ry, rz);
		GL11.glRotatef(-this.renderManager.playerViewY, 0.0f, 1.0f, 0.0f);
		GL11.glRotatef(this.renderManager.playerViewX, 1.0f, 0.0f, 0.0f);
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
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		t.setColorRGBA_F(0.05f, 0.10f, 0.22f, 0.72f);
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

	private void renderPopupsNear(EntityArcaneTargetDummy dummy, double rx, double ry, double rz) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.fontRenderer == null) {
			return;
		}
		long now = System.currentTimeMillis();
		synchronized (POPUPS) {
			if (POPUPS.isEmpty()) {
				return;
			}
			FontRenderer font = mc.fontRenderer;
			Iterator<DamagePopup> it = POPUPS.iterator();
			while (it.hasNext()) {
				DamagePopup p = it.next();
				float ageSec = (now - p.createdMs) / 1000.0f;
				if (ageSec > 1.15f) {
					it.remove();
					continue;
				}
				double dx = p.x - dummy.posX;
				double dz = p.z - dummy.posZ;
				if (dx * dx + dz * dz > 16.0) {
					continue;
				}
				double px = rx + dx + p.vx * ageSec;
				double py = ry + (p.y - dummy.posY) + p.vy * ageSec - 0.35 * ageSec * ageSec;
				double pz = rz + dz + p.vz * ageSec;

				String text = String.format("-%.1f", p.damage);
				float scale = 0.022f + Math.min(0.01f, p.damage * 0.00005f);
				GL11.glPushMatrix();
				GL11.glTranslated(px, py, pz);
				GL11.glRotatef(-this.renderManager.playerViewY, 0.0f, 1.0f, 0.0f);
				GL11.glRotatef(this.renderManager.playerViewX, 1.0f, 0.0f, 0.0f);
				GL11.glScalef(-scale, -scale, scale);

				GL11.glDisable(GL11.GL_LIGHTING);
				GL11.glDepthMask(false);
				GL11.glDisable(GL11.GL_DEPTH_TEST);
				int w = font.getStringWidth(text);
				font.drawStringWithShadow(text, -w / 2, 0, p.color);
				GL11.glEnable(GL11.GL_DEPTH_TEST);
				GL11.glDepthMask(true);
				GL11.glEnable(GL11.GL_LIGHTING);
				GL11.glPopMatrix();
			}
		}
	}

	@Override
	protected ResourceLocation getEntityTexture(Entity entity) {
		return DUMMY_TEX_FALLBACK;
	}
}
