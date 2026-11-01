package mobpreviewprobe;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.io.File;
import java.util.*;
import org.lwjgl.opengl.*;
@Mod(modid="mobpreviewprobe",name="Disposable Compendium Mob Preview Probe",version="1",dependencies="required-after:arsmagica2")
public class MobPreviewProbe {
 static Object mc,player,gui;static int state,ticks,index;static boolean finished;static boolean baseline=Boolean.getBoolean("preview.baseline");static List<String> log=new ArrayList<String>();
 static String[] entities={"EntityManaCreeper","EntityEarthElemental","EntityManaElemental","EntityWaterElemental","EntityDryad","EntityFlicker"};
 static Object call(Object o,String n,Object...a)throws Exception{for(Method m:o.getClass().getMethods())if(m.getName().equals(n)&&m.getParameterTypes().length==a.length)try{return m.invoke(o,a);}catch(IllegalArgumentException e){}throw new NoSuchMethodException(n);}
 static Object stat(String c,String n,Object...a)throws Exception{for(Method m:Class.forName(c).getMethods())if(Modifier.isStatic(m.getModifiers())&&m.getName().equals(n)&&m.getParameterTypes().length==a.length)try{return m.invoke(null,a);}catch(IllegalArgumentException e){}throw new NoSuchMethodException(n);}
 static Object field(String c,String n)throws Exception{return Class.forName(c).getField(n).get(null);}
 static Object get(Object o,String n)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException e){}throw new NoSuchFieldException(n);}
 static void set(Object o,String n,Object v)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);return;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(n);}
 static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}

 static void shot(String n)throws Exception{stat("net.minecraft.util.ScreenShotHelper","func_148259_a",new File("."),n,((Number)get(mc,"field_71443_c")).intValue(),((Number)get(mc,"field_71440_d")).intValue(),call(mc,"func_147110_a"));}
 static void page()throws Exception{
  Object world=get(mc,"field_71441_e");Object entity=Class.forName("am2.entities."+entities[index]).getConstructor(Class.forName("net.minecraft.world.World")).newInstance(world);
  gui=Class.forName("am2.guis.GuiArcaneCompendium").getConstructor(Class.forName("net.minecraft.entity.Entity")).newInstance(entity);call(mc,"func_147108_a",gui);ticks=0;
 }
 static boolean lightmap()throws Exception{int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);int unit=((Number)field("net.minecraft.client.renderer.OpenGlHelper","field_77476_b")).intValue();GL13.glActiveTexture(unit);boolean b=GL11.glIsEnabled(GL11.GL_TEXTURE_2D);GL13.glActiveTexture(active);return b;}
 static float[] matrix(int mode){FloatBuffer f=ByteBuffer.allocateDirect(16*4).order(ByteOrder.nativeOrder()).asFloatBuffer();GL11.glGetFloat(mode,f);float[] a=new float[16];f.get(a);return a;}
 static float[] textureMatrix(int unit){int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(unit);float[] a=matrix(GL11.GL_TEXTURE_MATRIX);GL13.glActiveTexture(active);return a;}
 static void verifyState()throws Exception{
  int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),matrix=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
  GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
  try{
   GL13.glActiveTexture(((Number)field("net.minecraft.client.renderer.OpenGlHelper","field_77476_b")).intValue());GL11.glDisable(GL11.GL_TEXTURE_2D);GL13.glActiveTexture(((Number)field("net.minecraft.client.renderer.OpenGlHelper","field_77478_a")).intValue());GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_LIGHTING);
   int lm=((Number)field("net.minecraft.client.renderer.OpenGlHelper","field_77476_b")).intValue(),df=((Number)field("net.minecraft.client.renderer.OpenGlHelper","field_77478_a")).intValue();
   float[] mv=matrix(GL11.GL_MODELVIEW_MATRIX),tex0=textureMatrix(df),tex1=textureMatrix(lm);int depth=GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
   Object lx=field("net.minecraft.client.renderer.OpenGlHelper","lastBrightnessX"),ly=field("net.minecraft.client.renderer.OpenGlHelper","lastBrightnessY");
   boolean before=lightmap();Method render=gui.getClass().getDeclaredMethod("renderEntityIntoUI");render.setAccessible(true);render.invoke(gui);boolean after=lightmap();boolean cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),lighting=GL11.glIsEnabled(GL11.GL_LIGHTING);
   log.add(entities[index]+"_LIGHTMAP="+before+" -> "+after+"; CULL="+cull+"; LIGHTING="+lighting);
   if(!baseline){require(before==after&&!cull&&!lighting,"Preview leaked graphics state "+entities[index]);require(Arrays.equals(mv,matrix(GL11.GL_MODELVIEW_MATRIX))&&Arrays.equals(tex0,textureMatrix(df))&&Arrays.equals(tex1,textureMatrix(lm)),"Preview modified external matrices");require(depth==GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH),"Preview unbalanced matrix stack");require(lx.equals(field("net.minecraft.client.renderer.OpenGlHelper","lastBrightnessX"))&&ly.equals(field("net.minecraft.client.renderer.OpenGlHelper","lastBrightnessY")),"Lightmap cache changed");log.add(entities[index]+"_MATRICES_AND_LIGHTMAP_CACHE=PASS");}
   else if(index<2) require(after,"Baseline failed to reproduce lightmap leak");
  }finally{GL11.glPopAttrib();GL13.glActiveTexture(active);GL11.glMatrixMode(matrix);}
 }
 static void finish(int code){finished=true;try{Files.write(Paths.get("mob-preview-result.txt"),log);}catch(Exception e){e.printStackTrace();}for(String s:log)System.out.println("PREVIEW_PROBE "+s);cpw.mods.fml.common.FMLCommonHandler.instance().exitJava(code,false);}
 @Mod.EventHandler public void init(FMLInitializationEvent e){cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(this);}
 @SubscribeEvent public void client(TickEvent.ClientTickEvent event){if(finished||event.phase!=TickEvent.Phase.END)return;try{
  if(mc==null)mc=stat("net.minecraft.client.Minecraft","func_71410_x");ticks++;
  if(state==0){Object screen=get(mc,"field_71462_r");if(screen==null||!screen.getClass().getName().contains("GuiMainMenu"))return;Object type=Enum.valueOf((Class)Class.forName("net.minecraft.world.WorldSettings$GameType"),"CREATIVE"),flat=field("net.minecraft.world.WorldType","field_77138_c");Object ws=Class.forName("net.minecraft.world.WorldSettings").getConstructor(long.class,type.getClass(),boolean.class,boolean.class,flat.getClass()).newInstance(42L,type,false,false,flat);state=1;ticks=0;call(mc,"func_71371_a","preview-disposable","Preview disposable",ws);return;}
  player=get(mc,"field_71439_g");if(player==null)return;
  if(state==1){if(ticks<40)return;page();state=2;return;}
  if(state==2&&ticks>12){shot(index+"-"+entities[index]+".png");verifyState();index++;if(index<entities.length){page();return;}Object page=Class.forName("am2.guis.GuiArcaneCompendium").getConstructor(String.class).newInstance("yourFirstSpell");call(mc,"func_147108_a",page);state=3;ticks=0;return;}
  if(state==3&&ticks>12){shot("6-return-book.png");call(mc,"func_147108_a",(Object)null);state=4;ticks=0;return;}
  if(state==4&&ticks>12){shot("7-return-world.png");log.add(baseline?"MOB_PREVIEW_LEAK_REPRODUCED=PASS":"MOB_PREVIEW_STATE_AND_NAVIGATION=PASS");finish(0);}
 }catch(Throwable t){t.printStackTrace();log.add("ERROR="+t);finish(1);}}
}
