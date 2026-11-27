package am2.guis;

import am2.api.spell.ItemSpellBase;
import am2.items.ItemSpellBook;
import am2.network.AMNetHandler;
import am2.spell.SpellUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Radial spell picker for the held spell book: hold the key, point at a slice, release.
 * The choice is sent with the existing spell book slot packet (subID ID_SET_SPELL) and the
 * server re-checks it. Drawing lives in {@link SpellWheelStyle} so it can be restyled freely.
 */
@SideOnly(Side.CLIENT)
public class GuiSpellBookWheel extends GuiScreen {
	public static final int SLOTS = 8;
	private static final float DEAD_ZONE = 18f;

	private final KeyBinding key;
	private final int inventorySlot;
	private final ItemStack[] scrolls = new ItemStack[SLOTS];
	private final String[] names = new String[SLOTS];
	private final float[] manaCosts = new float[SLOTS];
	private final int activeSlot;
	private int hovered = -1;
	private long openedAt;
	private boolean closing;

	public GuiSpellBookWheel(KeyBinding key, int inventorySlot, ItemStack book) {
		this.key = key;
		this.inventorySlot = inventorySlot;
		ItemSpellBook item = (ItemSpellBook) book.getItem();
		ItemStack[] active = item.getActiveScrollInventory(book);
		this.activeSlot = item.GetActiveSlot(book);
		for (int i = 0; i < SLOTS && i < active.length; i++) {
			ItemStack scroll = active[i];
			if (scroll == null || !(scroll.getItem() instanceof ItemSpellBase)) continue;
			scrolls[i] = scroll.copy();
			names[i] = scrolls[i].getDisplayName();
			try {
				manaCosts[i] = SpellUtils.instance.getSpellRequirements(scrolls[i].copy(), mc_player()).manaCost;
			} catch (Throwable unknown) {
				manaCosts[i] = -1;
			}
		}
	}

	private static net.minecraft.entity.player.EntityPlayer mc_player() {
		return net.minecraft.client.Minecraft.getMinecraft().thePlayer;
	}

	/** Whether the held stack has at least two spells to choose from. */
	public static boolean worthOpening(ItemStack book) {
		if (book == null || !isAmBook(book)) return false;
		int count = 0;
		for (ItemStack scroll : ((ItemSpellBook) book.getItem()).getActiveScrollInventory(book))
			if (scroll != null && scroll.getItem() instanceof ItemSpellBase) count++;
		return count >= 2;
	}

	static boolean isAmBook(ItemStack stack) {
		return stack != null && (stack.getItem() == am2.items.ItemsCommonProxy.spellBook
				|| stack.getItem() == am2.items.ItemsCommonProxy.arcaneSpellbook);
	}

	@Override
	public void initGui() {
		if (openedAt == 0) openedAt = net.minecraft.client.Minecraft.getSystemTime();
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partial) {
		float cx = width / 2f, cy = height / 2f;
		int previous = hovered;
		hovered = sectorAt(mouseX, mouseY, cx, cy);
		if (hovered != previous && hovered >= 0) click(1.6f);
		float appear = Math.min(1f, (net.minecraft.client.Minecraft.getSystemTime() - openedAt) / 120f);
		SpellWheelStyle.draw(this, fontRendererObj, itemRender, cx, cy, appear, scrolls, names, manaCosts, activeSlot, hovered);
		super.drawScreen(mouseX, mouseY, partial);
	}

	/** Slice under the mouse; slot 0 sits at the top and the slots go clockwise. */
	private int sectorAt(int mouseX, int mouseY, float cx, float cy) {
		float dx = mouseX - cx, dy = mouseY - cy;
		if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) return -1;
		double angle = Math.atan2(dy, dx) + Math.PI / 2 + Math.PI / SLOTS;
		while (angle < 0) angle += Math.PI * 2;
		int sector = (int) (angle / (Math.PI * 2 / SLOTS)) % SLOTS;
		return scrolls[sector] != null ? sector : -1;
	}

	@Override
	public void updateScreen() {
		if (!keyHeld()) choose();
		ItemStack held = mc.thePlayer == null ? null : mc.thePlayer.inventory.getStackInSlot(inventorySlot);
		if (!isAmBook(held) || mc.thePlayer.inventory.currentItem != inventorySlot)
			close();
	}

	private boolean keyHeld() {
		int code = key.getKeyCode();
		if (code == 0) return false;
		return code < 0 ? Mouse.isButtonDown(code + 100) : Keyboard.isKeyDown(code);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		if (button == 0) choose();
		else close();
	}

	@Override
	protected void keyTyped(char character, int keyCode) {
		if (keyCode == Keyboard.KEY_ESCAPE) close();
	}

	private void choose() {
		if (closing) return;
		if (hovered >= 0 && hovered != activeSlot) {
			AMNetHandler.INSTANCE.sendSpellbookSlotSelect(mc.thePlayer, inventorySlot, hovered);
			click(1.2f);
		}
		close();
	}

	private void close() {
		if (closing) return;
		closing = true;
		mc.displayGuiScreen(null);
		mc.setIngameFocus();
	}

	private void click(float pitch) {
		mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), pitch));
	}
}
