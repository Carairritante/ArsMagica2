package compassguiprobe;
import cpw.mods.fml.common.*;import cpw.mods.fml.common.event.*;import cpw.mods.fml.common.eventhandler.SubscribeEvent;import cpw.mods.fml.common.gameevent.TickEvent;import java.lang.reflect.*;import java.nio.file.*;import java.util.*;
@Mod(modid="compassguiprobe",name="Disposable Compass GUI Probe",version="1",dependencies="required-after:arsmagica2")
public class CompassGuiProbe {
 static Object mc,player;static boolean launched,finished;static volatile boolean prepared,searchRequested,searchVerified;static int age;static List<String> log=new ArrayList<String>();
    static Object get(Object o,String n)throws Exception{for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o instanceof Class?null:o);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(n);}
    static void set(Object o,String n,Object v)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(n);}
    static Object call(Object o,String n,Object...a)throws Exception{for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods())if(m.getName().equals(n)&&m.getParameterTypes().length==a.length)try{m.setAccessible(true);return m.invoke(o instanceof Class?null:o,a);}catch(IllegalArgumentException ignored){}throw new NoSuchMethodException(n);}
    static Object make(String n,Object...a)throws Exception{for(Constructor<?> c:Class.forName(n).getConstructors())if(c.getParameterTypes().length==a.length)try{return c.newInstance(a);}catch(IllegalArgumentException ignored){}throw new NoSuchMethodException(n);}
    static Object stat(String c,String n,Object...a)throws Exception{return call(Class.forName(c),n,a);}
    static Object field(String c,String n)throws Exception{return get(Class.forName(c),n);}

 static synchronized void record(String s)throws Exception{log.add(s);System.out.println("COMPASS_GUI "+s);Files.write(Paths.get("compass-gui-result.txt"),log);}
 static void end(int code)throws Exception{finished=true;record("EXIT="+code);FMLCommonHandler.instance().exitJava(code,false);}
 @Mod.EventHandler public void init(FMLInitializationEvent e){FMLCommonHandler.instance().bus().register(this);}
 @SubscribeEvent public void server(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END)return;try{
 Object s=stat("net.minecraft.server.MinecraftServer","func_71276_C");List<?> ps=(List<?>)get(call(s,"func_71203_ab"),"field_72404_b");if(ps.isEmpty())return;
 Object p=ps.get(0), w=get(p,"field_70170_p");
 if(prepared){if(searchRequested&&!searchVerified){Object tool=call(p,"func_70694_bm");if((Boolean)stat("am2.items.ItemAstralCompass","hasTarget",tool)){Object target=stat("am2.items.ItemAstralCompass","getTarget",tool);if(!get(target,"id").equals("wizardry:wizard_tower")||((Number)get(target,"x")).intValue()!=512)throw new AssertionError("Wrong search result");record("SERVER_SEARCH_TARGET=PASS");searchVerified=true;}}return;}
 ((Object[])get(get(p,"field_71071_by"),"field_70462_a"))[0]=make("net.minecraft.item.ItemStack",field("am2.items.ItemsCommonProxy","astralCompass"));
 Object props=stat("am2.playerextensions.ExtendedProperties","For",p);call(props,"setMagicLevel",80);call(props,"setMaxMana",50000F);call(props,"setCurrentMana",50000F);call(props,"forceSync");
 stat("am2.compass.AstralLandmarks","record",w,512,80,512,"wizardry:wizard_tower");
 call(get(p,"field_71070_bA"),"func_75142_b");
 record("SERVER_CATALOG="+((List<?>)stat("am2.compass.AstralStructureLocator","discoverCatalog",w)).size());prepared=true;
 }catch(Throwable t){t.printStackTrace();}}
 @SubscribeEvent public void client(TickEvent.ClientTickEvent e){if(finished||e.phase!=TickEvent.Phase.END)return;try{
 if(mc==null)mc=stat("net.minecraft.client.Minecraft","func_71410_x");
 if(!launched){Object scr=get(mc,"field_71462_r");if(scr==null)return;String n=scr.getClass().getName();if(!n.endsWith("GuiMainMenu")&&!n.endsWith("CinematicMenu")&&!n.startsWith("DummyCore.Client.GuiMainMenu")&&!n.endsWith("AetherMainMenu"))return;
 launched=true;Object type=Enum.valueOf((Class)Class.forName("net.minecraft.world.WorldSettings$GameType"),"CREATIVE");call(mc,"func_71371_a","compass-disposable","Compass Regression",make("net.minecraft.world.WorldSettings",42L,type,false,false,field("net.minecraft.world.WorldType","field_77138_c")));return;}
 player=get(mc,"field_71439_g");if(!prepared||player==null)return;Object held=call(player,"func_70694_bm");if(held==null)return;
 age++; if(age==40){call(mc,"func_147108_a",(Object)null);call(get(mc,"field_71442_b"),"func_78769_a",player,get(mc,"field_71441_e"),held);record("OPENED="+get(mc,"field_71462_r"));}
 if(age==180){List<?> list=(List<?>)stat("am2.items.ItemAstralCompass","getClientCatalog");record("CLIENT_CATALOG="+list.size());
 Object gui=get(mc,"field_71462_r");record("SCREEN="+gui);if(gui!=null&&gui.getClass().getName().equals("am2.guis.GuiAstralCompass"))record("FILTERED="+((List<?>)get(gui,"filtered")).size());
 click(gui,23);List<?> mods=(List<?>)get(gui,"filtered");record("MODS_TAB="+mods.size());
 Set<String> ids=new HashSet<String>();for(Object entry:mods)ids.add((String)get(entry,"id"));
 boolean ore=false,chroma=false;for(String id:ids){ore|=id.startsWith("orespawn:");chroma|=id.startsWith("chromaticraft:");}
 record("ORESPAWN="+ore+" CHROMATICRAFT="+chroma+" WIZARDRY="+ids.contains("wizardry:wizard_tower"));
 if(list.size()<=8||mods.isEmpty()||!ore||!chroma||!ids.contains("wizardry:wizard_tower"))throw new AssertionError("Missing mod structures");}
 if(age==185)stat("net.minecraft.util.ScreenShotHelper","func_148259_a",new java.io.File("."),"compass-mods.png",((Number)get(mc,"field_71443_c")).intValue(),((Number)get(mc,"field_71440_d")).intValue(),call(mc,"func_147110_a"));
 if(age==200){Object gui=get(mc,"field_71462_r");set(gui,"selectedId","wizardry:wizard_tower");call(gui,"refreshButtons");searchRequested=true;click(gui,4);record("GUI_SEARCH_SENT=PASS");}
 if(age==300){if(!searchVerified)throw new AssertionError("Server queue failed to execute GUI search");record("COMPASS_GUI_REGRESSION=PASS");end(0);}
 }catch(Throwable t){t.printStackTrace();try{record("FAIL="+t);end(1);}catch(Exception ignored){}}}
 static void click(Object gui,int id)throws Exception{for(Object b:(List<?>)get(gui,"field_146292_n"))if(((Number)get(b,"field_146127_k")).intValue()==id){if(!(Boolean)get(b,"field_146124_l"))throw new AssertionError("Disabled button "+id);call(gui,"func_146284_a",b);return;}throw new AssertionError("Missing button "+id);}
}
