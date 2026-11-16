package am2.client.vfx;

import am2.LogHelper;
import am2.api.spell.enums.Affinity;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads, caches and renders Blockbench 5.0 (.bbmodel) anime beam rigs and animations.
 * Pre-compiles geometry into primitive arrays for allocation-free per-frame rendering.
 */
@SideOnly(Side.CLIENT)
public final class BlockbenchBeamModel {

	private static final float BLOCK_SCALE = 1.0f / 16.0f;
	private static final float MODEL_ORIGIN_Y = 22.0f;
	private static final float MODEL_MUZZLE_Z = -14.0f;
	private static final float MODEL_SHAFT_LEN = 184.0f;
	private static final float BLANK_UV_THRESHOLD = 2.01f;

	private static final int BONE_ROOT = 0;
	private static final int BONE_MUZZLE = 1;
	private static final int BONE_SHAFT = 2;
	private static final int BONE_TRAVELER = 3;
	private static final int BONE_IMPACT = 4;

	private static final int FACE_NORTH = 0;
	private static final int FACE_SOUTH = 1;
	private static final int FACE_EAST = 2;
	private static final int FACE_WEST = 3;
	private static final int FACE_UP = 4;
	private static final int FACE_DOWN = 5;

	private static final int INTERP_LINEAR = 0;
	private static final int INTERP_CATMULLROM = 1;
	private static final int INTERP_STEP = 2;

	private static final EnumMap<Affinity, BlockbenchBeamModel> CACHE = new EnumMap<Affinity, BlockbenchBeamModel>(Affinity.class);
	private static final EnumMap<Affinity, Boolean> FAILED = new EnumMap<Affinity, Boolean>(Affinity.class);

	private final Affinity affinity;
	private ResourceLocation[] textures;
	private boolean[] additiveHint;
	private BoneNode rootBone;
	private AnimationClip channeledLoop;
	private AnimationClip castBurst;

	private static final class FaceQuad {
		int dir;
		int texIndex;
		float u0, v0, u1, v1;
	}

	private static final class CubeData {
		String name;
		float x0, y0, z0;
		float x1, y1, z1;
		float ox, oy, oz;
		float rx, ry, rz;
		boolean hasRot;
		FaceQuad[] faces;
		boolean emissiveLayer;
		boolean lowGfxSkip;
		float centerZbb;
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
		String boneName;
		ChannelTrack position;
		ChannelTrack rotation;
		ChannelTrack scale;
	}

	private static final class AnimationClip {
		float length;
		boolean loop;
		Map<String, BoneTrack> tracks = new HashMap<String, BoneTrack>();
	}

	private static final class BoneNode {
		String name;
		int role;
		float ox, oy, oz;
		float rx, ry, rz;
		boolean hasBindRot;
		boolean emissiveLayer;
		boolean lowGfxSkip;
		boolean travelerChild;
		CubeData[] cubes;
		BoneNode[] children;
	}

	private static final class EvalPose {
		float px, py, pz;
		float rx, ry, rz;
		float sx, sy, sz;
	}

	private final EvalPose scratchPose = new EvalPose();
	private final EvalPose scratchLoopPose = new EvalPose();
	private final EvalPose scratchBurstPose = new EvalPose();

	private BlockbenchBeamModel(Affinity affinity) {
		this.affinity = affinity;
	}

	public static BlockbenchBeamModel get(Affinity affinity) {
		if (affinity == null) {
			affinity = Affinity.NONE;
		}
		BlockbenchBeamModel cached = CACHE.get(affinity);
		if (cached != null) {
			return cached;
		}
		if (Boolean.TRUE.equals(FAILED.get(affinity))) {
			return null;
		}
		BlockbenchBeamModel loaded = load(affinity);
		if (loaded != null) {
			CACHE.put(affinity, loaded);
		} else {
			FAILED.put(affinity, Boolean.TRUE);
		}
		return loaded;
	}

	public static void clearCache() {
		Minecraft mc = Minecraft.getMinecraft();
		for (BlockbenchBeamModel model : CACHE.values()) {
			if (model != null && model.textures != null && mc != null && mc.getTextureManager() != null) {
				for (int i = 0; i < model.textures.length; i++) {
					if (model.textures[i] != null) {
						mc.getTextureManager().deleteTexture(model.textures[i]);
					}
				}
			}
		}
		CACHE.clear();
		FAILED.clear();
	}

	private static BlockbenchBeamModel load(Affinity affinity) {
		String key = affinity.name().toLowerCase(Locale.ROOT);
		ResourceLocation loc = new ResourceLocation("arsmagica2", "models/beams/beam_" + key + "_anime.bbmodel");
		InputStreamReader reader = null;
		try {
			IResource res = Minecraft.getMinecraft().getResourceManager().getResource(loc);
			reader = new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8);
			JsonObject root = new JsonParser().parse(reader).getAsJsonObject();

			BlockbenchBeamModel model = new BlockbenchBeamModel(affinity);
			float resW = 256.0f;
			float resH = 256.0f;
			if (root.has("resolution") && root.get("resolution").isJsonObject()) {
				JsonObject resObj = root.getAsJsonObject("resolution");
				resW = getFloat(resObj, "width", 256.0f);
				resH = getFloat(resObj, "height", 256.0f);
			}

			model.loadTextures(root, key);
			Map<String, CubeData> cubeMap = parseCubes(root, resW, resH);
			model.rootBone = parseOutliner(root, cubeMap);
			model.parseAnimations(root);
			return model;
		} catch (Throwable t) {
			LogHelper.error("Failed to load Blockbench beam model for affinity " + key + ": " + t.getMessage());
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

	private void loadTextures(JsonObject root, String affKey) {
		JsonArray texArr = root.getAsJsonArray("textures");
		if (texArr == null) {
			this.textures = new ResourceLocation[0];
			this.additiveHint = new boolean[0];
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		int count = texArr.size();
		this.textures = new ResourceLocation[count];
		this.additiveHint = new boolean[count];

		for (int i = 0; i < count; i++) {
			JsonObject texObj = texArr.get(i).getAsJsonObject();
			String name = texObj.has("name") ? texObj.get("name").getAsString().toLowerCase(Locale.ROOT) : ("tex_" + i);
			String source = texObj.has("source") ? texObj.get("source").getAsString() : "";
			int comma = source.indexOf(',');
			if (comma >= 0) {
				try {
					byte[] pngBytes = Base64.getDecoder().decode(source.substring(comma + 1));
					BufferedImage img = ImageIO.read(new ByteArrayInputStream(pngBytes));
					if (img != null) {
						DynamicTexture dyn = new DynamicTexture(img);
						ResourceLocation texLoc = mc.getTextureManager().getDynamicTextureLocation("am2_beam_" + affKey + "_" + i, dyn);
						this.textures[i] = texLoc;
					}
				} catch (Throwable t) {
					LogHelper.warn("Failed decoding beam texture " + i + " for " + affKey + ": " + t.getMessage());
				}
			}
			// Mark bright core/muzzle/ribbon sheets as strong emissive glow candidates
			this.additiveHint[i] = name.contains("beam_strips") || name.contains("muzzle") || name.contains("ribbon") || name.contains("shock");
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
			String name = el.has("name") ? el.get("name").getAsString().toLowerCase(Locale.ROOT) : "";
			if (name.startsWith("ref_")) {
				continue;
			}

			JsonArray fromArr = el.getAsJsonArray("from");
			JsonArray toArr = el.getAsJsonArray("to");
			if (fromArr == null || toArr == null || fromArr.size() < 3 || toArr.size() < 3) {
				continue;
			}

			float inf = getFloat(el, "inflate", 0.0f);
			float fx = fromArr.get(0).getAsFloat() - inf;
			float fy = (fromArr.get(1).getAsFloat() - inf) - MODEL_ORIGIN_Y;
			float fz = (fromArr.get(2).getAsFloat() - inf) - MODEL_MUZZLE_Z;
			float tx = toArr.get(0).getAsFloat() + inf;
			float ty = (toArr.get(1).getAsFloat() + inf) - MODEL_ORIGIN_Y;
			float tz = (toArr.get(2).getAsFloat() + inf) - MODEL_MUZZLE_Z;

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
			cube.name = name;
			cube.x0 = fx * BLOCK_SCALE;
			cube.y0 = fy * BLOCK_SCALE;
			cube.z0 = fz * BLOCK_SCALE;
			cube.x1 = tx * BLOCK_SCALE;
			cube.y1 = ty * BLOCK_SCALE;
			cube.z1 = tz * BLOCK_SCALE;
			cube.centerZbb = 0.5f * (fz + tz);

			JsonArray origArr = el.getAsJsonArray("origin");
			if (origArr != null && origArr.size() >= 3) {
				cube.ox = origArr.get(0).getAsFloat() * BLOCK_SCALE;
				cube.oy = (origArr.get(1).getAsFloat() - MODEL_ORIGIN_Y) * BLOCK_SCALE;
				cube.oz = (origArr.get(2).getAsFloat() - MODEL_MUZZLE_Z) * BLOCK_SCALE;
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
			cube.emissiveLayer = name.contains("core") || name.contains("bloom") || name.contains("star") || name.contains("pupil") || name.contains("laser") || name.contains("lance");
			cube.lowGfxSkip = name.contains("_w2_") || name.contains("_w3_") || name.contains("cloak") || name.contains("haze") || name.contains("droplet");
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

	private static BoneNode parseOutliner(JsonObject root, Map<String, CubeData> cubeMap) {
		JsonArray outliner = root.getAsJsonArray("outliner");
		if (outliner == null || outliner.size() == 0) {
			return null;
		}
		for (int i = 0; i < outliner.size(); i++) {
			JsonElement el = outliner.get(i);
			if (el.isJsonObject()) {
				BoneNode node = parseBoneNode(el.getAsJsonObject(), cubeMap, BONE_ROOT, 0);
				if (node != null) {
					return node;
				}
			}
		}
		return null;
	}

	private static BoneNode parseBoneNode(JsonObject obj, Map<String, CubeData> cubeMap, int parentRole, int depth) {
		if (obj.has("visibility") && !obj.get("visibility").getAsBoolean()) {
			return null;
		}
		String rawName = obj.has("name") ? obj.get("name").getAsString() : "";
		String name = rawName.toLowerCase(Locale.ROOT);
		if (name.startsWith("caster_ref")) {
			return null;
		}

		int role = parentRole;
		if (depth == 0) {
			role = BONE_ROOT;
		} else if (depth == 1) {
			if (name.startsWith("muzzle")) {
				role = BONE_MUZZLE;
			} else if (name.startsWith("impact")) {
				role = BONE_IMPACT;
			} else if (isContinuousShaftGroup(name)) {
				role = BONE_SHAFT;
			} else {
				role = BONE_TRAVELER;
			}
		}

		BoneNode node = new BoneNode();
		node.name = rawName;
		node.role = role;
		node.travelerChild = (parentRole == BONE_TRAVELER && depth >= 2);
		node.emissiveLayer = name.contains("core") || name.contains("muzzle") || name.contains("impact") || name.contains("helix") || name.contains("streamline") || name.contains("zigzag");
		node.lowGfxSkip = name.contains("wave_2") || name.contains("wave_3") || name.contains("petals_wave_2") || name.contains("petals_wave_3")
				|| name.contains("cloak") || name.contains("haze") || name.contains("dust_sheath");

		JsonArray orig = obj.getAsJsonArray("origin");
		if (orig != null && orig.size() >= 3) {
			node.ox = orig.get(0).getAsFloat() * BLOCK_SCALE;
			node.oy = (orig.get(1).getAsFloat() - MODEL_ORIGIN_Y) * BLOCK_SCALE;
			float rawZ = orig.get(2).getAsFloat() - MODEL_MUZZLE_Z;
			if (role == BONE_IMPACT && depth == 1) {
				// Anchor top-level impact bone locally around 0 after placement at beam end
				rawZ = 0.0f;
			} else if (role == BONE_IMPACT && depth > 1) {
				rawZ = orig.get(2).getAsFloat() - (-198.0f);
			}
			node.oz = rawZ * BLOCK_SCALE;
		}

		JsonArray rot = obj.getAsJsonArray("rotation");
		if (rot != null && rot.size() >= 3) {
			node.rx = rot.get(0).getAsFloat();
			node.ry = rot.get(1).getAsFloat();
			node.rz = rot.get(2).getAsFloat();
			node.hasBindRot = Math.abs(node.rx) > 0.001f || Math.abs(node.ry) > 0.001f || Math.abs(node.rz) > 0.001f;
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
						if (role == BONE_IMPACT) {
							// Shift impact cubes locally so z=-198 sits at z=0 relative to beam end
							float shiftImpactZ = (MODEL_SHAFT_LEN) * BLOCK_SCALE;
							c.z0 += shiftImpactZ;
							c.z1 += shiftImpactZ;
							c.oz += shiftImpactZ;
						}
						localCubes.add(c);
					}
				} else if (ch.isJsonObject()) {
					BoneNode sub = parseBoneNode(ch.getAsJsonObject(), cubeMap, role, depth + 1);
					if (sub != null) {
						childBones.add(sub);
					}
				}
			}
		}

		node.cubes = localCubes.toArray(new CubeData[localCubes.size()]);
		node.children = childBones.toArray(new BoneNode[childBones.size()]);
		return node;
	}

	private static boolean isContinuousShaftGroup(String lowerName) {
		if (lowerName.contains("boulder_belt") || lowerName.contains("crystal_spurs")) {
			return false;
		}
		return lowerName.startsWith("beam_")
				|| lowerName.startsWith("helix")
				|| lowerName.startsWith("streamline")
				|| lowerName.startsWith("zigzag")
				|| lowerName.startsWith("vine");
	}

	private void parseAnimations(JsonObject root) {
		JsonArray anims = root.getAsJsonArray("animations");
		if (anims == null) {
			return;
		}
		for (int i = 0; i < anims.size(); i++) {
			JsonObject animObj = anims.get(i).getAsJsonObject();
			String name = animObj.has("name") ? animObj.get("name").getAsString().toLowerCase(Locale.ROOT) : "";
			AnimationClip clip = new AnimationClip();
			clip.length = Math.max(0.01f, getFloat(animObj, "length", 2.0f));
			String loopMode = animObj.has("loop") ? animObj.get("loop").getAsString() : "once";
			clip.loop = "loop".equalsIgnoreCase(loopMode);

			JsonObject animators = animObj.getAsJsonObject("animators");
			if (animators != null) {
				for (Map.Entry<String, JsonElement> entry : animators.entrySet()) {
					if (!entry.getValue().isJsonObject()) {
						continue;
					}
					JsonObject animNode = entry.getValue().getAsJsonObject();
					String boneName = animNode.has("name") ? animNode.get("name").getAsString() : "";
					if (boneName.startsWith("caster_")) {
						continue;
					}
					JsonArray kfs = animNode.getAsJsonArray("keyframes");
					if (kfs == null || kfs.size() == 0) {
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
						pt.x = parseJsonNumber(dp.get("x"), "scale".equals(channel) ? 1.0f : 0.0f);
						pt.y = parseJsonNumber(dp.get("y"), "scale".equals(channel) ? 1.0f : 0.0f);
						pt.z = parseJsonNumber(dp.get("z"), "scale".equals(channel) ? 1.0f : 0.0f);
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
					track.boneName = boneName;
					track.position = toTrack(posList);
					track.rotation = toTrack(rotList);
					track.scale = toTrack(scaleList);
					clip.tracks.put(boneName, track);
				}
			}

			if (name.contains("channeled_loop") || clip.loop) {
				if (this.channeledLoop == null) {
					this.channeledLoop = clip;
				}
			} else if (name.contains("cast_burst")) {
				this.castBurst = clip;
			}
		}
	}

	private static ChannelTrack toTrack(List<KeyframePoint> list) {
		if (list.isEmpty()) {
			return null;
		}
		// Insertion sort by keyframe time
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
	 * Renders this anime beam along local -Z from origin (0,0,0) up to -lengthBlocks.
	 * Caller sets up world translation & orthonormal orientation matrix so -Z aims along target vector.
	 */
	public void render(
			float lengthBlocks,
			float ageSec,
			float alpha,
			float muzzleStartBlocks,
			float radialScale,
			boolean firstPerson,
			boolean lowGfx,
			int customColorRgb
	) {
		if (rootBone == null || textures == null || textures.length == 0 || alpha <= 0.01f) {
			return;
		}
		float lengthBb = Math.max(4.0f, lengthBlocks * 16.0f);
		float muzBb = Math.min(lengthBb * 0.45f, muzzleStartBlocks * 16.0f);
		float shaftSpanBb = Math.max(2.0f, lengthBb - muzBb);
		float shaftScaleZ = shaftSpanBb / MODEL_SHAFT_LEN;

		float loopTime = (channeledLoop != null) ? (ageSec % channeledLoop.length) : 0.0f;
		// Start slightly into anticipation so single taps still punch through cleanly
		float burstTime = ageSec + 0.14f;
		boolean hasBurst = (castBurst != null && burstTime <= 0.65f);

		float tintR = 1.0f;
		float tintG = 1.0f;
		float tintB = 1.0f;
		boolean hasCustomTint = (customColorRgb != -1);
		if (hasCustomTint) {
			tintR = ((customColorRgb >> 16) & 0xFF) / 255.0f;
			tintG = ((customColorRgb >> 8) & 0xFF) / 255.0f;
			tintB = (customColorRgb & 0xFF) / 255.0f;
		}

		// Pass 1: Crisp saturated anime pass (preserves dark void/boulder contrast in daytime)
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		renderRootSections(lengthBb, muzBb, shaftSpanBb, shaftScaleZ, loopTime, hasBurst, burstTime,
				alpha, radialScale, firstPerson, lowGfx, hasCustomTint, tintR, tintG, tintB, false);

		// Pass 2: Soft additive emissive halo/bloom pass on core & energy layers
		if (!lowGfx) {
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
			renderRootSections(lengthBb, muzBb, shaftSpanBb, shaftScaleZ, loopTime, hasBurst, burstTime,
					alpha * 0.40f, radialScale, firstPerson, false, hasCustomTint, tintR, tintG, tintB, true);
		}
	}

	private void renderRootSections(
			float lengthBb,
			float muzBb,
			float shaftSpanBb,
			float shaftScaleZ,
			float loopTime,
			boolean hasBurst,
			float burstTime,
			float alpha,
			float radialScale,
			boolean firstPerson,
			boolean lowGfx,
			boolean hasCustomTint,
			float tintR,
			float tintG,
			float tintB,
			boolean additivePass
	) {
		Minecraft mc = Minecraft.getMinecraft();
		Tessellator tess = Tessellator.instance;
		int[] boundTex = new int[] { -1 };

		for (int i = 0; i < rootBone.children.length; i++) {
			BoneNode section = rootBone.children[i];
			if (lowGfx && section.lowGfxSkip) {
				continue;
			}
			if (additivePass && !section.emissiveLayer) {
				continue;
			}

			GL11.glPushMatrix();
			switch (section.role) {
				case BONE_MUZZLE:
					GL11.glTranslatef(0.0f, 0.0f, -muzBb * BLOCK_SCALE);
					float muzScale = radialScale * (firstPerson ? 0.36f : 1.0f);
					if (Math.abs(muzScale - 1.0f) > 0.001f) {
						GL11.glScalef(muzScale, muzScale, muzScale);
					}
					renderNodeRecursive(section, 1.0f, loopTime, hasBurst, burstTime,
							alpha, lowGfx, hasCustomTint, tintR, tintG, tintB, additivePass, mc, tess, boundTex);
					break;

				case BONE_IMPACT:
					GL11.glTranslatef(0.0f, 0.0f, -lengthBb * BLOCK_SCALE);
					float impScale = radialScale;
					if (Math.abs(impScale - 1.0f) > 0.001f) {
						GL11.glScalef(impScale, impScale, impScale);
					}
					renderNodeRecursive(section, 1.0f, loopTime, hasBurst, burstTime,
							alpha, lowGfx, hasCustomTint, tintR, tintG, tintB, additivePass, mc, tess, boundTex);
					break;

				case BONE_SHAFT:
					GL11.glTranslatef(0.0f, 0.0f, -muzBb * BLOCK_SCALE);
					float rad = radialScale * (firstPerson ? 0.48f : 1.0f);
					GL11.glScalef(rad, rad, shaftScaleZ);
					renderNodeRecursive(section, 1.0f, loopTime, hasBurst, burstTime,
							alpha, lowGfx, hasCustomTint, tintR, tintG, tintB, additivePass, mc, tess, boundTex);
					break;

				case BONE_TRAVELER:
				default:
					GL11.glTranslatef(0.0f, 0.0f, -muzBb * BLOCK_SCALE);
					float travRad = radialScale * (firstPerson ? 0.50f : 1.0f);
					if (Math.abs(travRad - 1.0f) > 0.001f) {
						GL11.glScalef(travRad, travRad, 1.0f);
					}
					renderTravelerSection(section, shaftScaleZ, shaftSpanBb, loopTime, hasBurst, burstTime,
							alpha, lowGfx, hasCustomTint, tintR, tintG, tintB, additivePass, mc, tess, boundTex);
					break;
			}
			GL11.glPopMatrix();
		}
	}

	private void renderTravelerSection(
			BoneNode section,
			float shaftScaleZ,
			float shaftSpanBb,
			float loopTime,
			boolean hasBurst,
			float burstTime,
			float alpha,
			boolean lowGfx,
			boolean hasCustomTint,
			float tintR,
			float tintG,
			float tintB,
			boolean additivePass,
			Minecraft mc,
			Tessellator tess,
			int[] boundTex
	) {
		evalBonePose(section.name, loopTime, hasBurst, burstTime, scratchPose);
		GL11.glPushMatrix();
		GL11.glTranslatef(section.ox + scratchPose.px * BLOCK_SCALE, section.oy + scratchPose.py * BLOCK_SCALE,
				section.oz * shaftScaleZ + scratchPose.pz * BLOCK_SCALE * shaftScaleZ);
		applyZYX(section.rx + scratchPose.rx, section.ry + scratchPose.ry, section.rz + scratchPose.rz);
		GL11.glScalef(scratchPose.sx, scratchPose.sy, scratchPose.sz);
		GL11.glTranslatef(-section.ox, -section.oy, -section.oz * shaftScaleZ);

		// Render standalone traveler cubes around their scaled Z center (un-crushed)
		for (int c = 0; c < section.cubes.length; c++) {
			CubeData cube = section.cubes[c];
			if (lowGfx && cube.lowGfxSkip) {
				continue;
			}
			if (additivePass && !cube.emissiveLayer) {
				continue;
			}
			float scaledCz = cube.centerZbb * shaftScaleZ;
			if (scaledCz > 3.0f || scaledCz < -shaftSpanBb - 3.0f) {
				continue;
			}
			float dzShift = (scaledCz - cube.centerZbb) * BLOCK_SCALE;
			GL11.glPushMatrix();
			GL11.glTranslatef(0.0f, 0.0f, dzShift);
			drawCube(cube, alpha, hasCustomTint, tintR, tintG, tintB, additivePass, mc, tess, boundTex);
			GL11.glPopMatrix();
		}

		// Render child traveler bones (gates, boulder waves, crystal shards, orbit glyphs)
		for (int i = 0; i < section.children.length; i++) {
			BoneNode child = section.children[i];
			if (lowGfx && child.lowGfxSkip) {
				continue;
			}
			if (additivePass && !child.emissiveLayer) {
				continue;
			}
			EvalPose p = new EvalPose();
			evalBonePose(child.name, loopTime, hasBurst, burstTime, p);
			float worldZbb = (child.oz / BLOCK_SCALE + p.pz) * shaftScaleZ;
			if (worldZbb > 4.0f || worldZbb < -shaftSpanBb - 4.0f) {
				continue;
			}

			GL11.glPushMatrix();
			// Anchor traveler group's world Z proportionally along beam, preserving 1:1 local Z thickness
			float localZScale = Math.min(1.0f, Math.max(0.22f, shaftScaleZ * 1.25f));
			GL11.glTranslatef(child.ox + p.px * BLOCK_SCALE, child.oy + p.py * BLOCK_SCALE, worldZbb * BLOCK_SCALE);
			applyZYX(child.rx + p.rx, child.ry + p.ry, child.rz + p.rz);
			GL11.glScalef(p.sx, p.sy, p.sz * localZScale);
			GL11.glTranslatef(-child.ox, -child.oy, -child.oz);

			drawBoneContentsRecursive(child, loopTime, hasBurst, burstTime, alpha, lowGfx, hasCustomTint, tintR, tintG, tintB, additivePass, mc, tess, boundTex);
			GL11.glPopMatrix();
		}

		GL11.glPopMatrix();
	}

	private void drawBoneContentsRecursive(
			BoneNode node,
			float loopTime,
			boolean hasBurst,
			float burstTime,
			float alpha,
			boolean lowGfx,
			boolean hasCustomTint,
			float tintR,
			float tintG,
			float tintB,
			boolean additivePass,
			Minecraft mc,
			Tessellator tess,
			int[] boundTex
	) {
		for (int c = 0; c < node.cubes.length; c++) {
			CubeData cube = node.cubes[c];
			if (lowGfx && cube.lowGfxSkip) {
				continue;
			}
			if (additivePass && !cube.emissiveLayer) {
				continue;
			}
			drawCube(cube, alpha, hasCustomTint, tintR, tintG, tintB, additivePass, mc, tess, boundTex);
		}
		for (int i = 0; i < node.children.length; i++) {
			BoneNode child = node.children[i];
			if (lowGfx && child.lowGfxSkip) {
				continue;
			}
			if (additivePass && !child.emissiveLayer) {
				continue;
			}
			renderNodeRecursive(child, 1.0f, loopTime, hasBurst, burstTime, alpha, lowGfx, hasCustomTint, tintR, tintG, tintB, additivePass, mc, tess, boundTex);
		}
	}

	private void renderNodeRecursive(
			BoneNode node,
			float zPosMult,
			float loopTime,
			boolean hasBurst,
			float burstTime,
			float alpha,
			boolean lowGfx,
			boolean hasCustomTint,
			float tintR,
			float tintG,
			float tintB,
			boolean additivePass,
			Minecraft mc,
			Tessellator tess,
			int[] boundTex
	) {
		EvalPose p = (node.children.length > 0) ? new EvalPose() : scratchPose;
		evalBonePose(node.name, loopTime, hasBurst, burstTime, p);

		boolean hasPoseTrans = node.hasBindRot
				|| Math.abs(p.px) > 0.0001f || Math.abs(p.py) > 0.0001f || Math.abs(p.pz) > 0.0001f
				|| Math.abs(p.rx) > 0.001f || Math.abs(p.ry) > 0.001f || Math.abs(p.rz) > 0.001f
				|| Math.abs(p.sx - 1.0f) > 0.0005f || Math.abs(p.sy - 1.0f) > 0.0005f || Math.abs(p.sz - 1.0f) > 0.0005f;

		if (hasPoseTrans) {
			GL11.glPushMatrix();
			GL11.glTranslatef(node.ox + p.px * BLOCK_SCALE, node.oy + p.py * BLOCK_SCALE, node.oz + p.pz * BLOCK_SCALE * zPosMult);
			applyZYX(node.rx + p.rx, node.ry + p.ry, node.rz + p.rz);
			if (Math.abs(p.sx - 1.0f) > 0.0005f || Math.abs(p.sy - 1.0f) > 0.0005f || Math.abs(p.sz - 1.0f) > 0.0005f) {
				GL11.glScalef(p.sx, p.sy, p.sz);
			}
			GL11.glTranslatef(-node.ox, -node.oy, -node.oz);
		}

		for (int c = 0; c < node.cubes.length; c++) {
			CubeData cube = node.cubes[c];
			if (lowGfx && cube.lowGfxSkip) {
				continue;
			}
			if (additivePass && !cube.emissiveLayer) {
				continue;
			}
			drawCube(cube, alpha, hasCustomTint, tintR, tintG, tintB, additivePass, mc, tess, boundTex);
		}

		for (int i = 0; i < node.children.length; i++) {
			BoneNode child = node.children[i];
			if (lowGfx && child.lowGfxSkip) {
				continue;
			}
			if (additivePass && !child.emissiveLayer) {
				continue;
			}
			renderNodeRecursive(child, zPosMult, loopTime, hasBurst, burstTime, alpha, lowGfx, hasCustomTint, tintR, tintG, tintB, additivePass, mc, tess, boundTex);
		}

		if (hasPoseTrans) {
			GL11.glPopMatrix();
		}
	}

	private void drawCube(
			CubeData cube,
			float alpha,
			boolean hasCustomTint,
			float tintR,
			float tintG,
			float tintB,
			boolean additivePass,
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

		float r = 1.0f;
		float g = 1.0f;
		float b = 1.0f;
		if (hasCustomTint) {
			float blend = cube.emissiveLayer ? 0.55f : 0.85f;
			r = 1.0f - blend + blend * tintR;
			g = 1.0f - blend + blend * tintG;
			b = 1.0f - blend + blend * tintB;
		}
		float finalAlpha = additivePass && cube.emissiveLayer ? Math.min(1.0f, alpha * 1.15f) : alpha;

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
			tess.setColorRGBA_F(r, g, b, finalAlpha);
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

	private void evalBonePose(String boneName, float loopTime, boolean hasBurst, float burstTime, EvalPose out) {
		out.px = 0.0f; out.py = 0.0f; out.pz = 0.0f;
		out.rx = 0.0f; out.ry = 0.0f; out.rz = 0.0f;
		out.sx = 1.0f; out.sy = 1.0f; out.sz = 1.0f;

		if (channeledLoop != null) {
			BoneTrack lt = channeledLoop.tracks.get(boneName);
			if (lt != null) {
				sampleBoneTrack(lt, channeledLoop.length, true, loopTime, scratchLoopPose);
				out.px += scratchLoopPose.px; out.py += scratchLoopPose.py; out.pz += scratchLoopPose.pz;
				out.rx += scratchLoopPose.rx; out.ry += scratchLoopPose.ry; out.rz += scratchLoopPose.rz;
				out.sx *= scratchLoopPose.sx; out.sy *= scratchLoopPose.sy; out.sz *= scratchLoopPose.sz;
			}
		}

		if (hasBurst && castBurst != null) {
			BoneTrack bt = castBurst.tracks.get(boneName);
			if (bt != null) {
				sampleBoneTrack(bt, castBurst.length, false, burstTime, scratchBurstPose);
				out.px += scratchBurstPose.px; out.py += scratchBurstPose.py; out.pz += scratchBurstPose.pz;
				out.rx += scratchBurstPose.rx; out.ry += scratchBurstPose.ry; out.rz += scratchBurstPose.rz;
				out.sx *= scratchBurstPose.sx; out.sy *= scratchBurstPose.sy; out.sz *= scratchBurstPose.sz;
			}
		}
	}

	private static void sampleBoneTrack(BoneTrack track, float clipLen, boolean looping, float time, EvalPose out) {
		if (track.position != null) {
			sampleChannel(track.position, clipLen, looping, time, out, 0);
		} else {
			out.px = 0.0f; out.py = 0.0f; out.pz = 0.0f;
		}
		if (track.rotation != null) {
			sampleChannel(track.rotation, clipLen, looping, time, out, 1);
		} else {
			out.rx = 0.0f; out.ry = 0.0f; out.rz = 0.0f;
		}
		if (track.scale != null) {
			sampleChannel(track.scale, clipLen, looping, time, out, 2);
		} else {
			out.sx = 1.0f; out.sy = 1.0f; out.sz = 1.0f;
		}
	}

	private static void sampleChannel(ChannelTrack ch, float clipLen, boolean looping, float time, EvalPose out, int kind) {
		KeyframePoint[] pts = ch.points;
		int n = pts.length;
		if (n == 0) {
			return;
		}
		if (n == 1) {
			assignPoseValue(out, kind, pts[0].x, pts[0].y, pts[0].z);
			return;
		}

		if (time <= pts[0].time) {
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
			int i0 = idx - 1;
			if (i0 < 0) {
				i0 = looping && Math.abs(pts[0].time) < 0.01f && Math.abs(pts[n - 1].time - clipLen) < 0.01f ? Math.max(0, n - 2) : 0;
			}
			int i3 = idx + 2;
			if (i3 >= n) {
				i3 = looping && Math.abs(pts[0].time) < 0.01f && Math.abs(pts[n - 1].time - clipLen) < 0.01f ? Math.min(n - 1, 1) : (n - 1);
			}
			KeyframePoint p0 = pts[i0];
			KeyframePoint p3 = pts[i3];
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

	private static float getFloat(JsonObject obj, String key, float def) {
		if (!obj.has(key) || obj.get(key).isJsonNull()) {
			return def;
		}
		return obj.get(key).getAsFloat();
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
