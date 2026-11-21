package am2.compass;

import java.io.*;
import java.util.*;

/** Bounded optional extension after the unchanged v2 catalog trailer. Old clients ignore it. */
public final class AstralDimensionMetadata {
    private static final int MAGIC=0x414d3344;
    private AstralDimensionMetadata() {}
    public static final class Decoded {
        public final List<StructureTargetInfo> catalog;
        public final Map<Integer,String> names;
        private Decoded(List<StructureTargetInfo> catalog,Map<Integer,String> names) {
            this.catalog=Collections.unmodifiableList(catalog);this.names=Collections.unmodifiableMap(names);
        }
    }
    public static byte[] encode(List<StructureTargetInfo> catalog,Map<Integer,String> names) {
        if(catalog.size()>256||names.size()>128)throw new IllegalArgumentException("Dimension metadata limit exceeded");
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
            out.writeInt(MAGIC);out.writeInt(catalog.size());
            for(StructureTargetInfo info:catalog) {
                out.writeUTF(info.id);int[] dimensions=info.getSearchDimensions();out.writeInt(dimensions.length);
                for(int dimension:dimensions)out.writeInt(dimension);
            }
            out.writeInt(names.size());
            for(Map.Entry<Integer,String> entry:names.entrySet()) {
                out.writeInt(entry.getKey());String name=entry.getValue();out.writeUTF(name.length()>128?name.substring(0,128):name);
            }
            return bytes.toByteArray();
        } catch(IOException impossible) { throw new IllegalStateException(impossible); }
    }
    public static Decoded read(DataInputStream in,List<StructureTargetInfo> base) throws IOException {
        if(base.size()>256||in.readInt()!=MAGIC||in.readInt()!=base.size())throw new IOException("Invalid dimension metadata catalog");
        List<StructureTargetInfo> catalog=new ArrayList<StructureTargetInfo>();
        for(StructureTargetInfo info:base) {
            String id=in.readUTF();if(id.length()>128||!id.equals(info.id))throw new IOException("Mismatched structure ID");
            int count=in.readInt();if(count<0||count>64)throw new IOException("Invalid dimension count");
            int[] dimensions=new int[count];for(int i=0;i<count;i++)dimensions[i]=in.readInt();
            catalog.add(new StructureTargetInfo(info.id,info.rawName,info.category,dimensions,info.glyphIndex));
        }
        int count=in.readInt();if(count<0||count>128)throw new IOException("Invalid dimension name count");
        Map<Integer,String> names=new TreeMap<Integer,String>();
        for(int i=0;i<count;i++) {
            int dimension=in.readInt();String name=in.readUTF();
            if(name.length()>128||names.put(dimension,name)!=null)throw new IOException("Invalid dimension name");
        }
        if(in.available()!=0)throw new IOException("Trailing dimension metadata");
        return new Decoded(catalog,names);
    }
}
