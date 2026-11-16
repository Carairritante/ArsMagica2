package am2.client.vfx;

import am2.LogHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads and renders the Blockbench (.bbmodel) Zone seal + minimalist twin mountain spikes rig.
 */
@SideOnly(Side.CLIENT)
public final class BlockbenchZoneModel {

	private static final float BLOCK_SCALE = 1.0f / 16.0f;
	private static final float BLANK_UV_THRESHOLD = 2.5f;
	private static final ResourceLocation MODEL_LOC = new ResourceLocation("arsmagica2", "models/zones/zone_circle_spikes.bbmodel");

	private static final int FACE_NORTH = 0;
	private static final int FACE_SOUTH = 1;
	private static final int FACE_EAST = 2;
	private static final int FACE_WEST = 3;
	private static final int FACE_UP = 4;
	private static final int FACE_DOWN = 5;

	private static BlockbenchZoneModel cached;
	private static boolean loadAttempted = false;

	private ResourceLocation[] textures;
	private BoneNode ringBone;
	private final BoneNode[] spikeTiers = new BoneNode[8];

	private static final class FaceQuad {
		int dir;
		int texIndex;
		float u0, v0, u1, v1;
	}

	private static final class CubeData {
		float x0, y0, z0, x1, y1, z1;
		float ox, oy, oz;
		float rx, ry, rz;
		boolean hasRot;
		FaceQuad[] faces;
	}

	private static final class BoneNode {
		String name;
		float ox, oy, oz;
		float rx, ry, rz;
		boolean hasRot;
		CubeData[] cubes;
		BoneNode[] children;
	}

	private BlockbenchZoneModel() {
	}

	public static BlockbenchZoneModel get() {
		if (!loadAttempted) {
			loadAttempted = true;
			cached = load();
		}
		return cached;
	}

	public static void clearCache() {
		if (cached != null && cached.textures != null) {
			Minecraft mc = Minecraft.getMinecraft();
			if (mc != null && mc.getTextureManager() != null) {
				for (int i = 0; i < cached.textures.length; i++) {
					if (cached.textures[i] != null) {
						mc.getTextureManager().deleteTexture(cached.textures[i]);
					}
				}
			}
		}
		cached = null;
		loadAttempted = false;
	}

	private static BlockbenchZoneModel load() {
		InputStreamReader reader = null;
		try {
			IResource res = Minecraft.getMinecraft().getResourceManager().getResource(MODEL_LOC);
			reader = new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8);
			JsonObject root = new JsonParser().parse(reader).getAsJsonObject();

			BlockbenchZoneModel model = new BlockbenchZoneModel();
			float resW = 256.0f;
			float resH = 256.0f;
			if (root.has("resolution") && root.get("resolution").isJsonObject()) {
				JsonObject resObj = root.getAsJsonObject("resolution");
				resW = getFloat(resObj, "width", 256.0f);
				resH = getFloat(resObj, "height", 256.0f);
			}

			model.loadTextures(root);
			Map<String, CubeData> cubeMap = parseCubes(root, resW, resH);
			model.parseOutliner(root, cubeMap);
			if (model.ringBone == null) {
				LogHelper.warn("Zone bbmodel loaded without ring_base bone.");
				return null;
			}
			return model;
		} catch (Throwable t) {
			LogHelper.error("Failed to load Zone Blockbench model: " + t.getMessage());
			t.printStackTrace();
			return null;
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (Throwable ignored) {
				}
			}
		}
	}

	private void loadTextures(JsonObject root) {
		JsonArray texArr = root.getAsJsonArray("textures");
		if (texArr == null) {
			this.textures = new ResourceLocation[0];
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		int count = texArr.size();
		this.textures = new ResourceLocation[count];

		for (int i = 0; i < count; i++) {
			JsonObject texObj = texArr.get(i).getAsJsonObject();
			String source = texObj.has("source") ? texObj.get("source").getAsString() : "";
			int comma = source.indexOf(',');
			if (comma >= 0) {
				try {
					byte[] pngBytes = Base64.getDecoder().decode(source.substring(comma + 1));
					BufferedImage img = ImageIO.read(new ByteArrayInputStream(pngBytes));
					if (img != null) {
						DynamicTexture dyn = new DynamicTexture(img);
						this.textures[i] = mc.getTextureManager().getDynamicTextureLocation("am2_zone_vfx_" + i, dyn);
					}
				} catch (Throwable t) {
					LogHelper.warn("Failed decoding zone texture " + i + ": " + t.getMessage());
				}
			}
		}
	}

	private static Map<String, CubeData> parseCubes(JsonObject root, float resW, float resH) {
		Map<String, CubeData> map = new HashMap<String, CubeData>();
		JsonArray elements = root.getAsJsonArray("elements");
		if (elements == null) {
			return map;
		}
		for (int i = 0; i < elements.size(); i++) {
			JsonObject el = elements.get(i).getAsJsonObject();
			if (el.has("visibility") && !el.get("visibility").getAsBoolean()) {
				continue;
			}
			String uuid = el.has("uuid") ? el.get("uuid").getAsString() : null;
			if (uuid == null) {
				continue;
			}
			JsonArray fromArr = el.getAsJsonArray("from");
			JsonArray toArr = el.getAsJsonArray("to");
			if (fromArr == null || toArr == null || fromArr.size() < 3 || toArr.size() < 3) {
				continue;
			}

			JsonObject facesObj = el.getAsJsonObject("faces");
			if (facesObj == null) {
				continue;
			}
			List<FaceQuad> faceList = new ArrayList<FaceQuad>(6);
			addFace(facesObj, "north", FACE_NORTH, resW, resH, faceList);
			addFace(facesObj, "south", FACE_SOUTH, resW, resH, faceList);
			addFace(facesObj, "east", FACE_EAST, resW, resH, faceList);
			addFace(facesObj, "west", FACE_WEST, resW, resH, faceList);
			addFace(facesObj, "up", FACE_UP, resW, resH, faceList);
			addFace(facesObj, "down", FACE_DOWN, resW, resH, faceList);
			if (faceList.isEmpty()) {
				continue;
			}

			CubeData cube = new CubeData();
			cube.x0 = fromArr.get(0).getAsFloat() * BLOCK_SCALE;
			cube.y0 = fromArr.get(1).getAsFloat() * BLOCK_SCALE;
			cube.z0 = fromArr.get(2).getAsFloat() * BLOCK_SCALE;
			cube.x1 = toArr.get(0).getAsFloat() * BLOCK_SCALE;
			cube.y1 = toArr.get(1).getAsFloat() * BLOCK_SCALE;
			cube.z1 = toArr.get(2).getAsFloat() * BLOCK_SCALE;

			JsonArray origArr = el.getAsJsonArray("origin");
			if (origArr != null && origArr.size() >= 3) {
				cube.ox = origArr.get(0).getAsFloat() * BLOCK_SCALE;
				cube.oy = origArr.get(1).getAsFloat() * BLOCK_SCALE;
				cube.oz = origArr.get(2).getAsFloat() * BLOCK_SCALE;
			} else {
				cube.ox = 0.5f * (cube.x0 + cube.x1);
				cube.oy = 0.5f * (cube.y0 + cube.y1);
				cube.oz = 0.5f * (cube.z0 + cube.z1);
			}

			JsonArray rotArr = el.getAsJsonArray("rotation");
			if (rotArr != null && rotArr.size() >= 3) {
				cube.rx = rotArr.get(0).getAsFloat();
				cube.ry = rotArr.get(1).getAsFloat();
				cube.rz = rotArr.get(2).getAsFloat();
				cube.hasRot = Math.abs(cube.rx) > 0.001f || Math.abs(cube.ry) > 0.001f || Math.abs(cube.rz) > 0.001f;
			}

			cube.faces = faceList.toArray(new FaceQuad[faceList.size()]);
			map.put(uuid, cube);
		}
		return map;
	}

	private static void addFace(JsonObject facesObj, String key, int dir, float resW, float resH, List<FaceQuad> out) {
		if (!facesObj.has(key) || !facesObj.get(key).isJsonObject()) {
			return;
		}
		JsonObject f = facesObj.getAsJsonObject(key);
		if (!f.has("texture") || f.get("texture").isJsonNull()) {
			return;
		}
		int texIdx = f.get("texture").getAsInt();
		if (texIdx < 0) {
			return;
		}
		JsonArray uv = f.getAsJsonArray("uv");
		if (uv == null || uv.size() < 4) {
			return;
		}
		float u0 = uv.get(0).getAsFloat();
		float v0 = uv.get(1).getAsFloat();
		float u1 = uv.get(2).getAsFloat();
		float v1 = uv.get(3).getAsFloat();
		if (Math.abs(u1 - u0) <= BLANK_UV_THRESHOLD && Math.abs(v1 - v0) <= BLANK_UV_THRESHOLD) {
			return;
		}
		FaceQuad quad = new FaceQuad();
		quad.dir = dir;
		quad.texIndex = texIdx;
		quad.u0 = u0 / resW;
		quad.v0 = v0 / resH;
		quad.u1 = u1 / resW;
		quad.v1 = v1 / resH;
		out.add(quad);
	}

	private void parseOutliner(JsonObject root, Map<String, CubeData> cubeMap) {
		JsonArray outliner = root.getAsJsonArray("outliner");
		if (outliner == null || outliner.size() == 0) {
			return;
		}
		for (int i = 0; i < outliner.size(); i++) {
			JsonElement el = outliner.get(i);
			if (el.isJsonObject()) {
				BoneNode rootNode = parseBoneNode(el.getAsJsonObject(), cubeMap);
				if (rootNode != null) {
					indexTopLevelBones(rootNode);
				}
			}
		}
	}

	private void indexTopLevelBones(BoneNode node) {
		String lname = node.name.toLowerCase(Locale.ROOT);
		if ("ring_base".equals(lname)) {
			this.ringBone = node;
			return;
		}
		if (lname.startsWith("spikes_zone_")) {
			try {
				int idx = Integer.parseInt(lname.substring("spikes_zone_".length())) - 1;
				if (idx >= 0 && idx < spikeTiers.length) {
					spikeTiers[idx] = node;
				}
			} catch (NumberFormatException ignored) {
			}
			return;
		}
		for (int i = 0; i < node.children.length; i++) {
			indexTopLevelBones(node.children[i]);
		}
	}

	private static BoneNode parseBoneNode(JsonObject obj, Map<String, CubeData> cubeMap) {
		if (obj.has("visibility") && !obj.get("visibility").getAsBoolean()) {
			return null;
		}
		BoneNode node = new BoneNode();
		node.name = obj.has("name") ? obj.get("name").getAsString() : "";

		JsonArray orig = obj.getAsJsonArray("origin");
		if (orig != null && orig.size() >= 3) {
			node.ox = orig.get(0).getAsFloat() * BLOCK_SCALE;
			node.oy = orig.get(1).getAsFloat() * BLOCK_SCALE;
			node.oz = orig.get(2).getAsFloat() * BLOCK_SCALE;
		}

		JsonArray rot = obj.getAsJsonArray("rotation");
		if (rot != null && rot.size() >= 3) {
			node.rx = rot.get(0).getAsFloat();
			node.ry = rot.get(1).getAsFloat();
			node.rz = rot.get(2).getAsFloat();
			node.hasRot = Math.abs(node.rx) > 0.001f || Math.abs(node.ry) > 0.001f || Math.abs(node.rz) > 0.001f;
		}

		List<CubeData> localCubes = new ArrayList<CubeData>();
		List<BoneNode> childBones = new ArrayList<BoneNode>();
		JsonArray children = obj.getAsJsonArray("children");
		if (children != null) {
			for (int i = 0; i < children.size(); i++) {
				JsonElement ch = children.get(i);
				if (ch.isJsonPrimitive()) {
					CubeData c = cubeMap.get(ch.getAsString());
					if (c != null) {
						localCubes.add(c);
					}
				} else if (ch.isJsonObject()) {
					BoneNode child = parseBoneNode(ch.getAsJsonObject(), cubeMap);
					if (child != null) {
						childBones.add(child);
					}
				}
			}
		}
		node.cubes = localCubes.toArray(new CubeData[localCubes.size()]);
		node.children = childBones.toArray(new BoneNode[childBones.size()]);
		return node;
	}

	/**
	 * Renders the Zone circle scaled to radiusBlocks and renders activeTiers (1..8) pairs of twin mountain spikes.
	 */
	public void render(
			float radiusBlocks,
			float ringRotDeg,
			int activeTiers,
			int[] tierColorsRgb,
			float[] tierSpikePopY,
			float ringAlpha,
			int ringColorRgb,
			boolean lowGfx
	) {
		if (ringBone == null || textures == null || textures.length == 0 || ringAlpha <= 0.01f) {
			return;
		}

		float rScale = Math.max(0.5f, radiusBlocks / 2.0f);
		float crystalScale = (rScale <= 1.0f) ? rScale : Math.min(6.0f, (float) Math.pow(rScale, 0.78D));
		int clampedTiers = Math.max(1, Math.min(spikeTiers.length, activeTiers));

		Minecraft mc = Minecraft.getMinecraft();
		Tessellator tess = Tessellator.instance;
		int[] boundTex = new int[] { -1 };

		// Pass 1: Crisp semi-translucent base pass
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		renderScenePass(rScale, crystalScale, ringRotDeg, clampedTiers, tierColorsRgb, tierSpikePopY, ringAlpha, ringColorRgb, mc, tess, boundTex);

		// Pass 2: Subtle emissive glow pass (kept soft so Ender/dark hues stay rich)
		if (!lowGfx) {
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
			renderScenePass(rScale, crystalScale, ringRotDeg, clampedTiers, tierColorsRgb, tierSpikePopY, ringAlpha * 0.22f, ringColorRgb, mc, tess, boundTex);
		}
	}

	private void renderScenePass(
			float rScale,
			float crystalScale,
			float ringRotDeg,
			int activeTiers,
			int[] tierColorsRgb,
			float[] tierSpikePopY,
			float alpha,
			int ringColorRgb,
			Minecraft mc,
			Tessellator tess,
			int[] boundTex
	) {
		float rr = ((ringColorRgb >> 16) & 0xFF) / 255.0f;
		float rg = ((ringColorRgb >> 8) & 0xFF) / 255.0f;
		float rb = (ringColorRgb & 0xFF) / 255.0f;

		// 1. Ground runic circle (scaled smoothly horizontally & rotated around center)
		GL11.glPushMatrix();
		GL11.glRotatef(ringRotDeg, 0.0f, 1.0f, 0.0f);
		GL11.glScalef(rScale, 1.0f, rScale);
		renderBoneRecursive(ringBone, rr, rg, rb, alpha, mc, tess, boundTex);
		GL11.glPopMatrix();

		// 2. Twin-mountain spikes (anchored on perimeter rScale and scaled proportionally with Zone size)
		for (int t = 0; t < activeTiers; t++) {
			BoneNode tier = spikeTiers[t];
			if (tier == null) {
				continue;
			}
			float popY = (tierSpikePopY != null && t < tierSpikePopY.length) ? tierSpikePopY[t] : 1.0f;
			if (popY <= 0.01f) {
				continue;
			}
			int c = (tierColorsRgb != null && t < tierColorsRgb.length) ? tierColorsRgb[t] : ringColorRgb;
			float sr = ((c >> 16) & 0xFF) / 255.0f;
			float sg = ((c >> 8) & 0xFF) / 255.0f;
			float sb = (c & 0xFF) / 255.0f;

			for (int p = 0; p < tier.children.length; p++) {
				BoneNode pt = tier.children[p];
				GL11.glPushMatrix();
				GL11.glTranslatef(pt.ox * rScale, 0.0f, pt.oz * rScale);
				GL11.glScalef(crystalScale, crystalScale * popY, crystalScale);
				GL11.glTranslatef(-pt.ox, 0.0f, -pt.oz);
				renderBoneRecursive(pt, sr, sg, sb, alpha, mc, tess, boundTex);
				GL11.glPopMatrix();
			}
		}
	}

	private void renderBoneRecursive(
			BoneNode node,
			float r,
			float g,
			float b,
			float alpha,
			Minecraft mc,
			Tessellator tess,
			int[] boundTex
	) {
		if (node.hasRot) {
			GL11.glPushMatrix();
			GL11.glTranslatef(node.ox, node.oy, node.oz);
			applyZYX(node.rx, node.ry, node.rz);
			GL11.glTranslatef(-node.ox, -node.oy, -node.oz);
		}

		for (int c = 0; c < node.cubes.length; c++) {
			drawCube(node.cubes[c], r, g, b, alpha, mc, tess, boundTex);
		}
		for (int i = 0; i < node.children.length; i++) {
			renderBoneRecursive(node.children[i], r, g, b, alpha, mc, tess, boundTex);
		}

		if (node.hasRot) {
			GL11.glPopMatrix();
		}
	}

	private void drawCube(
			CubeData cube,
			float r,
			float g,
			float b,
			float alpha,
			Minecraft mc,
			Tessellator tess,
			int[] boundTex
	) {
		if (cube.hasRot) {
			GL11.glPushMatrix();
			GL11.glTranslatef(cube.ox, cube.oy, cube.oz);
			applyZYX(cube.rx, cube.ry, cube.rz);
			GL11.glTranslatef(-cube.ox, -cube.oy, -cube.oz);
		}

		float x0 = cube.x0, y0 = cube.y0, z0 = cube.z0;
		float x1 = cube.x1, y1 = cube.y1, z1 = cube.z1;

		for (int f = 0; f < cube.faces.length; f++) {
			FaceQuad face = cube.faces[f];
			if (face.texIndex < 0 || face.texIndex >= textures.length) {
				continue;
			}
			ResourceLocation loc = textures[face.texIndex];
			if (loc == null) {
				continue;
			}
			if (boundTex[0] != face.texIndex) {
				mc.renderEngine.bindTexture(loc);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
				boundTex[0] = face.texIndex;
			}

			float u0 = face.u0, v0 = face.v0, u1 = face.u1, v1 = face.v1;
			tess.startDrawingQuads();
			tess.setColorRGBA_F(r, g, b, alpha);
			switch (face.dir) {
				case FACE_NORTH:
					tess.addVertexWithUV(x1, y1, z0, u0, v0);
					tess.addVertexWithUV(x0, y1, z0, u1, v0);
					tess.addVertexWithUV(x0, y0, z0, u1, v1);
					tess.addVertexWithUV(x1, y0, z0, u0, v1);
					break;
				case FACE_SOUTH:
					tess.addVertexWithUV(x0, y1, z1, u0, v0);
					tess.addVertexWithUV(x1, y1, z1, u1, v0);
					tess.addVertexWithUV(x1, y0, z1, u1, v1);
					tess.addVertexWithUV(x0, y0, z1, u0, v1);
					break;
				case FACE_EAST:
					tess.addVertexWithUV(x1, y1, z1, u0, v0);
					tess.addVertexWithUV(x1, y1, z0, u1, v0);
					tess.addVertexWithUV(x1, y0, z0, u1, v1);
					tess.addVertexWithUV(x1, y0, z1, u0, v1);
					break;
				case FACE_WEST:
					tess.addVertexWithUV(x0, y1, z0, u0, v0);
					tess.addVertexWithUV(x0, y1, z1, u1, v0);
					tess.addVertexWithUV(x0, y0, z1, u1, v1);
					tess.addVertexWithUV(x0, y0, z0, u0, v1);
					break;
				case FACE_UP:
					tess.addVertexWithUV(x0, y1, z0, u0, v0);
					tess.addVertexWithUV(x1, y1, z0, u1, v0);
					tess.addVertexWithUV(x1, y1, z1, u1, v1);
					tess.addVertexWithUV(x0, y1, z1, u0, v1);
					break;
				case FACE_DOWN:
				default:
					tess.addVertexWithUV(x0, y0, z1, u0, v0);
					tess.addVertexWithUV(x1, y0, z1, u1, v0);
					tess.addVertexWithUV(x1, y0, z0, u1, v1);
					tess.addVertexWithUV(x0, y0, z0, u0, v1);
					break;
			}
			tess.draw();
		}

		if (cube.hasRot) {
			GL11.glPopMatrix();
		}
	}

	private static void applyZYX(float rx, float ry, float rz) {
		if (Math.abs(rz) > 0.001f) GL11.glRotatef(rz, 0.0f, 0.0f, 1.0f);
		if (Math.abs(ry) > 0.001f) GL11.glRotatef(ry, 0.0f, 1.0f, 0.0f);
		if (Math.abs(rx) > 0.001f) GL11.glRotatef(rx, 1.0f, 0.0f, 0.0f);
	}

	private static float getFloat(JsonObject obj, String key, float def) {
		if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
			return obj.get(key).getAsFloat();
		}
		return def;
	}
}
