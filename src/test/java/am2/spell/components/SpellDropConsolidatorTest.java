package am2.spell.components;

import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class SpellDropConsolidatorTest {

	private static final Item TEST_STONE = new Item().setUnlocalizedName("am2_test_stone").setMaxStackSize(64);

	@BeforeClass
	public static void registerTestItem() throws Exception {
		if (Item.itemRegistry.getObject("am2test:stone") == null) {
			Method addObjectRaw = FMLControlledNamespacedRegistry.class.getDeclaredMethod(
					"addObjectRaw", int.class, String.class, Object.class);
			addObjectRaw.setAccessible(true);
			addObjectRaw.invoke(Item.itemRegistry, 4094, "am2test:stone", TEST_STONE);
		}
	}

	@Test
	public void mergesMatchingStacksAndSeparatesDifferentMetaOrNbt() {
		SpellDropConsolidator.BatchScope scope = new SpellDropConsolidator.BatchScope(null, 10.5D, 64.5D, 20.5D, false);

		for (int i = 0; i < 150; i++) {
			scope.addStack(new ItemStack(TEST_STONE, 1, 0));
		}
		for (int i = 0; i < 40; i++) {
			scope.addStack(new ItemStack(TEST_STONE, 1, 2));
		}

		ItemStack withNbt = new ItemStack(TEST_STONE, 3, 0);
		NBTTagCompound tag = new NBTTagCompound();
		tag.setString("custom", "rune");
		withNbt.setTagCompound(tag);
		scope.addStack(withNbt);
		scope.addStack(withNbt.copy());

		assertEquals(3, scope.stacks.size());
		assertEquals(150, scope.stacks.get(0).totalCount);
		assertEquals(0, scope.stacks.get(0).prototype.getItemDamage());
		assertEquals(40, scope.stacks.get(1).totalCount);
		assertEquals(2, scope.stacks.get(1).prototype.getItemDamage());
		assertEquals(6, scope.stacks.get(2).totalCount);
		assertEquals("rune", scope.stacks.get(2).prototype.getTagCompound().getString("custom"));

		scope.addXp(5);
		scope.addXp(12);
		assertEquals(17, scope.totalXp);
	}
}
