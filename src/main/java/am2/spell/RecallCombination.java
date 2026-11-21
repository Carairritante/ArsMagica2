package am2.spell;

/** Registry identifiers, never translated names or hard references to addon classes. */
public final class RecallCombination {
    private RecallCombination() {}
    public static boolean qualifies(String... components) {
        boolean recall = false, ender = false, nether = false, cross = false;
        for (String name : components) {
            recall |= "Recall".equals(name);
            ender |= "EnderIntervention".equals(name);
            nether |= "NetherIntervention".equals(name);
            cross |= "CrossDimensionalIntervention".equals(name);
        }
        return recall && (cross || ender && nether);
    }
    public static boolean intervention(String name) {
        return "EnderIntervention".equals(name) || "NetherIntervention".equals(name)
            || "CrossDimensionalIntervention".equals(name);
    }
}
