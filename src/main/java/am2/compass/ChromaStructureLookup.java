package am2.compass;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.tileentity.TileEntity;
import java.lang.reflect.*;
import java.util.*;

/** Read-only V33 integration. No foreign class in public signatures; no generation/recache calls. */
final class ChromaStructureLookup {
    private static final String BASE="Reika.ChromatiCraft.";
    private static final String LOCATION="Reika.DragonAPI.Instantiable.Data.Immutable.WorldLocation";
    private static final Set<String> WARNED=Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String,Boolean>());
    private static void warn(String phase,Throwable failure) {
        Throwable detail=failure instanceof InvocationTargetException&&failure.getCause()!=null?failure.getCause():failure;
        if(WARNED.add(phase))am2.LogHelper.warn("Astral Compass ChromatiCraft (%s): %s",phase,detail.toString());
    }
    private static Class<?> type(String name)throws ClassNotFoundException {
        return Class.forName(name,false,ChromaStructureLookup.class.getClassLoader());
    }
    private static Field field(Class<?> owner,String name)throws ReflectiveOperationException {
        Field f=owner.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static List<Object> copy(Collection<?> values,int limit) {
        List<Object> out=new ArrayList<Object>();
        for(Object value:values){if(out.size()>=limit)break;out.add(value);}return out;
    }
    static Iterator<AstralLandmarks.Point> known(World world,String id,int x,int z,int radius) {
        if(world==null||world.isRemote)return Collections.<AstralLandmarks.Point>emptyList().iterator();
        try{return new Cursor(world,id,x,z,radius);}
        catch(ReflectiveOperationException e){warn("schema",e);}
        catch(LinkageError e){warn("optional classes",e);}
        catch(RuntimeException e){warn("snapshot",e);}
        return Collections.<AstralLandmarks.Point>emptyList().iterator();
    }
    private static final class Cursor implements Iterator<AstralLandmarks.Point> {
        private final World world;
        private final String id,kind,color;
        private final boolean pylon;
        private final int originX,originZ,radius;
        private final Class<?> tileType;
        private final Method tileKind;
        private final Field lx,ly,lz,dimension;
        private final Iterator<Chunk> chunks;
        private Iterator<Object> tiles=Collections.<Object>emptyList().iterator(),cacheEntries;
        private final Set<Long> exactChunks=new HashSet<Long>();
        private final Set<String> exactPoints=new HashSet<String>();
        private Object cache;
        private Method cacheGet;
        private Field status,generated,entryLocation,entryColor;
        private int scannedTiles;
        private boolean cacheStarted,done;
        Cursor(World world,String id,final int originX,final int originZ,int radius)throws ReflectiveOperationException {
            this.world=world;this.id=id;this.originX=originX;this.originZ=originZ;this.radius=radius;kind=id.substring("chromaticraft:".length());
            pylon=kind.equals("PYLON")||kind.startsWith("PYLON_");color=kind.startsWith("PYLON_")?kind.substring(6):"";
            tileType=type(BASE+(pylon?"TileEntity.Networking.TileEntityCrystalPylon":"TileEntity.Technical.TileEntityStructControl"));
            tileKind=tileType.getMethod(pylon?"getColor":"getStructureType");
            Class<?> loc=type(LOCATION);lx=loc.getField("xCoord");ly=loc.getField("yCoord");lz=loc.getField("zCoord");dimension=loc.getField("dimensionID");
            List<Chunk> loaded=new ArrayList<Chunk>();
            if(world.getChunkProvider() instanceof ChunkProviderServer) {
                List<?> raw=((ChunkProviderServer)world.getChunkProvider()).loadedChunks;
                int count=0;
                for(Object value:raw){
                    if(++count>8192)break;if(!(value instanceof Chunk))continue;Chunk chunk=(Chunk)value;
                    // Intersect the query circle conservatively; points are checked again by Search.
                    double dx=chunk.xPosition*16.0+8-originX,dz=chunk.zPosition*16.0+8-originZ;
                    if(dx*dx+dz*dz<=(double)(radius+24)*(radius+24))loaded.add(chunk);
                }
            }
            Collections.sort(loaded,new Comparator<Chunk>(){
                public int compare(Chunk a,Chunk b){return Double.compare(distance(a,originX,originZ),distance(b,originX,originZ));}
            });chunks=loaded.iterator();
        }
        private static double distance(Chunk c,int x,int z){double dx=c.xPosition*16.0+8-x,dz=c.zPosition*16.0+8-z;return dx*dx+dz*dz;}
        private long chunkKey(int x,int z){return ((long)(x>>4)<<32)^((z>>4)&0xffffffffL);}
        private AstralLandmarks.Point exact(int x,int y,int z) {
            String key=x+":"+y+":"+z;
            if(!exactPoints.add(key))return null;
            exactChunks.add(chunkKey(x,z));return new AstralLandmarks.Point(id,x,y,z);
        }
        private AstralLandmarks.Point loadedTile(Object raw)throws ReflectiveOperationException {
            if(!(raw instanceof TileEntity)||!tileType.isInstance(raw))return null;
            TileEntity tile=(TileEntity)raw;if(tile.isInvalid()||tile.getWorldObj()!=world)return null;
            if(!world.getChunkProvider().chunkExists(tile.xCoord>>4,tile.zCoord>>4))return null;
            Object value=tileKind.invoke(tile);if(!(value instanceof Enum))return null;String name=((Enum<?>)value).name();
            if(pylon?!color.isEmpty()&&!color.equals(name):!kind.equals(name))return null;
            return exact(tile.xCoord,tile.yCoord,tile.zCoord);
        }
        private boolean wantedLocation(Object location)throws ReflectiveOperationException {
            if(location==null||dimension.getInt(location)!=world.provider.dimensionId)return false;
            double dx=(double)lx.getInt(location)-originX,dz=(double)lz.getInt(location)-originZ;
            return dx*dx+dz*dz<=(double)(radius+24)*(radius+24);
        }
        private List<Object> locationSnapshot(Collection<?> keys)throws ReflectiveOperationException {
            List<Object> result=new ArrayList<Object>();int inspected=0;
            for(Object key:keys){if(++inspected>65536||result.size()>=8192)break;if(wantedLocation(key))result.add(key);}
            return result;
        }
        private void beginCache()throws ReflectiveOperationException {
            cacheStarted=true;
            if(pylon) {
                Class<?> generator=type(BASE+"World.IWG.PylonGenerator");Object singleton=generator.getField("instance").get(null);
                Object raw=field(generator,"colorCache").get(singleton);
                Class<?> entryType=type(BASE+"World.IWG.PylonGenerator$PylonEntry");entryLocation=entryType.getField("location");entryColor=entryType.getField("color");
                List<Object> entries=new ArrayList<Object>();
                int inspected=0;
                if(raw instanceof Map)for(Object values:((Map<?,?>)raw).values())if(values instanceof Collection) {
                    for(Object entry:(Collection<?>)values){
                        if(++inspected>65536||entries.size()>=8192)break;
                        Object value=entryColor.get(entry);
                        if(value instanceof Enum&&(color.isEmpty()||color.equals(((Enum<?>)value).name()))&&wantedLocation(entryLocation.get(entry)))entries.add(entry);
                    }
                    if(inspected>65536||entries.size()>=8192)break;
                }
                cacheEntries=entries.iterator();
            } else {
                Class<?> generator=type(BASE+"World.IWG.DungeonGenerator");Object singleton=generator.getField("instance").get(null);
                Object raw=field(generator,"structureMap").get(singleton);cache=null;
                if(raw instanceof Map)for(Map.Entry<?,?> entry:((Map<?,?>)raw).entrySet())
                    if(entry.getKey() instanceof Enum&&kind.equals(((Enum<?>)entry.getKey()).name())){cache=entry.getValue();break;}
                if(cache==null){cacheEntries=Collections.<Object>emptyList().iterator();return;}
                Class<?> cacheType=type("Reika.DragonAPI.Instantiable.Data.Maps.TileEntityCache");
                cacheGet=cacheType.getMethod("get",type(LOCATION));
                Object keys=cacheType.getMethod("keySet").invoke(cache);
                cacheEntries=keys instanceof Collection?locationSnapshot((Collection<?>)keys).iterator():Collections.<Object>emptyList().iterator();
                Class<?> data=type(BASE+"World.IWG.DungeonGenerator$StructureGenData");status=field(data,"status");generated=field(data,"generatedLocation");
            }
        }
        private AstralLandmarks.Point cached(Object entry)throws ReflectiveOperationException {
            Object loc=pylon?entryLocation.get(entry):entry;
            if(loc==null||dimension.getInt(loc)!=world.provider.dimensionId)return null;
            if(pylon) {
                Object value=entryColor.get(entry);if(!(value instanceof Enum)||!color.isEmpty()&&!color.equals(((Enum<?>)value).name()))return null;
                return exact(lx.getInt(loc),ly.getInt(loc),lz.getInt(loc));
            }
            Object data=cacheGet.invoke(cache,loc);if(data==null)return null;
            Object state=status.get(data);if(!(state instanceof Enum)||!"SUCCESS".equals(((Enum<?>)state).name()))return null;
            Object point=generated.get(data);
            if(point!=null) {
                Class<?> shape=point.getClass();return exact(shape.getField("xCoord").getInt(point),shape.getField("yCoord").getInt(point),shape.getField("zCoord").getInt(point));
            }
            // V33 recacheStructureTile marks SUCCESS but does not fill generatedLocation.
            // Its WorldLocation key is the chunk origin at Y=0, not a real block at that height.
            int x=lx.getInt(loc)+8,z=lz.getInt(loc)+8;
            if(exactChunks.contains(chunkKey(x,z)))return null;
            return new AstralLandmarks.Point(id,x,0,z,false,"region");
        }
        @Override public boolean hasNext(){return !done;}
        @Override public AstralLandmarks.Point next(){
            if(done)throw new NoSuchElementException();
            try {
                if(!cacheStarted) {
                    if(scannedTiles<65536&&tiles.hasNext()){scannedTiles++;return loadedTile(tiles.next());}
                    if(scannedTiles<65536&&chunks.hasNext()) {
                        Chunk chunk=chunks.next();
                        if(world.getChunkProvider().chunkExists(chunk.xPosition,chunk.zPosition))tiles=copy(chunk.chunkTileEntityMap.values(),Math.min(4096,65536-scannedTiles)).iterator();
                        return null;
                    }
                    beginCache();return null;
                }
                if(cacheEntries.hasNext())return cached(cacheEntries.next());
                done=true;return null;
            } catch(ReflectiveOperationException e){warn(pylon?"pylon cache":"dungeon cache",e);done=true;return null;}
              catch(RuntimeException e){warn("cache changed",e);done=true;return null;}
              catch(LinkageError e){warn("optional schema",e);done=true;return null;}
        }
        @Override public void remove(){throw new UnsupportedOperationException();}
    }
}
