package am2.guis;

import am2.compass.AstralStructureLocator;
import am2.compass.StructureTargetInfo;
import am2.guis.controls.ArcaneChalkStyle;
import am2.guis.controls.GuiButtonArcaneChalk;
import am2.items.ItemAstralCompass;
import am2.network.AMDataWriter;
import am2.network.AMNetHandler;
import am2.network.AMPacketIDs;
import am2.playerextensions.ExtendedProperties;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Searchable arcane divination interface modeled after the Ritual Architect chalk parchment UI.
 */
@SideOnly(Side.CLIENT)
public class GuiAstralCompass extends GuiScreen {

	private static final String[] CATEGORIES = {
			StructureTargetInfo.CAT_ALL,
			StructureTargetInfo.CAT_VANILLA,
			StructureTargetInfo.CAT_AM2,
			StructureTargetInfo.CAT_MODDED
	};

	private final EntityPlayer player;
	private final int heldSlot;
	private GuiTextField searchField;
	private String category = StructureTargetInfo.CAT_ALL;
	private List<StructureTargetInfo> filtered = new ArrayList<StructureTargetInfo>();
	private String selectedId = AstralStructureLocator.ID_STRONGHOLD;
	private int page;
	private int rows;
	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private int listTop;
	private int footer;
	private int lastCatalogHash;

	public GuiAstralCompass(EntityPlayer player) {
		this.player = player;
		this.heldSlot = player.inventory.currentItem;
		ItemStack stack = player.getCurrentEquippedItem();
		ItemAstralCompass.TargetData current = ItemAstralCompass.getTarget(stack);
		if (current != null && current.id != null && !current.id.isEmpty()) {
			this.selectedId = current.id;
		}
	}

	private static String text(String key, Object... args) {
		return StatCollector.translateToLocalFormatted("am2.astral_compass.gui." + key, args);
	}

	@Override
	public void initGui() {
		Keyboard.enableRepeatEvents(true);
		rows = Math.max(3, Math.min(7, (height - 205) / 24));
		panelWidth = Math.min(460, width - 24);
		panelHeight = rows * 24 + 196;
		left = (width - panelWidth) / 2;
		top = Math.max(4, (height - panelHeight) / 2);
		listTop = top + 96;
		footer = listTop + rows * 24 + 6;

		String existingQuery = searchField == null ? "" : searchField.getText();
		searchField = new GuiTextField(fontRendererObj, left + 18, top + 72, panelWidth - 36, 16);
		searchField.setMaxStringLength(64);
		searchField.setEnableBackgroundDrawing(false);
		searchField.setFocused(true);
		searchField.setText(existingQuery);

		requestCatalogSync();
		rebuildFilter(false);
	}

	@Override
	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);
	}

	@Override
	public void updateScreen() {
		super.updateScreen();
		if (searchField != null) {
			searchField.updateCursorCounter();
		}
		int hash = ItemAstralCompass.getClientCatalog().hashCode();
		if (hash != lastCatalogHash) {
			rebuildFilter(false);
		}
	}

	private void rebuildFilter(boolean resetPage) {
		List<StructureTargetInfo> catalog = ItemAstralCompass.getClientCatalog();
		lastCatalogHash = catalog.hashCode();
		String query = searchField == null ? "" : searchField.getText();
		filtered = AstralStructureLocator.filterCatalog(catalog, category, query);
		if (resetPage) {
			page = 0;
		}
		int maxPage = Math.max(0, (filtered.size() - 1) / Math.max(1, rows));
		page = Math.max(0, Math.min(page, maxPage));
		refreshButtons();
	}

	@SuppressWarnings("unchecked")
	private void refreshButtons() {
		buttonList.clear();

		int tabW = (panelWidth - 32 - 12) / CATEGORIES.length;
		for (int i = 0; i < CATEGORIES.length; i++) {
			String cat = CATEGORIES[i];
			String label = StatCollector.translateToLocal("am2.astral_compass.cat." + cat);
			buttonList.add(new GuiButtonArcaneChalk(
					20 + i,
					left + 16 + i * (tabW + 4),
					top + 48,
					tabW,
					18,
					label,
					-1,
					cat.equals(category)));
		}

		for (int i = 0; i < rows; i++) {
			int idx = page * rows + i;
			if (idx >= filtered.size()) break;
			StructureTargetInfo entry = filtered.get(idx);
			String label = entry.getDisplayName() + " \u00a78[" + entry.getCategoryLabel() + "]";
			buttonList.add(new GuiButtonArcaneChalk(
					100 + idx,
					left + 16,
					listTop + i * 24,
					panelWidth - 32,
					22,
					label,
					entry.glyphIndex,
					entry.id.equals(selectedId)));
		}

		GuiButton prev = new GuiButtonArcaneChalk(1, left + 16, footer, 27, 20, "<");
		prev.enabled = page > 0;
		buttonList.add(prev);

		GuiButton next = new GuiButtonArcaneChalk(2, left + 47, footer, 27, 20, ">");
		next.enabled = (page + 1) * rows < filtered.size();
		buttonList.add(next);

		buttonList.add(new GuiButtonArcaneChalk(3, left + panelWidth - 94, footer, 78, 20, text("done")));

		int actionW = (panelWidth - 38) / 2;
		boolean canDivinate = toolReady() && selectedId != null && !selectedId.isEmpty() && canAffordSearch();
		GuiButton divinate = new GuiButtonArcaneChalk(
				4,
				left + 16,
				footer + 25,
				actionW,
				22,
				text("divinate", (int) (AstralStructureLocator.MANA_COST / 1000.0F)));
		divinate.enabled = canDivinate;
		buttonList.add(divinate);

		GuiButton clear = new GuiButtonArcaneChalk(
				5,
				left + 22 + actionW,
				footer + 25,
				actionW,
				22,
				text("clear"));
		clear.enabled = toolReady() && ItemAstralCompass.hasTarget(player.getCurrentEquippedItem());
		buttonList.add(clear);
	}

	private boolean canAffordSearch() {
		ExtendedProperties props = ExtendedProperties.For(player);
		if (props == null) return false;
		return AstralStructureLocator.hasRequiredLevel(props.getMagicLevel())
				&& AstralStructureLocator.hasEnoughMana(props.getCurrentMana(), props.getBonusCurrentMana());
	}

	private boolean toolReady() {
		if (player == null || player.inventory.currentItem != heldSlot) return false;
		ItemStack stack = player.getCurrentEquippedItem();
		return stack != null && stack.getItem() instanceof ItemAstralCompass;
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		if (button.id >= 20 && button.id < 20 + CATEGORIES.length) {
			category = CATEGORIES[button.id - 20];
			rebuildFilter(true);
			return;
		}
		if (button.id >= 100) {
			int idx = button.id - 100;
			if (idx >= 0 && idx < filtered.size()) {
				selectedId = filtered.get(idx).id;
				refreshButtons();
			}
			return;
		}
		if (button.id == 1 && page > 0) {
			page--;
			refreshButtons();
			return;
		}
		if (button.id == 2 && (page + 1) * rows < filtered.size()) {
			page++;
			refreshButtons();
			return;
		}
		if (button.id == 3) {
			mc.displayGuiScreen(null);
			mc.setIngameFocus();
			return;
		}
		if (button.id == 4 && toolReady() && selectedId != null && canAffordSearch()) {
			sendAction(ItemAstralCompass.ACT_SEARCH, selectedId);
			mc.displayGuiScreen(null);
			mc.setIngameFocus();
			return;
		}
		if (button.id == 5 && toolReady()) {
			sendAction(ItemAstralCompass.ACT_CLEAR, "");
			refreshButtons();
		}
	}

	private void requestCatalogSync() {
		if (!toolReady()) return;
		sendAction(ItemAstralCompass.ACT_REQUEST_CATALOG, "");
	}

	private void sendAction(int action, String targetId) {
		AMNetHandler.INSTANCE.sendPacketToServer(
				AMPacketIDs.ASTRAL_COMPASS_ACTION,
				new AMDataWriter()
						.add(heldSlot)
						.add(action)
						.add(targetId == null ? "" : targetId)
						.generate());
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) {
		if (searchField != null && searchField.textboxKeyTyped(typedChar, keyCode)) {
			rebuildFilter(true);
			return;
		}
		if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
			if (toolReady() && selectedId != null && canAffordSearch()) {
				sendAction(ItemAstralCompass.ACT_SEARCH, selectedId);
				mc.displayGuiScreen(null);
				mc.setIngameFocus();
			}
			return;
		}
		super.keyTyped(typedChar, keyCode);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
		if (searchField != null) {
			searchField.mouseClicked(mouseX, mouseY, mouseButton);
		}
		super.mouseClicked(mouseX, mouseY, mouseButton);
	}

	@Override
	public void handleMouseInput() {
		super.handleMouseInput();
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0) {
			int maxPage = Math.max(0, (filtered.size() - 1) / Math.max(1, rows));
			if (wheel > 0 && page > 0) {
				page--;
				refreshButtons();
			} else if (wheel < 0 && page < maxPage) {
				page++;
				refreshButtons();
			}
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		drawWorldBackground(0);
		ArcaneChalkStyle.panel(left, top, panelWidth, panelHeight);
		ArcaneChalkStyle.seal(mc, 11, left + 34, top + 27);

		fontRendererObj.drawStringWithShadow(text("title"), left + 62, top + 14, 0xf0f6ff);

		ExtendedProperties props = ExtendedProperties.For(player);
		int level = props == null ? 0 : props.getMagicLevel();
		int totalMana = props == null ? 0 : (int) Math.floor(props.getCurrentMana() + props.getBonusCurrentMana());
		boolean lvlOk = AstralStructureLocator.hasRequiredLevel(level);
		boolean manaOk = AstralStructureLocator.hasEnoughMana(props == null ? 0f : props.getCurrentMana(), props == null ? 0f : props.getBonusCurrentMana());

		String reqLine = text("req_status", level, AstralStructureLocator.REQUIRED_MAGIC_LEVEL, totalMana, (int) AstralStructureLocator.MANA_COST);
		int reqColor = (lvlOk && manaOk) ? 0x78e2ba : 0xff929f;
		fontRendererObj.drawStringWithShadow(reqLine, left + 62, top + 28, reqColor);

		// Search input box matching ArcaneChalkStyle
		Gui.drawRect(left + 16, top + 69, left + panelWidth - 16, top + 89, 0xff294d74);
		Gui.drawRect(left + 17, top + 70, left + panelWidth - 17, top + 88, 0xff081526);
		if (searchField != null) {
			if (searchField.getText().isEmpty() && !searchField.isFocused()) {
				fontRendererObj.drawString(text("search_placeholder"), left + 22, top + 75, 0x5c799b);
			}
			searchField.drawTextBox();
		}

		if (filtered.isEmpty()) {
			String emptyMsg = text("no_results");
			int w = fontRendererObj.getStringWidth(emptyMsg);
			fontRendererObj.drawStringWithShadow(emptyMsg, left + (panelWidth - w) / 2, listTop + 26, 0x8ea7c4);
		}

		int pages = Math.max(1, (filtered.size() + rows - 1) / rows);
		String pageLabel = (page + 1) + "/" + pages;
		fontRendererObj.drawStringWithShadow(pageLabel, left + 82, footer + 6, 0x99b6d4);

		ItemAstralCompass.TargetData current = ItemAstralCompass.getTarget(player.getCurrentEquippedItem());
		String currentLine = current == null
				? text("no_active_target")
				: text("active_target", current.name, current.x, current.z, current.dimension);
		fontRendererObj.drawStringWithShadow(
				fontRendererObj.trimStringToWidth(currentLine, panelWidth - 36),
				left + 18,
				top + panelHeight - 31,
				current == null ? 0x88a3c4 : 0xa1e5ff);

		String hint = (!lvlOk ? text("need_level", AstralStructureLocator.REQUIRED_MAGIC_LEVEL)
				: (!manaOk ? text("need_mana", (int) AstralStructureLocator.MANA_COST) : text("ready_hint")));
		fontRendererObj.drawStringWithShadow(
				fontRendererObj.trimStringToWidth(hint, panelWidth - 36),
				left + 18,
				top + panelHeight - 19,
				(lvlOk && manaOk) ? 0x78e2ba : 0xffce86);

		GL11.glColor4f(1f, 1f, 1f, 1f);
		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
