package am2.spell;

import org.junit.Test;
import static org.junit.Assert.*;

public class RecallCombinationTest {
    @Test public void requiresRecallAndBothInterventionsOrCross() {
        assertTrue(RecallCombination.qualifies(new String[]{"Recall", "EnderIntervention", "NetherIntervention"}));
        assertTrue(RecallCombination.qualifies(new String[]{"Recall", "CrossDimensionalIntervention"}));
        assertTrue(RecallCombination.qualifies(new String[]{"NetherIntervention", "Recall", "EnderIntervention"}));
        assertFalse(RecallCombination.qualifies(new String[]{"Recall", "EnderIntervention"}));
        assertFalse(RecallCombination.qualifies(new String[]{"Recall", "NetherIntervention"}));
        assertFalse(RecallCombination.qualifies(new String[]{"CrossDimensionalIntervention"}));
        assertFalse(RecallCombination.qualifies(new String[]{"EnderIntervention", "NetherIntervention"}));
        assertFalse(RecallCombination.qualifies(new String[]{"Recall", "DivineIntervention"}));
    }
    @Test public void matchesRegisteredKeysNotLocalizedNamesOrPartialWords() {
        assertFalse(RecallCombination.qualifies(new String[]{"Recall", "Cross-Dimensional Intervention"}));
        assertFalse(RecallCombination.qualifies(new String[]{"Recall", null, "CrossDimensionalInterventionX"}));
        assertFalse(RecallCombination.intervention("DivineIntervention"));
        assertFalse(RecallCombination.intervention(null));
        assertTrue(RecallCombination.intervention("CrossDimensionalIntervention"));
    }
    @Test public void refusesNonFiniteAndOutOfWorldMarks() {
        assertTrue(RecallTravel.validCoordinates(128.25, 64.5, -50.75));
        assertFalse(RecallTravel.validCoordinates(Double.NaN, 60, 0));
        assertFalse(RecallTravel.validCoordinates(0, Double.POSITIVE_INFINITY, 0));
        assertFalse(RecallTravel.validCoordinates(30000000, 60, 0));
        assertFalse(RecallTravel.validCoordinates(0, -1, 0));
    }
}
