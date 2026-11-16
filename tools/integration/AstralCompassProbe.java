import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
@Mod(modid="astralcompassprobe",name="Disposable Astral Compass Probe",version="1",dependencies="required-after:arsmagica2")
public final class AstralCompassProbe {
    boolean ran;int age;
    static Object get(Object o,String name)throws Exception {
        for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())try {
            Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o instanceof Class?null:o);
        }catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    static void set(Object o,String name,Object value)throws Exception {
        for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try {Field f=c.getDeclaredField(name);f.setAccessible(true);f.set(o,value);return;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    static Object call(Object o,String name,Object...args)throws Exception {
        for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods()) {
            if(!m.getName().equals(name)||!matches(m.getParameterTypes(),args))continue;
            try {m.setAccessible(true);return m.invoke(o instanceof Class?null:o,args);}catch(IllegalArgumentException ignored){}
        }
        throw new NoSuchMethodException(name);
    }
    static Object stat(String c,String name,Object...args)throws Exception{return call(Class.forName(c),name,args);}
    static Object field(String c,String name)throws Exception{return get(Class.forName(c),name);}
    static Object make(String c,Object...args)throws Exception {
        for(Constructor<?> k:Class.forName(c).getConstructors())if(matches(k.getParameterTypes(),args))try{return k.newInstance(args);}catch(IllegalArgumentException ignored){}
        throw new NoSuchMethodException(c);
    }
    static boolean matches(Class<?>[] types,Object[] args){
        if(types.length!=args.length)return false;
        for(int i=0;i<types.length;i++){
            Class<?> c=types[i];Object v=args[i];
            if(!c.isPrimitive()){if(v!=null&&!c.isInstance(v))return false;continue;}
            if(v==null)return false;
            if(c==int.class&&!(v instanceof Integer)||c==float.class&&!(v instanceof Float)
              ||c==double.class&&!(v instanceof Double)||c==long.class&&!(v instanceof Long)
              ||c==boolean.class&&!(v instanceof Boolean)||c==byte.class&&!(v instanceof Byte))return false;
        }
        return true;
    }

    static void need(boolean b,String s){if(!b)throw new AssertionError(s);}
    static void log(String s)throws Exception{System.out.println("ASTRAL_PROBE "+s);Files.write(Paths.get("astral-results.txt"),(s+"\n").getBytes("UTF-8"),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    @Mod.EventHandler public void start(FMLServerStartedEvent event){FMLCommonHandler.instance().bus().register(this);}
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || ran || ++age<5)return;ran=true;
        try{run();log("ASTRAL_COMPASS=PASS");}catch(Throwable e){try{log("ASTRAL_COMPASS=FAIL "+e);e.printStackTrace();}catch(Exception ignored){}}
        try{call(stat("net.minecraft.server.MinecraftServer","func_71276_C"),"func_71263_m");}catch(Exception e){e.printStackTrace();}
    }
    static Object search(Object world,Object player,Object target)throws Exception{
        Object session=make("am2.compass.AstralStructureLocator$Search",world,player,target);
        int slices=0;long worst=0,total=0;
        while(!(Boolean)get(session,"done") && slices++<5000){long start=System.nanoTime();call(session,"step",512,start+2000000L);long took=System.nanoTime()-start;worst=Math.max(worst,took);total+=took;}
        need((Boolean)get(session,"done"),"search did not finish");
        log("SEARCH "+get(target,"id")+" slices="+slices+" totalMs="+(total/1e6)+" worstSliceMs="+(worst/1e6));return call(session,"result");
    }
    void run()throws Exception{
        Object server=stat("net.minecraft.server.MinecraftServer","func_71276_C");Object world=((Object[])get(server,"field_71305_c"))[0];
        Object profile=make("com.mojang.authlib.GameProfile",UUID.fromString("00000000-0000-0000-0000-000000000043"),"AstralProbe");
        Object player=stat("net.minecraftforge.common.util.FakePlayerFactory","get",world,profile);
        call(player,"func_70107_b",0D,80D,0D);
        Object compass=field("am2.items.ItemsCommonProxy","astralCompass");need(compass!=null,"server compass registration");
        log("DEDICATED_ITEM_REGISTRATION=PASS");
        for(String boss:new String[]{"Air","Arcane","Earth","Ender","Fire","Life","Lightning","Nature","Water","Winter"}){make("am2.bosses.Entity"+boss+"Guardian",world);log("GUARDIAN_CONSTRUCTED="+boss);}
        List<?> catalog=(List<?>)stat("am2.compass.AstralStructureLocator","discoverCatalog",world);
        Set<String> ids=new TreeSet<String>();for(Object t:catalog)ids.add((String)get(t,"id"));
        log("CATALOG size="+ids.size()+" ids="+ids);
        need(ids.contains("wizardry:wizard_tower"),"Wizardry adapter");need(ids.contains("chromaticraft:BURROW"),"ChromatiCraft adapter");
        need(ids.contains("orespawn:makeKingAltar"),"OreSpawn adapter");log("CATALOG size="+ids.size()+" ids="+ids);
        // Synthetic saved structure tests the real Forge world's storage and exact BB center.
        Object data=make("net.minecraft.world.gen.structure.MapGenStructureData","AstralProbeStructure");
        Object entry=make("net.minecraft.nbt.NBTTagCompound");call(entry,"func_74768_a","ChunkX",8);call(entry,"func_74768_a","ChunkZ",4);
        call(entry,"func_74783_a","BB",new int[]{128,20,64,160,40,96});call(data,"func_143043_a",entry,8,4);
        call(get(world,"perWorldStorage"),"func_75745_a","AstralProbeStructure",data);
        Object target=make("am2.compass.StructureTargetInfo","AstralProbeStructure","Probe","modded",0,true,1);
        Object provider=call(world,"func_72863_F");int before=((Number)call(provider,"func_73152_e")).intValue();
        Random random=(Random)get(world,"field_73012_v");random.setSeed(773L);
        Object result=search(world,player,target);need(result!=null && ((Number)get(result,"x")).intValue()==144 && ((Number)get(result,"z")).intValue()==80,"saved structure center");
        need(random.nextLong()==new Random(773L).nextLong(),"world RNG altered");
        need(before==((Number)call(provider,"func_73152_e")).intValue(),"search loaded chunks");log("SAVED_STRUCTURE_BB_RNG_CHUNKS=PASS");
        for(Object t:catalog)if(get(t,"id").equals("Village")){
            before=((Number)call(provider,"func_73152_e")).intValue();random.setSeed(881L);result=search(world,player,t);
            need(result!=null,"village prediction in flat provider list");need(random.nextLong()==new Random(881L).nextLong(),"prediction changed world RNG");
            need(before==((Number)call(provider,"func_73152_e")).intValue(),"prediction loaded chunks");log("VANILLA_PREDICTION_RNG_CHUNKS=PASS");
        }
        // A real OreSpawn builder must notify the AM2-owned index after completing its altar.
        Object ore=make("mglucas0123.utils.GenericDungeon");call(ore,"makeKingAltar",world,32,8,32);
        Object index=stat("am2.compass.AstralLandmarks","get",world);List<?> points=(List<?>)call(index,"points","orespawn:makeKingAltar");need(!points.isEmpty(),"OreSpawn real generation hook");
        log("ORESPAWN_REAL_BUILD_INDEX=PASS points="+points.size());
        for(Object t:catalog)if(get(t,"id").equals("orespawn:makeKingAltar")){result=search(world,player,t);need(result!=null && ((Number)get(result,"x")).intValue()==32,"OreSpawn search");}
        Object wizard=make("electroblob.wizardry.WizardryWorldGenerator");
        for(int i=0;i<5 && ((List<?>)call(index,"points","wizardry:wizard_tower")).isEmpty();i++)call(wizard,"generateWizardTower",world,new Random(444+i),i*2,0);
        points=(List<?>)call(index,"points","wizardry:wizard_tower");need(!points.isEmpty(),"Wizardry real tower generation hook");log("WIZARDRY_REAL_BUILD_INDEX=PASS points="+points.size());
        // Invalid dimension must not create a target or spend mana; ordinary fake players start below level 50.
        Object tool=make("net.minecraft.item.ItemStack",compass);
        Object inventory=get(player,"field_71071_by");((Object[])get(inventory,"field_70462_a"))[0]=tool;set(inventory,"field_70461_c",0);
        Object props=stat("am2.playerextensions.ExtendedProperties","For",player);
        call(props,"setMagicLevel",80);call(props,"setMaxMana",50000F);call(props,"setCurrentMana",50000F);
        float mana=((Number)call(props,"getCurrentMana")).floatValue();need(mana>=20000F,"fixture requires mana");
        int jobs=((Map<?,?>)field("am2.compass.AstralCompassService","jobs")).size();
        stat("am2.items.ItemAstralCompass","processServerAction",player,0,1,"Fortress");
        need(((Map<?,?>)field("am2.compass.AstralCompassService","jobs")).size()==jobs,"wrong dimension started search");
        need(((Number)call(props,"getCurrentMana")).floatValue()==mana,"wrong dimension charged mana");
        Object validResult=make("am2.compass.AstralStructureLocator$SearchResult","orespawn:makeKingAltar","King Altar",0,32,8,32,true);
        stat("am2.items.ItemAstralCompass","finishSearch",player,tool,null);
        need(((Number)call(props,"getCurrentMana")).floatValue()==mana,"failed search charged mana");
        call(props,"setCurrentMana",100F);stat("am2.items.ItemAstralCompass","finishSearch",player,tool,validResult);
        need(!(Boolean)stat("am2.items.ItemAstralCompass","hasTarget",tool),"insufficient mana granted target");
        call(props,"setCurrentMana",50000F);mana=((Number)call(props,"getCurrentMana")).floatValue();
        stat("am2.items.ItemAstralCompass","finishSearch",player,tool,validResult);
        need((Boolean)stat("am2.items.ItemAstralCompass","hasTarget",tool),"successful search missing target");
        need(((Number)call(props,"getCurrentMana")).floatValue()==mana-20000F,"success must charge exactly 20000");log("SERVER_DIMENSION_MANA_RECHECK_TARGET=PASS");
        Object handler=make("am2.network.AMPacketProcessorServer");
        int pending=((Map<?,?>)field("am2.compass.AstralCompassService","pending")).size();
        call(handler,"handleAstralCompassAction",new byte[0],player);
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();java.io.DataOutputStream output=new java.io.DataOutputStream(bytes);
        output.writeInt(0);output.writeInt(99);output.writeUTF("Village");call(handler,"handleAstralCompassAction",bytes.toByteArray(),player);
        bytes.reset();output.writeInt(0);output.writeInt(0);output.writeUTF("");output.writeByte(1);call(handler,"handleAstralCompassAction",bytes.toByteArray(),player);
        need(((Map<?,?>)field("am2.compass.AstralCompassService","pending")).size()==pending,"invalid packet queued work");log("MALFORMED_REQUESTS=PASS");
        log("NO_REMOTE_CLIENT_CONNECTED; GUI and real client handshake not exercised");
    }
}
