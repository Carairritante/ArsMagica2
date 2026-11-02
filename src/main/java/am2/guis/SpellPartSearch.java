package am2.guis;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.StringUtils;

import java.text.Normalizer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@SideOnly(Side.CLIENT)
public final class SpellPartSearch implements IResourceManagerReloadListener {
    public static final SpellPartSearch INSTANCE = new SpellPartSearch();
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");
    private final Map<String, String> englishNames = new HashMap<String, String>();
    private net.minecraft.client.resources.Locale english;
    private IResourceManager resources;

    private SpellPartSearch() {}

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        resources = manager;
        english = null;
        englishNames.clear();
    }

    public boolean matches(String localizedName, String registeredName, String query) {
        String needle = normalize(query);
        if (needle.isEmpty() || contains(localizedName, needle) || contains(registeredName, needle)) return true;
        if (registeredName == null) return false;
        String key = registeredName.toLowerCase(Locale.ROOT);
        String name = englishNames.get(key);
        if (name == null) {
            if (english == null) {
                english = new net.minecraft.client.resources.Locale();
                english.loadLocaleDataFiles(resources == null ? Minecraft.getMinecraft().getResourceManager() : resources,
                        Collections.singletonList("en_US"));
            }
            String translationKey = "am2.spell." + key;
            name = english.formatMessage(translationKey, new Object[0]);
            if (name.equals(translationKey)) name = "";
            englishNames.put(key, name);
        }
        return contains(name, needle);
    }

    private static boolean contains(String value, String needle) {
        String normalized = normalize(value);
        String compactNeedle = compact(needle);
        return normalized.contains(needle) || !compactNeedle.isEmpty() && compact(normalized).contains(compactNeedle);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return ACCENTS.matcher(Normalizer.normalize(StringUtils.stripControlCodes(value), Normalizer.Form.NFKD))
                .replaceAll("").toLowerCase(Locale.ROOT).trim();
    }

    private static String compact(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isWhitespace(c) && c != '_' && c != '-') result.append(c);
        }
        return result.toString();
    }
}
