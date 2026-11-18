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
    public static final int DEFAULT_RADIUS=5000;
    public static boolean validRadius(int radius) { return radius==2000 || radius==5000 || radius==10000; }
    private AstralStructureLocator() {}

    public static final class SearchResult {
        public final String targetId, displayName;
        public final int dimension,x,y,z;
        public final boolean hasY;
        public final String source;
        public SearchResult(String id,String name,int dim,int x,int y,int z,boolean hasY) {
            this(id,name,dim,x,y,z,hasY,"unknown");
        }
        public SearchResult(String id,String name,int dim,int x,int y,int z,boolean hasY,String source) {
            targetId=id;displayName=name;dimension=dim;this.x=x;this.y=y;this.z=z;this.hasY=hasY;this.source=source;
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
        if(!id.startsWith("Temple:"))return true;
        BiomeGenBase b=world.getWorldChunkManager().getBiomeGenAt(x,z);
        if(b==null) return false;
        if(ID_DESERT_PYRAMID.equals(id)) return b==BiomeGenBase.desert || b==BiomeGenBase.desertHills;
        if(ID_JUNGLE_TEMPLE.equals(id)) return b==BiomeGenBase.jungle || b==BiomeGenBase.jungleHills;
        return !ID_WITCH_HUT.equals(id) || b==BiomeGenBase.swampland;
    }
    public static final class Search {
        public final World world;
        public final StructureTargetInfo target;
        public final int radius;
        private final int x,z;
        private final am2.items.ItemAstralCompass.TargetData excluded;
        private final Set<Long> excludedStarts=new HashSet<Long>();
        private final List<StructureBoundingBox> excludedBounds=new ArrayList<StructureBoundingBox>();
        private Iterator<String> savedKeys;
        private List<MapGenStructure> generators;
        private NBTTagCompound saved;
        private Iterator<StructureBoundingBox> memory;
        private Iterator<AstralLandmarks.Point> points,foreign;
        private VanillaStructureProbe probe;
        private int stage,rx,rz,dx=0,dz=-1,iterations;
        public int samples;
        private double bestDistance=Double.POSITIVE_INFINITY;
        private SearchResult best;
        public boolean done;
        public Search(World world,EntityPlayer player,StructureTargetInfo target) {
            this(world,player,target,DEFAULT_RADIUS,null);
        }
        public Search(World world,EntityPlayer player,StructureTargetInfo target,int radius,am2.items.ItemAstralCompass.TargetData excluded) {
            if(!validRadius(radius))throw new IllegalArgumentException("Unsupported compass radius");
            this.world=world;this.target=target;this.radius=radius;this.excluded=excluded;
            x=(int)Math.floor(player.posX);z=(int)Math.floor(player.posZ);
        }
        public SearchResult result(){return best;}
        public int phase(){return stage;}
        private static long chunkKey(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
        private boolean contains(StructureBoundingBox box,int bx,int bz){return bx>=box.minX&&bx<=box.maxX&&bz>=box.minZ&&bz<=box.maxZ;}
        private boolean includesExcluded(StructureBoundingBox box){return excluded!=null&&contains(box,excluded.x,excluded.z)&&(!excluded.hasY||excluded.y>=box.minY&&excluded.y<=box.maxY);}
        private void consider(int bx,int by,int bz,boolean hasY,String source) {
            if(Math.abs((long)bx)>30000000 || Math.abs((long)bz)>30000000 || hasY&&!AstralLandmarks.validHeight(by))return;
            double dist=squaredDistance(x,z,bx,bz);if(dist>(double)radius*radius)return;
            if(excluded!=null) {
                if(bx==excluded.x&&bz==excluded.z&&(!hasY||!excluded.hasY||by==excluded.y))return;
                if("predicted".equals(source)&&excludedStarts.contains(chunkKey(bx>>4,bz>>4)))return;
                for(StructureBoundingBox box:excludedBounds)if(contains(box,bx,bz)&&(!hasY||by>=box.minY&&by<=box.maxY))return;
            }
            if(dist<bestDistance) {bestDistance=dist;best=new SearchResult(target.id,target.rawName,world.provider.dimensionId,bx,by,bz,hasY,source);}
        }
        private void registeredBox(StructureBoundingBox box,Integer startX,Integer startZ) {
            if(box.minX>box.maxX||box.minY>box.maxY||box.minZ>box.maxZ)return;
            int bx=(int)(((long)box.minX+box.maxX)/2),bz=(int)(((long)box.minZ+box.maxZ)/2);
            if(target.id.startsWith("Temple:")&&!matchesTempleBiome(world,bx,bz,target.id))return;
            if(includesExcluded(box)) {
                if(excludedBounds.size()<256)excludedBounds.add(box);
                if(startX!=null)excludedStarts.add(chunkKey(startX,startZ));return;
            }
            consider(bx,box.minY,bz,true,"recorded");
            AstralLandmarks.record(world,bx,box.minY,bz,target.id);
        }
        public static double squaredDistance(int x,int z,int bx,int bz){double a=(double)bx-x,b=(double)bz-z;return a*a+b*b;}
        /** Cooperative global tick budget. None of the sources calls world generation. */
        public void step(int budget,long deadline) {
            while(!done&&budget-->0&&System.nanoTime()<deadline) {
                samples++;
                if(stage==0) {
                    generators=collectMapGenStructures(world);
                    String base=target.id.startsWith("Temple:")?"Temple":target.id;
                    WorldSavedData data=world.perWorldStorage==null||base.indexOf(':')>=0||base.indexOf('/')>=0||base.contains("..")?null:world.perWorldStorage.loadData(MapGenStructureData.class,base);
                    if(data instanceof MapGenStructureData) {
                        saved=((MapGenStructureData)data).func_143041_a();
                        List<String> keys=new ArrayList<String>();for(Object key:saved.func_150296_c()){if(keys.size()>=65536)break;if(key instanceof String)keys.add((String)key);}savedKeys=keys.iterator();
                    }
                    stage=1;
                } else if(stage==1) {
                    if(savedKeys!=null&&savedKeys.hasNext()) {
                        NBTBase raw=saved.getTag(savedKeys.next());if(!(raw instanceof NBTTagCompound))continue;
                        NBTTagCompound entry=(NBTTagCompound)raw;
                        if(!entry.hasKey("ChunkX",99)||!entry.hasKey("ChunkZ",99)||entry.hasKey("Valid")&&!entry.getBoolean("Valid"))continue;
                        int cx=entry.getInteger("ChunkX"),cz=entry.getInteger("ChunkZ");
                        if(Math.abs((long)cx)>1875000||Math.abs((long)cz)>1875000)continue;
                        int[] bb=entry.getIntArray("BB");
                        if(bb.length==6)registeredBox(new StructureBoundingBox(bb),cx,cz);
                        else {
                            int bx=cx*16+8,bz=cz*16+8;
                            if(!target.id.startsWith("Temple:")||matchesTempleBiome(world,bx,bz,target.id))consider(bx,64,bz,false,"recorded");
                        }
                    } else {memory=SavedStructureRecords.snapshot(generators,target.id).iterator();stage=2;}
                } else if(stage==2) {
                    if(memory.hasNext())registeredBox(memory.next(),null,null);
                    else {points=AstralLandmarks.get(world).points(target.id).iterator();stage=3;}
                } else if(stage==3) {
                    if(points.hasNext()){AstralLandmarks.Point p=points.next();consider(p.x,p.y,p.z,true,"recorded");}
                    else stage=4;
                } else if(stage==4) {
                    if(foreign==null)foreign=AstralModStructures.known(world,target,x,z);
                    if(foreign.hasNext()){AstralLandmarks.Point p=foreign.next();if(p!=null)consider(p.x,p.y,p.z,true,"recorded");continue;}
                    if(world.getWorldInfo().isMapFeaturesEnabled()&&ID_STRONGHOLD.equals(target.id))try {
                        for(ChunkPosition p:VanillaStructureProbe.strongholds(world,generators))consider(p.chunkPosX,p.chunkPosY,p.chunkPosZ,false,"predicted");
                    }catch(ReflectiveOperationException e){am2.LogHelper.warn("Astral Compass: stronghold prediction unavailable: %s",e.toString());}
                    probe=world.getWorldInfo().isMapFeaturesEnabled()?VanillaStructureProbe.create(world,target.id,generators):null;
                    stage=5;if(probe==null&&!ID_WITCHWOOD_FOREST.equals(target.id))done=true;
                } else {
                    boolean biome=ID_WITCHWOOD_FOREST.equals(target.id);
                    int spacing=biome?1:probe.spacing(),step=biome?32:spacing*16,limit=radius/step+2;
                    if(biome) {
                        int bx=x+rx*step,bz=z+rz*step;
                        if(Math.abs((long)bx)<=30000000&&Math.abs((long)bz)<=30000000&&BiomeWitchwoodForest.instance!=null&&world.getWorldChunkManager().getBiomeGenAt(bx,bz)==BiomeWitchwoodForest.instance)consider(bx,64,bz,false,"biome");
                    } else {
                        int originX=Math.floorDiv(x>>4,spacing),originZ=Math.floorDiv(z>>4,spacing);
                        int regionX=originX+rx,regionZ=originZ+rz;
                        if(Math.abs((long)regionX*spacing)<=1875002&&Math.abs((long)regionZ*spacing)<=1875002) {
                            int[] point=probe.candidate(regionX,regionZ);
                            if(point!=null) {int bx=point[0]*16+8,bz=point[1]*16+8;
                                if(Math.abs((long)bx)<=30000000&&Math.abs((long)bz)<=30000000&&matchesTempleBiome(world,bx,bz,target.id))consider(bx,64,bz,false,"predicted");
                            }
                        }
                    }
                    if(rx==rz||rx<0&&rx==-rz||rx>0&&rx==1-rz){int t=dx;dx=-dz;dz=t;}
                    rx+=dx;rz+=dz;int ring=Math.max(Math.abs(rx),Math.abs(rz));
                    if(++iterations>=(2*limit+1)*(2*limit+1)||best!=null&&(long)Math.max(0,ring-1)*step>Math.sqrt(bestDistance)+1)done=true;
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
