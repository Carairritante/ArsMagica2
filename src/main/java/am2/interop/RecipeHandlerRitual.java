package am2.interop;

import am2.api.spell.component.interfaces.ISkillTreeEntry;
import am2.blocks.BlocksCommonProxy;
import am2.items.ItemEssence;
import am2.items.ItemOre;
import am2.items.ItemsCommonProxy;
import am2.spell.SkillManager;
import codechicken.lib.gui.GuiDraw;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * NEI Recipe/Usage handler for custom AM2 Rituals such as the Astral Divination Ritual.
 */
public class RecipeHandlerRitual extends TemplateRecipeHandler {

	public class CachedRitualRecipe extends CachedRecipe {
		public final List<PositionedStack> inputs = new ArrayList<PositionedStack>();
		public final PositionedStack output;
		public final String titleKey;
		public final String reqKey;

		public CachedRitualRecipe(
				ItemStack centerFocus,
				ItemStack spellCatalyst,
				ItemStack top,
				ItemStack left,
				ItemStack right,
				ItemStack bottom,
				ItemStack outputStack,
				String titleKey,
				String reqKey
		) {
			this.inputs.add(new PositionedStack(spellCatalyst, 18, 3));
			this.inputs.add(new PositionedStack(top, 75, 30));
			this.inputs.add(new PositionedStack(left, 42, 63));
			this.inputs.add(new PositionedStack(centerFocus, 75, 63));
			this.inputs.add(new PositionedStack(right, 107, 63));
			this.inputs.add(new PositionedStack(bottom, 75, 95));
			this.inputs.add(new PositionedStack(new ItemStack(BlocksCommonProxy.wizardChalk, 16, 0), 18, 36));
			this.inputs.add(new PositionedStack(new ItemStack(BlocksCommonProxy.candle, 4, 0), 18, 57));
			this.output = new PositionedStack(outputStack, 138, 98);
			this.titleKey = titleKey;
			this.reqKey = reqKey;
		}

		@Override
		public List<PositionedStack> getIngredients() {
			return getCycledIngredients(cycleticks / 20, inputs);
		}

		@Override
		public PositionedStack getResult() {
			return output;
		}
	}

	@Override
	public String getRecipeName() {
		return StatCollector.translateToLocal("am2.nei.ritual.handler");
	}

	public String getRecipeID() {
		return "arsmagica2.rituals";
	}

	@Override
	public String getHandlerId() {
		return "arsmagica2.rituals";
	}

	@Override
	public String getGuiTexture() {
		return "arsmagica2:textures/guis/essenceExtractorNEI.png";
	}

	@Override
	public void loadTransferRects() {
		transferRects.add(new RecipeTransferRect(new Rectangle(18, 3, 18, 18), getRecipeID()));
	}

	@Override
	public void drawBackground(int recipe) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GuiDraw.changeTexture(this.getGuiTexture());
		GuiDraw.drawTexturedModalRect(0, 0, 5, 11, 166, 128);
	}

	@Override
	public void drawForeground(int recipe) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glDisable(GL11.GL_LIGHTING);
		this.drawExtras(recipe);
	}

	@Override
	public void drawExtras(int recipe) {
		if (recipe < 0 || recipe >= arecipes.size()) return;
		CachedRecipe raw = arecipes.get(recipe);
		if (!(raw instanceof CachedRitualRecipe)) return;
		CachedRitualRecipe cached = (CachedRitualRecipe) raw;
		String title = StatCollector.translateToLocal(cached.titleKey);
		String req = StatCollector.translateToLocal(cached.reqKey);
		GuiDraw.drawStringC(title, 96, 6, 0x1F3B66, false);
		GuiDraw.drawStringC(req, 83, 118, 0x3D235C, false);
	}

	@Override
	public int recipiesPerPage() {
		return 1;
	}

	private List<CachedRitualRecipe> buildAllRecipes() {
		if (!GuiRecipeTab.handlerMap.containsKey(getHandlerId())) {
			HandlerInfo info = new HandlerInfo.Builder(getHandlerId(), "Ars Magica 2", "arsmagica2")
					.setHeight(HandlerInfo.DEFAULT_HEIGHT)
					.setWidth(HandlerInfo.DEFAULT_WIDTH)
					.setMaxRecipesPerPage(this.recipiesPerPage())
					.setDisplayStack(new ItemStack(ItemsCommonProxy.astralCompass))
					.build();
			GuiRecipeTab.handlerMap.put(getHandlerId(), info);
		}
		List<CachedRitualRecipe> list = new ArrayList<CachedRitualRecipe>();
		ISkillTreeEntry markSkill = SkillManager.instance.getSkill("Mark");
		ItemStack markStack = markSkill != null
				? new ItemStack(ItemsCommonProxy.spell_component, 1, SkillManager.instance.getShiftedPartID(markSkill))
				: new ItemStack(ItemsCommonProxy.wizardChalk);
		list.add(new CachedRitualRecipe(
				new ItemStack(Blocks.lapis_block),
				markStack,
				new ItemStack(Items.compass, 1, 0),
				new ItemStack(ItemsCommonProxy.essence, 1, ItemEssence.META_ENDER),
				new ItemStack(ItemsCommonProxy.essence, 1, ItemEssence.META_ARCANE),
				new ItemStack(ItemsCommonProxy.itemOre, 1, ItemOre.META_MOONSTONE),
				new ItemStack(ItemsCommonProxy.astralCompass),
				"am2.architect.ritual.astral_divination",
				"am2.nei.ritual.astral_divination.req"
		));
		return list;
	}

	@Override
	public void loadCraftingRecipes(String outputId, Object... results) {
		if (outputId.equals(getRecipeID())) {
			arecipes.addAll(buildAllRecipes());
		} else {
			super.loadCraftingRecipes(outputId, results);
		}
	}

	@Override
	public void loadCraftingRecipes(ItemStack result) {
		if (result == null) return;
		for (CachedRitualRecipe recipe : buildAllRecipes()) {
			ItemStack out = recipe.output.item;
			if (out != null && (NEIServerUtils.areStacksSameType(out, result)
					|| (out.stackTagCompound == null && NEIServerUtils.areStacksSameTypeCrafting(out, result)))) {
				arecipes.add(recipe);
			}
		}
	}

	@Override
	public void loadUsageRecipes(ItemStack ingredient) {
		if (ingredient == null) return;
		for (CachedRitualRecipe recipe : buildAllRecipes()) {
			if (recipe.contains(recipe.inputs, ingredient)) {
				arecipes.add(recipe);
			}
		}
	}
}
