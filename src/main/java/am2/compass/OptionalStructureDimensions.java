package am2.compass;

import java.io.InputStream;
import java.io.IOException;
import java.util.*;
import net.minecraftforge.common.DimensionManager;
import net.minecraft.world.WorldServer;

/** Optional dimension metadata. Reads fields/storage only; never loads a dimension or runs worldgen. */
public final class OptionalStructureDimensions {
    private static final Properties ORE = new Properties();
    private static final String[][] PROVIDERS = {
        {"utopia", "utopia.WorldProviderDIM80", "DimensionID", "Utopia"},
        {"danger", "extreme.WorldProviderDIM81", "DimensionID2", "Extreme"},
        {"village", "village.WorldProviderDIM82", "DimensionID3", "VillageMania"},
        {"islands", "islands.WorldProviderDIM83", "DimensionID4", "Islands"},
        {"crystal", "crystal.WorldProviderDIM84", "DimensionID5", "Crystal"},
        {"chaos", "chaos.WorldProviderDIM85", "DimensionID6", "Chaos"}
    };
    static {
        try (InputStream in = OptionalStructureDimensions.class.getResourceAsStream("/assets/arsmagica2/compass/ore_dimensions.properties")) {
            if (in != null) ORE.load(in);
        } catch (IOException ignored) { /* Unsupported metadata remains explicitly variable. */ }
    }
    private OptionalStructureDimensions() {}
    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, false, OptionalStructureDimensions.class.getClassLoader());
    }
    static Map<String,Integer> oreIds() {
        Map<String,Integer> ids = new HashMap<String,Integer>(); ids.put("overworld",0);ids.put("end",1);ids.put("nether",-1);
        for (String[] provider : PROVIDERS) try {
            ids.put(provider[0], type("mglucas0123.worlds." + provider[1]).getField(provider[2]).getInt(null));
        } catch (ReflectiveOperationException ignored) { /* Do not assume default IDs for another fork. */ }
          catch (LinkageError ignored) { /* Soft dependency. */ }
        return ids;
    }
    static Map<String,int[]> oreCatalog() {
        Map<String,int[]> result = new HashMap<String,int[]>();
        Map<String,Integer> ids = oreIds();
        try {
            boolean surface = type("mglucas0123.Config").getField("OVERWORLDDUNGEONS").getBoolean(null);
            for (String method : ORE.stringPropertyNames()) result.put(method, oreDimensions(method,ids,surface));
        } catch (ReflectiveOperationException ignored) { /* An unknown configuration is not an Overworld restriction. */ }
          catch (LinkageError ignored) { /* Soft dependency. */ }
        return result;
    }
    static int[] oreDimensions(String method, Map<String,Integer> ids, boolean surface) {
        String groups = ORE.getProperty(method);
        if (groups == null) return null;
        Set<Integer> result = new TreeSet<Integer>();
        for (String group : groups.split(",")) {
            // generateSurface is shared by Overworld and Utopia. Their two altars do not use that switch.
            if (!surface && (group.equals("overworld") || group.equals("utopia") && !method.equals("makeKingAltar") && !method.equals("makeQueenAltar"))) continue;
            Integer id = ids.get(group); if (id == null) return null; result.add(id);
        }
        int[] dimensions = new int[result.size()]; int at=0;for(Integer id:result)dimensions[at++]=id;
        return dimensions;
    }
    static int[] configuredIds(String owner,String field) {
        try {
            Object raw=type(owner).getField(field).get(null);
            if(raw instanceof int[] && ((int[])raw).length<=64)return ((int[])raw).clone();
        } catch(ReflectiveOperationException ignored) {} catch(LinkageError ignored) {}
        return new int[0];
    }
    public static Map<Integer,String> names(Collection<StructureTargetInfo> catalog) {
        Map<Integer,String> names = new TreeMap<Integer,String>();
        Map<String,Integer> ids=oreIds();
        for(String[] provider:PROVIDERS)if(ids.containsKey(provider[0]))names.put(ids.get(provider[0]),"OreSpawn: "+provider[3]);
        for(StructureTargetInfo info:catalog)for(int dim:info.getSearchDimensions()) {
            WorldServer loaded=DimensionManager.getWorld(dim);
            if(loaded!=null)names.put(dim,loaded.provider.getDimensionName());
        }
        // No createProviderFor/getWorldServer: unknown unloaded dimensions retain their numeric ID.
        return names;
    }
}
