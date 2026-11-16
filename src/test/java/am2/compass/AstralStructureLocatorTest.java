package am2.compass;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class AstralStructureLocatorTest {

	@Test
	public void magicLevelAndManaThresholdsAreStrict() {
		assertFalse(AstralStructureLocator.hasRequiredLevel(0));
		assertFalse(AstralStructureLocator.hasRequiredLevel(49));
		assertTrue(AstralStructureLocator.hasRequiredLevel(50));
		assertTrue(AstralStructureLocator.hasRequiredLevel(99));

		assertFalse(AstralStructureLocator.hasEnoughMana(19999.9f, 0.0f));
		assertTrue(AstralStructureLocator.hasEnoughMana(20000.0f, 0.0f));
		assertTrue(AstralStructureLocator.hasEnoughMana(15000.0f, 5000.0f));
		assertFalse(AstralStructureLocator.hasEnoughMana(15000.0f, 4999.0f));
	}

	@Test
	public void builtinCatalogContainsUniqueVanillaAndAm2Structures() {
		List<StructureTargetInfo> catalog = AstralStructureLocator.getBuiltinCatalog();
		assertFalse(catalog.isEmpty());

		Set<String> ids = new HashSet<String>();
		boolean hasStronghold = false;
		boolean hasWitchwood = false;
		for (StructureTargetInfo info : catalog) {
			assertTrue("Duplicate structure id: " + info.id, ids.add(info.id));
			if (AstralStructureLocator.ID_STRONGHOLD.equals(info.id)) hasStronghold = true;
			if (AstralStructureLocator.ID_WITCHWOOD_FOREST.equals(info.id)) hasWitchwood = true;
		}
		assertTrue(hasStronghold);
		assertTrue(hasWitchwood);
	}

	@Test
	public void filterCatalogSupportsCategoryAndQueryMatching() {
		List<StructureTargetInfo> catalog = AstralStructureLocator.getBuiltinCatalog();

		List<StructureTargetInfo> am2Only = AstralStructureLocator.filterCatalog(catalog, StructureTargetInfo.CAT_AM2, "");
		assertEquals(1, am2Only.size());
		assertEquals(AstralStructureLocator.ID_WITCHWOOD_FOREST, am2Only.get(0).id);

		List<StructureTargetInfo> pyramidMatch = AstralStructureLocator.filterCatalog(catalog, StructureTargetInfo.CAT_ALL, "pyramid");
		assertEquals(1, pyramidMatch.size());
		assertEquals(AstralStructureLocator.ID_DESERT_PYRAMID, pyramidMatch.get(0).id);

		List<StructureTargetInfo> compactMatch = AstralStructureLocator.filterCatalog(catalog, StructureTargetInfo.CAT_VANILLA, "witchhut");
		assertEquals(1, compactMatch.size());
		assertEquals(AstralStructureLocator.ID_WITCH_HUT, compactMatch.get(0).id);
	}

	@Test
	public void prettifyFormatsModdedIdsReadable() {
		assertEquals("Ancient Keep", StructureTargetInfo.prettify("modid:ancient_keep"));
		assertEquals("Dragon Roost", StructureTargetInfo.prettify("DragonRoost"));
	}
}
