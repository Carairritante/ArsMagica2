package am2.guis.grimoire;

import am2.LogHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Properties;

/** Client preferences of the grimoire screen, stored in config/AM2/grimoire.properties. */
@SideOnly(Side.CLIENT)
public final class GrimoirePrefs {
	private static GrimoirePrefs instance;

	public boolean classic = false;
	public boolean effects = true;
	public boolean trail = true;
	/** Optional in-book language override: "", "pt_BR", or "en_US". */
	public String language = "";

	private GrimoirePrefs() {}

	private static File file() {
		return new File(Minecraft.getMinecraft().mcDataDir, "config/AM2/grimoire.properties");
	}

	public static GrimoirePrefs get() {
		if (instance == null) {
			instance = new GrimoirePrefs();
			File f = file();
			if (f.isFile()) {
				try (InputStream in = new FileInputStream(f)) {
					Properties p = new Properties();
					p.load(in);
					instance.classic = Boolean.parseBoolean(p.getProperty("classic", "false"));
					instance.effects = Boolean.parseBoolean(p.getProperty("effects", "true"));
					instance.trail = Boolean.parseBoolean(p.getProperty("trail", "true"));
					String lang = p.getProperty("language", "").trim();
					if ("pt_BR".equals(lang) || "en_US".equals(lang)) {
						instance.language = lang;
					}
				} catch (Exception e) {
					LogHelper.warn("Could not read grimoire preferences: %s", e.toString());
				}
			}
		}
		return instance;
	}

	/** Effective Compendium language code ("pt_BR" or "en_US", or the active MC code when not overridden). */
	public String effectiveLanguageCode() {
		if ("pt_BR".equals(language) || "en_US".equals(language)) {
			return language;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc != null && mc.getLanguageManager() != null) {
			Language cur = mc.getLanguageManager().getCurrentLanguage();
			if (cur != null && cur.getLanguageCode() != null && !cur.getLanguageCode().trim().isEmpty()) {
				return cur.getLanguageCode().trim();
			}
		}
		return "en_US";
	}

	public boolean isPortuguese() {
		return effectiveLanguageCode().toLowerCase(Locale.ROOT).startsWith("pt");
	}

	public String toggleLanguage() {
		language = isPortuguese() ? "en_US" : "pt_BR";
		save();
		return language;
	}

	public void save() {
		File f = file();
		File dir = f.getParentFile();
		if (!dir.isDirectory() && !dir.mkdirs()) return;
		Properties p = new Properties();
		p.setProperty("classic", Boolean.toString(classic));
		p.setProperty("effects", Boolean.toString(effects));
		p.setProperty("trail", Boolean.toString(trail));
		p.setProperty("language", language == null ? "" : language);
		try (OutputStream out = new FileOutputStream(f)) {
			p.store(out, "Arcane Compendium screen");
		} catch (Exception e) {
			LogHelper.warn("Could not save grimoire preferences: %s", e.toString());
		}
	}
}
