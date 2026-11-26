package am2.guis.grimoire;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** What the grimoire index was showing, so "back" returns to the same place. */
@SideOnly(Side.CLIENT)
public final class GrimoireState {
	String category;
	String parentId;
	String selectedId;
	String search = "";
	int scroll;

	GrimoireState copy() {
		GrimoireState s = new GrimoireState();
		s.category = category;
		s.parentId = parentId;
		s.selectedId = selectedId;
		s.search = search;
		s.scroll = scroll;
		return s;
	}
}
