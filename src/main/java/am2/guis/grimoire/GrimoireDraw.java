package am2.guis.grimoire;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * Hybrid Dark-Fantasy + Anime Arcane 2D OpenGL primitives for the Grimoire:
 * physical open-tome silhouette (stacked parchment edges, shaded spine, swaying velvet bookmark,
 * antique-gold corner filigree, moon phases, subtle celestial constellations), paired with
 * sharp anime-style astrolabe tick rings, targeting brackets, radial glows and page-turn sweeps.
 */
@SideOnly(Side.CLIENT)
final class GrimoireDraw {
	static final int GOLD_HI = 0xFFE59E;
	static final int GOLD_MID = 0xC99842;
	static final int GOLD_LO = 0x6E5020;
	static final int CRIMSON = 0xB82E46;
	static final int CRIMSON_HI = 0xFF5A72;

	// Fixed elegant constellation star positions (normalized 0..1 inside the tome)
	private static final float[][] CONSTELLATION_PTS = {
			{0.08f, 0.16f}, {0.16f, 0.28f}, {0.11f, 0.44f}, {0.22f, 0.56f},
			{0.82f, 0.18f}, {0.91f, 0.30f}, {0.86f, 0.48f}, {0.93f, 0.66f}, {0.77f, 0.78f}
	};
	private static final int[][] CONSTELLATION_EDGES = {
			{0, 1}, {1, 2}, {2, 3}, {1, 3},
			{4, 5}, {5, 6}, {6, 7}, {6, 8}, {4, 6}
	};

	private GrimoireDraw() {}

	// ------------------------------------------------------------------ batching
	// Every shape below is a coloured quad. Inside batch() / endBatch() they share one
	// Tessellator draw (split only when the blend mode changes), instead of one draw call
	// and a full GL state toggle per shape. Order is preserved, so the picture is identical.
	// Anything textured (runes, items, text) must call flush() first; GrimoireDecor does.

	/** Lighter drawing while Effects are off: no radial glows and no constellations. */
	static boolean lite;

	private static int depth;
	private static boolean pending;
	private static boolean pendingAdditive;

	static void batch() {
		depth++;
	}

	static void endBatch() {
		if (depth > 0 && --depth == 0) flush();
	}

	/** Draws what is queued so other renderers can use the Tessellator; the batch stays open. */
	static void flush() {
		if (!pending) return;
		pending = false;
		Tessellator.instance.draw();
		end();
	}

	/** Recovers from a frame that ended with an open batch (for example after an exception). */
	static void resetBatch() {
		depth = 0;
		flush();
	}

	private static Tessellator quads(boolean additive) {
		Tessellator t = Tessellator.instance;
		if (depth == 0) {
			begin(additive);
			t.startDrawingQuads();
			return t;
		}
		if (pending && pendingAdditive != additive) {
			t.draw();
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, additive ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);
			t.startDrawingQuads();
		} else if (!pending) {
			begin(additive);
			t.startDrawingQuads();
			pending = true;
		}
		pendingAdditive = additive;
		return t;
	}

	private static void done() {
		if (depth == 0) {
			Tessellator.instance.draw();
			end();
		}
	}

	static void rect(float x, float y, float w, float h, int rgb, float alpha) {
		if (alpha <= 0.005f || w <= 0 || h <= 0) return;
		Tessellator t = quads(false);
		t.setColorRGBA_I(rgb, a(alpha));
		t.addVertex(x, y + h, 0);
		t.addVertex(x + w, y + h, 0);
		t.addVertex(x + w, y, 0);
		t.addVertex(x, y, 0);
		done();
	}

	static void vGrad(float x, float y, float w, float h, int topRgb, float topAlpha, int botRgb, float botAlpha, boolean additive) {
		if (w <= 0 || h <= 0 || (topAlpha <= 0.005f && botAlpha <= 0.005f)) return;
		Tessellator t = quads(additive);
		t.setColorRGBA_I(topRgb, a(topAlpha));
		t.addVertex(x + w, y, 0);
		t.addVertex(x, y, 0);
		t.setColorRGBA_I(botRgb, a(botAlpha));
		t.addVertex(x, y + h, 0);
		t.addVertex(x + w, y + h, 0);
		done();
	}

	static void hGrad(float x, float y, float w, float h, int leftRgb, float leftAlpha, int rightRgb, float rightAlpha, boolean additive) {
		if (w <= 0 || h <= 0 || (leftAlpha <= 0.005f && rightAlpha <= 0.005f)) return;
		Tessellator t = quads(additive);
		t.setColorRGBA_I(leftRgb, a(leftAlpha));
		t.addVertex(x, y + h, 0);
		t.addVertex(x, y, 0);
		t.setColorRGBA_I(rightRgb, a(rightAlpha));
		t.addVertex(x + w, y, 0);
		t.addVertex(x + w, y + h, 0);
		done();
	}

	/** Soft additive radial light halo (centre -> 0 alpha edge). */
	static void glow(float cx, float cy, float radius, int rgb, float alpha) {
		if (lite || alpha <= 0.005f || radius <= 0.5f) return;
		Tessellator t = quads(true);
		int centre = a(alpha);
		for (int i = 0; i < GLOW_SEGMENTS; i++) {
			t.setColorRGBA_I(rgb, centre);
			t.addVertex(cx, cy, 0);
			t.addVertex(cx, cy, 0);
			t.setColorRGBA_I(rgb, 0);
			t.addVertex(cx + COS[i + 1] * radius, cy + SIN[i + 1] * radius, 0);
			t.addVertex(cx + COS[i] * radius, cy + SIN[i] * radius, 0);
		}
		done();
	}

	private static final int GLOW_SEGMENTS = 28;
	private static final float[] COS = new float[GLOW_SEGMENTS + 1];
	private static final float[] SIN = new float[GLOW_SEGMENTS + 1];

	static {
		for (int i = 0; i <= GLOW_SEGMENTS; i++) {
			double ang = i * Math.PI * 2.0 / GLOW_SEGMENTS;
			COS[i] = (float) Math.cos(ang);
			SIN[i] = (float) Math.sin(ang);
		}
	}

	/** Crisp 4-point arcane crystal sparkle star. */
	static void star(float cx, float cy, float radius, int rgb, float alpha, float rotation) {
		if (alpha <= 0.01f || radius <= 0.4f) return;
		double rad = Math.toRadians(rotation);
		float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
		float inner = radius * 0.24f;
		Tessellator t = quads(true);
		t.setColorRGBA_I(rgb, a(alpha));
		turned(t, cx, cy, c, s, 0, -radius);
		turned(t, cx, cy, c, s, -inner, 0);
		turned(t, cx, cy, c, s, 0, radius);
		turned(t, cx, cy, c, s, inner, 0);
		turned(t, cx, cy, c, s, -radius, 0);
		turned(t, cx, cy, c, s, 0, inner);
		turned(t, cx, cy, c, s, radius, 0);
		turned(t, cx, cy, c, s, 0, -inner);
		done();
	}

	private static void turned(Tessellator t, float cx, float cy, float c, float s, float x, float y) {
		t.addVertex(cx + x * c - y * s, cy + x * s + y * c, 0);
	}

	/** Subtle dark-parchment / obsidian gradient inner panel with beveled border and inner top sheen. */
	static void panel(int x, int y, int w, int h, int fill, int border) {
		batch();
		try {
			panelShapes(x, y, w, h, fill, border);
		} finally {
			endBatch();
		}
	}

	private static void panelShapes(int x, int y, int w, int h, int fill, int border) {
		int dark = shade(fill, 0.74f);
		vGrad(x + 1, y + 1, w - 2, h - 2, fill, 0.94f, dark, 0.97f, false);
		rect(x + 1, y, w - 2, 1, border, 0.92f);
		rect(x + 1, y + h - 1, w - 2, 1, shade(border, 0.68f), 0.92f);
		rect(x, y + 1, 1, h - 2, shade(border, 0.82f), 0.92f);
		rect(x + w - 1, y + 1, 1, h - 2, shade(border, 0.82f), 0.92f);
		// Antique gold micro corner rivets
		rect(x + 1, y + 1, 1, 1, GOLD_MID, 0.65f);
		rect(x + w - 2, y + 1, 1, 1, GOLD_MID, 0.65f);
		rect(x + 1, y + h - 2, 1, 1, GOLD_LO, 0.65f);
		rect(x + w - 2, y + h - 2, 1, 1, GOLD_LO, 0.65f);
		// Subtle top inner sheen
		hGrad(x + 2, y + 1, (w - 4) * 0.5f, 1, border, 0.04f, 0xFFFFFF, 0.14f, true);
		hGrad(x + 2 + (w - 4) * 0.5f, y + 1, (w - 4) * 0.5f, 1, 0xFFFFFF, 0.14f, border, 0.04f, true);
	}

	/**
	 * Full hybrid Dark-Fantasy + Anime open-tome frame:
	 * stacked parchment leaf edges on both sides, swaying crimson-gold velvet bookmark ribbon,
	 * warm candlelight + affinity aura, corner vignette, optional central book spine,
	 * celestial constellation backdrop, antique-gold filigree corners, and lunar phases crest.
	 */
	static void grimoireBookFrame(int x, int y, int w, int h, int spineX, int accentRgb, float timeSec, boolean animated) {
		batch();
		try {
			bookFrameShapes(x, y, w, h, spineX, accentRgb, timeSec, animated);
		} finally {
			endBatch();
		}
	}

	private static void bookFrameShapes(int x, int y, int w, int h, int spineX, int accentRgb, float timeSec, boolean animated) {
		float breathe = animated ? 0.82f + 0.18f * (float) Math.sin(timeSec * 1.55f) : 1.0f;

		// 1. Swaying velvet bookmark ribbon hanging from the bottom edge
		float ribbonX = spineX > 0 ? spineX : (x + w * 0.5f);
		drawBookmarkRibbon(ribbonX, y + h - 2, timeSec, animated);

		// 2. Soft drop shadow beneath the leather tome
		rect(x - 3, y + 4, w + 8, h + 1, 0x03040A, 0.55f);
		rect(x + 2, y + 3, w + 2, h + 1, 0x04060E, 0.45f);

		// 3. Stacked aged parchment page-edges peeking out left & right (physical open book thickness)
		drawStackedPages(x, y, w, h);

		// 4. Outer dark leather binding cover lip
		rect(x - 1, y - 1, w + 2, h + 2, 0x161124, 0.98f);
		rect(x, y, w, h, 0x080A14, 1f);

		// 5. Deep nocturnal-indigo body gradient with warm antique undertone
		vGrad(x + 1, y + 1, w - 2, h - 2, 0x151832, 0.98f, 0x0B0D1D, 0.99f, false);

		// 6. Subtle candlelit warm gold centre glow + dynamic affinity aura
		glow(x + w * 0.5f, y + 16, w * 0.42f, GOLD_MID, 0.055f * breathe);
		glow(x + w * 0.5f, y + 10, w * 0.32f, accentRgb, 0.065f * breathe);
		glow(x + w - 46, y + h * 0.48f, 105f, accentRgb, 0.06f * breathe);

		// 7. Celestial constellation lines & twinkling stars in the background
		if (!lite) drawConstellations(x, y, w, h, accentRgb, timeSec, animated);

		// 8. Dark gothic corner vignette (frames the parchment like candlelight)
		vGrad(x + 1, y + 1, w - 2, 18, 0x05060E, 0.36f, 0x05060E, 0f, false);
		vGrad(x + 1, y + h - 20, w - 2, 19, 0x05060E, 0f, 0x05060E, 0.42f, false);
		hGrad(x + 1, y + 1, 18, h - 2, 0x05060E, 0.30f, 0x05060E, 0f, false);
		hGrad(x + w - 19, y + 1, 18, h - 2, 0x05060E, 0f, 0x05060E, 0.30f, false);

		// 9. Double beveled arcane-gold inner border
		rect(x + 2, y + 1, w - 4, 1, 0x4D5B94, 0.92f);
		rect(x + 2, y + h - 2, w - 4, 1, 0x3A4574, 0.92f);
		rect(x + 1, y + 2, 1, h - 4, 0x424E82, 0.92f);
		rect(x + w - 2, y + 2, 1, h - 4, 0x424E82, 0.92f);

		// Top & bottom ethereal luminous accent edges
		float midX = x + w * 0.5f;
		float half = Math.max(16f, w * 0.42f);
		hGrad(midX - half, y + 1, half, 1, accentRgb, 0f, accentRgb, 0.55f * breathe, true);
		hGrad(midX, y + 1, half, 1, accentRgb, 0.55f * breathe, accentRgb, 0f, true);
		hGrad(midX - half * 0.75f, y + h - 2, half * 0.75f, 1, GOLD_MID, 0f, GOLD_HI, 0.48f * breathe, true);
		hGrad(midX, y + h - 2, half * 0.75f, 1, GOLD_HI, 0.48f * breathe, GOLD_MID, 0f, true);

		// 10. Optional central book spine shadow crease (for 2-page entry reading view)
		if (spineX > x + 20 && spineX < x + w - 20) {
			hGrad(spineX - 11, y + 4, 11, h - 8, 0x04050C, 0f, 0x04050C, 0.55f, false);
			hGrad(spineX, y + 4, 11, h - 8, 0x04050C, 0.55f, 0x04050C, 0f, false);
			rect(spineX, y + 5, 1, h - 10, GOLD_LO, 0.55f);
			vGrad(spineX, y + 6, 1, (h - 12) * 0.5f, GOLD_HI, 0.05f, GOLD_HI, 0.45f * breathe, true);
			vGrad(spineX, y + 6 + (h - 12) * 0.5f, 1, (h - 12) * 0.5f, GOLD_HI, 0.45f * breathe, GOLD_HI, 0.05f, true);
		}

		// 11. Antique-gold gothic corner filigree + arcane gem
		drawCornerFiligree(x + 1, y + 1, 1, 1, accentRgb, breathe);
		drawCornerFiligree(x + w - 2, y + 1, -1, 1, accentRgb, breathe);
		drawCornerFiligree(x + 1, y + h - 2, 1, -1, accentRgb, breathe);
		drawCornerFiligree(x + w - 2, y + h - 2, -1, -1, accentRgb, breathe);

		// 12. Subtle antique-gold lunar phase ornaments along bottom centre rim
		drawLunarPhases(midX, y + h - 1, accentRgb, breathe);
	}

	private static void drawStackedPages(int x, int y, int w, int h) {
		// Left stacked parchment leaves (3 layered sheets stepping outward)
		rect(x - 4, y + 5, 3, h - 10, 0x262135, 0.95f);
		rect(x - 3, y + 4, 2, h - 8, 0x3D3549, 0.96f);
		rect(x - 2, y + 3, 1, h - 6, 0x574B5E, 0.92f);
		// Right stacked parchment leaves
		rect(x + w + 1, y + 5, 3, h - 10, 0x262135, 0.95f);
		rect(x + w + 1, y + 4, 2, h - 8, 0x3D3549, 0.96f);
		rect(x + w + 1, y + 3, 1, h - 6, 0x574B5E, 0.92f);
	}

	private static void drawBookmarkRibbon(float cx, float topY, float timeSec, boolean animated) {
		float sway = animated ? (float) Math.sin(timeSec * 1.45f) * 1.6f : 0f;
		float len = 12f;
		float hw = 4.2f;
		Tessellator t = quads(false);
		// Crimson velvet pennant with swallowtail V-notch
		t.setColorRGBA_I(CRIMSON, 240);
		t.addVertex(cx - hw, topY, 0);
		t.addVertex(cx, topY, 0);
		t.setColorRGBA_I(0x6C1528, 245);
		t.addVertex(cx + sway, topY + len - 3.2f, 0);
		t.addVertex(cx - hw + sway, topY + len, 0);

		t.setColorRGBA_I(CRIMSON, 240);
		t.addVertex(cx, topY, 0);
		t.addVertex(cx + hw, topY, 0);
		t.setColorRGBA_I(0x6C1528, 245);
		t.addVertex(cx + hw + sway, topY + len, 0);
		t.addVertex(cx + sway, topY + len - 3.2f, 0);
		done();
		// Fine golden embroidery thread down the ribbon centre
		line(cx, topY + 1, cx + sway, topY + len - 3.6f, 1.0f, GOLD_HI, 0.85f, true);
		line(cx - hw + 0.6f, topY + 1, cx - hw + 0.6f + sway, topY + len - 0.5f, 0.7f, GOLD_MID, 0.65f, false);
		line(cx + hw - 0.6f, topY + 1, cx + hw - 0.6f + sway, topY + len - 0.5f, 0.7f, GOLD_MID, 0.65f, false);
	}

	private static void drawConstellations(int x, int y, int w, int h, int accentRgb, float timeSec, boolean animated) {
		float alphaBase = 0.14f;
		for (int[] edge : CONSTELLATION_EDGES) {
			float x0 = x + CONSTELLATION_PTS[edge[0]][0] * w;
			float y0 = y + CONSTELLATION_PTS[edge[0]][1] * h;
			float x1 = x + CONSTELLATION_PTS[edge[1]][0] * w;
			float y1 = y + CONSTELLATION_PTS[edge[1]][1] * h;
			line(x0, y0, x1, y1, 0.75f, GOLD_MID, alphaBase, true);
		}
		for (int i = 0; i < CONSTELLATION_PTS.length; i++) {
			float sx = x + CONSTELLATION_PTS[i][0] * w;
			float sy = y + CONSTELLATION_PTS[i][1] * h;
			float tw = animated ? 0.5f + 0.5f * (float) Math.sin(timeSec * 2.1f + i * 1.7f) : 0.8f;
			star(sx, sy, 2.0f + tw * 0.9f, i % 2 == 0 ? GOLD_HI : accentRgb, 0.25f + 0.28f * tw, timeSec * 12f);
		}
	}

	private static void drawLunarPhases(float cx, float cy, int accentRgb, float breathe) {
		// Five tiny lunar emblems flanking the bottom spine (new -> crescent -> glowing full -> crescent -> new)
		float[] offsets = {-28f, -15f, 0f, 15f, 28f};
		for (int i = 0; i < offsets.length; i++) {
			float mx = cx + offsets[i];
			if (i == 2) {
				glow(mx, cy, 5.5f, GOLD_HI, 0.35f * breathe);
				ring(mx, cy, 2.3f, 2.3f, GOLD_HI, 0.95f, 0, 1, 1, true);
			} else if (i == 1 || i == 3) {
				ring(mx, cy, 2.0f, 0.9f, GOLD_MID, 0.85f, i == 1 ? -1.1f : 2.0f, 1, 0.58f, true);
			} else {
				ring(mx, cy, 1.7f, 0.65f, accentRgb, 0.55f, 0, 1, 1, true);
			}
		}
	}

	private static void drawCornerFiligree(int cx, int cy, int dx, int dy, int gemRgb, float breathe) {
		for (int k = 0; k < 12; k++) {
			int col = k < 4 ? GOLD_HI : (k < 8 ? GOLD_MID : GOLD_LO);
			rect(cx + dx * k, cy, 1, 1, col, 0.95f);
			rect(cx, cy + dy * k, 1, 1, col, 0.95f);
		}
		// Secondary gothic inner step
		for (int k = 2; k < 7; k++) {
			rect(cx + dx * k, cy + dy * 2, 1, 1, GOLD_LO, 0.7f);
			rect(cx + dx * 2, cy + dy * k, 1, 1, GOLD_LO, 0.7f);
		}
		rect(cx + dx, cy + dy, 1, 1, GOLD_HI, 1f);
		glow(cx + dx * 3, cy + dy * 3, 5f, gemRgb, 0.38f * breathe);
		rect(cx + dx * 2, cy + dy * 2, 2, 2, gemRgb, 0.88f * breathe);
	}

	/** Sharp anime-style targeting brackets that contract around a hovered/selected tarot card. */
	static void brackets(float x, float y, float w, float h, float len, int rgb, float alpha) {
		if (alpha <= 0.01f) return;
		batch();
		try {
			bracketShapes(x, y, w, h, len, rgb, alpha);
		} finally {
			endBatch();
		}
	}

	private static void bracketShapes(float x, float y, float w, float h, float len, int rgb, float alpha) {
		float t = 1.2f;
		// Top-left
		rect(x, y, len, t, rgb, alpha);
		rect(x, y, t, len, rgb, alpha);
		// Top-right
		rect(x + w - len, y, len, t, rgb, alpha);
		rect(x + w - t, y, t, len, rgb, alpha);
		// Bottom-left
		rect(x, y + h - t, len, t, rgb, alpha);
		rect(x, y + h - len, t, len, rgb, alpha);
		// Bottom-right
		rect(x + w - len, y + h - t, len, t, rgb, alpha);
		rect(x + w - t, y + h - len, t, len, rgb, alpha);
	}

	/** Crimson-gold wax seal badge for new discoveries. */
	static void waxSeal(float cx, float cy, float radius, float timeSec, boolean animated) {
		float pulse = animated ? 0.75f + 0.25f * (float) Math.sin(timeSec * 4.4f) : 1f;
		batch();
		try {
			glow(cx, cy, radius * 2.1f, GOLD_HI, 0.32f * pulse);
		ring(cx, cy, radius, radius, CRIMSON, 0.95f, 0, 1, 1, false);
		ring(cx, cy, radius + 0.4f, 0.8f, GOLD_HI, 0.9f * pulse, 0, 1, 1, true);
			star(cx, cy, radius * 0.85f * pulse, GOLD_HI, 0.95f, animated ? timeSec * 35f : 0f);
		} finally {
			endBatch();
		}
	}

	/** Subtle page-turn shadow + luminous crease sweeping across a panel (progress 0 -> 1). */
	static void pageTurnSweep(float x, float y, float w, float h, float progress, int accentRgb) {
		if (progress <= 0f || progress >= 1f) return;
		float ease = 1f - (1f - progress) * (1f - progress);
		float foldX = x + w * (1f - ease);
		float fade = (float) Math.sin(progress * Math.PI);
		float shadowW = Math.min(34f, w * 0.22f);
		float s0 = Math.max(x, foldX - shadowW);
		if (foldX > s0) {
			hGrad(s0, y, foldX - s0, h, 0x03040A, 0f, 0x03040A, 0.48f * fade, false);
		}
		float g1 = Math.min(x + w, foldX + 14f);
		if (g1 > foldX) {
			hGrad(foldX, y, g1 - foldX, h, mix(accentRgb, GOLD_HI, 0.55f), 0.32f * fade, accentRgb, 0f, true);
		}
		rect(foldX, y, 1.2f, h, GOLD_HI, 0.55f * fade);
	}

	/** Precision anime-astrolabe angular graduation ticks around a circle. */
	static void tickRing(float cx, float cy, float radius, float tickLen, int count, int rgb, float alpha, float rotation) {
		if (alpha <= 0.008f || count <= 0 || radius <= 1f) return;
		Tessellator t = quads(true);
		for (int i = 0; i < count; i++) {
			double ang = rotation + i * Math.PI * 2.0 / count;
			boolean major = (i % 3 == 0);
			float len = major ? tickLen * 1.65f : tickLen;
			float halfW = major ? 0.55f : 0.35f;
			float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
			float nx = -sin * halfW, ny = cos * halfW;
			float r0 = radius - len, r1 = radius;
			t.setColorRGBA_I(major ? mix(rgb, GOLD_HI, 0.5f) : rgb, a(major ? alpha * 1.15f : alpha * 0.72f));
			t.addVertex(cx + cos * r0 + nx, cy + sin * r0 + ny, 0);
			t.addVertex(cx + cos * r1 + nx, cy + sin * r1 + ny, 0);
			t.addVertex(cx + cos * r1 - nx, cy + sin * r1 - ny, 0);
			t.addVertex(cx + cos * r0 - nx, cy + sin * r0 - ny, 0);
		}
		done();
	}

	/** Beveled arcane button with gradient fill, top sheen and glowing hover border. */
	static void arcaneButton(int x, int y, int w, int h, int accentRgb, boolean active, boolean hover) {
		batch();
		try {
			buttonShapes(x, y, w, h, accentRgb, active, hover);
		} finally {
			endBatch();
		}
	}

	private static void buttonShapes(int x, int y, int w, int h, int accentRgb, boolean active, boolean hover) {
		int top = hover ? mix(0x232B56, accentRgb, 0.26f) : active ? mix(0x1B2246, accentRgb, 0.14f) : 0x151A34;
		int bot = hover ? mix(0x141A38, accentRgb, 0.14f) : 0x0D1124;
		int border = hover ? accentRgb : active ? mix(0x525F99, accentRgb, 0.45f) : 0x354273;
		vGrad(x + 1, y + 1, w - 2, h - 2, top, 0.96f, bot, 0.98f, false);
		rect(x + 1, y, w - 2, 1, border, 0.95f);
		rect(x + 1, y + h - 1, w - 2, 1, shade(border, 0.72f), 0.95f);
		rect(x, y + 1, 1, h - 2, shade(border, 0.86f), 0.95f);
		rect(x + w - 1, y + 1, 1, h - 2, shade(border, 0.86f), 0.95f);
		if (hover || active) {
			hGrad(x + 2, y + 1, (w - 4) * 0.5f, 1, accentRgb, 0.05f, 0xFFFFFF, hover ? 0.38f : 0.18f, true);
			hGrad(x + 2 + (w - 4) * 0.5f, y + 1, (w - 4) * 0.5f, 1, 0xFFFFFF, hover ? 0.38f : 0.18f, accentRgb, 0.05f, true);
		}
	}

	/** Pill badge for type/affinity signature (e.g. "Ritual • Arcano"). */
	static void badge(int x, int y, int w, int h, int rgb) {
		batch();
		try {
			badgeShapes(x, y, w, h, rgb);
		} finally {
			endBatch();
		}
	}

	private static void badgeShapes(int x, int y, int w, int h, int rgb) {
		vGrad(x + 1, y + 1, w - 2, h - 2, mix(0x151933, rgb, 0.22f), 0.9f, 0x0D1022, 0.94f, false);
		rect(x + 1, y, w - 2, 1, rgb, 0.65f);
		rect(x + 1, y + h - 1, w - 2, 1, shade(rgb, 0.65f), 0.65f);
		rect(x, y + 1, 1, h - 2, rgb, 0.55f);
		rect(x + w - 1, y + 1, 1, h - 2, rgb, 0.55f);
	}

	static void ring(float cx, float cy, float radius, float thickness, int rgb, float alpha,
					 float rotation, int dashes, float fill) {
		ring(cx, cy, radius, thickness, rgb, alpha, rotation, dashes, fill, false);
	}

	static void ring(float cx, float cy, float radius, float thickness, int rgb, float alpha,
					 float rotation, int dashes, float fill, boolean additive) {
		if (alpha <= 0.008f || radius <= 0.5f) return;
		Tessellator t = quads(additive);
		t.setColorRGBA_I(rgb, a(alpha));
		double slot = Math.PI * 2 / dashes;
		int steps = Math.max(2, 40 / dashes);
		float in = Math.max(0f, radius - thickness);
		for (int d = 0; d < dashes; d++) {
			double from = rotation + d * slot;
			double span = slot * fill / steps;
			for (int s = 0; s < steps; s++) {
				double a0 = from + s * span, a1 = a0 + span;
				t.addVertex(cx + Math.cos(a0) * radius, cy + Math.sin(a0) * radius, 0);
				t.addVertex(cx + Math.cos(a0) * in, cy + Math.sin(a0) * in, 0);
				t.addVertex(cx + Math.cos(a1) * in, cy + Math.sin(a1) * in, 0);
				t.addVertex(cx + Math.cos(a1) * radius, cy + Math.sin(a1) * radius, 0);
			}
		}
		done();
	}

	/** Regular sacred-geometry polygon outline (triangle, square, hexagon, etc.). */
	static void polygon(float cx, float cy, float radius, int sides, float thickness, int rgb, float alpha, float rotation, boolean additive) {
		if (alpha <= 0.008f || sides < 3 || radius <= 1f) return;
		batch();
		try {
			polygonShapes(cx, cy, radius, sides, thickness, rgb, alpha, rotation, additive);
		} finally {
			endBatch();
		}
	}

	private static void polygonShapes(float cx, float cy, float radius, int sides, float thickness, int rgb, float alpha, float rotation, boolean additive) {
		for (int i = 0; i < sides; i++) {
			double a0 = rotation + i * Math.PI * 2.0 / sides;
			double a1 = rotation + (i + 1) * Math.PI * 2.0 / sides;
			line(cx + (float) Math.cos(a0) * radius, cy + (float) Math.sin(a0) * radius,
					cx + (float) Math.cos(a1) * radius, cy + (float) Math.sin(a1) * radius,
					thickness, rgb, alpha, additive);
		}
	}

	static void line(float x0, float y0, float x1, float y1, float width, int rgb, float alpha) {
		line(x0, y0, x1, y1, width, rgb, alpha, false);
	}

	static void line(float x0, float y0, float x1, float y1, float width, int rgb, float alpha, boolean additive) {
		if (alpha <= 0.008f) return;
		float dx = x1 - x0, dy = y1 - y0;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 0.001f) return;
		float nx = -dy / len * width / 2, ny = dx / len * width / 2;
		Tessellator t = quads(additive);
		t.setColorRGBA_I(rgb, a(alpha));
		t.addVertex(x0 + nx, y0 + ny, 0);
		t.addVertex(x1 + nx, y1 + ny, 0);
		t.addVertex(x1 - nx, y1 - ny, 0);
		t.addVertex(x0 - nx, y0 - ny, 0);
		done();
	}

	static int mix(int rgbA, int rgbB, float t) {
		float f = Math.max(0f, Math.min(1f, t));
		int ar = (rgbA >> 16) & 255, ag = (rgbA >> 8) & 255, ab = rgbA & 255;
		int br = (rgbB >> 16) & 255, bg = (rgbB >> 8) & 255, bb = rgbB & 255;
		int r = (int) (ar + (br - ar) * f);
		int g = (int) (ag + (bg - ag) * f);
		int b = (int) (ab + (bb - ab) * f);
		return (r << 16) | (g << 8) | b;
	}

	static int shade(int rgb, float factor) {
		int r = Math.min(255, Math.max(0, (int) (((rgb >> 16) & 255) * factor)));
		int g = Math.min(255, Math.max(0, (int) (((rgb >> 8) & 255) * factor)));
		int b = Math.min(255, Math.max(0, (int) ((rgb & 255) * factor)));
		return (r << 16) | (g << 8) | b;
	}

	private static int a(float alpha) {
		return Math.max(0, Math.min(255, (int) (alpha * 255)));
	}

	private static void begin(boolean additive) {
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, additive ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glShadeModel(GL11.GL_SMOOTH);
	}

	private static void end() {
		GL11.glShadeModel(GL11.GL_FLAT);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glColor4f(1, 1, 1, 1);
	}
}
