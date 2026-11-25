package am2.skills;

import am2.api.spell.component.interfaces.ISkillTreeEntry;
import am2.playerextensions.ExtendedProperties;
import am2.playerextensions.SkillData;
import am2.spell.SkillManager;
import am2.spell.SkillTreeManager;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Endgame talent unlocked at Magic Level 90+ with a Red Skill Point.
 * Grants access to the Vocal Aura ritual and remote voice spell incantations.
 */
public class VocalResonance implements ISkillTreeEntry {

	public static final int SKILL_ID = 86;
	public static final int REQUIRED_MAGIC_LEVEL = 90;

	@Override
	public int getID() {
		return SKILL_ID;
	}

	public static boolean hasRequiredLevel(EntityPlayer player) {
		if (player == null) return false;
		ExtendedProperties props = ExtendedProperties.For(player);
		return props != null && props.getMagicLevel() >= REQUIRED_MAGIC_LEVEL;
	}

	public static boolean isUnlocked(EntityPlayer player) {
		if (player == null) return false;
		ISkillTreeEntry skill = SkillManager.instance.getSkill("VocalResonance");
		if (skill == null) return false;
		SkillData sk = SkillData.For(player);
		return sk != null && sk.isEntryKnown(SkillTreeManager.instance.getSkillTreeEntry(skill));
	}
}
