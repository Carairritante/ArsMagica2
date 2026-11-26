package am2.guis;

import am2.api.math.AMVector2;
import am2.api.spell.component.interfaces.*;
import am2.blocks.tileentities.TileEntityInscriptionTable;
import am2.containers.ContainerInscriptionTable;
import am2.guis.controls.GuiButtonVariableDims;
import am2.playerextensions.SkillData;
import am2.spell.SkillManager;
import am2.spell.SkillTreeManager;
import am2.spell.SpellValidator.ValidationResult;
import am2.spell.components.Tornado;
import am2.texture.ResourceManager;
import am2.texture.SpellIconManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GuiInscriptionTable extends GuiContainer{

	private static final ResourceLocation background = new ResourceLocation("arsmagica2", ResourceManager.GetGuiTexturePath("inscriptionTableGui.png"));

	private final EntityPlayer usingPlayer;

	private final Integer[] knownShapes;
	private final Integer[] knownComponents;
	private final Integer[] knownModifiers;

	private ISkillTreeEntry hoveredItem;
	private IIcon hoveredIcon;
	private boolean dragging;
	private boolean lowerHover;
	private int lowerHoverIndex;
	private int lowerHoverShapeGroup = -1;

	private int lastMouseX;
	private int lastMouseY;

	private int iconX;
	private int iconY;
	int IIconXStart_upper = 41;
	int IIconYStart_upper = 5;

	int IIconXStart_lower = 41;
	int IIconYStart_lower = 146;

	int shapeGroupWidth = 37;
	int shapeGroupPadding = 3;

	int shapeGroupY = 108;
	int shapeGroupX = 13;

	AMVector2 searchFieldPosition;
	AMVector2 nameFieldPosition;

	AMVector2 searchFieldDimensions;
	AMVector2 nameFieldDimensions;

	int IIconStep = 17;

	private ValidationResult result;
	private am2.spell.SpellInscriptionAnalyzer.Analysis analysis;
	private final am2.guis.grimoire.InscriptionPreviewPanel previewPanel = new am2.guis.grimoire.InscriptionPreviewPanel();
	private final am2.guis.grimoire.InscriptionBrowserOverlay browserOverlay = new am2.guis.grimoire.InscriptionBrowserOverlay();
	private am2.guis.grimoire.InscriptionBrowserOverlay.Tab activeTab = am2.guis.grimoire.InscriptionBrowserOverlay.Tab.ALL;
	private am2.api.spell.enums.Affinity activeAffinity = null;
	private final ArrayList<am2.api.spell.enums.Affinity> presentAffinities = new ArrayList<am2.api.spell.enums.Affinity>();
	private final java.util.HashMap<Integer, Boolean> modifierUsefulCache = new java.util.HashMap<Integer, Boolean>();
	private int scrollRow = 0;
	private int maxScrollRow = 0;

	private GuiTextField searchBar;
	private GuiTextField nameBar;
	private GuiButtonVariableDims createSpellButton;
	private GuiButtonVariableDims resetSpellButton;

	private String defaultSearchLabel = "\2477\247o" + StatCollector.translateToLocal("am2.gui.search");
	private String defaultNameLabel = "\2477\247o" + StatCollector.translateToLocal("am2.gui.name");

	public GuiInscriptionTable(InventoryPlayer playerInventory, TileEntityInscriptionTable table){
		super(new ContainerInscriptionTable(table, playerInventory));
		usingPlayer = playerInventory.player;
		xSize = 220;
		ySize = 252;
		dragging = false;

		knownShapes = SkillData.For(this.usingPlayer).getKnownShapes();
		knownComponents = SkillData.For(this.usingPlayer).getKnownComponents();
		knownModifiers = SkillData.For(this.usingPlayer).getKnownModifiers();
	}

	@Override
	public boolean doesGuiPauseGame(){
		return false;
	}

	@Override
	public void initGui(){
		super.initGui();
		searchFieldPosition = new AMVector2(39, 59);
		searchFieldDimensions = new AMVector2(141, 12);
		searchBar = new GuiTextField(Minecraft.getMinecraft().fontRenderer, searchFieldPosition.iX, searchFieldPosition.iY, searchFieldDimensions.iX, searchFieldDimensions.iY);

		nameFieldPosition = new AMVector2(39, 93);
		nameFieldDimensions = new AMVector2(141, 12);
		nameBar = new GuiTextField(Minecraft.getMinecraft().fontRenderer, nameFieldPosition.iX, nameFieldPosition.iY, nameFieldDimensions.iX, nameFieldDimensions.iY);


		int l = (width - xSize) / 2;
		int i1 = (height - ySize) / 2;

		createSpellButton = new GuiButtonVariableDims(0, l - 65, i1, StatCollector.translateToLocal("am2.gui.makeSpell"));
		createSpellButton.setDimensions(60, 20);

		resetSpellButton = new GuiButtonVariableDims(1, l + 120, i1 + 72, StatCollector.translateToLocal("am2.gui.resetSpell"));
		resetSpellButton.setDimensions(60, 20);
		resetSpellButton.visible = false;

		if (usingPlayer.capabilities.isCreativeMode){
			this.buttonList.add(createSpellButton);
		}

		this.buttonList.add(resetSpellButton);

		nameBar.setText(((ContainerInscriptionTable)this.inventorySlots).getSpellName());
		if (nameBar.getText().equals("")){
			nameBar.setText(defaultNameLabel);
		}

		searchBar.setText(defaultSearchLabel);

		result = ((ContainerInscriptionTable)this.inventorySlots).validateCurrentDefinition();
		rebuildPresentAffinities();
		refreshAnalysis();
	}

	private void rebuildPresentAffinities(){
		presentAffinities.clear();
		am2.api.spell.enums.Affinity[] order = {
				am2.api.spell.enums.Affinity.ARCANE, am2.api.spell.enums.Affinity.FIRE, am2.api.spell.enums.Affinity.WATER,
				am2.api.spell.enums.Affinity.AIR, am2.api.spell.enums.Affinity.EARTH, am2.api.spell.enums.Affinity.LIGHTNING,
				am2.api.spell.enums.Affinity.ICE, am2.api.spell.enums.Affinity.NATURE, am2.api.spell.enums.Affinity.LIFE,
				am2.api.spell.enums.Affinity.ENDER
		};
		java.util.EnumSet<am2.api.spell.enums.Affinity> found = java.util.EnumSet.noneOf(am2.api.spell.enums.Affinity.class);
		for (Integer id : knownComponents){
			ISkillTreeEntry part = SkillManager.instance.getSkill(id);
			if (part instanceof ISpellComponent && !SkillTreeManager.instance.isSkillDisabled(part)){
				java.util.EnumSet<am2.api.spell.enums.Affinity> affs = ((ISpellComponent)part).getAffinity();
				if (affs != null) found.addAll(affs);
			}
		}
		for (am2.api.spell.enums.Affinity aff : order){
			if (found.contains(aff)) presentAffinities.add(aff);
		}
		if (activeAffinity != null && !found.contains(activeAffinity)){
			activeAffinity = null;
		}
	}

	private int analyzedSignature = Integer.MIN_VALUE;

	private int recipeSignature(){
		ContainerInscriptionTable container = (ContainerInscriptionTable)this.inventorySlots;
		int sig = container.getCurrentRecipeSize() * 31 + container.getNumStageGroups();
		for (int i = 0; i < container.getCurrentRecipeSize(); ++i){
			ISpellPart part = container.getRecipeItemAt(i);
			sig = sig * 31 + (part == null ? 0 : SkillManager.instance.getShiftedPartID(part));
		}
		for (int g = 0; g < container.getNumStageGroups(); ++g){
			sig = sig * 17 + container.getShapeGroupSize(g);
			for (int n = 0; n < container.getShapeGroupSize(g); ++n){
				ISpellPart part = container.getShapeGroupPartAt(g, n);
				sig = sig * 31 + (part == null ? 0 : SkillManager.instance.getShiftedPartID(part));
			}
		}
		return sig;
	}

	private void refreshAnalysis(){
		analyzedSignature = recipeSignature();
		ContainerInscriptionTable container = (ContainerInscriptionTable)this.inventorySlots;
		ArrayList<ArrayList<ISpellPart>> groups = new ArrayList<ArrayList<ISpellPart>>();
		for (int g = 0; g < container.getNumStageGroups(); ++g){
			ArrayList<ISpellPart> list = new ArrayList<ISpellPart>();
			for (int n = 0; n < container.getShapeGroupSize(g); ++n){
				list.add(container.getShapeGroupPartAt(g, n));
			}
			groups.add(list);
		}
		ArrayList<ISpellPart> recipe = new ArrayList<ISpellPart>();
		for (int i = 0; i < container.getCurrentRecipeSize(); ++i){
			recipe.add(container.getRecipeItemAt(i));
		}
		analysis = am2.spell.SpellInscriptionAnalyzer.analyze(groups, recipe, usingPlayer);
		modifierUsefulCache.clear();
		if (analysis != null && analysis.totalParts > 0){
			for (Integer id : knownModifiers){
				ISkillTreeEntry part = SkillManager.instance.getSkill(id);
				if (part instanceof ISpellModifier){
					boolean useful = am2.spell.SpellInscriptionAnalyzer.isModifierUsefulForLastStage(
							(ISpellModifier)part, groups, recipe, am2.spell.SpellInscriptionAnalyzer.COMPENDIUM_LOOKUP);
					modifierUsefulCache.put(part.getID(), useful);
				}
			}
		}
	}

	@Override
	public void handleMouseInput(){
		super.handleMouseInput();
		int wheel = org.lwjgl.input.Mouse.getEventDWheel();
		if (wheel != 0 && maxScrollRow > 0 && lastMouseX >= 35 && lastMouseX <= 202 && lastMouseY >= 0 && lastMouseY <= 58){
			scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow + (wheel > 0 ? -1 : 1)));
		}
	}

	@Override
	protected void actionPerformed(GuiButton par1GuiButton){
		if (par1GuiButton == createSpellButton && usingPlayer.capabilities.isCreativeMode){
			((ContainerInscriptionTable)this.inventorySlots).giveSpellToPlayer(usingPlayer);
		}else if (par1GuiButton == resetSpellButton){
			((ContainerInscriptionTable)this.inventorySlots).resetSpellNameAndIcon();
		}
	}

	@Override
	protected void mouseClicked(int par1, int par2, int par3){
		super.mouseClicked(par1, par2, par3);
		int l = (width - xSize) / 2;
		int i1 = (height - ySize) / 2;
		par1 -= l;
		par2 -= i1;

		// Left-docked category tabs, affinity filter, and upper-grid scroll arrows
		am2.guis.grimoire.InscriptionBrowserOverlay.Tab clickedTab = browserOverlay.tabAt(l, par1, par2);
		if (clickedTab != null){
			activeTab = clickedTab;
			if (activeTab == am2.guis.grimoire.InscriptionBrowserOverlay.Tab.SHAPES || activeTab == am2.guis.grimoire.InscriptionBrowserOverlay.Tab.MODIFIERS){
				activeAffinity = null;
			}
			scrollRow = 0;
			return;
		}
		int affSlot = browserOverlay.affinitySlotAt(l, activeTab, presentAffinities, par1, par2);
		if (affSlot >= 0){
			if (affSlot == 0){
				activeAffinity = null;
			}else{
				am2.api.spell.enums.Affinity picked = presentAffinities.get(affSlot - 1);
				activeAffinity = (activeAffinity == picked) ? null : picked;
			}
			scrollRow = 0;
			return;
		}
		int scrollDelta = browserOverlay.scrollClick(maxScrollRow, par1, par2);
		if (scrollDelta != 0){
			scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow + scrollDelta));
			return;
		}

		if (hoveredItem != null && hoveredIcon != null){
			if (spellPartIsValidAddition(hoveredItem) && !lowerHover){
				if (isShiftKeyDown() && hoveredItem instanceof ISpellPart){
					quickAddPart((ISpellPart)hoveredItem);
				}else{
					dragging = true;
				}
			}else if (lowerHover){
				if (lowerHoverShapeGroup == -1 && ((ContainerInscriptionTable)this.inventorySlots).currentRecipeContains(hoveredItem)){
					if (hoveredItem instanceof ISpellShape){
						int index = lowerHoverIndex;
						int startIndex = index;
						int count = 0;
						index++;
						while (index < ((ContainerInscriptionTable)this.inventorySlots).getCurrentRecipeSize() && !(((ContainerInscriptionTable)this.inventorySlots).getRecipeItemAt(index) instanceof ISpellShape)){
							count++;
							index++;
						}
						((ContainerInscriptionTable)this.inventorySlots).removeMultipleRecipeParts(startIndex, count);
					}else{
						((ContainerInscriptionTable)this.inventorySlots).removeSingleRecipePart(lowerHoverIndex);
					}
					result = ((ContainerInscriptionTable)this.inventorySlots).validateCurrentDefinition();
					refreshAnalysis();
				}else if (lowerHoverShapeGroup >= 0){
					if (hoveredItem instanceof ISpellShape){
						int index = lowerHoverIndex;
						int startIndex = index;
						int count = 0;
						index++;
						while (index < ((ContainerInscriptionTable)this.inventorySlots).getShapeGroupSize(lowerHoverShapeGroup) &&
								!(((ContainerInscriptionTable)this.inventorySlots).getShapeGroupPartAt(lowerHoverShapeGroup, index) instanceof ISpellShape)){
							count++;
							index++;
						}
						((ContainerInscriptionTable)this.inventorySlots).removeMultipleRecipePartsFromGroup(lowerHoverShapeGroup, startIndex, count);
					}else{
						((ContainerInscriptionTable)this.inventorySlots).removeSingleRecipePartFromGroup(lowerHoverShapeGroup, lowerHoverIndex);
					}
					result = ((ContainerInscriptionTable)this.inventorySlots).validateCurrentDefinition();
					refreshAnalysis();
				}
			}
		}else{
			boolean boxClick = false;
			if (par1 >= searchFieldPosition.iX && par1 <= searchFieldPosition.iX + searchFieldDimensions.iX){
				if (par2 >= searchFieldPosition.iY && par2 <= searchFieldPosition.iY + searchFieldDimensions.iY){
					if (par3 == 1 || searchBar.getText().equals(defaultSearchLabel)){
						searchBar.setText("");
					}
					if (nameBar.getText().equals("")){
						nameBar.setText(defaultNameLabel);
					}
					boxClick = true;
				}
			}
			if (par1 >= nameFieldPosition.iX && par1 <= nameFieldPosition.iX + nameFieldDimensions.iX){
				if (par2 >= nameFieldPosition.iY && par2 <= nameFieldPosition.iY + nameFieldDimensions.iY){
					if (par3 == 1 || nameBar.getText().equals(defaultNameLabel)){
						nameBar.setText("");
						((ContainerInscriptionTable)this.inventorySlots).setSpellName(nameBar.getText());
					}
					if (searchBar.getText().equals("")){
						searchBar.setText(defaultSearchLabel);
					}
					boxClick = true;
				}
			}
			if (!boxClick){
				if (nameBar.getText().equals("")){
					nameBar.setText(defaultNameLabel);
				}
				if (searchBar.getText().equals("")){
					searchBar.setText(defaultSearchLabel);
				}
			}
			searchBar.mouseClicked(par1, par2, par3);
			nameBar.mouseClicked(par1, par2, par3);
		}
	}

	@Override
	protected void mouseMovedOrUp(int x, int y, int action){
		super.mouseMovedOrUp(x, y, action);

		int l = (width - xSize) / 2;
		int i1 = (height - ySize) / 2;
		x -= l;
		y -= i1;

		if (action == 0 || action == 1){
			if (dragging){
				dragging = false;
				//lower section
				if (x >= IIconXStart_lower && x <= IIconXStart_lower + 150){
					if (y >= IIconYStart_lower && y <= IIconYStart_lower + 18){
						((ContainerInscriptionTable)this.inventorySlots).addRecipePart((ISpellPart)hoveredItem);
						result = ((ContainerInscriptionTable)this.inventorySlots).validateCurrentDefinition();
						refreshAnalysis();
					}
				}
				//spell stage groups
				int sg = ((ContainerInscriptionTable)this.inventorySlots).getNumStageGroups();
				for (int i = 0; i < sg; ++i){
					int SGX = shapeGroupX + ((shapeGroupWidth + shapeGroupPadding) * i);
					int SGY = shapeGroupY;
					if (x >= SGX && x <= SGX + shapeGroupWidth){
						if (y >= SGY && y <= SGY + shapeGroupWidth){
							((ContainerInscriptionTable)this.inventorySlots).addRecipePartToGroup(i, (ISpellPart)hoveredItem);
							result = ((ContainerInscriptionTable)this.inventorySlots).validateCurrentDefinition();
							refreshAnalysis();
						}
					}
				}
			}
		}
	}

	private void drawDropZones(){

		int l = (width - xSize) / 2;
		int i1 = (height - ySize) / 2;

		drawRectangle(l + IIconXStart_upper, i1 + IIconYStart_upper, 150, 60, 0xFF0000);
		drawRectangle(l + IIconXStart_lower, i1 + IIconYStart_lower, 150, 20, 0xFF0000);

		int sg = ((ContainerInscriptionTable)this.inventorySlots).getNumStageGroups();
		for (int i = 0; i < sg; ++i){
			int SGX = l + shapeGroupX + ((shapeGroupWidth + shapeGroupPadding) * i);
			int SGY = i1 + shapeGroupY;

			drawRectangle(SGX, SGY, shapeGroupWidth, shapeGroupWidth, 0xFF0000);
		}
	}

	private void drawRectangle(int x, int y, int width, int height, int color){
		AMGuiHelper.line2d(x, y, x + width, y, this.zLevel, color);
		AMGuiHelper.line2d(x + width, y, x + width, y + height, this.zLevel, color);
		AMGuiHelper.line2d(x, y + height, x + width, y + height, this.zLevel, color);
		AMGuiHelper.line2d(x, y, x, y + height, this.zLevel, color);
	}

	@Override
	protected void keyTyped(char par1, int par2){
		if (searchBar.textboxKeyTyped(par1, par2)){
			scrollRow = 0;
		}else if (nameBar.textboxKeyTyped(par1, par2)){
			((ContainerInscriptionTable)this.inventorySlots).setSpellName(nameBar.getText());
		}else{
			super.keyTyped(par1, par2);
		}
	}

	private void quickAddPart(ISpellPart part){
		ContainerInscriptionTable container = (ContainerInscriptionTable)this.inventorySlots;
		boolean anyGroupHasShape = false;
		for (int g = 0; g < container.getNumStageGroups(); ++g){
			for (int n = 0; n < container.getShapeGroupSize(g); ++n){
				if (container.getShapeGroupPartAt(g, n) instanceof ISpellShape){
					anyGroupHasShape = true;
					break;
				}
			}
		}
		if (part instanceof ISpellShape && !anyGroupHasShape && container.getNumStageGroups() > 0 && container.getShapeGroupSize(0) < 4){
			container.addRecipePartToGroup(0, part);
		}else{
			container.addRecipePart(part);
		}
		result = container.validateCurrentDefinition();
		refreshAnalysis();
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float f, int i, int j){
		mc.renderEngine.bindTexture(background);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		int l = (width - xSize) / 2;
		int i1 = (height - ySize) / 2;
		drawTexturedModalRect(l, i1, 0, 0, xSize, 165);
		drawTexturedModalRect(l + 22, i1 + 165, 0, 165, 176, 87);


		int offsetX = l + shapeGroupX;

		for (int sg = 0; sg < TileEntityInscriptionTable.MAX_STAGE_GROUPS; ++sg){
			if (sg >= ((ContainerInscriptionTable)this.inventorySlots).getNumStageGroups())
				GL11.glColor3f(0.5f, 0.5f, 0.5f);
			drawTexturedModalRect(offsetX + (sg * (shapeGroupWidth + shapeGroupPadding)), i1 + shapeGroupY, 176, 165, 37, 37);
		}

		GL11.glColor3f(1f, 1f, 1f);

		drawTexturedModalRect(l + 101, i1 + 73, 220, 0, 18, 18);

		lastMouseX = i - l;
		lastMouseY = j - i1;
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int par1, int par2){
		super.drawGuiContainerForegroundLayer(par1, par2);
		if (recipeSignature() != analyzedSignature){
			result = ((ContainerInscriptionTable)this.inventorySlots).validateCurrentDefinition();
			refreshAnalysis();
		}

		ArrayList<String> label = new ArrayList<String>();

		int l = (width - xSize) / 2;
		int i1 = (height - ySize) / 2;

		this.zLevel = 0.0F;

		GL11.glEnable(GL11.GL_ALPHA_TEST);

		drawBookIcon();
		boolean hovering = false;
		if (drawAvailableParts(label)){
			hovering = true;
			lowerHover = false;
		}
		if (drawCurrentRecipe(label, l, i1)){
			hovering = true;
			lowerHover = true;
		}
		searchBar.drawTextBox();
		nameBar.drawTextBox();

		boolean bookOut = false;
		if (result.valid){
			if (((ContainerInscriptionTable)this.inventorySlots).slotHasStack(0)){
				if (((ContainerInscriptionTable)this.inventorySlots).slotIsBook(0)){
					bookOut = true;
				}else{
					resetSpellButton.visible = true;
				}

			}else{
				resetSpellButton.visible = false;
			}
			createSpellButton.enabled = true;
		}else{
			if (((ContainerInscriptionTable)this.inventorySlots).slotHasStack(0) && !((ContainerInscriptionTable)this.inventorySlots).slotIsBook(0)){
				resetSpellButton.visible = true;
			}else{
				resetSpellButton.visible = false;
			}
			createSpellButton.enabled = false;
		}

		int availRight = width - (l + xSize + 4);
		List<String> browserTip = browserOverlay.draw(Minecraft.getMinecraft(), Minecraft.getMinecraft().fontRenderer, l,
				activeTab, activeAffinity, presentAffinities, scrollRow, maxScrollRow, lastMouseX, lastMouseY);
		List<String> panelTip = previewPanel.draw(Minecraft.getMinecraft().fontRenderer, xSize + 5, 2, availRight,
				analysis, result, bookOut, usingPlayer, lastMouseX, lastMouseY);
		if (availRight < 74){
			// Narrow screen fallback: preserve original status string
			if (bookOut){
				Minecraft.getMinecraft().fontRenderer.drawSplitString(StatCollector.translateToLocal("am2.gui.bookOut"), 225, 5, 100, 0xFF7700);
			}else if (!result.valid){
				Minecraft.getMinecraft().fontRenderer.drawSplitString(result.message, 225, 5, 100, 0xFF7700);
			}
		}

		if (!dragging){
			if (hovering){
				drawHoveringText(label, lastMouseX, lastMouseY, Minecraft.getMinecraft().fontRenderer);
			}else if (panelTip != null && !panelTip.isEmpty()){
				drawHoveringText(panelTip, lastMouseX, lastMouseY, Minecraft.getMinecraft().fontRenderer);
			}else if (browserTip != null && !browserTip.isEmpty()){
				drawHoveringText(browserTip, lastMouseX, lastMouseY, Minecraft.getMinecraft().fontRenderer);
			}else{
				hoveredItem = null;
				hoveredIcon = null;
			}
		}else{
			GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
			drawDraggedItem();
		}

	}

	private void drawBookIcon(){
		int bookX = this.inventorySlots.getSlot(0).xDisplayPosition;
		int bookY = this.inventorySlots.getSlot(0).yDisplayPosition;

		IIcon icon = Items.writable_book.getIconFromDamage(0);

		if (AMGuiHelper.instance.getFastTicker() < 20)
			GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.4f);
		else
			GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.7f);
		AMGuiHelper.DrawIconAtXY(icon, bookX, bookY, this.zLevel, 16, 16, true);
		GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
	}

	private boolean drawCurrentRecipe(ArrayList<String> labelText, int l, int i1){

		iconX = IIconXStart_lower;
		iconY = IIconYStart_lower;

		boolean hovering = false;
		int index = 0;

		ArrayList<ISpellPart> mainRecipeParts = new ArrayList<ISpellPart>();
		//main recipe
		for (int i = 0; i < ((ContainerInscriptionTable)this.inventorySlots).getCurrentRecipeSize(); ++i){
			ISpellPart part = ((ContainerInscriptionTable)this.inventorySlots).getRecipeItemAt(i);
			// missingShape, missingComponent, missingModifier check. Temporary solution until someone figures out a good API.
			if (SkillManager.instance.getSkillName(part) == null){
				continue;
			}
			mainRecipeParts.add(part);
			String name = SkillManager.instance.getDisplayName(part);

			int drawAtX = iconX;
			int drawAtY = iconY;
			if (drawIcon(part, false)){
				appendPartTooltip(part, name, labelText, true, index);
				hovering = true;
				lowerHoverIndex = index;
				lowerHoverShapeGroup = -1;
			}
			if (analysis != null && part instanceof ISpellModifier && analysis.isUnused((ISpellModifier) part)){
				am2.guis.grimoire.InscriptionPreviewPanel.drawUnusedWarningMarker(drawAtX, drawAtY);
			}
			index++;
		}
		am2.guis.grimoire.InscriptionBrowserOverlay.drawStageBrackets(
				Minecraft.getMinecraft().fontRenderer, mainRecipeParts, IIconXStart_lower, IIconYStart_lower, IIconStep);

		//shape groups
		for (int i = 0; i < ((ContainerInscriptionTable)this.inventorySlots).getNumStageGroups(); ++i){
			for (int n = 0; n < ((ContainerInscriptionTable)this.inventorySlots).getShapeGroupSize(i); ++n){
				ISpellPart part = ((ContainerInscriptionTable)this.inventorySlots).getShapeGroupPartAt(i, n);
				// missingShape, missingComponent, missingModifier check. Temporary solution until someone figures out a good API.
				if (SkillManager.instance.getSkillName(part) == null) {
					continue;
				}
				String name = SkillManager.instance.getDisplayName(part);

				int SGX = shapeGroupX + ((shapeGroupWidth + shapeGroupPadding) * i) + 1;
				int SGY = shapeGroupY;

				iconX = SGX + (n % 2) * IIconStep;
				iconY = SGY + (int)Math.floor(n / 2) * IIconStep;

				int drawAtX = iconX;
				int drawAtY = iconY;
				if (drawIcon(part, false)){
					appendPartTooltip(part, name, labelText, true, -1);
					hovering = true;
					lowerHoverIndex = n;
					lowerHoverShapeGroup = i;
				}
				if (analysis != null && part instanceof ISpellModifier && analysis.isUnused((ISpellModifier) part)){
					am2.guis.grimoire.InscriptionPreviewPanel.drawUnusedWarningMarker(drawAtX, drawAtY);
				}
			}
		}
		return hovering;
	}

	private boolean drawAvailableParts(ArrayList<String> labelText){
		iconX = IIconXStart_upper;
		iconY = IIconYStart_upper;
		return drawPartIcons(labelText);
	}

	private boolean drawPartIcons(ArrayList<String> labelText){
		ArrayList<ISkillTreeEntry> filtered = new ArrayList<ISkillTreeEntry>();
		if (activeTab == am2.guis.grimoire.InscriptionBrowserOverlay.Tab.ALL || activeTab == am2.guis.grimoire.InscriptionBrowserOverlay.Tab.SHAPES || activeTab == am2.guis.grimoire.InscriptionBrowserOverlay.Tab.SILVER){
			if (activeAffinity == null) collectFilteredParts(knownShapes, filtered);
		}
		if (activeTab == am2.guis.grimoire.InscriptionBrowserOverlay.Tab.ALL || activeTab == am2.guis.grimoire.InscriptionBrowserOverlay.Tab.COMPONENTS || activeTab == am2.guis.grimoire.InscriptionBrowserOverlay.Tab.SILVER){
			collectFilteredParts(knownComponents, filtered);
		}
		if (activeTab == am2.guis.grimoire.InscriptionBrowserOverlay.Tab.ALL || activeTab == am2.guis.grimoire.InscriptionBrowserOverlay.Tab.MODIFIERS || activeTab == am2.guis.grimoire.InscriptionBrowserOverlay.Tab.SILVER){
			if (activeAffinity == null) collectFilteredParts(knownModifiers, filtered);
		}

		int cols = 8;
		int visibleRows = 3;
		int totalRows = (filtered.size() + cols - 1) / cols;
		maxScrollRow = Math.max(0, totalRows - visibleRows);
		if (scrollRow > maxScrollRow) scrollRow = maxScrollRow;
		if (scrollRow < 0) scrollRow = 0;

		int startIdx = scrollRow * cols;
		int endIdx = Math.min(filtered.size(), startIdx + cols * visibleRows);
		boolean hovering = false;
		for (int idx = startIdx; idx < endIdx; ++idx){
			ISkillTreeEntry part = filtered.get(idx);
			String name = SkillManager.instance.getDisplayName(part);
			if (drawIcon(part)){
				hovering = true;
				appendPartTooltip(part, name, labelText, false, -1);
			}
		}
		return hovering;
	}

	private void collectFilteredParts(Integer[] ids, ArrayList<ISkillTreeEntry> out){
		String filterText = searchBar.getText();
		boolean searching = !filterText.equalsIgnoreCase(defaultSearchLabel) && !filterText.trim().isEmpty();
		for (Integer i : ids){
			ISkillTreeEntry part = SkillManager.instance.getSkill(i);
			if (part == null || SkillManager.instance.getSkillName(part) == null || SkillTreeManager.instance.isSkillDisabled(part)){
				continue;
			}
			if (activeTab == am2.guis.grimoire.InscriptionBrowserOverlay.Tab.SILVER){
				if (!SkillTreeManager.instance.isSilverSkill(part) && !SkillTreeManager.instance.isGoldenSkill(part)){
					continue;
				}
			}
			if (activeAffinity != null && !am2.guis.grimoire.InscriptionBrowserOverlay.matchesAffinity(part, activeAffinity)){
				continue;
			}
			String name = SkillManager.instance.getDisplayName(part);
			if (searching && !SpellPartSearch.INSTANCE.matches(name, SkillManager.instance.getSkillName(part), filterText)){
				continue;
			}
			out.add(part);
		}
	}

	private boolean spellPartIsValidAddition(ISkillTreeEntry part){
		boolean hasShape = false;
		for (int i = 0; i < ((ContainerInscriptionTable)this.inventorySlots).getNumStageGroups(); ++i){
			for (int n = 0; n < ((ContainerInscriptionTable)this.inventorySlots).getShapeGroupSize(i); ++n){
				ISpellPart groupPart = ((ContainerInscriptionTable)this.inventorySlots).getShapeGroupPartAt(i, n);
				if (groupPart instanceof ISpellShape){
					hasShape = true;
					break;
				}
			}
		}
		if (!hasShape && !(part instanceof ISpellShape))
			return false;
		if (part instanceof ISpellShape && ((ContainerInscriptionTable)this.inventorySlots).currentRecipeContains(part))
			return false;
		if (part instanceof ISpellComponent){
			int index = ((ContainerInscriptionTable)this.inventorySlots).getCurrentRecipeSize() - 1;
			while (index >= 0 && !(((ContainerInscriptionTable)this.inventorySlots).getRecipeItemAt(index) instanceof ISpellShape)){
				ISpellPart curPart = ((ContainerInscriptionTable)this.inventorySlots).getRecipeItemAt(index--);
				if (curPart instanceof ISpellComponent && curPart.getID() == part.getID()){
					return false;
				}
			}
		}
		if (part instanceof ISpellModifier){
			return ((ContainerInscriptionTable)this.inventorySlots).modifierCanBeAdded((ISpellModifier)part);
		}
		return true;
	}

	private boolean drawIcon(ISkillTreeEntry part){
		return drawIcon(part, true);
	}

	private boolean drawIcon(ISkillTreeEntry part, boolean allowDarken){

		boolean hovering = false;
		IIcon shapeIcon = SpellIconManager.instance.getIcon(SkillManager.instance.getSkillName(part));

		if (shapeIcon == null)
			return false;

		if (!currentSpellDefIsReadOnly()){
			if (!spellPartIsValidAddition(part) && allowDarken){
				GL11.glColor3f(0.3f, 0.3f, 0.3f);
			}else{
				GL11.glColor3f(1.0f, 1.0f, 1.0f);
			}
		}else{
			GL11.glColor3f(1.0f, 0.7f, 0.7f);
		}

		AMGuiHelper.DrawIconAtXY(shapeIcon, iconX, iconY, this.zLevel, 16, 16, false);
		if (SkillTreeManager.instance.isGoldenSkill(part)){
			int gColor = (AMGuiHelper.instance.getFastTicker() % 20 < 10) ? 0xFFE564 : 0xD49418;
			drawOctagonOutline(iconX, iconY, 16, 4, gColor);
		}else{
			boolean hasRecipe = analysis != null && analysis.totalParts > 0;
			boolean modUseful = (part instanceof ISpellModifier) && Boolean.TRUE.equals(modifierUsefulCache.get(part.getID()));
			am2.guis.grimoire.InscriptionBrowserOverlay.drawSubtlePartMarker(
					part, iconX, iconY,
					allowDarken && activeTab == am2.guis.grimoire.InscriptionBrowserOverlay.Tab.ALL,
					allowDarken && hasRecipe,
					modUseful,
					AMGuiHelper.instance.getFastTicker());
		}

		if (!dragging){
			if (lastMouseX > iconX && lastMouseX < iconX + 16){
				if (lastMouseY > iconY && lastMouseY < iconY + 16){
					hoveredItem = part;
					hoveredIcon = shapeIcon;
					hovering = true;
				}
			}
		}

		iconX += IIconStep;
		if (iconX >= 175){
			iconX = IIconXStart_upper;
			iconY += 17;
		}

		return hovering;
	}

	private void drawDraggedItem(){
		AMGuiHelper.DrawIconAtXY(hoveredIcon, lastMouseX - 8, lastMouseY - 8, this.zLevel, 16, 16, false);
	}

	@Override
	protected void drawHoveringText(List par1List, int par2, int par3, FontRenderer font){
		if (!par1List.isEmpty()){
			GL11.glDisable(GL12.GL_RESCALE_NORMAL);
			RenderHelper.disableStandardItemLighting();
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			int k = 0;
			Iterator iterator = par1List.iterator();

			while (iterator.hasNext()){
				String s = (String)iterator.next();
				int l = font.getStringWidth(s);

				if (l > k){
					k = l;
				}
			}

			int i1 = par2 + 12;
			int j1 = par3 - 12;
			int k1 = 8;

			if (par1List.size() > 1){
				k1 += 2 + (par1List.size() - 1) * 10;
			}

			if (i1 + k > this.width){
				i1 -= 28 + k;
			}

			if (j1 + k1 + 6 > this.height){
				j1 = this.height - k1 - 6;
			}

			this.zLevel = 300.0F;
			int l1 = -267386864;
			this.drawGradientRect(i1 - 3, j1 - 4, i1 + k + 3, j1 - 3, l1, l1);
			this.drawGradientRect(i1 - 3, j1 + k1 + 3, i1 + k + 3, j1 + k1 + 4, l1, l1);
			this.drawGradientRect(i1 - 3, j1 - 3, i1 + k + 3, j1 + k1 + 3, l1, l1);
			this.drawGradientRect(i1 - 4, j1 - 3, i1 - 3, j1 + k1 + 3, l1, l1);
			this.drawGradientRect(i1 + k + 3, j1 - 3, i1 + k + 4, j1 + k1 + 3, l1, l1);
			int i2 = 1347420415;
			int j2 = (i2 & 16711422) >> 1 | i2 & -16777216;
			this.drawGradientRect(i1 - 3, j1 - 3 + 1, i1 - 3 + 1, j1 + k1 + 3 - 1, i2, j2);
			this.drawGradientRect(i1 + k + 2, j1 - 3 + 1, i1 + k + 3, j1 + k1 + 3 - 1, i2, j2);
			this.drawGradientRect(i1 - 3, j1 - 3, i1 + k + 3, j1 - 3 + 1, i2, i2);
			this.drawGradientRect(i1 - 3, j1 + k1 + 2, i1 + k + 3, j1 + k1 + 3, j2, j2);

			for (int k2 = 0; k2 < par1List.size(); ++k2){
				String s1 = (String)par1List.get(k2);
				font.drawStringWithShadow(s1, i1, j1, -1);

				if (k2 == 0){
					j1 += 2;
				}

				j1 += 10;
			}

			this.zLevel = 0.0F;
			GL11.glEnable(GL11.GL_LIGHTING);
			GL11.glEnable(GL11.GL_DEPTH_TEST);
			RenderHelper.enableStandardItemLighting();
			GL11.glEnable(GL12.GL_RESCALE_NORMAL);
		}
	}

	private void drawOctagonOutline(int x, int y, int size, int chamfer, int color){
		int x1 = x + chamfer;
		int x2 = x + size - chamfer;
		int x3 = x + size;
		int y1 = y + chamfer;
		int y2 = y + size - chamfer;
		int y3 = y + size;
		AMGuiHelper.line2d(x1, y, x2, y, this.zLevel, color);
		AMGuiHelper.line2d(x2, y, x3, y1, this.zLevel, color);
		AMGuiHelper.line2d(x3, y1, x3, y2, this.zLevel, color);
		AMGuiHelper.line2d(x3, y2, x2, y3, this.zLevel, color);
		AMGuiHelper.line2d(x2, y3, x1, y3, this.zLevel, color);
		AMGuiHelper.line2d(x1, y3, x, y2, this.zLevel, color);
		AMGuiHelper.line2d(x, y2, x, y1, this.zLevel, color);
		AMGuiHelper.line2d(x, y1, x1, y, this.zLevel, color);
	}

	private void appendPartTooltip(ISkillTreeEntry part, String displayName, ArrayList<String> labelText, boolean inRecipe, int recipeIndex){
		boolean isGolden = SkillTreeManager.instance.isGoldenSkill(part);
		boolean isSilver = SkillTreeManager.instance.isSilverSkill(part);
		if (isGolden){
			labelText.add("\u00a76\u2605 " + displayName);
			labelText.add("\u00a76" + StatCollector.translateToLocal("am2.gui.goldenSkill"));
		}else if (isSilver){
			labelText.add("\u00a7b\u2726 " + displayName);
			labelText.add("\u00a7b" + am2.guis.grimoire.GrimoireLang.tr("am2.inscription.silverSkill"));
		}else{
			labelText.add(displayName);
		}

		// Live part info: cost, affinity, multiplier, and modifier compatibility
		if (part instanceof ISpellComponent){
			ISpellComponent comp = (ISpellComponent) part;
			am2.api.spell.enums.Affinity aff = am2.guis.grimoire.InscriptionBrowserOverlay.primaryAffinity(part);
			if (aff != null){
				labelText.add("\u00a77" + am2.guis.grimoire.GrimoireLang.tr("am2.inscription.panel.affinity") + ": \u00a7f"
						+ am2.guis.grimoire.GrimoireLang.affinityName(aff));
			}
			labelText.add("\u00a7b" + am2.guis.grimoire.GrimoireLang.trFmt("am2.inscription.tip.mana",
					String.format(java.util.Locale.ROOT, "%.0f", comp.manaCost(usingPlayer)),
					String.format(java.util.Locale.ROOT, "%.0f", comp.burnout(usingPlayer))));
		}else if (part instanceof ISpellShape){
			try{
				float mult = ((ISpellShape) part).manaCostMultiplier(null);
				labelText.add("\u00a77" + am2.guis.grimoire.GrimoireLang.trFmt("am2.inscription.tip.shapeMult",
						String.format(java.util.Locale.ROOT, "%.2f", mult)));
			}catch (Throwable ignored){
			}
		}else if (part instanceof ISpellModifier){
			ISpellModifier mod = (ISpellModifier) part;
			if (inRecipe && analysis != null && analysis.isUnused(mod)){
				labelText.add("\u00a7c" + am2.guis.grimoire.GrimoireLang.tr("am2.inscription.tip.modUnused"));
			}else if (!inRecipe && analysis != null && analysis.totalParts > 0){
				boolean useful = Boolean.TRUE.equals(modifierUsefulCache.get(mod.getID()));
				labelText.add((useful ? "\u00a7a" : "\u00a7e") + am2.guis.grimoire.GrimoireLang.tr(
						useful ? "am2.inscription.tip.modUseful" : "am2.inscription.tip.modUnused"));
			}
		}

		if (!inRecipe && !currentSpellDefIsReadOnly() && spellPartIsValidAddition(part)){
			labelText.add("\u00a78" + am2.guis.grimoire.GrimoireLang.tr("am2.inscription.tip.shiftAdd"));
		}

		if (isGolden && part instanceof Tornado){
			if (inRecipe && recipeIndex >= 0){
				ContainerInscriptionTable container = (ContainerInscriptionTable) this.inventorySlots;
				int total = container.getCurrentRecipeSize();
				int start = 0;
				for (int k = recipeIndex; k >= 0; k--){
					if (container.getRecipeItemAt(k) instanceof ISpellShape){
						start = k;
						break;
					}
				}
				int end = total;
				for (int k = recipeIndex + 1; k < total; k++){
					if (container.getRecipeItemAt(k) instanceof ISpellShape){
						end = k;
						break;
					}
				}
				ArrayList<ISpellPart> stageParts = new ArrayList<ISpellPart>();
				for (int k = start; k < end; k++){
					stageParts.add(container.getRecipeItemAt(k));
				}
				int[] counts = Tornado.inspectTierModifiersFromParts(stageParts);
				int tier = Tornado.computeFujitaTier(counts[0], counts[1], counts[2], counts[3]);
				byte elem = Tornado.detectElementalSynergyFromParts(stageParts);
				float minRad = Tornado.getTierMinRadius(tier);
				float dmgMult = Tornado.getTierDamageMultiplier(tier);

				labelText.add("\u00a7e" + StatCollector.translateToLocalFormatted("am2.gui.tornadoPreviewTier", tier));
				labelText.add("\u00a7b" + StatCollector.translateToLocalFormatted("am2.gui.tornadoPreviewForm", StatCollector.translateToLocal(Tornado.getElementLangKey(elem))));
				labelText.add("\u00a77" + StatCollector.translateToLocalFormatted("am2.gui.tornadoPreviewStats", String.format("%.1f", minRad), String.format("%.2f", dmgMult)));
			}else{
				labelText.add("\u00a7e\u00a7o" + StatCollector.translateToLocal("am2.gui.goldenTornadoScale"));
			}
		}
	}

	private boolean currentSpellDefIsReadOnly(){
		return ((ContainerInscriptionTable)this.inventorySlots).currentSpellDefIsReadOnly();
	}
}
