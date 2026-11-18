package am2.compass;

import net.minecraft.nbt.*;
import net.minecraft.world.*;
import java.util.*;

/** AM2-owned dimension-local index. Stores coordinates, never worlds or entities. */
public final class AstralLandmarks extends WorldSavedData {
    private static final String NAME="AM2AstralLandmarks";
    public static final int MAX_POINTS=8192;
    private final LinkedHashMap<String,Point> entries=new LinkedHashMap<String,Point>();
    private final Map<String,LinkedHashMap<String,Point>> byType=new LinkedHashMap<String,LinkedHashMap<String,Point>>();
    private final Map<String,List<Point>> snapshots=new HashMap<String,List<Point>>();
    static boolean validHeight(int y) { return y>=-4096 && y<=4095; }
    private void put(Point point) {
        String key=point.key();if(entries.containsKey(key))return;
        if(entries.size()>=MAX_POINTS) {
            Point old=entries.remove(entries.keySet().iterator().next());
            Map<String,Point> type=byType.get(old.id);type.remove(old.key());
            if(type.isEmpty())byType.remove(old.id);snapshots.remove(old.id);
        }
        entries.put(key,point);
        LinkedHashMap<String,Point> type=byType.get(point.id);
        if(type==null){type=new LinkedHashMap<String,Point>();byType.put(point.id,type);}
        type.put(key,point);snapshots.remove(point.id);
    }
    public AstralLandmarks(String name){super(name);}
    public static final class Point {
        public final String id; public final int x,y,z;
        public final boolean hasY; public final String source;
        Point(String id,int x,int y,int z){this(id,x,y,z,true,"recorded");}
        Point(String id,int x,int y,int z,boolean hasY,String source){this.id=id;this.x=x;this.y=y;this.z=z;this.hasY=hasY;this.source=source;}
        String key(){return id+":"+x+":"+y+":"+z;}
    }
    public static AstralLandmarks get(World world) {
        AstralLandmarks data=(AstralLandmarks)world.perWorldStorage.loadData(AstralLandmarks.class,NAME);
        if(data==null){data=new AstralLandmarks(NAME);world.perWorldStorage.setData(NAME,data);}
        return data;
    }
    public static void record(World world,int x,int y,int z,String id) {
        if(world==null || world.isRemote || world.perWorldStorage==null || !AstralStructureLocator.validId(id) || Math.abs((long)x)>30000000 || Math.abs((long)z)>30000000 || !validHeight(y)) return;
        AstralLandmarks data=get(world);Point p=new Point(id,x,y,z);
        if(data.entries.containsKey(p.key())) return;
        data.put(p);data.markDirty();
    }
    /** ASM hook keeps the original success result and does not run a generator. */
    public static boolean recordSuccess(boolean success,World world,int x,int y,int z,String id) {
        if(success) record(world,x,y,z,id);return success;
    }
    public static void recordIfChanged(boolean changed,World world,int x,int y,int z,String id) {
        if(changed)record(world,x,y,z,id);
    }
    public Collection<String> types(){return new LinkedHashSet<String>(byType.keySet());}
    public List<Point> points(String id){
        List<Point> cached=snapshots.get(id);if(cached!=null)return cached;
        Map<String,Point> type=byType.get(id);
        cached=type==null?Collections.<Point>emptyList():Collections.unmodifiableList(new ArrayList<Point>(type.values()));
        // Do not retain arbitrary missing query IDs.
        if(type!=null)snapshots.put(id,cached);return cached;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        entries.clear();byType.clear();snapshots.clear();NBTTagList list=tag.getTagList("points",10);
        for(int i=0;i<Math.min(MAX_POINTS,list.tagCount());i++) {
            NBTTagCompound e=list.getCompoundTagAt(i);String id=e.getString("id");
            if(!AstralStructureLocator.validId(id))continue;
            Point p=new Point(id,e.getInteger("x"),e.getInteger("y"),e.getInteger("z"));
            if(Math.abs((long)p.x)<=30000000 && Math.abs((long)p.z)<=30000000 && validHeight(p.y))put(p);
        }
    }
    @Override public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list=new NBTTagList();
        for(Point p:entries.values()){NBTTagCompound e=new NBTTagCompound();e.setString("id",p.id);e.setInteger("x",p.x);e.setInteger("y",p.y);e.setInteger("z",p.z);list.appendTag(e);}
        tag.setTag("points",list);
    }
}
