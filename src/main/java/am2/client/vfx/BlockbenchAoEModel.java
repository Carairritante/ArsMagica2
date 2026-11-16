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
 * Loads, caches and renders the Blockbench 5.0 (.bbmodel) AoE shockwave rig and its one-shot keyframe animation.
 */
@SideOnly(Side.CLIENT)
public final class BlockbenchAoEModel {

	private static final float BLOCK_SCALE = 1.0f / 16.0f;
	private static final float BLANK_UV_THRESHOLD = 2.5f;
	private static final float ACTIVE_ANIM_END_SEC = 0.65f;
	private static final ResourceLocation MODEL_LOC = new ResourceLocation("arsmagica2", "models/aoe/aoe_shockwave.bbmodel");

	private static final int FACE_NORTH = 0;
	private static final int FACE_SOUTH = 1;
	private static final int FACE_EAST = 2;
	private static final int FACE_WEST = 3;
	private static final int FACE_UP = 4;
	private static final int FACE_DOWN = 5;

	private static final int INTERP_LINEAR = 0;
	private static final int INTERP_CATMULLROM = 1;
	private static final int INTERP_STEP = 2;

	private static BlockbenchAoEModel cached;
	private static boolean loadAttempted = false;

	private ResourceLocation[] textures;
	private BoneNode rootBone;
	private final Map<String, BoneTrack> tracks = new HashMap<String, BoneTrack>();
	private float animLength = ACTIVE_ANIM_END_SEC;
	private final EvalPose scratchPose = new EvalPose();

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
		boolean isCrestWall;
		CubeData[] cubes;
		BoneNode[] children;
	}

	private static final class KeyframePoint {
		float time;
		float x, y, z;
		int interp;
	}

	private static final class ChannelTrack {
		KeyframePoint[] points;
	}

	private static final class BoneTrack {
		ChannelTrack position;
		ChannelTrack rotation;
		ChannelTrack scale;
	}

	private static final class EvalPose {
		float px, py, pz;
		float rx, ry, rz;
		float sx = 1.0f, sy = 1.0f, sz = 1.0f;
	}

	private BlockbenchAoEModel() {
	}

	public static BlockbenchAoEModel get() {
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

	private static BlockbenchAoEModel load() {
		InputStreamReader reader = null;
		try {
			IResource res = Minecraft.getMinecraft().getResourceManager().getResource(MODEL_LOC);
			reader = new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8);
			JsonObject root = new JsonParser().parse(reader).getAsJsonObject();

			BlockbenchAoEModel model = new BlockbenchAoEModel();
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
			model.parseAnimations(root);

			if (model.rootBone == null) {
				LogHelper.warn("AoE shockwave bbmodel loaded without root bone.");
				return null;
			}
			return model;
		} catch (Throwable t) {
			LogHelper.error("Failed to load AoE Blockbench model: " + t.getMessage());
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
						this.textures[i] = mc.getTextureManager().getDynamicTextureLocation("am2_aoe_vfx_" + i, dyn);
					}
				} catch (Throwable t) {
					LogHelper.warn("Failed decoding AoE texture " + i + ": " + t.getMessage());
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
				BoneNode node = parseBoneNode(el.getAsJsonObject(), cubeMap);
				if (node != null && ("root".equalsIgnoreCase(node.name) || this.rootBone == null)) {
					this.rootBone = node;
				}
			}
		}
	}

	private static BoneNode parseBoneNode(JsonObject obj, Map<String, CubeData> cubeMap) {
		if (obj.has("visibility") && !obj.get("visibility").getAsBoolean()) {
			return null;
		}
		BoneNode node = new BoneNode();
		node.name = obj.has("name") ? obj.get("name").getAsString() : "";
		node.isCrestWall = "wave_crest_wall".equalsIgnoreCase(node.name);

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

	private void parseAnimations(JsonObject root) {
		JsonArray animations = root.getAsJsonArray("animations");
		if (animations == null || animations.size() == 0) {
			return;
		}
		JsonObject animObj = animations.get(0).getAsJsonObject();
		this.animLength = getFloat(animObj, "length", ACTIVE_ANIM_END_SEC);

		JsonObject animators = animObj.getAsJsonObject("animators");
		if (animators == null) {
			return;
		}

		for (Map.Entry<String, JsonElement> entry : animators.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue;
			}
			JsonObject animNode = entry.getValue().getAsJsonObject();
			String boneName = animNode.has("name") ? animNode.get("name").getAsString() : "";
			JsonArray kfs = animNode.getAsJsonArray("keyframes");
			if (boneName.isEmpty() || kfs == null || kfs.size() == 0) {
				continue;
			}

			List<KeyframePoint> posList = new ArrayList<KeyframePoint>();
			List<KeyframePoint> rotList = new ArrayList<KeyframePoint>();
			List<KeyframePoint> scaleList = new ArrayList<KeyframePoint>();

			for (int k = 0; k < kfs.size(); k++) {
				JsonObject kf = kfs.get(k).getAsJsonObject();
				String channel = kf.has("channel") ? kf.get("channel").getAsString() : "";
				float time = getFloat(kf, "time", 0.0f);
				String interpStr = kf.has("interpolation") ? kf.get("interpolation").getAsString() : "linear";
				int interp = INTERP_LINEAR;
				if ("catmullrom".equalsIgnoreCase(interpStr)) {
					interp = INTERP_CATMULLROM;
				} else if ("step".equalsIgnoreCase(interpStr)) {
					interp = INTERP_STEP;
				}

				JsonArray dps = kf.getAsJsonArray("data_points");
				if (dps == null || dps.size() == 0) {
					continue;
				}
				JsonObject dp = dps.get(0).getAsJsonObject();
				KeyframePoint pt = new KeyframePoint();
				pt.time = time;
				float defVal = "scale".equals(channel) ? 1.0f : 0.0f;
				pt.x = parseJsonNumber(dp.get("x"), defVal);
				pt.y = parseJsonNumber(dp.get("y"), defVal);
				pt.z = parseJsonNumber(dp.get("z"), defVal);
				pt.interp = interp;

				if ("position".equals(channel)) {
					posList.add(pt);
				} else if ("rotation".equals(channel)) {
					rotList.add(pt);
				} else if ("scale".equals(channel)) {
					scaleList.add(pt);
				}
			}

			BoneTrack track = new BoneTrack();
			track.position = toTrack(posList);
			track.rotation = toTrack(rotList);
			track.scale = toTrack(scaleList);
			this.tracks.put(boneName.toLowerCase(Locale.ROOT), track);
		}
	}

	private static ChannelTrack toTrack(List<KeyframePoint> list) {
		if (list.isEmpty()) {
			return null;
		}
		for (int i = 1; i < list.size(); i++) {
			KeyframePoint cur = list.get(i);
			int j = i - 1;
			while (j >= 0 && list.get(j).time > cur.time) {
				list.set(j + 1, list.get(j));
				j--;
			}
			list.set(j + 1, cur);
		}
		ChannelTrack track = new ChannelTrack();
		track.points = list.toArray(new KeyframePoint[list.size()]);
		return track;
	}

	/**
	 * Renders one-shot Blockbench AoE shockwave animation scaled to radiusBlocks at local origin.
	 */
	public void render(float radiusBlocks, float progress, int colorRgb, boolean lowGfx) {
		if (rootBone == null || textures == null || textures.length == 0) {
			return;
		}
		float clamped = Math.max(0.0f, Math.min(1.0f, progress));
		float fadeIn = Math.min(1.0f, clamped / 0.08f);
		float fadeOut = (clamped > 0.62f) ? Math.max(0.0f, 1.0f - (clamped - 0.62f) / 0.38f) : 1.0f;
		float baseAlpha = 0.94f * fadeIn * fadeOut;
		if (baseAlpha <= 0.01f) {
			return;
		}

		// Play through 0.00s..0.65s where all four wave layers launch, expand, flatten, and vanish.
		float animTime = clamped * Math.min(ACTIVE_ANIM_END_SEC, animLength);
		float rScale = Math.max(0.5f, radiusBlocks / 2.0f);
		float crestYScale = (rScale <= 1.0f) ? rScale : Math.min(4.5f, (float) Math.pow(rScale, 0.55D));

		if (colorRgb == -1) {
			colorRgb = 0xFFFFFF;
		}
		float r1 = ((colorRgb >> 16) & 0xFF) / 255.0f;
		float g1 = ((colorRgb >> 8) & 0xFF) / 255.0f;
		float b1 = (colorRgb & 0xFF) / 255.0f;
		if (colorRgb != 0xFFFFFF) {
			// Lift very dark affinities (Ender / Earth) slightly so detail stays readable against dark ground.
			float lum = r1 * 0.299f + g1 * 0.587f + b1 * 0.114f;
			if (lum < 0.28f && lum > 0.001f) {
				float boost = 0.28f / lum;
				r1 = Math.min(1.0f, r1 * boost);
				g1 = Math.min(1.0f, g1 * boost);
				b1 = Math.min(1.0f, b1 * boost);
			}
		}

		Minecraft mc = Minecraft.getMinecraft();
		Tessellator tess = Tessellator.instance;
		int[] boundTex = new int[] { -1 };

		// Pass 1: Crisp semi-translucent anime wave pass
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		renderRootAnimated(rScale, crestYScale, animTime, r1, g1, b1, baseAlpha, mc, tess, boundTex);

		// Pass 2: Luminous core highlight pass
		if (!lowGfx) {
			float r2 = r1 * 0.65f + 0.35f;
			float g2 = g1 * 0.65f + 0.35f;
			float b2 = b1 * 0.65f + 0.35f;
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
			renderRootAnimated(rScale, crestYScale, animTime, r2, g2, b2, baseAlpha * 0.34f, mc, tess, boundTex);
		}
	}

	private void renderRootAnimated(
			float rScale,
			float crestYScale,
			float animTime,
			float r,
			float g,
			float b,
			float alpha,
			Minecraft mc,
			Tessellator tess,
			int[] boundTex
	) {
		for (int i = 0; i < rootBone.children.length; i++) {
			BoneNode layer = rootBone.children[i];
			evalPose(layer.name, animTime, scratchPose);
			if (scratchPose.sx <= 0.005f || scratchPose.sy <= 0.005f || scratchPose.sz <= 0.005f) {
				continue;
			}
			float yScale = layer.isCrestWall ? crestYScale : 1.0f;

			GL11.glPushMatrix();
			GL11.glTranslatef(
					layer.ox + scratchPose.px * BLOCK_SCALE,
					layer.oy + scratchPose.py * BLOCK_SCALE * yScale,
					layer.oz + scratchPose.pz * BLOCK_SCALE
			);
			applyZYX(layer.rx + scratchPose.rx, layer.ry + scratchPose.ry, layer.rz + scratchPose.rz);
			GL11.glScalef(rScale * scratchPose.sx, yScale * scratchPose.sy, rScale * scratchPose.sz);
			GL11.glTranslatef(-layer.ox, -layer.oy, -layer.oz);

			renderBoneContents(layer, r, g, b, alpha, mc, tess, boundTex);
			GL11.glPopMatrix();
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

		renderBoneContents(node, r, g, b, alpha, mc, tess, boundTex);

		if (node.hasRot) {
			GL11.glPopMatrix();
		}
	}

	private void renderBoneContents(
			BoneNode node,
			float r,
			float g,
			float b,
			float alpha,
			Minecraft mc,
			Tessellator tess,
			int[] boundTex
	) {
		for (int c = 0; c < node.cubes.length; c++) {
			drawCube(node.cubes[c], r, g, b, alpha, mc, tess, boundTex);
		}
		for (int i = 0; i < node.children.length; i++) {
			renderBoneRecursive(node.children[i], r, g, b, alpha, mc, tess, boundTex);
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

	private void evalPose(String boneName, float time, EvalPose out) {
		out.px = 0.0f; out.py = 0.0f; out.pz = 0.0f;
		out.rx = 0.0f; out.ry = 0.0f; out.rz = 0.0f;
		out.sx = 1.0f; out.sy = 1.0f; out.sz = 1.0f;

		if (boneName == null) {
			return;
		}
		BoneTrack track = tracks.get(boneName.toLowerCase(Locale.ROOT));
		if (track == null) {
			return;
		}
		if (track.position != null) {
			sampleChannel(track.position, time, out, 0);
		}
		if (track.rotation != null) {
			sampleChannel(track.rotation, time, out, 1);
		}
		if (track.scale != null) {
			sampleChannel(track.scale, time, out, 2);
		}
	}

	private static void sampleChannel(ChannelTrack ch, float time, EvalPose out, int kind) {
		KeyframePoint[] pts = ch.points;
		int n = pts.length;
		if (n == 0) {
			return;
		}
		if (n == 1 || time <= pts[0].time) {
			assignPoseValue(out, kind, pts[0].x, pts[0].y, pts[0].z);
			return;
		}
		if (time >= pts[n - 1].time) {
			assignPoseValue(out, kind, pts[n - 1].x, pts[n - 1].y, pts[n - 1].z);
			return;
		}

		int idx = 0;
		for (int i = 0; i < n - 1; i++) {
			if (time >= pts[i].time && time <= pts[i + 1].time) {
				idx = i;
				break;
			}
		}

		KeyframePoint before = pts[idx];
		KeyframePoint after = pts[idx + 1];
		float span = after.time - before.time;
		if (span <= 1e-6f || before.interp == INTERP_STEP) {
			assignPoseValue(out, kind, before.x, before.y, before.z);
			return;
		}

		float alpha = (time - before.time) / span;
		if (before.interp == INTERP_CATMULLROM || after.interp == INTERP_CATMULLROM) {
			KeyframePoint p0 = pts[Math.max(0, idx - 1)];
			KeyframePoint p3 = pts[Math.min(n - 1, idx + 2)];
			float vx = catmullRom(p0.x, before.x, after.x, p3.x, alpha);
			float vy = catmullRom(p0.y, before.y, after.y, p3.y, alpha);
			float vz = catmullRom(p0.z, before.z, after.z, p3.z, alpha);
			assignPoseValue(out, kind, vx, vy, vz);
		} else {
			float vx = before.x + (after.x - before.x) * alpha;
			float vy = before.y + (after.y - before.y) * alpha;
			float vz = before.z + (after.z - before.z) * alpha;
			assignPoseValue(out, kind, vx, vy, vz);
		}
	}

	private static float catmullRom(float y0, float y1, float y2, float y3, float t) {
		float t2 = t * t;
		float t3 = t2 * t;
		return 0.5f * ((2.0f * y1)
				+ (-y0 + y2) * t
				+ (2.0f * y0 - 5.0f * y1 + 4.0f * y2 - y3) * t2
				+ (-y0 + 3.0f * y1 - 3.0f * y2 + y3) * t3);
	}

	private static void assignPoseValue(EvalPose out, int kind, float x, float y, float z) {
		if (kind == 0) {
			out.px = x; out.py = y; out.pz = z;
		} else if (kind == 1) {
			out.rx = x; out.ry = y; out.rz = z;
		} else {
			out.sx = x; out.sy = y; out.sz = z;
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

	private static float parseJsonNumber(JsonElement el, float def) {
		if (el == null || el.isJsonNull()) {
			return def;
		}
		try {
			if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
				return el.getAsFloat();
			}
			return Float.parseFloat(el.getAsString().trim());
		} catch (Throwable ignored) {
			return def;
		}
	}
}
