package am2.cinematics.server;

import am2.cinematics.scene.Scene;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/** Every guardian variant of the victory scene must be a valid scene with its own details. */
public class GuardianVictoryTriggerTest {
    private static String base() throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/resources/assets/amcinematics/scenes/guardiao.json")), Scene.UTF8);
    }

    @Test public void everyVariantParses() throws Exception {
        String base = base();
        Set<String> titles = new HashSet<String>();
        for (GuardianVictoryTrigger.Guardian g : GuardianVictoryTrigger.Guardian.values()) {
            String json = GuardianVictoryTrigger.variant(base, g);
            Scene scene = Scene.parse(json.replace("{player}", "Tester"));
            assertTrue(g.id, scene.duration <= 20 * 10);
            assertFalse(g.id, json.contains("{guardiao}"));
            assertTrue(g.id, json.contains(g.main) && json.contains(g.particle));
            assertTrue(titles.add(g.title));
        }
    }

    @Test public void onlyLightningAddsBolts() throws Exception {
        String base = base();
        for (GuardianVictoryTrigger.Guardian g : GuardianVictoryTrigger.Guardian.values()) {
            boolean bolts = GuardianVictoryTrigger.variant(base, g).contains("\"lightning\"");
            assertEquals(g.id, g == GuardianVictoryTrigger.Guardian.LIGHTNING, bolts);
        }
    }

    @Test public void idsAreUnique() {
        Set<String> ids = new HashSet<String>();
        for (GuardianVictoryTrigger.Guardian g : GuardianVictoryTrigger.Guardian.values()) {
            assertTrue(ids.add(g.id));
            assertSame(g, GuardianVictoryTrigger.Guardian.byId(g.id));
        }
    }
}
