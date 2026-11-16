package am2.compass;

import am2.worldgen.BiomeWitchwoodForest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.*;
import net.minecraft.world.*;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.structure.*;
import java.lang.reflect.*;
import java.util.*;

/** Read-only, incrementally budgeted searches. Never calls a foreign generator. */
public final class AstralStructureLocator {
    public static final int REQUIRED_MAGIC_LEVEL = 50;
    public static final float MANA_COST = 20000F;
    public static final int COOLDOWN_TICKS = 20;
    public static final String ID_VILLAGE="Village", ID_STRONGHOLD="Stronghold",
        ID_DESERT_PYRAMID="Temple:DesertPyramid", ID_JUNGLE_TEMPLE="Temple:JungleTemple",
        ID_WITCH_HUT="Temple:WitchHut", ID_MINESHAFT="Mineshaft", ID_FORTRESS="Fortress",
        ID_WITCHWOOD_FOREST="am2:witchwood_forest";
    public static final int MAX_CATALOG = 256;
    private AstralStructureLocator() {}

    public static final class SearchResult {
        public final String targetId, displayName;
        public final int dimension,x,y,z;
        public final boolean hasY;
        public SearchResult(String id,String name,int dim,int x,int y,int z,boolean hasY) {
            targetId=id; displayName=name; dimension=dim; this.x=x;this.y=y;this.z=z;this.hasY=hasY;
        }
    }
    public static boolean hasRequiredLevel(int level) { return level>=REQUIRED_MAGIC_LEVEL; }
    public static boolean hasEnoughMana(float mana,float bonus) {
        return !Float.isNaN(mana) && !Float.isInfinite(mana) && !Float.isNaN(bonus)
            && !Float.isInfinite(bonus) && mana+bonus>=MANA_COST;
    }
    public static List<StructureTargetInfo> getBuiltinCatalog() {
        List<StructureTargetInfo> list=new ArrayList<StructureTargetInfo>();
        String[] ids={ID_STRONGHOLD,ID_VILLAGE,ID_DESERT_PYRAMID,ID_JUNGLE_TEMPLE,ID_WITCH_HUT,ID_MINESHAFT,ID_FORTRESS,ID_WITCHWOOD_FOREST};
        String[] names={"Stronghold","Village","Desert Pyramid","Jungle Temple","Witch Hut","Mineshaft","Nether Fortress","Witchwood Forest"};
        for(int i=0;i<ids.length;i++) list.add(new StructureTargetInfo(ids[i],names[i],i==7?StructureTargetInfo.CAT_AM2:StructureTargetInfo.CAT_VANILLA,i==6?-1:0,true,i*2+1));
        return list;
    }
    public static List<StructureTargetInfo> discoverCatalog(World world) {
        LinkedHashMap<String,StructureTargetInfo> entries=new LinkedHashMap<String,StructureTargetInfo>();
        for(StructureTargetInfo info:getBuiltinCatalog()) entries.put(info.id,info);
        if(world!=null && !world.isRemote) {
            for(MapGenStructure gen:collectMapGenStructures(world)) {
                String name;
                try {name=gen.func_143025_a();}catch(RuntimeException e){continue;}catch(LinkageError e){continue;}
                if(validId(name) && !"Temple".equals(name) && !entries.containsKey(name))
                    entries.put(name,new StructureTargetInfo(name,name,StructureTargetInfo.CAT_MODDED,world.provider.dimensionId,true,name.hashCode()));
            }
            for(String name:loadedStructureData(world).keySet()) {
                if(validId(name) && !"Temple".equals(name) && !entries.containsKey(name))
                    entries.put(name,new StructureTargetInfo(name,name,StructureTargetInfo.CAT_MODDED,world.provider.dimensionId,true,name.hashCode()));
            }
            AstralModStructures.addCatalog(world,entries);
            for(String id:AstralLandmarks.get(world).types()) if(!entries.containsKey(id))
                entries.put(id,new StructureTargetInfo(id,StructureTargetInfo.prettify(id),StructureTargetInfo.CAT_MODDED,world.provider.dimensionId,true,id.hashCode()));
        }
        List<StructureTargetInfo> result=new ArrayList<StructureTargetInfo>();
        for(StructureTargetInfo info:entries.values()) { if(result.size()==MAX_CATALOG) break; result.add(info); }
        return result;
    }
    public static boolean validId(String id) {
        return id!=null && id.length()>0 && id.length()<=128 && id.matches("[A-Za-z0-9_:. /-]+");
    }
    public static List<StructureTargetInfo> filterCatalog(List<StructureTargetInfo> catalog,String category,String query) {
        String q=StructureTargetInfo.normalizeQuery(query), compact=StructureTargetInfo.compactQuery(q);
        List<StructureTargetInfo> result=new ArrayList<StructureTargetInfo>();
        for(StructureTargetInfo info:catalog) if((category==null || StructureTargetInfo.CAT_ALL.equals(category) || category.equals(info.category)) && info.matchesQuery(q,compact)) result.add(info);
        return result;
    }
    static Map<String,MapGenStructureData> loadedStructureData(World world) {
        Map<String,MapGenStructureData> result=new LinkedHashMap<String,MapGenStructureData>();
        if(world.perWorldStorage==null) return result;
        try {
            for(Field f:net.minecraft.world.storage.MapStorage.class.getDeclaredFields()) if(Map.class.isAssignableFrom(f.getType()) && !Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                Object value=f.get(world.perWorldStorage);
                if(value instanceof Map) for(Object raw:((Map<?,?>)value).entrySet()) {
                    Map.Entry<?,?> entry=(Map.Entry<?,?>)raw;
                    if(entry.getKey() instanceof String && entry.getValue() instanceof MapGenStructureData)
                        result.put((String)entry.getKey(),(MapGenStructureData)entry.getValue());
                }
            }
        } catch(ReflectiveOperationException e) { am2.LogHelper.warn("Astral Compass: structure storage unavailable: %s",e.toString()); }
        return result;
    }
    static boolean matchesTempleBiome(World world,int x,int z,String id) {
        BiomeGenBase b=world.getWorldChunkManager().getBiomeGenAt(x,z);
        if(b==null) return false;
        if(ID_DESERT_PYRAMID.equals(id)) return b==BiomeGenBase.desert || b==BiomeGenBase.desertHills;
        if(ID_JUNGLE_TEMPLE.equals(id)) return b==BiomeGenBase.jungle || b==BiomeGenBase.jungleHills;
        return !ID_WITCH_HUT.equals(id) || b==BiomeGenBase.swampland;
    }
    public static final class Search {
        public final World world;
        public final StructureTargetInfo target;
        private final int x,z;
        private Iterator<String> savedKeys;
        private NBTTagCompound saved;
        private Iterator<AstralLandmarks.Point> points;
        private VanillaStructureProbe probe;
        private Iterator<AstralLandmarks.Point> foreign;
        private int stage,rx,rz,dx=0,dz=-1,iterations;
        private double bestDistance=Double.POSITIVE_INFINITY;
        private SearchResult best;
        public boolean done;
        public Search(World world,EntityPlayer player,StructureTargetInfo target) {
            this.world=world;this.target=target;x=(int)Math.floor(player.posX);z=(int)Math.floor(player.posZ);
        }
        public SearchResult result() { return best; }
        private void consider(int bx,int by,int bz,boolean hasY) {
            double dist=squaredDistance(x,z,bx,bz);
            if(dist<bestDistance) { bestDistance=dist; best=new SearchResult(target.id,target.rawName,world.provider.dimensionId,bx,by,bz,hasY); }
        }
        public static double squaredDistance(int x,int z,int bx,int bz) {
            double a=(double)bx-x,b=(double)bz-z;return a*a+b*b;
        }
        /** At most budget samples, with a cooperative wall-clock deadline between samples. */
        public void step(int budget,long deadline) {
            while(!done && budget-->0 && System.nanoTime()<deadline) {
                if(stage==0) {
                    String base=target.id.startsWith("Temple:")?"Temple":target.id;
                    WorldSavedData data=world.perWorldStorage==null || base.indexOf(':')>=0 || base.indexOf('/')>=0 || base.contains("..")?null:world.perWorldStorage.loadData(MapGenStructureData.class,base);
                    if(data instanceof MapGenStructureData) {
                        saved=((MapGenStructureData)data).func_143041_a();
                        savedKeys=new ArrayList<String>(saved.func_150296_c()).iterator();
                    }
                    points=AstralLandmarks.get(world).points(target.id).iterator();
                    stage=1;
                } else if(stage==1) {
                    if(savedKeys!=null && savedKeys.hasNext()) {
                        NBTBase raw=saved.getTag(savedKeys.next());
                        if(!(raw instanceof NBTTagCompound)) continue;
                        NBTTagCompound entry=(NBTTagCompound)raw;
                        if(!entry.hasKey("ChunkX",99) || !entry.hasKey("ChunkZ",99) || entry.hasKey("Valid") && !entry.getBoolean("Valid")) continue;
                        int cx=(entry.getInteger("ChunkX")<<4)+8,cz=(entry.getInteger("ChunkZ")<<4)+8;
                        if(target.id.startsWith("Temple:") && !matchesTempleBiome(world,cx,cz,target.id)) continue;
                        int[] bb=entry.getIntArray("BB");
                        if(bb.length==6) consider((int)(((long)bb[0]+bb[3])/2),bb[1],(int)(((long)bb[2]+bb[5])/2),true);
                        else consider(cx,64,cz,false);
                    } else stage=2;
                } else if(stage==2) {
                    if(points.hasNext()) { AstralLandmarks.Point p=points.next();consider(p.x,p.y,p.z,true); }
                    else { stage=3; }
                } else if(stage==3) {
                    if(foreign==null)foreign=AstralModStructures.known(world,target,x,z);
                    if(foreign.hasNext()){AstralLandmarks.Point p=foreign.next();if(p!=null)consider(p.x,p.y,p.z,true);continue;}
                    SearchResult mod=AstralModStructures.locateKnown(world,target,x,z);
                    if(mod!=null) consider(mod.x,mod.y,mod.z,mod.hasY);
                    if(world.getWorldInfo().isMapFeaturesEnabled() && ID_STRONGHOLD.equals(target.id)) try {
                        for(ChunkPosition p:VanillaStructureProbe.strongholds(world)) consider(p.chunkPosX,p.chunkPosY,p.chunkPosZ,false);
                    } catch(ReflectiveOperationException e) { am2.LogHelper.warn("Astral Compass: stronghold prediction unavailable: %s",e.toString()); }
                    probe=world.getWorldInfo().isMapFeaturesEnabled()?VanillaStructureProbe.create(world,target.id):null;
                    if(probe==null && !ID_WITCHWOOD_FOREST.equals(target.id)) done=true;
                    stage=4;
                } else {
                    boolean biome=ID_WITCHWOOD_FOREST.equals(target.id);
                    int step=biome?32:16,limit=biome?128:220;
                    int bx=biome?x+rx*step:((x>>4)+rx)*16+8;
                    int bz=biome?z+rz*step:((z>>4)+rz)*16+8;
                    if(biome) {
                        if(BiomeWitchwoodForest.instance!=null && world.getWorldChunkManager().getBiomeGenAt(bx,bz)==BiomeWitchwoodForest.instance) consider(bx,64,bz,false);
                    } else if(probe.test(bx>>4,bz>>4) && matchesTempleBiome(world,bx,bz,target.id)) consider(bx,64,bz,false);
                    if(rx==rz || rx<0 && rx==-rz || rx>0 && rx==1-rz) { int t=dx;dx=-dz;dz=t; }
                    rx+=dx;rz+=dz;
                    int ring=Math.max(Math.abs(rx),Math.abs(rz));
                    if(++iterations>=(2*limit+1)*(2*limit+1) || best!=null && ring*step>Math.sqrt(bestDistance)+step*2) done=true;
                }
            }
        }
    }
	static List<MapGenStructure> collectMapGenStructures(World world) {
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
					if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
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
						} else if (Iterable.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
                                f.setAccessible(true);Object val=f.get(obj);
                                Iterable<?> values=val instanceof Map?((Map<?,?>)val).values():val instanceof Iterable?(Iterable<?>)val:null;
                                if(values!=null){int count=0;for(Object entry:values){if(++count>256)break;if(entry instanceof MapGenStructure && !found.contains(entry))found.add((MapGenStructure)entry);}}
						}
					} catch (Throwable ignored) {
					}
				}
				cls = cls.getSuperclass();
			}
		}
		return found;
	}

}
