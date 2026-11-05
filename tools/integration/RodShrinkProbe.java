package rodshrinkprobe;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.reflect.*;
import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.io.File;
import java.util.*;
import org.lwjgl.opengl.GL11;

/** Disposable client/server regression; never package in the playable mod. */
@Mod(modid="rodshrinkprobe",name="Disposable Rod and Shrink Regression",version="1",dependencies="required-after:arsmagica2")
public final class RodShrinkProbe {
 static Object mc,world,player,small;static boolean started,finished,clientDone;static volatile boolean serverDone,ready;
 static int ticks;static long soak;static final List<String> results=Collections.synchronizedList(new ArrayList<String>());
 static URLClassLoader baseline;
 static Object get(Object o,String n)throws Exception{for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o instanceof Class?null:o);}catch(NoSuchFieldException e){}throw new NoSuchFieldException(n);}
 static void set(Object o,String n,Object v)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);return;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(n);}
 static Object call(Object o,String n,Object...a)throws Exception{for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods())if(m.getName().equals(n)&&m.getParameterTypes().length==a.length)try{m.setAccessible(true);return m.invoke(o instanceof Class?null:o,a);}catch(IllegalArgumentException e){}throw new NoSuchMethodException(n);}
 static Object stat(String c,String n,Object...a)throws Exception{return call(Class.forName(c),n,a);}
 static Object field(String c,String n)throws Exception{return get(Class.forName(c),n);}
 static Object make(String n,Object...a)throws Exception{for(Constructor<?> c:Class.forName(n).getConstructors())if(c.getParameterTypes().length==a.length)try{return c.newInstance(a);}catch(IllegalArgumentException e){}throw new NoSuchMethodException(n);}
 static double number(Object o){return ((Number)o).doubleValue();}
 static void need(boolean b,String n){if(!b)throw new AssertionError(n);}
 static void pass(String n){results.add(n+"=PASS");System.out.println("ROD_SHRINK "+n+"=PASS");}
 static void finish(int code){if(finished)return;finished=true;try{Files.write(Paths.get("rod-shrink-result.txt"),results);}catch(Exception e){e.printStackTrace();}FMLCommonHandler.instance().exitJava(code,false);}
 static void fail(Throwable t){t.printStackTrace();results.add("ERROR="+t);finish(1);}
 static Object properties(Object p)throws Exception{return stat("am2.playerextensions.ExtendedProperties","For",p);}
 static void update(Object handler,Object p)throws Exception{call(handler,"onEntityLiving",make("net.minecraftforge.event.entity.living.LivingEvent$LivingUpdateEvent",p));}
 static double feet(Object p)throws Exception{return number(get(get(p,"field_70121_D"),"field_72338_b"));}
 static boolean menu(Object o){if(o==null)return false;for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass()){String n=c.getName();if(n.equals("net.minecraft.client.gui.GuiMainMenu")||n.equals("com.gildedgames.the_aether.client.gui.menu.AetherMainMenu")||n.startsWith("DummyCore.Client.GuiMainMenu")||n.equals("br.com.redeakashic.cinematic.client.CinematicMenu"))return true;}return false;}
 @Mod.EventHandler public void init(FMLInitializationEvent e){FMLCommonHandler.instance().bus().register(this);}
 @SubscribeEvent public void server(TickEvent.ServerTickEvent e){if(finished||serverDone||!ready||e.phase!=TickEvent.Phase.END)return;try{
  Object server=stat("net.minecraft.server.MinecraftServer","func_71276_C");List<?> players=(List<?>)get(call(server,"func_71203_ab"),"field_72404_b");if(players.isEmpty())return;
  player=players.get(0);world=get(player,"field_70170_p");call(call(world,"func_82736_K"),"func_82764_b","doMobSpawning","false");call(world,"func_72877_b",6000L);
  Object stone=field("net.minecraft.init.Blocks","field_150348_b");for(int x=3;x<=13;x++)for(int z=0;z<=14;z++)call(world,"func_147465_d",x,79,z,stone,0,3);
  Object p=stat("net.minecraftforge.common.util.FakePlayerFactory","get",world,make("com.mojang.authlib.GameProfile",UUID.nameUUIDFromBytes("ShrinkRegression".getBytes("UTF-8")),"ShrinkRegression"));
  set(p,"field_71135_a",get(player,"field_71135_a"));
  call(p,"func_70107_b",9.5,80.0,9.5);Object props=properties(p),handler=make("am2.ShrinkHandler");double ground=feet(p);
  Object buff=field("am2.buffs.BuffList","shrink");call(p,"func_70690_d",make("am2.buffs.BuffEffectShrink",10000,1));
  for(int i=0;i<240;i++)update(handler,p);
  need(Math.abs(feet(p)-ground)<1e-5&&number(get(p,"field_70129_M"))==0&&Math.abs(number(get(p,"field_70130_N"))-.3)<1e-5,"server shrink preserves feet and half width");
  float pct=((Number)call(props,"getShrinkPct")).floatValue();call(handler,"onEntityLiving",make("net.minecraftforge.event.entity.living.LivingEvent$LivingJumpEvent",p));need(number(call(props,"getShrinkPct"))==pct,"jump event cannot apply an extra shrink tick");
  call(p,"func_82170_o",((Number)get(buff,"field_76415_H")).intValue());for(int i=0;i<240;i++)update(handler,p);
  need(Math.abs(feet(p)-ground)<1e-5&&Math.abs(number(get(p,"field_70130_N"))-.6)<1e-5&&Math.abs(number(get(p,"field_70131_O"))-1.8)<1e-5,"server restores dimensions without moving underground");pass("SERVER_SHRINK_EXPIRY_AND_JUMP_GROUND_ANCHOR");
  Object caps=get(player,"field_71075_bZ");set(caps,"field_75098_d",true);set(caps,"field_75100_b",true);set(caps,"field_75101_c",true);call(player,"func_71016_p");call(player,"func_70634_a",5.0,82.0,2.0);serverDone=true;
 }catch(Throwable t){fail(t);}}
 static Object remote(String name,double x)throws Exception{Object w=get(mc,"field_71441_e");Object p=make("net.minecraft.client.entity.EntityOtherPlayerMP",w,make("com.mojang.authlib.GameProfile",UUID.nameUUIDFromBytes(name.getBytes("UTF-8")),name));call(p,"func_70107_b",x,80.0,9.5);return p;}
 static float[] matrix(){FloatBuffer b=ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,b);float[] m=new float[16];b.get(m);return m;}
 static void clientChecks()throws Exception{
  baseline=new URLClassLoader(new URL[]{new File(System.getProperty("rodprobe.reference")).toURI().toURL()},RodShrinkProbe.class.getClassLoader()){
   protected synchronized Class<?> loadClass(String n,boolean resolve)throws ClassNotFoundException{if(!n.equals("am2.items.ItemArcaneFishingRod")&&!n.equals("am2.ShrinkHandler"))return super.loadClass(n,resolve);Class<?> c=findLoadedClass(n);if(c==null)c=findClass(n);if(resolve)resolveClass(c);return c;}};
  Object cp=get(mc,"field_71439_g"),rod=field("am2.items.ItemsCommonProxy","itemArcaneFishingRod"),inv=get(cp,"field_71071_by");Object[] items=(Object[])get(inv,"field_70462_a");int slot=((Number)get(inv,"field_70461_c")).intValue();Object held=items[slot],hook=get(cp,"field_71104_cf");
  try{
   items[slot]=null;set(cp,"field_71104_cf",make("net.minecraft.entity.projectile.EntityFishHook",get(mc,"field_71441_e")));
   Object old=baseline.loadClass("am2.items.ItemArcaneFishingRod").newInstance();boolean failed=false;try{call(old,"func_77617_a",0);}catch(InvocationTargetException ex){failed=ex.getCause() instanceof NullPointerException;}need(failed,"CR19 empty-hand fishing crash reproduced");
   need(call(rod,"func_77617_a",0)!=null,"empty hand icon safe");
   call(make("net.minecraft.client.renderer.entity.RenderItem"),"func_82406_b",get(mc,"field_71466_p"),get(mc,"field_71446_o"),make("net.minecraft.item.ItemStack",rod),12,12);
   items[slot]=make("net.minecraft.item.ItemStack",rod);need(call(rod,"func_77617_a",0)==get(rod,"theIcon"),"held rod still uses cast icon");
   set(mc,"field_71439_g",null);try{need(call(rod,"func_77617_a",0)!=null,"icon previews without player safe");}finally{set(mc,"field_71439_g",cp);}
  }finally{items[slot]=held;set(cp,"field_71104_cf",hook);}pass("CR19_FISHING_CRASH_REPRODUCED_AND_FIXED_REAL_GUI_RENDER");
  Object previous=remote("BeforeShrink",8.0),oldHandler=baseline.loadClass("am2.ShrinkHandler").newInstance();call(properties(previous),"setIsShrunk",true);for(int i=0;i<220;i++)update(oldHandler,previous);need(number(get(previous,"field_70129_M"))>.7,"CR19 remote player incorrect eye offset reproduced");pass("CR19_REMOTE_AVATAR_SINKING_OFFSET_REPRODUCED");
  Object handler=make("am2.ShrinkHandler");small=remote("Shrunken",10.5);Object props=properties(small);call(props,"setIsShrunk",true);double ground=feet(small);
  for(int i=0;i<240;i++)update(handler,small);
  need(Math.abs(feet(small)-ground)<1e-5&&number(get(small,"field_70129_M"))==0&&Math.abs(number(get(small,"field_70131_O"))-.9)<1e-5,"remote avatar stays on ground");
  Object renderer=make("net.minecraft.client.renderer.entity.RenderPlayer"),pre=make("net.minecraftforge.client.event.RenderPlayerEvent$Pre",small,renderer,.5f);
  int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);GL11.glMatrixMode(GL11.GL_MODELVIEW);
  GL11.glPushMatrix();try{GL11.glLoadIdentity();call(make("am2.AMClientEventHandler"),"onPlayerRender",pre);stat("am2.utility.RenderUtilities","setupShrinkRender",small);need(Math.abs(matrix()[0]-.5)<1e-5&&Math.abs(matrix()[13])<1e-5,"fixed render scales once at feet");}finally{GL11.glPopMatrix();GL11.glMatrixMode(mode);}
  pass("SINGLE_FOOT_ANCHORED_SHRINK_RENDER_MATRIX");
  call(props,"setIsShrunk",false);for(int i=0;i<240;i++)update(handler,small);need(Math.abs(feet(small)-ground)<1e-5&&number(get(small,"field_70129_M"))==0&&Math.abs(number(get(small,"field_70131_O"))-1.8)<1e-5,"remote expiry restores ground and height");
  Object localProps=properties(cp);double localGround=feet(cp);call(localProps,"setIsShrunk",true);for(int i=0;i<220;i++)update(handler,cp);need(Math.abs(feet(cp)-localGround)<1e-5&&Math.abs(number(get(cp,"field_70129_M"))-.81)<.01,"local eye offset shrinks without moving feet");call(localProps,"setIsShrunk",false);for(int i=0;i<220;i++)update(handler,cp);need(Math.abs(feet(cp)-localGround)<1e-5&&Math.abs(number(get(cp,"field_70129_M"))-1.62)<.01,"local eye offset restores without sinking");
  pass("LOCAL_AND_REMOTE_SHRINK_RECOVERY");
  call(props,"setIsShrunk",true);for(int i=0;i<220;i++)update(handler,small);Object normal=remote("Normal",8.0),w=get(mc,"field_71441_e");call(w,"func_73027_a",880001,normal);call(w,"func_73027_a",880002,small);baseline.close();
 }
 @SubscribeEvent public void client(TickEvent.ClientTickEvent e){if(finished||e.phase!=TickEvent.Phase.END)return;try{
  if(mc==null)mc=stat("net.minecraft.client.Minecraft","func_71410_x");ticks++;
  if(!started){if(!menu(get(mc,"field_71462_r")))return;Object type=Enum.valueOf((Class)Class.forName("net.minecraft.world.WorldSettings$GameType"),"CREATIVE");started=true;call(mc,"func_71371_a","rod-shrink-disposable","Rod Shrink regression",make("net.minecraft.world.WorldSettings",42L,type,false,false,field("net.minecraft.world.WorldType","field_77138_c")));return;}
  if(get(mc,"field_71439_g")==null)return;ready=true;if(!serverDone)return;
  if(!clientDone){clientChecks();clientDone=true;ticks=0;return;}
  Object cp=get(mc,"field_71439_g");set(cp,"field_70177_z",-32f);set(cp,"field_70126_B",-32f);set(cp,"field_70125_A",12f);set(cp,"field_70127_C",12f);
  if(ticks==50)stat("net.minecraft.util.ScreenShotHelper","func_148259_a",new File("."),"01-shrunken-ground-comparison.png",((Number)get(mc,"field_71443_c")).intValue(),((Number)get(mc,"field_71440_d")).intValue(),call(mc,"func_147110_a"));
  if(soak==0)soak=System.nanoTime();if(System.nanoTime()-soak>30_000_000_000L){need(get(mc,"field_71441_e")!=null&&Math.abs(feet(small)-80)<.02,"rendered remote remains above ground");pass("WORLD_AND_REMOTE_RENDER_STABLE_30_SECONDS");pass("ROD_SHRINK_GAMEPLAY");finish(0);}
 }catch(Throwable t){fail(t);}}
}
