package am2.spell;

import am2.AMCore;
import am2.api.events.SpellCastingEvent;
import am2.api.spell.component.interfaces.ISkillTreeEntry;
import am2.api.spell.component.interfaces.ISpellComponent;
import am2.api.spell.component.interfaces.ISpellModifier;
import am2.api.spell.enums.SkillPointTypes;
import am2.api.spell.enums.SpellModifiers;
import am2.playerextensions.ExtendedProperties;
import am2.playerextensions.SkillData;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;

public class SpellUnlockManager{

	public static final int GOLDEN_MIN_MAGIC_LEVEL = 90;
	public static final int GOLDEN_MIN_SILVER_SKILLS = 1;

	private ArrayList<UnlockEntry> entries;

	public static boolean meetsGoldenEndgameRequirements(int magicLevel, int knownSilverInTree, boolean requiredGuardiansDefeated){
		return magicLevel >= GOLDEN_MIN_MAGIC_LEVEL
				&& knownSilverInTree >= GOLDEN_MIN_SILVER_SKILLS
				&& requiredGuardiansDefeated;
	}

	public static int countKnownSilverSkillsInTree(EntityPlayer player, am2.api.spell.enums.SkillTrees tree){
		if (player == null || tree == null) return 0;
		SkillData sk = SkillData.For(player);
		int count = 0;
		for (am2.api.SkillTreeEntry entry : SkillTreeManager.instance.getTree(tree)){
			if (entry == null || entry.registeredItem == null) continue;
			if (SkillTreeManager.instance.getSkillPointTypeForPart(entry.registeredItem) == SkillPointTypes.SILVER
					&& !SkillTreeManager.instance.isGoldenSkill(entry.registeredItem)){
				if (sk.isEntryKnown(entry)){
					count++;
				}
			}
		}
		return count;
	}

	public static boolean hasDefeatedRequiredGuardiansForGolden(EntityPlayer player, ISkillTreeEntry skill){
		if (player == null || skill == null) return false;
		ExtendedProperties props = ExtendedProperties.For(player);
		if (props == null) return false;
		String skillName = SkillManager.instance.getSkillName(skill);
		if ("Tornado".equalsIgnoreCase(skillName)){
			// Air Guardian (guardian3) and Lightning Guardian (guardian7)
			return props.guardian3 && props.guardian7;
		}
		return true;
	}

	public SpellUnlockManager(){
		init();
	}

	@SubscribeEvent
	public void onSpellCast(SpellCastingEvent.Pre event){
		if (event.caster instanceof EntityPlayer){
			if (ExtendedProperties.For(event.caster).getCurrentMana() < event.manaCost)
				return;
			for (UnlockEntry entry : entries){
				//check unlocks
				if (!event.caster.worldObj.isRemote){
					if (entry.unlockIsInPrimaryTree((EntityPlayer)event.caster) && entry.willSpellUnlock(event.stack)){
						entry.unlockFor((EntityPlayer)event.caster);
					}
				}
				if (!(entry.unlock instanceof ISpellModifier)){
					//lock out casting of spells that contain "silver" skills you don't know, or pre-learned ones (somehow) that aren't in your primary tree
					if (entry.unlockIsInSpell(event.stack) && (!SkillData.For((EntityPlayer)event.caster).isEntryKnown(SkillTreeManager.instance.getSkillTreeEntry(entry.unlock)) || !entry.unlockIsInPrimaryTree((EntityPlayer)event.caster))){
						event.setCanceled(true);
						return;
					}
				}
			}
		}
	}

	public void init(){
		entries = new ArrayList<UnlockEntry>();
		entries.add(new UnlockEntry(SkillManager.instance.getSkill("FallingStar"), SkillManager.instance.getSkill("MagicDamage"), SkillManager.instance.getSkill("Gravity"), SkillManager.instance.getSkill("AstralDistortion")));
		entries.add(new UnlockEntry(SkillManager.instance.getSkill("Blizzard"), SkillManager.instance.getSkill("Storm"), SkillManager.instance.getSkill("FrostDamage"), SkillManager.instance.getSkill("Freeze"), SkillManager.instance.getSkill("Damage")));
		entries.add(new UnlockEntry(SkillManager.instance.getSkill("FireRain"), SkillManager.instance.getSkill("Storm"), SkillManager.instance.getSkill("FireDamage"), SkillManager.instance.getSkill("Ignition"), SkillManager.instance.getSkill("Damage")));
		entries.add(new UnlockEntry(SkillManager.instance.getSkill("ConcentrateTime"), SkillManager.instance.getSkill("Accelerate"), SkillManager.instance.getSkill("Haste"), SkillManager.instance.getSkill("Lingering")));
		entries.add(new UnlockEntry(SkillManager.instance.getSkill("Dismembering"), SkillManager.instance.getSkill("Piercing"), SkillManager.instance.getSkill("Damage")));

		entries.add(new UnlockEntry(SkillManager.instance.getSkill("ManaLink"), SkillManager.instance.getSkill("ManaDrain"), SkillManager.instance.getSkill("Entangle")));
		entries.add(new UnlockEntry(SkillManager.instance.getSkill("ManaShield"), SkillManager.instance.getSkill("Shield"), SkillManager.instance.getSkill("Reflect"), SkillManager.instance.getSkill("LifeTap")));
		entries.add(new UnlockEntry(SkillManager.instance.getSkill("DiluteTime"), SkillManager.instance.getSkill("Freeze"), SkillManager.instance.getSkill("Slowfall"), SkillManager.instance.getSkill("Slow"), SkillManager.instance.getSkill("Weakness"), SkillManager.instance.getSkill("Lingering")));
		entries.add(new UnlockEntry(SkillManager.instance.getSkill("BuffPower"), SkillManager.instance.getSkill("Haste"), SkillManager.instance.getSkill("Slowfall"), SkillManager.instance.getSkill("SwiftSwim"), SkillManager.instance.getSkill("GravityWell"), SkillManager.instance.getSkill("Leap")));

		entries.add(new UnlockEntry(SkillManager.instance.getSkill("Daylight"), SkillManager.instance.getSkill("TrueSight"), SkillManager.instance.getSkill("DivineIntervention"), SkillManager.instance.getSkill("Light")));
		entries.add(new UnlockEntry(SkillManager.instance.getSkill("Moonrise"), SkillManager.instance.getSkill("NightVision"), SkillManager.instance.getSkill("EnderIntervention"), SkillManager.instance.getSkill("Lunar")));
		entries.add(new UnlockEntry(SkillManager.instance.getSkill("FortifyTime"), SkillManager.instance.getSkill("Shield"), SkillManager.instance.getSkill("ChronoAnchor"), SkillManager.instance.getSkill("Duration")));
		entries.add(new UnlockEntry(SkillManager.instance.getSkill("Prosperity"), SkillManager.instance.getSkill("Dig"), SkillManager.instance.getSkill("FeatherTouch"), SkillManager.instance.getSkill("MiningPower")));

		// Golden Spell: Tornado (Storm + Fling + Solar OR Storm + Fling + Lunar)
		entries.add(new UnlockEntry(SkillManager.instance.getSkill("Tornado"), SkillManager.instance.getSkill("Storm"), SkillManager.instance.getSkill("Fling"), SkillManager.instance.getSkill("Solar")));
		entries.add(new UnlockEntry(SkillManager.instance.getSkill("Tornado"), SkillManager.instance.getSkill("Storm"), SkillManager.instance.getSkill("Fling"), SkillManager.instance.getSkill("Lunar")));
	}

	class UnlockEntry{
		private ISkillTreeEntry unlock;
		private ISkillTreeEntry[] requiredComponents;

		public UnlockEntry(ISkillTreeEntry unlock, ISkillTreeEntry... components){
			this.unlock = unlock;
			this.requiredComponents = components;
		}

		public boolean unlockIsInSpell(ItemStack spell){
			for (int i = 0; i < SpellUtils.instance.numStages(spell); ++i)
				if (partIsInStage(spell, unlock, i))
					return true;
			return false;
		}

		public boolean partIsInStage(ItemStack spell, ISkillTreeEntry part, int stage){
			if (part instanceof ISpellComponent && !SpellUtils.instance.componentIsPresent(spell, part.getClass(), stage)){
				return false;
			}else if (part instanceof ISpellModifier){
				for (SpellModifiers modifier : ((ISpellModifier)part).getAspectsModified()){
					if (!SpellUtils.instance.modifierIsPresent(modifier, spell, stage)){
						return false;
					}
				}
			}
			return true;
		}

		public boolean unlockIsInPrimaryTree(EntityPlayer caster){
			if (AMCore.config.getSkillTreeSecondaryTierCap() >= SkillTreeManager.instance.getHighestTier())
				return true;
			return SkillData.For(caster).getPrimaryTree() == SkillTreeManager.instance.getSkillTreeEntry(unlock).tree;
		}

		public boolean willSpellUnlock(ItemStack spell){
			for (int i = 0; i < SpellUtils.instance.numStages(spell); ++i){

				boolean found = true;

				for (ISkillTreeEntry part : requiredComponents){
					if (!partIsInStage(spell, part, i)){
						found = false;
						break;
					}
				}

				if (found)
					return true;
			}

			return false;
		}

		public void unlockFor(EntityPlayer player){
			if (!player.worldObj.isRemote){
				am2.api.SkillTreeEntry ste = SkillTreeManager.instance.getSkillTreeEntry(unlock);
				if (ste == null || SkillData.For(player).isEntryKnown(ste)){
					return;
				}
				if (SkillTreeManager.instance.isGoldenSkill(unlock) && !player.capabilities.isCreativeMode){
					int magicLevel = ExtendedProperties.For(player).getMagicLevel();
					int silverCount = countKnownSilverSkillsInTree(player, ste.tree);
					boolean guardiansDone = hasDefeatedRequiredGuardiansForGolden(player, unlock);
					if (!meetsGoldenEndgameRequirements(magicLevel, silverCount, guardiansDone)){
						ChatComponentTranslation header = new ChatComponentTranslation("am2.spell.goldenLockedHeader", SkillManager.instance.getDisplayName(unlock));
						header.getChatStyle().setColor(EnumChatFormatting.GOLD);
						player.addChatMessage(header);
						if (magicLevel < GOLDEN_MIN_MAGIC_LEVEL){
							ChatComponentTranslation reqLvl = new ChatComponentTranslation("am2.spell.goldenReqLevel", GOLDEN_MIN_MAGIC_LEVEL, magicLevel);
							reqLvl.getChatStyle().setColor(EnumChatFormatting.RED);
							player.addChatMessage(reqLvl);
						}
						if (silverCount < GOLDEN_MIN_SILVER_SKILLS){
							ChatComponentTranslation reqSilver = new ChatComponentTranslation("am2.spell.goldenReqSilver", GOLDEN_MIN_SILVER_SKILLS);
							reqSilver.getChatStyle().setColor(EnumChatFormatting.RED);
							player.addChatMessage(reqSilver);
						}
						if (!guardiansDone){
							ChatComponentTranslation reqBoss = new ChatComponentTranslation("am2.spell.goldenReqTornadoGuardians");
							reqBoss.getChatStyle().setColor(EnumChatFormatting.RED);
							player.addChatMessage(reqBoss);
						}
						return;
					}
				}
				SkillData.For(player).incrementSpellPoints(SkillPointTypes.SILVER);
				SkillData.For(player).learn(ste.registeredItem);
				SkillData.For(player).forceSync();
				if (SkillTreeManager.instance.isGoldenSkill(unlock)){
					player.worldObj.playSoundAtEntity(player, "random.levelup", 1.0f, 0.85f);
					ChatComponentTranslation msg = new ChatComponentTranslation("am2.spell.goldenUnlock", SkillManager.instance.getDisplayName(unlock));
					msg.getChatStyle().setColor(EnumChatFormatting.GOLD);
					player.addChatMessage(msg);
					if (player instanceof net.minecraft.entity.player.EntityPlayerMP){
						String skillKey = SkillManager.instance.getSkillName(unlock);
						if (skillKey != null && !skillKey.isEmpty()){
							am2.network.AMNetHandler.INSTANCE.sendCompendiumUnlockPacket((net.minecraft.entity.player.EntityPlayerMP) player, skillKey, false);
						}
						am2.network.AMNetHandler.INSTANCE.sendCompendiumUnlockPacket((net.minecraft.entity.player.EntityPlayerMP) player, "silver_skills", false);
					}
				}
			}
		}
	}
}
