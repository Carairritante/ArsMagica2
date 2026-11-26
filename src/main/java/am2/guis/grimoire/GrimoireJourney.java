package am2.guis.grimoire;

import am2.api.SkillTreeEntry;
import am2.api.spell.ItemSpellBase;
import am2.api.spell.enums.SkillPointTypes;
import am2.api.spell.enums.SkillTrees;
import am2.blocks.BlocksCommonProxy;
import am2.items.ItemSpellBook;
import am2.items.ItemsCommonProxy;
import am2.lore.ArcaneCompendium;
import am2.lore.CompendiumEntry;
import am2.playerextensions.ExtendedProperties;
import am2.playerextensions.SkillData;
import am2.playerextensions.VoiceVaultStorage;
import am2.skills.VocalResonance;
import am2.spell.SkillManager;
import am2.spell.SkillTreeManager;
import am2.spell.SpellUnlockManager;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.Collections;
import java.util.List;

/**
 * Interactive scrollable 4-track progression map ("Jornada") inside the Grimoire:
 * 1. Mage Path (Awakening -> Oculus -> Inscription -> Crafting Altar -> Spell Book -> Affinities)
 * 2. Etherium Power Path (Obelisk -> Essence Refiner -> Black Aurem -> Celestial Prism -> Mana Battery)
 * 3. All 10 Guardians Hunt (tracked LIVE via real ExtendedProperties.guardian1..guardian10 + Mana bonus counter!)
 * 4. Archmage & Secrets (Astral Compass -> Eternal Bond -> Silver Skill -> Golden Tornado -> Vocal Resonance -> Vocal Aura)
 *    including live requirement checklists for Tornado, Vocal Resonance, and each Guardian.
 */
@SideOnly(Side.CLIENT)
final class GrimoireJourney {
	private static final int TEXT = 0xEEF1FF, MUTED = 0x8E98C8, GOLD = 0xFFE08C, CYAN = 0x7FE3FF, BORDER = 0x3D4C8A;
	private static final int TRACK_H = 54;
	private static final int BOSS_TRACK_H = 88;
	private static final int TRACK_GAP = 4;

	static final class Step {
		final String key;
		final String entryId;
		final int track;
		final int index;

		Step(String key, String entryId, int track, int index) {
			this.key = key;
			this.entryId = entryId;
			this.track = track;
			this.index = index;
		}

		String name() {
			return GrimoireLang.tr("am2.grimoire.j." + key + ".name");
		}

		String hint() {
			return GrimoireLang.tr("am2.grimoire.j." + key + ".hint");
		}
	}

	static final class Track {
		final String titleKey;
		final int color;
		final boolean twoRows;
		final Step[] steps;

		Track(String titleKey, int color, boolean twoRows, Step[] steps) {
			this.titleKey = titleKey;
			this.color = color;
			this.twoRows = twoRows;
			this.steps = steps;
		}

		int height() {
			return twoRows ? BOSS_TRACK_H : TRACK_H;
		}
	}

	private final Track[] tracks = {
			new Track("am2.grimoire.track.mage", 0x7FE3FF, false, new Step[]{
					new Step("awaken", "introToArsMagica", 0, 0),
					new Step("oculus", "occulus", 0, 1),
					new Step("inscribe", "inscriptionTable", 0, 2),
					new Step("altar", "craftingAltar", 0, 3),
					new Step("spellbook", "spell_book", 0, 4),
					new Step("affinity", "affinity", 0, 5)
			}),
			new Track("am2.grimoire.track.etherium", 0x5FE0C0, false, new Step[]{
					new Step("obelisk", "obelisk_structure", 1, 0),
					new Step("refiner", "essence_refiner", 1, 1),
					new Step("blackaurem", "blackaurem_structure", 1, 2),
					new Step("prism", "celestialprism_structure", 1, 3),
					new Step("battery", "mana_battery", 1, 4)
			}),
			new Track("am2.grimoire.track.bosses", 0xFF7A8A, true, new Step[]{
					new Step("boss_water", "BossWaterGuardian", 2, 0),
					new Step("boss_earth", "BossEarthGuardian", 2, 1),
					new Step("boss_air", "BossAirGuardian", 2, 2),
					new Step("boss_arcane", "BossArcaneGuardian", 2, 3),
					new Step("boss_life", "BossLifeGuardian", 2, 4),
					new Step("boss_nature", "BossNatureGuardian", 2, 5),
					new Step("boss_lightning", "BossLightningGuardian", 2, 6),
					new Step("boss_winter", "BossWinterGuardian", 2, 7),
					new Step("boss_fire", "BossFireGuardian", 2, 8),
					new Step("boss_ender", "BossEnderGuardian", 2, 9)
			}),
			new Track("am2.grimoire.track.archmage", 0xFFD966, false, new Step[]{
					new Step("astral", "astral_divination_ritual", 3, 0),
					new Step("soulbound", "true_soulbound_ritual", 3, 1),
					new Step("silver", "silver_skills", 3, 2),
					new Step("tornado", "Tornado", 3, 3),
					new Step("vocal", "VocalResonance", 3, 4),
					new Step("vocal_aura", "vocal_resonance_ritual", 3, 5)
			})
	};

	private Step selected = tracks[0].steps[0];

	Step selected() {
		return selected;
	}

	void select(Step step) {
		this.selected = step;
	}

	int selectedColor() {
		return selected != null ? tracks[selected.track].color : GOLD;
	}

	int contentHeight() {
		int total = 2;
		for (Track t : tracks) total += t.height() + TRACK_GAP;
		return total;
	}

	// Progress is read many times per frame (nodes, links, counters); recompute it at most
	// four times a second instead of rescanning the inventory for every call.
	private static final long DONE_CACHE_MS = 250L;
	private final java.util.Map<Step, Boolean> doneCache = new java.util.IdentityHashMap<Step, Boolean>();
	private final java.util.Map<Step, ItemStack> iconCache = new java.util.IdentityHashMap<Step, ItemStack>();
	private long doneCachedAt;
	private EntityPlayer donePlayer;

	boolean isDone(Step step, EntityPlayer player, SkillData skills) {
		long now = Minecraft.getSystemTime();
		if (player != donePlayer || now - doneCachedAt > DONE_CACHE_MS) {
			doneCache.clear();
			donePlayer = player;
			doneCachedAt = now;
		}
		Boolean cached = doneCache.get(step);
		if (cached == null) {
			cached = computeDone(step, player, skills);
			doneCache.put(step, cached);
		}
		return cached;
	}

	private boolean computeDone(Step step, EntityPlayer player, SkillData skills) {
		if (player == null) return false;
		ExtendedProperties props = ExtendedProperties.For(player);
		int level = props != null ? props.getMagicLevel() : 0;
		String k = step.key;
		if ("awaken".equals(k)) return level >= 1;
		if ("oculus".equals(k)) return skills != null && (skills.getKnownShapes().length + skills.getKnownComponents().length > 0);
		if ("inscribe".equals(k)) return level >= 3 || hasItem(player, ItemSpellBase.class);
		if ("altar".equals(k)) return level >= 5 || hasItem(player, ItemSpellBase.class);
		if ("spellbook".equals(k)) return level >= 10 || hasItem(player, ItemSpellBook.class);
		if ("affinity".equals(k)) return level >= 25;

		// Etherium power progression (unlocked entries or player progression)
		if ("obelisk".equals(k)) return isUnlocked("obelisk") || level >= 8;
		if ("refiner".equals(k)) return isUnlocked("essence_refiner") || level >= 12;
		if ("blackaurem".equals(k)) return isUnlocked("blackaurem_structure") || level >= 20;
		if ("prism".equals(k)) return isUnlocked("celestialprism_structure") || level >= 25;
		if ("battery".equals(k)) return isUnlocked("mana_battery") || level >= 30;

		// REAL guardian1..guardian10 flags synced from server!
		if (props != null) {
			if ("boss_water".equals(k)) return props.guardian1;
			if ("boss_earth".equals(k)) return props.guardian2;
			if ("boss_air".equals(k)) return props.guardian3;
			if ("boss_arcane".equals(k)) return props.guardian4;
			if ("boss_life".equals(k)) return props.guardian5;
			if ("boss_nature".equals(k)) return props.guardian6;
			if ("boss_lightning".equals(k)) return props.guardian7;
			if ("boss_winter".equals(k)) return props.guardian8;
			if ("boss_fire".equals(k)) return props.guardian9;
			if ("boss_ender".equals(k)) return props.guardian10;
		}

		if ("astral".equals(k)) return level >= 50 || hasSpecificItem(player, ItemsCommonProxy.astralCompass);
		if ("soulbound".equals(k)) return (props != null && props.hasRitual && level >= 50) || level >= 60;
		if ("silver".equals(k)) return countAllKnownSilverSkills(player) >= 1;
		if ("tornado".equals(k)) {
			if (skills == null) return false;
			SkillTreeEntry ste = SkillTreeManager.instance.getSkillTreeEntry(SkillManager.instance.getSkill("Tornado"));
			return ste != null && skills.isEntryKnown(ste);
		}
		if ("vocal".equals(k)) return VocalResonance.isUnlocked(player);
		if ("vocal_aura".equals(k)) {
			VoiceVaultStorage vault = VoiceVaultStorage.For(player);
			if (vault == null) return false;
			for (int i = 0; i < VoiceVaultStorage.NUM_SLOTS; i++) {
				if (vault.getStackInSlot(i) != null || !vault.getWord(i).isEmpty()) return true;
			}
			return false;
		}
		return false;
	}

	/** Live contextual detail text with real checklists for bosses, Silver skills, Tornado & Vocal Resonance! */
	String detailBody(Step step, EntityPlayer player, SkillData skills) {
		if (step == null) return "";
		boolean pt = GrimoirePrefs.get().isPortuguese();
		ExtendedProperties props = player != null ? ExtendedProperties.For(player) : null;
		int level = props != null ? props.getMagicLevel() : 0;
		String k = step.key;

		if (k.startsWith("boss_")) {
			boolean dead = isDone(step, player, skills);
			String tag = dead
					? (pt ? "\u2714 Derrotado (+2.000 Mana Máx ativos) — " : "\u2714 Defeated (+2,000 Max Mana active) — ")
					: (pt ? "\u2716 Falta derrotar (+2.000 Mana Máx) — " : "\u2716 Not defeated yet (+2,000 Max Mana) — ");
			return tag + step.hint();
		}
		if ("silver".equals(k)) {
			int count = countAllKnownSilverSkills(player);
			String tag = pt ? ("[" + count + "/13 Magias Prata] ") : ("[" + count + "/13 Silver Skills] ");
			return tag + step.hint();
		}
		if ("tornado".equals(k)) {
			boolean lvlOk = level >= SpellUnlockManager.GOLDEN_MIN_MAGIC_LEVEL;
			boolean silverOk = SpellUnlockManager.countKnownSilverSkillsInTree(player, SkillTrees.Offense) >= 1;
			boolean airOk = props != null && props.guardian3;
			boolean boltOk = props != null && props.guardian7;
			String chk = (lvlOk ? "\u2714" : "\u2716") + (pt ? "Nv.90 " : "Lv.90 ")
					+ (silverOk ? "\u2714" : "\u2716") + (pt ? "Prata Ofensa " : "Offense Silver ")
					+ (airOk ? "\u2714" : "\u2716") + (pt ? "Ar " : "Air ")
					+ (boltOk ? "\u2714" : "\u2716") + (pt ? "Raio" : "Lightning");
			return chk + " — " + step.hint();
		}
		if ("vocal".equals(k)) {
			boolean lvlOk = level >= VocalResonance.REQUIRED_MAGIC_LEVEL;
			boolean unlocked = VocalResonance.isUnlocked(player);
			String chk = (lvlOk ? "\u2714" : "\u2716") + (pt ? "Nv.90 " : "Lv.90 ")
					+ (unlocked ? "\u2714" : "\u2716") + (pt ? "Talento Desperto" : "Talent Unlocked");
			return chk + " — " + step.hint();
		}
		return step.hint();
	}

	private static boolean isUnlocked(String id) {
		CompendiumEntry e = ArcaneCompendium.instance.getEntry(id);
		return e != null && !e.isLocked();
	}

	static int countAllKnownSilverSkills(EntityPlayer player) {
		if (player == null) return 0;
		return SpellUnlockManager.countKnownSilverSkillsInTree(player, SkillTrees.Offense)
				+ SpellUnlockManager.countKnownSilverSkillsInTree(player, SkillTrees.Defense)
				+ SpellUnlockManager.countKnownSilverSkillsInTree(player, SkillTrees.Utility);
	}

	private int nextStepIndex(Track track, EntityPlayer player, SkillData skills) {
		for (int i = 0; i < track.steps.length; i++) {
			if (!isDone(track.steps[i], player, skills)) return i;
		}
		return track.steps.length;
	}

	private float nodeX(Track track, int i, int x, int w) {
		float padX = 24f;
		float span = Math.max(20f, w - padX * 2f);
		int perRow = track.twoRows ? 5 : track.steps.length;
		int col = i % perRow;
		return x + padX + (perRow <= 1 ? span * 0.5f : span * col / (float) (perRow - 1));
	}

	private float nodeY(Track track, int i, float ry) {
		if (!track.twoRows) return ry + 29f;
		int row = i / 5;
		return ry + (row == 0 ? 29f : 63f);
	}

	Step stepAt(int mouseX, int mouseY, int x, int y, int w, int h, float scroll) {
		if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) return null;
		float curY = y + 1 - scroll;
		for (Track track : tracks) {
			for (int i = 0; i < track.steps.length; i++) {
				float cx = nodeX(track, i, x, w);
				float cy = nodeY(track, i, curY);
				float dx = mouseX - cx;
				float dy = mouseY - cy;
				if (dx * dx + dy * dy <= 14f * 14f) {
					return track.steps[i];
				}
			}
			curY += track.height() + TRACK_GAP;
		}
		return null;
	}

	List<String> draw(Minecraft mc, FontRenderer font, RenderItem items, EntityPlayer player, SkillData skills,
					  int x, int y, int w, int h, float scroll, int mouseX, int mouseY, long now, float timeSec, boolean effects) {
		Step hovered = stepAt(mouseX, mouseY, x, y, w, h, scroll);
		List<String> tip = null;
		float curY = y + 1 - scroll;

		for (int t = 0; t < tracks.length; t++) {
			Track track = tracks[t];
			int rh = track.height();
			if (curY + rh >= y && curY <= y + h) {
				// Translucent dark-glass track band letting the central astrolabe mandala glow softly behind
				GrimoireDraw.vGrad(x + 2, curY, w - 4, rh, 0x131832, 0.72f, 0x0C1022, 0.78f, false);
				GrimoireDraw.rect(x + 2, curY, 2, rh, track.color, 0.80f);
				GrimoireDraw.hGrad(x + 4, curY, (w - 8) * 0.55f, 1, track.color, 0.44f, track.color, 0f, true);
				GrimoireDraw.rect(x + 2, curY + rh - 1, w - 4, 1, 0x26315C, 0.65f);

				int nextIdx = nextStepIndex(track, player, skills);
				int doneCount = 0;
				for (Step s : track.steps) if (isDone(s, player, skills)) doneCount++;

				String title = GrimoireLang.tr(track.titleKey);
				String prog;
				if (track.twoRows) {
					int manaBonus = doneCount * 2000;
					prog = doneCount + "/10 (+" + (manaBonus / 1000) + "k / +20k Mana)";
				} else {
					prog = doneCount + "/" + track.steps.length;
				}
				font.drawStringWithShadow(title, x + 8, (int) curY + 4, track.color);
				int pw = font.getStringWidth(prog);
				font.drawString(prog, x + w - 8 - pw, (int) curY + 4, doneCount == track.steps.length ? CYAN : (track.twoRows && doneCount > 0 ? GOLD : MUTED));

				// Draw connecting lines within each row
				int perRow = track.twoRows ? 5 : track.steps.length;
				for (int i = 1; i < track.steps.length; i++) {
					if (i % perRow == 0) continue; // don't draw diagonal slash between row 1 & row 2
					float x0 = nodeX(track, i - 1, x, w);
					float x1 = nodeX(track, i, x, w);
					float cy = nodeY(track, i, curY);
					boolean lit = track.twoRows
							? (isDone(track.steps[i - 1], player, skills) && isDone(track.steps[i], player, skills))
							: (i <= nextIdx);
					GrimoireDraw.line(x0 + 10, cy, x1 - 10, cy, lit ? 1.8f : 1.1f,
							lit ? track.color : BORDER, lit ? 0.78f : 0.48f, lit);
					if (lit && effects) {
						float travel = ((now + t * 420L + i * 260L) % 1500L) / 1500f;
						float px = (x0 + 10) + ((x1 - 10) - (x0 + 10)) * travel;
						GrimoireDraw.glow(px, cy, 4.5f, i == nextIdx ? GOLD : track.color, 0.65f);
						GrimoireDraw.star(px, cy, 1.9f, 0xFFFFFF, 0.85f, timeSec * 70f);
					}
				}

				// Draw nodes
				float span = Math.max(20f, w - 48f);
				for (int i = 0; i < track.steps.length; i++) {
					Step step = track.steps[i];
					float cx = nodeX(track, i, x, w);
					float cy = nodeY(track, i, curY);
					boolean done = isDone(step, player, skills);
					boolean isNext = (i == nextIdx);
					boolean isSel = (step == selected);
					boolean isOver = (step == hovered);
					int nodeCol = done ? CYAN : isNext ? GOLD : track.color;

					if (effects && (isNext || isSel)) {
						float pulse = 0.75f + 0.25f * (float) Math.sin(timeSec * 3.6f + t);
						GrimoireDraw.glow(cx, cy, 16f, isNext ? GOLD : track.color, 0.34f * pulse);
						if (isNext) {
							float wave = (now % 1600L) / 1600f;
							GrimoireDraw.ring(cx, cy, 9.8f + wave * 7f, 1.2f, GOLD, (1f - wave) * 0.8f, 0, 1, 1, true);
							GrimoireDecor.runeRing(cx, cy, 13.6f, 4, 5, GOLD, 0.85f, timeSec * 0.8f, true);
						}
					}
					if (isSel || isOver) {
						GrimoireDraw.ring(cx, cy, 12.2f, 1.1f, isSel ? GOLD : nodeCol, 0.95f,
								effects ? timeSec * 1.4f : 0f, 6, 0.58f, true);
					}

					GrimoireDraw.ring(cx, cy, 9.4f, 9.4f, 0x0F1329, 0.96f, 0, 1, 1, false);
					GrimoireDraw.ring(cx, cy, 9.6f, 1.3f, done ? CYAN : (isNext ? GOLD : (isOver ? track.color : BORDER)),
							done || isNext || isOver ? 0.98f : 0.72f, 0, 1, 1, done || isNext);

					ItemStack icon = iconForStep(step);
					if (icon != null && icon.getItem() != null) {
						drawMiniIcon(mc, font, items, icon, cx, cy, isOver || isSel ? 0.76f : 0.66f);
					} else {
						String num = String.valueOf(i + 1);
						font.drawString(num, (int) cx - font.getStringWidth(num) / 2, (int) cy - 3, isNext ? GOLD : done ? CYAN : MUTED);
					}

					if (done) {
						GrimoireDraw.star(cx + 7.2f, cy - 7.2f, 2.4f, CYAN, 0.95f, 0);
					}

					String lbl = step.name();
					GL11.glPushMatrix();
					GL11.glScalef(0.70f, 0.70f, 1f);
					int maxW = (int) ((span / Math.max(1, perRow - 1) - 4f) / 0.70f);
					String clipped = font.trimStringToWidth(lbl, Math.max(36, maxW));
					int lw = font.getStringWidth(clipped);
					font.drawString(clipped, (int) (cx / 0.70f) - lw / 2, (int) ((cy + 11.0f) / 0.70f),
							isNext ? GOLD : (isSel || isOver || done ? TEXT : MUTED));
					GL11.glPopMatrix();

					if (isOver) {
						tip = Collections.singletonList(step.name() + (done ? " \u2714" : isNext ? " \u2605" : ""));
					}
				}
			}
			curY += rh + TRACK_GAP;
		}
		return tip;
	}

	/** Shared, never modified: built once per step instead of every frame. */
	ItemStack iconForStep(Step step) {
		if (step == null) return null;
		if (!iconCache.containsKey(step)) iconCache.put(step, newIconForStep(step));
		return iconCache.get(step);
	}

	private ItemStack newIconForStep(Step step) {
		String k = step.key;
		try {
			if ("awaken".equals(k)) return new ItemStack(ItemsCommonProxy.arcaneCompendium);
			if ("oculus".equals(k)) return new ItemStack(BlocksCommonProxy.occulus);
			if ("inscribe".equals(k)) return new ItemStack(BlocksCommonProxy.inscriptionTable);
			if ("altar".equals(k)) return new ItemStack(BlocksCommonProxy.craftingAltar);
			if ("spellbook".equals(k)) return new ItemStack(ItemsCommonProxy.spellBook);
			if ("affinity".equals(k)) return new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_ARCANE);

			if ("obelisk".equals(k)) return new ItemStack(BlocksCommonProxy.obelisk);
			if ("refiner".equals(k)) return new ItemStack(BlocksCommonProxy.essenceRefiner);
			if ("blackaurem".equals(k)) return new ItemStack(BlocksCommonProxy.blackAurem);
			if ("prism".equals(k)) return new ItemStack(BlocksCommonProxy.celestialPrism);
			if ("battery".equals(k)) return new ItemStack(BlocksCommonProxy.manaBattery);

			if ("boss_water".equals(k)) return new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_WATER);
			if ("boss_earth".equals(k)) return new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_EARTH);
			if ("boss_air".equals(k)) return new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_AIR);
			if ("boss_arcane".equals(k)) return new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_ARCANE);
			if ("boss_life".equals(k)) return new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_LIFE);
			if ("boss_nature".equals(k)) return new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_NATURE);
			if ("boss_lightning".equals(k)) return new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_LIGHTNING);
			if ("boss_winter".equals(k)) return new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_ICE);
			if ("boss_fire".equals(k)) return new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_FIRE);
			if ("boss_ender".equals(k)) return new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_ENDER);

			if ("astral".equals(k)) return new ItemStack(ItemsCommonProxy.astralCompass);
			if ("soulbound".equals(k)) return new ItemStack(ItemsCommonProxy.itemOre, 1, ItemsCommonProxy.itemOre.META_CHIMERITE);
			if ("silver".equals(k)) return new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_PURE);
			if ("vocal_aura".equals(k)) return new ItemStack(BlocksCommonProxy.vocalAura);
			CompendiumEntry entry = ArcaneCompendium.instance.getEntry(step.entryId);
			if (entry != null) return entry.getRepresentItemStack(step.entryId, -1);
		} catch (Throwable ignored) {
		}
		return null;
	}

	private static void drawMiniIcon(Minecraft mc, FontRenderer font, RenderItem items, ItemStack stack, float cx, float cy, float scale) {
		GL11.glPushMatrix();
		GL11.glTranslatef(cx, cy, 0);
		GL11.glScalef(scale, scale, 1f);
		RenderHelper.enableGUIStandardItemLighting();
		GL11.glEnable(GL12.GL_RESCALE_NORMAL);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		items.zLevel = 100;
		try {
			items.renderItemAndEffectIntoGUI(font, mc.getTextureManager(), stack, -8, -8);
		} catch (Throwable ignored) {
		}
		RenderHelper.disableStandardItemLighting();
		GL11.glDisable(GL12.GL_RESCALE_NORMAL);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
		GL11.glColor4f(1, 1, 1, 1);
		GL11.glPopMatrix();
	}

	private static boolean hasItem(EntityPlayer player, Class<?> cls) {
		for (ItemStack s : player.inventory.mainInventory) {
			if (s != null && s.getItem() != null && cls.isInstance(s.getItem())) return true;
		}
		return false;
	}

	private static boolean hasSpecificItem(EntityPlayer player, Item item) {
		if (item == null) return false;
		for (ItemStack s : player.inventory.mainInventory) {
			if (s != null && s.getItem() == item) return true;
		}
		return false;
	}
}
