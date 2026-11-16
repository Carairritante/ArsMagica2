package am2.compass;

import net.minecraft.world.World;
import net.minecraft.tileentity.TileEntity;
import java.lang.reflect.*;
import java.util.*;

/** Optional adapters verified against the pack's JARs; no foreign type in descriptors. */
final class AstralModStructures {
    private static final String CHROMA="Reika.ChromatiCraft.World.IWG.DungeonGenerator";
    private static final Set<String> warned=new HashSet<String>();
    static boolean available(String name) {
        try {Class.forName(name,false,AstralModStructures.class.getClassLoader());return true;}
        catch(ClassNotFoundException e){return false;}catch(LinkageError e){return false;}
    }
    static void addCatalog(World world,Map<String,StructureTargetInfo> map) {
        if(available(CHROMA)) for(String type:new String[]{"CAVERN","BURROW","OCEAN","DESERT","SNOWSTRUCT","BIOMEFRAG"})
            add(map,"chromaticraft:"+type,"ChromatiCraft: "+StructureTargetInfo.prettify(type),world);
        if(available("electroblob.wizardry.WizardryWorldGenerator")) add(map,"wizardry:wizard_tower","Electroblob's Wizardry: Wizard Tower",world);
        if(available("mglucas0123.utils.GenericDungeon")) for(String method:am2.preloader.AstralStructureTransformer.oreCatalog())
            add(map,"orespawn:"+method,"OreSpawn: "+StructureTargetInfo.prettify(method.substring(4)),world);
        if(available("thaumcraft.common.lib.world.WorldGenMound")) {
            add(map,"thaumcraft:mound","Thaumcraft: Mound",world);
            add(map,"thaumcraft:eldritch_ring","Thaumcraft: Eldritch Ring",world);
            add(map,"thaumcraft:hilltop_stones","Thaumcraft: Hilltop Stones",world);
        }
        if(world.provider.getClass().getName().startsWith("twilightforest.") && available("twilightforest.TFFeature")) try {
            Class<?> feature=Class.forName("twilightforest.TFFeature");
            for(Object f:(Object[])feature.getField("featureList").get(null)) if(f!=null && feature.getField("isStructureEnabled").getBoolean(f)) {
                String name=(String)feature.getField("name").get(f);
                if(!"nothing".equals(name))add(map,"twilightforest:"+name,"Twilight Forest: "+StructureTargetInfo.prettify(name),world);
            }
        } catch(ReflectiveOperationException e){warn("twilightforest",e);}catch(LinkageError e){warn("twilightforest",e);}
    }
    private static void add(Map<String,StructureTargetInfo> map,String id,String name,World world) {
        if(!map.containsKey(id))map.put(id,new StructureTargetInfo(id,name,StructureTargetInfo.CAT_MODDED,world.provider.dimensionId,true,id.hashCode()));
    }
    static Iterator<AstralLandmarks.Point> known(final World world,StructureTargetInfo target,final int originX,final int originZ) {
        if(target.id.startsWith("twilightforest:") && world.provider.getClass().getName().startsWith("twilightforest.")) try {
            final Class<?> feature=Class.forName("twilightforest.TFFeature");
            final Method find=feature.getMethod("getFeatureForRegion",int.class,int.class,World.class);
            final Method center=feature.getMethod("getNearestCenterXYZ",int.class,int.class,World.class);
            final Field name=feature.getField("name");
            final String id=target.id;
            return new Iterator<AstralLandmarks.Point>() {
                int i;
                public boolean hasNext(){return i<33*33;}
                public AstralLandmarks.Point next(){
                    int cx=(Math.round((originX>>4)/16F)+i%33-16)*16;
                    int cz=(Math.round((originZ>>4)/16F)+i/33-16)*16;i++;
                    try {
                        Object f=find.invoke(null,cx,cz,world);
                        if(!id.equals("twilightforest:"+name.get(f)))return null;
                        net.minecraft.util.ChunkCoordinates p=(net.minecraft.util.ChunkCoordinates)center.invoke(null,cx,cz,world);
                        return new AstralLandmarks.Point(id,p.posX,p.posY,p.posZ);
                    }catch(ReflectiveOperationException e){warn(id,e);i=33*33;return null;}
                }
                public void remove(){throw new UnsupportedOperationException();}
            };
        } catch(ReflectiveOperationException e){warn(target.id,e);}catch(LinkageError e){warn(target.id,e);}
        if(!target.id.startsWith("chromaticraft:"))return Collections.<AstralLandmarks.Point>emptyList().iterator();
        try {
            Class<?> generator=Class.forName(CHROMA);
            Object instance=generator.getField("instance").get(null);
            Field structures=generator.getDeclaredField("structureMap");structures.setAccessible(true);
            final Object cache=findEnumEntry((Map<?,?>)structures.get(instance),target.id.substring(target.id.indexOf(':')+1));
            if(cache==null)return Collections.<AstralLandmarks.Point>emptyList().iterator();
            final Method get=cache.getClass().getMethod("get",Class.forName("Reika.DragonAPI.Instantiable.Data.Immutable.WorldLocation"));
            final Iterator<?> locations=((Collection<?>)cache.getClass().getMethod("keySet").invoke(cache)).iterator();
            final String id=target.id;final int dimension=world.provider.dimensionId;
            return new Iterator<AstralLandmarks.Point>() {
                int count;
                public boolean hasNext(){return count<8192 && locations.hasNext();}
                public AstralLandmarks.Point next(){
                    count++;Object location=locations.next();
                    try {
                        if(location.getClass().getField("dimensionID").getInt(location)!=dimension)return null;
                        Object data=get.invoke(cache,location);if(data==null)return null;
                        Field status=data.getClass().getDeclaredField("status");status.setAccessible(true);
                        if(!"SUCCESS".equals(((Enum<?>)status.get(data)).name()))return null;
                        Field generated=data.getClass().getDeclaredField("generatedLocation");generated.setAccessible(true);
                        Object point=generated.get(data);if(point==null)return null;
                        return new AstralLandmarks.Point(id,point.getClass().getField("xCoord").getInt(point),point.getClass().getField("yCoord").getInt(point),point.getClass().getField("zCoord").getInt(point));
                    }catch(ReflectiveOperationException e){warn(id,e);return null;}
                }
                public void remove(){throw new UnsupportedOperationException();}
            };
        } catch(ReflectiveOperationException e){warn(target.id,e);}catch(LinkageError e){warn(target.id,e);}
        return Collections.<AstralLandmarks.Point>emptyList().iterator();
    }
    private static Object findEnumEntry(Map<?,?> entries,String name) {
        for(Map.Entry<?,?> e:entries.entrySet())if(e.getKey() instanceof Enum && ((Enum<?>)e.getKey()).name().equals(name))return e.getValue();return null;
    }
    private static void warn(String id,Throwable e){if(warned.add(id))am2.LogHelper.warn("Astral Compass: optional adapter unavailable for %s: %s",id,e.toString());}
    // Kept as a harmless empty fallback: incremental known() owns external cache iteration.
    static AstralStructureLocator.SearchResult locateKnown(World world,StructureTargetInfo target,int x,int z){return null;}
}
