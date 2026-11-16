package am2.compass;

import am2.worldgen.BiomeWitchwoodForest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.MapGenBase;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenStructureData;
import net.minecraft.world.gen.structure.MapGenStructureIO;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Discovers all Vanilla, Ars Magica 2, and modded worldgen structures without forcing chunk generation,
 * and computes nearest structure coordinates for the Astral Structure Compass.
 */
public final class AstralStructureLocator {

	public static final int REQUIRED_MAGIC_LEVEL = 50;
	public static final float MANA_COST = 20000.0F;
	public static final int COOLDOWN_TICKS = 20;

	public static final String ID_VILLAGE = "Village";
	public static final String ID_STRONGHOLD = "Stronghold";
	public static final String ID_DESERT_PYRAMID = "Temple:DesertPyramid";
	public static final String ID_JUNGLE_TEMPLE = "Temple:JungleTemple";
	public static final String ID_WITCH_HUT = "Temple:WitchHut";
	public static final String ID_MINESHAFT = "Mineshaft";
	public static final String ID_FORTRESS = "Fortress";
	public static final String ID_WITCHWOOD_FOREST = "am2:witchwood_forest";

	private static final int MAX_CHUNK_SPIRAL_RADIUS = 220; // ~3,520 blocks around caster
	private static final int BIOME_SAMPLE_STEP_BLOCKS = 32;
	private static final int MAX_BIOME_RADIUS_BLOCKS = 4096;

	private AstralStructureLocator() {
	}

	public static final class SearchResult {
		public final String targetId;
		public final String displayName;
		public final int dimension;
		public final int x;
		public final int y;
		public final int z;
		public final boolean hasY;

		public SearchResult(String targetId, String displayName, int dimension, int x, int y, int z, boolean hasY) {
			this.targetId = targetId;
			this.displayName = displayName;
			this.dimension = dimension;
			this.x = x;
			this.y = y;
			this.z = z;
			this.hasY = hasY;
		}
	}

	public static boolean hasRequiredLevel(int magicLevel) {
		return magicLevel >= REQUIRED_MAGIC_LEVEL;
	}

	public static boolean hasEnoughMana(float currentMana, float bonusMana) {
		return (currentMana + bonusMana) >= MANA_COST;
	}

	public static List<StructureTargetInfo> getBuiltinCatalog() {
		List<StructureTargetInfo> list = new ArrayList<StructureTargetInfo>();
		list.add(new StructureTargetInfo(ID_STRONGHOLD, "Stronghold", StructureTargetInfo.CAT_VANILLA, 0, true, 1));
		list.add(new StructureTargetInfo(ID_VILLAGE, "Village", StructureTargetInfo.CAT_VANILLA, 0, true, 3));
		list.add(new StructureTargetInfo(ID_DESERT_PYRAMID, "Desert Pyramid", StructureTargetInfo.CAT_VANILLA, 0, true, 5));
		list.add(new StructureTargetInfo(ID_JUNGLE_TEMPLE, "Jungle Temple", StructureTargetInfo.CAT_VANILLA, 0, true, 6));
		list.add(new StructureTargetInfo(ID_WITCH_HUT, "Witch Hut", StructureTargetInfo.CAT_VANILLA, 0, true, 7));
		list.add(new StructureTargetInfo(ID_MINESHAFT, "Mineshaft", StructureTargetInfo.CAT_VANILLA, 0, true, 9));
		list.add(new StructureTargetInfo(ID_FORTRESS, "Nether Fortress", StructureTargetInfo.CAT_VANILLA, -1, true, 11));
		list.add(new StructureTargetInfo(ID_WITCHWOOD_FOREST, "Witchwood Forest", StructureTargetInfo.CAT_AM2, 0, false, 13));
		return list;
	}

	/**
	 * Discovers all built-in + MapGenStructureIO + IChunkProvider MapGenStructure entries on the server world.
	 */
	public static List<StructureTargetInfo> discoverCatalog(World world) {
		LinkedHashMap<String, StructureTargetInfo> map = new LinkedHashMap<String, StructureTargetInfo>();
		for (StructureTargetInfo info : getBuiltinCatalog()) {
			map.put(info.id, info);
		}

		if (world != null) {
			for (MapGenStructure gen : collectMapGenStructures(world)) {
				try {
					String name = gen.func_143025_a();
					if (name != null && !name.trim().isEmpty() && !isBuiltinGroupedName(name) && !map.containsKey(name)) {
						String cat = name.toLowerCase().contains("am2") ? StructureTargetInfo.CAT_AM2 : StructureTargetInfo.CAT_MODDED;
						map.put(name, new StructureTargetInfo(name, name, cat, world.provider.dimensionId, false, Math.abs(name.hashCode()) & 15));
					}
				} catch (Throwable ignored) {
				}
			}
		}

		for (String registered : collectRegisteredStructureIONames()) {
			if (registered != null && !registered.trim().isEmpty() && !isBuiltinGroupedName(registered) && !map.containsKey(registered)) {
				String cat = registered.toLowerCase().contains("am2") ? StructureTargetInfo.CAT_AM2 : StructureTargetInfo.CAT_MODDED;
				map.put(registered, new StructureTargetInfo(registered, registered, cat, 0, false, Math.abs(registered.hashCode()) & 15));
			}
		}

		return new ArrayList<StructureTargetInfo>(map.values());
	}

	public static List<StructureTargetInfo> filterCatalog(List<StructureTargetInfo> catalog, String category, String query) {
		String normQuery = StructureTargetInfo.normalizeQuery(query);
		String compactQuery = StructureTargetInfo.compactQuery(normQuery);
		List<StructureTargetInfo> out = new ArrayList<StructureTargetInfo>();
		for (StructureTargetInfo info : catalog) {
			if (category != null && !StructureTargetInfo.CAT_ALL.equals(category) && !category.equals(info.category)) {
				continue;
			}
			if (info.matchesQuery(normQuery, compactQuery)) {
				out.add(info);
			}
		}
		return out;
	}

	private static boolean isBuiltinGroupedName(String name) {
		return ID_VILLAGE.equals(name)
				|| ID_STRONGHOLD.equals(name)
				|| "Temple".equals(name)
				|| ID_MINESHAFT.equals(name)
				|| ID_FORTRESS.equals(name);
	}

	public static SearchResult locateNearest(World world, EntityPlayer player, String targetId) {
		if (world == null || player == null || targetId == null || targetId.isEmpty()) {
			return null;
		}
		int px = (int) Math.floor(player.posX);
		int py = (int) Math.floor(player.posY);
		int pz = (int) Math.floor(player.posZ);

		if (ID_WITCHWOOD_FOREST.equals(targetId)) {
			ChunkCoordinates pos = findNearestBiome(world, BiomeWitchwoodForest.instance.biomeID, px, pz);
			if (pos == null) return null;
			return new SearchResult(targetId, resolveDisplayName(world, targetId), world.provider.dimensionId, pos.posX, pos.posY, pos.posZ, false);
		}

		if (ID_STRONGHOLD.equals(targetId)) {
			ChunkPosition cp = world.findClosestStructure("Stronghold", px, py, pz);
			if (cp != null) {
				return new SearchResult(targetId, resolveDisplayName(world, targetId), world.provider.dimensionId, cp.chunkPosX, cp.chunkPosY, cp.chunkPosZ, true);
			}
		}

		String baseStructureName = targetId;
		int requiredBiomeId = -1;
		boolean requirePyramidOrTemple = false;
		if (ID_DESERT_PYRAMID.equals(targetId)) {
			baseStructureName = "Temple";
			requiredBiomeId = BiomeGenBase.desert.biomeID;
			requirePyramidOrTemple = true;
		} else if (ID_JUNGLE_TEMPLE.equals(targetId)) {
			baseStructureName = "Temple";
			requiredBiomeId = BiomeGenBase.jungle.biomeID;
			requirePyramidOrTemple = true;
		} else if (ID_WITCH_HUT.equals(targetId)) {
			baseStructureName = "Temple";
			requiredBiomeId = BiomeGenBase.swampland.biomeID;
			requirePyramidOrTemple = true;
		}

		// 1. Check already-persisted MapGenStructureData first without loading or generating chunks
		ChunkPosition savedPos = findNearestInSavedData(world, baseStructureName, px, pz, requirePyramidOrTemple ? requiredBiomeId : -1);
		if (savedPos != null) {
			return new SearchResult(targetId, resolveDisplayName(world, targetId), world.provider.dimensionId, savedPos.chunkPosX, savedPos.chunkPosY, savedPos.chunkPosZ, false);
		}

		// 2. Query any matching MapGenStructure instance via deterministic chunk-coord seed probing (zero chunk gen)
		for (MapGenStructure gen : collectMapGenStructures(world)) {
			try {
				String genName = gen.func_143025_a();
				if (!baseStructureName.equals(genName)) continue;

				ChunkPosition predicted = spiralQueryMapGen(gen, world, px, pz, requirePyramidOrTemple ? requiredBiomeId : -1);
				if (predicted != null) {
					return new SearchResult(targetId, resolveDisplayName(world, targetId), world.provider.dimensionId, predicted.chunkPosX, predicted.chunkPosY, predicted.chunkPosZ, false);
				}

				ChunkPosition fallback = gen.func_151545_a(world, px, py, pz);
				if (fallback != null && (!requirePyramidOrTemple || matchesTempleBiome(world, fallback.chunkPosX, fallback.chunkPosZ, requiredBiomeId))) {
					return new SearchResult(targetId, resolveDisplayName(world, targetId), world.provider.dimensionId, fallback.chunkPosX, fallback.chunkPosY, fallback.chunkPosZ, true);
				}
			} catch (Throwable ignored) {
			}
		}

		return null;
	}

	private static String resolveDisplayName(World world, String targetId) {
		for (StructureTargetInfo info : discoverCatalog(world)) {
			if (info.id.equals(targetId)) {
				return info.getDisplayName();
			}
		}
		return StructureTargetInfo.prettify(targetId);
	}

	private static boolean matchesTempleBiome(World world, int blockX, int blockZ, int requiredBiomeId) {
		BiomeGenBase biome = world.getWorldChunkManager().getBiomeGenAt(blockX, blockZ);
		if (biome == null) return false;
		if (requiredBiomeId == BiomeGenBase.desert.biomeID) {
			return biome.biomeID == BiomeGenBase.desert.biomeID || biome.biomeID == BiomeGenBase.desertHills.biomeID;
		}
		if (requiredBiomeId == BiomeGenBase.jungle.biomeID) {
			return biome.biomeID == BiomeGenBase.jungle.biomeID || biome.biomeID == BiomeGenBase.jungleHills.biomeID;
		}
		return biome.biomeID == requiredBiomeId;
	}

	private static ChunkCoordinates findNearestBiome(World world, int targetBiomeId, int startX, int startZ) {
		int maxSteps = MAX_BIOME_RADIUS_BLOCKS / BIOME_SAMPLE_STEP_BLOCKS;
		int x = 0, z = 0, dx = 0, dz = -1;
		int maxIter = (2 * maxSteps + 1) * (2 * maxSteps + 1);
		for (int i = 0; i < maxIter; i++) {
			if (Math.abs(x) <= maxSteps && Math.abs(z) <= maxSteps) {
				int bx = startX + x * BIOME_SAMPLE_STEP_BLOCKS;
				int bz = startZ + z * BIOME_SAMPLE_STEP_BLOCKS;
				BiomeGenBase biome = world.getWorldChunkManager().getBiomeGenAt(bx, bz);
				if (biome != null && biome.biomeID == targetBiomeId) {
					return new ChunkCoordinates(bx, 64, bz);
				}
			}
			if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
				int tmp = dx;
				dx = -dz;
				dz = tmp;
			}
			x += dx;
			z += dz;
		}
		return null;
	}

	private static ChunkPosition findNearestInSavedData(World world, String structureName, int originX, int originZ, int templeBiomeId) {
		if (world.perWorldStorage == null) return null;
		WorldSavedData raw = world.perWorldStorage.loadData(MapGenStructureData.class, structureName);
		if (!(raw instanceof MapGenStructureData)) return null;

		NBTTagCompound features = ((MapGenStructureData) raw).func_143041_a();
		if (features == null) return null;

		ChunkPosition best = null;
		double bestDistSq = Double.MAX_VALUE;
		@SuppressWarnings("unchecked")
		Set<String> keys = features.func_150296_c();
		for (String key : keys) {
			NBTBase tag = features.getTag(key);
			if (!(tag instanceof NBTTagCompound)) continue;
			NBTTagCompound entry = (NBTTagCompound) tag;
			if (!entry.hasKey("ChunkX") || !entry.hasKey("ChunkZ")) continue;
			int cx = (entry.getInteger("ChunkX") << 4) + 8;
			int cz = (entry.getInteger("ChunkZ") << 4) + 8;
			if (templeBiomeId >= 0 && !matchesTempleBiome(world, cx, cz, templeBiomeId)) continue;
			double dx = cx - originX;
			double dz = cz - originZ;
			double dSq = dx * dx + dz * dz;
			if (dSq < bestDistSq) {
				bestDistSq = dSq;
				best = new ChunkPosition(cx, 64, cz);
			}
		}
		return best;
	}

	private static ChunkPosition spiralQueryMapGen(MapGenStructure gen, World world, int originX, int originZ, int templeBiomeId) {
		Method canSpawn = findCanSpawnMethod(gen.getClass());
		if (canSpawn == null) return null;

		Field worldField = findTypedField(gen.getClass(), MapGenBase.class, World.class);
		Field randField = findTypedField(gen.getClass(), MapGenBase.class, Random.class);
		if (worldField == null || randField == null) return null;

		try {
			worldField.setAccessible(true);
			randField.setAccessible(true);
			World prevWorld = (World) worldField.get(gen);
			Random rand = (Random) randField.get(gen);
			if (rand == null) {
				rand = new Random();
				randField.set(gen, rand);
			}
			worldField.set(gen, world);
			try {
				rand.setSeed(world.getSeed());
				long seedL = rand.nextLong();
				long seedI1 = rand.nextLong();

				int startChunkX = originX >> 4;
				int startChunkZ = originZ >> 4;
				int rx = 0, rz = 0, dx = 0, dz = -1;
				int maxIter = (2 * MAX_CHUNK_SPIRAL_RADIUS + 1) * (2 * MAX_CHUNK_SPIRAL_RADIUS + 1);
				for (int i = 0; i < maxIter; i++) {
					if (Math.abs(rx) <= MAX_CHUNK_SPIRAL_RADIUS && Math.abs(rz) <= MAX_CHUNK_SPIRAL_RADIUS) {
						int cx = startChunkX + rx;
						int cz = startChunkZ + rz;
						long chunkSeed = ((long) cx * seedL) ^ ((long) cz * seedI1) ^ world.getSeed();
						rand.setSeed(chunkSeed);
						if ((Boolean) canSpawn.invoke(gen, cx, cz)) {
							int bx = (cx << 4) + 8;
							int bz = (cz << 4) + 8;
							if (templeBiomeId < 0 || matchesTempleBiome(world, bx, bz, templeBiomeId)) {
								return new ChunkPosition(bx, 64, bz);
							}
						}
					}
					if (rx == rz || (rx < 0 && rx == -rz) || (rx > 0 && rx == 1 - rz)) {
						int tmp = dx;
						dx = -dz;
						dz = tmp;
					}
					rx += dx;
					rz += dz;
				}
			} finally {
				worldField.set(gen, prevWorld);
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	private static List<MapGenStructure> collectMapGenStructures(World world) {
		List<MapGenStructure> found = new ArrayList<MapGenStructure>();
		Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
		Queue<Object> queue = new ArrayDeque<Object>();

		IChunkProvider provider = world.getChunkProvider();
		if (provider instanceof ChunkProviderServer && ((ChunkProviderServer) provider).currentChunkProvider != null) {
			queue.add(((ChunkProviderServer) provider).currentChunkProvider);
		}
		if (provider != null) {
			queue.add(provider);
		}

		int hops = 0;
		while (!queue.isEmpty() && hops++ < 64) {
			Object obj = queue.poll();
			if (obj == null || !visited.add(obj)) continue;

			Class<?> cls = obj.getClass();
			while (cls != null && cls != Object.class) {
				for (Field f : cls.getDeclaredFields()) {
					Class<?> type = f.getType();
					try {
						if (MapGenStructure.class.isAssignableFrom(type)) {
							f.setAccessible(true);
							Object val = f.get(obj);
							if (val instanceof MapGenStructure && !found.contains(val)) {
								found.add((MapGenStructure) val);
							}
						} else if (IChunkProvider.class.isAssignableFrom(type)) {
							f.setAccessible(true);
							Object val = f.get(obj);
							if (val != null && !visited.contains(val)) {
								queue.add(val);
							}
						}
					} catch (Throwable ignored) {
					}
				}
				cls = cls.getSuperclass();
			}
		}
		return found;
	}

	@SuppressWarnings("unchecked")
	private static Set<String> collectRegisteredStructureIONames() {
		Set<String> names = new LinkedHashSet<String>();
		try {
			for (Field f : MapGenStructureIO.class.getDeclaredFields()) {
				if (Map.class.isAssignableFrom(f.getType())) {
					f.setAccessible(true);
					Object val = f.get(null);
					if (val instanceof Map<?, ?>) {
						for (Object key : ((Map<?, ?>) val).keySet()) {
							if (key instanceof String) {
								names.add((String) key);
							}
						}
						if (!names.isEmpty()) break;
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return names;
	}

	private static Method findCanSpawnMethod(Class<?> cls) {
		Class<?> cur = cls;
		while (cur != null && cur != Object.class) {
			for (Method m : cur.getDeclaredMethods()) {
				Class<?>[] params = m.getParameterTypes();
				if (params.length == 2 && params[0] == int.class && params[1] == int.class && m.getReturnType() == boolean.class) {
					if ("canSpawnStructureAtCoords".equals(m.getName()) || "func_75047_a".equals(m.getName())) {
						m.setAccessible(true);
						return m;
					}
				}
			}
			cur = cur.getSuperclass();
		}
		return null;
	}

	private static Field findTypedField(Class<?> cls, Class<?> stopAt, Class<?> targetType) {
		Class<?> cur = cls;
		while (cur != null && cur != Object.class) {
			for (Field f : cur.getDeclaredFields()) {
				if (f.getType() == targetType) {
					return f;
				}
			}
			if (cur == stopAt) break;
			cur = cur.getSuperclass();
		}
		return null;
	}
}
