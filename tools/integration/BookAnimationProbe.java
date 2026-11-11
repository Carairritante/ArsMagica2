package bookanimationprobe;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.reflect.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/** Disposable visual/client regression. Never package this class in the mod. */
@Mod(modid="bookanimationprobe",name="Disposable Book Animation Probe",version="1",dependencies="required-after:arsmagica2")
public final class BookAnimationProbe {
    static final boolean baseline=Boolean.getBoolean("bookprobe.baseline");
    static final List<String> results=new ArrayList<String>();
    static volatile boolean prepared;
    static Object mc,player,world,remote,renderer,model;
    static boolean launched,finished,keyDown;
    static int ticks,captureTick=-1;
    static String pendingCapture;
    static Object get(Object o,String n)throws Exception{for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o instanceof Class?null:o);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(n);}
    static void set(Object o,String n,Object v)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(n);}
    static Object call(Object o,String n,Object...a)throws Exception{for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods())if(m.getName().equals(n)&&m.getParameterTypes().length==a.length)try{m.setAccessible(true);return m.invoke(o instanceof Class?null:o,a);}catch(IllegalArgumentException ignored){}throw new NoSuchMethodException(n);}
    static Object make(String n,Object...a)throws Exception{for(Constructor<?> c:Class.forName(n).getConstructors())if(c.getParameterTypes().length==a.length)try{return c.newInstance(a);}catch(IllegalArgumentException ignored){}throw new NoSuchMethodException(n);}
    static Object stat(String c,String n,Object...a)throws Exception{return call(Class.forName(c),n,a);}
    static Object field(String c,String n)throws Exception{return get(Class.forName(c),n);}
    static void need(boolean b,String s){if(!b)throw new AssertionError(s);}
    static void pass(String s){results.add(s+"=PASS");System.out.println("BOOK_PROBE "+s+"=PASS");}
    static void finish(int code){if(finished)return;finished=true;try{Files.write(Paths.get("book-animation-result.txt"),results);}catch(Exception e){e.printStackTrace();}FMLCommonHandler.instance().exitJava(code,false);}
    static void fail(Throwable e){e.printStackTrace();results.add("ERROR="+e);finish(1);}
    static Object stack(Object item)throws Exception{return make("net.minecraft.item.ItemStack",item);}
    static Object book(String item,boolean filled)throws Exception{
        Object b=stack(field("am2.items.ItemsCommonProxy",item));
        if(filled){Object spell=stack(field("am2.items.ItemsCommonProxy","spell"));call(spell,"func_77982_d",make("net.minecraft.nbt.NBTTagCompound"));call(field("am2.spell.SpellUtils","instance"),"addSpellStageToScroll",spell,"Touch",new String[]{"FireDamage"},new String[0]);call(spell,"func_151001_c","Ember Primer");Object[] slots=(Object[])Array.newInstance(Class.forName("net.minecraft.item.ItemStack"),8);slots[0]=spell;call(call(b,"func_77973_b"),"UpdateStackTagCompound",b,slots);}
        return b;
    }
    @Mod.EventHandler public void init(FMLInitializationEvent e){
        ModContainer am2=Loader.instance().getIndexedModList().get("arsmagica2");
        if(!baseline)need(am2.getVersion().equals(am2.getDisplayVersion()),"maintenance version hidden in Mods UI");
        results.add("VERSION="+am2.getVersion()+"; DISPLAY="+am2.getDisplayVersion());
        FMLCommonHandler.instance().bus().register(this);
    }
    @SubscribeEvent public void server(TickEvent.ServerTickEvent e){if(prepared||finished||e.phase!=TickEvent.Phase.END)return;try{
        Object server=stat("net.minecraft.server.MinecraftServer","func_71276_C");if(server==null)return;List<?> players=(List<?>)get(call(server,"func_71203_ab"),"field_72404_b");if(players.isEmpty())return;
        Object p=players.get(0),w=get(p,"field_70170_p"),inv=get(p,"field_71071_by");Object[] slots=(Object[])get(inv,"field_70462_a");slots[0]=book("spellBook",true);slots[1]=book("arcaneSpellbook",true);slots[2]=book("spellBook",false);
        call(call(w,"func_82736_K"),"func_82764_b","doMobSpawning","false");call(w,"func_72877_b",6000L);
        Object stone=field("net.minecraft.init.Blocks","field_150348_b");for(int x=27;x<39;x++)for(int z=27;z<39;z++)call(w,"func_147465_d",x,79,z,stone,0,3);
        call(p,"func_70634_a",32.5,80.0,32.5);prepared=true;
    }catch(Throwable t){fail(t);}}
    static void select(int slot,int camera)throws Exception{set(get(player,"field_71071_by"),"field_70461_c",slot);set(get(mc,"field_71474_y"),"field_74320_O",camera);set(player,"field_70177_z",0.0f);set(player,"field_70126_B",0.0f);set(player,"field_70125_A",8.0f);set(player,"field_70127_C",8.0f);}
    static void use(boolean down)throws Exception{keyDown=down;Object key=get(get(mc,"field_71474_y"),"field_74313_G");int code=((Number)call(key,"func_151463_i")).intValue();stat("net.minecraft.client.settings.KeyBinding","func_74510_a",code,down);if(down)call(mc,"func_147121_ag");}
    static void capture(String name){pendingCapture=name;captureTick=ticks;}
    static float angle()throws Exception{return ((Number)get(get(model,"field_78100_b"),"field_78796_g")).floatValue();}
    static void reportState(String name)throws Exception{results.add(name+": countdown="+call(player,"func_71052_bv")+", modelAngle="+angle());}
    @SubscribeEvent public void client(TickEvent.ClientTickEvent e){if(finished||e.phase!=TickEvent.Phase.END)return;try{
        if(mc==null)mc=stat("net.minecraft.client.Minecraft","func_71410_x");
        if(!launched){Object screen=get(mc,"field_71462_r");if(screen==null)return;String n=screen.getClass().getName();if(!n.endsWith("GuiMainMenu")&&!n.endsWith("CinematicMenu")&&!n.startsWith("DummyCore.Client.GuiMainMenu")&&!n.endsWith("AetherMainMenu"))return;launched=true;Object type=Enum.valueOf((Class)Class.forName("net.minecraft.world.WorldSettings$GameType"),"CREATIVE");call(mc,"func_71371_a","book-animation-disposable","Book Animation Regression",make("net.minecraft.world.WorldSettings",42L,type,false,false,field("net.minecraft.world.WorldType","field_77138_c")));return;}
        player=get(mc,"field_71439_g");world=get(mc,"field_71441_e");if(!prepared||player==null||world==null)return;
        if(((Object[])get(get(player,"field_71071_by"),"field_70462_a"))[0]==null)return;
        // Packs can auto-open starter manuals after joining. They obscure the
        // render and release all keys, so close them only in this disposable test.
        if(get(mc,"field_71462_r")!=null){call(mc,"func_147108_a",(Object)null);call(mc,"func_71381_h");}
        if(renderer==null){renderer=field("am2.items.renderers.SpellScrollRenderer","instance");model=get(renderer,"modelBook");}
        ticks++;set(mc,"field_71415_G",true);
        if(ticks==1){select(0,0);call(mc,"func_71381_h");}
        if(ticks==20)capture("01-ordinary-closed");
        if(ticks==30)use(true);
        if(ticks==52)capture("02-ordinary-casting");
        if(ticks==62){reportState("ORDINARY_CAST");use(false);}
        if(ticks==84)capture("03-ordinary-released");
        if(ticks==92)select(1,0);
        if(ticks==114)capture("04-arcane-closed");
        if(ticks==124)use(true);
        if(ticks==148)capture("05-arcane-casting");
        if(ticks==158){reportState("ARCANE_CAST");if(baseline)need(Math.abs(angle()-777)<0.01,"baseline arcane route unexpectedly animates");else need(angle()<-.5f&&angle()>-2,"arcane book is not opening");pass(baseline?"BASELINE_ARCANE_STATIC_ROUTE_REPRODUCED":"ARCANE_BOOK_ANIMATED_ROUTE");select(1,2);}
        if(ticks==186)capture("06-arcane-third-person");
        if(ticks==196){use(false);select(0,2);}
        if(ticks==204)use(true);
        if(ticks==230)capture("07-ordinary-third-person");
        if(ticks==240){use(false);select(2,0);}
        if(ticks==262)capture("08-empty-closed");
        if(ticks==272)use(true);
        if(ticks==298)capture("09-empty-use-key");
        if(ticks==308){reportState("EMPTY_USE_KEY");if(!baseline){need(angle()<-.5f,"empty book ignores the use key");pass("EMPTY_BOOK_OPENS_ON_BOUND_USE_KEY");}use(false);}
        if(ticks==330)capture("10-empty-released");
        if(ticks==340){
            select(3,0);remote=make("net.minecraft.client.entity.EntityOtherPlayerMP",world,make("com.mojang.authlib.GameProfile",UUID.nameUUIDFromBytes("BookRemote".getBytes("UTF-8")),"BookRemote"));call(remote,"func_70107_b",32.5,80.0,35.5);set(remote,"field_70177_z",180.0f);set(remote,"field_70126_B",180.0f);Object[] items=(Object[])get(get(remote,"field_71071_by"),"field_70462_a");items[0]=book("arcaneSpellbook",true);call(world,"func_73027_a",880003,remote);
        }
        if(ticks==374)capture("11-remote-player-casting");
        if(ticks==390){if(!baseline){Object held=call(remote,"func_70694_bm");need((Boolean)stat("am2.client.vfx.CastingAnimationState","isCasting",remote,held),"remote synchronized use flag ignored");float open=((Number)stat("am2.client.vfx.CastingAnimationState","getOpenProgress",remote,held,1.0f)).floatValue();need(open>.9f,"remote book stayed closed");pass("REMOTE_BOOK_USES_SYNCHRONIZED_FLAG");}pass(baseline?"BOOK_BASELINE_REPRODUCED":"BOOK_ANIMATION_REGRESSION");finish(0);}
    }catch(Throwable t){fail(t);}}
    @SubscribeEvent public void render(TickEvent.RenderTickEvent e){if(finished||renderer==null)return;try{
        if(e.phase==TickEvent.Phase.START){
            // A real ModelBook render must overwrite this sentinel every frame.
            if(ticks>=124&&ticks<=158)set(get(model,"field_78100_b"),"field_78796_g",777.0f);
            if(remote!=null)call(remote,"func_70052_a",4,true);
            if(player!=null)set(mc,"field_71415_G",true);
            Object useKey=get(get(mc,"field_71474_y"),"field_74313_G");
            stat("net.minecraft.client.settings.KeyBinding","func_74510_a",((Number)call(useKey,"func_151463_i")).intValue(),keyDown);
        }else if(pendingCapture!=null&&ticks>=captureTick){
            stat("net.minecraft.util.ScreenShotHelper","func_148259_a",new File("."),pendingCapture+".png",((Number)get(mc,"field_71443_c")).intValue(),((Number)get(mc,"field_71440_d")).intValue(),call(mc,"func_147110_a"));results.add("SCREENSHOT="+pendingCapture);pendingCapture=null;
        }
    }catch(Throwable t){fail(t);}}
}
