package am2.guis;

import am2.containers.ContainerVocalAura;
import am2.interop.voice.AkashicVoiceBridge;
import am2.interop.voice.VoiceAcousticMatcher;
import am2.interop.voice.VoiceSpellController;
import am2.network.AMNetHandler;
import am2.playerextensions.VoiceVaultStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Artisanal AM2-styled GUI for the Vocal Resonance Aura (256x238):
 * - Uses custom vocalAuraGui.png backdrop inspired by Astral Barrier / Rift Storage
 * - Custom arcane beveled buttons (no vanilla grey buttons) with live pulse states
 * - Real-time graphical microphone VU meter with VAD marker
 * - 8 spacious spell cards showing word, spell preview, and 4 crystal sample pips
 * - Helpful PT-BR hover tooltips for every control
 */
public class GuiVocalAura extends GuiContainer {

	private static final ResourceLocation GUI_TEXTURE = new ResourceLocation("arsmagica2", "textures/guis/vocalAuraGui.png");

	private static final int BTN_SAVE_WORD = 1;
	private static final int BTN_RECORD = 2;
	private static final int BTN_CLEAR = 3;
	private static final int BTN_MODE = 4;
	private static final int BTN_SENS_DOWN = 5;
	private static final int BTN_SENS_UP = 6;
	private static final int BTN_TEST = 7;

	private static final int STYLE_ARCANE = 0;
	private static final int STYLE_GOLD = 1;
	private static final int STYLE_CRIMSON = 2;
	private static final int STYLE_EMERALD = 3;
	private static final int STYLE_AMBER = 4;

	private final VoiceVaultStorage vault;
	private int selectedSlot = 0;
	private GuiTextField wordField;
	private ArcaneButton btnSave;
	private ArcaneButton btnRecord;
	private ArcaneButton btnClear;
	private ArcaneButton btnMode;
	private ArcaneButton btnTest;
	private ArcaneButton btnSensDown;
	private ArcaneButton btnSensUp;

	public GuiVocalAura(InventoryPlayer playerInv, VoiceVaultStorage vault) {
		super(new ContainerVocalAura(playerInv, vault));
		this.vault = vault;
		this.xSize = 256;
		this.ySize = 238;
	}

	@Override
	public void initGui() {
		super.initGui();
		Keyboard.enableRepeatEvents(true);

		int gx = (this.width - this.xSize) / 2;
		int gy = (this.height - this.ySize) / 2;

		this.wordField = new GuiTextField(this.fontRendererObj, gx + 13, gy + 121, 110, 12);
		this.wordField.setMaxStringLength(32);
		this.wordField.setEnableBackgroundDrawing(false);
		this.wordField.setTextColor(0xE8F6FF);
		this.wordField.setText(vault.getWord(selectedSlot));

		this.buttonList.clear();
		this.btnSave = new ArcaneButton(BTN_SAVE_WORD, gx + 129, gy + 117, 40, 16, StatCollector.translateToLocal("am2.gui.vocalSave"), STYLE_ARCANE);
		this.btnRecord = new ArcaneButton(BTN_RECORD, gx + 171, gy + 117, 52, 16, StatCollector.translateToLocal("am2.gui.vocalRecord"), STYLE_GOLD);
		this.btnClear = new ArcaneButton(BTN_CLEAR, gx + 225, gy + 117, 19, 16, "X", STYLE_CRIMSON);

		this.btnMode = new ArcaneButton(BTN_MODE, gx + 12, gy + 135, 78, 15, "", STYLE_ARCANE);
		this.btnTest = new ArcaneButton(BTN_TEST, gx + 93, gy + 135, 54, 15, "", STYLE_ARCANE);
		this.btnSensDown = new ArcaneButton(BTN_SENS_DOWN, gx + 180, gy + 135, 15, 15, "-", STYLE_ARCANE);
		this.btnSensUp = new ArcaneButton(BTN_SENS_UP, gx + 229, gy + 135, 15, 15, "+", STYLE_ARCANE);

		this.buttonList.add(btnSave);
		this.buttonList.add(btnRecord);
		this.buttonList.add(btnClear);
		this.buttonList.add(btnMode);
		this.buttonList.add(btnTest);
		this.buttonList.add(btnSensDown);
		this.buttonList.add(btnSensUp);

		refreshButtonLabels();
	}

	@Override
	public void onGuiClosed() {
		super.onGuiClosed();
		Keyboard.enableRepeatEvents(false);
		VoiceSpellController ctrl = VoiceSpellController.get();
		if (ctrl != null) {
			if (ctrl.isRecording()) {
				ctrl.cancelRecording();
			}
			ctrl.setGuiTestMode(false);
		}
	}

	private void selectSlot(int slot) {
		if (slot < 0 || slot >= VoiceVaultStorage.NUM_SLOTS) return;
		if (this.selectedSlot != slot) {
			VoiceSpellController ctrl = VoiceSpellController.get();
			if (ctrl != null && ctrl.isRecording()) {
				ctrl.cancelRecording();
			}
			this.selectedSlot = slot;
			if (this.wordField != null) {
				this.wordField.setText(vault.getWord(slot));
			}
		}
	}

	private void saveSelectedWord() {
		if (wordField == null) return;
		String normalized = VoiceVaultStorage.normalizeWord(wordField.getText());
		wordField.setText(normalized);
		vault.setWord(selectedSlot, normalized);
		AMNetHandler.INSTANCE.sendVoiceVaultWordUpdate(selectedSlot, normalized);
	}

	private void refreshButtonLabels() {
		VoiceSpellController ctrl = VoiceSpellController.get();
		if (ctrl == null) return;
		boolean cont = ctrl.isContinuousListen();
		this.btnMode.displayString = cont
				? StatCollector.translateToLocal("am2.gui.vocalModeContinuous")
				: StatCollector.translateToLocal("am2.gui.vocalModeKey");
		this.btnMode.setStyle(cont ? STYLE_AMBER : STYLE_ARCANE, false);

		boolean recThis = ctrl.isRecording() && ctrl.getRecordingVaultSlot() == selectedSlot;
		this.btnRecord.displayString = recThis
				? (EnumChatFormatting.WHITE + "Parar")
				: StatCollector.translateToLocal("am2.gui.vocalRecord");
		this.btnRecord.setStyle(recThis ? STYLE_CRIMSON : STYLE_GOLD, recThis);

		boolean testing = ctrl.isGuiTestMode();
		this.btnTest.displayString = testing
				? StatCollector.translateToLocal("am2.gui.vocalTesting")
				: StatCollector.translateToLocal("am2.gui.vocalTest");
		this.btnTest.setStyle(testing ? STYLE_EMERALD : STYLE_ARCANE, testing);
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		VoiceSpellController ctrl = VoiceSpellController.get();
		if (ctrl == null) return;

		if (button.id == BTN_SAVE_WORD) {
			saveSelectedWord();
		} else if (button.id == BTN_RECORD) {
			if (ctrl.isRecording()) {
				ctrl.cancelRecording();
			} else {
				saveSelectedWord();
				String w = vault.getWord(selectedSlot);
				ItemStack spell = vault.getStackInSlot(selectedSlot);
				ctrl.startVaultSlotRecording(selectedSlot, w, spell, false);
			}
		} else if (button.id == BTN_CLEAR) {
			ctrl.cancelRecording();
			String oldWord = vault.getWord(selectedSlot);
			wordField.setText("");
			vault.setWord(selectedSlot, "");
			AMNetHandler.INSTANCE.sendVoiceVaultWordUpdate(selectedSlot, "");
			if (oldWord != null && !oldWord.isEmpty() && vault.findSlotByWord(oldWord) < 0) {
				ctrl.getMatcher().removeBinding(oldWord);
			}
		} else if (button.id == BTN_MODE) {
			ctrl.setContinuousListen(!ctrl.isContinuousListen());
		} else if (button.id == BTN_TEST) {
			ctrl.setGuiTestMode(!ctrl.isGuiTestMode());
		} else if (button.id == BTN_SENS_DOWN) {
			ctrl.setMatchThreshold(ctrl.getMatchThreshold() - 0.03);
		} else if (button.id == BTN_SENS_UP) {
			ctrl.setMatchThreshold(ctrl.getMatchThreshold() + 0.03);
		}
		refreshButtonLabels();
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
		super.mouseClicked(mouseX, mouseY, mouseButton);

		int gx = (this.width - this.xSize) / 2;
		int gy = (this.height - this.ySize) / 2;
		// Click inside the visual text box area (including its padding) focuses wordField
		boolean inWordBox = mouseX >= gx + 10 && mouseX <= gx + 126 && mouseY >= gy + 116 && mouseY <= gy + 133;
		if (wordField != null) {
			wordField.mouseClicked(mouseX, mouseY, mouseButton);
			if (inWordBox) {
				wordField.setFocused(true);
			}
		}

		for (int i = 0; i < VoiceVaultStorage.NUM_SLOTS; i++) {
			int col = i / 4;
			int row = i % 4;
			int cardX = gx + ((col == 0) ? 10 : 130);
			int cardY = gy + 20 + row * 20;
			if (mouseX >= cardX && mouseX <= cardX + 116 && mouseY >= cardY && mouseY <= cardY + 18) {
				selectSlot(i);
				break;
			}
		}
		refreshButtonLabels();
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) {
		if (wordField != null && wordField.isFocused()) {
			if (keyCode == Keyboard.KEY_ESCAPE) {
				wordField.setFocused(false);
			} else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
				saveSelectedWord();
				wordField.setFocused(false);
			} else {
				wordField.textboxKeyTyped(typedChar, keyCode);
			}
			return;
		}
		super.keyTyped(typedChar, keyCode);
	}

	@Override
	public void updateScreen() {
		super.updateScreen();
		if (wordField != null) {
			wordField.updateCursorCounter();
		}
		refreshButtonLabels();
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		int gx = (this.width - this.xSize) / 2;
		int gy = (this.height - this.ySize) / 2;

		this.mc.getTextureManager().bindTexture(GUI_TEXTURE);
		this.drawTexturedModalRect(gx, gy, 0, 0, this.xSize, this.ySize);

		VoiceSpellController ctrl = VoiceSpellController.get();
		int testedHitSlot = ctrl != null ? ctrl.getHighlightedTestSlot() : -1;
		float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 160.0);

		// 8 spell vault cards (2 columns x 4 rows)
		for (int i = 0; i < VoiceVaultStorage.NUM_SLOTS; i++) {
			int col = i / 4;
			int row = i % 4;
			int cardX = gx + ((col == 0) ? 10 : 130);
			int cardY = gy + 20 + row * 20;
			boolean isSel = (i == selectedSlot);
			boolean isRec = ctrl != null && ctrl.isRecording() && ctrl.getRecordingVaultSlot() == i;
			boolean isTestHit = (i == testedHitSlot);
			boolean isHover = mouseX >= cardX && mouseX <= cardX + 116 && mouseY >= cardY && mouseY <= cardY + 18;
			boolean hasSpell = vault.getStackInSlot(i) != null;

			int bg;
			int border;
			if (isTestHit) {
				bg = 0xD8113E28;
				border = 0xFF3CFF8C;
			} else if (isRec) {
				int rGlow = (int) (195 + 60 * pulse);
				int gGlow = (int) (135 + 65 * pulse);
				bg = 0xD83B250C;
				border = 0xFF000000 | (rGlow << 16) | (gGlow << 8) | 0x28;
			} else if (isSel) {
				bg = 0xD8182C46;
				border = 0xFF52E5FF;
			} else if (isHover) {
				bg = 0xC81A2438;
				border = 0xFF6B85AC;
			} else {
				bg = 0xB8111623;
				border = 0xFF33415A;
			}

			drawRect(cardX, cardY, cardX + 116, cardY + 18, bg);
			drawBorder(cardX, cardY, 116, 18, border);

			// Selection right-side golden accent bar
			if (isSel) {
				drawRect(cardX + 114, cardY + 2, cardX + 115, cardY + 16, 0xFFE6BA58);
			}

			// Recessed 18x18 spell scroll slot on the left of each card
			drawRect(cardX, cardY, cardX + 18, cardY + 18, 0xFF0B0E16);
			int slotBorder = hasSpell ? 0xFF9C7E3E : 0xFF3E4B64;
			drawBorder(cardX, cardY, 18, 18, isSel ? 0xFFD9AE4E : slotBorder);
			// Inner subtle bevel
			drawRect(cardX + 1, cardY + 1, cardX + 17, cardY + 2, 0x55000000);
			drawRect(cardX + 1, cardY + 1, cardX + 2, cardY + 17, 0x55000000);

			// 4 crystal sample pips in bottom-right of card
			String word = vault.getWord(i);
			int samples = (ctrl != null && word != null && !word.isEmpty())
					? ctrl.getMatcher().getWordSampleCount(word)
					: 0;
			int pipStartX = cardX + 91;
			int pipY = cardY + 12;
			for (int p = 0; p < 4; p++) {
				int px = pipStartX + p * 5;
				int pipCol;
				if (p < samples) {
					pipCol = (samples == 1) ? 0xFFFFC842 : 0xFF3CE8C0;
				} else {
					pipCol = 0xFF232C3F;
				}
				drawRect(px, pipY, px + 3, pipY + 3, pipCol);
				if (p < samples) {
					drawRect(px + 1, pipY, px + 2, pipY + 1, 0xFFFFFFFF);
				}
			}
		}

		// Styled recessed word text field container (x=10..126, y=117..133)
		boolean focused = wordField != null && wordField.isFocused();
		drawRect(gx + 10, gy + 117, gx + 126, gy + 133, 0xEE090D16);
		drawBorder(gx + 10, gy + 117, 116, 16, focused ? 0xFF52E5FF : 0xFF425878);

		// Sensitivity subtle gauge bar under percentage readout (x=197..227, y=147..149)
		if (ctrl != null) {
			drawRect(gx + 197, gy + 147, gx + 227, gy + 149, 0xFF1A2233);
			double norm = (ctrl.getMatchThreshold() - 0.35) / (0.90 - 0.35);
			int fill = Math.max(1, Math.min(30, (int) Math.round(norm * 30.0)));
			drawRect(gx + 197, gy + 147, gx + 197 + fill, gy + 149, 0xFF4CD6F0);
		}

		// Graphical microphone VU meter in header (x=178..246, y=6..14)
		drawMicVuMeter(gx + 178, gy + 6, 68, 8, ctrl, pulse);

		if (wordField != null) {
			wordField.drawTextBox();
			if (wordField.getText().isEmpty() && !wordField.isFocused()) {
				this.fontRendererObj.drawString("Digite a palavra...", gx + 13, gy + 121, 0x5A6A84);
			}
		}
	}

	private void drawMicVuMeter(int x, int y, int w, int h, VoiceSpellController ctrl, float pulse) {
		boolean connected = AkashicVoiceBridge.isConnected();
		boolean muted = AkashicVoiceBridge.isMuted();
		boolean recording = ctrl != null && ctrl.isRecording();

		// Status indicator dot to the left of the meter
		int dotX = x - 9;
		int dotY = y + 2;
		int dotColor;
		if (!connected || muted) {
			dotColor = 0xFFE04848;
		} else if (recording) {
			int g = (int) (150 + 95 * pulse);
			dotColor = 0xFF000000 | (0xFF << 16) | (g << 8) | 0x35;
		} else {
			dotColor = 0xFF3EE89A;
		}
		drawRect(dotX - 1, dotY - 1, dotX + 5, dotY + 5, 0xFF090C14);
		drawRect(dotX, dotY, dotX + 4, dotY + 4, dotColor);

		// Meter trough
		drawRect(x, y, x + w, y + h, 0xEE080B12);
		drawBorder(x, y, w, h, connected && !muted ? 0xFF3A5270 : 0xFF6B2C32);

		if (!connected || muted || ctrl == null) {
			return;
		}

		double rms = AkashicVoiceBridge.getLatestRms();
		double vad = ctrl.getEffectiveVadThreshold();
		double maxScale = 0.16;
		int innerW = w - 2;
		int fillW = (int) Math.min(innerW, Math.round((rms / maxScale) * innerW));
		int vadX = x + 1 + (int) Math.min(innerW - 1, Math.max(2, Math.round((vad / maxScale) * innerW)));

		// Segmented smooth bar fill
		for (int px = 0; px < fillW; px++) {
			double frac = (double) px / (double) innerW;
			int col;
			if (rms < vad) {
				col = 0xFF4A6282;
			} else if (frac < 0.55) {
				col = 0xFF2ED9B8;
			} else if (frac < 0.80) {
				col = 0xFF4BE87A;
			} else {
				col = 0xFFFFC845;
			}
			drawRect(x + 1 + px, y + 1, x + 2 + px, y + h - 1, col);
		}

		// Crisp vertical VAD activation marker
		drawRect(vadX, y + 1, vadX + 1, y + h - 1, 0xDDFFFFFF);
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		VoiceSpellController ctrl = VoiceSpellController.get();

		// Title in header
		String title = StatCollector.translateToLocal("am2.gui.vocalAura");
		this.fontRendererObj.drawStringWithShadow(EnumChatFormatting.GOLD + title, 10, 6, 0xFFFFFF);

		if (!AkashicVoiceBridge.isConnected()) {
			String off = EnumChatFormatting.RED + "Offline";
			int ow = this.fontRendererObj.getStringWidth(off);
			this.fontRendererObj.drawStringWithShadow(off, 178 + (68 - ow) / 2, 6, 0xFFFFFF);
		} else if (AkashicVoiceBridge.isMuted()) {
			String mut = EnumChatFormatting.RED + "Mutado";
			int mw = this.fontRendererObj.getStringWidth(mut);
			this.fontRendererObj.drawStringWithShadow(mut, 178 + (68 - mw) / 2, 6, 0xFFFFFF);
		}

		// Card text (word with adaptive fit + clean compact spell subtitle)
		for (int i = 0; i < VoiceVaultStorage.NUM_SLOTS; i++) {
			int col = i / 4;
			int row = i % 4;
			int cardX = (col == 0) ? 10 : 130;
			int cardY = 20 + row * 20;
			int textX = cardX + 21;
			int maxTopW = 91; // leaves clean padding before right accent bar

			String word = vault.getWord(i);
			int samples = (ctrl != null && word != null && !word.isEmpty())
					? ctrl.getMatcher().getWordSampleCount(word)
					: 0;

			String num = EnumChatFormatting.GRAY + "#" + (i + 1) + " ";
			String fullTop;
			if (word == null || word.isEmpty()) {
				fullTop = num + EnumChatFormatting.DARK_GRAY + "(vazio)";
			} else {
				EnumChatFormatting wCol = (samples >= 2 ? EnumChatFormatting.AQUA : (samples == 1 ? EnumChatFormatting.YELLOW : EnumChatFormatting.WHITE));
				fullTop = num + wCol + word;
			}
			int topWidth = this.fontRendererObj.getStringWidth(fullTop);
			if (topWidth <= maxTopW) {
				this.fontRendererObj.drawString(fullTop, textX, cardY + 2, 0xFFFFFF);
			} else {
				// Smoothly scale down to 0.78x before ever truncating so longer words like "tornadodefogo" fit whole!
				float scale = Math.max(0.76f, (float) maxTopW / (float) topWidth);
				int scaledMax = (int) (maxTopW / scale);
				String fitted = fullTop;
				if (this.fontRendererObj.getStringWidth(fitted) > scaledMax) {
					fitted = this.fontRendererObj.trimStringToWidth(fitted, scaledMax - 6) + "..";
				}
				GL11.glPushMatrix();
				GL11.glScalef(scale, scale, 1.0f);
				this.fontRendererObj.drawString(fitted, (int) (textX / scale), (int) ((cardY + 2.5f) / scale), 0xFFFFFF);
				GL11.glPopMatrix();
			}

			// Compact scaled spell subtitle on lower-left of card (leaves room for the 4 crystal pips on the right)
			ItemStack st = vault.getStackInSlot(i);
			String sub = st != null ? VoiceAcousticMatcher.describeSpellCompact(st) : "Sem pergaminho";
			int subColor = st != null ? 0x90B0D0 : 0x4E5A70;
			GL11.glPushMatrix();
			GL11.glScalef(0.68f, 0.68f, 1.0f);
			int maxSubW = 98; // ~66.6px real width -> ends cleanly before the 4 crystal pips at +91
			String clippedSub = sub;
			if (this.fontRendererObj.getStringWidth(clippedSub) > maxSubW) {
				clippedSub = this.fontRendererObj.trimStringToWidth(clippedSub, maxSubW - 6) + "..";
			}
			this.fontRendererObj.drawString(clippedSub, (int) (textX / 0.68f), (int) ((cardY + 11) / 0.68f), subColor);
			GL11.glPopMatrix();
		}

		// Control panel status / banner line (y=105, wide 232px)
		String banner = ctrl != null ? ctrl.getActiveBannerText() : "";
		if (ctrl != null && ctrl.isRecording()) {
			String prompt;
			if (!ctrl.isRecordingArmed()) {
				prompt = EnumChatFormatting.YELLOW + "Prepare-se para falar...";
			} else if (banner != null && !banner.isEmpty()) {
				prompt = this.fontRendererObj.trimStringToWidth(banner, 232);
			} else {
				prompt = EnumChatFormatting.GOLD + "FALE AGORA no microfone!";
			}
			this.fontRendererObj.drawStringWithShadow(prompt, 12, 105, 0xFFFFFF);
		} else if (banner != null && !banner.isEmpty()) {
			String clippedBanner = this.fontRendererObj.trimStringToWidth(banner, 232);
			this.fontRendererObj.drawStringWithShadow(clippedBanner, 12, 105, 0xFFFFFF);
		} else {
			ItemStack selStack = vault.getStackInSlot(selectedSlot);
			String selWord = vault.getWord(selectedSlot);
			int selSamples = (ctrl != null && !selWord.isEmpty()) ? ctrl.getMatcher().getWordSampleCount(selWord) : 0;
			String info;
			if (selSamples == 1) {
				info = EnumChatFormatting.YELLOW + "Grave +1 amostra para precisão máxima (1/4)";
			} else if (selStack != null) {
				info = EnumChatFormatting.AQUA + VoiceAcousticMatcher.describeSpell(selStack);
			} else {
				info = EnumChatFormatting.GRAY + StatCollector.translateToLocal("am2.gui.vocalEmptySlot");
			}
			String header = EnumChatFormatting.GOLD + "Slot #" + (selectedSlot + 1) + ": " + info;
			this.fontRendererObj.drawStringWithShadow(this.fontRendererObj.trimStringToWidth(header, 232), 12, 105, 0xFFFFFF);
		}

		// Sensitivity label + centered percentage
		if (ctrl != null) {
			this.fontRendererObj.drawString("Sens:", 151, 139, 0x9BB0C9);
			int pct = (int) Math.round(ctrl.getMatchThreshold() * 100.0);
			String pctStr = String.format(Locale.ROOT, "%d%%", pct);
			int pw = this.fontRendererObj.getStringWidth(pctStr);
			this.fontRendererObj.drawStringWithShadow(pctStr, 197 + (30 - pw) / 2, 138, 0xE8F6FF);
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		super.drawScreen(mouseX, mouseY, partialTicks);
		drawControlTooltips(mouseX, mouseY);
	}

	private void drawControlTooltips(int mouseX, int mouseY) {
		int gx = (this.width - this.xSize) / 2;
		int gy = (this.height - this.ySize) / 2;
		List<String> tip = new ArrayList<String>();

		if (isInRect(mouseX, mouseY, gx + 168, gy + 5, 80, 10)) {
			tip.add(EnumChatFormatting.AQUA + "Medidor de Microfone");
			tip.add(EnumChatFormatting.GRAY + "A barra branca indica o volume mínimo (VAD).");
			tip.add(EnumChatFormatting.GRAY + "Fale até a barra ficar verde-ciano.");
		} else if (isHovering(btnSave, mouseX, mouseY)) {
			tip.add(EnumChatFormatting.AQUA + "Salvar Palavra");
			tip.add(EnumChatFormatting.GRAY + "Vincula o texto digitado ao slot selecionado.");
		} else if (isHovering(btnRecord, mouseX, mouseY)) {
			tip.add(EnumChatFormatting.GOLD + "Gravar Amostra de Voz");
			tip.add(EnumChatFormatting.GRAY + "Grave de 2 a 4 vezes falando naturalmente.");
			tip.add(EnumChatFormatting.GRAY + "As 4 gemas no card mostram o progresso.");
		} else if (isHovering(btnClear, mouseX, mouseY)) {
			tip.add(EnumChatFormatting.RED + "Limpar Slot Vocal");
			tip.add(EnumChatFormatting.GRAY + "Apaga a palavra e todas as gravações dela.");
		} else if (isHovering(btnMode, mouseX, mouseY)) {
			VoiceSpellController ctrl = VoiceSpellController.get();
			boolean cont = ctrl != null && ctrl.isContinuousListen();
			tip.add(EnumChatFormatting.AQUA + "Modo de Escuta: " + (cont ? "Contínuo" : "Tecla [V]"));
			tip.add(EnumChatFormatting.GRAY + (cont
					? "Sempre ouvindo (usa filtro extra contra conversa)."
					: "Segure a tecla [V] para conjurar pela voz."));
		} else if (isHovering(btnTest, mouseX, mouseY)) {
			tip.add(EnumChatFormatting.GREEN + "Modo de Teste Seguro");
			tip.add(EnumChatFormatting.GRAY + "Escuta sua voz e acende o card reconhecido");
			tip.add(EnumChatFormatting.GRAY + "com a pontuação sem disparar a magia!");
		} else if (isHovering(btnSensDown, mouseX, mouseY) || isHovering(btnSensUp, mouseX, mouseY)) {
			tip.add(EnumChatFormatting.AQUA + "Sensibilidade de Reconhecimento");
			tip.add(EnumChatFormatting.GRAY + "Menor % = dispara mais fácil.");
			tip.add(EnumChatFormatting.GRAY + "Maior % = mais rigoroso (ideal: 50%–58%).");
		} else {
			// Hovering over the text portion of any of the 8 spell cards shows full untruncated details
			VoiceSpellController ctrl = VoiceSpellController.get();
			for (int i = 0; i < VoiceVaultStorage.NUM_SLOTS; i++) {
				int col = i / 4;
				int row = i % 4;
				int cardX = gx + ((col == 0) ? 10 : 130);
				int cardY = gy + 20 + row * 20;
				if (isInRect(mouseX, mouseY, cardX + 19, cardY, 97, 18)) {
					String w = vault.getWord(i);
					ItemStack st = vault.getStackInSlot(i);
					int samples = (ctrl != null && w != null && !w.isEmpty()) ? ctrl.getMatcher().getWordSampleCount(w) : 0;
					tip.add(EnumChatFormatting.GOLD + "Slot #" + (i + 1) + (w != null && !w.isEmpty() ? (EnumChatFormatting.AQUA + " — '" + w + "'") : (EnumChatFormatting.DARK_GRAY + " — (sem palavra)")));
					if (st != null) {
						tip.add(EnumChatFormatting.WHITE + VoiceAcousticMatcher.describeSpell(st));
					} else {
						tip.add(EnumChatFormatting.GRAY + "Nenhum pergaminho inserido");
					}
					String sampCol = samples >= 2 ? EnumChatFormatting.GREEN.toString() : (samples == 1 ? EnumChatFormatting.YELLOW.toString() : EnumChatFormatting.GRAY.toString());
					tip.add(sampCol + samples + "/4 amostras de voz gravadas");
					break;
				}
			}
		}

		if (!tip.isEmpty()) {
			this.drawHoveringText(tip, mouseX, mouseY, this.fontRendererObj);
		}
	}

	private static boolean isInRect(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}

	private static boolean isHovering(GuiButton btn, int mx, int my) {
		return btn != null && btn.visible
				&& mx >= btn.xPosition && mx < btn.xPosition + btn.width
				&& my >= btn.yPosition && my < btn.yPosition + btn.height;
	}

	private void drawBorder(int x, int y, int w, int h, int color) {
		drawRect(x, y, x + w, y + 1, color);
		drawRect(x, y + h - 1, x + w, y + h, color);
		drawRect(x, y, x + 1, y + h, color);
		drawRect(x + w - 1, y, x + w, y + h, color);
	}

	/**
	 * Custom AM2-styled beveled arcane button replacing Minecraft's plain grey GuiButton.
	 */
	private static final class ArcaneButton extends GuiButton {
		private int style;
		private boolean pulsing;

		ArcaneButton(int id, int x, int y, int w, int h, String label, int style) {
			super(id, x, y, w, h, label);
			this.style = style;
		}

		void setStyle(int style, boolean pulsing) {
			this.style = style;
			this.pulsing = pulsing;
		}

		@Override
		public void drawButton(Minecraft mc, int mouseX, int mouseY) {
			if (!this.visible) return;
			FontRenderer fr = mc.fontRenderer;
			this.field_146123_n = mouseX >= this.xPosition && mouseY >= this.yPosition
					&& mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;
			boolean hover = this.field_146123_n;

			int topBg, botBg, border, textCol;
			if (style == STYLE_GOLD) {
				topBg = hover ? 0xEE4A3918 : 0xEE352811;
				botBg = hover ? 0xEE2B200D : 0xEE1F1709;
				border = hover ? 0xFFFFD86B : 0xFFC79B3B;
				textCol = hover ? 0xFFFFF4C8 : 0xFFFFDF85;
			} else if (style == STYLE_CRIMSON) {
				topBg = hover ? 0xEE581B22 : 0xEE3B1318;
				botBg = hover ? 0xEE360F14 : 0xEE240B0E;
				border = hover ? 0xFFFF6666 : 0xFFB83E46;
				textCol = hover ? 0xFFFFE5E5 : 0xFFFFB4B8;
			} else if (style == STYLE_EMERALD) {
				topBg = hover ? 0xEE194E35 : 0xEE123B28;
				botBg = hover ? 0xEE0E2E1F : 0xEE0A2418;
				border = hover ? 0xFF55FFA8 : 0xFF32C97D;
				textCol = hover ? 0xFFE6FFF0 : 0xFF9BFFCA;
			} else if (style == STYLE_AMBER) {
				topBg = hover ? 0xEE4A3214 : 0xEE36240E;
				botBg = hover ? 0xEE2E1E0B : 0xEE211508;
				border = hover ? 0xFFFFB84C : 0xFFD48832;
				textCol = hover ? 0xFFFFF0D4 : 0xFFFFCC7A;
			} else {
				topBg = hover ? 0xEE223654 : 0xEE17243A;
				botBg = hover ? 0xEE142136 : 0xEE0F1727;
				border = hover ? 0xFF66E2FF : 0xFF426288;
				textCol = hover ? 0xFFFFFFFF : 0xFFD4E8FF;
			}

			if (pulsing) {
				float p = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 150.0);
				if (style == STYLE_CRIMSON) {
					int r = (int) (190 + 65 * p);
					int g = (int) (75 + 75 * p);
					border = 0xFF000000 | (r << 16) | (g << 8) | 0x45;
				} else if (style == STYLE_EMERALD) {
					int g = (int) (185 + 70 * p);
					border = 0xFF000000 | (0x38 << 16) | (g << 8) | 0x8E;
				}
			}

			int x0 = this.xPosition;
			int y0 = this.yPosition;
			int x1 = x0 + this.width;
			int y1 = y0 + this.height;
			int midY = y0 + this.height / 2;

			// Two-tone arcane gradient fill
			drawRect(x0 + 1, y0 + 1, x1 - 1, midY, topBg);
			drawRect(x0 + 1, midY, x1 - 1, y1 - 1, botBg);

			// Crisp 1px themed border + subtle inner top highlight
			drawRect(x0, y0, x1, y0 + 1, border);
			drawRect(x0, y1 - 1, x1, y1, border);
			drawRect(x0, y0, x0 + 1, y1, border);
			drawRect(x1 - 1, y0, x1, y1, border);
			drawRect(x0 + 1, y0 + 1, x1 - 1, y0 + 2, 0x26FFFFFF);

			// Centered label
			int tw = fr.getStringWidth(this.displayString);
			fr.drawStringWithShadow(this.displayString, x0 + (this.width - tw) / 2, y0 + (this.height - 8) / 2, textCol);
		}
	}
}
