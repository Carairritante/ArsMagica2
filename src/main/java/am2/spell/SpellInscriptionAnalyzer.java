package am2.spell;

import am2.api.power.PowerTypes;
import am2.api.spell.component.interfaces.ISkillTreeEntry;
import am2.api.spell.component.interfaces.ISpellComponent;
import am2.api.spell.component.interfaces.ISpellModifier;
import am2.api.spell.component.interfaces.ISpellPart;
import am2.api.spell.component.interfaces.ISpellShape;
import am2.api.spell.enums.Affinity;
import am2.api.spell.enums.SpellModifiers;
import am2.items.ItemsCommonProxy;
import am2.lore.ArcaneCompendium;
import am2.lore.CompendiumEntry;
import am2.lore.CompendiumEntrySpellComponent;
import am2.lore.CompendiumEntrySpellShape;
import am2.playerextensions.AffinityData;
import am2.spell.SpellValidator;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Live spell-recipe analyzer for the Inscription Table:
 * computes exact base and affinity-discounted mana cost, burnout, dominant affinity,
 * altar complexity tier, unused modifiers per stage, and altar reagent summary using
 * the same formulas as {@link SpellUtils}.
 */
public final class SpellInscriptionAnalyzer {

    public interface ModifiedByLookup {
        /**
         * Returns the accepted {@link SpellModifiers} for a shape or component,
         * or {@code null} when metadata is unavailable (treated conservatively as unknown).
         */
        SpellModifiers[] acceptedModifiers(ISpellPart part);
    }

    public static final ModifiedByLookup COMPENDIUM_LOOKUP = new ModifiedByLookup() {
        @Override
        public SpellModifiers[] acceptedModifiers(ISpellPart part) {
            if (part == null) return null;
            String skillName = SkillManager.instance.getSkillName(part);
            if (skillName == null || skillName.isEmpty()) return null;
            CompendiumEntry entry = ArcaneCompendium.instance.getEntry(skillName);
            if (entry instanceof CompendiumEntrySpellComponent) {
                return ((CompendiumEntrySpellComponent) entry).getModifiedBy();
            }
            if (entry instanceof CompendiumEntrySpellShape) {
                return ((CompendiumEntrySpellShape) entry).getModifiedBy();
            }
            return null;
        }
    };

    public static final class Analysis {
        public final int totalParts;
        public final int stageCount;
        public final float minManaBase;
        public final float maxManaBase;
        public final float minManaEffective;
        public final float maxManaEffective;
        public final float burnout;
        public final Affinity dominantAffinity;
        public final float dominantAffinityShare;
        public final int altarTier;
        public final List<ISpellModifier> unusedModifiers;
        private final Set<ISpellModifier> unusedSet;
        public final LinkedHashMap<String, Integer> altarMaterials;
        public final int totalEtherium;

        Analysis(int totalParts, int stageCount,
                 float minManaBase, float maxManaBase,
                 float minManaEffective, float maxManaEffective,
                 float burnout, Affinity dominantAffinity, float dominantAffinityShare,
                 int altarTier, List<ISpellModifier> unusedModifiers,
                 LinkedHashMap<String, Integer> altarMaterials, int totalEtherium) {
            this.totalParts = totalParts;
            this.stageCount = stageCount;
            this.minManaBase = minManaBase;
            this.maxManaBase = maxManaBase;
            this.minManaEffective = minManaEffective;
            this.maxManaEffective = maxManaEffective;
            this.burnout = burnout;
            this.dominantAffinity = dominantAffinity;
            this.dominantAffinityShare = dominantAffinityShare;
            this.altarTier = altarTier;
            this.unusedModifiers = Collections.unmodifiableList(unusedModifiers);
            this.unusedSet = Collections.newSetFromMap(new IdentityHashMap<ISpellModifier, Boolean>());
            this.unusedSet.addAll(unusedModifiers);
            this.altarMaterials = altarMaterials;
            this.totalEtherium = totalEtherium;
        }

        public boolean isUnused(ISpellModifier modifier) {
            return modifier != null && unusedSet.contains(modifier);
        }

        public boolean hasRange() {
            return Math.abs(maxManaEffective - minManaEffective) >= 1.0f;
        }
    }

    private SpellInscriptionAnalyzer() {}

    public static Analysis analyze(List<? extends List<ISpellPart>> shapeGroups,
                                   List<ISpellPart> mainRecipe,
                                   EntityPlayer player) {
        return analyze(shapeGroups, mainRecipe, player, COMPENDIUM_LOOKUP, true);
    }

    public static Analysis analyze(List<? extends List<ISpellPart>> shapeGroups,
                                   List<ISpellPart> mainRecipe,
                                   EntityPlayer player,
                                   ModifiedByLookup lookup,
                                   boolean collectMaterials) {
        List<List<ISpellPart>> nonEmptyGroups = new ArrayList<List<ISpellPart>>();
        int totalParts = 0;
        if (shapeGroups != null) {
            for (List<ISpellPart> g : shapeGroups) {
                if (g != null && !g.isEmpty()) {
                    nonEmptyGroups.add(g);
                    totalParts += g.size();
                }
            }
        }
        List<ISpellPart> recipe = mainRecipe == null ? Collections.<ISpellPart>emptyList() : mainRecipe;
        totalParts += recipe.size();

        if (totalParts == 0) {
            return new Analysis(0, 0, 0f, 0f, 0f, 0f, 0f, Affinity.NONE, 0f, 0,
                    Collections.<ISpellModifier>emptyList(), new LinkedHashMap<String, Integer>(), 0);
        }

        // Build each effective spell sequence (one per populated shape group, or direct recipe if none)
        List<ArrayList<ArrayList<ISpellPart>>> variants = new ArrayList<ArrayList<ArrayList<ISpellPart>>>();
        List<ArrayList<ISpellPart>> sequences = new ArrayList<ArrayList<ISpellPart>>();
        if (nonEmptyGroups.isEmpty()) {
            sequences.add(new ArrayList<ISpellPart>(recipe));
            variants.add(SpellValidator.splitToStages(new ArrayList<ISpellPart>(recipe)));
        } else {
            for (List<ISpellPart> group : nonEmptyGroups) {
                ArrayList<ISpellPart> combined = new ArrayList<ISpellPart>(group.size() + recipe.size());
                combined.addAll(group);
                combined.addAll(recipe);
                sequences.add(combined);
                variants.add(SpellValidator.splitToStages(combined));
            }
        }

        // Affinity breakdown across components (matches SpellUtils.AffinityFor)
        Map<Affinity, Integer> affCounts = new HashMap<Affinity, Integer>();
        int totalAffEntries = 0;
        for (ISpellPart part : recipe) {
            if (part instanceof ISpellComponent) {
                EnumSet<Affinity> affs = ((ISpellComponent) part).getAffinity();
                if (affs != null) {
                    for (Affinity a : affs) {
                        if (a == null) continue;
                        totalAffEntries++;
                        Integer cur = affCounts.get(a);
                        affCounts.put(a, cur == null ? 1 : cur + 1);
                    }
                }
            }
        }
        Affinity dominant = Affinity.NONE;
        float dominantShare = 0f;
        Map<Affinity, Float> affShares = new HashMap<Affinity, Float>();
        if (totalAffEntries > 0) {
            for (Map.Entry<Affinity, Integer> e : affCounts.entrySet()) {
                float share = e.getValue() / (float) totalAffEntries;
                affShares.put(e.getKey(), share);
                if (e.getKey() != Affinity.NONE && share > dominantShare) {
                    dominantShare = share;
                    dominant = e.getKey();
                }
            }
        }

        AffinityData affData = null;
        if (player != null) {
            try {
                affData = AffinityData.For(player);
            } catch (Throwable ignored) {
            }
        }

        float minBase = Float.MAX_VALUE;
        float maxBase = 0f;
        float minEff = Float.MAX_VALUE;
        float maxEff = 0f;
        float burnout = 0f;
        int maxStages = 0;

        for (int v = 0; v < variants.size(); v++) {
            ArrayList<ArrayList<ISpellPart>> stages = variants.get(v);
            maxStages = Math.max(maxStages, stages.size());
            float[] costs = realSpellCost(sequences.get(v), player);
            if (costs == null) costs = computeStagesCost(stages, player);
            float baseMana = costs[0];
            float stageBurnout = costs[1];
            float effMana = applyAffinityDiscount(baseMana, affShares, affData);
            if (baseMana < minBase) minBase = baseMana;
            if (baseMana > maxBase) maxBase = baseMana;
            if (effMana < minEff) minEff = effMana;
            if (effMana > maxEff) maxEff = effMana;
            if (stageBurnout > burnout) burnout = stageBurnout;
        }
        if (minBase == Float.MAX_VALUE) minBase = 0f;
        if (minEff == Float.MAX_VALUE) minEff = 0f;

        // Detect unused modifiers across all variants (a modifier is unused only if it affects
        // NOTHING in any variant where it participates!)
        List<ISpellModifier> unused = findUnusedModifiers(variants, lookup);

        LinkedHashMap<String, Integer> materials = new LinkedHashMap<String, Integer>();
        int etherium = 0;
        if (collectMaterials) {
            List<ISpellPart> allParts = new ArrayList<ISpellPart>(totalParts);
            for (List<ISpellPart> g : nonEmptyGroups) allParts.addAll(g);
            allParts.addAll(recipe);
            etherium = collectAltarMaterials(allParts, materials);
        }

        return new Analysis(
                totalParts,
                maxStages,
                minBase,
                maxBase,
                minEff,
                maxEff,
                burnout,
                dominant,
                dominantShare,
                altarTierForParts(totalParts),
                unused,
                materials,
                etherium
        );
    }

    /**
     * Checks whether adding {@code modifier} to the last stage of the current recipe
     * would affect at least one shape or component in that stage.
     * Returns {@code true} when metadata is unknown or when the recipe has no stage yet.
     */
    public static boolean isModifierUsefulForLastStage(ISpellModifier modifier,
                                                       List<? extends List<ISpellPart>> shapeGroups,
                                                       List<ISpellPart> mainRecipe,
                                                       ModifiedByLookup lookup) {
        if (modifier == null || lookup == null) return true;
        EnumSet<SpellModifiers> modAspects = modifier.getAspectsModified();
        if (modAspects == null || modAspects.isEmpty()) return true;

        ArrayList<ISpellPart> combined = new ArrayList<ISpellPart>();
        if (shapeGroups != null) {
            for (List<ISpellPart> g : shapeGroups) {
                if (g != null && !g.isEmpty()) {
                    combined.addAll(g);
                    break;
                }
            }
        }
        if (mainRecipe != null) combined.addAll(mainRecipe);
        if (combined.isEmpty()) return true;

        ArrayList<ArrayList<ISpellPart>> stages = SpellValidator.splitToStages(combined);
        if (stages.isEmpty()) return true;
        ArrayList<ISpellPart> lastStage = stages.get(stages.size() - 1);
        return stageAcceptsModifier(lastStage, modifier, lookup);
    }

    /**
     * Minimum Crafting Altar tier needed for {@code partCount} spell parts,
     * matching {@code TileEntityCraftingAltar.checkStructure()} (maxEffects = capIndex+1 + mainBonus, max 13).
     * 0: <=2 (any), 1: <=5 (Iron/Redstone), 2: <=8 (Gold/Diamond), 3: <=11 (Emerald/Moonstone),
     * 4: <=13 (Sunstone + Quartz/Nether), 5: >13 (exceeds altar max!).
     */
    public static int altarTierForParts(int partCount) {
        if (partCount <= 2) return 0;
        if (partCount <= 5) return 1;
        if (partCount <= 8) return 2;
        if (partCount <= 11) return 3;
        if (partCount <= 13) return 4;
        return 5;
    }

    /**
     * Mana and burnout exactly as SpellUtils.calculateSpellRequirements does, read from a real
     * spell stack. Shapes such as AoE and Beam inspect that stack (radius modifiers, Wave), so
     * an empty stand-in stack gives wrong costs. Returns null if the stack cannot be built.
     */
    private static float[] realSpellCost(List<ISpellPart> sequence, EntityPlayer player) {
        try {
            ArrayList<am2.utility.KeyValuePair<ISpellPart, byte[]>> parts = new ArrayList<am2.utility.KeyValuePair<ISpellPart, byte[]>>();
            for (ISpellPart part : sequence) parts.add(new am2.utility.KeyValuePair<ISpellPart, byte[]>(part, new byte[0]));
            ItemStack stack = SpellUtils.instance.createSpellStack(
                    new ArrayList<ArrayList<am2.utility.KeyValuePair<ISpellPart, byte[]>>>(), parts);
            float mana = 0f, burnout = 0f;
            for (int i = SpellUtils.instance.numStages(stack) - 1; i >= 0; --i) {
                float stageMana = 0f, stageBurnout = 0f;
                for (ISpellComponent component : SpellUtils.instance.getComponentsForStage(stack, i)) {
                    stageMana += component.manaCost(player);
                    stageBurnout += component.burnout(player);
                }
                Map<ISpellModifier, Integer> counts = new HashMap<ISpellModifier, Integer>();
                for (ISpellModifier modifier : SpellUtils.instance.getModifiersForStage(stack, i)) {
                    Integer c = counts.get(modifier);
                    counts.put(modifier, c == null ? 1 : c + 1);
                }
                for (Map.Entry<ISpellModifier, Integer> e : counts.entrySet())
                    stageMana *= e.getKey().getManaCostMultiplier(stack, i, e.getValue(), player);
                mana += stageMana * SpellUtils.instance.getShapeForStage(stack, i).manaCostMultiplier(stack);
                burnout += stageBurnout;
            }
            return new float[] { mana, burnout };
        } catch (Throwable unavailable) {
            return null;
        }
    }

    private static float[] computeStagesCost(ArrayList<ArrayList<ISpellPart>> stages, EntityPlayer player) {
        float totalMana = 0f;
        float totalBurnout = 0f;
        ItemStack dummyStack = null;
        try {
            if (ItemsCommonProxy.spell != null) {
                dummyStack = new ItemStack(ItemsCommonProxy.spell);
            }
        } catch (Throwable ignored) {
        }

        for (int i = stages.size() - 1; i >= 0; --i) {
            ArrayList<ISpellPart> stage = stages.get(i);
            ISpellShape shape = null;
            float stageMana = 0f;
            float stageBurnout = 0f;
            Map<ISpellModifier, Integer> modCounts = new LinkedHashMap<ISpellModifier, Integer>();

            for (ISpellPart part : stage) {
                if (part instanceof ISpellShape) {
                    shape = (ISpellShape) part;
                } else if (part instanceof ISpellComponent) {
                    ISpellComponent comp = (ISpellComponent) part;
                    stageMana += comp.manaCost(player);
                    stageBurnout += comp.burnout(player);
                } else if (part instanceof ISpellModifier) {
                    ISpellModifier mod = (ISpellModifier) part;
                    Integer c = modCounts.get(mod);
                    modCounts.put(mod, c == null ? 1 : c + 1);
                }
            }

            for (Map.Entry<ISpellModifier, Integer> e : modCounts.entrySet()) {
                try {
                    stageMana *= e.getKey().getManaCostMultiplier(dummyStack, i, e.getValue(), player);
                } catch (Throwable ignored) {
                }
            }

            float shapeMult = 1.0f;
            if (shape != null) {
                try {
                    shapeMult = shape.manaCostMultiplier(dummyStack);
                } catch (Throwable ignored) {
                }
            }

            totalMana += stageMana * shapeMult;
            totalBurnout += stageBurnout;
        }
        return new float[] { totalMana, totalBurnout };
    }

    static float applyAffinityDiscount(float baseMana, Map<Affinity, Float> affShares, AffinityData affData) {
        if (affData == null || affShares.isEmpty()) return baseMana;
        float mana = baseMana;
        for (Map.Entry<Affinity, Float> e : affShares.entrySet()) {
            float depth = affData.getAffinityDepth(e.getKey());
            float effectiveness = e.getValue();
            float multiplier = 0.5f * depth;
            float manaMod = mana * effectiveness;
            mana -= manaMod * multiplier;
        }
        return Math.max(0f, mana);
    }

    private static List<ISpellModifier> findUnusedModifiers(List<ArrayList<ArrayList<ISpellPart>>> variants,
                                                            ModifiedByLookup lookup) {
        if (lookup == null) return Collections.emptyList();
        List<ISpellModifier> orderedMods = new ArrayList<ISpellModifier>();
        Set<ISpellModifier> seenOrdered = Collections.newSetFromMap(new IdentityHashMap<ISpellModifier, Boolean>());
        Set<ISpellModifier> useful = Collections.newSetFromMap(new IdentityHashMap<ISpellModifier, Boolean>());

        for (ArrayList<ArrayList<ISpellPart>> stages : variants) {
            for (ArrayList<ISpellPart> stage : stages) {
                for (ISpellPart part : stage) {
                    if (part instanceof ISpellModifier) {
                        ISpellModifier mod = (ISpellModifier) part;
                        if (seenOrdered.add(mod)) orderedMods.add(mod);
                        if (!useful.contains(mod) && stageAcceptsModifier(stage, mod, lookup)) {
                            useful.add(mod);
                        }
                    }
                }
            }
        }

        List<ISpellModifier> unused = new ArrayList<ISpellModifier>();
        for (ISpellModifier mod : orderedMods) {
            if (!useful.contains(mod)) {
                unused.add(mod);
            }
        }
        return unused;
    }

    /**
     * Aspects applied by the spell engine itself (SpellHelper and the shared renderers) to any
     * spell, so they work whatever the stage holds and Compendium modifiedBy lists omit them:
     * cloaking, colour, keystone recognition, lingering, dismembering and target filters.
     */
    static final EnumSet<SpellModifiers> ENGINE_WIDE = EnumSet.of(
            SpellModifiers.CLOAKING, SpellModifiers.COLOR, SpellModifiers.KEYSTONE_REC,
            SpellModifiers.LINGERING, SpellModifiers.DISMEMBERING_LEVEL,
            SpellModifiers.TARGET_BLOCKS, SpellModifiers.TARGET_CREATURES,
            SpellModifiers.TARGET_MONSTERS, SpellModifiers.TARGET_PLAYERS);

    private static boolean stageAcceptsModifier(List<ISpellPart> stage, ISpellModifier modifier, ModifiedByLookup lookup) {
        EnumSet<SpellModifiers> modAspects = modifier.getAspectsModified();
        if (modAspects == null || modAspects.isEmpty()) return true;
        for (SpellModifiers aspect : modAspects) if (ENGINE_WIDE.contains(aspect)) return true;

        boolean hasSubject = false;
        for (ISpellPart part : stage) {
            if (!(part instanceof ISpellShape) && !(part instanceof ISpellComponent)) continue;
            hasSubject = true;
            SpellModifiers[] accepted = lookup.acceptedModifiers(part);
            if (accepted == null) {
                // Unknown metadata (e.g., addon part without compendium entry): never show false positive.
                return true;
            }
            for (SpellModifiers m : accepted) {
                if (m != null && modAspects.contains(m)) {
                    return true;
                }
            }
        }
        return !hasSubject;
    }

    private static int collectAltarMaterials(List<ISpellPart> allParts, LinkedHashMap<String, Integer> materials) {
        int totalEtherium = 0;
        for (ISpellPart part : allParts) {
            if (part == null) continue;
            Object[] items;
            try {
                items = part.getRecipeItems();
            } catch (Throwable t) {
                continue;
            }
            if (items == null) continue;
            for (int i = 0; i < items.length; ++i) {
                Object o = items[i];
                if (o == null) continue;
                String name = null;
                int qty = 1;
                if (o instanceof ItemStack) {
                    ItemStack st = (ItemStack) o;
                    name = st.getDisplayName();
                    qty = Math.max(1, st.stackSize);
                } else if (o instanceof Item) {
                    ItemStack st = new ItemStack((Item) o);
                    name = st.getDisplayName();
                } else if (o instanceof Block) {
                    name = ((Block) o).getLocalizedName();
                } else if (o instanceof String) {
                    String s = (String) o;
                    if (s.startsWith("E:")) {
                        if (i + 1 < items.length && items[i + 1] instanceof Integer) {
                            totalEtherium += (Integer) items[++i];
                        }
                        continue;
                    } else if (s.startsWith("P:")) {
                        int pfx = SpellRecipeManager.parsePotionMeta(s.substring(2));
                        ItemStack pot = new ItemStack(Items.potionitem, 1, pfx);
                        name = pot.getDisplayName();
                    } else {
                        List<ItemStack> ores = OreDictionary.getOres(s);
                        name = (ores != null && !ores.isEmpty()) ? ores.get(0).getDisplayName() : s;
                    }
                }
                if (name != null && !name.isEmpty()) {
                    Integer prev = materials.get(name);
                    materials.put(name, prev == null ? qty : prev + qty);
                }
            }
        }
        return totalEtherium;
    }
}
