package am2.guis;

import am2.blocks.tileentities.TileEntityArcaneStriker;
import am2.guis.controls.InstrumentStyle;
import am2.guis.controls.GuiInstrumentButton;
import am2.network.AMNetHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;

/**
 * Interactive ArcaneChalkStyle control panel for the Arcane High Striker & Target Dummy machine.
 * Lets the player configure duration (1..60s), toggle invulnerability frames, start/stop tests, and read records.
 */
@SideOnly(Side.CLIENT)
public class GuiArcaneStriker extends GuiScreen {

	public static final int ACT_SET_DURATION = 1;
	public static final int ACT_TOGGLE_IFRAMES = 2;
	public static final int ACT_START_TEST = 3;
	public static final int ACT_STOP_TEST = 4;
	public static final int ACT_RESET_RECORD = 5;

	private final EntityPlayer player;
	private final TileEntityArcaneStriker tile;

	private int left, top, panelW, panelH;
    private int paintedDuration=-1;
    private boolean paintedActive,paintedBypass;
    private void drawLabel(String text,int x,int y,int color){fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(text,Math.max(1,left+panelW-18-x)),x,y,color);}

	private GuiInstrumentButton btnMinus5, btnMinus1, btnPlus1, btnPlus5;
	private GuiInstrumentButton btnPre10, btnPre30, btnPre60;
	private GuiInstrumentButton btnIframes;
	private GuiInstrumentButton btnStartStop;
	private GuiInstrumentButton btnResetRecord;
	private GuiInstrumentButton btnClose;

	public GuiArcaneStriker(EntityPlayer player, TileEntityArcaneStriker tile) {
		this.player = player;
		this.tile = tile;
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	@Override
	public void initGui() {
		panelW = Math.min(390, width - 20);
		panelH = Math.min(282, height - 16);
		left = (width - panelW) / 2;
		top = (height - panelH) / 2;
		rebuildButtons();
	}

	@SuppressWarnings("unchecked")
	private void rebuildButtons() {
        paintedDuration=tile.getConfiguredDurationSec();paintedActive=tile.isTestActive();paintedBypass=tile.isBypassIframes();
		buttonList.clear();

		int colLeft = left + 18;
		int colW = (panelW - 44) / 2;
		int yDur = top + 76;
		int bW = (colW - 12) / 4;

		btnMinus5 = new GuiInstrumentButton(1, colLeft, yDur, bW, 20, "-5s");
		btnMinus1 = new GuiInstrumentButton(2, colLeft + bW + 4, yDur, bW, 20, "-1s");
		btnPlus1 = new GuiInstrumentButton(3, colLeft + (bW + 4) * 2, yDur, bW, 20, "+1s");
		btnPlus5 = new GuiInstrumentButton(4, colLeft + (bW + 4) * 3, yDur, bW, 20, "+5s");

		int preW = (colW - 8) / 3;
		int yPre = yDur + 25;
		int curDur = tile.getConfiguredDurationSec();
		btnPre10 = new GuiInstrumentButton(5, colLeft, yPre, preW, 20, "10s", -1, curDur == 10);
		btnPre30 = new GuiInstrumentButton(6, colLeft + preW + 4, yPre, preW, 20, "30s", -1, curDur == 30);
		btnPre60 = new GuiInstrumentButton(7, colLeft + (preW + 4) * 2, yPre, preW, 20, "60s", -1, curDur == 60);

		boolean bypass = tile.isBypassIframes();
		btnIframes = new GuiInstrumentButton(
				8,
				colLeft,
				yPre + 34,
				colW,
				22,
				StatCollector.translateToLocal(bypass ? "am2.arcane_striker.gui.iframes_off" : "am2.arcane_striker.gui.iframes_on"),
				-1,
				bypass
		);

		boolean active = tile.isTestActive();
		btnStartStop = new GuiInstrumentButton(
				9,
				colLeft,
				top + panelH - 38,
				colW,
				24,
				StatCollector.translateToLocal(active ? "am2.arcane_striker.gui.stop" : "am2.arcane_striker.gui.start"),
				active ? 14 : 3,
				active
		);

		btnResetRecord = new GuiInstrumentButton(
				10,
				colLeft + colW + 14,
				top + panelH - 38,
				colW - 58,
				22,
				StatCollector.translateToLocal("am2.arcane_striker.gui.reset_record")
		);

		btnClose = new GuiInstrumentButton(
				11,
				left + panelW - 72,
				top + panelH - 38,
				54,
				22,
				StatCollector.translateToLocal("am2.gui.close")
		);

		buttonList.add(btnMinus5);
		buttonList.add(btnMinus1);
		buttonList.add(btnPlus1);
		buttonList.add(btnPlus5);
		buttonList.add(btnPre10);
		buttonList.add(btnPre30);
		buttonList.add(btnPre60);
		buttonList.add(btnIframes);
		buttonList.add(btnStartStop);
		buttonList.add(btnResetRecord);
		buttonList.add(btnClose);
	}

	@Override
	public void updateScreen() {
		super.updateScreen();
		if (tile == null || tile.isInvalid() || player == null
				|| player.getDistanceSq(tile.xCoord + 0.5, tile.yCoord + 0.5, tile.zCoord + 0.5) > 64.0) {
			mc.displayGuiScreen(null);
			return;
		}
        if(paintedDuration!=tile.getConfiguredDurationSec()||paintedActive!=tile.isTestActive()||paintedBypass!=tile.isBypassIframes())rebuildButtons();
		// Refresh toggle/active button labels smoothly from synced TE state
		boolean active = tile.isTestActive();
		btnStartStop.displayString = StatCollector.translateToLocal(active ? "am2.arcane_striker.gui.stop" : "am2.arcane_striker.gui.start");
		boolean bypass = tile.isBypassIframes();
		btnIframes.displayString = StatCollector.translateToLocal(bypass ? "am2.arcane_striker.gui.iframes_off" : "am2.arcane_striker.gui.iframes_on");
	}

	@Override
	protected void actionPerformed(GuiButton btn) {
		if (!btn.enabled) {
			return;
		}
		int curDur = tile.getConfiguredDurationSec();
		switch (btn.id) {
			case 1: sendAction(ACT_SET_DURATION, Math.max(TileEntityArcaneStriker.MIN_DURATION_SEC, curDur - 5)); break;
			case 2: sendAction(ACT_SET_DURATION, Math.max(TileEntityArcaneStriker.MIN_DURATION_SEC, curDur - 1)); break;
			case 3: sendAction(ACT_SET_DURATION, Math.min(TileEntityArcaneStriker.MAX_DURATION_SEC, curDur + 1)); break;
			case 4: sendAction(ACT_SET_DURATION, Math.min(TileEntityArcaneStriker.MAX_DURATION_SEC, curDur + 5)); break;
			case 5: sendAction(ACT_SET_DURATION, 10); break;
			case 6: sendAction(ACT_SET_DURATION, 30); break;
			case 7: sendAction(ACT_SET_DURATION, 60); break;
			case 8: sendAction(ACT_TOGGLE_IFRAMES, tile.isBypassIframes() ? 0 : 1); break;
			case 9:
				sendAction(tile.isTestActive() ? ACT_STOP_TEST : ACT_START_TEST, 0);
				if (!tile.isTestActive()) {
					mc.displayGuiScreen(null);
				}
				break;
			case 10: sendAction(ACT_RESET_RECORD, 0); break;
			case 11: mc.displayGuiScreen(null); break;
		}
	}

	private void sendAction(int action, int param) {
		AMNetHandler.INSTANCE.sendArcaneStrikerAction(tile.xCoord, tile.yCoord, tile.zCoord, action, param);
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		drawDefaultBackground();
		InstrumentStyle.panel(left, top, panelW, panelH,true);
		InstrumentStyle.icon(true,left+19,top+10,30);

		String title = StatCollector.translateToLocal("am2.arcane_striker.gui.title");
		drawLabel(title, left + 64, top + 18, 0xF0F6FF);
		String sub = StatCollector.translateToLocalFormatted(
				"am2.arcane_striker.gui.duration_label",
				tile.getConfiguredDurationSec(),
				TileEntityArcaneStriker.MAX_DURATION_SEC
		);
		drawLabel(sub, left + 64, top + 32, 0x77CAFF);
		Gui.drawRect(left + 16, top + 54, left + panelW - 16, top + 55, 0x55446EAB);

		int colLeft = left + 18;
		int colW = (panelW - 44) / 2;
		int rightCol = colLeft + colW + 14;

		drawLabel(StatCollector.translateToLocal("am2.arcane_striker.gui.config_header"), colLeft, top + 62, 0x9FD6FF);

		// Right-hand High Striker Readout Card
		Gui.drawRect(rightCol, top + 62, left + panelW - 18, top + panelH - 44, 0x66061224);
		int meterX = rightCol + 8;
		int meterY = top + 70;
		int meterH = panelH - 122;
		int meterW = 14;
		Gui.drawRect(meterX, meterY, meterX + meterW, meterY + meterH, 0xFF0A162B);
		float prog = tile.computeMeterProgress();
		int fillH = Math.round(meterH * prog);
		if (fillH > 0) {
			int fillTop = meterY + meterH - fillH;
			drawGradientRect(meterX + 2, fillTop, meterX + meterW - 2, meterY + meterH - 2, prog >= 0.99f ? 0xFFFFD84D : 0xFF55E6FF, 0xFF2882E6);
		}
        for(int i=0;i<=5;i++){int yy=meterY+i*meterH/5;Gui.drawRect(meterX-2,yy,meterX+meterW+2,yy+1,0x774d8194);}
		// Bell dot at top of bar
		Gui.drawRect(meterX + 2, meterY - 5, meterX + meterW - 2, meterY - 1, prog >= 0.99f ? 0xFFFFEA70 : 0xFF776633);

		int tx = rightCol + 30;
		int ty = top + 68;
		if (tile.isTestActive()) {
			drawLabel(StatCollector.translateToLocalFormatted("am2.arcane_striker.gui.status_live", String.format("%.1f", tile.getRemainingTicks() / 20.0f)), tx, ty, 0x78E2BA);
			drawLabel(StatCollector.translateToLocalFormatted("am2.arcane_striker.gui.stat_total", String.format("%.1f", tile.getCurrentTotalDamage())), tx, ty + 14, 0xFFE680);
			drawLabel(StatCollector.translateToLocalFormatted("am2.arcane_striker.gui.stat_dps", String.format("%.1f", tile.getCurrentDps())), tx, ty + 26, 0x77CAFF);
			drawLabel(StatCollector.translateToLocalFormatted("am2.arcane_striker.gui.stat_peak", String.format("%.1f", tile.getCurrentPeakHit())), tx, ty + 38, 0xD4E4F6);
			drawLabel(StatCollector.translateToLocalFormatted("am2.arcane_striker.gui.stat_hits", tile.getCurrentHitCount()), tx, ty + 50, 0x88A3C4);
		} else {
			drawLabel(StatCollector.translateToLocal("am2.arcane_striker.gui.last_header"), tx, ty, 0x9FD6FF);
			drawLabel(StatCollector.translateToLocalFormatted("am2.arcane_striker.gui.stat_total", String.format("%.1f", tile.getLastTotalDamage())), tx, ty + 12, 0xF0F6FF);
			drawLabel(StatCollector.translateToLocalFormatted("am2.arcane_striker.gui.stat_dps", String.format("%.1f", tile.getLastDps())), tx, ty + 24, 0x78E2BA);
			drawLabel(StatCollector.translateToLocalFormatted("am2.arcane_striker.gui.stat_peak", String.format("%.1f", tile.getLastPeakHit())), tx, ty + 36, 0xD4E4F6);
		}

		int recY = ty + 68;
		Gui.drawRect(tx, recY - 4, left + panelW - 24, recY - 3, 0x445588CC);
		drawLabel(StatCollector.translateToLocal("am2.arcane_striker.gui.record_header"), tx, recY, 0xFFD25A);
		drawLabel(StatCollector.translateToLocalFormatted("am2.arcane_striker.gui.stat_total", String.format("%.1f", tile.getRecordTotalDamage())), tx, recY + 12, 0xFFE89B);
		drawLabel(StatCollector.translateToLocalFormatted("am2.arcane_striker.gui.stat_dps", String.format("%.1f", tile.getRecordDps())), tx, recY + 24, 0xB8E6D6);
		if (!tile.getRecordHolderName().isEmpty()) {
			drawLabel(StatCollector.translateToLocalFormatted("am2.arcane_striker.gui.stat_holder", tile.getRecordHolderName()), tx, recY + 36, 0x88A3C4);
		}

		super.drawScreen(mouseX, mouseY, partialTicks);
	}
}
