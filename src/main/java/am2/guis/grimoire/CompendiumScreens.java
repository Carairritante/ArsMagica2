package am2.guis.grimoire;

import am2.guis.AMGuiHelper.CompendiumBreadcrumb;
import am2.guis.GuiCompendiumIndex;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiScreen;

/** Chooses between the new grimoire index and the classic book index. */
@SideOnly(Side.CLIENT)
public final class CompendiumScreens {
	private CompendiumScreens() {}

	public static GuiScreen index(CompendiumBreadcrumb crumb) {
		if (crumb != null) {
			if (crumb.refData.length == 1 && crumb.refData[0] instanceof GrimoireState)
				return new GuiGrimoire((GrimoireState) crumb.refData[0]);
			return new GuiCompendiumIndex(crumb);
		}
		return GrimoirePrefs.get().classic ? new GuiCompendiumIndex() : new GuiGrimoire(null);
	}
}
