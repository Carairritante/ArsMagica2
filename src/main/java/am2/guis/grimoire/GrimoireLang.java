package am2.guis.grimoire;

import am2.api.spell.enums.Affinity;
import am2.api.spell.enums.SpellModifiers;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Instant in-book translation lookup supporting live PT-BR <-> EN-US toggling inside the Grimoire
 * without forcing a full Minecraft resource-pack reload.
 */
@SideOnly(Side.CLIENT)
public final class GrimoireLang {
	private static final Map<String, Map<String, String>> TABLES = new HashMap<String, Map<String, String>>();

	private GrimoireLang() {}

	public static String tr(String key) {
		if (key == null) return "";
		GrimoirePrefs prefs = GrimoirePrefs.get();
		String override = prefs.language;
		if ("pt_BR".equals(override) || "en_US".equals(override)) {
			Map<String, String> map = table(override);
			String val = map.get(key);
			if (val != null) return val;
			if (!"en_US".equals(override)) {
				val = table("en_US").get(key);
				if (val != null) return val;
			}
		}
		return StatCollector.translateToLocal(key);
	}

	public static String trFmt(String key, Object... args) {
		String fmt = tr(key);
		try {
			return String.format(fmt, args);
		} catch (Throwable t) {
			return fmt;
		}
	}

	public static String categoryLabel(String categoryName, String fallback) {
		String key = "am2.compendium.category." + categoryName;
		String v = tr(key);
		return v.equals(key) ? fallback : v;
	}

	public static String affinityName(Affinity aff) {
		if (aff == null || aff == Affinity.NONE) return "";
		boolean pt = GrimoirePrefs.get().isPortuguese();
		switch (aff) {
			case ARCANE: return pt ? "Arcano" : "Arcane";
			case WATER: return pt ? "Água" : "Water";
			case FIRE: return pt ? "Fogo" : "Fire";
			case EARTH: return pt ? "Terra" : "Earth";
			case AIR: return pt ? "Ar" : "Air";
			case LIGHTNING: return pt ? "Raio" : "Lightning";
			case ICE: return pt ? "Gelo" : "Ice";
			case NATURE: return pt ? "Natureza" : "Nature";
			case LIFE: return pt ? "Vida" : "Life";
			case ENDER: return pt ? "Ender" : "Ender";
			default: return aff.name();
		}
	}

	public static String modifierName(SpellModifiers mod) {
		if (mod == null) return "";
		boolean pt = GrimoirePrefs.get().isPortuguese();
		switch (mod) {
			case SPEED: return pt ? "Velocidade" : "Speed";
			case GRAVITY: return pt ? "Gravidade" : "Gravity";
			case BOUNCE: return pt ? "Ricochete" : "Bounce";
			case DAMAGE: return pt ? "Dano" : "Damage";
			case HEALING: return pt ? "Cura" : "Healing";
			case VELOCITY_ADDED: return pt ? "Impulso" : "Velocity";
			case RADIUS: return pt ? "Raio" : "Radius";
			case DURATION: return pt ? "Duração" : "Duration";
			case PROCS: return pt ? "Procs" : "Procs";
			case RANGE: return pt ? "Alcance" : "Range";
			case TARGET_PLAYERS: return pt ? "Jogadores" : "Players";
			case TARGET_BLOCKS: return pt ? "Blocos" : "Blocks";
			case TARGET_MONSTERS: return pt ? "Monstros" : "Monsters";
			case TARGET_CREATURES: return pt ? "Criaturas" : "Creatures";
			case TARGET_NONSOLID_BLOCKS: return pt ? "Líquidos" : "Non-Solid";
			case PIERCING: return pt ? "Perfuração" : "Piercing";
			case COLOR: return pt ? "Cor" : "Color";
			case MINING_POWER: return pt ? "Mineração" : "Mining";
			case LINGERING: return pt ? "Persistência" : "Lingering";
			case CLOAKING: return pt ? "Ocultação" : "Cloaking";
			case KEYSTONE_REC: return pt ? "Pedra-Chave" : "Keystone";
			case FORTUNE_LEVEL: return pt ? "Fortuna" : "Fortune";
			case SILKTOUCH_LEVEL: return pt ? "Toque Suave" : "Silk Touch";
			case DISMEMBERING_LEVEL: return pt ? "Desmembrar" : "Dismember";
			case BUFF_POWER: return pt ? "Força Buff" : "Buff Power";
			case HOMING: return pt ? "Teleguiado" : "Homing";
			default: return mod.name().toLowerCase(Locale.ROOT);
		}
	}

	public static void clearCache() {
		TABLES.clear();
	}

	private static Map<String, String> table(String langCode) {
		Map<String, String> cached = TABLES.get(langCode);
		if (cached != null) return cached;
		Map<String, String> map = new HashMap<String, String>();
		ResourceLocation loc = new ResourceLocation("arsmagica2", "lang/" + langCode + ".lang");
		BufferedReader reader = null;
		try {
			IResource res = Minecraft.getMinecraft().getResourceManager().getResource(loc);
			if (res != null) {
				reader = new BufferedReader(new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8));
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.isEmpty() || line.startsWith("#")) continue;
					int eq = line.indexOf('=');
					if (eq > 0) {
						map.put(line.substring(0, eq).trim(), line.substring(eq + 1));
					}
				}
			}
		} catch (Throwable ignored) {
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (Throwable ignored) {
				}
			}
		}
		TABLES.put(langCode, map);
		return map;
	}
}
