package am2.compass.client;

import am2.compass.StructureTargetInfo;
import am2.guis.controls.InstrumentStyle;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Client-only registry mapping Astral Compass structure IDs to 256x256 isometric
 * visual preview textures and short recognition hints.
 */
@SideOnly(Side.CLIENT)
public final class StructurePreviewRegistry {

	private static final Set<String> KNOWN_KEYS;
	private static final Map<String, ResourceLocation> TEXTURE_CACHE = new HashMap<String, ResourceLocation>();

	static {
		Set<String> keys = new HashSet<String>();
		Collections.addAll(keys,
			"stronghold", "village", "mineshaft", "temple_desertpyramid",
			"temple_jungletemple", "temple_witchhut", "fortress", "am2_witchwood_forest",
			"chromaticraft_pylon", "chromaticraft_pylon_black", "chromaticraft_pylon_red",
			"chromaticraft_pylon_green", "chromaticraft_pylon_brown", "chromaticraft_pylon_blue",
			"chromaticraft_pylon_purple", "chromaticraft_pylon_cyan", "chromaticraft_pylon_lightgray",
			"chromaticraft_pylon_gray", "chromaticraft_pylon_pink", "chromaticraft_pylon_lime",
			"chromaticraft_pylon_yellow", "chromaticraft_pylon_lightblue", "chromaticraft_pylon_magenta",
			"chromaticraft_pylon_orange", "chromaticraft_pylon_white",
			"chromaticraft_cavern", "chromaticraft_burrow", "chromaticraft_ocean",
			"chromaticraft_desert", "chromaticraft_snowstruct", "chromaticraft_biomefrag",
			"wizardry_wizard_tower",
			"thaumcraft_mound", "thaumcraft_eldritch_ring", "thaumcraft_hilltop_stones",
			"orespawn_makealienwtfdungeon", "orespawn_makebeehive", "orespawn_makebouncycastle",
			"orespawn_makecephadromealtar", "orespawn_makecloudsharkdungeon", "orespawn_makecrystalbattletower",
			"orespawn_makecrystalhauntedhouse", "orespawn_makedamselindistress", "orespawn_makedungeon",
			"orespawn_makeendercastle", "orespawn_makeenderdragonhospital", "orespawn_makeenderknightdungeon",
			"orespawn_makeenderreapergraveyard", "orespawn_makeenormouscastle", "orespawn_makeenormouscastleq",
			"orespawn_makefrogpond", "orespawn_makegirlfriendisland", "orespawn_makegoldfishbowl",
			"orespawn_makegreenhousedungeon", "orespawn_makehauntedhouse", "orespawn_makeigloo",
			"orespawn_makeincapyramid", "orespawn_makekingaltar", "orespawn_makekyuubidungeon",
			"orespawn_makeleafmonsterdungeon", "orespawn_makeleonnest", "orespawn_makemantishive",
			"orespawn_makeminidungeon", "orespawn_makemonsterisland", "orespawn_makenightmarerookery",
			"orespawn_makeplaypool", "orespawn_makepumpkin", "orespawn_makequeenaltar",
			"orespawn_makerainbow", "orespawn_makeredanthangout", "orespawn_makerobotlab",
			"orespawn_makerotatorstation", "orespawn_makeroundrotator", "orespawn_makerubberduckypond",
			"orespawn_makeshadowdungeon", "orespawn_makesmallbeehive", "orespawn_makespiderhangout",
			"orespawn_makespitbuglair", "orespawn_makestinkyhouse", "orespawn_makeurchinspawner",
			"orespawn_makewaterdragonlair", "orespawn_makewhitehouse",
			"twilightforest_hill1", "twilightforest_hill2", "twilightforest_hill3",
			"twilightforest_hedgemaze", "twilightforest_nagacourtyard", "twilightforest_lichtower",
			"twilightforest_icetower", "twilightforest_questisland", "twilightforest_questgrove",
			"twilightforest_druidgrove", "twilightforest_floatruins", "twilightforest_hydralair",
			"twilightforest_labyrinth", "twilightforest_darktower", "twilightforest_tfstronghold",
			"twilightforest_worldtree", "twilightforest_yeticave", "twilightforest_trollcave",
			"twilightforest_finalcastle", "twilightforest_mushroomtower"
		);
		KNOWN_KEYS = Collections.unmodifiableSet(keys);
	}

	private StructurePreviewRegistry() {}

	public static String sanitizeKey(String id) {
		if (id == null || id.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder(id.length());
		boolean lastUnderscore = false;
		for (int i = 0; i < id.length(); i++) {
			char c = id.charAt(i);
			if (Character.isLetterOrDigit(c)) {
				sb.append(Character.toLowerCase(c));
				lastUnderscore = false;
			} else if (!lastUnderscore && sb.length() > 0) {
				sb.append('_');
				lastUnderscore = true;
			}
		}
		int len = sb.length();
		if (len > 0 && sb.charAt(len - 1) == '_') {
			sb.setLength(len - 1);
		}
		return sb.toString();
	}

	public static String resolvePreviewKey(String id) {
		String clean = sanitizeKey(id);
        // The studio capture contains only its stone support, not the Burrow.
        if ("chromaticraft_burrow".equals(clean)) return null;
		if (KNOWN_KEYS.contains(clean)) {
			return clean;
		}
		return null;
	}

	public static ResourceLocation getTexture(String id) {
		String key = resolvePreviewKey(id);
		if (key == null) {
			return null;
		}
		ResourceLocation loc = TEXTURE_CACHE.get(key);
		if (loc == null) {
			loc = new ResourceLocation("arsmagica2", "textures/gui/compass_previews/" + key + ".png");
			TEXTURE_CACHE.put(key, loc);
		}
		return loc;
	}

	/**
	 * Draws the crisp structure preview inside [x, y, w, h], falling back to the
	 * vector compass icon only if no preview texture exists.
	 */
	public static void drawPreview(Minecraft mc, String id, int x, int y, int width, int height) {
		ResourceLocation tex = getTexture(id);
		if (tex == null || mc == null) {
			int size = Math.min(width, height);
			InstrumentStyle.icon(false, x + (width - size) / 2, y + (height - size) / 2, size);
			return;
		}
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
		try {
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
			mc.getTextureManager().bindTexture(tex);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

			Tessellator tess = Tessellator.instance;
			tess.startDrawingQuads();
			tess.addVertexWithUV(x, y + height, 0.0D, 0.0D, 1.0D);
			tess.addVertexWithUV(x + width, y + height, 0.0D, 1.0D, 1.0D);
			tess.addVertexWithUV(x + width, y, 0.0D, 1.0D, 0.0D);
			tess.addVertexWithUV(x, y, 0.0D, 0.0D, 0.0D);
			tess.draw();
		} finally {
			GL11.glPopAttrib();
		}
	}

	public static String getVisualDescription(StructureTargetInfo entry) {
		if (entry == null) {
			return "";
		}
		if ("chromaticraft:BURROW".equals(entry.id)) return StatCollector.translateToLocal("am2.astral_compass.desc.preview_pending");
		String key = "am2.astral_compass.desc." + sanitizeKey(entry.id);
		if (StatCollector.canTranslate(key)) {
			return StatCollector.translateToLocal(key);
		}
		if (entry.id != null && entry.id.startsWith("chromaticraft:PYLON_") && entry.id.length() > "chromaticraft:PYLON_".length()) {
			String rawColor = entry.id.substring("chromaticraft:PYLON_".length()).toLowerCase(Locale.ROOT);
			String colorKey = "am2.astral_compass.color." + rawColor;
			String colorLabel = StatCollector.canTranslate(colorKey)
				? StatCollector.translateToLocal(colorKey)
				: StructureTargetInfo.prettify(rawColor);
			return StatCollector.translateToLocalFormatted("am2.astral_compass.desc.chromaticraft_pylon_color", colorLabel);
		}
		if (entry.id != null && entry.id.startsWith("orespawn:")) {
			return StatCollector.translateToLocal("am2.astral_compass.desc.generic_orespawn");
		}
		if (entry.id != null && entry.id.startsWith("twilightforest:")) {
			return StatCollector.translateToLocal("am2.astral_compass.desc.generic_twilight");
		}
		if (entry.id != null && entry.id.startsWith("chromaticraft:")) {
			return StatCollector.translateToLocal("am2.astral_compass.desc.generic_chroma");
		}
		return StatCollector.translateToLocal("am2.astral_compass.desc.generic");
	}
}
