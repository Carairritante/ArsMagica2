package am2.guis.grimoire;

import am2.api.SkillTreeEntry;
import am2.api.spell.component.interfaces.ISkillTreeEntry;
import am2.api.spell.enums.Affinity;
import am2.api.spell.enums.LearnStates;
import am2.api.spell.enums.SkillPointTypes;
import am2.guis.AMGuiHelper;
import am2.guis.AMGuiHelper.CompendiumBreadcrumb;
import am2.guis.GuiArcaneCompendium;
import am2.guis.GuiCompendiumIndex;
import am2.lore.ArcaneCompendium;
import am2.lore.CompendiumEntry;
import am2.lore.CompendiumEntryRitual;
import am2.lore.CompendiumEntrySpellComponent;
import am2.lore.CompendiumEntrySpellModifier;
import am2.lore.CompendiumEntrySpellShape;
import am2.lore.CompendiumEntryType;
import am2.playerextensions.AffinityData;
import am2.playerextensions.ExtendedProperties;
import am2.playerextensions.SkillData;
import am2.spell.SkillManager;
import am2.spell.SkillTreeManager;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Hybrid Dark-Fantasy + Anime Grimoire index of the Arcane Compendium:
 * - Instant in-book PT-BR <-> EN-US language switcher (no resource pack reload!)
 * - Dedicated scrollable "Jornada" tab with 4 real progression tracks (Mage, Etherium, 10 Guardians, Archmage)
 * - Dedicated "Rituais" tab with "? Como fazer" button, real spell icons and reagent previews
 * - Quick Affinity filter bar on Components & Rituals tabs
 * - Mage Signature card at bottom of sidebar (Level, Dominant Affinity %, Skill Points, Max Mana)
 * - Smart ranked search (Title > Affinity > Description, accent-insensitive)
 */
@SideOnly(Side.CLIENT)
public class GuiGrimoire extends GuiScreen {
	private static final int BG = 0x11152A, PANEL = 0x171D3A, PANEL_LIGHT = 0x242C5E, BORDER = 0x3D4C8A;
	private static final int TEXT = 0xEEF1FF, MUTED = 0x909AC9, GOLD = 0xFFE08C, CYAN = 0x7FE3FF;
	private static final int CARD_H = 32, CARD_GAP = 4, CARD_MIN_W = 112, HEADER_H = 18, FILTER_H = 16, ROW_H = 14, SIG_H = 26;
	private static final RenderItem ITEMS = new RenderItem();
	private static final String CAT_GUIDES = "guides";
	private static final String CAT_JOURNEY = "journey";
	private static final String CAT_COMPONENTS = "components";
	private static final String CAT_STRUCTURES = "structures";
	private static final String CAT_RITUALS = "rituals";
	private static final CompendiumEntryType JOURNEY_TAB = new CompendiumEntryType(CAT_JOURNEY, "journey", "Journey", 0, CompendiumEntry.class);
	private static final CompendiumEntryType RITUALS_TAB = new CompendiumEntryType(CAT_RITUALS, "ritual", "Rituals", 9, CompendiumEntryRitual.class);
	private static final Affinity[] FILTER_AFFINITIES = {
			Affinity.ARCANE, Affinity.FIRE, Affinity.WATER, Affinity.AIR, Affinity.EARTH,
			Affinity.LIGHTNING, Affinity.ICE, Affinity.NATURE, Affinity.LIFE, Affinity.ENDER
	};

	private static final class Card {
		CompendiumEntry entry;
		ItemStack icon;
		boolean iconFailed;
		int color = CYAN;
		Affinity affinity;
		List<String> lines;
		boolean cut;
		float hover;
		float shimmer = -1f;
		long born;
	}

	private static final class Spark {
		float x, y, vx, vy, life, max, size;
		int color;
		boolean star;
	}

	private static final class AmbientMote {
		float x, y, vx, vy, phase, size;
		boolean star;
	}

	private final GrimoireState state;
	private final GrimoirePrefs prefs = GrimoirePrefs.get();
	private final GrimoireJourney journey = new GrimoireJourney();
	private final List<CompendiumEntryType> categories = new ArrayList<CompendiumEntryType>();
	private final List<Boolean> categoryNew = new ArrayList<Boolean>();
	private final List<Affinity> availableAffinities = new ArrayList<Affinity>();
	private final List<Card> cards = new ArrayList<Card>();
	private final List<Spark> sparks = new ArrayList<Spark>();
	private final List<AmbientMote> motes = new ArrayList<AmbientMote>();
	private final Random random = new Random();

	private GuiTextField search;
	private SkillData skills;
	private Affinity activeAffinityFilter = null;
	private int left, top, w, h;
	private int bodyY, bodyH, sideW, sideListH, gridX, gridW, detailY;
	private int columns = 1, cardW = CARD_MIN_W;
	private int sideScroll, hidden;
	private float scrollShown;
	private int journeyScroll;
	private float journeyScrollShown;
	private int themeColor = CYAN;
	private long openedAt, selectedAt, pageTurnAt, lastFrame, emitAt;

	public GuiGrimoire(GrimoireState saved) {
		state = saved == null ? new GrimoireState() : saved.copy();
	}

	// ------------------------------------------------------------------ setup

	@Override
	public void initGui() {
		Keyboard.enableRepeatEvents(true);
		skills = SkillData.For(mc.thePlayer);
		if (openedAt == 0) openedAt = lastFrame = net.minecraft.client.Minecraft.getSystemTime();

		w = Math.min(width - 8, 444);
		h = Math.min(height - 8, 268);
		left = (width - w) / 2;
		top = (height - h) / 2;
		detailY = top + h - 55;
		bodyY = top + 25;
		bodyH = detailY - bodyY - 4;
		sideW = w < 360 ? 78 : 98;
		sideListH = bodyH - SIG_H - 3;
		gridX = left + sideW + 10;
		gridW = left + w - 6 - gridX;
		columns = Math.max(1, (gridW - 6 + CARD_GAP) / (CARD_MIN_W + CARD_GAP));
		cardW = (gridW - 9 - (columns - 1) * CARD_GAP) / columns;

		int searchX = left + 25 + fontRendererObj.getStringWidth(tr("am2.grimoire.title"));
		int searchW = Math.max(44, Math.min(144, left + w - 10 - toolbarWidth() - searchX - 6));
		search = new GuiTextField(fontRendererObj, searchX + 3, top + 9, searchW - 6, 10);
		search.setEnableBackgroundDrawing(false);
		search.setMaxStringLength(40);
		search.setText(state.search);

		categories.clear();
		categoryNew.clear();
		boolean addedJourney = false;
		for (CompendiumEntryType type : ArcaneCompendium.instance.getCategories()) {
			addCategoryIfVisible(type);
			if (CAT_GUIDES.equals(type.getCategoryName())) {
				categories.add(JOURNEY_TAB);
				categoryNew.add(false);
				addedJourney = true;
			}
			if (CAT_STRUCTURES.equals(type.getCategoryName())) {
				addCategoryIfVisible(RITUALS_TAB);
			}
		}
		if (!addedJourney) {
			categories.add(0, JOURNEY_TAB);
			categoryNew.add(0, false);
		}
		if (!hasCategory(state.category) && !categories.isEmpty()) state.category = categories.get(0).getCategoryName();
		themeColor = targetThemeColor();
		initMotes();
		rebuild(false);
		scrollShown = state.scroll;
		journeyScrollShown = journeyScroll;
	}

	private void addCategoryIfVisible(CompendiumEntryType type) {
		List<CompendiumEntry> visible = visibleIn(type.getCategoryName());
		if (visible.isEmpty()) return;
		categories.add(type);
		boolean fresh = false;
		for (CompendiumEntry entry : visible) fresh |= entry.isNew();
		categoryNew.add(fresh);
	}

	private void initMotes() {
		if (!motes.isEmpty()) return;
		for (int i = 0; i < 24; i++) {
			AmbientMote m = new AmbientMote();
			m.x = random.nextFloat();
			m.y = random.nextFloat();
			m.vx = (random.nextFloat() - 0.5f) * 0.014f;
			m.vy = -0.008f - random.nextFloat() * 0.018f;
			m.phase = random.nextFloat() * 6.283f;
			m.size = 1.1f + random.nextFloat() * 1.4f;
			m.star = (i % 4 == 0);
			motes.add(m);
		}
	}

	private boolean isJourneyView() {
		return state.search.trim().isEmpty() && CAT_JOURNEY.equals(state.category);
	}

	private boolean hasAffinityFilterBar() {
		return state.search.trim().isEmpty() && state.parentId == null
				&& (CAT_COMPONENTS.equals(state.category) || CAT_RITUALS.equals(state.category))
				&& !availableAffinities.isEmpty();
	}

	private int cardsTopOffset() {
		return HEADER_H + (hasAffinityFilterBar() ? FILTER_H : 0);
	}

	private int targetThemeColor() {
		if (isJourneyView()) return journey.selectedColor();
		if (activeAffinityFilter != null && hasAffinityFilterBar()) {
			return GrimoireDecor.brighten(activeAffinityFilter.color);
		}
		Card sel = selectedCard();
		if (sel != null) return sel.color;
		return state.category != null ? GrimoireDecor.categoryColor(state.category) : CYAN;
	}

	private boolean hasCategory(String name) {
		for (CompendiumEntryType type : categories)
			if (type.getCategoryName().equals(name)) return true;
		return false;
	}

	private List<CompendiumEntry> visibleIn(String category) {
		List<CompendiumEntry> out = new ArrayList<CompendiumEntry>();
		if (CAT_JOURNEY.equals(category)) return out;
		if (CAT_RITUALS.equals(category)) {
			Set<String> seenNames = new LinkedHashSet<String>();
			List<CompendiumEntry> raw = ArcaneCompendium.instance.getEntriesForCategory(CAT_STRUCTURES);
			for (CompendiumEntry entry : raw) {
				if (entry instanceof CompendiumEntryRitual && !entry.isLocked() && allowed(entry)) {
					if ("rituals_overview".equals(entry.getID())) continue;
					out.add(entry);
					if (entry.getName() != null) seenNames.add(fold(entry.getName()));
				}
			}
			for (CompendiumEntry entry : raw) {
				if (entry instanceof CompendiumEntryRitual && !entry.isLocked() && entry.hasSubItems()) {
					for (CompendiumEntry sub : entry.getSubItems()) {
						if (!allowed(sub)) continue;
						String key = sub.getName() != null ? fold(sub.getName()) : sub.getID();
						if (seenNames.add(key)) out.add(sub);
					}
				}
			}
			return out;
		}
		if (CAT_STRUCTURES.equals(category)) {
			for (CompendiumEntry entry : ArcaneCompendium.instance.getEntriesForCategory(CAT_STRUCTURES))
				if (!(entry instanceof CompendiumEntryRitual) && !entry.isLocked() && allowed(entry)) out.add(entry);
			return out;
		}
		for (CompendiumEntry entry : ArcaneCompendium.instance.getEntriesForCategory(category))
			if (!entry.isLocked() && allowed(entry)) out.add(entry);
		return out;
	}

	private boolean allowed(CompendiumEntry entry) {
		if (entry instanceof CompendiumEntrySpellComponent || entry instanceof CompendiumEntrySpellModifier || entry instanceof CompendiumEntrySpellShape) {
			ISkillTreeEntry part = SkillManager.instance.getSkill(entry.getID());
			if (part == null) return false;
			SkillTreeEntry ste = SkillTreeManager.instance.getSkillTreeEntry(part);
			if (ste == null) return false;
			return skills.isEntryKnown(ste) || skills.getLearnState(ste, mc.thePlayer) == LearnStates.CAN_LEARN;
		}
		return true;
	}

	private void rebuild(boolean resetScroll) {
		cards.clear();
		sparks.clear();
		availableAffinities.clear();
		hidden = 0;
		long now = net.minecraft.client.Minecraft.getSystemTime();
		if (resetScroll) {
			state.scroll = 0;
			scrollShown = 0;
			journeyScroll = 0;
			journeyScrollShown = 0;
			pageTurnAt = now;
		}
		if (isJourneyView()) {
			state.parentId = null;
			journeyScroll = clamp(journeyScroll, 0, maxJourneyScroll());
			return;
		}
		List<CompendiumEntry> list = new ArrayList<CompendiumEntry>();
		String query = fold(state.search.trim());
		CompendiumEntry parent = state.parentId == null ? null : ArcaneCompendium.instance.getEntry(state.parentId);
		if (!query.isEmpty()) {
			// Smart ranked search: Priority 1 = title, Priority 2 = affinity name, Priority 3 = description text!
			List<CompendiumEntry> tier1 = new ArrayList<CompendiumEntry>();
			List<CompendiumEntry> tier2 = new ArrayList<CompendiumEntry>();
			List<CompendiumEntry> tier3 = new ArrayList<CompendiumEntry>();
			Set<String> seen = new LinkedHashSet<String>();
			for (CompendiumEntryType type : categories) {
				for (CompendiumEntry entry : visibleIn(type.getCategoryName())) {
					collectSmartMatches(entry, query, tier1, tier2, tier3, seen);
				}
			}
			list.addAll(tier1);
			list.addAll(tier2);
			list.addAll(tier3);
		} else if (parent != null) {
			for (CompendiumEntry sub : parent.getSubItems())
				if (allowed(sub)) list.add(sub);
		} else {
			state.parentId = null;
			if (state.category != null) {
				List<CompendiumEntry> base = visibleIn(state.category);
				if (CAT_COMPONENTS.equals(state.category) || CAT_RITUALS.equals(state.category)) {
					Set<Affinity> present = new LinkedHashSet<Affinity>();
					for (CompendiumEntry e : base) {
						Affinity a = GrimoireDecor.affinityOf(e);
						if (a != null && a != Affinity.NONE) present.add(a);
					}
					for (Affinity a : FILTER_AFFINITIES) {
						if (present.contains(a)) availableAffinities.add(a);
					}
					if (activeAffinityFilter != null && !present.contains(activeAffinityFilter)) {
						activeAffinityFilter = null;
					}
				} else {
					activeAffinityFilter = null;
				}
				for (CompendiumEntry e : base) {
					if (activeAffinityFilter == null || activeAffinityFilter == GrimoireDecor.affinityOf(e)) {
						list.add(e);
					}
				}
				if (CAT_RITUALS.equals(state.category)) {
					for (CompendiumEntry entry : ArcaneCompendium.instance.getEntriesForCategory(CAT_STRUCTURES))
						if (entry instanceof CompendiumEntryRitual && entry.isLocked()) hidden++;
				} else if (CAT_STRUCTURES.equals(state.category)) {
					for (CompendiumEntry entry : ArcaneCompendium.instance.getEntriesForCategory(CAT_STRUCTURES))
						if (!(entry instanceof CompendiumEntryRitual) && entry.isLocked()) hidden++;
				} else {
					for (CompendiumEntry entry : ArcaneCompendium.instance.getEntriesForCategory(state.category))
						if (entry.isLocked()) hidden++;
				}
			}
		}
		for (int i = 0; i < list.size(); i++) {
			Card card = makeCard(list.get(i));
			card.born = now + Math.min(i, 14) * 18L;
			cards.add(card);
		}
		state.scroll = clamp(state.scroll, 0, maxScroll());
	}

	private void collectSmartMatches(CompendiumEntry entry, String query,
									 List<CompendiumEntry> tier1, List<CompendiumEntry> tier2,
									 List<CompendiumEntry> tier3, Set<String> seen) {
		if (seen.add(entry.getID())) {
			String name = entry.getName() != null ? fold(entry.getName()) : "";
			if (name.contains(query)) {
				tier1.add(entry);
			} else {
				Affinity aff = GrimoireDecor.affinityOf(entry);
				boolean affMatch = false;
				if (aff != null && aff != Affinity.NONE) {
					if (fold(aff.name()).contains(query) || fold(GrimoireLang.affinityName(aff)).contains(query)) {
						affMatch = true;
					}
				}
				if (affMatch) {
					tier2.add(entry);
				} else if (query.length() >= 3) {
					String desc = entry.getDescription();
					if (desc != null && fold(desc).contains(query)) {
						tier3.add(entry);
					}
				}
			}
		}
		for (CompendiumEntry sub : entry.getSubItems())
			if (allowed(sub)) collectSmartMatches(sub, query, tier1, tier2, tier3, seen);
	}

	private Card makeCard(CompendiumEntry entry) {
		Card card = new Card();
		card.entry = entry;
		String section = (entry instanceof CompendiumEntryRitual) ? CAT_RITUALS : entry.getParentSection();
		card.color = GrimoireDecor.categoryColor(section);
		try {
			card.affinity = GrimoireDecor.affinityOf(entry);
			if (card.affinity != null) card.color = GrimoireDecor.brighten(card.affinity.color);
			String id = entry.getID();
			int meta = -1;
			int at = id.indexOf('@');
			if (at > -1) {
				try {
					meta = Integer.parseInt(id.substring(at + 1));
				} catch (NumberFormatException ignored) {
				}
				id = id.substring(0, at);
			}
			card.icon = entry.getRepresentItemStack(id, meta);
		} catch (Throwable t) {
			card.icon = null;
		}
		String name = entry.getName() == null ? entry.getID() : entry.getName();
		List<String> lines = wrap(name, cardW - 36);
		if (lines.size() > 3) {
			card.cut = true;
			lines = new ArrayList<String>(lines.subList(0, 3));
			lines.set(2, fontRendererObj.trimStringToWidth(lines.get(2), cardW - 46) + "...");
		}
		card.lines = lines;
		return card;
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void drawScreen(int mouseX, int mouseY, float partial) {
		long now = net.minecraft.client.Minecraft.getSystemTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		float timeSec = (now % 3600000L) / 1000f;

		themeColor = prefs.effects ? GrimoireDraw.mix(themeColor, targetThemeColor(), Math.min(1f, dt * 6f)) : targetThemeColor();

		drawDefaultBackground();
		GrimoireDraw.resetBatch();
		GrimoireDraw.lite = !prefs.effects;

		float openT = prefs.effects ? Math.min(1f, (now - openedAt) / 240f) : 1f;
		float openEase = 1f - (1f - openT) * (1f - openT) * (1f - openT);
		float winScale = 0.96f + 0.04f * openEase;
		boolean scaling = winScale < 0.999f;

		GL11.glPushMatrix();
		if (scaling) {
			float cx = left + w * 0.5f;
			float cy = top + h * 0.5f;
			GL11.glTranslatef(cx, cy, 0);
			GL11.glScalef(winScale, winScale, 1f);
			GL11.glTranslatef(-cx, -cy, 0);
		}

		GrimoireDraw.grimoireBookFrame(left, top, w, h, 0, themeColor, timeSec, prefs.effects);
		if (prefs.effects) drawAmbientMotes(dt, timeSec);

		List<String> tooltip = drawToolbar(mouseX, mouseY, timeSec);
		drawSidebar(mouseX, mouseY, timeSec);
		List<String> mainTip = drawMainPanel(mouseX, mouseY, now, timeSec, dt);
		if (mainTip != null) tooltip = mainTip;
		drawDetail(mouseX, mouseY, now, timeSec);
		super.drawScreen(mouseX, mouseY, partial);

		GL11.glPopMatrix();

		if (tooltip != null) drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glColor4f(1, 1, 1, 1);
	}

	private void drawAmbientMotes(float dt, float timeSec) {
		for (AmbientMote m : motes) {
			m.x += m.vx * dt;
			m.y += m.vy * dt;
			if (m.y < 0.02f) {
				m.y = 0.98f;
				m.x = random.nextFloat();
			}
			if (m.x < 0.02f) m.x = 0.98f;
			if (m.x > 0.98f) m.x = 0.02f;
			float tw = 0.45f + 0.55f * (float) Math.sin(timeSec * 2.2f + m.phase);
			float px = left + 8 + m.x * (w - 16);
			float py = top + 8 + m.y * (h - 16);
			int col = m.star ? GrimoireDraw.mix(themeColor, GOLD, 0.5f) : themeColor;
			if (m.star) {
				GrimoireDraw.star(px, py, m.size * 1.4f, col, 0.24f * tw, timeSec * 25f + m.phase * 40f);
			} else {
				GrimoireDraw.glow(px, py, m.size * 2.1f, col, 0.16f * tw);
			}
		}
	}

	private String[] toolbarLabels() {
		return new String[]{
				prefs.isPortuguese() ? "PT" : "EN",
				tr(prefs.effects ? "am2.grimoire.effectsOn" : "am2.grimoire.effectsOff"),
				tr("am2.grimoire.classic")};
	}

	private int toolbarWidth() {
		int total = 0;
		for (String label : toolbarLabels()) total += fontRendererObj.getStringWidth(label) + 12;
		return total;
	}

	/** 0 = PT/EN language toggle, 1 = effects toggle, 2 = classic book. */
	private int toolbar(int mouseX, int mouseY, boolean draw) {
		String[] labels = toolbarLabels();
		int x = left + w - 6 - toolbarWidth();
		int found = -1;
		for (int i = 0; i < labels.length; i++) {
			int bw = fontRendererObj.getStringWidth(labels[i]) + 9;
			boolean over = inside(mouseX, mouseY, x, top + 6, bw, 14);
			if (over) found = i;
			if (draw) {
				boolean active = (i == 0) || (i == 1 && prefs.effects);
				int accent = (i == 0) ? CYAN : (i == 2 ? GOLD : themeColor);
				GrimoireDraw.arcaneButton(x, top + 6, bw, 14, accent, active, over);
				fontRendererObj.drawStringWithShadow(labels[i], x + 5, top + 9, over ? accent : active ? TEXT : MUTED);
			}
			x += bw + 3;
		}
		return found;
	}

	private List<String> drawToolbar(int mouseX, int mouseY, float timeSec) {
		float crestX = left + 13;
		float crestY = top + 12.5f;
		if (prefs.effects) {
			GrimoireDraw.ring(crestX, crestY, 6.2f, 0.8f, GOLD, 0.55f, timeSec * 0.7f, 6, 0.5f, true);
		}
		GrimoireDecor.rune(5, crestX, crestY, 9, GOLD, 0.95f, true);
		fontRendererObj.drawStringWithShadow(tr("am2.grimoire.title"), left + 22, top + 9, GOLD);

		int sx = search.xPosition - 3;
		int sy = top + 6;
		int sw = search.width + 6;
		if (search.isFocused() && prefs.effects) {
			GrimoireDraw.glow(sx + sw * 0.5f, sy + 7, sw * 0.45f, themeColor, 0.14f);
		}
		GrimoireDraw.panel(sx, sy, sw, 14, 0x0C1022, search.isFocused() ? themeColor : BORDER);
		if (search.getText().isEmpty() && !search.isFocused())
			fontRendererObj.drawString(fontRendererObj.trimStringToWidth(tr("am2.grimoire.search"), search.width), search.xPosition, search.yPosition, MUTED);
		search.drawTextBox();
		int overTool = toolbar(mouseX, mouseY, true);
		if (overTool == 0) {
			return Collections.singletonList(prefs.isPortuguese() ? "Idioma do Livro: Português (clique para EN)" : "Book Language: English (click for PT-BR)");
		}
		return null;
	}

	private int sideRowAt(int mouseX, int mouseY) {
		if (!inside(mouseX, mouseY, left + 7, bodyY + 1, sideW, sideListH - 2)) return -1;
		int row = (mouseY - bodyY - 2 + sideScroll) / ROW_H;
		return row >= 0 && row < categories.size() ? row : -1;
	}

	private void drawSidebar(int mouseX, int mouseY, float timeSec) {
		// Upper categories list
		GrimoireDraw.panel(left + 7, bodyY, sideW, sideListH, PANEL, 0x2C3768);
		sideScroll = clamp(sideScroll, 0, Math.max(0, categories.size() * ROW_H + 4 - sideListH));
		int over = sideRowAt(mouseX, mouseY);
		scissor(left + 7, bodyY + 1, sideW, sideListH - 2);
		for (int i = 0; i < categories.size(); i++) {
			CompendiumEntryType type = categories.get(i);
			int y = bodyY + 2 + i * ROW_H - sideScroll;
			boolean active = state.search.trim().isEmpty() && type.getCategoryName().equals(state.category);
			int color = GrimoireDecor.categoryColor(type.getCategoryName());
			if (active) {
				GrimoireDraw.hGrad(left + 9, y, sideW - 3, 13, color, 0.28f, PANEL_LIGHT, 0.08f, true);
				GrimoireDraw.rect(left + 9, y, 2, 13, GrimoireDraw.GOLD_HI, 0.95f);
				GrimoireDraw.rect(left + 9, y, sideW - 5, 1, color, 0.45f);
				GrimoireDraw.rect(left + 9, y + 12, sideW - 5, 1, color, 0.35f);
			} else if (over == i) {
				GrimoireDraw.hGrad(left + 9, y, sideW - 4, 13, color, 0.18f, color, 0.02f, true);
				GrimoireDraw.rect(left + 9, y, 2, 13, color, 0.85f);
			}
			int shift = (active || over == i) ? 2 : 0;
			int glyphVariant = CAT_JOURNEY.equals(type.getCategoryName()) ? 5 : ((i * 3 + 3) & 15);
			if (glyphVariant == 2) glyphVariant = 7;
			GrimoireDecor.rune(glyphVariant, left + 17 + (active ? 1 : 0), y + 6.5f, 9, color,
					active || over == i ? 1f : 0.62f, active || over == i);
			if (over == i || active) {
				GrimoireDraw.ring(left + 17 + (active ? 1 : 0), y + 6.5f, 6.4f, 0.85f, color, 0.85f,
						prefs.effects ? timeSec * 1.8f : 0, 4, 0.58f, true);
			}
			String catLabel = GrimoireLang.categoryLabel(type.getCategoryName(), type.getCategoryLabel());
			fontRendererObj.drawString(fontRendererObj.trimStringToWidth(catLabel, sideW - 26),
					left + 25 + shift, y + 3, active || over == i ? TEXT : MUTED);
			if (categoryNew.get(i)) {
				GrimoireDraw.waxSeal(left + sideW + 1, y + 6.5f, 3.0f, timeSec, prefs.effects);
			}
		}
		GL11.glDisable(GL11.GL_SCISSOR_TEST);

		// Bottom "Mage Signature" card (personal level, dominant affinity, skill points & max mana)
		drawMageSignature(left + 7, bodyY + sideListH + 3, sideW, SIG_H);
	}

	private void drawMageSignature(int x, int y, int sw, int sh) {
		ExtendedProperties props = mc.thePlayer != null ? ExtendedProperties.For(mc.thePlayer) : null;
		AffinityData affData = mc.thePlayer != null ? AffinityData.For(mc.thePlayer) : null;
		int level = props != null ? props.getMagicLevel() : 0;
		float maxMana = props != null ? props.getMaxMana() : 0f;

		Affinity dom = Affinity.NONE;
		float domDepth = 0f;
		if (affData != null) {
			for (Affinity a : FILTER_AFFINITIES) {
				float d = affData.getAffinityDepth(a);
				if (d > domDepth) {
					domDepth = d;
					dom = a;
				}
			}
		}
		int domColor = (dom != Affinity.NONE && domDepth >= 0.05f) ? GrimoireDecor.brighten(dom.color) : CYAN;

		GrimoireDraw.panel(x, y, sw, sh, 0x121730, 0x35447A);
		GrimoireDraw.rect(x + 1, y + 2, 2, sh - 4, domColor, 0.9f);
		GrimoireDraw.hGrad(x + 3, y + 1, sw - 5, sh - 2, domColor, 0.12f, domColor, 0f, true);

		// Row 1: Level + Dominant Affinity %
		String lvStr = (prefs.isPortuguese() ? "Nv." : "Lv.") + level;
		String affStr = (dom != Affinity.NONE && domDepth >= 0.05f)
				? (GrimoireLang.affinityName(dom) + " " + Math.round(domDepth * 100f) + "%")
				: (formatMana(maxMana) + " MP");
		GL11.glPushMatrix();
		GL11.glScalef(0.75f, 0.75f, 1f);
		fontRendererObj.drawStringWithShadow(lvStr, (int) ((x + 6) / 0.75f), (int) ((y + 4) / 0.75f), GOLD);
		int aw = fontRendererObj.getStringWidth(affStr);
		fontRendererObj.drawString(fontRendererObj.trimStringToWidth(affStr, (int) ((sw - 34) / 0.75f)),
				(int) ((x + sw - 4) / 0.75f) - Math.min(aw, (int) ((sw - 34) / 0.75f)), (int) ((y + 4) / 0.75f), domColor);
		GL11.glPopMatrix();

		// Row 2: Blue / Green / Red Skill Points + Max Mana
		int bp = skills != null ? skills.getSpellPoints(SkillPointTypes.BLUE) : 0;
		int gp = skills != null ? skills.getSpellPoints(SkillPointTypes.GREEN) : 0;
		int rp = skills != null ? skills.getSpellPoints(SkillPointTypes.RED) : 0;
		int ry = y + 15;
		GL11.glPushMatrix();
		GL11.glScalef(0.72f, 0.72f, 1f);
		int px = (int) ((x + 6) / 0.72f);
		int py = (int) (ry / 0.72f);
		String bTxt = "\u25CF" + bp;
		String gTxt = "\u25CF" + gp;
		String rTxt = "\u25CF" + rp;
		fontRendererObj.drawString(bTxt, px, py, 0x5EA8FF);
		px += fontRendererObj.getStringWidth(bTxt) + 4;
		fontRendererObj.drawString(gTxt, px, py, 0x5CE67A);
		px += fontRendererObj.getStringWidth(gTxt) + 4;
		fontRendererObj.drawString(rTxt, px, py, 0xFF6666);

		String manaTxt = formatMana(maxMana);
		int mw = fontRendererObj.getStringWidth(manaTxt);
		fontRendererObj.drawString(manaTxt, (int) ((x + sw - 4) / 0.72f) - mw, py, CYAN);
		GL11.glPopMatrix();
	}

	private static String formatMana(float mana) {
		if (mana >= 10000f) return String.format(Locale.ROOT, "%.0fk", mana / 1000f);
		if (mana >= 1000f) return String.format(Locale.ROOT, "%.1fk", mana / 1000f);
		return String.valueOf((int) mana);
	}

	private float cardX(int index) {
		return gridX + 3 + (index % columns) * (cardW + CARD_GAP);
	}

	private float cardY(int index) {
		return bodyY + cardsTopOffset() + (index / columns) * (CARD_H + CARD_GAP) - scrollShown;
	}

	private int cardAt(int mouseX, int mouseY) {
		if (isJourneyView()) return -1;
		int topOff = cardsTopOffset();
		if (!inside(mouseX, mouseY, gridX, bodyY + topOff - 2, gridW, bodyH - topOff + 1)) return -1;
		for (int i = 0; i < cards.size(); i++)
			if (inside(mouseX, mouseY, (int) cardX(i), Math.round(cardY(i)), cardW, CARD_H)) return i;
		return -1;
	}

	private int maxScroll() {
		if (isJourneyView()) return 0;
		int rows = (cards.size() + columns - 1) / columns;
		return Math.max(0, rows * (CARD_H + CARD_GAP) - (bodyH - cardsTopOffset() - 2));
	}

	private int maxJourneyScroll() {
		return Math.max(0, journey.contentHeight() - (bodyH - HEADER_H - 2));
	}

	private String headerText() {
		if (!state.search.trim().isEmpty()) return tr("am2.grimoire.results") + " (" + cards.size() + ")";
		if (state.parentId != null) {
			CompendiumEntry parent = ArcaneCompendium.instance.getEntry(state.parentId);
			if (parent != null) return "< " + parent.getName();
		}
		for (CompendiumEntryType type : categories)
			if (type.getCategoryName().equals(state.category))
				return GrimoireLang.categoryLabel(type.getCategoryName(), type.getCategoryLabel());
		return "";
	}

	private boolean overBack(int mouseX, int mouseY) {
		return state.parentId != null && state.search.trim().isEmpty() && inside(mouseX, mouseY, gridX, bodyY, gridW, HEADER_H - 3);
	}

	private boolean hasRitualGuideButton() {
		return state.search.trim().isEmpty() && state.parentId == null && CAT_RITUALS.equals(state.category)
				&& ArcaneCompendium.instance.getEntry("rituals_overview") != null;
	}

	private boolean ritualGuideButton(int mouseX, int mouseY, boolean draw) {
		if (!hasRitualGuideButton()) return false;
		String label = tr("am2.grimoire.ritualGuide");
		int bw = fontRendererObj.getStringWidth(label) + 8;
		int bx = gridX + gridW - 6 - bw;
		int by = bodyY + 2;
		boolean over = inside(mouseX, mouseY, bx, by, bw, 12);
		if (draw) {
			GrimoireDraw.arcaneButton(bx, by, bw, 12, GOLD, false, over);
			fontRendererObj.drawString(label, bx + 4, by + 2, over ? GOLD : TEXT);
		}
		return over;
	}

	/** Draws / hit-tests the Affinity filter bar. Returns index -1=none, 0=All, 1..n = availableAffinities[idx-1]. */
	private int affinityFilterBar(int mouseX, int mouseY, boolean draw) {
		if (!hasAffinityFilterBar()) return -1;
		int y = bodyY + HEADER_H - 2;
		int x = gridX + 5;
		int found = -1;

		String allLabel = tr("am2.grimoire.filterAll");
		int allW = fontRendererObj.getStringWidth(allLabel) + 8;
		boolean overAll = inside(mouseX, mouseY, x, y, allW, 13);
		if (overAll) found = 0;
		if (draw) {
			GrimoireDraw.arcaneButton(x, y, allW, 13, GOLD, activeAffinityFilter == null, overAll);
			fontRendererObj.drawString(allLabel, x + 4, y + 3, activeAffinityFilter == null ? GOLD : (overAll ? TEXT : MUTED));
		}
		x += allW + 4;

		for (int i = 0; i < availableAffinities.size(); i++) {
			Affinity aff = availableAffinities.get(i);
			if (x + 14 > gridX + gridW - 6) break;
			boolean over = inside(mouseX, mouseY, x, y, 13, 13);
			if (over) found = i + 1;
			if (draw) {
				boolean sel = (activeAffinityFilter == aff);
				int col = GrimoireDecor.brighten(aff.color);
				if (sel || over) {
					GrimoireDraw.glow(x + 6.5f, y + 6.5f, 9f, col, sel ? 0.35f : 0.22f);
				}
				GrimoireDraw.ring(x + 6.5f, y + 6.5f, 6.2f, 6.2f, sel ? GrimoireDraw.mix(0x161B38, col, 0.3f) : 0x0E1226, 0.92f, 0, 1, 1, false);
				GrimoireDraw.ring(x + 6.5f, y + 6.5f, 6.4f, sel ? 1.2f : 0.8f, col, sel ? 1f : (over ? 0.9f : 0.45f), 0, 1, 1, sel || over);
				ItemStack ess = GrimoireDecor.essenceForAffinity(aff);
				if (ess != null) {
					drawIcon(ess, "", col, false, null, x + 6.5f, y + 6.5f, 0.56f);
				}
			}
			x += 15;
		}
		return found;
	}

	private List<String> drawMainPanel(int mouseX, int mouseY, long now, float timeSec, float dt) {
		GrimoireDraw.panel(gridX, bodyY, gridW, bodyH, PANEL, 0x2C3768);
		GrimoireDraw.hGrad(gridX + 4, bodyY + HEADER_H - 4, (gridW - 8) * 0.65f, 1, themeColor, 0.42f, themeColor, 0f, true);

		boolean showGuideBtn = hasRitualGuideButton();
		String missing = (!showGuideBtn && hidden > 0) ? GrimoireLang.trFmt("am2.grimoire.undiscovered", hidden) : "";
		int rightReserved = showGuideBtn ? (fontRendererObj.getStringWidth(tr("am2.grimoire.ritualGuide")) + 14) : fontRendererObj.getStringWidth(missing);
		fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(headerText(), gridW - 18 - rightReserved), gridX + 6, bodyY + 4,
				overBack(mouseX, mouseY) ? CYAN : GOLD);
		if (showGuideBtn) {
			ritualGuideButton(mouseX, mouseY, true);
		} else if (!missing.isEmpty()) {
			fontRendererObj.drawString(missing, gridX + gridW - 6 - rightReserved, bodyY + 4, MUTED);
		}

		// Central rotating astrolabe mandala softly visible through the translucent cards/tracks
		scissor(gridX + 1, bodyY + 1, gridW - 2, bodyH - 2);
		GrimoireDecor.arcaneMandala(gridX + gridW * 0.5f, bodyY + HEADER_H + (bodyH - HEADER_H) * 0.5f,
				Math.min(1.04f, (bodyH - 12) / 142f), themeColor, 0.36f, timeSec, prefs.effects, true);
		GL11.glDisable(GL11.GL_SCISSOR_TEST);

		if (isJourneyView()) {
			journeyScrollShown = prefs.effects ? journeyScrollShown + (journeyScroll - journeyScrollShown) * Math.min(1f, dt * 16f) : journeyScroll;
			int jH = bodyH - HEADER_H - 1;
			scissor(gridX + 1, bodyY + HEADER_H - 2, gridW - 2, jH + 1);
			List<String> jTip = journey.draw(mc, fontRendererObj, ITEMS, mc.thePlayer, skills,
					gridX + 2, bodyY + HEADER_H - 1, gridW - 7, jH, journeyScrollShown,
					mouseX, mouseY, now, timeSec, prefs.effects);
			int jMax = maxJourneyScroll();
			if (jMax > 0 && journeyScrollShown < jMax - 2f) {
				float fadeA = Math.min(0.86f, (jMax - journeyScrollShown) / 18f);
				GrimoireDraw.vGrad(gridX + 1, bodyY + bodyH - 15, gridW - 6, 14, 0x10142A, 0f, 0x0C1022, fadeA, false);
			}
			if (prefs.effects && pageTurnAt > 0 && now - pageTurnAt < 260) {
				GrimoireDraw.pageTurnSweep(gridX + 1, bodyY + HEADER_H - 2, gridW - 2, jH + 1,
						(now - pageTurnAt) / 260f, themeColor);
			}
			GL11.glDisable(GL11.GL_SCISSOR_TEST);
			if (jMax > 0) {
				int track = bodyH - HEADER_H - 2;
				int thumb = Math.max(14, track * track / (track + jMax));
				float ty = bodyY + HEADER_H + (track - thumb) * Math.min(1f, journeyScrollShown / jMax);
				GrimoireDraw.rect(gridX + gridW - 4, bodyY + HEADER_H, 2, track, 0x0C1022, 0.7f);
				GrimoireDraw.vGrad(gridX + gridW - 4, ty, 2, thumb, themeColor, 0.85f, BORDER, 0.85f, false);
			}
			return jTip;
		}

		List<String> tip = null;
		int overFilter = affinityFilterBar(mouseX, mouseY, true);
		if (overFilter > 0 && overFilter <= availableAffinities.size()) {
			tip = Collections.singletonList(GrimoireLang.affinityName(availableAffinities.get(overFilter - 1)));
		}

		scrollShown = prefs.effects ? scrollShown + (state.scroll - scrollShown) * Math.min(1, dt * 16) : state.scroll;
		int topOff = cardsTopOffset();
		if (cards.isEmpty())
			fontRendererObj.drawSplitString(tr("am2.grimoire.empty"), gridX + 8, bodyY + topOff + 6, gridW - 16, MUTED);

		int over = cardAt(mouseX, mouseY);
		scissor(gridX + 1, bodyY + topOff - 2, gridW - 2, bodyH - topOff + 1);
		Card selected = null;
		float selX = 0, selY = 0;
		for (int i = 0; i < cards.size(); i++) {
			Card card = cards.get(i);
			float target = over == i ? 1 : 0;
			boolean entered = target > 0.5f && card.hover < 0.2f;
			card.hover = prefs.effects ? card.hover + (target - card.hover) * Math.min(1, dt * 14) : target;
			if (entered && prefs.effects) card.shimmer = 0f;
			if (card.shimmer >= 0f) {
				card.shimmer += dt * 2.1f;
				if (card.shimmer > 1.25f) card.shimmer = -1f;
			}
			float appear = prefs.effects ? Math.max(0, Math.min(1, (now - card.born) / 180f)) : 1;
			float easeAppear = 1f - (1f - appear) * (1f - appear);
			float x = cardX(i) + (1 - easeAppear) * 14;
			float y = cardY(i);
			if (y > bodyY + bodyH || y + CARD_H < bodyY || appear <= 0) continue;
			boolean isSelected = card.entry.getID().equals(state.selectedId);
			float lift = prefs.effects ? card.hover * 2f : 0;
			drawCard(card, x, y - lift, isSelected, timeSec);
			if (isSelected) {
				selected = card;
				selX = x + 16;
				selY = y + CARD_H / 2f - lift;
			}
			if (over == i) {
				if (card.cut) tip = Collections.singletonList(card.entry.getName());
				if (prefs.effects && now >= emitAt) {
					emit(card, x, y);
					emitAt = now + 62;
				}
			}
		}
		updateSparks(dt, timeSec);
		if (selected != null && prefs.effects && now - selectedAt < 520) {
			float t = (now - selectedAt) / 520f;
			GrimoireDraw.ring(selX, selY, 12 + t * 26, 2.0f, selected.color, (1 - t) * 0.9f, 0, 1, 1, true);
			GrimoireDraw.ring(selX, selY, 8 + t * 18, 1.2f, GOLD, (1 - t) * 0.7f, t * 2f, 6, 0.55f, true);
		}
		int max = maxScroll();
		if (max > 0 && scrollShown < max - 2f) {
			float fadeAlpha = Math.min(0.88f, (max - scrollShown) / 18f);
			GrimoireDraw.vGrad(gridX + 1, bodyY + bodyH - 16, gridW - 6, 15, 0x10142A, 0f, 0x0C1022, fadeAlpha, false);
		}
		if (prefs.effects && pageTurnAt > 0 && now - pageTurnAt < 260) {
			float pt = (now - pageTurnAt) / 260f;
			GrimoireDraw.pageTurnSweep(gridX + 1, bodyY + topOff - 2, gridW - 2, bodyH - topOff + 1, pt, themeColor);
		}
		GL11.glDisable(GL11.GL_SCISSOR_TEST);

		if (max > 0) {
			int track = bodyH - topOff - 2;
			int thumb = Math.max(12, track * track / (track + max));
			float ty = bodyY + topOff + (track - thumb) * Math.min(1, scrollShown / max);
			GrimoireDraw.rect(gridX + gridW - 4, bodyY + topOff, 2, track, 0x0C1022, 0.7f);
			GrimoireDraw.vGrad(gridX + gridW - 4, ty, 2, thumb, themeColor, 0.85f, BORDER, 0.85f, false);
		}
		return tip;
	}

	private void drawCard(Card card, float x, float y, boolean selected, float timeSec) {
		float hover = card.hover;
		GL11.glPushMatrix();
		GL11.glTranslatef(x, y, 0);

		int topBg = selected ? GrimoireDraw.mix(PANEL_LIGHT, card.color, 0.18f) : 0x131832;
		int botBg = selected ? 0x151B3A : 0x0C1023;
		float topAlpha = selected ? 0.90f : (0.72f + 0.14f * hover);
		float botAlpha = selected ? 0.94f : (0.78f + 0.12f * hover);
		GrimoireDraw.batch();
		try {
		GrimoireDraw.vGrad(0, 0, cardW, CARD_H, topBg, topAlpha, botBg, botAlpha, false);
		GrimoireDraw.rect(1, 2, 2, CARD_H - 4, card.color, selected ? 1.0f : (0.52f + 0.48f * hover));

		if (hover > 0.01f || selected) {
			float glowAmt = selected ? 0.20f : (0.14f * hover);
			GrimoireDraw.hGrad(1, 1, cardW - 2, CARD_H - 2, card.color, glowAmt, card.color, 0.02f, true);
		}

		if (card.shimmer >= 0f && prefs.effects) {
			float sx = card.shimmer * (cardW + 24) - 24;
			float sw = 24f;
			float cl0 = Math.max(1f, sx);
			float cl1 = Math.min(cardW - 1f, sx + sw);
			if (cl1 > cl0) {
				float mid = (cl0 + cl1) * 0.5f;
				GrimoireDraw.hGrad(cl0, 1, mid - cl0, CARD_H - 2, 0xFFFFFF, 0f, card.color, 0.24f, true);
				GrimoireDraw.hGrad(mid, 1, cl1 - mid, CARD_H - 2, card.color, 0.24f, 0xFFFFFF, 0f, true);
			}
		}

		GrimoireDraw.rect(0, 0, cardW, 1, 0x2B345E, 0.82f);
		GrimoireDraw.rect(0, CARD_H - 1, cardW, 1, 0x1E2546, 0.82f);
		GrimoireDraw.rect(0, 0, 1, CARD_H, 0x252E54, 0.82f);
		GrimoireDraw.rect(cardW - 1, 0, 1, CARD_H, 0x252E54, 0.82f);
		GrimoireDraw.rect(1, 1, 1, 1, GrimoireDraw.GOLD_MID, 0.65f);
		GrimoireDraw.rect(cardW - 2, 1, 1, 1, GrimoireDraw.GOLD_MID, 0.65f);
		GrimoireDraw.rect(1, CARD_H - 2, 1, 1, GrimoireDraw.GOLD_LO, 0.65f);
		GrimoireDraw.rect(cardW - 2, CARD_H - 2, 1, 1, GrimoireDraw.GOLD_LO, 0.65f);

		float edge = selected ? 0.95f : hover * 0.85f;
		if (edge > 0.02f) {
			GrimoireDraw.rect(0, 0, cardW, 1, card.color, edge);
			GrimoireDraw.rect(0, CARD_H - 1, cardW, 1, card.color, edge * 0.75f);
			GrimoireDraw.rect(0, 0, 1, CARD_H, card.color, edge);
			GrimoireDraw.rect(cardW - 1, 0, 1, CARD_H, card.color, edge * 0.85f);
			if (selected) GrimoireDraw.rect(cardW - 2, 2, 1, CARD_H - 4, GOLD, 0.9f);
			float expand = selected ? 0f : (1f - hover) * 3.5f;
			GrimoireDraw.brackets(-expand, -expand, cardW + expand * 2f, CARD_H + expand * 2f, 5.5f,
					selected ? GOLD : card.color, selected ? 0.95f : hover * 0.9f);
		}
		} finally {
			GrimoireDraw.endBatch();
		}

		GrimoireDecor.cardSeal(16, 16, card.color, hover, selected, timeSec, prefs.effects);
		drawIcon(card.icon, card.entry.getName(), card.color, card.iconFailed, card, 16, 16, prefs.effects ? 1 + hover * 0.18f : 1);

		int textY = (CARD_H - card.lines.size() * 9) / 2 + 1;
		for (String line : card.lines) {
			fontRendererObj.drawString(line, 33, textY, selected || hover > 0.45f ? TEXT : MUTED);
			textY += 9;
		}
		if (card.entry.isNew()) {
			GrimoireDraw.waxSeal(cardW - 6, 6, 3.0f, timeSec, prefs.effects);
		}
		GL11.glPopMatrix();
	}

	private void drawIcon(ItemStack icon, String fallbackName, int fallbackColor, boolean iconFailed, Card owner, float cx, float cy, float scale) {
		if (icon != null && icon.getItem() != null && !iconFailed) {
			GL11.glPushMatrix();
			GL11.glTranslatef(cx, cy, 0);
			GL11.glScalef(scale, scale, 1);
			RenderHelper.enableGUIStandardItemLighting();
			GL11.glEnable(GL12.GL_RESCALE_NORMAL);
			GL11.glEnable(GL11.GL_DEPTH_TEST);
			ITEMS.zLevel = 100;
			try {
				ITEMS.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), icon, -8, -8);
			} catch (Throwable t) {
				if (owner != null) owner.iconFailed = true;
			}
			RenderHelper.disableStandardItemLighting();
			GL11.glDisable(GL12.GL_RESCALE_NORMAL);
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			GL11.glEnable(GL11.GL_ALPHA_TEST);
			GL11.glColor4f(1, 1, 1, 1);
			GL11.glPopMatrix();
		} else {
			String letter = fallbackName == null || fallbackName.isEmpty() ? "?" : fallbackName.substring(0, 1);
			fontRendererObj.drawString(letter, (int) cx - fontRendererObj.getStringWidth(letter) / 2, (int) cy - 3, fallbackColor);
		}
	}

	private void emit(Card card, float x, float y) {
		Spark s = new Spark();
		s.color = card.color;
		s.size = 1.6f;
		s.max = s.life = 0.72f;
		s.x = x + 4 + random.nextFloat() * (cardW - 8);
		s.y = y + CARD_H / 2f;
		s.vx = (random.nextFloat() - 0.5f) * 12;
		s.vy = (random.nextFloat() - 0.5f) * 12;
		Affinity affinity = card.affinity;
		if (affinity == Affinity.FIRE) {
			s.y = y + CARD_H - 2;
			s.vy = -30 - random.nextFloat() * 20;
			s.color = random.nextBoolean() ? card.color : GOLD;
		} else if (affinity == Affinity.ICE || affinity == Affinity.WATER) {
			s.y = y + 2;
			s.vy = 18 + random.nextFloat() * 14;
			s.star = (affinity == Affinity.ICE);
		} else if (affinity == Affinity.LIGHTNING) {
			s.max = s.life = 0.19f;
			s.vx = (random.nextFloat() - 0.5f) * 230;
			s.vy = (random.nextFloat() - 0.5f) * 230;
			s.size = -1;
		} else if (affinity == Affinity.AIR) {
			s.x = x + 4;
			s.vx = 58 + random.nextFloat() * 34;
			s.vy = (random.nextFloat() - 0.5f) * 8;
		} else if (affinity == Affinity.EARTH) {
			s.size = 2.4f;
			s.y = y + 4;
			s.vy = 12;
		} else if (affinity == Affinity.NATURE || affinity == Affinity.LIFE) {
			s.y = y + CARD_H - 3;
			s.vx = (random.nextFloat() - 0.5f) * 34;
			s.vy = -16;
			s.size = 2.0f;
			s.star = true;
		} else {
			float angle = random.nextFloat() * 6.283f;
			s.x = x + 16 + (float) Math.cos(angle) * 14;
			s.y = y + 16 + (float) Math.sin(angle) * 14;
			s.vx = -(float) Math.sin(angle) * 22;
			s.vy = (float) Math.cos(angle) * 22;
			s.star = random.nextInt(3) == 0;
		}
		sparks.add(s);
	}

	private void burst(float x, float y, int color) {
		for (int i = 0; i < 16; i++) {
			Spark s = new Spark();
			double angle = i * Math.PI * 2 / 16 + random.nextDouble() * 0.2;
			float speed = 55f + random.nextFloat() * 35f;
			s.x = x;
			s.y = y;
			s.vx = (float) Math.cos(angle) * speed;
			s.vy = (float) Math.sin(angle) * speed;
			s.max = s.life = 0.42f + random.nextFloat() * 0.18f;
			s.size = 2.4f;
			s.color = (i % 3 == 0) ? GOLD : color;
			s.star = (i % 2 == 0);
			sparks.add(s);
		}
	}

	private void updateSparks(float dt, float timeSec) {
		for (Iterator<Spark> it = sparks.iterator(); it.hasNext(); ) {
			Spark s = it.next();
			s.life -= dt;
			if (s.life <= 0 || !prefs.effects) {
				it.remove();
				continue;
			}
			s.x += s.vx * dt;
			s.y += s.vy * dt;
			float fade = s.life / s.max;
			if (s.size < 0) {
				float mx = s.x + s.vy * 0.02f, my = s.y - s.vx * 0.02f;
				GrimoireDraw.line(s.x - s.vx * 0.04f, s.y - s.vy * 0.04f, mx, my, 1.3f, s.color, fade, true);
				GrimoireDraw.line(mx, my, s.x, s.y, 1.0f, 0xFFFFFF, fade, true);
			} else if (s.star) {
				GrimoireDraw.star(s.x, s.y, s.size * (0.5f + 0.7f * fade), s.color, fade * 0.95f, timeSec * 90f);
			} else {
				float size = s.size * (0.4f + 0.6f * fade);
				GrimoireDraw.glow(s.x, s.y, size * 1.8f, s.color, fade * 0.45f);
				GrimoireDraw.rect(s.x - size / 2, s.y - size / 2, size, size, s.color, fade);
			}
		}
	}

	// ------------------------------------------------------------------ detail panel

	private Card selectedCard() {
		if (isJourneyView()) return null;
		for (Card card : cards)
			if (card.entry.getID().equals(state.selectedId)) return card;
		return null;
	}

	private String actionLabel() {
		if (isJourneyView()) {
			GrimoireJourney.Step step = journey.selected();
			if (step == null) return null;
			CompendiumEntry guide = ArcaneCompendium.instance.getEntry(step.entryId);
			return guide != null ? tr("am2.grimoire.openGuide") : null;
		}
		Card card = selectedCard();
		if (card == null) return null;
		return tr(card.entry.hasSubItems() ? "am2.grimoire.items" : "am2.grimoire.open");
	}

	private boolean canReadParent() {
		Card card = selectedCard();
		return !isJourneyView() && card != null && card.entry.hasSubItems()
				&& card.entry.description != null && !card.entry.description.trim().isEmpty();
	}

	private int detailButtonWidth() {
		int widest = 44;
		for (String key : new String[]{"open", "openGuide", "items", "read"})
			widest = Math.max(widest, fontRendererObj.getStringWidth(tr("am2.grimoire." + key)));
		return widest + 14;
	}

	private int detailButton(int mouseX, int mouseY, boolean draw) {
		String main = actionLabel();
		if (main == null) return -1;
		String[] labels = canReadParent() ? new String[]{main, tr("am2.grimoire.read")} : new String[]{main};
		int found = -1;
		int bw = detailButtonWidth();
		for (int i = 0; i < labels.length; i++) {
			int x = left + w - 13 - bw;
			int y = detailY + (labels.length == 1 ? 16 : 6 + i * 20);
			boolean over = inside(mouseX, mouseY, x, y, bw, 16);
			if (over) found = i;
			if (draw) {
				int accent = (i == 0) ? GOLD : themeColor;
				GrimoireDraw.arcaneButton(x, y, bw, 16, accent, i == 0, over);
				fontRendererObj.drawStringWithShadow(labels[i], x + (bw - fontRendererObj.getStringWidth(labels[i])) / 2, y + 4, over ? accent : TEXT);
			}
		}
		return found;
	}

	private void drawDetail(int mouseX, int mouseY, long now, float timeSec) {
		GrimoireDraw.panel(left + 7, detailY, w - 14, 48, PANEL, 0x2D386A);
		float slide = prefs.effects ? 1 - Math.min(1, (now - selectedAt) / 200f) : 0;
		Card card = selectedCard();
		GrimoireJourney.Step jStep = isJourneyView() ? journey.selected() : null;
		String title, body;
		int color;
		ItemStack previewIcon = null;
		ItemStack[] ritualReagents = null;

		if (jStep != null) {
			title = jStep.name();
			body = journey.detailBody(jStep, mc.thePlayer, skills);
			color = journey.isDone(jStep, mc.thePlayer, skills) ? CYAN : journey.selectedColor();
			previewIcon = journey.iconForStep(jStep);
		} else if (card != null) {
			title = card.entry.getName();
			body = excerpt(card.entry);
			color = card.color;
			previewIcon = card.icon;
			if (card.entry instanceof CompendiumEntryRitual) {
				ritualReagents = ((CompendiumEntryRitual) card.entry).getPreviewReagents();
			}
		} else {
			title = tr("am2.grimoire.welcome");
			body = tr("am2.grimoire.select");
			color = GOLD;
		}

		GrimoireDraw.hGrad(left + 8, detailY + 1, 70, 46, color, 0.14f, color, 0f, true);
		GrimoireDraw.rect(left + 8, detailY + 1, 2, 46, color, 1f);
		GrimoireDecor.runeStrip(left + 14, detailY - 2, w - 28, color, 0.28f, now, prefs.effects);

		int textOffset = 15;
		if (previewIcon != null || card != null) {
			float ix = left + 27;
			float iy = detailY + 24;
			if (prefs.effects) {
				GrimoireDraw.glow(ix, iy, 18f, color, 0.28f);
				GrimoireDraw.ring(ix, iy, 13.5f, 1.0f, color, 0.7f, timeSec * 0.8f, 8, 0.55f, true);
			}
			GrimoireDraw.ring(ix, iy, 11f, 1.0f, color, 0.85f, 0, 1, 1, true);
			drawIcon(previewIcon, title, color, card != null && card.iconFailed, card, ix, iy, 1.15f);
			textOffset = 46;
		}

		int x = left + textOffset + Math.round(slide * 14);
		int textW = w - textOffset - 16 - (actionLabel() == null ? 0 : detailButtonWidth() + 8);
		int rightUsed = 0;

		String badgeText = card != null ? GrimoireDecor.entryBadgeLabel(card.entry, card.affinity) : null;
		if (badgeText != null && !badgeText.isEmpty()) {
			int bw = fontRendererObj.getStringWidth(badgeText) + 8;
			if (bw < textW / 2) {
				rightUsed = bw + 6;
				int bx = x + textW - bw + 4;
				GrimoireDraw.badge(bx, detailY + 4, bw, 11, color);
				fontRendererObj.drawString(badgeText, bx + 4, detailY + 6, color);
			}
		}

		if (ritualReagents != null && ritualReagents.length > 0) {
			int count = Math.min(5, ritualReagents.length);
			int rowW = count * 12;
			if (rightUsed + rowW + 10 < textW) {
				int rxStart = x + textW - rightUsed - rowW;
				for (int i = 0; i < count; i++) {
					ItemStack st = ritualReagents[i];
					if (st == null || st.getItem() == null) continue;
					float rcx = rxStart + i * 12 + 5;
					float rcy = detailY + 9.5f;
					GrimoireDraw.ring(rcx, rcy, 5.8f, 0.7f, color, 0.45f, 0, 1, 1, true);
					drawIcon(st, "", color, false, null, rcx, rcy, 0.58f);
				}
				rightUsed += rowW + 8;
			}
		}

		fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(title, Math.max(24, textW + 4 - rightUsed)), x, detailY + 6, color);
		List<String> lines = wrap(body, textW);
		for (int i = 0; i < Math.min(3, lines.size()); i++) {
			String line = lines.get(i);
			if (i == 2 && lines.size() > 3) line = line + "...";
			fontRendererObj.drawString(line, x, detailY + 18 + i * 10, MUTED);
		}
		detailButton(mouseX, mouseY, true);
	}

	private String excerpt(CompendiumEntry entry) {
		String text = entry.getDescription();
		if (text == null || text.trim().isEmpty()) return tr("am2.grimoire.select");
		return text.replaceAll("![pld]", " ").replaceAll("#[0-9a-fA-F]", "").replaceAll("\\s+", " ").trim();
	}

	// ------------------------------------------------------------------ input

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		search.mouseClicked(mouseX, mouseY, button);
		if (button != 0) return;

		int tool = toolbar(mouseX, mouseY, false);
		if (tool >= 0) {
			click(1.1f);
			if (tool == 0) {
				// Instant in-book PT-BR <-> EN-US language switch!
				String nextLang = prefs.toggleLanguage();
				ArcaneCompendium.instance.switchLanguageCode(nextLang);
				state.search = search.getText();
				pageTurnAt = net.minecraft.client.Minecraft.getSystemTime();
				initGui();
				return;
			} else if (tool == 1) {
				prefs.effects = !prefs.effects;
				prefs.save();
				state.search = search.getText();
				initGui();
				return;
			} else {
				prefs.classic = true;
				prefs.save();
				AMGuiHelper.instance.clearCompendiumBreadcrumbs();
				mc.displayGuiScreen(new GuiCompendiumIndex());
				return;
			}
		}

		int row = sideRowAt(mouseX, mouseY);
		if (row >= 0) {
			state.category = categories.get(row).getCategoryName();
			state.parentId = null;
			state.selectedId = null;
			activeAffinityFilter = null;
			state.search = "";
			search.setText("");
			search.setFocused(false);
			click(1);
			rebuild(true);
			return;
		}

		if (ritualGuideButton(mouseX, mouseY, false)) {
			openEntry(ArcaneCompendium.instance.getEntry("rituals_overview"));
			return;
		}

		if (overBack(mouseX, mouseY)) {
			leaveGroup();
			return;
		}

		int filterHit = affinityFilterBar(mouseX, mouseY, false);
		if (filterHit >= 0) {
			activeAffinityFilter = (filterHit == 0) ? null : availableAffinities.get(filterHit - 1);
			state.selectedId = null;
			click(1.2f);
			rebuild(true);
			return;
		}

		if (isJourneyView()) {
			GrimoireJourney.Step hit = journey.stepAt(mouseX, mouseY,
					gridX + 2, bodyY + HEADER_H - 1, gridW - 7, bodyH - HEADER_H - 1, journeyScrollShown);
			if (hit != null) {
				if (hit == journey.selected()) {
					openEntry(ArcaneCompendium.instance.getEntry(hit.entryId));
				} else {
					journey.select(hit);
					selectedAt = net.minecraft.client.Minecraft.getSystemTime();
					click(1.25f);
				}
				return;
			}
		}

		int index = cardAt(mouseX, mouseY);
		if (index >= 0) {
			Card card = cards.get(index);
			if (card.entry.getID().equals(state.selectedId)) openSelected();
			else select(card, index);
			return;
		}

		int action = detailButton(mouseX, mouseY, false);
		if (action == 1) {
			Card card = selectedCard();
			if (card != null) show(new GuiArcaneCompendium(card.entry.getID()));
		} else if (action == 0) {
			if (isJourneyView()) {
				GrimoireJourney.Step step = journey.selected();
				if (step != null) openEntry(ArcaneCompendium.instance.getEntry(step.entryId));
			} else {
				openSelected();
			}
		}
	}

	private void select(Card card, int index) {
		state.selectedId = card.entry.getID();
		selectedAt = net.minecraft.client.Minecraft.getSystemTime();
		card.entry.setIsNew(false);
		card.shimmer = 0f;
		if (prefs.effects) burst(cardX(index) + 16, cardY(index) + CARD_H / 2f, card.color);
		click(1.35f);
	}

	private void openSelected() {
		Card card = selectedCard();
		if (card == null) return;
		if (card.entry.hasSubItems()) {
			state.parentId = card.entry.getID();
			state.selectedId = null;
			state.search = "";
			search.setText("");
			search.setFocused(false);
			click(0.9f);
			rebuild(true);
		} else {
			openEntry(card.entry);
		}
	}

	private void openEntry(CompendiumEntry entry) {
		if (entry == null) return;
		entry.setIsNew(false);
		GuiArcaneCompendium gui = entry.getCompendiumGui(entry.getID());
		if (gui != null) show(gui);
	}

	private void show(GuiScreen next) {
		click(0.9f);
		remember();
		mc.displayGuiScreen(next);
	}

	private void leaveGroup() {
		state.selectedId = state.parentId;
		CompendiumEntry parent = ArcaneCompendium.instance.getEntry(state.parentId);
		state.parentId = parent != null && parent.getParent() != null ? parent.getParent().getID() : null;
		click(0.8f);
		rebuild(true);
	}

	private void remember() {
		state.search = search.getText();
		AMGuiHelper.instance.pushCompendiumBreadcrumb("", 0, CompendiumBreadcrumb.TYPE_INDEX, state.copy());
	}

	@Override
	public void handleMouseInput() {
		super.handleMouseInput();
		int wheel = Mouse.getEventDWheel();
		if (wheel == 0) return;
		int mouseX = Mouse.getEventX() * width / mc.displayWidth;
		int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
		int direction = wheel > 0 ? -1 : 1;
		if (inside(mouseX, mouseY, left + 7, bodyY, sideW, sideListH)) {
			sideScroll = clamp(sideScroll + direction * ROW_H, 0, Math.max(0, categories.size() * ROW_H + 4 - sideListH));
		} else if (isJourneyView()) {
			journeyScroll = clamp(journeyScroll + direction * 24, 0, maxJourneyScroll());
		} else {
			state.scroll = clamp(state.scroll + direction * (CARD_H + CARD_GAP), 0, maxScroll());
		}
	}

	@Override
	protected void keyTyped(char character, int key) {
		if (key == Keyboard.KEY_ESCAPE) {
			remember();
			mc.displayGuiScreen(null);
			mc.setIngameFocus();
			return;
		}
		if (search.isFocused()) {
			String before = search.getText();
			if (search.textboxKeyTyped(character, key) && !before.equals(search.getText())) {
				state.search = search.getText();
				state.selectedId = null;
				rebuild(true);
			}
		} else if (key == Keyboard.KEY_RETURN) {
			if (isJourneyView() && journey.selected() != null) {
				openEntry(ArcaneCompendium.instance.getEntry(journey.selected().entryId));
			} else {
				openSelected();
			}
		} else if (key == Keyboard.KEY_BACK && state.parentId != null) {
			leaveGroup();
		}
	}

	@Override
	public void updateScreen() {
		search.updateCursorCounter();
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	@Override
	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);
		ArcaneCompendium.instance.saveUnlockData();
		super.onGuiClosed();
	}

	// ------------------------------------------------------------------ helpers

	private void click(float pitch) {
		mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), pitch));
	}

	@SuppressWarnings("unchecked")
	private List<String> wrap(String text, int maxWidth) {
		return fontRendererObj.listFormattedStringToWidth(text, Math.max(20, maxWidth));
	}

	private void scissor(int x, int y, int sw, int sh) {
		int factor = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor(x * factor, mc.displayHeight - (y + sh) * factor, Math.max(0, sw * factor), Math.max(0, sh * factor));
	}

	private static String tr(String key) {
		return GrimoireLang.tr(key);
	}

	private static String fold(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}

	private static boolean inside(int px, int py, int x, int y, int rw, int rh) {
		return px >= x && px < x + rw && py >= y && py < y + rh;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
