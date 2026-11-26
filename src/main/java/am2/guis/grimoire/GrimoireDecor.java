package am2.guis.grimoire;

import am2.api.spell.component.interfaces.ISkillTreeEntry;
import am2.api.spell.component.interfaces.ISpellComponent;
import am2.api.spell.enums.Affinity;
import am2.guis.controls.ArcaneChalkStyle;
import am2.spell.SkillManager;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Multi-layered arcane mandalas, glowing Wizard Chalk runes, card seals,
 * ornate dividers, and colour rules shared by the grimoire screens.
 */
@SideOnly(Side.CLIENT)
final class GrimoireDecor {
	private static final ResourceLocation EXTRAS = new ResourceLocation("arsmagica2", "textures/guis/ArcaneCompendiumGuiExtras.png");
	private static final int GOLD = 0xFFD98A;

	private GrimoireDecor() {}

	/** One Wizard Chalk rune centred on the given point (supports crisp additive arcane glow). */
	static void rune(int variant, float cx, float cy, int size, int color, float alpha) {
		rune(variant, cx, cy, size, color, alpha, false);
	}

	static void rune(int variant, float cx, float cy, int size, int color, float alpha, boolean glow) {
		if (alpha <= 0.01f) return;
		if (GrimoireDraw.lite) glow = false;
		if (glow) {
			GrimoireDraw.glow(cx, cy, size * 0.95f, color, alpha * 0.30f);
		}
		GrimoireDraw.flush(); // the glyph is textured
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glPushMatrix();
		GL11.glTranslatef(cx - size / 2f, cy - size / 2f, 0);
		ArcaneChalkStyle.glyph(Minecraft.getMinecraft(), variant, 0, 0, size, color, alpha);
		if (glow) {
			// Additive second pass makes chalk glyphs truly luminescent on dark obsidian panels
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
			ArcaneChalkStyle.glyph(Minecraft.getMinecraft(), variant, 0, 0, size, color, alpha * 0.55f);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		}
		GL11.glPopMatrix();
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
	}

	/** Runes orbiting a centre; they stay upright while travelling around it. */
	static void runeRing(float cx, float cy, float radius, int count, int size, int color, float alpha, float rotation) {
		runeRing(cx, cy, radius, count, size, color, alpha, rotation, false);
	}

	static void runeRing(float cx, float cy, float radius, int count, int size, int color, float alpha, float rotation, boolean glow) {
		if (alpha <= 0.01f) return;
		for (int i = 0; i < count; i++) {
			double angle = rotation + i * Math.PI * 2.0 / count;
			rune(i * 5 + 1, cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius, size, color, alpha, glow);
		}
	}

	/** A line of runes with a luminous travelling pulse wave. */
	static void runeStrip(float x, float y, int length, int color, float alpha, long now, boolean animated) {
		int count = Math.max(0, length / 13);
		if (count == 0) return;
		float step = length / (float) count;
		float wavePos = animated ? ((now % 2600L) / 2600f) * (count + 4) - 2f : -10f;
		// Faint guiding hairline underneath the runes
		GrimoireDraw.hGrad(x, y, length * 0.5f, 1, color, 0f, color, alpha * 0.45f, true);
		GrimoireDraw.hGrad(x + length * 0.5f, y, length * 0.5f, 1, color, alpha * 0.45f, color, 0f, true);
		for (int i = 0; i < count; i++) {
			float boost = animated ? Math.max(0f, 1f - Math.abs(i - wavePos) / 2.6f) : 0f;
			float rx = x + step * i + step * 0.5f;
			rune(i * 3 + 1, rx, y, 8, boost > 0.35f ? GrimoireDraw.mix(color, GOLD, boost * 0.55f) : color,
					Math.min(1f, alpha + boost * 0.65f), boost > 0.30f);
		}
	}

	/** Luminous arcane section divider with fading beams, golden star and glowing rune. */
	static void ornateDivider(float x, float y, float width, int color, float alpha, int runeVariant, float timeSec, boolean animated) {
		if (width < 20 || alpha <= 0.01f) return;
		float cx = x + width * 0.5f;
		float half = width * 0.5f - 10f;
		float pulse = animated ? 0.82f + 0.18f * (float) Math.sin(timeSec * 3.0f) : 1f;
		if (half > 4f) {
			GrimoireDraw.hGrad(x, y, half, 1, color, 0f, color, alpha * 0.72f, true);
			GrimoireDraw.hGrad(cx + 10f, y, half, 1, color, alpha * 0.72f, color, 0f, true);
		}
		GrimoireDraw.glow(cx, y, 10f, color, alpha * 0.25f * pulse);
		GrimoireDraw.star(cx - 8f, y, 2.2f, GOLD, alpha * 0.65f, 0);
		GrimoireDraw.star(cx + 8f, y, 2.2f, GOLD, alpha * 0.65f, 0);
		rune(runeVariant, cx, y, 9, GrimoireDraw.mix(color, GOLD, 0.3f), Math.min(1f, alpha * 0.95f * pulse), true);
	}

	/**
	 * Multi-layered counter-rotating arcane mandala:
	 * combines radial light halo, the classic AM2 summoning circle texture, counter-rotating dashed rings,
	 * sacred hexagram geometry, orbiting Wizard Chalk runes, and cardinal crystal stars.
	 */
	static void arcaneMandala(float cx, float cy, float scale, int color, float alpha, float timeSec, boolean animated, boolean rich) {
		if (alpha <= 0.01f || scale <= 0.05f) return;
		float t = animated ? timeSec : 0f;
		float pulse = animated ? 0.88f + 0.12f * (float) Math.sin(t * 2.1f) : 1f;
		float outerR = 72f * scale;
		if (GrimoireDraw.lite) rich = false;
		GrimoireDraw.batch();
		try {
			mandalaShapes(cx, cy, scale, color, alpha, t, pulse, outerR, rich);
		} finally {
			GrimoireDraw.endBatch();
		}
		if (rich) {
			// 5. Orbiting glowing Wizard Chalk runes between inner and outer rings (textured, drawn last)
			runeRing(cx, cy, outerR * 0.73f, 8, Math.max(6, Math.round(9 * scale)), color, Math.min(1f, alpha * 0.82f), -t * 0.22f, true);
		}
	}

	private static void mandalaShapes(float cx, float cy, float scale, int color, float alpha, float t, float pulse, float outerR, boolean rich) {

		// 1. Soft atmospheric radial aura behind the circle
		GrimoireDraw.glow(cx, cy, outerR * 1.18f, color, Math.min(0.45f, alpha * 0.34f * pulse));

		// 2. Classic AM2 summoning circle texture rotating smoothly
		GrimoireDraw.flush();
		arcaneCircle(cx, cy, scale, color, alpha * 0.82f, t * 14f);

		// 3. Crisp outer solid + counter-rotating dashed concentric rings + anime astrolabe tick graduation
		GrimoireDraw.ring(cx, cy, outerR * 0.97f, 1.1f, color, alpha * 0.58f, 0, 1, 1, true);
		GrimoireDraw.tickRing(cx, cy, outerR * 0.95f, 2.8f * scale, 36, color, alpha * 0.62f, t * 0.14f);
		GrimoireDraw.ring(cx, cy, outerR * 0.85f, 1.2f, GrimoireDraw.mix(color, GOLD, 0.45f), alpha * 0.58f, -t * 0.32f, 12, 0.52f, true);
		GrimoireDraw.ring(cx, cy, outerR * 0.58f, 0.9f, color, alpha * 0.48f, t * 0.48f, 8, 0.62f, true);

		if (rich) {
			// 4. Sacred geometry counter-rotating double triangle (hexagram) + antique-gold inner octagon
			float hexR = outerR * 0.72f;
			GrimoireDraw.polygon(cx, cy, hexR, 3, 0.95f, color, alpha * 0.46f * pulse, t * 0.24f, true);
			GrimoireDraw.polygon(cx, cy, hexR, 3, 0.95f, GrimoireDraw.mix(color, GOLD, 0.48f), alpha * 0.42f * pulse, t * 0.24f + (float) Math.PI, true);
			GrimoireDraw.polygon(cx, cy, outerR * 0.46f, 8, 0.75f, GOLD, alpha * 0.34f * pulse, -t * 0.35f, true);

			// 6. Cardinal glowing crystal stars on the outer rim
			for (int i = 0; i < 4; i++) {
				double ang = t * 0.16f + i * Math.PI * 0.5;
				float sx = cx + (float) Math.cos(ang) * (outerR * 0.97f);
				float sy = cy + (float) Math.sin(ang) * (outerR * 0.97f);
				GrimoireDraw.star(sx, sy, 3.4f * scale, GOLD, Math.min(1f, alpha * 0.88f * pulse), t * 35f);
			}
		}
	}

	/** Living arcane seal drawn behind each entry card's icon. */
	static void cardSeal(float cx, float cy, int color, float hover, boolean selected, float timeSec, boolean animated) {
		float t = animated ? timeSec : 0f;
		if (selected) {
			float pulse = animated ? 0.85f + 0.15f * (float) Math.sin(t * 3.8f) : 1f;
			GrimoireDraw.glow(cx, cy, 18.5f, color, 0.36f * pulse);
			GrimoireDraw.ring(cx, cy, 11.4f, 1.2f, color, 0.95f, 0, 1, 1, true);
			GrimoireDraw.tickRing(cx, cy, 13.4f, 1.6f, 12, GOLD, 0.65f * pulse, t * 0.55f);
			GrimoireDraw.polygon(cx, cy, 11.4f, 3, 0.8f, GrimoireDraw.mix(color, GOLD, 0.45f), 0.58f, t * 0.9f, true);
			runeRing(cx, cy, 14.0f, 6, 6, color, 0.95f, -t * 0.65f, true);
		} else if (hover > 0.01f) {
			GrimoireDraw.glow(cx, cy, 16f * hover, color, 0.26f * hover);
			GrimoireDraw.ring(cx, cy, 12.6f, 1.2f, color, 0.9f * hover, t * 1.4f, 8, 0.52f, true);
			GrimoireDraw.tickRing(cx, cy, 13.8f, 1.3f, 12, GOLD, 0.45f * hover, -t * 0.8f);
			runeRing(cx, cy, 13.4f, 4, 6, color, 0.85f * hover, -t * 1.1f, true);
		} else {
			GrimoireDraw.ring(cx, cy, 11.0f, 0.9f, color, 0.28f, 0, 1, 1, false);
		}
	}

	/** Compact readable signature label for an entry's type & affinity (respects live PT/EN toggle). */
	static String entryBadgeLabel(am2.lore.CompendiumEntry entry, Affinity affinity) {
		if (entry == null) return null;
		String section = (entry instanceof am2.lore.CompendiumEntryRitual) ? "rituals" : entry.getParentSection();
		String fallback = (section != null && !section.isEmpty()) ? Character.toUpperCase(section.charAt(0)) + section.substring(1) : "";
		String catName = GrimoireLang.categoryLabel(section, fallback);
		if (affinity != null && affinity != Affinity.NONE) {
			return catName + " \u2022 " + GrimoireLang.affinityName(affinity);
		}
		return catName;
	}

	private static final java.util.EnumMap<Affinity, net.minecraft.item.ItemStack> ESSENCES =
			new java.util.EnumMap<Affinity, net.minecraft.item.ItemStack>(Affinity.class);

	/** Shared, never modified: one stack per affinity instead of a new one every frame. */
	static net.minecraft.item.ItemStack essenceForAffinity(Affinity aff) {
		if (aff == null || aff == Affinity.NONE) return null;
		if (!ESSENCES.containsKey(aff)) ESSENCES.put(aff, newEssence(aff));
		return ESSENCES.get(aff);
	}

	private static net.minecraft.item.ItemStack newEssence(Affinity aff) {
		am2.items.ItemEssence ess = am2.items.ItemsCommonProxy.essence;
		switch (aff) {
			case ARCANE: return new net.minecraft.item.ItemStack(ess, 1, ess.META_ARCANE);
			case WATER: return new net.minecraft.item.ItemStack(ess, 1, ess.META_WATER);
			case FIRE: return new net.minecraft.item.ItemStack(ess, 1, ess.META_FIRE);
			case EARTH: return new net.minecraft.item.ItemStack(ess, 1, ess.META_EARTH);
			case AIR: return new net.minecraft.item.ItemStack(ess, 1, ess.META_AIR);
			case LIGHTNING: return new net.minecraft.item.ItemStack(ess, 1, ess.META_LIGHTNING);
			case ICE: return new net.minecraft.item.ItemStack(ess, 1, ess.META_ICE);
			case NATURE: return new net.minecraft.item.ItemStack(ess, 1, ess.META_NATURE);
			case LIFE: return new net.minecraft.item.ItemStack(ess, 1, ess.META_LIFE);
			case ENDER: return new net.minecraft.item.ItemStack(ess, 1, ess.META_ENDER);
			default: return null;
		}
	}

	/** The pale summoning circle of the original book, tinted and turned around its own centre. */
	static void arcaneCircle(float cx, float cy, float scale, int color, float alpha, float degrees) {
		if (alpha <= 0.01f) return;
		GrimoireDraw.flush();
		Minecraft.getMinecraft().renderEngine.bindTexture(EXTRAS);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glPushMatrix();
		GL11.glTranslatef(cx, cy, 0);
		GL11.glRotatef(degrees, 0, 0, 1);
		GL11.glScalef(scale, scale, 1);
		float u1 = 100 / 256f, v0 = 101 / 256f, v1 = 248 / 256f;
		Tessellator t = Tessellator.instance;
		for (int pass = 0; pass < 2; pass++) {
			t.startDrawingQuads();
			t.setColorRGBA_I(color, Math.min(255, (int) (alpha * 255)));
			t.addVertexWithUV(-82, 69, 0, 0, v1);
			t.addVertexWithUV(68, 69, 0, u1, v1);
			t.addVertexWithUV(68, -81, 0, u1, v0);
			t.addVertexWithUV(-82, -81, 0, 0, v0);
			t.draw();
		}
		GL11.glPopMatrix();
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
		GL11.glColor4f(1, 1, 1, 1);
	}

	/** First affinity of a spell component (or a ritual's controller spell), or null for anything else. */
	static Affinity affinityOf(String entryId) {
		ISkillTreeEntry part = SkillManager.instance.getSkill(entryId);
		if (part instanceof ISpellComponent) {
			for (Affinity affinity : ((ISpellComponent) part).getAffinity())
				if (affinity != Affinity.NONE) return affinity;
		}
		return null;
	}

	static Affinity affinityOf(am2.lore.CompendiumEntry entry) {
		if (entry == null) return null;
		if (entry instanceof am2.lore.CompendiumEntryRitual) {
			String controller = ((am2.lore.CompendiumEntryRitual) entry).getRitualController();
			if (controller != null && !controller.isEmpty()) {
				return affinityOf(controller);
			}
			return null;
		}
		return affinityOf(entry.getID());
	}

	static int brighten(int rgb) {
		int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
		if (r * 3 + g * 6 + b < 1300) {
			r += (255 - r) * 2 / 5;
			g += (255 - g) * 2 / 5;
			b += (255 - b) * 2 / 5;
		}
		return (r << 16) | (g << 8) | b;
	}

	static int categoryColor(String category) {
		if ("guides".equals(category)) return 0xFFD98A;
		if ("journey".equals(category)) return 0xFFD966;
		if ("mechanics".equals(category)) return 0x5FE0C0;
		if ("items".equals(category)) return 0x9BD66B;
		if ("blocks".equals(category)) return 0xC9A27A;
		if ("shapes".equals(category)) return 0xB9A7FF;
		if ("components".equals(category)) return 0x7FE3FF;
		if ("modifiers".equals(category)) return 0xFF9ECB;
		if ("talents".equals(category)) return 0xFFB454;
		if ("mobs".equals(category)) return 0xE88C8C;
		if ("structures".equals(category)) return 0x86B8FF;
		if ("rituals".equals(category)) return 0xE078FF;
		if ("bosses".equals(category)) return 0xFF6B6B;
		return 0x7FE3FF;
	}
}
