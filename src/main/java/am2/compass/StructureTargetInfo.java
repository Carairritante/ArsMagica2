package am2.compass;

import net.minecraft.util.StatCollector;
import net.minecraft.util.StringUtils;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Metadata descriptor for a searchable structure or arcane landmark in Astral Compass.
 */
public final class StructureTargetInfo {

	public static final String CAT_ALL = "all";
	public static final String CAT_VANILLA = "vanilla";
	public static final String CAT_AM2 = "am2";
	public static final String CAT_MODDED = "modded";

	public final String id;
	public final String rawName;
	public final String category;
	public final int preferredDimension;
	public final boolean dimensionRestricted;
	public final int glyphIndex;

	public StructureTargetInfo(String id, String rawName, String category, int preferredDimension, boolean dimensionRestricted, int glyphIndex) {
		this.id = id;
		this.rawName = rawName == null ? id : rawName;
		this.category = category == null ? CAT_MODDED : category;
		this.preferredDimension = preferredDimension;
		this.dimensionRestricted = dimensionRestricted;
		this.glyphIndex = glyphIndex & 15;
	}

	public String getDisplayName() {
		String key = "am2.astral_compass.struct." + sanitizeKey(id);
		if (StatCollector.canTranslate(key)) {
			return StatCollector.translateToLocal(key);
		}
		return prettify(rawName);
	}

	public String getCategoryLabel() {
		return StatCollector.translateToLocal("am2.astral_compass.cat." + category);
	}

    public String getModId() {
        if (CAT_VANILLA.equals(category)) return "minecraft";
        if (CAT_AM2.equals(category)) return "arsmagica2";
        int colon = id.indexOf(':');
        return colon > 0 && !id.startsWith("Temple:") ? id.substring(0, colon).toLowerCase(Locale.ROOT) : "unknown";
    }

    public static String categoryForId(String id) {
        if (id.startsWith("am2:") || id.startsWith("arsmagica2:")) return CAT_AM2;
        if ("Stronghold".equals(id) || "Village".equals(id) || "Mineshaft".equals(id)
                || "Fortress".equals(id) || "Temple:DesertPyramid".equals(id)
                || "Temple:JungleTemple".equals(id) || "Temple:WitchHut".equals(id)) return CAT_VANILLA;
        return CAT_MODDED;
    }

    public String getModName() {
        String mod = getModId();
        if ("minecraft".equals(mod)) return "Minecraft";
        if ("arsmagica2".equals(mod) || "am2".equals(mod)) return "Ars Magica 2.5";
        if ("wizardry".equals(mod)) return "Electroblob's Wizardry";
        if ("orespawn".equals(mod)) return "OreSpawn";
        if ("chromaticraft".equals(mod)) return "ChromatiCraft";
        if ("thaumcraft".equals(mod)) return "Thaumcraft";
        if ("twilightforest".equals(mod)) return "Twilight Forest";
        ModContainer container = Loader.instance().getIndexedModList().get(mod);
        if (container != null) return container.getName();
        return "unknown".equals(mod) ? StatCollector.translateToLocal("am2.astral_compass.gui.unknown_mod") : prettify(mod);
    }

	private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");

	public static String normalizeQuery(String value) {
		if (value == null) return "";
		return ACCENTS.matcher(Normalizer.normalize(StringUtils.stripControlCodes(value), Normalizer.Form.NFKD))
				.replaceAll("").toLowerCase(Locale.ROOT).trim();
	}

	public static String compactQuery(String value) {
		StringBuilder result = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (!Character.isWhitespace(c) && c != '_' && c != '-') result.append(c);
		}
		return result.toString();
	}

	public boolean matchesQuery(String normalizedQuery, String compactQuery) {
		if (normalizedQuery == null || normalizedQuery.isEmpty()) {
			return true;
		}
		String hayDisplay = normalizeQuery(getDisplayName());
		String hayId = normalizeQuery(id);
		String hayRaw = normalizeQuery(rawName + " " + getModName());
		if (hayDisplay.contains(normalizedQuery) || hayId.contains(normalizedQuery) || hayRaw.contains(normalizedQuery)) {
			return true;
		}
		if (compactQuery != null && !compactQuery.isEmpty()) {
			return compactQuery(hayDisplay).contains(compactQuery)
					|| compactQuery(hayId).contains(compactQuery);
		}
		return false;
	}

	public static String sanitizeKey(String id) {
		return id == null ? "unknown" : id.toLowerCase(Locale.ROOT).replace(':', '_').replace(' ', '_');
	}

	public static String prettify(String name) {
		if (name == null || name.isEmpty()) return "Unknown";
		String clean = name.replace('_', ' ');
		int colon = clean.indexOf(':');
		if (colon >= 0 && colon + 1 < clean.length()) {
			clean = clean.substring(colon + 1);
		}
        if (clean.equals(clean.toUpperCase(Locale.ROOT))) clean=clean.toLowerCase(Locale.ROOT);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < clean.length(); i++) {
			char c = clean.charAt(i);
			if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(clean.charAt(i - 1))) {
				sb.append(' ');
			}
			boolean capNext = i == 0 || clean.charAt(i - 1) == ' ';
			sb.append(capNext ? Character.toUpperCase(c) : c);
		}
		return sb.toString().trim();
	}
}
