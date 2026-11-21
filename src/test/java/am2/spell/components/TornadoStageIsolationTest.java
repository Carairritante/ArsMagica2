package am2.spell.components;

import am2.api.spell.component.interfaces.ISkillTreeEntry;
import am2.entities.EntityDummyCaster;
import am2.entities.EntitySpellEffect;
import am2.spell.SkillManager;
import am2.spell.SpellUtils;
import am2.spell.modifiers.Damage;
import am2.spell.modifiers.Radius;
import am2.spell.modifiers.Solar;
import am2.spell.modifiers.Speed;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.Assert.*;

/** Uses actual spell NBT/stage parsing; no Forge world or recipe registry mutation. */
public class TornadoStageIsolationTest {

	private SkillManager previousManager;

	@Before
	@SuppressWarnings("unchecked")
	public void isolateSpellRegistry() throws Exception {
		previousManager = SkillManager.instance;
		Constructor<SkillManager> constructor = SkillManager.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		SkillManager.instance = constructor.newInstance();
		Field registered = SkillManager.class.getDeclaredField("registeredParts");
		registered.setAccessible(true);
		Map<Integer, ISkillTreeEntry> parts = (Map<Integer, ISkillTreeEntry>) registered.get(SkillManager.instance);
		for (ISkillTreeEntry part : new ISkillTreeEntry[]{
				new Tornado(), new FireDamage(), new LightningDamage(),
				new Solar(), new Radius(), new Damage(), new Speed()}) {
			parts.put(SkillManager.instance.getShiftedPartID(part), part);
		}
	}

	@After
	public void restoreSpellRegistry() {
		SkillManager.instance = previousManager;
	}

	private ItemStack spell(int[][] components, int[][] modifiers) {
		ItemStack stack = new ItemStack(new Item());
		NBTTagCompound tag = new NBTTagCompound();
		tag.setInteger("NumStages", components.length);
		for (int stage = 0; stage < components.length; stage++) {
			tag.setIntArray("SpellComponentIDs_" + stage, components[stage]);
			tag.setIntArray("SpellModifierIDs_" + stage, modifiers[stage]);
		}
		stack.setTagCompound(tag);
		return stack;
	}

	@Test
	public void laterStageCannotUpgradeTierOrChangeCurrentElement() {
		int fire = SkillManager.instance.getShiftedPartID(new FireDamage());
		ItemStack stack = spell(new int[][]{{1102}, {fire}},
				new int[][]{new int[0], {5012, 5012, 5012, 5005, 5001}});
		assertArrayEquals(new int[4], Tornado.inspectTierModifiers(stack));
		assertEquals(Tornado.ELEMENT_AIR, Tornado.detectElementalSynergy(stack));
		assertEquals(3, Tornado.inspectTierModifiers(stack, 1)[0]);
		assertEquals(Tornado.ELEMENT_FIRE, Tornado.detectElementalSynergy(stack, 1));
	}

	@Test
	public void independentTornadoStagesUseTheirOwnModifiersAfterPop() {
		int lightning = SkillManager.instance.getShiftedPartID(new LightningDamage());
		int fire = SkillManager.instance.getShiftedPartID(new FireDamage());
		ItemStack stack = spell(new int[][]{{1102, lightning}, {1102, fire}},
				new int[][]{{5001}, {5012, 5012, 5012}});
		assertArrayEquals(new int[]{0, 0, 0, 1}, Tornado.inspectTierModifiers(stack, 0));
		assertEquals(Tornado.ELEMENT_LIGHTNING, Tornado.detectElementalSynergy(stack, 0));
		ItemStack remaining = SpellUtils.instance.popStackStage(stack);
		assertArrayEquals(new int[]{3, 0, 0, 0}, Tornado.inspectTierModifiers(remaining, 0));
		assertEquals(Tornado.ELEMENT_FIRE, Tornado.detectElementalSynergy(remaining, 0));
		assertEquals(2, SpellUtils.instance.numStages(stack));
	}

	@Test
	public void legacyInspectorFindsTornadoWithoutCombiningEarlierStages() {
		ItemStack stack = spell(new int[][]{new int[0], {1102}},
				new int[][]{{5012, 5012, 5012}, {5005}});
		assertEquals(1, Tornado.findTornadoStage(stack));
		assertArrayEquals(new int[]{0, 0, 1, 0}, Tornado.inspectTierModifiers(stack));
		assertArrayEquals(new int[]{3, 0, 0, 0}, Tornado.inspectTierModifiers(stack, 0));
	}

	@Test
	public void speedDoesNotCountAsRadiusForFujitaTier() {
		ItemStack stack = spell(new int[][]{{1102}}, new int[][]{{5008}});
		assertArrayEquals(new int[4], Tornado.inspectTierModifiers(stack));
		stack.stackTagCompound.setIntArray("SpellModifierIDs_0", new int[]{5005});
		assertArrayEquals(new int[]{0, 0, 1, 0}, Tornado.inspectTierModifiers(stack));
	}

	@Test
	public void invalidStageAndMissingSpellHaveNoSynergy() {
		ItemStack stack = spell(new int[][]{{1102}}, new int[][]{{5012}});
		assertArrayEquals(new int[4], Tornado.inspectTierModifiers(stack, -1));
		assertArrayEquals(new int[4], Tornado.inspectTierModifiers(stack, 1));
		assertArrayEquals(new int[4], Tornado.inspectTierModifiers(null, 0));
		assertEquals(Tornado.ELEMENT_AIR, Tornado.detectElementalSynergy(stack, -1));
		assertEquals(Tornado.ELEMENT_AIR, Tornado.detectElementalSynergy(null, 0));
	}

	@Test
	public void onlyKnownSameCasterMayRefreshExistingTornado() throws Exception {
		EntitySpellEffect effect = new EntitySpellEffect(null);
		effect.setTornado(null, null, 0, 64, 0, 7, 260, 7, 0, 0, 1.8f, 0, 0);
		EntityDummyCaster first = new EntityDummyCaster(null);
		EntityDummyCaster second = new EntityDummyCaster(null);
		Field owner = EntitySpellEffect.class.getDeclaredField("casterEntityID");
		owner.setAccessible(true);
		owner.setInt(effect, first.getEntityId());
		assertTrue(Tornado.canRefreshTornado(effect, first));
		assertFalse(Tornado.canRefreshTornado(effect, second));
		assertFalse(Tornado.canRefreshTornado(effect, null));
		assertFalse(Tornado.canRefreshTornado(null, first));
		owner.setInt(effect, -1);
		assertFalse(Tornado.canRefreshTornado(effect, first));
		assertFalse(Tornado.canRefreshTornado(effect, null));
	}
}
