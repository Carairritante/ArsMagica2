import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;
@Mod(modid="serverthrottleprobe",name="Disposable Server Throttle Probe",version="1",dependencies="required-after:arsmagica2")
public final class ServerThrottleProbe {
    int age, phase; long initialTick; Object server,world,p1,p2,handler,a,b,blocked,props1,props2,tool1,tool2;
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
    static void log(String s)throws Exception{System.out.println("THROTTLE_PROBE "+s);Files.write(Paths.get("throttle-results.txt"),(s+"\n").getBytes("UTF-8"),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}

    @Mod.EventHandler public void start(FMLServerStartedEvent event){FMLCommonHandler.instance().bus().register(this);}
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || phase==99 || ++age<5)return;
        try {
            if(phase==0){setup();immediate();phase=1;}
            else if(phase==1 && time()-initialTick>=5){afterFive();phase=2;}
            else if(phase==2 && time()-initialTick>=20){afterTwenty();phase=3;}
            else if(phase==3 && time()-initialTick>=40){dimensionChecks();log("SERVER_THROTTLES=PASS");stop();}
        }catch(Throwable e){try{log("SERVER_THROTTLES=FAIL "+e);e.printStackTrace();stop();}catch(Exception ignored){e.printStackTrace();}}
    }
    void stop()throws Exception{phase=99;call(server,"func_71263_m");}
    long time()throws Exception{return ((Number)call(world,"func_82737_E")).longValue();}
    Object player(String name,int number)throws Exception{
        Object profile=make("com.mojang.authlib.GameProfile",UUID.fromString("00000000-0000-0000-0000-0000000000"+number),name);
        Object p=stat("net.minecraftforge.common.util.FakePlayerFactory","get",world,profile);
        // FakePlayerFactory supplies no network handler. Capture outgoing packets in a
        // closed embedded connection: no socket, handshake or client is simulated.
        Object manager=make("net.minecraft.network.NetworkManager",false);
        Object handlers=java.lang.reflect.Array.newInstance(Class.forName("io.netty.channel.ChannelHandler"),1);
        java.lang.reflect.Array.set(handlers,0,make("io.netty.channel.ChannelInboundHandlerAdapter"));
        Object channel=make("io.netty.channel.embedded.EmbeddedChannel",handlers);
        call(channel,"close");set(manager,"field_150746_k",channel);
        stat("cpw.mods.fml.common.network.handshake.NetworkDispatcher","allocAndSet",manager,call(server,"func_71203_ab"));
        make("net.minecraft.network.NetHandlerPlayServer",server,manager,p);
        call(p,"func_70107_b",10D,10D,10D);return p;
    }
    void block(int x,int y,int z,Object block)throws Exception{call(world,"func_147465_d",x,y,z,block,0,3);}
    Object machine(int x)throws Exception{
        block(x,8,8,field("am2.blocks.BlocksCommonProxy","arcaneStriker"));
        block(x,9,8,field("net.minecraft.init.Blocks","field_150350_a"));
        block(x,10,8,field("net.minecraft.init.Blocks","field_150350_a"));
        Object te=call(world,"func_147438_o",x,8,8);need(te!=null&&te.getClass().getName().equals("am2.blocks.tileentities.TileEntityArcaneStriker"),"machine placement");return te;
    }
    Object equip(Object p)throws Exception{
        Object tool=make("net.minecraft.item.ItemStack",field("am2.items.ItemsCommonProxy","astralCompass"));
        Object inv=get(p,"field_71071_by");((Object[])get(inv,"field_70462_a"))[0]=tool;set(inv,"field_70461_c",0);return tool;
    }
    Object properties(Object p)throws Exception{
        Object props=stat("am2.playerextensions.ExtendedProperties","For",p);
        call(props,"setMagicLevel",80);call(props,"setMaxMana",50000F);call(props,"setCurrentMana",50000F);return props;
    }
    void action(Object p,int x,int action)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(bytes);
        d.writeInt(x);d.writeInt(8);d.writeInt(8);d.writeInt(action);d.writeInt(0);
        call(handler,"handleArcaneStrikerAction",bytes.toByteArray(),p);
    }
    boolean active(Object te)throws Exception{return (Boolean)call(te,"isTestActive");}
    int aliveDummies()throws Exception{
        int n=0;for(Object e:(List<?>)get(world,"field_72996_f"))if(e.getClass().getName().equals("am2.entities.EntityArcaneTargetDummy")&&!(Boolean)get(e,"field_70128_L"))n++;return n;
    }
    Map<?,?> jobs()throws Exception{return (Map<?,?>)field("am2.compass.AstralCompassService","jobs");}
    float mana(Object props)throws Exception{return ((Number)call(props,"getCurrentMana")).floatValue();}
    long cooldown(Object p)throws Exception{return ((Number)call(call(p,"getEntityData"),"func_74763_f","am2AstralRequestUntil")).longValue();}
    void search(Object p,String id)throws Exception{stat("am2.items.ItemAstralCompass","processServerAction",p,0,1,id);}
    void setup()throws Exception{
        server=stat("net.minecraft.server.MinecraftServer","func_71276_C");world=((Object[])get(server,"field_71305_c"))[0];
        p1=player("ThrottleOne",51);p2=player("ThrottleTwo",52);handler=make("am2.network.AMPacketProcessorServer");
        a=machine(8);b=machine(12);blocked=machine(16);block(16,9,8,field("net.minecraft.init.Blocks","field_150348_b"));
        tool1=equip(p1);tool2=equip(p2);props1=properties(p1);props2=properties(p2);initialTick=time();
        stat("am2.compass.AstralLandmarks","record",world,32,8,32,"audit:throttle_target");
        List<?> catalog=(List<?>)stat("am2.compass.AstralStructureLocator","discoverCatalog",world);
        Set<String> ids=new HashSet<String>();for(Object t:catalog)ids.add((String)get(t,"id"));
        need(ids.contains("chromaticraft:PYLON")&&ids.contains("orespawn:makeKingAltar")&&ids.contains("wizardry:wizard_tower"),"Forbidden optional integrations absent");
        log("FORBIDDEN_CATALOG=PASS size="+catalog.size());
        Map<String,Object> entries=new HashMap<String,Object>();for(Object entry:catalog)entries.put((String)get(entry,"id"),entry);
        int utopia=((Number)field("mglucas0123.worlds.utopia.WorldProviderDIM80","DimensionID")).intValue();
        int crystal=((Number)field("mglucas0123.worlds.crystal.WorldProviderDIM84","DimensionID5")).intValue();
        int islands=((Number)field("mglucas0123.worlds.islands.WorldProviderDIM83","DimensionID4")).intValue();
        int twilight=((Number)field("twilightforest.TwilightForestMod","dimensionID")).intValue();
        need(Arrays.equals((int[])call(entries.get("orespawn:makeKingAltar"),"getSearchDimensions"),new int[]{utopia}),"King altar mislabeled");
        need(Arrays.equals((int[])call(entries.get("orespawn:makeCrystalBattleTower"),"getSearchDimensions"),new int[]{crystal}),"Crystal tower mislabeled");
        Object castle=entries.get("orespawn:makeEnderCastle");need((Boolean)call(castle,"listsDimension",1)&&(Boolean)call(castle,"listsDimension",islands)&&!(Boolean)call(castle,"listsDimension",0),"multiple natural dimensions lost");
        need(!(Boolean)call(entries.get("chromaticraft:PYLON"),"hasKnownDimensions"),"variable Chroma rules incorrectly inherited current dimension");
        boolean tf=false;for(String id:entries.keySet())if(id.startsWith("twilightforest:")){tf=true;need((Boolean)call(entries.get(id),"listsDimension",twilight),"Twilight wrong dimension");}
        need(tf,"Twilight catalog missing outside Twilight dimension");
        log("NATIVE_DIMENSION_METADATA=PASS king="+utopia+" crystal="+crystal+" islands="+islands+" twilight="+twilight);
    }
    void immediate()throws Exception{
        action(p1,8,3);need(active(a),"first start denied");need(call(a,"findActiveDummy")!=null,"first dummy missing");
        action(p2,8,4);need(!active(a),"another player cannot stop");
        for(int i=0;i<100;i++){action(i%2==0?p1:p2,8,3);need(!active(a),"same-tick stop/start bypass");action(p2,8,4);}
        need(aliveDummies()==0,"burst leaked living dummies");
        need(((Number)get(a,"lastStartWorldTick")).longValue()==initialTick,"burst extended start interval");
        action(p2,12,3);need(active(b),"throttle incorrectly shared between machines");need(aliveDummies()==1,"independent machine should have one dummy");
        action(p1,12,4);need(aliveDummies()==0,"stop must discard dummy immediately");
        action(p1,16,3);need(!active(blocked),"blocked machine started");
        block(16,9,8,field("net.minecraft.init.Blocks","field_150350_a"));action(p1,16,3);need(!active(blocked),"failed attempt bypassed interval");
        log("STRIKER_STOP_START_BURST=PASS attempts=100 players=2 liveDummies="+aliveDummies());
        log("STRIKER_INDEPENDENT_MACHINE_AND_FAILED_ATTEMPT=PASS");
        float before=mana(props1);search(p1,"audit:does_not_exist");long until=cooldown(p1);
        need(until==initialTick+20,"unknown target did not claim cooldown");need(jobs().isEmpty(),"unknown target started job");need(mana(props1)==before,"unknown target charged mana");
        for(int i=0;i<100;i++)search(p1,"audit:missing_"+i);
        need(cooldown(p1)==until,"rejected burst extended cooldown");need(jobs().isEmpty()&&mana(props1)==before,"burst changed jobs or mana");
        search(p1,"audit:throttle_target");need(jobs().isEmpty(),"valid target bypassed existing cooldown");
        search(p2,"audit:throttle_target");need(jobs().size()==1,"one player blocked another player's search");
        stat("am2.compass.AstralCompassService","cancel",p2);need(jobs().isEmpty(),"cancel did not remove job");need(mana(props2)==50000F,"cancel charged mana");
        log("COMPASS_UNKNOWN_TARGET_BURST=PASS attempts=100 cooldownTicks=20 manaUnchanged=true");
        log("COMPASS_INDEPENDENT_PLAYERS_AND_CANCEL=PASS");
    }
    void afterFive()throws Exception{
        action(p1,8,3);need(active(a)&&call(a,"findActiveDummy")!=null,"start did not recover after five ticks");
        action(p2,8,3);need(aliveDummies()==1,"duplicate start leaked dummy");action(p1,8,4);
        action(p1,16,3);need(active(blocked),"failed-space throttle never recovered");action(p1,16,4);
        need(aliveDummies()==0,"cleanup after normal use failed");log("STRIKER_RECOVERY_AFTER_5_TICKS=PASS");
        search(p1,"audit:throttle_target");need(jobs().isEmpty(),"search interval shorter than twenty ticks");
    }
    @SuppressWarnings("unchecked") void afterTwenty()throws Exception{
        // Enqueue the real request format, then let the service process both players.
        Object scm=call(server,"func_71203_ab");List<Object> players=(List<Object>)get(scm,"field_72404_b");
        need(!players.contains(p1)&&!players.contains(p2),"fake fixture unexpectedly logged in");
        players.add(p1);players.add(p2);
        try{
            for(Object p:new Object[]{p1,p2}){
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(bytes);
                d.writeInt(0);d.writeInt(1);d.writeUTF("audit:throttle_target");d.writeInt(2000);d.writeBoolean(false);
                call(handler,"handleAstralCompassAction",bytes.toByteArray(),p);
            }
            Object service=make("am2.compass.AstralCompassService");
            for(int i=0;i<200;i++){
                call(service,"tick",new TickEvent.ServerTickEvent(TickEvent.Phase.END));
                if(((Map<?,?>)field("am2.compass.AstralCompassService","pending")).isEmpty()&&jobs().isEmpty())break;
            }
            need(jobs().isEmpty(),"searches did not complete");
            need((Boolean)stat("am2.items.ItemAstralCompass","hasTarget",tool1)&&(Boolean)stat("am2.items.ItemAstralCompass","hasTarget",tool2),"valid queued target missing");
            need(mana(props1)==30000F&&mana(props2)==30000F,"successful search must charge exactly20000once per player");
        }finally{players.remove(p1);players.remove(p2);stat("am2.compass.AstralCompassService","cancel",p1);stat("am2.compass.AstralCompassService","cancel",p2);}
        need(aliveDummies()==0,"living dummy left at shutdown");
        log("COMPASS_TWO_QUEUED_SEARCHES_RECOVERY_AND_EXACT_MANA=PASS");
        for(Object p:new Object[]{p1,p2}){
            Object manager=get(get(p,"field_71135_a"),"field_147371_a");
            int outgoing=((java.util.Queue<?>)get(manager,"field_150745_j")).size();
            need(outgoing>0,"no outgoing response captured");log("CAPTURED_RESPONSE_PACKETS="+outgoing);
        }
        log("NO_REMOTE_CLIENT_CONNECTED; two Forge FakePlayers exercised server handlers, queue and world entities, not real network transport or graphical UI");
    }
    void dimensionChecks()throws Exception {
        float before=mana(props1);search(p1,"orespawn:makeKingAltar");
        need(jobs().isEmpty()&&mana(props1)==before,"wrong dimension started job or charged mana");
        int utopia=((Number)field("mglucas0123.worlds.utopia.WorldProviderDIM80","DimensionID")).intValue();
        Object nativeWorld=stat("net.minecraftforge.common.DimensionManager","getWorld",utopia);
        need(nativeWorld!=null,"fixture requires already-loaded Utopia; must never load worlds for metadata");
        Object previous=world;world=nativeWorld;Object nativePlayer;
        try{nativePlayer=player("UtopiaProbe",53);}finally{world=previous;}
        equip(nativePlayer);Object nativeProps=properties(nativePlayer);search(nativePlayer,"orespawn:makeKingAltar");
        need(jobs().size()==1&&mana(nativeProps)==50000F,"correct dimension rejected native search");
        stat("am2.compass.AstralCompassService","cancel",nativePlayer);
        // Observed spawner-built landmarks stay searchable outside natural worldgen dimensions.
        stat("am2.compass.AstralLandmarks","record",world,60,8,60,"orespawn:makeKingAltar");
        boolean found=false;for(Object e:(List<?>)stat("am2.compass.AstralStructureLocator","discoverCatalog",world))if(get(e,"id").equals("orespawn:makeKingAltar")){
            found=true;need((Boolean)call(e,"listsDimension",0)&&(Boolean)call(e,"listsDimension",utopia),"observed location lost natural dimension");
        }
        need(found,"observed catalog entry absent");log("WRONG_DIMENSION_REJECTION_NATIVE_ACCEPTANCE_OBSERVED_OVERRIDE=PASS");
    }

}
