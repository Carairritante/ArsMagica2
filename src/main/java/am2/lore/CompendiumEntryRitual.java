package am2.lore;

import am2.RitualShapeHelper;
import am2.api.blocks.MultiblockStructureDefinition;
import am2.api.spell.component.interfaces.IRitualInteraction;
import am2.api.spell.component.interfaces.ISkillTreeEntry;
import am2.guis.GuiArcaneCompendium;
import am2.spell.SkillManager;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.item.ItemStack;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.lang.reflect.Field;

public class CompendiumEntryRitual extends CompendiumEntry{

	private String ritualShape;
	private String ritualController;

	public CompendiumEntryRitual(){
		super(CompendiumEntryTypes.instance.RITUAL);
	}

	@Override
	protected void parseEx(Node node){
		NodeList childNodes = node.getChildNodes();
		for (int i = 0; i < childNodes.getLength(); ++i){
			Node child = childNodes.item(i);
			if (child.getNodeName().equals("ritualName")){
				this.ritualShape = child.getTextContent();
			}else if (child.getNodeName().equals("ritualController")){
				this.ritualController = child.getTextContent();
			}
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	protected GuiArcaneCompendium getCompendiumGui(String searchID, int meta){
		if (this.ritualShape != null){
			try{
				Field f = RitualShapeHelper.class.getField(ritualShape);
				MultiblockStructureDefinition def = (MultiblockStructureDefinition)f.get(RitualShapeHelper.instance);
				ISkillTreeEntry entry = SkillManager.instance.getSkill(ritualController);
				if (entry instanceof IRitualInteraction){
					IRitualInteraction controller = (IRitualInteraction)entry;
					return new GuiArcaneCompendium(searchID, def, controller);
				}
			}catch (Throwable e){
				e.printStackTrace();
			}
		}
		return new GuiArcaneCompendium(searchID);
	}

	public String getRitualController(){
		return this.ritualController;
	}

	public String getRitualShape(){
		return this.ritualShape;
	}

	/** Returns the reagents required by this ritual for preview icons, or null if overview/unavailable. */
	public ItemStack[] getPreviewReagents(){
		if (this.ritualShape == null || this.ritualShape.isEmpty()) return null;
		try{
			if ("worldFusion".equals(this.ritualShape) && am2.interop.CinematicsRitualGuide.active()){
				return am2.interop.CinematicsRitualGuide.previewReagents();
			}
			if ("trueSoulbound".equals(this.ritualShape)){
				return am2.soulbound.TrueSoulboundRitual.INSTANCE.getReagents();
			}
			if ("astralDivination".equals(this.ritualShape)){
				return am2.compass.AstralCompassRitual.INSTANCE.getReagents();
			}
			if ("vocalResonance".equals(this.ritualShape)){
				return am2.interop.voice.VocalAuraRitual.INSTANCE.getReagents();
			}
			if (this.ritualController != null && !this.ritualController.isEmpty()){
				ISkillTreeEntry entry = SkillManager.instance.getSkill(this.ritualController);
				if (entry instanceof IRitualInteraction){
					return ((IRitualInteraction)entry).getReagents();
				}
			}
		}catch (Throwable ignored){
		}
		return null;
	}

	@Override
	public ItemStack getRepresentItemStack(String searchID, int meta){
		if (this.ritualController != null && !this.ritualController.isEmpty()){
			am2.api.SkillTreeEntry entry = am2.spell.SkillTreeManager.instance.getSkillTreeEntry(SkillManager.instance.getSkill(this.ritualController));
			if (entry != null){
				return new ItemStack(am2.items.ItemsCommonProxy.spell_component, 1, SkillManager.instance.getShiftedPartID(entry.registeredItem));
			}
		}
		return new ItemStack(am2.items.ItemsCommonProxy.wizardChalk);
	}

}
