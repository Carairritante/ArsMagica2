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
        if(available("Reika.ChromatiCraft.World.IWG.PylonGenerator")) {
            add(map,"chromaticraft:PYLON","ChromatiCraft: Pylon",world);
            try {
                Class<?> colors=Class.forName("Reika.ChromatiCraft.Registry.CrystalElement",false,AstralModStructures.class.getClassLoader());
                int count=0;for(Object color:colors.getEnumConstants()) {
                    if(count++>=16)break;String name=((Enum<?>)color).name();
                    add(map,"chromaticraft:PYLON_"+name,"ChromatiCraft: "+StructureTargetInfo.prettify(name)+" Pylon",world);
                }
            } catch(ClassNotFoundException e){warn("chromaticraft:pylon-colors",e);}catch(LinkageError e){warn("chromaticraft:pylon-colors",e);}
        }
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
        return known(world,target,originX,originZ,AstralStructureLocator.DEFAULT_RADIUS);
    }
    static Iterator<AstralLandmarks.Point> known(final World world,StructureTargetInfo target,final int originX,final int originZ,int radius) {
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
        if(target.id.startsWith("chromaticraft:"))return ChromaStructureLookup.known(world,target.id,originX,originZ,radius);
        return Collections.<AstralLandmarks.Point>emptyList().iterator();
    }
    private static void warn(String id,Throwable e){if(warned.add(id))am2.LogHelper.warn("Astral Compass: optional adapter unavailable for %s: %s",id,e.toString());}
    // Kept as a harmless empty fallback: incremental known() owns external cache iteration.
    static AstralStructureLocator.SearchResult locateKnown(World world,StructureTargetInfo target,int x,int z){return null;}
}
