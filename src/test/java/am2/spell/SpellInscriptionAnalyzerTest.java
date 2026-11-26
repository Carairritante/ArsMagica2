package am2.spell;

import am2.api.spell.component.interfaces.ISpellComponent;
import am2.api.spell.component.interfaces.ISpellModifier;
import am2.api.spell.component.interfaces.ISpellPart;
import am2.api.spell.component.interfaces.ISpellShape;
import am2.api.spell.enums.Affinity;
import am2.api.spell.enums.SpellCastResult;
import am2.api.spell.enums.SpellModifiers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class SpellInscriptionAnalyzerTest {

    private static ISpellShape fakeShape(final int id, final float mult) {
        return new ISpellShape() {
            @Override public SpellCastResult beginStackStage(am2.api.spell.ItemSpellBase item, ItemStack stack, EntityLivingBase caster, EntityLivingBase target, World world, double x, double y, double z, int side, boolean giveXP, int useCount) { return null; }
            @Override public boolean isChanneled() { return false; }
            @Override public float manaCostMultiplier(ItemStack spellStack) { return mult; }
            @Override public boolean isTerminusShape() { return false; }
            @Override public boolean isPrincipumShape() { return false; }
            @Override public String getSoundForAffinity(Affinity affinity, ItemStack stack, World world) { return ""; }
            @Override public Object[] getRecipeItems() { return new Object[0]; }
            @Override public int getID() { return id; }
        };
    }

    private static ISpellComponent fakeComponent(final int id, final float mana, final float burnout, final Affinity aff) {
        return new ISpellComponent() {
            @Override public boolean applyEffectBlock(ItemStack stack, World world, int blockx, int blocky, int blockz, int blockFace, double impactX, double impactY, double impactZ, EntityLivingBase caster) { return false; }
            @Override public boolean applyEffectEntity(ItemStack stack, World world, EntityLivingBase caster, Entity target) { return false; }
            @Override public float manaCost(EntityLivingBase caster) { return mana; }
            @Override public float burnout(EntityLivingBase caster) { return burnout; }
            @Override public ItemStack[] reagents(EntityLivingBase caster) { return null; }
            @Override public void spawnParticles(World world, double x, double y, double z, EntityLivingBase caster, Entity target, java.util.Random rand, int colorModifier) {}
            @Override public EnumSet<Affinity> getAffinity() { return EnumSet.of(aff); }
            @Override public float getAffinityShift(Affinity context) { return 0; }
            @Override public Object[] getRecipeItems() { return new Object[0]; }
            @Override public int getID() { return id; }
        };
    }

    private static ISpellModifier fakeModifier(final int id, final float costMult, final SpellModifiers... aspects) {
        return new ISpellModifier() {
            @Override public EnumSet<SpellModifiers> getAspectsModified() { return EnumSet.copyOf(Arrays.asList(aspects)); }
            @Override public float getModifier(SpellModifiers type, EntityLivingBase caster, Entity target, World world, byte[] metadata) { return 1f; }
            @Override public float getManaCostMultiplier(ItemStack spellStack, int stage, int quantity, EntityLivingBase caster) { return (float) Math.pow(costMult, quantity); }
            @Override public byte[] getModifierMetadata(ItemStack[] matchedRecipe) { return new byte[0]; }
            @Override public Object[] getRecipeItems() { return new Object[0]; }
            @Override public int getID() { return id; }
            @Override public boolean canStack() { return true; }
        };
    }

    @Test
    public void calculatesExactManaBurnoutAndAffinity() {
        ISpellShape projectile = fakeShape(1, 1.25f);
        ISpellComponent fireDmg = fakeComponent(100, 120f, 20f, Affinity.FIRE);
        ISpellModifier dmgMod = fakeModifier(200, 1.5f, SpellModifiers.DAMAGE);

        List<List<ISpellPart>> groups = Collections.<List<ISpellPart>>singletonList(Collections.<ISpellPart>singletonList(projectile));
        List<ISpellPart> recipe = Arrays.<ISpellPart>asList(fireDmg, dmgMod);

        SpellInscriptionAnalyzer.Analysis a = SpellInscriptionAnalyzer.analyze(groups, recipe, null, null, false);
        assertEquals(3, a.totalParts);
        assertEquals(1, a.stageCount);
        assertEquals(120f * 1.5f * 1.25f, a.minManaBase, 0.01f);
        assertEquals(20f, a.burnout, 0.01f);
        assertEquals(Affinity.FIRE, a.dominantAffinity);
        assertEquals(1, a.altarTier);
    }

    @Test
    public void flagsUnusedModifiersAndAcceptsMatchingOnes() {
        final ISpellShape self = fakeShape(2, 0.5f);
        final ISpellComponent heal = fakeComponent(101, 200f, 35f, Affinity.LIFE);
        final ISpellModifier dmgMod = fakeModifier(201, 1.5f, SpellModifiers.DAMAGE);
        final ISpellModifier healMod = fakeModifier(202, 1.3f, SpellModifiers.HEALING);

        final Map<ISpellPart, SpellModifiers[]> meta = new HashMap<ISpellPart, SpellModifiers[]>();
        meta.put(self, new SpellModifiers[0]);
        meta.put(heal, new SpellModifiers[]{ SpellModifiers.HEALING });

        SpellInscriptionAnalyzer.ModifiedByLookup lookup = new SpellInscriptionAnalyzer.ModifiedByLookup() {
            @Override public SpellModifiers[] acceptedModifiers(ISpellPart part) {
                return meta.get(part);
            }
        };

        List<List<ISpellPart>> groups = Collections.<List<ISpellPart>>singletonList(Collections.<ISpellPart>singletonList(self));
        List<ISpellPart> recipe = Arrays.<ISpellPart>asList(heal, dmgMod, healMod);

        SpellInscriptionAnalyzer.Analysis a = SpellInscriptionAnalyzer.analyze(groups, recipe, null, lookup, false);
        assertTrue(a.isUnused(dmgMod));
        assertFalse(a.isUnused(healMod));
        assertEquals(1, a.unusedModifiers.size());
    }

    @Test
    public void unknownMetadataNeverProducesFalsePositiveUnusedWarning() {
        ISpellShape customShape = fakeShape(3, 1f);
        ISpellComponent customComp = fakeComponent(102, 50f, 5f, Affinity.ARCANE);
        ISpellModifier mod = fakeModifier(203, 1.2f, SpellModifiers.RADIUS);

        SpellInscriptionAnalyzer.ModifiedByLookup unknownLookup = new SpellInscriptionAnalyzer.ModifiedByLookup() {
            @Override public SpellModifiers[] acceptedModifiers(ISpellPart part) {
                return null;
            }
        };

        List<List<ISpellPart>> groups = Collections.<List<ISpellPart>>singletonList(Collections.<ISpellPart>singletonList(customShape));
        List<ISpellPart> recipe = Arrays.<ISpellPart>asList(customComp, mod);

        SpellInscriptionAnalyzer.Analysis a = SpellInscriptionAnalyzer.analyze(groups, recipe, null, unknownLookup, false);
        assertTrue(a.unusedModifiers.isEmpty());
        assertFalse(a.isUnused(mod));
    }

    @Test
    public void altarComplexityTiersMatchAltarCaps() {
        assertEquals(0, SpellInscriptionAnalyzer.altarTierForParts(2));
        assertEquals(1, SpellInscriptionAnalyzer.altarTierForParts(5));
        assertEquals(2, SpellInscriptionAnalyzer.altarTierForParts(8));
        assertEquals(3, SpellInscriptionAnalyzer.altarTierForParts(11));
        assertEquals(4, SpellInscriptionAnalyzer.altarTierForParts(13));
        assertEquals(5, SpellInscriptionAnalyzer.altarTierForParts(14));
    }
}
