package am2.interop;

import org.junit.Test;
import static org.junit.Assert.*;

/** Without AMCinematics everything must behave exactly as before. */
public class CinematicsRitualGuideTest {
    @Test public void inactiveWithoutTheMod() {
        assertFalse(CinematicsRitualGuide.active());
        assertFalse(CinematicsRitualGuide.appliesTo(CinematicsRitualGuide.WORLD_FUSION));
    }

    @Test public void returnsNeutralValuesWhenInactive() {
        assertEquals(0, CinematicsRitualGuide.groundReagents().length);
        assertEquals(0, CinematicsRitualGuide.allReagents().length);
        assertNull(CinematicsRitualGuide.catalyst());
        assertNull(CinematicsRitualGuide.description());
        assertNull(CinematicsRitualGuide.activation());
    }

    @Test public void onlyWorldFusionIsAffected() {
        assertFalse(CinematicsRitualGuide.appliesTo("purification"));
        assertFalse(CinematicsRitualGuide.appliesTo(null));
    }
}
