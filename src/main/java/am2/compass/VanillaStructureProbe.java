package am2.compass;

import net.minecraft.world.World;
import net.minecraft.world.gen.structure.*;
import java.lang.reflect.*;
import java.util.*;

/** Probes private copies only. Village/temple maths avoids World.setRandomSeed. */
abstract class VanillaStructureProbe {
    abstract boolean test(int x,int z);
    int spacing() { return 1; }
    int[] candidate(int regionX,int regionZ) { return test(regionX,regionZ)?new int[]{regionX,regionZ}:null; }
    static VanillaStructureProbe create(final World world, String id) {
        return create(world,id,AstralStructureLocator.collectMapGenStructures(world));
    }
    static VanillaStructureProbe create(final World world,String id,List<MapGenStructure> generators) {
        for(final MapGenStructure original:generators) {
            final Class<?> cls=original.getClass();
            try {
                if(AstralStructureLocator.ID_VILLAGE.equals(id) && cls==MapGenVillage.class
                        || id.startsWith("Temple:") && cls==MapGenScatteredFeature.class) {
                    final boolean village=cls==MapGenVillage.class;
                    final int distance=integer(original,village?new String[]{"field_82665_g"}:new String[]{"maxDistanceBetweenScatteredFeatures","field_82669_g"});
                    final int separation=integer(original,village?new String[]{"field_82666_h"}:new String[]{"minDistanceBetweenScatteredFeatures","field_82670_h"});
                    if(distance<=separation || separation<0 || distance>4096) return null;
                    return new VanillaStructureProbe() {
                        @Override int spacing() { return distance; }
                        @Override int[] candidate(int rx,int rz) {
                            Random r=new Random((long)rx*341873128712L+(long)rz*132897987541L+world.getSeed()+(village?10387312:14357617));
                            int x=rx*distance+r.nextInt(distance-separation),z=rz*distance+r.nextInt(distance-separation);
                            return !village || world.getWorldChunkManager().areBiomesViable(x*16+8,z*16+8,0,MapGenVillage.villageSpawnBiomes)?new int[]{x,z}:null;
                        }
                        boolean test(int x,int z) {
                            int[] point=candidate(Math.floorDiv(x,distance),Math.floorDiv(z,distance));
                            return point!=null && point[0]==x && point[1]==z;
                        }
                    };
                }
                if(AstralStructureLocator.ID_MINESHAFT.equals(id) && cls==MapGenMineshaft.class) {
                    final MineProbe p=new MineProbe();copyFields(original,p,cls);p.init(world);
                    Random seed=new Random(world.getSeed());final long a=seed.nextLong(),b=seed.nextLong();
                    return new VanillaStructureProbe(){boolean test(int x,int z){return p.check(x,z,(long)x*a^(long)z*b^world.getSeed());}};
                }
                if(AstralStructureLocator.ID_FORTRESS.equals(id) && cls==MapGenNetherBridge.class) {
                    final FortressProbe p=new FortressProbe();p.init(world);
                    return new VanillaStructureProbe(){boolean test(int x,int z){return p.check(x,z);}};
                }
            } catch(ReflectiveOperationException e) {
                am2.LogHelper.warn("Astral Compass: prediction disabled for %s: %s",id,e.toString());return null;
            }
        }
        return null;
    }
    static java.util.List<net.minecraft.world.ChunkPosition> strongholds(World world) throws ReflectiveOperationException {
        return strongholds(world,AstralStructureLocator.collectMapGenStructures(world));
    }
    static java.util.List<net.minecraft.world.ChunkPosition> strongholds(World world,List<MapGenStructure> generators) throws ReflectiveOperationException {
        for(MapGenStructure original:generators) if(original.getClass()==MapGenStronghold.class) {
            StrongholdProbe p=new StrongholdProbe();copyFields(original,p,MapGenStronghold.class);return p.positions(world);
        }
        return Collections.emptyList();
    }
    private static int integer(Object obj,String[] names) throws ReflectiveOperationException {
        for(String name:names) try {Field f=obj.getClass().getDeclaredField(name);f.setAccessible(true);return f.getInt(obj);} catch(NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(Arrays.toString(names));
    }
    private static void copyFields(Object from,Object to,Class<?> owner) throws IllegalAccessException {
        for(Field f:owner.getDeclaredFields()) if(!Modifier.isStatic(f.getModifiers())) {
            f.setAccessible(true);Object value=f.get(from);
            if(value instanceof Object[]) value=((Object[])value).clone();
            if(value instanceof List) value=new ArrayList<Object>((List<?>)value);
            f.set(to,value);
        }
    }
    private static class MineProbe extends MapGenMineshaft {
        void init(World w){worldObj=w;rand=new Random();}
        boolean check(int x,int z,long seed){rand.setSeed(seed);rand.nextInt();return canSpawnStructureAtCoords(x,z);}
    }
    private static class FortressProbe extends MapGenNetherBridge {
        void init(World w){worldObj=w;rand=new Random();}
        boolean check(int x,int z){return canSpawnStructureAtCoords(x,z);}
    }
    private static class StrongholdProbe extends MapGenStronghold {
        @SuppressWarnings("unchecked") List<net.minecraft.world.ChunkPosition> positions(World w) {
            worldObj=w;rand=new Random();canSpawnStructureAtCoords(0,0);return getCoordList();
        }
    }
}
