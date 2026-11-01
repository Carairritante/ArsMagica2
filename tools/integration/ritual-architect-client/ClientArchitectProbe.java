package clientarchitectprobe;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.reflect.*;
import java.nio.file.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Disposable integrated-client test: uses the actual menu, channel and inventory synchronization. */
@Mod(modid="clientarchitectprobe",name="Disposable integrated architect test",version="1",dependencies="required-after:arsmagica2")
public final class ClientArchitectProbe {
 static final String ENGINE="am2.ritual.RitualArchitect";
 static final List<String> results=new ArrayList<String>();
 static final ConcurrentLinkedQueue<Runnable> commands=new ConcurrentLinkedQueue<Runnable>();
 static volatile Object serverPlayer,serverTool;
 static volatile boolean fixtureReady;
 static Object mc,player,gui;
 static int state,ticks,index,expected,rotation;
 static long deadline;
 static final int[] choices={0,1,2,3,4,26,29,0};
 static boolean finished;
 static Object get(Object target,String...names)throws Exception{
  for(String name:names)for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored){}
  throw new NoSuchFieldException(Arrays.toString(names));
 }
 static void put(Object target,String name,Object value)throws Exception{
  for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException ignored){}
  throw new NoSuchFieldException(name);
 }
 static Object call(Object target,String name,Object...args)throws Exception{
  Class<?> type=target instanceof Class?(Class<?>)target:target.getClass();
  for(Class<?> c=type;c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterTypes().length==args.length){
   try{m.setAccessible(true);return m.invoke(target instanceof Class?null:target,args);}catch(IllegalArgumentException ignored){}
  }
  throw new NoSuchMethodException(type.getName()+"."+name);
 }
 static Object stat(String type,String name,Object...args)throws Exception{return call(Class.forName(type),name,args);}
 static Object sf(String type,String name)throws Exception{return Class.forName(type).getField(name).get(null);}
 static int num(Object value){return ((Number)value).intValue();}
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 @Mod.EventHandler public void init(FMLInitializationEvent e){System.out.println("CLIENT_ARCHITECT initialized "+FMLCommonHandler.instance().getSide());if(FMLCommonHandler.instance().getSide().isClient())FMLCommonHandler.instance().bus().register(this);}
 @SubscribeEvent public void server(TickEvent.ServerTickEvent e){
  if(e.phase!=TickEvent.Phase.END)return;
  for(Runnable command;(command=commands.poll())!=null;)try{command.run();}catch(Throwable t){fail(t);}
 }
 static void fail(Throwable t){if(finished)return;finished=true;results.add("ERROR="+t);t.printStackTrace();finish(1);}
 static void finish(int code){
  try{Files.write(Paths.get("client-architect-result.txt"),results);}catch(Exception t){t.printStackTrace();}
  for(String line:results)System.out.println("CLIENT_ARCHITECT "+line);
  FMLCommonHandler.instance().exitJava(code,false);
 }
 static Object held(Object p)throws Exception{return call(p,"func_70694_bm");}
 static int selected(Object p)throws Exception{return num(get(stat(ENGINE,"selected",held(p)),"index"));}
 static void open()throws Exception{
  gui=Class.forName("am2.guis.GuiRitualArchitect").getConstructor(Class.forName("net.minecraft.entity.player.EntityPlayer")).newInstance(player);
  call(mc,"func_147108_a",gui);
 }
 static void click(int id)throws Exception{
  Object target=null;
  for(Object button:(List<?>)get(gui,"field_146292_n","buttonList"))if(num(get(button,"field_146127_k","id"))==id)target=button;
  require(target!=null,"Button missing "+id);require(Boolean.TRUE.equals(get(target,"field_146124_l","enabled")),"Button disabled "+id);
  call(gui,"func_73864_a",num(get(target,"field_146128_h","xPosition"))+4,num(get(target,"field_146129_i","yPosition"))+4,0);
 }
 static void selectChoice(int target)throws Exception{
  int rows=num(get(gui,"rows")),targetPage=target/rows;
  while(num(get(gui,"page"))<targetPage)click(2);
  while(num(get(gui,"page"))>targetPage)click(1);
  click(100+target);
  expected=target;deadline=System.nanoTime()+8_000_000_000L;
 }
 static void screenshot(String name)throws Exception{
  stat("net.minecraft.util.ScreenShotHelper","func_148259_a",new File("."),name,num(get(mc,"field_71443_c","displayWidth")),num(get(mc,"field_71440_d","displayHeight")),call(mc,"func_147110_a"));
 }
 static void prepareFixture()throws Exception{
  Object srv=FMLCommonHandler.instance().getMinecraftServerInstance();Object w=call(srv,"func_71218_a",0);
  List<?> players=(List<?>)get(w,"field_73010_i","playerEntities");require(!players.isEmpty(),"Integrated server has no player");serverPlayer=players.get(0);
  Object inv=get(serverPlayer,"field_71071_by","inventory");Object item=sf("am2.items.ItemsCommonProxy","ritualArchitect");
  serverTool=Class.forName("net.minecraft.item.ItemStack").getConstructor(Class.forName("net.minecraft.item.Item"),int.class,int.class).newInstance(item,1,0);
  stat(ENGINE,"ensureToken",serverTool);stat(ENGINE,"select",serverTool,2,0);stat(ENGINE,"setAnchor",serverTool,w,8,80,8);
  Object stone=sf("net.minecraft.init.Blocks","field_150348_b"),air=sf("net.minecraft.init.Blocks","field_150350_a");
  for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++){
   call(w,"func_147465_d",8+dx,79,8+dz,stone,0,3);call(w,"func_147465_d",8+dx,80,8+dz,air,0,3);call(w,"func_147465_d",8+dx,81,8+dz,air,0,3);
  }
  put(inv,"field_70461_c",0);call(inv,"func_70299_a",0,serverTool);
  Object caps=get(serverPlayer,"field_71075_bZ","capabilities");put(caps,"field_75098_d",true);
  call(serverPlayer,"func_70634_a",8.5,80.1,8.5);call(serverPlayer,"func_71016_p");
  call(inv,"func_70296_d");call(get(serverPlayer,"field_71069_bz","openContainer"),"func_75142_b");
  fixtureReady=true;
 }
 @SubscribeEvent public void client(TickEvent.ClientTickEvent e){
  if(e.phase!=TickEvent.Phase.END||finished)return;
  try{
   ticks++;if(mc==null){System.out.println("CLIENT_ARCHITECT first tick");mc=stat("net.minecraft.client.Minecraft","func_71410_x");}
   if(state==0){
    Object screen=get(mc,"field_71462_r","currentScreen");if(screen==null||!screen.getClass().getName().contains("GuiMainMenu"))return;
    Object type=null;for(Object value:Class.forName("net.minecraft.world.WorldSettings$GameType").getEnumConstants())if(((Enum<?>)value).name().equals("CREATIVE"))type=value;
    require(type!=null,"Creative world type missing");Object flat=sf("net.minecraft.world.WorldType","field_77138_c");
    Object settings=Class.forName("net.minecraft.world.WorldSettings").getConstructor(long.class,type.getClass(),boolean.class,boolean.class,flat.getClass()).newInstance(42L,type,false,false,flat);
    call(settings,"func_77159_a");state=1;call(mc,"func_71371_a","architect-disposable","Architect disposable",settings);return;
   }
   player=get(mc,"field_71439_g","thePlayer");if(player==null)return;
   if(state==1){state=2;commands.add(()->{try{prepareFixture();}catch(Throwable t){fail(t);}});return;}
   if(state==2){if(!fixtureReady||held(player)==null||!held(player).getClass().getName().equals("net.minecraft.item.ItemStack"))return;
    if(!Boolean.TRUE.equals(stat(ENGINE,"canUse",player,stat(ENGINE,"getEntry",0))))return;
    if(selected(player)!=2||num(stat(ENGINE,"token",held(player)))==0)return;
    open();selectChoice(choices[index]);state=3;return;
   }
   if(state==3){
    if(selected(serverPlayer)!=expected||selected(player)!=expected){require(System.nanoTime()<deadline,"Menu selection stuck: requested="+expected+", server="+selected(serverPlayer)+", client="+selected(player));return;}
    results.add("LIVE_GUI_CHANNEL_SELECTION_"+expected+"=PASS");
    if(++index<choices.length){selectChoice(choices[index]);return;}
    screenshot("01-menu-selection.png");click(4);rotation=1;deadline=System.nanoTime()+8_000_000_000L;state=4;return;
   }
   if(state==4){if(num(stat(ENGINE,"rotation",held(player)))!=rotation||num(stat(ENGINE,"rotation",held(serverPlayer)))!=rotation){require(System.nanoTime()<deadline,"Rotation not synchronized");return;}
    results.add("LIVE_GUI_ROTATION_AND_NBT_SYNC=PASS");click(5);deadline=System.nanoTime()+8_000_000_000L;state=5;return;
   }
   if(state==5){if(stat(ENGINE,"anchor",held(player))!=null||stat(ENGINE,"anchor",held(serverPlayer))!=null){require(System.nanoTime()<deadline,"Clear center not synchronized");return;}
    results.add("LIVE_GUI_CLEAR_CENTER=PASS");Object world=get(mc,"field_71441_e","theWorld"),controller=get(mc,"field_71442_b","playerController");
    Object hit=stat("net.minecraft.util.Vec3","func_72443_a",8.5,80.0,8.5);
    call(controller,"func_78760_a",player,world,held(player),8,79,8,1,hit);deadline=System.nanoTime()+8_000_000_000L;state=6;return;
   }
   if(state==6){if(stat(ENGINE,"anchor",held(player))==null||stat(ENGINE,"anchor",held(serverPlayer))==null){require(System.nanoTime()<deadline,"Vanilla click did not anchor");return;}
    results.add("LIVE_C08_ANCHOR=PASS");open();click(6);deadline=System.nanoTime()+8_000_000_000L;state=7;return;
   }
   if(state==7){Object status=stat(ENGINE,"status",get(serverPlayer,"field_70170_p","worldObj"),held(serverPlayer));if(!Boolean.TRUE.equals(get(status,"structureReady"))){require(System.nanoTime()<deadline,"GUI build did not execute");return;}
    results.add("LIVE_GUI_CREATIVE_BUILD=PASS");state=8;ticks=0;return;
   }
   if(state==8&&ticks>15){screenshot("02-world-fusion-built.png");results.add("INTEGRATED_CLIENT_MENU_NETWORK_AND_RENDER=PASS");finished=true;finish(0);}
  }catch(Throwable t){fail(t);}
 }
}
