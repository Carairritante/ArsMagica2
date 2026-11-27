package am2.cinematics;

import org.junit.Test;

/** Runs the scene checks that came with the cinematics (camera limits, actors, ritual, minigames). */
public class SceneChecksTest {
    @Test public void bundledScenesAreValid() throws Exception {
        SceneChecks.main(new String[] { "src/main/resources/assets/amcinematics/scenes" });
    }
}
