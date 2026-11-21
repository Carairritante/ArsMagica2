package am2.client.vfx;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class MahouCastingMeshTest {
    private static final String[] VARIANTS = {"none","arcane","water","fire","earth","air","lightning",
            "ice","nature","life","ender","lightning_lunar","blizzard","firestorm","starfall","storm_lightning"};

    @Test public void allApprovedVariantsHaveValidGeometryAndDistinctAtlases() throws Exception {
        Set<String> hashes = new HashSet<String>();
        for (String variant : VARIANTS) {
            String root = "/assets/arsmagica2/";
            InputStream resource = getClass().getResourceAsStream(root + "models/mahou_vertical/" + variant + ".mesh");
            assertNotNull(variant, resource);
            MahouCastingMesh mesh = MahouCastingMesh.read(resource);
            assertEquals(896, mesh.vertices.length);
            assertEquals(1.4f, mesh.phaseEnds[0], .001f);
            assertEquals(3.78f, mesh.phaseEnds[1], .001f);
            assertEquals(6.83f, mesh.phaseEnds[2], .001f);
            // No reference player or central filled disc: every piece stays on a ring perimeter.
            for (float[] vertices : mesh.vertices) {
                double cx = 0, cz = 0;
                for (int i = 0; i < vertices.length; i += 5) { cx += vertices[i]; cz += vertices[i+2]; }
                cx /= 24; cz /= 24;
                assertTrue(variant + " central obstruction", cx*cx + cz*cz > 3.5*3.5);
            }
            try (InputStream atlas = getClass().getResourceAsStream(root + "textures/mahou_vertical/" + variant + ".png")) {
                assertNotNull(atlas);
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[4096]; int count;
                while ((count = atlas.read(buffer)) != -1) sha.update(buffer, 0, count);
                hashes.add(java.util.Arrays.toString(sha.digest()));
            }
        }
        assertEquals("All palettes must remain distinct", 16, hashes.size());
    }

    @Test public void normalCastingCannotRevealColossalLayer() {
        assertEquals(2, MahouCastingMesh.maxPhase(false));
        assertEquals(3, MahouCastingMesh.maxPhase(true));
        assertEquals(0, MahouCastingMesh.reveal(0, 1, 2), 0);
        assertEquals(.5f, MahouCastingMesh.reveal(1.5f, 1, 2), 0);
        assertEquals(1, MahouCastingMesh.reveal(100, 1, 2), 0);
    }

    @Test(expected = java.io.IOException.class) public void rejectsCorruptResource() throws Exception {
        MahouCastingMesh.read(new ByteArrayInputStream(new byte[20]));
    }
}
