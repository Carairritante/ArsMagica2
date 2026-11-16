package am2.client.vfx;

import am2.entities.EntitySpellEffect;
import am2.network.AMPacketIDs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class AoEShockwaveTest {

	@Test
	public void aoeModelAndEmbeddedTexturesAreValid() throws Exception {
		InputStream is = AoEShockwaveTest.class.getResourceAsStream("/assets/arsmagica2/models/aoe/aoe_shockwave.bbmodel");
		assertNotNull("Missing aoe_shockwave.bbmodel in classpath resources", is);

		JsonObject root;
		try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
			root = new JsonParser().parse(reader).getAsJsonObject();
		}

		JsonArray textures = root.getAsJsonArray("textures");
		assertNotNull("Expected textures array in aoe_shockwave.bbmodel", textures);
		assertEquals("Expected 3 embedded textures", 3, textures.size());

		for (int i = 0; i < textures.size(); i++) {
			String src = textures.get(i).getAsJsonObject().get("source").getAsString();
			int comma = src.indexOf(',');
			assertTrue("Texture " + i + " missing data URI comma", comma > 0);
			byte[] bytes = Base64.getDecoder().decode(src.substring(comma + 1));
			BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
			assertNotNull("Decoded texture " + i + " image was null", img);
			assertEquals(256, img.getWidth());
			assertEquals(256, img.getHeight());
		}

		JsonArray outliner = root.getAsJsonArray("outliner");
		assertNotNull(outliner);
		assertEquals(1, outliner.size());
		JsonObject rootBone = outliner.get(0).getAsJsonObject();
		assertEquals("root", rootBone.get("name").getAsString());

		Set<String> childNames = new HashSet<String>();
		JsonArray children = rootBone.getAsJsonArray("children");
		for (JsonElement el : children) {
			if (el.isJsonObject()) {
				childNames.add(el.getAsJsonObject().get("name").getAsString());
			}
		}
		assertTrue(childNames.contains("wave_ground_ring"));
		assertTrue(childNames.contains("wave_crest_wall"));
		assertTrue(childNames.contains("wave_inner_echo"));
		assertTrue(childNames.contains("origin_burst"));

		JsonArray animations = root.getAsJsonArray("animations");
		assertNotNull(animations);
		assertTrue("Expected at least 1 animation", animations.size() >= 1);
		JsonObject anim = animations.get(0).getAsJsonObject();
		assertEquals("animation.aoe.shockwave", anim.get("name").getAsString());
		JsonObject animators = anim.getAsJsonObject("animators");
		assertNotNull(animators);
		assertTrue("Expected animated bone tracks", animators.entrySet().size() >= 4);
	}

	@Test
	public void aoeShockwaveSideAndCoordinateClampingBehaveCleanly() {
		EntitySpellEffect fx = new EntitySpellEffect(null);
		fx.setAoEShockwave(null, 0.1f, 0);
		assertEquals(0.5f, fx.getRadius(), 0.001f);
		assertEquals(0, fx.getAoESide());

		for (int side = 0; side <= 5; side++) {
			fx.setAoEShockwave(null, 24.0f, side);
			assertEquals(24.0f, fx.getRadius(), 0.001f);
			assertEquals(side, fx.getAoESide());
			assertTrue(fx.isAoEShockwave());
		}
	}

	@Test
	public void entityAndPacketConstantsAreConsistent() {
		assertEquals((byte) 76, AMPacketIDs.AOE_SHOCKWAVE);
		assertEquals(12, EntitySpellEffect.TYPE_AOE_SHOCKWAVE);

		EntitySpellEffect fx = new EntitySpellEffect(null);
		fx.setAoEShockwave(null, 12.5f, 4);
		assertTrue(fx.isAoEShockwave());
		assertEquals(12.5f, fx.getRadius(), 0.001f);
		assertEquals(4, fx.getAoESide());
		assertEquals(14, fx.getTicksToExist());
	}

	@Test
	public void commonClassesHaveNoClientOnlyBytecodeReferences() throws Exception {
		String[] commonClasses = new String[] {
				"/am2/spell/shapes/AoE.class",
				"/am2/spell/SpellHelper.class",
				"/am2/entities/EntitySpellEffect.class",
				"/am2/network/AMPacketIDs.class"
		};
		String[] forbidden = new String[] {
				"net/minecraft/client/",
				"net/minecraftforge/client/",
				"org/lwjgl/",
				"am2/client/"
		};

		for (String resPath : commonClasses) {
			try (InputStream in = AoEShockwaveTest.class.getResourceAsStream(resPath)) {
				assertNotNull("Missing compiled class " + resPath, in);
				ByteArrayOutputStream buf = new ByteArrayOutputStream();
				byte[] tmp = new byte[4096];
				int n;
				while ((n = in.read(tmp)) != -1) {
					buf.write(tmp, 0, n);
				}
				String ascii = new String(buf.toByteArray(), StandardCharsets.ISO_8859_1);
				for (String bad : forbidden) {
					assertFalse("Forbidden client ref '" + bad + "' found in " + resPath, ascii.contains(bad));
				}
			}
		}
	}
}
