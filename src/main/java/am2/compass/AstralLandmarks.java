package am2.compass;

import net.minecraft.nbt.*;
import net.minecraft.world.*;
import java.util.*;

/** AM2-owned dimension-local index. Stores coordinates, never worlds or entities. */
public final class AstralLandmarks extends WorldSavedData {
    private static final String NAME="AM2AstralLandmarks";
    public static final int MAX_POINTS=8192;
    private final LinkedHashMap<String,Point> entries=new LinkedHashMap<String,Point>();
    public AstralLandmarks(String name){super(name);}
    public static final class Point {
        public final String id; public final int x,y,z;
        Point(String id,int x,int y,int z){this.id=id;this.x=x;this.y=y;this.z=z;}
        String key(){return id+":"+(x>>4)+":"+(z>>4);}
    }
    public static AstralLandmarks get(World world) {
        AstralLandmarks data=(AstralLandmarks)world.perWorldStorage.loadData(AstralLandmarks.class,NAME);
        if(data==null){data=new AstralLandmarks(NAME);world.perWorldStorage.setData(NAME,data);}
        return data;
    }
    public static void record(World world,int x,int y,int z,String id) {
        if(world==null || world.isRemote || world.perWorldStorage==null || !AstralStructureLocator.validId(id) || Math.abs((long)x)>30000000 || Math.abs((long)z)>30000000 || y<0 || y>4095) return;
        AstralLandmarks data=get(world);Point p=new Point(id,x,y,z);
        if(data.entries.containsKey(p.key())) return;
        if(data.entries.size()>=MAX_POINTS) data.entries.remove(data.entries.keySet().iterator().next());
        data.entries.put(p.key(),p);data.markDirty();
    }
    /** ASM hook keeps the original success result and does not run a generator. */
    public static boolean recordSuccess(boolean success,World world,int x,int y,int z,String id) {
        if(success) record(world,x,y,z,id);return success;
    }
    public static void recordIfChanged(boolean changed,World world,int x,int y,int z,String id) {
        if(changed)record(world,x,y,z,id);
    }
    public Collection<String> types(){Set<String> types=new LinkedHashSet<String>();for(Point p:entries.values()) types.add(p.id);return types;}
    public List<Point> points(String id){List<Point> out=new ArrayList<Point>();for(Point p:entries.values()) if(id.equals(p.id)) out.add(p);return out;}
    @Override public void readFromNBT(NBTTagCompound tag) {
        entries.clear();NBTTagList list=tag.getTagList("points",10);
        for(int i=0;i<Math.min(MAX_POINTS,list.tagCount());i++) {
            NBTTagCompound e=list.getCompoundTagAt(i);String id=e.getString("id");
            if(!AstralStructureLocator.validId(id))continue;
            Point p=new Point(id,e.getInteger("x"),e.getInteger("y"),e.getInteger("z"));
            if(Math.abs((long)p.x)<=30000000 && Math.abs((long)p.z)<=30000000 && p.y>=0 && p.y<=4095)entries.put(p.key(),p);
        }
    }
    @Override public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list=new NBTTagList();
        for(Point p:entries.values()){NBTTagCompound e=new NBTTagCompound();e.setString("id",p.id);e.setInteger("x",p.x);e.setInteger("y",p.y);e.setInteger("z",p.z);list.appendTag(e);}
        tag.setTag("points",list);
    }
}
