package am2.compass;

import net.minecraft.world.World;
import net.minecraft.world.gen.structure.*;
import java.lang.reflect.Field;
import java.util.*;

/** Snapshots already-known generator records; never creates starts or loads chunks. */
final class SavedStructureRecords {
    static List<StructureBoundingBox> snapshot(List<MapGenStructure> generators,String id) {
        List<StructureBoundingBox> boxes=new ArrayList<StructureBoundingBox>();
        String base=id.startsWith("Temple:")?"Temple":id;
        for(MapGenStructure generator:generators) {
            try {
                if(!base.equals(generator.func_143025_a()))continue;
                Field field;
                try{field=MapGenStructure.class.getDeclaredField("structureMap");}
                catch(NoSuchFieldException e){field=MapGenStructure.class.getDeclaredField("field_75053_d");}
                field.setAccessible(true);Object raw=field.get(generator);
                if(!(raw instanceof Map))continue;
                int inspected=0;
                for(Object value:((Map<?,?>)raw).values()) {
                    if(++inspected>8192 || boxes.size()>=8192)break;
                    if(!(value instanceof StructureStart))continue;
                    StructureStart start=(StructureStart)value;
                    if(!start.isSizeableStructure())continue;
                    StructureBoundingBox box=start.getBoundingBox();
                    if(box!=null)boxes.add(new StructureBoundingBox(box));
                }
            } catch(ReflectiveOperationException e) { /* Unsupported generator: retain other sources. */ }
              catch(RuntimeException e) { /* A foreign map is optional. */ }
              catch(LinkageError e) { /* No dependency on foreign implementation details. */ }
        }
        return boxes;
    }
}
