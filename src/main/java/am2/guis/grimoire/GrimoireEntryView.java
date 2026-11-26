package am2.guis.grimoire;

import am2.api.spell.enums.Affinity;
import am2.lore.ArcaneCompendium;
import am2.lore.CompendiumEntry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Rich dark Grimoire layout for a Compendium entry:
 * filigree arcane frame, smooth-scrolling lore column with ornate page dividers on the left,
 * and a multi-layered counter-rotating arcane mandala behind the item, recipe, creature or
 * multiblock structure on the right.
 */
@SideOnly(Side.CLIENT)
public final class GrimoireEntryView {
	public static final int INK = 0xEEF1FF;
	private static final int BG = 0x11152A, PANEL = 0x171D3A, BORDER = 0x3D4C8A;
	private static final int GOLD = 0xFFE08C;
	private static final int LINE_H = 10;
	private static final String DIVIDER = "\u0001";

	private final GrimoirePrefs prefs = GrimoirePrefs.get();
	private final List<String> lines = new ArrayList<String>();
	private int winX, winY, winW, winH, leftW, rightX, rightW;
	private int textY, textH, pageLeft;
	private int wrappedWidth = -1;
	private int color = 0x7FE3FF;
	private float shown = -1;
	private float lastDelta;
	private float lastTimeSec;
	private long lastFrame, openedAt;

	/** Null when the player prefers the classic book. */
	public static GrimoireEntryView createIfEnabled() {
		return GrimoirePrefs.get().classic ? null : new GrimoireEntryView();
	}

	/** Computes the layout and returns the virtual origin used by the classic right-page renderers. */
	public int pageLeft(int width) {
		winW = Math.min(width - 8, 444);
		winX = (width - winW) / 2;
		rightW = winW < 380 ? 188 : 204;
		rightX = winX + winW - rightW - 5;
		leftW = rightX - 4 - (winX + 5);
		// Classic renderers centre their content on origin + 250.
		pageLeft = rightX + rightW / 2 - 258;
		return pageLeft;
	}

	public int backX() {
		return winX + 10;
	}

	public int backY(int height) {
		return (height - Math.min(height - 4, 258)) / 2 + 10;
	}

	public int langBtnX() {
		return winX + leftW - 22;
	}

	public int langBtnY(int height) {
		return backY(height);
	}

	public void invalidateWrap() {
		wrappedWidth = -1;
	}

	public void draw(int width, int height, FontRenderer font, String entryName, CompendiumEntry entry, boolean plainItem, boolean structure) {
		long now = Minecraft.getSystemTime();
		if (openedAt == 0) openedAt = lastFrame = now;
		float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		float timeSec = (now % 3600000L) / 1000f;
		lastTimeSec = timeSec;

		pageLeft(width);
		winH = Math.min(height - 4, 258);
		winY = (height - winH) / 2;

		if (entry != null) {
			Affinity affinity = GrimoireDecor.affinityOf(entry);
			String cat = (entry instanceof am2.lore.CompendiumEntryRitual) ? "rituals" : entry.getParentSection();
			color = affinity != null ? GrimoireDecor.brighten(affinity.color) : GrimoireDecor.categoryColor(cat);
		}

		// The panels must never hide the 3D content drawn afterwards.
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDepthMask(false);

		int spineX = rightX - 2;
		GrimoireDraw.resetBatch();
		GrimoireDraw.lite = !prefs.effects;
		GrimoireDraw.grimoireBookFrame(winX, winY, winW, winH, spineX, color, timeSec, prefs.effects);
		GrimoireDraw.panel(winX + 5, winY + 5, leftW, winH - 10, PANEL, 0x2D386A);
		GrimoireDraw.panel(rightX, winY + 5, rightW, winH - 10, PANEL, 0x2D386A);

		// Deep shaded book-spine fold cast slightly over the inner edges of both pages
		GrimoireDraw.hGrad(winX + 5 + leftW - 10, winY + 6, 10, winH - 12, 0x04050C, 0f, 0x04050C, 0.34f, false);
		GrimoireDraw.hGrad(rightX + 1, winY + 6, 10, winH - 12, 0x04050C, 0.34f, 0x04050C, 0f, false);

		float appear = prefs.effects ? Math.min(1f, (now - openedAt) / 420f) : 1f;
		float appearEase = 1f - (1f - appear) * (1f - appear);
		float cx = pageLeft + 258;
		float cy = (height - 256) / 2 + 122;
		float circleAlpha = structure ? 0.22f : plainItem ? 0.85f : 0.48f;
		float scale = (0.72f + 0.28f * appearEase) * Math.min(1f, (rightW - 10) / 152f);

		// Multi-layered counter-rotating arcane astrolabe mandala behind the showcase
		GrimoireDecor.arcaneMandala(cx, cy, scale, color, circleAlpha * appearEase, timeSec, prefs.effects, !structure);
		if (plainItem && !structure) {
			float breathe = prefs.effects ? 2.0f * (float) Math.sin(timeSec * 2.4f) : 0f;
			GrimoireDraw.glow(cx, cy, 34f * appearEase, color, 0.34f * appearEase);
			GrimoireDraw.ring(cx, cy, 18f + breathe, 1.2f, GOLD, 0.8f * appearEase, -timeSec * 1.1f, 6, 0.58f, true);
			GrimoireDecor.runeRing(cx, cy, 32f * appearEase, 8, 8, color, 0.85f * appearEase, timeSec * 0.42f, true);
		}

		cornerRunes(winX + 5, winY + 5, leftW, winH - 10, timeSec);
		cornerRunes(rightX, winY + 5, rightW, winH - 10, timeSec);

		drawTitle(font, entryName, entry, now);
		GrimoireDecor.runeStrip(winX + 12, textY - 8, leftW - 16, color, 0.38f, now, prefs.effects);
		drawText(font, entryName, entry, dt, timeSec);

		// Smooth page-turn sweep across the open tome when first opening an entry
		if (prefs.effects && now - openedAt < 260L) {
			float pt = (now - openedAt) / 260f;
			GrimoireDraw.pageTurnSweep(winX + 5, winY + 5, winW - 10, winH - 10, pt, color);
		}

		GL11.glDepthMask(true);
		GL11.glColor4f(1, 1, 1, 1);
	}

	private void cornerRunes(int x, int y, int w, int h, float timeSec) {
		float pulse = prefs.effects ? 0.65f + 0.35f * (float) Math.sin(timeSec * 2.0f) : 0.75f;
		GrimoireDecor.rune(2, x + 7, y + h - 7, 7, color, 0.6f * pulse, true);
		GrimoireDecor.rune(7, x + w - 7, y + h - 7, 7, color, 0.6f * pulse, true);
		GrimoireDecor.rune(11, x + w - 7, y + 7, 7, color, 0.6f * pulse, true);
	}

	private void drawTitle(FontRenderer font, String entryName, CompendiumEntry entry, long now) {
		String title = entry == null ? entryName : entry.getName(entryName);
		List<?> parts = font.listFormattedStringToWidth(title, leftW - 18);
		float settle = prefs.effects ? Math.min(1f, (now - openedAt) / 360f) : 1f;
		int titleCol = GrimoireDraw.mix(0xFFFFFF, GOLD, settle);
		int y = winY + 27;
		for (int i = 0; i < Math.min(2, parts.size()); i++) {
			font.drawStringWithShadow((String) parts.get(i), winX + 13, y, titleCol);
			y += LINE_H;
		}
		String badge = entry != null ? GrimoireDecor.entryBadgeLabel(entry, GrimoireDecor.affinityOf(entry)) : null;
		if (badge != null && !badge.isEmpty()) {
			int bw = Math.min(leftW - 22, font.getStringWidth(badge) + 8);
			GrimoireDraw.badge(winX + 13, y + 1, bw, 11, color);
			font.drawString(font.trimStringToWidth(badge, bw - 6), winX + 17, y + 3, color);
			y += 14;
		}
		// Display accepted spell modifiers (<modifiedBy>) as crisp compact tags on Spell Component & Shape pages!
		am2.api.spell.enums.SpellModifiers[] mods = null;
		if (entry instanceof am2.lore.CompendiumEntrySpellComponent) {
			mods = ((am2.lore.CompendiumEntrySpellComponent) entry).getModifiedBy();
		} else if (entry instanceof am2.lore.CompendiumEntrySpellShape) {
			mods = ((am2.lore.CompendiumEntrySpellShape) entry).getModifiedBy();
		}
		if (mods != null && mods.length > 0) {
			StringBuilder sb = new StringBuilder(GrimoireLang.tr("am2.grimoire.accepts")).append(' ');
			int shownMods = 0;
			for (int i = 0; i < mods.length; i++) {
				if (mods[i] == null) continue;
				if (shownMods > 0) sb.append(" \u2022 ");
				sb.append(GrimoireLang.modifierName(mods[i]));
				shownMods++;
			}
			if (shownMods > 0) {
				GL11.glPushMatrix();
				GL11.glScalef(0.76f, 0.76f, 1f);
				int scaledW = (int) ((leftW - 22) / 0.76f);
				List<?> modLines = font.listFormattedStringToWidth(sb.toString(), scaledW);
				int maxModLines = Math.min(2, modLines.size());
				for (int i = 0; i < maxModLines; i++) {
					font.drawString((String) modLines.get(i), (int) ((winX + 13) / 0.76f), (int) ((y + i * 7.5f) / 0.76f), 0xB8C7F0);
				}
				GL11.glPopMatrix();
				y += maxModLines * 8 + 2;
			}
		}
		textY = y + 11;
		textH = winY + winH - 15 - textY;
	}

	private void drawText(FontRenderer font, String entryName, CompendiumEntry entry, float dt, float timeSec) {
		int width = leftW - 22;
		if (width != wrappedWidth) {
			wrappedWidth = width;
			wrap(font, entry == null ? "" : entry.getDescription(entryName), width);
		}
		if (shown < 0) shown = 0;
		Minecraft mc = Minecraft.getMinecraft();
		int factor = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor((winX + 6) * factor, mc.displayHeight - (textY + textH) * factor,
				Math.max(0, (leftW - 2) * factor), Math.max(0, textH * factor));
		int first = Math.max(0, (int) shown);
		float offset = (shown - first) * LINE_H;
		for (int i = first; i < lines.size() && (i - first) * LINE_H - offset < textH; i++) {
			float y = textY + (i - first) * LINE_H - offset;
			String line = lines.get(i);
			if (DIVIDER.equals(line)) {
				GrimoireDecor.ornateDivider(winX + 14, y + 4, leftW - 22, color, 0.9f, i + 3, timeSec, prefs.effects);
			} else {
				GL11.glPushMatrix();
				GL11.glTranslatef(0, y - (int) y, 0);
				font.drawString(line, winX + 13, (int) y, INK);
				GL11.glPopMatrix();
			}
		}
		GL11.glDisable(GL11.GL_SCISSOR_TEST);
		lastDelta = dt;
	}

	/** Eases the visible position towards the requested line. */
	public void follow(int scroll) {
		if (shown < 0) shown = scroll;
		else shown = prefs.effects ? shown + (scroll - shown) * Math.min(1, lastDelta * 16) : scroll;
		if (Math.abs(scroll - shown) < 0.02f) shown = scroll;
	}

	public void drawScrollBar(int scroll) {
		int max = maxScroll();
		if (max <= 0) return;
		int thumb = Math.max(12, textH * textH / (textH + max * LINE_H));
		float ty = textY + (textH - thumb) * Math.min(1, shown / max);
		GrimoireDraw.rect(winX + leftW, textY, 2, textH, 0x0B0F22, 0.7f);
		GrimoireDraw.vGrad(winX + leftW, ty, 2, thumb, color, 0.9f, BORDER, 0.85f, false);
		if (scroll < max) {
			float bob = prefs.effects ? 1.5f * (float) Math.sin(lastTimeSec * 4.2f) : 0f;
			float bx = winX + leftW * 0.5f + 5f;
			float by = textY + textH + 5f + bob;
			GrimoireDecor.rune(14, bx, by, 8, GOLD, 0.9f, true);
			GrimoireDraw.star(bx - 10f, by, 2.0f, color, 0.7f, 0);
			GrimoireDraw.star(bx + 10f, by, 2.0f, color, 0.7f, 0);
		}
	}

	public int maxScroll() {
		return Math.max(0, lines.size() - Math.max(1, textH / LINE_H));
	}

	/** Luminous arcane rune circle behind one recipe ingredient. */
	public void slot(int x, int y) {
		GL11.glDepthMask(false);
		float cx = x + 8f;
		float cy = y + 8f;
		if (prefs.effects) {
			GrimoireDraw.glow(cx, cy, 14f, color, 0.20f);
		}
		GrimoireDraw.ring(cx, cy, 11, 11, BG, 0.82f, 0, 1, 1, false);
		GrimoireDraw.ring(cx, cy, 12.2f, 1.1f, color, 0.85f, 0, 1, 1, true);
		GrimoireDraw.ring(cx, cy, 10.2f, 0.7f, GOLD, 0.45f, prefs.effects ? lastTimeSec * 0.5f : 0, 4, 0.35f, true);
		GL11.glDepthMask(true);
	}

	/** Converts the book's #n colour codes to chat codes and wraps the text for the column. */
	private void wrap(FontRenderer font, String text, int width) {
		lines.clear();
		StringBuilder paragraph = new StringBuilder();
		String[] colour = {""};
		String[] start = {""};
		for (String word : (text == null ? "" : text).replace("\n", " ").replace("\t", "").split(" ")) {
			String token = word.trim();
			if (token.isEmpty()) continue;
			boolean page = token.equals(ArcaneCompendium.KEYWORD_NEWPAGE);
			boolean single = token.equals(ArcaneCompendium.KEYWORD_NEWLINE);
			boolean twice = token.equals(ArcaneCompendium.KEYWORD_DOUBLENEWLINE);
			if (page || single || twice) {
				flush(font, paragraph, start[0], width);
				start[0] = colour[0];
				if (twice) blank();
				if (page) {
					blank();
					if (!lines.isEmpty()) lines.add(DIVIDER);
					blank();
				}
				continue;
			}
			if (paragraph.length() > 0) paragraph.append(' ');
			for (int i = 0; i < token.length(); i++) {
				char c = token.charAt(i);
				if (c == '#' && i + 1 < token.length() && "0123456789abcdef".indexOf(token.charAt(i + 1)) >= 0) {
					colour[0] = chatCode(token.charAt(++i));
					paragraph.append(colour[0].isEmpty() ? "\u00a7r" : colour[0]);
				} else {
					paragraph.append(c);
				}
			}
		}
		flush(font, paragraph, start[0], width);
		while (!lines.isEmpty() && (lines.get(lines.size() - 1).isEmpty() || DIVIDER.equals(lines.get(lines.size() - 1))))
			lines.remove(lines.size() - 1);
	}

	@SuppressWarnings("unchecked")
	private void flush(FontRenderer font, StringBuilder paragraph, String startColour, int width) {
		if (paragraph.length() == 0) return;
		lines.addAll(font.listFormattedStringToWidth(startColour + paragraph, Math.max(30, width)));
		paragraph.setLength(0);
	}

	private void blank() {
		if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) lines.add("");
	}

	/** Parchment colours re-picked with unicode escapes so every highlight stays crisp on dark panels. */
	private static String chatCode(char code) {
		switch (code) {
			case '0': return "";
			case '1': case '3': case '9': case 'b': return "\u00a7b";
			case '2': case 'a': return "\u00a7a";
			case '4': case 'c': return "\u00a7c";
			case '5': case 'd': return "\u00a7d";
			case '6': case 'e': return "\u00a7e";
			case '7': case '8': return "\u00a77";
			default: return "\u00a7f";
		}
	}
}
