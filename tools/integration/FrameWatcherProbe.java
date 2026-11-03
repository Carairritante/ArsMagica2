package framewatcherprobe;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** Disposable real-client regression; never install this probe in a player instance. */
@Mod(modid="framewatcherprobe",name="Disposable Frame Watcher Regression",version="1",dependencies="required-after:arsmagica2")
public final class FrameWatcherProbe {
    private static Object mc,world,player,fixed,baseline,frame,dummy,resource;
    private static volatile boolean gateReady,clientChecked,clientWorldReady,serverDone,finished;
    private static final CountDownLatch gate=new CountDownLatch(1);
    private static final List<String> results=Collections.synchronizedList(new ArrayList<String>());
    private static int oldBefore,oldRemovalBefore,fixedBefore,state;
    private static long soakStart,tickStart;
    private static URLClassLoader oldLoader;
    private static Object get(Object obj,String name)throws Exception{for(Class<?> c=obj instanceof Class?(Class<?>)obj:obj.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(obj instanceof Class?null:obj);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    private static Object call(Object obj,String name,Object...args)throws Exception{for(Class<?> c=obj instanceof Class?(Class<?>)obj:obj.getClass();c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterTypes().length==args.length)try{m.setAccessible(true);return m.invoke(obj instanceof Class?null:obj,args);}catch(IllegalArgumentException ignored){}throw new NoSuchMethodException(name);}
    private static Object make(String name,Object...args)throws Exception{for(Constructor<?> c:Class.forName(name).getConstructors())if(c.getParameterTypes().length==args.length)try{return c.newInstance(args);}catch(IllegalArgumentException ignored){}throw new NoSuchMethodException(name);}
    private static Object stat(String name,String method,Object...args)throws Exception{return call(Class.forName(name),method,args);}
    private static Object field(String name,String field)throws Exception{return get(Class.forName(name),field);}
    private static void need(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    private static void pass(String message){results.add(message+"=PASS");System.out.println("FRAME_WATCHER "+message+"=PASS");}
    private static void fail(Throwable error){error.printStackTrace();results.add("ERROR="+error);gate.countDown();finish(1);}
    private static void finish(int code){synchronized(FrameWatcherProbe.class){if(finished)return;finished=true;}try{synchronized(results){Files.write(Paths.get("frame-watcher-result.txt"),new ArrayList<String>(results));}}catch(Exception e){e.printStackTrace();}FMLCommonHandler.instance().exitJava(code,false);}
    private static boolean menu(Object s){if(s==null)return false;for(Class<?> c=s.getClass();c!=null;c=c.getSuperclass()){String n=c.getName();if(n.equals("net.minecraft.client.gui.GuiMainMenu")||n.equals("br.com.redeakashic.cinematic.client.CinematicMenu")||n.startsWith("DummyCore.Client.GuiMainMenu")||n.equals("com.gildedgames.the_aether.client.gui.menu.AetherMainMenu"))return true;}return false;}
    private static Object book()throws Exception{return stat("cpw.mods.fml.common.registry.GameRegistry","findItem","minecraft","book");}
    private static int progress(Object watcher,Object f,boolean client)throws Exception{
        try{Map<?,?> watched=(Map<?,?>)get(get(watcher,client?"clientFrames":"serverFrames"),"watched");Object p=watched.get(f);return p==null?-1:((Number)get(p,"time")).intValue();}
        catch(NoSuchFieldException legacy){for(Map.Entry<?,?> e:((Map<?,?>)get(watcher,"watchedFrames")).entrySet())if(get(e.getKey(),"frame")==f)return ((Number)e.getValue()).intValue();return -1;}
    }
    private static void fixture()throws Exception{
        world=call(FMLCommonHandler.instance().getMinecraftServerInstance(),"func_71218_a",0);List<?> people=(List<?>)get(world,"field_73010_i");if(people.isEmpty())return;player=people.get(0);
        Object caps=get(player,"field_71075_bZ");for(String flag:new String[]{"field_75098_d","field_75101_c","field_75100_b","field_75102_a"}){Field f=caps.getClass().getField(flag);f.setBoolean(caps,true);}call(player,"func_71016_p");call(player,"func_70634_a",12.5,83.0,12.5);
        boolean alternative=(Boolean)call(field("am2.AMCore","config"),"isAlternativeStart");resource=field("am2.blocks.BlocksCommonProxy",alternative?"witchwoodLog":"liquidEssence");
        call(world,"func_147465_d",8,80,8,resource,0,3);fixed=make("am2.ItemFrameWatcher");
        oldLoader=new URLClassLoader(new URL[]{new File(System.getProperty("frameprobe.reference")).toURI().toURL()},FrameWatcherProbe.class.getClassLoader()){
            protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(!name.equals("am2.ItemFrameWatcher")&&!name.startsWith("am2.ItemFrameWatcher$"))return super.loadClass(name,resolve);
                Class<?> c=findLoadedClass(name);if(c==null)c=findClass(name);if(resolve)resolveClass(c);return c;
            }
        };
        baseline=oldLoader.loadClass("am2.ItemFrameWatcher").newInstance();
        frame=make("net.minecraft.entity.item.EntityItemFrame",world);call(frame,"func_70107_b",8.5,80.5,8.5);call(frame,"func_82334_a",make("net.minecraft.item.ItemStack",book()));
        call(baseline,"startWatchingFrame",frame);call(fixed,"startWatchingFrame",frame);call(baseline,"checkWatchedFrames");call(fixed,"checkWatchedFrames");
        oldBefore=progress(baseline,frame,false);oldRemovalBefore=((Collection<?>)get(baseline,"queuedRemoveFrames")).size();fixedBefore=progress(fixed,frame,false);need(oldBefore>0&&fixedBefore>0,"recipe source reached on the server");
        gateReady=true;need(gate.await(25,TimeUnit.SECONDS)&&clientChecked,"client side comparison did not complete");
        int distant=1000008;need(!(Boolean)call(world,"func_72899_e",distant,80,distant),"remote chunk initially absent");
        Object remote=make("net.minecraft.entity.item.EntityItemFrame",world);call(remote,"func_70107_b",distant+.5,80.5,distant+.5);call(remote,"func_82334_a",make("net.minecraft.item.ItemStack",book()));call(fixed,"startWatchingFrame",remote);call(fixed,"checkWatchedFrames");
        need(!(Boolean)call(world,"func_72899_e",distant,80,distant),"recipe scan must not load a remote chunk");pass("UNLOADED_CHUNK_SCAN_DOES_NOT_GENERATE_WORLD");
        dummy=make("net.minecraft.entity.item.EntityItemFrame",world);call(dummy,"func_70107_b",9.5,80.5,8.5);call(dummy,"func_82334_a",make("net.minecraft.item.ItemStack",book()));
        final List<Throwable> errors=Collections.synchronizedList(new ArrayList<Throwable>());List<Thread> workers=new ArrayList<Thread>();
        for(int i=0;i<4;i++){Thread worker=new Thread(()->{try{for(int j=0;j<2000;j++){call(fixed,"startWatchingFrame",dummy);call(fixed,"stopWatchingFrame",dummy);}}catch(Throwable e){errors.add(e);}},"Frame queue producer "+i);workers.add(worker);worker.start();}
        for(int i=0;i<200;i++)call(fixed,"checkWatchedFrames");for(Thread worker:workers){worker.join(5000);need(!worker.isAlive(),"queue producer failed to finish");}need(errors.isEmpty(),"concurrent queue failure: "+errors);
        for(int i=0;i<900;i++)call(fixed,"checkWatchedFrames");
        need(call(call(frame,"func_82335_i"),"func_77973_b")==field("am2.items.ItemsCommonProxy","arcaneCompendium"),"native book recipe completes on the server");pass("CONCURRENT_FRAME_QUEUES_AND_REAL_COMPENDIUM_RECIPE");
        call(fixed,"clearWorld",world);call(fixed,"startWatchingFrame",dummy);call(fixed,"checkWatchedFrames");need(((Map<?,?>)get(get(fixed,"serverFrames"),"watched")).isEmpty(),"unloaded world retains frame state");pass("WORLD_UNLOAD_CLEARS_AND_REJECTS_STALE_FRAMES");
        oldLoader.close();tickStart=((Number)call(world,"func_82737_E")).longValue();serverDone=true;
    }
    @Mod.EventHandler public void init(FMLInitializationEvent e){FMLCommonHandler.instance().bus().register(this);}
    @SubscribeEvent public void server(TickEvent.ServerTickEvent e){if(finished||serverDone||gateReady||!clientWorldReady||e.phase!=TickEvent.Phase.END)return;try{fixture();}catch(Throwable t){fail(t);}}
    @SubscribeEvent public void client(TickEvent.ClientTickEvent e){
        if(finished||e.phase!=TickEvent.Phase.END)return;
        try {
            if(mc==null)mc=stat("net.minecraft.client.Minecraft","func_71410_x");
            if(state==0){if(!menu(get(mc,"field_71462_r")))return;Object type=Enum.valueOf((Class)Class.forName("net.minecraft.world.WorldSettings$GameType"),"CREATIVE");Object settings=make("net.minecraft.world.WorldSettings",42L,type,false,false,field("net.minecraft.world.WorldType","field_77138_c"));state=1;call(mc,"func_71371_a","frame-disposable","Frame regression",settings);return;}
            Object clientWorld=get(mc,"field_71441_e");if(clientWorld!=null&&get(mc,"field_71439_g")!=null&&call(clientWorld,"func_147439_a",8,79,8)!=field("net.minecraft.init.Blocks","field_150350_a"))clientWorldReady=true;
            if(gateReady&&!clientChecked){
                try {
                    call(baseline,"checkWatchedFrames");int oldAfter=progress(baseline,frame,false),removed=((Collection<?>)get(baseline,"queuedRemoveFrames")).size();results.add("BASELINE_SERVER_PROGRESS="+oldBefore+"->"+oldAfter+"; REMOVALS="+oldRemovalBefore+"->"+removed);need(oldAfter!=oldBefore||removed!=oldRemovalBefore,"CR18 wrong-side mutation not reproduced");pass("CR18_CLIENT_MODIFIES_SERVER_TRACKING_REPRODUCED");
                    call(fixed,"checkWatchedFrames");call(fixed,"checkWatchedFrames",false);need(progress(fixed,frame,false)==fixedBefore,"client touched corrected server state");pass("CR19_CLIENT_CANNOT_READ_OR_ADVANCE_SERVER_FRAMES");
                    Object cw=get(mc,"field_71441_e");need(cw!=null,"client world ready");call(cw,"func_147465_d",8,80,8,resource,0,3);
                    Object visual=make("net.minecraft.entity.item.EntityItemFrame",cw);call(visual,"func_70107_b",8.5,80.5,8.5);call(visual,"func_82334_a",make("net.minecraft.item.ItemStack",book()));call(fixed,"startWatchingFrame",visual);call(fixed,"checkWatchedFrames");need(progress(fixed,visual,true)>0,"client progress visuals preserved");
                    Object p=((Map<?,?>)get(get(fixed,"clientFrames"),"watched")).get(visual);Field time=p.getClass().getDeclaredField("time");time.setAccessible(true);time.setInt(p,799);call(fixed,"checkWatchedFrames");call(fixed,"checkWatchedFrames");need(call(call(visual,"func_82335_i"),"func_77973_b")==book(),"client must not mint the Compendium");
                    call(fixed,"clearWorld",cw);pass("CLIENT_VISUAL_PROGRESS_WITHOUT_ITEM_CREATION");clientChecked=true;
                }finally{gate.countDown();}
            }
            if(serverDone){
                if(soakStart==0){soakStart=System.nanoTime();pass("FULL_FORBIDDEN_REGRESSION_CHECKS");}
                need(get(mc,"field_71441_e")!=null&&get(mc,"field_71439_g")!=null,"world disconnected during soak");
                long elapsed=(System.nanoTime()-soakStart)/1000000000L;
                if(elapsed>=Long.parseLong(System.getProperty("frameprobe.soakSeconds","150"))){results.add("SOAK_SECONDS="+elapsed);results.add("SERVER_TICKS="+(((Number)call(world,"func_82737_E")).longValue()-tickStart));pass("INTEGRATED_WORLD_REMAINS_CONNECTED");pass("FRAME_WATCHER_GAMEPLAY");finish(0);}
            }
        }catch(Throwable t){fail(t);}
    }
}
